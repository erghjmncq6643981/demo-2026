import { asArray, number } from '/src/features/learning/scene-plan/model.js'

export function createSceneActions({
  state,
  elements,
  api,
  request,
  activeUnit,
  selectPlan,
  loadSceneData,
  renderSceneView,
  renderCurrentScene,
  setButtonLoading,
  confirmAction,
  toast,
  logEvent,
  sameId,
  createPreviewCookingUnit,
}) {
  async function promoteWord(entryId) {
    const plan = state.currentLearningPlan
    const unit = activeUnit(plan)
    if (!plan || !unit) return
    try {
      if (state.preview) {
        const word = asArray(unit.words).find((item) => sameId(item.id, entryId))
        if (word) {
          word.tier = 'core'
          word.firstLearning = true
          word.assessment ||= { prompt: `请选择“${word.term}”在当前场景中的含义`, options: [word.contextMeaning || word.meaning, '预订旅行行程', '准备一顿晚餐', '参加课堂讨论'], correct_answer: word.contextMeaning || word.meaning }
          unit.coreWordCount = number(unit.coreWordCount) + 1
        }
        renderCurrentScene()
      } else {
        await api.promoteEntry(plan.id, unit.id, entryId)
        await selectPlan(plan.id, { quiet: true, keepStage: true })
      }
      toast('已加入核心词，本场景检查会包含该词')
    } catch (error) {
      logEvent('error', '场景词提升失败', error.message)
      toast(`加入核心词失败：${error.message}`)
    }
  }

  async function completeCurrentUnit() {
    const plan = state.currentLearningPlan
    const unit = activeUnit(plan)
    if (!plan || !unit) return
    const units = asArray(plan.units)
    const currentIndex = units.findIndex((item) => sameId(item.id, unit.id))
    const nextExistingUnit = units.find((item, index) => index > currentIndex && item.status !== 'completed') || units.find((item) => !sameId(item.id, unit.id) && item.status !== 'completed')
    let nextStepText = '完成后可手动生成下一个场景。'
    if (nextExistingUnit) nextStepText = `完成后可直接进入下一篇「${nextExistingUnit.title || '下一场景'}」继续学习。`
    else if (!plan.canGenerateNext) nextStepText = '完成后将完成本计划的全部场景学习。'
    const confirmed = await confirmAction({ title: '完成当前场景', message: `确认完成「${unit.title}」？${nextStepText}`, acceptText: '完成场景' })
    if (!confirmed) return
    setButtonLoading(elements.sceneCompleteUnitBtn, true, '提交中...')
    try {
      const existingUnits = asArray(plan.units)
      const updated = state.preview
        ? (() => {
            unit.status = 'completed'
            plan.learnedCoreWords = number(plan.learnedCoreWords) + number(unit.completedCoreCount)
            plan.completedUnitCount = number(plan.completedUnitCount) + 1
            plan.currentUnitId = null
            plan.canGenerateNext = number(plan.learnedCoreWords) < number(plan.totalCatalogWords)
            return plan
          })()
        : await api.completeUnit(plan.id, unit.id)

      const mergedUnits = existingUnits.map((u) => sameId(u.id, unit.id) ? { ...u, status: 'completed', completedTime: new Date().toISOString() } : u)
      const mergedPlan = { ...plan, ...updated, units: mergedUnits }
      state.currentLearningPlan = mergedPlan
      state.learningPlans = state.learningPlans.map((item) => sameId(item.id, mergedPlan.id) ? mergedPlan : item)

      const currentIndex = mergedUnits.findIndex((u) => sameId(u.id, unit.id))
      const nextRemainingUnit = (currentIndex >= 0 ? mergedUnits.slice(currentIndex + 1).find((item) => item.status !== 'completed') : null)
        || mergedUnits.find((item) => item.status !== 'completed')
        || null
      state.sceneChallengeStage = nextRemainingUnit ? 'learning' : 'overview'
      state.currentSceneWordId = null

      if (state.preview) {
        renderSceneView()
      } else {
        await loadSceneData({ planId: plan.id, keepStage: Boolean(nextRemainingUnit) })
      }

      logEvent('learning', '完成场景学习', `${plan.name} / ${unit.title}`)
      if (nextRemainingUnit) {
        elements.sceneLearningStage?.scrollIntoView({ behavior: 'smooth', block: 'start' })
        toast(`当前场景已完成，已自动进入下一篇「${nextRemainingUnit.title || '下一场景'}」`)
      } else if (mergedPlan.canGenerateNext) {
        toast('场景已完成，可继续生成下一个场景')
      } else {
        toast('恭喜！学习计划已全部完成')
      }
    } catch (error) {
      logEvent('error', '完成场景失败', error.message)
      toast(`完成场景失败：${error.message}`)
    } finally {
      setButtonLoading(elements.sceneCompleteUnitBtn, false)
    }
  }

  async function generateNextUnit() {
    const plan = state.currentLearningPlan
    if (!plan?.canGenerateNext) return
    const confirmed = await confirmAction({ title: '生成下一场景材料', message: '任务将提交至后台异步生成下一批场景材料（若待挑战词超过 50 个将自动均分为多篇）。任务在后台执行，不会中断当前学习，可在任务中心实时查看进度。是否确认生成？', acceptText: '开始生成' })
    if (!confirmed) return
    setButtonLoading(elements.sceneNextUnitBtn, true, '提交中...')
    try {
      if (state.preview) {
        plan.units.push(createPreviewCookingUnit(plan.id, asArray(plan.units).length + 1))
        renderSceneView()
      } else {
        state.sceneScheduledTask = await api.scheduleNext(plan.id, { modelConfigId: elements.scenePlanModelSelect?.value || null, executionMode: 'immediate' })
        await loadSceneData({ planId: plan.id })
      }
      toast('场景材料生成任务已提交，可在任务中心查看进度')
    } catch (error) {
      logEvent('error', '生成下一场景失败', error.message)
      toast(`场景生成失败：${error.message}`)
    } finally {
      setButtonLoading(elements.sceneNextUnitBtn, false)
    }
  }

  async function scheduleNextUnit() {
    const plan = state.currentLearningPlan
    if (!plan?.canGenerateNext || plan.status !== 'active') return
    const confirmed = await confirmAction({ title: '安排低价时段生成', message: '任务会进入任务中心，并在每日 00:00 - 06:00 自动生成场景材料；当前学习场景不会被替换。', acceptText: '安排任务' })
    if (!confirmed) return
    setButtonLoading(elements.sceneScheduleNextUnitBtn, true, '安排中...')
    try {
      if (state.preview) {
        state.sceneScheduledTask = { id: Date.now(), taskName: '批量生成场景材料', status: 'pending', executionMode: 'low_cost_window' }
        toast('设计预览：任务已进入低价时段队列')
      } else {
        state.sceneScheduledTask = await api.scheduleNext(plan.id, { modelConfigId: elements.scenePlanModelSelect?.value || null, executionMode: 'low_cost_window' })
        toast('场景材料任务已安排，可在个人信息 - 任务中心查看')
      }
    } catch (error) {
      logEvent('error', '安排场景生成失败', error.message)
      toast(`安排场景生成失败：${error.message}`)
    } finally {
      setButtonLoading(elements.sceneScheduleNextUnitBtn, false)
    }
  }

  async function generateCards() {
    const plan = state.currentLearningPlan
    const unit = activeUnit(plan)
    if (!plan || !unit) return
    setButtonLoading(elements.sceneGenerateCardsBtn, true, '生成中...')
    try {
      if (state.preview) {
        const targets = asArray(unit.words).filter((word) => ['core', 'review'].includes(word.tier) && ['missing', 'failed'].includes(word.cardStatus))
        targets.forEach((word) => { word.cardStatus = 'ready' })
        state.sceneCardJob = { jobId: Date.now(), unitId: unit.id, successCount: targets.length, failedCount: 0 }
        renderCurrentScene()
        toast(`词卡任务完成：成功 ${targets.length}，失败 0`)
        return
      }
      const retry = state.sceneCardJob && sameId(state.sceneCardJob.unitId, unit.id) && number(state.sceneCardJob.failedCount) > 0
      const path = retry ? `/api/v1/vocabulary-card-jobs/${encodeURIComponent(state.sceneCardJob.jobId)}/retry` : `/api/v1/learning/plans/${encodeURIComponent(plan.id)}/units/${encodeURIComponent(unit.id)}/cards/generate`
      state.sceneCardJob = await request(path, { method: 'POST', body: JSON.stringify({ batchSize: 15 }) })
      toast('词卡任务已提交，正在生成...')
      state.sceneCardJob = await waitForCardJob(state.sceneCardJob)
      await selectPlan(plan.id, { quiet: true })
      if (['pending', 'running'].includes(state.sceneCardJob.status)) {
        toast('词卡仍在后台生成，可稍后再次查看进度')
        return
      }
      const failed = number(state.sceneCardJob.failedCount)
      elements.sceneGenerateCardsBtn.textContent = failed ? `重试失败词 (${failed})` : '补齐词卡'
      const failureReason = state.sceneCardJob.errorMessage ? `，原因：${state.sceneCardJob.errorMessage}` : ''
      toast(`词卡任务完成：成功 ${number(state.sceneCardJob.successCount)}，失败 ${failed}${failureReason}`)
    } catch (error) {
      logEvent('error', '批量词卡生成失败', error.message)
      toast(`批量词卡生成失败：${error.message}`)
    } finally {
      setButtonLoading(elements.sceneGenerateCardsBtn, false)
    }
  }

  async function scheduleCards() {
    const plan = state.currentLearningPlan
    const unit = activeUnit(plan)
    if (!plan || !unit) return
    const confirmed = await confirmAction({ title: '安排低价时段补齐词卡', message: '缺失词卡会在每日 00:00 - 06:00 分批生成，生成结果可在任务中心查看。', acceptText: '安排任务' })
    if (!confirmed) return
    setButtonLoading(elements.sceneScheduleCardsBtn, true, '安排中...')
    try {
      if (state.preview) {
        state.sceneScheduledTask = { id: Date.now(), taskName: '批量生成场景词卡', status: 'pending', executionMode: 'low_cost_window' }
        toast('设计预览：词卡任务已进入低价时段队列')
      } else {
        state.sceneCardJob = await request(`/api/v1/learning/plans/${encodeURIComponent(plan.id)}/units/${encodeURIComponent(unit.id)}/cards/generate`, { method: 'POST', body: JSON.stringify({ batchSize: 15, executionMode: 'low_cost_window' }) })
        toast('词卡任务已安排，可在个人信息 - 任务中心查看')
      }
    } catch (error) {
      logEvent('error', '安排词卡生成失败', error.message)
      toast(`安排词卡生成失败：${error.message}`)
    } finally {
      setButtonLoading(elements.sceneScheduleCardsBtn, false)
    }
  }

  async function generateRelatedWords(unit = activeUnit(state.currentLearningPlan)) {
    const plan = state.currentLearningPlan
    if (!plan || !unit) return
    try {
      if (state.preview) {
        toast('演示模式：已模拟提交场景相关词汇任务')
        return
      }
      const task = await api.generateRelatedWords(plan.id, unit.id, { targetCount: 50 })
      toast(`场景相关词汇任务已提交（${task?.status === 'pending' ? '等待执行' : '执行中'}）`)
    } catch (error) {
      logEvent('error', '生成场景相关词汇失败', error.message)
      toast(`生成场景相关词汇失败：${error.message}`)
    }
  }

  async function waitForCardJob(initialJob) {
    let current = initialJob
    const terminal = new Set(['completed', 'partial_failed', 'failed'])
    if (!current?.jobId || terminal.has(current.status)) return current
    for (let attempt = 0; attempt < 120; attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 1000))
      current = await request(`/api/v1/vocabulary-card-jobs/${encodeURIComponent(current.jobId)}`)
      if (terminal.has(current.status)) return current
    }
    return current
  }

  return { promoteWord, completeCurrentUnit, generateNextUnit, scheduleNextUnit, generateCards, scheduleCards, generateRelatedWords }
}
