import { hideModal } from '/src/shared/modal.js'

/** 注册跨学习场景的键盘交互，避免应用事件入口承载大量快捷键细节。 */
export function bindKeyboardShortcuts(ctx) {
  const {
    state,
    elements,
    closeQuickLookup,
    openQuickLookup,
    closeMiniQuizModal,
    editCurrentNote,
    toggleStudyNoteDrawer,
    openMiniQuizModal,
    closeStudyNoteDrawer,
    toggleReviewNotePreview,
    toggleReviewNoteDrawer,
    closeReviewNoteModal,
    toggleSceneNotePanel,
    setSceneNoteMode,
    toggleSceneNotePreview,
    closeSceneNotePanel,
    handleReviewKeydown,
    handleSceneChallengeKeydown,
  } = ctx

  document.addEventListener('keydown', (event) => {
    const key = event.key
    const keyLower = String(event.key || '').toLowerCase()
    const code = String(event.code || '')
    const isMetaOrCtrl = Boolean(event.metaKey || event.ctrlKey)
    const activeTag = document.activeElement ? document.activeElement.tagName.toLowerCase() : ''
    const isTyping = ['input', 'textarea', 'select'].includes(activeTag)
    const isNoteShortcut = isMetaOrCtrl && (keyLower === 'e' || code === 'KeyE')

    if (isMetaOrCtrl && (keyLower === 'k' || code === 'KeyK')) {
      event.preventDefault()
      event.stopPropagation()
      if (elements.quickLookupModal && !elements.quickLookupModal.classList.contains('hidden')) closeQuickLookup?.()
      else openQuickLookup?.()
      return
    }
    if (key === 'Escape' && elements.quickLookupModal && !elements.quickLookupModal.classList.contains('hidden')) {
      event.preventDefault(); event.stopPropagation(); closeQuickLookup?.(); return
    }
    if (key === 'Escape' && elements.miniQuizModal && !elements.miniQuizModal.classList.contains('hidden')) {
      event.preventDefault(); event.stopPropagation(); closeMiniQuizModal?.(); return
    }

    const isWordCardModalOpen = Boolean(elements.wordbookCardModal && !elements.wordbookCardModal.classList.contains('hidden'))
    if (isWordCardModalOpen) {
      if (isNoteShortcut) {
        event.preventDefault(); event.stopPropagation()
        if (state.selectedEntry) state.currentNoteEntry = state.selectedEntry
        editCurrentNote?.()
        return
      }
      if (key === 'Escape') {
        const isEditingNote = Boolean(elements.wordbookFocus?.querySelector('.note-editor'))
        event.preventDefault(); event.stopPropagation()
        if (isEditingNote) elements.wordbookFocus?.querySelector('[data-cancel-note]')?.click()
        else hideModal(elements.wordbookCardModal)
        return
      }
    }

    const isStudyView = state.activeView === 'studyView' || elements.studyView?.classList.contains('active')
    if (isStudyView) {
      const isKeyT = keyLower === 't' || code === 'KeyT'
      if (isNoteShortcut) {
        event.preventDefault(); event.stopPropagation(); toggleStudyNoteDrawer?.(); return
      }
      if ((isKeyT && !isTyping) || (event.altKey && isKeyT)) {
        event.preventDefault(); event.stopPropagation(); openMiniQuizModal?.(); return
      }
      if (key === 'Escape') {
        const isDrawerOpen = Boolean(elements.studyNoteDrawer && !elements.studyNoteDrawer.classList.contains('hidden'))
        if (isDrawerOpen) {
          event.preventDefault(); event.stopPropagation()
          const isEditing = Boolean(elements.studyNoteDrawer.querySelector('.note-editor'))
          if (isEditing) elements.studyNoteDrawer.querySelector('[data-cancel-note]')?.click()
          else closeStudyNoteDrawer?.()
          return
        }
      }
    }

    const isReviewView = state.activeView === 'reviewView' || elements.reviewView?.classList.contains('active')
    if (isReviewView) {
      const isReviewDrawerOpen = Boolean(elements.reviewNoteModal && !elements.reviewNoteModal.classList.contains('hidden'))
      if (isNoteShortcut) {
        event.preventDefault(); event.stopPropagation()
        if (isReviewDrawerOpen) toggleReviewNotePreview?.()
        else toggleReviewNoteDrawer?.()
        return
      }
      if (key === 'Escape' && isReviewDrawerOpen) {
        event.preventDefault(); event.stopPropagation(); closeReviewNoteModal?.(); return
      }
    }

    const isSceneView = state.activeView === 'scenePlanView' || elements.scenePlanView?.classList.contains('active')
    if (isSceneView) {
      if (isNoteShortcut) {
        event.preventDefault(); event.stopPropagation()
        if (!state.sceneNotePanelOpen) {
          toggleSceneNotePanel?.(true); setSceneNoteMode?.('edit')
        } else toggleSceneNotePreview?.()
        return
      }
      if ((keyLower === 'escape' || code === 'Escape') && state.sceneNotePanelOpen) {
        event.preventDefault(); event.stopPropagation()
        if (state.sceneNoteMode === 'edit') setSceneNoteMode?.('preview')
        else closeSceneNotePanel?.()
        return
      }
    }

    handleReviewKeydown?.(event)
    handleSceneChallengeKeydown?.(event)
  })
}

