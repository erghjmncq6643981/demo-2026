import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import {
  alignSentenceCopyInput,
  computeWordDiffSegments,
  normalizeSentenceTokens,
  renderMaskedLetters,
  schedulePeekTimer,
  tokenizeSentenceDisplay,
} from '../../public/src/shared/typing-engine.js'

describe('typing-engine 共享纯打字引擎', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renderMaskedLetters 支持明文、遮挡、偷看及错 3 次提示', () => {
    const copyHtml = renderMaskedLetters('ab c', 'ab')
    expect(copyHtml).toContain('<span class="typed">a</span>')
    expect(copyHtml).toContain('<span class="typed">b</span>')
    expect(copyHtml).toContain('<span class="current">Space</span>')
    expect(copyHtml).toContain('<span class="">c</span>')

    const recallMasked = renderMaskedLetters('peel', 'p', { stage: 'recall' })
    expect(recallMasked).toContain('<span class="typed recall-letter">p</span>')
    expect(recallMasked).toContain('<span class="current recall-letter">•</span>')

    const recallPeek = renderMaskedLetters('peel', 'p', { stage: 'recall', peek: true })
    expect(recallPeek).toContain('<span class="current recall-letter">e</span>')
    expect(recallPeek).not.toContain('•')

    const recallWrongHint = renderMaskedLetters('peel', 'p', {
      stage: 'recall',
      wrongCount: 3,
      hintAfterWrongs: 3,
    })
    expect(recallWrongHint).toContain('<span class="current recall-letter">e</span>')
    expect(recallWrongHint).toContain('<span class="recall-letter">•</span>')
  })

  it('normalizeSentenceTokens 与 tokenizeSentenceDisplay 忽略标点并保留原文展示词元', () => {
    expect(normalizeSentenceTokens('She’s peeling, boiling — and tasting!')).toBe(
      "she's peeling boiling and tasting",
    )
    const tokens = tokenizeSentenceDisplay('Peel it, please!')
    expect(tokens).toEqual([
      { index: 0, raw: 'Peel', clean: 'peel' },
      { index: 1, raw: 'it,', clean: 'it' },
      { index: 2, raw: 'please!', clean: 'please' },
    ])
  })

  it('alignSentenceCopyInput 支持跳过标点判完成、前缀输入跟踪及精确错误定位', () => {
    const sentence = 'First, peel the potato smoothly.'

    const prefix = alignSentenceCopyInput(sentence, 'first pe')
    expect(prefix.isComplete).toBe(false)
    expect(prefix.hasError).toBe(false)
    expect(prefix.tokens[0].status).toBe('matched')
    expect(prefix.tokens[1].status).toBe('current')

    const mismatch = alignSentenceCopyInput(sentence, 'first peal ')
    expect(mismatch.hasError).toBe(true)
    expect(mismatch.isComplete).toBe(false)
    expect(mismatch.firstErrorWord).toEqual({
      wordIndex: 1,
      expected: 'peel',
      rawToken: 'peel',
      typed: 'peal',
      mismatchIndex: 2,
    })
    expect(mismatch.tokens[1].status).toBe('error')

    const complete = alignSentenceCopyInput(sentence, 'first peel the potato smoothly')
    expect(complete.isComplete).toBe(true)
    expect(complete.hasError).toBe(false)
    expect(complete.firstErrorWord).toBeNull()

    const extraWord = alignSentenceCopyInput(sentence, 'first peel the potato smoothly extra')
    expect(extraWord.hasError).toBe(true)
    expect(extraWord.isComplete).toBe(false)
  })

  it('computeWordDiffSegments 准确拆分匹配、错字母与漏字母切片', () => {
    expect(computeWordDiffSegments('peeling', 'pealing')).toEqual([
      { text: 'pe', status: 'match' },
      { text: 'a', status: 'wrong' },
      { text: 'e', status: 'missing' },
      { text: 'ling', status: 'match' },
    ])
    expect(computeWordDiffSegments('boil', '')).toEqual([{ text: 'boil', status: 'missing' }])
  })

  it('schedulePeekTimer 在重复触发时清理旧定时器并在到期后执行回调', () => {
    const firstCb = vi.fn()
    const secondCb = vi.fn()
    const t1 = schedulePeekTimer(null, firstCb, 1200)
    const t2 = schedulePeekTimer(t1, secondCb, 1200)
    expect(t2).not.toBeNull()

    vi.advanceTimersByTime(1200)
    expect(firstCb).not.toHaveBeenCalled()
    expect(secondCb).toHaveBeenCalledTimes(1)
  })
})
