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
  let review

  beforeEach(() => {
    mockSpeakSentence = vi.fn()
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
        <button type="button" id="toggleReviewFocusModeBtn">专注模式</button>
        <div id="reviewFocus"></div>
      </div>
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
      speak: vi.fn(),
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

  it('tag 3 (没印象) shows cognitive image first, then masks word for blind recall', async () => {
    vi.useFakeTimers()
    await review.loadDueReviews()

    elements.reviewFocus.querySelector('[data-review-tag="forgotten"]').click()
    expect(state.reviewCardPhase).toBe('forgot_study')
    expect(elements.reviewFocus.textContent).toContain('柔软敏捷的小猫')
    expect(elements.reviewFocus.querySelector('.typing-board')).toBeNull()

    elements.reviewFocus.querySelector('[data-review-start-recall]').click()
    expect(state.reviewCardPhase).toBe('forgot_recall')
    expect(elements.reviewFocus.querySelector('h4').textContent).toBe('•••')
    expect(elements.reviewFocus.querySelector('.typing-board')).not.toBeNull()

    const preventDefault = vi.fn()
    review.handleReviewKeydown({ key: 'c', preventDefault })
    review.handleReviewKeydown({ key: 'a', preventDefault })
    review.handleReviewKeydown({ key: 't', preventDefault })
    await vi.advanceTimersByTimeAsync(220)

    const submitBody = JSON.parse(mockRequest.mock.calls.find((c) => c[0].endsWith('/submit'))[1].body)
    expect(submitBody).toMatchObject({ term: 'cat', result: 'forgotten', reviewAction: 'blind_recall' })
    vi.useRealTimers()
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
})
