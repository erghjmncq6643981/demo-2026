import { getFocusableElements, hideModal, showModal } from '/src/shared/modal.js'
import { getShortcutSections } from '/src/shared/shortcuts.js'
import { escapeHtml } from '/src/shared/text.js'

/** 注册跨学习场景的键盘交互，避免应用事件入口承载大量快捷键细节。 */
export function bindKeyboardShortcuts(ctx) {
  const {
    state,
    elements,
    closeQuickLookup,
    openQuickLookup,
    switchQuickLookupToSearch,
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
    submitReview,
    closeForgottenDetailModal,
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
    ruminationPracticeModal: () => ctx.closeRuminationPracticeModal?.(),
    addWordbookModal: () => ctx.closeAddWordbookModal?.(),
    entryTransferModal: () => ctx.closeEntryTransferModal?.(),
    entryStatusModal: () => ctx.closeEntryStatusModal?.(),
    reviewCompleteModal: () => ctx.closeReviewModal?.(),
    reviewQueueModal: () => ctx.closeReviewQueueModal?.(),
    reviewImagePreviewModal: () => ctx.closeReviewImagePreviewModal?.(),
    forgottenDetailModal: () => ctx.closeForgottenDetailModal?.(),
    aiSessionDetailModal: () => ctx.systemManagement?.closeDetail?.(),
    aiTaskDetailModal: () => hideModal(elements.aiTaskDetailModal),
    shortcutHelpModal: () => hideModal(elements.shortcutHelpModal),
    deleteConfirmModal: () => ctx.closeDeleteConfirm?.(false),
    ruminationNodeEditModal: () => {
      elements.closeNodeModalBtn?.click()
      hideModal(document.getElementById('ruminationNodeEditModal'))
    },
    addRuminationScenarioModal: () => {
      elements.closeScenarioModalBtn?.click()
      hideModal(document.getElementById('addRuminationScenarioModal'))
    },
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
        '#sceneCoreWordsModalCancelBtn, #deleteConfirmCancelBtn, #forgottenBackToReviewBtn, ' +
        '#sceneCoreWordsModalCloseBtn, #sceneRelatedWordsModalCloseBtn, #closeSceneVocabularyPreviewBtn, ' +
        '#closeVocabularyImportBtn, #closeScenePlanModalBtn, #closeModelModalBtn, #closeAgentModalBtn, ' +
        '#closeAdminUserModalBtn, #closeLearningConfigModalBtn, #closeTemplateModalBtn, #closeAccountModalBtn, ' +
        '#closeWordbookCardModalBtn, #closeWordbookModalBtn, #practiceBackToArticleBtn, #closeAddWordbookModalBtn, ' +
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

  const MODAL_Z_INDEX_FALLBACK = {
    deleteConfirmModal: 3000,
    quickLookupModal: 2000,
    shortcutHelpModal: 1800,
    ruminationPracticeModal: 40,
  }
  const KNOWN_ELEMENT_MODAL_KEYS = [
    ...Object.keys(modalCloseMap),
    'addRuminationScenarioModal',
    'ruminationNodeEditModal',
  ]

  function isEditableElement(el) {
    if (!el || typeof el.tagName !== 'string') return false
    const tag = el.tagName.toLowerCase()
    return tag === 'input' || tag === 'textarea' || tag === 'select' || Boolean(el.isContentEditable)
  }

  function isInteractiveControl(el) {
    if (!el || typeof el.tagName !== 'string') return false
    const tag = el.tagName.toLowerCase()
    if (tag === 'button' || tag === 'summary' || tag === 'option') return true
    if (tag === 'a' && el.hasAttribute?.('href')) return true
    const role = String(el.getAttribute?.('role') || '').toLowerCase()
    return ['button', 'link', 'menuitem', 'tab', 'checkbox', 'radio', 'switch'].includes(role)
  }

  function isInteractiveControlFocused(event) {
    return isInteractiveControl(document.activeElement) || isInteractiveControl(event?.target)
  }

  function isTextInputActive(event) {
    return Boolean(
      event?.isComposing ||
      event?.key === 'Process' ||
      event?.keyCode === 229 ||
      isEditableElement(document.activeElement) ||
      isEditableElement(event?.target),
    )
  }

  function renderShortcutHelpModal() {
    const container = elements.shortcutHelpContent || document.getElementById('shortcutHelpContent')
    if (!container) return
    const sections = getShortcutSections(state.activeView)
    container.innerHTML = sections
      .map(
        (sec) => `
          <div class="shortcut-help-section">
            <h4>${escapeHtml(sec.title)}</h4>
            <dl class="shortcut-help-list">
              ${sec.items
                .map(
                  (item) => `
                    <div class="shortcut-help-row">
                      <dt><kbd class="shortcut-help-keys">${escapeHtml(item.keys)}</kbd></dt>
                      <dd>${escapeHtml(item.desc)}</dd>
                    </div>
                  `,
                )
                .join('')}
            </dl>
          </div>
        `,
      )
      .join('')
  }

  function openShortcutHelpModal() {
    const modal = elements.shortcutHelpModal || document.getElementById('shortcutHelpModal')
    if (!modal) return
    renderShortcutHelpModal()
    showModal(modal)
  }

  function trapFocusWithinModal(event, topmostModal) {
    if (!topmostModal || event.defaultPrevented) return
    const focusables = getFocusableElements(topmostModal)
    if (!focusables.length) return
    const active = document.activeElement
    const first = focusables[0]
    const last = focusables[focusables.length - 1]
    if (!topmostModal.contains(active) || active === topmostModal) {
      event.preventDefault()
      ;(event.shiftKey ? last : first).focus?.({ preventScroll: true })
      return
    }
    if (event.shiftKey && active === first) {
      event.preventDefault()
      last.focus?.({ preventScroll: true })
      return
    }
    if (!event.shiftKey && active === last) {
      event.preventDefault()
      first.focus?.({ preventScroll: true })
    }
  }

  function getModalZIndex(modal) {
    if (!modal) return 0
    if (modal === elements.deleteConfirmModal || modal.id === 'deleteConfirmModal') {
      return MODAL_Z_INDEX_FALLBACK.deleteConfirmModal
    }
    if (modal === elements.quickLookupModal || modal.id === 'quickLookupModal') {
      return MODAL_Z_INDEX_FALLBACK.quickLookupModal
    }
    if (modal.id && MODAL_Z_INDEX_FALLBACK[modal.id]) {
      return MODAL_Z_INDEX_FALLBACK[modal.id]
    }
    const rawZ = typeof window !== 'undefined' && typeof window.getComputedStyle === 'function'
      ? Number(window.getComputedStyle(modal).zIndex)
      : NaN
    return Number.isFinite(rawZ) ? rawZ : 40
  }

  function getTopmostOpenModal() {
    const openModals = Array.from(document.querySelectorAll('.modal-backdrop:not(.hidden)'))
    for (const key of KNOWN_ELEMENT_MODAL_KEYS) {
      const el = elements?.[key]
      if (el && el.classList && !el.classList.contains('hidden') && !openModals.includes(el)) {
        openModals.push(el)
      }
    }
    if (!openModals.length) return null
    let topModal = openModals[0]
    let topZ = getModalZIndex(topModal)
    for (let i = 1; i < openModals.length; i += 1) {
      const candidate = openModals[i]
      const candidateZ = getModalZIndex(candidate)
      if (candidateZ >= topZ) {
        topModal = candidate
        topZ = candidateZ
      }
    }
    return topModal
  }

  const handleKeydown = (event) => {
    const key = event.key
    const keyLower = String(event.key || '').toLowerCase()
    const code = String(event.code || '')
    const isMetaOrCtrl = Boolean(event.metaKey || event.ctrlKey)
    const isTyping = isTextInputActive(event)
    const isFocusedControl = isInteractiveControlFocused(event)
    const isNoteShortcut = isMetaOrCtrl && (keyLower === 'e' || code === 'KeyE')
    const isEscape = key === 'Escape' || keyLower === 'escape' || code === 'Escape'
    const isHelpKey = !isMetaOrCtrl && !event.altKey && (key === '?' || (key === '/' && event.shiftKey) || (code === 'Slash' && event.shiftKey))
    const topmostModal = getTopmostOpenModal()
    const hasOpenModal = Boolean(topmostModal)

    if ((key === 'Tab' || code === 'Tab') && topmostModal) {
      trapFocusWithinModal(event, topmostModal)
      return
    }

    if (isMetaOrCtrl && (keyLower === 'k' || code === 'KeyK')) {
      event.preventDefault()
      event.stopPropagation()
      const isQuickLookupOpen = elements.quickLookupModal && !elements.quickLookupModal.classList.contains('hidden')
      const isNodeCardMode = isQuickLookupOpen && elements.quickLookupModal.classList.contains('is-node-card-mode')

      if (isNodeCardMode) {
        if (typeof switchQuickLookupToSearch === 'function') {
          switchQuickLookupToSearch()
        } else if (elements.quickLookupToggleSearchBtn) {
          elements.quickLookupToggleSearchBtn.click()
        }
        return
      }

      if (isQuickLookupOpen) {
        if (elements.quickLookupInput && document.activeElement !== elements.quickLookupInput) {
          elements.quickLookupInput.focus()
          elements.quickLookupInput.select()
          return
        }
        closeQuickLookup?.()
      } else {
        openQuickLookup?.()
      }
      return
    }

    const isUndo = isMetaOrCtrl && !event.shiftKey && (keyLower === 'z' || code === 'KeyZ')
    const isRedo = isMetaOrCtrl && ((event.shiftKey && (keyLower === 'z' || code === 'KeyZ')) || (keyLower === 'y' || code === 'KeyY'))

    if ((isUndo || isRedo) && !isTyping) {
      const ruminationEl = document.getElementById('ruminationView') || document.getElementById('articleStudyView')
      const isRuminationActive = state?.activeView === 'ruminationView'
        || state?.activeView === 'articleStudyView'
        || (ruminationEl && ruminationEl.classList.contains('active'))
      if (isRuminationActive && !hasOpenModal) {
        event.preventDefault()
        event.stopPropagation()
        if (isRedo) {
          const redoBtn = elements.graphRedoBtn || document.getElementById('graphRedoBtn')
          redoBtn?.click()
        } else {
          const undoBtn = elements.graphUndoBtn || document.getElementById('graphUndoBtn')
          undoBtn?.click()
        }
        return
      }
    }

    if (isHelpKey && !isTyping) {
      event.preventDefault()
      event.stopPropagation()
      const helpModal = elements.shortcutHelpModal || document.getElementById('shortcutHelpModal')
      if (helpModal && !helpModal.classList.contains('hidden')) hideModal(helpModal)
      else openShortcutHelpModal()
      return
    }

    if (isEscape) {
      // 1. 日期选择器下拉优先收起（若未被更高层级弹窗遮挡）
      const openPicker = document.querySelector('.datetime-picker-popover:not(.hidden)')
      if (openPicker && (!topmostModal || topmostModal.contains(openPicker))) {
        event.preventDefault()
        event.stopPropagation()
        document.querySelectorAll('.datetime-picker-popover:not(.hidden)').forEach((p) => p.classList.add('hidden'))
        return
      }

      // 1.5 图谱上下文菜单在无更高层级弹窗遮挡时优先由 Esc 收起
      const openGraphMenu = document.getElementById('graphContextMenu')
      if (openGraphMenu && !openGraphMenu.classList.contains('hidden') && (!topmostModal || getModalZIndex(topmostModal) <= 1000)) {
        event.preventDefault()
        event.stopPropagation()
        openGraphMenu.classList.add('hidden')
        return
      }

      // 2. 单词卡片、学习笔记侧边栏、场景笔记中的编辑状态优先退出到预览/取消编辑（仅当前层级处于最顶层时生效）
      const isWordCardTopmost = Boolean(topmostModal && elements.wordbookCardModal && topmostModal === elements.wordbookCardModal)
      if (isWordCardTopmost) {
        const isEditingNote = Boolean(elements.wordbookFocus?.querySelector('.note-editor'))
        if (isEditingNote) {
          event.preventDefault()
          event.stopPropagation()
          elements.wordbookFocus?.querySelector('[data-cancel-note]')?.click()
          return
        }
      }

      const isStudyDrawerOpen = Boolean(elements.studyNoteDrawer && !elements.studyNoteDrawer.classList.contains('hidden'))
      if (!hasOpenModal && isStudyDrawerOpen) {
        const isEditingNote = Boolean(elements.studyNoteDrawer?.querySelector('.note-editor'))
        if (isEditingNote) {
          event.preventDefault()
          event.stopPropagation()
          elements.studyNoteDrawer?.querySelector('[data-cancel-note]')?.click()
          return
        }
      }

      if (!hasOpenModal && state.sceneNotePanelOpen && state.sceneNoteMode === 'edit') {
        event.preventDefault()
        event.stopPropagation()
        setSceneNoteMode?.('preview')
        return
      }

      // 3. 所有弹窗（modal-backdrop）统一支持按 ESC 退出最顶层可见弹窗
      if (topmostModal) {
        event.preventDefault()
        event.stopPropagation()
        closeTopmostModal(topmostModal)
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

    const isWordCardModalOpen = Boolean(topmostModal && elements.wordbookCardModal && topmostModal === elements.wordbookCardModal)
    if (isWordCardModalOpen) {
      if (isNoteShortcut) {
        event.preventDefault()
        event.stopPropagation()
        if (state.selectedEntry) state.currentNoteEntry = state.selectedEntry
        editCurrentNote?.()
        return
      }
      if (!isTyping) {
        if (code === 'ArrowLeft' || key === 'ArrowLeft') {
          event.preventDefault()
          event.stopPropagation()
          elements.wordbookCardPrevBtn?.click()
          return
        }
        if (code === 'ArrowRight' || key === 'ArrowRight') {
          event.preventDefault()
          event.stopPropagation()
          elements.wordbookCardNextBtn?.click()
          return
        }
      }
    }

    const isReviewCompleteModalOpen = Boolean(topmostModal && elements.reviewCompleteModal && topmostModal === elements.reviewCompleteModal)
    if (isReviewCompleteModalOpen) {
      if (isMetaOrCtrl || event.altKey || isTyping) return

      const isEnterOrSpace = key === 'Enter' || code === 'Enter' || code === 'NumpadEnter' || key === ' ' || code === 'Space'
      // 焦点已通过 Tab 落在具体按钮/控件上时，保留该按钮的原生 Enter / Space 行为，绝不误提交为“记住”
      if (isEnterOrSpace && isFocusedControl) {
        return
      }

      const isForgottenKey = key === '1' || code === 'Digit1' || code === 'Numpad1' || key === 'ArrowLeft' || code === 'ArrowLeft'
      const isVagueKey = key === '2' || code === 'Digit2' || code === 'Numpad2' || key === 'ArrowDown' || code === 'ArrowDown'
      const isRememberedKey =
        key === '3' ||
        code === 'Digit3' ||
        code === 'Numpad3' ||
        key === 'Enter' ||
        code === 'Enter' ||
        code === 'NumpadEnter' ||
        key === ' ' ||
        code === 'Space' ||
        key === 'ArrowRight' ||
        code === 'ArrowRight'

      if (isForgottenKey) {
        event.preventDefault()
        event.stopPropagation()
        const entryId = state.pendingReviewEntryId || state.currentReviewEntry?.id
        if (entryId) (submitReview || ctx.submitReview)?.(entryId, 'forgotten')
        return
      }

      if (isVagueKey) {
        event.preventDefault()
        event.stopPropagation()
        const entryId = state.pendingReviewEntryId || state.currentReviewEntry?.id
        if (entryId) (submitReview || ctx.submitReview)?.(entryId, 'vague')
        return
      }

      if (isRememberedKey) {
        event.preventDefault()
        event.stopPropagation()
        const entryId = state.pendingReviewEntryId || state.currentReviewEntry?.id
        if (entryId) (submitReview || ctx.submitReview)?.(entryId, 'remembered')
        return
      }

      if (keyLower === 'p' || code === 'KeyP') {
        event.preventDefault()
        event.stopPropagation()
        elements.modalExamples?.querySelector('[data-modal-sentence]')?.click()
        return
      }
    }

    const isForgottenDetailModalOpen = Boolean(topmostModal && elements.forgottenDetailModal && topmostModal === elements.forgottenDetailModal)
    if (isForgottenDetailModalOpen) {
      if (isMetaOrCtrl || event.altKey || isTyping) return

      const isEnterOrSpace = key === 'Enter' || code === 'Enter' || code === 'NumpadEnter' || key === ' ' || code === 'Space'
      if (isEnterOrSpace && !isFocusedControl) {
        event.preventDefault()
        event.stopPropagation()
        ;(closeForgottenDetailModal || ctx.closeForgottenDetailModal)?.()
        return
      }

      if (keyLower === 'p' || code === 'KeyP') {
        event.preventDefault()
        event.stopPropagation()
        elements.forgottenDetailContent?.querySelector('[data-forgotten-word-audio]')?.click()
        return
      }
    }

    // 新增/编辑表单弹窗统一支持按 Enter 直接提交（多行 textarea 需 Cmd/Ctrl+Enter，聚焦在取消/关闭等按钮上时保留按钮自身行为）
    const isEnterKey = key === 'Enter' || code === 'Enter' || code === 'NumpadEnter'
    if (hasOpenModal && isEnterKey && !event.defaultPrevented && !event.isComposing && event.key !== 'Process' && event.keyCode !== 229 && !isFocusedControl) {
      const modalSubmitSelectorMap = {
        ruminationNodeEditModal: '#submitNodeModalBtn',
        addRuminationScenarioModal: '#submitCreateScenarioBtn',
        wordbookModal: '#createWordbookBtn',
        scenePlanModal: '#createScenePlanBtn',
        modelConfigModal: '#saveModelBtn',
        agentModal: '#saveAgentBtn',
        templateModal: '#saveTemplateBtn',
        adminUserModal: '#saveAdminUserBtn',
        learningConfigModal: '#saveSpeechBtn',
        deleteConfirmModal: '#deleteConfirmSubmitBtn',
      }
      const submitSelector = topmostModal?.id ? modalSubmitSelectorMap[topmostModal.id] : null
      if (submitSelector) {
        const activeTag = document.activeElement?.tagName?.toLowerCase() || event.target?.tagName?.toLowerCase() || ''
        const isInTextarea = activeTag === 'textarea'
        const allowTextareaEnter = topmostModal.id === 'addRuminationScenarioModal' && !event.shiftKey
        if (!isInTextarea || isMetaOrCtrl || allowTextareaEnter) {
          const submitBtn = topmostModal.querySelector(submitSelector) || document.querySelector(submitSelector)
          if (submitBtn && !submitBtn.disabled && !submitBtn.classList.contains('hidden')) {
            event.preventDefault()
            event.stopPropagation()
            submitBtn.click()
            return
          }
        }
      }
    }

    // 若有任何弹窗处于打开状态（包括快速查词等顶层弹窗），阻止底层学习视图的快捷键被误触发
    if (hasOpenModal) {
      return
    }

    const isWordbookView = state.activeView === 'wordbookView' || elements.wordbookView?.classList.contains('active')
    if (isWordbookView && !isWordCardModalOpen && !hasOpenModal && !isTyping) {
      if ((key === ' ' || code === 'Space') && !isFocusedControl) {
        event.preventDefault()
        event.stopPropagation()
        elements.wordbookPlayAudioBtn?.click()
        return
      }
      const isPlayerBarActive = elements.wordbookAudioPlayerBar && !elements.wordbookAudioPlayerBar.classList.contains('hidden')
      if (isPlayerBarActive) {
        if (keyLower === 'j' || code === 'KeyJ') {
          event.preventDefault()
          event.stopPropagation()
          elements.wordbookAudioPrevBtn?.click()
          return
        }
        if (keyLower === 'k' || code === 'KeyK') {
          event.preventDefault()
          event.stopPropagation()
          elements.wordbookAudioNextBtn?.click()
          return
        }
        if (key === 'Escape' || code === 'Escape') {
          event.preventDefault()
          event.stopPropagation()
          elements.wordbookAudioStopBtn?.click()
          return
        }
      }
    }

    const isStudyView = state.activeView === 'studyView' || elements.studyView?.classList.contains('active')
    if (isStudyView) {
      const isKeyT = keyLower === 't' || code === 'KeyT'
      if (isNoteShortcut) {
        event.preventDefault()
        event.stopPropagation()
        const isDrawerOpen = Boolean(elements.studyNoteDrawer && !elements.studyNoteDrawer.classList.contains('hidden'))
        const isEditing = Boolean(elements.studyNoteDrawer?.querySelector('.note-editor'))
        if (isDrawerOpen && !isEditing) {
          editCurrentNote?.()
        } else {
          toggleStudyNoteDrawer?.()
        }
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

  const openBtn = elements.openShortcutHelpBtn || document.getElementById('openShortcutHelpBtn')
  const closeBtn = elements.closeShortcutHelpModalBtn || document.getElementById('closeShortcutHelpModalBtn')
  const helpModalEl = elements.shortcutHelpModal || document.getElementById('shortcutHelpModal')
  const onOpenHelp = () => openShortcutHelpModal()
  const onCloseHelp = () => hideModal(helpModalEl)
  const onBackdropHelp = (e) => {
    if (e.target === helpModalEl) hideModal(helpModalEl)
  }
  openBtn?.addEventListener('click', onOpenHelp)
  closeBtn?.addEventListener('click', onCloseHelp)
  helpModalEl?.addEventListener('click', onBackdropHelp)

  document.addEventListener('keydown', handleKeydown)
  return () => {
    document.removeEventListener('keydown', handleKeydown)
    openBtn?.removeEventListener('click', onOpenHelp)
    closeBtn?.removeEventListener('click', onCloseHelp)
    helpModalEl?.removeEventListener('click', onBackdropHelp)
  }
}

