import { sameId } from '/src/shared/ids.js'
import { escapeHtml } from '/src/shared/text.js'
import { statusLabel } from '/src/shared/vocabulary.js'
import { syncCurrentWordbookId } from '/src/shared/wordbook.js'
import { createWordbookDetailFeature } from '/src/features/profile/wordbook-detail.js'

export function createWordbookProfileFeature(ctx) {
  const {
    state,
    elements,
    request,
    setLoading,
    toast,
    logEvent,
    confirmDelete,
    loadDueReviews: loadDueReviewsFromCtx,
    openEntryTransferModal,
    renderMarkdown,
    readText,
    stringifyValue,
    tagLabel,
    saveEntry,
    renderProfileMetrics,
    loadActivity,
    renderActivityHeatmap,
  } = ctx
  const detailFeature = createWordbookDetailFeature({
    ...ctx,
    openEntryStatusModal,
  })

  function renderWordbookFocus(entry) {
    return detailFeature.renderWordbookFocus(entry)
  }

  function loadWordbooks() {
    if (state.preview) {
      renderWordbooks()
      renderWordbookEntries()
      renderProfileMetrics()
      return Promise.resolve()
    }
    if (!state.token) {
      renderWordbooks()
      return Promise.resolve()
    }
    return request('/api/v1/learning/wordbooks')
      .then((wordbooks) => {
        state.wordbooks = Array.isArray(wordbooks) ? wordbooks : []
        renderWordbooks()
        return loadWordbookEntries()
      })
      .catch((error) => {
        logEvent('error', '词书加载失败', error.message)
        toast(`词书加载失败：${error.message}`)
      })
  }

  function renderWordbooks() {
    elements.wordbookSelect.innerHTML = ''
    elements.reviewWordbookSelect.innerHTML = ''
    if (!state.wordbooks.length) {
      elements.wordbookSelect.innerHTML = '<option value="">暂无词书</option>'
      elements.reviewWordbookSelect.innerHTML = '<option value="">暂无词书</option>'
      elements.wordbookCards.className = 'wordbook-cards empty'
      elements.wordbookCards.textContent = state.token ? '暂无词书' : '登录后查看词书'
      renderProfileMetrics()
      return
    }

    const hasSelected = state.wordbooks.some((item) => sameId(item.id, state.currentWordbookId))
    const fallback = state.wordbooks.find((item) => item.isDefault) || state.wordbooks[0]
    syncCurrentWordbookId(state, elements, hasSelected ? state.currentWordbookId : fallback.id)

    for (const wordbook of state.wordbooks) {
      const option = document.createElement('option')
      option.value = String(wordbook.id)
      option.textContent = `${wordbook.name} · ${wordbook.entryCount || 0}词 · ${wordbook.dueCount || 0}待复习`
      elements.wordbookSelect.appendChild(option)
      elements.reviewWordbookSelect.appendChild(option.cloneNode(true))
    }
    elements.wordbookSelect.value = String(state.currentWordbookId)
    elements.reviewWordbookSelect.value = String(state.currentWordbookId)

    elements.wordbookCards.className = 'wordbook-cards'
    elements.wordbookCards.innerHTML = state.wordbooks
      .map(
        (item) => `
          <div class="wordbook-card ${sameId(item.id, state.currentWordbookId) ? 'active' : ''}">
            <button class="wordbook-main" type="button" data-wordbook-id="${escapeHtml(item.id)}">
              <strong>${escapeHtml(item.name)}</strong>
              <span>${escapeHtml(item.description || (item.isDefault ? '默认词书' : '自定义词书'))}</span>
              <small>${item.isDefault ? '默认 · ' : ''}${item.entryCount || 0} 个单词 · ${item.dueCount || 0} 个待复习</small>
            </button>
            <div class="row-actions">
              <button class="icon-action-button" type="button" data-wordbook-edit="${escapeHtml(item.id)}" title="编辑词书" aria-label="编辑词书">✎</button>
              <button class="danger-icon-button" type="button" data-wordbook-delete="${escapeHtml(item.id)}" title="删除词书">×</button>
            </div>
          </div>
        `,
      )
      .join('')
    elements.wordbookCards.querySelectorAll('[data-wordbook-id]').forEach((button) => {
      button.addEventListener('click', () => changeWordbook(button.getAttribute('data-wordbook-id')))
    })
    elements.wordbookCards.querySelectorAll('[data-wordbook-edit]').forEach((button) => {
      button.addEventListener('click', () => openWordbookModal(button.getAttribute('data-wordbook-edit')))
    })
    elements.wordbookCards.querySelectorAll('[data-wordbook-delete]').forEach((button) => {
      button.addEventListener('click', () => deleteWordbook(button.getAttribute('data-wordbook-delete')))
    })
    renderProfileMetrics()
  }

  async function changeWordbook(wordbookId) {
    syncCurrentWordbookId(state, elements, wordbookId)
    renderWordbooks()
      await Promise.allSettled([loadWordbookEntries(), loadDueReviewsFromCtx()])
    logEvent('wordbook', '切换词书', currentWordbookName())
  }

  function openWordbookModal(id = null) {
    if (id) {
      fillWordbookForm(id)
      elements.wordbookModalTitle.textContent = '编辑词书'
    } else {
      resetWordbookForm({ keepModalOpen: true })
      elements.wordbookModalTitle.textContent = '新增词书'
    }
    elements.wordbookModal.classList.remove('hidden')
  }

  function closeWordbookModal() {
    elements.wordbookModal?.classList.add('hidden')
  }

  async function createWordbook() {
    const name = elements.newWordbookInput.value.trim()
    if (!name) {
      toast('请输入新词书名称')
      return
    }
    if (!state.token) {
      toast('请先登录')
      return
    }
    setLoading(true)
    try {
      const payload = {
        name,
        description: elements.wordbookDescriptionInput.value.trim(),
        isDefault: elements.wordbookDefaultInput.checked,
      }
      if (state.preview) {
        const id = state.currentWordbookEditId || String(Date.now())
        if (payload.isDefault) state.wordbooks.forEach((item) => (item.isDefault = false))
        const index = state.wordbooks.findIndex((item) => sameId(item.id, id))
        const existing = index >= 0 ? state.wordbooks[index] : {}
        const next = { ...existing, id, ...payload, entryCount: existing.entryCount ?? 0, dueCount: existing.dueCount ?? 0 }
        if (index >= 0) state.wordbooks.splice(index, 1, { ...state.wordbooks[index], ...next })
        else state.wordbooks.push(next)
        syncCurrentWordbookId(state, elements, id)
        resetWordbookForm()
        closeWordbookModal()
        renderWordbooks()
        toast('设计预览：词表已保存')
        return
      }
      const path = state.currentWordbookEditId ? `/api/v1/learning/wordbooks/${state.currentWordbookEditId}` : '/api/v1/learning/wordbooks'
      const method = state.currentWordbookEditId ? 'PUT' : 'POST'
      const wordbook = await request(path, { method, body: JSON.stringify(payload) })
      resetWordbookForm()
      closeWordbookModal()
      syncCurrentWordbookId(state, elements, wordbook.id)
      await loadWordbooks()
      logEvent('wordbook', method === 'PUT' ? '更新词表' : '创建词表', name)
      toast('词表已保存')
    } catch (error) {
      logEvent('error', '保存词表失败', error.message)
      toast(`保存词表失败：${error.message}`)
    } finally {
      setLoading(false)
    }
  }

  function fillWordbookForm(id) {
    const wordbook = state.wordbooks.find((item) => sameId(item.id, id))
    if (!wordbook) return
    state.currentWordbookEditId = wordbook.id
    elements.newWordbookInput.value = wordbook.name || ''
    elements.wordbookDescriptionInput.value = wordbook.description || ''
    elements.wordbookDefaultInput.checked = Boolean(wordbook.isDefault)
  }

  function resetWordbookForm(options = {}) {
    state.currentWordbookEditId = null
    elements.newWordbookInput.value = ''
    elements.wordbookDescriptionInput.value = ''
    elements.wordbookDefaultInput.checked = false
    if (!options.keepModalOpen) {
      closeWordbookModal()
    }
  }

  async function deleteWordbook(id) {
    const wordbook = state.wordbooks.find((item) => sameId(item.id, id))
    if (!wordbook) return
    const confirmed = await confirmDelete({
      title: '删除词书',
      message: `确认删除词书「${wordbook.name}」？词书中的单词也会从该词书移除。`,
    })
    if (!confirmed) return
    if (state.preview) {
      state.wordbooks = state.wordbooks.filter((item) => !sameId(item.id, id))
      if (!state.wordbooks.length) {
        state.wordbooks = [{ id: 1, name: '默认词书', description: '日常学习沉淀', isDefault: true, entryCount: 0, dueCount: 0 }]
      }
      const fallback = state.wordbooks.find((item) => item.isDefault) || state.wordbooks[0]
      fallback.isDefault = true
      syncCurrentWordbookId(state, elements, fallback.id)
      renderWordbooks()
      renderWordbookEntries()
      renderActivityHeatmap()
      toast('设计预览：词书已删除')
      return
    }
    try {
      await request(`/api/v1/learning/wordbooks/${encodeURIComponent(id)}`, { method: 'DELETE' })
      if (sameId(state.currentWordbookId, id)) {
        syncCurrentWordbookId(state, elements, null)
      }
      await Promise.allSettled([loadWordbooks(), loadDueReviewsFromCtx(), loadActivity()])
      toast('词书已删除')
    } catch (error) {
      logEvent('error', '删除词书失败', error.message)
      toast(`删除词书失败：${error.message}`)
    }
  }

  function loadWordbookEntries() {
    if (state.preview) {
      renderWordbookEntries()
      renderProfileMetrics()
      return Promise.resolve()
    }
    if (!state.token || !state.currentWordbookId) {
      state.wordbookEntries = []
      renderWordbookEntries()
      return Promise.resolve()
    }
    const status = elements.wordStatusFilter?.value || ''
    const query = status ? `?status=${encodeURIComponent(status)}` : ''
    return request(`/api/v1/learning/wordbooks/${encodeURIComponent(state.currentWordbookId)}/entries${query}`)
      .then((entries) => {
        state.wordbookEntries = Array.isArray(entries) ? entries : []
        renderWordbookEntries()
        renderProfileMetrics()
      })
      .catch((error) => {
        logEvent('error', '单词本加载失败', error.message)
      })
  }

  function openEntryStatusModal(entryId) {
    const entry = state.wordbookEntries.find((item) => sameId(item.id, entryId)) || state.reviewEntries.find((item) => sameId(item.id, entryId))
    if (!entry) return
    state.currentStatusEntryId = entry.id
    elements.entryStatusTerm.textContent = entry.term || entry.normalizedTerm || '当前单词'
    elements.entryStatusModal.classList.remove('hidden')
  }

  function closeEntryStatusModal() {
    elements.entryStatusModal?.classList.add('hidden')
    state.currentStatusEntryId = null
  }

  async function chooseEntryStatus(status) {
    const entryId = state.currentStatusEntryId
    if (!entryId) return
    await updateEntryStatus(entryId, status)
    closeEntryStatusModal()
  }

  async function updateEntryStatus(entryId, status) {
    await saveEntry(entryId, { status })
  }

  async function deleteWordbookEntry(entryId) {
    const entry = state.wordbookEntries.find((item) => sameId(item.id, entryId)) || state.reviewEntries.find((item) => sameId(item.id, entryId))
    const term = entry?.term || entry?.normalizedTerm || '当前单词'
    const confirmed = await confirmDelete({
      title: '删除单词',
      message: `确认从词书中删除「${term}」？删除后这个单词的笔记和复习计划会从当前词书移除。`,
    })
    if (!confirmed) return
    if (state.preview) {
      state.wordbookEntries = state.wordbookEntries.filter((entry) => !sameId(entry.id, entryId))
      state.reviewEntries = state.reviewEntries.filter((entry) => !sameId(entry.id, entryId))
      state.selectedEntry = null
      renderWordbookEntries()
      ctx.renderReviewQueue(state.reviewEntries)
      toast('设计预览：已从词表删除')
      return
    }
    try {
      await request(`/api/v1/learning/wordbook-entries/${encodeURIComponent(entryId)}`, { method: 'DELETE' })
      await Promise.allSettled([loadWordbooks(), loadWordbookEntries(), loadDueReviewsFromCtx()])
      await loadActivity()
      toast('已从词表删除')
    } catch (error) {
      logEvent('error', '删除词条失败', error.message)
      toast(`删除词条失败：${error.message}`)
    }
  }

  function renderWordbookEntries() {
    const filter = elements.wordStatusFilter?.value || ''
    const prefix = String(state.wordPrefixFilter || elements.wordPrefixInput?.value || '').trim().toLowerCase()
    const statusFiltered = filter && state.preview ? state.wordbookEntries.filter((entry) => (entry.status || 'vague') === filter) : state.wordbookEntries
    const entries = prefix ? statusFiltered.filter((entry) => entryMatchesPrefix(entry, prefix)) : statusFiltered
    if (!entries.length) {
      elements.wordbookEntryList.className = 'entry-list empty'
      elements.wordbookEntryList.textContent = prefix ? '没有匹配前缀的单词' : state.token ? '当前词书还没有单词' : '登录后查看单词本'
      state.selectedEntry = null
      renderWordbookFocus(null)
      ctx.renderNotes(null)
      return
    }
    const selectedEntry = state.selectedEntry && entries.some((entry) => sameId(entry.id, state.selectedEntry.id)) ? state.selectedEntry : entries[0]
    state.selectedEntry = selectedEntry
    elements.wordbookEntryList.className = 'entry-list'
    elements.wordbookEntryList.innerHTML = entries
      .map(
        (entry) => `
          <div class="entry-row ${sameId(selectedEntry.id, entry.id) ? 'active' : ''}">
            <button type="button" data-entry-id="${escapeHtml(entry.id)}">
              <span>${escapeHtml(entry.term || entry.normalizedTerm)}</span>
              <small>${escapeHtml(statusLabel(entry.status))} · 掌握 ${entry.masteryScore ?? 0}</small>
            </button>
            <div class="row-actions">
              <button class="icon-action-button" type="button" data-entry-transfer="${escapeHtml(entry.id)}" title="复制或移动" aria-label="复制或移动">＋</button>
              <button class="danger-icon-button" type="button" data-entry-delete="${escapeHtml(entry.id)}" title="删除单词">×</button>
            </div>
          </div>
        `,
      )
      .join('')
    elements.wordbookEntryList.querySelectorAll('[data-entry-id]').forEach((button) => {
      button.addEventListener('click', () => {
        const entry = state.wordbookEntries.find((item) => sameId(item.id, button.getAttribute('data-entry-id')))
        selectWordbookEntry(entry)
      })
    })
    elements.wordbookEntryList.querySelectorAll('[data-entry-delete]').forEach((button) => {
      button.addEventListener('click', () => deleteWordbookEntry(button.getAttribute('data-entry-delete')))
    })
    elements.wordbookEntryList.querySelectorAll('[data-entry-transfer]').forEach((button) => {
      button.addEventListener('click', () => openEntryTransferModal(button.getAttribute('data-entry-transfer')))
    })
    renderWordbookFocus(selectedEntry)
    ctx.renderNotes(selectedEntry)
  }

  function entryMatchesPrefix(entry, prefix) {
    const values = [entry.term, entry.normalizedTerm, entry.parsed?.term]
    return values.some((value) => String(value || '').trim().toLowerCase().startsWith(prefix))
  }

  function selectWordbookEntry(entry, options = {}) {
    if (!entry) {
      renderWordbookFocus(null)
      return
    }
    state.selectedEntry = entry
    renderWordbookFocus(entry)
    ctx.renderNotes(entry)
    if (!options.silent) renderWordbookEntries()
  }

  function currentWordbookName(wordbookId = state.currentWordbookId) {
    return state.wordbooks.find((item) => sameId(item.id, wordbookId))?.name || '所选词书'
  }

  return {
    loadWordbooks,
    renderWordbooks,
    changeWordbook,
    openWordbookModal,
    closeWordbookModal,
    createWordbook,
    fillWordbookForm,
    resetWordbookForm,
    deleteWordbook,
    loadWordbookEntries,
    renderWordbookEntries,
    selectWordbookEntry,
    ...detailFeature,
    openEntryStatusModal,
    closeEntryStatusModal,
    chooseEntryStatus,
    updateEntryStatus,
    deleteWordbookEntry,
    currentWordbookName,
  }
}
