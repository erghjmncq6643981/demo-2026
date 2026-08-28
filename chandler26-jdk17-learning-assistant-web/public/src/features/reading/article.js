import { sameId } from '/src/shared/ids.js'
import { hideModal, showModal } from '/src/shared/modal.js'
import { escapeHtml, formatDateTime } from '/src/shared/text.js'
import { normalizeArray, normalizeDefinitions, readText, statusLabel, stringifyValue } from '/src/shared/vocabulary.js'
import { syncCurrentWordbookId } from '/src/shared/wordbook.js'
import { isRequestAbort } from '/src/shared/latest-request.js'
import { createArticleApi } from '/src/features/reading/article-api.js'
import {
  ARTICLE_WORD_LIMIT,
  articleStatusLabel,
  formatArticleErrorForLog,
  normalizeAnswerValue,
  normalizeArticleError,
  normalizeArticleStage,
  readArticleError,
  scoreArticlePractice,
} from '/src/features/reading/article-model.js'
import {
  buildPreviewArticleRecord,
  renderArticleError,
  renderBilingualArticle,
  renderGrammarPoints,
  renderSimpleList,
  renderVocabularyFocus,
} from '/src/features/reading/article-render.js'

export function createWordbookArticleFeature(ctx) {
  const {
    state,
    elements,
    request,
    setLoading,
    toast,
    logEvent,
    confirmAction,
    speakSentence,
    setFocusMode,
  } = ctx
  const api = createArticleApi(request)
  let articleTaskPollTimer = null
  let articleTaskResultLoadingId = null

  const ACTIVE_TASK_STATUSES = ['pending', 'running', 'retry_wait']
  const TERMINAL_TASK_STATUSES = ['completed', 'partial_failed', 'attention_required', 'failed', 'cancelled']

  function clearArticleTaskPoll() {
    if (articleTaskPollTimer) {
      window.clearTimeout(articleTaskPollTimer)
      articleTaskPollTimer = null
    }
  }

  function isActiveArticleTask(status) {
    return ACTIVE_TASK_STATUSES.includes(status)
  }

  function isTerminalArticleTask(status) {
    return TERMINAL_TASK_STATUSES.includes(status)
  }

  function startArticleTaskPoll(taskId) {
    clearArticleTaskPoll()
    if (state.preview || !taskId) return
    const poll = () => {
      if (!state.articleGenerationTask || !sameId(state.articleGenerationTask.id, taskId)) return
      api.getTask(taskId)
        .then((task) => {
          if (!state.articleGenerationTask || !sameId(state.articleGenerationTask.id, taskId)) return
          applyArticleTaskUpdate({ ...task, id: task.id || taskId })
          if (isActiveArticleTask(task.status)) {
            articleTaskPollTimer = window.setTimeout(poll, 4000)
          } else {
            clearArticleTaskPoll()
          }
        })
        .catch((error) => {
          if (isRequestAbort(error)) return
          logEvent('error', '精读任务状态查询失败', error.message)
          // 网络短暂失败不改变任务状态，下一轮继续查询。
          articleTaskPollTimer = window.setTimeout(poll, 6000)
        })
    }
    poll()
  }

  function applyArticleTaskUpdate(detail) {
    if (!detail || detail.taskType !== 'article_material' || !state.articleGenerationTask
      || !sameId(detail.id, state.articleGenerationTask.id)) return
    const status = detail.status || state.articleGenerationTask.status
    state.articleGenerationTask = { ...state.articleGenerationTask, ...detail, status }
    if (isActiveArticleTask(status)) {
      state.articlePreviewLoading = true
      renderArticleModalPreview(null)
      return
    }
    state.articlePreviewLoading = false
    if (status === 'completed') {
      if (!detail.businessId) {
        state.articlePreviewError = normalizeArticleError(new Error('精读任务已完成，但未关联材料记录，请在任务中心查看详情'))
        renderArticleModalPreview(null)
        toast(state.articlePreviewError.message)
        return
      }
      if (articleTaskResultLoadingId && sameId(articleTaskResultLoadingId, detail.id)) return
      if (state.articleDraftRecord && sameId(state.articleDraftRecord.id, detail.businessId)) return
      articleTaskResultLoadingId = detail.id
      state.articlePreviewLoading = true
      renderArticleModalPreview(null)
      api.getRecord(detail.businessId)
        .then((record) => {
          if (!state.articleGenerationTask || !sameId(state.articleGenerationTask.id, detail.id)) return
          state.articleDraftRecord = record
          state.articleGenerationTask = { ...state.articleGenerationTask, businessId: detail.businessId, resultLoaded: true }
          state.articlePreviewLoading = false
          renderArticleModalPreview(record)
          loadArticleHistory()
          toast('精读材料已生成，可开始学习')
        })
        .catch((error) => {
          state.articlePreviewLoading = false
          logEvent('error', '精读材料结果加载失败', error.message)
          toast(`精读材料已生成，但详情加载失败：${error.message}`)
          renderArticleModalPreview(null)
        })
        .finally(() => {
          articleTaskResultLoadingId = null
        })
      return
    }
    if (['failed', 'partial_failed', 'attention_required', 'cancelled'].includes(status)) {
      state.articlePreviewError = normalizeArticleError(new Error(detail.errorMessage || '精读材料生成任务未完成'))
      renderArticleModalPreview(null)
      toast(status === 'cancelled' ? '精读材料生成任务已取消' : '精读材料生成失败，可在任务中心重试')
    }
  }

  window.addEventListener('learning:ai-task-updated', (event) => {
    applyArticleTaskUpdate(event?.detail || {})
  })

  async function changeArticleWordbook(wordbookId) {
    syncCurrentWordbookId(state, elements, wordbookId)
    state.articleHistoryPage = 1
    state.articleWordPage = 1
    state.selectedArticleEntryIds = []
    state.currentArticleRecord = null
    state.articleDraftRecord = null
    clearArticleTaskPoll()
    articleTaskResultLoadingId = null
    state.articleGenerationTask = null
    state.articleStage = 'reading'
    state.articleAnswerSets = {}
    state.articleCheckedRecords = {}
    renderArticleResult(null)
    renderArticleModalPreview(null)
    await Promise.allSettled([loadArticleWords(), loadArticleHistory()])
    logEvent('wordbook', '切换语境精读单词本', currentWordbookName())
  }

  async function openArticleStudyModal() {
    if (!state.currentWordbookId && elements.articleWordbookSelect?.value) {
      syncCurrentWordbookId(state, elements, elements.articleWordbookSelect.value)
    }
    if (!state.currentWordbookId) {
      toast('请先选择单词本')
      return
    }
    state.articleModalOpen = true
    state.articleDraftRecord = null
    state.articlePreviewError = ''
    if (state.articleGenerationTask && isTerminalArticleTask(state.articleGenerationTask.status)) {
      state.articleGenerationTask = null
      articleTaskResultLoadingId = null
    }
    renderArticleModalPreview(null)
    showModal(elements.articleStudyModal)
    await loadArticleWords()
  }

  function closeArticleStudyModal() {
    state.articleModalOpen = false
    state.articleDraftRecord = null
    state.articlePreviewError = ''
    hideModal(elements.articleStudyModal)
  }

  function loadArticleWords() {
    if (state.preview) {
      state.articleEntries = state.wordbookEntries.slice()
      state.articleWordTotal = state.articleEntries.length
      renderArticleWords()
      return Promise.resolve()
    }
    if (!state.token || !state.currentWordbookId) {
      state.articleEntries = []
      state.articleWordTotal = 0
      state.selectedArticleEntryIds = []
      renderArticleWords()
      return Promise.resolve()
    }
    const status = elements.articleStatusFilter?.value || ''
    const keyword = state.articlePrefixFilter || elements.articlePrefixInput?.value?.trim() || ''
    return api.listEntries(state.currentWordbookId, status, state.articleWordPage || 1,
      state.articleWordPageSize || 50, keyword)
      .then((result) => {
        const entries = Array.isArray(result) ? result : result?.items
        state.articleEntries = Array.isArray(entries) ? entries : []
        state.articleWordTotal = Number(result?.total || state.articleEntries.length)
        state.articleWordPage = Number(result?.page || state.articleWordPage || 1)
        renderArticleWords()
      })
      .catch((error) => {
        if (isRequestAbort(error)) return
        logEvent('error', '语境精读词汇加载失败', error.message)
        toast(`精读词汇加载失败：${error.message}`)
      })
  }

  function loadArticleHistory() {
    if (state.preview) {
      if (!state.articleRecords.length) {
        state.articleRecords = [buildPreviewArticleRecord(state)]
        state.currentArticleRecord = state.articleRecords[0]
      }
      renderArticleHistory()
      renderArticleResult(state.currentArticleRecord)
      return Promise.resolve()
    }
    if (!state.token || !state.currentWordbookId) {
      state.articleRecords = []
      renderArticleHistory()
      renderArticleResult(null)
      return Promise.resolve()
    }
    return api.listRecords(state.currentWordbookId, state.articleHistoryPage || 1, state.articleHistoryPageSize || 10)
      .then((result) => {
        const records = Array.isArray(result) ? result : result?.items
        state.articleRecords = Array.isArray(records) ? records : []
        state.articleHistoryTotal = Number(result?.total || state.articleRecords.length)
        state.articleHistoryPage = Number(result?.page || state.articleHistoryPage || 1)
        renderArticleHistory()
        if (!state.articleRecords.length) {
          renderArticleResult(null)
        } else if (!state.currentArticleRecord) {
          renderArticleResult(state.articleRecords[0])
        }
      })
      .catch((error) => {
        if (isRequestAbort(error)) return
        logEvent('error', '语境精读历史加载失败', error.message)
      })
  }

  function changeArticleWordPage(delta) {
    const maxPage = Math.max(1, Math.ceil((state.articleWordTotal || 0) / (state.articleWordPageSize || 50)))
    const next = Math.max(1, Math.min(maxPage, (state.articleWordPage || 1) + delta))
    if (next === (state.articleWordPage || 1)) return
    state.articleWordPage = next
    loadArticleWords()
  }

  function changeArticleHistoryPage(delta) {
    const maxPage = Math.max(1, Math.ceil((state.articleHistoryTotal || 0) / (state.articleHistoryPageSize || 10)))
    const next = Math.max(1, Math.min(maxPage, (state.articleHistoryPage || 1) + delta))
    if (next === (state.articleHistoryPage || 1)) return
    state.articleHistoryPage = next
    loadArticleHistory()
  }

  function renderArticleWords() {
    if (!elements.articleWordGrid) return
    const entries = filteredArticleEntries()
    elements.articleSelectedCount.textContent = `已选 ${state.selectedArticleEntryIds.length}`
    const maxPage = Math.max(1, Math.ceil((state.articleWordTotal || entries.length) / (state.articleWordPageSize || 50)))
    if (elements.articleWordPageInfo) elements.articleWordPageInfo.textContent = `第 ${state.articleWordPage || 1} / ${maxPage} 页 · 共 ${state.articleWordTotal || entries.length} 个`
    if (elements.articleWordPrevBtn) elements.articleWordPrevBtn.disabled = (state.articleWordPage || 1) <= 1
    if (elements.articleWordNextBtn) elements.articleWordNextBtn.disabled = (state.articleWordPage || 1) >= maxPage
    if (!entries.length) {
      elements.articleWordGrid.className = 'article-word-grid empty'
      elements.articleWordGrid.textContent = state.token ? '当前筛选下暂无单词' : '登录后查看单词本词汇'
      return
    }
    elements.articleWordGrid.className = 'article-word-grid'
    elements.articleWordGrid.innerHTML = entries.map(renderArticleWordCard).join('')
    elements.articleWordGrid.querySelectorAll('[data-article-entry-id]').forEach((button) => {
      button.addEventListener('click', () => toggleArticleEntry(button.getAttribute('data-article-entry-id')))
    })
  }

  function filteredArticleEntries() {
    const prefix = String(state.articlePrefixFilter || elements.articlePrefixInput?.value || '').trim().toLowerCase()
    const status = elements.articleStatusFilter?.value || ''
    const statusFiltered = status ? state.articleEntries.filter((entry) => (entry.status || 'vague') === status) : state.articleEntries
    return prefix ? statusFiltered.filter((entry) => entryMatchesFilter(entry, prefix)) : statusFiltered
  }

  function renderArticleWordCard(entry) {
    const selected = state.selectedArticleEntryIds.some((id) => sameId(id, entry.id))
    const definition = normalizeDefinitions(entry.parsed || {})[0] || {}
    const meaning = definition.cn || definition.en || '暂无核心含义'
    const pos = definition.pos || 'meaning'
    return `
      <button class="article-word-card ${selected ? 'selected' : ''}" type="button" data-article-entry-id="${escapeHtml(entry.id)}" aria-pressed="${selected}">
        <span class="article-word-topline">
          <strong>${escapeHtml(entry.term || entry.normalizedTerm)}</strong>
          <small>${escapeHtml(statusLabel(entry.status))}</small>
        </span>
        <p>${escapeHtml(pos)} · ${escapeHtml(meaning)}</p>
        <span class="selection-dot" aria-hidden="true"></span>
      </button>
    `
  }

  function toggleArticleEntry(entryId) {
    const exists = state.selectedArticleEntryIds.some((id) => sameId(id, entryId))
    if (exists) {
      state.selectedArticleEntryIds = state.selectedArticleEntryIds.filter((id) => !sameId(id, entryId))
    } else if (state.selectedArticleEntryIds.length >= ARTICLE_WORD_LIMIT) {
      toast(`一次最多选择 ${ARTICLE_WORD_LIMIT} 个单词`)
      return
    } else {
      state.selectedArticleEntryIds.push(entryId)
    }
    state.articleDraftRecord = null
    state.articlePreviewError = ''
    renderArticleWords()
    renderArticleModalPreview(null)
  }

  function clearArticleSelection() {
    state.selectedArticleEntryIds = []
    state.articleDraftRecord = null
    state.articlePreviewError = ''
    renderArticleWords()
    renderArticleModalPreview(null)
  }

  function recommendArticleWords() {
    const priority = { forgotten: 0, vague: 1, familiar: 2 }
    const candidates = [...filteredArticleEntries()]
      .sort((left, right) => {
        const statusDelta = (priority[left.status] ?? 1) - (priority[right.status] ?? 1)
        if (statusDelta !== 0) return statusDelta
        return Number(left.masteryScore || 0) - Number(right.masteryScore || 0)
      })
      .slice(0, Math.min(8, ARTICLE_WORD_LIMIT))
    state.selectedArticleEntryIds = candidates.map((entry) => entry.id)
    state.articleDraftRecord = null
    state.articlePreviewError = ''
    renderArticleWords()
    renderArticleModalPreview(null)
    toast(candidates.length ? `已推荐 ${candidates.length} 个优先学习词` : '当前筛选下没有可推荐词汇')
  }

  async function generateArticlePreview(options = {}) {
    if (!state.selectedArticleEntryIds.length) {
      toast('请先勾选要学习的词汇')
      return
    }
    if (!state.currentWordbookId) {
      toast('请先选择单词本')
      return
    }
    const forceRefresh = Boolean(options.forceRefresh)
    if (forceRefresh) {
      const confirmed = await confirmAction({
        title: '重新生成精读材料',
        message: '确认重新调用 AI 生成一份新材料？已有精读记录仍会保留。',
        acceptText: '确认生成',
      })
      if (!confirmed) return
    }
    const payload = {
      wordbookId: state.currentWordbookId,
      entryIds: state.selectedArticleEntryIds,
      wordCountRange: elements.articleWordCountSelect.value,
      difficulty: elements.articleDifficultySelect.value,
      remark: elements.articleRemarkInput.value.trim(),
      modelConfigId: elements.articleModelSelect?.value || null,
      forceRefresh,
    }
    setLoading(true)
    state.articlePreviewLoading = true
    state.articlePreviewError = ''
    updateArticlePreviewControls()
    try {
      if (state.preview) {
        const record = buildPreviewArticleRecord(state, payload)
        record.cacheHit = !forceRefresh
        state.articleDraftRecord = record
        renderArticleModalPreview(record)
        toast(forceRefresh ? '设计预览：已模拟重新生成文章' : '设计预览：已生成文章预览')
        return
      }
      const task = await api.createStudyAsync(payload)
      state.articleGenerationTask = task
      articleTaskResultLoadingId = null
      state.articlePreviewLoading = false
      renderArticleModalPreview(null)
      startArticleTaskPoll(task?.id)
      logEvent('ai', '提交语境精读材料任务', selectedWordsText({
        selectedWords: state.articleEntries.filter((entry) => state.selectedArticleEntryIds.some((id) => sameId(id, entry.id))),
      }))
      toast(task?.status === 'completed' ? '精读材料任务已完成' : '精读材料生成任务已提交，可在任务中心查看进度')
    } catch (error) {
      state.articleDraftRecord = null
      state.articlePreviewError = normalizeArticleError(error)
      logEvent('error', '精读材料生成失败', formatArticleErrorForLog(state.articlePreviewError))
      toast(`精读材料生成失败：${state.articlePreviewError.message}`)
      renderArticleModalPreview(null)
    } finally {
      state.articlePreviewLoading = false
      updateArticlePreviewControls()
      setLoading(false)
    }
  }

  async function saveArticleStudy() {
    if (!state.articleDraftRecord) {
      if (state.articleGenerationTask && !state.articleGenerationTask.businessId) {
        toast('精读材料正在生成，请等待任务完成')
        return
      }
      toast('请先生成文章预览')
      return
    }
    let record = state.articleDraftRecord
    try {
      if (state.preview) {
        record = { ...record, studyStatus: 'in_progress', currentStage: 'reading', startedTime: new Date().toISOString() }
      } else {
        record = await api.updateProgress(record.id, { stage: 'reading' })
      }
      replaceArticleRecord(record)
      state.articleStage = 'reading'
      renderArticleResult(record)
      renderArticleHistory()
      const title = readText(record.parsed, ['title']) || selectedWordsText(record) || '语境精读'
      logEvent('article', '开始语境精读', title)
      closeArticleStudyModal()
      toast('已进入语境精读')
    } catch (error) {
      logEvent('error', '开始语境精读失败', error.message)
      toast(`开始精读失败：${error.message}`)
    }
  }

  function generateArticle(options = {}) {
    return generateArticlePreview(options)
  }

  async function openArticleRecord(recordId) {
    let record = state.articleRecords.find((item) => sameId(item.id, recordId))
    try {
      if (!state.preview && !record?.parsed) {
        record = await api.getRecord(recordId)
      }
      if (!record) return
      const stage = record.studyStatus === 'completed' ? 'check' : normalizeArticleStage(record.currentStage)
      if (!state.preview && record.studyStatus !== 'completed') {
        record = await persistArticleStage(record, stage)
      } else if (state.preview && record.studyStatus !== 'completed') {
        record = { ...record, studyStatus: 'in_progress', currentStage: stage, startedTime: record.startedTime || new Date().toISOString() }
        replaceArticleRecord(record)
      }
      state.articleStage = stage
      renderArticleResult(record)
      renderArticleHistory()
    } catch (error) {
      logEvent('error', '精读记录打开失败', error.message)
      toast(`精读记录打开失败：${error.message}`)
    }
  }

  function renderArticleHistory() {
    if (!elements.articleHistoryList) return
    const records = state.articleRecords || []
    if (!records.length) {
      elements.articleHistoryList.className = 'article-history-list empty'
      elements.articleHistoryList.textContent = '暂无精读记录'
      elements.articleHistoryPageInfo.textContent = '第 1 / 1 页'
      elements.articleHistoryPrevBtn.disabled = true
      elements.articleHistoryNextBtn.disabled = true
      return
    }
    elements.articleHistoryList.className = 'article-history-list'
    const maxPage = Math.max(1, Math.ceil((state.articleHistoryTotal || records.length) / (state.articleHistoryPageSize || 10)))
    elements.articleHistoryPageInfo.textContent = `第 ${state.articleHistoryPage || 1} / ${maxPage} 页`
    elements.articleHistoryPrevBtn.disabled = (state.articleHistoryPage || 1) <= 1
    elements.articleHistoryNextBtn.disabled = (state.articleHistoryPage || 1) >= maxPage
    elements.articleHistoryList.innerHTML = records
      .map((record) => {
        const title = readText(record.parsed, ['title']) || selectedWordsText(record) || '语境精读'
        const time = formatDateTime(record.createTime || record.createdAt || record.updateTime)
        return `
          <button class="article-history-item ${state.currentArticleRecord && sameId(state.currentArticleRecord.id, record.id) ? 'active' : ''}" type="button" data-article-record="${escapeHtml(record.id)}">
            <span class="article-history-title"><strong>${escapeHtml(title)}</strong></span>
            <small class="article-history-time">${escapeHtml(time)}</small>
          </button>
        `
      })
      .join('')
    elements.articleHistoryList.querySelectorAll('[data-article-record]').forEach((button) => {
      button.addEventListener('click', () => openArticleRecord(button.getAttribute('data-article-record')))
    })
  }

  function renderArticleResult(record) {
    const previousRecordId = state.currentArticleRecord?.id
    state.currentArticleRecord = record || null
    if (!elements.articleResult) return
    if (!record) {
      elements.articleResultBadge.textContent = '等待生成'
      elements.articleResult.className = 'article-result empty'
      elements.articleResult.textContent = '选择一份精读材料开始学习'
      return
    }
    if (!sameId(previousRecordId, record.id)) {
      state.articleStage = record.studyStatus === 'completed' ? 'check' : normalizeArticleStage(record.currentStage)
    }
    renderArticleContent(elements.articleResult, elements.articleResultBadge, record)
    renderArticleHistory()
  }

  function renderArticleModalPreview(record) {
    if (!elements.articleModalPreview) return
    if (!record) {
      const articleError = readArticleError(state.articlePreviewError)
      const errorMessage = articleError?.message || ''
      const taskStatus = state.articleGenerationTask?.status
      elements.articleModalPreviewBadge.textContent = errorMessage
        ? '生成失败'
        : state.articlePreviewLoading || ['pending', 'running', 'retry_wait'].includes(taskStatus)
          ? '任务生成中'
          : '等待生成'
      elements.articleModalPreview.className = errorMessage ? 'article-result empty article-result-error' : 'article-result empty'
      if (articleError) {
        elements.articleModalPreview.innerHTML = renderArticleError(articleError)
      } else {
        elements.articleModalPreview.textContent = taskStatus && ['pending', 'running', 'retry_wait'].includes(taskStatus)
          ? '精读材料正在生成，完成后会自动加载。你也可以在任务中心查看进度。'
          : '选择目标词并生成精读材料'
      }
      updateArticlePreviewControls()
      return
    }
    renderArticleContent(elements.articleModalPreview, elements.articleModalPreviewBadge, record, { compact: true })
    updateArticlePreviewControls()
  }

  function updateArticlePreviewControls() {
    if (elements.articlePreviewGenerateBtn) {
      const taskPending = ['pending', 'running', 'retry_wait'].includes(state.articleGenerationTask?.status)
      elements.articlePreviewGenerateBtn.disabled = state.articlePreviewLoading || taskPending
      elements.articlePreviewGenerateBtn.textContent = state.articlePreviewLoading || taskPending ? '任务生成中...' : '生成学习材料'
    }
    if (elements.saveArticleStudyBtn) {
      elements.saveArticleStudyBtn.disabled = state.articlePreviewLoading || !state.articleDraftRecord
    }
  }

  function renderArticleContent(container, badge, record, options = {}) {
    const parsed = record.parsed || {}
    const title = readText(parsed, ['title']) || '语境精读'
    const article = readText(parsed, ['article', 'text', 'content'])
    const translation = readText(parsed, ['translation', 'translation_cn', 'translationCn', 'cn', 'zh'])
    const vocabularyFocus = normalizeArray(parsed.vocabulary_focus || parsed.vocabularyFocus || parsed.words || [])
    const grammarPoints = normalizeArray(parsed.grammar_points || parsed.grammarPoints || [])
    const keyPoints = normalizeArray(parsed.key_points || parsed.keyPoints || [])
    const tips = normalizeArray(parsed.study_tips || parsed.studyTips || parsed.tips || [])

    badge.textContent = `${articleStatusLabel(record.studyStatus)} · ${wordCountLabel(record.wordCountRange)} · ${difficultyLabel(record.difficulty)}`
    container.className = `article-result${options.compact ? ' article-result-preview' : ''}`
    if (options.compact) {
      container.innerHTML = renderArticlePreview(record, title, article, translation, vocabularyFocus)
      container.querySelector('[data-article-speak]')?.addEventListener('click', () => speakSentence(article))
      return
    }
    const stage = record.studyStatus === 'completed' ? state.articleStage : normalizeArticleStage(state.articleStage)
    container.innerHTML = `
      <article class="article-learning-card">
        <header class="article-learning-head">
          <div>
            <p class="eyebrow">${escapeHtml(currentWordbookName(record.wordbookId))}</p>
            <h4>${escapeHtml(title)}</h4>
            <div class="article-target-summary">${renderTargetWordChips(record.selectedWords)}</div>
          </div>
          <button class="icon-button" type="button" data-article-speak title="朗读英文文章" aria-label="朗读英文文章">▶</button>
        </header>
        ${renderArticleStageNav(stage, record)}
        <div class="article-stage-body">
          ${stage === 'vocabulary'
            ? renderVocabularyStage(record, vocabularyFocus, grammarPoints, keyPoints, tips)
            : stage === 'check'
              ? renderCheckStage(record)
              : renderReadingStage(record, article, translation)}
        </div>
      </article>
    `
    bindArticleLearningEvents(container, record, article)
  }

  function renderArticlePreview(record, title, article, translation, vocabularyFocus) {
    return `
      <article class="article-learning-card article-learning-card-preview">
        <header class="article-learning-head">
          <div>
            <p class="eyebrow">${escapeHtml(currentWordbookName(record.wordbookId))}</p>
            <h4>${escapeHtml(title)}</h4>
            <div class="article-target-summary">${renderTargetWordChips(record.selectedWords)}</div>
          </div>
          <button class="icon-button" type="button" data-article-speak title="朗读英文文章" aria-label="朗读英文文章">▶</button>
        </header>
        <section class="article-section">
          <h5>双语正文</h5>
          ${renderBilingualArticle(article, translation, { showTranslation: true, selectedWords: record.selectedWords })}
        </section>
        ${renderVocabularyFocus(vocabularyFocus)}
      </article>
    `
  }

  function renderArticleStageNav(stage, record) {
    const stages = [
      { code: 'reading', number: 1, label: '通读文章' },
      { code: 'vocabulary', number: 2, label: '词汇精讲' },
      { code: 'check', number: 3, label: '阅读检测' },
    ]
    const activeIndex = Math.max(0, stages.findIndex((item) => item.code === stage))
    const progress = record.studyStatus === 'completed' ? 100 : ((activeIndex + 1) / stages.length) * 100
    return `
      <div class="article-stage-shell">
        <nav class="article-stage-nav" aria-label="精读阶段">
          ${stages.map((item, index) => `
            <button class="article-stage-tab ${item.code === stage ? 'active' : ''} ${index < activeIndex || record.studyStatus === 'completed' ? 'done' : ''}"
              type="button" data-article-stage="${item.code}" aria-current="${item.code === stage ? 'step' : 'false'}">
              <span>${item.number}</span><strong>${item.label}</strong>
            </button>
          `).join('')}
        </nav>
        <div class="article-stage-progress" aria-hidden="true"><span style="width:${progress}%"></span></div>
      </div>
    `
  }

  function renderReadingStage(record, article, translation) {
    const showTranslation = state.articleReadingMode === 'bilingual'
    return `
      <section class="article-stage-section article-reading-stage">
        <div class="article-stage-toolbar">
          <h5>英文原文</h5>
          <div class="article-mode-switch" role="group" aria-label="译文显示方式">
            <button type="button" class="${showTranslation ? '' : 'active'}" data-article-reading-mode="english">仅英文</button>
            <button type="button" class="${showTranslation ? 'active' : ''}" data-article-reading-mode="bilingual">双语</button>
          </div>
        </div>
        ${renderBilingualArticle(article, translation, { showTranslation, selectedWords: record.selectedWords })}
        <div class="article-stage-actions">
          <button class="primary-button" type="button" data-article-next-stage="vocabulary">进入词汇精讲</button>
        </div>
      </section>
    `
  }

  function renderVocabularyStage(record, vocabularyFocus, grammarPoints, keyPoints, tips) {
    return `
      <section class="article-stage-section article-vocabulary-stage">
        ${renderVocabularyFocus(vocabularyFocus, record.selectedWords)}
        ${renderGrammarPoints(grammarPoints)}
        ${renderSimpleList('阅读要点', keyPoints)}
        ${renderSimpleList('复习建议', tips)}
        <div class="article-stage-actions article-stage-actions-split">
          <button class="secondary-button" type="button" data-article-next-stage="reading">返回通读</button>
          <button class="primary-button" type="button" data-article-next-stage="check">开始阅读检测</button>
        </div>
      </section>
    `
  }

  function renderCheckStage(record) {
    const parsed = record.parsed || {}
    const practice = normalizeArray(parsed.practice || parsed.questions || [])
    if (!practice.length) {
      return '<section class="article-stage-section"><div class="empty">当前材料没有可用的阅读检测</div></section>'
    }
    const answers = articleAnswers(record)
    const completed = record.studyStatus === 'completed'
    const checked = completed || Boolean(state.articleCheckedRecords[articleRecordKey(record)])
    const result = completed
      ? {
          total: Number(record.practiceTotal || practice.length),
          correct: Number(record.practiceCorrect || 0),
          score: Number(record.practiceScore || 0),
        }
      : scoreArticlePractice(practice, answers)
    const allAnswered = practice.every((_, index) => String(answers[index] || '').trim())
    return `
      <section class="article-stage-section article-check-stage">
        <div class="article-check-heading">
          <div><h5>阅读检测</h5><span>${practice.length} 题</span></div>
          ${checked ? `<strong>${result.score} 分</strong>` : `<strong>${answers.filter((answer) => String(answer || '').trim()).length}/${practice.length}</strong>`}
        </div>
        <div class="article-practice-list">
          ${practice.map((item, index) => renderPracticeQuestion(item, index, answers[index], checked, completed)).join('')}
        </div>
        ${checked ? renderPracticeResult(result, completed) : ''}
        <div class="article-stage-actions article-stage-actions-split">
          <button class="secondary-button" type="button" data-article-next-stage="vocabulary">返回词汇精讲</button>
          ${completed
            ? '<button class="primary-button" type="button" data-article-next-stage="reading">再次阅读</button>'
            : checked
              ? '<span class="article-check-commands"><button class="secondary-button" type="button" data-article-reset-check>重新作答</button><button class="primary-button" type="button" data-article-complete>完成本次精读</button></span>'
              : `<button class="primary-button" type="button" data-article-check ${allAnswered ? '' : 'disabled'}>检查答案</button>`}
        </div>
      </section>
    `
  }

  function renderPracticeQuestion(item, index, selectedAnswer, checked, completed) {
    const question = readText(item, ['question', 'stem']) || `问题 ${index + 1}`
    const options = normalizeArray(item?.options || [])
    const correctAnswer = readText(item, ['correct_answer', 'correctAnswer', 'answer'])
    const explanation = readText(item, ['explanation', 'analysis'])
    const selected = String(selectedAnswer || '')
    const correct = normalizeAnswerValue(selected) === normalizeAnswerValue(correctAnswer)
    const questionState = checked && !completed ? (correct ? 'correct' : 'incorrect') : ''
    return `
      <article class="article-practice-question ${questionState}">
        <header><span>${index + 1}</span><strong>${escapeHtml(question)}</strong></header>
        ${options.length
          ? `<div class="article-practice-options">
              ${options.map((option) => {
                const value = stringifyValue(option)
                const optionSelected = normalizeAnswerValue(value) === normalizeAnswerValue(selected)
                const optionCorrect = normalizeAnswerValue(value) === normalizeAnswerValue(correctAnswer)
                const classes = [optionSelected ? 'selected' : '', checked && optionCorrect ? 'correct' : '', checked && optionSelected && !optionCorrect ? 'incorrect' : ''].filter(Boolean).join(' ')
                return `<button type="button" class="${classes}" data-article-answer-index="${index}" data-article-answer="${escapeHtml(value)}" ${checked ? 'disabled' : ''}>${escapeHtml(value)}</button>`
              }).join('')}
            </div>`
          : `<label class="article-free-answer"><span class="sr-only">第 ${index + 1} 题答案</span><input data-article-free-answer="${index}" value="${escapeHtml(selected)}" placeholder="输入答案" ${checked ? 'disabled' : ''} /></label>`}
        ${checked ? `<div class="article-answer-feedback"><strong>${correct || completed ? '正确答案' : '本题未答对'}：${escapeHtml(correctAnswer || '暂无')}</strong>${explanation ? `<p>${escapeHtml(explanation)}</p>` : ''}</div>` : ''}
      </article>
    `
  }

  function renderPracticeResult(result, completed) {
    return `
      <div class="article-check-result ${result.score >= 60 ? 'passed' : 'needs-review'}">
        <strong>${completed ? '本次精读已完成' : result.score >= 60 ? '检测通过' : '建议回看文章'}</strong>
        <span>${result.correct}/${result.total} 题正确 · ${result.score} 分</span>
      </div>
    `
  }

  function renderTargetWordChips(words) {
    const values = Array.isArray(words) ? words : []
    return values.length
      ? values.map((item) => `<span>${escapeHtml(item.term || item.normalizedTerm)}</span>`).join('')
      : '<span>暂无目标词</span>'
  }

  function bindArticleLearningEvents(container, record, article) {
    container.querySelector('[data-article-speak]')?.addEventListener('click', () => speakSentence(article))
    container.querySelectorAll('[data-article-stage], [data-article-next-stage]').forEach((button) => {
      button.addEventListener('click', () => changeArticleStage(button.dataset.articleStage || button.dataset.articleNextStage))
    })
    container.querySelectorAll('[data-article-reading-mode]').forEach((button) => {
      button.addEventListener('click', () => {
        state.articleReadingMode = button.dataset.articleReadingMode
        renderArticleResult(record)
      })
    })
    container.querySelectorAll('[data-article-target]').forEach((button) => {
      button.addEventListener('click', async () => {
        const term = button.dataset.articleTarget
        await changeArticleStage('vocabulary')
        elements.articleResult?.querySelector(`[data-article-focus-word="${cssEscape(term)}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      })
    })
    container.querySelectorAll('[data-article-answer-index]').forEach((button) => {
      button.addEventListener('click', () => {
        setArticleAnswer(record, Number(button.dataset.articleAnswerIndex), button.dataset.articleAnswer)
        renderArticleResult(record)
      })
    })
    container.querySelectorAll('[data-article-free-answer]').forEach((input) => {
      input.addEventListener('input', () => {
        setArticleAnswer(record, Number(input.dataset.articleFreeAnswer), input.value)
        const practice = normalizeArray(record.parsed?.practice || record.parsed?.questions || [])
        const allAnswered = practice.every((_, index) => String(articleAnswers(record)[index] || '').trim())
        container.querySelector('[data-article-check]')?.toggleAttribute('disabled', !allAnswered)
      })
    })
    container.querySelector('[data-article-check]')?.addEventListener('click', () => {
      state.articleCheckedRecords[articleRecordKey(record)] = true
      renderArticleResult(record)
    })
    container.querySelector('[data-article-reset-check]')?.addEventListener('click', () => {
      state.articleCheckedRecords[articleRecordKey(record)] = false
      state.articleAnswerSets[articleRecordKey(record)] = []
      renderArticleResult(record)
    })
    container.querySelector('[data-article-complete]')?.addEventListener('click', completeArticleStudy)
  }

  async function changeArticleStage(stage) {
    const normalized = normalizeArticleStage(stage)
    let record = state.currentArticleRecord
    if (!record || normalized === state.articleStage) return
    try {
      if (state.preview) {
        record = record.studyStatus === 'completed'
          ? record
          : { ...record, studyStatus: 'in_progress', currentStage: normalized, startedTime: record.startedTime || new Date().toISOString() }
        replaceArticleRecord(record)
      } else if (record.studyStatus !== 'completed') {
        record = await persistArticleStage(record, normalized)
      }
      state.articleStage = normalized
      renderArticleResult(record)
    } catch (error) {
      logEvent('error', '精读阶段切换失败', error.message)
      toast(`切换学习阶段失败：${error.message}`)
    }
  }

  async function completeArticleStudy() {
    const record = state.currentArticleRecord
    if (!record) return
    const practice = normalizeArray(record.parsed?.practice || record.parsed?.questions || [])
    const answers = articleAnswers(record)
    const payload = {
      answers: practice.map((_, questionIndex) => ({ questionIndex, answer: String(answers[questionIndex] || '').trim() })),
    }
    try {
      let completed
      if (state.preview) {
        const result = scoreArticlePractice(practice, answers)
        completed = {
          ...record,
          studyStatus: 'completed',
          currentStage: 'completed',
          practiceTotal: result.total,
          practiceCorrect: result.correct,
          practiceScore: result.score,
          completedTime: new Date().toISOString(),
          updateTime: new Date().toISOString(),
        }
      } else {
        completed = await api.complete(record.id, payload)
      }
      replaceArticleRecord(completed)
      state.articleStage = 'check'
      state.articleCheckedRecords[articleRecordKey(completed)] = true
      renderArticleResult(completed)
      renderArticleHistory()
      logEvent('article', '完成语境精读', `${readText(completed.parsed, ['title']) || '精读材料'} · ${completed.practiceScore || 0} 分`)
      toast(`精读完成，阅读检测 ${completed.practiceScore || 0} 分`)
    } catch (error) {
      logEvent('error', '完成语境精读失败', error.message)
      toast(`完成精读失败：${error.message}`)
    }
  }

  async function persistArticleStage(record, stage) {
    const updated = await api.updateProgress(record.id, { stage })
    replaceArticleRecord(updated)
    return updated
  }

  function replaceArticleRecord(record) {
    if (!record) return
    state.articleRecords = [record, ...state.articleRecords.filter((item) => !sameId(item.id, record.id))]
    if (state.articleDraftRecord && sameId(state.articleDraftRecord.id, record.id)) state.articleDraftRecord = record
    if (state.currentArticleRecord && sameId(state.currentArticleRecord.id, record.id)) state.currentArticleRecord = record
  }

  function articleAnswers(record) {
    return state.articleAnswerSets[articleRecordKey(record)] || []
  }

  function setArticleAnswer(record, index, value) {
    const key = articleRecordKey(record)
    const answers = [...(state.articleAnswerSets[key] || [])]
    answers[index] = value
    state.articleAnswerSets[key] = answers
    state.articleCheckedRecords[key] = false
  }

  function articleRecordKey(record) {
    return String(record?.id || 'draft')
  }

  function cssEscape(value) {
    return window.CSS?.escape ? window.CSS.escape(String(value || '')) : String(value || '').replace(/["\\]/g, '\\$&')
  }

  function entryMatchesFilter(entry, prefix) {
    const definitions = normalizeDefinitions(entry.parsed || {})
    const meaning = definitions.map((item) => `${item.pos || ''} ${item.cn || ''} ${item.en || ''}`).join(' ')
    const values = [entry.term, entry.normalizedTerm, entry.parsed?.term, meaning]
    return values.some((value) => String(value || '').trim().toLowerCase().startsWith(prefix))
  }

  function selectedWordsText(record) {
    const words = Array.isArray(record?.selectedWords) ? record.selectedWords : []
    return words.map((item) => item.term || item.normalizedTerm).filter(Boolean).join('、')
  }

  function currentWordbookName(wordbookId = state.currentWordbookId) {
    return state.wordbooks.find((item) => sameId(item.id, wordbookId))?.name || '所选单词本'
  }

  function wordCountLabel(value) {
    return value || '300-500'
  }

  function difficultyLabel(value) {
    return (
      {
        easy: '基础',
        medium: '适中',
        hard: '挑战',
      }[value] || value || '适中'
    )
  }

  function toggleArticleFocusMode(forceState = null) {
    const next = typeof forceState === 'boolean' ? forceState : !state.articleFocusMode
    state.articleFocusMode = next
    setFocusMode?.('articleStudyView', next)
    elements.articleStudyToolbar?.classList.toggle('hidden', next)
    elements.articleHistoryPanel?.classList.toggle('hidden', next)
    elements.articleStudyLayout?.classList.toggle('article-focus-layout', next)
    if (elements.toggleArticleFocusModeBtn) {
      elements.toggleArticleFocusModeBtn.textContent = next ? '退出专注' : '专注模式'
      elements.toggleArticleFocusModeBtn.classList.toggle('active', next)
    }
  }

  return {
    changeArticleWordbook,
    loadArticleWords,
    loadArticleHistory,
    changeArticleWordPage,
    changeArticleHistoryPage,
    renderArticleWords,
    renderArticleHistory,
    renderArticleResult,
    renderArticleModalPreview,
    toggleArticleEntry,
    clearArticleSelection,
    recommendArticleWords,
    openArticleStudyModal,
    closeArticleStudyModal,
    generateArticlePreview,
    generateArticle,
    saveArticleStudy,
    openArticleRecord,
    toggleArticleFocusMode,
  }
}
