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
          Object.assign(unitMap.get(idStr), u)
        } else {
          unitMap.set(idStr, u)
        }
      })
    })
    plan.units = Array.from(unitMap.values()).sort((a, b) => (Number(a.unitNo) || 0) - (Number(b.unitNo) || 0))
  }

  async function loadDetail(plan, unitId) {
    if (!plan || !unitId) return null
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
