const state = {
  build: '20260519-20',
  apiBase: localStorage.getItem('learning.apiBase') || 'http://localhost:16681',
  token: localStorage.getItem('learning.token') || '',
  user: readJsonStorage('learning.user'),
  preview: new URLSearchParams(window.location.search).get('preview') === '1',
  activeView: localStorage.getItem('learning.activeView') || 'profileView',
  sidebarCollapsed: initialSidebarCollapsed(),
  wordbooks: [],
  wordbookEntries: [],
  reviewEntries: [],
  previewReviewEntries: [],
  modelConfigs: [],
  currentWordbookId: localStorage.getItem('learning.wordbookId') || null,
  currentWordbookEditId: null,
  currentModelEditId: null,
  currentStatusEntryId: null,
  selectedEntry: null,
  currentNoteEntry: null,
  currentRecord: null,
  currentSessionId: null,
  activeProfileTab: localStorage.getItem('learning.profileTab') || 'accountPanel',
  currentReviewEntry: null,
  currentReviewIndex: 0,
  reviewTyped: '',
  reviewWrongCount: 0,
  pendingReviewEntryId: null,
  pendingDeleteConfirm: null,
  promptTemplates: [],
  currentTemplate: null,
  activity: null,
  systemLogs: readJsonStorage('learning.systemLogs') || [],
}

const providerCatalog = {
  deepseek: {
    label: 'DeepSeek',
    baseUrl: 'https://api.deepseek.com',
    chatPath: '/chat/completions',
    models: ['deepseek-chat', 'deepseek-reasoner'],
  },
  kimi: {
    label: 'Kimi',
    baseUrl: 'https://api.moonshot.cn',
    chatPath: '/v1/chat/completions',
    models: ['moonshot-v1-8k', 'moonshot-v1-32k', 'moonshot-v1-128k'],
  },
  doubao: {
    label: '豆包',
    baseUrl: 'https://ark.cn-beijing.volces.com',
    chatPath: '/api/v3/chat/completions',
    models: ['doubao-pro-32k', 'doubao-lite-32k'],
  },
  yuanbao: {
    label: '元宝',
    baseUrl: '',
    chatPath: '/chat/completions',
    models: ['hunyuan-turbos-latest', 'hunyuan-lite'],
  },
}

const $ = (id) => document.getElementById(id)

function initialSidebarCollapsed() {
  const saved = localStorage.getItem('learning.sidebarCollapsed')
  if (saved !== null) return saved === '1'
  return window.matchMedia('(max-width: 1100px)').matches
}

const elements = {
  loginScreen: $('loginScreen'),
  productShell: $('productShell'),
  connectionStatus: $('connectionStatus'),
  apiBaseInput: $('apiBaseInput'),
  usernameInput: $('usernameInput'),
  passwordInput: $('passwordInput'),
  nicknameInput: $('nicknameInput'),
  toggleSidebarBtn: $('toggleSidebarBtn'),
  sidebarBackdrop: $('sidebarBackdrop'),
  loginBtn: $('loginBtn'),
  registerBtn: $('registerBtn'),
  logoutBtn: $('logoutBtn'),
  buildVersion: $('buildVersion'),
  userBadge: $('userBadge'),
  userAvatar: $('userAvatar'),
  profileAvatar: $('profileAvatar'),
  profileName: $('profileName'),
  profileUsername: $('profileUsername'),
  profileStatus: $('profileStatus'),
  wordbookCount: $('wordbookCount'),
  wordCount: $('wordCount'),
  dueCount: $('dueCount'),
  openAccountModalBtn: $('openAccountModalBtn'),
  accountModal: $('accountModal'),
  closeAccountModalBtn: $('closeAccountModalBtn'),
  accountNicknameInput: $('accountNicknameInput'),
  accountCurrentPasswordInput: $('accountCurrentPasswordInput'),
  accountNewPasswordInput: $('accountNewPasswordInput'),
  accountConfirmPasswordInput: $('accountConfirmPasswordInput'),
  saveAccountBtn: $('saveAccountBtn'),
  activityHeatmap: $('activityHeatmap'),
  activitySummary: $('activitySummary'),
  viewEyebrow: $('viewEyebrow'),
  viewTitle: $('viewTitle'),
  wordbookSelect: $('wordbookSelect'),
  reloadWordbookEntriesBtn: $('reloadWordbookEntriesBtn'),
  openWordbookModalBtn: $('openWordbookModalBtn'),
  wordbookModal: $('wordbookModal'),
  wordbookModalTitle: $('wordbookModalTitle'),
  closeWordbookModalBtn: $('closeWordbookModalBtn'),
  newWordbookInput: $('newWordbookInput'),
  wordbookDescriptionInput: $('wordbookDescriptionInput'),
  wordbookDefaultInput: $('wordbookDefaultInput'),
  createWordbookBtn: $('createWordbookBtn'),
  wordbookCards: $('wordbookCards'),
  wordbookEntryList: $('wordbookEntryList'),
  wordStatusFilter: $('wordStatusFilter'),
  reloadWordbookViewBtn: $('reloadWordbookViewBtn'),
  wordbookFocus: $('wordbookFocus'),
  entryStatusModal: $('entryStatusModal'),
  entryStatusTerm: $('entryStatusTerm'),
  closeEntryStatusModalBtn: $('closeEntryStatusModalBtn'),
  reloadModelsBtn: $('reloadModelsBtn'),
  openModelModalBtn: $('openModelModalBtn'),
  modelConfigModal: $('modelConfigModal'),
  closeModelModalBtn: $('closeModelModalBtn'),
  modelModalTitle: $('modelModalTitle'),
  modelNameInput: $('modelNameInput'),
  modelProviderInput: $('modelProviderInput'),
  modelModelNameInput: $('modelModelNameInput'),
  modelBaseUrlInput: $('modelBaseUrlInput'),
  modelChatPathInput: $('modelChatPathInput'),
  modelApiKeyInput: $('modelApiKeyInput'),
  modelSequenceInput: $('modelSequenceInput'),
  modelDefaultInput: $('modelDefaultInput'),
  modelEnabledInput: $('modelEnabledInput'),
  modelDefaultToggleBtn: $('modelDefaultToggleBtn'),
  modelEnabledToggleBtn: $('modelEnabledToggleBtn'),
  saveModelBtn: $('saveModelBtn'),
  resetModelFormBtn: $('resetModelFormBtn'),
  modelConfigList: $('modelConfigList'),
  systemLogList: $('systemLogList'),
  reloadSystemLogsBtn: $('reloadSystemLogsBtn'),
  clearLogBtn: $('clearLogBtn'),
  rawJson: $('rawJson'),
  sessionIdBadge: $('sessionIdBadge'),
  reloadAgentsBtn: $('reloadAgentsBtn'),
  studyForm: $('studyForm'),
  termInput: $('termInput'),
  studyBtn: $('studyBtn'),
  studyRegenerateBtn: $('studyRegenerateBtn'),
  studyModelSelect: $('studyModelSelect'),
  cacheState: $('cacheState'),
  wordTitle: $('wordTitle'),
  phoneticLine: $('phoneticLine'),
  addToWordbookBtn: $('addToWordbookBtn'),
  addWordbookModal: $('addWordbookModal'),
  closeAddWordbookModalBtn: $('closeAddWordbookModalBtn'),
  addWordbookTerm: $('addWordbookTerm'),
  addWordbookList: $('addWordbookList'),
  speakWordBtn: $('speakWordBtn'),
  speakSentenceBtn: $('speakSentenceBtn'),
  meaningList: $('meaningList'),
  tagList: $('tagList'),
  relationList: $('relationList'),
  examples: $('examples'),
  collocations: $('collocations'),
  memoryTips: $('memoryTips'),
  editStudyNoteBtn: $('editStudyNoteBtn'),
  studyNote: $('studyNote'),
  agentSelect: $('agentSelect'),
  templateSelect: $('templateSelect'),
  templateSummary: $('templateSummary'),
  templateNameInput: $('templateNameInput'),
  templateCodeInput: $('templateCodeInput'),
  templateTypeInput: $('templateTypeInput'),
  templateSequenceInput: $('templateSequenceInput'),
  templateTagsInput: $('templateTagsInput'),
  templateDescriptionInput: $('templateDescriptionInput'),
  templateExampleInput: $('templateExampleInput'),
  templateExampleOutput: $('templateExampleOutput'),
  templatePlaceholderList: $('templatePlaceholderList'),
  templateContentInput: $('templateContentInput'),
  templateValidationMessage: $('templateValidationMessage'),
  saveTemplateBtn: $('saveTemplateBtn'),
  voiceSelect: $('voiceSelect'),
  chatInput: $('chatInput'),
  chatBtn: $('chatBtn'),
  reloadReviewBtn: $('reloadReviewBtn'),
  reviewWordbookSelect: $('reviewWordbookSelect'),
  reviewLimitInput: $('reviewLimitInput'),
  reviewProgressBadge: $('reviewProgressBadge'),
  reviewFocus: $('reviewFocus'),
  editReviewNoteBtn: $('editReviewNoteBtn'),
  reviewNote: $('reviewNote'),
  celebrationLayer: $('celebrationLayer'),
  reviewCompleteModal: $('reviewCompleteModal'),
  modalWordTitle: $('modalWordTitle'),
  modalExamples: $('modalExamples'),
  closeReviewModalBtn: $('closeReviewModalBtn'),
  forgottenDetailModal: $('forgottenDetailModal'),
  forgottenDetailTitle: $('forgottenDetailTitle'),
  forgottenDetailContent: $('forgottenDetailContent'),
  closeForgottenDetailModalBtn: $('closeForgottenDetailModalBtn'),
  forgottenBackToReviewBtn: $('forgottenBackToReviewBtn'),
  deleteConfirmModal: $('deleteConfirmModal'),
  deleteConfirmTitle: $('deleteConfirmTitle'),
  deleteConfirmMessage: $('deleteConfirmMessage'),
  deleteConfirmCloseBtn: $('deleteConfirmCloseBtn'),
  deleteConfirmCancelBtn: $('deleteConfirmCancelBtn'),
  deleteConfirmAcceptBtn: $('deleteConfirmAcceptBtn'),
  toast: $('toast'),
}

const viewMeta = {
  profileView: ['Profile', '个人信息'],
  wordbookView: ['Wordbook', '单词本'],
  studyView: ['Study', '英语学习'],
  reviewView: ['Review', '复习计划'],
}

if (elements.apiBaseInput) elements.apiBaseInput.value = state.apiBase
if (elements.buildVersion) elements.buildVersion.textContent = `build ${state.build}`

function apiUrl(path) {
  return `${state.apiBase.replace(/\/$/, '')}${path}`
}

async function request(path, options = {}) {
  const headers = { 'content-type': 'application/json', ...(options.headers || {}) }
  if (state.token && !headers.Authorization) {
    headers.Authorization = `Bearer ${state.token}`
  }
  const response = await fetch(apiUrl(path), { headers, ...options })
  if (!response.ok) {
    const text = await response.text()
    throw new Error(readErrorMessage(text) || `HTTP ${response.status}`)
  }
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

function setLoading(isLoading) {
  for (const element of [
    elements.loginBtn,
    elements.registerBtn,
    elements.studyBtn,
    elements.studyRegenerateBtn,
    elements.chatBtn,
    elements.addToWordbookBtn,
    elements.createWordbookBtn,
    elements.saveModelBtn,
    elements.saveTemplateBtn,
    elements.saveAccountBtn,
  ]) {
    if (element) element.disabled = isLoading
  }
}

function toast(message) {
  elements.toast.textContent = message
  elements.toast.classList.add('show')
  window.clearTimeout(toast.timer)
  toast.timer = window.setTimeout(() => elements.toast.classList.remove('show'), 2600)
}

function confirmAction({ title = '确认操作', message = '请确认是否继续。', acceptText = '确认', danger = false } = {}) {
  return new Promise((resolve) => {
    if (!elements.deleteConfirmModal) {
      resolve(window.confirm(message))
      return
    }
    state.pendingDeleteConfirm = resolve
    elements.deleteConfirmTitle.textContent = title
    elements.deleteConfirmMessage.textContent = message
    elements.deleteConfirmAcceptBtn.textContent = acceptText
    elements.deleteConfirmAcceptBtn.classList.toggle('danger-button', danger)
    elements.deleteConfirmAcceptBtn.classList.toggle('primary-button', !danger)
    elements.deleteConfirmModal.classList.remove('hidden')
    elements.deleteConfirmAcceptBtn.focus()
  })
}

function confirmDelete(options = {}) {
  return confirmAction({ acceptText: '确认删除', danger: true, ...options })
}

function closeDeleteConfirm(confirmed = false) {
  elements.deleteConfirmModal?.classList.add('hidden')
  const resolve = state.pendingDeleteConfirm
  state.pendingDeleteConfirm = null
  if (resolve) resolve(confirmed)
}

function logEvent(type, title, detail = '') {
  const entry = {
    type,
    title,
    detail,
    time: new Date().toISOString(),
    source: 'client',
  }
  state.systemLogs = [entry, ...state.systemLogs].slice(0, 60)
  if (state.preview || !state.token) {
    localStorage.setItem('learning.systemLogs', JSON.stringify(state.systemLogs))
  }
  renderSystemLogs()
  persistSystemLog(entry)
}

async function persistSystemLog(entry) {
  if (state.preview || !state.token) return
  try {
    await request('/api/v1/learning/system-logs', {
      method: 'POST',
      body: JSON.stringify(entry),
    })
  } catch {
    // 日志写入失败不阻断主流程。
  }
}

function updateShellVisibility() {
  const loggedIn = Boolean((state.token && state.user) || state.preview)
  elements.loginScreen.classList.toggle('hidden', loggedIn)
  elements.productShell.classList.toggle('hidden', !loggedIn)
  if (loggedIn) {
    setView(state.activeView || 'profileView', { silent: true })
  }
}

function updateAuthView() {
  const user = state.user
  const displayName = user?.nickname || user?.username || '未登录'
  const initial = displayName.slice(0, 1).toUpperCase()

  elements.userBadge.textContent = displayName
  elements.userAvatar.textContent = initial
  elements.profileAvatar.textContent = initial
  elements.profileName.textContent = displayName
  elements.profileUsername.textContent = user?.username ? `@${user.username}` : '等待登录'
  elements.profileStatus.textContent = state.token ? '在线' : '离线'
  if (user?.username) elements.usernameInput.value = user.username
  updateShellVisibility()
  renderProfileMetrics()
  renderActivityHeatmap()
}

function setConnection(ok) {
  for (const item of [elements.connectionStatus]) {
    if (!item) continue
    item.classList.toggle('ok', ok)
    item.classList.toggle('bad', !ok)
    item.textContent = ok ? '后端已连接' : '后端未连接'
  }
}

function syncSidebarState() {
  const collapsed = state.sidebarCollapsed
  elements.productShell.classList.toggle('sidebar-collapsed', collapsed)
  elements.toggleSidebarBtn.setAttribute('aria-expanded', String(!collapsed))
  elements.toggleSidebarBtn.title = collapsed ? '显示导航' : '隐藏导航'
  elements.toggleSidebarBtn.setAttribute('aria-label', collapsed ? '显示导航' : '隐藏导航')
}

function setSidebarCollapsed(collapsed) {
  state.sidebarCollapsed = collapsed
  localStorage.setItem('learning.sidebarCollapsed', collapsed ? '1' : '0')
  syncSidebarState()
}

function toggleSidebar() {
  setSidebarCollapsed(!state.sidebarCollapsed)
}

function handleViewportChange(event) {
  if (event.matches) {
    setSidebarCollapsed(true)
  }
}

function setView(viewId, options = {}) {
  state.activeView = viewId
  localStorage.setItem('learning.activeView', viewId)
  document.querySelectorAll('.view').forEach((view) => view.classList.toggle('active', view.id === viewId))
  document.querySelectorAll('.nav-item').forEach((button) => button.classList.toggle('active', button.dataset.view === viewId))
  const [eyebrow, title] = viewMeta[viewId] || viewMeta.profileView
  elements.viewEyebrow.textContent = eyebrow
  elements.viewTitle.textContent = title
  if (!options.silent) {
    logEvent('navigation', `进入${title}`)
  }
  if (viewId === 'reviewView' && !options.skipReviewReload) loadDueReviews()
  if (viewId === 'wordbookView') loadWordbookEntries()
  if (window.matchMedia('(max-width: 1100px)').matches) {
    setSidebarCollapsed(true)
  }
}

function setProfileTab(tabId) {
  const fallback = document.getElementById(tabId) ? tabId : 'accountPanel'
  state.activeProfileTab = fallback
  localStorage.setItem('learning.profileTab', fallback)
  document.querySelectorAll('.profile-tab').forEach((button) => button.classList.toggle('active', button.dataset.profileTab === fallback))
  document.querySelectorAll('.profile-section').forEach((section) => section.classList.toggle('active', section.id === fallback))
}

async function loginOrRegister(mode) {
  const username = elements.usernameInput.value.trim()
  const password = elements.passwordInput.value
  const nickname = elements.nicknameInput?.value.trim() || ''
  if (!username || !password) {
    toast('请输入用户名和密码')
    return
  }
  setLoading(true)
  try {
    const response = await request(`/api/v1/learning/auth/${mode}`, {
      method: 'POST',
      body: JSON.stringify({ username, password, nickname }),
    })
    state.token = response.token
    state.user = response.user
    localStorage.setItem('learning.token', state.token)
    localStorage.setItem('learning.user', JSON.stringify(state.user))
    elements.passwordInput.value = ''
    updateAuthView()
    await loadInitialData()
    logEvent('auth', mode === 'login' ? '登录成功' : '注册成功', state.user?.username || '')
    toast(mode === 'login' ? '登录成功' : '注册成功，已创建默认词书')
  } catch (error) {
    logEvent('error', `${mode === 'login' ? '登录' : '注册'}失败`, error.message)
    toast(`${mode === 'login' ? '登录' : '注册'}失败：${error.message}`)
  } finally {
    setLoading(false)
  }
}

async function logout() {
  if (state.token) {
    try {
      await request('/api/v1/learning/auth/logout', { method: 'POST' })
    } catch {
      // 本地退出优先，服务端不可达时仍清理浏览器状态。
    }
  }
  state.token = ''
  state.user = null
  state.wordbooks = []
  state.wordbookEntries = []
  state.reviewEntries = []
  state.previewReviewEntries = []
  state.modelConfigs = []
  state.promptTemplates = []
  state.currentTemplate = null
  state.currentWordbookId = null
  state.currentWordbookEditId = null
  state.currentModelEditId = null
  state.currentStatusEntryId = null
  state.selectedEntry = null
  state.currentNoteEntry = null
  state.currentRecord = null
  state.currentReviewEntry = null
  state.currentReviewIndex = 0
  state.reviewTyped = ''
  state.reviewWrongCount = 0
  state.pendingReviewEntryId = null
  state.activity = null
  localStorage.removeItem('learning.token')
  localStorage.removeItem('learning.user')
  localStorage.removeItem('learning.wordbookId')
  renderWordbooks()
  renderWordbookEntries()
  renderModelConfigs()
  renderTemplateOptions()
  renderReviewQueue([])
  renderReviewFocus(null)
  closeReviewModal()
  closeWordbookModal()
  closeAccountModal()
  closeEntryStatusModal()
  renderNotes(null)
  renderActivityHeatmap()
  updateAuthView()
  logEvent('auth', '退出登录')
  toast('已退出登录')
}

async function loadInitialData() {
  if (state.preview) {
    loadPreviewData()
    return
  }
  await Promise.allSettled([loadAgents(), loadWordbooks(), loadModelConfigs(), loadPromptTemplates(), loadActivity(), loadSystemLogs()])
  await Promise.allSettled([loadDueReviews(), loadWordbookEntries()])
}

function loadPreviewData() {
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
  state.promptTemplates = [
    {
      id: 1001,
      name: '英语词汇卡片 JSON',
      code: 'english_vocab_card_json',
      type: 'user',
      tags: '英语,词汇,JSON',
      content: '请为英语词汇「{{term}}」生成学习卡片。只输出合法 JSON，不要输出 Markdown。JSON 字段包括：term、definitions、examples、collocations、synonyms、antonyms、word_family、memory_tips。',
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
  state.currentWordbookId = state.currentWordbookId || '1'
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
  renderTemplateOptions()
  renderWordbooks()
  renderWordbookEntries()
  renderReviewQueue(state.reviewEntries)
  renderRecord(previewRecord())
  renderActivityHeatmap()
  logEvent('system', '设计预览模式', '使用 ?preview=1 查看无后端登录后的产品界面')
}

async function loadAgents() {
  if (state.preview) {
    elements.agentSelect.innerHTML = '<option value="english_vocabulary">English Vocabulary (english_vocabulary)</option>'
    state.lastAgentCode = elements.agentSelect.value || 'english_vocabulary'
    setConnection(true)
    return
  }
  try {
    const agents = await request('/api/v1/ai/agents')
    elements.agentSelect.innerHTML = ''
    const list = Array.isArray(agents) && agents.length ? agents : [{ code: 'english_vocabulary', name: 'English Vocabulary' }]
    for (const agent of list) {
      const option = document.createElement('option')
      option.value = agent.code
      option.textContent = `${agent.name || agent.code} (${agent.code})`
      elements.agentSelect.appendChild(option)
    }
    if ([...elements.agentSelect.options].some((item) => item.value === 'english_vocabulary')) {
      elements.agentSelect.value = 'english_vocabulary'
    }
    state.lastAgentCode = elements.agentSelect.value || ''
    setConnection(true)
  } catch (error) {
    setConnection(false)
    elements.agentSelect.innerHTML = '<option value="english_vocabulary">English Vocabulary</option>'
    state.lastAgentCode = elements.agentSelect.value || 'english_vocabulary'
    logEvent('error', 'Agent 加载失败', error.message)
  }
}

async function loadModelConfigs() {
  if (state.preview) {
    renderModelConfigs()
    renderStudyModelOptions()
    return
  }
  try {
    const configs = await request('/api/v1/ai/model-configs')
    state.modelConfigs = Array.isArray(configs) ? configs : []
    renderModelConfigs()
    renderStudyModelOptions()
  } catch (error) {
    logEvent('error', '模型配置加载失败', error.message)
    renderStudyModelOptions()
  }
}

async function loadPromptTemplates() {
  if (state.preview) {
    renderTemplateOptions()
    return
  }
  try {
    const templates = await request('/api/v1/ai/prompt-templates?type=user')
    state.promptTemplates = Array.isArray(templates) ? templates : []
    renderTemplateOptions()
  } catch (error) {
    logEvent('error', '模板加载失败', error.message)
    renderTemplateOptions()
  }
}

function renderTemplateOptions() {
  if (!elements.templateSelect) return
  const previous = elements.templateSelect.value || 'english_vocab_card_json'
  const templates = state.promptTemplates.length
    ? state.promptTemplates
    : [
        { code: 'english_vocab_card_json', name: '词汇卡片 JSON' },
        { code: 'english_vocab_quiz_json', name: '练习题 JSON' },
      ]
  elements.templateSelect.innerHTML = ''
  for (const template of templates) {
    const option = document.createElement('option')
    option.value = template.code
    option.textContent = `${template.name || template.code} (${template.code})`
    elements.templateSelect.appendChild(option)
  }
  elements.templateSelect.value = templates.some((item) => item.code === previous) ? previous : templates[0]?.code || ''
  renderSelectedTemplate()
}

async function renderSelectedTemplate() {
  const code = elements.templateSelect?.value
  if (!code) return
  const previousCode = state.lastTemplateCode
  if (previousCode && previousCode !== code) {
    const confirmed = await confirmAction({
      title: '切换学习 Agent 模板',
      message: `确认从模板「${previousCode}」切换到「${code}」？未保存的模板编辑内容不会自动保存。`,
      acceptText: '确认切换',
    })
    if (!confirmed) {
      elements.templateSelect.value = previousCode
      return
    }
  }
  let template = state.promptTemplates.find((item) => item.code === code)
  if (!template && !state.preview) {
    try {
      template = await request(`/api/v1/ai/prompt-templates/code/${encodeURIComponent(code)}`)
      if (template) {
        state.promptTemplates = [template, ...state.promptTemplates.filter((item) => item.code !== code)]
      }
    } catch (error) {
      logEvent('error', '模板详情加载失败', error.message)
    }
  }
  state.currentTemplate = template || null
  fillTemplateForm(template)
}

async function changeLearningAgent() {
  const nextCode = elements.agentSelect?.value || ''
  const previousCode = state.lastAgentCode || ''
  if (previousCode && previousCode !== nextCode) {
    const confirmed = await confirmAction({
      title: '修改学习 Agent',
      message: `确认将学习 Agent 从「${previousCode}」切换为「${nextCode}」？后续学习请求会使用新的 Agent。`,
      acceptText: '确认修改',
    })
    if (!confirmed) {
      elements.agentSelect.value = previousCode
      return
    }
  }
  state.lastAgentCode = nextCode
  logEvent('ai', '修改学习 Agent', nextCode)
}

function fillTemplateForm(template) {
  if (!elements.templateNameInput) return
  state.lastTemplateCode = template?.code || elements.templateSelect?.value || ''
  elements.templateNameInput.value = template?.name || ''
  elements.templateCodeInput.value = template?.code || ''
  elements.templateTypeInput.value = template?.type || 'user'
  elements.templateSequenceInput.value = template?.sequence ?? 0
  elements.templateTagsInput.value = template?.tags || ''
  elements.templateDescriptionInput.value = template?.description || ''
  elements.templateExampleInput.value = template?.exampleInput || ''
  elements.templateExampleOutput.value = template?.exampleOutput || ''
  elements.templateContentInput.value = template?.content || ''
  const placeholders = templatePlaceholders(template)
  renderTemplatePlaceholders(placeholders)
  elements.templateSummary.textContent = template
    ? `${template.description || '暂无描述'} · ${placeholders.length ? `占位符 ${placeholders.join(', ')}` : '暂无占位符'}`
    : '选择模板后查看完整信息。'
  validateTemplatePlaceholders({ quiet: true })
}

function templatePlaceholders(template = state.currentTemplate) {
  const declared = parseTemplateVariables(template?.variables)
  const fromContent = extractPlaceholders(template?.content || '')
  return [...new Set([...declared, ...fromContent])]
}

function parseTemplateVariables(variables) {
  if (!variables) return []
  try {
    const parsed = typeof variables === 'string' ? JSON.parse(variables) : variables
    const list = Array.isArray(parsed) ? parsed : Object.values(parsed)
    return list
      .map((item) => (typeof item === 'string' ? item : item?.name))
      .filter(Boolean)
  } catch {
    return extractPlaceholders(String(variables))
  }
}

function extractPlaceholders(content) {
  return [...String(content || '').matchAll(/\{\{\s*([a-zA-Z0-9_.-]+)\s*\}\}/g)].map((match) => match[1])
}

function renderTemplatePlaceholders(placeholders) {
  if (!placeholders.length) {
    elements.templatePlaceholderList.className = 'chips empty'
    elements.templatePlaceholderList.textContent = '暂无占位符'
    return
  }
  elements.templatePlaceholderList.className = 'chips'
  elements.templatePlaceholderList.innerHTML = placeholders.map((item) => `<span class="chip">${escapeHtml(`{{${item}}}`)}</span>`).join('')
}

function validateTemplatePlaceholders(options = {}) {
  const content = elements.templateContentInput?.value || ''
  const required = parseTemplateVariables(state.currentTemplate?.variables)
  const missing = required.filter((name) => !new RegExp(`\\{\\{\\s*${escapeRegExp(name)}\\s*\\}\\}`).test(content))
  if (!required.length && !extractPlaceholders(content).includes('term')) {
    missing.push('term')
  }
  const ok = missing.length === 0
  elements.templateValidationMessage.className = `validation-message ${ok ? 'ok' : 'bad'}`
  elements.templateValidationMessage.textContent = ok ? '占位符校验通过' : `缺少占位符：${missing.map((name) => `{{${name}}}`).join('、')}`
  if (!ok && !options.quiet) toast(elements.templateValidationMessage.textContent)
  return ok
}

async function savePromptTemplate() {
  const template = state.currentTemplate
  if (!template?.id) {
    toast('请先选择模板')
    return
  }
  if (!validateTemplatePlaceholders()) return
  const payload = {
    name: elements.templateNameInput.value.trim(),
    code: elements.templateCodeInput.value.trim(),
    type: elements.templateTypeInput.value.trim() || 'user',
    tags: elements.templateTagsInput.value.trim(),
    content: elements.templateContentInput.value.trim(),
    variables: template.variables || JSON.stringify(extractPlaceholders(elements.templateContentInput.value).map((name) => ({ name, required: true }))),
    description: elements.templateDescriptionInput.value.trim(),
    exampleInput: elements.templateExampleInput.value.trim(),
    exampleOutput: elements.templateExampleOutput.value.trim(),
    publicTemplate: Boolean(template.publicTemplate),
    sequence: Number(elements.templateSequenceInput.value || 0),
  }
  if (!payload.name || !payload.code || !payload.content) {
    toast('请补全模板名称、编码和内容')
    return
  }
  const confirmed = await confirmAction({
    title: '保存学习 Agent 模板',
    message: `确认保存学习 Agent 模板「${payload.name}」？保存后后续学习卡片生成会使用新的模板内容。`,
    acceptText: '确认保存',
  })
  if (!confirmed) return
  setLoading(true)
  try {
    if (state.preview) {
      Object.assign(template, payload)
      renderTemplateOptions()
      elements.templateSelect.value = payload.code
      fillTemplateForm(template)
      toast('设计预览：模板已保存')
      return
    }
    await request(`/api/v1/ai/prompt-templates/${template.id}`, { method: 'PUT', body: JSON.stringify(payload) })
    await loadPromptTemplates()
    elements.templateSelect.value = payload.code
    await renderSelectedTemplate()
    toast('模板已保存')
  } catch (error) {
    logEvent('error', '模板保存失败', error.message)
    toast(`模板保存失败：${error.message}`)
  } finally {
    setLoading(false)
  }
}

function renderStudyModelOptions() {
  if (!elements.studyModelSelect) return
  elements.studyModelSelect.innerHTML = ''
  const enabled = state.modelConfigs.filter((item) => item.enabled)
  if (!enabled.length) {
    elements.studyModelSelect.innerHTML = '<option value="">默认模型</option>'
    return
  }
  for (const item of enabled) {
    const option = document.createElement('option')
    option.value = String(item.id)
    option.textContent = `${item.name} · ${item.modelName}${item.isDefault ? ' · 默认' : ''}`
    elements.studyModelSelect.appendChild(option)
  }
  const preferred = enabled.find((item) => item.isDefault) || enabled[0]
  elements.studyModelSelect.value = String(preferred.id)
}

function renderProviderOptions(selectedProvider = '') {
  if (!elements.modelProviderInput) return
  const provider = selectedProvider || elements.modelProviderInput.value || 'deepseek'
  let html = Object.entries(providerCatalog)
    .map(([value, item]) => `<option value="${escapeHtml(value)}">${escapeHtml(item.label)} (${escapeHtml(value)})</option>`)
    .join('')
  if (provider && !providerCatalog[provider]) {
    html += `<option value="${escapeHtml(provider)}">${escapeHtml(provider)} (自定义)</option>`
  }
  elements.modelProviderInput.innerHTML = html
  elements.modelProviderInput.value = provider
}

function syncModelProviderDefaults(options = {}) {
  const provider = elements.modelProviderInput.value || 'deepseek'
  const config = providerCatalog[provider] || { baseUrl: '', chatPath: '/chat/completions', models: [] }
  const currentModel = options.modelName || elements.modelModelNameInput.value
  const models = [...config.models]
  if (options.keepUnknownModel && currentModel && !models.includes(currentModel)) {
    models.unshift(currentModel)
  }
  elements.modelModelNameInput.innerHTML = models
    .map((model) => `<option value="${escapeHtml(model)}">${escapeHtml(model)}</option>`)
    .join('')
  elements.modelModelNameInput.value = models.includes(currentModel) ? currentModel : models[0] || ''
  if (!options.keepValues) {
    elements.modelBaseUrlInput.value = config.baseUrl || ''
    elements.modelChatPathInput.value = config.chatPath || '/chat/completions'
  }
}

function openModelModal(id = null) {
  renderProviderOptions()
  if (id) {
    editModelConfig(id, { openModal: false })
    elements.modelModalTitle.textContent = '编辑模型'
  } else {
    resetModelForm({ keepModalOpen: true })
    elements.modelModalTitle.textContent = '新增模型'
  }
  elements.modelConfigModal.classList.remove('hidden')
}

function closeModelModal() {
  elements.modelConfigModal.classList.add('hidden')
}

function openAccountModal() {
  elements.accountNicknameInput.value = state.user?.nickname || state.user?.username || ''
  elements.accountCurrentPasswordInput.value = ''
  elements.accountNewPasswordInput.value = ''
  elements.accountConfirmPasswordInput.value = ''
  elements.accountModal.classList.remove('hidden')
}

function closeAccountModal() {
  elements.accountModal?.classList.add('hidden')
}

async function saveAccountProfile() {
  const nickname = elements.accountNicknameInput.value.trim()
  const currentPassword = elements.accountCurrentPasswordInput.value
  const newPassword = elements.accountNewPasswordInput.value
  const confirmPassword = elements.accountConfirmPasswordInput.value
  if (!nickname) {
    toast('请输入昵称')
    return
  }
  if (newPassword || confirmPassword || currentPassword) {
    if (newPassword.length < 6) {
      toast('新密码至少 6 位')
      return
    }
    if (newPassword !== confirmPassword) {
      toast('两次输入的新密码不一致')
      return
    }
    if (!currentPassword) {
      toast('修改密码需要输入当前密码')
      return
    }
  }
  setLoading(true)
  try {
    const payload = { nickname }
    if (newPassword) {
      payload.currentPassword = currentPassword
      payload.newPassword = newPassword
    }
    if (state.preview) {
      state.user = { ...(state.user || {}), nickname }
      localStorage.setItem('learning.user', JSON.stringify(state.user))
      updateAuthView()
      closeAccountModal()
      toast('设计预览：账户信息已更新')
      return
    }
    const user = await request('/api/v1/learning/auth/me', {
      method: 'PUT',
      body: JSON.stringify(payload),
    })
    state.user = user
    localStorage.setItem('learning.user', JSON.stringify(state.user))
    updateAuthView()
    closeAccountModal()
    logEvent('auth', '更新账户信息', nickname)
    toast('账户信息已更新')
  } catch (error) {
    logEvent('error', '账户更新失败', error.message)
    toast(`账户更新失败：${error.message}`)
  } finally {
    setLoading(false)
  }
}

function renderModelConfigs() {
  renderStudyModelOptions()
  if (!elements.modelConfigList) return
  const list = state.modelConfigs
  if (!list.length) {
    elements.modelConfigList.className = 'model-list empty'
    elements.modelConfigList.textContent = '暂无模型配置'
    return
  }
  elements.modelConfigList.className = 'model-list'
  elements.modelConfigList.innerHTML = list
    .map(
      (item) => `
        <div class="model-item ${item.enabled ? '' : 'disabled'}">
          <div>
            <div class="model-title-line">
              <strong>${escapeHtml(item.name)}</strong>
              <span class="mini-pill ${item.enabled ? 'ok' : ''}">${item.enabled ? '启用' : '停用'}</span>
              ${item.isDefault ? '<span class="mini-pill ok">默认</span>' : ''}
            </div>
            <p>${escapeHtml(item.provider)} · ${escapeHtml(item.modelName)} · ${escapeHtml(item.baseUrl || '')}</p>
            <small>优先级 ${item.sequence ?? 0} · ${escapeHtml(item.apiKeyMasked || '')}</small>
          </div>
          <div class="row-actions">
            <button class="icon-action-button" type="button" data-model-toggle="${escapeHtml(item.id)}" title="${item.enabled ? '停用模型' : '启用模型'}" aria-label="${item.enabled ? '停用模型' : '启用模型'}">${item.enabled ? '⏸' : '▶'}</button>
            <button class="icon-action-button" type="button" data-model-edit="${escapeHtml(item.id)}" title="编辑模型" aria-label="编辑模型">✎</button>
            <button class="danger-icon-button" type="button" data-model-delete="${escapeHtml(item.id)}" title="删除模型">×</button>
          </div>
        </div>
      `,
    )
    .join('')
  elements.modelConfigList.querySelectorAll('[data-model-edit]').forEach((button) => {
    button.addEventListener('click', () => openModelModal(button.getAttribute('data-model-edit')))
  })
  elements.modelConfigList.querySelectorAll('[data-model-toggle]').forEach((button) => {
    button.addEventListener('click', () => toggleModelConfig(button.getAttribute('data-model-toggle')))
  })
  elements.modelConfigList.querySelectorAll('[data-model-delete]').forEach((button) => {
    button.addEventListener('click', () => deleteModelConfig(button.getAttribute('data-model-delete')))
  })
}

function editModelConfig(id) {
  const item = state.modelConfigs.find((model) => sameId(model.id, id))
  if (!item) return
  renderProviderOptions(item.provider || 'deepseek')
  state.currentModelEditId = item.id
  elements.modelNameInput.value = item.name || ''
  elements.modelProviderInput.value = item.provider || ''
  if (!providerCatalog[elements.modelProviderInput.value]) {
    renderProviderOptions(elements.modelProviderInput.value || 'deepseek')
  }
  syncModelProviderDefaults({ keepValues: true, keepUnknownModel: true, modelName: item.modelName || '' })
  elements.modelModelNameInput.value = item.modelName || ''
  elements.modelBaseUrlInput.value = item.baseUrl || ''
  elements.modelChatPathInput.value = item.chatPath || '/chat/completions'
  elements.modelApiKeyInput.value = ''
  elements.modelApiKeyInput.placeholder = item.apiKeyMasked || 'API KEY'
  elements.modelSequenceInput.value = item.sequence ?? 0
  elements.modelDefaultInput.checked = Boolean(item.isDefault)
  elements.modelEnabledInput.checked = Boolean(item.enabled)
  syncModelToggleButtons()
}

function resetModelForm(options = {}) {
  state.currentModelEditId = null
  renderProviderOptions('deepseek')
  elements.modelNameInput.value = ''
  elements.modelProviderInput.value = 'deepseek'
  syncModelProviderDefaults()
  elements.modelApiKeyInput.value = ''
  elements.modelApiKeyInput.placeholder = 'API KEY'
  elements.modelSequenceInput.value = '0'
  elements.modelDefaultInput.checked = false
  elements.modelEnabledInput.checked = true
  syncModelToggleButtons()
  if (!options.keepModalOpen) {
    closeModelModal()
  }
}

function syncModelToggleButtons() {
  syncToggleButton(elements.modelDefaultToggleBtn, elements.modelDefaultInput, '已设默认', '默认模型')
  syncToggleButton(elements.modelEnabledToggleBtn, elements.modelEnabledInput, '已启用', '已停用')
}

function syncToggleButton(button, input, activeLabel, inactiveLabel) {
  if (!button || !input) return
  const active = Boolean(input.checked)
  button.classList.toggle('active', active)
  button.setAttribute('aria-pressed', String(active))
  const label = button.querySelector('span')
  if (label) label.textContent = active ? activeLabel : inactiveLabel
}

function toggleModelFlag(input) {
  if (!input) return
  input.checked = !input.checked
  syncModelToggleButtons()
}

async function saveModelConfig() {
  const payload = {
    name: elements.modelNameInput.value.trim(),
    provider: elements.modelProviderInput.value.trim(),
    modelName: elements.modelModelNameInput.value.trim(),
    baseUrl: elements.modelBaseUrlInput.value.trim(),
    chatPath: elements.modelChatPathInput.value.trim() || '/chat/completions',
    apiKey: elements.modelApiKeyInput.value.trim(),
    sequence: Number(elements.modelSequenceInput.value || 0),
    isDefault: elements.modelDefaultInput.checked,
    enabled: elements.modelEnabledInput.checked,
  }
  if (!payload.name || !payload.provider || !payload.modelName || !payload.baseUrl || (!payload.apiKey && !state.currentModelEditId)) {
    toast('请补全模型配置')
    return
  }
  if (state.currentModelEditId) {
    const confirmed = await confirmAction({
      title: '修改模型配置',
      message: `确认修改模型「${payload.name}」？保存后学习页将使用新的模型配置。`,
      acceptText: '确认修改',
    })
    if (!confirmed) return
  }
  setLoading(true)
  try {
    if (state.preview) {
      const id = state.currentModelEditId || String(Date.now())
      const existingIndex = state.modelConfigs.findIndex((item) => sameId(item.id, id))
      const next = { ...payload, id, apiKeyMasked: payload.apiKey ? `${payload.apiKey.slice(0, 6)}****` : 'sk-****' }
      if (next.isDefault) state.modelConfigs.forEach((item) => (item.isDefault = false))
      if (existingIndex >= 0) state.modelConfigs.splice(existingIndex, 1, next)
      else state.modelConfigs.push(next)
      renderModelConfigs()
      resetModelForm()
      toast('设计预览：模型配置已保存')
      return
    }
    const path = state.currentModelEditId ? `/api/v1/ai/model-configs/${encodeURIComponent(state.currentModelEditId)}` : '/api/v1/ai/model-configs'
    const method = state.currentModelEditId ? 'PUT' : 'POST'
    await request(path, { method, body: JSON.stringify(payload) })
    await loadModelConfigs()
    resetModelForm()
    toast('模型配置已保存')
  } catch (error) {
    logEvent('error', '模型配置保存失败', error.message)
    toast(`模型配置保存失败：${error.message}`)
  } finally {
    setLoading(false)
  }
}

async function toggleModelConfig(id) {
  const item = state.modelConfigs.find((model) => sameId(model.id, id))
  if (!item) return
  const targetEnabled = !item.enabled
  const confirmed = await confirmAction({
    title: targetEnabled ? '启用模型' : '停用模型',
    message: `确认${targetEnabled ? '启用' : '停用'}模型「${item.name}」？${targetEnabled ? '启用后学习页可以选择该模型。' : '停用后学习页将不能选择该模型。'}`,
    acceptText: targetEnabled ? '确认启用' : '确认停用',
  })
  if (!confirmed) return
  if (state.preview) {
    item.enabled = targetEnabled
    renderModelConfigs()
    return
  }
  await request(`/api/v1/ai/model-configs/${encodeURIComponent(id)}/${item.enabled ? 'disable' : 'enable'}`, { method: 'POST' })
  await loadModelConfigs()
}

async function setDefaultModelConfig(id) {
  const item = state.modelConfigs.find((model) => sameId(model.id, id))
  if (!item) return
  if (state.preview) {
    state.modelConfigs.forEach((model) => (model.isDefault = sameId(model.id, id)))
    renderModelConfigs()
    return
  }
  await request(`/api/v1/ai/model-configs/${encodeURIComponent(id)}/priority`, {
    method: 'POST',
    body: JSON.stringify({ sequence: item.sequence ?? 0, isDefault: true }),
  })
  await loadModelConfigs()
}

async function deleteModelConfig(id) {
  const item = state.modelConfigs.find((model) => sameId(model.id, id))
  if (!item) return
  const confirmed = await confirmDelete({
    title: '删除模型配置',
    message: `确认删除模型配置「${item.name}」？删除后学习页将不能再选择这个模型。`,
  })
  if (!confirmed) return
  if (state.preview) {
    state.modelConfigs = state.modelConfigs.filter((model) => !sameId(model.id, id))
    renderModelConfigs()
    toast('设计预览：模型配置已删除')
    return
  }
  try {
    await request(`/api/v1/ai/model-configs/${encodeURIComponent(id)}`, { method: 'DELETE' })
    await loadModelConfigs()
    toast('模型配置已删除')
  } catch (error) {
    logEvent('error', '删除模型配置失败', error.message)
    toast(`删除模型配置失败：${error.message}`)
  }
}

async function loadWordbooks() {
  if (state.preview) {
    renderWordbooks()
    renderWordbookEntries()
    renderProfileMetrics()
    return
  }
  if (!state.token) {
    renderWordbooks()
    return
  }
  try {
    const wordbooks = await request('/api/v1/learning/wordbooks')
    state.wordbooks = Array.isArray(wordbooks) ? wordbooks : []
    renderWordbooks()
    await loadWordbookEntries()
  } catch (error) {
    logEvent('error', '词书加载失败', error.message)
    toast(`词书加载失败：${error.message}`)
  }
}

function renderWordbooks() {
  elements.wordbookSelect.innerHTML = ''
  elements.reviewWordbookSelect.innerHTML = ''
  if (!state.wordbooks.length) {
    elements.wordbookSelect.innerHTML = '<option value="">暂无词书</option>'
    elements.reviewWordbookSelect.innerHTML = '<option value="">暂无词书</option>'
    elements.wordbookCards.className = 'wordbook-cards empty'
    elements.wordbookCards.textContent = state.token ? '暂无词书' : '登录后查看词书'
    renderProfileMetrics()
    return
  }

  const hasSelected = state.wordbooks.some((item) => sameId(item.id, state.currentWordbookId))
  const fallback = state.wordbooks.find((item) => item.isDefault) || state.wordbooks[0]
  state.currentWordbookId = hasSelected ? String(state.currentWordbookId) : String(fallback.id)
  localStorage.setItem('learning.wordbookId', String(state.currentWordbookId))

  for (const wordbook of state.wordbooks) {
    const option = document.createElement('option')
    option.value = String(wordbook.id)
    option.textContent = `${wordbook.name} · ${wordbook.entryCount || 0}词 · ${wordbook.dueCount || 0}待复习`
    elements.wordbookSelect.appendChild(option)
    elements.reviewWordbookSelect.appendChild(option.cloneNode(true))
  }
  elements.wordbookSelect.value = String(state.currentWordbookId)
  elements.reviewWordbookSelect.value = String(state.currentWordbookId)

  elements.wordbookCards.className = 'wordbook-cards'
  elements.wordbookCards.innerHTML = state.wordbooks
    .map(
      (item) => `
        <div class="wordbook-card ${sameId(item.id, state.currentWordbookId) ? 'active' : ''}">
          <button class="wordbook-main" type="button" data-wordbook-id="${escapeHtml(item.id)}">
            <strong>${escapeHtml(item.name)}</strong>
            <span>${escapeHtml(item.description || (item.isDefault ? '默认词书' : '自定义词书'))}</span>
            <small>${item.isDefault ? '默认 · ' : ''}${item.entryCount || 0} 个单词 · ${item.dueCount || 0} 个待复习</small>
          </button>
          <div class="row-actions">
            <button class="icon-action-button" type="button" data-wordbook-edit="${escapeHtml(item.id)}" title="编辑词书" aria-label="编辑词书">✎</button>
            <button class="danger-icon-button" type="button" data-wordbook-delete="${escapeHtml(item.id)}" title="删除词书">×</button>
          </div>
        </div>
      `,
    )
    .join('')
  elements.wordbookCards.querySelectorAll('[data-wordbook-id]').forEach((button) => {
    button.addEventListener('click', () => changeWordbook(button.getAttribute('data-wordbook-id')))
  })
  elements.wordbookCards.querySelectorAll('[data-wordbook-edit]').forEach((button) => {
    button.addEventListener('click', () => openWordbookModal(button.getAttribute('data-wordbook-edit')))
  })
  elements.wordbookCards.querySelectorAll('[data-wordbook-delete]').forEach((button) => {
    button.addEventListener('click', () => deleteWordbook(button.getAttribute('data-wordbook-delete')))
  })
  renderProfileMetrics()
}

async function changeWordbook(wordbookId) {
  state.currentWordbookId = String(wordbookId || '')
  elements.wordbookSelect.value = String(wordbookId)
  localStorage.setItem('learning.wordbookId', String(wordbookId))
  renderWordbooks()
  await Promise.allSettled([loadWordbookEntries(), loadDueReviews()])
  logEvent('wordbook', '切换词书', currentWordbookName())
}

function openWordbookModal(id = null) {
  if (id) {
    fillWordbookForm(id)
    elements.wordbookModalTitle.textContent = '编辑词书'
  } else {
    resetWordbookForm({ keepModalOpen: true })
    elements.wordbookModalTitle.textContent = '新增词书'
  }
  elements.wordbookModal.classList.remove('hidden')
}

function closeWordbookModal() {
  elements.wordbookModal?.classList.add('hidden')
}

async function createWordbook() {
  const name = elements.newWordbookInput.value.trim()
  if (!name) {
    toast('请输入新词书名称')
    return
  }
  if (!state.token) {
    toast('请先登录')
    return
  }
  setLoading(true)
  try {
    const payload = {
      name,
      description: elements.wordbookDescriptionInput.value.trim(),
      isDefault: elements.wordbookDefaultInput.checked,
    }
    if (state.preview) {
      const id = state.currentWordbookEditId || String(Date.now())
      if (payload.isDefault) state.wordbooks.forEach((item) => (item.isDefault = false))
      const index = state.wordbooks.findIndex((item) => sameId(item.id, id))
      const existing = index >= 0 ? state.wordbooks[index] : {}
      const next = { ...existing, id, ...payload, entryCount: existing.entryCount ?? 0, dueCount: existing.dueCount ?? 0 }
      if (index >= 0) state.wordbooks.splice(index, 1, { ...state.wordbooks[index], ...next })
      else state.wordbooks.push(next)
      state.currentWordbookId = String(id)
      resetWordbookForm()
      closeWordbookModal()
      renderWordbooks()
      toast('设计预览：词表已保存')
      return
    }
    const path = state.currentWordbookEditId ? `/api/v1/learning/wordbooks/${state.currentWordbookEditId}` : '/api/v1/learning/wordbooks'
    const method = state.currentWordbookEditId ? 'PUT' : 'POST'
    const wordbook = await request(path, { method, body: JSON.stringify(payload) })
    resetWordbookForm()
    closeWordbookModal()
    state.currentWordbookId = String(wordbook.id)
    await loadWordbooks()
    logEvent('wordbook', method === 'PUT' ? '更新词表' : '创建词表', name)
    toast('词表已保存')
  } catch (error) {
    logEvent('error', '保存词表失败', error.message)
    toast(`保存词表失败：${error.message}`)
  } finally {
    setLoading(false)
  }
}

function fillWordbookForm(id) {
  const wordbook = state.wordbooks.find((item) => sameId(item.id, id))
  if (!wordbook) return
  state.currentWordbookEditId = wordbook.id
  elements.newWordbookInput.value = wordbook.name || ''
  elements.wordbookDescriptionInput.value = wordbook.description || ''
  elements.wordbookDefaultInput.checked = Boolean(wordbook.isDefault)
}

function resetWordbookForm(options = {}) {
  state.currentWordbookEditId = null
  elements.newWordbookInput.value = ''
  elements.wordbookDescriptionInput.value = ''
  elements.wordbookDefaultInput.checked = false
  if (!options.keepModalOpen) {
    closeWordbookModal()
  }
}

async function deleteWordbook(id) {
  const wordbook = state.wordbooks.find((item) => sameId(item.id, id))
  if (!wordbook) return
  const confirmed = await confirmDelete({
    title: '删除词书',
    message: `确认删除词书「${wordbook.name}」？词书中的单词也会从该词书移除。`,
  })
  if (!confirmed) return
  if (state.preview) {
    state.wordbooks = state.wordbooks.filter((item) => !sameId(item.id, id))
    if (!state.wordbooks.length) {
      state.wordbooks = [{ id: 1, name: '默认词书', description: '日常学习沉淀', isDefault: true, entryCount: 0, dueCount: 0 }]
    }
    const fallback = state.wordbooks.find((item) => item.isDefault) || state.wordbooks[0]
    fallback.isDefault = true
    state.currentWordbookId = String(fallback.id)
    renderWordbooks()
    renderWordbookEntries()
    renderActivityHeatmap()
    toast('设计预览：词书已删除')
    return
  }
  try {
    await request(`/api/v1/learning/wordbooks/${encodeURIComponent(id)}`, { method: 'DELETE' })
    if (sameId(state.currentWordbookId, id)) {
      localStorage.removeItem('learning.wordbookId')
      state.currentWordbookId = null
    }
    await Promise.allSettled([loadWordbooks(), loadDueReviews(), loadActivity()])
    toast('词书已删除')
  } catch (error) {
    logEvent('error', '删除词书失败', error.message)
    toast(`删除词书失败：${error.message}`)
  }
}

async function loadWordbookEntries() {
  if (state.preview) {
    renderWordbookEntries()
    renderProfileMetrics()
    return
  }
  if (!state.token || !state.currentWordbookId) {
    state.wordbookEntries = []
    renderWordbookEntries()
    return
  }
  try {
    const status = elements.wordStatusFilter?.value || ''
    const query = status ? `?status=${encodeURIComponent(status)}` : ''
    const entries = await request(`/api/v1/learning/wordbooks/${encodeURIComponent(state.currentWordbookId)}/entries${query}`)
    state.wordbookEntries = Array.isArray(entries) ? entries : []
    renderWordbookEntries()
    renderProfileMetrics()
  } catch (error) {
    logEvent('error', '单词本加载失败', error.message)
  }
}

function renderWordbookEntries() {
  const filter = elements.wordStatusFilter?.value || ''
  const entries = filter && state.preview ? state.wordbookEntries.filter((entry) => (entry.status || 'vague') === filter) : state.wordbookEntries
  if (!entries.length) {
    elements.wordbookEntryList.className = 'entry-list empty'
    elements.wordbookEntryList.textContent = state.token ? '当前词书还没有单词' : '登录后查看单词本'
    state.selectedEntry = null
    renderWordbookFocus(null)
    renderNotes(null)
    return
  }
  const selectedEntry = state.selectedEntry && entries.some((entry) => sameId(entry.id, state.selectedEntry.id)) ? state.selectedEntry : entries[0]
  state.selectedEntry = selectedEntry
  elements.wordbookEntryList.className = 'entry-list'
  elements.wordbookEntryList.innerHTML = entries
    .map(
      (entry) => `
        <div class="entry-row ${sameId(selectedEntry.id, entry.id) ? 'active' : ''}">
          <button type="button" data-entry-id="${escapeHtml(entry.id)}">
            <span>${escapeHtml(entry.term || entry.normalizedTerm)}</span>
            <small>${escapeHtml(statusLabel(entry.status))} · 掌握 ${entry.masteryScore ?? 0}</small>
          </button>
          <div class="row-actions">
            <button class="danger-icon-button" type="button" data-entry-delete="${escapeHtml(entry.id)}" title="删除单词">×</button>
          </div>
        </div>
      `,
    )
    .join('')
  elements.wordbookEntryList.querySelectorAll('[data-entry-id]').forEach((button) => {
    button.addEventListener('click', () => {
      const entry = state.wordbookEntries.find((item) => sameId(item.id, button.getAttribute('data-entry-id')))
      selectWordbookEntry(entry)
    })
  })
  elements.wordbookEntryList.querySelectorAll('[data-entry-delete]').forEach((button) => {
    button.addEventListener('click', () => deleteWordbookEntry(button.getAttribute('data-entry-delete')))
  })
  renderWordbookFocus(selectedEntry)
  renderNotes(selectedEntry)
}

function selectWordbookEntry(entry, options = {}) {
  if (!entry) {
    renderWordbookFocus(null)
    return
  }
  state.selectedEntry = entry
  renderWordbookFocus(entry)
  renderNotes(entry)
  if (!options.silent) renderWordbookEntries()
}

function renderWordbookFocus(entry) {
  if (!entry) {
    elements.wordbookFocus.className = 'empty'
    elements.wordbookFocus.textContent = '选择单词后查看详情和笔记'
    return
  }
  const parsed = entry.parsed || {}
  const definitions = normalizeDefinitions(parsed)
  const examples = normalizeExamples(parsed).slice(0, 3)
  const collocations = normalizeArray(parsed?.collocations || parsed?.phrases || parsed?.common_phrases).slice(0, 6)
  const memoryTips = normalizeArray(parsed?.memory_tips || parsed?.memoryTips || parsed?.tips || parsed?.memory).slice(0, 3)
  const tags = Array.isArray(entry.tags) ? entry.tags.slice(0, 6) : []
  const relations = Array.isArray(entry.relations) ? entry.relations.slice(0, 6) : []
  const phoneticItems = [
    parsed?.phonetic?.uk && { type: 'uk', label: 'UK', text: parsed.phonetic.uk },
    parsed?.phonetic?.us && { type: 'us', label: 'US', text: parsed.phonetic.us },
  ].filter(Boolean)
  elements.wordbookFocus.className = 'wordbook-focus-card'
  elements.wordbookFocus.innerHTML = `
    <div class="wordbook-focus-head">
      <div>
        <p class="eyebrow">${escapeHtml(statusLabel(entry.status))} · 阶段 ${entry.reviewStage ?? 0}</p>
        <h4>${escapeHtml(entry.term || entry.normalizedTerm)}</h4>
        <div class="phonetic phonetic-actions">
          ${
            phoneticItems.length
              ? phoneticItems
                  .map(
                    (item) => `
                      <span class="phonetic-item">
                        <span>${escapeHtml(item.label)} ${escapeHtml(item.text)}</span>
                        <button class="mini-audio-button" type="button" data-focus-word-voice="${item.type}" title="播放${item.label}发音">${item.label} ▶</button>
                      </span>
                    `,
                  )
                  .join('')
              : '<span>暂无音标</span>'
          }
        </div>
      </div>
      <div class="inline-actions">
        <span class="mini-pill next-review-pill">下次 ${escapeHtml(formatDateTime(entry.nextReviewTime))}</span>
        <button class="secondary-button compact" type="button" data-status-open="${escapeHtml(entry.id)}">熟练程度</button>
        <button class="secondary-button compact" type="button" data-open-review="${escapeHtml(entry.id)}">去复习</button>
      </div>
    </div>
    <div class="mini-definition-list focus-section">
      ${
        definitions.length
          ? definitions.map((item) => `<div><span>${escapeHtml(item.pos || 'meaning')}</span><p>${escapeHtml(item.cn || item.en || '')}</p></div>`).join('')
          : '<div class="empty">暂无释义</div>'
      }
    </div>
    <div class="focus-section">
      <div class="panel-heading compact-heading">
        <h3>例句</h3>
        <span class="mini-pill">${examples.length}</span>
      </div>
      <div class="stack ${examples.length ? '' : 'empty'}">
        ${
          examples.length
            ? examples
                .map(
                  (item, index) => `
                    <div class="example-item">
                      <button class="icon-button" type="button" data-focus-sentence="${index}" title="播放例句">▶</button>
                      <p class="sentence">${escapeHtml(item.sentence || '')}</p>
                      <p class="translation">${escapeHtml(item.translation || '')}</p>
                    </div>
                  `,
                )
                .join('')
            : '暂无例句'
        }
      </div>
    </div>
    <div class="focus-section">
      <div class="panel-heading compact-heading">
        <h3>记忆提示</h3>
      </div>
      <div class="stack ${memoryTips.length ? '' : 'empty'}">
        ${
          memoryTips.length
            ? memoryTips.map((item) => `<div class="tip-item">${escapeHtml(readText(item, ['content', 'tip', 'text', 'meaning']) || stringifyValue(item))}</div>`).join('')
            : '暂无记忆提示'
        }
      </div>
    </div>
    <div class="focus-subgrid">
      <div>
        <div class="panel-heading compact-heading"><h3>搭配</h3></div>
        <div class="collocation-list ${collocations.length ? '' : 'empty'}">
          ${collocations.length ? collocations.map(renderCollocationMini).join('') : '暂无搭配'}
        </div>
      </div>
      <div>
        <div class="panel-heading compact-heading"><h3>相关单词</h3></div>
        <div class="relation-list ${relations.length ? '' : 'empty'}">
          ${
            relations.length
              ? relations
                  .map(
                    (item) => `
                      <button class="relation-item" type="button" data-focus-related="${escapeHtml(item.relatedTerm || '')}">
                        <div>
                          <strong>${escapeHtml(item.relatedTerm || '')}</strong>
                          <p>${escapeHtml(relationMeaningLine(item))}</p>
                        </div>
                        <small>${escapeHtml(relationMetaLine(item))}</small>
                      </button>
                    `,
                  )
                  .join('')
              : '暂无关联词'
          }
        </div>
      </div>
    </div>
    <div class="focus-section">
      <div class="panel-heading compact-heading">
        <h3>笔记</h3>
        <button class="secondary-button compact" type="button" data-edit-focus-note>编辑笔记</button>
      </div>
      <div class="note-view">${renderMarkdown(entry.note || '') || '<span class="empty">暂无笔记</span>'}</div>
    </div>
    <div class="focus-section">
      <button class="ghost-button compact" type="button" data-toggle-tags>查看标签</button>
      <div class="chips focus-tags hidden">
        ${tags.length ? tags.map((tag) => `<span class="chip tag-chip">${escapeHtml(tagLabel(tag))}</span>`).join('') : '<span class="empty">暂无标签</span>'}
      </div>
    </div>
  `
  elements.wordbookFocus.querySelector('[data-open-review]')?.addEventListener('click', () => openEntryInReview(entry))
  elements.wordbookFocus.querySelector('[data-status-open]')?.addEventListener('click', () => openEntryStatusModal(entry.id))
  elements.wordbookFocus.querySelector('[data-edit-focus-note]')?.addEventListener('click', () => {
    state.currentNoteEntry = entry
    editCurrentNote()
  })
  elements.wordbookFocus.querySelector('[data-toggle-tags]')?.addEventListener('click', () => {
    elements.wordbookFocus.querySelector('.focus-tags')?.classList.toggle('hidden')
  })
  elements.wordbookFocus.querySelectorAll('[data-focus-sentence]').forEach((button) => {
    button.addEventListener('click', () => speak(examples[Number(button.getAttribute('data-focus-sentence'))]?.sentence, elements.voiceSelect.value))
  })
  elements.wordbookFocus.querySelectorAll('[data-focus-word-voice]').forEach((button) => {
    button.addEventListener('click', () => speak(entry.term || entry.normalizedTerm, button.getAttribute('data-focus-word-voice')))
  })
  elements.wordbookFocus.querySelectorAll('[data-focus-related]').forEach((button) => {
    button.addEventListener('click', () => {
      const term = button.getAttribute('data-focus-related') || ''
      elements.termInput.value = term
      setView('studyView')
      study(term)
    })
  })
  elements.wordbookFocus.querySelectorAll('[data-collocation-term]').forEach((button) => {
    button.addEventListener('click', () => {
      const term = button.getAttribute('data-collocation-term') || ''
      elements.termInput.value = term
      setView('studyView')
      study(term)
    })
  })
}

function renderCollocationMini(item) {
  if (typeof item === 'string') {
    return `<button class="collocation-item" type="button" data-collocation-term="${escapeHtml(item)}"><strong>${escapeHtml(item)}</strong><p>暂无含义</p></button>`
  }
  const phrase = readText(item, ['phrase', 'collocation', 'text', 'word', 'expression'])
  const meaning = readText(item, ['meaning_cn', 'meaningCn', 'meaning', 'translation', 'translation_cn', 'cn'])
  return `<button class="collocation-item" type="button" data-collocation-term="${escapeHtml(phrase)}"><strong>${escapeHtml(phrase || '搭配')}</strong><p>${escapeHtml(meaning || '暂无含义')}</p></button>`
}

function openEntryStatusModal(entryId) {
  const entry = state.wordbookEntries.find((item) => sameId(item.id, entryId)) || state.reviewEntries.find((item) => sameId(item.id, entryId))
  if (!entry) return
  state.currentStatusEntryId = entry.id
  elements.entryStatusTerm.textContent = entry.term || entry.normalizedTerm || '当前单词'
  elements.entryStatusModal.classList.remove('hidden')
}

function closeEntryStatusModal() {
  elements.entryStatusModal?.classList.add('hidden')
  state.currentStatusEntryId = null
}

async function chooseEntryStatus(status) {
  const entryId = state.currentStatusEntryId
  if (!entryId) return
  await updateEntryStatus(entryId, status)
  closeEntryStatusModal()
}

async function updateEntryStatus(entryId, status) {
  await saveEntry(entryId, { status })
}

async function deleteWordbookEntry(entryId) {
  const entry = state.wordbookEntries.find((item) => sameId(item.id, entryId)) || state.reviewEntries.find((item) => sameId(item.id, entryId))
  const term = entry?.term || entry?.normalizedTerm || '当前单词'
  const confirmed = await confirmDelete({
    title: '删除单词',
    message: `确认从词书中删除「${term}」？删除后这个单词的笔记和复习计划会从当前词书移除。`,
  })
  if (!confirmed) return
  if (state.preview) {
    state.wordbookEntries = state.wordbookEntries.filter((entry) => !sameId(entry.id, entryId))
    state.reviewEntries = state.reviewEntries.filter((entry) => !sameId(entry.id, entryId))
    state.selectedEntry = null
    renderWordbookEntries()
    renderReviewQueue(state.reviewEntries)
    toast('设计预览：已从词表删除')
    return
  }
  try {
    await request(`/api/v1/learning/wordbook-entries/${encodeURIComponent(entryId)}`, { method: 'DELETE' })
    await Promise.allSettled([loadWordbooks(), loadWordbookEntries(), loadDueReviews()])
    await loadActivity()
    toast('已从词表删除')
  } catch (error) {
    logEvent('error', '删除词条失败', error.message)
    toast(`删除词条失败：${error.message}`)
  }
}

function renderProfileMetrics() {
  const wordbookCount = state.wordbooks.length
  const wordCount = state.wordbooks.reduce((sum, item) => sum + Number(item.entryCount || 0), 0)
  const dueCount = state.wordbooks.reduce((sum, item) => sum + Number(item.dueCount || 0), 0)
  elements.wordbookCount.textContent = String(wordbookCount)
  elements.wordCount.textContent = String(wordCount)
  elements.dueCount.textContent = String(dueCount)
}

async function loadActivity() {
  if (state.preview) {
    state.activity = state.activity || createPreviewActivity()
    renderActivityHeatmap()
    return
  }
  if (!state.token) {
    state.activity = null
    renderActivityHeatmap()
    return
  }
  try {
    state.activity = await request('/api/v1/learning/activity?days=180')
    renderActivityHeatmap()
  } catch (error) {
    logEvent('error', '学习活跃图加载失败', error.message)
    renderActivityHeatmap()
  }
}

function renderActivityHeatmap() {
  if (!elements.activityHeatmap) return
  const items = Array.isArray(state.activity?.items) ? state.activity.items : []
  const learnedTotal = state.activity?.learnedTotal ?? items.reduce((sum, item) => sum + Number(item.learnedCount || 0), 0)
  const reviewTotal = state.activity?.reviewTotal ?? items.reduce((sum, item) => sum + Number(item.reviewCount || 0), 0)
  elements.activitySummary.textContent = `${learnedTotal} 学习 / ${reviewTotal} 复习`
  if (!items.length) {
    elements.activityHeatmap.className = 'activity-heatmap empty'
    elements.activityHeatmap.textContent = state.token || state.preview ? '暂无学习活跃数据' : '登录后查看学习活跃图'
    return
  }
  const maxTotal = Math.max(1, ...items.map((item) => Number(item.totalCount || 0)))
  elements.activityHeatmap.className = 'activity-heatmap'
  elements.activityHeatmap.innerHTML = items
    .map((item) => {
      const total = Number(item.totalCount || 0)
      const level = activityLevel(total, maxTotal)
      const title = `${item.date}: 学习 ${item.learnedCount || 0}，复习 ${item.reviewCount || 0}`
      return `<span data-level="${level}" title="${escapeHtml(title)}" aria-label="${escapeHtml(title)}"></span>`
    })
    .join('')
}

function activityLevel(total, maxTotal) {
  if (!total) return 0
  if (total >= maxTotal * 0.75) return 4
  if (total >= maxTotal * 0.45) return 3
  if (total >= maxTotal * 0.2) return 2
  return 1
}

async function study(term, options = {}) {
  const value = String(term || '').trim()
  if (!value) {
    toast('先输入一个英语单词')
    return
  }
  const forceRefresh = Boolean(options.forceRefresh)
  const modelConfigId = options.modelConfigId !== undefined ? options.modelConfigId : elements.studyModelSelect?.value || null
  setLoading(true)
  try {
    if (state.preview) {
      const record = previewRecord(value)
      record.cacheHit = !forceRefresh
      renderRecord(record)
      logEvent(forceRefresh ? 'ai' : 'cache', forceRefresh ? '预览重新生成词汇卡片' : '预览词汇卡片', record.normalizedTerm)
      toast(forceRefresh ? '设计预览：已模拟重新生成' : '设计预览：已展示模拟学习卡片')
      return
    }
    const record = await request('/api/v1/english/vocabularies/study', {
      method: 'POST',
      body: JSON.stringify({
        term: value,
        agentCode: elements.agentSelect.value,
        templateCode: elements.templateSelect.value,
        modelConfigId,
        forceRefresh,
      }),
    })
    renderRecord(record)
    logEvent(record.cacheHit ? 'cache' : 'ai', record.cacheHit ? '读取词汇缓存' : 'AI 生成词汇卡片', record.normalizedTerm || value)
    if (record?.normalizedTerm && record.normalizedTerm !== value.toLowerCase()) {
      logEvent('cache', '已匹配标准单词', `${value} -> ${record.normalizedTerm}`)
      toast(`已匹配到：${record.normalizedTerm}`)
      return
    }
    toast(record.cacheHit ? '已从数据库缓存读取' : 'AI 已生成并保存到数据库')
  } catch (error) {
    logEvent('error', '学习请求失败', error.message)
    const match = await showBestMatch(value)
    if (!match) {
      toast(`学习请求失败：${error.message}`)
    }
  } finally {
    setLoading(false)
  }
}

function regenerateStudyCard() {
  const term = state.currentRecord?.normalizedTerm || elements.termInput.value
  study(term, { forceRefresh: true })
}

function renderRecord(record) {
  state.currentRecord = record
  state.currentSessionId = record?.sessionId || state.currentSessionId
  const parsed = record?.parsed || null
  const term = parsed?.term || record?.term || record?.normalizedTerm || 'Ready'
  elements.cacheState.textContent = record ? (record.cacheHit ? 'CACHE HIT' : 'AI GENERATED') : '等待输入'
  elements.wordTitle.textContent = term
  const uk = parsed?.phonetic?.uk ? `UK ${parsed.phonetic.uk}` : ''
  const us = parsed?.phonetic?.us ? `US ${parsed.phonetic.us}` : ''
  renderPhonetics(term, uk, us)
  renderDefinitions(parsed)
  renderTags(record?.tags)
  renderRelations(record?.relations)
  renderExamples(parsed)
  renderCollocations(parsed)
  renderMemoryTips(parsed)
  renderRawJson(record)
  renderReviewFocus(record)
  renderNotes(findEntryForRecord(record))
}

function renderPhonetics(term, uk, us) {
  const items = [
    { type: 'uk', label: 'UK', text: uk.replace(/^UK\s+/, '') },
    { type: 'us', label: 'US', text: us.replace(/^US\s+/, '') },
  ].filter((item) => item.text)
  if (!items.length) {
    elements.phoneticLine.className = 'phonetic phonetic-actions'
    elements.phoneticLine.textContent = '暂无音标'
    return
  }
  elements.phoneticLine.className = 'phonetic phonetic-actions'
  elements.phoneticLine.innerHTML = items
    .map(
      (item) => `
        <span class="phonetic-item">
          <span>${escapeHtml(item.text)}</span>
          <button class="mini-audio-button" type="button" data-voice-type="${item.type}" title="播放${item.label}发音">${item.label} ▶</button>
        </span>
      `,
    )
    .join('')
  elements.phoneticLine.querySelectorAll('[data-voice-type]').forEach((button) => {
    button.addEventListener('click', () => speak(term, button.getAttribute('data-voice-type')))
  })
}

function renderRawJson(record) {
  const parsed = record?.parsed || null
  elements.rawJson.textContent = parsed ? JSON.stringify(parsed, null, 2) : record?.rawContent || '{}'
  if (elements.sessionIdBadge) {
    const provider = record?.provider || 'AI'
    const model = record?.modelName || 'raw'
    elements.sessionIdBadge.textContent = record?.sessionId ? `${provider} · ${model} · #${record.sessionId}` : `${provider} · ${model}`
  }
}

function renderDefinitions(parsed) {
  const definitions = normalizeDefinitions(parsed)
  if (!definitions.length) {
    elements.meaningList.innerHTML = '<div class="empty">暂无释义</div>'
    return
  }
  elements.meaningList.innerHTML = definitions
    .map(
      (item) => `
        <div class="meaning-item">
          <span class="pos">${escapeHtml(item.pos || 'meaning')}</span>
          <div>
            <p>${escapeHtml(item.cn || '暂无中文释义')}</p>
            <p class="meaning-en">${escapeHtml(item.en || item.extra || '暂无英文释义')}</p>
          </div>
        </div>
      `,
    )
    .join('')
}

function renderTags(tags) {
  const list = Array.isArray(tags) ? tags : []
  if (!list.length) {
    elements.tagList.className = 'chips empty'
    elements.tagList.textContent = '暂无标签'
    return
  }
  elements.tagList.className = 'chips'
  elements.tagList.innerHTML = list.map((tag) => `<span class="chip tag-chip">${escapeHtml(tagLabel(tag))}</span>`).join('')
}

function renderRelations(relations) {
  const list = Array.isArray(relations) ? relations : []
  if (!list.length) {
    elements.relationList.className = 'relation-list empty'
    elements.relationList.textContent = '暂无关联词'
    return
  }
  elements.relationList.className = 'relation-list'
  elements.relationList.innerHTML = list
    .map(
      (item) => `
        <button class="relation-item" type="button" data-related-term="${escapeHtml(item.relatedTerm || '')}">
          <div>
            <strong>${escapeHtml(item.relatedTerm || '')}</strong>
            <p>${escapeHtml(relationMeaningLine(item))}</p>
          </div>
          <small>${escapeHtml(relationMetaLine(item))}</small>
        </button>
      `,
    )
    .join('')
  elements.relationList.querySelectorAll('[data-related-term]').forEach((button) => {
    button.addEventListener('click', () => {
      const term = button.getAttribute('data-related-term') || ''
      elements.termInput.value = term
      study(term)
    })
  })
}

function renderExamples(parsed) {
  const examples = normalizeExamples(parsed)
  if (!examples.length) {
    elements.examples.className = 'stack empty'
    elements.examples.textContent = '暂无例句'
    return
  }
  elements.examples.className = 'stack'
  elements.examples.innerHTML = examples
    .map(
      (item, index) => `
        <div class="example-item">
          <button class="icon-button" type="button" data-sentence-index="${index}" title="播放例句">▶</button>
          <p class="sentence">${escapeHtml(item.sentence || '')}</p>
          <p class="translation">${escapeHtml(item.translation || '')}</p>
        </div>
      `,
    )
    .join('')
  elements.examples.querySelectorAll('[data-sentence-index]').forEach((button) => {
    button.addEventListener('click', () => speak(examples[Number(button.getAttribute('data-sentence-index'))]?.sentence))
  })
}

function renderCollocations(parsed) {
  const collocations = normalizeArray(parsed?.collocations || parsed?.phrases || parsed?.common_phrases)
  if (!collocations.length) {
    elements.collocations.className = 'collocation-list empty'
    elements.collocations.textContent = '暂无搭配'
    return
  }
  elements.collocations.className = 'collocation-list'
  elements.collocations.innerHTML = collocations
    .map((item) => {
      if (typeof item === 'string') {
        return `
          <button class="collocation-item" type="button" data-collocation-term="${escapeHtml(item)}">
            <strong>${escapeHtml(item)}</strong>
            <p>暂无含义</p>
          </button>
        `
      }
      const phrase = readText(item, ['phrase', 'collocation', 'text', 'word', 'expression'])
      const meaning = readText(item, ['meaning_cn', 'meaningCn', 'meaning', 'translation', 'translation_cn', 'cn'])
      return `
        <button class="collocation-item" type="button" data-collocation-term="${escapeHtml(phrase)}">
          <strong>${escapeHtml(phrase || '搭配')}</strong>
          <p>${escapeHtml(meaning || '暂无含义')}</p>
        </button>
      `
    })
    .join('')
  elements.collocations.querySelectorAll('[data-collocation-term]').forEach((button) => {
    button.addEventListener('click', () => {
      const term = button.getAttribute('data-collocation-term') || ''
      elements.termInput.value = term
      study(term)
    })
  })
}

function renderMemoryTips(parsed) {
  const tips = normalizeArray(parsed?.memory_tips || parsed?.memoryTips || parsed?.tips || parsed?.memory)
  if (!tips.length) {
    elements.memoryTips.className = 'stack empty'
    elements.memoryTips.textContent = '暂无记忆提示'
    return
  }
  elements.memoryTips.className = 'stack'
  elements.memoryTips.innerHTML = tips
    .map((item) => `<div class="tip-item">${escapeHtml(readText(item, ['content', 'tip', 'text', 'meaning']) || stringifyValue(item))}</div>`)
    .join('')
}

async function addCurrentWordToWordbook() {
  const term = state.currentRecord?.normalizedTerm || elements.termInput.value.trim()
  if (!state.token) {
    toast('请先登录')
    return
  }
  if (!term) {
    toast('先学习一个单词')
    return
  }
  openAddWordbookModal(term)
}

function openAddWordbookModal(term) {
  elements.addWordbookTerm.textContent = term
  renderAddWordbookList(term)
  elements.addWordbookModal.classList.remove('hidden')
}

function closeAddWordbookModal() {
  elements.addWordbookModal.classList.add('hidden')
}

function renderAddWordbookList(term) {
  if (!state.wordbooks.length) {
    elements.addWordbookList.className = 'wordbook-picker-list empty'
    elements.addWordbookList.textContent = '暂无词书，请先在个人信息中创建词书'
    return
  }
  elements.addWordbookList.className = 'wordbook-picker-list'
  elements.addWordbookList.innerHTML = state.wordbooks
    .map(
      (wordbook) => `
        <button class="wordbook-picker-item ${sameId(wordbook.id, state.currentWordbookId) ? 'active' : ''}" type="button" data-add-wordbook-id="${escapeHtml(wordbook.id)}">
          <strong>${escapeHtml(wordbook.name)}</strong>
          <span>${escapeHtml(wordbook.description || (wordbook.isDefault ? '默认词书' : '自定义词书'))}</span>
          <small>${wordbook.entryCount || 0} 个单词 · ${wordbook.dueCount || 0} 个待复习</small>
        </button>
      `,
    )
    .join('')
  elements.addWordbookList.querySelectorAll('[data-add-wordbook-id]').forEach((button) => {
    button.addEventListener('click', () => addWordToWordbook(term, button.getAttribute('data-add-wordbook-id')))
  })
}

async function addWordToWordbook(term, wordbookId) {
  if (!wordbookId) {
    toast('请选择词书')
    return
  }
  setLoading(true)
  try {
    if (state.preview) {
      const existing = state.wordbookEntries.find((entry) => entry.normalizedTerm === term)
      if (!existing) {
        const entry = {
          id: String(Date.now()),
          term,
          normalizedTerm: term,
          status: 'vague',
          note: '',
          reviewStage: 0,
          masteryScore: 0,
          nextReviewTime: new Date().toISOString(),
          parsed: state.currentRecord?.parsed,
        }
      state.wordbookEntries.unshift(entry)
      state.reviewEntries.unshift(entry)
      state.activity = createPreviewActivity()
      renderWordbookEntries()
      renderNotes(entry)
      }
      state.currentWordbookId = String(wordbookId)
      localStorage.setItem('learning.wordbookId', state.currentWordbookId)
      closeAddWordbookModal()
      renderWordbooks()
      logEvent('wordbook', '预览加入词表', `${term} -> ${currentWordbookName(wordbookId)}`)
      toast('设计预览：已模拟加入词书')
      return
    }
    state.currentWordbookId = String(wordbookId)
    localStorage.setItem('learning.wordbookId', state.currentWordbookId)
    const entry = await request(`/api/v1/learning/wordbooks/${encodeURIComponent(wordbookId)}/entries`, {
      method: 'POST',
      body: JSON.stringify({ term }),
    })
    await Promise.allSettled([loadWordbooks(), loadDueReviews()])
    await loadWordbookEntries()
    await loadActivity()
    renderNotes(entry)
    closeAddWordbookModal()
    logEvent('wordbook', '加入单词本', `${term} -> ${currentWordbookName(wordbookId)}`)
    toast('已加入词书，并生成复习计划')
  } catch (error) {
    logEvent('error', '加入词书失败', error.message)
    toast(`加入词书失败：${error.message}`)
  } finally {
    setLoading(false)
  }
}

async function chat() {
  const message = elements.chatInput.value.trim()
  if (!message) return
  setLoading(true)
  try {
    if (state.preview) {
      elements.chatInput.value = ''
      elements.rawJson.textContent = JSON.stringify({ preview: true, answer: '这里会显示 AI 追问的原始返回内容。', message }, null, 2)
      logEvent('chat', '预览 AI 追问回复', message)
      toast('设计预览：已模拟 AI 回复')
      return
    }
    const response = await request('/api/v1/ai/agents/chat', {
      method: 'POST',
      body: JSON.stringify({
        agentCode: elements.agentSelect.value,
        modelConfigId: elements.studyModelSelect.value || null,
        sessionId: state.currentSessionId,
        message,
        variables: {
          term: state.currentRecord?.normalizedTerm || elements.termInput.value.trim(),
        },
      }),
    })
    state.currentSessionId = response.sessionId
    elements.chatInput.value = ''
    elements.rawJson.textContent = response.content
    if (elements.sessionIdBadge) {
      elements.sessionIdBadge.textContent = `${response.modelProvider || 'AI'} · ${response.modelName || 'chat'} · #${response.sessionId || '-'}`
    }
    logEvent('chat', 'AI 追问回复', message)
    toast('AI 已回复，完整内容进入个人信息的 AI 会话')
  } catch (error) {
    logEvent('error', '追问失败', error.message)
    toast(`追问失败：${error.message}`)
  } finally {
    setLoading(false)
  }
}

async function loadDueReviews() {
  const selectedWordbookId = elements.reviewWordbookSelect?.value || state.currentWordbookId || ''
  const limit = Math.max(1, Number(elements.reviewLimitInput?.value || 10))
  if (state.preview) {
    state.currentWordbookId = selectedWordbookId || state.currentWordbookId
    localStorage.setItem('learning.wordbookId', String(state.currentWordbookId || ''))
    const entries = (state.previewReviewEntries.length ? state.previewReviewEntries : state.reviewEntries).slice(0, limit)
    renderReviewQueue(entries)
    return
  }
  if (!state.token) {
    renderReviewQueue([])
    return
  }
  try {
    state.currentWordbookId = selectedWordbookId || state.currentWordbookId
    if (state.currentWordbookId) localStorage.setItem('learning.wordbookId', String(state.currentWordbookId))
    const params = new URLSearchParams()
    if (selectedWordbookId) params.set('wordbookId', selectedWordbookId)
    const query = params.toString() ? `?${params.toString()}` : ''
    const entries = await request(`/api/v1/learning/reviews/due${query}`)
    state.reviewEntries = (Array.isArray(entries) ? entries : []).slice(0, limit)
    renderReviewQueue(state.reviewEntries)
    renderProfileMetrics()
  } catch (error) {
    logEvent('error', '复习队列加载失败', error.message)
    toast(`复习队列加载失败：${error.message}`)
  }
}

function openEntryInReview(entry) {
  if (!entry) return
  if (entry.wordbookId) {
    state.currentWordbookId = String(entry.wordbookId)
    localStorage.setItem('learning.wordbookId', state.currentWordbookId)
    if (elements.reviewWordbookSelect) elements.reviewWordbookSelect.value = state.currentWordbookId
    if (elements.wordbookSelect) elements.wordbookSelect.value = state.currentWordbookId
  }
  const existingIndex = state.reviewEntries.findIndex((item) => sameId(item.id, entry.id))
  if (existingIndex >= 0) {
    state.currentReviewIndex = existingIndex
    state.currentReviewEntry = state.reviewEntries[existingIndex]
  } else {
    state.reviewEntries = [entry, ...state.reviewEntries.filter((item) => !sameId(item.id, entry.id))]
    state.currentReviewIndex = 0
    state.currentReviewEntry = entry
  }
  state.reviewTyped = ''
  state.reviewWrongCount = 0
  setView('reviewView', { skipReviewReload: true })
  renderReviewQueue(state.reviewEntries)
  renderNotes(entry)
  toast(`已进入「${entry.term || entry.normalizedTerm}」复习`)
}

function renderReviewQueue(entries) {
  if (!state.token) {
    state.reviewEntries = []
    state.currentReviewIndex = 0
    state.currentReviewEntry = null
    renderReviewFocus(null)
    renderNotes(null)
    updateReviewProgressBadge()
    return
  }
  if (!entries.length) {
    state.reviewEntries = []
    state.currentReviewIndex = 0
    state.currentReviewEntry = null
    renderReviewFocus(null)
    renderNotes(null)
    updateReviewProgressBadge()
    return
  }
  state.reviewEntries = entries
  const existingIndex = state.currentReviewEntry ? entries.findIndex((entry) => sameId(entry.id, state.currentReviewEntry.id)) : -1
  state.currentReviewIndex = existingIndex >= 0 ? existingIndex : Math.min(state.currentReviewIndex, entries.length - 1)
  const selectedEntry = entries[state.currentReviewIndex] || entries[0]
  state.currentReviewEntry = selectedEntry
  renderReviewFocus(selectedEntry)
  renderNotes(selectedEntry)
  updateReviewProgressBadge()
}

function selectReviewEntry(entry) {
  if (!entry) {
    state.currentReviewEntry = null
    state.reviewTyped = ''
    state.reviewWrongCount = 0
    renderReviewFocus(null)
    updateReviewProgressBadge()
    return
  }
  const index = state.reviewEntries.findIndex((item) => sameId(item.id, entry.id))
  state.currentReviewIndex = index >= 0 ? index : state.currentReviewIndex
  state.currentReviewEntry = entry
  state.reviewTyped = ''
  state.reviewWrongCount = 0
  renderReviewFocus(entry)
  renderNotes(entry)
  updateReviewProgressBadge()
}

function renderReviewFocus(entryOrRecord) {
  if (!entryOrRecord) {
    elements.reviewFocus.className = 'empty'
    elements.reviewFocus.textContent = '选择词书和数量后开始复习'
    return
  }
  const parsed = entryOrRecord.parsed || state.currentRecord?.parsed || null
  const term = parsed?.term || entryOrRecord.term || entryOrRecord.normalizedTerm || state.currentRecord?.normalizedTerm || 'Ready'
  const definitions = normalizeDefinitions(parsed).slice(0, 3)
  const letters = renderTypingLetters(term, state.reviewTyped)
  const progress = term ? Math.round((state.reviewTyped.length / term.length) * 100) : 0
  const total = state.reviewEntries.length
  const canPrev = total > 1 && state.currentReviewIndex > 0
  const canNext = total > 1 && state.currentReviewIndex < total - 1
  elements.reviewFocus.className = 'review-focus-card'
  elements.reviewFocus.innerHTML = `
    <div class="review-card-topline">
      <p class="eyebrow">Typing Review</p>
      <span>${total ? `${state.currentReviewIndex + 1} / ${total}` : '0 / 0'}</span>
    </div>
    <h4>${escapeHtml(term)}</h4>
    <p class="phonetic">${escapeHtml([parsed?.phonetic?.uk, parsed?.phonetic?.us].filter(Boolean).join('    ') || '暂无音标')}</p>
    <div class="typing-board" tabindex="0" aria-label="跟敲单词 ${escapeHtml(term)}">
      <div class="typing-letters">${letters}</div>
      <div class="typing-progress"><span style="width: ${progress}%"></span></div>
      <p class="typing-hint">按键盘逐字输入，错误会提示；完成后查看例句并提交复习结果。</p>
    </div>
    <div class="mini-definition-list">
      ${
        definitions.length
          ? definitions.map((item) => `<div><span>${escapeHtml(item.pos || 'meaning')}</span><p>${escapeHtml(item.cn || item.en || '')}</p></div>`).join('')
          : '<div class="empty">暂无释义</div>'
      }
    </div>
    <div class="review-card-actions">
      <button class="secondary-button compact" type="button" data-review-prev ${canPrev ? '' : 'disabled'}>上一个</button>
      <button class="secondary-button compact" type="button" data-review-next ${canNext ? '' : 'disabled'}>下一个</button>
    </div>
  `
  elements.reviewFocus.querySelector('[data-review-prev]')?.addEventListener('click', () => goToReviewOffset(-1))
  elements.reviewFocus.querySelector('[data-review-next]')?.addEventListener('click', () => goToReviewOffset(1))
  updateReviewProgressBadge()
}

function updateReviewProgressBadge() {
  if (!elements.reviewProgressBadge) return
  const total = state.reviewEntries.length
  elements.reviewProgressBadge.textContent = total ? `${state.currentReviewIndex + 1} / ${total}` : '0 / 0'
}

function goToReviewOffset(offset) {
  const total = state.reviewEntries.length
  if (!total) return
  const nextIndex = Math.max(0, Math.min(total - 1, state.currentReviewIndex + offset))
  if (nextIndex === state.currentReviewIndex && state.currentReviewEntry) return
  state.currentReviewIndex = nextIndex
  selectReviewEntry(state.reviewEntries[nextIndex])
}

function renderTypingLetters(term, typed) {
  return [...String(term || '')]
    .map((letter, index) => {
      const className = index < typed.length ? 'typed' : index === typed.length ? 'current' : ''
      const label = letter === ' ' ? 'Space' : letter
      return `<span class="${className}">${escapeHtml(label)}</span>`
    })
    .join('')
}

function handleReviewKeydown(event) {
  if (state.activeView !== 'reviewView' || !state.currentReviewEntry || !state.token) return
  const activeTag = document.activeElement?.tagName?.toLowerCase()
  if (['input', 'textarea', 'select'].includes(activeTag) || elements.reviewCompleteModal?.classList.contains('hidden') === false) return
  if (event.altKey || event.ctrlKey || event.metaKey) return
  if (event.key === 'Backspace') {
    event.preventDefault()
    state.reviewTyped = state.reviewTyped.slice(0, -1)
    renderReviewFocus(state.currentReviewEntry)
    return
  }
  if (event.key === 'Escape') {
    state.reviewTyped = ''
    state.reviewWrongCount = 0
    renderReviewFocus(state.currentReviewEntry)
    return
  }
  if (event.key.length !== 1) return
  const term = reviewTargetTerm(state.currentReviewEntry)
  const expected = term[state.reviewTyped.length]
  if (!expected) return
  event.preventDefault()
  if (event.key.toLowerCase() === expected.toLowerCase()) {
    state.reviewTyped += expected
    playUiTone('correct')
    renderReviewFocus(state.currentReviewEntry)
    if (state.reviewTyped.length === term.length) {
      window.setTimeout(() => completeReviewTyping(), 120)
    }
    return
  }
  state.reviewWrongCount += 1
  playUiTone('wrong')
  shakeTypingBoard()
}

function reviewTargetTerm(entry) {
  return String(entry?.term || entry?.normalizedTerm || '').trim()
}

function shakeTypingBoard() {
  const board = elements.reviewFocus.querySelector('.typing-board')
  if (!board) return
  board.classList.remove('shake')
  void board.offsetWidth
  board.classList.add('shake')
}

function completeReviewTyping() {
  const entry = state.currentReviewEntry
  if (!entry) return
  state.pendingReviewEntryId = entry.id
  playUiTone('success')
  renderReviewCompleteModal(entry)
  showCelebration()
}

function renderReviewCompleteModal(entry) {
  const parsed = entry?.parsed || {}
  const examples = normalizeExamples(parsed).slice(0, 3)
  elements.modalWordTitle.textContent = reviewTargetTerm(entry)
  elements.modalExamples.className = examples.length ? 'modal-examples' : 'modal-examples empty'
  elements.modalExamples.innerHTML = examples.length
    ? examples
        .map(
          (item, index) => `
            <div class="modal-example-item">
              <button class="mini-audio-button" type="button" data-modal-sentence="${index}" title="播放例句">▶</button>
              <p class="sentence">${escapeHtml(item.sentence || '')}</p>
              <p class="translation">${escapeHtml(item.translation || '')}</p>
            </div>
          `,
        )
        .join('')
    : '暂无例句'
  elements.modalExamples.querySelectorAll('[data-modal-sentence]').forEach((button) => {
    button.addEventListener('click', () => speak(examples[Number(button.getAttribute('data-modal-sentence'))]?.sentence, elements.voiceSelect.value))
  })
  elements.reviewCompleteModal.classList.remove('hidden')
}

function closeReviewModal(options = {}) {
  if (!elements.reviewCompleteModal) return
  elements.reviewCompleteModal.classList.add('hidden')
  state.pendingReviewEntryId = null
  if (!options.keepTyped) {
    state.reviewTyped = ''
    state.reviewWrongCount = 0
  }
  if (!options.skipRender && state.activeView === 'reviewView' && state.currentReviewEntry) {
    renderReviewFocus(state.currentReviewEntry)
  }
}

function openForgottenDetailModal(entry) {
  if (!entry) return
  const parsed = entry.parsed || {}
  const definitions = normalizeDefinitions(parsed).slice(0, 4)
  const examples = normalizeExamples(parsed).slice(0, 3)
  const memoryTips = normalizeArray(parsed?.memory_tips || parsed?.memoryTips || parsed?.tips || parsed?.memory).slice(0, 2)
  const collocations = normalizeArray(parsed?.collocations || parsed?.phrases || parsed?.common_phrases).slice(0, 4)
  elements.forgottenDetailTitle.textContent = entry.term || entry.normalizedTerm || '单词详情'
  elements.forgottenDetailContent.className = 'forgotten-detail-content'
  elements.forgottenDetailContent.innerHTML = `
    <div class="forgotten-word-head">
      <div>
        <p class="eyebrow">${escapeHtml(statusLabel(entry.status))}</p>
        <h4>${escapeHtml(entry.term || entry.normalizedTerm || '')}</h4>
        <p class="phonetic">${escapeHtml([parsed?.phonetic?.uk, parsed?.phonetic?.us].filter(Boolean).join('    ') || '暂无音标')}</p>
      </div>
      <button class="mini-audio-button" type="button" data-forgotten-word-audio>播放</button>
    </div>
    <div class="mini-definition-list">
      ${
        definitions.length
          ? definitions.map((item) => `<div><span>${escapeHtml(item.pos || 'meaning')}</span><p>${escapeHtml(item.cn || item.en || '')}</p><p class="meaning-en">${escapeHtml(item.en || item.extra || '')}</p></div>`).join('')
          : '<div class="empty">暂无释义</div>'
      }
    </div>
    <div class="focus-section">
      <div class="panel-heading compact-heading"><h3>例句</h3></div>
      <div class="stack ${examples.length ? '' : 'empty'}">
        ${
          examples.length
            ? examples
                .map(
                  (item, index) => `
                    <div class="example-item">
                      <button class="icon-button" type="button" data-forgotten-sentence="${index}" title="播放例句">▶</button>
                      <p class="sentence">${escapeHtml(item.sentence || '')}</p>
                      <p class="translation">${escapeHtml(item.translation || '')}</p>
                    </div>
                  `,
                )
                .join('')
            : '暂无例句'
        }
      </div>
    </div>
    <div class="focus-section">
      <div class="panel-heading compact-heading"><h3>记忆提示</h3></div>
      <div class="stack ${memoryTips.length ? '' : 'empty'}">
        ${
          memoryTips.length
            ? memoryTips.map((item) => `<div class="tip-item">${escapeHtml(readText(item, ['content', 'tip', 'text', 'meaning']) || stringifyValue(item))}</div>`).join('')
            : '暂无记忆提示'
        }
      </div>
    </div>
    <div class="focus-section">
      <div class="panel-heading compact-heading"><h3>搭配</h3></div>
      <div class="collocation-list ${collocations.length ? '' : 'empty'}">
        ${collocations.length ? collocations.map(renderCollocationMini).join('') : '暂无搭配'}
      </div>
    </div>
  `
  elements.forgottenDetailContent.querySelector('[data-forgotten-word-audio]')?.addEventListener('click', () => speak(entry.term || entry.normalizedTerm, elements.voiceSelect.value))
  elements.forgottenDetailContent.querySelectorAll('[data-forgotten-sentence]').forEach((button) => {
    button.addEventListener('click', () => speak(examples[Number(button.getAttribute('data-forgotten-sentence'))]?.sentence, elements.voiceSelect.value))
  })
  elements.forgottenDetailModal.classList.remove('hidden')
}

function closeForgottenDetailModal() {
  if (!elements.forgottenDetailModal) return
  elements.forgottenDetailModal.classList.add('hidden')
}

function showCelebration() {
  const layer = elements.celebrationLayer
  if (!layer) return
  layer.innerHTML = Array.from({ length: 34 }, (_, index) => {
    const left = Math.round(Math.random() * 100)
    const delay = Math.round(Math.random() * 260)
    const color = ['#818cf8', '#60a5fa', '#7dd3a8', '#facc6b', '#fb7185'][index % 5]
    return `<span style="left:${left}%; animation-delay:${delay}ms; background:${color}"></span>`
  }).join('')
  layer.classList.add('show')
  window.clearTimeout(showCelebration.timer)
  showCelebration.timer = window.setTimeout(() => {
    layer.classList.remove('show')
    layer.innerHTML = ''
  }, 1500)
}

function playUiTone(type) {
  try {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext
    if (!AudioContextClass) return
    const context = playUiTone.context || new AudioContextClass()
    playUiTone.context = context
    const oscillator = context.createOscillator()
    const gain = context.createGain()
    const now = context.currentTime
    const config = {
      correct: { frequency: 520, duration: 0.045, gain: 0.025, type: 'sine' },
      wrong: { frequency: 150, duration: 0.12, gain: 0.05, type: 'square' },
      success: { frequency: 720, duration: 0.16, gain: 0.045, type: 'triangle' },
    }[type] || { frequency: 360, duration: 0.08, gain: 0.03, type: 'sine' }
    oscillator.type = config.type
    oscillator.frequency.setValueAtTime(config.frequency, now)
    gain.gain.setValueAtTime(config.gain, now)
    gain.gain.exponentialRampToValueAtTime(0.0001, now + config.duration)
    oscillator.connect(gain)
    gain.connect(context.destination)
    oscillator.start(now)
    oscillator.stop(now + config.duration)
  } catch {
    // 音效是锦上添花，浏览器限制时不影响跟敲。
  }
}

async function submitReview(entryId, result) {
  setLoading(true)
  try {
    if (state.preview) {
      const entry = state.reviewEntries.find((item) => sameId(item.id, entryId))
      const completedIndex = state.reviewEntries.findIndex((item) => sameId(item.id, entryId))
      if (entry) {
        entry.status = reviewResultToStatus(result)
        const source = state.wordbookEntries.find((item) => sameId(item.id, entryId))
        if (source) source.status = entry.status
        const previewSource = state.previewReviewEntries.find((item) => sameId(item.id, entryId))
        if (previewSource) previewSource.status = entry.status
      }
      state.reviewTyped = ''
      state.reviewWrongCount = 0
      closeReviewModal({ skipRender: true })
      if (result === 'remembered' && completedIndex >= 0 && state.reviewEntries.length > 1) {
        state.currentReviewIndex = Math.min(completedIndex + 1, state.reviewEntries.length - 1)
      }
      renderReviewQueue(state.reviewEntries)
      renderWordbookEntries()
      logEvent('review', '预览提交复习结果', `${entryId} -> ${result}`)
      if (result === 'forgotten') {
        openForgottenDetailModal(entry || state.currentReviewEntry)
      }
      toast('设计预览：已模拟提交复习结果')
      return
    }
    const currentEntryBeforeSubmit = state.currentReviewEntry
    const currentIndexBeforeSubmit = state.currentReviewIndex
    const response = await request(`/api/v1/learning/reviews/${encodeURIComponent(entryId)}`, {
      method: 'POST',
      body: JSON.stringify({ result }),
    })
    await Promise.allSettled([loadWordbooks(), loadWordbookEntries()])
    await loadActivity()
    state.reviewTyped = ''
    state.reviewWrongCount = 0
    closeReviewModal({ skipRender: true })
    if (result === 'remembered') {
      await loadDueReviews()
      if (state.reviewEntries.length) {
        state.currentReviewIndex = Math.min(currentIndexBeforeSubmit, state.reviewEntries.length - 1)
        selectReviewEntry(state.reviewEntries[state.currentReviewIndex])
      }
    } else {
      const updatedEntry = state.wordbookEntries.find((item) => sameId(item.id, entryId)) || { ...currentEntryBeforeSubmit, status: reviewResultToStatus(result) }
      state.currentReviewEntry = updatedEntry
      renderReviewFocus(updatedEntry)
      renderNotes(updatedEntry)
      updateReviewProgressBadge()
      if (result === 'forgotten') {
        openForgottenDetailModal(updatedEntry)
      }
    }
    logEvent('review', '提交复习结果', `${response.normalizedTerm} -> ${result}`)
    toast(`已记录复习，下次：${formatDateTime(response.nextReviewTime)}`)
  } catch (error) {
    logEvent('error', '提交复习失败', error.message)
    toast(`提交复习失败：${error.message}`)
  } finally {
    setLoading(false)
  }
}

function findEntryForRecord(record) {
  const term = record?.normalizedTerm || record?.term || record?.parsed?.term
  if (!term) return null
  return state.wordbookEntries.find((entry) => entry.normalizedTerm === term || entry.term === term) || null
}

function renderNotes(entry) {
  state.currentNoteEntry = entry || null
  const html = entry?.note ? renderMarkdown(entry.note) : ''
  const fallback = entry ? '<span class="empty">暂无笔记，点击编辑记录 Markdown</span>' : '<span class="empty">加入或选择单词后可以记录 Markdown 笔记</span>'
  elements.studyNote.className = `note-view${html ? '' : ' empty'}`
  elements.reviewNote.className = `note-view${html ? '' : ' empty'}`
  elements.studyNote.innerHTML = html || fallback
  elements.reviewNote.innerHTML = html || (entry ? '<span class="empty">暂无笔记，复习时也可以编辑同一份笔记</span>' : '<span class="empty">选择复习单词后查看同一份 Markdown 笔记</span>')
}

function editCurrentNote() {
  const entry = state.currentNoteEntry || findEntryForRecord(state.currentRecord)
  if (!entry) {
    toast('请先把单词加入当前词表')
    return
  }
  state.currentNoteEntry = entry
  const focusTarget = state.selectedEntry && sameId(state.selectedEntry.id, entry.id) ? elements.wordbookFocus.querySelector('.note-view') : null
  const textarea = `
    <div class="note-editor">
      <textarea rows="8" placeholder="支持 Markdown，例如：## 记忆点">${escapeHtml(entry.note || '')}</textarea>
      <div class="inline-actions">
        <button class="secondary-button compact" type="button" data-save-note>保存笔记</button>
        <button class="ghost-button compact" type="button" data-cancel-note>取消</button>
      </div>
    </div>
  `
  elements.studyNote.innerHTML = textarea
  elements.reviewNote.innerHTML = textarea
  if (focusTarget) focusTarget.innerHTML = textarea
  document.querySelectorAll('[data-save-note]').forEach((button) => button.addEventListener('click', () => saveCurrentNote(button)))
  document.querySelectorAll('[data-cancel-note]').forEach((button) =>
    button.addEventListener('click', () => {
      renderNotes(entry)
      if (state.selectedEntry && sameId(state.selectedEntry.id, entry.id)) renderWordbookFocus(entry)
    }),
  )
}

async function saveCurrentNote(button) {
  const entry = state.currentNoteEntry
  if (!entry) return
  const input = button.closest('.note-editor')?.querySelector('textarea')
  const note = input?.value || ''
  await saveEntry(entry.id, { note })
}

async function saveEntry(entryId, payload) {
  if (state.preview) {
    for (const list of [state.wordbookEntries, state.reviewEntries]) {
      const entry = list.find((item) => sameId(item.id, entryId))
      if (entry) Object.assign(entry, payload)
    }
    const updated = state.wordbookEntries.find((item) => sameId(item.id, entryId)) || state.reviewEntries.find((item) => sameId(item.id, entryId))
    if (state.selectedEntry && sameId(state.selectedEntry.id, entryId)) {
      state.selectedEntry = { ...state.selectedEntry, ...updated }
    }
    if (state.currentReviewEntry && sameId(state.currentReviewEntry.id, entryId)) {
      state.currentReviewEntry = { ...state.currentReviewEntry, ...updated }
    }
    renderWordbookEntries()
    renderReviewQueue(state.reviewEntries)
    renderNotes(updated)
    toast('设计预览：词条已更新')
    return updated
  }
  try {
    const updated = await request(`/api/v1/learning/wordbook-entries/${encodeURIComponent(entryId)}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    })
    state.wordbookEntries = state.wordbookEntries.map((entry) => (sameId(entry.id, entryId) ? { ...entry, ...updated } : entry))
    state.reviewEntries = state.reviewEntries.map((entry) => (sameId(entry.id, entryId) ? { ...entry, ...updated } : entry))
    if (state.selectedEntry && sameId(state.selectedEntry.id, entryId)) {
      state.selectedEntry = { ...state.selectedEntry, ...updated }
    }
    if (state.currentReviewEntry && sameId(state.currentReviewEntry.id, entryId)) {
      state.currentReviewEntry = { ...state.currentReviewEntry, ...updated }
    }
    renderWordbookEntries()
    renderReviewQueue(state.reviewEntries)
    renderNotes(updated)
    toast('词条已更新')
    return updated
  } catch (error) {
    logEvent('error', '词条更新失败', error.message)
    toast(`词条更新失败：${error.message}`)
    return null
  }
}

function renderSystemLogs() {
  if (!state.systemLogs.length) {
    elements.systemLogList.className = 'log-list empty'
    elements.systemLogList.textContent = '暂无系统日志'
    return
  }
  elements.systemLogList.className = 'log-list'
  elements.systemLogList.innerHTML = state.systemLogs
    .map(
      (item) => `
        <div class="log-item">
          <span>${escapeHtml(logTypeLabel(item.type))}</span>
          <div>
            <strong>${escapeHtml(item.title)}</strong>
            <p>${escapeHtml(item.detail || '')}</p>
            <small>${escapeHtml(formatDateTime(item.time || item.createTime))}${item.source ? ` · ${escapeHtml(item.source)}` : ''}</small>
          </div>
        </div>
      `,
    )
    .join('')
}

async function loadSystemLogs() {
  if (state.preview) {
    renderSystemLogs()
    return
  }
  if (!state.token) {
    state.systemLogs = readJsonStorage('learning.systemLogs') || []
    renderSystemLogs()
    return
  }
  try {
    const logs = await request('/api/v1/learning/system-logs?limit=80')
    state.systemLogs = Array.isArray(logs) ? logs : []
    localStorage.removeItem('learning.systemLogs')
    renderSystemLogs()
  } catch (error) {
    state.systemLogs = readJsonStorage('learning.systemLogs') || state.systemLogs
    renderSystemLogs()
    toast(`系统日志加载失败：${error.message}`)
  }
}

async function clearLogs() {
  const confirmed = await confirmDelete({
    title: '清空系统日志',
    message: '确认清空系统日志？清空后当前列表中的日志记录将被移除。',
    acceptText: '确认清空',
  })
  if (!confirmed) return
  state.systemLogs = []
  try {
    if (state.preview || !state.token) {
      localStorage.removeItem('learning.systemLogs')
    } else {
      await request('/api/v1/learning/system-logs', { method: 'DELETE' })
    }
    renderSystemLogs()
    toast('系统日志已清空')
  } catch (error) {
    toast(`系统日志清空失败：${error.message}`)
  }
}

function pronunciationUrl(text, type = 'us') {
  const encoded = encodeURIComponent(text)
  return `https://dict.youdao.com/dictvoice?audio=${encoded}&type=${type === 'uk' ? 1 : 2}`
}

function playRemoteAudio(content, fallback) {
  playRemoteAudioByType(content, elements.voiceSelect.value, fallback)
}

function playRemoteAudioByType(content, voiceType = 'us', fallback) {
  const audio = new Audio(pronunciationUrl(content, voiceType))
  audio.preload = 'auto'
  audio.play()
    .then(() => toast('正在播放发音'))
    .catch(() => {
      if (fallback) {
        fallback()
        return
      }
      toast('浏览器阻止了音频播放，请检查站点声音权限')
    })
}

function speak(text, voiceType = elements.voiceSelect.value) {
  const content = String(text || '').trim()
  if (!content) {
    toast('暂无可播放内容')
    return
  }
  if ('speechSynthesis' in window && 'SpeechSynthesisUtterance' in window) {
    playRemoteAudioByType(content, voiceType, () => speakWithBrowserVoice(content, voiceType))
    return
  }
  playRemoteAudioByType(content, voiceType)
}

function speakWithBrowserVoice(content, voiceType = elements.voiceSelect.value) {
  if (!('speechSynthesis' in window && 'SpeechSynthesisUtterance' in window)) return
  window.speechSynthesis.cancel()
  const utterance = new SpeechSynthesisUtterance(content)
  utterance.lang = voiceType === 'uk' ? 'en-GB' : 'en-US'
  utterance.rate = 0.86
  utterance.pitch = 1
  utterance.onerror = () => toast('浏览器阻止了音频播放，请检查站点声音权限')
  window.speechSynthesis.speak(utterance)
  toast('正在播放发音')
}

function firstExample(parsed) {
  return Array.isArray(parsed?.examples) && parsed.examples.length > 0 ? parsed.examples[0].sentence : ''
}

function currentWordbookName(wordbookId = state.currentWordbookId) {
  return state.wordbooks.find((item) => sameId(item.id, wordbookId))?.name || '所选词书'
}

function previewParsed(term = 'abandon') {
  return {
    term,
    is_valid: true,
    language: 'en',
    phonetic: {
      uk: '/əˈbændən/',
      us: '/əˈbændən/',
    },
    definitions: [
      {
        part_of_speech: 'verb',
        meaning: '抛弃，遗弃',
        english: 'To leave someone or something permanently.',
      },
      {
        part_of_speech: 'verb',
        meaning: '放弃计划或想法',
        english: 'To stop doing or planning something.',
      },
    ],
    examples: [
      {
        sentence: 'They had to abandon the project due to lack of funds.',
        translation: '由于缺乏资金，他们不得不放弃这个项目。',
      },
      {
        sentence: 'The old house was abandoned for years.',
        translation: '那栋老房子被废弃了很多年。',
      },
    ],
    collocations: [
      { phrase: 'abandon a plan', meaning: '放弃计划' },
      { phrase: 'abandon hope', meaning: '放弃希望' },
      { phrase: 'with abandon', meaning: '放纵地，尽情地' },
    ],
    synonyms: [
      { word: 'desert', part_of_speech: 'verb', meaning: '遗弃，离弃' },
      { word: 'forsake', part_of_speech: 'verb', meaning: '抛弃，舍弃' },
    ],
    antonyms: [
      { word: 'retain', part_of_speech: 'verb', meaning: '保留，保持' },
      { word: 'maintain', part_of_speech: 'verb', meaning: '维持，坚持' },
    ],
    word_family: [
      { word: 'abandonment', part_of_speech: 'noun', meaning: '遗弃，放弃' },
      { word: 'abandoned', part_of_speech: 'adjective', meaning: '被遗弃的' },
    ],
    memory_tips: '把 abandon 想成“放开控制”，引申为放弃、抛弃。',
  }
}

function previewRecord(term = 'abandon') {
  const parsed = previewParsed(term)
  return {
    id: 1,
    term,
    normalizedTerm: term.toLowerCase(),
    cacheHit: true,
    provider: 'preview',
    modelName: 'design-preview',
    sessionId: 1,
    parsed,
    rawContent: JSON.stringify(parsed, null, 2),
    lookupCount: 3,
    tags: [
      { tagType: 'part_of_speech', displayName: 'verb' },
      { tagType: 'meaning_topic', displayName: '放弃' },
      { tagType: 'difficulty', displayName: 'medium' },
      { tagType: 'collocation', displayName: 'abandon a plan' },
    ],
    relations: [
      { relatedTerm: 'desert', relationType: 'synonym', relatedPartOfSpeech: 'verb', relatedMeaning: '遗弃，离弃', matchType: 'parsed_object', matchScore: 92 },
      { relatedTerm: 'retain', relationType: 'antonym', relatedPartOfSpeech: 'verb', relatedMeaning: '保留，保持', matchType: 'parsed_object', matchScore: 82 },
      { relatedTerm: 'abandonment', relationType: 'word_family', relatedPartOfSpeech: 'noun', relatedMeaning: '遗弃，放弃', matchType: 'parsed_object', matchScore: 78 },
    ],
  }
}

function createPreviewActivity() {
  const days = 180
  const items = Array.from({ length: days }, (_, index) => {
    const date = new Date()
    date.setDate(date.getDate() - (days - index - 1))
    const pulse = index % 9 === 0 ? 3 : index % 5 === 0 ? 2 : index % 3 === 0 ? 1 : 0
    const learnedCount = index % 11 === 0 ? 2 : pulse > 1 ? 1 : 0
    const reviewCount = pulse
    return {
      date: date.toISOString().slice(0, 10),
      learnedCount,
      reviewCount,
      totalCount: learnedCount + reviewCount,
    }
  })
  return {
    days,
    learnedTotal: items.reduce((sum, item) => sum + item.learnedCount, 0),
    reviewTotal: items.reduce((sum, item) => sum + item.reviewCount, 0),
    items,
  }
}

function daysAgoIso(days) {
  const date = new Date()
  date.setDate(date.getDate() - days)
  return date.toISOString()
}

async function showBestMatch(term) {
  if (state.preview) return false
  try {
    const match = await request(`/api/v1/english/vocabularies/${encodeURIComponent(term)}/best-match`)
    if (!match?.record) return false
    renderRecord(match.record)
    logEvent('cache', '已展示最匹配单词', `${term} -> ${match.normalizedTerm} · ${match.matchScore}`)
    toast(`未直接命中，已展示最匹配：${match.normalizedTerm}`)
    return true
  } catch (error) {
    logEvent('error', '最匹配单词查询失败', error.message)
    return false
  }
}

function tagLabel(tag) {
  const typeMap = {
    part_of_speech: '词性',
    meaning_topic: '含义',
    difficulty: '难度',
    collocation: '搭配',
    word_family: '词族',
  }
  const type = typeMap[tag?.tagType] || tag?.tagType || '标签'
  return `${type}: ${tag?.displayName || tag?.tagValue || ''}`
}

function relationTypeLabel(type) {
  return (
    {
      synonym: '同义',
      antonym: '反义',
      word_family: '词族',
      collocation: '搭配',
      tag_overlap: '相近',
    }[type] || type || '相关'
  )
}

function relationMeaningLine(item) {
  const pieces = [item.relatedPartOfSpeech, item.relatedMeaning || item.relationValue].filter(Boolean)
  return pieces.length ? pieces.join(' · ') : '暂无核心含义'
}

function relationMetaLine(item) {
  const pieces = [relationTypeLabel(item.relationType)]
  if (item.matchScore != null) {
    pieces.push(`${item.matchScore}`)
  }
  return pieces.join(' · ')
}

function statusLabel(status) {
  return (
    {
      familiar: '熟悉',
      forgotten: '遗忘',
      vague: '模糊',
    }[status] || '模糊'
  )
}

function reviewResultToStatus(result) {
  return (
    {
      remembered: 'familiar',
      forgotten: 'forgotten',
      vague: 'vague',
    }[result] || 'vague'
  )
}

function logTypeLabel(type) {
  return (
    {
      auth: '账户',
      ai: 'AI',
      cache: '缓存',
      chat: '追问',
      error: '错误',
      navigation: '导航',
      review: '复习',
      wordbook: '词书',
    }[type] || '系统'
  )
}

function normalizeDefinitions(parsed) {
  const source = parsed?.definitions || parsed?.meanings || parsed?.translations || parsed?.definition || []
  const list = normalizeArray(source)
  return list
    .map((item) => {
      if (typeof item === 'string') return { pos: 'meaning', cn: item, en: '' }
      const pos = readText(item, ['part_of_speech', 'partOfSpeech', 'pos', 'type', 'word_class'])
      const cn = readText(item, ['meaning_cn', 'meaningCn', 'chinese', 'translation', 'translation_cn', 'cn', 'meaning', 'definition_cn', 'definitionCn'])
      const en = readText(item, ['meaning_en', 'meaningEn', 'english', 'definition', 'definition_en', 'definitionEn', 'en'])
      const extra = fallbackObjectText(item, [
        'part_of_speech',
        'partOfSpeech',
        'pos',
        'type',
        'word_class',
        'meaning_cn',
        'meaningCn',
        'chinese',
        'translation',
        'translation_cn',
        'cn',
        'meaning',
        'definition_cn',
        'definitionCn',
        'meaning_en',
        'meaningEn',
        'english',
        'definition',
        'definition_en',
        'definitionEn',
        'en',
        'frequency',
      ])
      return { pos: pos || 'meaning', cn: cn || extra, en, extra }
    })
    .filter((item) => item.cn || item.en || item.extra)
}

function normalizeExamples(parsed) {
  const examples = normalizeArray(parsed?.examples || parsed?.sentences || parsed?.example_sentences)
  return examples
    .map((item) => {
      if (typeof item === 'string') return { sentence: item, translation: '' }
      return {
        sentence: readText(item, ['sentence', 'example', 'text', 'en', 'english']),
        translation: readText(item, ['translation_cn', 'translationCn', 'translation', 'cn', 'chinese', 'meaning']),
      }
    })
    .filter((item) => item.sentence || item.translation)
}

function normalizeArray(value) {
  if (!value) return []
  if (Array.isArray(value)) return value
  if (typeof value === 'object') return Object.values(value)
  return [value]
}

function readText(source, keys) {
  if (!source || typeof source !== 'object') return ''
  for (const key of keys) {
    const value = source[key]
    const text = stringifyValue(value)
    if (text) return text
  }
  return ''
}

function stringifyValue(value) {
  if (value == null) return ''
  if (Array.isArray(value)) return value.map(stringifyValue).filter(Boolean).join('；')
  if (typeof value === 'object') {
    return Object.entries(value)
      .map(([key, item]) => {
        const text = stringifyValue(item)
        return text ? `${key}: ${text}` : ''
      })
      .filter(Boolean)
      .join('；')
  }
  return String(value).trim()
}

function fallbackObjectText(source, usedKeys) {
  if (!source || typeof source !== 'object') return ''
  return Object.entries(source)
    .filter(([key]) => !usedKeys.includes(key))
    .map(([key, value]) => {
      const text = stringifyValue(value)
      return text ? `${key}: ${text}` : ''
    })
    .filter(Boolean)
    .join('；')
}

function renderMarkdown(markdown) {
  const source = String(markdown || '').trim()
  if (!source) return ''
  const lines = source.split(/\r?\n/)
  const html = []
  let listOpen = false
  for (const line of lines) {
    const text = line.trim()
    if (!text) {
      if (listOpen) {
        html.push('</ul>')
        listOpen = false
      }
      continue
    }
    if (text.startsWith('## ')) {
      if (listOpen) {
        html.push('</ul>')
        listOpen = false
      }
      html.push(`<h4>${inlineMarkdown(text.slice(3))}</h4>`)
    } else if (text.startsWith('# ')) {
      if (listOpen) {
        html.push('</ul>')
        listOpen = false
      }
      html.push(`<h3>${inlineMarkdown(text.slice(2))}</h3>`)
    } else if (text.startsWith('- ')) {
      if (!listOpen) {
        html.push('<ul>')
        listOpen = true
      }
      html.push(`<li>${inlineMarkdown(text.slice(2))}</li>`)
    } else {
      if (listOpen) {
        html.push('</ul>')
        listOpen = false
      }
      html.push(`<p>${inlineMarkdown(text)}</p>`)
    }
  }
  if (listOpen) html.push('</ul>')
  return html.join('')
}

function inlineMarkdown(text) {
  return escapeHtml(text)
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
}

function formatDateTime(value) {
  if (!value) return '待定'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

function readJsonStorage(key) {
  try {
    return JSON.parse(localStorage.getItem(key) || 'null')
  } catch {
    return null
  }
}

function readErrorMessage(text) {
  if (!text) return ''
  try {
    const payload = JSON.parse(text)
    return payload.message || payload.error || text
  } catch {
    return text
  }
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')
}

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function sameId(left, right) {
  return String(left ?? '') === String(right ?? '')
}

elements.loginBtn.addEventListener('click', () => loginOrRegister('login'))
elements.registerBtn.addEventListener('click', () => loginOrRegister('register'))
elements.logoutBtn.addEventListener('click', logout)
if (elements.apiBaseInput) {
  elements.apiBaseInput.addEventListener('change', () => {
    state.apiBase = elements.apiBaseInput.value.trim() || 'http://localhost:16681'
    localStorage.setItem('learning.apiBase', state.apiBase)
    loadAgents()
    loadModelConfigs()
    loadPromptTemplates()
  })
}
elements.toggleSidebarBtn.addEventListener('click', toggleSidebar)
elements.sidebarBackdrop.addEventListener('click', () => setSidebarCollapsed(true))
window.matchMedia('(max-width: 1100px)').addEventListener('change', handleViewportChange)
elements.reloadAgentsBtn.addEventListener('click', loadAgents)
elements.reloadModelsBtn.addEventListener('click', loadModelConfigs)
elements.openModelModalBtn.addEventListener('click', () => openModelModal())
elements.closeModelModalBtn.addEventListener('click', closeModelModal)
elements.modelConfigModal.addEventListener('click', (event) => {
  if (event.target === elements.modelConfigModal) closeModelModal()
})
elements.modelProviderInput.addEventListener('change', () => syncModelProviderDefaults())
elements.modelDefaultToggleBtn?.addEventListener('click', () => toggleModelFlag(elements.modelDefaultInput))
elements.modelEnabledToggleBtn?.addEventListener('click', () => toggleModelFlag(elements.modelEnabledInput))
elements.openAccountModalBtn.addEventListener('click', openAccountModal)
elements.closeAccountModalBtn.addEventListener('click', closeAccountModal)
elements.accountModal.addEventListener('click', (event) => {
  if (event.target === elements.accountModal) closeAccountModal()
})
elements.saveAccountBtn.addEventListener('click', saveAccountProfile)
elements.reloadWordbookEntriesBtn.addEventListener('click', loadWordbookEntries)
elements.reloadWordbookViewBtn.addEventListener('click', loadWordbookEntries)
elements.openWordbookModalBtn.addEventListener('click', () => openWordbookModal())
elements.closeWordbookModalBtn.addEventListener('click', closeWordbookModal)
elements.wordbookModal.addEventListener('click', (event) => {
  if (event.target === elements.wordbookModal) closeWordbookModal()
})
elements.createWordbookBtn.addEventListener('click', createWordbook)
elements.saveModelBtn.addEventListener('click', saveModelConfig)
if (elements.resetModelFormBtn) {
  elements.resetModelFormBtn.addEventListener('click', () => resetModelForm({ keepModalOpen: true }))
}
elements.agentSelect.addEventListener('change', changeLearningAgent)
elements.templateSelect.addEventListener('change', renderSelectedTemplate)
elements.templateContentInput.addEventListener('input', () => validateTemplatePlaceholders({ quiet: true }))
elements.saveTemplateBtn.addEventListener('click', savePromptTemplate)
elements.wordbookSelect.addEventListener('change', () => changeWordbook(elements.wordbookSelect.value))
elements.wordStatusFilter.addEventListener('change', loadWordbookEntries)
elements.reloadSystemLogsBtn.addEventListener('click', loadSystemLogs)
elements.clearLogBtn.addEventListener('click', clearLogs)
elements.studyForm.addEventListener('submit', (event) => {
  event.preventDefault()
  study(elements.termInput.value)
})
elements.studyRegenerateBtn?.addEventListener('click', regenerateStudyCard)
elements.addToWordbookBtn.addEventListener('click', addCurrentWordToWordbook)
elements.closeAddWordbookModalBtn.addEventListener('click', closeAddWordbookModal)
elements.addWordbookModal.addEventListener('click', (event) => {
  if (event.target === elements.addWordbookModal) closeAddWordbookModal()
})
elements.closeEntryStatusModalBtn.addEventListener('click', closeEntryStatusModal)
elements.entryStatusModal.addEventListener('click', (event) => {
  if (event.target === elements.entryStatusModal) closeEntryStatusModal()
})
elements.entryStatusModal.querySelectorAll('[data-status-choice]').forEach((button) => {
  button.addEventListener('click', () => chooseEntryStatus(button.getAttribute('data-status-choice')))
})
elements.speakWordBtn.addEventListener('click', () => speak(state.currentRecord?.normalizedTerm || elements.termInput.value))
elements.speakSentenceBtn.addEventListener('click', () => speak(firstExample(state.currentRecord?.parsed)))
elements.editStudyNoteBtn.addEventListener('click', editCurrentNote)
elements.editReviewNoteBtn.addEventListener('click', editCurrentNote)
elements.chatBtn.addEventListener('click', chat)
elements.reloadReviewBtn.addEventListener('click', loadDueReviews)
elements.reviewWordbookSelect.addEventListener('change', () => {
  state.currentWordbookId = elements.reviewWordbookSelect.value
  if (state.currentWordbookId) localStorage.setItem('learning.wordbookId', state.currentWordbookId)
})
elements.closeReviewModalBtn.addEventListener('click', closeReviewModal)
elements.reviewCompleteModal.addEventListener('click', (event) => {
  if (event.target === elements.reviewCompleteModal) closeReviewModal()
})
elements.reviewCompleteModal.querySelectorAll('[data-modal-result]').forEach((button) => {
  button.addEventListener('click', () => {
    const entryId = state.pendingReviewEntryId || state.currentReviewEntry?.id
    if (!entryId) return
    submitReview(entryId, button.getAttribute('data-modal-result'))
  })
})
elements.closeForgottenDetailModalBtn.addEventListener('click', closeForgottenDetailModal)
elements.forgottenBackToReviewBtn.addEventListener('click', closeForgottenDetailModal)
elements.forgottenDetailModal.addEventListener('click', (event) => {
  if (event.target === elements.forgottenDetailModal) closeForgottenDetailModal()
})
elements.deleteConfirmCloseBtn?.addEventListener('click', () => closeDeleteConfirm(false))
elements.deleteConfirmCancelBtn?.addEventListener('click', () => closeDeleteConfirm(false))
elements.deleteConfirmAcceptBtn?.addEventListener('click', () => closeDeleteConfirm(true))
elements.deleteConfirmModal?.addEventListener('click', (event) => {
  if (event.target === elements.deleteConfirmModal) closeDeleteConfirm(false)
})
document.querySelectorAll('.nav-item').forEach((button) => {
  button.addEventListener('click', () => setView(button.dataset.view))
})
document.querySelectorAll('.profile-tab').forEach((button) => {
  button.addEventListener('click', () => setProfileTab(button.dataset.profileTab))
})
document.addEventListener('keydown', handleReviewKeydown)

window.renderRecord = renderRecord
window.learningAssistant = { renderRecord, renderReviewCompleteModal, speak, setView, setProfileTab, state }

updateAuthView()
syncSidebarState()
setProfileTab(state.activeProfileTab)
renderSystemLogs()
renderRawJson(null)
renderProviderOptions()
syncModelProviderDefaults()
loadAgents()
loadModelConfigs()
loadPromptTemplates()
if (state.token || state.preview) {
  loadInitialData()
}
