import { describe, expect, it, vi } from 'vitest'
import { createReviewTypingFeature } from '../../public/src/features/learning/review/typing.js'
import { createReviewFeature } from '../../public/src/features/learning/review/review.js'
import { createReviewModalFeature } from '../../public/src/features/learning/review/modal.js'
import { normalizeExamples } from '../../public/src/shared/vocabulary.js'

vi.mock('/src/shared/audio.js', () => ({
  playUiTone: vi.fn(),
}))

import { playUiTone } from '/src/shared/audio.js'

describe('review typing keydown', () => {
  it('handles safe keydown when event or event.key is undefined or not string', () => {
    const state = {
      activeView: 'reviewView',
      token: 'fake-token',
      currentReviewEntry: { term: 'apple' },
      reviewTyped: '',
    }
    const elements = {}
    const renderReviewFocus = vi.fn()

    const typingFeature = createReviewTypingFeature({
      state,
      elements,
      escapeHtml: (s) => s,
      renderReviewFocus,
    })

    // Should not throw when event is undefined, null, or event.key is undefined
    expect(() => typingFeature.handleReviewKeydown(null)).not.toThrow()
    expect(() => typingFeature.handleReviewKeydown({})).not.toThrow()
    expect(() => typingFeature.handleReviewKeydown({ key: undefined })).not.toThrow()
    expect(() => typingFeature.handleReviewKeydown({ key: null })).not.toThrow()
    expect(() => typingFeature.handleReviewKeydown({ key: 123 })).not.toThrow()
  })

  it('correctly types matching letters and ignores mismatched keys', () => {
    const state = {
      activeView: 'reviewView',
      token: 'fake-token',
      currentReviewEntry: { term: 'apple' },
      reviewTyped: '',
      reviewWrongCount: 0,
    }
    const elements = {}
    const renderReviewFocus = vi.fn()

    const typingFeature = createReviewTypingFeature({
      state,
      elements,
      escapeHtml: (s) => s,
      renderReviewFocus,
    })

    const preventDefault = vi.fn()
    typingFeature.handleReviewKeydown({ key: 'a', preventDefault })
    expect(preventDefault).toHaveBeenCalled()
    expect(state.reviewTyped).toBe('a')
    expect(playUiTone).toHaveBeenCalledWith('correct')

    // Mismatched letter
    typingFeature.handleReviewKeydown({ key: 'x', preventDefault })
    expect(state.reviewTyped).toBe('a')
    expect(state.reviewWrongCount).toBe(1)
    expect(playUiTone).toHaveBeenCalledWith('wrong')

    // Backspace
    typingFeature.handleReviewKeydown({ key: 'Backspace', preventDefault })
    expect(state.reviewTyped).toBe('')
  })

  it('renders typing letters differently in copy vs recall stage', () => {
    const state = { reviewWrongCount: 0 }
    const typingFeature = createReviewTypingFeature({
      state,
      elements: {},
      escapeHtml: (s) => s,
      renderReviewFocus: vi.fn(),
    })

    // Copy stage: visible letters
    const copyHtml = typingFeature.renderTypingLetters('cat', 'c', 'copy', false)
    expect(copyHtml).toContain('>c<')
    expect(copyHtml).toContain('>a<')
    expect(copyHtml).toContain('>t<')
    expect(copyHtml).not.toContain('>•<')

    // Recall stage: untyped letters are masked with •
    const recallHtml = typingFeature.renderTypingLetters('cat', 'c', 'recall', false)
    expect(recallHtml).toContain('>c<')
    expect(recallHtml).toContain('>•<')
    expect(recallHtml).toContain('recall-letter')

    // Recall stage with peek=true: reveals letters
    const peekHtml = typingFeature.renderTypingLetters('cat', 'c', 'recall', true)
    expect(peekHtml).toContain('>c<')
    expect(peekHtml).toContain('>a<')
    expect(peekHtml).toContain('>t<')
    expect(peekHtml).not.toContain('>•<')

    // Recall stage with 3 errors on current letter: reveals weak hint on current letter
    state.reviewWrongCount = 3
    const hintHtml = typingFeature.renderTypingLetters('cat', 'c', 'recall', false)
    expect(hintHtml).toContain('>c<')
    expect(hintHtml).toContain('>a<') // revealed because current index = 1 ('a') and wrongCount >= 3
    expect(hintHtml).toContain('>•<') // 't' is still masked
  })

  it('transitions from copy stage to recall stage, and only opens complete modal after recall stage', () => {
    vi.useFakeTimers()
    const state = {
      activeView: 'reviewView',
      token: 'fake-token',
      currentReviewEntry: { id: '1', term: 'go' },
      reviewTyped: '',
      reviewStage: 'copy',
      reviewWrongCount: 0,
      reviewPeek: false,
    }
    const renderReviewFocus = vi.fn()
    const renderReviewCompleteModal = vi.fn()
    const showCelebration = vi.fn()
    const speak = vi.fn()

    const typingFeature = createReviewTypingFeature({
      state,
      elements: { voiceSelect: { value: 'en-US' } },
      escapeHtml: (s) => s,
      renderReviewFocus,
      renderReviewCompleteModal,
      showCelebration,
      speak,
    })

    const preventDefault = vi.fn()

    // Stage 1: copy typing 'g', 'o'
    typingFeature.handleReviewKeydown({ key: 'g', preventDefault })
    expect(state.reviewTyped).toBe('g')
    typingFeature.handleReviewKeydown({ key: 'o', preventDefault })
    expect(state.reviewTyped).toBe('go')

    // Before timer, stage is still copy
    expect(state.reviewStage).toBe('copy')
    expect(renderReviewCompleteModal).not.toHaveBeenCalled()

    // Advance timer for transition (180ms)
    vi.advanceTimersByTime(200)

    // Should transition to recall stage
    expect(state.reviewStage).toBe('recall')
    expect(state.reviewTyped).toBe('')
    expect(state.reviewWrongCount).toBe(0)
    expect(speak).toHaveBeenCalledWith('go', 'en-US')
    expect(renderReviewCompleteModal).not.toHaveBeenCalled()

    // Stage 2: recall typing 'g', 'o'
    typingFeature.handleReviewKeydown({ key: 'g', preventDefault })
    expect(state.reviewTyped).toBe('g')
    typingFeature.handleReviewKeydown({ key: 'o', preventDefault })
    expect(state.reviewTyped).toBe('go')

    // Advance timer for completion (180ms)
    vi.advanceTimersByTime(200)

    // Complete modal and celebration should now be triggered!
    expect(renderReviewCompleteModal).toHaveBeenCalledWith(state.currentReviewEntry)
    expect(showCelebration).toHaveBeenCalled()
    expect(playUiTone).toHaveBeenCalledWith('success')

    vi.useRealTimers()
  })

  it('handles Tab key in recall stage to peek hints', () => {
    vi.useFakeTimers()
    const state = {
      activeView: 'reviewView',
      token: 'fake-token',
      currentReviewEntry: { id: '1', term: 'test' },
      reviewTyped: '',
      reviewStage: 'recall',
      reviewPeek: false,
    }
    const renderReviewFocus = vi.fn()
    const typingFeature = createReviewTypingFeature({
      state,
      elements: {},
      escapeHtml: (s) => s,
      renderReviewFocus,
    })

    const preventDefault = vi.fn()
    typingFeature.handleReviewKeydown({ key: 'Tab', preventDefault })
    expect(preventDefault).toHaveBeenCalled()
    expect(state.reviewPeek).toBe(true)

    // Auto resets after 1200ms
    vi.advanceTimersByTime(1250)
    expect(state.reviewPeek).toBe(false)

    vi.useRealTimers()
  })

  it('handles Escape key to step back from recall to copy stage when empty', () => {
    const state = {
      activeView: 'reviewView',
      token: 'fake-token',
      currentReviewEntry: { id: '1', term: 'cat' },
      reviewTyped: '',
      reviewStage: 'recall',
      reviewWrongCount: 2,
    }
    const renderReviewFocus = vi.fn()
    const typingFeature = createReviewTypingFeature({
      state,
      elements: {},
      escapeHtml: (s) => s,
      renderReviewFocus,
    })

    typingFeature.handleReviewKeydown({ key: 'Escape' })
    expect(state.reviewStage).toBe('copy')
    expect(state.reviewWrongCount).toBe(0)
  })

  it('ignores review typing when any modal-backdrop is open or IME composition is active', () => {
    const backdrop = document.createElement('section')
    backdrop.className = 'modal-backdrop'
    document.body.appendChild(backdrop)

    const state = {
      activeView: 'reviewView',
      token: 'fake-token',
      currentReviewEntry: { id: '1', term: 'abrupt' },
      reviewTyped: '',
      reviewWrongCount: 0,
    }
    const typingFeature = createReviewTypingFeature({
      state,
      elements: {},
      escapeHtml: (s) => s,
      renderReviewFocus: vi.fn(),
    })

    const preventDefault = vi.fn()
    typingFeature.handleReviewKeydown({ key: 'a', preventDefault })
    expect(state.reviewTyped).toBe('')
    expect(preventDefault).not.toHaveBeenCalled()

    backdrop.remove()
    typingFeature.handleReviewKeydown({ key: 'a', isComposing: true, preventDefault })
    expect(state.reviewTyped).toBe('')
  })

  it('resets wrong count per letter in recall stage so next letter is never revealed prematurely and triggers celebrationLayer', () => {
    vi.useFakeTimers()
    document.body.innerHTML = '<div class="celebration-layer" id="celebrationLayer"></div>'
    const celebrationLayer = document.getElementById('celebrationLayer')
    const state = {
      activeView: 'reviewView',
      token: 'fake-token',
      currentReviewEntry: { id: '2', term: 'cat' },
      reviewTyped: '',
      reviewStage: 'recall',
      reviewWrongCount: 0,
      reviewPeek: false,
    }
    const elements = { celebrationLayer }
    const renderReviewFocus = vi.fn()
    const renderReviewCompleteModal = vi.fn()
    const typingFeature = createReviewTypingFeature({
      state,
      elements,
      escapeHtml: (s) => s,
      renderReviewFocus,
      renderReviewCompleteModal,
    })

    const preventDefault = vi.fn()
    // Err 3 times on index 0 ('c') -> reveals 'c' only
    typingFeature.handleReviewKeydown({ key: 'x', preventDefault })
    typingFeature.handleReviewKeydown({ key: 'y', preventDefault })
    typingFeature.handleReviewKeydown({ key: 'z', preventDefault })
    expect(state.reviewWrongCount).toBe(3)
    const hintHtmlAt0 = typingFeature.renderTypingLetters('cat', state.reviewTyped, 'recall', false)
    expect(hintHtmlAt0).toContain('>c<')
    expect(hintHtmlAt0).not.toContain('>a<')

    // Type 'c' correctly -> wrongCount MUST reset to 0 so next letter 'a' remains masked
    typingFeature.handleReviewKeydown({ key: 'c', preventDefault })
    expect(state.reviewTyped).toBe('c')
    expect(state.reviewWrongCount).toBe(0)
    const maskedHtmlAt1 = typingFeature.renderTypingLetters('cat', state.reviewTyped, 'recall', false)
    expect(maskedHtmlAt1).toContain('>c<')
    expect(maskedHtmlAt1).not.toContain('>a<')
    expect(maskedHtmlAt1).not.toContain('>t<')

    // Finish remaining letters 'a', 't' -> plays success tone & triggers full-screen celebrationLayer
    typingFeature.handleReviewKeydown({ key: 'a', preventDefault })
    typingFeature.handleReviewKeydown({ key: 't', preventDefault })
    vi.advanceTimersByTime(200)
    expect(renderReviewCompleteModal).toHaveBeenCalledWith(state.currentReviewEntry)
    expect(playUiTone).toHaveBeenCalledWith('success')
    expect(celebrationLayer.classList.contains('show')).toBe(true)
    expect(celebrationLayer.querySelectorAll('span')).toHaveLength(34)

    vi.advanceTimersByTime(1550)
    expect(celebrationLayer.classList.contains('show')).toBe(false)
    document.body.innerHTML = ''
    vi.useRealTimers()
  })

  it('triggers onGuidedCopyComplete even if currentReviewEntry object reference changed during typing (async detail resolution)', () => {
    vi.useFakeTimers()
    const initialEntry = { id: '42', term: 'curtain' }
    const state = {
      activeView: 'reviewView',
      token: 'fake-token',
      currentReviewEntry: initialEntry,
      reviewCardPhase: 'vague_copy',
      reviewStage: 'copy',
      reviewTyped: '',
      reviewWrongCount: 0,
      reviewPeek: false,
    }
    const renderReviewFocus = vi.fn()
    const onGuidedCopyComplete = vi.fn()

    const typingFeature = createReviewTypingFeature({
      state,
      elements: {},
      escapeHtml: (s) => s,
      renderReviewFocus,
      onGuidedCopyComplete,
    })

    const preventDefault = vi.fn()
    // Type 'c', 'u', 'r', 't', 'a', 'i'
    for (const ch of 'curtai') {
      typingFeature.handleReviewKeydown({ key: ch, preventDefault })
    }
    expect(state.reviewTyped).toBe('curtai')

    // Simulate async detail load resolving before or during the last letter:
    // currentReviewEntry is replaced with a brand new object instance
    const updatedEntry = { ...initialEntry, detailLoaded: true, phonetic: '/kɜːtn/' }
    state.currentReviewEntry = updatedEntry

    // Type the final letter 'n'
    typingFeature.handleReviewKeydown({ key: 'n', preventDefault })
    expect(state.reviewTyped).toBe('curtain')

    // Advance 200ms
    vi.advanceTimersByTime(200)

    expect(onGuidedCopyComplete).toHaveBeenCalledTimes(1)
    expect(onGuidedCopyComplete).toHaveBeenCalledWith(expect.objectContaining({ id: '42', term: 'curtain' }))
    vi.useRealTimers()
  })

  it('prioritizes parsed.term over entry.term when plural/inflected lookup term differs from lemma (e.g. curtains -> curtain)', () => {
    vi.useFakeTimers()
    const entry = { id: '99', term: 'curtains', parsed: { term: 'curtain' } }
    const state = {
      activeView: 'reviewView',
      token: 'fake-token',
      currentReviewEntry: entry,
      reviewCardPhase: 'vague_copy',
      reviewStage: 'copy',
      reviewTyped: '',
      reviewWrongCount: 0,
      reviewPeek: false,
    }
    const onGuidedCopyComplete = vi.fn()

    const typingFeature = createReviewTypingFeature({
      state,
      elements: {},
      escapeHtml: (s) => s,
      renderReviewFocus: vi.fn(),
      onGuidedCopyComplete,
    })

    expect(typingFeature.reviewTargetTerm(entry)).toBe('curtain')

    const preventDefault = vi.fn()
    for (const ch of 'curtain') {
      typingFeature.handleReviewKeydown({ key: ch, preventDefault })
    }
    expect(state.reviewTyped).toBe('curtain')

    vi.advanceTimersByTime(200)

    expect(onGuidedCopyComplete).toHaveBeenCalledTimes(1)
    vi.useRealTimers()
  })
})

describe('review focus UI and complete modal cognitive image', () => {
  it('omits stage badge and typing hint text in reviewFocus', () => {
    const reviewFocus = document.createElement('div')
    const state = {
      reviewEntries: [{ id: '1', term: 'absence', parsed: { definitions: [{ part_of_speech: 'noun', meaning: '缺席' }] } }],
      currentReviewIndex: 0,
      reviewStage: 'copy',
      reviewTyped: '',
      reviewPeek: false,
      reviewWrongCount: 0,
    }
    const review = createReviewFeature({
      state,
      elements: { reviewFocus },
      sameId: (a, b) => String(a) === String(b),
      normalizeDefinitions: (p) => (p?.definitions || []).map((d) => ({ pos: d.part_of_speech, cn: d.meaning })),
      normalizeExamples,
      normalizeArray: (v) => (Array.isArray(v) ? v : []),
      escapeHtml: (s) => String(s || ''),
      readText: () => '',
      stringifyValue: (v) => String(v || ''),
      renderCollocationMini: () => '',
      speakSentence: vi.fn(),
      speak: vi.fn(),
      bindStudyTermCards: vi.fn(),
      bindInlineAudio: vi.fn(),
      statusLabel: () => '学习中',
      renderMarkdown: (s) => s,
      saveEntry: vi.fn(),
      toast: vi.fn(),
    })

    review.renderReviewFocus(state.reviewEntries[0])
    expect(reviewFocus.querySelector('.review-stage-badge')).toBeNull()
    expect(reviewFocus.querySelector('.typing-hint')).toBeNull()
    expect(reviewFocus.textContent).not.toContain('明文跟敲')
    expect(reviewFocus.textContent).toContain('1 / 1')

    state.reviewStage = 'recall'
    review.renderReviewFocus(state.reviewEntries[0])
    expect(reviewFocus.querySelector('.review-stage-badge')).toBeNull()
    expect(reviewFocus.querySelector('.typing-hint')).toBeNull()
    expect(reviewFocus.textContent).not.toContain('遮挡默写')
  })

  it('renders cognitive image card above examples in reviewCompleteModal and hides when absent', () => {
    document.body.innerHTML = `
      <section class="modal-backdrop hidden" id="reviewCompleteModal">
        <div class="review-modal">
          <h3 id="modalWordTitle"></h3>
          <div id="modalCognitiveImage" class="modal-cognitive-image hidden"></div>
          <div id="modalExamples"></div>
        </div>
      </section>
    `
    const elements = {
      reviewCompleteModal: document.getElementById('reviewCompleteModal'),
      modalWordTitle: document.getElementById('modalWordTitle'),
      modalCognitiveImage: document.getElementById('modalCognitiveImage'),
      modalExamples: document.getElementById('modalExamples'),
    }
    const state = { reviewStage: 'recall', reviewPeek: true, reviewTyped: 'absence', reviewWrongCount: 0 }
    const modalFeature = createReviewModalFeature({
      state,
      elements,
      normalizeDefinitions: () => [],
      normalizeExamples,
      normalizeArray: (v) => (Array.isArray(v) ? v : []),
      escapeHtml: (s) => String(s || ''),
      readText: () => '',
      stringifyValue: (v) => String(v || ''),
      renderCollocationMini: () => '',
      speakSentence: vi.fn(),
      speak: vi.fn(),
      bindStudyTermCards: vi.fn(),
      bindInlineAudio: vi.fn(),
      statusLabel: () => '',
      renderMarkdown: (s) => s,
      saveEntry: vi.fn(),
      toast: vi.fn(),
      reviewTargetTerm: (entry) => entry.term,
      renderReviewFocus: vi.fn(),
    })

    modalFeature.renderReviewCompleteModal({
      term: 'absence',
      parsed: {
        cognitive_image: {
          title: '空白席位与留白之境',
          description: '由 ab-(离开) + essence(存在) 构成，即在场位置空缺出来。',
          usage_note: '常搭配 in the absence of 表示在缺乏……的情况下',
        },
        examples: [
          { sentence: 'His absence was noted.', translation: '他的缺席引起了注意。' },
        ],
      },
    })

    expect(elements.modalCognitiveImage.classList.contains('hidden')).toBe(false)
    expect(elements.modalCognitiveImage.textContent).toContain('母语认知画像')
    expect(elements.modalCognitiveImage.textContent).toContain('空白席位与留白之境')
    const childrenOrder = Array.from(elements.reviewCompleteModal.querySelector('.review-modal').children).map((el) => el.id)
    expect(childrenOrder.indexOf('modalCognitiveImage')).toBeLessThan(childrenOrder.indexOf('modalExamples'))

    modalFeature.closeReviewModal({ skipRender: true })
    expect(elements.modalCognitiveImage.classList.contains('hidden')).toBe(true)
    expect(elements.modalCognitiveImage.innerHTML).toBe('')

    modalFeature.renderReviewCompleteModal({
      term: 'plain',
      parsed: { examples: [{ sentence: 'Plain text.', translation: '纯文本。' }] },
    })
    expect(elements.modalCognitiveImage.classList.contains('hidden')).toBe(true)
    document.body.innerHTML = ''
  })
})
