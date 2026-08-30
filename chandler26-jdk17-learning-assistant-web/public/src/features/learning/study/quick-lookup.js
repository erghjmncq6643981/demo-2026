import { escapeHtml } from '/src/shared/text.js'
import { normalizeDefinitions, normalizeExamples } from '/src/shared/vocabulary.js'

export function createQuickLookupFeature({
  state,
  elements,
  request,
  speak,
  speakSentence,
  study,
  confirmAction,
  setView,
}) {
  let activeRecord = null

  function open(initialTerm = '') {
    if (!elements.quickLookupModal) return
    elements.quickLookupModal.classList.remove('hidden')
    if (elements.quickLookupInput) {
      elements.quickLookupInput.value = initialTerm || ''
      elements.quickLookupInput.focus()
      elements.quickLookupInput.select()
    }
    if (initialTerm) {
      lookup(initialTerm)
    }
  }

  function close() {
    if (!elements.quickLookupModal) return
    elements.quickLookupModal.classList.add('hidden')
    activeRecord = null
  }

  async function lookup(term) {
    const clean = String(term || '').trim()
    if (!clean) return
    if (!elements.quickLookupContent) return
    elements.quickLookupContent.className = 'quick-lookup-content loading'
    elements.quickLookupContent.innerHTML = '<div class="quick-lookup-loading">正在查询 AI 词卡...</div>'
    try {
      const record = await request(`/api/v1/english/vocabularies/${encodeURIComponent(clean)}`)
      if (record) {
        renderQuickRecord(record)
      } else {
        // If not cached, fetch or generate via study
        const studyRecord = await request('/api/v1/english/vocabularies/study', {
          method: 'POST',
          body: JSON.stringify({
            term: clean,
            agentCode: elements.agentSelect?.value || 'english_vocabulary_plan',
            templateCode: elements.templateSelect?.value || 'vocabulary_card_single',
          }),
        })
        renderQuickRecord(studyRecord)
      }
    } catch (error) {
      elements.quickLookupContent.className = 'quick-lookup-content error'
      elements.quickLookupContent.innerHTML = `<div class="quick-lookup-error">查询失败：${escapeHtml(error.message)}</div>`
    }
  }

  function renderQuickRecord(record) {
    activeRecord = record
    if (!elements.quickLookupContent) return
    elements.quickLookupContent.className = 'quick-lookup-content'
    const parsed = record?.parsed || {}
    const term = record?.term || record?.normalizedTerm || parsed?.term || 'Word'
    const cleanUk = parsed?.phonetic?.uk ? String(parsed.phonetic.uk).replace(/^\/+|\/+$/g, '') : ''
    const cleanUs = parsed?.phonetic?.us ? String(parsed.phonetic.us).replace(/^\/+|\/+$/g, '') : ''
    const uk = cleanUk ? `UK /${cleanUk}/` : ''
    const us = cleanUs ? `US /${cleanUs}/` : ''
    const meanings = normalizeDefinitions(parsed)
    const examples = normalizeExamples(parsed).slice(0, 2)

    elements.quickLookupContent.innerHTML = `
      ${record?.isAliasHit || (record?.queriedTerm && record.queriedTerm.toLowerCase() !== (term || '').toLowerCase()) ? `
        <div class="quick-lemma-notice">
          <span class="quick-lemma-icon">🔀</span>
          <span>检索词：<strong class="quick-lemma-highlight">${escapeHtml(record.queriedTerm)}</strong> · 已关联至原形单词 <strong class="quick-lemma-highlight">${escapeHtml(record.lemma || term)}</strong></span>
        </div>
      ` : ''}
      <div class="quick-card-hero">
        <div>
          <div class="quick-card-title-row">
            <h4>${escapeHtml(term)}</h4>
            <span class="mini-pill">${record?.cacheHit ? '缓存命中' : 'AI 生成'}</span>
          </div>
          <div class="quick-phonetic-row">
            <span>${escapeHtml([uk, us].filter(Boolean).join(' · ') || '暂无音标')}</span>
            <button type="button" class="mini-audio-button" data-quick-speak="${escapeHtml(term)}">🔊 发音</button>
          </div>
        </div>
        <div class="quick-card-actions">
          <button type="button" class="primary-button compact-primary" data-quick-goto="${escapeHtml(term)}">进入深度精读 →</button>
        </div>
      </div>
      ${Array.isArray(record?.inflections) && record.inflections.length ? `
        <div class="quick-inflections-row">
          <span class="quick-inflections-label">形态变体：</span>
          ${record.inflections.map((inf) => `<span class="quick-inflection-tag" data-quick-inflection="${escapeHtml(inf)}">${escapeHtml(inf)}</span>`).join('')}
        </div>
      ` : ''}
      <div class="quick-card-meanings">
        ${meanings.map((m) => `
          <div class="quick-meaning-item">
            <span class="pos">${escapeHtml(m.pos || 'meaning')}</span>
            <span class="meaning-cn">${escapeHtml(m.cn || '暂无中文释义')}</span>
            ${m.en ? `<span class="meaning-en">${escapeHtml(m.en)}</span>` : ''}
          </div>
        `).join('')}
      </div>
      ${examples.length ? `
        <div class="quick-card-examples">
          <p class="section-subtitle">经典例句</p>
          ${examples.map((ex) => `<div class="quick-example-item"><p class="sentence">${escapeHtml(ex.sentence || '')}</p><p class="translation">${escapeHtml(ex.translation || '')}</p></div>`).join('')}
        </div>
      ` : ''}
    `

    elements.quickLookupContent.querySelectorAll('[data-quick-inflection]')?.forEach((tag) => {
      tag.addEventListener('click', (e) => {
        const infTerm = e.currentTarget.getAttribute('data-quick-inflection')
        if (infTerm) {
          if (elements.quickLookupInput) elements.quickLookupInput.value = infTerm
          lookup(infTerm)
        }
      })
    })

    elements.quickLookupContent.querySelector('[data-quick-speak]')?.addEventListener('click', () => {
      speak?.(term)
    })

    elements.quickLookupContent.querySelector('[data-quick-goto]')?.addEventListener('click', () => {
      close()
      if (elements.termInput) elements.termInput.value = term
      setView?.('studyView')
      study?.(term)
    })
  }

  function bind() {
    elements.quickLookupCloseBtn?.addEventListener('click', close)
    elements.quickLookupModal?.addEventListener('click', (event) => {
      if (event.target === elements.quickLookupModal) close()
    })
    elements.quickLookupForm?.addEventListener('submit', (event) => {
      event.preventDefault()
      const term = elements.quickLookupInput?.value.trim()
      if (term) lookup(term)
    })
  }

  return {
    open,
    close,
    lookup,
    bind,
  }
}
