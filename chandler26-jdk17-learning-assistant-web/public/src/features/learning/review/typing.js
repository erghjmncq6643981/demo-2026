import { playUiTone } from '/src/shared/audio.js'

export function createReviewTypingFeature(ctx) {
  const {
    state,
    elements,
    escapeHtml,
    renderReviewFocus,
    renderReviewCompleteModal,
    showCelebration,
  } = ctx

  function updateReviewProgressBadge() {
    if (!elements.reviewProgressBadge) return
    const total = state.reviewEntries.length
    elements.reviewProgressBadge.textContent = total ? `${state.currentReviewIndex + 1} / ${total}` : '0 / 0'
  }

  function goToReviewOffset(offset) {
    const total = state.reviewEntries.length
    if (!total) return
    const nextIndex = Math.max(0, Math.min(total - 1, state.currentReviewIndex + offset))
    if (nextIndex === state.currentReviewIndex && state.currentReviewEntry) return
    state.currentReviewIndex = nextIndex
    ctx.selectReviewEntry(state.reviewEntries[nextIndex])
  }

  function renderTypingLetters(term, typed) {
    return [...String(term || '')]
      .map((letter, index) => {
        const className = index < typed.length ? 'typed' : index === typed.length ? 'current' : ''
        const label = letter === ' ' ? 'Space' : letter
        return `<span class="${className}">${escapeHtml(label)}</span>`
      })
      .join('')
  }

  function handleReviewKeydown(event) {
    if (state.activeView !== 'reviewView' || !state.currentReviewEntry || !state.token) return
    const activeTag = document.activeElement?.tagName?.toLowerCase()
    if (['input', 'textarea', 'select'].includes(activeTag) || elements.reviewCompleteModal?.classList.contains('hidden') === false) return
    if (event.altKey || event.ctrlKey || event.metaKey) return
    if (event.key === 'Backspace') {
      event.preventDefault()
      state.reviewTyped = state.reviewTyped.slice(0, -1)
      renderReviewFocus(state.currentReviewEntry)
      return
    }
    if (event.key === 'Escape') {
      state.reviewTyped = ''
      state.reviewWrongCount = 0
      renderReviewFocus(state.currentReviewEntry)
      return
    }
    if (event.key.length !== 1) return
    const term = reviewTargetTerm(state.currentReviewEntry)
    const expected = term[state.reviewTyped.length]
    if (!expected) return
    event.preventDefault()
    if (event.key.toLowerCase() === expected.toLowerCase()) {
      state.reviewTyped += expected
      playUiTone('correct')
      renderReviewFocus(state.currentReviewEntry)
      if (state.reviewTyped.length === term.length) {
        window.setTimeout(() => completeReviewTyping(), 120)
      }
      return
    }
    state.reviewWrongCount += 1
    playUiTone('wrong')
    shakeTypingBoard()
  }

  function reviewTargetTerm(entry) {
    return String(entry?.term || entry?.normalizedTerm || '').trim()
  }

  function shakeTypingBoard() {
    const board = elements.reviewFocus.querySelector('.typing-board')
    if (!board) return
    board.classList.remove('shake')
    void board.offsetWidth
    board.classList.add('shake')
  }

  function completeReviewTyping() {
    const entry = state.currentReviewEntry
    if (!entry) return
    state.pendingReviewEntryId = entry.id
    playUiTone('success')
    renderReviewCompleteModal(entry)
    showCelebration()
  }

  function showCelebrationEffect() {
    const layer = elements.celebrationLayer
    if (!layer) return
    layer.innerHTML = Array.from({ length: 34 }, (_, index) => {
      const left = Math.round(Math.random() * 100)
      const delay = Math.round(Math.random() * 260)
      const color = ['#818cf8', '#60a5fa', '#7dd3a8', '#facc6b', '#fb7185'][index % 5]
      return `<span style="left:${left}%; animation-delay:${delay}ms; background:${color}"></span>`
    }).join('')
    layer.classList.add('show')
    window.clearTimeout(showCelebrationEffect.timer)
    showCelebrationEffect.timer = window.setTimeout(() => {
      layer.classList.remove('show')
      layer.innerHTML = ''
    }, 1500)
  }

  return {
    updateReviewProgressBadge,
    goToReviewOffset,
    renderTypingLetters,
    handleReviewKeydown,
    reviewTargetTerm,
    shakeTypingBoard,
    completeReviewTyping,
    showCelebration: showCelebrationEffect,
    playUiTone,
  }
}
