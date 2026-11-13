/**
 * 跨模块复用的纯前端跟敲与遮挡拼写对齐内核。
 * 供「复习计划」单词打字与「知识反刍」句子巩固共用，不耦合具体页面 DOM 或视图状态。
 */

function defaultEscape(text) {
  return String(text ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/**
 * 渲染逐字符打字格（明文跟敲 / 遮挡默写 / 偷看 / 错 3 次微提示）。
 */
export function renderMaskedLetters(
  term,
  typed,
  {
    stage = 'copy',
    peek = false,
    wrongCount = 0,
    hintAfterWrongs = 3,
    escapeHtml = defaultEscape,
  } = {},
) {
  const isRecall = stage === 'recall'
  const safeTyped = String(typed || '')
  return [...String(term || '')]
    .map((letter, index) => {
      const isTyped = index < safeTyped.length
      const isCurrent = index === safeTyped.length
      const className = [
        isTyped ? 'typed' : '',
        isCurrent ? 'current' : '',
        isRecall ? 'recall-letter' : '',
      ]
        .filter(Boolean)
        .join(' ')

      let label
      if (letter === ' ') {
        label = 'Space'
      } else if (isRecall && !peek && !isTyped) {
        if (isCurrent && (wrongCount || 0) >= hintAfterWrongs) {
          label = letter
        } else {
          label = '•'
        }
      } else {
        label = letter
      }
      return `<span class="${className}">${escapeHtml(label)}</span>`
    })
    .join('')
}

/**
 * 统一规范化英文句子词元，自动忽略标点符号差异与多余空白，聚焦拼写与搭配语序。
 */
export function normalizeSentenceTokens(text) {
  return String(text || '')
    .toLowerCase()
    .replace(/[’']/g, "'")
    .replace(/[^a-z0-9'\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

/**
 * 提取句子中带标点包裹的词元列表，用于渲染直观的跟敲进度与错误定位。
 */
export function tokenizeSentenceDisplay(sentenceText) {
  return String(sentenceText || '')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((raw, index) => ({
      index,
      raw,
      clean: normalizeSentenceTokens(raw),
    }))
}

/**
 * 对比用户输入的句子与标准原句，精确定位已完成、当前输入及首个拼写错误位置。
 */
export function alignSentenceCopyInput(sentenceText, userInput) {
  const displayTokens = tokenizeSentenceDisplay(sentenceText)
  const expectedWords = displayTokens.map((t) => t.clean).filter(Boolean)
  const rawInput = String(userInput || '')
  const trailingSpace = /\s$/.test(rawInput)
  const actualNormalized = normalizeSentenceTokens(rawInput)
  const actualWords = actualNormalized ? actualNormalized.split(' ') : []
  const expectedNormalized = expectedWords.join(' ')

  if (expectedWords.length === 0) {
    return {
      isComplete: false,
      hasError: false,
      expectedNormalized: '',
      actualNormalized,
      firstErrorWord: null,
      tokens: [],
    }
  }

  let firstErrorWord = null
  const tokens = []
  let activeWordIdx = 0

  for (let i = 0; i < displayTokens.length; i++) {
    const token = displayTokens[i]
    if (!token.clean) {
      tokens.push({ ...token, status: 'matched', mismatchIndex: -1, typedPreview: '' })
      continue
    }

    const wordPos = activeWordIdx
    activeWordIdx += 1
    const typedWord = actualWords[wordPos]

    if (firstErrorWord) {
      tokens.push({ ...token, status: 'pending', mismatchIndex: -1, typedPreview: '' })
      continue
    }

    if (typedWord === undefined) {
      const isCurrent = wordPos === actualWords.length
      tokens.push({
        ...token,
        status: isCurrent ? 'current' : 'pending',
        mismatchIndex: -1,
        typedPreview: '',
      })
      continue
    }

    const isLastWordTyped = wordPos === actualWords.length - 1
    const exactMatch = typedWord === token.clean
    const isValidPrefix = isLastWordTyped && !trailingSpace && token.clean.startsWith(typedWord)

    if (exactMatch) {
      tokens.push({ ...token, status: 'matched', mismatchIndex: -1, typedPreview: typedWord })
    } else if (isValidPrefix) {
      tokens.push({ ...token, status: 'current', mismatchIndex: -1, typedPreview: typedWord })
    } else {
      const mismatchIndex = firstMismatchIndex(token.clean, typedWord)
      firstErrorWord = {
        wordIndex: wordPos,
        expected: token.clean,
        rawToken: token.raw,
        typed: typedWord,
        mismatchIndex,
      }
      tokens.push({
        ...token,
        status: 'error',
        mismatchIndex,
        typedPreview: typedWord,
      })
    }
  }

  const isComplete =
    !firstErrorWord &&
    actualWords.length === expectedWords.length &&
    actualNormalized === expectedNormalized

  return {
    isComplete,
    hasError: Boolean(firstErrorWord) || actualWords.length > expectedWords.length,
    expectedNormalized,
    actualNormalized,
    firstErrorWord,
    tokens,
  }
}

/**
 * 对比目标词拼写答案，生成逐字符级正确/错误/遗漏片段，用于遮挡补全后的错因定位。
 */
export function computeWordDiffSegments(expectedWord, actualInput) {
  const expected = String(expectedWord || '').trim()
  const expectedLower = expected.toLowerCase()
  const actualLower = String(actualInput || '').trim().replace(/\s+/g, ' ').toLowerCase()

  if (!expected) return []
  const segments = []
  const minLen = Math.min(expectedLower.length, actualLower.length)

  let prefixEnd = 0
  while (prefixEnd < minLen && expectedLower[prefixEnd] === actualLower[prefixEnd]) {
    prefixEnd++
  }

  if (prefixEnd > 0) {
    segments.push({ text: expected.slice(0, prefixEnd), status: 'match' })
  }

  if (actualLower.length === 0) {
    segments.push({ text: expected, status: 'missing' })
    return segments
  }

  const expectedRest = expected.slice(prefixEnd)
  const actualRest = actualLower.slice(prefixEnd)

  if (!expectedRest && !actualRest) {
    return segments
  }

  // 检查末尾共同后缀，以精确突出中间写错或漏写的字母切片
  let suffixLen = 0
  while (
    suffixLen < expectedRest.length &&
    suffixLen < actualRest.length &&
    expectedRest[expectedRest.length - 1 - suffixLen].toLowerCase() ===
      actualRest[actualRest.length - 1 - suffixLen]
  ) {
    suffixLen++
  }

  const expectedMid = expectedRest.slice(0, expectedRest.length - suffixLen)
  const actualMid = actualRest.slice(0, actualRest.length - suffixLen)

  if (actualMid) {
    segments.push({ text: actualMid, status: 'wrong' })
  }
  if (expectedMid) {
    segments.push({ text: expectedMid, status: 'missing' })
  }
  if (suffixLen > 0) {
    segments.push({ text: expectedRest.slice(expectedRest.length - suffixLen), status: 'match' })
  }
  return segments
}

/**
 * 管理 1200ms 短暂偷看原词定时器，避免重复触发残留闪退。
 */
export function schedulePeekTimer(prevTimer, onExpire, durationMs = 1200) {
  if (prevTimer && typeof window !== 'undefined') {
    window.clearTimeout(prevTimer)
  }
  if (typeof window === 'undefined') return null
  return window.setTimeout(onExpire, durationMs)
}

function firstMismatchIndex(expected, typed) {
  const limit = Math.min(expected.length, typed.length)
  for (let i = 0; i < limit; i++) {
    if (expected[i] !== typed[i]) return i
  }
  return limit
}
