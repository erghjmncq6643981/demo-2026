import { sameId } from '/src/shared/ids.js'
import { hideModal, showModal } from '/src/shared/modal.js'
import { escapeHtml, formatDateTime } from '/src/shared/text.js'
import { normalizeArray, normalizeDefinitions, readText } from '/src/shared/vocabulary.js'
import { syncCurrentWordbookId } from '/src/shared/wordbook.js'
import { isRequestAbort } from '/src/shared/latest-request.js'
import { createArticleApi } from '/src/features/reading/article-api.js'
import { createArticleTaskController } from '/src/features/reading/article-task-controller.js'
import { createArticleWordSelector } from '/src/features/reading/article-word-selector.js'
import { createArticleLearningView } from '/src/features/reading/article-learning-view.js'
import {
  articleStatusLabel,
  formatArticleErrorForLog,
  normalizeArticleError,
  normalizeArticleStage,
  readArticleError,
  scoreArticlePractice,
} from '/src/features/reading/article-model.js'
import {
  buildPreviewArticleRecord,
  renderArticleError,
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
  const taskController = createArticleTaskController({
    state,
    api,
    sameId,
    logEvent,
    toast,
    renderPreview: (...args) => renderArticleModalPreview(...args),
    loadHistory: () => loadArticleHistory(),
    normalizeError: normalizeArticleError,
  })
  const wordSelector = createArticleWordSelector({
    state,
    elements,
    toast,
    sameId,
    entryMatchesFilter,
    renderPreview: (...args) => renderArticleModalPreview(...args),
  })
  const learningView = createArticleLearningView({
    state,
    speakSentence,
    currentWordbookName,
    changeArticleStage,
    setArticleAnswer,
    articleAnswers,
    articleRecordKey,
    completeArticleStudy,
    renderArticleResult,
    cssEscape,
    articleStatusLabel,
    wordCountLabel,
    difficultyLabel,
  })

  window.addEventListener('learning:ai-task-updated', (event) => taskController.apply(event?.detail || {}))

  function renderArticleWords() {
    wordSelector.render()
  }

  function toggleArticleEntry(entryId) {
    wordSelector.toggle(entryId)
  }

  function clearArticleSelection() {
    wordSelector.clear()
  }

  function recommendArticleWords() {
    wordSelector.recommend()
  }

  async function changeArticleWordbook(wordbookId) {
    syncCurrentWordbookId(state, elements, wordbookId)
    state.articleHistoryPage = 1
    state.articleWordPage = 1
    state.selectedArticleEntryIds = []
    state.currentArticleRecord = null
    state.articleDraftRecord = null
    taskController.clear()
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
    if (state.articleGenerationTask && taskController.isTerminal(state.articleGenerationTask.status)) {
      state.articleGenerationTask = null
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
      state.articlePreviewLoading = false
      renderArticleModalPreview(null)
      taskController.start(task?.id)
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
        replaceArticleRecord(record)
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
        const title = record.title || readText(record.parsed, ['title']) || selectedWordsText(record) || '语境精读'
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
    learningView.renderContent(elements.articleResult, elements.articleResultBadge, record)
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
    learningView.renderContent(elements.articleModalPreview, elements.articleModalPreviewBadge, record, { compact: true })
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
    const exists = state.articleRecords.some((item) => sameId(item.id, record.id))
    if (exists) {
      state.articleRecords = state.articleRecords.map((item) => sameId(item.id, record.id) ? { ...item, ...record } : item)
    } else {
      state.articleRecords = [record, ...state.articleRecords]
    }
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
