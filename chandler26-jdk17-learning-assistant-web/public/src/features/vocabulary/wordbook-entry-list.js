import { showModal } from '/src/shared/modal.js'
import { escapeHtml, formatDateTime } from '/src/shared/text.js'
import { cardStatusLabel, statusLabel } from '/src/shared/vocabulary.js'

/** 单词本词条列表、分页和行级交互。完整词卡由详情模块按需加载。 */
export function createWordbookEntryListFeature({
  state,
  elements,
  sameId,
  normalizeDefinitions,
  entryMatchesKeyword,
  renderWordbookFocus,
  renderNotes,
  openEntryStatusModal,
  generateEntryCardWithConfirm,
  deleteWordbookEntry,
  openEntryTransferModal,
  loadWordbookEntryDetail,
  selectWordbookEntry,
}) {
  function render(entries = state.wordbookEntries) {
    let visibleEntries = entries
    if (state.preview) {
      const filter = elements.wordStatusFilter?.value || ''
      const keyword = String(elements.wordPrefixInput?.value || '').trim().toLowerCase()
      if (filter) visibleEntries = visibleEntries.filter((entry) => (entry.status || 'vague') === filter)
      if (keyword) visibleEntries = visibleEntries.filter((entry) => entryMatchesKeyword(entry, keyword))
    }

    const totalCount = Number(state.wordbookTotal ?? visibleEntries.length)
    if (elements.wordbookCountSummary) {
      elements.wordbookCountSummary.textContent = `共 ${totalCount} 个单词`
      const maxPage = Math.max(1, Math.ceil(totalCount / (state.wordbookPageSize || 30)))
      const currentPage = Number(state.wordbookPage || 1)
      elements.wordbookPageInfo.textContent = `第 ${currentPage} / ${maxPage} 页`
      if (elements.wordbookPrevBtn) elements.wordbookPrevBtn.disabled = currentPage <= 1
      if (elements.wordbookNextBtn) elements.wordbookNextBtn.disabled = currentPage >= maxPage
    }

    if (!visibleEntries.length) {
      const keyword = String(elements.wordPrefixInput?.value || '').trim()
      elements.wordbookEntryList.innerHTML = `
        <tr><td colspan="8" class="empty" style="text-align: center; padding: 48px 16px;">
          ${keyword ? '没有匹配搜索条件的单词' : state.token ? '当前单词本还没有单词' : '登录后查看单词本'}
        </td></tr>`
      state.selectedEntry = null
      renderWordbookFocus(null)
      renderNotes(null)
      return
    }

    const selectedEntry = state.selectedEntry && visibleEntries.some((entry) => sameId(entry.id, state.selectedEntry.id))
      ? state.selectedEntry : visibleEntries[0]
    state.selectedEntry = selectedEntry
    elements.wordbookEntryList.innerHTML = visibleEntries.map((entry) => renderRow(entry, selectedEntry)).join('')
    elements.wordbookEntryList.onclick = (event) => handleClick(event)
    renderWordbookFocus(selectedEntry)
    renderNotes(selectedEntry)
  }

  function renderRow(entry, selectedEntry) {
    const parsed = entry.parsed || {}
    const phonetic = parsed.phonetic?.uk
      ? `UK /${parsed.phonetic.uk}/`
      : parsed.phonetic?.us ? `US /${parsed.phonetic.us}/` : entry.phonetic ? `/${entry.phonetic}/` : '-'
    const definitions = typeof normalizeDefinitions === 'function' ? normalizeDefinitions(parsed) : []
    const meaningSummary = definitions.length
      ? definitions.map((definition) => {
        const pos = definition.pos && definition.pos !== 'meaning' && definition.pos !== 'pos'
          ? (definition.pos.endsWith('.') ? definition.pos : `${definition.pos}.`) : ''
        const text = (definition.cn || definition.en || '').trim()
        return pos && !text.startsWith(pos) ? `${pos} ${text}` : text
      }).join('； ')
      : entry.meaningText || entry.definition || '-'
    const cardCode = entry.cardStatus || 'missing'
    const isClickable = ['missing', 'not_required', 'failed'].includes(cardCode)
    const cardTitle = cardCode === 'missing' ? '未生成 AI 完整词卡，点击二次确认触发生成'
      : cardCode === 'failed' ? 'AI 词卡生成失败，点击二次确认重新生成'
        : cardCode === 'not_required' ? '基础静态词条，点击二次确认生成 AI 深度词卡'
          : cardCode === 'ready' ? 'AI 完整词卡已就绪' : '词卡生成中...'
    const stateCode = entry.status || 'vague'
    const nextReview = entry.nextReviewTime ? formatDateTime(entry.nextReviewTime) : '-'
    return `
      <tr class="wordbook-row ${sameId(selectedEntry.id, entry.id) ? 'active' : ''}" data-entry-id="${escapeHtml(entry.id)}">
        <td class="cell-term"><button class="word-link-btn" type="button" data-word-card="${escapeHtml(entry.id)}" title="点击查看词卡"><strong class="term-text">${escapeHtml(entry.term || entry.normalizedTerm)}</strong></button></td>
        <td class="cell-phonetic"><span class="phonetic-text">${escapeHtml(phonetic)}</span></td>
        <td class="cell-meaning"><span class="meaning-text expandable" data-toggle-expand title="点击展开/收起完整释义">${escapeHtml(meaningSummary)}</span></td>
        <td class="cell-card-status">${isClickable
          ? `<button class="card-status-pill card-status-btn card-status-${escapeHtml(cardCode)}" type="button" data-generate-entry-card="${escapeHtml(entry.id)}" title="${escapeHtml(cardTitle)}">${escapeHtml(cardStatusLabel(cardCode))}<span class="card-action-hint">⚡</span></button>`
          : `<span class="card-status-pill card-status-${escapeHtml(cardCode)}" title="${escapeHtml(cardTitle)}">${escapeHtml(cardStatusLabel(cardCode))}</span>`}</td>
        <td class="cell-status"><button class="status-pill status-pill-btn status-${escapeHtml(stateCode)}" type="button" data-change-status="${escapeHtml(entry.id)}" title="点击修改掌握状态">${escapeHtml(statusLabel(stateCode))} · 阶段 ${entry.reviewStage ?? 0}<span class="status-edit-hint">✎</span></button></td>
        <td class="cell-mastery"><span class="mastery-score">${entry.masteryScore ?? 0}</span></td>
        <td class="cell-next-review"><span class="next-review-text">${escapeHtml(nextReview)}</span></td>
        <td class="cell-actions" style="text-align: center;"><div class="row-actions" style="justify-content: center; gap: 6px;">
          <button class="icon-action-button" type="button" data-entry-status="${escapeHtml(entry.id)}" title="修改掌握状态" aria-label="修改掌握状态">✎</button>
          <button class="icon-action-button" type="button" data-entry-transfer="${escapeHtml(entry.id)}" title="复制或移动到其他单词本" aria-label="复制或移动到其他单词本">＋</button>
          <button class="danger-icon-button" type="button" data-entry-delete="${escapeHtml(entry.id)}" title="从单词本删除" aria-label="删除">×</button>
        </div></td>
      </tr>`
  }

  function handleClick(event) {
    const statusButton = event.target.closest('[data-change-status], [data-entry-status]')
    if (statusButton) {
      event.stopPropagation()
      openEntryStatusModal(statusButton.getAttribute('data-change-status') || statusButton.getAttribute('data-entry-status'))
      return
    }
    const cardButton = event.target.closest('[data-generate-entry-card]')
    if (cardButton) {
      event.stopPropagation()
      const entry = state.wordbookEntries.find((item) => sameId(item.id, cardButton.getAttribute('data-generate-entry-card')))
      if (entry) generateEntryCardWithConfirm(entry, cardButton)
      return
    }
    const deleteButton = event.target.closest('[data-entry-delete]')
    if (deleteButton) {
      event.stopPropagation()
      deleteWordbookEntry(deleteButton.getAttribute('data-entry-delete'))
      return
    }
    const transferButton = event.target.closest('[data-entry-transfer]')
    if (transferButton) {
      event.stopPropagation()
      openEntryTransferModal(transferButton.getAttribute('data-entry-transfer'))
      return
    }
    const expand = event.target.closest('[data-toggle-expand]')
    if (expand) {
      event.stopPropagation()
      expand.classList.toggle('expanded')
      return
    }
    const wordTarget = event.target.closest('[data-word-card], .cell-term')
    if (!wordTarget) return
    event.stopPropagation()
    const row = wordTarget.closest('tr[data-entry-id]')
    const entryId = row?.getAttribute('data-entry-id') || wordTarget.getAttribute('data-word-card')
    const entry = state.wordbookEntries.find((item) => sameId(item.id, entryId))
    if (!entry) return
    selectWordbookEntry(entry, { silent: true })
    const modal = elements.wordbookCardModal || document.getElementById('wordbookCardModal')
    if (modal) showModal(modal)
    if (!state.preview && !(entry.parsed && typeof entry.parsed === 'object')) loadWordbookEntryDetail(entry)
  }

  return { render }
}
