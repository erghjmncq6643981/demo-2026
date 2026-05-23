import { escapeHtml, formatDateTime } from '/src/shared/text.js'
import { logTypeLabel } from '/src/shared/vocabulary.js'

export function createActivityProfileFeature(ctx) {
  const { state, elements, request, toast, logEvent, confirmDelete, createPreviewActivity } = ctx

  function renderProfileMetrics() {
    const wordbookCount = state.wordbooks.length
    const wordCount = state.wordbooks.reduce((sum, item) => sum + Number(item.entryCount || 0), 0)
    const dueCount = state.wordbooks.reduce((sum, item) => sum + Number(item.dueCount || 0), 0)
    elements.wordbookCount.textContent = String(wordbookCount)
    elements.wordCount.textContent = String(wordCount)
    elements.dueCount.textContent = String(dueCount)
  }

  function loadActivity() {
    if (state.preview) {
      state.activity = state.activity || createPreviewActivity()
      renderActivityHeatmap()
      return Promise.resolve()
    }
    if (!state.token) {
      state.activity = null
      renderActivityHeatmap()
      return Promise.resolve()
    }
    return request('/api/v1/learning/activity?days=365')
      .then((activity) => {
        state.activity = activity
        renderActivityHeatmap()
      })
      .catch((error) => {
        logEvent('error', '学习活跃图加载失败', error.message)
        renderActivityHeatmap()
      })
  }

  function renderActivityHeatmap() {
    if (!elements.activityHeatmap) return
    const items = Array.isArray(state.activity?.items) ? state.activity.items : []
    const learnedTotal = state.activity?.learnedTotal ?? items.reduce((sum, item) => sum + Number(item.learnedCount || 0), 0)
    const reviewTotal = state.activity?.reviewTotal ?? items.reduce((sum, item) => sum + Number(item.reviewCount || 0), 0)
    elements.activitySummary.textContent = `${learnedTotal} 学习 / ${reviewTotal} 复习`
    if (!items.length) {
      elements.activityHeatmap.className = 'activity-heatmap empty'
      elements.activityHeatmap.textContent = state.token || state.preview ? '暂无学习活跃数据' : '登录后查看学习活跃图'
      return
    }
    const days = normalizeActivityDays(items)
    const weeks = buildActivityWeeks(days)
    const maxTotal = Math.max(1, ...days.map((item) => Number(item.totalCount || 0)))
    elements.activityHeatmap.className = 'activity-heatmap'
    elements.activityHeatmap.innerHTML = `
      <div class="activity-graph" style="--activity-week-count: ${weeks.length}" role="img" aria-label="${escapeHtml(`过去 ${days.length} 天学习活跃图`)}">
        <div class="activity-months" aria-hidden="true">
          <span class="activity-month-spacer"></span>
          ${weeks.map((week, index) => `<span class="activity-month">${escapeHtml(monthLabelForWeek(week, index))}</span>`).join('')}
        </div>
        <div class="activity-grid-row">
          <div class="activity-weekdays" aria-hidden="true">
            <span></span>
            <span>一</span>
            <span></span>
            <span>三</span>
            <span></span>
            <span>五</span>
            <span></span>
          </div>
          <div class="activity-weeks">
            ${weeks.map((week) => `<div class="activity-week">${week.map((item) => renderActivityDay(item, maxTotal)).join('')}</div>`).join('')}
          </div>
        </div>
      </div>
    `
  }

  function activityLevel(total, maxTotal) {
    if (!total) return 0
    if (total >= maxTotal * 0.75) return 4
    if (total >= maxTotal * 0.45) return 3
    if (total >= maxTotal * 0.2) return 2
    return 1
  }

  function normalizeActivityDays(items) {
    const byDate = new Map()
    items.forEach((item) => {
      const dateKey = String(item?.date || '').slice(0, 10)
      const date = parseLocalDate(dateKey)
      if (!date) return
      const learnedCount = Number(item.learnedCount || 0)
      const reviewCount = Number(item.reviewCount || 0)
      byDate.set(dateKey, {
        date: dateKey,
        learnedCount,
        reviewCount,
        totalCount: Number(item.totalCount ?? learnedCount + reviewCount),
      })
    })
    const dateKeys = [...byDate.keys()].sort()
    if (!dateKeys.length) return []
    const startDate = parseLocalDate(dateKeys[0])
    const endDate = parseLocalDate(dateKeys[dateKeys.length - 1])
    const days = []
    for (let cursor = startDate; cursor <= endDate; cursor = addDays(cursor, 1)) {
      const dateKey = toDateKey(cursor)
      days.push(
        byDate.get(dateKey) || {
          date: dateKey,
          learnedCount: 0,
          reviewCount: 0,
          totalCount: 0,
        },
      )
    }
    return days
  }

  function buildActivityWeeks(days) {
    const weeks = []
    if (!days.length) return weeks
    const startDate = parseLocalDate(days[0].date)
    const startOffset = mondayIndex(startDate)
    days.forEach((item, index) => {
      const cellIndex = startOffset + index
      const weekIndex = Math.floor(cellIndex / 7)
      const dayIndex = cellIndex % 7
      if (!weeks[weekIndex]) weeks[weekIndex] = Array(7).fill(null)
      weeks[weekIndex][dayIndex] = item
    })
    return weeks
  }

  function renderActivityDay(item, maxTotal) {
    if (!item) return '<span class="activity-day placeholder" aria-hidden="true"></span>'
    const total = Number(item.totalCount || 0)
    const level = activityLevel(total, maxTotal)
    const title = `${item.date}: 学习 ${item.learnedCount || 0}，复习 ${item.reviewCount || 0}`
    return `<span class="activity-day" data-level="${level}" title="${escapeHtml(title)}" aria-label="${escapeHtml(title)}"></span>`
  }

  function monthLabelForWeek(week, index) {
    const firstDay = week.find(Boolean)
    if (!firstDay) return ''
    if (index === 0) return formatMonth(firstDay.date)
    const monthStart = week.find((item) => item && parseLocalDate(item.date)?.getDate() === 1)
    return monthStart ? formatMonth(monthStart.date) : ''
  }

  function formatMonth(dateKey) {
    const date = parseLocalDate(dateKey)
    return date ? `${date.getMonth() + 1}月` : ''
  }

  function mondayIndex(date) {
    return date ? (date.getDay() + 6) % 7 : 0
  }

  function parseLocalDate(value) {
    const [year, month, day] = String(value || '')
      .split('-')
      .map((part) => Number(part))
    if (!year || !month || !day) return null
    const date = new Date(year, month - 1, day)
    return Number.isNaN(date.getTime()) ? null : date
  }

  function addDays(date, days) {
    const next = new Date(date)
    next.setDate(next.getDate() + days)
    return next
  }

  function toDateKey(date) {
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${date.getFullYear()}-${month}-${day}`
  }

  function renderSystemLogs() {
    if (!state.systemLogs.length) {
      elements.systemLogList.className = 'log-list empty'
      elements.systemLogList.textContent = '暂无系统日志'
      return
    }
    elements.systemLogList.className = 'log-list'
    elements.systemLogList.innerHTML = state.systemLogs
      .map(
        (item) => `
          <div class="log-item">
            <span>${escapeHtml(logTypeLabel(item.type))}</span>
            <div>
              <strong>${escapeHtml(item.title)}</strong>
              <p>${escapeHtml(item.detail || '')}</p>
              <small>${escapeHtml(formatDateTime(item.time || item.createTime))}${item.source ? ` · ${escapeHtml(item.source)}` : ''}</small>
            </div>
          </div>
        `,
      )
      .join('')
  }

  function loadSystemLogs() {
    if (state.preview) {
      renderSystemLogs()
      return Promise.resolve()
    }
    if (!state.token) {
      state.systemLogs = JSON.parse(localStorage.getItem('learning.systemLogs') || '[]')
      renderSystemLogs()
      return Promise.resolve()
    }
    return request('/api/v1/learning/system-logs?limit=80')
      .then((logs) => {
        state.systemLogs = Array.isArray(logs) ? logs : []
        localStorage.removeItem('learning.systemLogs')
        renderSystemLogs()
      })
      .catch((error) => {
        state.systemLogs = JSON.parse(localStorage.getItem('learning.systemLogs') || '[]')
        renderSystemLogs()
        toast(`系统日志加载失败：${error.message}`)
      })
  }

  async function clearLogs() {
    const confirmed = await confirmDelete({
      title: '清空系统日志',
      message: '确认清空系统日志？清空后当前列表中的日志记录将被移除。',
      acceptText: '确认清空',
    })
    if (!confirmed) return
    state.systemLogs = []
    try {
      if (state.preview || !state.token) {
        localStorage.removeItem('learning.systemLogs')
      } else {
        await request('/api/v1/learning/system-logs', { method: 'DELETE' })
      }
      renderSystemLogs()
      toast('系统日志已清空')
    } catch (error) {
      toast(`系统日志清空失败：${error.message}`)
    }
  }


  return {
    renderProfileMetrics,
    loadActivity,
    renderActivityHeatmap,
    activityLevel,
    renderSystemLogs,
    loadSystemLogs,
    clearLogs,
  }
}
