import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { bindKeyboardShortcuts } from '../../public/src/app/keyboard-shortcuts.js'

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
      sceneCoreWordsModal: document.getElementById('sceneCoreWordsModal'),
      sceneRelatedWordsModal: document.getElementById('sceneRelatedWordsModal'),
      deleteConfirmModal: document.getElementById('deleteConfirmModal'),
      scenePlanModal: document.getElementById('scenePlanModal'),
      adminUserModal: document.getElementById('adminUserModal'),
      wordbookCardModal: document.getElementById('wordbookCardModal'),
      wordbookFocus: document.getElementById('wordbookFocus'),
      studyNoteDrawer: document.getElementById('studyNoteDrawer'),
      reviewNoteModal: document.getElementById('reviewNoteModal'),
      sceneNotePanel: document.getElementById('sceneNotePanel'),
      sidebarBackdrop: document.getElementById('sidebarBackdrop'),
    }

    ctx = {
      state,
      elements,
      closeCoreWordsModal: vi.fn(() => elements.sceneCoreWordsModal.classList.add('hidden')),
      closeRelatedWordsModal: vi.fn(() => elements.sceneRelatedWordsModal.classList.add('hidden')),
      closeDeleteConfirm: vi.fn((confirmed) => {
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
})
