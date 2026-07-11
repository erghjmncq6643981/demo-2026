export function formatDateTime(value) {
  if (!value) return '待定'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

export function readErrorMessage(text) {
  return readErrorPayload(text).message
}

export function readErrorPayload(text, status = null) {
  if (!text) return { message: '', errorCode: '', status, raw: '' }
  try {
    const payload = JSON.parse(text)
    if (payload && typeof payload === 'object') {
      const nestedError = payload.error && typeof payload.error === 'object' ? payload.error : null
      const message = payload.message || nestedError?.message || payload.error || text
      return {
        message: String(message || ''),
        errorCode: payload.errorCode || nestedError?.code || '',
        status,
        raw: payload,
      }
    }
    return { message: String(payload || text), errorCode: '', status, raw: payload }
  } catch {
    return { message: text, errorCode: '', status, raw: text }
  }
}

export function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')
}

export function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

export function inlineMarkdown(text) {
  return escapeHtml(text)
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
}
