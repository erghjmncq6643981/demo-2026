import { createReviewModalFeature } from '/src/features/learning/review/modal.js'
import { createReviewTypingFeature } from '/src/features/learning/review/typing.js'

export function createReviewFeature(ctx) {
  const {
    state,
    elements,
    sameId,
    syncCurrentWordbookId,
    request,
    setLoading,
    toast,
    logEvent,
    loadWordbooks,
    loadWordbookEntries,
    loadActivity,
    setView,
    renderWordbookEntries,
    confirmAction,
    renderNotes,
    renderProfileMetrics,
    openEntryTransferModal,
    formatDateTime,
    normalizeDefinitions,
    normalizeExamples,
    normalizeArray,
    renderMarkdown,
    saveEntry,
    escapeHtml,
    readText,
    stringifyValue,
    renderCollocationMini,
    speakSentence,
    speak,
    bindStudyTermCards,
    bindInlineAudio,
    reviewResultToStatus,
    statusLabel,
  } = ctx
  const feature = {}

  function getReviewLimit() {
    const rawLimit = Number(elements.reviewLimitInput?.value || 10)
    const normalizedLimit = Number.isFinite(rawLimit) ? Math.floor(rawLimit) : 10
    return Math.max(1, Math.min(100, normalizedLimit))
  }

  function applyReviewWordbookSelection(selectedWordbookId) {
    if (selectedWordbookId) {
      syncCurrentWordbookId(state, elements, selectedWordbookId)
    } else if (state.currentWordbookId) {
      syncCurrentWordbookId(state, elements, state.currentWordbookId)
    }
  }

  async function loadDueReviews() {
    const selectedWordbookId = elements.reviewWordbookSelect?.value || state.currentWordbookId || ''
    const limit = getReviewLimit()
    if (state.preview) {
      applyReviewWordbookSelection(selectedWordbookId)
      const entries = (state.previewReviewEntries.length ? state.previewReviewEntries : state.reviewEntries).slice(0, limit)
      feature.renderReviewQueue(entries)
      return entries
    }
    if (!state.token) {
      feature.renderReviewQueue([])
      return null
    }
    try {
      applyReviewWordbookSelection(selectedWordbookId)
      const params = new URLSearchParams()
      if (selectedWordbookId) params.set('wordbookId', selectedWordbookId)
      const query = params.toString() ? `?${params.toString()}` : ''
      const entries = await request(`/api/v1/learning/reviews/due${query}`)
      state.reviewEntries = (Array.isArray(entries) ? entries : []).slice(0, limit)
      feature.renderReviewQueue(state.reviewEntries)
      renderProfileMetrics()
      return state.reviewEntries
    } catch (error) {
      logEvent('error', '复习队列加载失败', error.message)
      toast(`复习队列加载失败：${error.message}`)
      return null
    }
  }

  async function restartReviewTasks() {
    const selectedWordbookId = elements.reviewWordbookSelect?.value || state.currentWordbookId || ''
    const limit = getReviewLimit()
    applyReviewWordbookSelection(selectedWordbookId)
    if (state.preview) {
      const sourceEntries = state.wordbookEntries.filter((entry) => !selectedWordbookId || sameId(entry.wordbookId, selectedWordbookId))
      const entries = sourceEntries.slice(0, limit)
      state.previewReviewEntries = entries.slice()
      feature.renderReviewQueue(entries)
      toast(entries.length ? `已重新生成 ${entries.length} 个复习任务` : '当前词书还没有可复习的单词')
      return entries
    }
    if (!state.token) {
      feature.renderReviewQueue([])
      return null
    }
    try {
      const params = new URLSearchParams()
      params.set('limit', String(limit))
      if (selectedWordbookId) params.set('wordbookId', selectedWordbookId)
      const entries = await request(`/api/v1/learning/reviews/restart?${params.toString()}`)
      state.reviewEntries = Array.isArray(entries) ? entries : []
      feature.renderReviewQueue(state.reviewEntries)
      renderProfileMetrics()
      logEvent('review', '重新生成复习任务', `共 ${state.reviewEntries.length} 个单词`)
      toast(state.reviewEntries.length ? `已重新生成 ${state.reviewEntries.length} 个复习任务` : '当前词书还没有可复习的单词')
      return state.reviewEntries
    } catch (error) {
      logEvent('error', '重新生成复习任务失败', error.message)
      toast(`重新生成复习任务失败：${error.message}`)
      return null
    }
  }

  async function startReview() {
    const dueEntries = await loadDueReviews()
    if (dueEntries === null || dueEntries.length > 0) return
    const confirmed = await confirmAction({
      title: '重新生成复习任务',
      message: '当前词书已经没有到期复习任务。是否从这个词书中重新生成一组复习任务，重新进行学习？',
      acceptText: '重新生成',
    })
    if (!confirmed) return
    await restartReviewTasks()
  }

  function openEntryInReview(entry) {
    if (!entry) return
    if (entry.wordbookId) {
      syncCurrentWordbookId(state, elements, entry.wordbookId)
    }
    const existingIndex = state.reviewEntries.findIndex((item) => sameId(item.id, entry.id))
    if (existingIndex >= 0) {
      state.currentReviewIndex = existingIndex
      state.currentReviewEntry = state.reviewEntries[existingIndex]
    } else {
      state.reviewEntries = [entry, ...state.reviewEntries.filter((item) => !sameId(item.id, entry.id))]
      state.currentReviewIndex = 0
      state.currentReviewEntry = entry
    }
    state.reviewTyped = ''
    state.reviewWrongCount = 0
    setView('reviewView', { skipReviewReload: true })
    feature.renderReviewQueue(state.reviewEntries)
    renderNotes(entry)
    toast(`已进入「${entry.term || entry.normalizedTerm}」复习`)
  }

  function renderReviewQueue(entries) {
    if (!state.token) {
      state.reviewEntries = []
      state.currentReviewIndex = 0
      state.currentReviewEntry = null
      feature.renderReviewFocus(null)
      renderNotes(null)
      feature.updateReviewProgressBadge()
      return
    }
    if (!entries.length) {
      state.reviewEntries = []
      state.currentReviewIndex = 0
      state.currentReviewEntry = null
      feature.renderReviewFocus(null)
      renderNotes(null)
      feature.updateReviewProgressBadge()
      return
    }
    state.reviewEntries = entries
    const existingIndex = state.currentReviewEntry ? entries.findIndex((entry) => sameId(entry.id, state.currentReviewEntry.id)) : -1
    state.currentReviewIndex = existingIndex >= 0 ? existingIndex : Math.min(state.currentReviewIndex, entries.length - 1)
    const selectedEntry = entries[state.currentReviewIndex] || entries[0]
    state.currentReviewEntry = selectedEntry
    feature.renderReviewFocus(selectedEntry)
    renderNotes(selectedEntry)
    feature.updateReviewProgressBadge()
  }

  function selectReviewEntry(entry) {
    if (!entry) {
      state.currentReviewEntry = null
      state.reviewTyped = ''
      state.reviewWrongCount = 0
      feature.renderReviewFocus(null)
      feature.updateReviewProgressBadge()
      return
    }
    const index = state.reviewEntries.findIndex((item) => sameId(item.id, entry.id))
    state.currentReviewIndex = index >= 0 ? index : state.currentReviewIndex
    state.currentReviewEntry = entry
    state.reviewTyped = ''
    state.reviewWrongCount = 0
    feature.renderReviewFocus(entry)
    renderNotes(entry)
    feature.updateReviewProgressBadge()
  }

  function renderReviewFocus(entryOrRecord) {
    if (!entryOrRecord) {
      elements.reviewFocus.className = 'empty'
      elements.reviewFocus.textContent = '选择词书和数量后开始复习'
      return
    }
    const parsed = entryOrRecord.parsed || state.currentRecord?.parsed || null
    const term = parsed?.term || entryOrRecord.term || entryOrRecord.normalizedTerm || state.currentRecord?.normalizedTerm || 'Ready'
    const definitions = normalizeDefinitions(parsed).slice(0, 3)
    const letters = feature.renderTypingLetters(term, state.reviewTyped)
    const progress = term ? Math.round((state.reviewTyped.length / term.length) * 100) : 0
    const total = state.reviewEntries.length
    const canPrev = total > 1 && state.currentReviewIndex > 0
    const canNext = total > 1 && state.currentReviewIndex < total - 1
    elements.reviewFocus.className = 'review-focus-card'
    elements.reviewFocus.innerHTML = `
      <div class="review-card-fixed">
        <div class="review-card-topline">
          <span>${total ? `${state.currentReviewIndex + 1} / ${total}` : '0 / 0'}</span>
        </div>
        <h4>${escapeHtml(term)}</h4>
        <p class="phonetic">${escapeHtml([parsed?.phonetic?.uk, parsed?.phonetic?.us].filter(Boolean).join('    ') || '暂无音标')}</p>
      </div>
      <div class="review-card-scroll">
        <div class="typing-board" tabindex="0" aria-label="跟敲单词 ${escapeHtml(term)}">
          <div class="typing-letters">${letters}</div>
          <div class="typing-progress"><span style="width: ${progress}%"></span></div>
          <p class="typing-hint">按键盘逐字输入，错误会提示；完成后查看例句并提交复习结果。</p>
        </div>
        <div class="mini-definition-list">
        ${
          definitions.length
            ? definitions.map((item) => `<div><span>${escapeHtml(item.pos || 'meaning')}</span><p>${escapeHtml(item.cn || item.en || '')}</p></div>`).join('')
            : '<div class="empty">暂无释义</div>'
        }
        </div>
      </div>
      <div class="review-card-actions">
        <div class="review-card-nav">
          <button class="secondary-button compact" type="button" data-review-prev ${canPrev ? '' : 'disabled'}>上一个</button>
          <button class="secondary-button compact" type="button" data-review-next ${canNext ? '' : 'disabled'}>下一个</button>
        </div>
        <div style="display: flex; gap: 8px; align-items: center;">
          <button class="secondary-button compact" type="button" data-review-note>查看笔记</button>
          <button class="icon-action-button" type="button" data-review-transfer="${escapeHtml(entryOrRecord.id)}" title="复制或移动" aria-label="复制或移动">＋</button>
        </div>
      </div>
    `
    elements.reviewFocus.querySelector('[data-review-prev]')?.addEventListener('click', () => feature.goToReviewOffset(-1))
    elements.reviewFocus.querySelector('[data-review-next]')?.addEventListener('click', () => feature.goToReviewOffset(1))
    elements.reviewFocus.querySelector('[data-review-note]')?.addEventListener('click', () => feature.openReviewNoteModal(entryOrRecord))
    elements.reviewFocus.querySelector('[data-review-transfer]')?.addEventListener('click', () => openEntryTransferModal(entryOrRecord.id))
    elements.reviewFocus.querySelector('.typing-board')?.addEventListener(
      'wheel',
      (event) => {
        event.stopPropagation()
      },
      { passive: true },
    )
    feature.updateReviewProgressBadge()
  }

  async function submitReview(entryId, result) {
    setLoading(true)
    try {
      if (state.preview) {
        const entry = state.reviewEntries.find((item) => sameId(item.id, entryId))
        const completedIndex = state.reviewEntries.findIndex((item) => sameId(item.id, entryId))
        if (entry) {
          entry.status = reviewResultToStatus(result)
          const source = state.wordbookEntries.find((item) => sameId(item.id, entryId))
          if (source) source.status = entry.status
          const previewSource = state.previewReviewEntries.find((item) => sameId(item.id, entryId))
          if (previewSource) previewSource.status = entry.status
        }
        state.reviewTyped = ''
        state.reviewWrongCount = 0
        feature.closeReviewModal({ skipRender: true })
        if (result === 'remembered' && completedIndex >= 0 && state.reviewEntries.length > 1) {
          state.currentReviewIndex = Math.min(completedIndex + 1, state.reviewEntries.length - 1)
        }
        feature.renderReviewQueue(state.reviewEntries)
        renderWordbookEntries()
        logEvent('review', '预览提交复习结果', `${entryId} -> ${result}`)
        if (result === 'forgotten') {
          feature.openForgottenDetailModal(entry || state.currentReviewEntry)
        }
        toast('设计预览：已模拟提交复习结果')
        return
      }
      const currentEntryBeforeSubmit = state.currentReviewEntry
      const currentIndexBeforeSubmit = state.currentReviewIndex
      const response = await request(`/api/v1/learning/reviews/${encodeURIComponent(entryId)}`, {
        method: 'POST',
        body: JSON.stringify({ result }),
      })
      await Promise.allSettled([loadWordbooks(), loadWordbookEntries()])
      await loadActivity()
      state.reviewTyped = ''
      state.reviewWrongCount = 0
      feature.closeReviewModal({ skipRender: true })
      if (result === 'remembered') {
        state.reviewEntries = state.reviewEntries.filter((entry) => !sameId(entry.id, entryId))
        if (state.reviewEntries.length) {
          state.currentReviewIndex = Math.min(currentIndexBeforeSubmit, state.reviewEntries.length - 1)
          feature.selectReviewEntry(state.reviewEntries[state.currentReviewIndex])
          feature.renderReviewQueue(state.reviewEntries)
        } else {
          feature.renderReviewQueue([])
        }
      } else {
        const updatedEntry = state.wordbookEntries.find((item) => sameId(item.id, entryId)) || { ...currentEntryBeforeSubmit, status: reviewResultToStatus(result) }
        state.reviewEntries = state.reviewEntries.map((entry) => (sameId(entry.id, entryId) ? { ...entry, ...updatedEntry } : entry))
        state.currentReviewEntry = updatedEntry
        feature.renderReviewFocus(updatedEntry)
        renderNotes(updatedEntry)
        feature.updateReviewProgressBadge()
        if (result === 'forgotten') {
          feature.openForgottenDetailModal(updatedEntry)
        }
      }
      logEvent('review', '提交复习结果', `${response.normalizedTerm} -> ${result}`)
      toast(`已记录复习，下次：${formatDateTime(response.nextReviewTime)}`)
    } catch (error) {
      logEvent('error', '提交复习失败', error.message)
      toast(`提交复习失败：${error.message}`)
    } finally {
      setLoading(false)
    }
  }

  const typingFeature = createReviewTypingFeature({
    state,
    elements,
    escapeHtml,
    renderReviewFocus: (...args) => feature.renderReviewFocus(...args),
    renderReviewCompleteModal: (...args) => feature.renderReviewCompleteModal(...args),
    showCelebration: (...args) => feature.showCelebration(...args),
    selectReviewEntry: (...args) => feature.selectReviewEntry(...args),
  })
  const modalFeature = createReviewModalFeature({
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
    reviewTargetTerm: (...args) => feature.reviewTargetTerm(...args),
    renderReviewFocus: (...args) => feature.renderReviewFocus(...args),
  })

  Object.assign(feature, {
    getReviewLimit,
    applyReviewWordbookSelection,
    loadDueReviews,
    restartReviewTasks,
    startReview,
    openEntryInReview,
    renderReviewQueue,
    selectReviewEntry,
    renderReviewFocus,
    submitReview,
    ...typingFeature,
    ...modalFeature,
  })
  return feature
}
