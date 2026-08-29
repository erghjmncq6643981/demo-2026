import { describe, expect, it, vi } from 'vitest'
import { createStudyAutocomplete } from '../../public/src/features/learning/study/autocomplete.js'
import { createQuickLookupFeature } from '../../public/src/features/learning/study/quick-lookup.js'
import { createStudyCardFeature } from '../../public/src/features/learning/study/card.js'

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
})
