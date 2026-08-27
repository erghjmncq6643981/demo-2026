export function createCalendarView({ state, render, refresh }) {
  function shiftRange(days) {
    if (!state.sceneCalendarRange) return
    const [from, to] = state.sceneCalendarRange
    const nextFrom = new Date(from)
    nextFrom.setDate(nextFrom.getDate() + days)
    const nextTo = new Date(to)
    nextTo.setDate(nextTo.getDate() + days)
    state.sceneCalendarRange = [nextFrom, nextTo]
    render?.()
    refresh?.()
  }

  function resetRange() {
    state.sceneCalendarRange = null
    render?.()
    refresh?.()
  }

  return {
    shiftRange,
    resetRange,
    render,
    refresh,
  }
}
