import { escapeHtml, formatDateTime } from '/src/shared/text.js'
import { logTypeLabel } from '/src/shared/vocabulary.js'

const WEEKDAY_LABELS = ['日', '一', '二', '三', '四', '五', '六']

export function createActivityProfileFeature(ctx) {
  const { state, elements, request, toast, logEvent, confirmDelete, createPreviewActivity } = ctx
  let activityRequest = null
  let selectedDays = 365
  let loadSeq = 0
  let dayLookup = new Map()
  let selectedDateKey = ''
  let needsScrollToLatest = false
  let resizeObserver = null

  function ensureResizeScrollObserver() {
    if (!elements.activityHeatmap || resizeObserver || typeof ResizeObserver !== 'function') return
    resizeObserver = new ResizeObserver(() => {
      if (!needsScrollToLatest || !elements.activityHeatmap) return
      if (elements.activityHeatmap.scrollWidth > elements.activityHeatmap.clientWidth) {
        elements.activityHeatmap.scrollLeft = elements.activityHeatmap.scrollWidth
        needsScrollToLatest = false
      }
    })
    resizeObserver.observe(elements.activityHeatmap)
  }

  function bindRangeTabs() {
    if (!elements.activityRangeTabs || elements.activityRangeTabs.dataset.bound === 'true') return
    elements.activityRangeTabs.dataset.bound = 'true'
    elements.activityRangeTabs.addEventListener('click', (event) => {
      const btn = event.target.closest?.('.activity-range-tab[data-days]')
      if (!btn) return
      const nextDays = Number(btn.dataset.days)
      if (!nextDays || nextDays === selectedDays) return
      selectedDays = nextDays
      syncRangeTabState()
      loadActivity(selectedDays, { force: true })
    })
  }

  function bindHeatmapInteractions() {
    if (!elements.activityHeatmap || elements.activityHeatmap.dataset.bound === 'true') return
    elements.activityHeatmap.dataset.bound = 'true'

    elements.activityHeatmap.addEventListener('mouseover', (event) => {
      const cell = event.target.closest?.('.activity-day[data-date]')
      if (!cell) return
      showTooltipForCell(cell)
    })

    elements.activityHeatmap.addEventListener('focusin', (event) => {
      const cell = event.target.closest?.('.activity-day[data-date]')
      if (!cell) return
      showTooltipForCell(cell)
    })

    elements.activityHeatmap.addEventListener('mouseleave', () => {
      hideTooltip()
    })

    elements.activityHeatmap.addEventListener('focusout', (event) => {
      if (!elements.activityHeatmap.contains(event.relatedTarget)) {
        hideTooltip()
      }
    })

    elements.activityHeatmap.addEventListener('scroll', () => {
      hideTooltip()
    }, { passive: true })

    elements.activityHeatmap.addEventListener('click', (event) => {
      const cell = event.target.closest?.('.activity-day[data-date]')
      if (!cell) return
      const dateKey = cell.dataset.date || ''
      const item = dayLookup.get(dateKey)
      if (!item) return
      selectedDateKey = dateKey
      elements.activityHeatmap.querySelectorAll('.activity-day.is-selected').forEach((el) => {
        el.classList.remove('is-selected')
      })
      cell.classList.add('is-selected')
      updateSelectedHint(item)
      showTooltipForCell(cell)
    })
  }

  function syncRangeTabState() {
    if (!elements.activityRangeTabs) return
    elements.activityRangeTabs.querySelectorAll('.activity-range-tab[data-days]').forEach((btn) => {
      const active = Number(btn.dataset.days) === selectedDays
      btn.classList.toggle('active', active)
      btn.setAttribute('aria-selected', active ? 'true' : 'false')
    })
  }

  function renderProfileMetrics() {
    const wordbookCount = state.wordbooks.length
    const wordCount = state.wordbooks.reduce((sum, item) => sum + Number(item.entryCount || 0), 0)
    const dueCount = state.wordbooks.reduce((sum, item) => sum + Number(item.dueCount || 0), 0)
    elements.wordbookCount.textContent = String(wordbookCount)
    elements.wordCount.textContent = String(wordCount)
    elements.dueCount.textContent = String(dueCount)
  }

  function loadActivity(days = selectedDays, { force = false } = {}) {
    bindRangeTabs()
    bindHeatmapInteractions()
    selectedDays = Number(days) || selectedDays || 365
    syncRangeTabState()

    if (state.preview) {
      state.activity = createPreviewActivity(selectedDays)
      renderActivityHeatmap()
      return Promise.resolve(state.activity)
    }
    if (!state.token) {
      state.activity = null
      renderActivityHeatmap()
      return Promise.resolve()
    }
    if (activityRequest && !force) return activityRequest
    const seq = ++loadSeq
    if (elements.activityHeatmap) {
      elements.activityHeatmap.className = 'activity-heatmap loading'
      elements.activityHeatmap.textContent = '正在加载学习活跃数据...'
      hideTooltip()
    }
    const req = request(`/api/v1/learning/activity?days=${encodeURIComponent(selectedDays)}`)
      .then((activity) => {
        if (seq !== loadSeq) return activity
        state.activity = activity
        renderActivityHeatmap()
        return activity
      })
      .catch((error) => {
        if (seq !== loadSeq) return null
        logEvent('error', '学习活跃图加载失败', error.message)
        renderActivityHeatmap()
        throw error
      })
      .finally(() => {
        if (activityRequest === req) {
          activityRequest = null
        }
      })
    activityRequest = req
    return req
  }

  function renderActivityHeatmap() {
    bindRangeTabs()
    bindHeatmapInteractions()
    if (!elements.activityHeatmap) return

    const items = Array.isArray(state.activity?.items) ? state.activity.items : []
    const days = normalizeActivityDays(items)
    const stats = computeActivitySummary(state.activity, days)

    if (elements.activityTotalPill) {
      elements.activityTotalPill.textContent = `共 ${stats.totalActivityCount} 次活跃`
    }
    if (elements.activityActiveDaysPill) {
      elements.activityActiveDaysPill.textContent = `活跃 ${stats.activeDays} 天`
    }
    if (elements.activityStreakPill) {
      elements.activityStreakPill.textContent = `连续 ${stats.currentStreak} 天`
      elements.activityStreakPill.classList.toggle('is-streak', stats.currentStreak > 0)
      elements.activityStreakPill.title = stats.longestStreak > 0
        ? `周期内最长连续活跃 ${stats.longestStreak} 天`
        : '连续每天完成有效学习即可点亮连续记录'
    }
    if (elements.activitySummary) {
      const summaryParts = [
        `${stats.learnedTotal} 学习`,
        `${stats.reviewTotal} 复习`,
        `${stats.sceneCompletedTotal} 场景`,
        `${stats.ruminationTotal} 反刍`,
      ]
      if (stats.wordAddedTotal > 0) {
        summaryParts.push(`${stats.wordAddedTotal} 加词`)
      }
      elements.activitySummary.textContent = summaryParts.join(' / ')
    }

    hideTooltip()
    dayLookup = new Map(days.map((item) => [item.date, item]))

    if (!days.length) {
      elements.activityHeatmap.className = 'activity-heatmap empty'
      elements.activityHeatmap.textContent = state.token || state.preview ? '暂无学习活跃数据' : '登录后查看学习活跃图'
      if (elements.activitySelectedHint) {
        elements.activitySelectedHint.textContent = '悬停或点击日期格子查看当日学习明细'
      }
      return
    }

    const weeks = buildActivityWeeks(days)
    const thresholds = buildActivityThresholds(days)
    elements.activityHeatmap.className = 'activity-heatmap'
    elements.activityHeatmap.innerHTML = `
      <div class="activity-graph" style="--activity-week-count: ${weeks.length}" role="region" aria-label="${escapeHtml(`过去 ${days.length} 天学习活跃图`)}">
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
            ${weeks.map((week) => `<div class="activity-week">${week.map((item) => renderActivityDay(item, thresholds)).join('')}</div>`).join('')}
          </div>
        </div>
      </div>
    `

    // 默认自动滚动到最右侧「今天」，若此时页面尚未展开则由 ResizeObserver 在首次可见时补滚
    ensureResizeScrollObserver()
    needsScrollToLatest = true
    const scrollToLatest = () => {
      if (!elements.activityHeatmap || !needsScrollToLatest) return
      if (elements.activityHeatmap.scrollWidth > elements.activityHeatmap.clientWidth) {
        elements.activityHeatmap.scrollLeft = elements.activityHeatmap.scrollWidth
        needsScrollToLatest = false
      }
    }
    scrollToLatest()
    if (typeof requestAnimationFrame === 'function') {
      requestAnimationFrame(scrollToLatest)
    }

    if (selectedDateKey && dayLookup.has(selectedDateKey)) {
      updateSelectedHint(dayLookup.get(selectedDateKey))
    } else if (elements.activitySelectedHint) {
      elements.activitySelectedHint.textContent = '悬停或点击日期格子查看当日学习明细'
    }
  }

  function computeActivitySummary(activity, days) {
    const learnedTotal = activity?.learnedTotal ?? days.reduce((sum, item) => sum + item.learnedCount, 0)
    const wordAddedTotal = activity?.wordAddedTotal ?? days.reduce((sum, item) => sum + item.wordAddedCount, 0)
    const reviewTotal = activity?.reviewTotal ?? days.reduce((sum, item) => sum + item.reviewCount, 0)
    const sceneCompletedTotal = activity?.sceneCompletedTotal ?? days.reduce((sum, item) => sum + item.sceneCompletedCount, 0)
    const articleCompletedTotal = activity?.articleCompletedTotal ?? days.reduce((sum, item) => sum + item.articleCompletedCount, 0)
    const ruminationTotal = activity?.ruminationTotal ?? days.reduce((sum, item) => sum + item.ruminationCount, 0)
    const totalActivityCount = activity?.totalActivityCount ?? days.reduce((sum, item) => sum + item.totalCount, 0)

    let activeDays = activity?.activeDays
    let longestStreak = activity?.longestStreak
    let currentStreak = activity?.currentStreak
    if (activeDays == null || longestStreak == null || currentStreak == null) {
      let computedActive = 0
      let computedLongest = 0
      let running = 0
      days.forEach((item) => {
        if (item.totalCount > 0) {
          computedActive += 1
          running += 1
          if (running > computedLongest) computedLongest = running
        } else {
          running = 0
        }
      })
      let idx = days.length - 1
      if (idx >= 0 && days[idx].totalCount <= 0) idx -= 1
      let computedCurrent = 0
      while (idx >= 0 && days[idx].totalCount > 0) {
        computedCurrent += 1
        idx -= 1
      }
      activeDays = activeDays ?? computedActive
      longestStreak = longestStreak ?? computedLongest
      currentStreak = currentStreak ?? computedCurrent
    }

    return {
      learnedTotal,
      wordAddedTotal,
      reviewTotal,
      sceneCompletedTotal,
      articleCompletedTotal,
      ruminationTotal,
      totalActivityCount,
      activeDays,
      currentStreak,
      longestStreak,
    }
  }

  /**
   * 基于活跃日分位数构建色阶阈值，避免单日爆发峰值将日常学习压成单一浅色。
   */
  function buildActivityThresholds(days) {
    const activeTotals = days
      .map((item) => Number(item.totalCount || 0))
      .filter((value) => value > 0)
      .sort((a, b) => a - b)
    if (!activeTotals.length) {
      return { t2: 2, t3: 4, t4: 7 }
    }
    const max = activeTotals[activeTotals.length - 1]
    if (max <= 4) {
      return { t2: 2, t3: 3, t4: 4 }
    }
    if (activeTotals.length < 4) {
      return {
        t2: Math.max(2, Math.ceil(max * 0.25)),
        t3: Math.max(3, Math.ceil(max * 0.55)),
        t4: Math.max(4, Math.ceil(max * 0.8)),
      }
    }
    const quantile = (sorted, ratio) => {
      const pos = Math.min(sorted.length - 1, Math.max(0, Math.floor((sorted.length - 1) * ratio)))
      return sorted[pos]
    }
    const t2 = Math.max(2, quantile(activeTotals, 0.35))
    const t3 = Math.max(t2 + 1, quantile(activeTotals, 0.65))
    const t4 = Math.max(t3 + 1, quantile(activeTotals, 0.88))
    return { t2, t3, t4 }
  }

  function activityLevel(total, thresholdsOrMax) {
    const value = Number(total || 0)
    if (value <= 0) return 0
    if (typeof thresholdsOrMax === 'object' && thresholdsOrMax !== null) {
      if (value >= thresholdsOrMax.t4) return 4
      if (value >= thresholdsOrMax.t3) return 3
      if (value >= thresholdsOrMax.t2) return 2
      return 1
    }
    const maxTotal = Math.max(1, Number(thresholdsOrMax || 1))
    if (value >= maxTotal * 0.75) return 4
    if (value >= maxTotal * 0.45) return 3
    if (value >= maxTotal * 0.2) return 2
    return 1
  }

  function normalizeActivityDays(items) {
    const byDate = new Map()
    items.forEach((item) => {
      const dateKey = String(item?.date || '').slice(0, 10)
      const date = parseLocalDate(dateKey)
      if (!date) return
      const learnedCount = Number(item.learnedCount || 0)
      const wordAddedCount = Number(item.wordAddedCount || 0)
      const reviewCount = Number(item.reviewCount || 0)
      const sceneCompletedCount = Number(item.sceneCompletedCount || 0)
      const articleCompletedCount = Number(item.articleCompletedCount || 0)
      const ruminationCount = Number(item.ruminationCount || 0)
      const studySeconds = Number(item.studySeconds || 0)
      byDate.set(dateKey, {
        date: dateKey,
        learnedCount,
        wordAddedCount,
        reviewCount,
        sceneCompletedCount,
        articleCompletedCount,
        ruminationCount,
        studySeconds,
        totalCount: Number(
          item.totalCount
            ?? learnedCount + wordAddedCount + reviewCount + sceneCompletedCount + articleCompletedCount + ruminationCount,
        ),
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
          wordAddedCount: 0,
          reviewCount: 0,
          sceneCompletedCount: 0,
          articleCompletedCount: 0,
          ruminationCount: 0,
          studySeconds: 0,
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

  function buildNonZeroBreakdown(item) {
    const parts = []
    if (item.learnedCount > 0) parts.push({ label: '学习', value: item.learnedCount })
    if (item.reviewCount > 0) parts.push({ label: '复习', value: item.reviewCount })
    if (item.sceneCompletedCount > 0) parts.push({ label: '场景', value: item.sceneCompletedCount })
    if (item.ruminationCount > 0) parts.push({ label: '反刍', value: item.ruminationCount })
    if (item.wordAddedCount > 0) parts.push({ label: '加词', value: item.wordAddedCount })
    if (item.articleCompletedCount > 0) parts.push({ label: '精读', value: item.articleCompletedCount })
    return parts
  }

  function formatStudyDuration(seconds) {
    const totalSec = Math.max(0, Math.round(Number(seconds || 0)))
    if (totalSec <= 0) return ''
    if (totalSec < 60) return `${totalSec} 秒`
    const minutes = Math.floor(totalSec / 60)
    if (minutes < 60) return `${minutes} 分钟`
    const hours = Math.floor(minutes / 60)
    const remainMinutes = minutes % 60
    return remainMinutes > 0 ? `${hours} 小时 ${remainMinutes} 分钟` : `${hours} 小时`
  }

  function formatDateWithWeekday(dateKey) {
    const date = parseLocalDate(dateKey)
    if (!date) return dateKey
    return `${dateKey} 周${WEEKDAY_LABELS[date.getDay()]}`
  }

  function buildAccessibleSummary(item) {
    const dateText = formatDateWithWeekday(item.date)
    if (!item.totalCount) return `${dateText}：当日暂无学习记录`
    const parts = buildNonZeroBreakdown(item).map((entry) => `${entry.label} ${entry.value}`)
    const duration = formatStudyDuration(item.studySeconds)
    if (duration) parts.push(`时长 ${duration}`)
    return `${dateText}：共 ${item.totalCount} 次活跃（${parts.join('，')}）`
  }

  function renderActivityDay(item, thresholds) {
    if (!item) return '<span class="activity-day placeholder" aria-hidden="true"></span>'
    const total = Number(item.totalCount || 0)
    const level = activityLevel(total, thresholds)
    const label = buildAccessibleSummary(item)
    const selectedClass = selectedDateKey && selectedDateKey === item.date ? ' is-selected' : ''
    return `<button type="button" class="activity-day${selectedClass}" data-date="${escapeHtml(item.date)}" data-level="${level}" aria-label="${escapeHtml(label)}"></button>`
  }

  function showTooltipForCell(cell) {
    const tooltip = elements.activityTooltip
    const stage = elements.activityHeatmapStage
    if (!tooltip || !stage) return
    const item = dayLookup.get(cell.dataset.date || '')
    if (!item) return

    const dateText = formatDateWithWeekday(item.date)
    const breakdown = buildNonZeroBreakdown(item)
    const duration = formatStudyDuration(item.studySeconds)

    tooltip.innerHTML = `
      <div class="activity-tooltip-head">
        <strong>${escapeHtml(dateText)}</strong>
        <span class="activity-tooltip-total">${item.totalCount > 0 ? `${item.totalCount} 次活跃` : '无记录'}</span>
      </div>
      ${breakdown.length
        ? `<div class="activity-tooltip-metrics">${breakdown.map((entry) => `<span>${escapeHtml(entry.label)} <b>${entry.value}</b></span>`).join('')}</div>`
        : '<div class="activity-tooltip-empty">当日暂无学习记录</div>'}
      ${duration ? `<div class="activity-tooltip-duration">有效学习时长：${escapeHtml(duration)}</div>` : ''}
    `
    tooltip.classList.remove('hidden')
    tooltip.setAttribute('aria-hidden', 'false')

    const stageRect = stage.getBoundingClientRect()
    const cellRect = cell.getBoundingClientRect()
    const tipWidth = tooltip.offsetWidth || 200
    const tipHeight = tooltip.offsetHeight || 64

    let left = cellRect.left - stageRect.left + cellRect.width / 2 - tipWidth / 2
    const maxLeft = Math.max(4, stageRect.width - tipWidth - 4)
    left = Math.min(Math.max(4, left), maxLeft)

    let top = cellRect.top - stageRect.top - tipHeight - 10
    if (top < -stageRect.top + 8) {
      top = cellRect.bottom - stageRect.top + 10
    }

    tooltip.style.left = `${Math.round(left)}px`
    tooltip.style.top = `${Math.round(top)}px`
  }

  function hideTooltip() {
    if (!elements.activityTooltip) return
    elements.activityTooltip.classList.add('hidden')
    elements.activityTooltip.setAttribute('aria-hidden', 'true')
  }

  function updateSelectedHint(item) {
    if (!elements.activitySelectedHint || !item) return
    elements.activitySelectedHint.textContent = buildAccessibleSummary(item)
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
    return request('/api/v1/learning/system-logs/page?page=1&pageSize=40')
      .then((result) => {
        // 日志接口按页返回，个人中心只展示最近一页，避免历史记录拖慢首屏。
        state.systemLogs = Array.isArray(result) ? result : (Array.isArray(result?.items) ? result.items : [])
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
    buildActivityThresholds,
    activityLevel,
    renderSystemLogs,
    loadSystemLogs,
    clearLogs,
  }
}
