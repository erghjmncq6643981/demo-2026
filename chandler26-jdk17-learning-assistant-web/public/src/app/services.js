import { readErrorPayload } from '/src/shared/text.js'
import { hideModal, showModal } from '/src/shared/modal.js'

export function createAppServices({ state, elements }) {
  let renderSystemLogs = () => {}

  function setSystemLogRenderer(renderer) {
    renderSystemLogs = typeof renderer === 'function' ? renderer : () => {}
  }

  function apiUrl(path) {
    return `${state.apiBase.replace(/\/$/, '')}${path}`
  }

  async function request(path, options = {}) {
    const headers = { 'content-type': 'application/json', ...(options.headers || {}) }
    if (state.token && !headers.Authorization) {
      headers.Authorization = `Bearer ${state.token}`
    }
    const response = await fetch(apiUrl(path), { headers, ...options })
    if (!response.ok) {
      const text = await response.text()
      const errorPayload = readErrorPayload(text, response.status)
      const error = new Error(errorPayload.message || `HTTP ${response.status}`)
      error.errorCode = errorPayload.errorCode
      error.status = response.status
      error.payload = errorPayload.raw
      throw error
    }
    const text = await response.text()
    if (!text) return null
    try {
      return JSON.parse(text)
    } catch {
      return text
    }
  }

  function setLoading(isLoading) {
    for (const element of [
      elements.loginBtn,
      elements.registerBtn,
      elements.studyBtn,
      elements.studyRegenerateBtn,
      elements.chatBtn,
      elements.addToWordbookBtn,
      elements.articleGenerateBtn,
      elements.articlePreviewGenerateBtn,
      elements.createWordbookBtn,
      elements.saveAgentBtn,
      elements.saveSpeechBtn,
      elements.saveModelBtn,
      elements.saveTemplateBtn,
      elements.saveAccountProfileBtn,
      elements.saveAccountSecurityBtn,
    ]) {
      if (element) element.disabled = isLoading
    }
  }

  function toast(message) {
    elements.toast.textContent = message
    elements.toast.classList.add('show')
    window.clearTimeout(toast.timer)
    toast.timer = window.setTimeout(() => elements.toast.classList.remove('show'), 2600)
  }

  function confirmAction({ title = '确认操作', message = '请确认是否继续。', acceptText = '确认', danger = false } = {}) {
    return new Promise((resolve) => {
      if (!elements.deleteConfirmModal) {
        resolve(window.confirm(message))
        return
      }
      state.pendingDeleteConfirm = resolve
      elements.deleteConfirmTitle.textContent = title
      elements.deleteConfirmMessage.textContent = message
      elements.deleteConfirmAcceptBtn.textContent = acceptText
      elements.deleteConfirmAcceptBtn.classList.toggle('danger-button', danger)
      elements.deleteConfirmAcceptBtn.classList.toggle('primary-button', !danger)
      showModal(elements.deleteConfirmModal)
      elements.deleteConfirmAcceptBtn.focus()
    })
  }

  function confirmDelete(options = {}) {
    return confirmAction({ acceptText: '确认删除', danger: true, ...options })
  }

  function closeDeleteConfirm(confirmed = false) {
    hideModal(elements.deleteConfirmModal)
    const resolve = state.pendingDeleteConfirm
    state.pendingDeleteConfirm = null
    if (resolve) resolve(confirmed)
  }

  function logEvent(type, title, detail = '') {
    const entry = {
      type,
      title,
      detail,
      time: new Date().toISOString(),
      source: 'client',
    }
    state.systemLogs = [entry, ...state.systemLogs].slice(0, 60)
    if (state.preview || !state.token) {
      localStorage.setItem('learning.systemLogs', JSON.stringify(state.systemLogs))
    }
    renderSystemLogs()
    persistSystemLog(entry)
  }

  async function persistSystemLog(entry) {
    if (state.preview || !state.token) return
    try {
      await request('/api/v1/learning/system-logs', {
        method: 'POST',
        body: JSON.stringify(entry),
      })
    } catch {
      // 日志写入失败不阻断主流程。
    }
  }

  function setConnection(ok) {
    for (const item of [elements.connectionStatus]) {
      if (!item) continue
      const preview = ok === 'preview'
      item.classList.toggle('ok', ok === true)
      item.classList.toggle('bad', ok === false)
      item.textContent = preview ? '设计预览 · 未连接后端' : ok ? '后端已连接' : '后端未连接'
    }
  }

  return {
    apiUrl,
    request,
    setLoading,
    toast,
    confirmAction,
    confirmDelete,
    closeDeleteConfirm,
    logEvent,
    persistSystemLog,
    setConnection,
    setSystemLogRenderer,
  }
}
