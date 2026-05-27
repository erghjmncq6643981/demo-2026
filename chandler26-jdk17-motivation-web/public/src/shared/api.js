const API_BASE = localStorage.getItem('motivation_api_base') || 'http://127.0.0.1:17680/api/v1'
const TOKEN_KEY = 'motivation_token'
const API_PREFIX = '/api/v1'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

export async function request(path, options = {}) {
  const headers = new Headers(options.headers || {})
  headers.set('Accept', 'application/json')
  if (options.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(apiUrl(path), {
    ...options,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok || (payload && payload.code !== 0)) {
    const message = payload?.message || `请求失败：${response.status}`
    throw new Error(message)
  }
  return payload?.data
}

export async function requestForm(path, formData, options = {}) {
  const headers = new Headers(options.headers || {})
  headers.set('Accept', 'application/json')
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(apiUrl(path), {
    method: options.method || 'POST',
    ...options,
    headers,
    body: formData,
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok || (payload && payload.code !== 0)) {
    const message = payload?.message || `请求失败：${response.status}`
    throw new Error(message)
  }
  return payload?.data
}

export async function requestBlob(path, options = {}) {
  const headers = new Headers(options.headers || {})
  headers.set('Accept', 'image/*,application/json')
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const response = await fetch(apiUrl(path), {
    ...options,
    headers,
  })
  const contentType = response.headers.get('Content-Type') || ''
  if (!response.ok || contentType.includes('application/json')) {
    const text = await response.text().catch(() => '')
    let message = `请求失败：${response.status}`
    try {
      const payload = JSON.parse(text)
      message = payload?.message || message
    } catch {
      if (text) message = text
    }
    throw new Error(message)
  }
  return response.blob()
}

function apiUrl(path) {
  const value = String(path || '')
  if (/^https?:\/\//i.test(value)) {
    return value
  }
  const normalized = value.startsWith(API_PREFIX) ? value.slice(API_PREFIX.length) : value
  return `${API_BASE}${normalized.startsWith('/') ? normalized : `/${normalized}`}`
}

export const api = {
  health: () => request('/health'),
  register: (body) => request('/auth/register', { method: 'POST', body }),
  login: (body) => request('/auth/login', { method: 'POST', body }),
  profile: () => request('/auth/profile'),
  updateProfile: (body) => request('/auth/profile', { method: 'PUT', body }),
  preferences: () => request('/preferences'),
  savePreferences: (body) => request('/preferences', { method: 'PUT', body }),
  uploadProfileAvatar: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return requestForm('/auth/profile/avatar', formData)
  },
  childActivityLogs: (childId = '', limit = 20) => {
    const params = new URLSearchParams()
    if (childId) {
      params.set('childId', childId)
    }
    params.set('limit', String(limit))
    return request(`/activity-logs/children?${params.toString()}`)
  },
  avatarBlob: (avatarUrl) => requestBlob(avatarUrl),
  children: () => request('/children'),
  createChild: (body) => request('/children', { method: 'POST', body }),
  updateChild: (childId, body) => request(`/children/${childId}`, { method: 'PUT', body }),
  deleteChild: (childId) => request(`/children/${childId}`, { method: 'DELETE' }),
  readChildAccountPassword: (childId) => request(`/children/${childId}/account/password`),
  updateChildAccountPassword: (childId, body) => request(`/children/${childId}/account/password`, { method: 'PUT', body }),
  uploadChildAvatar: (childId, file) => {
    const formData = new FormData()
    formData.append('file', file)
    return requestForm(`/children/${childId}/avatar`, formData)
  },
  goals: (childId) => request(`/goals?childId=${encodeURIComponent(childId)}`),
  createGoal: (body) => request('/goals', { method: 'POST', body }),
  updateGoal: (goalId, body) => request(`/goals/${goalId}`, { method: 'PUT', body }),
  deleteGoal: (goalId) => request(`/goals/${goalId}`, { method: 'DELETE' }),
  tasks: (childId) => request(`/tasks?childId=${encodeURIComponent(childId)}`),
  createTask: (body) => request('/tasks', { method: 'POST', body }),
  updateTask: (taskId, body) => request(`/tasks/${taskId}`, { method: 'PUT', body }),
  deleteTask: (taskId) => request(`/tasks/${taskId}`, { method: 'DELETE' }),
  completeTask: (taskId, body) => request(`/tasks/${taskId}/complete`, { method: 'POST', body }),
  approveTaskRecord: (recordId, body = {}) => request(`/tasks/records/${recordId}/approve`, { method: 'POST', body }),
  rejectTaskRecord: (recordId, body = {}) => request(`/tasks/records/${recordId}/reject`, { method: 'POST', body }),
  calendar: (childId, date) => request(`/calendar?childId=${encodeURIComponent(childId)}&year=${date.getFullYear()}&month=${date.getMonth() + 1}`),
  pointSummary: (childId) => request(`/children/${childId}/points/summary`),
  ledger: (childId) => request(`/children/${childId}/points/ledger?limit=20`),
  manualAdjust: (childId, body) => request(`/children/${childId}/points/manual-adjust`, { method: 'POST', body }),
  pointExchangeRule: (childId) => request(`/children/${childId}/points/exchange-rule`),
  savePointExchangeRule: (childId, body) => request(`/children/${childId}/points/exchange-rule`, { method: 'PUT', body }),
  exchangePoints: (childId, body) => request(`/children/${childId}/points/exchange`, { method: 'POST', body }),
  pointCurrencies: (childId) => request(`/children/${childId}/points/currencies`),
  createPointCurrency: (childId, body) => request(`/children/${childId}/points/currencies`, { method: 'POST', body }),
  updatePointCurrency: (childId, currencyId, body) => request(`/children/${childId}/points/currencies/${currencyId}`, { method: 'PUT', body }),
  deletePointCurrency: (childId, currencyId) => request(`/children/${childId}/points/currencies/${currencyId}`, { method: 'DELETE' }),
  rewards: (childId) => request(`/rewards?childId=${encodeURIComponent(childId)}`),
  createReward: (body) => request('/rewards', { method: 'POST', body }),
  updateReward: (rewardId, body) => request(`/rewards/${rewardId}`, { method: 'PUT', body }),
  deleteReward: (rewardId) => request(`/rewards/${rewardId}`, { method: 'DELETE' }),
  exchangeReward: (body) => request('/rewards/exchange', { method: 'POST', body }),
  rewardExchanges: (childId) => request(`/rewards/exchanges?childId=${encodeURIComponent(childId)}&limit=20`),
  approveRewardExchange: (exchangeId, body = {}) => request(`/rewards/exchanges/${exchangeId}/approve`, { method: 'POST', body }),
  rejectRewardExchange: (exchangeId, body = {}) => request(`/rewards/exchanges/${exchangeId}/reject`, { method: 'POST', body }),
  updateRewardFulfillment: (exchangeId, body = {}) => request(`/rewards/exchanges/${exchangeId}/fulfillment`, { method: 'PUT', body }),
  confirmRewardExchange: (exchangeId, body = {}) => request(`/rewards/exchanges/${exchangeId}/confirm`, { method: 'POST', body }),
}
