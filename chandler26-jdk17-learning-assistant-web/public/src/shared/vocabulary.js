import { escapeHtml, inlineMarkdown } from './text.js'

export function normalizeArray(value) {
  if (!value) return []
  if (Array.isArray(value)) return value
  if (typeof value === 'object') return Object.values(value)
  return [value]
}

export function readText(source, keys) {
  if (!source || typeof source !== 'object') return ''
  for (const key of keys) {
    const value = source[key]
    const text = stringifyValue(value)
    if (text) return text
  }
  return ''
}

export function stringifyValue(value) {
  if (value == null) return ''
  if (Array.isArray(value)) return value.map(stringifyValue).filter(Boolean).join('；')
  if (typeof value === 'object') {
    return Object.entries(value)
      .map(([key, item]) => {
        const text = stringifyValue(item)
        return text ? `${key}: ${text}` : ''
      })
      .filter(Boolean)
      .join('；')
  }
  return String(value).trim()
}

export function fallbackObjectText(source, usedKeys) {
  if (!source || typeof source !== 'object') return ''
  return Object.entries(source)
    .filter(([key]) => !usedKeys.includes(key))
    .map(([key, value]) => {
      const text = stringifyValue(value)
      return text ? `${key}: ${text}` : ''
    })
    .filter(Boolean)
    .join('；')
}

export function renderMarkdown(markdown) {
  const source = String(markdown || '').trim()
  if (!source) return ''
  const lines = source.split(/\r?\n/)
  const html = []
  let listOpen = false
  for (const line of lines) {
    const text = line.trim()
    if (!text) {
      if (listOpen) {
        html.push('</ul>')
        listOpen = false
      }
      continue
    }
    if (text.startsWith('## ')) {
      if (listOpen) {
        html.push('</ul>')
        listOpen = false
      }
      html.push(`<h4>${inlineMarkdown(text.slice(3))}</h4>`)
    } else if (text.startsWith('# ')) {
      if (listOpen) {
        html.push('</ul>')
        listOpen = false
      }
      html.push(`<h3>${inlineMarkdown(text.slice(2))}</h3>`)
    } else if (text.startsWith('- ')) {
      if (!listOpen) {
        html.push('<ul>')
        listOpen = true
      }
      html.push(`<li>${inlineMarkdown(text.slice(2))}</li>`)
    } else {
      if (listOpen) {
        html.push('</ul>')
        listOpen = false
      }
      html.push(`<p>${inlineMarkdown(text)}</p>`)
    }
  }
  if (listOpen) html.push('</ul>')
  return html.join('')
}

export function tagLabel(tag) {
  const typeMap = {
    part_of_speech: '词性',
    meaning_topic: '含义',
    difficulty: '难度',
    collocation: '搭配',
    word_family: '词族',
  }
  const type = typeMap[tag?.tagType] || tag?.tagType || '标签'
  return `${type}: ${tag?.displayName || tag?.tagValue || ''}`
}

export function relationTypeLabel(type) {
  return (
    {
      synonym: '同义',
      antonym: '反义',
      word_family: '词族',
    }[type] || type || '相关'
  )
}

export function isSemanticRelation(item) {
  return ['synonym', 'antonym', 'word_family'].includes(item?.relationType)
}

export function semanticRelations(relations) {
  return Array.isArray(relations) ? relations.filter(isSemanticRelation) : []
}

export function relationMeaningLine(item) {
  const pieces = [item.relatedPartOfSpeech, item.relatedMeaning || item.relationValue].filter(Boolean)
  return pieces.length ? pieces.join(' · ') : '暂无核心含义'
}

export function relationPhoneticLine(item) {
  const pieces = []
  if (item?.relatedPhoneticUk) pieces.push(`UK ${item.relatedPhoneticUk}`)
  if (item?.relatedPhoneticUs) pieces.push(`US ${item.relatedPhoneticUs}`)
  return pieces.join('  ')
}

export function relationMetaLine(item) {
  const pieces = [relationTypeLabel(item.relationType)]
  if (item.matchScore != null) {
    pieces.push(`${item.matchScore}`)
  }
  return pieces.join(' · ')
}

export function renderRelationItem(item, dataAttribute = 'related-term') {
  const term = item?.relatedTerm || ''
  const phonetic = relationPhoneticLine(item)
  return `
    <div class="relation-item" role="button" tabindex="0" data-${dataAttribute}="${escapeHtml(term)}">
      <div>
        <div class="relation-title-line">
          <strong>${escapeHtml(term || '相关词')}</strong>
          <span>${escapeHtml(phonetic || '暂无音标')}</span>
        </div>
        <p>${escapeHtml(relationMeaningLine(item))}</p>
      </div>
      <div class="relation-side-actions">
        <small>${escapeHtml(relationMetaLine(item))}</small>
        <button class="mini-audio-button inline-audio-button" type="button" data-speak-text="${escapeHtml(term)}" title="播放相关单词发音">▶</button>
      </div>
    </div>
  `
}

export function renderCollocationMini(item) {
  const render = (phrase, meaning) => `
    <div class="collocation-item" role="button" tabindex="0" data-collocation-term="${escapeHtml(phrase || '')}">
      <div>
        <strong>${escapeHtml(phrase || '搭配')}</strong>
        <p>${escapeHtml(meaning || '暂无含义')}</p>
      </div>
      <button class="mini-audio-button inline-audio-button" type="button" data-speak-text="${escapeHtml(phrase || '')}" title="播放词组发音">▶</button>
    </div>
  `
  if (typeof item === 'string') {
    return render(item, '暂无含义')
  }
  const phrase = readText(item, ['phrase', 'collocation', 'text', 'word', 'expression'])
  const meaning = readText(item, ['meaning_cn', 'meaningCn', 'meaning', 'translation', 'translation_cn', 'cn'])
  return render(phrase, meaning)
}

export function bindInlineAudio(container, speak) {
  container?.querySelectorAll('[data-speak-text]').forEach((button) => {
    button.addEventListener('click', (event) => {
      event.preventDefault()
      event.stopPropagation()
      speak(button.getAttribute('data-speak-text') || '')
    })
  })
}

export function bindStudyTermCards(container, selector, onOpenTerm) {
  const attributeName = selector.match(/^\[([^\]]+)]$/)?.[1]
  if (!attributeName) return
  container?.querySelectorAll(selector).forEach((item) => {
    const open = (event) => {
      if (event?.target?.closest?.('[data-speak-text]')) return
      onOpenTerm(item.getAttribute(attributeName) || '')
    }
    item.addEventListener('click', open)
    item.addEventListener('keydown', (event) => {
      if (event.target?.closest?.('[data-speak-text]')) return
      if (event.key !== 'Enter' && event.key !== ' ') return
      event.preventDefault()
      open(event)
    })
  })
}

export function statusLabel(status) {
  return (
    {
      familiar: '熟悉',
      forgotten: '遗忘',
      vague: '模糊',
    }[status] || '模糊'
  )
}

export function cardStatusLabel(status) {
  return (
    {
      ready: '已就绪',
      generating: '生成中',
      queued: '排队中',
      missing: '未生成',
      failed: '生成失败',
      not_required: '无需生成',
    }[status] || '未生成'
  )
}

export function reviewResultToStatus(result) {
  return (
    {
      remembered: 'familiar',
      forgotten: 'forgotten',
      vague: 'vague',
    }[result] || 'vague'
  )
}

export function logTypeLabel(type) {
  return (
    {
      auth: '账户',
      ai: 'AI',
      cache: '缓存',
      error: '错误',
      article: '文章',
      navigation: '导航',
      review: '复习',
      wordbook: '单词本',
    }[type] || '系统'
  )
}

export function normalizeDefinitions(parsed) {
  const source = parsed?.definitions || parsed?.meanings || parsed?.translations || parsed?.definition || []
  const list = normalizeArray(source)
  return list
    .map((item) => {
      if (typeof item === 'string') return { pos: 'meaning', cn: item, en: '' }
      const pos = readText(item, ['part_of_speech', 'partOfSpeech', 'pos', 'type', 'word_class'])
      const cn = readText(item, ['meaning_cn', 'meaningCn', 'chinese', 'translation', 'translation_cn', 'cn', 'meaning', 'definition_cn', 'definitionCn'])
      const en = readText(item, ['meaning_en', 'meaningEn', 'english', 'definition', 'definition_en', 'definitionEn', 'en'])
      const extra = fallbackObjectText(item, [
        'part_of_speech',
        'partOfSpeech',
        'pos',
        'type',
        'word_class',
        'meaning_cn',
        'meaningCn',
        'chinese',
        'translation',
        'translation_cn',
        'cn',
        'meaning',
        'definition_cn',
        'definitionCn',
        'meaning_en',
        'meaningEn',
        'english',
        'definition',
        'definition_en',
        'definitionEn',
        'en',
        'frequency',
      ])
      return { pos: pos || 'meaning', cn: cn || extra, en, extra }
    })
    .filter((item) => item.cn || item.en || item.extra)
}

export function normalizeExamples(parsed) {
  const examples = normalizeArray(parsed?.examples || parsed?.sentences || parsed?.example_sentences || parsed?.exampleSentences)
  return examples
    .map((item) => {
      if (typeof item === 'string') return { sentence: item, translation: '' }
      return {
        sentence: readText(item, ['sentence', 'example', 'text', 'en', 'english', 'sentence_en', 'sentenceEn', 'example_en', 'exampleEn']),
        translation: readText(item, [
          'translation_cn',
          'translationCn',
          'translation',
          'cn',
          'zh',
          'zh_cn',
          'zhCn',
          'chinese',
          'chinese_translation',
          'chineseTranslation',
          'sentence_cn',
          'sentenceCn',
          'example_cn',
          'exampleCn',
          'example_translation',
          'exampleTranslation',
          'meaning',
          'meaning_cn',
          'meaningCn',
        ]),
      }
    })
    .filter((item) => item.sentence || item.translation)
}

export function firstExample(parsed) {
  return Array.isArray(parsed?.examples) && parsed.examples.length > 0 ? parsed.examples[0].sentence : ''
}

export function previewParsed(term = 'abandon') {
  return {
    term,
    is_valid: true,
    language: 'en',
    phonetic: {
      uk: '/əˈbændən/',
      us: '/əˈbændən/',
    },
    definitions: [
      {
        part_of_speech: 'verb',
        meaning: '抛弃，遗弃',
        english: 'To leave someone or something permanently.',
      },
      {
        part_of_speech: 'verb',
        meaning: '放弃计划或想法',
        english: 'To stop doing or planning something.',
      },
    ],
    examples: [
      {
        sentence: 'They had to abandon the project due to lack of funds.',
        translation: '由于缺乏资金，他们不得不放弃这个项目。',
      },
      {
        sentence: 'The old house was abandoned for years.',
        translation: '那栋老房子被废弃了很多年。',
      },
    ],
    collocations: [
      { phrase: 'abandon a plan', meaning: '放弃计划' },
      { phrase: 'abandon hope', meaning: '放弃希望' },
      { phrase: 'with abandon', meaning: '放纵地，尽情地' },
    ],
    synonyms: [
      { word: 'desert', part_of_speech: 'verb', meaning: '遗弃，离弃' },
      { word: 'forsake', part_of_speech: 'verb', meaning: '抛弃，舍弃' },
    ],
    antonyms: [
      { word: 'retain', part_of_speech: 'verb', meaning: '保留，保持' },
      { word: 'maintain', part_of_speech: 'verb', meaning: '维持，坚持' },
    ],
    word_family: [
      { word: 'abandonment', part_of_speech: 'noun', meaning: '遗弃，放弃' },
      { word: 'abandoned', part_of_speech: 'adjective', meaning: '被遗弃的' },
    ],
    memory_tips: '把 abandon 想成“放开控制”，引申为放弃、抛弃。',
  }
}

export function previewRecord(term = 'abandon') {
  const parsed = previewParsed(term)
  return {
    id: 1,
    term,
    normalizedTerm: term.toLowerCase(),
    cacheHit: true,
    provider: 'preview',
    modelName: 'design-preview',
    sessionId: 1,
    parsed,
    rawContent: JSON.stringify(parsed, null, 2),
    lookupCount: 3,
    tags: [
      { tagType: 'part_of_speech', displayName: 'verb' },
      { tagType: 'meaning_topic', displayName: '放弃' },
      { tagType: 'difficulty', displayName: 'medium' },
      { tagType: 'collocation', displayName: 'abandon a plan' },
    ],
    relations: [
      { relatedTerm: 'desert', relationType: 'synonym', relatedPartOfSpeech: 'verb', relatedMeaning: '遗弃，离弃', relatedPhoneticUk: '/dɪˈzɜːt/', relatedPhoneticUs: '/dɪˈzɜːrt/', matchType: 'parsed_object', matchScore: 92 },
      { relatedTerm: 'retain', relationType: 'antonym', relatedPartOfSpeech: 'verb', relatedMeaning: '保留，保持', relatedPhoneticUk: '/rɪˈteɪn/', relatedPhoneticUs: '/rɪˈteɪn/', matchType: 'parsed_object', matchScore: 82 },
      { relatedTerm: 'abandonment', relationType: 'word_family', relatedPartOfSpeech: 'noun', relatedMeaning: '遗弃，放弃', relatedPhoneticUk: '/əˈbændənmənt/', relatedPhoneticUs: '/əˈbændənmənt/', matchType: 'parsed_object', matchScore: 78 },
    ],
  }
}

export function createPreviewActivity() {
  const days = 365
  const items = Array.from({ length: days }, (_, index) => {
    const date = new Date()
    date.setDate(date.getDate() - (days - index - 1))
    const pulse = index % 9 === 0 ? 3 : index % 5 === 0 ? 2 : index % 3 === 0 ? 1 : 0
    const learnedCount = index % 11 === 0 ? 2 : pulse > 1 ? 1 : 0
    const reviewCount = pulse
    return {
      date: date.toISOString().slice(0, 10),
      learnedCount,
      reviewCount,
      totalCount: learnedCount + reviewCount,
    }
  })
  return {
    days,
    learnedTotal: items.reduce((sum, item) => sum + item.learnedCount, 0),
    reviewTotal: items.reduce((sum, item) => sum + item.reviewCount, 0),
    items,
  }
}

export function daysAgoIso(days) {
  const date = new Date()
  date.setDate(date.getDate() - days)
  return date.toISOString()
}
