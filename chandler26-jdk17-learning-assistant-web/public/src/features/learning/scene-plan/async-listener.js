export function createAsyncListener({ state, refreshCalendar }) {
  let boundHandler = null

  function onTaskUpdated(event) {
    const task = event?.detail
    if (!task) return
    const currentPlanId = state.currentLearningPlan?.id
    if (!currentPlanId) return
    if (task.planId == null || String(task.planId) === String(currentPlanId)) {
      refreshCalendar?.(state.currentLearningPlan)
    }
  }

  function bind() {
    if (boundHandler) return
    boundHandler = onTaskUpdated
    window.addEventListener('learning:ai-task-updated', boundHandler)
  }

  function unbind() {
    if (!boundHandler) return
    window.removeEventListener('learning:ai-task-updated', boundHandler)
    boundHandler = null
  }

  return {
    bind,
    unbind,
  }
}
