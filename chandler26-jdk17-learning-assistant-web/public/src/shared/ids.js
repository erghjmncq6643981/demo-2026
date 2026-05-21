export function normalizeId(value) {
  if (value === null || value === undefined) return ''
  return String(value).trim()
}

export function sameId(left, right) {
  return normalizeId(left) === normalizeId(right)
}
