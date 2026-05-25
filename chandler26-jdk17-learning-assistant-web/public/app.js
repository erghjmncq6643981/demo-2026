import { bindAppEvents, exposeDebugGlobals } from '/src/app/events.js'
import { createElements } from '/src/app/elements.js'
import { providerCatalog } from '/src/app/config.js'
import { createInitialState } from '/src/app/state.js'
import { createAppServices } from '/src/app/services.js'
import { createAppShell } from '/src/app/shell.js'
import { createFeatureFacade } from '/src/app/facade.js'
import { sameId } from '/src/shared/ids.js'
import { clampNumber } from '/src/shared/storage.js'
import { normalizeWordbookId, syncCurrentWordbookId } from '/src/shared/wordbook.js'
import { escapeHtml, formatDateTime } from '/src/shared/text.js'
import {
  createPreviewActivity,
  normalizeArray,
  normalizeDefinitions,
  normalizeExamples,
  readText,
  renderMarkdown,
  reviewResultToStatus,
  statusLabel,
  stringifyValue,
  tagLabel,
} from '/src/shared/vocabulary.js'
import { createWordbookTransferFeature } from '/src/features/wordbook/transfer.js'
import { createProfileFeature } from '/src/features/profile/profile.js'
import { createSpeechFeature } from '/src/features/speech/speech.js'
import { createStudyFeature } from '/src/features/study/study.js'
import { createReviewFeature } from '/src/features/review/review.js'

const state = createInitialState()
const elements = createElements()
let profileFeature
let studyFeature
let reviewFeature
const services = createAppServices({ state, elements })
const {
  request,
  setLoading,
  toast,
  confirmAction,
  confirmDelete,
  closeDeleteConfirm,
  logEvent,
  setConnection,
  setSystemLogRenderer,
} = services
const profile = createFeatureFacade(() => profileFeature)
const studyFacade = createFeatureFacade(() => studyFeature)
const review = createFeatureFacade(() => reviewFeature)

const speechFeature = createSpeechFeature({
  state,
  elements,
  request,
  toast,
  logEvent,
  clampNumber,
  renderLearningConfigSummary: (...args) => profile.renderLearningConfigSummary(...args),
})

const {
  speak,
  speakSentence,
  currentVoiceType,
  loadSpeechPreferences,
  saveSpeechPreferences,
  initSpeechSettings,
} = speechFeature

const {
  updateAuthView,
  syncSidebarState,
  setSidebarCollapsed,
  toggleSidebar,
  handleViewportChange,
  setView,
  setProfileTab,
  loginOrRegister,
  logout,
  loadInitialData,
} = createAppShell({
  state,
  elements,
  request,
  setLoading,
  toast,
  logEvent,
  loadAgents: profile.loadAgents,
  loadWordbooks: profile.loadWordbooks,
  loadModelConfigs: profile.loadModelConfigs,
  loadPromptTemplates: profile.loadPromptTemplates,
  loadSpeechPreferences,
  loadActivity: profile.loadActivity,
  loadSystemLogs: profile.loadSystemLogs,
  loadDueReviews: review.loadDueReviews,
  loadWordbookEntries: profile.loadWordbookEntries,
  renderProfileMetrics: profile.renderProfileMetrics,
  renderActivityHeatmap: profile.renderActivityHeatmap,
  renderWordbooks: profile.renderWordbooks,
  renderWordbookEntries: profile.renderWordbookEntries,
  renderModelConfigs: profile.renderModelConfigs,
  renderAgentConfigs: profile.renderAgentConfigs,
  renderLearningAgentOptions: profile.renderLearningAgentOptions,
  renderLearningConfigSummary: profile.renderLearningConfigSummary,
  renderTemplateOptions: profile.renderTemplateOptions,
  renderTemplateConfigs: profile.renderTemplateConfigs,
  renderReviewQueue: review.renderReviewQueue,
  renderReviewFocus: review.renderReviewFocus,
  renderRecord: studyFacade.renderRecord,
  renderNotes: studyFacade.renderNotes,
  closeReviewModal: review.closeReviewModal,
  closeWordbookModal: profile.closeWordbookModal,
  closeAccountModal: profile.closeAccountModal,
  closeEntryStatusModal: profile.closeEntryStatusModal,
})

studyFeature = createStudyFeature({
  state,
  elements,
  request,
  setLoading,
  toast,
  logEvent,
  confirmAction,
  setView,
  createPreviewActivity,
  renderReviewFocus: review.renderReviewFocus,
  renderReviewQueue: review.renderReviewQueue,
  renderWordbookEntries: profile.renderWordbookEntries,
  renderWordbookFocus: profile.renderWordbookFocus,
  renderWordbooks: profile.renderWordbooks,
  loadWordbooks: profile.loadWordbooks,
  loadWordbookEntries: profile.loadWordbookEntries,
  loadDueReviews: review.loadDueReviews,
  loadActivity: profile.loadActivity,
  currentWordbookName: profile.currentWordbookName,
  speak,
  speakSentence,
})

const wordbookTransferFeature = createWordbookTransferFeature({
  state,
  elements,
  sameId,
  normalizeWordbookId,
  escapeHtml,
  request,
  setLoading,
  toast,
  logEvent,
  renderWordbookEntries: profile.renderWordbookEntries,
  renderWordbooks: profile.renderWordbooks,
  loadWordbooks: profile.loadWordbooks,
  loadWordbookEntries: profile.loadWordbookEntries,
  loadDueReviews: review.loadDueReviews,
  loadActivity: profile.loadActivity,
})

const {
  openEntryTransferModal,
  closeEntryTransferModal,
} = wordbookTransferFeature

reviewFeature = createReviewFeature({
  state,
  elements,
  sameId,
  syncCurrentWordbookId,
  request,
  setLoading,
  toast,
  logEvent,
  loadWordbooks: profile.loadWordbooks,
  loadWordbookEntries: profile.loadWordbookEntries,
  loadActivity: profile.loadActivity,
  confirmAction,
  renderNotes: studyFacade.renderNotes,
  renderProfileMetrics: profile.renderProfileMetrics,
  openEntryTransferModal,
  formatDateTime,
  normalizeDefinitions,
  normalizeExamples,
  normalizeArray,
  escapeHtml,
  readText,
  stringifyValue,
  renderCollocationMini: profile.renderCollocationMiniItem,
  speakSentence,
  speak,
  bindStudyTermCards: studyFacade.bindStudyTermCards,
  bindInlineAudio: studyFacade.bindInlineAudio,
  currentVoiceType,
  reviewResultToStatus,
  statusLabel,
})

const {
  startReview,
  handleReviewKeydown,
  renderReviewCompleteModal,
  closeReviewModal,
  closeForgottenDetailModal,
  submitReview,
} = reviewFeature

profileFeature = createProfileFeature({
  state,
  elements,
  request,
  setLoading,
  toast,
  logEvent,
  confirmAction,
  confirmDelete,
  setConnection,
  providerCatalog,
  normalizeDefinitions,
  normalizeExamples,
  renderMarkdown,
  readText,
  stringifyValue,
  tagLabel,
  renderReviewQueue: review.renderReviewQueue,
  renderReviewFocus: review.renderReviewFocus,
  renderNotes: studyFacade.renderNotes,
  openEntryInReview: review.openEntryInReview,
  openEntryTransferModal,
  speak,
  speakSentence,
  bindStudyTermCards: studyFacade.bindStudyTermCards,
  bindInlineAudio: studyFacade.bindInlineAudio,
  confirmStudyTerm: studyFacade.confirmStudyTerm,
  editCurrentNote: studyFacade.editCurrentNote,
  loadWordbooks: profile.loadWordbooks,
  loadWordbookEntries: profile.loadWordbookEntries,
  loadDueReviews: review.loadDueReviews,
  loadActivity: profile.loadActivity,
  createPreviewActivity,
  updateAuthView: (...args) => updateAuthView(...args),
  saveEntry: studyFacade.saveEntry,
})

if (elements.apiBaseInput) elements.apiBaseInput.value = state.apiBase
if (elements.buildVersion) elements.buildVersion.textContent = `build ${state.build}`
initSpeechSettings()

setSystemLogRenderer(profile.renderSystemLogs)

bindAppEvents({
  state,
  elements,
  loginOrRegister,
  logout,
  toggleSidebar,
  setSidebarCollapsed,
  handleViewportChange,
  loadAgents: profile.loadAgents,
  loadModelConfigs: profile.loadModelConfigs,
  loadPromptTemplates: profile.loadPromptTemplates,
  openModelModal: profile.openModelModal,
  closeModelModal: profile.closeModelModal,
  syncModelProviderDefaults: profile.syncModelProviderDefaults,
  toggleModelFlag: profile.toggleModelFlag,
  openAgentModal: profile.openAgentModal,
  closeAgentModal: profile.closeAgentModal,
  syncAgentModelProviderDefaults: profile.syncAgentModelProviderDefaults,
  openLearningConfigModal: profile.openLearningConfigModal,
  closeLearningConfigModal: profile.closeLearningConfigModal,
  saveAgentConfig: profile.saveAgentConfig,
  openTemplateModal: profile.openTemplateModal,
  closeTemplateModal: profile.closeTemplateModal,
  openAccountModal: profile.openAccountModal,
  closeAccountModal: profile.closeAccountModal,
  setAccountModalTab: profile.setAccountModalTab,
  saveAccountProfile: profile.saveAccountProfile,
  saveAccountSecurity: profile.saveAccountSecurity,
  updateAccountPasswordStrength: profile.updateAccountPasswordStrength,
  loadWordbookEntries: profile.loadWordbookEntries,
  openWordbookModal: profile.openWordbookModal,
  closeWordbookModal: profile.closeWordbookModal,
  createWordbook: profile.createWordbook,
  saveModelConfig: profile.saveModelConfig,
  resetModelForm: profile.resetModelForm,
  changeLearningAgent: profile.changeLearningAgent,
  renderSelectedTemplate: profile.renderSelectedTemplate,
  validateTemplatePlaceholders: profile.validateTemplatePlaceholders,
  savePromptTemplate: profile.savePromptTemplate,
  saveSpeechPreferences,
  changeWordbook: profile.changeWordbook,
  renderWordbookEntries: profile.renderWordbookEntries,
  loadSystemLogs: profile.loadSystemLogs,
  clearLogs: profile.clearLogs,
  study: studyFacade.study,
  regenerateStudyCard: studyFacade.regenerateStudyCard,
  addCurrentWordToWordbook: studyFacade.addCurrentWordToWordbook,
  closeAddWordbookModal: studyFacade.closeAddWordbookModal,
  closeEntryTransferModal,
  closeEntryStatusModal: profile.closeEntryStatusModal,
  chooseEntryStatus: profile.chooseEntryStatus,
  speak,
  speakSentence,
  editCurrentNote: studyFacade.editCurrentNote,
  chat: studyFacade.chat,
  startReview,
  closeReviewModal,
  submitReview,
  closeForgottenDetailModal,
  closeDeleteConfirm,
  setView,
  setProfileTab,
  handleReviewKeydown,
})
exposeDebugGlobals({ state, renderRecord: studyFacade.renderRecord, renderReviewCompleteModal, speak, setView, setProfileTab })

updateAuthView()
syncSidebarState()
setProfileTab(state.activeProfileTab)
profile.renderSystemLogs()
studyFacade.renderRawJson(null)
profile.renderProviderOptions()
profile.syncModelProviderDefaults()
profile.renderLearningConfigSummary()
profile.loadAgents()
profile.loadModelConfigs()
profile.loadPromptTemplates()
if (state.token || state.preview) {
  loadInitialData()
}
