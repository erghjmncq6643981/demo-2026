import { asArray } from '/src/features/learning/scene-plan/model.js'

export function createUnitList({ state, api, sameId }) {
  function activeUnit(plan = state.currentLearningPlan) {
    if (!plan) return null
    const units = asArray(plan.units)
    if (plan.currentUnitId != null) {
      return units.find((unit) => sameId(unit.id, plan.currentUnitId))
        || [...units].reverse().find((unit) => unit.status !== 'completed')
        || null
    }
    return [...units].reverse().find((unit) => unit.status !== 'completed') || null
  }

  function mergeCalendarUnits(plan, calendarData) {
    if (!plan || !Array.isArray(calendarData)) return
    const currentUnits = asArray(plan.units)
    const unitMap = new Map(currentUnits.map((u) => [String(u.id), u]))
    calendarData.forEach((day) => {
      asArray(day.units).forEach((u) => {
        const idStr = String(u.id)
        if (unitMap.has(idStr)) {
          const existing = unitMap.get(idStr)
          // 仅合并非空字段，绝不能用日历概要的 null/undefined 覆盖已加载的详情字段（words, relatedWords, learningText 等）
          Object.keys(u).forEach((key) => {
            if (u[key] !== null && u[key] !== undefined) {
              if (Array.isArray(existing[key]) && existing[key].length > 0 && Array.isArray(u[key]) && u[key].length === 0) {
                return
              }
              existing[key] = u[key]
            }
          })
        } else {
          unitMap.set(idStr, { ...u })
        }
      })
    })
    plan.units = Array.from(unitMap.values()).sort((a, b) => (Number(a.unitNo) || 0) - (Number(b.unitNo) || 0))
  }

  async function loadDetail(plan, unitId) {
    if (!plan || !unitId) return null
    const cached = asArray(plan.units).find((unit) => sameId(unit.id, unitId))
    if (cached && asArray(cached.words).length && (cached.learningText || cached.material)) return cached
    const detail = await api.getUnit(plan.id, unitId)
    if (!detail) return null
    const units = asArray(plan.units)
    const idx = units.findIndex((u) => sameId(u.id, unitId))
    if (idx >= 0) {
      units[idx] = { ...units[idx], ...detail }
    } else {
      units.push(detail)
      units.sort((a, b) => (Number(a.unitNo) || 0) - (Number(b.unitNo) || 0))
    }
    plan.units = units
    return detail
  }

  return {
    activeUnit,
    mergeCalendarUnits,
    loadDetail,
  }
}
