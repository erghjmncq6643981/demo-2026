import { escapeHtml } from '/src/shared/text.js'
import {
  bindInlineAudio as bindInlineAudioShared,
  bindStudyTermCards as bindStudyTermCardsShared,
  normalizeArray,
  normalizeDefinitions,
  normalizeExamples,
  readText,
  renderCollocationMini,
  renderRelationItem,
  semanticRelations,
  stringifyValue,
  tagLabel,
} from '/src/shared/vocabulary.js'

export function createStudyCardFeature(ctx) {
  const {
    state,
    elements,
    confirmAction,
    setView,
    study,
    renderReviewFocus,
    findEntryForRecord,
    renderNotes,
    speak,
    speakSentence,
  } = ctx

  function renderRecord(record) {
    state.currentRecord = record
    state.currentSessionId = record?.sessionId || state.currentSessionId
    const parsed = record?.parsed || null
    const term = parsed?.term || record?.term || record?.normalizedTerm || 'Ready'
    elements.cacheState.textContent = record ? (record.cacheHit ? 'CACHE HIT' : 'AI GENERATED') : '等待输入'
    elements.wordTitle.textContent = term
    const uk = parsed?.phonetic?.uk ? `UK ${parsed.phonetic.uk}` : ''
    const us = parsed?.phonetic?.us ? `US ${parsed.phonetic.us}` : ''
    renderPhonetics(term, uk, us)
    renderDefinitions(parsed)
    renderTags(record?.tags)
    renderRelations(record?.relations)
    renderExamples(parsed)
    renderCollocations(parsed)
    renderMemoryTips(parsed)
    renderRawJson(record)
    renderReviewFocus(record)
    renderNotes(findEntryForRecord(record))
  }

  function renderPhonetics(term, uk, us) {
    const items = [
      { type: 'uk', label: 'UK', text: uk.replace(/^UK\s+/, '') },
      { type: 'us', label: 'US', text: us.replace(/^US\s+/, '') },
    ].filter((item) => item.text)
    if (!items.length) {
      elements.phoneticLine.className = 'phonetic phonetic-actions'
      elements.phoneticLine.textContent = '暂无音标'
      return
    }
    elements.phoneticLine.className = 'phonetic phonetic-actions'
    elements.phoneticLine.innerHTML = items
      .map(
        (item) => `
          <span class="phonetic-item">
            <span>${escapeHtml(item.text)}</span>
            <button class="mini-audio-button" type="button" data-voice-type="${item.type}" title="播放${item.label}发音">${item.label} ▶</button>
          </span>
        `,
      )
      .join('')
    elements.phoneticLine.querySelectorAll('[data-voice-type]').forEach((button) => {
      button.addEventListener('click', () => speak(term, button.getAttribute('data-voice-type')))
    })
  }

  function renderRawJson(record) {
    const parsed = record?.parsed || null
    elements.rawJson.textContent = parsed ? JSON.stringify(parsed, null, 2) : record?.rawContent || '{}'
    if (elements.sessionIdBadge) {
      const provider = record?.provider || 'AI'
      const model = record?.modelName || 'raw'
      elements.sessionIdBadge.textContent = record?.sessionId ? `${provider} · ${model} · #${record.sessionId}` : `${provider} · ${model}`
    }
  }

  function renderDefinitions(parsed) {
    const definitions = normalizeDefinitions(parsed)
    if (!definitions.length) {
      elements.meaningList.innerHTML = '<div class="empty">暂无释义</div>'
      return
    }
    elements.meaningList.innerHTML = definitions
      .map(
        (item) => `
          <div class="meaning-item">
            <span class="pos">${escapeHtml(item.pos || 'meaning')}</span>
            <div>
              <p>${escapeHtml(item.cn || '暂无中文释义')}</p>
              <p class="meaning-en">${escapeHtml(item.en || item.extra || '暂无英文释义')}</p>
            </div>
          </div>
        `,
      )
      .join('')
  }

  function renderTags(tags) {
    const list = Array.isArray(tags) ? tags : []
    if (!list.length) {
      elements.tagList.className = 'chips empty'
      elements.tagList.textContent = '暂无标签'
      return
    }
    elements.tagList.className = 'chips'
    elements.tagList.innerHTML = list.map((tag) => `<span class="chip tag-chip">${escapeHtml(tagLabel(tag))}</span>`).join('')
  }

  function renderRelations(relations) {
    const list = semanticRelations(relations)
    if (!list.length) {
      elements.relationList.className = 'relation-list empty'
      elements.relationList.textContent = '暂无关联词'
      return
    }
    elements.relationList.className = 'relation-list'
    elements.relationList.innerHTML = list.map((item) => renderRelationItem(item, 'related-term')).join('')
    bindStudyTermCards(elements.relationList, '[data-related-term]', '单词')
    bindInlineAudio(elements.relationList)
  }

  function bindInlineAudio(container) {
    bindInlineAudioShared(container, (text) => speak(text, elements.voiceSelect.value))
  }

  function bindStudyTermCards(container, selector, label) {
    bindStudyTermCardsShared(container, selector, (term) => confirmStudyTerm(term, label))
  }

  async function confirmStudyTerm(term, label = '单词') {
    const cleanTerm = String(term || '').trim()
    if (!cleanTerm) return
    const confirmed = await confirmAction({
      title: `学习${label}`,
      message: `是否去学习「${cleanTerm}」？当前学习卡会切换到这个${label}。`,
      acceptText: '去学习',
    })
    if (!confirmed) return
    elements.termInput.value = cleanTerm
    setView('studyView')
    study(cleanTerm)
  }

  function renderExamples(parsed) {
    const examples = normalizeExamples(parsed)
    if (!examples.length) {
      elements.examples.className = 'stack empty'
      elements.examples.textContent = '暂无例句'
      return
    }
    elements.examples.className = 'stack'
    elements.examples.innerHTML = examples
      .map(
        (item, index) => `
          <div class="example-item">
            <button class="icon-button" type="button" data-sentence-index="${index}" title="播放例句">▶</button>
            <p class="sentence">${escapeHtml(item.sentence || '')}</p>
            <p class="translation">${escapeHtml(item.translation || '')}</p>
          </div>
        `,
      )
      .join('')
    elements.examples.querySelectorAll('[data-sentence-index]').forEach((button) => {
      button.addEventListener('click', () => speakSentence(examples[Number(button.getAttribute('data-sentence-index'))]?.sentence))
    })
  }

  function renderCollocations(parsed) {
    const collocations = normalizeArray(parsed?.collocations || parsed?.phrases || parsed?.common_phrases)
    if (!collocations.length) {
      elements.collocations.className = 'collocation-list empty'
      elements.collocations.textContent = '暂无搭配'
      return
    }
    elements.collocations.className = 'collocation-list'
    elements.collocations.innerHTML = collocations.map(renderCollocationMini).join('')
    bindStudyTermCards(elements.collocations, '[data-collocation-term]', '词组')
    bindInlineAudio(elements.collocations)
  }

  function renderMemoryTips(parsed) {
    const tips = normalizeArray(parsed?.memory_tips || parsed?.memoryTips || parsed?.tips || parsed?.memory)
    if (!tips.length) {
      elements.memoryTips.className = 'stack empty'
      elements.memoryTips.textContent = '暂无记忆提示'
      return
    }
    elements.memoryTips.className = 'stack'
    elements.memoryTips.innerHTML = tips.map((item) => `<div class="tip-item">${escapeHtml(readText(item, ['content', 'tip', 'text', 'meaning']) || stringifyValue(item))}</div>`).join('')
  }

  return {
    renderRecord,
    renderPhonetics,
    renderRawJson,
    renderDefinitions,
    renderTags,
    renderRelations,
    bindInlineAudio,
    bindStudyTermCards,
    confirmStudyTerm,
    renderExamples,
    renderCollocations,
    renderMemoryTips,
  }
}
