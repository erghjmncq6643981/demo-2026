import { normalizeId } from './ids.js'

export function normalizeWordbookId(value) {
  const id = normalizeId(value)
  return id || ''
}

export function syncCurrentWordbookId(state, elements, wordbookId, options = {}) {
  const normalized = normalizeWordbookId(wordbookId)
  state.currentWordbookId = normalized || null

  const shouldPersist = options.persist ?? true
  if (shouldPersist && typeof localStorage !== 'undefined') {
    if (normalized) {
      localStorage.setItem('learning.wordbookId', normalized)
    } else {
      localStorage.removeItem('learning.wordbookId')
    }
  }

  if (elements?.wordbookSelect) {
    elements.wordbookSelect.value = normalized
  }
  if (elements?.reviewWordbookSelect) {
    elements.reviewWordbookSelect.value = normalized
  }

  return state.currentWordbookId
}
