import { escapeHtml, formatDateTime } from '/src/shared/text.js'
import { normalizeArray, renderCollocationMini, renderRelationItem, semanticRelations, statusLabel } from '/src/shared/vocabulary.js'

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
    openEntryInReview,
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

  function renderWordbookFocus(entry) {
    if (!entry) {
      elements.wordbookFocus.className = 'empty'
      elements.wordbookFocus.textContent = '选择单词后查看详情和笔记'
      return
    }
    const parsed = entry.parsed || {}
    const definitions = normalizeDefinitions(parsed)
    const examples = normalizeExamples(parsed).slice(0, 3)
    const collocations = normalizeArray(parsed?.collocations || parsed?.phrases || parsed?.common_phrases).slice(0, 6)
    const memoryTips = normalizeArray(parsed?.memory_tips || parsed?.memoryTips || parsed?.tips || parsed?.memory).slice(0, 3)
    const tags = Array.isArray(entry.tags) ? entry.tags.slice(0, 6) : []
    const relations = semanticRelations(entry.relations).slice(0, 6)
    const phoneticItems = [
      parsed?.phonetic?.uk && { type: 'uk', label: 'UK', text: parsed.phonetic.uk },
      parsed?.phonetic?.us && { type: 'us', label: 'US', text: parsed.phonetic.us },
    ].filter(Boolean)
    const isCardReady = entry.cardStatus === 'ready' || (definitions.length > 0 && examples.length > 0)
    const term = entry.term || entry.normalizedTerm || ''
    if (elements.wordbookCardModalTitle) {
      elements.wordbookCardModalTitle.textContent = `单词卡片 · ${term}`
    }
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
        <div class="inline-actions">
          <button class="primary-button compact-primary" type="button" data-generate-card="${escapeHtml(entry.id)}" title="${isCardReady ? '重新通过 AI 生成更丰富的例句、记忆法与关联词' : '通过 AI 生成例句、记忆法、搭配与关联词'}">${isCardReady ? '重新生成词卡' : 'AI 生成词卡'}</button>
          <button class="secondary-button compact" type="button" data-open-review="${escapeHtml(entry.id)}">去复习</button>
        </div>
      </div>
      <div class="mini-definition-list focus-section">
        ${
          definitions.length
            ? definitions.map((item) => `<div><span>${escapeHtml(item.pos || 'meaning')}</span><p>${escapeHtml(item.cn || item.en || '')}</p></div>`).join('')
            : '<div class="empty">暂无释义</div>'
        }
      </div>
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
              : '暂无例句'
          }
        </div>
      </div>
      <div class="focus-section">
        <div class="panel-heading compact-heading">
          <h3>记忆提示</h3>
        </div>
        <div class="stack ${memoryTips.length ? '' : 'empty'}">
          ${
            memoryTips.length
              ? memoryTips.map((item) => `<div class="tip-item">${escapeHtml(readText(item, ['content', 'tip', 'text', 'meaning']) || stringifyValue(item))}</div>`).join('')
              : '暂无记忆提示'
          }
        </div>
      </div>
      <div class="focus-subgrid">
        <div>
          <div class="panel-heading compact-heading"><h3>搭配</h3></div>
          <div class="collocation-list ${collocations.length ? '' : 'empty'}">
            ${collocations.length ? collocations.map(renderCollocationMini).join('') : '暂无搭配'}
          </div>
        </div>
        <div>
          <div class="panel-heading compact-heading"><h3>相关单词</h3></div>
          <div class="relation-list ${relations.length ? '' : 'empty'}">
            ${relations.length ? relations.map((item) => renderRelationItem(item, 'focus-related')).join('') : '暂无关联词'}
          </div>
        </div>
      </div>
      <div class="focus-section">
        <div class="panel-heading compact-heading">
          <h3>笔记</h3>
          <button class="secondary-button compact" type="button" data-edit-focus-note>编辑笔记</button>
        </div>
        <div class="note-view">${renderMarkdown(entry.note || '') || '<span class="empty">暂无笔记</span>'}</div>
      </div>
      <div class="focus-section">
        <div class="panel-heading compact-heading">
          <h3>标签</h3>
          <button class="icon-button compact-icon" type="button" data-toggle-tags title="隐藏标签" aria-label="隐藏标签" aria-pressed="true">${visibilityIcon(false)}</button>
        </div>
        <div class="chips focus-tags">
          ${tags.length ? tags.map((tag) => `<span class="chip tag-chip">${escapeHtml(tagLabel(tag))}</span>`).join('') : '<span class="empty">暂无标签</span>'}
        </div>
      </div>
      <div class="focus-section focus-actions-bar">
        <button class="secondary-button compact" type="button" data-status-open="${escapeHtml(entry.id)}">熟练度标记</button>
      </div>
    `
    elements.wordbookFocus.querySelector('[data-open-review]')?.addEventListener('click', () => openEntryInReview(entry))
    elements.wordbookFocus.querySelector('[data-status-open]')?.addEventListener('click', () => openEntryStatusModal(entry.id))
    elements.wordbookFocus.querySelector('[data-generate-card]')?.addEventListener('click', async (e) => {
      const button = e.currentTarget
      const entryId = button.dataset.generateCard
      if (!entryId) return
      if (typeof setButtonLoading === 'function') setButtonLoading(button, true, '生成中...')
      else button.disabled = true
      try {
        if (state.preview) {
          if (typeof toast === 'function') toast('设计预览：模拟生成词卡完成')
          return
        }
        const updated = await request(`/api/v1/learning/wordbook-entries/${encodeURIComponent(entryId)}/generate-card?forceRefresh=true`, {
          method: 'POST',
        })
        if (updated) {
          const idx = state.wordbookEntries.findIndex((item) => sameId(item.id, entryId))
          if (idx >= 0) {
            state.wordbookEntries[idx] = updated
          }
          state.selectedEntry = updated
          renderWordbookFocus(updated)
          if (typeof toast === 'function') toast(`单词「${updated.term || updated.normalizedTerm}」AI 词卡已生成`)
        }
      } catch (err) {
        if (typeof toast === 'function') toast(`生成词卡失败：${err.message}`)
      } finally {
        if (typeof setButtonLoading === 'function') setButtonLoading(button, false)
        else button.disabled = false
      }
    })
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
    renderCollocationMiniItem,
    toggleFocusTags,
    visibilityIcon,
  }
}
