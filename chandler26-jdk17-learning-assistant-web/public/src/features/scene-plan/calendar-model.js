import { asArray, localDateKey } from '/src/features/scene-plan/model.js'
import { pendingChallengeWords } from '/src/features/scene-plan/challenge-model.js'

export function addDays(date, count) {
  const result = new Date(date)
  result.setDate(result.getDate() + count)
  return result
}

export function dateFromKey(key) {
  const [year, month, day] = String(key || '').split('-').map(Number)
  if (![year, month, day].every(Number.isFinite)) return new Date()
  return new Date(year, month - 1, day, 12)
}

export function startOfWeek(date) {
  const result = new Date(date)
  const day = result.getDay()
  result.setDate(result.getDate() + (day === 0 ? -6 : 1 - day))
  result.setHours(12, 0, 0, 0)
  return result
}

export function calendarDates(range, cursorDate) {
  const anchor = dateFromKey(cursorDate || localDateKey())
  if (range === 'month') {
    const first = new Date(anchor.getFullYear(), anchor.getMonth(), 1, 12)
    const gridStart = startOfWeek(first)
    return Array.from({ length: 42 }, (_, index) => addDays(gridStart, index))
  }
  const first = startOfWeek(anchor)
  return Array.from({ length: 7 }, (_, index) => addDays(first, index))
}

export function calendarTitle(range, cursorDate, dates) {
  if (!dates.length) return '本周'
  if (range === 'month') {
    const anchor = dateFromKey(cursorDate || localDateKey())
    return `${anchor.getFullYear()}年${anchor.getMonth() + 1}月`
  }
  const start = dates[0]
  const end = dates[dates.length - 1]
  const endYear = start.getFullYear() === end.getFullYear() ? '' : `${end.getFullYear()}年`
  return `${start.getFullYear()}年${start.getMonth() + 1}月${start.getDate()}日 - ${endYear}${end.getMonth() + 1}月${end.getDate()}日`
}

export function formatCalendarDate(date, withMonth = false) {
  if (!date) return ''
  if (typeof date === 'string') {
    const match = date.match(/^(\d{4})-(\d{1,2})-(\d{1,2})/)
    if (match) return withMonth ? `${Number(match[2])}月${Number(match[3])}日` : `${Number(match[3])}日`
  }
  const value = date instanceof Date ? date : new Date(date)
  if (Number.isNaN(value.getTime())) return String(date)
  return withMonth ? `${value.getMonth() + 1}月${value.getDate()}日` : `${value.getDate()}日`
}

export function unitDateKey(unit) {
  if (unit?.recommendedDate) return unit.recommendedDate
  return unit?.generatedTime ? unit.generatedTime.split('T')[0] : ''
}

export function unitsForDate(plan, key) {
  return asArray(plan?.units).filter((unit) => unitDateKey(unit) === key)
}

export function unitStatusLabel(unit) {
  if (unit?.status === 'completed') return '已完成'
  if (unit?.status === 'in_progress') return '学习中'
  return '待学习'
}

export function calendarDaySummary(plan, date) {
  const units = unitsForDate(plan, localDateKey(date))
  return {
    units,
    pendingCount: units.reduce((sum, unit) => sum + pendingChallengeWords(unit).length, 0),
  }
}
