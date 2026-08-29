import { hideModal, showModal } from '/src/shared/modal.js'
import { asArray, localDateKey, number } from '/src/features/learning/scene-plan/model.js'
import { pendingChallengeWords, isWordComplete } from '/src/features/learning/scene-plan/challenge-model.js'

export function createSceneOverview({
  state,
  elements,
  api,
  unitList,
  activeUnit,
  calendarDates,
  calendarTitle,
  dateFromKey,
  formatCalendarDate,
  unitDateKey,
  unitStatusLabel,
  unitsForDate,
  createPreviewCookingUnit,
  loadSceneData,
  loadSceneNote,
  startLearning,
  renderCurrentScene,
  setButtonLoading,
  confirmAction,
  toast,
  logEvent,
  escapeHtml,
  sameId,
}) {
  function dateKey(date) {
    return localDateKey(date)
  }

  function closeVocabularyPreview() {
    hideModal(elements.sceneVocabularyPreviewModal)
  }

  async function openVocabularyPreview({ date, unitId } = {}) {
    const plan = state.currentLearningPlan
    if (!plan) return
    const selectedUnits = unitId
      ? asArray(plan.units).filter((unit) => sameId(unit.id, unitId))
      : unitsForDate(plan, date)

    function renderModal() {
      const displayDate = date || unitDateKey(selectedUnits[0])
      let pendingTotal = 0
      let completedTotal = 0
      selectedUnits.forEach((unit) => {
        const coreWords = asArray(unit.words).filter((word) => word.tier === 'core')
        const summaryWords = coreWords.length
          ? coreWords
          : asArray(unit.pendingChallengeWords).map((word) => typeof word === 'string' ? { term: word, tier: 'core' } : word)
        summaryWords.forEach((word) => isWordComplete(word) ? completedTotal++ : pendingTotal++)
      })
      elements.sceneVocabularyPreviewTitle.textContent = displayDate
        ? `${formatCalendarDate(new Date(`${displayDate}T12:00:00`), true)} · 场景词汇`
        : '场景词汇'
      elements.sceneVocabularyPreviewSummary.textContent = selectedUnits.length
        ? `${selectedUnits.length} 个场景，待挑战 ${pendingTotal} 词 · 已完成 ${completedTotal} 词`
        : '该日期的场景尚未生成，生成后即可预览具体词汇。'
      elements.sceneVocabularyPreviewList.className = selectedUnits.length
        ? 'scene-vocabulary-preview-list'
        : 'scene-vocabulary-preview-list empty'
      elements.sceneVocabularyPreviewList.innerHTML = selectedUnits.length
        ? selectedUnits.map((unit) => {
          const coreWords = asArray(unit.words).filter((word) => word.tier === 'core')
          const displayWords = coreWords.length
            ? coreWords
            : asArray(unit.pendingChallengeWords).map((word) => typeof word === 'string' ? { term: word, tier: 'core' } : word)
          const unitPending = displayWords.filter((w) => !isWordComplete(w)).length
          const pillText = unitPending > 0 ? `${unitPending} 待挑战词` : '已全部完成'
          return `<section class="scene-vocabulary-preview-group">
            <div class="scene-vocabulary-preview-heading"><div><strong>${escapeHtml(unit.title || '场景单元')}</strong><small>Scene ${number(unit.unitNo)} · ${unitStatusLabel(unit)}</small></div><span class="mini-pill ${unitPending === 0 ? 'ok' : ''}">${pillText}</span></div>
            ${displayWords.length ? `<div class="scene-vocabulary-preview-words">${displayWords.map((word, index) => {
              const complete = isWordComplete(word)
              const phonetic = word.phonetic
                ? (word.phonetic.startsWith('/') || word.phonetic.startsWith('[') ? word.phonetic : `/${word.phonetic}/`)
                : ''
              const meaning = word.contextMeaning || word.meaning || '暂无释义'
              return `<div class="scene-vocabulary-preview-word ${complete ? 'completed' : ''}"><span class="scene-vocabulary-preview-index">${index + 1}</span><div class="scene-vocabulary-preview-word-content"><div class="scene-vocabulary-preview-topline"><strong>${escapeHtml(word.term)}</strong>${phonetic ? `<small>${escapeHtml(phonetic)}</small>` : ''}</div><p class="scene-vocabulary-preview-meaning">${escapeHtml(meaning)}</p></div><span class="scene-vocabulary-preview-status-pill ${complete ? 'completed' : 'pending'}">${complete ? '已完成' : (word.masteryRequirement === 'spelling' ? '待拼写' : '待认读')}</span></div>`
            }).join('')}</div>` : '<div class="empty">加载中...</div>'}
          </section>`
        }).join('')
        : '暂无词汇'
    }

    renderModal()
    showModal(elements.sceneVocabularyPreviewModal)

    const unitsNeedDetail = selectedUnits.filter((u) => !asArray(u.words).length && !asArray(u.pendingChallengeWords).length)
    if (unitsNeedDetail.length > 0 && !state.preview && api?.getUnit) {
      try {
        await Promise.all(unitsNeedDetail.map(async (u) => {
          const detail = await api.getUnit(plan.id, u.id)
          if (detail) {
            Object.assign(u, detail)
          }
        }))
        renderModal()
      } catch (err) {
        logEvent('error', '加载场景词汇详情失败', err?.message)
      }
    }
  }

  function isDayGenerating(key) {
    if (!key) return false
    const dayData = asArray(state.sceneCalendarData).find((item) => String(item?.date || '').slice(0, 10) === key)
    return Boolean(dayData?.generating)
  }

  function renderCalendar(plan) {
    if (!elements.sceneCalendar) return
    const range = state.sceneCalendarRange || 'week'
    if (!plan) {
      elements.sceneCalendar.className = 'scene-calendar empty'
      elements.sceneCalendar.textContent = '选择计划后查看学习日历'
      if (elements.sceneCalendarTitle) elements.sceneCalendarTitle.textContent = range === 'month' ? `${new Date().getFullYear()}年${new Date().getMonth() + 1}月` : calendarTitle(calendarDates())
      renderOverviewUnits(null)
      return
    }
    const dates = calendarDates()
    if (elements.sceneCalendarTitle) elements.sceneCalendarTitle.textContent = calendarTitle(dates)
    const today = dateFromKey(localDateKey())
    const todayKey = dateKey(today)
    const planStartKey = plan.startTime ? plan.startTime.split('T')[0] : null
    const planEndKey = plan.endTime ? plan.endTime.split('T')[0] : null
    const remainingWords = Math.max(0, number(plan.totalCatalogWords) - number(plan.learnedCoreWords))
    let suggestedDailyCount = 8
    if (plan.endTime) {
      const planEnd = dateFromKey(plan.endTime.split('T')[0])
      const startForRemaining = planStartKey && todayKey < planStartKey ? dateFromKey(planStartKey) : new Date(today.getTime())
      startForRemaining.setHours(12, 0, 0, 0)
      planEnd.setHours(12, 0, 0, 0)
      const diffTime = planEnd.getTime() - startForRemaining.getTime()
      const remainingDays = diffTime <= 0 ? 1 : Math.ceil(diffTime / (1000 * 3600 * 24)) + 1
      if (remainingDays > 0) suggestedDailyCount = Math.max(8, Math.ceil(remainingWords / remainingDays))
    }
    const dayDataFor = (key) => asArray(state.sceneCalendarData).find((item) => String(item?.date || '').slice(0, 10) === key)
    elements.sceneCalendar.className = `scene-calendar ${range}`
    elements.sceneCalendar.innerHTML = `<div class="scene-calendar-grid">${dates.map((date) => {
      const key = dateKey(date)
      const isToday = key === todayKey
      const isPast = key < todayKey
      const withinPlan = (!planStartKey || key >= planStartKey) && (!planEndKey || key <= planEndKey)
      const units = unitsForDate(plan, key)
      const dayData = dayDataFor(key)
      const isGenerating = isDayGenerating(key)
      const generated = number(dayData?.generatedUnitCount) > 0 || units.length > 0
      const pendingCount = dayData ? number(dayData.pendingChallengeCount) : units.reduce((sum, unit) => sum + pendingChallengeWords(unit).length, 0)
      const overdue = number(dayData?.overdueCount) > 0 || (isPast && pendingCount > 0)
      const isCompleted = units.length && units.every((unit) => unit.status === 'completed')
      let count = pendingCount
      let label = '待挑战词汇'
      if (isGenerating) label = '生成中'
      else if (generated) {
        if (isCompleted) {
          label = '已完成'
          count = units.reduce((sum, unit) => sum + number(unit.completedCoreCount || unit.coreWordCount), 0) || number(dayData?.completedCount) || 0
        } else label = overdue ? `逾期 ${pendingCount}` : '待挑战词汇'
      } else if (!withinPlan) {
        count = 0
        label = '计划外'
      } else if (isPast) {
        count = 0
        label = '未生成'
      } else {
        count = suggestedDailyCount
        label = isToday ? '待生成' : '预计待挑战'
      }
      return `<button class="scene-calendar-day ${isToday ? 'today' : ''} ${isPast ? 'past' : ''} ${!withinPlan ? 'outside-plan' : ''} ${overdue ? 'overdue' : ''} ${isCompleted ? 'completed-day' : ''} ${isGenerating ? 'generating-day' : ''}" type="button" data-calendar-preview="${key}" aria-label="预览 ${formatCalendarDate(date, true)} 的词汇"><span>${range === 'month' ? `${date.getDate()}日` : formatCalendarDate(date, true)}</span><strong>${count}</strong><small>${label}</small></button>`
    }).join('')}</div>`
    elements.sceneCalendar.querySelectorAll('[data-calendar-preview]').forEach((button) => button.addEventListener('click', () => openVocabularyPreview({ date: button.dataset.calendarPreview })))
    renderOverviewUnits(plan)
  }

  function renderOverviewUnits(plan) {
    if (!elements.sceneOverviewUnitsContainer || !elements.sceneOverviewUnitsList) return
    if (!plan) {
      elements.sceneOverviewUnitsContainer.classList.add('hidden')
      return
    }
    elements.sceneOverviewUnitsContainer.classList.remove('hidden')
    const today = dateFromKey(localDateKey())
    let dates = calendarDates()
    const todayKey = dateKey(today)
    const planStartKey = plan.startTime ? plan.startTime.split('T')[0] : null
    const planEndKey = plan.endTime ? plan.endTime.split('T')[0] : null
    if (planStartKey || planEndKey) {
      dates = dates.filter((date) => (!planStartKey || dateKey(date) >= planStartKey) && (!planEndKey || dateKey(date) <= planEndKey))
    } else {
      dates = dates.filter((date) => asArray(plan.units).some((unit) => unit.recommendedDate === dateKey(date) || unit.generatedTime?.split('T')[0] === dateKey(date)))
    }
    if (!dates.length) {
      elements.sceneOverviewUnitsContainer.classList.add('hidden')
      return
    }
    elements.sceneOverviewUnitsList.innerHTML = dates.map((date) => renderDateUnits(plan, date, todayKey)).join('')
    bindOverviewActions(plan)
  }

  function renderDateUnits(plan, date, todayKey) {
    const key = dateKey(date)
    const isPast = key < todayKey
    const isToday = key === todayKey
    const units = unitsForDate(plan, key)
    const isGenerating = isDayGenerating(key)
    if (units.length > 1) {
      const totalPending = units.reduce((sum, unit) => sum + pendingChallengeWords(unit).length, 0)
      const totalCompleted = units.reduce((sum, unit) => sum + number(unit.completedCoreCount || unit.coreWordCount), 0)
      const allCompleted = units.every((unit) => unit.status === 'completed')
      const statusLabel = isGenerating ? '生成中...' : (allCompleted ? '已完成' : (isToday ? '今日任务' : '待学习'))
      const statusClass = isGenerating ? 'running' : (allCompleted ? 'generated' : (isToday ? 'today' : 'generated'))
      const dayMeta = isGenerating ? 'AI 正在后台生成场景材料与练习题...' : (allCompleted ? `共 ${units.length} 篇场景材料 · ${totalCompleted} 个已完成词汇` : `共 ${units.length} 篇场景材料 · ${totalPending} 个待挑战词汇`)
      return `<div class="scene-overview-day-group ${isToday ? 'today' : ''} ${isPast ? 'past' : ''}"><div class="day-group-header"><div class="day-group-date-info"><span class="day-group-date">${formatCalendarDate(date, true)}</span><span class="unit-status-tag ${statusClass}">${statusLabel}</span><span class="day-group-meta">${dayMeta}</span></div><div class="day-group-actions">${isGenerating ? '<button class="secondary-button compact" type="button" disabled>生成中...</button>' : `<button class="secondary-button compact" type="button" data-action-regenerate-date="${key}" ${plan.status !== 'active' ? 'disabled' : ''}>重新生成</button>`}</div></div><div class="day-group-units">${units.map((unit, idx) => {
        const isComplete = unit.status === 'completed'
        const pendingCount = pendingChallengeWords(unit).length
        const completedCount = number(unit.completedCoreCount || unit.coreWordCount)
        const wordInfo = isComplete ? `${completedCount} 个已完成词汇 · 已完成` : `${pendingCount} 个待挑战词汇 · ${unitStatusLabel(unit)}`
        return `<div class="day-unit-sub-row"><button class="unit-preview-button" type="button" data-preview-date="${key}" data-preview-unit="${escapeHtml(unit.id)}" aria-label="预览第 ${idx + 1} 篇 ${escapeHtml(unit.title || '场景单元')} 的词汇"><span class="unit-index-badge">篇章 ${idx + 1}/${units.length}</span><span class="unit-detail-info"><strong class="unit-title">${escapeHtml(unit.title || '场景单元')}</strong><span class="unit-words-count">${wordInfo}</span></span></button><div class="unit-action-button"><button class="primary-button compact-primary" type="button" data-action-learn="${escapeHtml(unit.id)}">${isComplete ? '回顾场景' : '开始学习'}</button></div></div>`
      }).join('')}</div></div>`
    }
    const unit = units.length === 1 ? units[0] : null
    const isComplete = unit?.status === 'completed'
    const pendingCount = unit ? pendingChallengeWords(unit).length : 0
    const completedCount = unit ? number(unit.completedCoreCount || unit.coreWordCount) : 0
    const wordInfo = isGenerating ? 'AI 正在后台异步生成场景短文与练习题，请稍候...' : (unit ? (isComplete ? `${completedCount} 个已完成词汇 · 已完成` : `${pendingCount} 个待挑战词汇 · ${unitStatusLabel(unit)}`) : '场景生成后可预览待挑战与已完成词汇')
    const tagLabel = isGenerating ? '生成中...' : (unit ? unitStatusLabel(unit) : '待生成')
    const tagClass = isGenerating ? 'running' : (unit ? (isComplete ? 'generated' : 'today') : 'pending')
    return `<div class="scene-overview-unit-row ${isToday ? 'today' : ''} ${isPast ? 'past' : ''}"><button class="unit-preview-button" type="button" data-preview-date="${key}" ${unit ? `data-preview-unit="${escapeHtml(unit.id)}"` : ''} aria-label="预览 ${unit ? escapeHtml(unit.title || '场景单元') : formatCalendarDate(date, true)} 的词汇"><span class="unit-date-info"><span class="unit-date">${formatCalendarDate(date, true)}</span><span class="unit-status-tag ${tagClass}">${tagLabel}</span></span><span class="unit-detail-info">${unit ? `<strong class="unit-title">${escapeHtml(unit.title || '场景单元')}</strong><span class="unit-words-count">${wordInfo}</span>` : `<span class="unit-placeholder-text">${wordInfo}</span>`}</span></button><div class="unit-action-button">${isGenerating ? `<button class="secondary-button compact" type="button" disabled>生成中...</button>${unit ? `<button class="primary-button compact-primary" type="button" data-action-learn="${escapeHtml(unit.id)}">${isComplete ? '回顾场景' : '开始学习'}</button>` : ''}` : unit ? `<button class="secondary-button compact" type="button" data-action-regenerate-date="${key}" ${plan.status !== 'active' ? 'disabled' : ''}>重新生成</button><button class="primary-button compact-primary" type="button" data-action-learn="${escapeHtml(unit.id)}">${isComplete ? '回顾场景' : '开始学习'}</button>` : `<button class="secondary-button compact" type="button" data-action-generate="${escapeHtml(plan.id)}" data-recommended-date="${key}" ${plan.status !== 'active' ? 'disabled' : ''}>生成场景</button>`}</div></div>`
  }

  function bindOverviewActions(plan) {
    elements.sceneOverviewUnitsList.querySelectorAll('[data-preview-date]').forEach((row) => row.addEventListener('click', () => openVocabularyPreview({ date: row.dataset.previewDate, unitId: row.dataset.previewUnit || null })))
    elements.sceneOverviewUnitsList.querySelectorAll('[data-action-learn]').forEach((button) => button.addEventListener('click', async (event) => {
      event.stopPropagation()
      const unitId = button.dataset.actionLearn
      setButtonLoading(button, true, '打开中...')
      try {
        const selectedUnit = asArray(plan.units).find((unit) => sameId(unit.id, unitId))
        if (state.preview) {
          if (selectedUnit?.status !== 'completed') {
            asArray(plan.units).forEach((unit) => { if (unit.status === 'in_progress') unit.status = 'ready' })
            if (selectedUnit) selectedUnit.status = 'in_progress'
          }
          plan.currentUnitId = unitId
        } else if (selectedUnit?.status !== 'completed') {
          const updated = await api.startUnit(plan.id, unitId)
          Object.assign(plan, updated, { units: asArray(plan.units), currentUnitId: unitId })
          state.currentLearningPlan = plan
          state.learningPlans = state.learningPlans.map((item) => sameId(item.id, plan.id) ? plan : item)
        }
        plan.currentUnitId = unitId
        const detail = await unitList.loadDetail(plan, unitId)
        const active = detail || activeUnit(state.currentLearningPlan)
        state.currentSceneWordId = asArray(active?.words).find((word) => word.tier === 'core' && !isWordComplete(word))?.id || asArray(active?.words).find((word) => word.tier === 'core')?.id || null
        renderCurrentScene()
        await loadSceneNote(active)
        await startLearning()
      } catch (error) {
        logEvent('error', '开始场景失败', error.message)
        toast(`开始场景失败：${error.message}`)
      } finally {
        setButtonLoading(button, false)
      }
    }))
    elements.sceneOverviewUnitsList.querySelectorAll('[data-action-regenerate-date]').forEach((button) => button.addEventListener('click', async (event) => {
      event.stopPropagation()
      const recommendedDate = button.dataset.actionRegenerateDate
      const confirmed = await confirmAction({ title: '重新生成场景材料', message: `重新生成将为【${formatCalendarDate(recommendedDate, true)}】重新创作全新的场景短文、翻译与题目，当天的做题记录将被重置，是否确认重新生成？`, acceptText: '重新生成', danger: true })
      if (!confirmed) return
      setButtonLoading(button, true, '生成中...')
      try {
        if (state.preview) {
          toast('演示模式已模拟重新生成')
          return
        }
        const modelConfigId = elements.scenePlanModelSelect?.value || null
        const task = await api.regenerateDayAsync(plan.id, { modelConfigId: modelConfigId ? String(modelConfigId) : null, recommendedDate })
        await loadSceneData({ planId: plan.id })
        toast(`重新生成任务已提交（${task.status === 'pending' ? '等待执行' : '执行中'}），可在任务中心查看进度`)
      } catch (error) {
        logEvent('error', '重新生成场景材料失败', error.message)
        toast(`重新生成失败：${error.message}`)
      } finally {
        setButtonLoading(button, false)
      }
    }))
    elements.sceneOverviewUnitsList.querySelectorAll('[data-action-generate]').forEach((button) => button.addEventListener('click', async (event) => {
      event.stopPropagation()
      const recommendedDate = button.dataset.recommendedDate || null
      const confirmed = await confirmAction({ title: '生成场景材料', message: `将为【${formatCalendarDate(recommendedDate, true)}】提交后台异步生成场景材料。任务在后台执行，不会中断当前学习，生成进度可在任务中心实时查看。是否确认生成？`, acceptText: '开始生成' })
      if (!confirmed) return
      setButtonLoading(button, true, '提交中...')
      try {
        if (state.preview) {
          plan.units.push(createPreviewCookingUnit(plan.id, asArray(plan.units).length + 1, recommendedDate))
        } else {
          const modelConfigId = elements.scenePlanModelSelect?.value || null
          state.sceneScheduledTask = await api.scheduleNext(plan.id, { modelConfigId: modelConfigId || null, recommendedDate, executionMode: 'immediate' })
        }
        await loadSceneData({ planId: plan.id })
        toast('场景材料生成任务已提交，可在任务中心查看进度')
      } catch (error) {
        toast(`生成场景失败：${error.message}`)
      } finally {
        setButtonLoading(button, false)
      }
    }))
  }

  return {
    closeVocabularyPreview,
    openVocabularyPreview,
    isDayGenerating,
    renderCalendar,
    renderOverviewUnits,
  }
}
