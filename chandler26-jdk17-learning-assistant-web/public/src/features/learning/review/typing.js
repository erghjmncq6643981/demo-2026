import { playUiTone } from '/src/shared/audio.js'
import { showCelebrationEffect as triggerCelebrationEffect } from '/src/shared/celebration.js'
import { renderMaskedLetters, schedulePeekTimer } from '/src/shared/typing-engine.js'
import { sameId as sharedSameId } from '/src/shared/ids.js'

export function createReviewTypingFeature(ctx) {
  const {
    state,
    elements,
    escapeHtml,
    renderReviewFocus,
    renderReviewCompleteModal,
    showCelebration,
    speak,
    toast,
    onSelectTag,
    onConfirmKnown,
    onStartGuidedCopy,
    onStartForgotCopy,
    onStartBlindRecall,
    onGuidedCopyComplete,
    onForgotCopyComplete,
    onBlindRecallComplete,
  } = ctx

  const sameId = ctx.sameId || sharedSameId

  function isSameCurrentEntry(current, target) {
    if (!current || !target) return false
    if (current === target) return true
    const currentId = current.id ?? current.entryId ?? current.lookupId
    const targetId = target.id ?? target.entryId ?? target.lookupId
    if (currentId !== undefined && currentId !== null && targetId !== undefined && targetId !== null) {
      if (sameId(currentId, targetId)) return true
    }
    const currentTerm = reviewTargetTerm(current).toLowerCase()
    const targetTerm = reviewTargetTerm(target).toLowerCase()
    return Boolean(currentTerm && currentTerm === targetTerm)
  }

  function updateReviewProgressBadge() {
    if (!elements.reviewProgressBadge) return
    const total = state.reviewEntries.length
    elements.reviewProgressBadge.textContent = total ? `${state.currentReviewIndex + 1} / ${total}` : '0 / 0'
  }

  function cancelPeekTimer() {
    if (triggerPeek.timer) {
      window.clearTimeout(triggerPeek.timer)
      triggerPeek.timer = null
    }
  }

  function goToReviewOffset(offset) {
    const total = state.reviewEntries.length
    if (!total) return
    const nextIndex = Math.max(0, Math.min(total - 1, state.currentReviewIndex + offset))
    if (nextIndex === state.currentReviewIndex && state.currentReviewEntry) return
    cancelPeekTimer()
    state.currentReviewIndex = nextIndex
    state.reviewStage = 'copy'
    state.reviewPeek = false
    state.reviewTyped = ''
    state.reviewWrongCount = 0
    ctx.selectReviewEntry(state.reviewEntries[nextIndex])
  }

  function renderTypingLetters(term, typed, stage = 'copy', peek = false) {
    return renderMaskedLetters(term, typed, {
      stage,
      peek,
      wrongCount: state.reviewWrongCount || 0,
      hintAfterWrongs: 3,
      escapeHtml,
    })
  }

  function triggerPeek() {
    state.reviewPeek = true
    renderReviewFocus(state.currentReviewEntry)
    triggerPeek.timer = schedulePeekTimer(triggerPeek.timer, () => {
      state.reviewPeek = false
      if (state.activeView === 'reviewView' && state.currentReviewEntry) {
        renderReviewFocus(state.currentReviewEntry)
      }
    }, 1200)
  }

  function isEditableOrBlockedTarget(el) {
    if (!el || typeof el.tagName !== 'string') return false
    const tag = el.tagName.toLowerCase()
    return ['input', 'textarea', 'select'].includes(tag) || Boolean(el.isContentEditable)
  }

  function handleReviewKeydown(event) {
    if (!event || typeof event.key !== 'string') return
    if (event.isComposing || event.keyCode === 229) return
    if (state.activeView !== 'reviewView' || !state.currentReviewEntry || !state.token) return
    const hasOpenModal = typeof document !== 'undefined' && Boolean(document.querySelector?.('.modal-backdrop:not(.hidden)'))
    if (
      hasOpenModal ||
      isEditableOrBlockedTarget(document.activeElement) ||
      isEditableOrBlockedTarget(event.target) ||
      elements.reviewCompleteModal?.classList.contains('hidden') === false ||
      elements.reviewNoteModal?.classList.contains('hidden') === false
    ) {
      return
    }
    if (event.altKey || event.ctrlKey || event.metaKey) return

    const phase = state.reviewCardPhase
    if (phase === 'brief') {
      if (event.key === '1') {
        event.preventDefault()
        onSelectTag?.('known')
      } else if (event.key === '2') {
        event.preventDefault()
        onSelectTag?.('vague')
      } else if (event.key === '3') {
        event.preventDefault()
        onSelectTag?.('forgotten')
      } else if (event.key === 'r' || event.key === 'R') {
        event.preventDefault()
        speak?.(reviewTargetTerm(state.currentReviewEntry), elements?.voiceSelect?.value)
      }
      return
    }
    if (phase === 'known_check') {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault()
        onConfirmKnown?.()
      } else if (event.key === '2') {
        event.preventDefault()
        onSelectTag?.('vague')
      } else if (event.key === 'r' || event.key === 'R') {
        event.preventDefault()
        speak?.(reviewTargetTerm(state.currentReviewEntry), elements?.voiceSelect?.value)
      }
      return
    }
    if (phase === 'vague_study') {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault()
        onStartGuidedCopy?.()
      } else if (event.key === 'r' || event.key === 'R') {
        event.preventDefault()
        speak?.(reviewTargetTerm(state.currentReviewEntry), elements?.voiceSelect?.value)
      }
      return
    }
    if (phase === 'forgot_study') {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault()
        if (typeof onStartForgotCopy === 'function') {
          onStartForgotCopy()
        } else {
          onStartBlindRecall?.()
        }
      } else if (event.key === 'r' || event.key === 'R') {
        event.preventDefault()
        speak?.(reviewTargetTerm(state.currentReviewEntry), elements?.voiceSelect?.value)
      }
      return
    }

    if (event.key === 'Tab') {
      if (state.reviewStage === 'recall') {
        event.preventDefault()
        triggerPeek()
      }
      return
    }

    if (event.key === 'Backspace') {
      event.preventDefault()
      state.reviewTyped = (state.reviewTyped || '').slice(0, -1)
      state.reviewWrongCount = 0
      renderReviewFocus(state.currentReviewEntry)
      return
    }

    if (event.key === 'Escape') {
      cancelPeekTimer()
      if (phase === 'forgot_recall' && !state.reviewTyped) {
        state.reviewCardPhase = 'forgot_study'
        state.reviewStage = 'study'
      } else if (phase === 'forgot_copy' && !state.reviewTyped) {
        state.reviewCardPhase = 'forgot_study'
        state.reviewStage = 'study'
      } else if (state.reviewStage === 'recall' && !state.reviewTyped) {
        state.reviewStage = 'copy'
      } else {
        state.reviewTyped = ''
      }
      state.reviewWrongCount = 0
      state.reviewPeek = false
      renderReviewFocus(state.currentReviewEntry)
      return
    }

    if (event.key.length !== 1) return
    const term = reviewTargetTerm(state.currentReviewEntry)
    const typed = state.reviewTyped || ''
    const expected = term[typed.length]
    if (!expected) return
    event.preventDefault()
    if (event.key.toLowerCase() === expected.toLowerCase()) {
      const targetEntry = state.currentReviewEntry
      state.reviewTyped = typed + expected
      // 每输入正确一个字母即重置当前字母错误计数，避免历史失误提前剧透下一个字母
      state.reviewWrongCount = 0
      playUiTone('correct')
      renderReviewFocus(state.currentReviewEntry)
      if (state.reviewTyped.length === term.length) {
        if (phase === 'vague_copy') {
          window.setTimeout(() => {
            if (state.activeView === 'reviewView' && isSameCurrentEntry(state.currentReviewEntry, targetEntry)) {
              cancelPeekTimer()
              playUiTone('success')
              onGuidedCopyComplete?.(state.currentReviewEntry || targetEntry)
            }
          }, 180)
        } else if (phase === 'forgot_copy') {
          window.setTimeout(() => {
            if (state.activeView === 'reviewView' && isSameCurrentEntry(state.currentReviewEntry, targetEntry)) {
              cancelPeekTimer()
              playUiTone('success')
              toast?.('跟敲完成，现在开始遮挡默写')
              onForgotCopyComplete?.(state.currentReviewEntry || targetEntry)
            }
          }, 200)
        } else if (phase === 'forgot_recall') {
          window.setTimeout(() => {
            if (state.activeView === 'reviewView' && isSameCurrentEntry(state.currentReviewEntry, targetEntry)) {
              cancelPeekTimer()
              playUiTone('success')
              if (typeof showCelebration === 'function') showCelebration()
              else showCelebrationEffect()
              onBlindRecallComplete?.(state.currentReviewEntry || targetEntry)
            }
          }, 180)
        } else if (state.reviewStage !== 'recall') {
          // 阶段一（跟敲）完成 -> 过渡到阶段二（遮挡默写）
          window.setTimeout(() => {
            if (state.activeView === 'reviewView' && isSameCurrentEntry(state.currentReviewEntry, targetEntry)) {
              cancelPeekTimer()
              state.reviewStage = 'recall'
              state.reviewTyped = ''
              state.reviewWrongCount = 0
              state.reviewPeek = false
              renderReviewFocus(state.currentReviewEntry)
              speak?.(term, elements?.voiceSelect?.value)
            }
          }, 180)
        } else {
          // 阶段二（默写）完成 -> 触发结算弹窗与音效喝彩
          window.setTimeout(() => {
            if (state.activeView === 'reviewView' && isSameCurrentEntry(state.currentReviewEntry, targetEntry)) {
              completeReviewTyping()
            }
          }, 180)
        }
      }
      return
    }
    state.reviewWrongCount += 1
    playUiTone('wrong')
    shakeTypingBoard()
    if (state.reviewStage === 'recall' && state.reviewWrongCount >= 3) {
      renderReviewFocus(state.currentReviewEntry)
    }
  }

  function reviewTargetTerm(entry) {
    if (!entry) return ''
    let parsed = entry.parsed
    if (typeof parsed === 'string') {
      try { parsed = JSON.parse(parsed) } catch {}
    }
    return String(parsed?.term || entry.term || entry.normalizedTerm || '').trim()
  }

  function shakeTypingBoard() {
    const board = elements.reviewFocus?.querySelector('.typing-board')
    if (!board) return
    board.classList.remove('shake')
    void board.offsetWidth
    board.classList.add('shake')
  }

  function completeReviewTyping() {
    const entry = state.currentReviewEntry
    if (!entry) return
    cancelPeekTimer()
    state.pendingReviewEntryId = entry.id
    playUiTone('success')
    renderReviewCompleteModal?.(entry)
    if (typeof showCelebration === 'function') {
      showCelebration()
    } else {
      showCelebrationEffect()
    }
  }

  function showCelebrationEffect() {
    const layer =
      elements.celebrationLayer ||
      (typeof document !== 'undefined' ? document.getElementById('celebrationLayer') : null)
    triggerCelebrationEffect(layer)
  }

  return {
    updateReviewProgressBadge,
    goToReviewOffset,
    renderTypingLetters,
    triggerPeek,
    handleReviewKeydown,
    reviewTargetTerm,
    shakeTypingBoard,
    completeReviewTyping,
    showCelebration: showCelebrationEffect,
    playUiTone,
  }
}
