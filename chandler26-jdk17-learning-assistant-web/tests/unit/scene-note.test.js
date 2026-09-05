import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { createSceneNote, md5 } from '../../public/src/features/learning/scene-plan/scene-note.js'

describe('MD5 Hashing', () => {
  it('computes standard MD5 hashes accurately', () => {
    expect(md5('')).toBe('d41d8cd98f00b204e9800998ecf8427e')
    expect(md5('hello')).toBe('5d41402abc4b2a76b9719d911017c592')
    expect(md5('学习笔记测试 123')).toBe(md5('学习笔记测试 123'))
    expect(md5('content A')).not.toBe(md5('content B'))
  })
})

describe('Scene Note Smart Auto-Save with MD5, UpdateTime and 15s Idle Refresh', () => {
  let fixture

  function createTestFixture(initialContent = 'Initial Note') {
    const state = {
      sceneNote: { content: initialContent, updateTime: '2026-08-31T10:00:00.000Z', unitId: 'unit-1' },
      sceneNoteMode: 'edit',
      sceneNotePanelOpen: true,
      currentLearningPlan: { id: 'plan-1' },
    }

    const input = {
      value: initialContent,
      focus: vi.fn(),
      classList: { toggle: vi.fn() },
    }

    const preview = {
      innerHTML: '',
      classList: { toggle: vi.fn() },
    }

    const status = {
      textContent: '',
      className: '',
    }

    const elements = {
      sceneNoteInput: input,
      sceneNotePreview: preview,
      sceneNoteStatus: status,
      sceneNotePanel: { classList: { toggle: vi.fn() } },
      sceneStudySplitLayout: { classList: { toggle: vi.fn() } },
      sceneOpenNoteBtnText: { textContent: '' },
      sceneOpenNoteModalBtn: { classList: { toggle: vi.fn() }, title: '' },
      sceneNoteEditBtn: { classList: { toggle: vi.fn() } },
      sceneNotePreviewBtn: { classList: { toggle: vi.fn() } },
    }

    const api = {
      getNote: vi.fn().mockResolvedValue({ content: initialContent, updateTime: '2026-08-31T10:00:00.000Z' }),
      saveNote: vi.fn().mockImplementation(async (planId, unitId, content) => ({
        content,
        updateTime: '2026-08-31T10:05:00.000Z',
      })),
    }

    const activeUnit = vi.fn().mockReturnValue({
      id: 'unit-1',
      planId: 'plan-1',
      note: { content: initialContent, updateTime: '2026-08-31T10:00:00.000Z' },
    })

    const toast = vi.fn()
    const logEvent = vi.fn()

    const sceneNote = createSceneNote({
      state,
      elements,
      api,
      activeUnit,
      sameId: (a, b) => String(a) === String(b),
      toast,
      logEvent,
    })

    return { state, elements, api, activeUnit, toast, logEvent, sceneNote }
  }

  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('skips network save when content MD5 is unchanged', async () => {
    fixture = createTestFixture('Initial Note')
    await fixture.sceneNote.load()

    await fixture.sceneNote.flushSave(true)

    expect(fixture.api.saveNote).not.toHaveBeenCalled()
    expect(fixture.toast).toHaveBeenCalledWith('保存成功')
  })

  it('resets dirty state when user edits and reverts back to original MD5', async () => {
    fixture = createTestFixture('Original')
    await fixture.sceneNote.load()

    fixture.elements.sceneNoteInput.value = 'Original Changed'
    fixture.sceneNote.handleInput()
    expect(fixture.elements.sceneNoteStatus.textContent).toBe('● 待保存')

    fixture.elements.sceneNoteInput.value = 'Original'
    fixture.sceneNote.handleInput()
    expect(fixture.elements.sceneNoteStatus.textContent).toContain('✓ 已自动保存')

    await fixture.sceneNote.flushSave()
    expect(fixture.api.saveNote).not.toHaveBeenCalled()
  })

  it('triggers save after 2.5s debounce and updates MD5 and updateTime', async () => {
    fixture = createTestFixture('Before')
    await fixture.sceneNote.load()

    fixture.elements.sceneNoteInput.value = 'After Modification'
    fixture.sceneNote.handleInput()

    expect(fixture.api.saveNote).not.toHaveBeenCalled()

    // Fast-forward 2.5s debounce
    await vi.advanceTimersByTimeAsync(2500)

    expect(fixture.api.saveNote).toHaveBeenCalledWith('plan-1', 'unit-1', 'After Modification')
    expect(fixture.state.sceneNote.content).toBe('After Modification')
    expect(fixture.state.sceneNote.updateTime).toBe('2026-08-31T10:05:00.000Z')
    expect(fixture.elements.sceneNoteStatus.textContent).toContain('✓ 已自动保存')
  })

  it('never touches or resets input.value during editing save', async () => {
    fixture = createTestFixture('Before')
    await fixture.sceneNote.load()

    // Simulate input is focused
    global.document = { activeElement: fixture.elements.sceneNoteInput }

    fixture.elements.sceneNoteInput.value = 'User is currently typing...'
    fixture.sceneNote.handleInput()

    await fixture.sceneNote.flushSave()

    // Value should still be untouched
    expect(fixture.elements.sceneNoteInput.value).toBe('User is currently typing...')
  })

  it('refreshes preview and flushes save when switching to preview mode', async () => {
    fixture = createTestFixture('Note for Preview')
    await fixture.sceneNote.load()

    fixture.elements.sceneNoteInput.value = '# Title\nSome content'
    fixture.sceneNote.handleInput()

    fixture.sceneNote.setMode('preview')
    await Promise.resolve()

    expect(fixture.state.sceneNoteMode).toBe('preview')
    expect(fixture.elements.sceneNotePreview.innerHTML).toContain('Some content')
    expect(fixture.api.saveNote).toHaveBeenCalledWith('plan-1', 'unit-1', '# Title\nSome content')
  })

  it('supports Cmd+S / Ctrl+S shortcut to immediately save with feedback', async () => {
    fixture = createTestFixture('Before')
    await fixture.sceneNote.load()

    fixture.elements.sceneNoteInput.value = 'New Note Content via Shortcut'
    fixture.sceneNote.handleInput()

    const event = {
      metaKey: true,
      ctrlKey: false,
      key: 's',
      preventDefault: vi.fn(),
      stopPropagation: vi.fn(),
    }

    fixture.sceneNote.handleKeydown(event)
    await Promise.resolve()

    expect(event.preventDefault).toHaveBeenCalled()
    expect(event.stopPropagation).toHaveBeenCalled()
    expect(fixture.api.saveNote).toHaveBeenCalledWith('plan-1', 'unit-1', 'New Note Content via Shortcut')
    expect(fixture.toast).toHaveBeenCalledWith('保存成功')
  })

  it('supports Cmd+E / Ctrl+E shortcut to toggle between edit and preview mode', async () => {
    fixture = createTestFixture('Note text')
    await fixture.sceneNote.load()

    expect(fixture.state.sceneNoteMode).toBe('edit')

    const eventE = {
      metaKey: true,
      ctrlKey: false,
      key: 'e',
      preventDefault: vi.fn(),
      stopPropagation: vi.fn(),
    }
    fixture.sceneNote.handleKeydown(eventE)

    expect(eventE.preventDefault).toHaveBeenCalled()
    expect(eventE.stopPropagation).toHaveBeenCalled()
    expect(fixture.state.sceneNoteMode).toBe('preview')

    // Press again to switch back to edit
    fixture.sceneNote.handleKeydown(eventE)
    expect(fixture.state.sceneNoteMode).toBe('edit')
  })

  it('supports Escape key to switch from edit mode to preview mode', async () => {
    fixture = createTestFixture('Note text')
    await fixture.sceneNote.load()

    fixture.state.sceneNoteMode = 'edit'

    const eventEsc = {
      metaKey: false,
      ctrlKey: false,
      key: 'Escape',
      preventDefault: vi.fn(),
      stopPropagation: vi.fn(),
    }
    fixture.sceneNote.handleKeydown(eventEsc)

    expect(eventEsc.preventDefault).toHaveBeenCalled()
    expect(eventEsc.stopPropagation).toHaveBeenCalled()
    expect(fixture.state.sceneNoteMode).toBe('preview')
  })
})
