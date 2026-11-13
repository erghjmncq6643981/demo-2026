/**
 * 知识反刍模块 - 场景材料与微环境探针视图管理
 * 负责场景材料文章归档、段落富文本发音交互、以及微环境探针作答与复习进度持久化回写。
 */

import { sameId } from '/src/shared/ids.js'
import { escapeHtml } from '/src/shared/text.js'
import { formatGenerationSourceLabel } from '/src/features/rumination/rumination-material-task.js'

export function createRuminationMaterialView(elements = {}, options = {}) {
  const {
    api = null,
    state = {},
    toast = () => {},
    speakSentence = () => {},
    onProbeIlluminated = () => {},
    isContextActive = null,
    isTaskActive = () => false,
    onMaterialChanged = () => {},
    onRegenerateMaterial = () => {},
    onPracticeSentence = () => {},
    onStartRecommendedFromProbe = () => {},
  } = options

  let savedMaterialsList = []
  let currentArticle = null
  let currentMaterialRecord = null
  let regeneratingMaterialId = null

  function sourceBadgeHtml(source) {
    const resolved = source === 'fallback' ? 'fallback' : 'ai'
    return `<span class="rumination-source-pill ${resolved === 'fallback' ? 'is-fallback' : 'is-ai'}">${formatGenerationSourceLabel(resolved)}</span>`
  }

  // 加载指定场景下沉淀的场景材料文章
  async function loadMaterials(wordbookId, scenarioCode, focusMaterialId = null) {
    if (!api?.listRuminationMaterials || !wordbookId) return
    try {
      const list = await api.listRuminationMaterials(wordbookId, scenarioCode)
      if (typeof isContextActive === 'function' && !isContextActive(wordbookId, scenarioCode)) {
        return
      }
      savedMaterialsList = Array.isArray(list) ? list : []
      renderMaterialsArchive(focusMaterialId)
    } catch (err) {
      console.warn('加载场景材料列表失败', err)
    }
  }

  function setArchiveDrawerOpen(isOpen) {
    const open = Boolean(isOpen)
    if (elements.materialsArchiveBox) {
      elements.materialsArchiveBox.classList.toggle('hidden', !open)
    }
    if (elements.toggleArchiveDrawerBtn) {
      elements.toggleArchiveDrawerBtn.setAttribute('aria-expanded', open ? 'true' : 'false')
    }
  }

  function applyRegenerateButtonsState() {
    if (!elements.materialsArchiveList) return
    const taskBusy = Boolean(typeof isTaskActive === 'function' && isTaskActive())
    const locked = Boolean(regeneratingMaterialId) || taskBusy
    elements.materialsArchiveList.querySelectorAll('.material-regenerate-btn').forEach((btn) => {
      const id = btn.getAttribute('data-material-id')
      const isCurrent = Boolean(regeneratingMaterialId && sameId(id, regeneratingMaterialId))
      btn.disabled = locked
      btn.textContent = isCurrent ? '⏳ 生成中...' : '🔄 重新生成'
      btn.title = locked
        ? '当前已有生成任务在进行中，请稍候'
        : '按原目标词覆盖重新生成当前场景材料（不扣除图谱词袋）'
    })
  }

  function setRegeneratingState(materialId, busy) {
    regeneratingMaterialId = busy && materialId != null ? String(materialId) : null
    applyRegenerateButtonsState()
  }

  // 渲染沉淀的场景材料列表
  function renderMaterialsArchive(focusMaterialId = null) {
    if (elements.materialsNavBadge) {
      elements.materialsNavBadge.textContent = `${savedMaterialsList.length} 篇`
    }
    if (elements.archiveCountBadge) {
      elements.archiveCountBadge.textContent = `${savedMaterialsList.length} 篇`
    }
    if (elements.archiveCountInfo) {
      elements.archiveCountInfo.textContent = savedMaterialsList.length > 0 ? `共 ${savedMaterialsList.length} 篇场景语境沉淀` : '暂无沉淀材料'
    }

    if (!elements.materialsArchiveList) return

    if (savedMaterialsList.length === 0) {
      currentMaterialRecord = null
      currentArticle = null
      onMaterialChanged(null)
      setArchiveDrawerOpen(true)
      if (elements.materialsEmptyState) elements.materialsEmptyState.classList.remove('hidden')
      elements.materialsArchiveList.innerHTML = ''
      if (elements.articleCard) elements.articleCard.classList.add('hidden')
      if (elements.probesCard) elements.probesCard.classList.add('hidden')
      renderProbes([])
      return
    }

    if (elements.materialsEmptyState) elements.materialsEmptyState.classList.add('hidden')
    setArchiveDrawerOpen(false)

    const activeIndex = focusMaterialId
      ? Math.max(0, savedMaterialsList.findIndex((m) => sameId(m.id, focusMaterialId)))
      : 0

    elements.materialsArchiveList.innerHTML = savedMaterialsList
      .map((item, idx) => {
        const words = Array.isArray(item.targetWords) ? item.targetWords : []
        const wordsHtml = words
          .slice(0, 8)
          .map((w) => `<span class="word-tag">${escapeHtml(w)}</span>`)
          .join('')
        const dateStr = item.createTime ? item.createTime.replace('T', ' ').slice(0, 16) : ''
        return `
        <div class="material-card-item ${idx === activeIndex ? 'active' : ''}" data-material-id="${escapeHtml(item.id)}">
          <div class="material-card-info">
            <h5>${escapeHtml(item.title)} ${sourceBadgeHtml(item.generationSource)}</h5>
            <p>${escapeHtml(item.scenarioSummary || '')}</p>
            <div class="material-card-words">
              ${wordsHtml}
              ${words.length > 8 ? `<span style="font-size: 11px; color: #64748b;">+${words.length - 8}</span>` : ''}
            </div>
          </div>
          <div class="material-card-actions">
            <span class="material-card-date">${escapeHtml(dateStr)}</span>
            <button type="button" class="secondary-button compact material-regenerate-btn" data-material-id="${escapeHtml(item.id)}" title="按原目标词覆盖重新生成当前场景材料（不扣除图谱词袋）">🔄 重新生成</button>
          </div>
        </div>
      `
      })
      .join('')

    // 默认展示选定或最新的场景材料
    if (savedMaterialsList.length > 0) {
      displayMaterial(savedMaterialsList[activeIndex])
    }

    elements.materialsArchiveList.querySelectorAll('.material-regenerate-btn').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation()
        const id = btn.getAttribute('data-material-id')
        const found = savedMaterialsList.find((m) => String(m.id) === String(id))
        if (found) {
          onRegenerateMaterial(found)
        }
      })
    })
    applyRegenerateButtonsState()

    // 绑定点击切换选定材料（选中后自动收起抽屉，保持阅读主视线）
    elements.materialsArchiveList.querySelectorAll('.material-card-item').forEach((itemEl) => {
      itemEl.addEventListener('click', () => {
        const id = itemEl.getAttribute('data-material-id')
        const found = savedMaterialsList.find((m) => String(m.id) === String(id))
        if (found) {
          elements.materialsArchiveList.querySelectorAll('.material-card-item').forEach((el) => el.classList.remove('active'))
          itemEl.classList.add('active')
          displayMaterial(found)
          setArchiveDrawerOpen(false)
        }
      })
    })
  }

  function resolveSentenceTriggerMeta(sentence) {
    const copyCount = Number(sentence?.copyCount || 0)
    if (sentence?.clozeIndependentPassed) {
      return {
        cls: 'is-consolidated',
        icon: '✓',
        title: copyCount > 0 ? `已巩固（已跟敲 ${copyCount} 次），点击可再次练习` : '已巩固，点击可再次练习',
        ariaLabel: '已巩固这句',
      }
    }
    if (sentence?.recommended) {
      return {
        cls: 'is-recommended',
        icon: '⭐',
        title: copyCount > 0 ? `推荐练习（已跟敲 ${copyCount} 次）：原句跟敲 + 目标词遮挡补全` : '推荐练习：原句跟敲 + 目标词遮挡补全',
        ariaLabel: '推荐练习这句',
      }
    }
    return {
      cls: copyCount > 0 ? 'is-practiced' : '',
      icon: '✍️',
      title: copyCount > 0 ? `已跟敲 ${copyCount} 次：点击继续练习这句` : '练习这句（原句跟敲 + 目标词遮挡补全）',
      ariaLabel: '练习这句',
    }
  }

  function renderSentenceTextHtml(sentence) {
    const text = String(sentence?.sentenceText || '')
    const spans = Array.isArray(sentence?.targetSpans)
      ? [...sentence.targetSpans]
          .filter(
            (sp) =>
              typeof sp.startOffset === 'number' &&
              typeof sp.endOffset === 'number' &&
              sp.startOffset >= 0 &&
              sp.endOffset > sp.startOffset &&
              sp.endOffset <= text.length,
          )
          .sort((a, b) => a.startOffset - b.startOffset)
      : []

    if (!spans.length) {
      return escapeHtml(text)
    }

    let out = ''
    let cursor = 0
    for (const sp of spans) {
      if (sp.startOffset < cursor) continue
      out += escapeHtml(text.slice(cursor, sp.startOffset))
      const surface = text.slice(sp.startOffset, sp.endOffset)
      out += `<span class="target-word-mark" data-word="${escapeHtml(surface)}">${escapeHtml(surface)}</span>`
      cursor = sp.endOffset
    }
    out += escapeHtml(text.slice(cursor))
    return out
  }

  function renderArticleBody(material) {
    if (!elements.articleBody || !material) return
    const sentences = Array.isArray(material.practiceSentences) ? material.practiceSentences : []

    if (sentences.length > 0) {
      const paragraphGroups = []
      let currentPIdx = null
      let currentGroup = []
      for (const s of sentences) {
        const pIdx = Number(s.paragraphIndex ?? 0)
        if (currentPIdx === null || pIdx !== currentPIdx) {
          if (currentGroup.length) paragraphGroups.push(currentGroup)
          currentPIdx = pIdx
          currentGroup = [s]
        } else {
          currentGroup.push(s)
        }
      }
      if (currentGroup.length) paragraphGroups.push(currentGroup)

      elements.articleBody.innerHTML = paragraphGroups
        .map((group) => {
          const sentenceHtml = group
            .map((s) => {
              const hasTargets = Array.isArray(s.targetSpans) && s.targetSpans.length > 0
              if (!hasTargets) {
                return `<span class="rumination-sentence-unit" data-sentence-index="${escapeHtml(s.sentenceIndex)}">${renderSentenceTextHtml(s)}</span>`
              }
              const meta = resolveSentenceTriggerMeta(s)
              return `<span class="rumination-sentence-unit has-practice" data-sentence-index="${escapeHtml(s.sentenceIndex)}">${renderSentenceTextHtml(s)}<button type="button" class="sentence-practice-trigger ${meta.cls}" data-sentence-index="${escapeHtml(s.sentenceIndex)}" title="${escapeHtml(meta.title)}" aria-label="${escapeHtml(meta.ariaLabel)}">${meta.icon}</button></span>`
            })
            .join(' ')
          return `<p>${sentenceHtml}</p>`
        })
        .join('')
    } else {
      let bodyHtml = escapeHtml(material.articleContent || '')
      const words = material.targetWords || []
      for (const word of words) {
        if (!word) continue
        const regex = new RegExp(`\\b(${word}(?:s|es|ed|ing|d)?)\\b`, 'gi')
        bodyHtml = bodyHtml.replace(regex, '<span class="target-word-mark" data-word="$1">$1</span>')
      }
      elements.articleBody.innerHTML = bodyHtml
        .split('\n\n')
        .map((p) => `<p>${p}</p>`)
        .join('')
    }

    elements.articleBody.querySelectorAll('.target-word-mark').forEach((mark) => {
      mark.addEventListener('click', () => {
        const w = mark.getAttribute('data-word')
        speakSentence(w)
        toast(`正在发音：${w}`)
      })
    })

    elements.articleBody.querySelectorAll('.sentence-practice-trigger').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation()
        const sIdx = Number(btn.getAttribute('data-sentence-index'))
        if (currentMaterialRecord && !Number.isNaN(sIdx)) {
          onPracticeSentence(currentMaterialRecord, sIdx)
        }
      })
    })
  }

  function refreshPracticeUi(updatedMaterial = null) {
    if (updatedMaterial && currentMaterialRecord && sameId(updatedMaterial.id, currentMaterialRecord.id)) {
      currentMaterialRecord.practiceSentences = updatedMaterial.practiceSentences
      currentMaterialRecord.practiceSummary = updatedMaterial.practiceSummary
      const listIdx = savedMaterialsList.findIndex((m) => sameId(m.id, updatedMaterial.id))
      if (listIdx >= 0) {
        savedMaterialsList[listIdx] = currentMaterialRecord
      }
    }
    if (!currentMaterialRecord) return
    renderArticleBody(currentMaterialRecord)
  }

  // 展示选定的场景材料文章与内联微环境探针
  function displayMaterial(material) {
    if (!material) return
    currentMaterialRecord = material
    currentArticle = {
      id: material.id,
      title: material.title,
      scenarioSummary: material.scenarioSummary,
      article: material.articleContent,
      translation: material.translationContent,
      targetWords: material.targetWords || [],
      probes: material.probes || [],
      generationSource: material.generationSource || 'ai',
      practiceSentences: material.practiceSentences || [],
      practiceSummary: material.practiceSummary || null,
    }
    onMaterialChanged(material)

    if (elements.articleCard) elements.articleCard.classList.remove('hidden')
    if (elements.probesCard) elements.probesCard.classList.remove('hidden')
    if (elements.articleTitle) elements.articleTitle.textContent = currentArticle.title

    renderArticleBody(material)

    elements.translationDetails?.removeAttribute('open')
    if (elements.translationBox) {
      elements.translationBox.innerHTML = escapeHtml(currentArticle.translation || '')
        .split('\n\n')
        .map((p) => `<p style="margin-bottom: 10px;">${p}</p>`)
        .join('')
    }

    if (elements.probesSubtitle) {
      const probeCount = Array.isArray(currentArticle.probes) ? currentArticle.probes.length : 0
      elements.probesSubtitle.textContent = `当前考查材料：《${currentArticle.title}》 · 共 ${probeCount} 题，绑定本篇语境与星系词节点，答对即点亮节点并回写复习进度`
    }

    renderProbes(currentArticle.probes || [])
  }

  // 渲染微环境探针题目
  function renderProbes(probes) {
    if (!elements.probeList) return

    if (!probes || probes.length === 0) {
      if (elements.probesEmptyState) elements.probesEmptyState.classList.remove('hidden')
      elements.probeList.innerHTML = '<div class="probes-empty-state"><p>当前材料未关联学习结果检查</p></div>'
      return
    }

    if (elements.probesEmptyState) elements.probesEmptyState.classList.add('hidden')

    elements.probeList.innerHTML = probes
      .map(
        (probe, idx) => {
          const isFallbackProbe = probe.generationSource === 'fallback'
          const sourceBadgeHtml = `<span class="probe-source-badge ${isFallbackProbe ? 'is-fallback' : 'is-ai'}">${isFallbackProbe ? '🧩 本地补题' : '🤖 AI 出题'}</span>`
          return `
      <div class="probe-item-card" data-probe-id="${escapeHtml(probe.id || String(idx))}">
        <div class="probe-topline">
          <span class="probe-badge">${escapeHtml(probe.probeTypeName || '情境辨析题')}</span>
          ${sourceBadgeHtml}
          <span class="probe-target-pill">考察核心：<strong>${escapeHtml(probe.targetWord)}</strong></span>
        </div>
        <div class="probe-question">${escapeHtml(probe.question)}</div>
        <div class="probe-hint">${escapeHtml(probe.hint || '')}</div>
        <div class="probe-options-grid">
          ${(probe.options || [])
            .map(
              (opt) => `
            <button class="probe-option-btn" type="button" data-option="${escapeHtml(opt)}">
              ${escapeHtml(opt)}
            </button>
          `
            )
            .join('')}
        </div>
        <div class="probe-explanation hidden"></div>
      </div>
    `
        }
      )
      .join('')

    // 绑定选项点击事件，若已作答则直接回显历史记录，若未作答则由服务端裁决判分并持久化
    elements.probeList.querySelectorAll('.probe-item-card').forEach((card, idx) => {
      const probeData = probes[idx]
      const explanationEl = card.querySelector('.probe-explanation')
      const optionButtons = card.querySelectorAll('.probe-option-btn')

      // 若历史数据中已完成过该探针作答，回显已答状态
      if (probeData.selectedAnswer || probeData.answeredTime) {
        const isAnswerCorrect = Boolean(probeData.isCorrect)
        optionButtons.forEach((btn) => {
          btn.disabled = true
          const optVal = btn.getAttribute('data-option')
          if (optVal === probeData.correctAnswer) {
            btn.classList.add('correct')
          } else if (optVal === probeData.selectedAnswer && !isAnswerCorrect) {
            btn.classList.add('wrong')
          }
        })
        explanationEl.className = 'probe-explanation'
        if (isAnswerCorrect) {
          explanationEl.innerHTML = `<strong>🎉 回答正确！</strong> 精准契合语境。<br/><span style="color: #38bdf8; font-size: 11px;">🌟 该词在星系图谱中已被点亮激活，熟练度 +10！</span>`
          onProbeIlluminated(probeData.targetWord)
        } else {
          explanationEl.style.borderColor = '#ef4444'
          explanationEl.style.background = 'rgba(239, 68, 68, 0.1)'
          explanationEl.style.color = '#fca5a5'
          explanationEl.innerHTML = `<strong>线索：</strong> 正确答案为【${escapeHtml(probeData.correctAnswer)}】。结合文章语境记忆效果更佳。`
        }
        return
      }

      optionButtons.forEach((btn) => {
        btn.addEventListener('click', async () => {
          const selected = btn.getAttribute('data-option')
          optionButtons.forEach((b) => (b.disabled = true))

          const currentWbId = state.currentWordbookId || document.getElementById('articleWordbookSelect')?.value
          if (!api?.submitRuminationProbe || !currentWbId) {
            return
          }

          try {
            // 提交至服务端独立核验与判分
            const submitRes = await api.submitRuminationProbe({
              wordbookId: currentWbId,
              probeId: probeData.id,
              targetWord: probeData.targetWord,
              probeIndex: idx + 1,
              selectedAnswer: selected,
              durationMillis: 1800,
            })

            const isCorrect = Boolean(submitRes?.isCorrect)
            const officialCorrectAnswer = submitRes?.correctAnswer || probeData.correctAnswer || ''
            probeData.answeredTime = new Date().toISOString()
            probeData.selectedAnswer = selected
            probeData.isCorrect = isCorrect
            probeData.correctAnswer = officialCorrectAnswer

            optionButtons.forEach((b) => {
              const optVal = b.getAttribute('data-option')
              if (optVal === officialCorrectAnswer) {
                b.classList.add('correct')
              } else if (optVal === selected && !isCorrect) {
                b.classList.add('wrong')
              }
            })

            explanationEl.className = 'probe-explanation'
            if (isCorrect) {
              const extraNote = submitRes?.alreadyAnswered ? '（此前已结算）' : '，持久化熟练度 +10！'
              explanationEl.innerHTML = `<strong>🎉 回答正确！</strong> 精准契合语境。<br/><span style="color: #38bdf8; font-size: 11px;">🌟 该词在星系图谱中已被点亮激活${extraNote}</span>`
              toast(`🎉 正确！已点亮星系中的【${probeData.targetWord}】节点`)
              onProbeIlluminated(probeData.targetWord)

              if (submitRes?.masteryScoreAfter) {
                explanationEl.innerHTML = `<strong>🎉 回答正确！</strong><br/><span style="color: #38bdf8; font-size: 11px;">🌟 已持久化同步至词本！熟练度更新为 ${submitRes.masteryScoreAfter}，复习阶段更新为 ${submitRes.reviewStageAfter}！</span>`
              }
            } else {
              explanationEl.style.borderColor = '#ef4444'
              explanationEl.style.background = 'rgba(239, 68, 68, 0.1)'
              explanationEl.style.color = '#fca5a5'
              explanationEl.innerHTML = `<strong>线索：</strong> 正确答案为【${escapeHtml(officialCorrectAnswer)}】。结合文章语境记忆效果更佳。`
              toast(`答错啦，建议查看线索强化该场景搭配`)
            }
            renderPostProbePracticeCta(probes)
          } catch (err) {
            console.error('提交学习结果检查作答失败', err)
            toast(err.message || '提交作答失败，请稍后重试')
            optionButtons.forEach((b) => (b.disabled = false))
          }
        })
      })
    })

    renderPostProbePracticeCta(probes)
  }

  function renderPostProbePracticeCta(probes) {
    if (!elements.probeList) return
    const existingCta = elements.probeList.querySelector('#probePostPracticeCta')
    if (existingCta) existingCta.remove()

    if (!Array.isArray(probes) || probes.length === 0) return
    const allAnswered = probes.every((p) => Boolean(p?.selectedAnswer || p?.answeredTime))
    if (!allAnswered || !currentMaterialRecord) return

    const sentences = Array.isArray(currentMaterialRecord.practiceSentences)
      ? currentMaterialRecord.practiceSentences
      : []
    const withTargets = sentences.filter((s) => Array.isArray(s.targetSpans) && s.targetSpans.length > 0)
    if (!withTargets.length) return

    // 若本次探针中有答错的目标词，即时提升其在本地推荐队列中的优先级，确保切换巩固时立即生效
    const wrongWords = new Set(
      probes
        .filter((p) => p && (p.selectedAnswer || p.answeredTime) && !p.isCorrect && p.targetWord)
        .map((p) => String(p.targetWord).trim().toLowerCase()),
    )
    if (wrongWords.size > 0) {
      const ranked = [...withTargets].sort((a, b) => {
        const aWrong = (a.targetSpans || []).some((sp) => wrongWords.has(String(sp.targetWord || '').toLowerCase())) ? 1 : 0
        const bWrong = (b.targetSpans || []).some((sp) => wrongWords.has(String(sp.targetWord || '').toLowerCase())) ? 1 : 0
        if (aWrong !== bWrong) return bWrong - aWrong
        return (b.recommended ? 1 : 0) - (a.recommended ? 1 : 0)
      })
      const topIndices = new Set(ranked.slice(0, 3).map((s) => s.sentenceIndex))
      sentences.forEach((s) => {
        s.recommended = topIndices.has(s.sentenceIndex)
      })
      renderArticleBody(currentMaterialRecord)
    }

    const recommendedCount = withTargets.filter((s) => s.recommended).length || Math.min(3, withTargets.length)
    const ctaEl = document.createElement('div')
    ctaEl.className = 'probe-post-practice-cta'
    ctaEl.id = 'probePostPracticeCta'
    ctaEl.innerHTML = `
      <div class="probe-post-practice-cta-text">
        <strong>🎯 理解检测已完成</strong>
        <small>趁热打铁巩固本篇推荐重点句（先原句跟敲再遮挡补全），错题关联词将优先安排。</small>
      </div>
      <button type="button" class="primary-button compact" id="startRecommendedFromProbeBtn">
        ✍️ 巩固重点句（推荐 ${recommendedCount} 句）
      </button>
    `
    ctaEl.querySelector('#startRecommendedFromProbeBtn')?.addEventListener('click', () => {
      if (currentMaterialRecord) {
        onStartRecommendedFromProbe(currentMaterialRecord)
      }
    })
    elements.probeList.appendChild(ctaEl)
  }

  // 归档抽屉展开/收起切换
  if (elements.toggleArchiveDrawerBtn) {
    elements.toggleArchiveDrawerBtn.addEventListener('click', () => {
      const currentlyOpen = elements.materialsArchiveBox && !elements.materialsArchiveBox.classList.contains('hidden')
      setArchiveDrawerOpen(!currentlyOpen)
    })
  }

  return {
    loadMaterials,
    renderMaterialsArchive,
    displayMaterial,
    renderProbes,
    refreshPracticeUi,
    setRegeneratingState,
    syncRegenerateButtons: applyRegenerateButtonsState,
    getSavedMaterialsList: () => savedMaterialsList,
    getCurrentArticle: () => currentArticle,
    getCurrentMaterialRecord: () => currentMaterialRecord,
  }
}
