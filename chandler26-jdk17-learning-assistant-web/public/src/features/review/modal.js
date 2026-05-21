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
    elements.reviewCompleteModal.classList.remove('hidden')
  }

  function closeReviewModal(options = {}) {
    if (!elements.reviewCompleteModal) return
    elements.reviewCompleteModal.classList.add('hidden')
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
    elements.forgottenDetailModal.classList.remove('hidden')
  }

  function closeForgottenDetailModal() {
    if (!elements.forgottenDetailModal) return
    elements.forgottenDetailModal.classList.add('hidden')
  }

  return {
    renderReviewCompleteModal,
    closeReviewModal,
    openForgottenDetailModal,
    closeForgottenDetailModal,
  }
}
