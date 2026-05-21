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
    syncAgentModelProviderDefaults,
    saveAgentConfig,
    openTemplateModal,
    closeTemplateModal,
    openAccountModal,
    closeAccountModal,
    saveAccountProfile,
    loadWordbookEntries,
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
    changeWordbook,
    renderWordbookEntries,
    loadSystemLogs,
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
    closeDeleteConfirm,
    setView,
    setProfileTab,
    handleReviewKeydown,
  } = ctx

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
elements.reloadAgentsBtn?.addEventListener('click', loadAgents)
elements.reloadAgentConfigsBtn?.addEventListener('click', loadAgents)
elements.reloadModelsBtn.addEventListener('click', loadModelConfigs)
elements.openModelModalBtn.addEventListener('click', () => openModelModal())
elements.closeModelModalBtn.addEventListener('click', closeModelModal)
elements.modelConfigModal.addEventListener('click', (event) => {
  if (event.target === elements.modelConfigModal) closeModelModal()
})
elements.modelProviderInput.addEventListener('change', () => syncModelProviderDefaults())
elements.modelDefaultToggleBtn?.addEventListener('click', () => toggleModelFlag(elements.modelDefaultInput))
elements.modelEnabledToggleBtn?.addEventListener('click', () => toggleModelFlag(elements.modelEnabledInput))
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
elements.agentModelProviderInput?.addEventListener('change', () => syncAgentModelProviderDefaults())
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
elements.saveSpeechBtn?.addEventListener('click', async () => {
  const saved = await saveSpeechPreferences()
  if (saved !== false) closeLearningConfigModal()
})
elements.wordbookSelect.addEventListener('change', () => changeWordbook(elements.wordbookSelect.value))
elements.wordStatusFilter.addEventListener('change', loadWordbookEntries)
elements.wordPrefixInput?.addEventListener('input', () => {
  state.wordPrefixFilter = elements.wordPrefixInput.value.trim()
  renderWordbookEntries()
})
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
elements.editReviewNoteBtn.addEventListener('click', editCurrentNote)
elements.chatBtn.addEventListener('click', chat)
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
document.querySelectorAll('.profile-tab').forEach((button) => {
  button.addEventListener('click', () => setProfileTab(button.dataset.profileTab))
})
document.addEventListener('keydown', handleReviewKeydown)
}

export function exposeDebugGlobals({ state, renderRecord, renderReviewCompleteModal, speak, setView, setProfileTab }) {
  window.renderRecord = renderRecord
  window.learningAssistant = { renderRecord, renderReviewCompleteModal, speak, setView, setProfileTab, state }
}
