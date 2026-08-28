import { escapeHtml } from '/src/shared/text.js'
import { normalizeArray, normalizeDefinitions, readText, stringifyValue } from '/src/shared/vocabulary.js'
import { normalizeAnswerValue, splitArticleLines } from '/src/features/reading/article-model.js'

/**
 * 文章学习的纯渲染与预览构造函数。
 * 流程状态、请求和事件绑定由 article.js 负责，避免渲染逻辑继续膨胀为单文件。
 */
export function renderArticleError(error) {
  const meta = [
    error.errorCode ? `错误码：${error.errorCode}` : '',
    error.status ? `HTTP：${error.status}` : '',
  ].filter(Boolean)
  return `
    <div class="article-error-content">
      <strong>精读材料生成失败</strong>
      <p>${escapeHtml(error.message)}</p>
      ${error.suggestion ? `<small>${escapeHtml(error.suggestion)}</small>` : ''}
      ${meta.length ? `<span>${escapeHtml(meta.join(' · '))}</span>` : ''}
    </div>
  `
}

export function renderVocabularyFocus(items, selectedWords = []) {
  if (!items.length) return ''
  return `
    <section class="article-section">
      <h5>目标词精讲</h5>
      <div class="article-vocabulary-list">
        ${items
          .map((item) => {
            const word = readText(item, ['word', 'term']) || stringifyValue(item)
            const meaning = readText(item, ['meaning', 'translation', 'cn'])
            const usage = readText(item, ['usage', 'explanation', 'tip'])
            const sentence = readText(item, ['sentence', 'example'])
            const translation = readText(item, ['translation', 'sentence_translation', 'sentenceTranslation', 'zh'])
            const selectedWord = selectedWords.find((entry) => normalizeAnswerValue(entry.term || entry.normalizedTerm) === normalizeAnswerValue(word))
            return `
              <article class="article-vocabulary-row" data-article-focus-word="${escapeHtml(word)}">
                <header><strong>${escapeHtml(word)}</strong><span>${escapeHtml(selectedWord?.partOfSpeech || '')}</span></header>
                <p>${escapeHtml(meaning || selectedWord?.meaning || '暂无核心含义')}</p>
                ${usage ? `<div>${escapeHtml(usage)}</div>` : ''}
                ${sentence ? `<small>${escapeHtml(sentence)}</small>` : ''}
                ${translation && translation !== meaning ? `<small>${escapeHtml(translation)}</small>` : ''}
              </article>
            `
          })
          .join('')}
      </div>
    </section>
  `
}

export function renderGrammarPoints(items) {
  if (!items.length) return ''
  return `
    <section class="article-section">
      <h5>语法知识点</h5>
      <div class="article-stack">
        ${items
          .map((item) => {
            const examples = normalizeArray(item?.examples || [])
            return `
              <div class="article-info-block">
                <strong>${escapeHtml(readText(item, ['title', 'name']) || '语法点')}</strong>
                <p>${escapeHtml(readText(item, ['explanation', 'description', 'content']) || stringifyValue(item))}</p>
                ${examples.length
                  ? examples
                      .map((example) => {
                        const sentence = readText(example, ['sentence', 'example', 'text'])
                        const translation = readText(example, ['translation', 'cn', 'zh'])
                        return `<small>${escapeHtml([sentence, translation].filter(Boolean).join(' / '))}</small>`
                      })
                      .join('')
                  : ''}
              </div>
            `
          })
          .join('')}
      </div>
    </section>
  `
}

export function renderSimpleList(title, items) {
  if (!items.length) return ''
  return `
    <section class="article-section">
      <h5>${escapeHtml(title)}</h5>
      <ul class="article-point-list">
        ${items.map((item) => `<li>${escapeHtml(typeof item === 'string' ? item : stringifyValue(item))}</li>`).join('')}
      </ul>
    </section>
  `
}

export function renderBilingualArticle(article, translation, options = {}) {
  const englishLines = splitArticleLines(article, 'en')
  const chineseLines = splitArticleLines(translation, 'zh')
  const showTranslation = options.showTranslation !== false
  const lineCount = showTranslation ? Math.max(englishLines.length, chineseLines.length) : englishLines.length
  if (!lineCount) return '<div class="empty">暂无文章内容</div>'
  return `
    <div class="article-bilingual-lines">
      ${Array.from({ length: lineCount })
        .map((_, index) => {
          const english = englishLines[index] || ''
          const chinese = showTranslation ? chineseLines[index] || '' : ''
          return `
            <div class="article-bilingual-pair">
              ${english ? `<p class="article-line-en">${renderHighlightedArticleText(english, options.selectedWords)}</p>` : ''}
              ${chinese ? `<p class="article-line-zh">${escapeHtml(chinese)}</p>` : ''}
            </div>
          `
        })
        .join('')}
    </div>
  `
}

function renderHighlightedArticleText(text, selectedWords) {
  const words = (Array.isArray(selectedWords) ? selectedWords : [])
    .filter((item) => String(item.term || item.normalizedTerm || '').trim())
    .sort((left, right) => String(right.term || right.normalizedTerm).length - String(left.term || left.normalizedTerm).length)
  if (!words.length) return escapeHtml(text)
  const byTerm = new Map(words.map((item) => [normalizeAnswerValue(item.term || item.normalizedTerm), item]))
  const source = words.map((item) => String(item.term || item.normalizedTerm).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')
  const pattern = new RegExp(`(${source})`, 'gi')
  return String(text).split(pattern).map((part) => {
    const word = byTerm.get(normalizeAnswerValue(part))
    if (!word) return escapeHtml(part)
    const term = word.term || word.normalizedTerm
    const hint = [term, word.partOfSpeech, word.meaning].filter(Boolean).join(' · ')
    return `<button class="article-target-word" type="button" data-article-target="${escapeHtml(term)}" title="${escapeHtml(hint)}">${escapeHtml(part)}</button>`
  }).join('')
}

export function buildPreviewArticleRecord(state, payload = {}) {
  const selectedEntries = state.articleEntries.filter((entry) => state.selectedArticleEntryIds.some((id) => String(id) === String(entry.id)))
  const selectedWords = (selectedEntries.length ? selectedEntries : state.wordbookEntries.slice(0, 3)).map((entry) => {
    const definition = normalizeDefinitions(entry.parsed || {})[0] || {}
    return {
      entryId: entry.id,
      term: entry.term || entry.normalizedTerm,
      normalizedTerm: entry.normalizedTerm,
      status: entry.status || 'vague',
      partOfSpeech: definition.pos || 'meaning',
      meaning: definition.cn || '核心含义',
    }
  })
  return {
    id: payload.forceRefresh ? String(Date.now()) : 'preview-article',
    wordbookId: state.currentWordbookId || '1',
    selectedWords,
    wordCountRange: payload.wordCountRange || '300-500',
    difficulty: payload.difficulty || 'medium',
    remark: payload.remark || '偏日常语境，突出语法点。',
    cacheHit: false,
    studyStatus: 'generated',
    currentStage: 'reading',
    practiceTotal: 0,
    practiceCorrect: 0,
    practiceScore: 0,
    provider: 'preview',
    modelName: 'mock-article',
    sessionId: 'preview',
    updateTime: new Date().toISOString(),
    parsed: {
      title: 'A Choice That Changed the Plan',
      article: 'Mia had to abandon an old plan, but she decided to maintain her confidence. Instead of giving up, she compared several options and found a clear contrast between fear and careful action.',
      translation: '米娅不得不放弃一个旧计划，但她决定保持自信。她没有直接放弃，而是比较了几个选择，并看清了恐惧和谨慎行动之间的差别。',
      vocabulary_focus: selectedWords.map((word) => ({
        word: word.term,
        meaning: word.meaning,
        usage: '文章中用于真实语境复现',
        sentence: `Try to use ${word.term} in your own sentence.`,
        translation: `尝试用 ${word.term} 写一个自己的句子。`,
      })),
      grammar_points: [
        {
          title: 'Instead of + doing',
          explanation: 'instead of 后面常接名词或动名词，用来表达“不是……而是……”。',
          examples: [{ sentence: 'Instead of giving up, she tried again.', translation: '她没有放弃，而是又试了一次。' }],
        },
      ],
      key_points: ['把词汇放进连续文章，更容易记住语境。', '注意文章中的转折连接词。'],
      practice: [
        {
          question: 'What did Mia decide to maintain?',
          options: ['Her confidence.', 'Her old plan.', 'Her fear.', 'Her schedule.'],
          correct_answer: 'Her confidence.',
          explanation: '文章第一句说明她决定保持自信。',
        },
        {
          question: 'What did Mia abandon?',
          options: ['An old plan.', 'A new job.', 'A travel guide.', 'A class.'],
          correct_answer: 'An old plan.',
          explanation: '文章开头说明她不得不放弃旧计划。',
        },
        {
          question: 'What contrast did Mia notice?',
          options: ['Fear and careful action.', 'Work and travel.', 'Day and night.', 'Success and money.'],
          correct_answer: 'Fear and careful action.',
          explanation: '文章结尾比较了恐惧与谨慎行动。',
        },
      ],
      study_tips: ['先朗读英文文章，再看中文译文。', '把所选词汇各写一个新句子。'],
    },
  }
}
