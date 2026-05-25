import { escapeHtml, formatDate, monthTitle, pointIcon, statusName } from '/src/shared/text.js'

const weekLabels = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

export function renderCalendar({ monthDate, events, viewMode = 'month', eventKind = 'tasks' }) {
  const isWeekView = viewMode === 'week'
  const gridStart = isWeekView ? getWeekStart(monthDate) : getMonthGridStart(monthDate)
  const cellCount = isWeekView ? 7 : 42
  const todayKey = formatDate(new Date())
  const cells = []

  for (let index = 0; index < cellCount; index += 1) {
    const date = new Date(gridStart)
    date.setDate(gridStart.getDate() + index)
    const dateKey = formatDate(date)
    const allDayEvents = events.filter((event) => formatDate(event.date) === dateKey)
    const dayEvents = allDayEvents.slice(0, 3)
    const overflowCount = Math.max(0, allDayEvents.length - dayEvents.length)
    const outside = !isWeekView && date.getMonth() !== monthDate.getMonth()
    const today = dateKey === todayKey
    cells.push(`
      <div class="day ${outside ? 'outside' : ''} ${today ? 'today' : ''}" data-action="open-calendar-day" data-date="${dateKey}">
        <div class="date">${date.getDate()}${today ? '<span>今天</span>' : ''}</div>
        <div class="events">
          ${dayEvents.map(renderCalendarEvent).join('')}
          ${overflowCount ? `<div class="event-more" title="还有 ${overflowCount} 条记录">... +${overflowCount}</div>` : ''}
        </div>
      </div>
    `)
  }

  return `
    <div class="calendar-shell">
      <div class="calendar-header">
        <div class="calendar-title">
          <strong>${isWeekView ? weekTitle(monthDate) : monthTitle(monthDate)}</strong>
          <span>任务日历 / Notion 风格${isWeekView ? '周' : '月'}视图</span>
        </div>
        <div class="nav">
          <button class="icon-btn" type="button" data-action="calendar-prev" aria-label="${isWeekView ? '上一周' : '上个月'}">‹</button>
          <button class="small-btn today-btn" type="button" data-action="calendar-today">今天</button>
          <button class="icon-btn" type="button" data-action="calendar-next" aria-label="${isWeekView ? '下一周' : '下个月'}">›</button>
        </div>
      </div>
      <div class="calendar-toolbar">
        <div class="calendar-toolbar-group">
          <button class="btn ${isWeekView ? '' : 'primary'}" type="button" data-calendar-view-mode="month">月视图</button>
          <button class="btn ${isWeekView ? 'primary' : ''}" type="button" data-calendar-view-mode="week">周视图</button>
        </div>
        <div class="calendar-toolbar-group calendar-kind-group">
          <button class="btn ${eventKind === 'tasks' ? 'primary' : ''}" type="button" data-calendar-event-kind="tasks">任务</button>
          <button class="btn ${eventKind === 'points' ? 'primary' : ''}" type="button" data-calendar-event-kind="points">积分</button>
          <button class="btn ${eventKind === 'rewards' ? 'primary' : ''}" type="button" data-calendar-event-kind="rewards">奖励</button>
        </div>
      </div>
      <div class="calendar-grid ${isWeekView ? 'week-view' : ''}">
        ${weekLabels.map((label) => `<div class="dow">${label}</div>`).join('')}
        ${cells.join('')}
      </div>
      <div class="legend">
        ${renderLegend(eventKind)}
      </div>
    </div>
  `
}

function renderCalendarEvent(event) {
  const color = event.color || event.taskColor || event.pointColor || '#6c63ff'
  const progress = event.subtitle || eventScheduleText(event)
  const title = event.kind === 'points'
    ? `${event.changeAmount >= 0 ? '+' : '-'}${Math.abs(Number(event.changeAmount || 0))} ${pointIcon(event.pointType)}`
    : event.title || event.taskName || '任务'
  const compactTitle = truncateTitle(title, 6)
  return `
    <button class="event" type="button"
      data-action="open-calendar-event"
      data-calendar-event-id="${escapeHtml(event.uid || '')}"
      data-calendar-event-kind="${escapeHtml(event.kind || 'tasks')}"
      data-task-id="${escapeHtml(event.taskId || '')}"
      data-task-date="${escapeHtml(event.date || event.taskDate)}"
      style="--event-bg:${hexToRgba(color, 0.12)}; --event-line:${hexToRgba(color, 0.2)}; --event-ink:${color};"
      title="${escapeHtml(title)} / ${escapeHtml(progress)}">
      <i></i><span>${escapeHtml(compactTitle)}</span>
    </button>
  `
}

function truncateTitle(title, length) {
  const chars = Array.from(String(title || '任务'))
  return chars.length > length ? `${chars.slice(0, length).join('')}...` : chars.join('')
}

function renderLegend(eventKind) {
  if (eventKind === 'points') {
    return `
      <span><b style="background:#22c55e"></b> 积分增加</span>
      <span><b style="background:#ef4444"></b> 积分扣减</span>
      <span><b style="background:#f59e0b"></b> 积分流水</span>
    `
  }
  if (eventKind === 'rewards') {
    return `
      <span><b style="background:#ff9f43"></b> 兑换申请</span>
      <span><b style="background:#22c55e"></b> 已完成兑换</span>
      <span><b style="background:#ef4444"></b> 已拒绝兑换</span>
    `
  }
  return `
    <span><b style="background:#ff5c8a"></b> 日任务</span>
    <span><b style="background:#6c63ff"></b> 周任务</span>
    <span><b style="background:#34c759"></b> 月任务</span>
  `
}

function getMonthGridStart(monthDate) {
  const firstDay = new Date(monthDate.getFullYear(), monthDate.getMonth(), 1)
  const startOffset = (firstDay.getDay() + 6) % 7
  const gridStart = new Date(firstDay)
  gridStart.setDate(firstDay.getDate() - startOffset)
  return gridStart
}

function getWeekStart(date) {
  const weekStart = new Date(date)
  const offset = (weekStart.getDay() + 6) % 7
  weekStart.setDate(weekStart.getDate() - offset)
  return weekStart
}

function weekTitle(date) {
  const start = getWeekStart(date)
  const end = new Date(start)
  end.setDate(start.getDate() + 6)
  return `${start.getFullYear()} 年 ${start.getMonth() + 1} 月 ${start.getDate()} 日 - ${end.getMonth() + 1} 月 ${end.getDate()} 日`
}

function eventScheduleText(event) {
  if (event.status && event.status !== 'PENDING') {
    return statusName(event.status)
  }
  const schedule = parseSchedule(event.scheduleJson)
  if (event.periodType === 'WEEKLY' || schedule.type === 'WEEKLY') {
    return `待完成 ${schedule.requiredCount || 1} 次`
  }
  if (event.periodType === 'MONTHLY' || schedule.type === 'MONTHLY') {
    return `本月 ${schedule.requiredCount || 1} 次`
  }
  const startHour = schedule.timeRange?.startHour ?? schedule.startHour ?? 6
  const endHour = schedule.timeRange?.endHour ?? schedule.endHour ?? 22
  return `${formatHour(startHour)}-${formatHour(endHour)}`
}

function formatHour(hour) {
  const value = Number(hour)
  if (!Number.isFinite(value)) return '06:00'
  return `${String(value).padStart(2, '0')}:00`
}

function parseSchedule(scheduleJson) {
  try {
    return JSON.parse(scheduleJson || '{}')
  } catch {
    return {}
  }
}

function hexToRgba(hex, alpha) {
  if (!hex || !hex.startsWith('#')) {
    return `rgba(108, 99, 255, ${alpha})`
  }
  const normalized = hex.length === 4
    ? `#${hex[1]}${hex[1]}${hex[2]}${hex[2]}${hex[3]}${hex[3]}`
    : hex
  const value = Number.parseInt(normalized.slice(1), 16)
  if (Number.isNaN(value)) {
    return `rgba(108, 99, 255, ${alpha})`
  }
  const red = (value >> 16) & 255
  const green = (value >> 8) & 255
  const blue = value & 255
  return `rgba(${red}, ${green}, ${blue}, ${alpha})`
}
