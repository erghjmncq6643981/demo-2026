import { syncCurrentWordbookId } from '/src/shared/wordbook.js'
import { createPreviewActivity, daysAgoIso, previewParsed, previewRecord } from '/src/shared/vocabulary.js'

export function loadPreviewData(ctx) {
  const {
    state,
    elements,
    updateAuthView,
    renderModelConfigs,
    renderAgentConfigs,
    renderLearningAgentOptions,
    renderLearningConfigSummary,
    renderTemplateOptions,
    renderTemplateConfigs,
    renderWordbooks,
    renderWordbookEntries,
    renderReviewQueue,
    renderRecord,
    renderActivityHeatmap,
    logEvent,
  } = ctx

  state.token = state.token || 'preview-token'
  state.user = state.user || {
    id: 1,
    username: 'chandler',
    nickname: 'Chandler',
    phoneMasked: '187****6252',
    emailMasked: 'er****@163.com',
  }
  state.wordbooks = [
    { id: 1, name: '默认单词本', description: '日常学习沉淀', isDefault: true, entryCount: 18, dueCount: 3 },
    { id: 2, name: 'CET-4 高频词', description: '考试核心词', isDefault: false, entryCount: 64, dueCount: 9 },
  ]
  state.modelConfigs = [
    {
      id: 101,
      name: 'DeepSeek V4 Flash',
      provider: 'deepseek',
      providerName: 'DeepSeek',
      modelName: 'deepseek-v4-flash',
      modelDisplayName: 'DeepSeek V4 Flash',
      apiProtocol: 'openai_chat_completions',
      contextWindowTokens: 1048576,
      supported: true,
      baseUrl: 'https://api.deepseek.com',
      chatPath: '/chat/completions',
      apiKeyMasked: 'sk-****2101',
      enabled: true,
      isDefault: true,
      sequence: 0,
      boundAgentCount: 1,
      boundAgentNames: ['英语词汇学习 Agent'],
    },
    {
      id: 103,
      name: 'DeepSeek V4 Pro',
      provider: 'deepseek',
      providerName: 'DeepSeek',
      modelName: 'deepseek-v4-pro',
      modelDisplayName: 'DeepSeek V4 Pro',
      apiProtocol: 'openai_chat_completions',
      contextWindowTokens: 1048576,
      supported: true,
      baseUrl: 'https://api.deepseek.com',
      chatPath: '/chat/completions',
      apiKeyMasked: 'sk-****2102',
      enabled: true,
      isDefault: false,
      sequence: 1,
      boundAgentCount: 1,
      boundAgentNames: ['英语语境精读助手'],
    },
    {
      id: 102,
      name: 'Moonshot V1 历史配置',
      provider: 'moonshot',
      modelName: 'moonshot-v1-8k',
      supported: false,
      baseUrl: 'https://api.moonshot.cn/v1',
      chatPath: '/chat/completions',
      apiKeyMasked: 'sk-****wdBD',
      enabled: false,
      isDefault: false,
      sequence: 10,
      boundAgentCount: 0,
      boundAgentNames: [],
    },
  ]
  state.agentConfigs = [
    {
      id: 201,
      name: '英语词汇学习 Agent',
      code: 'english_vocabulary',
      type: 'chat',
      icon: 'EV',
      description: '生成英语词汇学习卡和结构化 JSON。',
      systemPrompt: '你是英语词汇学习助手，只输出适合结构化解析的学习内容。',
      concisePrompt: '围绕当前词汇生成简洁、准确的学习卡片内容。',
      welcomeMessage: '输入一个英语单词，我会生成学习卡片。',
      modelConfigId: 101,
      modelConfigName: 'DeepSeek V4 Flash',
      modelConfigEnabled: true,
      modelProvider: 'deepseek',
      modelName: 'deepseek-v4-flash',
      temperature: 0.2,
      maxTokens: 1800,
      presetCommands: '[{"label":"举例","prompt":"再给我 3 个真实语境例句"}]',
      enabled: true,
      sequence: 1,
    },
    {
      id: 202,
      name: '英语语境精读助手',
      code: 'english_article',
      type: 'chat',
      icon: 'EA',
      description: '基于单词本词汇生成英语学习文章、语法点和练习题。',
      systemPrompt: '你是英语语境精读设计师，只输出合法 JSON。',
      concisePrompt: '围绕当前文章继续解释，保持结构化。',
      welcomeMessage: '选择一组单词，我会生成学习文章。',
      modelConfigId: 103,
      modelConfigName: 'DeepSeek V4 Pro',
      modelConfigEnabled: true,
      modelProvider: 'deepseek',
      modelName: 'deepseek-v4-pro',
      temperature: 0.65,
      maxTokens: 6000,
      presetCommands: '[{"label":"语法","prompt":"讲解文章语法点"}]',
      enabled: true,
      sequence: 2,
    },
  ]
  state.promptTemplates = [
    {
      id: 1001,
      name: '英语词汇卡片 JSON',
      code: 'english_vocab_card_json',
      type: 'user',
      tags: '英语,词汇,JSON',
      content: '请为英语词汇「{{term}}」生成学习卡片。只输出合法 JSON，不要输出 Markdown。JSON 字段包括：term、is_valid、language、phonetic.uk、phonetic.us、definitions、examples、collocations、synonyms、antonyms、word_family、memory_tips。definitions 生成 1 到 4 条，每条包含 part_of_speech、meaning、english。examples 生成 3 条对象数组，每条必须包含 sentence 和 translation，其中 sentence 是英文例句，translation 是对应中文翻译。collocations 生成 3 到 6 条对象数组，每条包含 phrase 和 meaning。synonyms、antonyms、word_family 生成对象数组，每条包含 word、part_of_speech、meaning、phonetic.uk、phonetic.us，其中 phonetic 是该相关词的英音/美音音标。中文解释要简洁准确。如果输入拼写疑似错误，请在 term 中输出你判断的最匹配标准单词，并保持 is_valid=true。',
      variables: JSON.stringify([{ name: 'term', label: '英语单词或短语', required: true }]),
      description: '生成可解析入库的英语词汇学习卡片',
      exampleInput: '{"term":"abandon"}',
      exampleOutput: '{"term":"abandon","is_valid":true}',
      publicTemplate: true,
      sequence: 1,
    },
    {
      id: 1002,
      name: '英语词汇练习题 JSON',
      code: 'english_vocab_quiz_json',
      type: 'user',
      tags: '英语,词汇,练习题,JSON',
      content: '请基于英语词汇「{{term}}」生成 5 道词汇练习题。只输出合法 JSON。',
      variables: JSON.stringify([{ name: 'term', label: '英语单词或短语', required: true }]),
      description: '生成可解析入库的英语词汇练习题',
      exampleInput: '{"term":"abandon"}',
      exampleOutput: '{"term":"abandon","questions":[]}',
      publicTemplate: true,
      sequence: 2,
    },
    {
      id: 1101,
      name: '英语语境精读 JSON',
      code: 'english_vocab_article_json',
      type: 'user',
      tags: '英语,文章,词汇,语法,JSON',
      content: '请基于以下目标词生成语境精读材料。目标词 JSON：{{words}}。文章字数范围：{{word_count_range}}。难度：{{difficulty}}。阅读主题：{{remark}}。只输出合法 JSON。',
      variables: JSON.stringify([
        { name: 'words', label: '所选词汇 JSON', required: true },
        { name: 'word_count_range', label: '字数范围', required: true },
        { name: 'difficulty', label: '难度', required: true },
        { name: 'remark', label: '备注', required: true },
      ]),
      description: '生成可通读、精讲和检测的语境精读材料',
      exampleInput: '{"words":[{"term":"abandon"}]}',
      exampleOutput: '{"title":"A Difficult Decision","article":"..."}',
      publicTemplate: true,
      sequence: 3,
    },
  ]
  syncCurrentWordbookId(state, elements, state.currentWordbookId || '1')
  state.wordbookEntries = [
    { id: 10, term: 'vigor', normalizedTerm: 'vigor', status: 'familiar', cardStatus: 'ready', note: '有道词典精细对齐词条：母语认知画像 + 高频搭配 + 构词积木。', reviewStage: 3, masteryScore: 60, createTime: daysAgoIso(1), nextReviewTime: new Date().toISOString(), parsed: previewParsed('vigor'), tags: previewRecord('vigor').tags, relations: previewRecord('vigor').relations },
    { id: 11, term: 'abandon', normalizedTerm: 'abandon', status: 'vague', cardStatus: 'ready', note: '## 记忆\n- abandon a plan\n- with abandon', reviewStage: 2, masteryScore: 45, createTime: daysAgoIso(2), nextReviewTime: new Date().toISOString(), parsed: previewParsed('abandon'), tags: previewRecord('abandon').tags, relations: previewRecord('abandon').relations },
    { id: 12, term: 'maintain', normalizedTerm: 'maintain', status: 'familiar', cardStatus: 'ready', note: '常和 **relationship/status** 搭配。', reviewStage: 4, masteryScore: 72, createTime: daysAgoIso(7), nextReviewTime: new Date(Date.now() + 86400000).toISOString(), parsed: previewParsed('maintain'), tags: previewRecord('maintain').tags, relations: previewRecord('maintain').relations },
    { id: 13, term: 'contrast', normalizedTerm: 'contrast', status: 'forgotten', cardStatus: 'missing', note: '', reviewStage: 1, masteryScore: 30, createTime: daysAgoIso(12), nextReviewTime: new Date().toISOString(), parsed: null, tags: [], relations: [] },
  ]
  state.reviewEntries = state.wordbookEntries.slice(0, 2).map((entry) => ({
    ...entry,
    parsed: previewParsed(entry.term),
  }))
  state.previewReviewEntries = state.reviewEntries.slice()
  state.activity = createPreviewActivity()
  updateAuthView()
  renderModelConfigs()
  renderAgentConfigs()
  renderLearningAgentOptions()
  renderTemplateOptions()
  renderTemplateConfigs()
  renderLearningConfigSummary?.()
  renderWordbooks()
  renderWordbookEntries()
  renderReviewQueue(state.reviewEntries)
  renderRecord(previewRecord())
  renderActivityHeatmap()
  logEvent('system', '设计预览模式', '使用 ?preview=1 查看无后端登录后的产品界面')

}
