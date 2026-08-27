import { asArray } from '/src/features/learning/scene-plan/model.js'

export function createPlanManager({ state, api, sameId, loadSceneData, toast, logEvent, confirmAction }) {
  function targetPlan(planId = null) {
    if (planId != null) {
      return asArray(state.learningPlans).find((item) => sameId(item.id, planId))
        || state.currentLearningPlan
    }
    return asArray(state.learningPlans).find((item) => sameId(item.id, state.currentPlanEditId))
      || state.currentLearningPlan
      || null
  }

  async function createPlan(payload) {
    const plan = await api.createPlan(payload)
    toast('场景学习计划创建成功')
    logEvent('learning', '创建场景学习计划', plan.name)
    await loadSceneData({ preferredPlanId: plan.id })
    return plan
  }

  async function updatePlan(planId, payload) {
    const plan = await api.updatePlan(planId, payload)
    toast('场景学习计划更新成功')
    logEvent('learning', '更新场景学习计划', plan.name)
    await loadSceneData({ preferredPlanId: plan.id })
    return plan
  }

  async function pausePlan(plan) {
    const confirmed = await confirmAction('暂停学习计划后，AI 每日自动安排将暂停。确认暂停？')
    if (!confirmed) return null
    const result = await api.pausePlan(plan.id)
    toast('学习计划已暂停')
    logEvent('learning', '暂停场景学习计划', plan.name)
    await loadSceneData({ preferredPlanId: plan.id })
    return result
  }

  async function resumePlan(plan) {
    const result = await api.resumePlan(plan.id)
    toast('学习计划已恢复')
    logEvent('learning', '恢复场景学习计划', plan.name)
    await loadSceneData({ preferredPlanId: plan.id })
    return result
  }

  async function cancelPlan(plan) {
    const confirmed = await confirmAction('取消计划后无法重新恢复，未完成的场景将停止。确认取消？')
    if (!confirmed) return null
    const result = await api.cancelPlan(plan.id)
    toast('学习计划已取消')
    logEvent('learning', '取消场景学习计划', plan.name)
    await loadSceneData()
    return result
  }

  return {
    targetPlan,
    createPlan,
    updatePlan,
    pausePlan,
    resumePlan,
    cancelPlan,
  }
}
