export function readJsonStorage(key) {
  try {
    return JSON.parse(localStorage.getItem(key) || 'null')
  } catch {
    return null
  }
}

export function readNumberStorage(key, fallback) {
  return clampNumber(localStorage.getItem(key), Number.NEGATIVE_INFINITY, Number.POSITIVE_INFINITY, fallback)
}

export function clampNumber(value, min, max, fallback) {
  const number = Number(value)
  if (!Number.isFinite(number)) return fallback
  return Math.max(min, Math.min(max, number))
}
