import { playUiTone } from '/src/shared/audio.js'
import { showCelebrationEffect } from '/src/shared/celebration.js'
import { showModal, hideModal } from '/src/shared/modal.js'
import { escapeHtml } from '/src/shared/text.js'
import {
  alignSentenceCopyInput,
  computeWordDiffSegments,
  renderMaskedLetters,
  schedulePeekTimer,
} from '/src/shared/typing-engine.js'

/**
 * 知识反刍阅读后「按句跟敲 + 目标词遮挡补全」交互视图控制器。
 * 采用卡片内真实输入框承载桌面/移动端打字，避免全局 keydown 劫持快捷键或阻塞软键盘。
 */
export function createRuminationPracticeView(elements = {}, deps = {}) {
  const {
    api = null,
    state = {},
    toast = (msg) => console.log(msg),
    speakSentence = () => {},
    onPracticeUpdated = () => {},
    onClose = () => {},
  } = deps

  let currentMaterial = null
  let queue = []
  let activeIdx = 0
  let step = 'copy' // 'copy' | 'cloze'
  let copyInputValue = ''
  let copyWrongKeyCount = 0
  let prevCopyHasError = false
  let copyStartedAt = 0
  let copySubmitting = false
  let copySuccessNote = ''
  let autoAdvanceTimer = null

  let clozeInputValue = ''
  let clozeTargetIdx = 0
  const clozeSolvedKeys = new Set()
  let clozeAttemptCount = 1
  let clozeUsedHint = false
  let clozePeekActive = false
  let clozePeekTimer = null
  let clozeSubmitting = false
  let clozeErrorState = null
  let clozeSuccessState = null
  let clozeStartedAt = 0
  let lastTriggerElement = null
  let lastTriggerSentenceIndex = null

  const roundStats = {
    copySentences: new Set(),
    independentWords: new Set(),
    assistedWords: new Set(),
  }

  function clearTimers() {
    if (autoAdvanceTimer) {
      window.clearTimeout(autoAdvanceTimer)
      autoAdvanceTimer = null
    }
    if (clozePeekTimer) {
      window.clearTimeout(clozePeekTimer)
      clozePeekTimer = null
    }
  }

  function getWordbookId() {
    return (
      currentMaterial?.wordbookId ||
      state.currentWordbookId ||
      document.getElementById('articleWordbookSelect')?.value ||
      ''
    )
  }

  function activeSentence() {
    return queue[activeIdx] || null
  }

  function primarySpanOf(sentence) {
    if (!sentence) return null
    const spans = Array.isArray(sentence.targetSpans) ? sentence.targetSpans : []
    if (!spans.length) return null
    if (sentence.primaryTargetWord) {
      const matched = spans.find(
        (s) => String(s.targetWord || '').toLowerCase() === String(sentence.primaryTargetWord).toLowerCase(),
      )
      if (matched) return matched
    }
    return spans[0]
  }

  function clozeTargetsOf(sentence) {
    if (!sentence) return []
    const spans = Array.isArray(sentence.targetSpans) ? [...sentence.targetSpans] : []
    spans.sort((a, b) => Number(a?.startOffset ?? 0) - Number(b?.startOffset ?? 0))
    const byKey = new Map()
    for (const sp of spans) {
      const targetWord = sp?.targetWord || sp?.surfaceForm || ''
      const surfaceForm = sp?.surfaceForm || sp?.targetWord || ''
      const key = String(targetWord).trim().toLowerCase()
      if (!key) continue
      if (!byKey.has(key)) {
        byKey.set(key, {
          key,
          targetWord,
          surfaceForm,
          spans: [],
        })
      }
      byKey.get(key).spans.push(sp)
    }
    return Array.from(byKey.values())
  }

  function resetCurrentClozeBlankState() {
    clozeInputValue = ''
    clozeAttemptCount = 1
    clozePeekActive = false
    clozeErrorState = null
    clozeStartedAt = Date.now()
  }

  function resetSentenceStepState(targetStep = 'copy') {
    clearTimers()
    step = targetStep
    copyInputValue = ''
    copyWrongKeyCount = 0
    prevCopyHasError = false
    copyStartedAt = Date.now()
    copySubmitting = false
    copySuccessNote = ''

    clozeTargetIdx = 0
    clozeSolvedKeys.clear()
    clozeUsedHint = false
    clozeSubmitting = false
    clozeSuccessState = null
    resetCurrentClozeBlankState()
  }

  function startRecommended(material) {
    if (!material) return
    const sentences = Array.isArray(material.practiceSentences) ? material.practiceSentences : []
    const withTargets = sentences.filter((s) => Array.isArray(s.targetSpans) && s.targetSpans.length > 0)
    if (!withTargets.length) {
      toast('当前场景材料暂无可巩固的目标词汇句子')
      return
    }
    let picked = withTargets.filter((s) => s.recommended)
    if (!picked.length) {
      picked = withTargets.slice(0, 3)
    }
    openQueue(material, picked)
  }

  function startSingleSentence(material, sentenceIndex) {
    if (!material) return
    const sentences = Array.isArray(material.practiceSentences) ? material.practiceSentences : []
    const target = sentences.find((s) => Number(s.sentenceIndex) === Number(sentenceIndex))
    if (!target || !Array.isArray(target.targetSpans) || target.targetSpans.length === 0) {
      toast('未找到可巩固的目标词句子')
      return
    }
    openQueue(material, [target])
  }

  function isHigherPriorityModalOpen() {
    if (typeof document === 'undefined') return false
    const quickLookup = document.getElementById('quickLookupModal')
    const deleteConfirm = document.getElementById('deleteConfirmModal')
    return Boolean(
      (quickLookup && !quickLookup.classList.contains('hidden')) ||
      (deleteConfirm && !deleteConfirm.classList.contains('hidden')),
    )
  }

  function isPracticeOpen() {
    if (elements.practiceModal) {
      return !elements.practiceModal.classList.contains('hidden')
    }
    return Boolean(elements.practiceCard && !elements.practiceCard.classList.contains('hidden'))
  }

  function openQueue(material, selectedSentences) {
    if (!isPracticeOpen() && typeof document !== 'undefined') {
      const active = document.activeElement
      const modalContainer = elements.practiceModal || elements.practiceCard
      if (active && active !== document.body && !modalContainer?.contains?.(active)) {
        lastTriggerElement = active
        lastTriggerSentenceIndex = active.getAttribute?.('data-sentence-index') || null
      }
    }
    currentMaterial = material
    queue = selectedSentences.map((s) => s)
    activeIdx = 0
    roundStats.copySentences.clear()
    roundStats.independentWords.clear()
    roundStats.assistedWords.clear()
    resetSentenceStepState('copy')

    if (elements.practiceModal) {
      showModal(elements.practiceModal)
    }
    if (elements.practiceCard) {
      elements.practiceCard.classList.remove('hidden')
    }
    render()
  }

  function closePractice() {
    clearTimers()
    if (elements.practiceModal) {
      hideModal(elements.practiceModal)
    } else if (elements.practiceCard) {
      elements.practiceCard.classList.add('hidden')
    }
    if (elements.practiceCard) {
      elements.practiceCard.innerHTML = ''
    }
    const triggerEl = lastTriggerElement
    const triggerIdx = lastTriggerSentenceIndex
    lastTriggerElement = null
    lastTriggerSentenceIndex = null
    onClose()

    if (!isHigherPriorityModalOpen() && typeof document !== 'undefined') {
      const fallbackTrigger =
        triggerIdx !== null && elements.articleBody
          ? elements.articleBody.querySelector(`.sentence-practice-trigger[data-sentence-index="${triggerIdx}"]`)
          : null
      const focusTarget =
        triggerEl && (!document.contains || document.contains(triggerEl))
          ? triggerEl
          : fallbackTrigger
      if (focusTarget && typeof focusTarget.focus === 'function' && !focusTarget.closest?.('.hidden')) {
        try {
          focusTarget.focus({ preventScroll: true })
        } catch {
          // 忽略不可聚焦节点异常
        }
      }
    }
  }

  function shakeElement(selector) {
    const el = elements.practiceCard?.querySelector(selector)
    if (!el) return
    el.classList.remove('shake')
    void el.offsetWidth
    el.classList.add('shake')
  }

  function renderTokensHtml(sentence, alignment) {
    const spans = Array.isArray(sentence?.targetSpans) ? sentence.targetSpans : []
    const targetCleanWords = new Set(
      spans.map((s) => String(s.surfaceForm || '').toLowerCase()).flatMap((form) => form.split(/\s+/)),
    )

    return alignment.tokens
      .map((token) => {
        const isTarget = targetCleanWords.has(token.clean)
        const classes = ['copy-token', token.status, isTarget ? 'is-target' : ''].filter(Boolean).join(' ')
        if (token.status === 'error' && token.mismatchIndex >= 0) {
          const raw = token.raw
          const idx = Math.min(token.mismatchIndex, raw.length - 1)
          const before = escapeHtml(raw.slice(0, idx))
          const badChar = escapeHtml(raw.slice(idx, idx + 1) || '')
          const after = escapeHtml(raw.slice(idx + 1))
          return `<span class="${classes}">${before}<u style="color:#f87171;text-decoration-thickness:2px;">${badChar}</u>${after}</span>`
        }
        return `<span class="${classes}">${escapeHtml(token.raw)}</span>`
      })
      .join(' ')
  }

  function renderClozeSlotHtml(surfaceForm, { isSolved, isActive, isFirstActive }) {
    if (isSolved) {
      return `<span class="rumination-cloze-slot is-solved">${escapeHtml(surfaceForm)}</span>`
    }
    const maskedHtml = renderMaskedLetters(surfaceForm, '', {
      stage: 'recall',
      peek: clozePeekActive,
      wrongCount: isActive ? Math.max(0, clozeAttemptCount - 1) : 0,
      hintAfterWrongs: isActive ? 3 : 99,
      escapeHtml,
    })
    const classes = [
      'rumination-cloze-slot',
      isActive ? 'is-active' : 'is-pending',
      clozePeekActive ? 'is-peeking' : '',
    ]
      .filter(Boolean)
      .join(' ')
    const idAttr = isFirstActive ? ' id="ruminationClozeSlot"' : ''
    return `<span class="${classes}"${idAttr} data-surface="${escapeHtml(surfaceForm)}">${maskedHtml}</span>`
  }

  function renderClozeSentenceHtml(sentence, targets, activeIdx) {
    const text = String(sentence?.sentenceText || '')
    const spans = (Array.isArray(sentence?.targetSpans) ? [...sentence.targetSpans] : [])
      .filter((sp) => Number(sp?.startOffset ?? -1) >= 0 && Number(sp?.endOffset ?? -1) > Number(sp?.startOffset ?? -1) && Number(sp?.endOffset ?? -1) <= text.length)
      .sort((a, b) => Number(a.startOffset) - Number(b.startOffset))

    const activeKey = targets[activeIdx]?.key || ''
    const isSentenceSolved = Boolean(clozeSuccessState?.isCorrect)
    let assignedActiveId = false

    if (spans.length > 0) {
      let cursor = 0
      const parts = []
      for (const sp of spans) {
        const start = Number(sp.startOffset)
        const end = Number(sp.endOffset)
        if (start < cursor) continue
        if (start > cursor) {
          parts.push(escapeHtml(text.slice(cursor, start)))
        }
        const key = String(sp.targetWord || sp.surfaceForm || '').trim().toLowerCase()
        const isSolved = isSentenceSolved || clozeSolvedKeys.has(key)
        const isActive = !isSolved && key === activeKey
        const isFirstActive = isActive && !assignedActiveId
        if (isFirstActive) assignedActiveId = true
        parts.push(
          renderClozeSlotHtml(sp.surfaceForm || text.slice(start, end), {
            isSolved,
            isActive,
            isFirstActive,
          }),
        )
        cursor = end
      }
      if (cursor < text.length) {
        parts.push(escapeHtml(text.slice(cursor)))
      }
      return parts.join('')
    }

    const fallbackTarget = targets[activeIdx] || primarySpanOf(sentence)
    return `${escapeHtml(text)} ${renderClozeSlotHtml(fallbackTarget?.surfaceForm || '', {
      isSolved: isSentenceSolved,
      isActive: !isSentenceSolved,
      isFirstActive: !isSentenceSolved,
    })}`
  }

  function renderDiffHtml(expectedSurfaceForm, submittedText) {
    const segments = computeWordDiffSegments(expectedSurfaceForm, submittedText)
    const expectedLen = String(expectedSurfaceForm || '').trim().length
    return segments
      .map((seg) => {
        // 若漏写部分几乎涵盖整词，避免首次错误就直接剧透完整答案，改为长度提示点
        const display =
          seg.status === 'missing' && seg.text.length >= expectedLen && expectedLen > 2
            ? '•'.repeat(seg.text.length)
            : seg.text
        return `<span class="diff-seg ${seg.status}">${escapeHtml(display)}</span>`
      })
      .join('')
  }

  function updateSentenceRecordFromResponse(sentence, targetSpans, submitRes, practiceMode = 'cloze_recall') {
    if (!sentence || !submitRes) return
    const spanList = Array.isArray(targetSpans) ? targetSpans : targetSpans ? [targetSpans] : []
    if (typeof submitRes.copyCount === 'number') {
      sentence.copyCount = submitRes.copyCount
      spanList.forEach((sp) => {
        if (sp) sp.copyCount = submitRes.copyCount
      })
    }
    if (typeof submitRes.clozeAttemptCount === 'number') {
      spanList.forEach((sp) => {
        if (sp) sp.clozeAttemptCount = submitRes.clozeAttemptCount
      })
    }
    if (practiceMode === 'cloze_recall' && typeof submitRes.clozeIndependentPassed === 'boolean') {
      spanList.forEach((sp) => {
        if (sp) sp.clozeIndependentPassed = submitRes.clozeIndependentPassed
      })
      const allSpans = Array.isArray(sentence.targetSpans) ? sentence.targetSpans : []
      sentence.clozeIndependentPassed =
        allSpans.length > 0 && allSpans.every((sp) => Boolean(sp.clozeIndependentPassed))
    }
    if (submitRes.practiceSummary && currentMaterial) {
      currentMaterial.practiceSummary = submitRes.practiceSummary
    }
    onPracticeUpdated(currentMaterial)
  }

  async function submitCopyStep() {
    const sentence = activeSentence()
    const span = primarySpanOf(sentence)
    if (!sentence || !span || copySubmitting) return

    const wbId = getWordbookId()
    const alignment = alignSentenceCopyInput(sentence.sentenceText, copyInputValue)
    if (!alignment.isComplete) {
      copyWrongKeyCount += 1
      playUiTone('wrong')
      shakeElement('#ruminationCopyBoard')
      renderCopyLiveState(sentence, alignment)
      return
    }

    copySubmitting = true
    try {
      let submitRes = null
      if (api?.submitRuminationSentencePractice && currentMaterial?.id && wbId) {
        submitRes = await api.submitRuminationSentencePractice({
          materialId: currentMaterial.id,
          wordbookId: wbId,
          sentenceIndex: sentence.sentenceIndex,
          targetWord: span.targetWord,
          practiceMode: 'copy_sentence',
          userAnswer: copyInputValue,
          wrongKeyCount: copyWrongKeyCount,
          durationMillis: Math.max(500, Date.now() - copyStartedAt),
        })
        updateSentenceRecordFromResponse(sentence, sentence.targetSpans, submitRes, 'copy_sentence')
      }
      playUiTone('success')
      roundStats.copySentences.add(sentence.sentenceIndex)
      copySuccessNote =
        submitRes?.feedbackMessage || '原句跟敲已完成，已记入练习次数（不影响词条复习排期）'
      render()
      autoAdvanceTimer = window.setTimeout(() => {
        if (activeSentence()?.sentenceIndex === sentence.sentenceIndex && step === 'copy') {
          resetSentenceStepState('cloze')
          render()
        }
      }, 420)
    } catch (err) {
      toast(err?.message || '保存跟敲记录失败，请稍后重试')
    } finally {
      copySubmitting = false
    }
  }

  async function submitClozeStep() {
    const sentence = activeSentence()
    const targets = clozeTargetsOf(sentence)
    const activeTarget = targets[clozeTargetIdx] || null
    if (!sentence || !activeTarget || clozeSubmitting) return

    const trimmed = String(clozeInputValue || '').trim()
    if (!trimmed) {
      toast('请先输入被遮挡的目标词拼写')
      return
    }

    const wbId = getWordbookId()
    clozeSubmitting = true
    const attemptNow = clozeAttemptCount

    try {
      let submitRes = null
      if (api?.submitRuminationSentencePractice && currentMaterial?.id && wbId) {
        submitRes = await api.submitRuminationSentencePractice({
          materialId: currentMaterial.id,
          wordbookId: wbId,
          sentenceIndex: sentence.sentenceIndex,
          targetWord: activeTarget.targetWord,
          practiceMode: 'cloze_recall',
          userAnswer: trimmed,
          usedHint: clozeUsedHint,
          attemptCount: attemptNow,
          durationMillis: Math.max(500, Date.now() - clozeStartedAt),
        })
        updateSentenceRecordFromResponse(sentence, activeTarget.spans, submitRes, 'cloze_recall')
      } else {
        const expected = String(activeTarget.surfaceForm || '').trim().toLowerCase()
        const actual = trimmed.replace(/\s+/g, ' ').toLowerCase()
        const isCorrect = expected === actual
        submitRes = {
          isCorrect,
          expectedSurfaceForm: activeTarget.surfaceForm,
          usedHint: clozeUsedHint,
          masterySettled: isCorrect && !clozeUsedHint && attemptNow === 1,
          scheduleAdvanced: false,
          clozeIndependentPassed: isCorrect && !clozeUsedHint && attemptNow === 1,
          feedbackMessage: isCorrect
            ? !clozeUsedHint && attemptNow === 1
              ? '独立无提示一次拼写通过！已提升拼写掌握度'
              : '已完成遮挡巩固（提示或重试仅计入练习次数）'
            : '拼写尚未完全匹配，请对照差异片段后重试',
        }
        if (isCorrect) {
          updateSentenceRecordFromResponse(sentence, activeTarget.spans, submitRes, 'cloze_recall')
        }
      }

      if (!submitRes.isCorrect) {
        clozeAttemptCount += 1
        playUiTone('wrong')
        const expectedForm = submitRes.expectedSurfaceForm || activeTarget.surfaceForm
        clozeErrorState = {
          expectedSurfaceForm: expectedForm,
          submitted: trimmed,
          message: submitRes.feedbackMessage || '目标词拼写尚未完全匹配，请定位标红部位并听音频后重试',
        }
        clozeSuccessState = null
        render()
        shakeElement('#ruminationClozeBoard')
        return
      }

      playUiTone('success')
      clozeErrorState = null
      clozeSolvedKeys.add(activeTarget.key)
      if (submitRes.masterySettled || (!submitRes.usedHint && attemptNow === 1 && submitRes.clozeIndependentPassed)) {
        roundStats.independentWords.add(activeTarget.targetWord)
      } else {
        roundStats.assistedWords.add(activeTarget.targetWord)
      }

      if (clozeTargetIdx + 1 < targets.length) {
        clozeTargetIdx += 1
        resetCurrentClozeBlankState()
        render()
        return
      }

      clozeSuccessState = submitRes
      if (activeIdx + 1 >= queue.length) {
        const layer =
          elements.celebrationLayer ||
          (typeof document !== 'undefined' ? document.getElementById('celebrationLayer') : null)
        showCelebrationEffect(layer)
        toast(submitRes.feedbackMessage || '🎉 重点句巩固完成！')
        closePractice()
        return
      }
      render()
    } catch (err) {
      toast(err?.message || '提交遮挡拼写失败，请稍后重试')
    } finally {
      clozeSubmitting = false
    }
  }

  function refreshClozeSlotsDuringPeek(isPeek) {
    const slots = elements.practiceCard?.querySelectorAll('.rumination-cloze-slot[data-surface]') || []
    slots.forEach((slotEl) => {
      const surface = slotEl.getAttribute('data-surface') || ''
      const isActiveSlot = slotEl.id === 'ruminationClozeSlot'
      slotEl.classList.toggle('is-peeking', isPeek)
      slotEl.innerHTML = renderMaskedLetters(surface, isPeek ? surface : '', {
        stage: 'recall',
        peek: isPeek,
        wrongCount: isPeek ? 0 : isActiveSlot ? Math.max(0, clozeAttemptCount - 1) : 0,
        hintAfterWrongs: isActiveSlot ? 3 : 99,
        escapeHtml,
      })
    })
  }

  function triggerPeekOriginal() {
    clozeUsedHint = true
    clozePeekActive = true
    refreshClozeSlotsDuringPeek(true)
    const peekBox = elements.practiceCard?.querySelector('#clozeOriginalPeekBox')
    if (peekBox) {
      peekBox.classList.remove('hidden')
    }
    const hintTag = elements.practiceCard?.querySelector('#clozeHintNotice')
    if (hintTag) {
      hintTag.textContent = '已触发查看原句提示（本句通过仅计入练习巩固）'
    }
    clozePeekTimer = schedulePeekTimer(
      clozePeekTimer,
      () => {
        clozePeekActive = false
        refreshClozeSlotsDuringPeek(false)
        elements.practiceCard?.querySelector('#clozeOriginalPeekBox')?.classList.add('hidden')
      },
      1200,
    )
  }

  function renderCopyLiveState(sentence, alignment) {
    const card = elements.practiceCard
    if (!card) return
    const tokensEl = card.querySelector('#ruminationCopyTokens')
    if (tokensEl) {
      tokensEl.innerHTML = renderTokensHtml(sentence, alignment)
    }
    const errEl = card.querySelector('#ruminationCopyErrorLine')
    if (errEl) {
      if (alignment.firstErrorWord) {
        errEl.classList.remove('hidden')
        errEl.innerHTML = `定位到错误片段：<strong>${escapeHtml(alignment.firstErrorWord.rawToken)}</strong>（你输入了 <code>${escapeHtml(alignment.firstErrorWord.typed)}</code>），可听音频对照拼写并搭配词组`
      } else {
        errEl.classList.add('hidden')
        errEl.innerHTML = ''
      }
    }
  }

  function advanceNextSentence() {
    if (activeIdx + 1 < queue.length) {
      activeIdx += 1
      resetSentenceStepState('copy')
      render()
    } else {
      closePractice()
    }
  }

  function renderQueueHeaderHtml() {
    const dots = queue
      .map((item, idx) => {
        const done = Boolean(item.clozeIndependentPassed || roundStats.copySentences.has(item.sentenceIndex))
        const classes = ['practice-queue-dot', idx === activeIdx ? 'active' : '', done ? 'done' : '']
          .filter(Boolean)
          .join(' ')
        return `<button class="${classes}" type="button" data-queue-idx="${idx}">第 ${idx + 1} 句</button>`
      })
      .join('')

    return `
      <div class="rumination-practice-header">
        <div class="rumination-practice-queue">${dots}</div>
        <div class="inline-actions">
          <button class="icon-button" id="practiceBackToArticleBtn" type="button" title="关闭 (Esc)" aria-label="关闭" data-close-modal>×</button>
        </div>
      </div>
    `
  }

  function render() {
    const card = elements.practiceCard
    if (!card || !currentMaterial) return

    const sentence = activeSentence()
    const targets = clozeTargetsOf(sentence)
    if (!sentence || !targets.length) {
      closePractice()
      return
    }

    const headerHtml = renderQueueHeaderHtml()

    if (step === 'copy') {
      const alignment = alignSentenceCopyInput(sentence.sentenceText, copyInputValue)
      card.innerHTML = `
        ${headerHtml}
        <div class="rumination-copy-stage-board" id="ruminationCopyBoard">
          <div class="rumination-copy-tokens" id="ruminationCopyTokens">${renderTokensHtml(sentence, alignment)}</div>
          <div class="rumination-practice-input-row">
            <input
              type="text"
              class="rumination-practice-input"
              id="ruminationCopyInput"
              autocomplete="off"
              autocorrect="off"
              autocapitalize="off"
              spellcheck="false"
              placeholder="按上方原句顺次跟敲（自动跳过标点与大小写）"
              value="${escapeHtml(copyInputValue)}"
            />
            <button class="secondary-button compact" id="practiceSpeakSentenceBtn" type="button">🔊 听原句</button>
          </div>
          <div class="rumination-error-localization ${alignment.firstErrorWord ? '' : 'hidden'}" id="ruminationCopyErrorLine"></div>
          ${copySuccessNote ? `<div class="rumination-practice-feedback">${escapeHtml(copySuccessNote)}</div>` : ''}
        </div>
      `

      const copyInput = card.querySelector('#ruminationCopyInput')
      if (copyInput && !isHigherPriorityModalOpen()) {
        copyInput.focus()
        if (copyInputValue) {
          copyInput.setSelectionRange(copyInputValue.length, copyInputValue.length)
        }
      }
      copyInput?.addEventListener('input', (e) => {
        copyInputValue = e.target.value
        const nextAlign = alignSentenceCopyInput(sentence.sentenceText, copyInputValue)
        if (nextAlign.hasError && !prevCopyHasError) {
          copyWrongKeyCount += 1
          playUiTone('wrong')
          shakeElement('#ruminationCopyBoard')
        }
        prevCopyHasError = nextAlign.hasError
        renderCopyLiveState(sentence, nextAlign)
        if (nextAlign.isComplete) {
          submitCopyStep()
        }
      })
      copyInput?.addEventListener('keydown', (e) => {
        if (isHigherPriorityModalOpen()) return
        if (e.key === 'Enter' && !e.isComposing) {
          e.preventDefault()
          submitCopyStep()
        }
      })
      card.querySelector('#practiceSpeakSentenceBtn')?.addEventListener('click', () => {
        speakSentence(sentence.sentenceText)
      })
    } else {
      if (clozeTargetIdx >= targets.length) {
        clozeTargetIdx = Math.max(0, targets.length - 1)
      }
      const activeTarget = targets[clozeTargetIdx]
      const hasSuccess = Boolean(clozeSuccessState?.isCorrect)
      const solvedFormsText = targets.map((t) => t.surfaceForm).join('、')
      const placeholderText =
        targets.length > 1
          ? `按顺序输入第 ${clozeTargetIdx + 1}/${targets.length} 个被遮挡词汇（回车检查）`
          : '输入遮挡处单词（注意当前句子的时态/单复数，回车提交）'
      const hintMetaText =
        targets.length > 1
          ? `第 ${clozeTargetIdx + 1}/${targets.length} 词 · 长度 ${String(activeTarget.surfaceForm || '').length} 字符`
          : `目标词长度 ${String(activeTarget.surfaceForm || '').length} 字符`

      card.innerHTML = `
        ${headerHtml}
        <div class="rumination-cloze-stage-board" id="ruminationClozeBoard">
          <div class="rumination-cloze-sentence">${renderClozeSentenceHtml(sentence, targets, clozeTargetIdx)}</div>
          <div class="rumination-practice-input-row">
            <input
              type="text"
              class="rumination-practice-input"
              id="ruminationClozeInput"
              autocomplete="off"
              autocorrect="off"
              autocapitalize="off"
              spellcheck="false"
              placeholder="${escapeHtml(placeholderText)}"
              value="${escapeHtml(clozeInputValue)}"
              ${hasSuccess ? 'disabled' : ''}
            />
            ${
              hasSuccess
                ? '<button class="primary-button compact" id="practiceNextSentenceBtn" type="button">下一句 →</button>'
                : '<button class="primary-button compact" id="submitClozeBtn" type="button">检查拼写</button>'
            }
          </div>
          <div class="rumination-practice-tools">
            <div class="inline-actions">
              <button class="secondary-button compact" id="peekOriginalSentenceBtn" type="button" ${hasSuccess ? 'disabled' : ''}>👁️ 查看原句 (1.2s / Tab)</button>
              <button class="secondary-button compact" id="practiceSpeakSentenceBtn" type="button">🔊 听这句音频</button>
              <button class="secondary-button compact" id="backToCopyStepBtn" type="button">← 回练原句跟敲</button>
            </div>
            <span id="clozeHintNotice" style="font-size:12px; color:#94a3b8;">
              ${
                clozeUsedHint
                  ? '已触发查看原句提示（本句通过仅计入练习巩固）'
                  : clozeAttemptCount > 1
                    ? `当前第 ${clozeAttemptCount} 次尝试（重试通过仅计入练习巩固）`
                    : escapeHtml(hintMetaText)
              }
            </span>
          </div>
          <div class="rumination-translation-box hidden" id="clozeOriginalPeekBox" style="margin-top:10px;">
            ${escapeHtml(sentence.sentenceText)}
          </div>
          ${
            clozeErrorState
              ? `
            <div class="rumination-error-localization" id="clozeErrorDiffBox">
              <div style="margin-bottom:6px;">${escapeHtml(clozeErrorState.message)}</div>
              <div>拼写定位：${renderDiffHtml(clozeErrorState.expectedSurfaceForm, clozeErrorState.submitted)}</div>
            </div>
          `
              : ''
          }
          ${
            hasSuccess
              ? `
            <div class="rumination-practice-feedback" id="clozeSuccessFeedback">
              <strong>✓ 拼写正确（${escapeHtml(solvedFormsText)}）</strong><br/>
              <span>${escapeHtml(clozeSuccessState.feedbackMessage || '')}</span>
            </div>
          `
              : ''
          }
        </div>
      `

      const clozeInput = card.querySelector('#ruminationClozeInput')
      const nextSentenceBtn = card.querySelector('#practiceNextSentenceBtn')
      if (hasSuccess && nextSentenceBtn && !isHigherPriorityModalOpen()) {
        nextSentenceBtn.focus()
      } else if (clozeInput && !hasSuccess && !isHigherPriorityModalOpen()) {
        clozeInput.focus()
        if (clozeInputValue) {
          clozeInput.setSelectionRange(clozeInputValue.length, clozeInputValue.length)
        }
      }
      clozeInput?.addEventListener('input', (e) => {
        clozeInputValue = e.target.value
      })
      clozeInput?.addEventListener('keydown', (e) => {
        if (isHigherPriorityModalOpen()) return
        if (e.key === 'Tab') {
          e.preventDefault()
          triggerPeekOriginal()
          return
        }
        if (e.key === 'Enter' && !e.isComposing) {
          e.preventDefault()
          submitClozeStep()
        }
      })
      card.querySelector('#submitClozeBtn')?.addEventListener('click', submitClozeStep)
      nextSentenceBtn?.addEventListener('click', advanceNextSentence)
      card.querySelector('#peekOriginalSentenceBtn')?.addEventListener('click', () => {
        triggerPeekOriginal()
        clozeInput?.focus()
      })
      card.querySelector('#practiceSpeakSentenceBtn')?.addEventListener('click', () => {
        speakSentence(sentence.sentenceText)
      })
      card.querySelector('#backToCopyStepBtn')?.addEventListener('click', () => {
        resetSentenceStepState('copy')
        render()
      })
    }

    card.querySelector('#practiceBackToArticleBtn')?.addEventListener('click', closePractice)
    card.querySelectorAll('.practice-queue-dot').forEach((dot) => {
      dot.addEventListener('click', () => {
        const nextIdx = Number(dot.getAttribute('data-queue-idx'))
        if (!Number.isNaN(nextIdx) && nextIdx !== activeIdx && nextIdx >= 0 && nextIdx < queue.length) {
          activeIdx = nextIdx
          resetSentenceStepState('copy')
          render()
        }
      })
    })
  }

  if (elements.practiceModal && typeof elements.practiceModal.addEventListener === 'function') {
    elements.practiceModal.addEventListener('click', (event) => {
      if (event.target === elements.practiceModal) {
        closePractice()
      }
    })
  }

  return {
    startRecommended,
    startSingleSentence,
    closePractice,
    isOpen: isPracticeOpen,
  }
}
