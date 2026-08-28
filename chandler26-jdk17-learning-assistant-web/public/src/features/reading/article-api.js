import { createLatestRequest } from '/src/shared/latest-request.js'

export function createArticleApi(request) {
  const requests = createLatestRequest(request)
  const id = (value) => encodeURIComponent(value)

  return {
    cancelAll: requests.cancelAll,
    listEntries: (wordbookId, status = '', page = 1, pageSize = 50, keyword = '') => requests.latest(
      'article-entries',
      `/api/v1/learning/wordbooks/${id(wordbookId)}/entries?status=${id(status)}&keyword=${id(keyword)}&page=${page}&pageSize=${pageSize}`,
    ),
    listRecords: (wordbookId, page = 1, pageSize = 10) => requests.latest(
      'article-records',
      `/api/v1/learning/articles?wordbookId=${id(wordbookId)}&page=${page}&pageSize=${pageSize}`,
    ),
    createStudy: (payload) => request('/api/v1/learning/articles/study', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
    createStudyAsync: (payload) => request('/api/v1/learning/articles/study/async', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
    getTask: (taskId) => requests.latest(`article-task-${id(taskId)}`, `/api/v1/learning/ai-tasks/${id(taskId)}`),
    getRecord: (recordId) => requests.latest('article-record', `/api/v1/learning/articles/${id(recordId)}`),
    updateProgress: (recordId, payload) => request(`/api/v1/learning/articles/${id(recordId)}/progress`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
    complete: (recordId, payload) => request(`/api/v1/learning/articles/${id(recordId)}/complete`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  }
}
