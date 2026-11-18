import { escapeHtml } from '/src/shared/text.js'
import { showModal, hideModal } from '/src/shared/modal.js'
import { normalizeDefinitions, normalizeExamples, parseVocabularyPayload, renderCognitiveImageCard } from '/src/shared/vocabulary.js'
import { getRecentSearches, saveRecentSearch, clearRecentSearches } from '/src/features/learning/study/autocomplete.js'

const DIMENSION_LABELS = {
  peer: '🥬 同类延展',
  action: '🔪 动作动词',
  downstream: '🥫 加工产物',
  attribute: '🍋 感官属性',
}

const STATUS_LABELS = {
  mastered: '已掌握',
  vague: '待反刍',
  unlearned: '未学',
}

function clamp(val, min, max) {
  return Math.max(min, Math.min(max, val))
}

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
  let currentOpenMode = 'search'
  let currentNodeContext = null
  let lastFocusedBeforeOpen = null

  function getDialogPanel() {
    return elements.quickLookupModal?.querySelector?.('.quick-lookup-modal') || null
  }

  function applyModalOrigin(origin) {
    const dialog = getDialogPanel()
    if (!dialog || !dialog.style) return
    if (origin && typeof origin.x === 'number' && typeof origin.y === 'number' && typeof window !== 'undefined') {
      const dx = clamp((origin.x - window.innerWidth / 2) * 0.42, -160, 160)
      const dy = clamp((origin.y - window.innerHeight / 2) * 0.42, -120, 120)
      dialog.style.setProperty('--modal-origin-x', `${Math.round(dx)}px`)
      dialog.style.setProperty('--modal-origin-y', `${Math.round(dy)}px`)
      dialog.style.setProperty('--modal-origin-scale', '0.9')
    } else {
      dialog.style.setProperty('--modal-origin-x', '0px')
      dialog.style.setProperty('--modal-origin-y', '10px')
      dialog.style.setProperty('--modal-origin-scale', '0.96')
    }
  }

  function renderNodeContext(nodeContext) {
    if (!elements.quickLookupContext) return
    if (!nodeContext || currentOpenMode !== 'node') {
      elements.quickLookupContext.classList.add('hidden')
      elements.quickLookupContext.innerHTML = ''
      return
    }
    const { scenarioLabel = '', node = {} } = nodeContext
    const pills = []
    if (scenarioLabel) pills.push(`<span class="context-pill is-scene">${escapeHtml(scenarioLabel)}</span>`)
    const dimLabel = DIMENSION_LABELS[node.dimension] || (node.nodeType === 'core' ? '✨ 考纲核心' : '✨ 4D 衍生')
    if (dimLabel) pills.push(`<span class="context-pill">${escapeHtml(dimLabel)}</span>`)
    const statusLabel = STATUS_LABELS[node.status]
    if (statusLabel) pills.push(`<span class="context-pill is-status-${escapeHtml(node.status || 'unlearned')}">${escapeHtml(statusLabel)}</span>`)
    elements.quickLookupContext.innerHTML = pills.join('')
    elements.quickLookupContext.classList.toggle('hidden', pills.length === 0)
    if (elements.quickLookupTitle) {
      elements.quickLookupTitle.textContent = '星系节点词卡'
    }
  }

  function applyPresentationMode(mode, nodeContext = null) {
    currentOpenMode = mode === 'node' ? 'node' : 'search'
    currentNodeContext = currentOpenMode === 'node' ? nodeContext : null
    elements.quickLookupModal?.classList?.toggle('is-node-card-mode', currentOpenMode === 'node')

    if (currentOpenMode === 'node') {
      if (elements.quickLookupEyebrow) elements.quickLookupEyebrow.textContent = 'Galaxy Word Card'
      if (elements.quickLookupTitle) elements.quickLookupTitle.textContent = '星系节点词卡'
      renderNodeContext(currentNodeContext)
      elements.quickLookupToggleSearchBtn?.classList?.remove('hidden')
    } else {
      if (elements.quickLookupEyebrow) elements.quickLookupEyebrow.textContent = 'Quick Lookup'
      if (elements.quickLookupTitle) elements.quickLookupTitle.textContent = '全局快速查词 (⌘K / Ctrl+K)'
      elements.quickLookupContext?.classList?.add('hidden')
      elements.quickLookupToggleSearchBtn?.classList?.add('hidden')
    }
  }

  function renderRecentSearches() {
    if (!elements.quickLookupRecentBar || !elements.quickLookupRecentChips) return
    const list = getRecentSearches()
    if (!list.length) {
      elements.quickLookupRecentBar.classList.add('hidden')
      return
    }
    elements.quickLookupRecentBar.classList.remove('hidden')
    const activeTerm = (currentLookupTerm || elements.quickLookupInput?.value || '').trim().toLowerCase()
    elements.quickLookupRecentChips.innerHTML = list
      .map(
        (t) => `
          <button type="button" class="recent-chip ${t.toLowerCase() === activeTerm ? 'active' : ''}" data-quick-recent-term="${escapeHtml(t)}" title="查看「${escapeHtml(t)}」">
            ${escapeHtml(t)}
          </button>
        `,
      )
      .join('')

    elements.quickLookupRecentChips.querySelectorAll('[data-quick-recent-term]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const term = btn.getAttribute('data-quick-recent-term')
        if (term) {
          if (elements.quickLookupInput) elements.quickLookupInput.value = term
          lookup(term)
        }
      })
    })
  }

  function reset() {
    currentLookupTerm = ''
    isLookingUp = false
    renderRecentSearches()
    if (!elements.quickLookupContent) return
    elements.quickLookupContent.className = 'quick-lookup-content empty'
    const list = getRecentSearches()
    elements.quickLookupContent.innerHTML = list.length
      ? '输入英语单词回车查询，或直接点击上方最近查词快速重温'
      : '输入任意英文单词快速查看 AI 词卡'
  }

  function showLoading(term) {
    if (!elements.quickLookupContent) return
    elements.quickLookupContent.className = 'quick-lookup-content loading'
    const previewDef = currentOpenMode === 'node' ? currentNodeContext?.node?.definition : ''
    elements.quickLookupContent.innerHTML = `
      ${currentOpenMode === 'node' ? `
        <div class="quick-card-hero quick-card-loading-preview">
          <div>
            <div class="quick-card-title-row">
              <h4>${escapeHtml(term)}</h4>
              <span class="mini-pill">词卡加载中</span>
            </div>
            ${previewDef ? `<p class="quick-loading-def">${escapeHtml(previewDef)}</p>` : ''}
          </div>
          <span class="quick-loading-tag">⏳ 同步音标与认知画像...</span>
        </div>
      ` : `
        <div class="quick-lookup-loading">
          <span class="quick-lookup-loading-icon">⏳</span>
          <span>正在查询「<strong>${escapeHtml(term)}</strong>」AI 词卡...</span>
        </div>
      `}
      <div class="quick-card-skeleton" aria-hidden="true">
        <div class="skeleton-bar is-lg"></div>
        <div class="skeleton-bar is-md"></div>
        <div class="skeleton-block"></div>
      </div>
    `
  }

  function open(initialTerm = '', options = {}) {
    if (!elements.quickLookupModal) return
    if (typeof document !== 'undefined' && elements.quickLookupModal.classList.contains('hidden')) {
      const active = document.activeElement
      if (active && active !== document.body && !elements.quickLookupModal.contains(active)) {
        lastFocusedBeforeOpen = active
      }
    }
    const term = String(initialTerm || '').trim()
    const mode = options?.mode === 'node' && term ? 'node' : 'search'
    const nodeContext = mode === 'node'
      ? { scenarioLabel: options?.scenarioLabel || '', node: options?.node || {} }
      : null

    applyModalOrigin(options?.origin)
    applyPresentationMode(mode, nodeContext)
    showModal(elements.quickLookupModal)
    renderRecentSearches()

    if (term) {
      if (elements.quickLookupInput) {
        elements.quickLookupInput.value = term
        if (mode === 'search') {
          elements.quickLookupInput.focus()
          elements.quickLookupInput.select()
        }
      }
      if (mode === 'node') {
        elements.quickLookupCloseBtn?.focus?.({ preventScroll: true })
      }
      return lookup(term)
    }

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

  function close(options = {}) {
    if (!elements.quickLookupModal) return
    const shouldRestoreFocus = options?.restoreFocus !== false
    const restoreTarget = shouldRestoreFocus ? lastFocusedBeforeOpen : null
    lastFocusedBeforeOpen = null
    if (typeof document !== 'undefined' && elements.quickLookupModal.contains(document.activeElement)) {
      document.activeElement?.blur?.()
    }
    hideModal(elements.quickLookupModal)
    if (shouldRestoreFocus && typeof document !== 'undefined') {
      let focusTarget =
        restoreTarget &&
        (!document.contains || document.contains(restoreTarget)) &&
        !restoreTarget.closest?.('.hidden')
          ? restoreTarget
          : null
      if (!focusTarget && restoreTarget) {
        const remainingModals = Array.from(document.querySelectorAll('.modal-backdrop:not(.hidden)'))
        const topRemaining = remainingModals[remainingModals.length - 1]
        focusTarget =
          topRemaining?.querySelector?.('input:not([type="hidden"]):not([disabled]), textarea:not([disabled])') || null
      }
      if (focusTarget && typeof focusTarget.focus === 'function') {
        try {
          focusTarget.focus({ preventScroll: true })
        } catch {
          // 忽略不可聚焦节点异常
        }
      }
    }
  }

  async function lookup(term, options = {}) {
    const clean = String(term || '').trim()
    if (!clean) return
    currentLookupTerm = clean
    isLookingUp = true
    saveRecentSearch(clean)
    renderRecentSearches()
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
          agentCode: 'english_vocabulary',
          templateCode: 'english_vocab_card_json',
          forceRefresh,
          lookupSource: 'quick_lookup',
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
    elements.quickLookupContent.className = 'quick-lookup-content quick-card-content-enter'
    const parsed = parseVocabularyPayload(record?.parsed)
    const term = record?.term || record?.normalizedTerm || parsed?.term || 'Word'
    const ukRaw = parsed?.phonetic?.uk || parsed?.['phonetic.uk'] || parsed?.phonetic_uk || parsed?.uk_phonetic || (typeof parsed?.phonetic === 'string' ? parsed.phonetic : '') || ''
    const usRaw = parsed?.phonetic?.us || parsed?.['phonetic.us'] || parsed?.phonetic_us || parsed?.us_phonetic || ''
    const cleanUk = ukRaw ? String(ukRaw).replace(/^UK\s+/i, '').replace(/^\/+|\/+$/g, '') : ''
    const cleanUs = usRaw ? String(usRaw).replace(/^US\s+/i, '').replace(/^\/+|\/+$/g, '') : ''
    const meanings = normalizeDefinitions(parsed)
    const examples = normalizeExamples(parsed).slice(0, 2)
    const cognitiveHtml = renderCognitiveImageCard(parsed, { compact: true })
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
      ${cognitiveHtml}
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
      close({ restoreFocus: false })
      if (elements.termInput) elements.termInput.value = term
      setView?.('studyView')
      study?.(term)
    })
    renderRecentSearches()
  }

  function switchToSearch() {
    applyPresentationMode('search', null)
    if (elements.quickLookupInput) {
      elements.quickLookupInput.value = currentLookupTerm || elements.quickLookupInput.value || ''
      elements.quickLookupInput.focus()
      elements.quickLookupInput.select()
    }
  }

  function bind() {
    renderRecentSearches()
    elements.quickLookupCloseBtn?.addEventListener('click', close)
    elements.quickLookupToggleSearchBtn?.addEventListener('click', switchToSearch)
    elements.quickLookupModal?.addEventListener('click', (event) => {
      if (event.target === elements.quickLookupModal) close()
    })
    elements.quickLookupForm?.addEventListener('submit', (event) => {
      event.preventDefault()
      const term = elements.quickLookupInput?.value.trim()
      if (term) lookup(term)
    })
    elements.clearQuickRecentBtn?.addEventListener('click', (event) => {
      event.preventDefault()
      event.stopPropagation()
      clearRecentSearches()
      renderRecentSearches()
      if (!currentLookupTerm) {
        reset()
      }
    })
  }

  if (typeof window !== 'undefined') {
    window.__openQuickLookup = open
  }

  return {
    open,
    close,
    lookup,
    bind,
    switchToSearch,
  }
}
