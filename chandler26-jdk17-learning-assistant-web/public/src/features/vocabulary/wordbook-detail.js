import { escapeHtml } from '/src/shared/text.js'
import { cleanPhonetic, normalizeArray, renderCollocationMini, renderRelationItem, semanticRelations, statusLabel } from '/src/shared/vocabulary.js'

export function createWordbookDetailFeature(ctx) {
  const {
    state,
    elements,
    normalizeDefinitions,
    normalizeExamples,
    renderMarkdown,
    readText,
    stringifyValue,
    tagLabel,
    editCurrentNote,
    openEntryStatusModal,
    speak,
    speakSentence,
    bindStudyTermCards,
    bindInlineAudio,
    request,
    toast,
    setButtonLoading,
    sameId,
  } = ctx

  function updateOuterCardControls(entry) {
    if (!elements.wordbookCardModal) return
    const entries = Array.isArray(state.wordbookEntries) ? state.wordbookEntries : []
    const currentIndex = entry ? entries.findIndex((item) => sameId(item.id, entry.id)) : -1
    const hasEntry = Boolean(entry && currentIndex >= 0)
    const totalCount = Number(state.wordbookTotal ?? entries.length)
    const pageSize = Number(state.wordbookPageSize || 30)
    const maxPage = Math.max(1, Math.ceil(totalCount / pageSize))
    const currentPage = Number(state.wordbookPage || 1)

    if (elements.wordbookCardProgress) {
      if (hasEntry && entries.length > 0) {
        const overallIndex = (currentPage - 1) * pageSize + (currentIndex + 1)
        const progressText = totalCount > entries.length
          ? `${overallIndex} / ${totalCount}`
          : `${currentIndex + 1} / ${entries.length}`
        elements.wordbookCardProgress.textContent = progressText
        elements.wordbookCardProgress.title = `第 ${currentPage} / ${maxPage} 页（当前页第 ${currentIndex + 1} / ${entries.length} 词，共 ${totalCount} 词）`
        elements.wordbookCardProgress.classList.remove('hidden')
      } else {
        elements.wordbookCardProgress.textContent = '0 / 0'
        elements.wordbookCardProgress.title = ''
        elements.wordbookCardProgress.classList.add('hidden')
      }
    }
    if (elements.wordbookCardPrevBtn) {
      elements.wordbookCardPrevBtn.disabled = !hasEntry || (currentIndex <= 0 && currentPage <= 1)
    }
    if (elements.wordbookCardNextBtn) {
      elements.wordbookCardNextBtn.disabled = !hasEntry || (currentIndex >= entries.length - 1 && currentPage >= maxPage)
    }
    if (elements.wordbookCardGenerateBtn) {
      if (!entry) {
        elements.wordbookCardGenerateBtn.disabled = true
        elements.wordbookCardGenerateBtn.textContent = 'AI 生成词卡'
        elements.wordbookCardGenerateBtn.title = '选择单词后生成词卡'
        elements.wordbookCardGenerateBtn.dataset.generateCard = ''
      } else {
        let parsed = entry.parsed
        if (typeof parsed === 'string') {
          try { parsed = JSON.parse(parsed) } catch { parsed = {} }
        }
        parsed = parsed || {}
        let defs = normalizeDefinitions(parsed)
        if (!defs.length && entry.meaningText) {
          defs = [{ pos: 'meaning', cn: entry.meaningText }]
        }
        const exs = normalizeExamples(parsed)
        const isCardReady = entry.cardStatus === 'ready' || (defs.length > 0 && exs.length > 0)
        const isGenerating = entry.cardStatus === 'generating'
        elements.wordbookCardGenerateBtn.dataset.generateCard = String(entry.id || '')
        elements.wordbookCardGenerateBtn.textContent = isGenerating ? '生成中...' : (isCardReady ? '重新生成词卡' : 'AI 生成词卡')
        elements.wordbookCardGenerateBtn.title = isCardReady ? '重新通过 AI 生成更丰富的例句、记忆法与关联词' : '通过 AI 生成例句、记忆法、搭配与关联词'
        elements.wordbookCardGenerateBtn.disabled = isGenerating
      }
    }
  }

  function renderWordbookFocus(entry) {
    if (!entry) {
      elements.wordbookFocus.className = 'empty'
      elements.wordbookFocus.textContent = '选择单词后查看详情和笔记'
      if (elements.wordbookCardModalTitle) {
        elements.wordbookCardModalTitle.textContent = '单词卡片'
      }
      updateOuterCardControls(null)
      return
    }
    let parsed = entry.parsed
    if (typeof parsed === 'string') {
      try { parsed = JSON.parse(parsed) } catch { parsed = {} }
    }
    parsed = parsed || {}
    const hasParsedDetail = Boolean(parsed && typeof parsed === 'object' && Object.keys(parsed).length > 0)
    const isCardReady = entry.cardStatus === 'ready'
    const isDetailLoading = isCardReady && !hasParsedDetail
    let definitions = normalizeDefinitions(parsed)
    if (!definitions.length && entry.meaningText) {
      definitions = [{ pos: 'meaning', cn: entry.meaningText }]
    }
    const examples = normalizeExamples(parsed).slice(0, 3)
    const collocations = normalizeArray(parsed?.collocations || parsed?.phrases || parsed?.common_phrases).slice(0, 6)
    const memoryTips = normalizeArray(parsed?.memory_tips || parsed?.memoryTips || parsed?.tips || parsed?.memory).slice(0, 3)
    const tags = Array.isArray(entry.tags) ? entry.tags.slice(0, 6) : []
    const relations = semanticRelations(entry.relations).slice(0, 6)
    const pUk = parsed?.phonetic?.uk || parsed?.['phonetic.uk'] || parsed?.phonetic_uk || parsed?.phoneticUk
    const pUs = parsed?.phonetic?.us || parsed?.['phonetic.us'] || parsed?.phonetic_us || parsed?.phoneticUs
    const pSingle = typeof parsed?.phonetic === 'string' ? parsed.phonetic : (entry.phonetic || null)

    const phoneticItems = [
      pUk && { type: 'uk', label: 'UK', text: cleanPhonetic(pUk) ? `/${cleanPhonetic(pUk)}/` : pUk },
      pUs && { type: 'us', label: 'US', text: cleanPhonetic(pUs) ? `/${cleanPhonetic(pUs)}/` : pUs },
    ].filter(Boolean)
    if (!phoneticItems.length && pSingle) {
      const c = cleanPhonetic(pSingle)
      phoneticItems.push({ type: 'us', label: '', text: c.startsWith('[') && c.endsWith(']') ? c : `/${c}/` })
    }
    const cognitiveImage = parsed?.cognitive_image || parsed?.cognitiveImage || null
    const etymology = parsed?.etymology || null
    const definitionsHtml = definitions.length
      ? definitions
          .map((item) => {
            const posBadge = item.pos && item.pos !== 'meaning' && item.pos !== 'pos' ? `<span>${escapeHtml(item.pos)}</span>` : ''
            return `<div>${posBadge}<p>${escapeHtml(item.cn || item.en || '')}</p></div>`
          })
          .join('')
      : '<div class="empty">暂无释义</div>'

    const cognitiveImageHtml = cognitiveImage && (cognitiveImage.title || cognitiveImage.description)
      ? `
        <div class="cognitive-image-card">
          <div class="cognitive-image-header">
            <span class="cognitive-badge">母语认知画像</span>
            <h5 class="cognitive-title">${escapeHtml(cognitiveImage.title || '核心心智画面')}</h5>
          </div>
          <p class="cognitive-desc">${escapeHtml(cognitiveImage.description || '')}</p>
          ${cognitiveImage.usage_note ? `<div class="cognitive-note"><span>💡 ${escapeHtml(cognitiveImage.usage_note)}</span></div>` : ''}
        </div>
      `
      : (isDetailLoading
        ? `
          <div class="cognitive-image-card cognitive-image-card-loading">
            <div class="cognitive-image-header">
              <span class="cognitive-badge">母语认知画像</span>
              <h5 class="cognitive-title">心智画面加载中...</h5>
            </div>
            <p class="cognitive-desc">正在拉取该单词的母语认知画像与语境构建...</p>
          </div>
        `
        : '')

    const etymologyComponents = Array.isArray(etymology?.components) ? etymology.components : []
    const etymologyFamily = Array.isArray(etymology?.word_family) ? etymology.word_family : []
    const etymologyHtml = (etymologyComponents.length || etymologyFamily.length)
      ? `
        <div class="focus-section">
          <div class="panel-heading compact-heading">
            <h3>词汇拓展与构词积木</h3>
          </div>
          <div class="etymology-block">
            ${etymologyComponents.length ? `
              <div class="etymology-chips">
                ${etymologyComponents.map((part) => `
                  <div class="etymology-chip ${escapeHtml(part.type || 'root')}">
                    <span class="chip-tag">${escapeHtml(part.type === 'prefix' ? '前缀' : part.type === 'suffix' ? '后缀' : '词根')}</span>
                    <strong class="chip-text">${escapeHtml(part.text || '')}</strong>
                    <span class="chip-desc">${escapeHtml(part.origin ? `${part.origin}: ` : '')}${escapeHtml(part.meaning || '')}</span>
                  </div>
                `).join('')}
              </div>
            ` : ''}
            ${etymologyFamily.length ? `
              <div class="etymology-family-title">派生词族：</div>
              <div class="etymology-family-list">
                ${etymologyFamily.map((wf) => `
                  <div class="etymology-family-item">
                    <strong data-focus-related="${escapeHtml(wf.word)}">${escapeHtml(wf.word)}</strong>
                    <span>${escapeHtml(wf.part_of_speech || '')} ${escapeHtml(wf.meaning || '')}</span>
                  </div>
                `).join('')}
              </div>
            ` : ''}
          </div>
        </div>
      `
      : ''

    const collocationsHtml = collocations.length
      ? `
        <div class="focus-section">
          <div class="panel-heading compact-heading">
            <h3>高频短语与搭配</h3>
            <span class="mini-pill">${collocations.length}</span>
          </div>
          <div class="collocation-card-grid">
            ${collocations.map((item) => {
              const phrase = typeof item === 'string' ? item : item.phrase || item.text || ''
              const meaning = typeof item === 'string' ? '' : item.meaning || item.translation || ''
              return `
                <div class="collocation-card">
                  <div class="collocation-card-header">
                    <strong class="collocation-phrase" data-collocation-term="${escapeHtml(phrase)}">${escapeHtml(phrase)}</strong>
                    ${phrase ? `<button class="mini-audio-button" type="button" data-speak-text="${escapeHtml(phrase)}" title="朗读短语">▶</button>` : ''}
                  </div>
                  ${meaning ? `<p class="collocation-meaning">${escapeHtml(meaning)}</p>` : ''}
                </div>
              `
            }).join('')}
          </div>
        </div>
      `
      : (isDetailLoading
        ? `
          <div class="focus-section">
            <div class="panel-heading compact-heading">
              <h3>高频短语与搭配</h3>
            </div>
            <div class="card-detail-loading-placeholder"><span class="card-loading-spinner"></span> 搭配与短语加载中...</div>
          </div>
        `
        : '')
    const term = entry.term || entry.normalizedTerm || ''
    if (elements.wordbookCardModalTitle) {
      elements.wordbookCardModalTitle.textContent = `单词卡片 · ${term}`
    }
    updateOuterCardControls(entry)
    elements.wordbookFocus.className = 'wordbook-focus-card'
    elements.wordbookFocus.innerHTML = `
      <div class="wordbook-focus-head">
        <div>
          <p class="eyebrow">${escapeHtml(statusLabel(entry.status))} · 阶段 ${entry.reviewStage ?? 0}</p>
          <h4>${escapeHtml(entry.term || entry.normalizedTerm)}</h4>
          <div class="phonetic phonetic-actions">
            ${
              phoneticItems.length
                ? phoneticItems
                    .map(
                      (item) => `
                        <span class="phonetic-item">
                          <span>${escapeHtml(item.label)} ${escapeHtml(item.text)}</span>
                          <button class="mini-audio-button" type="button" data-focus-word-voice="${item.type}" title="播放${item.label}发音">${item.label} ▶</button>
                        </span>
                      `,
                    )
                    .join('')
                : '<span>暂无音标</span>'
            }
          </div>
        </div>
      </div>
      <div class="mini-definition-list focus-section">
        ${definitionsHtml}
      </div>
      ${cognitiveImageHtml}
      ${collocationsHtml}
      ${etymologyHtml}
      <div class="focus-section">
        <div class="panel-heading compact-heading">
          <h3>例句</h3>
          <span class="mini-pill">${examples.length}</span>
        </div>
        <div class="stack ${examples.length ? '' : 'empty'}">
          ${
            examples.length
              ? examples
                  .map(
                    (item, index) => `
                      <div class="example-item">
                        <button class="icon-button" type="button" data-focus-sentence="${index}" title="播放例句">▶</button>
                        <p class="sentence">${escapeHtml(item.sentence || '')}</p>
                        <p class="translation">${escapeHtml(item.translation || '')}</p>
                      </div>
                    `,
                  )
                  .join('')
              : (isDetailLoading
                  ? '<div class="card-detail-loading-hint"><span class="card-loading-spinner"></span> 正在加载完整例句与深度释义...</div>'
                  : '暂无例句')
          }
        </div>
      </div>
      ${
        !etymologyHtml && memoryTips.length
          ? `
            <div class="focus-section">
              <div class="panel-heading compact-heading">
                <h3>记忆提示</h3>
              </div>
              <div class="stack">
                ${memoryTips.map((item) => `<div class="tip-item">${escapeHtml(readText(item, ['content', 'tip', 'text', 'meaning']) || stringifyValue(item))}</div>`).join('')}
              </div>
            </div>
          `
          : ''
      }
      <div class="focus-section">
        <div class="panel-heading compact-heading"><h3>相关单词</h3></div>
        <div class="relation-list ${relations.length ? '' : 'empty'}">
          ${relations.length ? relations.map((item) => renderRelationItem(item, 'focus-related')).join('') : (isDetailLoading ? '<span class="card-detail-loading-hint">关联词加载中...</span>' : '暂无关联词')}
        </div>
      </div>
      <div class="focus-section">
        <div class="panel-heading compact-heading">
          <h3>笔记</h3>
          <button class="secondary-button compact" type="button" data-edit-focus-note title="编辑笔记 (快捷键: ⌘E / Ctrl+E)">编辑笔记 (⌘E)</button>
        </div>
        <div class="note-view">${renderMarkdown(entry.note || '') || '<span class="empty">暂无笔记</span>'}</div>
      </div>
      <div class="focus-section">
        <div class="panel-heading compact-heading">
          <h3>标签</h3>
          <button class="icon-button compact-icon" type="button" data-toggle-tags title="隐藏标签" aria-label="隐藏标签" aria-pressed="true">${visibilityIcon(false)}</button>
        </div>
        <div class="chips focus-tags">
          ${tags.length ? tags.map((tag) => `<span class="chip tag-chip">${escapeHtml(tagLabel(tag))}</span>`).join('') : (isDetailLoading ? '<span class="card-detail-loading-hint">标签加载中...</span>' : '<span class="empty">暂无标签</span>')}
        </div>
      </div>
      <div class="focus-section focus-actions-bar">
        <button class="secondary-button compact" type="button" data-status-open="${escapeHtml(entry.id)}">熟练度标记</button>
      </div>
    `
    elements.wordbookFocus.querySelector('[data-status-open]')?.addEventListener('click', () => openEntryStatusModal(entry.id))
    elements.wordbookFocus.querySelector('[data-edit-focus-note]')?.addEventListener('click', () => {
      state.currentNoteEntry = entry
      editCurrentNote()
    })
    elements.wordbookFocus.querySelector('[data-toggle-tags]')?.addEventListener('click', () => {
      toggleFocusTags()
    })
    elements.wordbookFocus.querySelectorAll('[data-focus-sentence]').forEach((button) => {
      button.addEventListener('click', () => speakSentence(examples[Number(button.getAttribute('data-focus-sentence'))]?.sentence))
    })
    elements.wordbookFocus.querySelectorAll('[data-focus-word-voice]').forEach((button) => {
      button.addEventListener('click', () => speak(entry.term || entry.normalizedTerm))
    })
    bindStudyTermCards(elements.wordbookFocus, '[data-focus-related]', (term) => ctx.confirmStudyTerm(term, '单词'))
    bindStudyTermCards(elements.wordbookFocus, '[data-collocation-term]', (term) => ctx.confirmStudyTerm(term, '词组'))
    bindInlineAudio(elements.wordbookFocus, speak)
  }

  async function generateCurrentWordbookCard(target) {
    const entry = target && typeof target === 'object' ? target : (state.selectedEntry || null)
    const entryId = typeof target === 'string' ? target : entry?.id
    if (!entryId) return
    const button = elements.wordbookCardGenerateBtn
    if (button) {
      if (typeof setButtonLoading === 'function') setButtonLoading(button, true, '生成中...')
      else button.disabled = true
    }
    try {
      if (state.preview) {
        if (typeof toast === 'function') toast('设计预览：模拟生成词卡完成')
        return
      }
      await request(`/api/v1/learning/wordbook-entries/${encodeURIComponent(entryId)}/generate-card/async?forceRefresh=true`, {
        method: 'POST',
      })
      const current = state.wordbookEntries?.find((item) => sameId(item.id, entryId))
      const updated = { ...(current || entry || {}), cardStatus: 'generating', cardErrorMessage: null }
      const idx = state.wordbookEntries?.findIndex((item) => sameId(item.id, entryId)) ?? -1
      if (idx >= 0 && Array.isArray(state.wordbookEntries)) {
        state.wordbookEntries[idx] = updated
      }
      if (sameId(state.selectedEntry?.id, entryId)) {
        state.selectedEntry = updated
        renderWordbookFocus(updated)
      }
      if (typeof toast === 'function') toast(`单词「${updated.term || updated.normalizedTerm}」词卡任务已提交，可在任务中心查看进度`)
    } catch (err) {
      if (typeof toast === 'function') toast(`生成词卡失败：${err.message}`)
    } finally {
      if (button) {
        if (typeof setButtonLoading === 'function') setButtonLoading(button, false)
        else button.disabled = false
      }
      if (sameId(state.selectedEntry?.id, entryId)) {
        updateOuterCardControls(state.selectedEntry)
      }
    }
  }

  function renderCollocationMiniItem(item) {
    return renderCollocationMini(item)
  }

  function toggleFocusTags() {
    const tags = elements.wordbookFocus.querySelector('.focus-tags')
    const button = elements.wordbookFocus.querySelector('[data-toggle-tags]')
    if (!tags || !button) return
    const hidden = tags.classList.toggle('hidden')
    button.innerHTML = visibilityIcon(hidden)
    button.title = hidden ? '显示标签' : '隐藏标签'
    button.setAttribute('aria-label', hidden ? '显示标签' : '隐藏标签')
    button.setAttribute('aria-pressed', String(!hidden))
  }

  function visibilityIcon(hidden) {
    if (hidden) {
      return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3l18 18"/><path d="M10.6 10.6a2 2 0 0 0 2.8 2.8"/><path d="M9.9 4.3A9.8 9.8 0 0 1 12 4c5.2 0 8.8 4.1 10 8a12.1 12.1 0 0 1-2.3 4.2"/><path d="M6.1 6.1A12.3 12.3 0 0 0 2 12c1.2 3.9 4.8 8 10 8a9.8 9.8 0 0 0 4.1-.9"/></svg>'
    }
    return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3.6-8 10-8 10 8 10 8-3.6 8-10 8S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/></svg>'
  }

  return {
    renderWordbookFocus,
    generateCurrentWordbookCard,
    updateOuterCardControls,
    renderCollocationMiniItem,
    toggleFocusTags,
    visibilityIcon,
  }
}
