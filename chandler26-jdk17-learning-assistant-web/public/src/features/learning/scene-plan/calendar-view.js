import { localDateKey } from '/src/features/learning/scene-plan/model.js'
import { dateFromKey, resolvePlanAnchorDate } from '/src/features/learning/scene-plan/calendar-model.js'

export function createCalendarView({ state, render, refresh }) {
  function currentAnchor() {
    return resolvePlanAnchorDate(state.currentLearningPlan)
  }

  function changeRange(range) {
    if (!['week', 'month'].includes(range)) return
    state.sceneCalendarRange = range
    if (!state.sceneCalendarCursorDate) state.sceneCalendarCursorDate = currentAnchor()
    state.sceneCalendarData = null
    render?.()
    refresh?.(state.currentLearningPlan)
  }

  function changeOffset(offset) {
    const anchor = dateFromKey(state.sceneCalendarCursorDate || currentAnchor())
    if (state.sceneCalendarRange === 'month') {
      anchor.setDate(1)
      anchor.setMonth(anchor.getMonth() + offset)
    } else {
      anchor.setDate(anchor.getDate() + offset * 7)
    }
    state.sceneCalendarCursorDate = localDateKey(anchor)
    state.sceneCalendarData = null
    render?.()
    refresh?.(state.currentLearningPlan)
  }

  function reset() {
    state.sceneCalendarCursorDate = currentAnchor()
    state.sceneCalendarData = null
    render?.()
    refresh?.(state.currentLearningPlan)
  }

  return {
    changeRange,
    changeOffset,
    reset,
    render,
    refresh,
  }
}
