import { describe, expect, it, vi } from 'vitest'
import { createReviewTypingFeature } from '../../public/src/features/learning/review/typing.js'

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
})
