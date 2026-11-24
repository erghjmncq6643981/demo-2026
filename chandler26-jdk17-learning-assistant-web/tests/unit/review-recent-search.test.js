import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createReviewFeature } from '../../public/src/features/learning/review/review.js'
import { normalizeExamples } from '../../public/src/shared/vocabulary.js'

vi.mock('/src/shared/audio.js', () => ({
  playUiTone: vi.fn(),
}))

describe('Recent-Search 3-Tag Progressive Review (Plan A)', () => {
  let root
  let elements
  let state
  let mockRequest
  let mockSpeakSentence
  let mockSpeak
  let review

  beforeEach(() => {
    mockSpeakSentence = vi.fn()
    mockSpeak = vi.fn()
    document.body.innerHTML = ''
    root = document.createElement('div')
    root.innerHTML = `
      <section id="reviewHeaderPanel">
        <div id="reviewModeTabs">
          <button type="button" class="review-mode-tab active" data-review-mode="recent" aria-selected="true">近期查词</button>
          <button type="button" class="review-mode-tab" data-review-mode="wordbook" aria-selected="false">词本抽查</button>
        </div>
        <div id="reviewRecentMeta">
          <span id="reviewSourceHint"></span>
          <button type="button" id="refreshRecentReviewBtn">刷新</button>
        </div>
        <div id="reviewWordbookControls" class="hidden">
          <select id="reviewWordbookSelect"></select>
          <input id="reviewLimitInput" value="10" />
          <button type="button" id="reloadReviewBtn">开始复习</button>
        </div>
      </section>
      <div id="reviewLayout">
        <span id="reviewProgressBadge">0 / 0</span>
        <button type="button" id="openReviewQueueModalBtn">词单全览</button>
        <button type="button" id="toggleReviewFocusModeBtn">专注模式</button>
        <div id="reviewFocus"></div>
      </div>
      <section class="modal-backdrop hidden" id="reviewQueueModal">
        <span id="reviewQueueModalCount">0 词</span>
        <span id="reviewQueueSummaryText"></span>
        <div id="reviewQueueFilterGroup">
          <button class="review-queue-filter-btn active" data-queue-filter="all">全部 (0)</button>
          <button class="review-queue-filter-btn" data-queue-filter="pending">待复习 (0)</button>
          <button class="review-queue-filter-btn" data-queue-filter="remembered">今日已复习 (0)</button>
        </div>
        <div id="reviewQueueList"></div>
        <button id="closeReviewQueueModalBtn">×</button>
        <button id="closeReviewQueueModalFooterBtn">关闭</button>
        <button id="exportReviewQueueImageFooterBtn">📷 导出长图 (PNG)</button>
      </section>
      <section class="modal-backdrop hidden" id="reviewImagePreviewModal">
        <img id="reviewImagePreviewImg" src="" />
        <button id="closeReviewImagePreviewModalBtn">×</button>
        <button id="closeReviewImagePreviewModalFooterBtn">关闭</button>
      </section>
    `
    document.body.appendChild(root)

    elements = {
      reviewHeaderPanel: root.querySelector('#reviewHeaderPanel'),
      reviewModeTabs: root.querySelector('#reviewModeTabs'),
      reviewRecentMeta: root.querySelector('#reviewRecentMeta'),
      reviewSourceHint: root.querySelector('#reviewSourceHint'),
      refreshRecentReviewBtn: root.querySelector('#refreshRecentReviewBtn'),
      reviewWordbookControls: root.querySelector('#reviewWordbookControls'),
      reviewWordbookSelect: root.querySelector('#reviewWordbookSelect'),
      reviewLimitInput: root.querySelector('#reviewLimitInput'),
      reloadReviewBtn: root.querySelector('#reloadReviewBtn'),
      reviewLayout: root.querySelector('#reviewLayout'),
      reviewProgressBadge: root.querySelector('#reviewProgressBadge'),
      openReviewQueueModalBtn: root.querySelector('#openReviewQueueModalBtn'),
      reviewQueueModal: root.querySelector('#reviewQueueModal'),
      reviewQueueModalCount: root.querySelector('#reviewQueueModalCount'),
      reviewQueueSummaryText: root.querySelector('#reviewQueueSummaryText'),
      reviewQueueFilterGroup: root.querySelector('#reviewQueueFilterGroup'),
      reviewQueueList: root.querySelector('#reviewQueueList'),
      closeReviewQueueModalBtn: root.querySelector('#closeReviewQueueModalBtn'),
      closeReviewQueueModalFooterBtn: root.querySelector('#closeReviewQueueModalFooterBtn'),
      exportReviewQueueImageFooterBtn: root.querySelector('#exportReviewQueueImageFooterBtn'),
      reviewImagePreviewModal: root.querySelector('#reviewImagePreviewModal'),
      reviewImagePreviewImg: root.querySelector('#reviewImagePreviewImg'),
      closeReviewImagePreviewModalBtn: root.querySelector('#closeReviewImagePreviewModalBtn'),
      closeReviewImagePreviewModalFooterBtn: root.querySelector('#closeReviewImagePreviewModalFooterBtn'),
      toggleReviewFocusModeBtn: root.querySelector('#toggleReviewFocusModeBtn'),
      reviewFocus: root.querySelector('#reviewFocus'),
    }

    state = {
      activeView: 'reviewView',
      token: 'jwt-token',
      preview: false,
      reviewEntries: [],
      currentReviewIndex: 0,
      currentReviewEntry: null,
      wordbooks: [{ id: 'wb-1', name: '默认词本' }],
      currentWordbookId: 'wb-1',
    }

    mockRequest = vi.fn(async (url) => {
      if (url.startsWith('/api/v1/learning/reviews/recent-searches?')) {
        return {
          mode: 'today_yesterday',
          recentTwoDayCount: 2,
          totalCount: 2,
          hintMessage: '今天与昨天共搜索过 2 个词，趁热巩固一遍',
          items: [
            {
              id: 'lk-1',
              lookupId: 'lk-1',
              entryId: null,
              wordbookId: null,
              term: 'cat',
              normalizedTerm: 'cat',
              lookupSource: 'quick_lookup',
              lookupCount: 3,
              lastLookupTime: new Date().toISOString(),
              parsed: {
                term: 'cat',
                phonetic: { uk: '/kæt/' },
                definitions: [{ part_of_speech: 'noun', meaning: '猫' }],
                cognitive_image: { title: '柔软敏捷的小猫', description: '日常宠物猫' },
                examples: [{ sentence: 'The cat is sleeping.', translation: '猫在睡觉。' }],
              },
            },
            {
              id: 'lk-2',
              lookupId: 'lk-2',
              entryId: 'ent-2',
              wordbookId: 'wb-1',
              term: 'dog',
              normalizedTerm: 'dog',
              lookupSource: 'study',
              lookupCount: 1,
              lastLookupTime: new Date().toISOString(),
              parsed: {
                term: 'dog',
                phonetic: { uk: '/dɒɡ/' },
                definitions: [{ part_of_speech: 'noun', meaning: '狗' }],
              },
            },
          ],
        }
      }
      if (url.startsWith('/api/v1/learning/reviews/due')) {
        return [
          {
            id: 'ent-1',
            wordbookId: 'wb-1',
            term: 'occur',
            phonetic: "/ə'kɜː(r)/",
            meaningText: '发生；出现',
            status: 'unfamiliar',
          },
          {
            id: 'ent-2',
            wordbookId: 'wb-1',
            term: 'ancient',
            phonetic: "/'eɪnʃənt/",
            meaningText: '古老的，远古的',
            status: 'unfamiliar',
          },
        ]
      }
      if (url.startsWith('/api/v1/learning/reviews/reviewed-today')) {
        return []
      }
      if (url === '/api/v1/learning/reviews/recent-searches/submit') {
        return { normalizedTerm: 'cat', reviewStage: 1, masteryScore: 70, nextReviewTime: new Date().toISOString() }
      }
      return {}
    })

    review = createReviewFeature({
      state,
      elements,
      sameId: (a, b) => String(a) === String(b),
      syncCurrentWordbookId: vi.fn(),
      request: mockRequest,
      setLoading: vi.fn(),
      toast: vi.fn(),
      logEvent: vi.fn(),
      loadWordbooks: vi.fn(async () => {}),
      loadWordbookEntries: vi.fn(async () => {}),
      loadActivity: vi.fn(async () => {}),
      setView: vi.fn(),
      renderWordbookEntries: vi.fn(),
      confirmAction: vi.fn(),
      renderNotes: vi.fn(),
      renderProfileMetrics: vi.fn(),
      openEntryTransferModal: vi.fn(),
      formatDateTime: (v) => String(v || ''),
      normalizeDefinitions: (p) => (p?.definitions || []).map((d) => ({ pos: d.part_of_speech, cn: d.meaning })),
      normalizeExamples,
      normalizeArray: (v) => (Array.isArray(v) ? v : []),
      renderMarkdown: (s) => s,
      saveEntry: vi.fn(),
      escapeHtml: (s) => String(s || ''),
      readText: () => '',
      stringifyValue: (v) => String(v || ''),
      renderCollocationMini: () => '',
      speakSentence: mockSpeakSentence,
      speak: mockSpeak,
      setFocusMode: vi.fn(),
      bindStudyTermCards: vi.fn(),
      bindInlineAudio: vi.fn(),
      reviewResultToStatus: (r) => (r === 'remembered' ? 'familiar' : r),
      statusLabel: (s) => s,
    })
  })

  it('loads recent searches by default and shows ultra-clean brief card with 3 tags only (no verbose copy or spoiler)', async () => {
    await review.loadDueReviews()

    expect(mockRequest).toHaveBeenCalledWith('/api/v1/learning/reviews/recent-searches?days=2&minCount=5&fallbackLimit=20')
    expect(elements.reviewFocus.textContent).toContain('cat')
    expect(elements.reviewFocus.textContent).toContain('/kæt/')
    expect(elements.reviewFocus.textContent).toContain('查过 3 次')
    expect(elements.reviewFocus.querySelectorAll('[data-review-tag]')).toHaveLength(3)
    expect(elements.reviewFocus.querySelector('.review-brief-prompt')).toBeNull()
    expect(elements.reviewFocus.querySelector('.review-tag-desc')).toBeNull()
    expect(elements.reviewFocus.querySelector('.typing-board')).toBeNull()
    expect(elements.reviewFocus.textContent).not.toContain('猫')
    expect(elements.reviewFocus.textContent).not.toContain('先凭英文')
    expect(elements.reviewFocus.textContent).not.toContain('免打字秒过')
  })

  it('tag 1 (认识) reveals definitions without typing and submits quick_pass on confirm', async () => {
    await review.loadDueReviews()

    elements.reviewFocus.querySelector('[data-review-tag="known"]').click()
    expect(state.reviewCardPhase).toBe('known_check')
    expect(elements.reviewFocus.textContent).toContain('猫')
    expect(elements.reviewFocus.querySelector('.typing-board')).toBeNull()

    elements.reviewFocus.querySelector('[data-review-confirm-known]').click()
    await new Promise((r) => setTimeout(r, 10))

    expect(mockRequest).toHaveBeenCalledWith(
      '/api/v1/learning/reviews/recent-searches/submit',
      expect.objectContaining({ method: 'POST' }),
    )
    const submitBody = JSON.parse(mockRequest.mock.calls.find((c) => c[0].endsWith('/submit'))[1].body)
    expect(submitBody).toMatchObject({ term: 'cat', result: 'remembered', reviewAction: 'quick_pass' })
    expect(state.reviewEntries).toHaveLength(1)
    expect(state.currentReviewEntry.term).toBe('dog')
    expect(state.reviewCardPhase).toBe('brief')
  })

  it('tag 2 (模糊) shows study definitions first, then opens 1-pass guided copy typing on start', async () => {
    vi.useFakeTimers()
    await review.loadDueReviews()

    elements.reviewFocus.querySelector('[data-review-tag="vague"]').click()
    expect(state.reviewCardPhase).toBe('vague_study')
    expect(elements.reviewFocus.querySelector('.typing-board')).toBeNull()
    expect(elements.reviewFocus.textContent).toContain('猫')
    expect(elements.reviewFocus.querySelector('[data-review-start-vague]')).not.toBeNull()

    elements.reviewFocus.querySelector('[data-review-start-vague]').click()
    expect(state.reviewCardPhase).toBe('vague_copy')
    expect(elements.reviewFocus.querySelector('.typing-board')).not.toBeNull()

    const preventDefault = vi.fn()
    review.handleReviewKeydown({ key: 'c', preventDefault })
    review.handleReviewKeydown({ key: 'a', preventDefault })
    review.handleReviewKeydown({ key: 't', preventDefault })
    await vi.advanceTimersByTimeAsync(220)

    const submitBody = JSON.parse(mockRequest.mock.calls.find((c) => c[0].endsWith('/submit'))[1].body)
    expect(submitBody).toMatchObject({ term: 'cat', result: 'vague', reviewAction: 'guided_copy' })
    expect(state.currentReviewEntry.term).toBe('dog')
    expect(state.reviewEntries[1].term).toBe('cat')
    expect(state.reviewEntries[1].requeued).toBe(true)
    vi.useRealTimers()
  })

  it('pressing Enter in vague_study starts guided copy', async () => {
    await review.loadDueReviews()
    elements.reviewFocus.querySelector('[data-review-tag="vague"]').click()
    expect(state.reviewCardPhase).toBe('vague_study')

    const preventDefault = vi.fn()
    review.handleReviewKeydown({ key: 'Enter', preventDefault })
    expect(preventDefault).toHaveBeenCalled()
    expect(state.reviewCardPhase).toBe('vague_copy')
  })

  it('tag 3 (没印象) shows cognitive image first, then guided copy 1 pass, then transitions to blind recall (Plan 2)', async () => {
    vi.useFakeTimers()
    await review.loadDueReviews()

    elements.reviewFocus.querySelector('[data-review-tag="forgotten"]').click()
    expect(state.reviewCardPhase).toBe('forgot_study')
    expect(elements.reviewFocus.textContent).toContain('柔软敏捷的小猫')
    expect(elements.reviewFocus.querySelector('.typing-board')).toBeNull()
    expect(elements.reviewFocus.querySelector('[data-review-start-forgot]')).not.toBeNull()

    // Step 1: Start copy typing (明文跟敲)
    elements.reviewFocus.querySelector('[data-review-start-forgot]').click()
    expect(state.reviewCardPhase).toBe('forgot_copy')
    expect(elements.reviewFocus.querySelector('h4').textContent).toBe('cat')
    expect(elements.reviewFocus.querySelector('.typing-board')).not.toBeNull()

    const preventDefault = vi.fn()
    review.handleReviewKeydown({ key: 'c', preventDefault })
    review.handleReviewKeydown({ key: 'a', preventDefault })
    review.handleReviewKeydown({ key: 't', preventDefault })
    await vi.advanceTimersByTimeAsync(220)

    // Step 2: Automatically transitions to blind recall (遮挡默写)
    expect(state.reviewCardPhase).toBe('forgot_recall')
    expect(elements.reviewFocus.querySelector('h4').textContent).toBe('•••')
    expect(elements.reviewFocus.querySelector('.typing-board')).not.toBeNull()

    // Step 3: Type in recall mode to complete
    review.handleReviewKeydown({ key: 'c', preventDefault })
    review.handleReviewKeydown({ key: 'a', preventDefault })
    review.handleReviewKeydown({ key: 't', preventDefault })
    await vi.advanceTimersByTimeAsync(220)

    const submitBody = JSON.parse(mockRequest.mock.calls.find((c) => c[0].endsWith('/submit'))[1].body)
    expect(submitBody).toMatchObject({ term: 'cat', result: 'forgotten', reviewAction: 'blind_recall' })
    vi.useRealTimers()
  })

  it('pressing Enter in forgot_study starts forgot_copy', async () => {
    await review.loadDueReviews()
    elements.reviewFocus.querySelector('[data-review-tag="forgotten"]').click()
    expect(state.reviewCardPhase).toBe('forgot_study')

    const preventDefault = vi.fn()
    review.handleReviewKeydown({ key: 'Enter', preventDefault })
    expect(preventDefault).toHaveBeenCalled()
    expect(state.reviewCardPhase).toBe('forgot_copy')
  })

  it('renders example play button in vague_study and plays sentence audio on click', async () => {
    await review.loadDueReviews()
    elements.reviewFocus.querySelector('[data-review-tag="vague"]').click()
    expect(state.reviewCardPhase).toBe('vague_study')

    const playBtn = elements.reviewFocus.querySelector('[data-review-sentence]')
    expect(playBtn).not.toBeNull()
    expect(playBtn.textContent).toBe('▶ 读音')
    expect(playBtn.getAttribute('data-sentence-text')).toBe('The cat is sleeping.')

    playBtn.click()
    expect(mockSpeakSentence).toHaveBeenCalledWith('The cat is sleeping.')
  })

  it('renders review queue modal with list of entries, counts, and active current entry highlight', async () => {
    await review.loadDueReviews()
    review.openReviewQueueModal()

    expect(elements.reviewQueueModal.classList.contains('hidden')).toBe(false)
    expect(elements.reviewQueueModalCount.textContent).toBe('2 词')
    expect(elements.reviewQueueSummaryText.textContent).toContain('共 2 词')
    expect(elements.reviewQueueSummaryText.textContent).toContain('2 个待复习')

    const items = elements.reviewQueueList.querySelectorAll('.review-queue-item')
    expect(items.length).toBe(2)

    // First item is active (cat)
    expect(items[0].classList.contains('active')).toBe(true)
    expect(items[0].querySelector('.review-queue-item-term').textContent).toBe('cat')
    expect(items[0].querySelector('.review-queue-status-tag').textContent).toBe('● 复习中')
    expect(items[0].querySelector('.review-queue-jump-btn').disabled).toBe(true)

    // Second item is pending (dog)
    expect(items[1].classList.contains('active')).toBe(false)
    expect(items[1].querySelector('.review-queue-item-term').textContent).toBe('dog')
    expect(items[1].querySelector('.review-queue-status-tag').textContent).toBe('待复习')
    expect(items[1].querySelector('.review-queue-jump-btn').disabled).toBe(false)
  })

  it('clicking a word row in review queue modal jumps to that entry and closes modal', async () => {
    await review.loadDueReviews()
    expect(state.currentReviewIndex).toBe(0)
    expect(state.currentReviewEntry.term).toBe('cat')

    review.openReviewQueueModal()
    const items = elements.reviewQueueList.querySelectorAll('.review-queue-item')
    // Click on dog row
    items[1].click()

    expect(elements.reviewQueueModal.classList.contains('hidden')).toBe(true)
    expect(state.currentReviewIndex).toBe(1)
    expect(state.currentReviewEntry.term).toBe('dog')
    expect(elements.reviewFocus.querySelector('h4').textContent).toBe('dog')
  })

  it('plays pronunciation audio when clicking audio button in review queue modal', async () => {
    await review.loadDueReviews()
    review.openReviewQueueModal()

    const audioBtns = elements.reviewQueueList.querySelectorAll('[data-queue-term]')
    expect(audioBtns.length).toBe(2)
    audioBtns[1].click()

    expect(mockSpeak).toHaveBeenCalledWith('dog')
  })

  it('triggers opening queue modal via openReviewQueueModalBtn and reviewProgressBadge', async () => {
    await review.loadDueReviews()

    elements.openReviewQueueModalBtn.click()
    expect(elements.reviewQueueModal.classList.contains('hidden')).toBe(false)

    review.closeReviewQueueModal()
    expect(elements.reviewQueueModal.classList.contains('hidden')).toBe(true)

    elements.reviewProgressBadge.click()
    expect(elements.reviewQueueModal.classList.contains('hidden')).toBe(false)
  })

  it('displays phonetic and meaningText correctly in wordbook review queue when parsed is null', async () => {
    await review.loadWordbookDueReviews()
    review.openReviewQueueModal()

    const items = elements.reviewQueueList.querySelectorAll('.review-queue-item')
    expect(items.length).toBe(2)

    // Item 2 is ancient with phonetic and meaningText from summary
    const item2 = items[1]
    expect(item2.querySelector('.review-queue-item-term').textContent).toBe('ancient')
    expect(item2.querySelector('.review-queue-item-phonetic').textContent).toContain("/'eɪnʃənt/")
    expect(item2.querySelector('.review-queue-item-defs').textContent).toContain('古老的，远古的')
    expect(item2.querySelector('.review-queue-item-defs').textContent).not.toBe('暂无释义')
  })

  it('exportReviewQueueImage triggers image generation and preview modal', async () => {
    const origGetContext = HTMLCanvasElement.prototype.getContext
    const origToDataURL = HTMLCanvasElement.prototype.toDataURL
    try {
      HTMLCanvasElement.prototype.getContext = vi.fn(() => ({
        measureText: vi.fn(() => ({ width: 50 })),
        fillText: vi.fn(),
        fillRect: vi.fn(),
        beginPath: vi.fn(),
        moveTo: vi.fn(),
        lineTo: vi.fn(),
        stroke: vi.fn(),
        fill: vi.fn(),
        scale: vi.fn(),
        rect: vi.fn(),
        roundRect: vi.fn(),
        quadraticCurveTo: vi.fn(),
        closePath: vi.fn(),
        createLinearGradient: vi.fn(() => ({ addColorStop: vi.fn() })),
      }))
      HTMLCanvasElement.prototype.toDataURL = vi.fn(() => 'data:image/png;base64,fakePngData')

      await review.loadDueReviews()
      review.exportReviewQueueImage()

      expect(elements.reviewImagePreviewModal.classList.contains('hidden')).toBe(false)
      expect(elements.reviewImagePreviewImg.src).toContain('data:image/png;base64,fakePngData')

      review.closeReviewImagePreviewModal()
      expect(elements.reviewImagePreviewModal.classList.contains('hidden')).toBe(true)
    } finally {
      HTMLCanvasElement.prototype.getContext = origGetContext
      HTMLCanvasElement.prototype.toDataURL = origToDataURL
    }
  })

  it('merges reviewed-today entries and allows filtering by all, pending, and remembered', async () => {
    mockRequest.mockImplementation(async (url) => {
      if (url.startsWith('/api/v1/learning/reviews/due')) {
        return [
          {
            id: 'ent-1',
            wordbookId: 'wb-1',
            term: 'occur',
            phonetic: "/ə'kɜː(r)/",
            meaningText: '发生；出现',
            status: 'unfamiliar',
          },
          {
            id: 'ent-2',
            wordbookId: 'wb-1',
            term: 'ancient',
            phonetic: "/'eɪnʃənt/",
            meaningText: '古老的，远古的',
            status: 'unfamiliar',
          },
        ]
      }
      if (url.startsWith('/api/v1/learning/reviews/reviewed-today')) {
        return [
          {
            id: 'ent-3',
            wordbookId: 'wb-1',
            term: 'bulb',
            phonetic: '/bʌlb/',
            meaningText: '灯泡',
            status: 'familiar',
          },
        ]
      }
      return {}
    })

    await review.loadWordbookDueReviews()

    // 2 due entries for practice
    expect(state.reviewEntries.length).toBe(2)
    // 3 round entries total (2 due + 1 reviewed today)
    expect(state.reviewRoundEntries.length).toBe(3)

    review.openReviewQueueModal()

    const allBtn = elements.reviewQueueFilterGroup.querySelector('[data-queue-filter="all"]')
    const pendingBtn = elements.reviewQueueFilterGroup.querySelector('[data-queue-filter="pending"]')
    const remBtn = elements.reviewQueueFilterGroup.querySelector('[data-queue-filter="remembered"]')

    expect(allBtn.textContent).toBe('全部 (3)')
    expect(pendingBtn.textContent).toBe('待复习 (2)')
    expect(remBtn.textContent).toBe('今日已复习 (1)')

    // By default "all" shows 3 items
    let rows = elements.reviewQueueList.querySelectorAll('.review-queue-item')
    expect(rows.length).toBe(3)
    expect(rows[2].querySelector('.review-queue-item-term').textContent).toBe('bulb')
    expect(rows[2].querySelector('.review-queue-status-tag').textContent).toBe('✓ 今日已掌握')

    // Click "待复习" tab
    pendingBtn.click()
    rows = elements.reviewQueueList.querySelectorAll('.review-queue-item')
    expect(rows.length).toBe(2)
    expect(rows[0].querySelector('.review-queue-item-term').textContent).toBe('occur')
    expect(rows[1].querySelector('.review-queue-item-term').textContent).toBe('ancient')

    // Click "今日已复习" tab
    remBtn.click()
    rows = elements.reviewQueueList.querySelectorAll('.review-queue-item')
    expect(rows.length).toBe(1)
    expect(rows[0].querySelector('.review-queue-item-term').textContent).toBe('bulb')

    // Click "全部" tab
    allBtn.click()
    rows = elements.reviewQueueList.querySelectorAll('.review-queue-item')
    expect(rows.length).toBe(3)
  })
})
