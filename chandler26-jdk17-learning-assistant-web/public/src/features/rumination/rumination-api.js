import { createLatestRequest } from '/src/shared/latest-request.js'

/**
 * 知识反刍模块接口封装（图谱、场景、异步任务、材料、语音与句子练习）。
 */
export function createRuminationApi(request) {
  const requests = createLatestRequest(request)
  const id = (value) => encodeURIComponent(value)

  return {
    cancelAll: requests.cancelAll,
    listRuminationScenarios: (wordbookId) => requests.latest(
      'rumination-scenarios',
      `/api/v1/learning/rumination/scenarios?wordbookId=${id(wordbookId)}`,
    ),
    getRuminationGraph: (wordbookId, scenarioCode = 'produce_kitchen') => requests.latest(
      'rumination-graph',
      `/api/v1/learning/rumination/graph?wordbookId=${id(wordbookId)}&scenarioCode=${id(scenarioCode)}`,
    ),
    submitRuminationProbe: (payload) => request('/api/v1/learning/rumination/probes/submit', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
    submitRuminationSentencePractice: (payload) => request('/api/v1/learning/rumination/materials/practice/submit', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
    saveRuminationGraph: (payload, fetchOptions = {}) => request('/api/v1/learning/rumination/graph/save', {
      method: 'POST',
      body: JSON.stringify(payload),
      ...fetchOptions,
    }),
    updateRuminationGraphPositions: (payload, fetchOptions = {}) => request('/api/v1/learning/rumination/graph/positions', {
      method: 'POST',
      body: JSON.stringify(payload),
      ...fetchOptions,
    }),
    updateRuminationBagItem: (payload, fetchOptions = {}) => request('/api/v1/learning/rumination/graph/bag', {
      method: 'POST',
      body: JSON.stringify(payload),
      ...fetchOptions,
    }),
    listRuminationMaterials: (wordbookId, scenarioCode = '') => requests.latest(
      'rumination-materials',
      `/api/v1/learning/rumination/materials?wordbookId=${id(wordbookId)}${scenarioCode ? `&scenarioCode=${id(scenarioCode)}` : ''}`,
    ),
    generateRuminationMaterial: (payload) => request('/api/v1/learning/rumination/materials/generate', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
    submitRuminationMaterialTask: (payload) => request('/api/v1/learning/rumination/materials/generate/async', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
    regenerateRuminationMaterialTask: (materialId, requestId = '') => {
      const qs = requestId ? `?requestId=${encodeURIComponent(requestId)}` : ''
      return request(`/api/v1/learning/rumination/materials/${id(materialId)}/regenerate/async${qs}`, {
        method: 'POST',
      })
    },
    getRuminationMaterialTask: (wordbookId, scenarioCode = '') => requests.latest(
      'rumination-material-task',
      `/api/v1/learning/rumination/materials/task?wordbookId=${id(wordbookId)}&scenarioCode=${id(scenarioCode)}`,
    ),
    getRuminationMaterialDetail: (materialId) => request(`/api/v1/learning/rumination/materials/${id(materialId)}`),
    getRuminationMaterialAudioStatus: (materialId) => request(`/api/v1/learning/rumination/materials/${id(materialId)}/audio/status`),
    generateRuminationMaterialAudioAsync: (materialId, forceRefresh = false) => request(
      `/api/v1/learning/rumination/materials/${id(materialId)}/audio/async?forceRefresh=${forceRefresh ? 'true' : 'false'}`,
      { method: 'POST' },
    ),
    getAiTask: (taskId) => requests.latest(`rumination-task-${id(taskId)}`, `/api/v1/learning/ai-tasks/${id(taskId)}`),
    retryAiTask: (taskId) => request(`/api/v1/learning/ai-tasks/${id(taskId)}/retry`, {
      method: 'POST',
    }),
    cancelAiTask: (taskId) => request(`/api/v1/learning/ai-tasks/${id(taskId)}/cancel`, {
      method: 'POST',
    }),
    initCustomScenario: (payload) => request('/api/v1/learning/rumination/scenarios/custom', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
    submitCustomScenarioTask: (payload) => request('/api/v1/learning/rumination/scenarios/custom/async', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
    fissionRuminationNode: (payload) => request('/api/v1/learning/rumination/fission', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
    submitRuminationFissionTask: (payload) => request('/api/v1/learning/rumination/fission/async', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
    listRuminationTasks: (wordbookId, scenarioCode = '') => requests.latest(
      'rumination-all-tasks',
      `/api/v1/learning/rumination/tasks?wordbookId=${id(wordbookId)}${scenarioCode ? `&scenarioCode=${id(scenarioCode)}` : ''}`,
    ),
    getRuminationFissionResult: (taskId) => request(`/api/v1/learning/rumination/fission/tasks/${id(taskId)}/result`),
  }
}
