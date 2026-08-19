import { hideModal } from '/src/shared/modal.js'
import { syncCurrentWordbookId } from '/src/shared/wordbook.js'
import { firstExample } from '/src/shared/vocabulary.js'

export function bindAppEvents(ctx) {
  const {
    state,
    elements,
    loginOrRegister,
    logout,
    toggleSidebar,
    setSidebarCollapsed,
    handleViewportChange,
    loadAgents,
    loadModelConfigs,
    loadPromptTemplates,
    openModelModal,
    closeModelModal,
    syncModelProviderDefaults,
    toggleModelFlag,
    openAgentModal,
    closeAgentModal,
    openLearningConfigModal,
    closeLearningConfigModal,
    saveAgentConfig,
    openTemplateModal,
    closeTemplateModal,
    openAccountModal,
    closeAccountModal,
    setAccountModalTab,
    openAccountSecurityEditor,
    cancelAccountSecurityEditor,
    saveAccountProfile,
    saveAccountSecurity,
    updateAccountPasswordStrength,
    loadWordbookEntries,
    changeArticleWordbook,
    loadSceneData,
    changeSceneWordbook,
    openVocabularyImport,
    closeVocabularyImport,
    startVocabularyImport,
    saveVocabularyImportMetadata,
    loadImportReview,
    changeImportSearch,
    confirmAllWarnings,
    previousImportPage,
    nextImportPage,
    publishVocabularyImport,
    triggerVocabularyAnalysis,
    openScenePlanModal,
    closeScenePlanModal,
    closeSceneVocabularyPreview,
    createScenePlan,
    changeScenePlanCatalog,
    completeCurrentUnit,
    generateNextUnit,
    scheduleNextUnit,
    generateSceneCards,
    scheduleSceneCards,
    speakCurrentScene,
    startSceneLearning,
    showSceneChallengeWords,
    startSceneChallenge,
    backToSceneReading,
    backToSceneOverview,
    changeSceneCalendarRange,
    changeSceneCalendarOffset,
    resetSceneCalendar,
    changeSelectedScenePlan,
    pauseScenePlan,
    resumeScenePlan,
    cancelScenePlan,
    saveSceneNote,
    toggleSceneNotePreview,
    openSceneNoteModal,
    closeSceneNoteModal,
    openCoreWordsModal,
    closeCoreWordsModal,
    openRelatedWordsModal,
    closeRelatedWordsModal,
    renderSceneRelatedWords,
    loadArticleWords,
    loadArticleHistory,
    renderArticleWords,
    renderArticleHistory,
    clearArticleSelection,
    recommendArticleWords,
    openArticleStudyModal,
    closeArticleStudyModal,
    generateArticlePreview,
    saveArticleStudy,
    openWordbookModal,
    closeWordbookModal,
    createWordbook,
    saveModelConfig,
    resetModelForm,
    changeLearningAgent,
    renderSelectedTemplate,
    validateTemplatePlaceholders,
    savePromptTemplate,
    saveSpeechPreferences,
    saveLearningSettings,
    changeWordbook,
    renderWordbookEntries,
    toggleWordbookFocusMode,
    toggleArticleFocusMode,
    loadSystemLogs,
    loadAiTasks,
    renderAiTasks,
    clearLogs,
    study,
    regenerateStudyCard,
    addCurrentWordToWordbook,
    closeAddWordbookModal,
    closeEntryTransferModal,
    closeEntryStatusModal,
    chooseEntryStatus,
    speak,
    speakSentence,
    editCurrentNote,
    chat,
    startReview,
    closeReviewModal,
    submitReview,
    closeForgottenDetailModal,
    closeReviewNoteModal,
    toggleReviewNotePreview,
    saveReviewNote,
    closeDeleteConfirm,
    setView,
    setProfileTab,
    setSystemTab,
    systemManagement,
    handleReviewKeydown,
  } = ctx

elements.loginBtn.addEventListener('click', () => loginOrRegister('login'))
elements.registerBtn.addEventListener('click', () => loginOrRegister('register'))
elements.usernameInput?.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') loginOrRegister('login')
})
elements.passwordInput?.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') loginOrRegister('login')
})
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
elements.reloadAgentsBtn?.addEventListener('click', loadAgents)
elements.reloadAgentConfigsBtn?.addEventListener('click', loadAgents)
elements.reloadModelsBtn.addEventListener('click', loadModelConfigs)
elements.openModelModalBtn.addEventListener('click', () => openModelModal())
elements.closeModelModalBtn.addEventListener('click', closeModelModal)
elements.modelConfigModal.addEventListener('click', (event) => {
  if (event.target === elements.modelConfigModal) closeModelModal()
})
elements.modelProviderInput.addEventListener('change', () => syncModelProviderDefaults())
elements.modelProviderInput.addEventListener('input', () => syncModelProviderDefaults())
elements.modelDefaultToggleBtn?.addEventListener('click', () => toggleModelFlag(elements.modelDefaultInput))
elements.modelEnabledToggleBtn?.addEventListener('click', () => toggleModelFlag(elements.modelEnabledInput))
elements.saveAndTestModelBtn?.addEventListener('click', () => saveModelConfig({ testAfterSave: true }))
elements.openAgentModalBtn?.addEventListener('click', () => openAgentModal())
elements.closeAgentModalBtn?.addEventListener('click', closeAgentModal)
elements.agentModal?.addEventListener('click', (event) => {
  if (event.target === elements.agentModal) closeAgentModal()
})
elements.openLearningConfigModalBtn?.addEventListener('click', openLearningConfigModal)
elements.closeLearningConfigModalBtn?.addEventListener('click', closeLearningConfigModal)
elements.learningConfigModal?.addEventListener('click', (event) => {
  if (event.target === elements.learningConfigModal) closeLearningConfigModal()
})
elements.saveAgentBtn?.addEventListener('click', saveAgentConfig)
elements.openTemplateModalBtn?.addEventListener('click', () => openTemplateModal())
elements.closeTemplateModalBtn?.addEventListener('click', closeTemplateModal)
elements.templateModal?.addEventListener('click', (event) => {
  if (event.target === elements.templateModal) closeTemplateModal()
})
elements.reloadTemplatesBtn?.addEventListener('click', loadPromptTemplates)
elements.openAccountModalBtn.addEventListener('click', openAccountModal)
elements.closeAccountModalBtn.addEventListener('click', closeAccountModal)
elements.accountModal.addEventListener('click', (event) => {
  if (event.target === elements.accountModal) closeAccountModal()
})
elements.accountModal.querySelectorAll('[data-account-tab]').forEach((button) => {
  button.addEventListener('click', () => setAccountModalTab(button.dataset.accountTab))
})
elements.editAccountPasswordBtn?.addEventListener('click', () => openAccountSecurityEditor('password'))
elements.editAccountPhoneBtn?.addEventListener('click', () => openAccountSecurityEditor('phone'))
elements.editAccountEmailBtn?.addEventListener('click', () => openAccountSecurityEditor('email'))
elements.cancelAccountSecurityBtn?.addEventListener('click', cancelAccountSecurityEditor)
elements.accountNewPasswordInput?.addEventListener('input', updateAccountPasswordStrength)
elements.saveAccountProfileBtn.addEventListener('click', saveAccountProfile)
elements.saveAccountSecurityBtn.addEventListener('click', saveAccountSecurity)
elements.reloadWordbookEntriesBtn.addEventListener('click', loadWordbookEntries)
elements.openVocabularyImportBtn?.addEventListener('click', openVocabularyImport)
elements.openProfileScenePlanModalBtn?.addEventListener('click', openScenePlanModal)
elements.reloadWordbookViewBtn.addEventListener('click', loadWordbookEntries)
elements.openWordbookModalBtn.addEventListener('click', () => openWordbookModal())
elements.closeWordbookModalBtn.addEventListener('click', closeWordbookModal)
elements.wordbookModal.addEventListener('click', (event) => {
  if (event.target === elements.wordbookModal) closeWordbookModal()
})
elements.closeWordbookCardModalBtn?.addEventListener('click', () => hideModal(elements.wordbookCardModal))
elements.wordbookCardModal?.addEventListener('click', (event) => {
  if (event.target === elements.wordbookCardModal) hideModal(elements.wordbookCardModal)
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
elements.saveSpeechBtn?.addEventListener('click', async () => {
  const speechSaved = await saveSpeechPreferences()
  const learningSaved = await saveLearningSettings?.()
  const saved = speechSaved !== false && learningSaved !== false
  if (saved !== false) closeLearningConfigModal()
})
elements.wordbookSelect.addEventListener('change', () => changeWordbook(elements.wordbookSelect.value))
elements.wordStatusFilter.addEventListener('change', loadWordbookEntries)
elements.toggleWordbookFocusModeBtn?.addEventListener('click', toggleWordbookFocusMode)
elements.wordPrefixInput?.addEventListener('input', () => {
  state.wordPrefixFilter = elements.wordPrefixInput.value.trim()
  renderWordbookEntries()
})
elements.articleWordbookSelect?.addEventListener('change', () => changeArticleWordbook(elements.articleWordbookSelect.value))
elements.articleStatusFilter?.addEventListener('change', loadArticleWords)
elements.toggleArticleFocusModeBtn?.addEventListener('click', toggleArticleFocusMode)
elements.articlePrefixInput?.addEventListener('input', () => {
  state.articlePrefixFilter = elements.articlePrefixInput.value.trim()
  renderArticleWords()
})
elements.articleReloadWordsBtn?.addEventListener('click', () => {
  loadArticleWords()
  loadArticleHistory()
})
elements.articleReloadHistoryBtn?.addEventListener('click', loadArticleHistory)
elements.sceneWordbookSelect?.addEventListener('change', () => changeSceneWordbook())
elements.sceneReloadBtn?.addEventListener('click', loadSceneData)
elements.openScenePlanModalBtn?.addEventListener('click', openScenePlanModal)
elements.closeVocabularyImportBtn?.addEventListener('click', closeVocabularyImport)
elements.vocabularyImportModal?.addEventListener('click', (event) => {
  if (event.target === elements.vocabularyImportModal) closeVocabularyImport()
})
elements.startVocabularyImportBtn?.addEventListener('click', startVocabularyImport)
elements.saveVocabularyImportMetadataBtn?.addEventListener('click', saveVocabularyImportMetadata)
elements.vocabularyImportFile?.addEventListener('change', () => {
  const file = elements.vocabularyImportFile.files?.[0]
  const placeholder = document.getElementById('fileUploadPlaceholder')
  if (placeholder) {
    placeholder.textContent = file ? file.name : '选择 Markdown 文件'
  }
})
elements.vocabularyWarningOnly?.addEventListener('change', () => loadImportReview())
elements.vocabularyImportKeyword?.addEventListener('input', changeImportSearch)
elements.vocabularyBatchConfirmBtn?.addEventListener('click', confirmAllWarnings)
elements.vocabularyReloadReviewBtn?.addEventListener('click', () => loadImportReview())
elements.vocabularyPrevPageBtn?.addEventListener('click', previousImportPage)
elements.vocabularyNextPageBtn?.addEventListener('click', nextImportPage)
elements.publishVocabularyImportBtn?.addEventListener('click', publishVocabularyImport)
elements.triggerVocabularyAnalysisBtn?.addEventListener('click', triggerVocabularyAnalysis)
elements.closeScenePlanModalBtn?.addEventListener('click', closeScenePlanModal)
elements.scenePlanModal?.addEventListener('click', (event) => {
  if (event.target === elements.scenePlanModal) closeScenePlanModal()
})
elements.closeSceneVocabularyPreviewBtn?.addEventListener('click', closeSceneVocabularyPreview)
elements.sceneVocabularyPreviewModal?.addEventListener('click', (event) => {
  if (event.target === elements.sceneVocabularyPreviewModal) closeSceneVocabularyPreview()
})
elements.createScenePlanBtn?.addEventListener('click', createScenePlan)
elements.scenePlanPauseBtn?.addEventListener('click', pauseScenePlan)
elements.scenePlanResumeBtn?.addEventListener('click', resumeScenePlan)
elements.scenePlanCancelBtn?.addEventListener('click', cancelScenePlan)
elements.sceneCatalogSelect?.addEventListener('change', changeScenePlanCatalog)
elements.sceneCompleteUnitBtn?.addEventListener('click', completeCurrentUnit)
elements.sceneNextUnitBtn?.addEventListener('click', generateNextUnit)
elements.sceneOverviewNextUnitBtn?.addEventListener('click', generateNextUnit)
elements.sceneScheduleNextUnitBtn?.addEventListener('click', scheduleNextUnit)
elements.sceneGenerateCardsBtn?.addEventListener('click', generateSceneCards)
elements.sceneScheduleCardsBtn?.addEventListener('click', scheduleSceneCards)
elements.sceneSpeakBtn?.addEventListener('click', speakCurrentScene)
elements.sceneOpenNoteModalBtn?.addEventListener('click', openSceneNoteModal)
elements.sceneNoteModalCloseBtn?.addEventListener('click', closeSceneNoteModal)
elements.sceneNoteModalCancelBtn?.addEventListener('click', closeSceneNoteModal)
elements.sceneNoteModal?.addEventListener('click', (event) => {
  if (event.target === elements.sceneNoteModal) closeSceneNoteModal()
})
elements.sceneNoteSaveBtn?.addEventListener('click', saveSceneNote)
elements.sceneNotePreviewBtn?.addEventListener('click', toggleSceneNotePreview)

elements.sceneOpenCoreWordsBtn?.addEventListener('click', openCoreWordsModal)
elements.sceneCoreWordsModalCloseBtn?.addEventListener('click', closeCoreWordsModal)
elements.sceneCoreWordsModalCancelBtn?.addEventListener('click', closeCoreWordsModal)
elements.sceneStartChallengeFromModalBtn?.addEventListener('click', () => {
  closeCoreWordsModal()
  startSceneChallenge()
})
elements.sceneCoreWordsModal?.addEventListener('click', (event) => {
  if (event.target === elements.sceneCoreWordsModal) closeCoreWordsModal()
})

elements.sceneOpenRelatedWordsBtn?.addEventListener('click', openRelatedWordsModal)
elements.sceneRelatedWordsModalCloseBtn?.addEventListener('click', closeRelatedWordsModal)
elements.sceneRelatedWordsModal?.addEventListener('click', (event) => {
  if (event.target === elements.sceneRelatedWordsModal) closeRelatedWordsModal()
})
elements.sceneRelatedFilter?.addEventListener('input', renderSceneRelatedWords)
elements.sceneTierFilter?.addEventListener('change', renderSceneRelatedWords)

elements.scenePlanSelect?.addEventListener('change', () => changeSelectedScenePlan(elements.scenePlanSelect.value))
document.querySelectorAll('[data-calendar-range]').forEach((button) => {
  button.addEventListener('click', () => changeSceneCalendarRange(button.dataset.calendarRange))
})
elements.sceneCalendarPreviousBtn?.addEventListener('click', () => changeSceneCalendarOffset(-1))
elements.sceneCalendarTodayBtn?.addEventListener('click', resetSceneCalendar)
elements.sceneCalendarNextBtn?.addEventListener('click', () => changeSceneCalendarOffset(1))
elements.sceneStartLearningBtn?.addEventListener('click', startSceneLearning)
elements.sceneBackToPlanBtn?.addEventListener('click', backToSceneOverview)
elements.sceneVocabularyChallengeBtn?.addEventListener('click', showSceneChallengeWords)
elements.sceneChallengeBackBtn?.addEventListener('click', backToSceneReading)
elements.sceneChallengeStartBtn?.addEventListener('click', startSceneChallenge)
elements.articleClearSelectionBtn?.addEventListener('click', clearArticleSelection)
elements.articleRecommendWordsBtn?.addEventListener('click', recommendArticleWords)
elements.articleGenerateBtn?.addEventListener('click', openArticleStudyModal)
elements.closeArticleStudyModalBtn?.addEventListener('click', closeArticleStudyModal)
elements.cancelArticleStudyBtn?.addEventListener('click', closeArticleStudyModal)
elements.articleStudyModal?.addEventListener('click', (event) => {
  if (event.target === elements.articleStudyModal) closeArticleStudyModal()
})
elements.articlePreviewGenerateBtn?.addEventListener('click', () => generateArticlePreview({ forceRefresh: Boolean(state.articleDraftRecord) }))
elements.saveArticleStudyBtn?.addEventListener('click', saveArticleStudy)
elements.reloadSystemLogsBtn.addEventListener('click', loadSystemLogs)
elements.reloadAiTasksBtn?.addEventListener('click', loadAiTasks)
elements.aiTaskStatusFilter?.addEventListener('change', renderAiTasks)
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
elements.closeEntryTransferModalBtn?.addEventListener('click', closeEntryTransferModal)
elements.entryTransferModal?.addEventListener('click', (event) => {
  if (event.target === elements.entryTransferModal) closeEntryTransferModal()
})
elements.closeEntryStatusModalBtn.addEventListener('click', closeEntryStatusModal)
elements.entryStatusModal.addEventListener('click', (event) => {
  if (event.target === elements.entryStatusModal) closeEntryStatusModal()
})
elements.entryStatusModal.querySelectorAll('[data-status-choice]').forEach((button) => {
  button.addEventListener('click', () => chooseEntryStatus(button.getAttribute('data-status-choice')))
})
elements.speakWordBtn.addEventListener('click', () => speak(state.currentRecord?.normalizedTerm || elements.termInput.value))
elements.speakSentenceBtn.addEventListener('click', () => speakSentence(firstExample(state.currentRecord?.parsed)))
elements.editStudyNoteBtn.addEventListener('click', editCurrentNote)
elements.editReviewNoteBtn?.addEventListener('click', editCurrentNote)
elements.reviewNoteModalCloseBtn?.addEventListener('click', closeReviewNoteModal)
elements.reviewNoteModalCancelBtn?.addEventListener('click', closeReviewNoteModal)
elements.reviewNoteModal?.addEventListener('click', (event) => {
  if (event.target === elements.reviewNoteModal) closeReviewNoteModal()
})
elements.reviewNotePreviewBtn?.addEventListener('click', toggleReviewNotePreview)
elements.reviewNoteSaveBtn?.addEventListener('click', saveReviewNote)
elements.reloadReviewBtn.addEventListener('click', startReview)
elements.reviewWordbookSelect.addEventListener('change', () => {
  syncCurrentWordbookId(state, elements, elements.reviewWordbookSelect.value)
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
document.querySelectorAll('[data-profile-tab]').forEach((button) => {
  button.addEventListener('click', () => setProfileTab(button.dataset.profileTab))
})
document.querySelectorAll('[data-system-tab]').forEach((button) => {
  button.addEventListener('click', () => setSystemTab(button.dataset.systemTab))
})
elements.openAdminUserModalBtn?.addEventListener('click', () => systemManagement?.openUserModal())
elements.closeAdminUserModalBtn?.addEventListener('click', () => systemManagement?.closeUserModal())
elements.adminUserModal?.addEventListener('click', (event) => {
  if (event.target === elements.adminUserModal) systemManagement?.closeUserModal()
})
elements.saveAdminUserBtn?.addEventListener('click', () => systemManagement?.saveUser())
elements.searchAdminUsersBtn?.addEventListener('click', () => { state.adminUserPage = 1; systemManagement?.loadUsers() })
elements.resetAdminUsersBtn?.addEventListener('click', () => {
  elements.adminUserKeywordInput.value = ''
  elements.adminUserRoleFilter.value = ''
  elements.adminUserEnabledFilter.value = ''
  state.adminUserPage = 1
  systemManagement?.loadUsers()
})
elements.adminUserPrevBtn?.addEventListener('click', () => systemManagement?.changePage(-1))
elements.adminUserNextBtn?.addEventListener('click', () => systemManagement?.changePage(1))
elements.openAdminVocabularyImportBtn?.addEventListener('click', openVocabularyImport)
elements.searchAiSessionsBtn?.addEventListener('click', () => { state.aiSessionPage = 1; systemManagement?.loadAiSessions() })
elements.resetAiSessionsBtn?.addEventListener('click', () => systemManagement?.resetAiSessionFilters())
elements.aiSessionPrevBtn?.addEventListener('click', () => systemManagement?.changeAiSessionPage(-1))
elements.aiSessionNextBtn?.addEventListener('click', () => systemManagement?.changeAiSessionPage(1))
elements.closeAiSessionDetailBtn?.addEventListener('click', () => systemManagement?.closeDetail())
elements.aiSessionDetailModal?.addEventListener('click', (event) => { if (event.target === elements.aiSessionDetailModal) systemManagement?.closeDetail() })
  document.addEventListener('keydown', handleReviewKeydown)

  window.addEventListener('unhandledrejection', (event) => {
    const error = event.reason
    if (error?.status === 401) {
      state.onUnauthorized?.()
      event.preventDefault()
      return
    }
    console.error('Unhandled rejection:', error)
  })

  window.addEventListener('error', (event) => {
    console.error('Global script error:', event.error || event.message)
  })
}

export function exposeDebugGlobals({ state, renderRecord, renderReviewCompleteModal, speak, setView, setProfileTab }) {
  window.renderRecord = renderRecord
  window.learningAssistant = { renderRecord, renderReviewCompleteModal, speak, setView, setProfileTab, state }
}
