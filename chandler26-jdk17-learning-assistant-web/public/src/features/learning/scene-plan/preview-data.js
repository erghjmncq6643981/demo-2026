import { localDateKey, number } from '/src/features/learning/scene-plan/model.js'

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

export function createPreviewCookingUnit(planId, unitNo, recommendedDate = localDateKey()) {
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

export function createPreviewPlan(options = {}) {
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

export function previewCatalog() {
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
