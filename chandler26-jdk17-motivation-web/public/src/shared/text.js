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
    PENDING: '待履约',
    SCHEDULED: '履约中',
    IN_PROGRESS: '履约中',
    COMPLETED: '待宝贝确认',
    CONFIRMED: '宝贝已确认',
  }
  return names[status] || '待履约'
}

export function branchStatusName(status) {
  const names = {
    PENDING: '待处理',
    PURCHASE_ORDERED: '家长已下单',
    PURCHASE_SHIPPING: '奖励运输中',
    PURCHASE_ARRIVED: '奖励已到货',
    SCHEDULED: '已加入日程',
    IN_PROGRESS: '奖励进行中',
    COMPLETED: '已完成',
  }
  return names[status] || '待处理'
}

export function rewardMainFlowName(exchangeStatus, fulfillmentStatus) {
  if (exchangeStatus === 'REQUESTED') return '宝贝已申请'
  if (exchangeStatus === 'REJECTED') return '家长已拒绝'
  if (exchangeStatus === 'COMPLETED' || fulfillmentStatus === 'CONFIRMED') return '宝贝已确认'
  if (exchangeStatus === 'APPROVED' && fulfillmentStatus === 'COMPLETED') return '等待宝贝确认'
  if (exchangeStatus === 'APPROVED') return '家长已确认，履约中'
  return statusName(exchangeStatus)
}

export function clamp(value, min, max) {
  const number = Number(value)
  if (Number.isNaN(number)) {
    return min
  }
  return Math.max(min, Math.min(max, number))
}
