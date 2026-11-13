import { syncCurrentWordbookId } from '/src/shared/wordbook.js'
import { createRuminationApi } from '/src/features/rumination/rumination-api.js'
import { initRuminationWorkspace } from '/src/features/rumination/rumination-controller.js'

/**
 * 知识反刍（Knowledge Rumination）领域特性入口：
 * 管理词本与场景联动、专注模式、实时 AI 任务同步与反刍图谱生命周期。
 */
export function createRuminationFeature(ctx) {
  const {
    state,
    elements,
    request,
    toast,
    speakSentence,
    stopSpeaking,
    setFocusMode,
    openQuickLookup,
  } = ctx
  const api = createRuminationApi(request)

  let ruminationWorkspace = null
  let loadedWordbookId = null

  function onAiTaskUpdated(event) {
    const detail = event?.detail || {}
    ruminationWorkspace?.handleTaskUpdate?.(detail)
  }

  window.addEventListener('learning:ai-task-updated', onAiTaskUpdated)

  function ensureRuminationWorkspace() {
    if (!ruminationWorkspace && document.getElementById('ruminationGraphCanvas')) {
      ruminationWorkspace = initRuminationWorkspace({
        state,
        api,
        toast,
        speakSentence,
        stopSpeaking,
        openQuickLookup,
      })
    }
    return ruminationWorkspace
  }

  function reloadRuminationWorkspace({ force = false } = {}) {
    const wordbookId = state.currentWordbookId || elements.ruminationWordbookSelect?.value || elements.articleWordbookSelect?.value || ''
    if (!wordbookId) return
    const ws = ensureRuminationWorkspace()
    if (!ws) return
    if (!force && loadedWordbookId === String(wordbookId)) {
      return
    }
    loadedWordbookId = String(wordbookId)
    ws.reload(wordbookId)
  }

  function clearRuminationWorkspace() {
    loadedWordbookId = null
    api.cancelAll?.()
  }

  setTimeout(() => {
    if (state.currentWordbookId) {
      reloadRuminationWorkspace()
    }
  }, 150)

  async function changeRuminationWordbook(wordbookId) {
    syncCurrentWordbookId(state, elements, wordbookId)
    reloadRuminationWorkspace({ force: true })
  }

  function toggleRuminationFocusMode(forceState = null) {
    const next = typeof forceState === 'boolean' ? forceState : !state.ruminationFocusMode
    state.ruminationFocusMode = next
    if (state.articleFocusMode !== undefined) {
      state.articleFocusMode = next
    }
    const viewName = document.getElementById('ruminationView') ? 'ruminationView' : 'articleStudyView'
    setFocusMode?.(viewName, next)
    const toolbar = elements.ruminationToolbar || elements.articleStudyToolbar
    toolbar?.classList.toggle('hidden', next)
    const toggleBtn = elements.toggleRuminationFocusModeBtn || elements.toggleArticleFocusModeBtn
    if (toggleBtn) {
      toggleBtn.textContent = next ? '退出专注' : '专注模式'
      toggleBtn.classList.toggle('active', next)
    }
  }

  function destroy() {
    window.removeEventListener('learning:ai-task-updated', onAiTaskUpdated)
    clearRuminationWorkspace()
    ruminationWorkspace?.destroy?.()
    ruminationWorkspace = null
  }

  return {
    changeRuminationWordbook,
    changeArticleWordbook: changeRuminationWordbook,
    reloadRuminationWorkspace,
    clearRuminationWorkspace,
    closeRuminationPractice: () => ruminationWorkspace?.closePractice?.(),
    setGraphActive: (active) => ruminationWorkspace?.setGraphActive?.(active),
    toggleRuminationFocusMode,
    toggleArticleFocusMode: toggleRuminationFocusMode,
    getWorkspace: () => ruminationWorkspace,
    destroy,
  }
}

// 兼容别名
export const createWordbookArticleFeature = createRuminationFeature
