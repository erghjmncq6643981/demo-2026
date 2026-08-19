export const TIER_LABELS = {
  core: '核心',
  extended: '词表扩展',
  supplementary: 'AI 补充',
  review: '复习',
}

export const PLAN_STATUS_LABELS = {
  not_started: '未开始',
  active: '学习中',
  completed: '已完成',
  paused: '已暂停',
  cancelled: '已取消',
}

export const IMPORT_STATUS_LABELS = {
  reviewing: '待审核',
  published: '已发布',
  failed: '失败',
}

export const ANALYSIS_STATUS_LABELS = {
  not_started: '未开始',
  pending: '等待执行',
  running: '分析中',
  completed: '已完成',
  partial_failed: '部分完成',
  failed: '失败',
}

export const ASSESSMENT_LABELS = {
  meaning_choice: '含义选择',
  copy_typing: '跟敲单词',
  meaning_spelling: '含义拼写',
}

export const SOURCE_LABELS = {
  self_study: '自考',
  cet4: '四级',
  cet6: '六级',
  ielts: '雅思',
}

export function asArray(value) {
  return Array.isArray(value) ? value : []
}

export function number(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

export function localDateKey(date = new Date()) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
