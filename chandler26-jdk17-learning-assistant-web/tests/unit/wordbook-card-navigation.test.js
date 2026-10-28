import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createWordbookDetailFeature } from '../../public/src/features/vocabulary/wordbook-detail.js'
import { createWordbookProfileFeature } from '../../public/src/features/vocabulary/wordbook.js'

describe('Wordbook Card Continuous Learning Navigation', () => {
  let state
  let elements
  let ctx

  beforeEach(() => {
    document.body.innerHTML = `
      <section class="modal-backdrop hidden" id="wordbookCardModal">
        <div class="panel-heading wordbook-card-modal-header">
          <div class="wordbook-card-header-main">
            <h3 id="wordbookCardModalTitle">单词卡片</h3>
            <span class="badge wordbook-card-progress hidden" id="wordbookCardProgress">0 / 0</span>
          </div>
          <div class="wordbook-card-header-actions">
            <button id="wordbookCardPrevBtn" type="button">上一个</button>
            <button id="wordbookCardNextBtn" type="button">下一个</button>
            <button id="wordbookCardGenerateBtn" type="button">AI 生成词卡</button>
            <button id="closeWordbookCardModalBtn" type="button">×</button>
          </div>
        </div>
        <div id="wordbookFocus" class="wordbook-focus-container"></div>
      </section>
      <div id="wordbookEntryList"></div>
    `

    elements = {
      wordbookCardModal: document.getElementById('wordbookCardModal'),
      wordbookCardModalTitle: document.getElementById('wordbookCardModalTitle'),
      wordbookCardProgress: document.getElementById('wordbookCardProgress'),
      wordbookCardPrevBtn: document.getElementById('wordbookCardPrevBtn'),
      wordbookCardNextBtn: document.getElementById('wordbookCardNextBtn'),
      wordbookCardGenerateBtn: document.getElementById('wordbookCardGenerateBtn'),
      closeWordbookCardModalBtn: document.getElementById('closeWordbookCardModalBtn'),
      wordbookFocus: document.getElementById('wordbookFocus'),
      wordbookEntryList: document.getElementById('wordbookEntryList'),
    }

    state = {
      preview: false,
      token: 'test-token',
      selectedEntry: null,
      wordbookEntries: [
        {
          id: '101',
          term: 'abandon',
          normalizedTerm: 'abandon',
          status: 'NOT_STARTED',
          reviewStage: 0,
          meaningText: '放弃，抛弃',
          parsed: {
            phonetic: { uk: '/əˈbændən/', us: '/əˈbændən/' },
            definitions: [{ pos: 'v.', cn: '放弃，抛弃' }],
            examples: [{ sentence: 'He abandoned the car.', translation: '他弃车而去。' }],
          },
        },
        {
          id: '102',
          term: 'ability',
          normalizedTerm: 'ability',
          status: 'FAMILIAR',
          reviewStage: 1,
          meaningText: '能力',
          // detail not loaded yet
          parsed: null,
        },
        {
          id: '103',
          term: 'abnormal',
          normalizedTerm: 'abnormal',
          status: 'MASTERED',
          reviewStage: 3,
          meaningText: '不正常的',
          parsed: {
            phonetic: { uk: '/æbˈnɔːml/', us: '/æbˈnɔːrml/' },
            definitions: [{ pos: 'adj.', cn: '不正常的' }],
            examples: [{ sentence: 'Abnormal weather.', translation: '异常天气。' }],
          },
        },
      ],
      wordbooks: [],
    }

    ctx = {
      state,
      elements,
      normalizeDefinitions: (parsed) => Array.isArray(parsed?.definitions) ? parsed.definitions : [],
      normalizeExamples: (parsed) => Array.isArray(parsed?.examples) ? parsed.examples : [],
      renderMarkdown: (text) => text || '',
      readText: (item) => typeof item === 'string' ? item : item?.text || '',
      stringifyValue: (v) => String(v || ''),
      tagLabel: (t) => String(t || ''),
      openEntryInReview: vi.fn(),
      editCurrentNote: vi.fn(),
      openEntryStatusModal: vi.fn(),
      speak: vi.fn(),
      speakSentence: vi.fn(),
      bindStudyTermCards: vi.fn(),
      bindInlineAudio: vi.fn(),
      request: vi.fn().mockResolvedValue({}),
      toast: vi.fn(),
      setButtonLoading: vi.fn(),
      sameId: (a, b) => String(a) === String(b),
      renderNotes: vi.fn(),
      setLoading: vi.fn(),
      logEvent: vi.fn(),
      confirmAction: vi.fn(),
      confirmDelete: vi.fn(),
      saveEntry: vi.fn(),
    }
  })

  it('renders word card and synchronizes outer controls properly', () => {
    const detailFeature = createWordbookDetailFeature(ctx)
    state.selectedEntry = state.wordbookEntries[0]
    detailFeature.renderWordbookFocus(state.selectedEntry)

    // Title and progress
    expect(elements.wordbookCardModalTitle.textContent).toBe('单词卡片 · abandon')
    expect(elements.wordbookCardProgress.textContent).toBe('1 / 3')
    expect(elements.wordbookCardProgress.classList.contains('hidden')).toBe(false)

    // Boundaries on first item: prev disabled, next enabled
    expect(elements.wordbookCardPrevBtn.disabled).toBe(true)
    expect(elements.wordbookCardNextBtn.disabled).toBe(false)

    // Outer generate button: since abandon has parsed definitions & examples, text is 重新生成词卡
    expect(elements.wordbookCardGenerateBtn.textContent).toBe('重新生成词卡')
    expect(elements.wordbookCardGenerateBtn.disabled).toBe(false)

    // Inside card focus content: 去复习 is completely removed!
    expect(elements.wordbookFocus.querySelector('[data-open-review]')).toBeNull()
    // No duplicate inline generate button inside head
    expect(elements.wordbookFocus.querySelector('.wordbook-focus-head [data-generate-card]')).toBeNull()
  })

  it('navigates next and previous continuously without closing modal', () => {
    const wordbookFeature = createWordbookProfileFeature(ctx)
    wordbookFeature.selectWordbookEntry(state.wordbookEntries[0])

    expect(state.selectedEntry.id).toBe('101')
    expect(elements.wordbookCardProgress.textContent).toBe('1 / 3')
    expect(elements.wordbookCardPrevBtn.disabled).toBe(true)
    expect(elements.wordbookCardNextBtn.disabled).toBe(false)

    // Navigate to next word (index 1: ability)
    wordbookFeature.navigateWordbookCard(1)
    expect(state.selectedEntry.id).toBe('102')
    expect(elements.wordbookCardModalTitle.textContent).toBe('单词卡片 · ability')
    expect(elements.wordbookCardProgress.textContent).toBe('2 / 3')
    expect(elements.wordbookCardPrevBtn.disabled).toBe(false)
    expect(elements.wordbookCardNextBtn.disabled).toBe(false)
    // ability doesn't have card details yet -> AI 生成词卡
    expect(elements.wordbookCardGenerateBtn.textContent).toBe('AI 生成词卡')

    // Navigate to next word (index 2: abnormal - last item)
    wordbookFeature.navigateWordbookCard(1)
    expect(state.selectedEntry.id).toBe('103')
    expect(elements.wordbookCardProgress.textContent).toBe('3 / 3')
    expect(elements.wordbookCardPrevBtn.disabled).toBe(false)
    expect(elements.wordbookCardNextBtn.disabled).toBe(true)

    // Out-of-bounds next navigation is safely ignored
    wordbookFeature.navigateWordbookCard(1)
    expect(state.selectedEntry.id).toBe('103')
    expect(elements.wordbookCardProgress.textContent).toBe('3 / 3')

    // Navigate back to previous word (index 1: ability)
    wordbookFeature.navigateWordbookCard(-1)
    expect(state.selectedEntry.id).toBe('102')
    expect(elements.wordbookCardProgress.textContent).toBe('2 / 3')
  })

  it('triggers generateCurrentWordbookCard successfully', async () => {
    const wordbookFeature = createWordbookProfileFeature(ctx)
    wordbookFeature.selectWordbookEntry(state.wordbookEntries[0])

    await wordbookFeature.generateCurrentWordbookCard()

    expect(ctx.request).toHaveBeenCalledWith(
      '/api/v1/learning/wordbook-entries/101/generate-card/async?forceRefresh=true',
      { method: 'POST' }
    )
    expect(ctx.toast).toHaveBeenCalledWith(expect.stringContaining('任务已提交'))
  })

  it('navigates seamlessly across pages when clicking next at the end of a page and previous at the start of a page', async () => {
    const page1Entries = [
      {
        id: '201',
        term: 'apple',
        normalizedTerm: 'apple',
        status: 'NOT_STARTED',
        reviewStage: 0,
        meaningText: '苹果',
        parsed: { definitions: [{ pos: 'n.', cn: '苹果' }], examples: [{ sentence: 'Eat an apple.', translation: '吃个苹果。' }] },
      },
      {
        id: '202',
        term: 'banana',
        normalizedTerm: 'banana',
        status: 'FAMILIAR',
        reviewStage: 1,
        meaningText: '香蕉',
        parsed: { definitions: [{ pos: 'n.', cn: '香蕉' }], examples: [{ sentence: 'Yellow banana.', translation: '黄香蕉。' }] },
      },
    ]

    const page2Entries = [
      {
        id: '203',
        term: 'cherry',
        normalizedTerm: 'cherry',
        status: 'MASTERED',
        reviewStage: 2,
        meaningText: '樱桃',
        parsed: { definitions: [{ pos: 'n.', cn: '樱桃' }], examples: [{ sentence: 'Sweet cherry.', translation: '甜樱桃。' }] },
      },
      {
        id: '204',
        term: 'date',
        normalizedTerm: 'date',
        status: 'NOT_STARTED',
        reviewStage: 0,
        meaningText: '红枣',
        parsed: { definitions: [{ pos: 'n.', cn: '红枣' }], examples: [{ sentence: 'Red date.', translation: '红枣。' }] },
      },
    ]

    state.currentWordbookId = 'wb-test'
    state.wordbookEntries = page1Entries
    state.wordbookPage = 1
    state.wordbookPageSize = 2
    state.wordbookTotal = 4

    ctx.request.mockImplementation((url) => {
      if (url.includes('page=2')) {
        return Promise.resolve({ items: page2Entries, total: 4, page: 2 })
      }
      if (url.includes('page=1')) {
        return Promise.resolve({ items: page1Entries, total: 4, page: 1 })
      }
      return Promise.resolve({})
    })

    const wordbookFeature = createWordbookProfileFeature(ctx)
    // Select last item of page 1 (banana, index 1)
    wordbookFeature.selectWordbookEntry(page1Entries[1])

    expect(state.selectedEntry.id).toBe('202')
    expect(elements.wordbookCardProgress.textContent).toBe('2 / 4')
    // Next button must NOT be disabled because there is a page 2!
    expect(elements.wordbookCardNextBtn.disabled).toBe(false)

    // Navigate next -> should transition to page 2 and select cherry (index 0)
    await wordbookFeature.navigateWordbookCard(1)
    expect(state.wordbookPage).toBe(2)
    expect(state.selectedEntry.id).toBe('203')
    expect(elements.wordbookCardModalTitle.textContent).toBe('单词卡片 · cherry')
    expect(elements.wordbookCardProgress.textContent).toBe('3 / 4')
    // Prev button must NOT be disabled because we can go back to page 1!
    expect(elements.wordbookCardPrevBtn.disabled).toBe(false)
    expect(elements.wordbookCardNextBtn.disabled).toBe(false)

    // Navigate previous from first item of page 2 -> should transition back to page 1 and select banana (last item)
    await wordbookFeature.navigateWordbookCard(-1)
    expect(state.wordbookPage).toBe(1)
    expect(state.selectedEntry.id).toBe('202')
    expect(elements.wordbookCardModalTitle.textContent).toBe('单词卡片 · banana')
    expect(elements.wordbookCardProgress.textContent).toBe('2 / 4')
    expect(elements.wordbookCardNextBtn.disabled).toBe(false)
  })

  it('shows loading placeholders instead of empty text when ready card detail is still loading', () => {
    const detailFeature = createWordbookDetailFeature(ctx)
    const entryWithoutParsed = {
      id: '301',
      term: 'afterward',
      normalizedTerm: 'afterward',
      status: 'NOT_STARTED',
      cardStatus: 'ready',
      meaningText: '(某个事件或时间点) 之后，后来',
      phonetic: '/ɑ:ftəwəd/',
      parsed: null,
    }

    detailFeature.renderWordbookFocus(entryWithoutParsed)

    // Should render the term and title
    expect(elements.wordbookCardModalTitle.textContent).toBe('单词卡片 · afterward')

    // Should NOT show "暂无例句" or "暂无关联词" when cardStatus is ready but details loading
    expect(elements.wordbookFocus.innerHTML).not.toContain('暂无例句')
    expect(elements.wordbookFocus.innerHTML).not.toContain('暂无关联词')
    expect(elements.wordbookFocus.innerHTML).toContain('完整例句与深度释义')
    expect(elements.wordbookFocus.innerHTML).toContain('母语认知画像')
    expect(elements.wordbookFocus.innerHTML).toContain('心智画面加载中')
    expect(elements.wordbookFocus.innerHTML).toContain('搭配与短语加载中')
  })

  it('automatically loads word card detail when selecting an entry while card modal is open during audio carousel', async () => {
    const detailedAfterward = {
      id: '302',
      term: 'afterward',
      normalizedTerm: 'afterward',
      cardStatus: 'ready',
      parsed: {
        definitions: [{ pos: 'adv.', cn: '后来，之后' }],
        examples: [{ sentence: 'Shortly afterward, he left.', translation: '不久之后，他离开了。' }],
        collocations: [{ phrase: 'soon afterward', meaning: '不久之后' }],
      },
    }

    ctx.request.mockImplementation((url) => {
      if (url.includes('302')) {
        return Promise.resolve(detailedAfterward)
      }
      return Promise.resolve({})
    })

    const wordbookFeature = createWordbookProfileFeature(ctx)
    const summaryEntry = {
      id: '302',
      term: 'afterward',
      normalizedTerm: 'afterward',
      status: 'NOT_STARTED',
      cardStatus: 'ready',
      meaningText: '后来，之后',
      parsed: null,
    }
    state.wordbookEntries = [summaryEntry]

    // Modal is currently open
    elements.wordbookCardModal.classList.remove('hidden')
    elements.wordbookFocus.scrollTop = 120

    // During audio carousel onRowChange or selectWordbookEntry:
    wordbookFeature.selectWordbookEntry(summaryEntry, { silent: true })

    // Immediately shows loading state while detail request was dispatched
    expect(ctx.request).toHaveBeenCalledWith('/api/v1/learning/wordbook-entries/302')

    // Wait for the async detail to resolve and update the card
    await Promise.resolve()
    await Promise.resolve()

    // Focus card should now display full details from server
    expect(elements.wordbookFocus.innerHTML).toContain('Shortly afterward, he left.')
    expect(elements.wordbookFocus.innerHTML).toContain('soon afterward')
    expect(state.selectedEntry.parsed).toBeDefined()
  })
})
