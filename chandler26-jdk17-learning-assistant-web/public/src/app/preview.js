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
  state.user = state.user || { id: 1, username: 'chandler', nickname: 'Chandler' }
  state.wordbooks = [
    { id: 1, name: '默认词书', description: '日常学习沉淀', isDefault: true, entryCount: 18, dueCount: 3 },
    { id: 2, name: 'CET-4 高频词', description: '考试核心词', isDefault: false, entryCount: 64, dueCount: 9 },
  ]
  state.modelConfigs = [
    {
      id: 101,
      name: 'DeepSeek 默认',
      provider: 'deepseek',
      modelName: 'deepseek-chat',
      baseUrl: 'https://api.deepseek.com',
      chatPath: '/chat/completions',
      apiKeyMasked: 'sk-****2101',
      enabled: true,
      isDefault: true,
      sequence: 0,
    },
    {
      id: 102,
      name: 'Kimi 备用',
      provider: 'kimi',
      modelName: 'moonshot-v1-8k',
      baseUrl: 'https://api.moonshot.cn',
      chatPath: '/v1/chat/completions',
      apiKeyMasked: 'sk-****wdBD',
      enabled: false,
      isDefault: false,
      sequence: 10,
    },
  ]
  state.agentConfigs = [
    {
      id: 201,
      name: '英语词汇学习 Agent',
      code: 'english_vocabulary',
      type: 'chat',
      icon: 'EV',
      description: '生成英语词汇学习卡、追问解释和结构化 JSON。',
      systemPrompt: '你是英语词汇学习助手，只输出适合结构化解析的学习内容。',
      concisePrompt: '围绕当前词汇继续解释，保持简洁准确。',
      welcomeMessage: '输入一个英语单词，我会生成学习卡片。',
      modelProvider: 'deepseek',
      modelName: 'deepseek-chat',
      temperature: 0.2,
      maxTokens: 1800,
      presetCommands: '[{"label":"举例","prompt":"再给我 3 个真实语境例句"}]',
      enabled: true,
      sequence: 1,
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
  ]
  syncCurrentWordbookId(state, elements, state.currentWordbookId || '1')
  state.wordbookEntries = [
    { id: 11, term: 'abandon', normalizedTerm: 'abandon', status: 'vague', note: '## 记忆\n- abandon a plan\n- with abandon', reviewStage: 2, masteryScore: 45, createTime: daysAgoIso(2), nextReviewTime: new Date().toISOString(), parsed: previewParsed('abandon'), tags: previewRecord('abandon').tags, relations: previewRecord('abandon').relations },
    { id: 12, term: 'maintain', normalizedTerm: 'maintain', status: 'familiar', note: '常和 **relationship/status** 搭配。', reviewStage: 4, masteryScore: 72, createTime: daysAgoIso(7), nextReviewTime: new Date(Date.now() + 86400000).toISOString(), parsed: previewParsed('maintain'), tags: previewRecord('maintain').tags, relations: previewRecord('maintain').relations },
    { id: 13, term: 'contrast', normalizedTerm: 'contrast', status: 'forgotten', note: '', reviewStage: 1, masteryScore: 30, createTime: daysAgoIso(12), nextReviewTime: new Date().toISOString(), parsed: previewParsed('contrast'), tags: previewRecord('contrast').tags, relations: previewRecord('contrast').relations },
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
