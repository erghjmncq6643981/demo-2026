import { describe, expect, it, vi } from 'vitest'
import { createStudyAutocomplete } from '../../public/src/features/learning/study/autocomplete.js'
import { createQuickLookupFeature } from '../../public/src/features/learning/study/quick-lookup.js'
import { createStudyCardFeature } from '../../public/src/features/learning/study/card.js'
import { createStudyNotesFeature } from '../../public/src/features/learning/study/notes.js'
import { bindAppEvents } from '../../public/src/app/events.js'

describe('Study Card Enhancements', () => {
  it('saves and clears recent searches in autocomplete', () => {
    localStorage.clear()
    const autocomplete = createStudyAutocomplete({
      inputElement: document.createElement('input'),
      dropdownElement: document.createElement('div'),
      recentBarElement: document.createElement('div'),
      recentChipsElement: document.createElement('div'),
      clearRecentBtn: document.createElement('button'),
      api: { getSuggestions: vi.fn() },
      onSelectTerm: vi.fn(),
    })

    autocomplete.saveRecentSearch('serendipity')
    autocomplete.saveRecentSearch('compelling')
    const stored = JSON.parse(localStorage.getItem('learning.recentSearches') || '[]')
    expect(stored).toEqual(['compelling', 'serendipity'])
  })

  it('opens and closes quick lookup modal correctly, and navigates to studyView on action', async () => {
    const modal = document.createElement('div')
    modal.classList.add('hidden')
    const input = document.createElement('input')
    const content = document.createElement('div')
    const setView = vi.fn()
    const study = vi.fn()

    const quickLookup = createQuickLookupFeature({
      state: {},
      elements: {
        quickLookupModal: modal,
        quickLookupInput: input,
        quickLookupContent: content,
      },
      request: vi.fn().mockResolvedValue({
        term: 'eloquent',
        cacheHit: true,
        parsed: {
          phonetic: { uk: 'ˈeləkwənt', us: 'ˈeləkwənt' },
          definitions: [{ pos: 'adj.', cn: '雄辩的，有说服力的' }],
        },
      }),
      speak: vi.fn(),
      speakSentence: vi.fn(),
      study,
      confirmAction: vi.fn(),
      setView,
    })

    await quickLookup.open('eloquent')
    expect(modal.classList.contains('hidden')).toBe(false)
    expect(input.value).toBe('eloquent')

    const gotoBtn = content.querySelector('[data-quick-goto]')
    gotoBtn?.click()
    expect(setView).toHaveBeenCalledWith('studyView')
    expect(study).toHaveBeenCalledWith('eloquent')
    expect(modal.classList.contains('hidden')).toBe(true)
  })

  it('renders morphology dissection correctly for affix words', () => {
    const board = document.createElement('div')
    const cardFeature = createStudyCardFeature({
      state: {},
      elements: {
        morphologyBoard: board,
      },
      confirmAction: vi.fn(),
      setView: vi.fn(),
      study: vi.fn(),
      renderReviewFocus: vi.fn(),
      findEntryForRecord: vi.fn().mockReturnValue(null),
      renderNotes: vi.fn(),
      speak: vi.fn(),
      speakSentence: vi.fn(),
      request: vi.fn(),
      toast: vi.fn(),
    })

    cardFeature.renderMorphology('unpredictable', {
      memory_tips: ['前缀 un- (不，无) + 词根 predict (预言) + 后缀 -able (可...的)'],
    })

    expect(board.querySelectorAll('.morphology-chip').length).toBeGreaterThan(0)
    expect(board.textContent).toContain('un')
  })

  it('renders sentence audio button in quick lookup and triggers speakSentence on click', async () => {
    const modal = document.createElement('div')
    const input = document.createElement('input')
    const content = document.createElement('div')
    const speakSentence = vi.fn()

    const quickLookup = createQuickLookupFeature({
      state: {},
      elements: {
        quickLookupModal: modal,
        quickLookupInput: input,
        quickLookupContent: content,
      },
      request: vi.fn().mockResolvedValue({
        term: 'abundant',
        parsed: {
          examples: [
            { sentence: 'The region has abundant natural resources.', translation: '该地区有丰富的自然资源。' },
          ],
        },
      }),
      speak: vi.fn(),
      speakSentence,
      study: vi.fn(),
      setView: vi.fn(),
    })

    await quickLookup.open('abundant')
    const playBtn = content.querySelector('[data-quick-sentence="0"]')
    expect(playBtn).toBeTruthy()
    playBtn?.click()
    expect(speakSentence).toHaveBeenCalledWith('The region has abundant natural resources.')
  })

  it('handles JSON string parsed and catches speakSentence errors gracefully in quick lookup', async () => {
    const modal = document.createElement('div')
    const input = document.createElement('input')
    const content = document.createElement('div')
    const speakSentence = vi.fn().mockImplementation(() => {
      throw new Error('TTS engine crashed')
    })
    const toast = vi.fn()

    const quickLookup = createQuickLookupFeature({
      state: {},
      elements: {
        quickLookupModal: modal,
        quickLookupInput: input,
        quickLookupContent: content,
      },
      request: vi.fn().mockResolvedValue({
        term: 'abandon',
        parsed: JSON.stringify({
          examples: [
            { sentence: 'They had to abandon the plan.', translation: '他们不得不放弃计划。' },
          ],
        }),
      }),
      toast,
      speak: vi.fn(),
      speakSentence,
      study: vi.fn(),
      setView: vi.fn(),
    })

    await quickLookup.open('abandon')
    const playBtn = content.querySelector('[data-quick-sentence="0"]')
    expect(playBtn).toBeTruthy()
    expect(() => playBtn?.click()).not.toThrow()
    expect(speakSentence).toHaveBeenCalledWith('They had to abandon the plan.')
    expect(toast).toHaveBeenCalledWith('播放例句失败')
  })

  it('toggles study note drawer and supports saving note with shortcut', async () => {
    const drawer = document.createElement('aside')
    drawer.classList.add('hidden')
    const backdrop = document.createElement('div')
    backdrop.classList.add('hidden')
    const title = document.createElement('h3')
    const noteView = document.createElement('div')
    const state = {
      currentNoteEntry: { id: 'entry-1', term: 'abundant', note: 'Original note' },
      preview: true,
      wordbookEntries: [{ id: 'entry-1', term: 'abundant', note: 'Original note' }],
      reviewEntries: [],
    }

    // Instantiate notes feature
    const notes = createStudyNotesFeature({
      state,
      elements: {
        studyNoteDrawer: drawer,
        studyNoteDrawerBackdrop: backdrop,
        studyNoteDrawerTitle: title,
        studyNote: noteView,
      },
      request: vi.fn(),
      toast: vi.fn(),
      logEvent: vi.fn(),
      findEntryForRecord: vi.fn(),
      renderWordbookEntries: vi.fn(),
      renderWordbookFocus: vi.fn(),
      renderReviewQueue: vi.fn(),
    })

    notes.openStudyNoteDrawer(true)
    expect(drawer.classList.contains('hidden')).toBe(false)
    expect(backdrop.classList.contains('hidden')).toBe(false)

    const textarea = noteView.querySelector('textarea')
    expect(textarea).toBeTruthy()
    expect(textarea.value).toBe('Original note')

    // Simulate modifying and saving with Cmd+S
    textarea.value = 'Updated note with shortcut'
    const event = new KeyboardEvent('keydown', { key: 's', metaKey: true, bubbles: true })
    textarea.dispatchEvent(event)
    await new Promise((resolve) => setTimeout(resolve, 10))

    expect(state.currentNoteEntry.note).toBe('Updated note with shortcut')
  })

  it('opens and closes mini quiz modal correctly', () => {
    const modal = document.createElement('div')
    modal.classList.add('hidden')
    const board = document.createElement('div')

    const cardFeature = createStudyCardFeature({
      state: { currentRecord: { term: 'serendipity' } },
      elements: {
        miniQuizModal: modal,
        miniQuizBoard: board,
      },
      confirmAction: vi.fn(),
      setView: vi.fn(),
      study: vi.fn(),
      renderReviewFocus: vi.fn(),
      findEntryForRecord: vi.fn().mockReturnValue(null),
      renderNotes: vi.fn(),
      speak: vi.fn(),
      speakSentence: vi.fn(),
      request: vi.fn(),
      toast: vi.fn(),
    })

    cardFeature.openMiniQuizModal()
    expect(modal.classList.contains('hidden')).toBe(false)

    cardFeature.closeMiniQuizModal()
    expect(modal.classList.contains('hidden')).toBe(true)
  })

  it('routes keyboard shortcuts correctly in studyView, wordbook modal, and reviewView', () => {
    const toggleStudyNoteDrawer = vi.fn()
    const openMiniQuizModal = vi.fn()
    const editCurrentNote = vi.fn()
    const toggleReviewNoteDrawer = vi.fn()
    const toggleReviewNotePreview = vi.fn()
    const wordbookCardModal = document.createElement('div')
    wordbookCardModal.classList.add('hidden')
    const wordbookFocus = document.createElement('div')
    const reviewNoteModal = document.createElement('div')
    reviewNoteModal.classList.add('hidden')

    const state = { activeView: 'studyView' }
    const elements = {
      wordbookCardModal,
      wordbookFocus,
      reviewNoteModal,
    }

    bindAppEvents({
      state,
      elements,
      toggleStudyNoteDrawer,
      openMiniQuizModal,
      editCurrentNote,
      toggleReviewNoteDrawer,
      toggleReviewNotePreview,
    })

    // 1. In studyView, press 'n' does NOT trigger note (to prevent follow-typing conflicts), while Cmd+E triggers toggleStudyNoteDrawer
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'n', bubbles: true }))
    expect(toggleStudyNoteDrawer).not.toHaveBeenCalled()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'e', metaKey: true, bubbles: true }))
    expect(toggleStudyNoteDrawer).toHaveBeenCalledTimes(1)

    // In studyView, press 't' triggers openMiniQuizModal
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 't', bubbles: true }))
    expect(openMiniQuizModal).toHaveBeenCalledTimes(1)

    // 2. Open wordbook card modal: press 'n' does NOT trigger note, while Cmd+E triggers editCurrentNote
    wordbookCardModal.classList.remove('hidden')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'n', bubbles: true }))
    expect(editCurrentNote).not.toHaveBeenCalled()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'e', metaKey: true, bubbles: true }))
    expect(editCurrentNote).toHaveBeenCalledTimes(1)
    wordbookCardModal.classList.add('hidden')

    // 3. In reviewView, when drawer closed, press 'n' does NOT trigger note, while Cmd+E triggers toggleReviewNoteDrawer
    state.activeView = 'reviewView'
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'n', bubbles: true }))
    expect(toggleReviewNoteDrawer).not.toHaveBeenCalled()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'e', metaKey: true, bubbles: true }))
    expect(toggleReviewNoteDrawer).toHaveBeenCalledTimes(1)

    // When reviewNoteModal is open, Cmd+E triggers toggleReviewNotePreview
    reviewNoteModal.classList.remove('hidden')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'e', metaKey: true, bubbles: true }))
    expect(toggleReviewNotePreview).toHaveBeenCalledTimes(1)
  })
})
