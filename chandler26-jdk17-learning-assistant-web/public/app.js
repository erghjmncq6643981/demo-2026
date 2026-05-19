const state = {
  build: '20260519-3',
  apiBase: localStorage.getItem('learning.apiBase') || 'http://localhost:16681',
  token: localStorage.getItem('learning.token') || '',
  user: readJsonStorage('learning.user'),
  preview: new URLSearchParams(window.location.search).get('preview') === '1',
  activeView: localStorage.getItem('learning.activeView') || 'profileView',
  wordbooks: [],
  wordbookEntries: [],
  reviewEntries: [],
  modelConfigs: [],
  currentWordbookId: Number(localStorage.getItem('learning.wordbookId')) || null,
  currentWordbookEditId: null,
  currentModelEditId: null,
  selectedEntry: null,
  currentNoteEntry: null,
  currentRecord: null,
  currentSessionId: null,
  systemLogs: readJsonStorage('learning.systemLogs') || [],
}

const $ = (id) => document.getElementById(id)

const elements = {
  loginScreen: $('loginScreen'),
  productShell: $('productShell'),
  loginConnectionStatus: $('loginConnectionStatus'),
  connectionStatus: $('connectionStatus'),
  apiBaseInput: $('apiBaseInput'),
  usernameInput: $('usernameInput'),
  passwordInput: $('passwordInput'),
  nicknameInput: $('nicknameInput'),
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
  viewEyebrow: $('viewEyebrow'),
  viewTitle: $('viewTitle'),
  wordbookSelect: $('wordbookSelect'),
  reloadWordbooksBtn: $('reloadWordbooksBtn'),
  reloadWordbookEntriesBtn: $('reloadWordbookEntriesBtn'),
  newWordbookInput: $('newWordbookInput'),
  wordbookDescriptionInput: $('wordbookDescriptionInput'),
  wordbookDefaultInput: $('wordbookDefaultInput'),
  createWordbookBtn: $('createWordbookBtn'),
  resetWordbookFormBtn: $('resetWordbookFormBtn'),
  wordbookCards: $('wordbookCards'),
  wordbookEntryList: $('wordbookEntryList'),
  wordStatusFilter: $('wordStatusFilter'),
  reloadWordbookViewBtn: $('reloadWordbookViewBtn'),
  wordbookFocus: $('wordbookFocus'),
  reloadModelsBtn: $('reloadModelsBtn'),
  modelNameInput: $('modelNameInput'),
  modelProviderInput: $('modelProviderInput'),
  modelModelNameInput: $('modelModelNameInput'),
  modelBaseUrlInput: $('modelBaseUrlInput'),
  modelChatPathInput: $('modelChatPathInput'),
  modelApiKeyInput: $('modelApiKeyInput'),
  modelSequenceInput: $('modelSequenceInput'),
  modelDefaultInput: $('modelDefaultInput'),
  modelEnabledInput: $('modelEnabledInput'),
  saveModelBtn: $('saveModelBtn'),
  resetModelFormBtn: $('resetModelFormBtn'),
  modelConfigList: $('modelConfigList'),
  systemLogList: $('systemLogList'),
  clearLogBtn: $('clearLogBtn'),
  rawJson: $('rawJson'),
  reloadAgentsBtn: $('reloadAgentsBtn'),
  studyForm: $('studyForm'),
  termInput: $('termInput'),
  studyBtn: $('studyBtn'),
  studyModelSelect: $('studyModelSelect'),
  cacheState: $('cacheState'),
  wordTitle: $('wordTitle'),
  phoneticLine: $('phoneticLine'),
  addToWordbookBtn: $('addToWordbookBtn'),
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
  voiceSelect: $('voiceSelect'),
  forceRefreshInput: $('forceRefreshInput'),
  chatInput: $('chatInput'),
  chatBtn: $('chatBtn'),
  reloadReviewBtn: $('reloadReviewBtn'),
  reviewQueue: $('reviewQueue'),
  reviewFocus: $('reviewFocus'),
  editReviewNoteBtn: $('editReviewNoteBtn'),
  reviewNote: $('reviewNote'),
  toast: $('toast'),
}

const viewMeta = {
  profileView: ['Profile', '个人信息'],
  wordbookView: ['Wordbook', '单词本'],
  studyView: ['Study', '英语学习'],
  reviewView: ['Review', '复习计划'],
}

elements.apiBaseInput.value = state.apiBase
elements.buildVersion.textContent = `build ${state.build}`

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
    elements.chatBtn,
    elements.addToWordbookBtn,
    elements.createWordbookBtn,
    elements.saveModelBtn,
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

function logEvent(type, title, detail = '') {
  const entry = {
    type,
    title,
    detail,
    time: new Date().toISOString(),
  }
  state.systemLogs = [entry, ...state.systemLogs].slice(0, 60)
  localStorage.setItem('learning.systemLogs', JSON.stringify(state.systemLogs))
  renderSystemLogs()
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
}

function setConnection(ok) {
  for (const item of [elements.connectionStatus, elements.loginConnectionStatus]) {
    item.classList.toggle('ok', ok)
    item.classList.toggle('bad', !ok)
    item.textContent = ok ? '后端已连接' : '后端未连接'
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
  if (viewId === 'reviewView') loadDueReviews()
  if (viewId === 'wordbookView') loadWordbookEntries()
}

async function loginOrRegister(mode) {
  const username = elements.usernameInput.value.trim()
  const password = elements.passwordInput.value
  const nickname = elements.nicknameInput.value.trim()
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
  state.modelConfigs = []
  state.currentWordbookId = null
  state.currentWordbookEditId = null
  state.currentModelEditId = null
  state.selectedEntry = null
  state.currentNoteEntry = null
  state.currentRecord = null
  localStorage.removeItem('learning.token')
  localStorage.removeItem('learning.user')
  localStorage.removeItem('learning.wordbookId')
  renderWordbooks()
  renderWordbookEntries()
  renderModelConfigs()
  renderReviewQueue([])
  renderReviewFocus(null)
  renderNotes(null)
  updateAuthView()
  logEvent('auth', '退出登录')
  toast('已退出登录')
}

async function loadInitialData() {
  if (state.preview) {
    loadPreviewData()
    return
  }
  await Promise.allSettled([loadAgents(), loadWordbooks(), loadModelConfigs()])
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
  state.currentWordbookId = state.currentWordbookId || 1
  state.wordbookEntries = [
    { id: 11, term: 'abandon', normalizedTerm: 'abandon', status: 'vague', note: '## 记忆\n- abandon a plan\n- with abandon', reviewStage: 2, masteryScore: 45, nextReviewTime: new Date().toISOString(), parsed: previewParsed('abandon') },
    { id: 12, term: 'maintain', normalizedTerm: 'maintain', status: 'familiar', note: '常和 **relationship/status** 搭配。', reviewStage: 4, masteryScore: 72, nextReviewTime: new Date(Date.now() + 86400000).toISOString(), parsed: previewParsed('maintain') },
    { id: 13, term: 'contrast', normalizedTerm: 'contrast', status: 'forgotten', note: '', reviewStage: 1, masteryScore: 30, nextReviewTime: new Date().toISOString(), parsed: previewParsed('contrast') },
  ]
  state.reviewEntries = state.wordbookEntries.slice(0, 2).map((entry) => ({
    ...entry,
    parsed: previewParsed(entry.term),
  }))
  updateAuthView()
  renderModelConfigs()
  renderWordbooks()
  renderWordbookEntries()
  renderReviewQueue(state.reviewEntries)
  renderRecord(previewRecord())
  logEvent('system', '设计预览模式', '使用 ?preview=1 查看无后端登录后的产品界面')
}

async function loadAgents() {
  if (state.preview) {
    elements.agentSelect.innerHTML = '<option value="english_vocabulary">English Vocabulary (english_vocabulary)</option>'
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
    setConnection(true)
  } catch (error) {
    setConnection(false)
    elements.agentSelect.innerHTML = '<option value="english_vocabulary">English Vocabulary</option>'
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
    option.value = item.id
    option.textContent = `${item.name} · ${item.modelName}${item.isDefault ? ' · 默认' : ''}`
    elements.studyModelSelect.appendChild(option)
  }
  const preferred = enabled.find((item) => item.isDefault) || enabled[0]
  elements.studyModelSelect.value = String(preferred.id)
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
            <strong>${escapeHtml(item.name)}</strong>
            <p>${escapeHtml(item.provider)} · ${escapeHtml(item.modelName)} · ${escapeHtml(item.baseUrl || '')}</p>
            <small>${item.isDefault ? '默认 · ' : ''}优先级 ${item.sequence ?? 0} · ${escapeHtml(item.apiKeyMasked || '')}</small>
          </div>
          <div class="row-actions">
            <button type="button" data-model-edit="${item.id}">编辑</button>
            <button type="button" data-model-toggle="${item.id}">${item.enabled ? '禁用' : '启用'}</button>
            <button type="button" data-model-default="${item.id}">默认</button>
          </div>
        </div>
      `,
    )
    .join('')
  elements.modelConfigList.querySelectorAll('[data-model-edit]').forEach((button) => {
    button.addEventListener('click', () => editModelConfig(Number(button.getAttribute('data-model-edit'))))
  })
  elements.modelConfigList.querySelectorAll('[data-model-toggle]').forEach((button) => {
    button.addEventListener('click', () => toggleModelConfig(Number(button.getAttribute('data-model-toggle'))))
  })
  elements.modelConfigList.querySelectorAll('[data-model-default]').forEach((button) => {
    button.addEventListener('click', () => setDefaultModelConfig(Number(button.getAttribute('data-model-default'))))
  })
}

function editModelConfig(id) {
  const item = state.modelConfigs.find((model) => Number(model.id) === Number(id))
  if (!item) return
  state.currentModelEditId = item.id
  elements.modelNameInput.value = item.name || ''
  elements.modelProviderInput.value = item.provider || ''
  elements.modelModelNameInput.value = item.modelName || ''
  elements.modelBaseUrlInput.value = item.baseUrl || ''
  elements.modelChatPathInput.value = item.chatPath || '/chat/completions'
  elements.modelApiKeyInput.value = ''
  elements.modelApiKeyInput.placeholder = item.apiKeyMasked || 'API KEY'
  elements.modelSequenceInput.value = item.sequence ?? 0
  elements.modelDefaultInput.checked = Boolean(item.isDefault)
  elements.modelEnabledInput.checked = Boolean(item.enabled)
}

function resetModelForm() {
  state.currentModelEditId = null
  elements.modelNameInput.value = ''
  elements.modelProviderInput.value = ''
  elements.modelModelNameInput.value = ''
  elements.modelBaseUrlInput.value = ''
  elements.modelChatPathInput.value = '/chat/completions'
  elements.modelApiKeyInput.value = ''
  elements.modelApiKeyInput.placeholder = 'API KEY'
  elements.modelSequenceInput.value = '0'
  elements.modelDefaultInput.checked = false
  elements.modelEnabledInput.checked = true
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
  setLoading(true)
  try {
    if (state.preview) {
      const id = state.currentModelEditId || Date.now()
      const existingIndex = state.modelConfigs.findIndex((item) => Number(item.id) === Number(id))
      const next = { ...payload, id, apiKeyMasked: payload.apiKey ? `${payload.apiKey.slice(0, 6)}****` : 'sk-****' }
      if (next.isDefault) state.modelConfigs.forEach((item) => (item.isDefault = false))
      if (existingIndex >= 0) state.modelConfigs.splice(existingIndex, 1, next)
      else state.modelConfigs.push(next)
      renderModelConfigs()
      resetModelForm()
      toast('设计预览：模型配置已保存')
      return
    }
    const path = state.currentModelEditId ? `/api/v1/ai/model-configs/${state.currentModelEditId}` : '/api/v1/ai/model-configs'
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
  const item = state.modelConfigs.find((model) => Number(model.id) === Number(id))
  if (!item) return
  if (state.preview) {
    item.enabled = !item.enabled
    renderModelConfigs()
    return
  }
  await request(`/api/v1/ai/model-configs/${id}/${item.enabled ? 'disable' : 'enable'}`, { method: 'POST' })
  await loadModelConfigs()
}

async function setDefaultModelConfig(id) {
  const item = state.modelConfigs.find((model) => Number(model.id) === Number(id))
  if (!item) return
  if (state.preview) {
    state.modelConfigs.forEach((model) => (model.isDefault = Number(model.id) === Number(id)))
    renderModelConfigs()
    return
  }
  await request(`/api/v1/ai/model-configs/${id}/priority`, {
    method: 'POST',
    body: JSON.stringify({ sequence: item.sequence ?? 0, isDefault: true }),
  })
  await loadModelConfigs()
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
  if (!state.wordbooks.length) {
    elements.wordbookSelect.innerHTML = '<option value="">暂无词书</option>'
    elements.wordbookCards.className = 'wordbook-cards empty'
    elements.wordbookCards.textContent = state.token ? '暂无词书' : '登录后查看词书'
    renderProfileMetrics()
    return
  }

  const hasSelected = state.wordbooks.some((item) => Number(item.id) === Number(state.currentWordbookId))
  const fallback = state.wordbooks.find((item) => item.isDefault) || state.wordbooks[0]
  state.currentWordbookId = hasSelected ? state.currentWordbookId : fallback.id
  localStorage.setItem('learning.wordbookId', String(state.currentWordbookId))

  for (const wordbook of state.wordbooks) {
    const option = document.createElement('option')
    option.value = wordbook.id
    option.textContent = `${wordbook.name} · ${wordbook.entryCount || 0}词 · ${wordbook.dueCount || 0}待复习`
    elements.wordbookSelect.appendChild(option)
  }
  elements.wordbookSelect.value = String(state.currentWordbookId)

  elements.wordbookCards.className = 'wordbook-cards'
  elements.wordbookCards.innerHTML = state.wordbooks
    .map(
      (item) => `
        <button class="wordbook-card ${Number(item.id) === Number(state.currentWordbookId) ? 'active' : ''}" type="button" data-wordbook-id="${item.id}">
          <strong>${escapeHtml(item.name)}</strong>
          <span>${escapeHtml(item.description || (item.isDefault ? '默认词书' : '自定义词书'))}</span>
          <small>${item.entryCount || 0} 个单词 · ${item.dueCount || 0} 个待复习</small>
          <em data-wordbook-edit="${item.id}">编辑</em>
        </button>
      `,
    )
    .join('')
  elements.wordbookCards.querySelectorAll('[data-wordbook-id]').forEach((button) => {
    button.addEventListener('click', () => changeWordbook(Number(button.getAttribute('data-wordbook-id'))))
  })
  elements.wordbookCards.querySelectorAll('[data-wordbook-edit]').forEach((button) => {
    button.addEventListener('click', (event) => {
      event.stopPropagation()
      editWordbook(Number(button.getAttribute('data-wordbook-edit')))
    })
  })
  renderProfileMetrics()
}

async function changeWordbook(wordbookId) {
  state.currentWordbookId = wordbookId
  elements.wordbookSelect.value = String(wordbookId)
  localStorage.setItem('learning.wordbookId', String(wordbookId))
  renderWordbooks()
  await Promise.allSettled([loadWordbookEntries(), loadDueReviews()])
  logEvent('wordbook', '切换词书', currentWordbookName())
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
      const id = state.currentWordbookEditId || Date.now()
      if (payload.isDefault) state.wordbooks.forEach((item) => (item.isDefault = false))
      const index = state.wordbooks.findIndex((item) => Number(item.id) === Number(id))
      const next = { id, ...payload, entryCount: 0, dueCount: 0 }
      if (index >= 0) state.wordbooks.splice(index, 1, { ...state.wordbooks[index], ...next })
      else state.wordbooks.push(next)
      state.currentWordbookId = id
      resetWordbookForm()
      renderWordbooks()
      toast('设计预览：词表已保存')
      return
    }
    const path = state.currentWordbookEditId ? `/api/v1/learning/wordbooks/${state.currentWordbookEditId}` : '/api/v1/learning/wordbooks'
    const method = state.currentWordbookEditId ? 'PUT' : 'POST'
    const wordbook = await request(path, { method, body: JSON.stringify(payload) })
    resetWordbookForm()
    state.currentWordbookId = wordbook.id
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

function editWordbook(id) {
  const wordbook = state.wordbooks.find((item) => Number(item.id) === Number(id))
  if (!wordbook) return
  state.currentWordbookEditId = wordbook.id
  elements.newWordbookInput.value = wordbook.name || ''
  elements.wordbookDescriptionInput.value = wordbook.description || ''
  elements.wordbookDefaultInput.checked = Boolean(wordbook.isDefault)
}

function resetWordbookForm() {
  state.currentWordbookEditId = null
  elements.newWordbookInput.value = ''
  elements.wordbookDescriptionInput.value = ''
  elements.wordbookDefaultInput.checked = false
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
    const entries = await request(`/api/v1/learning/wordbooks/${state.currentWordbookId}/entries${query}`)
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
  const selectedEntry = state.selectedEntry && entries.some((entry) => Number(entry.id) === Number(state.selectedEntry.id)) ? state.selectedEntry : entries[0]
  state.selectedEntry = selectedEntry
  elements.wordbookEntryList.className = 'entry-list'
  elements.wordbookEntryList.innerHTML = entries
    .map(
      (entry) => `
        <div class="entry-row ${Number(selectedEntry.id) === Number(entry.id) ? 'active' : ''}">
          <button type="button" data-entry-id="${entry.id}">
            <span>${escapeHtml(entry.term || entry.normalizedTerm)}</span>
            <small>${escapeHtml(statusLabel(entry.status))} · 阶段 ${entry.reviewStage ?? 0} · 掌握 ${entry.masteryScore ?? 0} · 下次 ${escapeHtml(formatDateTime(entry.nextReviewTime))}</small>
          </button>
          <div class="row-actions">
            <button type="button" data-entry-status="familiar" data-entry-update="${entry.id}">熟悉</button>
            <button type="button" data-entry-status="vague" data-entry-update="${entry.id}">模糊</button>
            <button type="button" data-entry-status="forgotten" data-entry-update="${entry.id}">遗忘</button>
            <button type="button" data-entry-delete="${entry.id}">删除</button>
          </div>
        </div>
      `,
    )
    .join('')
  elements.wordbookEntryList.querySelectorAll('[data-entry-id]').forEach((button) => {
    button.addEventListener('click', () => {
      const entry = state.wordbookEntries.find((item) => Number(item.id) === Number(button.getAttribute('data-entry-id')))
      selectWordbookEntry(entry)
    })
  })
  elements.wordbookEntryList.querySelectorAll('[data-entry-update]').forEach((button) => {
    button.addEventListener('click', () => updateEntryStatus(Number(button.getAttribute('data-entry-update')), button.getAttribute('data-entry-status')))
  })
  elements.wordbookEntryList.querySelectorAll('[data-entry-delete]').forEach((button) => {
    button.addEventListener('click', () => deleteWordbookEntry(Number(button.getAttribute('data-entry-delete'))))
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
  const definitions = normalizeDefinitions(entry.parsed).slice(0, 2)
  elements.wordbookFocus.className = 'wordbook-focus-card'
  elements.wordbookFocus.innerHTML = `
    <p class="eyebrow">${escapeHtml(statusLabel(entry.status))}</p>
    <h4>${escapeHtml(entry.term || entry.normalizedTerm)}</h4>
    <div class="mini-definition-list">
      ${
        definitions.length
          ? definitions.map((item) => `<div><span>${escapeHtml(item.pos || 'meaning')}</span><p>${escapeHtml(item.cn || item.en || '')}</p></div>`).join('')
          : '<div class="empty">暂无释义</div>'
      }
    </div>
    <div class="note-view">${renderMarkdown(entry.note || '') || '<span class="empty">暂无笔记</span>'}</div>
    <button class="secondary-button compact" type="button" data-open-study="${escapeHtml(entry.normalizedTerm)}">去学习</button>
  `
  elements.wordbookFocus.querySelector('[data-open-study]')?.addEventListener('click', () => {
    elements.termInput.value = entry.normalizedTerm
    setView('studyView')
    study(entry.normalizedTerm)
  })
}

async function updateEntryStatus(entryId, status) {
  await saveEntry(entryId, { status })
}

async function deleteWordbookEntry(entryId) {
  if (state.preview) {
    state.wordbookEntries = state.wordbookEntries.filter((entry) => Number(entry.id) !== Number(entryId))
    state.reviewEntries = state.reviewEntries.filter((entry) => Number(entry.id) !== Number(entryId))
    state.selectedEntry = null
    renderWordbookEntries()
    renderReviewQueue(state.reviewEntries)
    toast('设计预览：已从词表删除')
    return
  }
  try {
    await request(`/api/v1/learning/wordbook-entries/${entryId}`, { method: 'DELETE' })
    await Promise.allSettled([loadWordbooks(), loadWordbookEntries(), loadDueReviews()])
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

async function study(term) {
  const value = String(term || '').trim()
  if (!value) {
    toast('先输入一个英语单词')
    return
  }
  setLoading(true)
  try {
    if (state.preview) {
      const record = previewRecord(value)
      renderRecord(record)
      logEvent('cache', '预览词汇卡片', record.normalizedTerm)
      toast('设计预览：已展示模拟学习卡片')
      return
    }
    const record = await request('/api/v1/english/vocabularies/study', {
      method: 'POST',
      body: JSON.stringify({
        term: value,
        agentCode: elements.agentSelect.value,
        templateCode: elements.templateSelect.value,
        modelConfigId: elements.studyModelSelect.value ? Number(elements.studyModelSelect.value) : null,
        forceRefresh: elements.forceRefreshInput.checked,
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
  if (!state.currentWordbookId) {
    toast('请选择词书')
    return
  }
  if (!term) {
    toast('先学习一个单词')
    return
  }
  setLoading(true)
  try {
    if (state.preview) {
      const existing = state.wordbookEntries.find((entry) => entry.normalizedTerm === term)
      if (!existing) {
        const entry = {
          id: Date.now(),
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
        renderWordbookEntries()
        renderNotes(entry)
      }
      logEvent('wordbook', '预览加入词表', `${term} -> ${currentWordbookName()}`)
      toast('设计预览：已模拟加入词书')
      return
    }
    const entry = await request(`/api/v1/learning/wordbooks/${state.currentWordbookId}/entries`, {
      method: 'POST',
      body: JSON.stringify({ term }),
    })
    await Promise.allSettled([loadWordbooks(), loadDueReviews()])
    await loadWordbookEntries()
    renderNotes(entry)
    logEvent('wordbook', '加入单词本', `${term} -> ${currentWordbookName()}`)
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
        modelConfigId: elements.studyModelSelect.value ? Number(elements.studyModelSelect.value) : null,
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
    logEvent('chat', 'AI 追问回复', message)
    toast('AI 已回复，完整内容进入个人信息的系统日志')
  } catch (error) {
    logEvent('error', '追问失败', error.message)
    toast(`追问失败：${error.message}`)
  } finally {
    setLoading(false)
  }
}

async function loadDueReviews() {
  if (state.preview) {
    renderReviewQueue(state.reviewEntries)
    return
  }
  if (!state.token) {
    renderReviewQueue([])
    return
  }
  try {
    const wordbookParam = state.currentWordbookId ? `?wordbookId=${state.currentWordbookId}` : ''
    const entries = await request(`/api/v1/learning/reviews/due${wordbookParam}`)
    state.reviewEntries = Array.isArray(entries) ? entries : []
    renderReviewQueue(state.reviewEntries)
    renderProfileMetrics()
  } catch (error) {
    logEvent('error', '复习队列加载失败', error.message)
    toast(`复习队列加载失败：${error.message}`)
  }
}

function renderReviewQueue(entries) {
  if (!state.token) {
    elements.reviewQueue.className = 'review-list empty'
    elements.reviewQueue.textContent = '登录后查看复习任务'
    renderReviewFocus(null)
    renderNotes(null)
    return
  }
  if (!entries.length) {
    elements.reviewQueue.className = 'review-list empty'
    elements.reviewQueue.textContent = '当前没有到期复习'
    renderReviewFocus(null)
    renderNotes(null)
    return
  }
  elements.reviewQueue.className = 'review-list'
  elements.reviewQueue.innerHTML = entries
    .map(
      (entry) => `
        <div class="review-item">
          <button class="review-word" type="button" data-review-term="${escapeHtml(entry.normalizedTerm)}">
            ${escapeHtml(entry.term || entry.normalizedTerm)}
          </button>
          <div class="review-meta">
            阶段 ${entry.reviewStage ?? 0} · 掌握 ${entry.masteryScore ?? 0} · 下次 ${escapeHtml(formatDateTime(entry.nextReviewTime))}
          </div>
          <div class="review-actions">
            <button type="button" data-review-result="forgotten" data-entry-id="${entry.id}">忘记</button>
            <button type="button" data-review-result="vague" data-entry-id="${entry.id}">模糊</button>
            <button type="button" data-review-result="remembered" data-entry-id="${entry.id}">记住</button>
          </div>
        </div>
      `,
    )
    .join('')
  elements.reviewQueue.querySelectorAll('[data-review-term]').forEach((button) => {
    button.addEventListener('click', () => {
      const term = button.getAttribute('data-review-term')
      const entry = state.reviewEntries.find((item) => item.normalizedTerm === term)
      renderReviewFocus(entry)
      renderNotes(entry)
      elements.termInput.value = term
      study(term)
    })
  })
  elements.reviewQueue.querySelectorAll('[data-review-result]').forEach((button) => {
    button.addEventListener('click', () => submitReview(button.getAttribute('data-entry-id'), button.getAttribute('data-review-result')))
  })
  renderReviewFocus(entries[0])
  renderNotes(entries[0])
}

function renderReviewFocus(entryOrRecord) {
  if (!entryOrRecord) {
    elements.reviewFocus.className = 'empty'
    elements.reviewFocus.textContent = '点击待复习单词后查看学习卡片'
    return
  }
  const parsed = entryOrRecord.parsed || state.currentRecord?.parsed || null
  const term = parsed?.term || entryOrRecord.term || entryOrRecord.normalizedTerm || state.currentRecord?.normalizedTerm || 'Ready'
  const definitions = normalizeDefinitions(parsed).slice(0, 3)
  elements.reviewFocus.className = 'review-focus-card'
  elements.reviewFocus.innerHTML = `
    <p class="eyebrow">Focus</p>
    <h4>${escapeHtml(term)}</h4>
    <p class="phonetic">${escapeHtml([parsed?.phonetic?.uk, parsed?.phonetic?.us].filter(Boolean).join('    ') || '暂无音标')}</p>
    <div class="mini-definition-list">
      ${
        definitions.length
          ? definitions.map((item) => `<div><span>${escapeHtml(item.pos || 'meaning')}</span><p>${escapeHtml(item.cn || item.en || '')}</p></div>`).join('')
          : '<div class="empty">暂无释义</div>'
      }
    </div>
  `
}

async function submitReview(entryId, result) {
  setLoading(true)
  try {
    if (state.preview) {
      const entry = state.reviewEntries.find((item) => Number(item.id) === Number(entryId))
      if (entry) {
        entry.status = reviewResultToStatus(result)
        const source = state.wordbookEntries.find((item) => Number(item.id) === Number(entryId))
        if (source) source.status = entry.status
      }
      renderReviewQueue(state.reviewEntries)
      renderWordbookEntries()
      logEvent('review', '预览提交复习结果', `${entryId} -> ${result}`)
      toast('设计预览：已模拟提交复习结果')
      return
    }
    const response = await request(`/api/v1/learning/reviews/${entryId}`, {
      method: 'POST',
      body: JSON.stringify({ result }),
    })
    await Promise.allSettled([loadWordbooks(), loadDueReviews(), loadWordbookEntries()])
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
  document.querySelectorAll('[data-save-note]').forEach((button) => button.addEventListener('click', () => saveCurrentNote(button)))
  document.querySelectorAll('[data-cancel-note]').forEach((button) => button.addEventListener('click', () => renderNotes(entry)))
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
      const entry = list.find((item) => Number(item.id) === Number(entryId))
      if (entry) Object.assign(entry, payload)
    }
    const updated = state.wordbookEntries.find((item) => Number(item.id) === Number(entryId)) || state.reviewEntries.find((item) => Number(item.id) === Number(entryId))
    renderWordbookEntries()
    renderReviewQueue(state.reviewEntries)
    renderNotes(updated)
    toast('设计预览：词条已更新')
    return updated
  }
  try {
    const updated = await request(`/api/v1/learning/wordbook-entries/${entryId}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    })
    state.wordbookEntries = state.wordbookEntries.map((entry) => (Number(entry.id) === Number(entryId) ? { ...entry, ...updated } : entry))
    state.reviewEntries = state.reviewEntries.map((entry) => (Number(entry.id) === Number(entryId) ? { ...entry, ...updated } : entry))
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
            <small>${escapeHtml(formatDateTime(item.time))}</small>
          </div>
        </div>
      `,
    )
    .join('')
}

function clearLogs() {
  state.systemLogs = []
  localStorage.removeItem('learning.systemLogs')
  renderSystemLogs()
  elements.rawJson.textContent = '{}'
  toast('系统日志已清空')
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

function currentWordbookName() {
  return state.wordbooks.find((item) => Number(item.id) === Number(state.currentWordbookId))?.name || '当前词书'
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

elements.loginBtn.addEventListener('click', () => loginOrRegister('login'))
elements.registerBtn.addEventListener('click', () => loginOrRegister('register'))
elements.logoutBtn.addEventListener('click', logout)
elements.apiBaseInput.addEventListener('change', () => {
  state.apiBase = elements.apiBaseInput.value.trim() || 'http://localhost:16681'
  localStorage.setItem('learning.apiBase', state.apiBase)
  loadAgents()
  loadModelConfigs()
})
elements.reloadAgentsBtn.addEventListener('click', loadAgents)
elements.reloadModelsBtn.addEventListener('click', loadModelConfigs)
elements.reloadWordbooksBtn.addEventListener('click', loadWordbooks)
elements.reloadWordbookEntriesBtn.addEventListener('click', loadWordbookEntries)
elements.reloadWordbookViewBtn.addEventListener('click', loadWordbookEntries)
elements.createWordbookBtn.addEventListener('click', createWordbook)
elements.resetWordbookFormBtn.addEventListener('click', resetWordbookForm)
elements.saveModelBtn.addEventListener('click', saveModelConfig)
elements.resetModelFormBtn.addEventListener('click', resetModelForm)
elements.wordbookSelect.addEventListener('change', () => changeWordbook(Number(elements.wordbookSelect.value)))
elements.wordStatusFilter.addEventListener('change', loadWordbookEntries)
elements.clearLogBtn.addEventListener('click', clearLogs)
elements.studyForm.addEventListener('submit', (event) => {
  event.preventDefault()
  study(elements.termInput.value)
})
elements.addToWordbookBtn.addEventListener('click', addCurrentWordToWordbook)
elements.speakWordBtn.addEventListener('click', () => speak(state.currentRecord?.normalizedTerm || elements.termInput.value))
elements.speakSentenceBtn.addEventListener('click', () => speak(firstExample(state.currentRecord?.parsed)))
elements.editStudyNoteBtn.addEventListener('click', editCurrentNote)
elements.editReviewNoteBtn.addEventListener('click', editCurrentNote)
elements.chatBtn.addEventListener('click', chat)
elements.reloadReviewBtn.addEventListener('click', loadDueReviews)
document.querySelectorAll('.nav-item').forEach((button) => {
  button.addEventListener('click', () => setView(button.dataset.view))
})

window.renderRecord = renderRecord
window.learningAssistant = { renderRecord, speak, setView }

updateAuthView()
renderSystemLogs()
renderRawJson(null)
loadAgents()
loadModelConfigs()
if (state.token || state.preview) {
  loadInitialData()
}
