import { escapeHtml, formatDate, monthTitle, pointIcon, statusName } from '/src/shared/text.js'

const weekLabels = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

export function renderCalendar({ monthDate, events, onPrevious, onNext }) {
  const firstDay = new Date(monthDate.getFullYear(), monthDate.getMonth(), 1)
  const startOffset = (firstDay.getDay() + 6) % 7
  const gridStart = new Date(firstDay)
  gridStart.setDate(firstDay.getDate() - startOffset)
  const cells = []
  const todayKey = formatDate(new Date())

  for (let index = 0; index < 42; index += 1) {
    const date = new Date(gridStart)
    date.setDate(gridStart.getDate() + index)
    const dateKey = formatDate(date)
    const allDayEvents = events.filter((event) => formatDate(event.taskDate) === dateKey)
    const dayEvents = allDayEvents.slice(0, 3)
    const overflowCount = Math.max(0, allDayEvents.length - dayEvents.length)
    const outside = date.getMonth() !== monthDate.getMonth()
    const today = dateKey === todayKey
    cells.push(`
      <div class="day ${outside ? 'outside' : ''} ${today ? 'today' : ''}" data-action="open-calendar-day" data-date="${dateKey}">
        <div class="date">${date.getDate()}</div>
        <div class="events">
          ${dayEvents.map(renderCalendarEvent).join('')}
          ${overflowCount ? `<div class="event-more" title="还有 ${overflowCount} 个任务">... +${overflowCount}</div>` : ''}
        </div>
      </div>
    `)
  }

  queueMicrotask(() => {
    document.querySelector('[data-action="calendar-prev"]')?.addEventListener('click', onPrevious)
    document.querySelector('[data-action="calendar-next"]')?.addEventListener('click', onNext)
  })

  return `
    <div class="calendar-shell">
      <div class="calendar-header">
        <div class="calendar-title">
          <strong>${monthTitle(monthDate)}</strong>
          <span>任务日历 / Notion 风格月视图</span>
        </div>
        <div class="nav">
          <button class="icon-btn" type="button" data-action="calendar-prev" aria-label="上个月">‹</button>
          <button class="icon-btn" type="button" data-action="calendar-next" aria-label="下个月">›</button>
        </div>
      </div>
      <div class="calendar-toolbar">
        <button class="btn primary" type="button">月视图</button>
        <button class="btn" type="button">任务</button>
        <button class="btn" type="button">积分</button>
        <button class="btn" type="button">奖励</button>
      </div>
      <div class="calendar-grid">
        ${weekLabels.map((label) => `<div class="dow">${label}</div>`).join('')}
        ${cells.join('')}
      </div>
      <div class="legend">
        <span><b style="background:#ff5c8a"></b> 日任务</span>
        <span><b style="background:#6c63ff"></b> 周任务</span>
        <span><b style="background:#34c759"></b> 月任务</span>
        <span><b style="background:#ff9f43"></b> 奖励事件</span>
      </div>
    </div>
  `
}

function renderCalendarEvent(event) {
  const color = event.taskColor || event.pointColor || '#6c63ff'
  const progress = event.status === 'APPROVED' ? `${pointIcon(event.pointType)} +${event.scoreAwarded || event.basePoints || 0}` : eventScheduleText(event)
  return `
    <button class="event" type="button"
      data-action="open-calendar-event"
      data-task-id="${event.taskId}"
      data-task-date="${escapeHtml(event.taskDate)}"
      style="--event-bg:${hexToRgba(color, 0.12)}; --event-line:${hexToRgba(color, 0.2)}; --event-ink:${color};"
      title="${escapeHtml(event.taskName)} / ${escapeHtml(progress)}">
      <i></i><span>${escapeHtml(event.taskName)}</span><em>${escapeHtml(progress)}</em>
    </button>
  `
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
