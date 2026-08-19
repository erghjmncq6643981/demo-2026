import { readText } from '/src/shared/vocabulary.js'

export const ARTICLE_WORD_LIMIT = 20
export const ARTICLE_STAGES = ['reading', 'vocabulary', 'check']
export const ERROR_CODE_AI_MODEL_BALANCE_INSUFFICIENT = 'AI_MODEL_BALANCE_INSUFFICIENT'
export const ERROR_CODE_AI_MODEL_CALL_FAILED = 'AI_MODEL_CALL_FAILED'

export function normalizeArticleStage(stage) {
  return ARTICLE_STAGES.includes(stage) ? stage : 'reading'
}

export function normalizeAnswerValue(value) {
  return String(value || '').trim().replace(/\s+/g, ' ').toLowerCase()
}

export function scoreArticlePractice(practice, answers) {
  const correct = practice.reduce((count, item, index) => {
    const expected = readText(item, ['correct_answer', 'correctAnswer', 'answer'])
    return count + (normalizeAnswerValue(answers[index]) === normalizeAnswerValue(expected) ? 1 : 0)
  }, 0)
  const total = practice.length
  return { total, correct, score: total ? Math.round((correct * 100) / total) : 0 }
}

export function articleStatusLabel(status) {
  return ({ generated: '待开始', in_progress: '学习中', completed: '已完成' })[status] || '待开始'
}

export function normalizeArticleError(error) {
  const message = String(error?.message || '精读材料生成失败').trim()
  const errorCode = String(error?.errorCode || '').trim()
  const status = error?.status || ''
  return { message, errorCode, status, suggestion: articleErrorSuggestion(errorCode, status) }
}

export function readArticleError(error) {
  if (!error) return null
  if (typeof error === 'string') {
    const message = error.trim()
    return message ? { message, errorCode: '', status: '', suggestion: '' } : null
  }
  const message = String(error.message || '').trim()
  return message ? error : null
}

export function articleErrorSuggestion(errorCode, status) {
  if (errorCode === ERROR_CODE_AI_MODEL_BALANCE_INSUFFICIENT) {
    return '可以先切换到其它启用模型，或补充当前供应商账户余额后重试。'
  }
  if (errorCode === ERROR_CODE_AI_MODEL_CALL_FAILED) {
    return '请检查模型配置、Base URL、API Key 或稍后重试。'
  }
  if (Number(status) >= 500) {
    return '服务端返回异常，请查看个人信息里的系统日志或后端日志定位原因。'
  }
  return ''
}

export function formatArticleErrorForLog(error) {
  return [
    error.message,
    error.errorCode ? `错误码：${error.errorCode}` : '',
    error.status ? `HTTP：${error.status}` : '',
  ].filter(Boolean).join('；')
}

export function splitArticleLines(text, language) {
  const normalized = String(text || '').replace(/\s+/g, ' ').trim()
  if (!normalized) return []
  const pattern = language === 'zh'
    ? /[^。！？!?]+[。！？!?]+|[^。！？!?]+$/g
    : /[^.!?]+[.!?]+(?:["')\]]+)?|[^.!?]+$/g
  return (normalized.match(pattern) || [normalized]).map((line) => line.trim()).filter(Boolean)
}
