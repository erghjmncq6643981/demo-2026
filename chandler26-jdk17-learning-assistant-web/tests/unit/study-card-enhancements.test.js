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

  it('renders cognitive image in quick lookup when available', async () => {
    const modal = document.createElement('div')
    const input = document.createElement('input')
    const content = document.createElement('div')

    const quickLookup = createQuickLookupFeature({
      state: {},
      elements: {
        quickLookupModal: modal,
        quickLookupInput: input,
        quickLookupContent: content,
      },
      request: vi.fn().mockResolvedValue({
        term: 'vigor',
        parsed: {
          definitions: [{ pos: 'n.', cn: '精力，活力' }],
          cognitive_image: {
            title: '生机勃发的植物脉动',
            description: '初春嫩芽破土、细胞充满汁液的喷薄生命力',
            usage_note: '常用于形容生理或组织机构充满源源不断的活力',
          },
        },
      }),
      speak: vi.fn(),
      speakSentence: vi.fn(),
      study: vi.fn(),
      setView: vi.fn(),
    })

    await quickLookup.open('vigor')
    const cognitiveCard = content.querySelector('.quick-cognitive-card')
    expect(cognitiveCard).toBeTruthy()
    expect(cognitiveCard.textContent).toContain('生机勃发的植物脉动')
    expect(cognitiveCard.textContent).toContain('初春嫩芽破土')
    expect(cognitiveCard.textContent).toContain('母语认知画像')
  })

  it('saves, renders and switches recent search chips in quick lookup', async () => {
    localStorage.clear()
    const modal = document.createElement('div')
    modal.classList.add('hidden')
    const input = document.createElement('input')
    const content = document.createElement('div')
    const recentBar = document.createElement('div')
    recentBar.classList.add('hidden')
    const recentChips = document.createElement('div')
    const clearBtn = document.createElement('button')

    const request = vi.fn().mockImplementation((url) => {
      const term = url.split('/').pop()
      return Promise.resolve({
        term,
        parsed: {
          definitions: [{ pos: 'n.', cn: `${term} 的释义` }],
        },
      })
    })

    const quickLookup = createQuickLookupFeature({
      state: {},
      elements: {
        quickLookupModal: modal,
        quickLookupInput: input,
        quickLookupContent: content,
        quickLookupRecentBar: recentBar,
        quickLookupRecentChips: recentChips,
        clearQuickRecentBtn: clearBtn,
      },
      request,
      speak: vi.fn(),
      speakSentence: vi.fn(),
      study: vi.fn(),
      setView: vi.fn(),
    })
    quickLookup.bind()

    // 1. Open and lookup "tense"
    await quickLookup.open('tense')
    expect(recentBar.classList.contains('hidden')).toBe(false)
    let chips = recentChips.querySelectorAll('.recent-chip')
    expect(chips.length).toBe(1)
    expect(chips[0].textContent).toContain('tense')
    expect(chips[0].classList.contains('active')).toBe(true)

    // 2. Lookup "vigor"
    await quickLookup.lookup('vigor')
    chips = recentChips.querySelectorAll('.recent-chip')
    expect(chips.length).toBe(2)
    expect(chips[0].textContent).toContain('vigor')
    expect(chips[0].classList.contains('active')).toBe(true)
    expect(chips[1].textContent).toContain('tense')

    // 3. Click "tense" chip to switch back
    chips[1].click()
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(input.value).toBe('tense')

    // 4. Click clearBtn to remove recent searches
    clearBtn.click()
    expect(recentBar.classList.contains('hidden')).toBe(true)
  })

  it('opens node word card mode from galaxy double-click without selecting search input and toggles search on demand', async () => {
    const modal = document.createElement('div')
    modal.classList.add('hidden')
    modal.innerHTML = '<div class="quick-lookup-modal"></div>'
    const dialogPanel = modal.querySelector('.quick-lookup-modal')
    const eyebrow = document.createElement('p')
    const title = document.createElement('h3')
    const context = document.createElement('p')
    context.classList.add('hidden')
    const toggleSearchBtn = document.createElement('button')
    toggleSearchBtn.classList.add('hidden')
    const closeBtn = document.createElement('button')
    const input = document.createElement('input')
    input.select = vi.fn()
    const content = document.createElement('div')

    let resolveLookup = null
    const request = vi.fn(() => new Promise((resolve) => {
      resolveLookup = resolve
    }))

    const quickLookup = createQuickLookupFeature({
      elements: {
        quickLookupModal: modal,
        quickLookupEyebrow: eyebrow,
        quickLookupTitle: title,
        quickLookupContext: context,
        quickLookupToggleSearchBtn: toggleSearchBtn,
        quickLookupCloseBtn: closeBtn,
        quickLookupInput: input,
        quickLookupContent: content,
      },
      request,
      speak: vi.fn(),
      speakSentence: vi.fn(),
      study: vi.fn(),
      setView: vi.fn(),
    })
    quickLookup.bind()

    const pendingOpen = quickLookup.open('oregano', {
      mode: 'node',
      origin: { x: 240, y: 180 },
      scenarioLabel: '🥦 生鲜采购与厨房料理',
      node: {
        id: 'exp-oregano',
        term: 'oregano',
        nodeType: 'expansion',
        dimension: 'attribute',
        status: 'unlearned',
        definition: '牛至',
      },
    })

    expect(modal.classList.contains('hidden')).toBe(false)
    expect(modal.classList.contains('is-node-card-mode')).toBe(true)
    expect(eyebrow.textContent).toBe('Galaxy Word Card')
    expect(title.textContent).toBe('星系节点词卡')
    expect(context.classList.contains('hidden')).toBe(false)
    expect(context.textContent).toContain('生鲜采购与厨房料理')
    expect(context.textContent).toContain('感官属性')
    expect(input.select).not.toHaveBeenCalled()
    expect(content.querySelector('.quick-card-skeleton')).not.toBeNull()
    expect(content.textContent).toContain('牛至')
    expect(dialogPanel.style.getPropertyValue('--modal-origin-x')).not.toBe('')

    resolveLookup({
      term: 'oregano',
      cacheHit: true,
      parsed: { definitions: [{ pos: 'n.', cn: '牛至' }] },
    })
    await pendingOpen

    expect(content.querySelector('.quick-card-skeleton')).toBeNull()
    expect(content.classList.contains('quick-card-content-enter')).toBe(true)

    toggleSearchBtn.click()
    expect(modal.classList.contains('is-node-card-mode')).toBe(false)
    expect(title.textContent).toContain('全局快速查词')
    expect(input.select).toHaveBeenCalledTimes(1)
  })

  it('toggles unified study note drawer with auto-save, preview-first, and isolates cross-view note leakage', async () => {
    const drawer = document.createElement('aside')
    drawer.classList.add('hidden')
    const title = document.createElement('h3')
    const status = document.createElement('span')
    const previewBtn = document.createElement('button')
    const noteInput = document.createElement('textarea')
    noteInput.classList.add('hidden')
    const noteView = document.createElement('div')
    const splitLayout = document.createElement('div')
    const studyLayout = document.createElement('section')
    studyLayout.classList.add('is-empty')

    const abundantEntry = { id: 'entry-1', term: 'abundant', normalizedTerm: 'abundant', note: 'Original note' }
    const reviewBlanketEntry = { id: 'entry-2', term: 'blanket/quilt', normalizedTerm: 'blanket/quilt', note: 'Review blanket note' }

    const state = {
      currentRecord: null,
      currentNoteEntry: null,
      preview: true,
      wordbookEntries: [abundantEntry],
      reviewEntries: [reviewBlanketEntry],
    }

    const setSidebarCollapsed = vi.fn()
    const notes = createStudyNotesFeature({
      state,
      elements: {
        studyLayout,
        studySplitLayout: splitLayout,
        studyNoteDrawer: drawer,
        studyNoteDrawerTitle: title,
        studyNoteStatus: status,
        studyNotePreviewBtn: previewBtn,
        studyNoteInput: noteInput,
        studyNote: noteView,
      },
      request: vi.fn(),
      toast: vi.fn(),
      logEvent: vi.fn(),
      findEntryForRecord: (record) => (record?.normalizedTerm === 'abundant' ? abundantEntry : null),
      renderWordbookEntries: vi.fn(),
      renderWordbookFocus: vi.fn(),
      renderReviewQueue: vi.fn(),
      setSidebarCollapsed,
    })

    // 1. Cross-view isolation: saving a review entry while studyView has no currentRecord does NOT leak into studyNote
    await notes.saveEntry('entry-2', { note: 'Review blanket updated' }, { silent: true })
    notes.openStudyNoteDrawer(false)
    expect(drawer.classList.contains('hidden')).toBe(true)
    expect(setSidebarCollapsed).not.toHaveBeenCalled()
    expect(noteView.innerHTML).not.toContain('Review blanket updated')

    // 2. Sync record for 'abundant' -> opens in preview-first mode with concise title & status and collapses sidebar
    state.currentRecord = { term: 'abundant', normalizedTerm: 'abundant', userEntry: abundantEntry }
    notes.syncStudyNoteForRecord(state.currentRecord)
    notes.openStudyNoteDrawer(false)
    expect(setSidebarCollapsed).toHaveBeenCalledWith(true, { persist: false })
    expect(drawer.classList.contains('hidden')).toBe(false)
    expect(title.textContent).toBe('学习笔记')
    expect(status.textContent).toBe('✓ 已保存')
    expect(previewBtn.textContent).toBe('✏️ 编辑')
    expect(noteView.classList.contains('hidden')).toBe(false)
    expect(noteInput.classList.contains('hidden')).toBe(true)

    // 3. Switch to edit mode, update text, save manually
    notes.toggleStudyNotePreview()
    expect(noteInput.classList.contains('hidden')).toBe(false)
    expect(previewBtn.textContent).toBe('👁️ 预览')

    noteInput.value = 'Updated abundant note'
    notes.handleStudyNoteInput()
    expect(status.textContent).toBe('● 待保存')

    await notes.saveStudyNote(true)
    expect(abundantEntry.note).toBe('Updated abundant note')
    expect(status.textContent).toBe('✓ 已保存')
  })

  it('toggles two-state studyView and hides empty secondary panels', () => {
    const studyLayout = document.createElement('section')
    studyLayout.classList.add('is-empty')
    const studySplitLayout = document.createElement('div')
    studySplitLayout.classList.add('hidden')
    const morphologyPanel = document.createElement('section')
    const morphologyBoard = document.createElement('div')
    const examplesTipsGrid = document.createElement('section')
    const examplesPanel = document.createElement('div')
    const examples = document.createElement('div')
    const memoryTipsPanel = document.createElement('div')
    const memoryTips = document.createElement('div')
    const collocationsRelationsGrid = document.createElement('section')
    const collocationsPanel = document.createElement('div')
    const collocations = document.createElement('div')
    const relationsPanel = document.createElement('div')
    const relationList = document.createElement('div')
    const tagsPanel = document.createElement('section')
    const tagList = document.createElement('div')

    const closeStudyNoteDrawer = vi.fn()
    const syncStudyNoteForRecord = vi.fn()

    const cardFeature = createStudyCardFeature({
      state: { wordbookEntries: [], wordbooks: [] },
      elements: {
        studyLayout,
        studySplitLayout,
        morphologyPanel,
        morphologyBoard,
        examplesTipsGrid,
        examplesPanel,
        examples,
        memoryTipsPanel,
        memoryTips,
        collocationsRelationsGrid,
        collocationsPanel,
        collocations,
        relationsPanel,
        relationList,
        tagsPanel,
        tagList,
      },
      confirmAction: vi.fn(),
      setView: vi.fn(),
      study: vi.fn(),
      renderReviewFocus: vi.fn(),
      findEntryForRecord: vi.fn().mockReturnValue(null),
      renderNotes: vi.fn(),
      closeStudyNoteDrawer,
      syncStudyNoteForRecord,
      speak: vi.fn(),
      speakSentence: vi.fn(),
      request: vi.fn(),
    })

    // Empty state stays centered and hides split layout
    cardFeature.renderRecord(null)
    expect(studyLayout.classList.contains('is-empty')).toBe(true)
    expect(studySplitLayout.classList.contains('hidden')).toBe(true)
    expect(closeStudyNoteDrawer).toHaveBeenCalled()

    // Record with examples only -> reveals studySplitLayout, hides empty panels, marks single-col grid
    cardFeature.renderRecord({
      term: 'cat',
      normalizedTerm: 'cat',
      parsed: {
        definitions: [{ pos: 'n.', cn: '猫', en: 'feline' }],
        examples: [{ sentence: 'The cat slept.', translation: '猫睡着了。' }],
      },
      tags: [],
      relations: [],
    })
    expect(studyLayout.classList.contains('is-empty')).toBe(false)
    expect(studySplitLayout.classList.contains('hidden')).toBe(false)
    expect(morphologyPanel.classList.contains('hidden')).toBe(true)
    expect(examplesPanel.classList.contains('hidden')).toBe(false)
    expect(memoryTipsPanel.classList.contains('hidden')).toBe(true)
    expect(examplesTipsGrid.classList.contains('hidden')).toBe(false)
    expect(examplesTipsGrid.classList.contains('single-col')).toBe(true)
    expect(collocationsRelationsGrid.classList.contains('hidden')).toBe(true)
    expect(tagsPanel.classList.contains('hidden')).toBe(true)
    expect(syncStudyNoteForRecord).toHaveBeenCalled()
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

    const state = { activeView: 'studyView', currentRecord: null }
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

    // 1a. When currentRecord is null in studyView, Cmd+E and T do nothing
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'e', metaKey: true, bubbles: true }))
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 't', bubbles: true }))
    expect(toggleStudyNoteDrawer).not.toHaveBeenCalled()
    expect(openMiniQuizModal).not.toHaveBeenCalled()

    // 1b. With currentRecord in studyView, press 'n' does NOT trigger note, while Cmd+E triggers toggleStudyNoteDrawer
    state.currentRecord = { term: 'abundant', normalizedTerm: 'abundant' }
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

  it('unifies wordbookCardModal inline note editor with auto-save and preview toggle', async () => {
    vi.useFakeTimers()
    try {
      const wordbookCardModal = document.createElement('div')
      const wordbookFocus = document.createElement('div')
      wordbookFocus.innerHTML = `
        <div class="focus-section">
          <div class="panel-heading compact-heading">
            <div class="scene-note-header-main">
              <h3>笔记</h3>
              <span class="scene-note-status saved" data-focus-note-status>✓ 已保存</span>
            </div>
            <button type="button" data-edit-focus-note>✏️ 编辑</button>
          </div>
          <div class="note-view scene-note-preview"><p>Initial wordbook note</p></div>
        </div>
      `
      const entry = { id: 'wb-entry-1', term: 'vigor', normalizedTerm: 'vigor', note: 'Initial wordbook note' }
      const state = {
        preview: true,
        selectedEntry: entry,
        wordbookEntries: [entry],
        reviewEntries: [],
      }
      const notes = createStudyNotesFeature({
        state,
        elements: { wordbookCardModal, wordbookFocus },
        request: vi.fn(),
        toast: vi.fn(),
        logEvent: vi.fn(),
        findEntryForRecord: vi.fn(),
        renderWordbookEntries: vi.fn(),
        renderWordbookFocus: vi.fn(),
        renderReviewQueue: vi.fn(),
      })

      await notes.editCurrentNote()
      const toggleBtn = wordbookFocus.querySelector('[data-edit-focus-note]')
      const statusEl = wordbookFocus.querySelector('[data-focus-note-status]')
      const textarea = wordbookFocus.querySelector('textarea')
      expect(toggleBtn.textContent).toBe('👁️ 预览')
      expect(textarea).toBeTruthy()
      expect(textarea.value).toBe('Initial wordbook note')

      textarea.value = 'Updated wordbook note'
      textarea.dispatchEvent(new Event('input'))
      expect(statusEl.textContent).toBe('● 待保存')

      await vi.advanceTimersByTimeAsync(2500)
      expect(entry.note).toBe('Updated wordbook note')
      expect(statusEl.textContent).toBe('✓ 已保存')

      // Toggling again exits edit mode back to Markdown preview
      await notes.editCurrentNote()
      await Promise.resolve()
      expect(wordbookFocus.querySelector('.note-editor')).toBeNull()
      expect(wordbookFocus.querySelector('.note-view').innerHTML).toContain('Updated wordbook note')
      expect(toggleBtn.textContent).toBe('✏️ 编辑')
    } finally {
      vi.useRealTimers()
    }
  })
})
