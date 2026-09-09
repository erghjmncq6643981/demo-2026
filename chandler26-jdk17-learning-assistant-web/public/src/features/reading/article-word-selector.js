import { escapeHtml } from '/src/shared/text.js'
import { normalizeDefinitions, statusLabel } from '/src/shared/vocabulary.js'
import { ARTICLE_WORD_LIMIT } from '/src/features/reading/article-model.js'

/** 语境精读词条选择器：筛选、推荐与选择状态只在本模块内管理。 */
export function createArticleWordSelector({ state, elements, toast, sameId, entryMatchesFilter, renderPreview }) {
  function filteredEntries() {
    const prefix = String(state.articlePrefixFilter || elements.articlePrefixInput?.value || '').trim().toLowerCase()
    const status = elements.articleStatusFilter?.value || ''
    const statusFiltered = status ? state.articleEntries.filter((entry) => (entry.status || 'vague') === status) : state.articleEntries
    return prefix ? statusFiltered.filter((entry) => entryMatchesFilter(entry, prefix)) : statusFiltered
  }

  function render() {
    if (!elements.articleWordGrid) return
    const entries = filteredEntries()
    elements.articleSelectedCount.textContent = `已选 ${state.selectedArticleEntryIds.length}`
    const maxPage = Math.max(1, Math.ceil((state.articleWordTotal || entries.length) / (state.articleWordPageSize || 50)))
    if (elements.articleWordPageInfo) elements.articleWordPageInfo.textContent = `第 ${state.articleWordPage || 1} / ${maxPage} 页 · 共 ${state.articleWordTotal || entries.length} 个`
    if (elements.articleWordPrevBtn) elements.articleWordPrevBtn.disabled = (state.articleWordPage || 1) <= 1
    if (elements.articleWordNextBtn) elements.articleWordNextBtn.disabled = (state.articleWordPage || 1) >= maxPage
    if (!entries.length) {
      elements.articleWordGrid.className = 'article-word-grid empty'
      elements.articleWordGrid.textContent = state.token ? '当前筛选下暂无单词' : '登录后查看单词本词汇'
      return
    }
    elements.articleWordGrid.className = 'article-word-grid'
    elements.articleWordGrid.innerHTML = entries.map(renderCard).join('')
    elements.articleWordGrid.querySelectorAll('[data-article-entry-id]').forEach((button) => {
      button.addEventListener('click', () => toggle(button.getAttribute('data-article-entry-id')))
    })
  }

  function renderCard(entry) {
    const selected = state.selectedArticleEntryIds.some((id) => sameId(id, entry.id))
    const definition = normalizeDefinitions(entry.parsed || {})[0] || {}
    return `<button class="article-word-card ${selected ? 'selected' : ''}" type="button" data-article-entry-id="${escapeHtml(entry.id)}" aria-pressed="${selected}">
      <span class="article-word-topline"><strong>${escapeHtml(entry.term || entry.normalizedTerm)}</strong><small>${escapeHtml(statusLabel(entry.status))}</small></span>
      <p>${escapeHtml(definition.pos || 'meaning')} · ${escapeHtml(definition.cn || definition.en || '暂无核心含义')}</p>
      <span class="selection-dot" aria-hidden="true"></span>
    </button>`
  }

  function toggle(entryId) {
    const exists = state.selectedArticleEntryIds.some((id) => sameId(id, entryId))
    if (exists) {
      state.selectedArticleEntryIds = state.selectedArticleEntryIds.filter((id) => !sameId(id, entryId))
    } else if (state.selectedArticleEntryIds.length >= ARTICLE_WORD_LIMIT) {
      toast(`一次最多选择 ${ARTICLE_WORD_LIMIT} 个单词`)
      return
    } else {
      state.selectedArticleEntryIds.push(entryId)
    }
    state.articleDraftRecord = null
    state.articlePreviewError = ''
    render()
    renderPreview(null)
  }

  function clear() {
    state.selectedArticleEntryIds = []
    state.articleDraftRecord = null
    state.articlePreviewError = ''
    render()
    renderPreview(null)
  }

  function recommend() {
    const priority = { forgotten: 0, vague: 1, familiar: 2 }
    const candidates = [...filteredEntries()]
      .sort((left, right) => {
        const statusDelta = (priority[left.status] ?? 1) - (priority[right.status] ?? 1)
        return statusDelta !== 0 ? statusDelta : Number(left.masteryScore || 0) - Number(right.masteryScore || 0)
      })
      .slice(0, Math.min(8, ARTICLE_WORD_LIMIT))
    state.selectedArticleEntryIds = candidates.map((entry) => entry.id)
    state.articleDraftRecord = null
    state.articlePreviewError = ''
    render()
    renderPreview(null)
    toast(candidates.length ? `已推荐 ${candidates.length} 个优先学习词` : '当前筛选下没有可推荐词汇')
  }

  return { render, filteredEntries, toggle, clear, recommend }
}
