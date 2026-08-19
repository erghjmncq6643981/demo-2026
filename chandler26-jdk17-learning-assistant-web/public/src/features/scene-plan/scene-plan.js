import { hideModal, showModal } from '/src/shared/modal.js'
import { normalizeWordbookId, syncCurrentWordbookId } from '/src/shared/wordbook.js'
import { initDatetimePicker } from '/src/shared/datetime-picker.js'
import { renderMarkdown } from '/src/shared/vocabulary.js'
import { formatDateTime } from '/src/shared/text.js'

const TIER_LABELS = {
  core: '核心',
  extended: '词表扩展',
  supplementary: 'AI 补充',
  review: '复习',
}

const PLAN_STATUS_LABELS = {
  not_started: '未开始',
  active: '学习中',
  completed: '已完成',
  paused: '已暂停',
  cancelled: '已取消',
}

const IMPORT_STATUS_LABELS = {
  reviewing: '待审核',
  published: '已发布',
  failed: '失败',
}

const ANALYSIS_STATUS_LABELS = {
  not_started: '未开始',
  pending: '等待执行',
  running: '分析中',
  completed: '已完成',
  partial_failed: '部分完成',
  failed: '失败',
}

const ASSESSMENT_LABELS = {
  meaning_choice: '含义选择',
  copy_typing: '跟敲单词',
  meaning_spelling: '含义拼写',
}

const SOURCE_LABELS = {
  self_study: '自考',
  cet4: '四级',
  cet6: '六级',
  ielts: '雅思',
}

function asArray(value) {
  return Array.isArray(value) ? value : []
}

function number(value) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

function localDateKey(date = new Date()) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function previewWords() {
  const core = [
    ['declutter', '/diːˈklʌtər/', '清理不需要的物品', 'spelling'],
    ['laundry', '/ˈlɔːndri/', '待洗或洗好的衣物', 'recognition'],
    ['vacuum', '/ˈvækjuːm/', '用吸尘器清洁', 'spelling'],
    ['shelf', '/ʃelf/', '架子', 'recognition'],
    ['drawer', '/drɔːr/', '抽屉', 'recognition'],
    ['organize', '/ˈɔːrɡənaɪz/', '整理，使有条理', 'spelling'],
    ['dust', '/dʌst/', '擦去灰尘', 'recognition'],
    ['donate', '/ˈdoʊneɪt/', '捐赠', 'spelling'],
  ].map(([term, phonetic, meaning, masteryRequirement], index) => ({
    id: index + 1,
    wordbookEntryId: index + 1,
    term,
    normalizedTerm: term,
    phonetic,
    meaning,
    contextMeaning: meaning,
    tier: 'core',
    masteryRequirement,
    firstLearning: true,
    learningState: 'learning',
    cardStatus: index < 3 ? 'ready' : 'missing',
    passedAssessments: [],
    acceptedSpellings: [term],
    assessment: {
      prompt: `请选择“${term}”在周末大扫除场景中的含义`,
      options: [meaning, '预订旅行行程', '准备一顿晚餐', '参加课堂讨论'],
      correct_answer: meaning,
    },
  }))
  const relatedTerms = [
    ['T-shirt', '短袖 T 恤'], ['hoodie', '连帽衫'], ['blouse', '女式衬衫'], ['sweater', '毛衣'],
    ['jeans', '牛仔裤'], ['shorts', '短裤'], ['skirt', '裙子'], ['jacket', '夹克'],
    ['coat', '外套'], ['dress', '连衣裙'], ['wardrobe', '衣柜'], ['hanger', '衣架'],
  ]
  return [
    ...core,
    ...relatedTerms.map(([term, meaning], index) => ({
      id: 100 + index,
      term,
      normalizedTerm: term.toLowerCase(),
      meaning,
      contextMeaning: meaning,
      tier: index < 8 ? 'extended' : 'supplementary',
      masteryRequirement: 'recognition',
      firstLearning: false,
      cardStatus: 'not_required',
      passedAssessments: [],
    })),
  ]
}

function createPreviewCookingUnit(planId, unitNo, recommendedDate = localDateKey()) {
  const definitions = [
    ['chop', '/tʃɑːp/', '切碎', 'spelling'],
    ['ingredient', '/ɪnˈɡriːdiənt/', '食材，原料', 'recognition'],
    ['whisk', '/wɪsk/', '搅打', 'spelling'],
    ['skillet', '/ˈskɪlɪt/', '平底煎锅', 'recognition'],
    ['simmer', '/ˈsɪmər/', '用小火慢煮', 'recognition'],
    ['season', '/ˈsiːzən/', '给食物调味', 'spelling'],
    ['dough', '/doʊ/', '面团', 'recognition'],
    ['garnish', '/ˈɡɑːrnɪʃ/', '给菜肴加装饰配料', 'spelling'],
  ]
  const coreWords = definitions.map(([term, phonetic, meaning, masteryRequirement], index) => ({
    id: unitNo * 1000 + index + 1,
    wordbookEntryId: unitNo * 1000 + index + 1,
    term,
    normalizedTerm: term,
    phonetic,
    meaning,
    contextMeaning: meaning,
    tier: 'core',
    masteryRequirement,
    firstLearning: true,
    learningState: 'learning',
    cardStatus: 'missing',
    passedAssessments: [],
    acceptedSpellings: [term],
    assessment: {
      prompt: `请选择“${term}”在周末烹饪课场景中的含义`,
      options: [meaning, '整理旅行行李', '清洁卧室家具', '参加工作会议'],
      correct_answer: meaning,
    },
  }))
  const kitchenNouns = [
    ['spatula', '锅铲'], ['ladle', '长柄勺'], ['colander', '滤盆'], ['cutting board', '砧板'],
    ['apron', '围裙'], ['oven mitt', '隔热手套'], ['saucepan', '深平底锅'], ['measuring cup', '量杯'],
    ['peeler', '削皮器'], ['grater', '刨丝器'], ['rolling pin', '擀面杖'], ['pantry', '食品储藏柜'],
  ].map(([term, meaning], index) => ({
    id: unitNo * 1000 + 100 + index,
    term,
    normalizedTerm: term,
    meaning,
    contextMeaning: meaning,
    tier: index < 8 ? 'extended' : 'supplementary',
    masteryRequirement: 'recognition',
    firstLearning: false,
    cardStatus: 'not_required',
    passedAssessments: [],
  }))
  return {
    id: planId * 100 + unitNo,
    planId,
    unitNo,
    title: '周末烹饪课',
    scenarioType: 'Cooking & Kitchen',
    summary: '跟随食谱准备食材、使用厨具并完成一道家常菜。',
    status: 'ready',
    coreWordCount: coreWords.length,
    extendedWordCount: 8,
    supplementaryWordCount: 4,
    completedCoreCount: 0,
    recommendedDate,
    learningText: 'At the cooking class, Leo checked every ingredient before he began. He used a sharp knife to chop the vegetables and a whisk to mix the eggs. While the sauce simmered in a skillet, he learned to season the dough carefully. At the end, he used fresh herbs to garnish the finished dish.',
    translation: '在烹饪课上，利奥开始前检查了每一种食材。他用锋利的刀切碎蔬菜，用打蛋器搅打鸡蛋。酱汁在平底锅里慢煮时，他学习了如何给面团调味。最后，他用新鲜香草装饰完成的菜肴。',
    words: [...coreWords, ...kitchenNouns],
  }
}

function createPreviewPlan(options = {}) {
  const catalog = options.catalog || {
    catalogId: 1,
    catalogVersionId: 1,
    catalogName: '自考英语（二）全部词汇',
    totalCount: 5087,
  }
  const planId = options.id || 1
  const today = localDateKey()
  const previewEndDate = new Date(`${today}T12:00:00`)
  previewEndDate.setDate(previewEndDate.getDate() + 6)
  const unit = {
    id: planId * 100 + 1,
    planId,
    unitNo: 1,
    title: '周末大扫除',
    scenarioType: 'Home & Cleaning',
    summary: '整理衣柜、清洁房间，并处理不再需要的衣物。',
    status: 'in_progress',
    coreWordCount: 8,
    extendedWordCount: 8,
    supplementaryWordCount: 4,
    completedCoreCount: 0,
    recommendedDate: today,
    learningText: 'On Saturday morning, Mia decided to declutter her bedroom. She sorted the laundry, dusted each shelf, and used the vacuum under the bed. Then she opened every drawer to organize her clothes. She kept the items she often wore and packed the rest to donate.',
    translation: '周六早上，米娅决定清理卧室。她整理了衣物，擦拭每层架子，并用吸尘器清理床底。随后，她打开每个抽屉整理衣服，留下常穿的衣物，其余打包捐赠。',
    words: previewWords(),
  }
  return {
    id: planId,
    catalogId: catalog.catalogId,
    catalogVersionId: catalog.catalogVersionId,
    wordbookId: 1,
    name: options.name || '自考英语（二）场景突破',
    learningPurpose: options.learningPurpose || '三个月后参加自考英语（二），高频动词需要会拼写，其余词汇达到阅读中能识别。',
    startTime: `${today}T08:00:00`,
    endTime: `${localDateKey(previewEndDate)}T22:00:00`,
    status: 'active',
    totalCatalogWords: number(catalog.totalCount) || 5087,
    learnedCoreWords: options.learnedCoreWords ?? 120,
    completedUnitCount: options.completedUnitCount ?? 6,
    currentUnitId: unit.id,
    canGenerateNext: true,
    units: [unit],
  }
}

function previewCatalog() {
  return {
    catalogId: 1,
    catalogVersionId: 1,
    catalogName: '自考英语（二）全部词汇',
    sourceType: 'self_study',
    learningPurpose: '自考英语（二）考试大纲词汇',
    status: 'published',
    totalCount: 5087,
    publishedTime: new Date().toISOString(),
  }
}

function splitMarkdownRow(line) {
  let value = String(line || '').trim()
  if (value.startsWith('|')) value = value.slice(1)
  if (value.endsWith('|')) value = value.slice(0, -1)
  return value.split(/(?<!\\)\|/).map((cell) => cell.trim().replace(/\\\|/g, '|'))
}

function cleanMarkdownCell(value) {
  const trimmed = String(value || '').trim()
  return trimmed.startsWith('`') && trimmed.endsWith('`') ? trimmed.slice(1, -1).trim() : trimmed
}

function suggestedSplitCorrection(term) {
  const tokens = String(term || '').trim().split(/\s+/)
  for (let index = 0; index < tokens.length; index += 1) {
    const token = tokens[index]
    if (!/^[a-z]$/i.test(token)) continue
    if (index === tokens.length - 1 && index > 0 && (tokens[index - 1].match(/[a-z]/gi) || []).length >= 4) {
      return [...tokens.slice(0, index - 1), `${tokens[index - 1]}${token}`].join(' ')
    }
    if (index < tokens.length - 1 && !/^[ai]$/i.test(token) && (tokens[index + 1].match(/[a-z]/gi) || []).length >= 4) {
      return [...tokens.slice(0, index), `${token}${tokens[index + 1]}`, ...tokens.slice(index + 2)].join(' ')
    }
  }
  return term
}

function parsePreviewMarkdown(markdown) {
  const lines = String(markdown || '').replace(/\r\n?/g, '\n').split('\n')
  let columns = null
  const items = []
  for (const line of lines) {
    if (!line.includes('|')) continue
    const cells = splitMarkdownRow(line)
    if (!columns) {
      const names = cells.map((cell) => cleanMarkdownCell(cell).toLowerCase())
      const candidate = {
        order: names.indexOf('序号'),
        word: names.indexOf('word'),
        phonetic: names.indexOf('音标'),
        definition: names.indexOf('释义'),
      }
      if (Object.values(candidate).every((index) => index >= 0)) columns = candidate
      continue
    }
    if (cells.every((cell) => /^:?-{3,}:?$/.test(cell))) continue
    const originalTerm = cleanMarkdownCell(cells[columns.word])
    const orderText = cleanMarkdownCell(cells[columns.order])
    if (!originalTerm && !orderText) continue
    const sourceOrder = Number(orderText)
    if (!Number.isInteger(sourceOrder)) throw new Error(`词表序号不是有效整数：${orderText}`)
    if (!originalTerm) throw new Error(`序号 ${sourceOrder} 的 Word 为空`)
    const suggestedTerm = suggestedSplitCorrection(originalTerm)
    const suspicious = suggestedTerm !== originalTerm
    items.push({
      id: items.length + 1,
      sourceOrder,
      originalTerm,
      suggestedTerm,
      approvedTerm: null,
      effectiveTerm: originalTerm,
      phonetic: cleanMarkdownCell(cells[columns.phonetic]),
      definition: cleanMarkdownCell(cells[columns.definition]),
      suspicious,
      reviewStatus: suspicious ? 'pending' : 'not_required',
      warnings: suspicious ? ['疑似断词'] : [],
    })
  }
  if (!columns) throw new Error('未找到包含“序号、Word、音标、释义”的 Markdown 表头')
  if (!items.length) throw new Error('Markdown 表格中没有可导入词条')
  const orders = new Set()
  const duplicate = items.find((item) => orders.has(item.sourceOrder) || !orders.add(item.sourceOrder))
  if (duplicate) throw new Error(`词表序号重复：${duplicate.sourceOrder}`)
  return items
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
    if (plan.currentUnitId != null) {
      return units.find((unit) => sameId(unit.id, plan.currentUnitId))
        || [...units].reverse().find((unit) => unit.status !== 'completed')
        || null
    }
    return [...units].reverse().find((unit) => unit.status !== 'completed') || null
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

    renderSelectOptions(
      elements.sceneCatalogSelect,
      asArray(state.publicVocabularyCatalogs).map((item) => ({ ...item, id: item.catalogVersionId })),
      elements.sceneCatalogSelect?.value,
      (item) => `${item.catalogName} · ${SOURCE_LABELS[item.sourceType] || item.sourceType || '公共'} · ${item.totalCount || 0}词`,
      '请先导入并发布公共词本',
    )

    renderSelectOptions(
      elements.scenePlanSelect,
      asArray(state.learningPlans),
      state.currentLearningPlan?.id || elements.scenePlanSelect?.value,
      (item) => `${item.name} · ${number(item.learnedCoreWords)}/${number(item.totalCatalogWords)}词`,
      '暂无学习计划',
    )

    const enabledModels = asArray(state.modelConfigs).filter((item) => item.enabled)
    if (elements.scenePlanModelSelect) {
      const current = elements.scenePlanModelSelect.value
      elements.scenePlanModelSelect.innerHTML = '<option value="">使用 Agent 绑定模型</option>'
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
      if (!state.publicVocabularyCatalogs.length) state.publicVocabularyCatalogs = [previewCatalog()]
      if (!state.vocabularyImports.length) {
        const catalog = state.publicVocabularyCatalogs[0]
        state.vocabularyImports = [
          {
            jobId: 1,
            ...catalog,
            importerUserId: 1,
            importerName: '系统管理员',
            status: 'published',
            fileName: '自学考试(二)全部词汇5087_正序版.md',
            warningCount: 3,
            reviewedWarningCount: 3,
            pendingWarningCount: 0,
            items: [],
            filteredTotal: 0,
            page: 1,
            pageSize: state.vocabularyImportPageSize,
            createTime: new Date(Date.now() - 86400000 * 5).toISOString(),
          },
          {
            jobId: 2,
            catalogId: 2,
            catalogVersionId: 2,
            catalogName: '大学英语四级核心词汇',
            sourceType: 'cet4',
            totalCount: 3260,
            importerUserId: 2,
            importerName: '内容管理员',
            status: 'reviewing',
            fileName: 'cet4-core.md',
            warningCount: 8,
            reviewedWarningCount: 5,
            pendingWarningCount: 3,
            items: [],
            filteredTotal: 0,
            page: 1,
            pageSize: state.vocabularyImportPageSize,
            createTime: new Date(Date.now() - 86400000 * 2).toISOString(),
          },
        ]
      }
      if (!state.learningPlans.length) state.learningPlans = [createPreviewPlan({ catalog: state.publicVocabularyCatalogs[0] })]
      const selectedPlanId = options.planId || state.currentLearningPlan?.id || state.learningPlans[0]?.id
      state.currentLearningPlan = state.learningPlans.find((plan) => sameId(plan.id, selectedPlanId)) || state.learningPlans[0] || null
      if (state.currentLearningPlan && state.currentSceneWordId == null) {
        state.currentSceneWordId = activeUnit(state.currentLearningPlan)?.words?.find((word) => word.tier === 'core')?.id || null
      }
      renderSceneView()
      return
    }
    if (!state.token) {
      clearSceneData()
      return
    }
    const selectedPlanId = options.planId || state.currentLearningPlan?.id
    try {
      const canManageCatalogs = state.user?.roleCode === 'ADMIN'
      const [imports, publicCatalogs, plans] = await Promise.all([
        canManageCatalogs ? request('/api/v1/vocabulary-imports') : Promise.resolve([]),
        request('/api/v1/vocabulary-imports/public'),
        request('/api/v1/learning/plans'),
      ])
      state.vocabularyImports = asArray(imports)
      state.publicVocabularyCatalogs = asArray(publicCatalogs)
      state.learningPlans = asArray(plans)
      renderSourceOptions()
      renderPlanList()
      renderImportList()
      const visiblePlans = asArray(state.learningPlans)
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
    state.publicVocabularyCatalogs = []
    state.currentVocabularyImport = null
    state.learningPlans = []
    state.currentLearningPlan = null
    state.currentSceneWordId = null
    state.sceneCardJob = null
    state.sceneNote = { content: '', updateTime: null, unitId: null }
    state.sceneNoteMode = 'edit'
    assessmentFeedback = null
    renderSceneView()
  }

  function renderSceneView() {
    renderSourceOptions()
    renderPlanList()
    renderImportList()
    renderCurrentScene()
  }

  async function loadSceneNote(unit = activeUnit()) {
    if (!unit) {
      state.sceneNote = { content: '', updateTime: null, unitId: null }
      renderSceneNote(null)
      return
    }
    if (sameId(state.sceneNote?.unitId, unit.id)) {
      renderSceneNote(unit)
      return
    }
    if (state.preview) {
      const content = unit.note?.content || ''
      state.sceneNote = { content, updateTime: unit.note?.updateTime || null, unitId: unit.id }
      renderSceneNote(unit)
      return
    }
    try {
      const note = await request(`/api/v1/learning/plans/${encodeURIComponent(unit.planId)}/units/${encodeURIComponent(unit.id)}/note`)
      state.sceneNote = { content: note?.content || '', updateTime: note?.updateTime || null, unitId: unit.id }
      renderSceneNote(unit)
    } catch (error) {
      state.sceneNote = { content: '', updateTime: null, unitId: unit.id }
      renderSceneNote(unit, '笔记加载失败，可重试')
      logEvent('error', '场景笔记加载失败', error.message)
    }
  }

  function renderSceneNote(unit, errorMessage = '') {
    if (!elements.sceneNoteInput || !elements.sceneNotePreview) return
    const content = state.sceneNote?.content || ''
    elements.sceneNoteInput.value = content
    elements.sceneNotePreview.innerHTML = content
      ? renderMarkdown(content)
      : '<span class="empty">还没有笔记，记录本篇材料的重点、疑问或例句。</span>'
    elements.sceneNotePreview.classList.toggle('hidden', state.sceneNoteMode !== 'preview')
    elements.sceneNoteInput.classList.toggle('hidden', state.sceneNoteMode === 'preview')
    elements.sceneNotePreviewBtn.textContent = state.sceneNoteMode === 'preview' ? '编辑笔记' : '预览 Markdown'
    elements.sceneNoteStatus.textContent = errorMessage || (state.sceneNote?.updateTime ? `已保存 ${formatNoteTime(state.sceneNote.updateTime)}` : '未保存')
    elements.sceneNoteStatus.classList.toggle('error', Boolean(errorMessage))
    elements.sceneNoteSaveBtn.disabled = !unit || state.sceneNoteMode === 'preview'
  }

  function formatNoteTime(value) {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? '刚刚' : date.toLocaleString('zh-CN', { hour12: false })
  }

  function toggleSceneNotePreview() {
    const nextMode = state.sceneNoteMode === 'preview' ? 'edit' : 'preview'
    if (nextMode === 'preview' && elements.sceneNoteInput) {
      state.sceneNote = { ...state.sceneNote, content: elements.sceneNoteInput.value }
    }
    state.sceneNoteMode = nextMode
    renderSceneNote(activeUnit())
  }

  async function saveSceneNote() {
    const unit = activeUnit()
    if (!unit || !elements.sceneNoteInput) return
    const content = elements.sceneNoteInput.value
    elements.sceneNoteSaveBtn.disabled = true
    elements.sceneNoteStatus.textContent = '保存中...'
    try {
      if (state.preview) {
        state.sceneNote = { content, updateTime: new Date().toISOString(), unitId: unit.id }
        unit.note = { content, updateTime: state.sceneNote.updateTime }
      } else {
        const note = await request(`/api/v1/learning/plans/${encodeURIComponent(unit.planId)}/units/${encodeURIComponent(unit.id)}/note`, {
          method: 'PUT',
          body: JSON.stringify({ content }),
        })
        state.sceneNote = { content: note?.content || '', updateTime: note?.updateTime || new Date().toISOString(), unitId: unit.id }
      }
      renderSceneNote(unit)
      toast('场景材料笔记已保存')
    } catch (error) {
      elements.sceneNoteStatus.textContent = '保存失败，请重试'
      elements.sceneNoteStatus.classList.add('error')
      toast(`场景笔记保存失败：${error.message}`)
    } finally {
      elements.sceneNoteSaveBtn.disabled = false
    }
  }

  function renderPlanList() {
    const plans = asArray(state.learningPlans)
    elements.scenePlanCount.textContent = String(plans.length)
    renderSelectOptions(
      elements.scenePlanSelect,
      plans,
      state.currentLearningPlan?.id,
      (item) => `${item.name} · ${number(item.learnedCoreWords)}/${number(item.totalCatalogWords)}词`,
      '暂无学习计划',
    )
    if (!plans.length) {
      elements.scenePlanList.className = 'scene-plan-list empty'
      elements.scenePlanList.textContent = '暂无学习计划'
      elements.profileLearningPlanList.className = 'profile-learning-plan-list empty'
      elements.profileLearningPlanList.textContent = '暂无学习计划'
      return
    }
    const cards = plans
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
    elements.scenePlanList.className = 'scene-plan-list'
    elements.scenePlanList.innerHTML = cards
    elements.profileLearningPlanList.className = 'profile-learning-plan-list'
    elements.profileLearningPlanList.innerHTML = plans.map((plan) => `
      <article class="profile-learning-plan-card">
        <div>
          <span class="mini-pill">${escapeHtml(PLAN_STATUS_LABELS[plan.status] || plan.status || '学习中')}</span>
          <h4>${escapeHtml(plan.name)}</h4>
          <p>${escapeHtml(plan.learningPurpose || '未填写学习目标')}</p>
        </div>
        <div class="profile-plan-progress">
          <strong>${number(plan.learnedCoreWords)} / ${number(plan.totalCatalogWords)}</strong>
          <span>已掌握词汇 · ${number(plan.completedUnitCount)} 个场景</span>
          <div class="plan-actions" style="display: flex; gap: 8px; align-items: center; margin-top: 10px;">
            <button class="secondary-button compact" type="button" data-open-scene-plan="${escapeHtml(plan.id)}">进入挑战</button>
            <button class="icon-action-button" type="button" data-scene-plan-edit="${escapeHtml(plan.id)}" title="修改计划" aria-label="修改计划">✎</button>
          </div>
        </div>
      </article>
    `).join('')
    for (const container of [elements.scenePlanList, elements.profileLearningPlanList]) {
      container.querySelectorAll('[data-scene-plan-id], [data-open-scene-plan]').forEach((button) => {
        button.addEventListener('click', async () => {
          const planId = button.dataset.scenePlanId || button.dataset.openScenePlan
          await changeSelectedPlan(planId)
          if (button.dataset.openScenePlan) document.querySelector('[data-view="scenePlanView"]')?.click()
        })
      })
    }
    elements.profileLearningPlanList.querySelectorAll('[data-scene-plan-edit]').forEach((button) => {
      button.addEventListener('click', (e) => {
        e.stopPropagation()
        openScenePlanModal(button.dataset.scenePlanEdit)
      })
    })
  }

  function renderImportList() {
    if (!elements.sceneImportList) return
    const canManageCatalogs = state.preview || state.user?.roleCode === 'ADMIN'
    const imports = asArray(state.vocabularyImports)
    if (!imports.length) {
      elements.sceneImportList.className = 'scene-import-list empty'
      elements.sceneImportList.textContent = '暂无导入记录'
      return
    }
    elements.sceneImportList.className = 'scene-import-list'
    elements.sceneImportList.innerHTML = imports
      .map((item) => `
        <div class="scene-import-card ${sameId(item.jobId, state.currentVocabularyImport?.jobId) ? 'active' : ''}">
          <button class="scene-import-main" type="button" data-import-job-id="${escapeHtml(item.jobId)}">
            <span class="scene-item-topline">
              <strong>${escapeHtml(item.catalogName)}</strong>
              <small class="import-status ${item.status}">${escapeHtml(IMPORT_STATUS_LABELS[item.status] || item.status)}</small>
            </span>
            <span>${escapeHtml(SOURCE_LABELS[item.sourceType] || item.sourceType || '公共词本')} · ${number(item.totalCount)} 词 · ${number(item.pendingWarningCount)} 个待确认</span>
            <small>导入人：${escapeHtml(item.importerName || (item.importerUserId ? `用户 #${item.importerUserId}` : '系统管理员'))} · ${escapeHtml(formatDateTime(item.createTime) || '时间未知')}</small>
          </button>
          ${canManageCatalogs ? `
          <div class="row-actions">
            <button class="icon-action-button" type="button" data-import-job-edit="${escapeHtml(item.jobId)}" title="编辑词表" aria-label="编辑词表">✎</button>
            <button class="danger-icon-button" type="button" data-import-job-delete="${escapeHtml(item.jobId)}" title="删除导入记录">×</button>
          </div>
          ` : ''}
        </div>
      `)
      .join('')
    elements.sceneImportList.querySelectorAll('[data-import-job-id]').forEach((button) => {
      button.addEventListener('click', () => openImportReview(button.dataset.importJobId))
    })
    elements.sceneImportList.querySelectorAll('[data-import-job-edit]').forEach((button) => {
      button.addEventListener('click', (e) => {
        e.stopPropagation()
        openImportReview(button.dataset.importJobEdit)
      })
    })
    elements.sceneImportList.querySelectorAll('[data-import-job-delete]').forEach((button) => {
      button.addEventListener('click', async (e) => {
        e.stopPropagation()
        await deleteImportJob(button.dataset.importJobDelete)
      })
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
      state.sceneChallengeStage = options.keepStage ? state.sceneChallengeStage : 'overview'
      assessmentFeedback = null
      if (!state.sceneCalendarCursorDate) state.sceneCalendarCursorDate = localDateKey()
      renderPlanList()
      await loadCalendarData(plan)
      renderCurrentScene()
      await loadSceneNote(activeUnit(plan))
      if (!options.quiet) logEvent('learning', '切换场景学习计划', plan.name)
    } catch (error) {
      logEvent('error', '学习计划加载失败', error.message)
      toast(`学习计划加载失败：${error.message}`)
    }
  }

  function changeSelectedPlan(planId) {
    if (!planId || sameId(planId, state.currentLearningPlan?.id)) {
      if (elements.scenePlanSelect && planId) elements.scenePlanSelect.value = String(planId)
      return Promise.resolve()
    }
    return selectPlan(planId)
  }

  function addDays(date, count) {
    const result = new Date(date)
    result.setDate(result.getDate() + count)
    return result
  }

  function dateKey(date) {
    return localDateKey(date)
  }

  function dateFromKey(key) {
    const [year, month, day] = String(key || '').split('-').map(Number)
    if (![year, month, day].every(Number.isFinite)) return new Date()
    return new Date(year, month - 1, day, 12)
  }

  function startOfWeek(date) {
    const result = new Date(date)
    const day = result.getDay()
    const offset = day === 0 ? -6 : 1 - day
    result.setDate(result.getDate() + offset)
    result.setHours(12, 0, 0, 0)
    return result
  }

  function calendarDates() {
    const anchor = dateFromKey(state.sceneCalendarCursorDate || localDateKey())
    if (state.sceneCalendarRange === 'month') {
      const first = new Date(anchor.getFullYear(), anchor.getMonth(), 1, 12)
      const gridStart = startOfWeek(first)
      return Array.from({ length: 42 }, (_, index) => addDays(gridStart, index))
    }
    const first = startOfWeek(anchor)
    return Array.from({ length: 7 }, (_, index) => addDays(first, index))
  }

  function calendarTitle(dates) {
    if (!dates.length) return '本周'
    if (state.sceneCalendarRange === 'month') {
      const anchor = dateFromKey(state.sceneCalendarCursorDate || localDateKey())
      return `${anchor.getFullYear()}年${anchor.getMonth() + 1}月`
    }
    const start = dates[0]
    const end = dates[dates.length - 1]
    const endYear = start.getFullYear() === end.getFullYear() ? '' : `${end.getFullYear()}年`
    return `${start.getFullYear()}年${start.getMonth() + 1}月${start.getDate()}日 - ${endYear}${end.getMonth() + 1}月${end.getDate()}日`
  }

  async function loadCalendarData(plan) {
    if (!plan || state.preview || !state.token) {
      state.sceneCalendarData = null
      return null
    }
    const dates = calendarDates()
    const from = dateKey(dates[0])
    const to = dateKey(dates[dates.length - 1])
    try {
      const calendarData = await request(
        `/api/v1/learning/plans/${encodeURIComponent(plan.id)}/calendar?from=${from}&to=${to}`,
      )
      const currentDates = calendarDates()
      const isCurrentRange = sameId(state.currentLearningPlan?.id, plan.id)
        && dateKey(currentDates[0]) === from
        && dateKey(currentDates[currentDates.length - 1]) === to
      if (isCurrentRange) state.sceneCalendarData = calendarData
      return calendarData
    } catch (error) {
      const currentDates = calendarDates()
      if (sameId(state.currentLearningPlan?.id, plan.id)
          && dateKey(currentDates[0]) === from
          && dateKey(currentDates[currentDates.length - 1]) === to) {
        state.sceneCalendarData = null
      }
      logEvent('error', '学习日历加载失败', error.message)
      return null
    }
  }

  async function refreshCalendarData(plan) {
    const planId = plan?.id
    await loadCalendarData(plan)
    if (planId && state.currentLearningPlan && sameId(state.currentLearningPlan.id, planId)) {
      renderCalendar(state.currentLearningPlan)
    }
  }

  function formatCalendarDate(date, withMonth = false) {
    return withMonth
      ? `${date.getMonth() + 1}月${date.getDate()}日`
      : `${date.getDate()}日`
  }

  function unitDateKey(unit) {
    if (unit?.recommendedDate) return unit.recommendedDate
    return unit?.generatedTime ? unit.generatedTime.split('T')[0] : ''
  }

  function unitsForDate(plan, key) {
    return asArray(plan?.units).filter((unit) => unitDateKey(unit) === key)
  }

  function pendingChallengeWords(unit) {
    return asArray(unit?.words).filter((word) => word.tier === 'core' && !isWordComplete(word))
  }

  function unitStatusLabel(unit) {
    if (unit?.status === 'completed') return '已完成'
    if (unit?.status === 'in_progress') return '学习中'
    return '待学习'
  }

  function closeSceneVocabularyPreview() {
    hideModal(elements.sceneVocabularyPreviewModal)
  }

  function openSceneVocabularyPreview({ date, unitId } = {}) {
    const plan = state.currentLearningPlan
    if (!plan) return
    const selectedUnits = unitId
      ? asArray(plan.units).filter((unit) => sameId(unit.id, unitId))
      : unitsForDate(plan, date)
    const displayDate = date || unitDateKey(selectedUnits[0])

    let pendingTotal = 0
    let completedTotal = 0
    selectedUnits.forEach((unit) => {
      const coreWords = asArray(unit.words).filter((word) => word.tier === 'core')
      coreWords.forEach((word) => {
        if (isWordComplete(word)) {
          completedTotal++
        } else {
          pendingTotal++
        }
      })
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
          return `
            <section class="scene-vocabulary-preview-group">
              <div class="scene-vocabulary-preview-heading">
                <div>
                  <strong>${escapeHtml(unit.title || '场景单元')}</strong>
                  <small>Scene ${number(unit.unitNo)} · ${unitStatusLabel(unit)}</small>
                </div>
                <span class="mini-pill">${coreWords.length} 核心词</span>
              </div>
              ${coreWords.length ? `
                <div class="scene-vocabulary-preview-words">
                  ${coreWords.map((word, index) => {
                    const complete = isWordComplete(word)
                    return `
                    <div class="scene-vocabulary-preview-word ${complete ? 'completed' : ''}">
                      <span class="scene-vocabulary-preview-index">${index + 1}</span>
                      <div class="scene-vocabulary-preview-word-content">
                        <div class="scene-vocabulary-preview-topline">
                          <strong>${escapeHtml(word.term)}</strong>
                          <small>${escapeHtml(word.phonetic || '')}</small>
                        </div>
                        <p class="scene-vocabulary-preview-meaning">${escapeHtml(word.contextMeaning || word.meaning || '场景释义待补充')}</p>
                      </div>
                      <span class="scene-vocabulary-preview-status-pill ${complete ? 'completed' : 'pending'}">
                        ${complete ? '已完成' : (word.masteryRequirement === 'spelling' ? '待拼写' : '待认读')}
                      </span>
                    </div>
                  `}).join('')}
                </div>
              ` : '<div class="empty">本场景暂无核心词汇</div>'}
            </section>
          `
        }).join('')
      : '暂无词汇'
    showModal(elements.sceneVocabularyPreviewModal)
  }

  function renderCalendar(plan) {
    if (!elements.sceneCalendar) return
    const range = state.sceneCalendarRange || 'week'
    if (!plan) {
      elements.sceneCalendar.className = 'scene-calendar empty'
      elements.sceneCalendar.textContent = '选择计划后查看学习日历'
      if (elements.sceneCalendarTitle) {
        elements.sceneCalendarTitle.textContent = range === 'month'
          ? `${new Date().getFullYear()}年${new Date().getMonth() + 1}月`
          : calendarTitle(calendarDates())
      }
      renderOverviewUnits(null)
      return
    }
    const today = dateFromKey(localDateKey())
    const dates = calendarDates()
    const planStartKey = plan.startTime ? plan.startTime.split('T')[0] : null
    const planEndKey = plan.endTime ? plan.endTime.split('T')[0] : null
    const todayKey = dateKey(today)

    if (elements.sceneCalendarTitle) elements.sceneCalendarTitle.textContent = calendarTitle(dates)
    if (elements.sceneCalendarPreviousBtn) {
      const label = range === 'month' ? '上个月' : '上一周'
      elements.sceneCalendarPreviousBtn.title = label
      elements.sceneCalendarPreviousBtn.setAttribute('aria-label', label)
    }
    if (elements.sceneCalendarNextBtn) {
      const label = range === 'month' ? '下个月' : '下一周'
      elements.sceneCalendarNextBtn.title = label
      elements.sceneCalendarNextBtn.setAttribute('aria-label', label)
    }

    // Calculate suggestedDailyCount based on remaining unassigned words & days
    const generatedCoreCount = asArray(plan.units).reduce((sum, u) => sum + number(u.coreWordCount), 0)
    const remainingToGenerate = Math.max(0, number(plan.totalCatalogWords) - generatedCoreCount)
    let suggestedDailyCount = 8
    if (plan.endTime) {
      const planStart = plan.startTime ? new Date(plan.startTime) : today
      const planEnd = new Date(plan.endTime)
      const startForRemaining = today > planStart ? today : planStart
      startForRemaining.setHours(12, 0, 0, 0)
      planEnd.setHours(12, 0, 0, 0)
      const diffTime = planEnd.getTime() - startForRemaining.getTime()
      const remainingDays = diffTime <= 0 ? 1 : Math.ceil(diffTime / (1000 * 3600 * 24)) + 1
      if (remainingDays > 0) {
        suggestedDailyCount = Math.ceil(remainingToGenerate / remainingDays)
      }
    } else {
      const currentUnit = activeUnit(plan)
      suggestedDailyCount = number(currentUnit?.coreWordCount) || 8
    }
    suggestedDailyCount = Math.max(8, suggestedDailyCount)

    const dayDataFor = (key) => asArray(state.sceneCalendarData)
      .find((item) => String(item?.date || '').slice(0, 10) === key)

    let totalScheduled = 0
    dates.forEach((date) => {
      const key = dateKey(date)
      const withinPlan = (!planStartKey || key >= planStartKey) && (!planEndKey || key <= planEndKey)
      const units = unitsForDate(plan, key)
      const dayData = dayDataFor(key)
      const generated = number(dayData?.generatedUnitCount) > 0 || units.length > 0
      if (generated) {
        const pendingCount = dayData
          ? number(dayData.pendingChallengeCount)
          : units.reduce((sum, unit) => sum + pendingChallengeWords(unit).length, 0)
        totalScheduled += pendingCount
      } else if (withinPlan && key >= todayKey) {
        totalScheduled += suggestedDailyCount
      }
    })

    const remainingWords = Math.max(0, number(plan.totalCatalogWords) - number(plan.learnedCoreWords))
    const rangeLabel = range === 'month' ? '本月预计' : '本周预计'

    elements.sceneCalendar.className = `scene-calendar ${range}`
    elements.sceneCalendar.innerHTML = `
      <div class="scene-calendar-summary">
        <span><strong>${rangeLabel} ${Math.min(remainingWords, totalScheduled)}</strong> 个待挑战词汇（每日目标约 ${suggestedDailyCount} 词）</span>
        <small>点击日期可预览词汇；可提前生成后续场景</small>
      </div>
      <div class="scene-calendar-grid">
        ${dates.map((date) => {
          const key = dateKey(date)
          const isToday = key === todayKey
          const isPast = key < todayKey
          const withinPlan = (!planStartKey || key >= planStartKey) && (!planEndKey || key <= planEndKey)
          const units = unitsForDate(plan, key)
          const dayData = dayDataFor(key)
          const generated = number(dayData?.generatedUnitCount) > 0 || units.length > 0
          const pendingCount = dayData
            ? number(dayData.pendingChallengeCount)
            : units.reduce((sum, unit) => sum + pendingChallengeWords(unit).length, 0)
          const overdue = number(dayData?.overdueCount) > 0 || (isPast && pendingCount > 0)
          const isCompleted = units.length && units.every((unit) => unit.status === 'completed')

          let count = pendingCount
          let label = '待挑战词汇'
          if (generated) {
            if (isCompleted) {
              label = '已完成'
              count = units.reduce((sum, unit) => sum + number(unit.completedCoreCount || unit.coreWordCount), 0)
                || number(dayData?.completedCount)
                || 0
            } else {
              label = overdue ? `逾期 ${pendingCount}` : '待挑战词汇'
            }
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

          return `
            <button class="scene-calendar-day ${isToday ? 'today' : ''} ${isPast ? 'past' : ''} ${!withinPlan ? 'outside-plan' : ''} ${overdue ? 'overdue' : ''} ${isCompleted ? 'completed-day' : ''}" type="button" data-calendar-preview="${key}" aria-label="预览 ${formatCalendarDate(date, true)} 的词汇">
              <span>${range === 'month' ? `${date.getDate()}日` : formatCalendarDate(date, true)}</span>
              <strong>${count}</strong>
              <small>${label}</small>
            </button>
          `
        }).join('')}
      </div>
    `
    document.querySelectorAll('[data-calendar-range]').forEach((button) => {
      button.classList.toggle('active', button.dataset.calendarRange === range)
    })
    elements.sceneCalendar.querySelectorAll('[data-calendar-preview]').forEach((button) => {
      button.addEventListener('click', () => openSceneVocabularyPreview({ date: button.dataset.calendarPreview }))
    })
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

    // Only use explicitly-set plan dates for filtering — do NOT fall back to createTime
    const planStartKey = plan.startTime ? plan.startTime.split('T')[0] : null
    const planEndKey = plan.endTime ? plan.endTime.split('T')[0] : null
    const hasPlanDateRange = !!(planStartKey || planEndKey)

    // Filter dates to plan range when a range is defined
    if (hasPlanDateRange) {
      dates = dates.filter((date) => {
        const key = dateKey(date)
        if (planStartKey && key < planStartKey) return false
        if (planEndKey && key > planEndKey) return false
        return true
      })
    } else {
      // No date range: only keep dates that have a generated unit
      dates = dates.filter((date) => {
        const key = dateKey(date)
        return asArray(plan.units).some((u) => {
          if (u.recommendedDate) return u.recommendedDate === key
          if (u.generatedTime) return u.generatedTime.split('T')[0] === key
          return false
        })
      })
    }

    if (!dates.length) {
      // Hide the container entirely when there's nothing to show
      elements.sceneOverviewUnitsContainer.classList.add('hidden')
      return
    }
    elements.sceneOverviewUnitsList.innerHTML = dates.map((date) => {
      const key = dateKey(date)
      const isPast = key < todayKey
      const isToday = key === todayKey
      const units = unitsForDate(plan, key)

      if (units.length > 1) {
        const totalPending = units.reduce((sum, u) => sum + pendingChallengeWords(u).length, 0)
        const totalCompleted = units.reduce((sum, u) => sum + number(u.completedCoreCount || u.coreWordCount), 0)
        const allCompleted = units.every((u) => u.status === 'completed')
        const statusLabel = allCompleted ? '已完成' : (isToday ? '今日任务' : '待学习')
        const statusClass = allCompleted ? 'generated' : (isToday ? 'today' : 'generated')
        const dayMeta = allCompleted
          ? `共 ${units.length} 篇场景材料 · ${totalCompleted} 个已完成词汇`
          : `共 ${units.length} 篇场景材料 · ${totalPending} 个待挑战词汇`

        return `
          <div class="scene-overview-day-group ${isToday ? 'today' : ''} ${isPast ? 'past' : ''}">
            <div class="day-group-header">
              <div class="day-group-date-info">
                <span class="day-group-date">${formatCalendarDate(date, true)}</span>
                <span class="unit-status-tag ${statusClass}">${statusLabel}</span>
              </div>
              <span class="day-group-meta">${dayMeta}</span>
            </div>
            <div class="day-group-units">
              ${units.map((unit, idx) => {
                const isComplete = unit.status === 'completed'
                const pendingCount = pendingChallengeWords(unit).length
                const completedCount = number(unit.completedCoreCount || unit.coreWordCount)
                const wordInfo = isComplete
                  ? `${completedCount} 个已完成词汇 · 已完成`
                  : `${pendingCount} 个待挑战词汇 · ${unitStatusLabel(unit)}`
                return `
                  <div class="day-unit-sub-row">
                    <button class="unit-preview-button" type="button" data-preview-date="${key}" data-preview-unit="${escapeHtml(unit.id)}" aria-label="预览第 ${idx + 1} 篇 ${escapeHtml(unit.title || '场景单元')} 的词汇">
                      <span class="unit-index-badge">篇章 ${idx + 1}/${units.length}</span>
                      <span class="unit-detail-info">
                        <strong class="unit-title">${escapeHtml(unit.title || '场景单元')}</strong>
                        <span class="unit-words-count">${wordInfo}</span>
                      </span>
                    </button>
                    <div class="unit-action-button">
                      <button class="primary-button compact-primary" type="button" data-action-learn="${escapeHtml(unit.id)}">
                        ${unit.status === 'completed' ? '回顾场景' : '开始学习'}
                      </button>
                    </div>
                  </div>
                `
              }).join('')}
            </div>
          </div>
        `
      }

      const unit = units.length === 1 ? units[0] : null
      const isComplete = unit?.status === 'completed'
      const pendingCount = unit ? pendingChallengeWords(unit).length : 0
      const completedCount = unit ? number(unit.completedCoreCount || unit.coreWordCount) : 0
      const wordInfo = unit
        ? (isComplete ? `${completedCount} 个已完成词汇 · 已完成` : `${pendingCount} 个待挑战词汇 · ${unitStatusLabel(unit)}`)
        : '场景生成后可预览待挑战与已完成词汇'
      return `
        <div class="scene-overview-unit-row ${isToday ? 'today' : ''} ${isPast ? 'past' : ''}">
          <button class="unit-preview-button" type="button" data-preview-date="${key}" ${unit ? `data-preview-unit="${escapeHtml(unit.id)}"` : ''} aria-label="预览 ${unit ? escapeHtml(unit.title || '场景单元') : formatCalendarDate(date, true)} 的词汇">
            <span class="unit-date-info">
              <span class="unit-date">${formatCalendarDate(date, true)}</span>
              <span class="unit-status-tag ${unit ? (isComplete ? 'generated' : 'today') : 'pending'}">${unit ? unitStatusLabel(unit) : '待生成'}</span>
            </span>
            <span class="unit-detail-info">
              ${unit ? `
                <strong class="unit-title">${escapeHtml(unit.title || '场景单元')}</strong>
                <span class="unit-words-count">${wordInfo}</span>
              ` : `
                <span class="unit-placeholder-text">${wordInfo}</span>
              `}
            </span>
          </button>
          <div class="unit-action-button">
            ${unit ? `
              <button class="primary-button compact-primary" type="button" data-action-learn="${escapeHtml(unit.id)}">
                ${unit.status === 'completed' ? '回顾场景' : '开始学习'}
              </button>
            ` : `
              <button class="secondary-button compact" type="button" data-action-generate="${escapeHtml(plan.id)}" data-recommended-date="${key}" ${plan.status !== 'active' ? 'disabled' : ''}>
                生成场景
              </button>
            `}
          </div>
        </div>
      `
    }).join('')

    elements.sceneOverviewUnitsList.querySelectorAll('[data-preview-date]').forEach((row) => {
      row.addEventListener('click', () => openSceneVocabularyPreview({
        date: row.dataset.previewDate,
        unitId: row.dataset.previewUnit || null,
      }))
    })

    elements.sceneOverviewUnitsList.querySelectorAll('[data-action-learn]').forEach((button) => {
      button.addEventListener('click', async (event) => {
        event.stopPropagation()
        const unitId = button.dataset.actionLearn
        if (!plan) return
        setButtonLoading(button, true, '打开中...')
        try {
          const selectedUnit = asArray(plan.units).find((unit) => sameId(unit.id, unitId))
          if (state.preview) {
            if (selectedUnit?.status !== 'completed') {
              asArray(plan.units).forEach((unit) => {
                if (unit.status === 'in_progress') unit.status = 'ready'
              })
              selectedUnit.status = 'in_progress'
            }
            plan.currentUnitId = unitId
          } else if (selectedUnit?.status !== 'completed') {
            const updated = await request(`/api/v1/learning/plans/${encodeURIComponent(plan.id)}/units/${encodeURIComponent(unitId)}/start`, {
              method: 'POST',
            })
            state.currentLearningPlan = updated
            state.learningPlans = state.learningPlans.map((item) => sameId(item.id, updated.id) ? { ...item, ...updated } : item)
          } else {
            plan.currentUnitId = unitId
          }
          const active = activeUnit(state.currentLearningPlan)
          state.currentSceneWordId = asArray(active?.words).find((word) => word.tier === 'core' && !isWordComplete(word))?.id
            || asArray(active?.words).find((word) => word.tier === 'core')?.id
            || null
          renderCurrentScene()
          await loadSceneNote(selectedUnit)
          startLearning()
        } catch (error) {
          logEvent('error', '开始场景失败', error.message)
          toast(`开始场景失败：${error.message}`)
        } finally {
          setButtonLoading(button, false)
        }
      })
    })

    elements.sceneOverviewUnitsList.querySelectorAll('[data-action-generate]').forEach((button) => {
      button.addEventListener('click', async (event) => {
        event.stopPropagation()
        setButtonLoading(button, true, '生成中...')
        try {
          const recommendedDate = button.dataset.recommendedDate || null
          let generatedCount = 1
          if (state.preview) {
            const generated = createPreviewCookingUnit(plan.id, asArray(plan.units).length + 1, recommendedDate)
            plan.units.push(generated)
          } else {
            const modelConfigId = elements.scenePlanModelSelect?.value || null
            const generatedUnits = await request(`/api/v1/learning/plans/${encodeURIComponent(plan.id)}/units/next`, {
              method: 'POST',
              body: JSON.stringify({ modelConfigId: modelConfigId || null, recommendedDate }),
            })
            generatedCount = Math.max(1, asArray(generatedUnits).length)
          }
          await loadSceneData({ planId: plan.id })
          toast(generatedCount > 1
            ? `当日词汇已均分生成 ${generatedCount} 篇场景材料`
            : '场景材料已生成，可点击场景学习计划预览词汇')
        } catch (error) {
          toast(`生成场景失败：${error.message}`)
        } finally {
          setButtonLoading(button, false)
        }
      })
    })
  }

  function renderChallengeWords(coreWords) {
    const spellingCount = coreWords.filter((word) => word.masteryRequirement === 'spelling').length
    const recognitionCount = coreWords.length - spellingCount
    elements.sceneChallengeWordCount.textContent = `${coreWords.length} 词`
    elements.sceneChallengeWords.className = coreWords.length ? 'scene-challenge-prep' : 'scene-challenge-prep empty'
    elements.sceneChallengeWords.innerHTML = coreWords.length
      ? `<div><strong>${coreWords.length}</strong><span>本轮词数</span></div><div><strong>${recognitionCount}</strong><span>含义选择</span></div><div><strong>${spellingCount}</strong><span>拼写检查</span></div><p>挑战开始后逐题作答，不再重复展示完整词表。</p>`
      : '当前没有可挑战词汇'
  }

  function applySceneStage(stage) {
    state.sceneChallengeStage = stage
    const hasPlan = Boolean(state.currentLearningPlan && activeUnit())
    const inLearning = hasPlan && stage !== 'overview'

    if (elements.scenePlanToolbar) {
      elements.scenePlanToolbar.classList.toggle('hidden', inLearning)
    }
    if (elements.scenePlanSidebar) {
      elements.scenePlanSidebar.classList.toggle('hidden', inLearning)
    }
    if (elements.scenePlanLayout) {
      elements.scenePlanLayout.classList.toggle('scene-focus-layout', inLearning)
    }

    elements.scenePlanOverview.classList.toggle('hidden', inLearning)
    elements.sceneLearningStage.classList.toggle('hidden', !inLearning)
    const showReading = stage === 'learning'
    elements.sceneLearningStage.querySelector('.scene-unit-header')?.classList.toggle('hidden', false)
    elements.sceneLearningStage.querySelector('.scene-reading-panel')?.classList.toggle('hidden', !showReading)
    if (elements.sceneLearningFooter) {
      elements.sceneLearningFooter.classList.toggle('hidden', !showReading)
    }
    elements.sceneChallengeStage.classList.toggle('hidden', stage !== 'challenge')
    elements.sceneAssessmentPanel.classList.toggle('hidden', stage !== 'assessment')
  }

  function openSceneNoteModal() {
    const unit = activeUnit()
    if (!unit) return
    void loadSceneNote(unit)
    showModal(elements.sceneNoteModal)
  }

  function closeSceneNoteModal() {
    hideModal(elements.sceneNoteModal)
  }

  function openCoreWordsModal() {
    const unit = activeUnit()
    if (!unit) return
    const coreWords = asArray(unit.words).filter((word) => word.tier === 'core')
    renderCoreWords(coreWords)
    showModal(elements.sceneCoreWordsModal)
  }

  function closeCoreWordsModal() {
    hideModal(elements.sceneCoreWordsModal)
  }

  function openRelatedWordsModal() {
    const unit = activeUnit()
    if (!unit) return
    renderRelatedWords(unit)
    showModal(elements.sceneRelatedWordsModal)
  }

  function closeRelatedWordsModal() {
    hideModal(elements.sceneRelatedWordsModal)
  }

  function startLearning() {
    if (!state.currentLearningPlan || !activeUnit()) return
    applySceneStage('learning')
    elements.sceneLearningStage.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  function showChallengeWords() {
    const unit = activeUnit()
    if (!unit) return
    renderChallengeWords(asArray(unit.words).filter((word) => word.tier === 'core'))
    applySceneStage('challenge')
    elements.sceneChallengeStage.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  function startChallenge() {
    const unit = activeUnit()
    const coreWords = asArray(unit?.words).filter((word) => word.tier === 'core')
    if (!coreWords.length) return
    const firstIncomplete = coreWords.find((word) => !isWordComplete(word)) || coreWords[0]
    state.currentSceneWordId = firstIncomplete.id
    state.sceneAssessmentStartedAt = Date.now()
    assessmentFeedback = null
    applySceneStage('assessment')
    renderAssessment(unit)
    elements.sceneAssessmentPanel.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  function backToReading() {
    applySceneStage('learning')
  }

  function backToPlanOverview() {
    applySceneStage('overview')
  }

  function changeCalendarRange(range) {
    if (!['week', 'month'].includes(range)) return
    state.sceneCalendarRange = range
    if (!state.sceneCalendarCursorDate) state.sceneCalendarCursorDate = localDateKey()
    state.sceneCalendarData = null
    renderCalendar(state.currentLearningPlan)
    void refreshCalendarData(state.currentLearningPlan)
  }

  function changeCalendarOffset(offset) {
    const plan = state.currentLearningPlan
    const anchor = dateFromKey(state.sceneCalendarCursorDate || localDateKey())
    if (state.sceneCalendarRange === 'month') {
      anchor.setDate(1)
      anchor.setMonth(anchor.getMonth() + offset)
    } else {
      anchor.setDate(anchor.getDate() + offset * 7)
    }
    state.sceneCalendarCursorDate = localDateKey(anchor)
    state.sceneCalendarData = null
    renderCalendar(plan)
    void refreshCalendarData(plan)
  }

  function resetCalendar() {
    state.sceneCalendarCursorDate = localDateKey()
    state.sceneCalendarData = null
    renderCalendar(state.currentLearningPlan)
    void refreshCalendarData(state.currentLearningPlan)
  }

  function formatPlanDate(dateStr) {
    if (!dateStr) return '-'
    const d = new Date(dateStr)
    if (isNaN(d.getTime())) return '-'
    const pad = (n) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  }

  function renderCurrentScene() {
    const plan = state.currentLearningPlan
    const unit = activeUnit(plan)
    elements.sceneOverviewTitle.textContent = plan?.name || '选择学习计划'
    elements.sceneOverviewSummary.textContent = plan?.learningPurpose || '通过日历了解近期学习量，再开始当前场景。'
    elements.sceneOverviewProgress.textContent = plan
      ? `${number(plan.learnedCoreWords)} / ${number(plan.totalCatalogWords)} 词`
      : '0 / 0 词'

    // Plan Meta Bar and Transition Buttons
    if (plan) {
      elements.scenePlanMetaBar.classList.remove('hidden')
      const startStr = formatPlanDate(plan.startTime)
      const endStr = formatPlanDate(plan.endTime)
      elements.scenePlanDatesText.textContent = `${startStr} 至 ${endStr}`
      elements.scenePlanStatusText.textContent = PLAN_STATUS_LABELS[plan.status] || plan.status || '-'

      elements.scenePlanPauseBtn.classList.add('hidden')
      elements.scenePlanResumeBtn.classList.add('hidden')
      elements.scenePlanCancelBtn.classList.add('hidden')

      if (plan.status === 'not_started') {
        elements.scenePlanResumeBtn.classList.remove('hidden')
        elements.scenePlanResumeBtn.textContent = '启动计划'
        elements.scenePlanCancelBtn.classList.remove('hidden')
      } else if (plan.status === 'active') {
        elements.scenePlanPauseBtn.classList.remove('hidden')
        elements.scenePlanCancelBtn.classList.remove('hidden')
      } else if (plan.status === 'paused') {
        elements.scenePlanResumeBtn.classList.remove('hidden')
        elements.scenePlanResumeBtn.textContent = '恢复计划'
        elements.scenePlanCancelBtn.classList.remove('hidden')
      }
      elements.sceneStartLearningBtn.disabled = !unit || plan.status !== 'active'
    } else {
      elements.scenePlanMetaBar.classList.add('hidden')
      elements.scenePlanPauseBtn.classList.add('hidden')
      elements.scenePlanResumeBtn.classList.add('hidden')
      elements.scenePlanCancelBtn.classList.add('hidden')
      elements.sceneStartLearningBtn.disabled = !unit
    }

    elements.sceneStartLearningBtn.classList.toggle('hidden', !unit)
    elements.sceneOverviewNextUnitBtn.classList.toggle('hidden', !plan?.canGenerateNext || Boolean(unit) || plan?.status !== 'active')
    elements.sceneScheduleNextUnitBtn?.classList.toggle('hidden', !plan?.canGenerateNext || Boolean(unit) || plan?.status !== 'active')
    renderCalendar(plan)
    applySceneStage(state.sceneChallengeStage || 'overview')
    if (!plan || !unit) {
      elements.sceneUnitEyebrow.textContent = plan ? 'Ready for next scene' : 'Current Scene'
      elements.sceneUnitTitle.textContent = plan ? '可以生成下一个场景' : '选择一个学习计划'
      const rawSummary = plan?.learningPurpose || '当前场景会显示在这里'
      elements.sceneUnitSummary.dataset.tooltip = rawSummary
      elements.sceneUnitSummary.removeAttribute('title')
      elements.sceneUnitSummary.textContent = rawSummary
      elements.sceneUnitProgress.textContent = plan ? `${number(plan.learnedCoreWords)} / ${number(plan.totalCatalogWords)}` : '0 / 0'
      elements.sceneLearningText.className = 'scene-learning-text empty'
      elements.sceneLearningText.textContent = plan ? '当前没有进行中的场景' : '暂无场景材料'
      elements.sceneTranslation.textContent = '暂无译文'
      if (elements.sceneCoreWords) {
        elements.sceneCoreWords.className = 'scene-core-words empty'
        elements.sceneCoreWords.textContent = '暂无核心词汇'
      }
      if (elements.sceneCoreCount) elements.sceneCoreCount.textContent = '0'
      if (elements.sceneCoreModalCount) elements.sceneCoreModalCount.textContent = '0 词'
      if (elements.sceneRelatedWords) {
        elements.sceneRelatedWords.className = 'scene-related-words empty'
        elements.sceneRelatedWords.textContent = '暂无场景相关词汇'
      }
      if (elements.sceneRelatedCount) elements.sceneRelatedCount.textContent = '0'
      if (elements.sceneRelatedModalCount) elements.sceneRelatedModalCount.textContent = '0 词'
      elements.sceneAssessment.className = 'scene-assessment empty'
      elements.sceneAssessment.textContent = '选择一个核心词开始检查'
      elements.sceneAssessmentStage.textContent = '未开始'
      if (elements.sceneGenerateCardsBtn) elements.sceneGenerateCardsBtn.classList.add('hidden')
      if (elements.sceneScheduleCardsBtn) elements.sceneScheduleCardsBtn.classList.add('hidden')
      if (elements.sceneCompleteUnitBtn) elements.sceneCompleteUnitBtn.classList.add('hidden')
      if (elements.sceneNextUnitBtn) elements.sceneNextUnitBtn.classList.toggle('hidden', !plan?.canGenerateNext || plan?.status !== 'active')
      renderChallengeWords([])
      return
    }

    const coreWords = asArray(unit.words).filter((word) => word.tier === 'core')
    const missingCards = asArray(unit.words).some((word) =>
      ['core', 'review'].includes(word.tier) && ['missing', 'failed'].includes(word.cardStatus),
    )
    elements.sceneUnitEyebrow.textContent = `Scene ${unit.unitNo || asArray(plan.units).length} · ${unit.scenarioType || 'Vocabulary'}`
    elements.sceneUnitTitle.textContent = unit.title || '未命名场景'
    const rawSummary = unit.summary || plan.learningPurpose || '通过当前场景学习相关词汇'
    elements.sceneUnitSummary.dataset.tooltip = rawSummary
    elements.sceneUnitSummary.removeAttribute('title')
    elements.sceneUnitSummary.textContent = rawSummary
    elements.sceneUnitProgress.textContent = `${number(unit.completedCoreCount)} / ${number(unit.coreWordCount)}`
    if (elements.sceneGenerateCardsBtn) elements.sceneGenerateCardsBtn.classList.toggle('hidden', !missingCards || plan.status !== 'active')
    if (elements.sceneScheduleCardsBtn) elements.sceneScheduleCardsBtn.classList.toggle('hidden', !missingCards || plan.status !== 'active')
    if (elements.sceneCompleteUnitBtn) elements.sceneCompleteUnitBtn.classList.toggle('hidden', unit.status === 'completed' || number(unit.completedCoreCount) < number(unit.coreWordCount) || plan.status !== 'active')
    if (elements.sceneNextUnitBtn) elements.sceneNextUnitBtn.classList.toggle('hidden', !plan.canGenerateNext || plan.status !== 'active')
    renderLearningText(unit, coreWords)
    renderCoreWords(coreWords)
    renderRelatedWords(unit)
    renderChallengeWords(coreWords)
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
    if (elements.sceneCoreCount) elements.sceneCoreCount.textContent = String(words.length)
    if (elements.sceneCoreModalCount) elements.sceneCoreModalCount.textContent = `${words.length} 词`
    if (!elements.sceneCoreWords) return
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
    if (elements.sceneRelatedCount) elements.sceneRelatedCount.textContent = String(related.length)
    if (elements.sceneRelatedModalCount) elements.sceneRelatedModalCount.textContent = `${related.length} 词`
    if (!elements.sceneRelatedWords) return
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
            ${word.tier !== 'supplementary' ? `<span class="mini-pill">${escapeHtml(TIER_LABELS[word.tier] || word.tier)}</span>` : '<span></span>'}
            ${['extended', 'supplementary'].includes(word.tier) ? `<button class="icon-action-button" type="button" data-promote-word="${escapeHtml(word.id)}" title="加入核心" aria-label="加入核心">＋</button>` : ''}
          </div>
        </article>
      `)
      .join('')
    elements.sceneRelatedWords.querySelectorAll('[data-promote-word]').forEach((button) => {
      button.addEventListener('click', () => promoteWord(button.dataset.promoteWord))
    })
  }

  function renderAssessment(unit) {
    const coreWords = asArray(unit?.words).filter((item) => item.tier === 'core')
    const completedWords = coreWords.filter(isWordComplete)
    if (coreWords.length && completedWords.length === coreWords.length) {
      elements.sceneAssessmentStage.textContent = `${coreWords.length} / ${coreWords.length}`
      elements.sceneAssessment.className = 'scene-assessment'
      elements.sceneAssessment.innerHTML = `
        <div class="scene-assessment-complete scene-unit-complete">
          <span class="scene-check-mark">✓</span>
          <strong>本轮 ${coreWords.length} 个词已全部通过</strong>
          <p>含义识别和要求掌握的拼写项目已写入学习记录。</p>
          <div class="scene-complete-actions">
            <button class="secondary-button compact" type="button" data-return-reading>回看场景</button>
            <button class="primary-button compact-primary" type="button" data-finish-challenge>完成本场景</button>
          </div>
        </div>
      `
      elements.sceneAssessment.querySelector('[data-return-reading]')?.addEventListener('click', backToReading)
      elements.sceneAssessment.querySelector('[data-finish-challenge]')?.addEventListener('click', completeCurrentUnit)
      return
    }
    const word = currentSceneWord(unit)
    if (!word) {
      elements.sceneAssessment.className = 'scene-assessment empty'
      elements.sceneAssessment.textContent = '选择一个核心词开始检查'
      elements.sceneAssessmentStage.textContent = '未开始'
      return
    }
    const type = nextAssessment(word)
    const passedCount = asArray(word.passedAssessments).filter((item) => requiredAssessments(word).includes(item)).length
    const wordIndex = coreWords.findIndex((item) => sameId(item.id, word.id)) + 1
    elements.sceneAssessmentStage.textContent = type
      ? `第 ${wordIndex}/${coreWords.length} 词 · ${passedCount + 1}/${requiredAssessments(word).length}`
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
      let result
      if (state.preview) {
        const normalizedAnswer = String(answer).trim().toLowerCase()
        const correctAnswer = type === 'meaning_choice'
          ? word.assessment?.correct_answer || word.contextMeaning || word.meaning
          : word.term
        const accepted = type === 'meaning_choice'
          ? [correctAnswer]
          : asArray(word.acceptedSpellings).length ? word.acceptedSpellings : [word.term]
        const correct = accepted.some((value) => String(value).trim().toLowerCase() === normalizedAnswer)
        if (correct) {
          word.passedAssessments = [...new Set([...asArray(word.passedAssessments), type])]
          word.learningState = isWordComplete(word) ? 'learned' : 'learning'
          word.recognitionScore = type === 'meaning_choice' ? 100 : number(word.recognitionScore)
          word.spellingScore = type !== 'meaning_choice' ? 100 : number(word.spellingScore)
        }
        unit.completedCoreCount = asArray(unit.words).filter((item) => item.tier === 'core' && isWordComplete(item)).length
        result = {
          correct,
          correctAnswer,
          learningState: word.learningState,
          recognitionScore: word.recognitionScore,
          spellingScore: word.spellingScore,
          completedCoreCount: unit.completedCoreCount,
        }
      } else {
        result = await request(`/api/v1/learning/plans/${encodeURIComponent(plan.id)}/units/${encodeURIComponent(unit.id)}/assessments`, {
          method: 'POST',
          body: JSON.stringify({
            unitEntryId: word.id,
            assessmentType: type,
            answer,
            attemptCount: 1,
            durationMillis: Math.max(0, Date.now() - startedAt),
          }),
        })
      }
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
      if (state.preview) {
        const word = asArray(unit.words).find((item) => sameId(item.id, entryId))
        if (word) {
          word.tier = 'core'
          word.firstLearning = true
          word.assessment ||= {
            prompt: `请选择“${word.term}”在当前场景中的含义`,
            options: [word.contextMeaning || word.meaning, '预订旅行行程', '准备一顿晚餐', '参加课堂讨论'],
            correct_answer: word.contextMeaning || word.meaning,
          }
          unit.coreWordCount = number(unit.coreWordCount) + 1
        }
        renderCurrentScene()
      } else {
        await request(`/api/v1/learning/plans/${encodeURIComponent(plan.id)}/units/${encodeURIComponent(unit.id)}/entries/${encodeURIComponent(entryId)}/promote`, {
          method: 'POST',
        })
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
    const confirmed = await confirmAction({
      title: '完成当前场景',
      message: `确认完成「${unit.title}」？完成后可手动生成下一个场景。`,
      acceptText: '完成场景',
    })
    if (!confirmed) return
    setButtonLoading(elements.sceneCompleteUnitBtn, true, '提交中...')
    try {
      const updated = state.preview
        ? (() => {
            unit.status = 'completed'
            plan.learnedCoreWords = number(plan.learnedCoreWords) + number(unit.completedCoreCount)
            plan.completedUnitCount = number(plan.completedUnitCount) + 1
            plan.currentUnitId = null
            plan.canGenerateNext = number(plan.learnedCoreWords) < number(plan.totalCatalogWords)
            return plan
          })()
        : await request(`/api/v1/learning/plans/${encodeURIComponent(plan.id)}/units/${encodeURIComponent(unit.id)}/complete`, {
            method: 'POST',
          })
      state.currentLearningPlan = updated
      state.learningPlans = state.learningPlans.map((item) => sameId(item.id, updated.id) ? { ...item, ...updated } : item)
      state.sceneChallengeStage = 'overview'
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
      title: '提前生成场景',
      message: 'AI 会按学习计划生成待学习场景材料；超过 50 个待挑战词会自动均分为多篇，不会中断当前场景。',
      acceptText: '开始生成',
    })
    if (!confirmed) return
    setButtonLoading(elements.sceneNextUnitBtn, true, '生成中...')
    try {
      let generatedCount = 1
      if (state.preview) {
        const generated = createPreviewCookingUnit(plan.id, asArray(plan.units).length + 1)
        plan.units.push(generated)
        renderSceneView()
      } else {
        const modelConfigId = elements.scenePlanModelSelect?.value || null
        const generatedUnits = await request(`/api/v1/learning/plans/${encodeURIComponent(plan.id)}/units/next`, {
          method: 'POST',
          body: JSON.stringify({ modelConfigId: modelConfigId || null }),
        })
        generatedCount = Math.max(1, asArray(generatedUnits).length)
        await selectPlan(plan.id, { quiet: true, keepStage: true })
      }
      toast(generatedCount > 1
        ? `当日词汇已均分生成 ${generatedCount} 篇场景材料`
        : '场景材料已生成，可在场景学习计划中预览')
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
    const confirmed = await confirmAction({
      title: '安排低价时段生成',
      message: '任务会进入任务中心，并在每日 00:00 - 06:00 自动生成场景材料；当前学习场景不会被替换。',
      acceptText: '安排任务',
    })
    if (!confirmed) return
    setButtonLoading(elements.sceneScheduleNextUnitBtn, true, '安排中...')
    try {
      if (state.preview) {
        state.sceneScheduledTask = { id: Date.now(), taskName: '批量生成场景材料', status: 'pending', executionMode: 'low_cost_window' }
        toast('设计预览：任务已进入低价时段队列')
      } else {
        const modelConfigId = elements.scenePlanModelSelect?.value || null
        const task = await request(`/api/v1/learning/plans/${encodeURIComponent(plan.id)}/units/next/async`, {
          method: 'POST',
          body: JSON.stringify({ modelConfigId: modelConfigId || null, executionMode: 'low_cost_window' }),
        })
        state.sceneScheduledTask = task
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
    const confirmed = await confirmAction({
      title: '安排低价时段补齐词卡',
      message: '缺失词卡会在每日 00:00 - 06:00 分批生成，生成结果可在任务中心查看。',
      acceptText: '安排任务',
    })
    if (!confirmed) return
    setButtonLoading(elements.sceneScheduleCardsBtn, true, '安排中...')
    try {
      if (state.preview) {
        state.sceneScheduledTask = { id: Date.now(), taskName: '批量生成场景词卡', status: 'pending', executionMode: 'low_cost_window' }
        toast('设计预览：词卡任务已进入低价时段队列')
      } else {
        state.sceneCardJob = await request(`/api/v1/learning/plans/${encodeURIComponent(plan.id)}/units/${encodeURIComponent(unit.id)}/cards/generate`, {
          method: 'POST',
          body: JSON.stringify({ batchSize: 15, executionMode: 'low_cost_window' }),
        })
        toast('词卡任务已安排，可在个人信息 - 任务中心查看')
      }
    } catch (error) {
      logEvent('error', '安排词卡生成失败', error.message)
      toast(`安排词卡生成失败：${error.message}`)
    } finally {
      setButtonLoading(elements.sceneScheduleCardsBtn, false)
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

  function openVocabularyImport() {
    renderSourceOptions()
    state.currentVocabularyImport = null
    state.currentVocabularyAnalysis = null
    state.vocabularyImportPage = 1
    elements.vocabularyImportFile.value = ''
    elements.vocabularyImportName.value = ''
    elements.vocabularyImportPurpose.value = ''
    elements.vocabularyImportSourceType.value = 'self_study'
    
    // Enable inputs
    elements.vocabularyImportFile.disabled = false
    elements.vocabularyImportName.disabled = false
    elements.vocabularyImportSourceType.disabled = false
    elements.vocabularyImportPurpose.disabled = false
    
    // Reset visual file upload text
    const placeholder = document.getElementById('fileUploadPlaceholder')
    if (placeholder) {
      placeholder.textContent = '选择 Markdown 文件'
    }
    
    // Show/hide buttons appropriately
    elements.startVocabularyImportBtn.classList.remove('hidden')
    elements.saveVocabularyImportMetadataBtn.classList.add('hidden')
    elements.startVocabularyImportBtn.disabled = false
    elements.saveVocabularyImportMetadataBtn.disabled = false
    
    elements.vocabularyReviewSection.classList.add('hidden')
    showModal(elements.vocabularyImportModal)
  }

  function closeVocabularyImport() {
    hideModal(elements.vocabularyImportModal)
  }

  async function startVocabularyImport() {
    const file = elements.vocabularyImportFile.files?.[0]
    const catalogName = elements.vocabularyImportName.value.trim()
    const sourceType = elements.vocabularyImportSourceType.value
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
      let result
      if (state.preview) {
        const allItems = parsePreviewMarkdown(content)
        const warningCount = allItems.filter((item) => item.suspicious).length
        result = {
          jobId: Date.now(),
          catalogId: Date.now(),
          catalogVersionId: Date.now(),
          catalogName,
          sourceType,
          learningPurpose: elements.vocabularyImportPurpose.value.trim(),
          fileName: file.name,
          status: 'reviewing',
          totalCount: allItems.length,
          warningCount,
          reviewedWarningCount: 0,
          pendingWarningCount: warningCount,
          page: 1,
          pageSize: state.vocabularyImportPageSize,
          filteredTotal: allItems.length,
          items: allItems,
          _allItems: allItems,
          createTime: new Date().toISOString(),
        }
        state.vocabularyImports.unshift(result)
      } else {
        result = await request('/api/v1/vocabulary-imports/markdown', {
          method: 'POST',
          body: JSON.stringify({
            catalogName,
            sourceType,
            learningPurpose: elements.vocabularyImportPurpose.value.trim(),
            fileName: file.name,
            content,
          }),
        })
      }
      state.currentVocabularyImport = result
      state.vocabularyImportPage = 1
      elements.vocabularyReviewSection.classList.remove('hidden')
      await loadImportReview(result.jobId)
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
    state.currentVocabularyAnalysis = null
    state.vocabularyImportPage = 1
    elements.vocabularyWarningOnly.checked = false
    elements.vocabularyImportKeyword.value = ''
    renderSourceOptions()
    showModal(elements.vocabularyImportModal)
    elements.vocabularyReviewSection.classList.remove('hidden')
    await loadImportReview(jobId)
  }

  async function loadImportReview(jobId = state.currentVocabularyImport?.jobId) {
    if (!jobId) return
    if (state.preview) {
      const source = asArray(state.vocabularyImports).find((item) => sameId(item.jobId, jobId)) || state.currentVocabularyImport
      if (!source) return
      const allItems = asArray(source._allItems).length ? source._allItems : asArray(source.items)
      const keyword = elements.vocabularyImportKeyword.value.trim().toLowerCase()
      const warningOnly = Boolean(elements.vocabularyWarningOnly.checked)
      const filtered = allItems.filter((item) => {
        if (warningOnly && !item.suspicious) return false
        const haystack = `${item.originalTerm || ''} ${item.approvedTerm || ''} ${item.definition || ''}`.toLowerCase()
        return !keyword || haystack.includes(keyword)
      })
      const pageSize = number(state.vocabularyImportPageSize) || 100
      const pages = Math.max(1, Math.ceil(filtered.length / pageSize))
      state.vocabularyImportPage = Math.min(Math.max(1, number(state.vocabularyImportPage) || 1), pages)
      const start = (state.vocabularyImportPage - 1) * pageSize
      source.page = state.vocabularyImportPage
      source.pageSize = pageSize
      source.filteredTotal = filtered.length
      source.items = filtered.slice(start, start + pageSize)
      source.pendingWarningCount = allItems.filter((item) => item.suspicious && item.reviewStatus !== 'confirmed').length
      source.reviewedWarningCount = allItems.filter((item) => item.suspicious && item.reviewStatus === 'confirmed').length
      state.currentVocabularyImport = source
      if (source.status === 'published' && source.catalogVersionId) {
        const total = number(source.totalCount)
        state.currentVocabularyAnalysis = {
          catalogId: source.catalogId,
          catalogVersionId: source.catalogVersionId,
          status: 'not_started',
          publishedCount: total,
          analyzedCount: 0,
          unanalyzedCount: total,
          canTrigger: total > 0,
        }
      }
      renderImportReview()
      renderImportList()
      return
    }
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
      if (state.currentVocabularyImport.status === 'published') {
        await loadVocabularyAnalysis(state.currentVocabularyImport.catalogVersionId, { quiet: true })
      }
    } catch (error) {
      logEvent('error', '词表审核数据加载失败', error.message)
      toast(`词表审核加载失败：${error.message}`)
    }
  }

  function renderImportReview() {
    const current = state.currentVocabularyImport
    if (!current) return
    const canManageCatalogs = state.preview || state.user?.roleCode === 'ADMIN'
    const isPublicCatalogView = current.status === 'published' || !canManageCatalogs
    const SOURCE_LABELS = {
      self_study: '自考',
      cet4: '四级',
      cet6: '六级',
      ielts: '雅思',
      toefl: '托福',
    }
    
    // Update modal header
    if (elements.vocabularyImportEyebrow) {
      elements.vocabularyImportEyebrow.textContent = isPublicCatalogView ? 'Public Vocabulary' : 'Vocabulary Import'
    }
    if (elements.vocabularyImportTitle) {
      elements.vocabularyImportTitle.textContent = isPublicCatalogView ? (current.catalogName || '公共词本详情') : (current.jobId ? '审核 Markdown 词表' : '导入 Markdown 词表')
    }

    // Toggle import form vs clean metadata banner
    if (elements.vocabularyImportForm) {
      elements.vocabularyImportForm.classList.toggle('hidden', isPublicCatalogView)
    }
    if (elements.vocabularyCatalogMetaBanner) {
      elements.vocabularyCatalogMetaBanner.classList.toggle('hidden', !isPublicCatalogView)
      if (isPublicCatalogView) {
        if (elements.catalogMetaSourceType) elements.catalogMetaSourceType.textContent = SOURCE_LABELS[current.sourceType] || current.sourceType || '公共词本'
        if (elements.catalogMetaPurpose) elements.catalogMetaPurpose.textContent = current.learningPurpose || '官方精选公共词表'
        if (elements.catalogMetaTotal) elements.catalogMetaTotal.textContent = `${number(current.totalCount)} 词`
      }
    }

    if (!isPublicCatalogView) {
      // Populate metadata inputs for draft import
      elements.vocabularyImportName.value = current.catalogName || ''
      elements.vocabularyImportSourceType.value = current.sourceType || 'self_study'
      elements.vocabularyImportPurpose.value = current.learningPurpose || ''
      
      elements.vocabularyImportFile.disabled = false
      elements.vocabularyImportName.disabled = false
      elements.vocabularyImportSourceType.disabled = false
      elements.vocabularyImportPurpose.disabled = false
      
      const placeholder = document.getElementById('fileUploadPlaceholder')
      if (placeholder) {
        placeholder.textContent = current.fileName || '已导入文件'
      }
      
      elements.startVocabularyImportBtn.classList.toggle('hidden', current.jobId != null)
      elements.saveVocabularyImportMetadataBtn.classList.toggle('hidden', current.jobId == null)
      elements.startVocabularyImportBtn.disabled = false
      elements.saveVocabularyImportMetadataBtn.disabled = false
    }
    
    elements.vocabularyImportSummary.textContent = `${number(current.totalCount)} 个词 · ${escapeHtml(current.catalogName || '')}`
    
    // Warning summary and warning filter: hide in public catalog view
    if (elements.vocabularyWarningSummary) {
      elements.vocabularyWarningSummary.textContent = `${number(current.pendingWarningCount)} 个待确认`
      elements.vocabularyWarningSummary.classList.toggle('ok', number(current.pendingWarningCount) === 0)
      elements.vocabularyWarningSummary.classList.toggle('hidden', isPublicCatalogView)
    }
    if (elements.vocabularyWarningOnlyLabel) {
      elements.vocabularyWarningOnlyLabel.classList.toggle('hidden', isPublicCatalogView)
    }
    
    // Publish & batch confirm buttons: hidden in public catalog view
    if (elements.publishVocabularyImportBtn) {
      elements.publishVocabularyImportBtn.classList.toggle('hidden', isPublicCatalogView)
      elements.publishVocabularyImportBtn.disabled = isPublicCatalogView || number(current.pendingWarningCount) > 0
    }
    if (elements.vocabularyBatchConfirmBtn) {
      elements.vocabularyBatchConfirmBtn.classList.toggle('hidden', isPublicCatalogView)
      elements.vocabularyBatchConfirmBtn.disabled = isPublicCatalogView || number(current.pendingWarningCount) === 0
    }
    
    const items = asArray(current.items)
    if (isPublicCatalogView) {
      if (elements.vocabularyReviewThead) {
        elements.vocabularyReviewThead.innerHTML = `
          <tr>
            <th>序号</th>
            <th>单词</th>
            <th>音标</th>
            <th>释义</th>
          </tr>
        `
      }
      elements.vocabularyReviewRows.innerHTML = items.length
        ? items.map((item) => `
            <tr>
              <td>${number(item.sourceOrder)}</td>
              <td><strong>${escapeHtml(item.approvedTerm || item.effectiveTerm || item.originalTerm || '')}</strong></td>
              <td>${escapeHtml(item.phonetic || '')}</td>
              <td>${escapeHtml(item.definition || '')}</td>
            </tr>
          `).join('')
        : '<tr><td colspan="4" class="empty">没有符合条件的词条</td></tr>'
    } else {
      if (elements.vocabularyReviewThead) {
        elements.vocabularyReviewThead.innerHTML = `
          <tr>
            <th>序号</th>
            <th>原词</th>
            <th>建议 / 人工确认</th>
            <th>音标</th>
            <th>释义</th>
            <th>状态</th>
          </tr>
        `
      }
      elements.vocabularyReviewRows.innerHTML = items.length
        ? items.map((item) => `
            <tr class="${item.suspicious ? 'warning' : ''}">
              <td>${number(item.sourceOrder)}</td>
              <td><strong>${escapeHtml(item.originalTerm)}</strong></td>
              <td>
                <div class="vocabulary-correction-field">
                  <input value="${escapeHtml(item.approvedTerm || item.suggestedTerm || item.originalTerm || '')}" data-import-entry-input="${escapeHtml(item.id)}" />
                  ${item.suspicious ? `<button class="secondary-button compact" type="button" data-save-import-entry="${escapeHtml(item.id)}">确认</button>` : ''}
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
    }

    const page = number(current.page) || 1
    const pageSize = number(current.pageSize) || state.vocabularyImportPageSize
    const pages = Math.max(1, Math.ceil(number(current.filteredTotal) / pageSize))
    elements.vocabularyPageInfo.textContent = `第 ${page} / ${pages} 页 · ${number(current.filteredTotal)} 条`
    elements.vocabularyPrevPageBtn.disabled = page <= 1
    elements.vocabularyNextPageBtn.disabled = page >= pages
    if (canManageCatalogs && current.status === 'published') {
      renderVocabularyAnalysis(current)
    } else if (elements.vocabularyAnalysisAction) {
      elements.vocabularyAnalysisAction.classList.add('hidden')
    }
  }

  function renderVocabularyAnalysis(current = state.currentVocabularyImport) {
    if (!elements.vocabularyAnalysisAction) return
    const published = current?.status === 'published'
    elements.vocabularyAnalysisAction.classList.toggle('hidden', !published)
    if (!published) return
    const analysis = sameId(state.currentVocabularyAnalysis?.catalogVersionId, current.catalogVersionId)
      ? state.currentVocabularyAnalysis : null
    const status = analysis?.status || 'not_started'
    const analyzed = number(analysis?.analyzedCount)
    const total = number(analysis?.publishedCount) || number(current.totalCount)
    const pending = Math.max(0, number(analysis?.unanalyzedCount) || (total - analyzed))
    const groups = number(analysis?.groupCount)
    const isAllAnalyzed = total > 0 && analyzed >= total && pending === 0
    const effectiveStatus = isAllAnalyzed ? 'completed' : status
    elements.vocabularyAnalysisStatus.textContent = `词本关联分析：${ANALYSIS_STATUS_LABELS[effectiveStatus] || effectiveStatus} · ${analyzed}/${total} 词${groups ? ` · ${groups} 组` : ''}`
    const running = !isAllAnalyzed && (status === 'pending' || status === 'running')
    elements.triggerVocabularyAnalysisBtn.disabled = running || pending === 0 || analysis?.canTrigger === false || isAllAnalyzed
    elements.triggerVocabularyAnalysisBtn.textContent = running
      ? (status === 'running' ? '分析中...' : '等待执行')
      : (pending > 0 ? `分析剩余 ${pending} 词` : '分析已完成')
  }

  async function loadVocabularyAnalysis(catalogVersionId, options = {}) {
    if (!catalogVersionId) return null
    if (state.preview) {
      renderVocabularyAnalysis()
      return state.currentVocabularyAnalysis
    }
    try {
      const analysis = await request(`/api/v1/vocabulary-catalogs/${encodeURIComponent(catalogVersionId)}/analysis`)
      state.currentVocabularyAnalysis = analysis
      renderVocabularyAnalysis()
      return analysis
    } catch (error) {
      if (!options.quiet) toast(`词本关联分析状态加载失败：${error.message}`)
      logEvent('error', '词本关联分析状态加载失败', error.message)
      return null
    }
  }

  async function triggerVocabularyAnalysis() {
    const current = state.currentVocabularyImport
    if (!current || current.status !== 'published') return
    const pending = number(state.currentVocabularyAnalysis?.unanalyzedCount) || number(current.totalCount)
    if (pending <= 0) return
    const confirmed = await confirmAction({
      title: '分析公共词本',
      message: `将对「${current.catalogName}」中尚未分析的 ${pending} 个词进行批量语义分析。分析在后台执行，可在任务中心查看进度。`,
      acceptText: '开始分析',
    })
    if (!confirmed) return
    setButtonLoading(elements.triggerVocabularyAnalysisBtn, true, '提交中...')
    try {
      if (state.preview) {
        state.currentVocabularyAnalysis = { ...state.currentVocabularyAnalysis, status: 'pending', canTrigger: false }
      } else {
        state.currentVocabularyAnalysis = await request(`/api/v1/vocabulary-catalogs/${encodeURIComponent(current.catalogVersionId)}/analysis`, {
          method: 'POST',
          body: JSON.stringify({ executionMode: 'immediate', batchSize: 25 }),
        })
      }
      renderVocabularyAnalysis()
      logEvent('vocabulary', '触发公共词本关联分析', `${current.catalogName} · ${pending} 词`)
      toast('词本关联分析任务已创建，可在个人信息 - 任务中心查看进度')
    } catch (error) {
      logEvent('error', '公共词本关联分析触发失败', error.message)
      toast(`词本关联分析触发失败：${error.message}`)
    } finally {
      setButtonLoading(elements.triggerVocabularyAnalysisBtn, false)
      renderVocabularyAnalysis()
    }
  }

  async function saveImportEntry(entryId) {
    const current = state.currentVocabularyImport
    const input = elements.vocabularyReviewRows.querySelector(`[data-import-entry-input="${CSS.escape(String(entryId))}"]`)
    const approvedTerm = input?.value.trim()
    if (!current || !approvedTerm) return
    try {
      if (state.preview) {
        const item = asArray(current._allItems).find((entry) => sameId(entry.id, entryId))
        if (item) {
          item.approvedTerm = approvedTerm
          item.effectiveTerm = approvedTerm
          item.reviewStatus = 'confirmed'
        }
      } else {
        await request(`/api/v1/vocabulary-imports/${encodeURIComponent(current.jobId)}/entries/${encodeURIComponent(entryId)}`, {
          method: 'PUT',
          body: JSON.stringify({ approvedTerm }),
        })
      }
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
      if (state.preview) {
        asArray(current._allItems).filter((item) => item.suspicious && item.reviewStatus !== 'confirmed').forEach((item) => {
          item.approvedTerm = item.suggestedTerm || item.originalTerm
          item.effectiveTerm = item.approvedTerm
          item.reviewStatus = 'confirmed'
        })
      } else {
        await request(`/api/v1/vocabulary-imports/${encodeURIComponent(current.jobId)}/warnings/confirm`, {
          method: 'POST',
          body: JSON.stringify({ applySuggested: true }),
        })
      }
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
    if (!current || current.status === 'published') return
    if (number(current.pendingWarningCount) > 0) {
      toast('请先确认所有疑似断词')
      return
    }
    const confirmed = await confirmAction({
      title: '发布公共词本',
      message: `确认将「${current.catalogName}」发布为公共词本？发布后可用于新建学习计划，导入阶段不会批量生成 AI 词卡。`,
      acceptText: '确认发布',
    })
    if (!confirmed) return
    setButtonLoading(elements.publishVocabularyImportBtn, true, '发布中...')
    try {
      if (state.preview) {
        current.status = 'published'
        const catalog = {
          catalogId: current.catalogId,
          catalogVersionId: current.catalogVersionId,
          catalogName: current.catalogName,
          sourceType: current.sourceType,
          learningPurpose: current.learningPurpose,
          status: 'published',
          totalCount: current.totalCount,
          publishedTime: new Date().toISOString(),
        }
        state.publicVocabularyCatalogs = [catalog, ...state.publicVocabularyCatalogs.filter((item) => !sameId(item.catalogVersionId, catalog.catalogVersionId))]
        state.currentVocabularyImport = current
        state.currentVocabularyAnalysis = {
          catalogId: current.catalogId,
          catalogVersionId: current.catalogVersionId,
          status: 'not_started',
          publishedCount: number(current.totalCount),
          analyzedCount: 0,
          unanalyzedCount: number(current.totalCount),
          canTrigger: number(current.totalCount) > 0,
        }
      } else {
        state.currentVocabularyImport = await request(`/api/v1/vocabulary-imports/${encodeURIComponent(current.jobId)}/publish`, {
          method: 'POST',
          body: JSON.stringify({}),
        })
        await loadVocabularyAnalysis(state.currentVocabularyImport.catalogVersionId, { quiet: true })
      }
      await Promise.allSettled([reloadImportHistory(), loadWordbooks?.()])
      renderImportReview()
      renderSourceOptions()
      logEvent('vocabulary', '发布公共词本', current.catalogName)
      toast('公共词本已发布，可在学习计划中选择')
    } catch (error) {
      logEvent('error', '词表发布失败', error.message)
      toast(`词表发布失败：${error.message}`)
    } finally {
      setButtonLoading(elements.publishVocabularyImportBtn, false)
    }
  }

  let startPickerInited = false
  let endPickerInited = false

  function openScenePlanModal(planId = null) {
    const targetPlanId = (typeof planId === 'string' || typeof planId === 'number') && String(planId).trim() ? planId : null
    renderSourceOptions()
    
    const startPickerEl = document.getElementById('scenePlanStartTimePicker')
    const endPickerEl = document.getElementById('scenePlanEndTimePicker')

    if (startPickerEl && !startPickerInited) {
      initDatetimePicker(startPickerEl)
      startPickerInited = true
    }
    if (endPickerEl && !endPickerInited) {
      initDatetimePicker(endPickerEl)
      endPickerInited = true
    }

    state.currentPlanEditId = targetPlanId

    if (targetPlanId) {
      const plan = asArray(state.learningPlans).find((item) => sameId(item.id, targetPlanId))
      if (!plan) return

      if (elements.scenePlanModalTitle) {
        elements.scenePlanModalTitle.textContent = '编辑学习计划'
      }
      elements.createScenePlanBtn.textContent = '保存修改'

      if (elements.sceneCatalogSelect) {
        elements.sceneCatalogSelect.value = String(plan.catalogVersionId || '')
        elements.sceneCatalogSelect.disabled = true
      }

      elements.scenePlanNameInput.value = plan.name || ''
      elements.scenePlanPurposeInput.value = plan.learningPurpose || ''
      elements.scenePlanModelSelect.value = plan.modelConfigId || ''
      if (elements.scenePlanWordbookSelect) {
        elements.scenePlanWordbookSelect.value = String(plan.wordbookId || '')
      }

      if (elements.scenePlanStartTimeInput) {
        elements.scenePlanStartTimeInput.value = plan.startTime || ''
        const labelEl = startPickerEl?.querySelector('.datetime-picker-label')
        if (labelEl) {
          const val = plan.startTime
          labelEl.textContent = val ? formatPlanDate(val) : (startPickerEl.dataset.placeholder || '选择开始时间')
          labelEl.classList.toggle('placeholder', !val)
        }
      }
      if (elements.scenePlanEndTimeInput) {
        elements.scenePlanEndTimeInput.value = plan.endTime || ''
        const labelEl = endPickerEl?.querySelector('.datetime-picker-label')
        if (labelEl) {
          const val = plan.endTime
          labelEl.textContent = val ? formatPlanDate(val) : (endPickerEl.dataset.placeholder || '选择结束时间')
          labelEl.classList.toggle('placeholder', !val)
        }
      }

      if (elements.scenePlanStatusField) {
        elements.scenePlanStatusField.classList.remove('hidden')
      }
      if (elements.scenePlanStatusSelect) {
        elements.scenePlanStatusSelect.value = plan.status || 'active'
      }

    } else {
      if (elements.scenePlanModalTitle) {
        elements.scenePlanModalTitle.textContent = '新建学习计划'
      }
      elements.createScenePlanBtn.textContent = '创建计划'

      if (elements.sceneCatalogSelect) {
        elements.sceneCatalogSelect.disabled = false
      }

      const selected = state.publicVocabularyCatalogs.find((item) => String(item.catalogVersionId) === elements.sceneCatalogSelect?.value)
      elements.scenePlanNameInput.value = selected ? `${selected.catalogName}学习计划` : ''
      elements.scenePlanPurposeInput.value = selected?.learningPurpose || ''
      elements.scenePlanModelSelect.value = ''
      if (elements.scenePlanWordbookSelect) {
        elements.scenePlanWordbookSelect.value = String(state.currentWordbookId || '')
      }

      if (elements.scenePlanStartTimeInput) {
        elements.scenePlanStartTimeInput.value = ''
        const labelEl = startPickerEl?.querySelector('.datetime-picker-label')
        if (labelEl) {
          labelEl.textContent = startPickerEl.dataset.placeholder || '选择开始时间'
          labelEl.classList.add('placeholder')
        }
      }
      if (elements.scenePlanEndTimeInput) {
        elements.scenePlanEndTimeInput.value = ''
        const labelEl = endPickerEl?.querySelector('.datetime-picker-label')
        if (labelEl) {
          labelEl.textContent = endPickerEl.dataset.placeholder || '选择结束时间'
          labelEl.classList.add('placeholder')
        }
      }

      if (elements.scenePlanStatusField) {
        elements.scenePlanStatusField.classList.add('hidden')
      }
    }

    showModal(elements.scenePlanModal)
  }

  function closeScenePlanModal() {
    hideModal(elements.scenePlanModal)
  }

  function changePlanCatalog() {
    const selected = state.publicVocabularyCatalogs.find((item) => sameId(item.catalogVersionId, elements.sceneCatalogSelect.value))
    if (!selected) return
    elements.scenePlanNameInput.value = `${selected.catalogName}学习计划`
    elements.scenePlanPurposeInput.value = selected.learningPurpose || ''
  }

  async function createScenePlan() {
    const catalogVersionId = elements.sceneCatalogSelect.value
    const name = elements.scenePlanNameInput.value.trim()
    const learningPurpose = elements.scenePlanPurposeInput.value.trim()
    if (!name || !learningPurpose) {
      toast('请填写计划名称和学习目标')
      return
    }
    if (!state.currentPlanEditId && !catalogVersionId) {
      toast('请选择公共词本')
      return
    }
    const startTime = elements.scenePlanStartTimeInput?.value || null
    const endTime = elements.scenePlanEndTimeInput?.value || null
    const wordbookId = elements.scenePlanWordbookSelect?.value || null

    if (state.currentPlanEditId) {
      setButtonLoading(elements.createScenePlanBtn, true, '保存中...')
      try {
        const modelConfigId = elements.scenePlanModelSelect.value
        const status = elements.scenePlanStatusSelect?.value || null
        let plan
        if (state.preview) {
          plan = state.learningPlans.find((item) => sameId(item.id, state.currentPlanEditId))
          if (plan) {
            plan.name = name
            plan.learningPurpose = learningPurpose
            plan.startTime = startTime
            plan.endTime = endTime
            plan.wordbookId = wordbookId || plan.wordbookId
            plan.modelConfigId = modelConfigId || null
            if (status) plan.status = status
          }
        } else {
          plan = await request(`/api/v1/learning/plans/${encodeURIComponent(state.currentPlanEditId)}`, {
            method: 'PUT',
            body: JSON.stringify({
              name,
              learningPurpose,
              wordbookId: wordbookId || null,
              modelConfigId: modelConfigId || null,
              startTime,
              endTime,
              status,
            }),
          })
        }
        if (plan) {
          if (sameId(state.currentLearningPlan?.id, plan.id)) {
            state.currentLearningPlan = plan
          }
          await loadSceneData({ planId: plan.id })
        }
        closeScenePlanModal()
        toast('学习计划已更新')
      } catch (error) {
        logEvent('error', '更新学习计划失败', error.message)
        toast(`更新计划失败：${error.message}`)
      } finally {
        setButtonLoading(elements.createScenePlanBtn, false)
      }
      return
    }

    const isFuture = startTime && new Date(startTime) > new Date()
    const btnLoadingText = isFuture ? '创建中...' : '生成首个场景中...'

    setButtonLoading(elements.createScenePlanBtn, true, btnLoadingText)
    try {
      const modelConfigId = elements.scenePlanModelSelect.value
      let plan
      if (state.preview) {
        const catalog = state.publicVocabularyCatalogs.find((item) => sameId(item.catalogVersionId, catalogVersionId))
        plan = createPreviewPlan({
          id: `preview-${Date.now()}`,
          catalog,
          name,
          learningPurpose,
          wordbookId: wordbookId || null,
          startTime,
          endTime,
          status: isFuture ? 'not_started' : 'active',
          learnedCoreWords: 0,
          completedUnitCount: 0,
        })
        state.learningPlans.unshift(plan)
      } else {
        plan = await request('/api/v1/learning/plans', {
          method: 'POST',
          body: JSON.stringify({
            catalogVersionId: catalogVersionId,
            wordbookId: wordbookId || null,
            name,
            learningPurpose,
            modelConfigId: modelConfigId || null,
            generateFirstUnit: !isFuture,
            startTime,
            endTime,
          }),
        })
      }
      state.currentLearningPlan = plan
      await loadSceneData({ planId: plan.id })
      closeScenePlanModal()
      logEvent('learning', '创建学习计划', name)
      toast(isFuture ? '学习计划已成功创建（未开始）' : '学习计划和首个场景已生成')
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
    renderCurrentScene()
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

  async function deleteImportJob(jobId) {
    const job = asArray(state.vocabularyImports).find((item) => sameId(item.jobId, jobId))
    if (!job) return
    const confirmed = await confirmAction({
      title: '删除导入记录',
      message: `确认删除公共词表导入记录「${job.catalogName}」？删除后对应的公共词本及词条关系将被清除。`,
    })
    if (!confirmed) return
    try {
      await request(`/api/v1/vocabulary-imports/${encodeURIComponent(jobId)}`, { method: 'DELETE' })
      toast('导入记录已删除')
      if (sameId(state.currentVocabularyImport?.jobId, jobId)) {
        state.currentVocabularyImport = null
        elements.vocabularyReviewSection.classList.add('hidden')
        closeVocabularyImport()
      }
      await reloadImportHistory()
    } catch (error) {
      logEvent('error', '删除导入记录失败', error.message)
      toast(`删除导入记录失败：${error.message}`)
    }
  }

  async function saveVocabularyImportMetadata() {
    const current = state.currentVocabularyImport
    if (!current || !current.jobId) return
    const catalogName = elements.vocabularyImportName.value.trim()
    const sourceType = elements.vocabularyImportSourceType.value
    const learningPurpose = elements.vocabularyImportPurpose.value.trim()
    if (!catalogName) {
      toast('请输入词表名称')
      return
    }
    setButtonLoading(elements.saveVocabularyImportMetadataBtn, true, '保存中...')
    try {
      if (state.preview) {
        current.catalogName = catalogName
        current.sourceType = sourceType
        current.learningPurpose = learningPurpose
        toast('设计预览：元数据已保存')
      } else {
        const result = await request(`/api/v1/vocabulary-imports/${encodeURIComponent(current.jobId)}`, {
          method: 'PUT',
          body: JSON.stringify({
            catalogName,
            sourceType,
            learningPurpose,
          }),
        })
        state.currentVocabularyImport = result
      }
      toast('词表信息已更新')
      await reloadImportHistory()
      renderImportReview()
    } catch (error) {
      logEvent('error', '保存词表信息失败', error.message)
      toast(`保存词表信息失败：${error.message}`)
    } finally {
      setButtonLoading(elements.saveVocabularyImportMetadataBtn, false)
    }
  }

  async function pausePlan() {
    const plan = state.currentLearningPlan
    if (!plan) return
    try {
      if (state.preview) {
        plan.status = 'paused'
        toast('设计预览：计划已暂停')
      } else {
        const result = await request(`/api/v1/learning/plans/${encodeURIComponent(plan.id)}/pause`, { method: 'POST' })
        state.currentLearningPlan = result
      }
      await loadSceneData({ planId: plan.id })
      toast('学习计划已暂停')
    } catch (error) {
      logEvent('error', '暂停学习计划失败', error.message)
      toast(`暂停失败：${error.message}`)
    }
  }

  async function resumePlan() {
    const plan = state.currentLearningPlan
    if (!plan) return
    try {
      if (state.preview) {
        plan.status = 'active'
        toast('设计预览：计划已启动/恢复')
      } else {
        const result = await request(`/api/v1/learning/plans/${encodeURIComponent(plan.id)}/resume`, { method: 'POST' })
        state.currentLearningPlan = result
      }
      await loadSceneData({ planId: plan.id })
      toast('学习计划已启动/恢复')
    } catch (error) {
      logEvent('error', '恢复学习计划失败', error.message)
      toast(`恢复失败：${error.message}`)
    }
  }

  async function cancelPlan() {
    const plan = state.currentLearningPlan
    if (!plan) return
    const confirmed = await confirmAction({
      title: '取消学习计划',
      message: `确认取消学习计划「${plan.name}」？取消后将不能继续学习或恢复。`,
    })
    if (!confirmed) return
    try {
      if (state.preview) {
        plan.status = 'cancelled'
        toast('设计预览：计划已取消')
      } else {
        const result = await request(`/api/v1/learning/plans/${encodeURIComponent(plan.id)}/cancel`, { method: 'POST' })
        state.currentLearningPlan = result
      }
      await loadSceneData({ planId: plan.id })
      toast('学习计划已取消')
    } catch (error) {
      logEvent('error', '取消学习计划失败', error.message)
      toast(`取消失败：${error.message}`)
    }
  }

  return {
    loadSceneData,
    clearSceneData,
    renderSceneView,
    renderRelatedWords: () => renderRelatedWords(activeUnit()),
    openVocabularyImport,
    closeVocabularyImport,
    startVocabularyImport,
    deleteImportJob,
    saveVocabularyImportMetadata,
    loadImportReview,
    openImportReview,
    confirmAllWarnings,
    publishVocabularyImport,
    triggerVocabularyAnalysis,
    openScenePlanModal,
    closeScenePlanModal,
    closeSceneVocabularyPreview,
    createScenePlan,
    changePlanCatalog,
    changeSceneWordbook,
    changeImportSearch,
    previousImportPage,
    nextImportPage,
    completeCurrentUnit,
    generateNextUnit,
    scheduleNextUnit,
    generateCards,
    scheduleCards,
    startLearning,
    showChallengeWords,
    startChallenge,
    backToReading,
    backToPlanOverview,
    changeCalendarRange,
    changeCalendarOffset,
    resetCalendar,
    changeSelectedPlan,
    pausePlan,
    resumePlan,
    cancelPlan,
    speakCurrentScene: () => speakSentence(activeUnit()?.learningText || ''),
    loadSceneNote,
    renderSceneNote,
    saveSceneNote,
    toggleSceneNotePreview,
    openSceneNoteModal,
    closeSceneNoteModal,
    openCoreWordsModal,
    closeCoreWordsModal,
    openRelatedWordsModal,
    closeRelatedWordsModal,
  }
}
