import { hideModal, showModal } from '/src/shared/modal.js'
import { parseVocabularyPayload, renderCognitiveImageCard } from '/src/shared/vocabulary.js'

export function createReviewModalFeature(ctx) {
  const {
    state,
    elements,
    normalizeDefinitions,
    normalizeExamples,
    normalizeArray,
    escapeHtml,
    readText,
    stringifyValue,
    renderCollocationMini,
    speakSentence,
    speak,
    bindStudyTermCards,
    bindInlineAudio,
    statusLabel,
    renderMarkdown,
    saveEntry,
    toast,
    reviewTargetTerm,
    renderReviewFocus,
    selectReviewEntry,
    request,
    setSidebarCollapsed,
  } = ctx

  const sameId = ctx.sameId || ((a, b) => String(a ?? '') === String(b ?? ''))

  function renderReviewCompleteModal(entry) {
    const parsed = parseVocabularyPayload(entry?.parsed || entry?.rawContent)
    const examples = normalizeExamples(parsed).slice(0, 3)
    elements.modalWordTitle.textContent = reviewTargetTerm(entry)
    if (elements.modalCognitiveImage) {
      const cognitiveHtml = renderCognitiveImageCard(parsed, { compact: true })
      elements.modalCognitiveImage.innerHTML = cognitiveHtml
      elements.modalCognitiveImage.classList.toggle('hidden', !cognitiveHtml)
    }
    elements.modalExamples.className = examples.length ? 'modal-examples' : 'modal-examples empty'
    elements.modalExamples.innerHTML = examples.length
      ? examples
          .map(
            (item, index) => `
              <div class="modal-example-item">
                <button class="mini-audio-button" type="button" data-modal-sentence="${index}" title="播放例句">▶</button>
                <p class="sentence">${escapeHtml(item.sentence || '')}</p>
                <p class="translation">${escapeHtml(item.translation || '')}</p>
              </div>
            `,
          )
          .join('')
      : '暂无例句'
    elements.modalExamples.querySelectorAll('[data-modal-sentence]').forEach((button) => {
      button.addEventListener('click', () => speakSentence(examples[Number(button.getAttribute('data-modal-sentence'))]?.sentence))
    })
    showModal(elements.reviewCompleteModal)
  }

  function closeReviewModal(options = {}) {
    if (!elements.reviewCompleteModal) return
    hideModal(elements.reviewCompleteModal)
    if (elements.modalCognitiveImage) {
      elements.modalCognitiveImage.innerHTML = ''
      elements.modalCognitiveImage.classList.add('hidden')
    }
    state.pendingReviewEntryId = null
    state.reviewStage = 'copy'
    state.reviewPeek = false
    if (!options.keepTyped) {
      state.reviewTyped = ''
      state.reviewWrongCount = 0
    }
    if (!options.skipRender && state.activeView === 'reviewView' && state.currentReviewEntry) {
      renderReviewFocus(state.currentReviewEntry)
    }
  }

  function openForgottenDetailModal(entry) {
    if (!entry) return
    const parsed = entry.parsed || {}
    const definitions = normalizeDefinitions(parsed).slice(0, 4)
    const examples = normalizeExamples(parsed).slice(0, 3)
    const memoryTips = normalizeArray(parsed?.memory_tips || parsed?.memoryTips || parsed?.tips || parsed?.memory).slice(0, 2)
    const collocations = normalizeArray(parsed?.collocations || parsed?.phrases || parsed?.common_phrases).slice(0, 4)
    elements.forgottenDetailTitle.textContent = entry.term || entry.normalizedTerm || '单词详情'
    elements.forgottenDetailContent.className = 'forgotten-detail-content'
    elements.forgottenDetailContent.innerHTML = `
      <div class="forgotten-word-head">
        <div>
          <p class="eyebrow">${escapeHtml(statusLabel(entry.status))}</p>
          <h4>${escapeHtml(entry.term || entry.normalizedTerm || '')}</h4>
          <p class="phonetic">${escapeHtml([parsed?.phonetic?.uk, parsed?.phonetic?.us].filter(Boolean).join('    ') || '暂无音标')}</p>
        </div>
        <button class="mini-audio-button" type="button" data-forgotten-word-audio>播放</button>
      </div>
      <div class="mini-definition-list">
        ${
          definitions.length
            ? definitions.map((item) => `<div><span>${escapeHtml(item.pos || 'meaning')}</span><p>${escapeHtml(item.cn || item.en || '')}</p><p class="meaning-en">${escapeHtml(item.en || item.extra || '')}</p></div>`).join('')
            : '<div class="empty">暂无释义</div>'
        }
      </div>
      <div class="focus-section">
        <div class="panel-heading compact-heading"><h3>例句</h3></div>
        <div class="stack ${examples.length ? '' : 'empty'}">
          ${
            examples.length
              ? examples
                  .map(
                    (item, index) => `
                      <div class="example-item">
                        <button class="icon-button" type="button" data-forgotten-sentence="${index}" title="播放例句">▶</button>
                        <p class="sentence">${escapeHtml(item.sentence || '')}</p>
                        <p class="translation">${escapeHtml(item.translation || '')}</p>
                      </div>
                    `,
                  )
                  .join('')
              : '暂无例句'
          }
        </div>
      </div>
      <div class="focus-section">
        <div class="panel-heading compact-heading"><h3>记忆提示</h3></div>
        <div class="stack ${memoryTips.length ? '' : 'empty'}">
          ${
            memoryTips.length
              ? memoryTips.map((item) => `<div class="tip-item">${escapeHtml(readText(item, ['content', 'tip', 'text', 'meaning']) || stringifyValue(item))}</div>`).join('')
              : '暂无记忆提示'
          }
        </div>
      </div>
      <div class="focus-section">
        <div class="panel-heading compact-heading"><h3>搭配</h3></div>
        <div class="collocation-list ${collocations.length ? '' : 'empty'}">
          ${collocations.length ? collocations.map(renderCollocationMini).join('') : '暂无搭配'}
        </div>
      </div>
    `
    elements.forgottenDetailContent.querySelector('[data-forgotten-word-audio]')?.addEventListener('click', () => speak(entry.term || entry.normalizedTerm, elements.voiceSelect.value))
    elements.forgottenDetailContent.querySelectorAll('[data-forgotten-sentence]').forEach((button) => {
      button.addEventListener('click', () => speakSentence(examples[Number(button.getAttribute('data-forgotten-sentence'))]?.sentence))
    })
    bindStudyTermCards(elements.forgottenDetailContent, '[data-collocation-term]', '词组')
    bindInlineAudio(elements.forgottenDetailContent)
    showModal(elements.forgottenDetailModal)
  }

  function closeForgottenDetailModal() {
    if (!elements.forgottenDetailModal) return
    hideModal(elements.forgottenDetailModal)
  }

  const NOTE_DEBOUNCE_DELAY_MS = 2500
  let isNoteComposing = false
  let isNoteDirty = false
  let lastSavedNoteContent = ''
  let noteAutoSaveTimer = null
  let inFlightNoteSavePromise = null

  const REVIEW_NOTE_EMPTY_HTML = '<div class="scene-note-empty-preview"><p class="sub">暂无笔记，双击开始编辑</p></div>'

  function updateReviewNoteStatus(statusText, tone = '', title = '') {
    if (!elements.reviewNoteStatus) return
    elements.reviewNoteStatus.textContent = statusText
    elements.reviewNoteStatus.title = title
    elements.reviewNoteStatus.className = tone ? `scene-note-status ${tone}` : 'scene-note-status'
  }

  function handleReviewNoteCompositionStart() {
    isNoteComposing = true
  }

  function handleReviewNoteCompositionEnd() {
    isNoteComposing = false
    handleReviewNoteInput()
  }

  function handleReviewNoteInput() {
    if (isNoteComposing) return
    const targetEntry = state.currentReviewNoteEntry || state.currentReviewEntry
    if (!targetEntry || !elements.reviewNoteInput) return

    const currentContent = elements.reviewNoteInput.value || ''
    if (currentContent === lastSavedNoteContent) {
      isNoteDirty = false
      if (noteAutoSaveTimer) {
        clearTimeout(noteAutoSaveTimer)
        noteAutoSaveTimer = null
      }
      if (lastSavedNoteContent.trim()) {
        updateReviewNoteStatus('✓ 已保存', 'saved', '已同步')
      } else {
        updateReviewNoteStatus('')
      }
      return
    }

    isNoteDirty = true
    updateReviewNoteStatus('● 待保存', 'saving')

    if (noteAutoSaveTimer) {
      clearTimeout(noteAutoSaveTimer)
    }
    noteAutoSaveTimer = setTimeout(() => {
      void flushReviewNoteSave(false)
    }, NOTE_DEBOUNCE_DELAY_MS)
  }

  async function flushReviewNoteSave(isManual = false) {
    if (noteAutoSaveTimer) {
      clearTimeout(noteAutoSaveTimer)
      noteAutoSaveTimer = null
    }
    if (inFlightNoteSavePromise) {
      return inFlightNoteSavePromise
    }

    const targetEntry = state.currentReviewNoteEntry || state.currentReviewEntry
    if (!targetEntry || !elements.reviewNoteInput) return null

    const note = elements.reviewNoteInput.value || ''
    if (!isNoteDirty && note === lastSavedNoteContent && !isManual) {
      return targetEntry
    }

    updateReviewNoteStatus('● 保存中...', 'saving')

    inFlightNoteSavePromise = (async () => {
      try {
        let resolvedEntryId = targetEntry.entryId || (targetEntry.wordbookId ? targetEntry.id : null)

        // 若词条未入单词本且处于非预览模式，先自动加入默认单词本
        if (!resolvedEntryId && !state.preview && typeof request === 'function') {
          const targetWordbookId = state.currentWordbookId || state.wordbooks?.[0]?.id
          if (targetWordbookId && (targetEntry.term || targetEntry.normalizedTerm)) {
            try {
              const termToAdd = targetEntry.term || targetEntry.normalizedTerm
              const created = await request(`/api/v1/learning/wordbooks/${encodeURIComponent(targetWordbookId)}/entries`, {
                method: 'POST',
                body: JSON.stringify({ term: termToAdd, note }),
              })
              if (created?.id) {
                resolvedEntryId = created.id
                targetEntry.entryId = created.id
                targetEntry.wordbookId = created.wordbookId || targetWordbookId
              }
            } catch (createErr) {
              // 失败降级继续尝试直接更新
            }
          }
        }

        const idToUpdate = resolvedEntryId || targetEntry.id
        const updated = await saveEntry(idToUpdate, { note }, { silent: !isManual })

        if (updated || state.preview) {
          lastSavedNoteContent = note
          isNoteDirty = false
          targetEntry.note = note
          if (state.currentReviewEntry && sameId(state.currentReviewEntry.id, targetEntry.id)) {
            state.currentReviewEntry.note = note
            if (resolvedEntryId) state.currentReviewEntry.entryId = resolvedEntryId
          }
          if (state.currentReviewNoteEntry) {
            state.currentReviewNoteEntry.note = note
            if (resolvedEntryId) state.currentReviewNoteEntry.entryId = resolvedEntryId
          }
          updateReviewNoteStatus('✓ 已保存', 'saved', isManual ? '已手动保存' : '已自动保存')
          if (isManual) {
            toast?.('复习笔记已保存')
          }
          if (elements.reviewNotePreview) {
            elements.reviewNotePreview.innerHTML = note ? renderMarkdown(note) : REVIEW_NOTE_EMPTY_HTML
          }
          return updated || targetEntry
        } else {
          updateReviewNoteStatus('保存失败', 'error', '按 ⌘S 重试')
          return null
        }
      } catch (error) {
        updateReviewNoteStatus('保存失败', 'error', '按 ⌘S 重试')
        toast?.(`复习笔记保存失败：${error.message || error}`)
        return null
      } finally {
        inFlightNoteSavePromise = null
      }
    })()

    return inFlightNoteSavePromise
  }

  function saveReviewNote(isManual = true) {
    return flushReviewNoteSave(isManual)
  }

  function openReviewNoteModal(entry) {
    const targetEntry = entry || state.currentReviewEntry
    if (!targetEntry) return

    // 如果切换到了不同的词条且存在未落库草稿，先静默保存前一个词条的草稿
    if (isNoteDirty && state.currentReviewNoteEntry && !sameId(state.currentReviewNoteEntry.id, targetEntry.id)) {
      void flushReviewNoteSave(false)
    }

    if (noteAutoSaveTimer) {
      clearTimeout(noteAutoSaveTimer)
      noteAutoSaveTimer = null
    }

    state.currentReviewNoteEntry = targetEntry
    if (elements.reviewNoteModalTitle) elements.reviewNoteModalTitle.textContent = '复习笔记'

    const note = targetEntry.note || ''
    const hasNote = Boolean(note.trim())
    lastSavedNoteContent = note
    isNoteDirty = false

    if (elements.reviewNoteInput) {
      elements.reviewNoteInput.value = note
      elements.reviewNoteInput.classList.toggle('hidden', hasNote)
    }
    if (elements.reviewNotePreview) {
      elements.reviewNotePreview.innerHTML = note ? renderMarkdown(note) : REVIEW_NOTE_EMPTY_HTML
      elements.reviewNotePreview.classList.toggle('hidden', !hasNote)
    }
    if (elements.reviewNotePreviewBtn) elements.reviewNotePreviewBtn.textContent = hasNote ? '✏️ 编辑' : '👁️ 预览'
    if (hasNote) {
      updateReviewNoteStatus('✓ 已保存', 'saved', '已同步')
    } else {
      updateReviewNoteStatus('')
    }

    const wasHidden = elements.reviewNoteModal?.classList.contains('hidden')
    if (wasHidden) {
      setSidebarCollapsed?.(true, { persist: false })
    }
    if (elements.reviewNoteModal) {
      elements.reviewNoteModal.classList.remove('hidden')
    }
    if (elements.reviewNoteDrawerBackdrop) {
      elements.reviewNoteDrawerBackdrop.classList.remove('hidden')
    }
    elements.reviewLayout?.classList.add('with-note-open')
    if (!hasNote) {
      elements.reviewNoteInput?.focus()
    }
  }

  function closeReviewNoteModal() {
    if (isNoteDirty) {
      void flushReviewNoteSave(false)
    }
    if (noteAutoSaveTimer) {
      clearTimeout(noteAutoSaveTimer)
      noteAutoSaveTimer = null
    }
    if (elements.reviewNoteModal) elements.reviewNoteModal.classList.add('hidden')
    if (elements.reviewNoteDrawerBackdrop) elements.reviewNoteDrawerBackdrop.classList.add('hidden')
    elements.reviewLayout?.classList.remove('with-note-open')
    state.currentReviewNoteEntry = null
    isNoteDirty = false
  }

  function toggleReviewNoteDrawer(forceState) {
    if (!elements.reviewNoteModal) return
    const isOpen = !elements.reviewNoteModal.classList.contains('hidden')
    const next = typeof forceState === 'boolean' ? forceState : !isOpen
    if (next) {
      openReviewNoteModal(state.currentReviewEntry)
    } else {
      closeReviewNoteModal()
    }
  }

  function toggleReviewNotePreview() {
    if (!elements.reviewNotePreview || !elements.reviewNoteInput) return
    const isPreview = !elements.reviewNotePreview.classList.contains('hidden')
    if (isPreview) {
      elements.reviewNotePreview.classList.add('hidden')
      elements.reviewNoteInput.classList.remove('hidden')
      if (elements.reviewNotePreviewBtn) elements.reviewNotePreviewBtn.textContent = '👁️ 预览'
      elements.reviewNoteInput.focus()
    } else {
      if (isNoteDirty) {
        void flushReviewNoteSave(false)
      }
      const content = elements.reviewNoteInput.value || ''
      elements.reviewNotePreview.innerHTML = content ? renderMarkdown(content) : REVIEW_NOTE_EMPTY_HTML
      elements.reviewNotePreview.classList.remove('hidden')
      elements.reviewNoteInput.classList.add('hidden')
      if (elements.reviewNotePreviewBtn) elements.reviewNotePreviewBtn.textContent = '✏️ 编辑'
    }
  }

  function renderReviewQueueModal() {
    if (!elements.reviewQueueModal) return

    state.reviewQueueFilter = state.reviewQueueFilter || 'all'

    const allEntries = (state.reviewRoundEntries && state.reviewRoundEntries.length > 0)
      ? state.reviewRoundEntries
      : (state.reviewEntries || [])

    const total = allEntries.length
    if (elements.reviewQueueModalCount) {
      elements.reviewQueueModalCount.textContent = `${total} 词`
    }

    let pendingCount = 0
    let rememberedCount = 0
    let requeuedCount = 0

    allEntries.forEach((entry) => {
      const isRemembered = entry.roundStatus === 'remembered'
      const isRequeued = entry.roundStatus === 'requeued' || Boolean(entry.requeued)
      if (isRemembered) rememberedCount += 1
      else if (isRequeued) requeuedCount += 1
      else pendingCount += 1
    })

    if (elements.reviewQueueFilterGroup) {
      const allBtn = elements.reviewQueueFilterGroup.querySelector('[data-queue-filter="all"]')
      const pendingBtn = elements.reviewQueueFilterGroup.querySelector('[data-queue-filter="pending"]')
      const remBtn = elements.reviewQueueFilterGroup.querySelector('[data-queue-filter="remembered"]')
      if (allBtn) {
        allBtn.textContent = `全部 (${total})`
        allBtn.classList.toggle('active', state.reviewQueueFilter === 'all')
      }
      if (pendingBtn) {
        pendingBtn.textContent = `待复习 (${pendingCount + requeuedCount})`
        pendingBtn.classList.toggle('active', state.reviewQueueFilter === 'pending')
      }
      if (remBtn) {
        remBtn.textContent = `今日已复习 (${rememberedCount})`
        remBtn.classList.toggle('active', state.reviewQueueFilter === 'remembered')
      }

      if (elements.reviewQueueFilterGroup.dataset.bound !== 'true') {
        elements.reviewQueueFilterGroup.dataset.bound = 'true'
        elements.reviewQueueFilterGroup.addEventListener('click', (e) => {
          const btn = e.target.closest('[data-queue-filter]')
          if (!btn) return
          state.reviewQueueFilter = btn.getAttribute('data-queue-filter') || 'all'
          renderReviewQueueModal()
        })
      }
    }

    if (!total) {
      if (elements.reviewQueueSummaryText) {
        elements.reviewQueueSummaryText.textContent = '暂无待复习词汇'
      }
      if (elements.reviewQueueList) {
        elements.reviewQueueList.innerHTML = '<div class="empty">当前复习队列为空，请先在上方点击「开始复习」或「近期查词」</div>'
      }
      return
    }

    if (elements.reviewQueueSummaryText) {
      const parts = [`共 ${total} 词`]
      if (pendingCount > 0) parts.push(`${pendingCount} 个待复习`)
      if (rememberedCount > 0) parts.push(`${rememberedCount} 个今日已复习`)
      if (requeuedCount > 0) parts.push(`${requeuedCount} 个本轮回看`)
      elements.reviewQueueSummaryText.textContent = parts.join(' · ')
    }

    if (!elements.reviewQueueList) return

    let visibleEntries = allEntries
    if (state.reviewQueueFilter === 'pending') {
      visibleEntries = allEntries.filter((entry) => entry.roundStatus !== 'remembered')
    } else if (state.reviewQueueFilter === 'remembered') {
      visibleEntries = allEntries.filter((entry) => entry.roundStatus === 'remembered')
    }

    if (!visibleEntries.length) {
      const emptyMsg = state.reviewQueueFilter === 'remembered'
        ? '今日暂无已复习完成的词汇'
        : (state.reviewQueueFilter === 'pending' ? '当前没有待复习词汇' : '当前复习队列为空')
      elements.reviewQueueList.innerHTML = `<div class="empty">${emptyMsg}</div>`
      return
    }

    const rowsHtml = visibleEntries.map((entry, index) => {
      const isCurrent = state.currentReviewEntry && sameId(entry.id, state.currentReviewEntry.id)
      const isRemembered = entry.roundStatus === 'remembered'
      const isRequeued = entry.roundStatus === 'requeued' || Boolean(entry.requeued)

      let statusClass = 'pending'
      let statusText = '待复习'
      if (isCurrent) {
        statusClass = 'current'
        statusText = '● 复习中'
      } else if (isRemembered) {
        statusClass = 'remembered'
        statusText = '✓ 今日已掌握'
      } else if (isRequeued) {
        statusClass = 'requeued'
        statusText = '↻ 本轮回看'
      }

      const term = (typeof reviewTargetTerm === 'function' ? reviewTargetTerm(entry) : null) || entry.term || entry.normalizedTerm || ''
      const parsed = parseVocabularyPayload(entry?.parsed || entry?.rawContent)
      const phonetic = [parsed?.phonetic?.uk, parsed?.phonetic?.us].filter(Boolean).join('  ') || parsed?.phonetic_text || entry.phonetic || ''

      const defs = typeof normalizeDefinitions === 'function' ? normalizeDefinitions(parsed).slice(0, 3) : []
      let defText = ''
      if (defs.length) {
        defText = defs
          .map((d) => (d.pos && d.pos !== 'meaning' ? `<span class="review-queue-pos">${escapeHtml(d.pos)}</span> ${escapeHtml(d.cn || d.en)}` : escapeHtml(d.cn || d.en)))
          .join('； ')
      } else {
        const fallback = entry.meaningText || entry.meaning_text || entry.definition || entry.meaning || '暂无释义'
        defText = escapeHtml(fallback)
      }

      return `
        <div class="review-queue-item ${isCurrent ? 'active' : ''}" data-review-queue-index="${index}">
          <div class="review-queue-item-seq">#${index + 1}</div>
          <div class="review-queue-item-main">
            <div class="review-queue-item-header">
              <span class="review-queue-item-term">${escapeHtml(term)}</span>
              <button class="mini-audio-button review-queue-audio-btn" type="button" data-queue-term="${escapeHtml(term)}" title="发音">▶ 读音</button>
              ${phonetic ? `<span class="review-queue-item-phonetic">${escapeHtml(phonetic)}</span>` : ''}
              <span class="review-queue-status-tag ${statusClass}">${statusText}</span>
            </div>
            <div class="review-queue-item-defs">${defText}</div>
          </div>
          <div class="review-queue-item-action">
            <button class="secondary-button compact review-queue-jump-btn" type="button" data-jump-index="${index}" ${isCurrent ? 'disabled' : ''}>
              ${isCurrent ? '复习中' : '定位复习'}
            </button>
          </div>
        </div>
      `
    }).join('')

    elements.reviewQueueList.innerHTML = rowsHtml

    // Bind pronunciation audio buttons
    elements.reviewQueueList.querySelectorAll('[data-queue-term]').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation()
        const t = btn.getAttribute('data-queue-term')
        if (t && typeof speak === 'function') speak(t)
      })
    })

    // Bind row and button clicks for jump
    elements.reviewQueueList.querySelectorAll('[data-review-queue-index]').forEach((row) => {
      const idx = Number(row.getAttribute('data-review-queue-index'))
      const targetEntry = visibleEntries[idx]
      if (!targetEntry) return

      row.addEventListener('click', (e) => {
        if (e.target.closest('[data-queue-term]')) return
        jumpToEntry(targetEntry)
      })
    })
  }

  function jumpToEntry(entry) {
    if (!entry) return
    closeReviewQueueModal()

    if (state.currentReviewEntry && sameId(entry.id, state.currentReviewEntry.id)) {
      return
    }

    const index = (state.reviewEntries || []).findIndex((item) => sameId(item.id, entry.id))
    if (index >= 0) {
      if (typeof selectReviewEntry === 'function') {
        selectReviewEntry(state.reviewEntries[index], 'brief')
      }
    } else {
      entry.roundStatus = 'pending'
      if (!Array.isArray(state.reviewEntries)) state.reviewEntries = []
      state.reviewEntries.splice(state.currentReviewIndex, 0, entry)
      if (typeof selectReviewEntry === 'function') {
        selectReviewEntry(entry, 'brief')
      }
    }
    toast?.(`已切换至「${entry.term || entry.normalizedTerm}」复习`)
  }

  function openReviewQueueModal() {
    if (!elements.reviewQueueModal) return
    renderReviewQueueModal()
    showModal(elements.reviewQueueModal)
    const activeItem = elements.reviewQueueList?.querySelector('.review-queue-item.active')
    if (activeItem && typeof activeItem.scrollIntoView === 'function') {
      activeItem.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
    }
  }

  function closeReviewQueueModal() {
    if (!elements.reviewQueueModal) return
    hideModal(elements.reviewQueueModal)
  }

  function drawRoundedRect(ctx, x, y, width, height, radius) {
    if (typeof ctx.roundRect === 'function') {
      ctx.beginPath?.()
      ctx.roundRect(x, y, width, height, radius)
      return
    }
    if (typeof ctx.quadraticCurveTo === 'function') {
      ctx.beginPath?.()
      ctx.moveTo?.(x + radius, y)
      ctx.lineTo?.(x + width - radius, y)
      ctx.quadraticCurveTo(x + width, y, x + width, y + radius)
      ctx.lineTo?.(x + width, y + height - radius)
      ctx.quadraticCurveTo(x + width, y + height, x + width - radius, y + height)
      ctx.lineTo?.(x + radius, y + height)
      ctx.quadraticCurveTo(x, y + height, x, y + height - radius)
      ctx.lineTo?.(x, y + radius)
      ctx.quadraticCurveTo(x, y, x + radius, y)
      ctx.closePath?.()
      return
    }
    if (typeof ctx.rect === 'function') {
      ctx.beginPath?.()
      ctx.rect(x, y, width, height)
      return
    }
    ctx.fillRect?.(x, y, width, height)
  }

  function wrapCanvasText(ctx, text, maxWidth) {
    if (!text) return []
    const lines = []
    let currentLine = ''
    for (const char of String(text)) {
      const testLine = currentLine + char
      if (ctx.measureText(testLine).width > maxWidth && currentLine.length > 0) {
        lines.push(currentLine)
        currentLine = char
      } else {
        currentLine = testLine
      }
    }
    if (currentLine) lines.push(currentLine)
    return lines
  }

  function generateReviewVocabularyImage(allEntries) {
    if (!allEntries || !allEntries.length) return null
    if (typeof document === 'undefined') return null

    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext?.('2d')
    if (!ctx) return null

    const dpr = 2
    const canvasWidth = 760
    const paddingX = 32

    ctx.font = '14px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
    const maxContentWidth = canvasWidth - paddingX * 2 - 68

    const itemLayouts = allEntries.map((entry, index) => {
      const term = (typeof reviewTargetTerm === 'function' ? reviewTargetTerm(entry) : null) || entry.term || entry.normalizedTerm || ''
      const parsed = parseVocabularyPayload(entry?.parsed || entry?.rawContent)
      const phonetic = [parsed?.phonetic?.uk, parsed?.phonetic?.us].filter(Boolean).join('  ') || parsed?.phonetic_text || entry.phonetic || ''
      const defs = typeof normalizeDefinitions === 'function' ? normalizeDefinitions(parsed).slice(0, 3) : []
      let defText = ''
      if (defs.length) {
        defText = defs.map((d) => (d.pos && d.pos !== 'meaning' ? `${d.pos} ${d.cn || d.en}` : (d.cn || d.en))).join('； ')
      } else {
        defText = entry.meaningText || entry.meaning_text || entry.definition || entry.meaning || '暂无释义'
      }

      ctx.font = '14px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      const wrappedDefLines = wrapCanvasText(ctx, defText, maxContentWidth)
      const contentHeight = Math.max(54, 28 + wrappedDefLines.length * 20 + 8)

      return {
        seq: `#${index + 1}`,
        term,
        phonetic,
        defLines: wrappedDefLines,
        cardHeight: contentHeight + 16,
        roundStatus: entry.roundStatus,
      }
    })

    const headerHeight = 150
    const footerHeight = 100
    const itemsGap = 12
    const totalItemsHeight = itemLayouts.reduce((acc, item) => acc + item.cardHeight + itemsGap, 0)
    const totalHeight = headerHeight + totalItemsHeight + footerHeight

    canvas.width = Math.floor(canvasWidth * dpr)
    canvas.height = Math.floor(totalHeight * dpr)
    ctx.scale(dpr, dpr)

    // Background: dark gradient
    const bgGradient = ctx.createLinearGradient(0, 0, 0, totalHeight)
    bgGradient.addColorStop(0, '#0b0f19')
    bgGradient.addColorStop(1, '#0f172a')
    ctx.fillStyle = bgGradient
    ctx.fillRect(0, 0, canvasWidth, totalHeight)

    // Header Badge
    ctx.fillStyle = '#6366f1'
    ctx.font = '600 12px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
    ctx.fillText('ENGLISH AGENT · 词汇学习助手', paddingX, 42)

    // Header Title
    ctx.fillStyle = '#ffffff'
    ctx.font = 'bold 26px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
    ctx.fillText('今日复习词单 · 睡前 / 晨读速览', paddingX, 76)

    // Header Info line
    const now = new Date()
    const dateStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
    const modeLabel = state.reviewSourceMode === 'wordbook' ? '词本抽查' : '近期查词'
    let remCount = 0
    let pendCount = 0
    allEntries.forEach((entry) => {
      if (entry.roundStatus === 'remembered') remCount += 1
      else pendCount += 1
    })
    let countInfo = `共 ${allEntries.length} 词`
    if (remCount > 0 && pendCount > 0) {
      countInfo += ` (已复习 ${remCount} · 待复习 ${pendCount})`
    } else if (remCount > 0) {
      countInfo += ` (已全部掌握)`
    }
    ctx.fillStyle = '#94a3b8'
    ctx.font = '13px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
    ctx.fillText(`日期：${dateStr}   ·   数量：${countInfo}   ·   模式：${modeLabel}`, paddingX, 106)

    // Divider
    ctx.strokeStyle = '#334155'
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(paddingX, 126)
    ctx.lineTo(canvasWidth - paddingX, 126)
    ctx.stroke()

    // Draw Items
    let currentY = headerHeight
    itemLayouts.forEach((item) => {
      const cardW = canvasWidth - paddingX * 2
      const cardH = item.cardHeight

      // Card Background
      drawRoundedRect(ctx, paddingX, currentY, cardW, cardH, 8)
      ctx.fillStyle = 'rgba(30, 41, 59, 0.7)'
      ctx.fill()
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.08)'
      ctx.stroke()

      // Seq Number
      ctx.fillStyle = '#818cf8'
      ctx.font = 'bold 13px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      ctx.fillText(item.seq, paddingX + 14, currentY + 24)

      // Term
      ctx.fillStyle = '#ffffff'
      ctx.font = 'bold 18px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      const termX = paddingX + 54
      ctx.fillText(item.term, termX, currentY + 24)

      // Phonetic (after term)
      if (item.phonetic) {
        const termWidth = ctx.measureText(item.term).width
        ctx.fillStyle = '#94a3b8'
        ctx.font = '13px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
        ctx.fillText(item.phonetic, termX + termWidth + 12, currentY + 23)
      }

      // Status Pill
      const isRem = item.roundStatus === 'remembered'
      const statusText = isRem ? '✓ 已掌握' : '待复习'
      const statusColor = isRem ? '#4ade80' : '#94a3b8'
      const statusBg = isRem ? 'rgba(34, 197, 94, 0.15)' : 'rgba(148, 163, 184, 0.12)'
      const tagW = 62
      const tagH = 20
      const tagX = paddingX + cardW - tagW - 14
      const tagY = currentY + 12
      drawRoundedRect(ctx, tagX, tagY, tagW, tagH, 4)
      ctx.fillStyle = statusBg
      ctx.fill()
      ctx.fillStyle = statusColor
      ctx.font = '600 11px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      ctx.textAlign = 'center'
      ctx.fillText(statusText, tagX + tagW / 2, tagY + 14)
      ctx.textAlign = 'left'

      // Definition lines
      ctx.fillStyle = '#cbd5e1'
      ctx.font = '14px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      item.defLines.forEach((line, lineIdx) => {
        ctx.fillText(line, termX, currentY + 48 + lineIdx * 20)
      })

      currentY += cardH + itemsGap
    })

    // Footer
    ctx.strokeStyle = '#334155'
    ctx.beginPath()
    ctx.moveTo(paddingX, currentY + 10)
    ctx.lineTo(canvasWidth - paddingX, currentY + 10)
    ctx.stroke()

    ctx.fillStyle = '#94a3b8'
    ctx.font = '13px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText('💡 睡前或晨读 5 分钟速览 · 艾宾浩斯复习计划', canvasWidth / 2, currentY + 38)

    ctx.fillStyle = '#64748b'
    ctx.font = '11px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
    ctx.fillText('Generated by English Learning Assistant · 坚持复习，聚沙成塔', canvasWidth / 2, currentY + 58)
    ctx.textAlign = 'left'

    try {
      return canvas.toDataURL('image/png')
    } catch {
      return null
    }
  }

  function exportReviewQueueImage() {
    const allEntries = (state.reviewRoundEntries && state.reviewRoundEntries.length > 0)
      ? state.reviewRoundEntries
      : (state.reviewEntries || [])

    let entriesToExport = allEntries
    if (state.reviewQueueFilter === 'pending') {
      entriesToExport = allEntries.filter((e) => e.roundStatus !== 'remembered')
    } else if (state.reviewQueueFilter === 'remembered') {
      entriesToExport = allEntries.filter((e) => e.roundStatus === 'remembered')
    }

    if (!entriesToExport.length) {
      toast?.('当前没有可导出的复习词汇')
      return
    }

    const dataUrl = generateReviewVocabularyImage(entriesToExport)
    if (!dataUrl) {
      toast?.('长图生成失败，请重试')
      return
    }

    const now = new Date()
    const dateStr = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
    const fileName = `review-words-${dateStr}.png`

    if (elements.reviewImagePreviewImg) {
      elements.reviewImagePreviewImg.src = dataUrl
    }
    if (elements.downloadReviewImageBtn) {
      elements.downloadReviewImageBtn.href = dataUrl
      elements.downloadReviewImageBtn.setAttribute('download', fileName)
    }

    try {
      const a = document.createElement('a')
      a.download = fileName
      a.href = dataUrl
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
    } catch {
      // Ignore automated click failure
    }

    if (elements.reviewImagePreviewModal) {
      showModal(elements.reviewImagePreviewModal)
    }

    toast?.('已生成复习词单长图，可长按图片保存或发送到手机')
  }

  function closeReviewImagePreviewModal() {
    if (!elements.reviewImagePreviewModal) return
    hideModal(elements.reviewImagePreviewModal)
  }

  return {
    renderReviewCompleteModal,
    closeReviewModal,
    openForgottenDetailModal,
    closeForgottenDetailModal,
    openReviewNoteModal,
    closeReviewNoteModal,
    toggleReviewNoteDrawer,
    toggleReviewNotePreview,
    saveReviewNote,
    flushReviewNoteSave,
    handleReviewNoteInput,
    handleReviewNoteCompositionStart,
    handleReviewNoteCompositionEnd,
    renderReviewQueueModal,
    openReviewQueueModal,
    closeReviewQueueModal,
    generateReviewVocabularyImage,
    exportReviewQueueImage,
    closeReviewImagePreviewModal,
  }
}
