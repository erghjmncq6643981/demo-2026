import { normalizeWordbookId } from '/src/shared/wordbook.js'
import { PLAN_STATUS_LABELS, SOURCE_LABELS, asArray, localDateKey, number } from '/src/features/learning/scene-plan/model.js'
import { createPreviewPlan, createPreviewCookingUnit, previewCatalog } from '/src/features/learning/scene-plan/preview-data.js'
import { isWordComplete } from '/src/features/learning/scene-plan/challenge-model.js'
import {
  calendarDates as resolveCalendarDates,
  calendarTitle as resolveCalendarTitle,
  dateFromKey,
  formatCalendarDate,
  unitDateKey,
  unitStatusLabel,
  unitsForDate,
} from '/src/features/learning/scene-plan/calendar-model.js'
import { createScenePlanApi } from '/src/features/learning/scene-plan/api.js'
import { createPlanManager } from '/src/features/learning/scene-plan/plan-manager.js'
import { createCalendarView } from '/src/features/learning/scene-plan/calendar-view.js'
import { createUnitList } from '/src/features/learning/scene-plan/unit-list.js'
import { createStudyEngine } from '/src/features/learning/scene-plan/study-engine.js'
import { createAsyncListener } from '/src/features/learning/scene-plan/async-listener.js'
import { createVocabularyImportWorkflow } from '/src/features/learning/scene-plan/vocabulary-import.js'
import { createSceneOverview } from '/src/features/learning/scene-plan/scene-overview.js'
import { createSceneStudy } from '/src/features/learning/scene-plan/scene-study.js'
import { createSceneNote } from '/src/features/learning/scene-plan/scene-note.js'
import { createSceneActions } from '/src/features/learning/scene-plan/scene-actions.js'
import { createPlanWorkflow } from '/src/features/learning/scene-plan/plan-workflow.js'
import { isRequestAbort } from '/src/shared/latest-request.js'
import { createVocabularyCatalogApi } from '/src/features/vocabulary/catalog/api.js'

export function createScenePlanFeature(ctx) {
  const { state, elements, request, toast, logEvent, confirmAction, escapeHtml, sameId, speak, speakSentence, loadWordbooks } = ctx
  const api = createScenePlanApi(request)
  const catalogApi = createVocabularyCatalogApi(request)
  let sceneStudy
  let sceneOverview
  let sceneNote
  let sceneActions
  let planWorkflow

  function setButtonLoading(button, loading, text) {
    if (!button) return
    if (loading) {
      button.dataset.previousText = button.textContent
      if (text) button.textContent = text
    } else if (button.dataset.previousText) {
      button.textContent = button.dataset.previousText
      delete button.dataset.previousText
    }
    button.disabled = loading
  }

  const unitList = createUnitList({ state, api, sameId })

  function activeUnit(plan = state.currentLearningPlan) {
    return unitList.activeUnit(plan)
  }

  function renderSelectOptions(select, items, selected, label, emptyLabel) {
    if (!select) return
    select.innerHTML = ''
    if (!items.length) {
      select.innerHTML = `<option value="">${escapeHtml(emptyLabel)}</option>`
      return
    }
    items.forEach((item) => {
      const option = document.createElement('option')
      option.value = String(item.id)
      option.textContent = label(item)
      select.appendChild(option)
    })
    const normalizedSelected = String(selected || '')
    select.value = items.some((item) => String(item.id) === normalizedSelected) ? normalizedSelected : String(items[0].id)
  }

  function renderSourceOptions() {
    const wordbooks = asArray(state.wordbooks)
    const preferredWordbook = normalizeWordbookId(state.currentWordbookId) || normalizeWordbookId(wordbooks[0]?.id)
    ;[elements.sceneWordbookSelect, elements.vocabularyImportWordbook, elements.scenePlanWordbookSelect].forEach((select) => renderSelectOptions(select, wordbooks, select?.value || preferredWordbook, (item) => `${item.name} · ${item.entryCount || 0}词`, '暂无单词本'))
    renderSelectOptions(elements.sceneCatalogSelect, asArray(state.publicVocabularyCatalogs).map((item) => ({ ...item, id: item.catalogVersionId })), elements.sceneCatalogSelect?.value, (item) => `${item.catalogName} · ${SOURCE_LABELS[item.sourceType] || item.sourceType || '公共'} · ${item.totalCount || 0}词`, '请先导入并发布公共词本')
    renderSelectOptions(elements.scenePlanSelect, asArray(state.learningPlans), state.currentLearningPlan?.id || elements.scenePlanSelect?.value, (item) => `${item.name} · ${number(item.learnedCoreWords)}/${number(item.totalCatalogWords)}词`, '暂无学习计划')
    if (elements.scenePlanModelSelect) {
      const current = elements.scenePlanModelSelect.value
      const enabledModels = asArray(state.modelConfigs).filter((item) => item.enabled)
      elements.scenePlanModelSelect.innerHTML = '<option value="">使用 Agent 绑定模型</option>'
      enabledModels.forEach((model) => {
        const option = document.createElement('option')
        option.value = String(model.id)
        option.textContent = `${model.name} · ${model.modelName}${model.isDefault ? ' · 默认' : ''}`
        elements.scenePlanModelSelect.appendChild(option)
      })
      elements.scenePlanModelSelect.value = enabledModels.some((model) => String(model.id) === current) ? current : ''
    }
  }

  async function ensureActiveUnitDetail() {
    const plan = state.currentLearningPlan
    const unit = activeUnit(plan)
    if (!plan || !unit) return null
    const detail = await unitList.loadDetail(plan, unit.id)
    if (detail) {
      state.currentLearningPlan = plan
      renderCurrentScene()
      await sceneNote?.load(detail)
    }
    return detail || unit
  }

  async function loadSceneData(options = {}) {
    if (state.preview) {
      if (!state.publicVocabularyCatalogs.length) state.publicVocabularyCatalogs = [previewCatalog()]
      if (!state.vocabularyImports.length) {
        const catalog = state.publicVocabularyCatalogs[0]
        state.vocabularyImports = [
          { jobId: 1, ...catalog, importerUserId: 1, importerName: '系统管理员', status: 'published', fileName: '自学考试(二)全部词汇5087_正序版.md', warningCount: 3, reviewedWarningCount: 3, pendingWarningCount: 0, items: [], filteredTotal: 0, page: 1, pageSize: state.vocabularyImportPageSize, createTime: new Date(Date.now() - 86400000 * 5).toISOString() },
          { jobId: 2, catalogId: 2, catalogVersionId: 2, catalogName: '大学英语四级核心词汇', sourceType: 'cet4', totalCount: 3260, importerUserId: 2, importerName: '内容管理员', status: 'reviewing', fileName: 'cet4-core.md', warningCount: 8, reviewedWarningCount: 5, pendingWarningCount: 3, items: [], filteredTotal: 0, page: 1, pageSize: state.vocabularyImportPageSize, createTime: new Date(Date.now() - 86400000 * 2).toISOString() },
        ]
      }
      if (!state.learningPlans.length) state.learningPlans = [createPreviewPlan({ catalog: state.publicVocabularyCatalogs[0] })]
      const selectedPlanId = options.planId || state.currentLearningPlan?.id || state.learningPlans[0]?.id
      state.currentLearningPlan = state.learningPlans.find((plan) => sameId(plan.id, selectedPlanId)) || state.learningPlans[0] || null
      renderSceneView()
      return
    }
    if (!state.token) {
      clearSceneData()
      return
    }
    const selectedPlanId = options.planId || options.preferredPlanId || state.currentLearningPlan?.id
    try {
      const canManageCatalogs = state.user?.roleCode === 'ADMIN'
      const [imports, publicCatalogs, plans] = await Promise.all([canManageCatalogs ? api.listImports() : Promise.resolve([]), api.listPublicCatalogs(), api.listPlans()])
      applyImportHistoryPage(imports)
      state.publicVocabularyCatalogs = asArray(publicCatalogs)
      state.learningPlans = asArray(plans)
      renderSourceOptions()
      planWorkflow?.renderPlanList()
      renderImportList()
      const planId = state.learningPlans.some((plan) => sameId(plan.id, selectedPlanId)) ? selectedPlanId : state.learningPlans[0]?.id
      const shouldKeepStage = options.keepStage ?? (sameId(planId, state.currentLearningPlan?.id) && Boolean(state.sceneChallengeStage && state.sceneChallengeStage !== 'overview'))
      if (planId) await selectPlan(planId, { quiet: true, keepStage: shouldKeepStage })
      else { state.currentLearningPlan = null; renderCurrentScene() }
    } catch (error) {
      if (isRequestAbort(error)) return
      logEvent('error', '场景学习数据加载失败', error.message)
      toast(`场景学习加载失败：${error.message}`)
    }
  }

  function clearSceneData() {
    api.cancelAll()
    catalogApi.cancelAll()
    state.vocabularyImports = []
    state.vocabularyImportHistoryTotal = 0
    state.publicVocabularyCatalogs = []
    state.currentVocabularyImport = null
    state.learningPlans = []
    state.currentLearningPlan = null
    state.currentSceneWordId = null
    state.sceneCardJob = null
    state.sceneNote = { content: '', updateTime: null, unitId: null }
    state.sceneNoteMode = 'edit'
    sceneStudy?.resetAssessment()
    renderSceneView()
  }

  function renderSceneView() {
    syncCalendarRangeControls()
    renderSourceOptions()
    planWorkflow?.renderPlanList()
    renderImportList()
    renderCurrentScene()
  }

  function syncCalendarRangeControls() {
    document.querySelectorAll('[data-calendar-range]').forEach((button) => {
      button.classList.toggle('active', button.dataset.calendarRange === state.sceneCalendarRange)
    })
  }

  function changeCalendarRange(range) {
    calendarView.changeRange(range)
    syncCalendarRangeControls()
  }

  async function selectPlan(planId, options = {}) {
    if (!planId) return
    try {
      const previousPlanId = state.currentLearningPlan?.id
      const shouldKeepStage = options.keepStage ?? (sameId(planId, previousPlanId) && Boolean(state.sceneChallengeStage && state.sceneChallengeStage !== 'overview'))
      const plan = state.preview ? asArray(state.learningPlans).find((item) => sameId(item.id, planId)) : await api.getPlan(planId)
      if (!plan) return
      state.currentLearningPlan = plan
      const unit = activeUnit(plan)
      const coreWords = asArray(unit?.words).filter((word) => word.tier === 'core')
      state.currentSceneWordId = (coreWords.find((word) => !isWordComplete(word)) || coreWords[0])?.id || null
      state.sceneChallengeStage = shouldKeepStage ? state.sceneChallengeStage : 'overview'
      sceneStudy?.resetAssessment()
      if (!state.sceneCalendarCursorDate) state.sceneCalendarCursorDate = localDateKey()
      planWorkflow?.renderPlanList()
      await loadCalendarData(plan)
      if (shouldKeepStage) {
        await ensureActiveUnitDetail()
      }
      renderCurrentScene()
      if (!options.quiet) logEvent('learning', '切换场景学习计划', plan.name)
    } catch (error) {
      if (isRequestAbort(error)) return
      logEvent('error', '学习计划加载失败', error.message)
      toast(`学习计划加载失败：${error.message}`)
    }
  }

  function changeSelectedPlan(planId) {
    if (!planId || sameId(planId, state.currentLearningPlan?.id)) return Promise.resolve()
    if (elements.scenePlanSelect) elements.scenePlanSelect.value = String(planId)
    return selectPlan(planId)
  }

  function calendarDates() { return resolveCalendarDates(state.sceneCalendarRange, state.sceneCalendarCursorDate) }
  function calendarTitle(dates) { return resolveCalendarTitle(state.sceneCalendarRange, state.sceneCalendarCursorDate, dates) }

  async function loadCalendarData(plan) {
    if (!plan || state.preview || !state.token) { state.sceneCalendarData = null; return null }
    const dates = calendarDates()
    const from = localDateKey(dates[0])
    const to = localDateKey(dates[dates.length - 1])
    try {
      const data = await api.getCalendar(plan.id, from, to)
      const currentDates = calendarDates()
      if (sameId(state.currentLearningPlan?.id, plan.id) && localDateKey(currentDates[0]) === from && localDateKey(currentDates[currentDates.length - 1]) === to) {
        state.sceneCalendarData = data
        unitList.mergeCalendarUnits(plan, data)
      }
      return data
    } catch (error) {
      if (isRequestAbort(error)) return null
      if (sameId(state.currentLearningPlan?.id, plan.id)) state.sceneCalendarData = null
      logEvent('error', '学习日历加载失败', error.message)
      return null
    }
  }

  async function refreshCalendarData(plan) {
    const planId = plan?.id
    await loadCalendarData(plan)
    if (planId && sameId(state.currentLearningPlan?.id, planId)) sceneOverview?.renderCalendar(state.currentLearningPlan)
  }

  function renderCurrentScene() {
    const plan = state.currentLearningPlan
    const unit = activeUnit(plan)
    if (elements.sceneOverviewTitle) elements.sceneOverviewTitle.textContent = plan?.name || '选择学习计划'
    if (elements.sceneOverviewSummary) elements.sceneOverviewSummary.textContent = plan?.learningPurpose || '通过日历了解近期学习量，再开始当前场景。'
    if (elements.sceneOverviewProgress) elements.sceneOverviewProgress.textContent = plan ? `${number(plan.learnedCoreWords)} / ${number(plan.totalCatalogWords)} 词` : '0 / 0 词'
    if (plan) {
      elements.scenePlanMetaBar?.classList.remove('hidden')
      if (elements.scenePlanDatesText) elements.scenePlanDatesText.textContent = `${formatPlanDate(plan.startTime)} 至 ${formatPlanDate(plan.endTime)}`
      if (elements.scenePlanStatusText) elements.scenePlanStatusText.textContent = PLAN_STATUS_LABELS[plan.status] || plan.status || '-'
      if (elements.sceneStartLearningBtn) elements.sceneStartLearningBtn.disabled = !unit || plan.status !== 'active'
    } else {
      elements.scenePlanMetaBar?.classList.add('hidden')
      if (elements.sceneStartLearningBtn) elements.sceneStartLearningBtn.disabled = true
    }
    elements.sceneStartLearningBtn?.classList.toggle('hidden', !unit)
    elements.sceneOverviewNextUnitBtn?.classList.toggle('hidden', !plan?.canGenerateNext || Boolean(unit) || plan?.status !== 'active')
    elements.sceneScheduleNextUnitBtn?.classList.toggle('hidden', !plan?.canGenerateNext || Boolean(unit) || plan?.status !== 'active')
    sceneOverview?.renderCalendar(plan)
    sceneStudy?.applyStage(state.sceneChallengeStage || 'overview')
    if (!plan || !unit) {
      if (elements.sceneUnitEyebrow) elements.sceneUnitEyebrow.textContent = plan ? 'Ready for next scene' : 'Current Scene'
      if (elements.sceneUnitTitle) elements.sceneUnitTitle.textContent = plan ? '可以生成下一个场景' : '选择一个学习计划'
      if (elements.sceneUnitSummary) elements.sceneUnitSummary.textContent = plan?.learningPurpose || '当前场景会显示在这里'
      if (elements.sceneLearningText) { elements.sceneLearningText.className = 'scene-learning-text empty'; elements.sceneLearningText.textContent = plan ? '当前没有进行中的场景' : '暂无场景材料' }
      if (elements.sceneTranslation) elements.sceneTranslation.textContent = '暂无译文'
      sceneStudy?.renderCoreWords([])
      sceneStudy?.renderRelatedWords(null)
      sceneStudy?.renderChallengeWords([])
      sceneStudy?.renderAssessment(null)
      elements.sceneGenerateCardsBtn?.classList.add('hidden')
      elements.sceneScheduleCardsBtn?.classList.add('hidden')
      elements.sceneCompleteUnitBtn?.classList.add('hidden')
      return
    }
    const coreWords = asArray(unit.words).filter((word) => word.tier === 'core')
    const missingCards = asArray(unit.words).some((word) => ['core', 'review'].includes(word.tier) && ['missing', 'failed'].includes(word.cardStatus))
    if (elements.sceneUnitEyebrow) elements.sceneUnitEyebrow.textContent = `Scene ${unit.unitNo || asArray(plan.units).length} · ${unit.scenarioType || 'Vocabulary'}`
    if (elements.sceneUnitTitle) elements.sceneUnitTitle.textContent = unit.title || '未命名场景'
    if (elements.sceneUnitSummary) { elements.sceneUnitSummary.dataset.tooltip = unit.summary || plan.learningPurpose || ''; elements.sceneUnitSummary.textContent = unit.summary || plan.learningPurpose || '通过当前场景学习相关词汇' }
    if (elements.sceneUnitProgress) elements.sceneUnitProgress.textContent = `${number(unit.completedCoreCount)} / ${number(unit.coreWordCount)}`
    elements.sceneGenerateCardsBtn?.classList.toggle('hidden', !missingCards || plan.status !== 'active')
    elements.sceneScheduleCardsBtn?.classList.toggle('hidden', !missingCards || plan.status !== 'active')
    elements.sceneCompleteUnitBtn?.classList.toggle('hidden', unit.status === 'completed' || number(unit.completedCoreCount) < number(unit.coreWordCount) || plan.status !== 'active')
    elements.sceneNextUnitBtn?.classList.toggle('hidden', !plan.canGenerateNext || plan.status !== 'active')
    sceneStudy?.renderLearningText(unit, coreWords)
    sceneStudy?.renderCoreWords(coreWords)
    sceneStudy?.renderRelatedWords(unit)
    sceneStudy?.renderChallengeWords(coreWords)
    sceneStudy?.renderAssessment(unit)
    sceneNote?.updateButtonText(unit)
  }

  function formatPlanDate(value) {
    if (!value) return '-'
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? '未设置' : date.toLocaleDateString('zh-CN')
  }

  const planManager = createPlanManager({ state, api, sameId, loadSceneData, toast, logEvent, confirmAction })
  const calendarView = createCalendarView({ state, render: () => sceneOverview?.renderCalendar(state.currentLearningPlan), refresh: () => refreshCalendarData(state.currentLearningPlan) })
  const studyEngine = createStudyEngine({ state, elements, applyStage: (stage) => sceneStudy?.applyStage(stage), renderChallengeWords: (words) => sceneStudy?.renderChallengeWords(words), renderAssessment: (unit) => sceneStudy?.renderAssessment(unit), prepareUnit: ensureActiveUnitDetail, coreWords: (unit) => asArray(unit?.words).filter((word) => word.tier === 'core'), isWordComplete, onChallengeStart: (word) => { state.currentSceneWordId = word?.id || null; state.sceneAssessmentStartedAt = Date.now(); sceneStudy?.resetAssessment() } })
  const importWorkflow = createVocabularyImportWorkflow({ state, elements, catalogApi, renderSourceOptions, setButtonLoading, toast, logEvent, confirmAction, escapeHtml, sameId, loadWordbooks })
  const { open: openVocabularyImport, close: closeVocabularyImport, start: startVocabularyImport, remove: deleteImportJob, saveMetadata: saveVocabularyImportMetadata, loadReview: loadImportReview, openReview: openImportReview, confirmAll: confirmAllWarnings, publish: publishVocabularyImport, triggerAnalysis: triggerVocabularyAnalysis, applyHistoryPage: applyImportHistoryPage, renderImportList, changeSearch: changeImportSearch, previousPage: previousImportPage, nextPage: nextImportPage, previousHistoryPage: previousHistoryPage, nextHistoryPage: nextHistoryPage } = importWorkflow

  sceneNote = createSceneNote({ state, elements, api, activeUnit, sameId, toast, logEvent })
  sceneActions = createSceneActions({ state, elements, api, request, activeUnit, selectPlan, loadSceneData, renderSceneView, renderCurrentScene, setButtonLoading, confirmAction, toast, logEvent, sameId, createPreviewCookingUnit })
  sceneStudy = createSceneStudy({ state, elements, api, activeUnit, renderCurrentScene, setButtonLoading, escapeHtml, sameId, toast, logEvent, completeCurrentUnit: (...args) => sceneActions.completeCurrentUnit(...args), backToReading: () => studyEngine.backToReading(), startChallenge: () => studyEngine.startChallenge(), generateRelatedWords: (...args) => sceneActions.generateRelatedWords?.(...args), promoteWord: (...args) => sceneActions.promoteWord(...args), speak: (...args) => ctx.speak?.(...args) })
  sceneOverview = createSceneOverview({ state, elements, api, unitList, activeUnit, calendarDates, calendarTitle, dateFromKey, formatCalendarDate, unitDateKey, unitStatusLabel, unitsForDate, createPreviewCookingUnit, loadSceneData, loadSceneNote: sceneNote.load, startLearning: studyEngine.startLearning, renderCurrentScene, setButtonLoading, confirmAction, toast, logEvent, escapeHtml, sameId, preview: state.preview })
  planWorkflow = createPlanWorkflow({ state, elements, api, loadSceneData, renderSourceOptions, changeSelectedPlan, targetPlan: planManager.targetPlan, setButtonLoading, toast, logEvent, confirmAction, escapeHtml, sameId })
  createAsyncListener({ state, refreshCalendar: refreshCalendarData }).bind()

  return {
    loadSceneData, clearSceneData, renderSceneView, renderRelatedWords: () => sceneStudy.renderRelatedWords(activeUnit()),
    openVocabularyImport, closeVocabularyImport, startVocabularyImport, deleteImportJob, saveVocabularyImportMetadata, loadReview: loadImportReview, loadReview: loadImportReview, openImportReview, confirmAllWarnings, publishVocabularyImport, triggerVocabularyAnalysis,
    openScenePlanModal: planWorkflow.openModal, closeScenePlanModal: planWorkflow.closeModal, createScenePlan: planWorkflow.savePlan, changePlanCatalog: planWorkflow.changeCatalog, changeSceneWordbook: planWorkflow.changeWordbook,
    changeImportSearch, previousImportPage, nextImportPage, previousHistoryPage, nextHistoryPage,
    completeCurrentUnit: sceneActions.completeCurrentUnit, generateNextUnit: sceneActions.generateNextUnit, scheduleNextUnit: sceneActions.scheduleNextUnit, generateCards: sceneActions.generateCards, scheduleCards: sceneActions.scheduleCards,
    startLearning: studyEngine.startLearning, showChallengeWords: studyEngine.showChallengeWords, startChallenge: studyEngine.startChallenge, backToReading: studyEngine.backToReading, backToPlanOverview: () => sceneStudy.applyStage('overview'),
    changeCalendarRange, changeCalendarOffset: calendarView.changeOffset, resetCalendar: calendarView.reset, changeSelectedPlan, pausePlan: planWorkflow.pausePlan, resumePlan: planWorkflow.resumePlan, cancelPlan: planWorkflow.cancelPlan,
    speakCurrentScene: () => speakSentence(activeUnit()?.learningText || ''), loadSceneNote: sceneNote.load, renderSceneNote: sceneNote.render, saveSceneNote: sceneNote.save, toggleSceneNotePreview: sceneNote.togglePreview, handleSceneNoteInput: sceneNote.handleInput, setSceneNoteMode: sceneNote.setMode, toggleSceneNotePanel: sceneNote.togglePanel, openSceneNotePanel: sceneNote.openPanel, closeSceneNotePanel: sceneNote.closePanel, openSceneNoteModal: sceneNote.openPanel, closeSceneNoteModal: sceneNote.closePanel,
    closeSceneVocabularyPreview: sceneOverview.closeVocabularyPreview, openCoreWordsModal: sceneStudy.openCoreWordsModal, closeCoreWordsModal: sceneStudy.closeCoreWordsModal, openRelatedWordsModal: sceneStudy.openRelatedWordsModal, closeRelatedWordsModal: sceneStudy.closeRelatedWordsModal, generateRelatedWords: () => sceneActions.generateRelatedWords?.(),
    handleSceneChallengeKeydown: (event) => sceneStudy?.handleChallengeKeydown?.(event),
  }
}
