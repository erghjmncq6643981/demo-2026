import { createLatestRequest } from '/src/shared/latest-request.js'

/** 公共词本导入、审核、发布和关联分析 API。 */
export function createVocabularyCatalogApi(request) {
  const requests = createLatestRequest(request)
  const id = (value) => encodeURIComponent(value)
  return {
    cancelAll: requests.cancelAll,
    listImports: () => requests.latest('imports', '/api/v1/vocabulary-imports'),
    importMarkdown: (payload) => request('/api/v1/vocabulary-imports/markdown', {
      method: 'POST', body: JSON.stringify(payload),
    }),
    getImport: (jobId, query) => requests.latest('import-review', `/api/v1/vocabulary-imports/${id(jobId)}?${query}`),
    updateEntry: (jobId, entryId, approvedTerm) => request(
      `/api/v1/vocabulary-imports/${id(jobId)}/entries/${id(entryId)}`,
      { method: 'PUT', body: JSON.stringify({ approvedTerm }) },
    ),
    confirmWarnings: (jobId) => request(`/api/v1/vocabulary-imports/${id(jobId)}/warnings/confirm`, {
      method: 'POST', body: JSON.stringify({ applySuggested: true }),
    }),
    publish: (jobId) => request(`/api/v1/vocabulary-imports/${id(jobId)}/publish`, {
      method: 'POST', body: JSON.stringify({}),
    }),
    updateImport: (jobId, payload) => request(`/api/v1/vocabulary-imports/${id(jobId)}`, {
      method: 'PUT', body: JSON.stringify(payload),
    }),
    deleteImport: (jobId) => request(`/api/v1/vocabulary-imports/${id(jobId)}`, { method: 'DELETE' }),
    getAnalysis: (catalogVersionId) => requests.latest(
      'catalog-analysis', `/api/v1/vocabulary-catalogs/${id(catalogVersionId)}/analysis`,
    ),
    triggerAnalysis: (catalogVersionId, payload) => request(
      `/api/v1/vocabulary-catalogs/${id(catalogVersionId)}/analysis`,
      { method: 'POST', body: JSON.stringify(payload) },
    ),
  }
}
