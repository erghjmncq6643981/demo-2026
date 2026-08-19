import { createLatestRequest } from '/src/shared/latest-request.js'

export function createArticleApi(request) {
  const requests = createLatestRequest(request)
  const id = (value) => encodeURIComponent(value)

  return {
    cancelAll: requests.cancelAll,
    listEntries: (wordbookId, status = '') => requests.latest(
      'article-entries',
      `/api/v1/learning/wordbooks/${id(wordbookId)}/entries${status ? `?status=${id(status)}` : ''}`,
    ),
    listRecords: (wordbookId, limit = 10) => requests.latest(
      'article-records',
      `/api/v1/learning/articles?wordbookId=${id(wordbookId)}&limit=${limit}`,
    ),
    createStudy: (payload) => request('/api/v1/learning/articles/study', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
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
