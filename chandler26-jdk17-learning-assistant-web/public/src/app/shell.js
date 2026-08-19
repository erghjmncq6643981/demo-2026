import { viewMeta } from '/src/app/config.js'
import { loadPreviewData as loadPreviewFixture } from '/src/app/preview.js'
import { syncCurrentWordbookId } from '/src/shared/wordbook.js'

export function createAppShell(ctx) {
  const {
    state,
    elements,
    request,
    setLoading,
    toast,
    logEvent,
    loadAgents,
    loadWordbooks,
    loadModelConfigs,
    loadPromptTemplates,
    loadSpeechPreferences,
    loadLearningSettings,
    loadActivity,
    loadSystemLogs,
    loadAiTasks,
    systemManagement,
    loadDueReviews,
    loadWordbookEntries,
    loadArticleWords,
    loadArticleHistory,
    loadSceneData,
    clearSceneData,
    renderProfileMetrics,
    renderActivityHeatmap,
    renderWordbooks,
    renderWordbookEntries,
    renderArticleWords,
    renderArticleHistory,
    renderArticleResult,
    renderModelConfigs,
    renderAgentConfigs,
    renderLearningAgentOptions,
    renderLearningConfigSummary,
    renderTemplateOptions,
    renderTemplateConfigs,
    renderReviewQueue,
    renderReviewFocus,
    renderRecord,
    renderNotes,
    closeReviewModal,
    closeWordbookModal,
    closeAccountModal,
    closeEntryStatusModal,
  } = ctx

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
    systemManagement?.renderAdminEntry()
    if (user?.username) elements.usernameInput.value = user.username
    updateShellVisibility()
    renderProfileMetrics()
    renderActivityHeatmap()
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
    if (viewId === 'systemAdminView' && !systemManagement?.isAdmin()) {
      viewId = 'profileView'
    }
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
    if (viewId === 'articleStudyView') {
      loadArticleWords?.()
      loadArticleHistory?.()
    }
    if (viewId === 'scenePlanView') loadSceneData?.()
    if (viewId === 'systemAdminView') {
      systemManagement?.mountPanels()
      systemManagement?.renderSystemTab(state.activeSystemTab)
    }
    if (window.matchMedia('(max-width: 1100px)').matches) {
      setSidebarCollapsed(true)
    }
  }

  function setProfileTab(tabId) {
    const fallback = document.querySelector(`[data-profile-tab="${tabId}"]`) ? tabId : 'accountPanel'
    state.activeProfileTab = fallback
    localStorage.setItem('learning.profileTab', fallback)
    document.querySelectorAll('.profile-tab').forEach((button) => {
      const active = button.dataset.profileTab === fallback
      button.classList.toggle('active', active)
      if (active) button.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' })
    })
    document.querySelectorAll('.profile-section').forEach((section) => section.classList.toggle('active', section.id === fallback))
    if (fallback === 'aiTaskPanel') loadAiTasks?.()
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
    state.articleEntries = []
    state.articleRecords = []
    state.selectedArticleEntryIds = []
    state.currentArticleRecord = null
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
    clearSceneData?.()
    localStorage.removeItem('learning.token')
    localStorage.removeItem('learning.user')
    localStorage.removeItem('learning.wordbookId')
    syncCurrentWordbookId(state, elements, null, { persist: false })
    renderWordbooks()
    renderWordbookEntries()
    renderArticleWords?.()
    renderArticleHistory?.()
    renderArticleResult?.(null)
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
    await Promise.allSettled([loadAgents(), loadWordbooks(), loadModelConfigs(), loadPromptTemplates(), loadSpeechPreferences(), loadActivity(), loadSystemLogs(), loadAiTasks?.()])
    await Promise.allSettled([loadLearningSettings?.()])
    await Promise.allSettled([loadDueReviews(), loadWordbookEntries()])
    await Promise.allSettled([loadSceneData?.()])
    if (state.activeView === 'articleStudyView') {
      await Promise.allSettled([loadArticleWords?.(), loadArticleHistory?.()])
    }
  }

  function loadPreviewData() {
    const result = loadPreviewFixture({
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
      renderArticleWords,
      renderArticleHistory,
      renderArticleResult,
      renderReviewQueue,
      renderRecord,
      renderActivityHeatmap,
      logEvent,
    })
    loadSceneData?.()
    return result
  }

  function setSystemTab(tabId) {
    systemManagement?.renderSystemTab(tabId)
  }

  return {
    updateAuthView,
    syncSidebarState,
    setSidebarCollapsed,
    toggleSidebar,
    handleViewportChange,
    setView,
    setProfileTab,
    setSystemTab,
    loginOrRegister,
    logout,
    loadInitialData,
    loadPreviewData,
  }
}
