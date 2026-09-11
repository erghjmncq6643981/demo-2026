import { escapeHtml } from '/src/shared/text.js'
import { normalizeDefinitions, normalizeExamples } from '/src/shared/vocabulary.js'

export function createQuickLookupFeature({
  elements,
  request,
  toast,
  speak,
  speakSentence,
  preloadAudio,
  study,
  setView,
}) {
  let currentLookupTerm = ''
  let isLookingUp = false

  function reset() {
    currentLookupTerm = ''
    isLookingUp = false
    if (!elements.quickLookupContent) return
    elements.quickLookupContent.className = 'quick-lookup-content empty'
    elements.quickLookupContent.innerHTML = '输入任意英文单词快速查看 AI 词卡'
  }

  function showLoading(term) {
    if (!elements.quickLookupContent) return
    elements.quickLookupContent.className = 'quick-lookup-content loading'
    elements.quickLookupContent.innerHTML = `
      <div class="quick-lookup-loading">
        <span class="quick-lookup-loading-icon">⏳</span>
        <span>正在查询「<strong>${escapeHtml(term)}</strong>」AI 词卡...</span>
      </div>
    `
  }

  function open(initialTerm = '') {
    if (!elements.quickLookupModal) return
    elements.quickLookupModal.classList.remove('hidden')
    const term = String(initialTerm || '').trim()
    if (term) {
      if (elements.quickLookupInput) {
        elements.quickLookupInput.value = term
        elements.quickLookupInput.focus()
        elements.quickLookupInput.select()
      }
      lookup(term)
    } else {
      if (isLookingUp && currentLookupTerm) {
        if (elements.quickLookupInput) {
          elements.quickLookupInput.value = currentLookupTerm
          elements.quickLookupInput.focus()
          elements.quickLookupInput.select()
        }
        showLoading(currentLookupTerm)
      } else {
        if (elements.quickLookupInput) {
          elements.quickLookupInput.value = ''
          elements.quickLookupInput.focus()
        }
        reset()
      }
    }
  }

  function close() {
    if (!elements.quickLookupModal) return
    elements.quickLookupModal.classList.add('hidden')
  }

  async function lookup(term, options = {}) {
    const clean = String(term || '').trim()
    if (!clean) return
    currentLookupTerm = clean
    isLookingUp = true
    showLoading(clean)

    const forceRefresh = Boolean(options.forceRefresh)
    try {
      // 1. 若非强制刷新，优先查询本地/服务端缓存
      if (!forceRefresh) {
        const cached = await request(`/api/v1/english/vocabularies/${encodeURIComponent(clean)}`)
        if (cached && !cached.generating) {
          if (currentLookupTerm === clean) {
            isLookingUp = false
            renderQuickRecord(cached)
          }
          return
        }
      }

      // 2. 若未缓存、后台生成中或强制刷新，调用 study 接口（后端自动合并并发，避免重复请求模型）
      const studyRecord = await request('/api/v1/english/vocabularies/study', {
        method: 'POST',
        body: JSON.stringify({
          term: clean,
          agentCode: elements.agentSelect?.value || 'english_vocabulary_plan',
          templateCode: elements.templateSelect?.value || 'vocabulary_card_single',
          forceRefresh,
        }),
      })

      if (currentLookupTerm === clean) {
        isLookingUp = false
        renderQuickRecord(studyRecord)
      }
    } catch (error) {
      if (currentLookupTerm === clean) {
        isLookingUp = false
        if (elements.quickLookupContent) {
          elements.quickLookupContent.className = 'quick-lookup-content error'
          elements.quickLookupContent.innerHTML = `<div class="quick-lookup-error">查询失败：${escapeHtml(error.message)}</div>`
        }
      }
    }
  }

  function renderQuickRecord(record) {
    if (!elements.quickLookupContent) return
    elements.quickLookupContent.className = 'quick-lookup-content'
    let parsed = record?.parsed || {}
    if (typeof parsed === 'string') {
      try {
        parsed = JSON.parse(parsed)
      } catch {
        parsed = {}
      }
    }
    const term = record?.term || record?.normalizedTerm || parsed?.term || 'Word'
    const ukRaw = parsed?.phonetic?.uk || parsed?.['phonetic.uk'] || parsed?.phonetic_uk || parsed?.uk_phonetic || (typeof parsed?.phonetic === 'string' ? parsed.phonetic : '') || ''
    const usRaw = parsed?.phonetic?.us || parsed?.['phonetic.us'] || parsed?.phonetic_us || parsed?.us_phonetic || ''
    const cleanUk = ukRaw ? String(ukRaw).replace(/^UK\s+/i, '').replace(/^\/+|\/+$/g, '') : ''
    const cleanUs = usRaw ? String(usRaw).replace(/^US\s+/i, '').replace(/^\/+|\/+$/g, '') : ''
    const meanings = normalizeDefinitions(parsed)
    const examples = normalizeExamples(parsed).slice(0, 2)
    preloadAudio?.(term, 'us')
    preloadAudio?.(term, 'uk')

    elements.quickLookupContent.innerHTML = `
      ${record?.isAliasHit || (record?.queriedTerm && record.queriedTerm.toLowerCase() !== (term || '').toLowerCase()) ? `
        <div class="quick-lemma-notice">
          <div class="quick-lemma-info">
            <span class="quick-lemma-icon">🔀</span>
            <span>检索词：<strong class="quick-lemma-highlight">${escapeHtml(record.queriedTerm)}</strong> · 已关联至原形单词 <strong class="quick-lemma-highlight">${escapeHtml(record.lemma || term)}</strong></span>
          </div>
          <button type="button" class="quick-lemma-standalone-btn" data-standalone-term="${escapeHtml(record.queriedTerm)}" title="不关联至原形，坚持以「${escapeHtml(record.queriedTerm)}」生成独立词卡">以独立单词查询 →</button>
        </div>
      ` : ''}
      <div class="quick-card-hero">
        <div>
          <div class="quick-card-title-row">
            <h4>${escapeHtml(term)}</h4>
            <span class="mini-pill">${record?.cacheHit ? '缓存命中' : 'AI 生成'}</span>
          </div>
          <div class="quick-phonetic-row">
            ${cleanUk ? `<span class="phonetic-item"><span>UK /${escapeHtml(cleanUk)}/</span> <button type="button" class="mini-audio-button" data-voice-type="uk" title="播放英音">UK ▶</button></span>` : ''}
            ${cleanUs ? `<span class="phonetic-item"><span>US /${escapeHtml(cleanUs)}/</span> <button type="button" class="mini-audio-button" data-voice-type="us" title="播放美音">US ▶</button></span>` : ''}
            ${!cleanUk && !cleanUs ? `<span>暂无音标</span> <button type="button" class="mini-audio-button" data-quick-speak="${escapeHtml(term)}">🔊 发音</button>` : ''}
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
          ${examples.map((ex, index) => `
            <div class="quick-example-item">
              <div class="quick-example-header">
                <p class="sentence">${escapeHtml(ex.sentence || '')}</p>
                ${ex.sentence ? `<button type="button" class="mini-audio-button" data-quick-sentence="${index}" title="播放例句">▶</button>` : ''}
              </div>
              <p class="translation">${escapeHtml(ex.translation || '')}</p>
            </div>
          `).join('')}
        </div>
      ` : ''}
    `

    elements.quickLookupContent.querySelectorAll('[data-quick-sentence]')?.forEach((btn) => {
      btn.addEventListener('click', (event) => {
        event.preventDefault()
        event.stopPropagation()
        try {
          const idx = Number(btn.getAttribute('data-quick-sentence'))
          const sentence = examples[idx]?.sentence
          if (sentence) {
            speakSentence?.(sentence)
          } else {
            toast?.('暂无可播放例句')
          }
        } catch (error) {
          console.error('播放例句异常:', error)
          toast?.('播放例句失败')
        }
      })
    })

    elements.quickLookupContent.querySelectorAll('[data-quick-inflection]')?.forEach((tag) => {
      tag.addEventListener('click', (e) => {
        e.preventDefault()
        e.stopPropagation()
        const infTerm = e.currentTarget.getAttribute('data-quick-inflection')
        if (infTerm) {
          if (elements.quickLookupInput) elements.quickLookupInput.value = infTerm
          lookup(infTerm)
        }
      })
    })

    elements.quickLookupContent.querySelectorAll('[data-voice-type]')?.forEach((btn) => {
      btn.addEventListener('click', (event) => {
        event.preventDefault()
        event.stopPropagation()
        try {
          const voiceType = btn.getAttribute('data-voice-type') || 'us'
          speak?.(term, voiceType)
        } catch (error) {
          console.error('发音播放异常:', error)
          toast?.('发音播放异常')
        }
      })
    })

    elements.quickLookupContent.querySelector('[data-quick-speak]')?.addEventListener('click', (event) => {
      event.preventDefault()
      event.stopPropagation()
      try {
        speak?.(term)
      } catch (error) {
        console.error('发音播放异常:', error)
        toast?.('发音播放异常')
      }
    })

    elements.quickLookupContent.querySelectorAll('[data-standalone-term]')?.forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.preventDefault()
        e.stopPropagation()
        const standaloneTerm = btn.getAttribute('data-standalone-term')
        if (standaloneTerm) {
          if (elements.quickLookupInput) elements.quickLookupInput.value = standaloneTerm
          lookup(standaloneTerm, { forceRefresh: true })
        }
      })
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
