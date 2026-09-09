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

  const modalCloseMap = {
    quickLookupModal: () => closeQuickLookup?.(),
    miniQuizModal: () => closeMiniQuizModal?.(),
    sceneCoreWordsModal: () => ctx.closeCoreWordsModal?.(),
    sceneRelatedWordsModal: () => ctx.closeRelatedWordsModal?.(),
    sceneVocabularyPreviewModal: () => ctx.closeSceneVocabularyPreview?.(),
    vocabularyImportModal: () => ctx.closeVocabularyImport?.(),
    scenePlanModal: () => ctx.closeScenePlanModal?.(),
    modelConfigModal: () => ctx.closeModelModal?.(),
    agentModal: () => ctx.closeAgentModal?.(),
    adminUserModal: () => ctx.systemManagement?.closeUserModal?.(),
    learningConfigModal: () => ctx.closeLearningConfigModal?.(),
    templateModal: () => ctx.closeTemplateModal?.(),
    accountModal: () => ctx.closeAccountModal?.(),
    wordbookCardModal: () => hideModal(elements.wordbookCardModal),
    wordbookModal: () => ctx.closeWordbookModal?.(),
    articleStudyModal: () => ctx.closeArticleStudyModal?.(),
    addWordbookModal: () => ctx.closeAddWordbookModal?.(),
    entryTransferModal: () => ctx.closeEntryTransferModal?.(),
    entryStatusModal: () => ctx.closeEntryStatusModal?.(),
    reviewCompleteModal: () => ctx.closeReviewModal?.(),
    forgottenDetailModal: () => ctx.closeForgottenDetailModal?.(),
    aiSessionDetailModal: () => ctx.systemManagement?.closeDetail?.(),
    aiTaskDetailModal: () => hideModal(elements.aiTaskDetailModal),
    deleteConfirmModal: () => ctx.closeDeleteConfirm?.(false),
  }

  function closeTopmostModal(topModal) {
    const modalId = topModal.id
    if (modalId && typeof modalCloseMap[modalId] === 'function') {
      try {
        modalCloseMap[modalId]()
      } catch (err) {
        console.warn('Error invoking modal close handler:', err)
      }
    }

    if (!topModal.classList.contains('hidden')) {
      const closeBtn = topModal.querySelector(
        '#sceneCoreWordsModalCancelBtn, #deleteConfirmCancelBtn, #forgottenBackToReviewBtn, #cancelArticleStudyBtn, ' +
        '#sceneCoreWordsModalCloseBtn, #sceneRelatedWordsModalCloseBtn, #closeSceneVocabularyPreviewBtn, ' +
        '#closeVocabularyImportBtn, #closeScenePlanModalBtn, #closeModelModalBtn, #closeAgentModalBtn, ' +
        '#closeAdminUserModalBtn, #closeLearningConfigModalBtn, #closeTemplateModalBtn, #closeAccountModalBtn, ' +
        '#closeWordbookCardModalBtn, #closeWordbookModalBtn, #closeArticleStudyModalBtn, #closeAddWordbookModalBtn, ' +
        '#closeEntryTransferModalBtn, #closeEntryStatusModalBtn, #closeReviewModalBtn, #closeForgottenDetailModalBtn, ' +
        '#closeAiSessionDetailBtn, #aiTaskDetailCloseBtn, #deleteConfirmCloseBtn, #quickLookupCloseBtn, #miniQuizModalCloseBtn, ' +
        '.panel-heading .icon-button, [data-close-modal], .close-btn, ' +
        'button[title="关闭"], button[aria-label="关闭"], ' +
        'button[id*="Close" i], button[id*="Cancel" i]'
      )
      closeBtn?.click()
    }

    if (!topModal.classList.contains('hidden')) {
      hideModal(topModal)
    }
  }

  const handleKeydown = (event) => {
    const key = event.key
    const keyLower = String(event.key || '').toLowerCase()
    const code = String(event.code || '')
    const isMetaOrCtrl = Boolean(event.metaKey || event.ctrlKey)
    const activeTag = document.activeElement ? document.activeElement.tagName.toLowerCase() : ''
    const isTyping = ['input', 'textarea', 'select'].includes(activeTag)
    const isNoteShortcut = isMetaOrCtrl && (keyLower === 'e' || code === 'KeyE')
    const isEscape = key === 'Escape' || keyLower === 'escape' || code === 'Escape'

    if (isMetaOrCtrl && (keyLower === 'k' || code === 'KeyK')) {
      event.preventDefault()
      event.stopPropagation()
      if (elements.quickLookupModal && !elements.quickLookupModal.classList.contains('hidden')) closeQuickLookup?.()
      else openQuickLookup?.()
      return
    }

    if (isEscape) {
      // 1. 日期选择器下拉优先收起
      const openPicker = document.querySelector('.datetime-picker-popover:not(.hidden)')
      if (openPicker) {
        event.preventDefault()
        event.stopPropagation()
        document.querySelectorAll('.datetime-picker-popover:not(.hidden)').forEach((p) => p.classList.add('hidden'))
        return
      }

      // 2. 单词卡片、学习笔记侧边栏、场景笔记中的编辑状态优先退出到预览/取消编辑
      const isWordCardModalOpen = Boolean(elements.wordbookCardModal && !elements.wordbookCardModal.classList.contains('hidden'))
      if (isWordCardModalOpen) {
        const isEditingNote = Boolean(elements.wordbookFocus?.querySelector('.note-editor'))
        if (isEditingNote) {
          event.preventDefault()
          event.stopPropagation()
          elements.wordbookFocus?.querySelector('[data-cancel-note]')?.click()
          return
        }
      }

      const isStudyDrawerOpen = Boolean(elements.studyNoteDrawer && !elements.studyNoteDrawer.classList.contains('hidden'))
      if (isStudyDrawerOpen) {
        const isEditingNote = Boolean(elements.studyNoteDrawer?.querySelector('.note-editor'))
        if (isEditingNote) {
          event.preventDefault()
          event.stopPropagation()
          elements.studyNoteDrawer?.querySelector('[data-cancel-note]')?.click()
          return
        }
      }

      if (state.sceneNotePanelOpen && state.sceneNoteMode === 'edit') {
        event.preventDefault()
        event.stopPropagation()
        setSceneNoteMode?.('preview')
        return
      }

      // 3. 所有弹窗（modal-backdrop）统一支持按 ESC 退出顶层弹窗
      const openModals = Array.from(document.querySelectorAll('.modal-backdrop:not(.hidden)'))
      if (openModals.length > 0) {
        event.preventDefault()
        event.stopPropagation()
        const topModal = openModals[openModals.length - 1]
        closeTopmostModal(topModal)
        return
      }

      // 4. 无弹窗时，按 ESC 关闭侧边抽屉
      if (isStudyDrawerOpen) {
        event.preventDefault()
        event.stopPropagation()
        closeStudyNoteDrawer?.()
        return
      }

      const isReviewDrawerOpen = Boolean(elements.reviewNoteModal && !elements.reviewNoteModal.classList.contains('hidden'))
      if (isReviewDrawerOpen) {
        event.preventDefault()
        event.stopPropagation()
        closeReviewNoteModal?.()
        return
      }

      if (state.sceneNotePanelOpen) {
        event.preventDefault()
        event.stopPropagation()
        closeSceneNotePanel?.()
        return
      }

      // 5. 移动端抽屉导航遮罩
      const isSidebarBackdropOpen = Boolean(elements.sidebarBackdrop && !elements.sidebarBackdrop.classList.contains('hidden'))
      if (isSidebarBackdropOpen) {
        event.preventDefault()
        event.stopPropagation()
        ctx.setSidebarCollapsed?.(true)
        return
      }
    }

    // 若有弹窗处于打开状态，阻止底层学习视图的快捷键（如词卡笔记快捷键、微测或挑战按键）被误触发
    const hasOpenModal = Boolean(document.querySelector('.modal-backdrop:not(.hidden)'))

    const isWordCardModalOpen = Boolean(elements.wordbookCardModal && !elements.wordbookCardModal.classList.contains('hidden'))
    if (isWordCardModalOpen) {
      if (isNoteShortcut) {
        event.preventDefault()
        event.stopPropagation()
        if (state.selectedEntry) state.currentNoteEntry = state.selectedEntry
        editCurrentNote?.()
        return
      }
    }

    if (hasOpenModal) {
      return
    }

    const isStudyView = state.activeView === 'studyView' || elements.studyView?.classList.contains('active')
    if (isStudyView) {
      const isKeyT = keyLower === 't' || code === 'KeyT'
      if (isNoteShortcut) {
        event.preventDefault()
        event.stopPropagation()
        toggleStudyNoteDrawer?.()
        return
      }
      if ((isKeyT && !isTyping) || (event.altKey && isKeyT)) {
        event.preventDefault()
        event.stopPropagation()
        openMiniQuizModal?.()
        return
      }
    }

    const isReviewView = state.activeView === 'reviewView' || elements.reviewView?.classList.contains('active')
    if (isReviewView) {
      const isReviewDrawerOpen = Boolean(elements.reviewNoteModal && !elements.reviewNoteModal.classList.contains('hidden'))
      if (isNoteShortcut) {
        event.preventDefault()
        event.stopPropagation()
        if (isReviewDrawerOpen) toggleReviewNotePreview?.()
        else toggleReviewNoteDrawer?.()
        return
      }
    }

    const isSceneView = state.activeView === 'scenePlanView' || elements.scenePlanView?.classList.contains('active')
    if (isSceneView) {
      if (isNoteShortcut) {
        event.preventDefault()
        event.stopPropagation()
        if (!state.sceneNotePanelOpen) {
          toggleSceneNotePanel?.(true)
          setSceneNoteMode?.('edit')
        } else toggleSceneNotePreview?.()
        return
      }
    }

    handleReviewKeydown?.(event)
    handleSceneChallengeKeydown?.(event)
  }

  document.addEventListener('keydown', handleKeydown)
  return () => {
    document.removeEventListener('keydown', handleKeydown)
  }
}

