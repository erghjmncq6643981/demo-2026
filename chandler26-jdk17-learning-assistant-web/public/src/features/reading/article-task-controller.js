import { isRequestAbort } from '/src/shared/latest-request.js'

/** 语境精读 AI 任务轮询与结果回填控制器。 */
export function createArticleTaskController({
  state,
  api,
  sameId,
  logEvent,
  toast,
  renderPreview,
  loadHistory,
  normalizeError,
}) {
  const activeStatuses = ['pending', 'running', 'retry_wait']
  const terminalStatuses = ['completed', 'partial_failed', 'attention_required', 'failed', 'cancelled']
  let pollTimer = null
  let resultLoadingId = null

  function clear() {
    if (pollTimer) {
      window.clearTimeout(pollTimer)
      pollTimer = null
    }
    resultLoadingId = null
  }

  function isActive(status) {
    return activeStatuses.includes(status)
  }

  function isTerminal(status) {
    return terminalStatuses.includes(status)
  }

  function start(taskId) {
    clear()
    if (state.preview || !taskId) return
    const poll = () => {
      if (!state.articleGenerationTask || !sameId(state.articleGenerationTask.id, taskId)) return
      api.getTask(taskId)
        .then((task) => {
          if (!state.articleGenerationTask || !sameId(state.articleGenerationTask.id, taskId)) return
          apply({ ...task, id: task.id || taskId })
          if (isActive(task.status)) {
            pollTimer = window.setTimeout(poll, 4000)
          } else {
            clear()
          }
        })
        .catch((error) => {
          if (isRequestAbort(error)) return
          logEvent('error', '精读任务状态查询失败', error.message)
          pollTimer = window.setTimeout(poll, 6000)
        })
    }
    poll()
  }

  function apply(detail) {
    if (!detail || detail.taskType !== 'article_material' || !state.articleGenerationTask
      || !sameId(detail.id, state.articleGenerationTask.id)) return
    const status = detail.status || state.articleGenerationTask.status
    state.articleGenerationTask = { ...state.articleGenerationTask, ...detail, status }
    if (isActive(status)) {
      state.articlePreviewLoading = true
      renderPreview(null)
      return
    }
    state.articlePreviewLoading = false
    if (status === 'completed') {
      if (!detail.businessId) {
        state.articlePreviewError = normalizeError(new Error('精读任务已完成，但未关联材料记录，请在任务中心查看详情'))
        renderPreview(null)
        toast(state.articlePreviewError.message)
        return
      }
      if (resultLoadingId && sameId(resultLoadingId, detail.id)) return
      if (state.articleDraftRecord && sameId(state.articleDraftRecord.id, detail.businessId)) return
      resultLoadingId = detail.id
      state.articlePreviewLoading = true
      renderPreview(null)
      api.getRecord(detail.businessId)
        .then((record) => {
          if (!state.articleGenerationTask || !sameId(state.articleGenerationTask.id, detail.id)) return
          state.articleDraftRecord = record
          state.articleGenerationTask = { ...state.articleGenerationTask, businessId: detail.businessId, resultLoaded: true }
          state.articlePreviewLoading = false
          renderPreview(record)
          loadHistory()
          toast('精读材料已生成，可开始学习')
        })
        .catch((error) => {
          state.articlePreviewLoading = false
          logEvent('error', '精读材料结果加载失败', error.message)
          toast(`精读材料已生成，但详情加载失败：${error.message}`)
          renderPreview(null)
        })
        .finally(() => {
          resultLoadingId = null
        })
      return
    }
    if (['failed', 'partial_failed', 'attention_required', 'cancelled'].includes(status)) {
      state.articlePreviewError = normalizeError(new Error(detail.errorMessage || '精读材料生成任务未完成'))
      renderPreview(null)
      toast(status === 'cancelled' ? '精读材料生成任务已取消' : '精读材料生成失败，可在任务中心重试')
    }
  }

  return { clear, isActive, isTerminal, start, apply }
}
