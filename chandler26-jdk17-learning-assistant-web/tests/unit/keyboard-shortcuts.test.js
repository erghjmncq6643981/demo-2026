import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { bindKeyboardShortcuts } from '../../public/src/app/keyboard-shortcuts.js'
import { showModal, hideModal } from '../../public/src/shared/modal.js'
import { formatShortcutCombo } from '../../public/src/shared/shortcuts.js'

describe('keyboard-shortcuts Escape handling for all modals', () => {
  let state
  let elements
  let ctx
  let unbind

  afterEach(() => {
    unbind?.()
  })

  beforeEach(() => {
    document.body.innerHTML = `
      <section class="modal-backdrop hidden" id="quickLookupModal">
        <div class="panel-heading">
          <button id="quickLookupToggleSearchBtn">换词</button>
          <button id="quickLookupCloseBtn" class="icon-button">×</button>
        </div>
        <form id="quickLookupForm">
          <input id="quickLookupInput" />
        </form>
      </section>
      <section class="modal-backdrop hidden" id="ruminationPracticeModal">
        <div class="config-modal rumination-practice-modal">
          <div id="ruminationPracticeCard">
            <button id="practiceBackToArticleBtn" data-close-modal class="icon-button">×</button>
            <input id="ruminationCopyInput" />
          </div>
        </div>
      </section>
      <section class="modal-backdrop hidden" id="sceneCoreWordsModal">
        <div class="panel-heading">
          <button id="sceneCoreWordsModalCloseBtn" class="icon-button">×</button>
        </div>
        <button id="sceneCoreWordsModalCancelBtn">返回阅读</button>
      </section>
      <section class="modal-backdrop hidden" id="sceneRelatedWordsModal">
        <div class="panel-heading">
          <button id="sceneRelatedWordsModalCloseBtn" class="icon-button">×</button>
        </div>
      </section>
      <section class="modal-backdrop hidden" id="scenePlanModal">
        <div class="panel-heading">
          <button id="closeScenePlanModalBtn" class="icon-button">×</button>
        </div>
        <div class="datetime-picker">
          <div class="datetime-picker-popover hidden"></div>
        </div>
      </section>
      <section class="modal-backdrop hidden" id="adminUserModal">
        <div class="panel-heading">
          <button id="closeAdminUserModalBtn" class="icon-button">×</button>
        </div>
      </section>
      <section class="modal-backdrop hidden" id="wordbookCardModal">
        <div class="panel-heading">
          <button id="wordbookCardPrevBtn">Prev</button>
          <button id="wordbookCardNextBtn">Next</button>
          <button id="closeWordbookCardModalBtn" class="icon-button">×</button>
        </div>
        <div id="wordbookFocus"></div>
      </section>
      <section class="modal-backdrop hidden" id="deleteConfirmModal">
        <div class="panel-heading">
          <button id="deleteConfirmCloseBtn" class="icon-button">×</button>
        </div>
        <button id="deleteConfirmCancelBtn">取消</button>
      </section>
      <section class="modal-backdrop hidden" id="reviewCompleteModal">
        <div id="modalExamples">
          <button data-modal-sentence="0" id="firstExampleAudioBtn">Play</button>
        </div>
        <button data-modal-result="forgotten">忘记了</button>
        <button data-modal-result="vague">有点模糊</button>
        <button data-modal-result="remembered">记住了</button>
        <button id="closeReviewModalBtn">×</button>
      </section>
      <section class="modal-backdrop hidden" id="forgottenDetailModal">
        <div id="forgottenDetailContent">
          <button data-forgotten-word-audio id="forgottenWordAudioBtn">Play</button>
        </div>
        <button id="forgottenBackToReviewBtn">回到复习</button>
        <button id="closeForgottenDetailModalBtn">×</button>
      </section>
      <section class="modal-backdrop hidden" id="shortcutHelpModal">
        <div id="shortcutHelpContent"></div>
        <button id="closeShortcutHelpModalBtn">×</button>
      </section>
      <section class="modal-backdrop hidden" id="ruminationNodeEditModal">
        <input id="nodeModalTermInput" />
        <button id="closeNodeModalBtn">×</button>
      </section>
      <div class="graph-context-menu hidden" id="graphContextMenu"></div>
      <button id="graphUndoBtn"></button>
      <button id="graphRedoBtn"></button>
      <button id="openShortcutHelpBtn">?</button>
      <aside class="panel study-note-panel hidden" id="studyNoteDrawer"></aside>
      <aside class="panel review-note-panel hidden" id="reviewNoteModal"></aside>
      <section class="panel scene-note-panel hidden" id="sceneNotePanel"></section>
      <div id="sidebarBackdrop" class="hidden"></div>
    `

    state = {
      activeView: 'scenePlanView',
      sceneNotePanelOpen: false,
      sceneNoteMode: 'preview',
    }

    elements = {
      quickLookupModal: document.getElementById('quickLookupModal'),
      quickLookupInput: document.getElementById('quickLookupInput'),
      quickLookupToggleSearchBtn: document.getElementById('quickLookupToggleSearchBtn'),
      ruminationPracticeModal: document.getElementById('ruminationPracticeModal'),
      sceneCoreWordsModal: document.getElementById('sceneCoreWordsModal'),
      sceneRelatedWordsModal: document.getElementById('sceneRelatedWordsModal'),
      deleteConfirmModal: document.getElementById('deleteConfirmModal'),
      scenePlanModal: document.getElementById('scenePlanModal'),
      adminUserModal: document.getElementById('adminUserModal'),
      wordbookCardModal: document.getElementById('wordbookCardModal'),
      wordbookCardPrevBtn: document.getElementById('wordbookCardPrevBtn'),
      wordbookCardNextBtn: document.getElementById('wordbookCardNextBtn'),
      wordbookFocus: document.getElementById('wordbookFocus'),
      studyNoteDrawer: document.getElementById('studyNoteDrawer'),
      reviewNoteModal: document.getElementById('reviewNoteModal'),
      sceneNotePanel: document.getElementById('sceneNotePanel'),
      sidebarBackdrop: document.getElementById('sidebarBackdrop'),
      reviewCompleteModal: document.getElementById('reviewCompleteModal'),
      modalExamples: document.getElementById('modalExamples'),
      closeReviewModalBtn: document.getElementById('closeReviewModalBtn'),
      forgottenDetailModal: document.getElementById('forgottenDetailModal'),
      forgottenDetailContent: document.getElementById('forgottenDetailContent'),
      closeForgottenDetailModalBtn: document.getElementById('closeForgottenDetailModalBtn'),
      forgottenBackToReviewBtn: document.getElementById('forgottenBackToReviewBtn'),
      shortcutHelpModal: document.getElementById('shortcutHelpModal'),
      shortcutHelpContent: document.getElementById('shortcutHelpContent'),
      closeShortcutHelpModalBtn: document.getElementById('closeShortcutHelpModalBtn'),
      openShortcutHelpBtn: document.getElementById('openShortcutHelpBtn'),
      graphUndoBtn: document.getElementById('graphUndoBtn'),
      graphRedoBtn: document.getElementById('graphRedoBtn'),
    }

    ctx = {
      state,
      elements,
      submitReview: vi.fn(),
      closeQuickLookup: vi.fn(() => elements.quickLookupModal.classList.add('hidden')),
      openQuickLookup: vi.fn(() => elements.quickLookupModal.classList.remove('hidden')),
      switchQuickLookupToSearch: vi.fn(() => elements.quickLookupModal.classList.remove('is-node-card-mode')),
      closeReviewModal: vi.fn(() => elements.reviewCompleteModal.classList.add('hidden')),
      closeRuminationPracticeModal: vi.fn(() => elements.ruminationPracticeModal.classList.add('hidden')),
      closeForgottenDetailModal: vi.fn(() => elements.forgottenDetailModal.classList.add('hidden')),
      closeCoreWordsModal: vi.fn(() => elements.sceneCoreWordsModal.classList.add('hidden')),
      closeRelatedWordsModal: vi.fn(() => elements.sceneRelatedWordsModal.classList.add('hidden')),
      closeDeleteConfirm: vi.fn((_confirmed) => {
        elements.deleteConfirmModal.classList.add('hidden')
      }),
      closeScenePlanModal: vi.fn(() => elements.scenePlanModal.classList.add('hidden')),
      closeStudyNoteDrawer: vi.fn(() => elements.studyNoteDrawer.classList.add('hidden')),
      closeReviewNoteModal: vi.fn(() => elements.reviewNoteModal.classList.add('hidden')),
      closeSceneNotePanel: vi.fn(() => {
        state.sceneNotePanelOpen = false
        elements.sceneNotePanel.classList.add('hidden')
      }),
      setSceneNoteMode: vi.fn((mode) => { state.sceneNoteMode = mode }),
      setSidebarCollapsed: vi.fn(() => elements.sidebarBackdrop.classList.add('hidden')),
      systemManagement: {
        closeUserModal: vi.fn(() => elements.adminUserModal.classList.add('hidden')),
      },
      handleReviewKeydown: vi.fn(),
      handleSceneChallengeKeydown: vi.fn(),
    }

    unbind = bindKeyboardShortcuts(ctx)
  })

  it('closes sceneCoreWordsModal when Escape is pressed', () => {
    elements.sceneCoreWordsModal.classList.remove('hidden')
    expect(elements.sceneCoreWordsModal.classList.contains('hidden')).toBe(false)

    const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })
    document.dispatchEvent(event)

    expect(ctx.closeCoreWordsModal).toHaveBeenCalled()
    expect(elements.sceneCoreWordsModal.classList.contains('hidden')).toBe(true)
    expect(ctx.handleSceneChallengeKeydown).not.toHaveBeenCalled()
  })

  it('closes sceneRelatedWordsModal when Escape is pressed', () => {
    elements.sceneRelatedWordsModal.classList.remove('hidden')

    const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })
    document.dispatchEvent(event)

    expect(ctx.closeRelatedWordsModal).toHaveBeenCalled()
    expect(elements.sceneRelatedWordsModal.classList.contains('hidden')).toBe(true)
  })

  it('cancels deleteConfirmModal with false when Escape is pressed', () => {
    elements.deleteConfirmModal.classList.remove('hidden')

    const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })
    document.dispatchEvent(event)

    expect(ctx.closeDeleteConfirm).toHaveBeenCalledWith(false)
    expect(elements.deleteConfirmModal.classList.contains('hidden')).toBe(true)
  })

  it('closes adminUserModal via systemManagement when Escape is pressed', () => {
    elements.adminUserModal.classList.remove('hidden')

    const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })
    document.dispatchEvent(event)

    expect(ctx.systemManagement.closeUserModal).toHaveBeenCalled()
    expect(elements.adminUserModal.classList.contains('hidden')).toBe(true)
  })

  it('closes datetime-picker-popover first on Escape, then closes modal on next Escape', () => {
    elements.scenePlanModal.classList.remove('hidden')
    const popover = elements.scenePlanModal.querySelector('.datetime-picker-popover')
    popover.classList.remove('hidden')

    // First ESC: closes popover
    const event1 = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })
    document.dispatchEvent(event1)

    expect(popover.classList.contains('hidden')).toBe(true)
    expect(elements.scenePlanModal.classList.contains('hidden')).toBe(false)
    expect(ctx.closeScenePlanModal).not.toHaveBeenCalled()

    // Second ESC: closes scenePlanModal
    const event2 = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })
    document.dispatchEvent(event2)

    expect(elements.scenePlanModal.classList.contains('hidden')).toBe(true)
    expect(ctx.closeScenePlanModal).toHaveBeenCalled()
  })

  it('cancels note edit mode in wordbookCardModal first on Escape, then closes modal on next Escape', () => {
    elements.wordbookCardModal.classList.remove('hidden')
    const cancelNoteBtn = document.createElement('button')
    cancelNoteBtn.setAttribute('data-cancel-note', 'true')
    const noteEditor = document.createElement('div')
    noteEditor.className = 'note-editor'
    noteEditor.appendChild(cancelNoteBtn)
    elements.wordbookFocus.appendChild(noteEditor)

    const cancelSpy = vi.fn(() => noteEditor.remove())
    cancelNoteBtn.addEventListener('click', cancelSpy)

    // First ESC: cancels note editing
    const event1 = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })
    document.dispatchEvent(event1)

    expect(cancelSpy).toHaveBeenCalled()
    expect(elements.wordbookCardModal.classList.contains('hidden')).toBe(false)

    // Second ESC: closes wordbookCardModal
    const event2 = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })
    document.dispatchEvent(event2)

    expect(elements.wordbookCardModal.classList.contains('hidden')).toBe(true)
  })

  it('closes topmost modal first when multiple modals are stacked', () => {
    elements.wordbookCardModal.classList.remove('hidden')
    elements.deleteConfirmModal.classList.remove('hidden')

    // First ESC: closes topmost modal (deleteConfirmModal)
    const event1 = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })
    document.dispatchEvent(event1)

    expect(ctx.closeDeleteConfirm).toHaveBeenCalledWith(false)
    expect(elements.deleteConfirmModal.classList.contains('hidden')).toBe(true)
    expect(elements.wordbookCardModal.classList.contains('hidden')).toBe(false)

    // Second ESC: closes wordbookCardModal
    const event2 = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })
    document.dispatchEvent(event2)

    expect(elements.wordbookCardModal.classList.contains('hidden')).toBe(true)
  })

  it('closes side drawers on Escape when no modal is open', () => {
    state.sceneNotePanelOpen = true
    elements.sceneNotePanel.classList.remove('hidden')

    const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })
    document.dispatchEvent(event)

    expect(ctx.closeSceneNotePanel).toHaveBeenCalled()
    expect(elements.sceneNotePanel.classList.contains('hidden')).toBe(true)
  })

  it('delegates to handleSceneChallengeKeydown when no modal or drawer is open', () => {
    const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true })
    document.dispatchEvent(event)

    expect(ctx.handleSceneChallengeKeydown).toHaveBeenCalled()
  })

  it('triggers previous / next word navigation on ArrowLeft / ArrowRight when word card modal is open', () => {
    elements.wordbookCardModal.classList.remove('hidden')
    const prevSpy = vi.spyOn(elements.wordbookCardPrevBtn, 'click')
    const nextSpy = vi.spyOn(elements.wordbookCardNextBtn, 'click')

    const leftEvent = new KeyboardEvent('keydown', { key: 'ArrowLeft', code: 'ArrowLeft', bubbles: true })
    document.dispatchEvent(leftEvent)
    expect(prevSpy).toHaveBeenCalledTimes(1)
    expect(nextSpy).not.toHaveBeenCalled()

    const rightEvent = new KeyboardEvent('keydown', { key: 'ArrowRight', code: 'ArrowRight', bubbles: true })
    document.dispatchEvent(rightEvent)
    expect(nextSpy).toHaveBeenCalledTimes(1)
  })

  it('does not trigger card navigation on Arrow keys when typing in an input', () => {
    elements.wordbookCardModal.classList.remove('hidden')
    const prevSpy = vi.spyOn(elements.wordbookCardPrevBtn, 'click')
    const input = document.createElement('input')
    document.body.appendChild(input)
    input.focus()

    const leftEvent = new KeyboardEvent('keydown', { key: 'ArrowLeft', code: 'ArrowLeft', bubbles: true })
    input.dispatchEvent(leftEvent)
    expect(prevSpy).not.toHaveBeenCalled()
    input.remove()
  })

  it('submits forgotten on 1 or ArrowLeft when review complete modal is open', () => {
    state.pendingReviewEntryId = 'word-101'
    elements.reviewCompleteModal.classList.remove('hidden')

    const event1 = new KeyboardEvent('keydown', { key: '1', code: 'Digit1', bubbles: true })
    document.dispatchEvent(event1)
    expect(ctx.submitReview).toHaveBeenCalledWith('word-101', 'forgotten')

    const eventLeft = new KeyboardEvent('keydown', { key: 'ArrowLeft', code: 'ArrowLeft', bubbles: true })
    document.dispatchEvent(eventLeft)
    expect(ctx.submitReview).toHaveBeenCalledWith('word-101', 'forgotten')
  })

  it('submits vague on 2 or ArrowDown when review complete modal is open', () => {
    state.pendingReviewEntryId = 'word-101'
    elements.reviewCompleteModal.classList.remove('hidden')

    const event2 = new KeyboardEvent('keydown', { key: '2', code: 'Digit2', bubbles: true })
    document.dispatchEvent(event2)
    expect(ctx.submitReview).toHaveBeenCalledWith('word-101', 'vague')

    const eventDown = new KeyboardEvent('keydown', { key: 'ArrowDown', code: 'ArrowDown', bubbles: true })
    document.dispatchEvent(eventDown)
    expect(ctx.submitReview).toHaveBeenCalledWith('word-101', 'vague')
  })

  it('submits remembered on 3, Enter, Space or ArrowRight when review complete modal is open', () => {
    state.pendingReviewEntryId = 'word-101'
    elements.reviewCompleteModal.classList.remove('hidden')

    const event3 = new KeyboardEvent('keydown', { key: '3', code: 'Digit3', bubbles: true })
    document.dispatchEvent(event3)
    expect(ctx.submitReview).toHaveBeenCalledWith('word-101', 'remembered')

    const eventEnter = new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', bubbles: true })
    document.dispatchEvent(eventEnter)
    expect(ctx.submitReview).toHaveBeenCalledWith('word-101', 'remembered')

    const eventSpace = new KeyboardEvent('keydown', { key: ' ', code: 'Space', bubbles: true })
    document.dispatchEvent(eventSpace)
    expect(ctx.submitReview).toHaveBeenCalledWith('word-101', 'remembered')

    const eventRight = new KeyboardEvent('keydown', { key: 'ArrowRight', code: 'ArrowRight', bubbles: true })
    document.dispatchEvent(eventRight)
    expect(ctx.submitReview).toHaveBeenCalledWith('word-101', 'remembered')
  })

  it('plays example sentence audio on P when review complete modal is open', () => {
    elements.reviewCompleteModal.classList.remove('hidden')
    const playBtn = document.getElementById('firstExampleAudioBtn')
    const playSpy = vi.spyOn(playBtn, 'click')

    const eventP = new KeyboardEvent('keydown', { key: 'p', code: 'KeyP', bubbles: true })
    document.dispatchEvent(eventP)
    expect(playSpy).toHaveBeenCalled()
  })

  it('returns to review on Enter or Space when forgotten detail modal is open', () => {
    elements.forgottenDetailModal.classList.remove('hidden')

    const eventEnter = new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', bubbles: true })
    document.dispatchEvent(eventEnter)
    expect(ctx.closeForgottenDetailModal).toHaveBeenCalledTimes(1)

    // Re-open modal to test Space key
    elements.forgottenDetailModal.classList.remove('hidden')
    const eventSpace = new KeyboardEvent('keydown', { key: ' ', code: 'Space', bubbles: true })
    document.dispatchEvent(eventSpace)
    expect(ctx.closeForgottenDetailModal).toHaveBeenCalledTimes(2)
  })

  it('plays word audio on P when forgotten detail modal is open', () => {
    elements.forgottenDetailModal.classList.remove('hidden')
    const wordAudioBtn = document.getElementById('forgottenWordAudioBtn')
    const playSpy = vi.spyOn(wordAudioBtn, 'click')

    const eventP = new KeyboardEvent('keydown', { key: 'p', code: 'KeyP', bubbles: true })
    document.dispatchEvent(eventP)
    expect(playSpy).toHaveBeenCalled()
  })

  it('does not leak typing or Enter from quickLookupModal to underlying reviewCompleteModal or forgottenDetailModal', () => {
    state.activeView = 'reviewView'
    state.pendingReviewEntryId = 'word-101'
    elements.reviewCompleteModal.classList.remove('hidden')
    elements.quickLookupModal.classList.remove('hidden')

    const playBtn = document.getElementById('firstExampleAudioBtn')
    const playSpy = vi.spyOn(playBtn, 'click')
    elements.quickLookupInput.focus()

    const eventP = new KeyboardEvent('keydown', { key: 'p', code: 'KeyP', bubbles: true, cancelable: true })
    elements.quickLookupInput.dispatchEvent(eventP)
    expect(playSpy).not.toHaveBeenCalled()
    expect(eventP.defaultPrevented).toBe(false)

    const eventEnter = new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', bubbles: true, cancelable: true })
    elements.quickLookupInput.dispatchEvent(eventEnter)
    expect(ctx.submitReview).not.toHaveBeenCalled()
    expect(eventEnter.defaultPrevented).toBe(false)

    const eventSpace = new KeyboardEvent('keydown', { key: ' ', code: 'Space', bubbles: true, cancelable: true })
    elements.quickLookupInput.dispatchEvent(eventSpace)
    expect(ctx.submitReview).not.toHaveBeenCalled()

    // Even if focus moves out of input inside quickLookupModal (e.g. button or card content), shortcuts must stay isolated
    elements.quickLookupInput.blur()
    const cardLevelP = new KeyboardEvent('keydown', { key: 'p', code: 'KeyP', bubbles: true, cancelable: true })
    document.dispatchEvent(cardLevelP)
    expect(playSpy).not.toHaveBeenCalled()

    // Escape closes quickLookupModal first (higher z-index), leaving reviewCompleteModal open underneath
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    expect(ctx.closeQuickLookup).toHaveBeenCalledTimes(1)
    expect(ctx.closeReviewModal).not.toHaveBeenCalled()
    expect(elements.quickLookupModal.classList.contains('hidden')).toBe(true)
    expect(elements.reviewCompleteModal.classList.contains('hidden')).toBe(false)

    // Once quickLookupModal is closed, shortcuts on reviewCompleteModal resume working
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', bubbles: true, cancelable: true }))
    expect(ctx.submitReview).toHaveBeenCalledWith('word-101', 'remembered')

    // Also verify quickLookupModal blocks forgottenDetailModal underneath
    elements.reviewCompleteModal.classList.add('hidden')
    elements.forgottenDetailModal.classList.remove('hidden')
    elements.quickLookupModal.classList.remove('hidden')
    const wordAudioBtn = document.getElementById('forgottenWordAudioBtn')
    const wordAudioSpy = vi.spyOn(wordAudioBtn, 'click')
    elements.quickLookupInput.focus()

    elements.quickLookupInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'p', code: 'KeyP', bubbles: true, cancelable: true }))
    elements.quickLookupInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', bubbles: true, cancelable: true }))
    expect(wordAudioSpy).not.toHaveBeenCalled()
    expect(ctx.closeForgottenDetailModal).not.toHaveBeenCalled()

    // deleteConfirmModal stays above quickLookupModal on Escape
    elements.deleteConfirmModal.classList.remove('hidden')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    expect(ctx.closeDeleteConfirm).toHaveBeenCalledWith(false)
    expect(elements.quickLookupModal.classList.contains('hidden')).toBe(false)
  })

  it('stacks quickLookupModal strictly above ruminationPracticeModal on Cmd+K and Escape', () => {
    state.activeView = 'articleStudyView'
    elements.ruminationPracticeModal.classList.remove('hidden')
    const copyInput = document.getElementById('ruminationCopyInput')
    copyInput.focus()

    // 在跟敲弹窗中输入框聚焦时按下 Cmd+K，应打开全局查词弹窗且不关闭跟敲弹窗
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', code: 'KeyK', metaKey: true, bubbles: true, cancelable: true }))
    expect(ctx.openQuickLookup).toHaveBeenCalledTimes(1)
    expect(elements.quickLookupModal.classList.contains('hidden')).toBe(false)
    expect(elements.ruminationPracticeModal.classList.contains('hidden')).toBe(false)

    // 第 1 次按 Esc 只关闭顶层的 quickLookupModal，底层跟敲弹窗继续保持打开
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    expect(ctx.closeQuickLookup).toHaveBeenCalledTimes(1)
    expect(ctx.closeRuminationPracticeModal).not.toHaveBeenCalled()
    expect(elements.quickLookupModal.classList.contains('hidden')).toBe(true)
    expect(elements.ruminationPracticeModal.classList.contains('hidden')).toBe(false)

    // 第 2 次按 Esc 才关闭底层的 ruminationPracticeModal
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    expect(ctx.closeRuminationPracticeModal).toHaveBeenCalledTimes(1)
    expect(elements.ruminationPracticeModal.classList.contains('hidden')).toBe(true)
  })

  it('switches to search mode on Cmd+K when quickLookupModal is open in node card mode', () => {
    elements.quickLookupModal.classList.remove('hidden')
    elements.quickLookupModal.classList.add('is-node-card-mode')

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', code: 'KeyK', metaKey: true, bubbles: true, cancelable: true }))
    expect(ctx.switchQuickLookupToSearch).toHaveBeenCalledTimes(1)
    expect(ctx.closeQuickLookup).not.toHaveBeenCalled()
    expect(elements.quickLookupModal.classList.contains('hidden')).toBe(false)
  })

  it('refocuses search input on Cmd+K when search modal is open but input is blurred, and closes when already focused', () => {
    elements.quickLookupModal.classList.remove('hidden')
    elements.quickLookupModal.classList.remove('is-node-card-mode')
    elements.quickLookupInput.blur()

    // 输入框未聚焦时按 Cmd+K：聚焦输入框，不关闭弹窗
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', code: 'KeyK', metaKey: true, bubbles: true, cancelable: true }))
    expect(document.activeElement).toBe(elements.quickLookupInput)
    expect(ctx.closeQuickLookup).not.toHaveBeenCalled()

    // 输入框已聚焦时按 Cmd+K：关闭弹窗
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', code: 'KeyK', metaKey: true, bubbles: true, cancelable: true }))
    expect(ctx.closeQuickLookup).toHaveBeenCalledTimes(1)
  })

  it('preserves native Enter/Space on focused controls and ignores IME composition inside review modals', () => {
    state.pendingReviewEntryId = 'word-202'
    elements.reviewCompleteModal.classList.remove('hidden')
    const forgottenBtn = elements.reviewCompleteModal.querySelector('[data-modal-result="forgotten"]')
    forgottenBtn.focus()

    const enterOnForgotten = new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', bubbles: true, cancelable: true })
    forgottenBtn.dispatchEvent(enterOnForgotten)
    expect(ctx.submitReview).not.toHaveBeenCalled()
    expect(enterOnForgotten.defaultPrevented).toBe(false)

    const spaceOnForgotten = new KeyboardEvent('keydown', { key: ' ', code: 'Space', bubbles: true, cancelable: true })
    forgottenBtn.dispatchEvent(spaceOnForgotten)
    expect(ctx.submitReview).not.toHaveBeenCalled()
    expect(spaceOnForgotten.defaultPrevented).toBe(false)

    // 中文输入法合成阶段不应触发快捷键提交或切题
    forgottenBtn.blur()
    const imeEvent = new KeyboardEvent('keydown', { key: 'Enter', keyCode: 229, isComposing: true, bubbles: true, cancelable: true })
    document.dispatchEvent(imeEvent)
    expect(ctx.submitReview).not.toHaveBeenCalled()
  })

  it('traps Tab focus inside active modal and restores trigger focus via showModal/hideModal on Escape', () => {
    const triggerBtn = document.createElement('button')
    triggerBtn.id = 'modalOpenerBtn'
    document.body.appendChild(triggerBtn)
    triggerBtn.focus()

    showModal(elements.deleteConfirmModal)
    const closeBtn = document.getElementById('deleteConfirmCloseBtn')
    const cancelBtn = document.getElementById('deleteConfirmCancelBtn')
    // jsdom offsetParent 需补丁以模拟可见布局节点
    Object.defineProperty(closeBtn, 'offsetParent', { configurable: true, get: () => document.body })
    Object.defineProperty(cancelBtn, 'offsetParent', { configurable: true, get: () => document.body })

    cancelBtn.focus()
    const tabForward = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true })
    cancelBtn.dispatchEvent(tabForward)
    expect(tabForward.defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(closeBtn)

    const tabBackward = new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true })
    closeBtn.dispatchEvent(tabBackward)
    expect(tabBackward.defaultPrevented).toBe(true)
    expect(document.activeElement).toBe(cancelBtn)

    hideModal(elements.deleteConfirmModal)
    expect(document.activeElement).toBe(triggerBtn)
  })

  it('opens shortcutHelpModal on ? key, formats platform shortcuts, and closes graphContextMenu first on Escape', () => {
    expect(formatShortcutCombo('⌘K', true)).toBe('⌘K')
    expect(formatShortcutCombo('⌘K', false)).toBe('Ctrl+K')
    expect(formatShortcutCombo('⌘⌥N', false)).toBe('Ctrl+Alt+N')

    state.activeView = 'articleStudyView'
    document.dispatchEvent(new KeyboardEvent('keydown', { key: '?', shiftKey: true, bubbles: true, cancelable: true }))
    expect(elements.shortcutHelpModal.classList.contains('hidden')).toBe(false)
    expect(elements.shortcutHelpContent.textContent).toContain('当前页面 · 知识反刍')

    // 关闭帮助弹窗后，验证图谱右键菜单与节点维护弹窗按层关闭
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    expect(elements.shortcutHelpModal.classList.contains('hidden')).toBe(true)

    const contextMenu = document.getElementById('graphContextMenu')
    const nodeModal = document.getElementById('ruminationNodeEditModal')
    nodeModal.classList.remove('hidden')
    contextMenu.classList.remove('hidden')

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    expect(contextMenu.classList.contains('hidden')).toBe(true)
    expect(nodeModal.classList.contains('hidden')).toBe(false)

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }))
    expect(nodeModal.classList.contains('hidden')).toBe(true)
  })

  it('triggers graph undo and redo on Cmd+Z, Cmd+Shift+Z, and Cmd+Y when on rumination view', () => {
    state.activeView = 'ruminationView'
    const undoSpy = vi.fn()
    const redoSpy = vi.fn()
    elements.graphUndoBtn.addEventListener('click', undoSpy)
    elements.graphRedoBtn.addEventListener('click', redoSpy)

    // 1. Cmd+Z -> Undo
    const undoEvent = new KeyboardEvent('keydown', { key: 'z', code: 'KeyZ', metaKey: true, bubbles: true, cancelable: true })
    document.dispatchEvent(undoEvent)
    expect(undoSpy).toHaveBeenCalledTimes(1)
    expect(undoEvent.defaultPrevented).toBe(true)

    // 2. Cmd+Shift+Z -> Redo
    const redoShiftEvent = new KeyboardEvent('keydown', { key: 'z', code: 'KeyZ', metaKey: true, shiftKey: true, bubbles: true, cancelable: true })
    document.dispatchEvent(redoShiftEvent)
    expect(redoSpy).toHaveBeenCalledTimes(1)
    expect(redoShiftEvent.defaultPrevented).toBe(true)

    // 3. Cmd+Y -> Redo
    const redoYEvent = new KeyboardEvent('keydown', { key: 'y', code: 'KeyY', metaKey: true, bubbles: true, cancelable: true })
    document.dispatchEvent(redoYEvent)
    expect(redoSpy).toHaveBeenCalledTimes(2)
    expect(redoYEvent.defaultPrevented).toBe(true)

    // 4. In text input: do not intercept (native text undo respected)
    const textInput = elements.quickLookupInput
    textInput.focus()
    const typingEvent = new KeyboardEvent('keydown', { key: 'z', code: 'KeyZ', metaKey: true, bubbles: true, cancelable: true })
    textInput.dispatchEvent(typingEvent)
    expect(undoSpy).toHaveBeenCalledTimes(1)
  })
})
