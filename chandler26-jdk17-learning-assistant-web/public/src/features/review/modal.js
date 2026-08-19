import { hideModal, showModal } from '/src/shared/modal.js'

export function createReviewModalFeature(ctx) {
  const {
    state,
    elements,
    normalizeDefinitions,
    normalizeExamples,
    normalizeArray,
    escapeHtml,
    readText,
    stringifyValue,
    renderCollocationMini,
    speakSentence,
    speak,
    bindStudyTermCards,
    bindInlineAudio,
    statusLabel,
    renderMarkdown,
    saveEntry,
    toast,
    reviewTargetTerm,
    renderReviewFocus,
  } = ctx

  function renderReviewCompleteModal(entry) {
    const parsed = entry?.parsed || {}
    const examples = normalizeExamples(parsed).slice(0, 3)
    elements.modalWordTitle.textContent = reviewTargetTerm(entry)
    elements.modalExamples.className = examples.length ? 'modal-examples' : 'modal-examples empty'
    elements.modalExamples.innerHTML = examples.length
      ? examples
          .map(
            (item, index) => `
              <div class="modal-example-item">
                <button class="mini-audio-button" type="button" data-modal-sentence="${index}" title="播放例句">▶</button>
                <p class="sentence">${escapeHtml(item.sentence || '')}</p>
                <p class="translation">${escapeHtml(item.translation || '')}</p>
              </div>
            `,
          )
          .join('')
      : '暂无例句'
    elements.modalExamples.querySelectorAll('[data-modal-sentence]').forEach((button) => {
      button.addEventListener('click', () => speakSentence(examples[Number(button.getAttribute('data-modal-sentence'))]?.sentence))
    })
    showModal(elements.reviewCompleteModal)
  }

  function closeReviewModal(options = {}) {
    if (!elements.reviewCompleteModal) return
    hideModal(elements.reviewCompleteModal)
    state.pendingReviewEntryId = null
    if (!options.keepTyped) {
      state.reviewTyped = ''
      state.reviewWrongCount = 0
    }
    if (!options.skipRender && state.activeView === 'reviewView' && state.currentReviewEntry) {
      renderReviewFocus(state.currentReviewEntry)
    }
  }

  function openForgottenDetailModal(entry) {
    if (!entry) return
    const parsed = entry.parsed || {}
    const definitions = normalizeDefinitions(parsed).slice(0, 4)
    const examples = normalizeExamples(parsed).slice(0, 3)
    const memoryTips = normalizeArray(parsed?.memory_tips || parsed?.memoryTips || parsed?.tips || parsed?.memory).slice(0, 2)
    const collocations = normalizeArray(parsed?.collocations || parsed?.phrases || parsed?.common_phrases).slice(0, 4)
    elements.forgottenDetailTitle.textContent = entry.term || entry.normalizedTerm || '单词详情'
    elements.forgottenDetailContent.className = 'forgotten-detail-content'
    elements.forgottenDetailContent.innerHTML = `
      <div class="forgotten-word-head">
        <div>
          <p class="eyebrow">${escapeHtml(statusLabel(entry.status))}</p>
          <h4>${escapeHtml(entry.term || entry.normalizedTerm || '')}</h4>
          <p class="phonetic">${escapeHtml([parsed?.phonetic?.uk, parsed?.phonetic?.us].filter(Boolean).join('    ') || '暂无音标')}</p>
        </div>
        <button class="mini-audio-button" type="button" data-forgotten-word-audio>播放</button>
      </div>
      <div class="mini-definition-list">
        ${
          definitions.length
            ? definitions.map((item) => `<div><span>${escapeHtml(item.pos || 'meaning')}</span><p>${escapeHtml(item.cn || item.en || '')}</p><p class="meaning-en">${escapeHtml(item.en || item.extra || '')}</p></div>`).join('')
            : '<div class="empty">暂无释义</div>'
        }
      </div>
      <div class="focus-section">
        <div class="panel-heading compact-heading"><h3>例句</h3></div>
        <div class="stack ${examples.length ? '' : 'empty'}">
          ${
            examples.length
              ? examples
                  .map(
                    (item, index) => `
                      <div class="example-item">
                        <button class="icon-button" type="button" data-forgotten-sentence="${index}" title="播放例句">▶</button>
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
        <div class="panel-heading compact-heading"><h3>记忆提示</h3></div>
        <div class="stack ${memoryTips.length ? '' : 'empty'}">
          ${
            memoryTips.length
              ? memoryTips.map((item) => `<div class="tip-item">${escapeHtml(readText(item, ['content', 'tip', 'text', 'meaning']) || stringifyValue(item))}</div>`).join('')
              : '暂无记忆提示'
          }
        </div>
      </div>
      <div class="focus-section">
        <div class="panel-heading compact-heading"><h3>搭配</h3></div>
        <div class="collocation-list ${collocations.length ? '' : 'empty'}">
          ${collocations.length ? collocations.map(renderCollocationMini).join('') : '暂无搭配'}
        </div>
      </div>
    `
    elements.forgottenDetailContent.querySelector('[data-forgotten-word-audio]')?.addEventListener('click', () => speak(entry.term || entry.normalizedTerm, elements.voiceSelect.value))
    elements.forgottenDetailContent.querySelectorAll('[data-forgotten-sentence]').forEach((button) => {
      button.addEventListener('click', () => speakSentence(examples[Number(button.getAttribute('data-forgotten-sentence'))]?.sentence))
    })
    bindStudyTermCards(elements.forgottenDetailContent, '[data-collocation-term]', '词组')
    bindInlineAudio(elements.forgottenDetailContent)
    showModal(elements.forgottenDetailModal)
  }

  function closeForgottenDetailModal() {
    if (!elements.forgottenDetailModal) return
    hideModal(elements.forgottenDetailModal)
  }

  function openReviewNoteModal(entry) {
    if (!entry) return
    state.currentReviewNoteEntry = entry
    const term = entry.term || entry.normalizedTerm || '复习单词'
    if (elements.reviewNoteModalTitle) elements.reviewNoteModalTitle.textContent = `复习笔记 · ${term}`
    if (elements.reviewNoteInput) elements.reviewNoteInput.value = entry.note || ''
    if (elements.reviewNotePreview) {
      elements.reviewNotePreview.innerHTML = entry.note ? renderMarkdown(entry.note) : '<span class="empty">暂无笔记内容</span>'
      elements.reviewNotePreview.classList.add('hidden')
    }
    if (elements.reviewNoteInput) elements.reviewNoteInput.classList.remove('hidden')
    if (elements.reviewNotePreviewBtn) elements.reviewNotePreviewBtn.textContent = '预览 Markdown'
    if (elements.reviewNoteStatus) elements.reviewNoteStatus.textContent = entry.note ? '已同步' : '未记录'
    showModal(elements.reviewNoteModal)
  }

  function closeReviewNoteModal() {
    if (!elements.reviewNoteModal) return
    hideModal(elements.reviewNoteModal)
    state.currentReviewNoteEntry = null
  }

  function toggleReviewNotePreview() {
    if (!elements.reviewNotePreview || !elements.reviewNoteInput) return
    const isPreview = !elements.reviewNotePreview.classList.contains('hidden')
    if (isPreview) {
      elements.reviewNotePreview.classList.add('hidden')
      elements.reviewNoteInput.classList.remove('hidden')
      if (elements.reviewNotePreviewBtn) elements.reviewNotePreviewBtn.textContent = '预览 Markdown'
    } else {
      const content = elements.reviewNoteInput.value || ''
      elements.reviewNotePreview.innerHTML = content ? renderMarkdown(content) : '<span class="empty">暂无笔记内容</span>'
      elements.reviewNotePreview.classList.remove('hidden')
      elements.reviewNoteInput.classList.add('hidden')
      if (elements.reviewNotePreviewBtn) elements.reviewNotePreviewBtn.textContent = '编辑 Markdown'
    }
  }

  async function saveReviewNote() {
    const entry = state.currentReviewNoteEntry || state.currentReviewEntry
    if (!entry) return
    const note = elements.reviewNoteInput?.value || ''
    if (elements.reviewNoteStatus) elements.reviewNoteStatus.textContent = '保存中...'
    const updated = await saveEntry(entry.id, { note })
    if (updated) {
      if (elements.reviewNoteStatus) elements.reviewNoteStatus.textContent = '已保存'
      toast('复习笔记已保存')
      closeReviewNoteModal()
    } else {
      if (elements.reviewNoteStatus) elements.reviewNoteStatus.textContent = '保存失败'
    }
  }

  return {
    renderReviewCompleteModal,
    closeReviewModal,
    openForgottenDetailModal,
    closeForgottenDetailModal,
    openReviewNoteModal,
    closeReviewNoteModal,
    toggleReviewNotePreview,
    saveReviewNote,
  }
}
