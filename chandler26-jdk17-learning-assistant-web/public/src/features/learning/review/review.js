import { createReviewModalFeature } from '/src/features/learning/review/modal.js'
import { createReviewTypingFeature } from '/src/features/learning/review/typing.js'
import { formatShortcutCombo } from '/src/shared/shortcuts.js'
import {
  LOOKUP_SOURCE_LABELS,
  formatRelativeLookupTime,
  isTodayDate,
  buildFootprintBadge,
  renderDefinitionsHtml,
  renderCognitiveAndTipsHtml,
  renderCompactExampleHtml,
} from '/src/features/learning/review/card-render.js'

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
    setFocusMode,
    bindStudyTermCards,
    bindInlineAudio,
    reviewResultToStatus,
    statusLabel,
  } = ctx
  const feature = {}
  const detailLoads = new Map()
  let cardShownAt = Date.now()
  let isSubmittingReview = false

  function getReviewSourceMode() {
    return state.reviewSourceMode === 'wordbook' ? 'wordbook' : 'recent'
  }

  function resetRoundStats(total = 0) {
    state.reviewRoundStats = {
      initialTotal: total,
      known: 0,
      vague: 0,
      forgotten: 0,
      completedCount: 0,
    }
  }

  function bindHeaderControls() {
    if (elements.reviewModeTabs && elements.reviewModeTabs.dataset.bound !== 'true') {
      elements.reviewModeTabs.dataset.bound = 'true'
      elements.reviewModeTabs.addEventListener('click', (event) => {
        const btn = event.target.closest?.('.review-mode-tab[data-review-mode]')
        if (!btn) return
        const nextMode = btn.dataset.reviewMode === 'wordbook' ? 'wordbook' : 'recent'
        if (nextMode === getReviewSourceMode()) return
        state.reviewSourceMode = nextMode
        syncHeaderModeUi()
        loadDueReviews()
      })
    }
    if (elements.refreshRecentReviewBtn && elements.refreshRecentReviewBtn.dataset.bound !== 'true') {
      elements.refreshRecentReviewBtn.dataset.bound = 'true'
      elements.refreshRecentReviewBtn.addEventListener('click', () => {
        loadRecentSearchReviews()
      })
    }
    if (elements.openReviewQueueModalBtn && elements.openReviewQueueModalBtn.dataset.bound !== 'true') {
      elements.openReviewQueueModalBtn.dataset.bound = 'true'
      elements.openReviewQueueModalBtn.addEventListener('click', () => {
        feature.openReviewQueueModal?.()
      })
    }
    if (elements.reviewProgressBadge && elements.reviewProgressBadge.dataset.bound !== 'true') {
      elements.reviewProgressBadge.dataset.bound = 'true'
      elements.reviewProgressBadge.addEventListener('click', () => {
        feature.openReviewQueueModal?.()
      })
    }
  }

  function syncHeaderModeUi() {
    const mode = getReviewSourceMode()
    elements.reviewModeTabs?.querySelectorAll('.review-mode-tab[data-review-mode]').forEach((btn) => {
      const active = btn.dataset.reviewMode === mode
      btn.classList.toggle('active', active)
      btn.setAttribute('aria-selected', active ? 'true' : 'false')
    })
    elements.reviewRecentMeta?.classList.toggle('hidden', mode !== 'recent')
    elements.reviewWordbookControls?.classList.toggle('hidden', mode !== 'wordbook')
  }

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

  function resetCardPhaseForEntry(entry, phase = 'brief') {
    cardShownAt = Date.now()
    state.reviewCardPhase = phase
    state.reviewStage = phase === 'forgot_recall' ? 'recall' : 'copy'
    state.reviewPeek = false
    state.reviewTyped = ''
    state.reviewWrongCount = 0
    if (entry) {
      state.currentReviewEntry = entry
    }
  }

  async function loadRecentSearchReviews() {
    bindHeaderControls()
    state.reviewSourceMode = 'recent'
    syncHeaderModeUi()
    if (state.preview) {
      const sourceEntries = (state.previewReviewEntries.length ? state.previewReviewEntries : state.wordbookEntries).slice(0, 8)
      const items = sourceEntries.map((entry, idx) => ({
        ...entry,
        entryId: entry.id,
        lookupSource: idx % 2 === 0 ? 'study' : 'quick_lookup',
        lookupCount: idx === 0 ? 3 : 1,
        lastLookupTime: new Date(Date.now() - idx * 3600 * 1000).toISOString(),
        detailLoaded: Boolean(entry.parsed),
      }))
      state.reviewRoundEntries = items.map((item) => ({ ...item, roundStatus: 'pending' }))
      resetRoundStats(items.length)
      feature.renderReviewQueue(items)
      return items
    }
    if (!state.token) {
      feature.renderReviewQueue([])
      return null
    }
    try {
      const res = await request('/api/v1/learning/reviews/recent-searches?days=2&minCount=5&fallbackLimit=20')
      const items = (Array.isArray(res?.items) ? res.items : []).map((item) => {
        const isReviewedToday = isTodayDate(item.lastReviewedTime)
        return {
          ...item,
          roundStatus: isReviewedToday ? 'remembered' : 'pending',
          detailLoaded: Boolean(item.parsed),
        }
      })
      const unreviewed = items.filter((item) => item.roundStatus !== 'remembered')
      state.reviewEntries = unreviewed.length > 0 ? unreviewed : items
      state.reviewRoundEntries = items
      state.currentReviewIndex = 0
      state.currentReviewEntry = null
      resetRoundStats(state.reviewEntries.length)
      feature.renderReviewQueue(state.reviewEntries)
      renderProfileMetrics?.()
      return items
    } catch (error) {
      logEvent('error', '近期查词复习队列加载失败', error.message)
      toast(`近期查词加载失败：${error.message}`)
      return null
    }
  }

  async function loadWordbookDueReviews() {
    bindHeaderControls()
    state.reviewSourceMode = 'wordbook'
    syncHeaderModeUi()
    const selectedWordbookId = elements.reviewWordbookSelect?.value || state.currentWordbookId || ''
    const limit = getReviewLimit()
    if (state.preview) {
      applyReviewWordbookSelection(selectedWordbookId)
      const entries = (state.previewReviewEntries.length ? state.previewReviewEntries : state.reviewEntries).slice(0, limit)
      state.reviewRoundEntries = entries.map((item) => ({ ...item, roundStatus: 'pending' }))
      resetRoundStats(entries.length)
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
      params.set('limit', String(limit))
      if (selectedWordbookId) params.set('wordbookId', selectedWordbookId)
      const query = params.toString() ? `?${params.toString()}` : ''
      const entries = await request(`/api/v1/learning/reviews/due${query}`)
      const rawDue = (Array.isArray(entries) ? entries : []).slice(0, limit)
      const dueItems = rawDue.map((item) => ({
        ...item,
        roundStatus: 'pending',
        detailLoaded: Boolean(item.parsed),
      }))

      let reviewedTodayItems = []
      try {
        const todayParams = new URLSearchParams()
        todayParams.set('limit', '100')
        if (selectedWordbookId) todayParams.set('wordbookId', selectedWordbookId)
        const reviewedRes = await request(`/api/v1/learning/reviews/reviewed-today?${todayParams.toString()}`)
        if (Array.isArray(reviewedRes)) {
          reviewedTodayItems = reviewedRes.map((item) => ({
            ...item,
            roundStatus: 'remembered',
            detailLoaded: Boolean(item.parsed),
          }))
        }
      } catch (err) {
        logEvent('debug', '获取今日已复习词条失败', err.message)
      }

      const dueIdSet = new Set(dueItems.map((item) => String(item.id)))
      const nonDueReviewed = reviewedTodayItems.filter((item) => !dueIdSet.has(String(item.id)))
      const combinedRound = [...dueItems, ...nonDueReviewed]

      state.reviewEntries = dueItems
      state.reviewRoundEntries = combinedRound
      state.currentReviewIndex = 0
      state.currentReviewEntry = null
      resetRoundStats(dueItems.length)
      feature.renderReviewQueue(dueItems)
      renderProfileMetrics?.()
      prefetchReviewEntriesDetails(combinedRound)
      return dueItems
    } catch (error) {
      logEvent('error', '复习队列加载失败', error.message)
      toast(`复习队列加载失败：${error.message}`)
      return null
    }
  }

  async function loadDueReviews() {
    bindHeaderControls()
    syncHeaderModeUi()
    if (getReviewSourceMode() === 'recent') {
      return loadRecentSearchReviews()
    }
    return loadWordbookDueReviews()
  }

  async function restartReviewTasks() {
    const selectedWordbookId = elements.reviewWordbookSelect?.value || state.currentWordbookId || ''
    const limit = getReviewLimit()
    applyReviewWordbookSelection(selectedWordbookId)
    if (state.preview) {
      const sourceEntries = state.wordbookEntries.filter((entry) => !selectedWordbookId || sameId(entry.wordbookId, selectedWordbookId))
      const entries = sourceEntries.slice(0, limit)
      state.previewReviewEntries = entries.slice()
      state.reviewRoundEntries = entries.map((item) => ({ ...item, roundStatus: 'pending' }))
      resetRoundStats(entries.length)
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
      const restartItems = (Array.isArray(entries) ? entries : []).map((item) => ({
        ...item,
        roundStatus: 'pending',
        detailLoaded: Boolean(item.parsed),
      }))
      const oldRemembered = (state.reviewRoundEntries || []).filter((e) => e.roundStatus === 'remembered')
      const restartIdSet = new Set(restartItems.map((e) => String(e.id)))
      const preservedRemembered = oldRemembered.filter((e) => !restartIdSet.has(String(e.id)))

      state.reviewEntries = restartItems
      state.reviewRoundEntries = [...restartItems, ...preservedRemembered]
      state.currentReviewIndex = 0
      state.currentReviewEntry = null
      resetRoundStats(state.reviewEntries.length)
      feature.renderReviewQueue(state.reviewEntries)
      renderProfileMetrics?.()
      prefetchReviewEntriesDetails(state.reviewRoundEntries)
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
    if (getReviewSourceMode() === 'recent') {
      return loadRecentSearchReviews()
    }
    const dueEntries = await loadWordbookDueReviews()
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
    resetCardPhaseForEntry(state.currentReviewEntry, 'brief')
    setView('reviewView', { skipReviewReload: true })
    feature.renderReviewQueue(state.reviewEntries)
    renderNotes?.(entry)
    feature.loadReviewEntryDetail(state.currentReviewEntry)
    toast(`已进入「${entry.term || entry.normalizedTerm}」复习`)
  }

  async function loadReviewEntryDetail(entry) {
    if (!entry || entry.detailLoaded || state.preview || !state.token) return entry
    const entryId = String(entry.entryId || (entry.wordbookId ? entry.id : '') || '')
    const term = String(entry.term || entry.normalizedTerm || '').trim()
    if (!entryId && !term) return entry
    const loadKey = entryId || `term:${term.toLowerCase()}`
    if (detailLoads.has(loadKey)) return detailLoads.get(loadKey)

    const fetcher = entryId
      ? request(`/api/v1/learning/wordbook-entries/${encodeURIComponent(entryId)}`)
      : request(`/api/v1/english/vocabularies/${encodeURIComponent(term)}`)

    const pending = fetcher
      .then((detail) => {
        if (!detail) return entry
        const resolved = entryId
          ? { ...entry, ...detail, entryId: detail.id || entry.entryId, detailLoaded: true }
          : { ...entry, parsed: detail.parsed || entry.parsed, vocabularyId: detail.id || entry.vocabularyId, detailLoaded: true }
        state.reviewEntries = state.reviewEntries.map((item) => (sameId(item.id, entry.id) ? resolved : item))
        if (state.reviewRoundEntries) {
          state.reviewRoundEntries = state.reviewRoundEntries.map((item) => (sameId(item.id, entry.id) ? { ...item, ...resolved } : item))
        }
        if (state.currentReviewEntry && sameId(state.currentReviewEntry.id, entry.id)) {
          state.currentReviewEntry = resolved
          feature.renderReviewFocus(resolved)
          renderNotes?.(resolved)
        }
        if (elements.reviewQueueModal && !elements.reviewQueueModal.classList.contains('hidden')) {
          modalFeature?.renderReviewQueueModal?.()
        }
        return resolved
      })
      .catch((error) => {
        logEvent?.('error', '复习词条详情加载失败', `${loadKey}: ${error.message}`)
        return entry
      })
      .finally(() => detailLoads.delete(loadKey))
    detailLoads.set(loadKey, pending)
    return pending
  }

  function prefetchReviewEntriesDetails(entries) {
    if (!Array.isArray(entries) || !state.token || state.preview) return
    const targets = entries.filter((e) => !e.detailLoaded && !e.parsed).slice(0, 30)
    targets.forEach((entry) => {
      loadReviewEntryDetail(entry).catch(() => {})
    })
  }

  function renderReviewQueue(entries) {
    bindHeaderControls()
    syncHeaderModeUi()
    if (elements.toggleReviewFocusModeBtn) {
      elements.toggleReviewFocusModeBtn.disabled = !entries.length && !state.reviewFocusMode
    }
    if (!state.token && !state.preview) {
      state.reviewEntries = []
      state.currentReviewIndex = 0
      state.currentReviewEntry = null
      feature.renderReviewFocus(null)
      renderNotes?.(null)
      feature.updateReviewProgressBadge()
      return
    }
    if (!entries.length) {
      state.reviewEntries = []
      state.currentReviewIndex = 0
      state.currentReviewEntry = null
      feature.renderReviewFocus(null)
      renderNotes?.(null)
      feature.updateReviewProgressBadge()
      return
    }
    state.reviewEntries = entries
    const existingIndex = state.currentReviewEntry ? entries.findIndex((entry) => sameId(entry.id, state.currentReviewEntry.id)) : -1
    state.currentReviewIndex = existingIndex >= 0 ? existingIndex : Math.min(state.currentReviewIndex, entries.length - 1)
    const selectedEntry = entries[state.currentReviewIndex] || entries[0]
    state.currentReviewEntry = selectedEntry
    if (!state.reviewCardPhase) {
      resetCardPhaseForEntry(selectedEntry, 'brief')
    }
    feature.renderReviewFocus(selectedEntry)
    renderNotes?.(selectedEntry)
    feature.updateReviewProgressBadge()
    feature.loadReviewEntryDetail(selectedEntry)
  }

  function selectReviewEntry(entry, phase = 'brief') {
    if (!entry) {
      state.currentReviewEntry = null
      resetCardPhaseForEntry(null, 'brief')
      feature.renderReviewFocus(null)
      feature.updateReviewProgressBadge()
      return
    }
    const index = state.reviewEntries.findIndex((item) => sameId(item.id, entry.id))
    state.currentReviewIndex = index >= 0 ? index : state.currentReviewIndex
    resetCardPhaseForEntry(entry, phase)
    feature.renderReviewFocus(entry)
    renderNotes?.(entry)
    feature.updateReviewProgressBadge()
    feature.loadReviewEntryDetail(entry)
  }

  function handleSelectReviewTag(tag) {
    const entry = state.currentReviewEntry
    if (!entry) return
    if (tag === 'known') {
      state.reviewCardPhase = 'known_check'
      feature.renderReviewFocus(entry)
      return
    }
    if (tag === 'vague') {
      state.reviewCardPhase = 'vague_study'
      state.reviewStage = 'copy'
      state.reviewTyped = ''
      state.reviewWrongCount = 0
      state.reviewPeek = false
      feature.renderReviewFocus(entry)
      return
    }
    if (tag === 'forgotten') {
      state.reviewCardPhase = 'forgot_study'
      state.reviewStage = 'copy'
      state.reviewTyped = ''
      state.reviewWrongCount = 0
      state.reviewPeek = false
      feature.renderReviewFocus(entry)
    }
  }

  function startGuidedCopyForCurrent() {
    const entry = state.currentReviewEntry
    if (!entry) return
    state.reviewCardPhase = 'vague_copy'
    state.reviewStage = 'copy'
    state.reviewTyped = ''
    state.reviewWrongCount = 0
    state.reviewPeek = false
    feature.renderReviewFocus(entry)
    speak?.(feature.reviewTargetTerm(entry), elements.voiceSelect?.value)
  }

  function startForgotCopyForCurrent() {
    const entry = state.currentReviewEntry
    if (!entry) return
    state.reviewCardPhase = 'forgot_copy'
    state.reviewStage = 'copy'
    state.reviewTyped = ''
    state.reviewWrongCount = 0
    state.reviewPeek = false
    feature.renderReviewFocus(entry)
    speak?.(feature.reviewTargetTerm(entry), elements.voiceSelect?.value)
  }

  function startBlindRecallForCurrent() {
    const entry = state.currentReviewEntry
    if (!entry) return
    state.reviewCardPhase = 'forgot_recall'
    state.reviewStage = 'recall'
    state.reviewTyped = ''
    state.reviewWrongCount = 0
    state.reviewPeek = false
    feature.renderReviewFocus(entry)
    speak?.(feature.reviewTargetTerm(entry), elements.voiceSelect?.value)
  }

  function renderCompletedEmptyState() {
    const stats = state.reviewRoundStats
    if (stats && stats.completedCount > 0) {
      elements.reviewFocus.className = 'review-complete-empty'
      elements.reviewFocus.innerHTML = `
        <div class="review-cleared-card">
          <div class="review-cleared-icon">🎉</div>
          <h4>本轮复习已全部清零！</h4>
          <p>本次共完成 ${stats.completedCount} 词（认识秒过 ${stats.known} · 模糊跟敲 ${stats.vague} · 盲敲巩固 ${stats.forgotten}）</p>
          <div class="review-cleared-actions">
            <button class="primary-button compact-primary" type="button" data-review-reload-recent>刷新近期查词</button>
            <button class="secondary-button compact" type="button" data-review-switch-wordbook>去词本抽查</button>
          </div>
        </div>
      `
      elements.reviewFocus.querySelector('[data-review-reload-recent]')?.addEventListener('click', () => loadRecentSearchReviews())
      elements.reviewFocus.querySelector('[data-review-switch-wordbook]')?.addEventListener('click', () => {
        state.reviewSourceMode = 'wordbook'
        syncHeaderModeUi()
        loadWordbookDueReviews()
      })
      return
    }
    elements.reviewFocus.className = 'empty'
    elements.reviewFocus.textContent = getReviewSourceMode() === 'recent'
      ? '今天和昨天还没有查词记录，去卡片学习或知识反刍查几个词吧'
      : '选择词书和数量后开始复习'
  }

  function renderReviewFocus(entryOrRecord) {
    if (!entryOrRecord) {
      renderCompletedEmptyState()
      return
    }
    const term = feature.reviewTargetTerm(entryOrRecord) || 'Ready'
    const parsed = entryOrRecord.parsed || null
    const phase = state.reviewCardPhase || 'brief'
    const isRecall = phase === 'forgot_recall' || state.reviewStage === 'recall'
    const showTyping = phase === 'vague_copy' || phase === 'forgot_copy' || phase === 'forgot_recall' || (!state.reviewCardPhase && (state.reviewStage === 'copy' || state.reviewStage === 'recall'))
    const definitions = normalizeDefinitions(parsed).slice(0, 3)
    const letters = showTyping ? feature.renderTypingLetters(term, state.reviewTyped, isRecall ? 'recall' : 'copy', state.reviewPeek) : ''
    const progress = term ? Math.round(((state.reviewTyped || '').length / term.length) * 100) : 0
    const total = state.reviewEntries.length
    const canPrev = total > 1 && state.currentReviewIndex > 0
    const canNext = total > 1 && state.currentReviewIndex < total - 1
    const maskedTerm = isRecall && !state.reviewPeek
      ? [...term].map((ch) => (ch === ' ' ? ' ' : '•')).join('')
      : term
    const phoneticText = [parsed?.phonetic?.uk, parsed?.phonetic?.us].filter(Boolean).join('    ') || '暂无音标'
    const hasWordbookEntry = Boolean(entryOrRecord.entryId || entryOrRecord.wordbookId)
    const transferId = entryOrRecord.entryId || (entryOrRecord.wordbookId ? entryOrRecord.id : '')

    let bodyHtml = ''
    if (phase === 'brief') {
      bodyHtml = `
        <div class="review-tag-grid" role="group" aria-label="选择熟悉程度">
          <button type="button" class="review-tag-card is-known" data-review-tag="known" title="看释义核对">
            <span class="review-tag-key">1</span>
            <span class="review-tag-title">认识</span>
          </button>
          <button type="button" class="review-tag-card is-vague" data-review-tag="vague" title="看释义后跟敲 1 遍">
            <span class="review-tag-key">2</span>
            <span class="review-tag-title">模糊</span>
          </button>
          <button type="button" class="review-tag-card is-forgot" data-review-tag="forgotten" title="看解析并盲敲巩固">
            <span class="review-tag-key">3</span>
            <span class="review-tag-title">没印象</span>
          </button>
        </div>
      `
    } else if (phase === 'known_check') {
      bodyHtml = `
        <div class="mini-definition-list">${renderDefinitionsHtml(definitions, escapeHtml)}</div>
        ${renderCompactExampleHtml(parsed, { escapeHtml, normalizeExamples })}
        <div class="review-phase-actions">
          <button class="primary-button" type="button" data-review-confirm-known title="确认认识并进入下一词 (Enter / Space)">下一词</button>
          <button class="secondary-button" type="button" data-review-tag="vague" title="记错了，转为模糊跟敲 (2)">记错了</button>
        </div>
      `
    } else if (phase === 'vague_study') {
      bodyHtml = `
        <div class="mini-definition-list">${renderDefinitionsHtml(definitions, escapeHtml)}</div>
        ${renderCompactExampleHtml(parsed, { escapeHtml, normalizeExamples })}
        <div class="review-phase-actions">
          <button class="primary-button" type="button" data-review-start-vague title="查看释义后开始跟敲 1 遍 (Enter / Space)">开始跟敲</button>
          <button class="secondary-button" type="button" data-review-skip-vague title="跳过跟敲，记为模糊并下一词">跳过</button>
        </div>
      `
    } else if (phase === 'vague_copy') {
      bodyHtml = `
        <div class="typing-board" tabindex="0" aria-label="跟敲单词 ${escapeHtml(term)}">
          <div class="typing-letters">${letters}</div>
          <div class="typing-progress"><span style="width: ${progress}%"></span></div>
        </div>
        <div class="mini-definition-list">${renderDefinitionsHtml(definitions, escapeHtml)}</div>
        ${renderCompactExampleHtml(parsed, { escapeHtml, normalizeExamples })}
        <div class="review-phase-actions compact">
          <button class="secondary-button compact" type="button" data-review-back-vague title="返回查看释义与例句">看解析</button>
          <button class="secondary-button compact" type="button" data-review-skip-vague title="跳过跟敲，记为模糊并下一词">跳过</button>
        </div>
      `
    } else if (phase === 'forgot_study') {
      bodyHtml = `
        <div class="mini-definition-list">${renderDefinitionsHtml(definitions, escapeHtml)}</div>
        ${renderCognitiveAndTipsHtml(parsed, { escapeHtml, normalizeArray, stringifyValue })}
        ${renderCompactExampleHtml(parsed, { escapeHtml, normalizeExamples })}
        <div class="review-phase-actions">
          <button class="primary-button" type="button" data-review-start-forgot title="查看助记后照词跟敲 1 遍 (Enter / Space)">开始跟敲</button>
          <button class="secondary-button" type="button" data-review-skip-forgot title="放队尾稍后回看">稍后回看</button>
        </div>
      `
    } else if (phase === 'forgot_copy') {
      bodyHtml = `
        <div class="typing-board" tabindex="0" aria-label="跟敲单词 ${escapeHtml(term)}">
          <div class="typing-letters">${letters}</div>
          <div class="typing-progress"><span style="width: ${progress}%"></span></div>
        </div>
        <div class="mini-definition-list">${renderDefinitionsHtml(definitions, escapeHtml)}</div>
        ${renderCognitiveAndTipsHtml(parsed, { escapeHtml, normalizeArray, stringifyValue })}
        ${renderCompactExampleHtml(parsed, { escapeHtml, normalizeExamples })}
        <div class="review-phase-actions compact">
          <button class="secondary-button compact" type="button" data-review-back-study title="返回查看助记与例句">看解析</button>
          <button class="secondary-button compact" type="button" data-review-skip-forgot title="跳过敲写，放队尾稍后回看">稍后回看</button>
        </div>
      `
    } else {
      bodyHtml = `
        <div class="typing-board" tabindex="0" aria-label="默写单词 ${escapeHtml(term)}">
          <div class="typing-letters">${letters}</div>
          <div class="typing-progress"><span style="width: ${progress}%"></span></div>
        </div>
        <div class="mini-definition-list">${renderDefinitionsHtml(definitions, escapeHtml)}</div>
        <div class="review-phase-actions compact">
          <button class="secondary-button compact" type="button" data-review-peek-btn title="偷看提示 (Tab)">提示</button>
          <button class="secondary-button compact" type="button" data-review-back-study title="返回查看释义与助记">看解析</button>
        </div>
      `
    }

    elements.reviewFocus.className = 'review-focus-card'
    elements.reviewFocus.innerHTML = `
      <div class="review-card-fixed">
        <div class="review-card-topline">
          <div class="review-meta-chips">${buildFootprintBadge(entryOrRecord, escapeHtml)}</div>
          <span class="review-card-progress-hint" data-open-review-queue title="查看复习词单全览">${total ? `${state.currentReviewIndex + 1} / ${total}` : '0 / 0'}</span>
        </div>
        <div class="review-card-word-row">
          <h4 class="${isRecall ? 'recall-masked-title' : ''}">${escapeHtml(maskedTerm)}</h4>
          <button class="mini-audio-button" type="button" data-review-audio title="播放单词发音 (R)">▶ 读音</button>
        </div>
        <p class="phonetic">${escapeHtml(phoneticText)}</p>
      </div>
      <div class="review-card-scroll">
        ${bodyHtml}
      </div>
      <div class="review-card-actions">
        <div class="review-card-nav">
          <button class="secondary-button compact" type="button" data-review-prev ${canPrev ? '' : 'disabled'}>上一个</button>
          <button class="secondary-button compact" type="button" data-review-next ${canNext ? '' : 'disabled'}>下一个</button>
          ${phase !== 'brief' ? '<button class="secondary-button compact" type="button" data-review-reset-brief title="重新选择掌握度">重选</button>' : ''}
        </div>
        <div style="display: flex; gap: 8px; align-items: center;">
          ${hasWordbookEntry
            ? `<button class="secondary-button compact" type="button" data-review-note title="复习笔记 (${escapeHtml(formatShortcutCombo('⌘E'))})">复习笔记 (${escapeHtml(formatShortcutCombo('⌘E'))})</button>
               <button class="icon-action-button" type="button" data-review-transfer="${escapeHtml(transferId)}" title="复制或移动" aria-label="复制或移动">＋</button>`
            : `<button class="secondary-button compact" type="button" data-review-add-wordbook title="保存到当前默认单词本">＋ 存入词本</button>`}
        </div>
      </div>
    `

    elements.reviewFocus.querySelector('[data-open-review-queue]')?.addEventListener('click', () => {
      feature.openReviewQueueModal?.()
    })
    elements.reviewFocus.querySelector('[data-review-audio]')?.addEventListener('click', () => {
      speak?.(term, elements.voiceSelect?.value)
    })
    elements.reviewFocus.querySelectorAll('[data-review-sentence]').forEach((btn) => {
      btn.addEventListener('click', (event) => {
        event.preventDefault()
        event.stopPropagation()
        const text = btn.getAttribute('data-sentence-text')
        if (text) {
          speakSentence?.(text)
        }
      })
    })
    elements.reviewFocus.querySelectorAll('[data-review-tag]').forEach((btn) => {
      btn.addEventListener('click', () => handleSelectReviewTag(btn.dataset.reviewTag))
    })
    elements.reviewFocus.querySelector('[data-review-confirm-known]')?.addEventListener('click', () => {
      submitCardOutcome(entryOrRecord, 'remembered', 'quick_pass')
    })
    elements.reviewFocus.querySelector('[data-review-start-vague]')?.addEventListener('click', () => {
      startGuidedCopyForCurrent()
    })
    elements.reviewFocus.querySelector('[data-review-back-vague]')?.addEventListener('click', () => {
      handleSelectReviewTag('vague')
    })
    elements.reviewFocus.querySelector('[data-review-skip-vague]')?.addEventListener('click', () => {
      submitCardOutcome(entryOrRecord, 'vague', 'guided_copy')
    })
    elements.reviewFocus.querySelector('[data-review-start-forgot]')?.addEventListener('click', () => {
      startForgotCopyForCurrent()
    })
    elements.reviewFocus.querySelector('[data-review-start-recall]')?.addEventListener('click', () => {
      startBlindRecallForCurrent()
    })
    elements.reviewFocus.querySelector('[data-review-skip-forgot]')?.addEventListener('click', () => {
      submitCardOutcome(entryOrRecord, 'forgotten', 'blind_recall')
    })
    elements.reviewFocus.querySelector('[data-review-back-study]')?.addEventListener('click', () => {
      handleSelectReviewTag('forgotten')
    })
    elements.reviewFocus.querySelector('[data-review-reset-brief]')?.addEventListener('click', () => {
      resetCardPhaseForEntry(entryOrRecord, 'brief')
      feature.renderReviewFocus(entryOrRecord)
    })
    elements.reviewFocus.querySelector('[data-review-peek-btn]')?.addEventListener('click', () => {
      feature.triggerPeek?.()
    })
    elements.reviewFocus.querySelector('[data-review-prev]')?.addEventListener('click', () => feature.goToReviewOffset(-1))
    elements.reviewFocus.querySelector('[data-review-next]')?.addEventListener('click', () => feature.goToReviewOffset(1))
    elements.reviewFocus.querySelector('[data-review-note]')?.addEventListener('click', () => feature.openReviewNoteModal(entryOrRecord))
    elements.reviewFocus.querySelector('[data-review-transfer]')?.addEventListener('click', () => openEntryTransferModal(transferId))
    elements.reviewFocus.querySelector('[data-review-add-wordbook]')?.addEventListener('click', () => quickAddToDefaultWordbook(entryOrRecord))
    elements.reviewFocus.querySelector('.typing-board')?.addEventListener('wheel', (event) => event.stopPropagation(), { passive: true })
    feature.updateReviewProgressBadge()
  }

  async function quickAddToDefaultWordbook(entry) {
    const targetWordbookId = state.currentWordbookId || state.wordbooks?.[0]?.id
    if (!targetWordbookId || !entry?.term) {
      toast('请先创建或选择个人单词本')
      return
    }
    try {
      const created = await request(`/api/v1/learning/wordbooks/${encodeURIComponent(targetWordbookId)}/entries`, {
        method: 'POST',
        body: JSON.stringify({ term: entry.term }),
      })
      const updated = { ...entry, entryId: created.id, wordbookId: created.wordbookId || targetWordbookId }
      state.reviewEntries = state.reviewEntries.map((item) => (sameId(item.id, entry.id) ? updated : item))
      if (state.currentReviewEntry && sameId(state.currentReviewEntry.id, entry.id)) {
        state.currentReviewEntry = updated
        feature.renderReviewFocus(updated)
      }
      loadWordbooks?.().catch?.(() => {})
      toast(`已将「${entry.term}」存入个人单词本`)
    } catch (error) {
      toast(`存入词本失败：${error.message}`)
    }
  }

  function advanceQueueAfterOutcome(entry, result) {
    if (!state.reviewRoundStats) resetRoundStats(state.reviewEntries.length)
    state.reviewRoundStats.completedCount += 1
    if (result === 'remembered') state.reviewRoundStats.known += 1
    else if (result === 'vague') state.reviewRoundStats.vague += 1
    else state.reviewRoundStats.forgotten += 1

    if (state.reviewRoundEntries) {
      const roundItem = state.reviewRoundEntries.find((item) => sameId(item.id, entry.id))
      if (roundItem) {
        roundItem.roundStatus = result === 'remembered' ? 'remembered' : 'requeued'
      } else {
        state.reviewRoundEntries.push({
          ...entry,
          roundStatus: result === 'remembered' ? 'remembered' : 'requeued',
        })
      }
    }

    const currentIndex = state.currentReviewIndex
    if (result === 'remembered') {
      state.reviewEntries = state.reviewEntries.filter((item) => !sameId(item.id, entry.id))
      if (state.reviewEntries.length) {
        const nextIndex = Math.min(currentIndex, state.reviewEntries.length - 1)
        selectReviewEntry(state.reviewEntries[nextIndex], 'brief')
      } else {
        selectReviewEntry(null)
        feature.renderReviewQueue([])
      }
      return
    }

    // 模糊 / 没印象：移至本轮队尾回看一次（避免原地死循环）
    const remaining = state.reviewEntries.filter((item) => !sameId(item.id, entry.id))
    const requeued = { ...entry, status: reviewResultToStatus ? reviewResultToStatus(result) : result, requeued: true }
    state.reviewEntries = [...remaining, requeued]
    const nextIndex = remaining.length ? Math.min(currentIndex, remaining.length - 1) : 0
    selectReviewEntry(state.reviewEntries[nextIndex], 'brief')
  }

  async function submitCardOutcome(entry, result, reviewAction) {
    if (!entry || isSubmittingReview) return
    isSubmittingReview = true
    const durationSeconds = Math.max(1, Math.round((Date.now() - cardShownAt) / 1000))
    setLoading?.(true)
    try {
      if (state.preview) {
        advanceQueueAfterOutcome(entry, result)
        toast(result === 'remembered' ? '已确认认识，进入下一词' : '已巩固，放入本轮队尾稍后回看')
        return
      }
      const isRecentFlow = getReviewSourceMode() === 'recent' || !entry.wordbookId
      if (isRecentFlow) {
        await request('/api/v1/learning/reviews/recent-searches/submit', {
          method: 'POST',
          body: JSON.stringify({
            lookupId: entry.lookupId || null,
            entryId: entry.entryId || (entry.wordbookId ? entry.id : null),
            term: entry.term || entry.normalizedTerm || feature.reviewTargetTerm(entry),
            result,
            reviewAction,
            durationSeconds,
          }),
        })
      } else {
        await request(`/api/v1/learning/reviews/${encodeURIComponent(entry.entryId || entry.id)}`, {
          method: 'POST',
          body: JSON.stringify({ result, assessmentType: reviewAction, durationSeconds }),
        })
      }
      loadActivity?.()?.catch?.(() => {})
      if (entry.entryId || entry.wordbookId) {
        loadWordbooks?.()?.catch?.(() => {})
      }
      advanceQueueAfterOutcome(entry, result)
      logEvent?.('review', '提交复习结果', `${entry.term || entry.normalizedTerm} -> ${result} (${reviewAction})`)
      if (result === 'remembered') {
        toast(`✓「${entry.term || entry.normalizedTerm}」已过关`)
      } else if (result === 'vague') {
        toast(`🟡「${entry.term || entry.normalizedTerm}」跟敲完成，已放队尾再巩固`)
      } else {
        toast(`🔴「${entry.term || entry.normalizedTerm}」盲敲完成，已放队尾再巩固`)
      }
    } catch (error) {
      logEvent?.('error', '提交复习失败', error.message)
      toast(`提交复习失败：${error.message}`)
    } finally {
      isSubmittingReview = false
      setLoading?.(false)
    }
  }

  async function submitReview(entryId, result) {
    const target = state.reviewEntries.find((item) => sameId(item.id, entryId)) || state.currentReviewEntry
    if (!target) return
    feature.closeReviewModal?.({ skipRender: true })
    await submitCardOutcome(target, result, result === 'remembered' ? 'quick_pass' : 'guided_copy')
  }

  function toggleReviewFocusMode(forceState = null) {
    const next = typeof forceState === 'boolean' ? forceState : !state.reviewFocusMode
    state.reviewFocusMode = next
    setFocusMode?.('reviewView', next)
    elements.reviewHeaderPanel?.classList.toggle('hidden', next)
    elements.reviewLayout?.classList.toggle('review-focus-layout', next)
    if (elements.toggleReviewFocusModeBtn) {
      elements.toggleReviewFocusModeBtn.textContent = next ? '退出专注' : '专注模式'
      elements.toggleReviewFocusModeBtn.classList.toggle('active', next)
    }
  }

  const typingFeature = createReviewTypingFeature({
    state,
    elements,
    escapeHtml,
    sameId,
    renderReviewFocus: (...args) => feature.renderReviewFocus(...args),
    renderReviewCompleteModal: (...args) => feature.renderReviewCompleteModal(...args),
    showCelebration: (...args) => feature.showCelebration(...args),
    selectReviewEntry: (...args) => feature.selectReviewEntry(...args),
    speak: (...args) => speak?.(...args),
    toast: (...args) => toast?.(...args),
    onSelectTag: (tag) => handleSelectReviewTag(tag),
    onConfirmKnown: () => submitCardOutcome(state.currentReviewEntry, 'remembered', 'quick_pass'),
    onStartGuidedCopy: () => startGuidedCopyForCurrent(),
    onStartForgotCopy: () => startForgotCopyForCurrent(),
    onStartBlindRecall: () => startBlindRecallForCurrent(),
    onGuidedCopyComplete: (entry) => submitCardOutcome(entry, 'vague', 'guided_copy'),
    onForgotCopyComplete: (entry) => startBlindRecallForCurrent(),
    onBlindRecallComplete: (entry) => submitCardOutcome(entry, 'forgotten', 'blind_recall'),
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
    selectReviewEntry: (...args) => feature.selectReviewEntry(...args),
    sameId,
  })

  Object.assign(feature, {
    getReviewLimit,
    applyReviewWordbookSelection,
    loadRecentSearchReviews,
    loadWordbookDueReviews,
    loadDueReviews,
    restartReviewTasks,
    startReview,
    openEntryInReview,
    loadReviewEntryDetail,
    renderReviewQueue,
    selectReviewEntry,
    renderReviewFocus,
    handleSelectReviewTag,
    startBlindRecallForCurrent,
    submitCardOutcome,
    submitReview,
    toggleReviewFocusMode,
    ...typingFeature,
    ...modalFeature,
  })
  return feature
}
