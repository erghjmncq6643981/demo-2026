import { hideModal, showModal } from '/src/shared/modal.js'
import { normalizeWordbookId, syncCurrentWordbookId } from '/src/shared/wordbook.js'

const TIER_LABELS = {
  core: '核心',
  extended: '词表扩展',
  supplementary: 'AI 补充',
  review: '复习',
}

const PLAN_STATUS_LABELS = {
  active: '学习中',
  completed: '已完成',
  paused: '已暂停',
}

const IMPORT_STATUS_LABELS = {
  reviewing: '待审核',
  published: '已发布',
  failed: '失败',
}

const ASSESSMENT_LABELS = {
  meaning_choice: '含义选择',
  copy_typing: '跟敲单词',
  meaning_spelling: '含义拼写',
}

function asArray(value) {
  return Array.isArray(value) ? value : []
}

function number(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

export function createScenePlanFeature(ctx) {
  const {
    state,
    elements,
    request,
    setLoading,
    toast,
    logEvent,
    confirmAction,
    escapeHtml,
    sameId,
    speakSentence,
    loadWordbooks,
  } = ctx

  let importSearchTimer = null
  let assessmentFeedback = null

  function setButtonLoading(button, loading, text) {
    if (!button) return
    if (loading) {
      button.dataset.previousText = button.textContent
      button.textContent = text
    } else if (button.dataset.previousText) {
      button.textContent = button.dataset.previousText
      delete button.dataset.previousText
    }
    button.disabled = loading
  }

  function activeWordbookId() {
    return normalizeWordbookId(elements.sceneWordbookSelect?.value || state.currentWordbookId)
  }

  function activeUnit(plan = state.currentLearningPlan) {
    if (!plan) return null
    const units = asArray(plan.units)
    return units.find((unit) => sameId(unit.id, plan.currentUnitId)) || units.at(-1) || null
  }

  function currentSceneWord(unit = activeUnit()) {
    const coreWords = asArray(unit?.words).filter((word) => word.tier === 'core')
    return coreWords.find((word) => sameId(word.id, state.currentSceneWordId)) || coreWords[0] || null
  }

  function requiredAssessments(word) {
    return word?.masteryRequirement === 'spelling'
      ? ['meaning_choice', 'copy_typing', 'meaning_spelling']
      : ['meaning_choice']
  }

  function nextAssessment(word) {
    const passed = new Set(asArray(word?.passedAssessments))
    return requiredAssessments(word).find((type) => !passed.has(type)) || null
  }

  function isWordComplete(word) {
    return !nextAssessment(word)
  }

  function renderSelectOptions(select, items, selected, label, emptyLabel) {
    if (!select) return
    select.innerHTML = ''
    if (!items.length) {
      select.innerHTML = `<option value="">${escapeHtml(emptyLabel)}</option>`
      return
    }
    for (const item of items) {
      const option = document.createElement('option')
      option.value = String(item.id)
      option.textContent = label(item)
      select.appendChild(option)
    }
    const normalizedSelected = String(selected || '')
    select.value = items.some((item) => String(item.id) === normalizedSelected)
      ? normalizedSelected
      : String(items[0].id)
  }

  function renderSourceOptions() {
    const wordbooks = asArray(state.wordbooks)
    const preferredWordbook = normalizeWordbookId(state.currentWordbookId) || normalizeWordbookId(wordbooks[0]?.id)
    for (const select of [elements.sceneWordbookSelect, elements.vocabularyImportWordbook, elements.scenePlanWordbookSelect]) {
      renderSelectOptions(
        select,
        wordbooks,
        select?.value || preferredWordbook,
        (item) => `${item.name} · ${item.entryCount || 0}词`,
        '暂无单词本',
      )
    }

    const publishedImports = asArray(state.vocabularyImports).filter((item) => item.status === 'published')
    renderSelectOptions(
      elements.sceneCatalogSelect,
      publishedImports.map((item) => ({ ...item, id: item.catalogVersionId })),
      elements.sceneCatalogSelect?.value,
      (item) => `${item.catalogName} · ${item.totalCount || 0}词`,
      '请先发布词表',
    )

    const enabledModels = asArray(state.modelConfigs).filter((item) => item.enabled)
    if (elements.scenePlanModelSelect) {
      const current = elements.scenePlanModelSelect.value
      elements.scenePlanModelSelect.innerHTML = '<option value="">使用默认模型</option>'
      for (const model of enabledModels) {
        const option = document.createElement('option')
        option.value = String(model.id)
        option.textContent = `${model.name} · ${model.modelName}${model.isDefault ? ' · 默认' : ''}`
        elements.scenePlanModelSelect.appendChild(option)
      }
      elements.scenePlanModelSelect.value = enabledModels.some((model) => String(model.id) === current) ? current : ''
    }
  }

  async function loadSceneData(options = {}) {
    if (state.preview) {
      renderSourceOptions()
      renderSceneView()
      return
    }
    if (!state.token) {
      clearSceneData()
      return
    }
    const selectedPlanId = options.planId || state.currentLearningPlan?.id
    try {
      const [imports, plans] = await Promise.all([
        request('/api/v1/vocabulary-imports'),
        request('/api/v1/learning/plans'),
      ])
      state.vocabularyImports = asArray(imports)
      state.learningPlans = asArray(plans)
      renderSourceOptions()
      renderPlanList()
      renderImportList()
      const visiblePlans = plansForWordbook()
      const planId = visiblePlans.some((plan) => sameId(plan.id, selectedPlanId))
        ? selectedPlanId
        : visiblePlans[0]?.id
      if (planId) {
        await selectPlan(planId, { quiet: true })
      } else {
        state.currentLearningPlan = null
        renderCurrentScene()
      }
    } catch (error) {
      logEvent('error', '场景学习数据加载失败', error.message)
      toast(`场景学习加载失败：${error.message}`)
    }
  }

  function clearSceneData() {
    state.vocabularyImports = []
    state.currentVocabularyImport = null
    state.learningPlans = []
    state.currentLearningPlan = null
    state.currentSceneWordId = null
    state.sceneCardJob = null
    assessmentFeedback = null
    renderSceneView()
  }

  function plansForWordbook() {
    const wordbookId = activeWordbookId()
    return asArray(state.learningPlans).filter((plan) => !wordbookId || sameId(plan.wordbookId, wordbookId))
  }

  function renderSceneView() {
    renderSourceOptions()
    renderPlanList()
    renderImportList()
    renderCurrentScene()
  }

  function renderPlanList() {
    if (!elements.scenePlanList) return
    const plans = plansForWordbook()
    elements.scenePlanCount.textContent = String(plans.length)
    if (!plans.length) {
      elements.scenePlanList.className = 'scene-plan-list empty'
      elements.scenePlanList.textContent = '当前单词本暂无学习计划'
      return
    }
    elements.scenePlanList.className = 'scene-plan-list'
    elements.scenePlanList.innerHTML = plans
      .map((plan) => `
        <button class="scene-plan-item ${sameId(plan.id, state.currentLearningPlan?.id) ? 'active' : ''}" type="button" data-scene-plan-id="${escapeHtml(plan.id)}">
          <span class="scene-item-topline">
            <strong>${escapeHtml(plan.name)}</strong>
            <small>${escapeHtml(PLAN_STATUS_LABELS[plan.status] || plan.status || '学习中')}</small>
          </span>
          <span>${number(plan.learnedCoreWords)} / ${number(plan.totalCatalogWords)} 个核心词</span>
          <small>已完成 ${number(plan.completedUnitCount)} 个场景</small>
        </button>
      `)
      .join('')
    elements.scenePlanList.querySelectorAll('[data-scene-plan-id]').forEach((button) => {
      button.addEventListener('click', () => selectPlan(button.dataset.scenePlanId))
    })
  }

  function renderImportList() {
    if (!elements.sceneImportList) return
    const imports = asArray(state.vocabularyImports)
    if (!imports.length) {
      elements.sceneImportList.className = 'scene-import-list empty'
      elements.sceneImportList.textContent = '暂无导入记录'
      return
    }
    elements.sceneImportList.className = 'scene-import-list'
    elements.sceneImportList.innerHTML = imports
      .map((item) => `
        <button class="scene-import-item" type="button" data-import-job-id="${escapeHtml(item.jobId)}">
          <span class="scene-item-topline">
            <strong>${escapeHtml(item.catalogName)}</strong>
            <small>${escapeHtml(IMPORT_STATUS_LABELS[item.status] || item.status)}</small>
          </span>
          <span>${number(item.totalCount)} 词 · ${number(item.pendingWarningCount)} 个待确认</span>
        </button>
      `)
      .join('')
    elements.sceneImportList.querySelectorAll('[data-import-job-id]').forEach((button) => {
      button.addEventListener('click', () => openImportReview(button.dataset.importJobId))
    })
  }

  async function selectPlan(planId, options = {}) {
    if (!planId) return
    try {
      const plan = state.preview
        ? asArray(state.learningPlans).find((item) => sameId(item.id, planId))
        : await request(`/api/v1/learning/plans/${encodeURIComponent(planId)}`)
      if (!plan) return
      state.currentLearningPlan = plan
      const unit = activeUnit(plan)
      const coreWords = asArray(unit?.words).filter((word) => word.tier === 'core')
      const firstIncomplete = coreWords.find((word) => !isWordComplete(word)) || coreWords[0]
      state.currentSceneWordId = firstIncomplete?.id || null
      assessmentFeedback = null
      renderPlanList()
      renderCurrentScene()
      if (!options.quiet) logEvent('learning', '切换场景学习计划', plan.name)
    } catch (error) {
      logEvent('error', '学习计划加载失败', error.message)
      toast(`学习计划加载失败：${error.message}`)
    }
  }

  function renderCurrentScene() {
    const plan = state.currentLearningPlan
    const unit = activeUnit(plan)
    if (!plan || !unit) {
      elements.sceneUnitEyebrow.textContent = plan ? 'Ready for next scene' : 'Current Scene'
      elements.sceneUnitTitle.textContent = plan ? '可以生成下一个场景' : '选择一个学习计划'
      elements.sceneUnitSummary.textContent = plan?.learningPurpose || '当前场景会显示在这里'
      elements.sceneUnitProgress.textContent = plan ? `${number(plan.learnedCoreWords)} / ${number(plan.totalCatalogWords)}` : '0 / 0'
      elements.sceneLearningText.className = 'scene-learning-text empty'
      elements.sceneLearningText.textContent = plan ? '当前没有进行中的场景' : '暂无场景材料'
      elements.sceneTranslation.textContent = '暂无译文'
      elements.sceneCoreWords.className = 'scene-core-words empty'
      elements.sceneCoreWords.textContent = '暂无核心词汇'
      elements.sceneCoreCount.textContent = '0'
      elements.sceneRelatedWords.className = 'scene-related-words empty'
      elements.sceneRelatedWords.textContent = '暂无场景相关词汇'
      elements.sceneRelatedCount.textContent = '0'
      elements.sceneAssessment.className = 'scene-assessment empty'
      elements.sceneAssessment.textContent = '选择一个核心词开始检查'
      elements.sceneAssessmentStage.textContent = '未开始'
      elements.sceneGenerateCardsBtn.classList.add('hidden')
      elements.sceneCompleteUnitBtn.classList.add('hidden')
      elements.sceneNextUnitBtn.classList.toggle('hidden', !plan?.canGenerateNext)
      return
    }

    const coreWords = asArray(unit.words).filter((word) => word.tier === 'core')
    const missingCards = asArray(unit.words).some((word) =>
      ['core', 'review'].includes(word.tier) && ['missing', 'failed'].includes(word.cardStatus),
    )
    elements.sceneUnitEyebrow.textContent = `Scene ${unit.unitNo || asArray(plan.units).length} · ${unit.scenarioType || 'Vocabulary'}`
    elements.sceneUnitTitle.textContent = unit.title || '未命名场景'
    elements.sceneUnitSummary.textContent = unit.summary || plan.learningPurpose || '通过当前场景学习相关词汇'
    elements.sceneUnitProgress.textContent = `${number(unit.completedCoreCount)} / ${number(unit.coreWordCount)}`
    elements.sceneGenerateCardsBtn.classList.toggle('hidden', !missingCards)
    elements.sceneCompleteUnitBtn.classList.toggle('hidden', unit.status === 'completed')
    elements.sceneNextUnitBtn.classList.toggle('hidden', !plan.canGenerateNext)
    renderLearningText(unit, coreWords)
    renderCoreWords(coreWords)
    renderRelatedWords(unit)
    renderAssessment(unit)
  }

  function renderLearningText(unit, coreWords) {
    const learningText = unit.learningText || unit.material?.learning_text || unit.material?.learningText || ''
    elements.sceneLearningText.className = learningText ? 'scene-learning-text' : 'scene-learning-text empty'
    elements.sceneLearningText.innerHTML = learningText
      ? annotateUnknownWords(learningText, coreWords.filter((word) => word.firstLearning))
      : '暂无场景材料'
    elements.sceneTranslation.textContent = unit.translation || unit.material?.translation || '暂无译文'
  }

  function annotateUnknownWords(text, words) {
    const byTerm = new Map(
      words
        .filter((word) => word.term)
        .map((word) => [String(word.term).toLowerCase(), word]),
    )
    if (!byTerm.size) {
      return String(text)
        .split(/\r?\n/)
        .filter(Boolean)
        .map((line) => `<p>${escapeHtml(line)}</p>`)
        .join('')
    }
    const terms = [...byTerm.keys()].sort((left, right) => right.length - left.length)
    const pattern = new RegExp(`(?<![A-Za-z])(${terms.map(escapeRegExp).join('|')})(?![A-Za-z])`, 'gi')
    let cursor = 0
    let html = ''
    for (const match of String(text).matchAll(pattern)) {
      const index = match.index ?? 0
      const word = byTerm.get(match[0].toLowerCase())
      html += escapeHtml(String(text).slice(cursor, index))
      html += `<mark class="scene-inline-word" tabindex="0"><strong>${escapeHtml(match[0])}</strong><span>(${escapeHtml(word?.phonetic || '暂无音标')}，${escapeHtml(word?.contextMeaning || word?.meaning || '当前场景含义待补充')})</span></mark>`
      cursor = index + match[0].length
    }
    html += escapeHtml(String(text).slice(cursor))
    return html
      .split(/\r?\n/)
      .filter(Boolean)
      .map((line) => `<p>${line}</p>`)
      .join('')
  }

  function renderCoreWords(words) {
    elements.sceneCoreCount.textContent = String(words.length)
    if (!words.length) {
      elements.sceneCoreWords.className = 'scene-core-words empty'
      elements.sceneCoreWords.textContent = '暂无核心词汇'
      return
    }
    elements.sceneCoreWords.className = 'scene-core-words'
    elements.sceneCoreWords.innerHTML = words
      .map((word) => {
        const passed = new Set(asArray(word.passedAssessments))
        const stages = requiredAssessments(word)
          .map((type) => `<span class="scene-step ${passed.has(type) ? 'done' : ''}" title="${escapeHtml(ASSESSMENT_LABELS[type])}"></span>`)
          .join('')
        return `
          <button class="scene-core-word ${sameId(word.id, state.currentSceneWordId) ? 'active' : ''} ${isWordComplete(word) ? 'completed' : ''}" type="button" data-scene-word-id="${escapeHtml(word.id)}">
            <span>
              <strong>${escapeHtml(word.term)}</strong>
              <small>${escapeHtml(word.phonetic || '暂无音标')}</small>
            </span>
            <span class="scene-word-requirement">${word.masteryRequirement === 'spelling' ? '会拼写' : '认识'}</span>
            <span class="scene-step-list">${stages}</span>
          </button>
        `
      })
      .join('')
    elements.sceneCoreWords.querySelectorAll('[data-scene-word-id]').forEach((button) => {
      button.addEventListener('click', () => {
        state.currentSceneWordId = button.dataset.sceneWordId
        state.sceneAssessmentStartedAt = Date.now()
        assessmentFeedback = null
        renderCoreWords(words)
        renderAssessment(activeUnit())
      })
    })
  }

  function renderRelatedWords(unit) {
    const keyword = elements.sceneRelatedFilter?.value.trim().toLowerCase() || ''
    const tier = elements.sceneTierFilter?.value || ''
    const related = asArray(unit.words).filter((word) => {
      if (word.tier === 'core') return false
      if (tier && word.tier !== tier) return false
      const haystack = `${word.term || ''} ${word.meaning || ''} ${word.contextMeaning || ''}`.toLowerCase()
      return !keyword || haystack.includes(keyword)
    })
    elements.sceneRelatedCount.textContent = String(related.length)
    if (!related.length) {
      elements.sceneRelatedWords.className = 'scene-related-words empty'
      elements.sceneRelatedWords.textContent = '没有符合条件的场景词汇'
      return
    }
    elements.sceneRelatedWords.className = 'scene-related-words'
    elements.sceneRelatedWords.innerHTML = related
      .map((word) => `
        <article class="scene-related-word">
          <div>
            <span class="scene-related-title"><strong>${escapeHtml(word.term)}</strong><small>${escapeHtml(word.phonetic || '')}</small></span>
            <p>${escapeHtml(word.contextMeaning || word.meaning || '暂无释义')}</p>
          </div>
          <div class="scene-related-side">
            <span class="mini-pill">${escapeHtml(TIER_LABELS[word.tier] || word.tier)}</span>
            ${['extended', 'supplementary'].includes(word.tier) ? `<button class="secondary-button compact" type="button" data-promote-word="${escapeHtml(word.id)}">加入核心</button>` : ''}
          </div>
        </article>
      `)
      .join('')
    elements.sceneRelatedWords.querySelectorAll('[data-promote-word]').forEach((button) => {
      button.addEventListener('click', () => promoteWord(button.dataset.promoteWord))
    })
  }

  function renderAssessment(unit) {
    const word = currentSceneWord(unit)
    if (!word) {
      elements.sceneAssessment.className = 'scene-assessment empty'
      elements.sceneAssessment.textContent = '选择一个核心词开始检查'
      elements.sceneAssessmentStage.textContent = '未开始'
      return
    }
    const type = nextAssessment(word)
    const passedCount = asArray(word.passedAssessments).filter((item) => requiredAssessments(word).includes(item)).length
    elements.sceneAssessmentStage.textContent = type
      ? `${passedCount + 1} / ${requiredAssessments(word).length}`
      : '已通过'
    elements.sceneAssessment.className = 'scene-assessment'
    if (!type) {
      elements.sceneAssessment.innerHTML = `
        <div class="scene-assessment-complete">
          <span class="scene-check-mark">✓</span>
          <strong>${escapeHtml(word.term)} 已完成当前场景检查</strong>
          <p>${escapeHtml(word.meaning || word.contextMeaning || '')}</p>
          <button class="secondary-button compact" type="button" data-next-core-word>检查下一个词</button>
        </div>
      `
      elements.sceneAssessment.querySelector('[data-next-core-word]')?.addEventListener('click', selectNextCoreWord)
      return
    }

    state.sceneAssessmentType = type
    if (!state.sceneAssessmentStartedAt) state.sceneAssessmentStartedAt = Date.now()
    const feedback = assessmentFeedback
      ? `<div class="scene-assessment-feedback ${assessmentFeedback.correct ? 'ok' : 'bad'}">${escapeHtml(assessmentFeedback.message)}</div>`
      : ''
    if (type === 'meaning_choice') {
      const assessment = word.assessment || {}
      const options = asArray(assessment.options)
      elements.sceneAssessment.innerHTML = `
        <div class="scene-assessment-prompt">
          <span class="mini-pill">${ASSESSMENT_LABELS[type]}</span>
          <h4>${escapeHtml(assessment.prompt || `请选择 ${word.term} 在当前场景中的含义`)}</h4>
          <p class="phonetic">${escapeHtml(word.phonetic || '暂无音标')}</p>
        </div>
        <div class="scene-choice-list">
          ${options.map((option, index) => `<button type="button" data-scene-answer="${escapeHtml(option)}"><span>${String.fromCharCode(65 + index)}</span>${escapeHtml(option)}</button>`).join('')}
        </div>
        ${feedback}
      `
      elements.sceneAssessment.querySelectorAll('[data-scene-answer]').forEach((button) => {
        button.addEventListener('click', () => submitAssessment(button.dataset.sceneAnswer))
      })
      return
    }

    const copyTyping = type === 'copy_typing'
    elements.sceneAssessment.innerHTML = `
      <div class="scene-assessment-prompt">
        <span class="mini-pill">${ASSESSMENT_LABELS[type]}</span>
        <h4>${copyTyping ? `跟敲 ${escapeHtml(word.term)}` : escapeHtml(word.contextMeaning || word.meaning || '根据含义拼写单词')}</h4>
        <p>${copyTyping ? '按显示内容完整输入单词或短语' : '输入对应的英文单词或短语'}</p>
      </div>
      <form class="scene-spelling-form" data-scene-spelling-form>
        <input data-scene-spelling-input autocomplete="off" autocapitalize="off" spellcheck="false" aria-label="${ASSESSMENT_LABELS[type]}答案" />
        <button class="primary-button compact-primary" type="submit">提交</button>
      </form>
      ${feedback}
    `
    const form = elements.sceneAssessment.querySelector('[data-scene-spelling-form]')
    const input = elements.sceneAssessment.querySelector('[data-scene-spelling-input]')
    form?.addEventListener('submit', (event) => {
      event.preventDefault()
      if (input?.value.trim()) submitAssessment(input.value.trim())
    })
    input?.focus()
  }

  function selectNextCoreWord() {
    const unit = activeUnit()
    const coreWords = asArray(unit?.words).filter((word) => word.tier === 'core')
    const currentIndex = coreWords.findIndex((word) => sameId(word.id, state.currentSceneWordId))
    const next = coreWords.slice(currentIndex + 1).find((word) => !isWordComplete(word))
      || coreWords.find((word) => !isWordComplete(word))
      || coreWords[(currentIndex + 1) % coreWords.length]
    if (!next) return
    state.currentSceneWordId = next.id
    state.sceneAssessmentStartedAt = Date.now()
    assessmentFeedback = null
    renderCoreWords(coreWords)
    renderAssessment(unit)
  }

  async function submitAssessment(answer) {
    const plan = state.currentLearningPlan
    const unit = activeUnit(plan)
    const word = currentSceneWord(unit)
    const type = nextAssessment(word)
    if (!plan || !unit || !word || !type || !answer) return
    const startedAt = state.sceneAssessmentStartedAt || Date.now()
    try {
      const result = await request(`/api/v1/learning/plans/${encodeURIComponent(plan.id)}/units/${encodeURIComponent(unit.id)}/assessments`, {
        method: 'POST',
        body: JSON.stringify({
          unitEntryId: word.id,
          assessmentType: type,
          answer,
          attemptCount: 1,
          durationMillis: Math.max(0, Date.now() - startedAt),
        }),
      })
      word.learningState = result.learningState
      word.recognitionScore = result.recognitionScore
      word.spellingScore = result.spellingScore
      unit.completedCoreCount = result.completedCoreCount
      if (result.correct) {
        word.passedAssessments = [...new Set([...asArray(word.passedAssessments), type])]
        assessmentFeedback = { correct: true, message: '回答正确，已记录本词学习情况' }
        state.sceneAssessmentStartedAt = Date.now()
        renderCurrentScene()
      } else {
        assessmentFeedback = {
          correct: false,
          message: `还未答对，正确答案：${result.correctAnswer || word.term}`,
        }
        renderAssessment(unit)
      }
      logEvent('learning', '提交场景词汇检查', `${word.term} · ${ASSESSMENT_LABELS[type]} · ${result.correct ? '正确' : '错误'}`)
    } catch (error) {
      logEvent('error', '场景词汇检查失败', error.message)
      toast(`检查提交失败：${error.message}`)
    }
  }

  async function promoteWord(entryId) {
    const plan = state.currentLearningPlan
    const unit = activeUnit(plan)
    if (!plan || !unit) return
    try {
      await request(`/api/v1/learning/plans/${encodeURIComponent(plan.id)}/units/${encodeURIComponent(unit.id)}/entries/${encodeURIComponent(entryId)}/promote`, {
        method: 'POST',
      })
      await selectPlan(plan.id, { quiet: true })
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
    const confirmed = await confirmAction({
      title: '完成当前场景',
      message: `确认完成「${unit.title}」？完成后可手动生成下一个场景。`,
      acceptText: '完成场景',
    })
    if (!confirmed) return
    setButtonLoading(elements.sceneCompleteUnitBtn, true, '提交中...')
    try {
      const updated = await request(`/api/v1/learning/plans/${encodeURIComponent(plan.id)}/units/${encodeURIComponent(unit.id)}/complete`, {
        method: 'POST',
      })
      state.currentLearningPlan = updated
      state.learningPlans = state.learningPlans.map((item) => sameId(item.id, updated.id) ? { ...item, ...updated, units: [] } : item)
      renderPlanList()
      renderCurrentScene()
      logEvent('learning', '完成场景学习', `${plan.name} / ${unit.title}`)
      toast(updated.canGenerateNext ? '场景已完成，可继续生成下一个场景' : '学习计划已完成')
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
    const confirmed = await confirmAction({
      title: '生成下一个场景',
      message: 'AI 会根据剩余词汇与学习进度生成一个新场景。每天和每个计划均不限制场景数量。',
      acceptText: '开始生成',
    })
    if (!confirmed) return
    setButtonLoading(elements.sceneNextUnitBtn, true, '生成中...')
    try {
      const modelConfigId = elements.scenePlanModelSelect?.value || null
      await request(`/api/v1/learning/plans/${encodeURIComponent(plan.id)}/units/next`, {
        method: 'POST',
        body: JSON.stringify({ modelConfigId: modelConfigId ? Number(modelConfigId) : null }),
      })
      await selectPlan(plan.id, { quiet: true })
      toast('新场景已生成')
    } catch (error) {
      logEvent('error', '生成下一场景失败', error.message)
      toast(`场景生成失败：${error.message}`)
    } finally {
      setButtonLoading(elements.sceneNextUnitBtn, false)
    }
  }

  async function generateCards() {
    const plan = state.currentLearningPlan
    const unit = activeUnit(plan)
    if (!plan || !unit) return
    setButtonLoading(elements.sceneGenerateCardsBtn, true, '生成中...')
    try {
      const retry = state.sceneCardJob
        && sameId(state.sceneCardJob.unitId, unit.id)
        && number(state.sceneCardJob.failedCount) > 0
      const path = retry
        ? `/api/v1/vocabulary-card-jobs/${encodeURIComponent(state.sceneCardJob.jobId)}/retry`
        : `/api/v1/learning/plans/${encodeURIComponent(plan.id)}/units/${encodeURIComponent(unit.id)}/cards/generate`
      state.sceneCardJob = await request(path, {
        method: 'POST',
        body: JSON.stringify({ batchSize: 15 }),
      })
      await selectPlan(plan.id, { quiet: true })
      const failed = number(state.sceneCardJob.failedCount)
      elements.sceneGenerateCardsBtn.textContent = failed ? `重试失败词 (${failed})` : '补齐词卡'
      toast(`词卡任务完成：成功 ${number(state.sceneCardJob.successCount)}，失败 ${failed}`)
    } catch (error) {
      logEvent('error', '批量词卡生成失败', error.message)
      toast(`批量词卡生成失败：${error.message}`)
    } finally {
      setButtonLoading(elements.sceneGenerateCardsBtn, false)
    }
  }

  function openVocabularyImport() {
    renderSourceOptions()
    state.currentVocabularyImport = null
    state.vocabularyImportPage = 1
    elements.vocabularyImportFile.value = ''
    elements.vocabularyImportName.value = ''
    elements.vocabularyImportPurpose.value = ''
    elements.vocabularyReviewSection.classList.add('hidden')
    showModal(elements.vocabularyImportModal)
  }

  function closeVocabularyImport() {
    hideModal(elements.vocabularyImportModal)
  }

  async function startVocabularyImport() {
    const file = elements.vocabularyImportFile.files?.[0]
    const catalogName = elements.vocabularyImportName.value.trim()
    if (!file) {
      toast('请选择 Markdown 文件')
      return
    }
    if (!catalogName) {
      toast('请输入词表名称')
      return
    }
    setButtonLoading(elements.startVocabularyImportBtn, true, '解析中...')
    try {
      const content = await file.text()
      const result = await request('/api/v1/vocabulary-imports/markdown', {
        method: 'POST',
        body: JSON.stringify({
          catalogName,
          learningPurpose: elements.vocabularyImportPurpose.value.trim(),
          fileName: file.name,
          content,
        }),
      })
      state.currentVocabularyImport = result
      state.vocabularyImportPage = 1
      elements.vocabularyReviewSection.classList.remove('hidden')
      renderImportReview()
      await reloadImportHistory()
      logEvent('vocabulary', '导入 Markdown 词表', `${catalogName} · ${number(result.totalCount)} 词`)
      toast(`已解析 ${number(result.totalCount)} 个词，请确认疑似断词后发布`)
    } catch (error) {
      logEvent('error', '词表导入失败', error.message)
      toast(`词表导入失败：${error.message}`)
    } finally {
      setButtonLoading(elements.startVocabularyImportBtn, false)
    }
  }

  async function reloadImportHistory() {
    if (state.preview || !state.token) {
      renderImportList()
      renderSourceOptions()
      return
    }
    const imports = await request('/api/v1/vocabulary-imports')
    state.vocabularyImports = asArray(imports)
    renderImportList()
    renderSourceOptions()
  }

  async function openImportReview(jobId) {
    state.vocabularyImportPage = 1
    elements.vocabularyWarningOnly.checked = true
    elements.vocabularyImportKeyword.value = ''
    renderSourceOptions()
    showModal(elements.vocabularyImportModal)
    elements.vocabularyReviewSection.classList.remove('hidden')
    await loadImportReview(jobId)
  }

  async function loadImportReview(jobId = state.currentVocabularyImport?.jobId) {
    if (!jobId || state.preview) return
    try {
      const params = new URLSearchParams({
        warningOnly: String(Boolean(elements.vocabularyWarningOnly.checked)),
        page: String(state.vocabularyImportPage || 1),
        pageSize: String(state.vocabularyImportPageSize || 100),
      })
      const keyword = elements.vocabularyImportKeyword.value.trim()
      if (keyword) params.set('keyword', keyword)
      state.currentVocabularyImport = await request(`/api/v1/vocabulary-imports/${encodeURIComponent(jobId)}?${params}`)
      renderImportReview()
    } catch (error) {
      logEvent('error', '词表审核数据加载失败', error.message)
      toast(`词表审核加载失败：${error.message}`)
    }
  }

  function renderImportReview() {
    const current = state.currentVocabularyImport
    if (!current) return
    const published = current.status === 'published'
    elements.vocabularyImportSummary.textContent = `${number(current.totalCount)} 个词 · ${escapeHtml(current.catalogName || '')}`
    elements.vocabularyWarningSummary.textContent = `${number(current.pendingWarningCount)} 个待确认`
    elements.vocabularyWarningSummary.classList.toggle('ok', number(current.pendingWarningCount) === 0)
    elements.publishVocabularyImportBtn.disabled = published || number(current.pendingWarningCount) > 0
    elements.publishVocabularyImportBtn.textContent = published ? '已发布' : '发布并导入单词本'
    elements.vocabularyBatchConfirmBtn.disabled = published || number(current.pendingWarningCount) === 0
    const items = asArray(current.items)
    elements.vocabularyReviewRows.innerHTML = items.length
      ? items.map((item) => `
          <tr class="${item.suspicious ? 'warning' : ''}">
            <td>${number(item.sourceOrder)}</td>
            <td><strong>${escapeHtml(item.originalTerm)}</strong></td>
            <td>
              <div class="vocabulary-correction-field">
                <input value="${escapeHtml(item.approvedTerm || item.suggestedTerm || item.originalTerm || '')}" data-import-entry-input="${escapeHtml(item.id)}" ${published ? 'disabled' : ''} />
                ${item.suspicious && !published ? `<button class="secondary-button compact" type="button" data-save-import-entry="${escapeHtml(item.id)}">确认</button>` : ''}
              </div>
            </td>
            <td>${escapeHtml(item.phonetic || '')}</td>
            <td>${escapeHtml(item.definition || '')}</td>
            <td><span class="mini-pill ${item.reviewStatus === 'confirmed' || !item.suspicious ? 'ok' : ''}">${item.suspicious ? (item.reviewStatus === 'confirmed' ? '已确认' : '疑似断词') : '正常'}</span></td>
          </tr>
        `).join('')
      : '<tr><td colspan="6" class="empty">没有符合条件的词条</td></tr>'
    elements.vocabularyReviewRows.querySelectorAll('[data-save-import-entry]').forEach((button) => {
      button.addEventListener('click', () => saveImportEntry(button.dataset.saveImportEntry))
    })
    const page = number(current.page) || 1
    const pageSize = number(current.pageSize) || state.vocabularyImportPageSize
    const pages = Math.max(1, Math.ceil(number(current.filteredTotal) / pageSize))
    elements.vocabularyPageInfo.textContent = `第 ${page} / ${pages} 页 · ${number(current.filteredTotal)} 条`
    elements.vocabularyPrevPageBtn.disabled = page <= 1
    elements.vocabularyNextPageBtn.disabled = page >= pages
  }

  async function saveImportEntry(entryId) {
    const current = state.currentVocabularyImport
    const input = elements.vocabularyReviewRows.querySelector(`[data-import-entry-input="${CSS.escape(String(entryId))}"]`)
    const approvedTerm = input?.value.trim()
    if (!current || !approvedTerm) return
    try {
      await request(`/api/v1/vocabulary-imports/${encodeURIComponent(current.jobId)}/entries/${encodeURIComponent(entryId)}`, {
        method: 'PUT',
        body: JSON.stringify({ approvedTerm }),
      })
      await loadImportReview()
      await reloadImportHistory()
      toast('修正已确认')
    } catch (error) {
      logEvent('error', '疑似断词修正失败', error.message)
      toast(`修正失败：${error.message}`)
    }
  }

  async function confirmAllWarnings() {
    const current = state.currentVocabularyImport
    if (!current || number(current.pendingWarningCount) === 0) return
    const confirmed = await confirmAction({
      title: '采用全部建议',
      message: `将为剩余 ${number(current.pendingWarningCount)} 个疑似断词采用系统建议，仍可在发布前逐条修改。`,
      acceptText: '采用建议',
    })
    if (!confirmed) return
    try {
      await request(`/api/v1/vocabulary-imports/${encodeURIComponent(current.jobId)}/warnings/confirm`, {
        method: 'POST',
        body: JSON.stringify({ applySuggested: true }),
      })
      await loadImportReview()
      await reloadImportHistory()
      toast('已确认全部疑似断词')
    } catch (error) {
      logEvent('error', '批量确认疑似断词失败', error.message)
      toast(`批量确认失败：${error.message}`)
    }
  }

  async function publishVocabularyImport() {
    const current = state.currentVocabularyImport
    const wordbookId = normalizeWordbookId(elements.vocabularyImportWordbook.value)
    if (!current || !wordbookId || current.status === 'published') return
    if (number(current.pendingWarningCount) > 0) {
      toast('请先确认所有疑似断词')
      return
    }
    const wordbook = state.wordbooks.find((item) => sameId(item.id, wordbookId))
    const confirmed = await confirmAction({
      title: '发布并导入单词本',
      message: `确认发布「${current.catalogName}」并导入「${wordbook?.name || '所选单词本'}」？导入阶段不会批量生成 AI 词卡。`,
      acceptText: '发布词表',
    })
    if (!confirmed) return
    setButtonLoading(elements.publishVocabularyImportBtn, true, '发布中...')
    try {
      state.currentVocabularyImport = await request(`/api/v1/vocabulary-imports/${encodeURIComponent(current.jobId)}/publish`, {
        method: 'POST',
        body: JSON.stringify({ wordbookId: Number(wordbookId) }),
      })
      await Promise.allSettled([reloadImportHistory(), loadWordbooks?.()])
      renderImportReview()
      renderSourceOptions()
      logEvent('vocabulary', '发布词表', `${current.catalogName} -> ${wordbook?.name || wordbookId}`)
      toast('词表已发布并导入单词本，可创建场景学习计划')
    } catch (error) {
      logEvent('error', '词表发布失败', error.message)
      toast(`词表发布失败：${error.message}`)
    } finally {
      setButtonLoading(elements.publishVocabularyImportBtn, false)
    }
  }

  function openScenePlanModal() {
    renderSourceOptions()
    const selected = state.vocabularyImports.find((item) => String(item.catalogVersionId) === elements.sceneCatalogSelect?.value)
    elements.scenePlanNameInput.value = selected ? `${selected.catalogName}场景学习` : ''
    elements.scenePlanPurposeInput.value = selected?.learningPurpose || ''
    showModal(elements.scenePlanModal)
  }

  function closeScenePlanModal() {
    hideModal(elements.scenePlanModal)
  }

  async function createScenePlan() {
    const catalogVersionId = elements.sceneCatalogSelect.value
    const wordbookId = normalizeWordbookId(elements.scenePlanWordbookSelect.value)
    const name = elements.scenePlanNameInput.value.trim()
    if (!catalogVersionId || !wordbookId || !name) {
      toast('请选择已发布词表和单词本，并填写计划名称')
      return
    }
    setButtonLoading(elements.createScenePlanBtn, true, '生成首个场景中...')
    try {
      const modelConfigId = elements.scenePlanModelSelect.value
      const plan = await request('/api/v1/learning/plans', {
        method: 'POST',
        body: JSON.stringify({
          catalogVersionId: Number(catalogVersionId),
          wordbookId: Number(wordbookId),
          name,
          learningPurpose: elements.scenePlanPurposeInput.value.trim(),
          modelConfigId: modelConfigId ? Number(modelConfigId) : null,
          generateFirstUnit: true,
        }),
      })
      state.currentLearningPlan = plan
      await loadSceneData({ planId: plan.id })
      closeScenePlanModal()
      logEvent('learning', '创建场景学习计划', name)
      toast('学习计划和首个场景已生成')
    } catch (error) {
      logEvent('error', '创建场景学习计划失败', error.message)
      toast(`创建计划失败：${error.message}`)
    } finally {
      setButtonLoading(elements.createScenePlanBtn, false)
    }
  }

  function changeSceneWordbook() {
    const wordbookId = normalizeWordbookId(elements.sceneWordbookSelect.value)
    syncCurrentWordbookId(state, elements, wordbookId)
    elements.sceneWordbookSelect.value = wordbookId
    renderPlanList()
    const visiblePlans = plansForWordbook()
    if (!visiblePlans.some((plan) => sameId(plan.id, state.currentLearningPlan?.id))) {
      if (visiblePlans[0]) selectPlan(visiblePlans[0].id, { quiet: true })
      else {
        state.currentLearningPlan = null
        renderCurrentScene()
      }
    }
  }

  function changeImportSearch() {
    window.clearTimeout(importSearchTimer)
    importSearchTimer = window.setTimeout(() => {
      state.vocabularyImportPage = 1
      loadImportReview()
    }, 280)
  }

  function previousImportPage() {
    state.vocabularyImportPage = Math.max(1, number(state.vocabularyImportPage) - 1)
    loadImportReview()
  }

  function nextImportPage() {
    state.vocabularyImportPage = number(state.vocabularyImportPage) + 1
    loadImportReview()
  }

  return {
    loadSceneData,
    clearSceneData,
    renderSceneView,
    renderRelatedWords: () => renderRelatedWords(activeUnit()),
    openVocabularyImport,
    closeVocabularyImport,
    startVocabularyImport,
    loadImportReview,
    openImportReview,
    confirmAllWarnings,
    publishVocabularyImport,
    openScenePlanModal,
    closeScenePlanModal,
    createScenePlan,
    changeSceneWordbook,
    changeImportSearch,
    previousImportPage,
    nextImportPage,
    completeCurrentUnit,
    generateNextUnit,
    generateCards,
    speakCurrentScene: () => speakSentence(activeUnit()?.learningText || ''),
  }
}
