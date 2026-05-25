export function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')
}

export function formatDate(date) {
  const value = date instanceof Date ? date : new Date(date)
  if (Number.isNaN(value.getTime())) {
    return ''
  }
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function sameDay(left, right) {
  return formatDate(left) === formatDate(right)
}

export function monthTitle(date) {
  return `${date.getFullYear()} 年 ${date.getMonth() + 1} 月`
}

export function pointIcon(pointType) {
  const icons = {
    STAR: '★',
    FLOWER: '✿',
    CROWN: '♛',
  }
  return icons[pointType] || '★'
}

export function pointName(pointType) {
  const names = {
    STAR: '星星',
    FLOWER: '红花',
    CROWN: '皇冠',
  }
  return names[pointType] || pointType || '积分'
}

export function statusName(status) {
  const names = {
    PENDING: '待完成',
    SUBMITTED: '待审核',
    APPROVED: '已完成',
    REJECTED: '未通过',
    SKIPPED: '已跳过',
    REQUESTED: '待确认',
    COMPLETED: '已确认',
    ACTIVE: '启用',
    PAUSED: '暂停',
    FINISHED: '完成',
    ARCHIVED: '已归档',
    INACTIVE: '停用',
  }
  return names[status] || status || '未知'
}

export function fulfillmentStatusName(status) {
  const names = {
    PENDING: '待实现',
    SCHEDULED: '已加入日程',
    IN_PROGRESS: '待实现',
    COMPLETED: '已实现',
    CONFIRMED: '已确认',
  }
  return names[status] || '待实现'
}

export function clamp(value, min, max) {
  const number = Number(value)
  if (Number.isNaN(number)) {
    return min
  }
  return Math.max(min, Math.min(max, number))
}
