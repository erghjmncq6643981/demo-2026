import { normalizeId, sameId } from './ids.js'

export { normalizeId, sameId }

export function normalizeWordbookId(value) {
  const id = normalizeId(value)
  return id || ''
}

export function isDefaultWordbook(wordbook) {
  return Boolean(wordbook?.isDefault ?? wordbook?.default ?? wordbook?.defaultFlag ?? wordbook?.is_default)
}

export function normalizeWordbook(wordbook) {
  if (!wordbook || typeof wordbook !== 'object') return null
  const id = normalizeWordbookId(wordbook.id)
  if (!id) return null
  return {
    ...wordbook,
    id,
    isDefault: isDefaultWordbook(wordbook),
  }
}

export function normalizeWordbooks(wordbooks) {
  return (Array.isArray(wordbooks) ? wordbooks : []).map(normalizeWordbook).filter(Boolean)
}

export function resolveSelectedWordbookId(state, elements, options = {}) {
  const wordbooks = normalizeWordbooks(state.wordbooks)
  if (wordbooks.length !== state.wordbooks.length || wordbooks.some((wordbook, index) => wordbook !== state.wordbooks[index])) {
    state.wordbooks = wordbooks
  }
  if (!wordbooks.length) {
    return syncCurrentWordbookId(state, elements, null)
  }
  const fallback = wordbooks.find(isDefaultWordbook) || wordbooks[0]
  if (options.preferDefault) {
    return syncCurrentWordbookId(state, elements, fallback.id)
  }
  const candidates = [
    state.currentWordbookId,
    elements?.wordbookSelect?.value,
    elements?.reviewWordbookSelect?.value,
    elements?.articleWordbookSelect?.value,
    localStorage.getItem('learning.wordbookId'),
  ]
  const selected = candidates
    .map(normalizeWordbookId)
    .find((id) => id && wordbooks.some((wordbook) => normalizeWordbookId(wordbook.id) === id))
  return syncCurrentWordbookId(state, elements, selected || fallback.id)
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
  if (elements?.articleWordbookSelect) {
    elements.articleWordbookSelect.value = normalized
  }

  return state.currentWordbookId
}
