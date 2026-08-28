import { createLatestRequest } from '/src/shared/latest-request.js'

export function createScenePlanApi(request) {
  const requests = createLatestRequest(request)
  const id = (value) => encodeURIComponent(value)

  return {
    cancelAll: requests.cancelAll,
    listImports: (page = 1, pageSize = 20) => requests.latest(
      'imports', `/api/v1/vocabulary-imports?page=${encodeURIComponent(page)}&pageSize=${encodeURIComponent(pageSize)}`,
    ),
    listPublicCatalogs: () => requests.latest('public-catalogs', '/api/v1/vocabulary-imports/public'),
    listPlans: () => requests.latest('plans', '/api/v1/learning/plans'),
    getPlan: (planId) => requests.latest('plan-detail', `/api/v1/learning/plans/${id(planId)}`),
    getCalendar: (planId, from, to) => requests.latest(
      'plan-calendar',
      `/api/v1/learning/plans/${id(planId)}/calendar?from=${id(from)}&to=${id(to)}`,
    ),
    getUnit: (planId, unitId) => requests.latest(
      'scene-unit-detail',
      `/api/v1/learning/plans/${id(planId)}/units/${id(unitId)}`,
    ),
    getNote: (planId, unitId) => requests.latest(
      'scene-note',
      `/api/v1/learning/plans/${id(planId)}/units/${id(unitId)}/note`,
    ),
    saveNote: (planId, unitId, content) => request(
      `/api/v1/learning/plans/${id(planId)}/units/${id(unitId)}/note`,
      { method: 'PUT', body: JSON.stringify({ content }) },
    ),
    startUnit: (planId, unitId) => request(
      `/api/v1/learning/plans/${id(planId)}/units/${id(unitId)}/start`,
      { method: 'POST' },
    ),
    regenerateDay: (planId, payload) => request(
      `/api/v1/learning/plans/${id(planId)}/units/regenerate-day`,
      { method: 'POST', body: JSON.stringify(payload) },
    ),
    submitAssessment: (planId, unitId, payload) => request(
      `/api/v1/learning/plans/${id(planId)}/units/${id(unitId)}/assessments`,
      { method: 'POST', body: JSON.stringify(payload) },
    ),
    promoteEntry: (planId, unitId, entryId) => request(
      `/api/v1/learning/plans/${id(planId)}/units/${id(unitId)}/entries/${id(entryId)}/promote`,
      { method: 'POST' },
    ),
    completeUnit: (planId, unitId) => request(
      `/api/v1/learning/plans/${id(planId)}/units/${id(unitId)}/complete`,
      { method: 'POST' },
    ),
    generateNext: (planId, payload) => request(
      `/api/v1/learning/plans/${id(planId)}/units/next`,
      { method: 'POST', body: JSON.stringify(payload) },
    ),
    scheduleNext: (planId, payload) => request(
      `/api/v1/learning/plans/${id(planId)}/units/next/async`,
      { method: 'POST', body: JSON.stringify(payload) },
    ),
    regenerateDayAsync: (planId, payload) => request(
      `/api/v1/learning/plans/${id(planId)}/units/regenerate-day/async`,
      { method: 'POST', body: JSON.stringify(payload) },
    ),
    generateRelatedWords: (planId, unitId, payload = {}) => request(
      `/api/v1/learning/plans/${id(planId)}/units/${id(unitId)}/related-words/async`,
      { method: 'POST', body: JSON.stringify(payload) },
    ),
    createPlan: (payload) => request('/api/v1/learning/plans', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
    updatePlan: (planId, payload) => request(`/api/v1/learning/plans/${id(planId)}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),
    updatePlanStatus: (planId, action) => request(
      `/api/v1/learning/plans/${id(planId)}/${action}`,
      { method: 'POST' },
    ),
  }
}
