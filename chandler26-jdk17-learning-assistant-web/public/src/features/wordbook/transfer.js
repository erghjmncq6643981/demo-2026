export function createWordbookTransferFeature(ctx) {
  function renderEntryTransferList(entry) {
    const list = ctx.state.wordbooks.filter((wordbook) => !ctx.sameId(wordbook.id, entry.wordbookId))
    if (!list.length) {
      ctx.elements.entryTransferList.className = 'wordbook-picker-list empty'
      ctx.elements.entryTransferList.textContent = '暂无其他单词本'
      return
    }
    ctx.elements.entryTransferList.className = 'wordbook-picker-list'
    ctx.elements.entryTransferList.innerHTML = list
      .map(
        (wordbook) => `
        <div class="wordbook-picker-item transfer-item">
          <div>
          <strong>${ctx.escapeHtml(wordbook.name)}</strong>
          <span>${ctx.escapeHtml(wordbook.description || (wordbook.isDefault ? '默认单词本' : '自定义单词本'))}</span>
          <small>${wordbook.entryCount || 0} 个单词 · ${wordbook.dueCount || 0} 个待复习</small>
          </div>
          <div class="transfer-actions">
            <button class="secondary-button compact" type="button" data-transfer-copy="${ctx.escapeHtml(wordbook.id)}">复制</button>
            <button class="primary-button compact-primary" type="button" data-transfer-move="${ctx.escapeHtml(wordbook.id)}">移动</button>
          </div>
        </div>
      `,
      )
      .join('')
    ctx.elements.entryTransferList.querySelectorAll('[data-transfer-copy]').forEach((button) => {
      button.addEventListener('click', () => transferWordbookEntry(entry.id, button.getAttribute('data-transfer-copy'), true))
    })
    ctx.elements.entryTransferList.querySelectorAll('[data-transfer-move]').forEach((button) => {
      button.addEventListener('click', () => transferWordbookEntry(entry.id, button.getAttribute('data-transfer-move'), false))
    })
  }

  function openEntryTransferModal(entryId) {
    const entry = ctx.state.wordbookEntries.find((item) => ctx.sameId(item.id, entryId)) || ctx.state.reviewEntries.find((item) => ctx.sameId(item.id, entryId))
    if (!entry) return
    ctx.state.currentTransferEntryId = entry.id
    ctx.elements.entryTransferTerm.textContent = entry.term || entry.normalizedTerm || '当前单词'
    renderEntryTransferList(entry)
    ctx.elements.entryTransferModal.classList.remove('hidden')
  }

  function closeEntryTransferModal() {
    ctx.elements.entryTransferModal?.classList.add('hidden')
    ctx.state.currentTransferEntryId = null
  }

  async function transferWordbookEntry(entryId, targetWordbookId, copy = true) {
    ctx.setLoading(true)
    try {
      const targetWordbookIdValue = ctx.normalizeWordbookId(targetWordbookId)
      if (ctx.state.preview) {
        const entry = ctx.state.wordbookEntries.find((item) => ctx.sameId(item.id, entryId)) || ctx.state.reviewEntries.find((item) => ctx.sameId(item.id, entryId))
        if (!entry) return
        const targetWordbook = ctx.state.wordbooks.find((item) => ctx.sameId(item.id, targetWordbookIdValue))
        if (!targetWordbook) return
        if (copy) {
          const cloned = { ...entry, id: String(Date.now()), wordbookId: targetWordbook.id }
          ctx.state.wordbookEntries.unshift(cloned)
        } else {
          entry.wordbookId = targetWordbook.id
        }
        ctx.renderWordbookEntries()
        ctx.renderWordbooks()
        closeEntryTransferModal()
        ctx.toast(copy ? '设计预览：已复制到单词本' : '设计预览：已移动到单词本')
        return
      }
      await ctx.request(`/api/v1/learning/wordbook-entries/${encodeURIComponent(entryId)}/transfer`, {
        method: 'POST',
        body: JSON.stringify({ targetWordbookId: targetWordbookIdValue, copy }),
      })
      await Promise.allSettled([ctx.loadWordbooks(), ctx.loadWordbookEntries(), ctx.loadDueReviews(), ctx.loadActivity()])
      closeEntryTransferModal()
      ctx.toast(copy ? '已复制到其它单词本' : '已移动到其它单词本')
    } catch (error) {
      ctx.logEvent('error', copy ? '复制词条失败' : '移动词条失败', error.message)
      ctx.toast(`${copy ? '复制' : '移动'}词条失败：${error.message}`)
    } finally {
      ctx.setLoading(false)
    }
  }

  return {
    openEntryTransferModal,
    closeEntryTransferModal,
    renderEntryTransferList,
    transferWordbookEntry,
  }
}
