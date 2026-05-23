import { sameId } from '/src/shared/ids.js'
import { hideModal, showModal } from '/src/shared/modal.js'
import { normalizeWordbookId, resolveSelectedWordbookId, syncCurrentWordbookId } from '/src/shared/wordbook.js'
import { escapeHtml } from '/src/shared/text.js'

export function createStudyWordbookFeature(ctx) {
  const {
    state,
    elements,
    request,
    setLoading,
    toast,
    logEvent,
    createPreviewActivity,
    renderWordbookEntries,
    renderWordbooks,
    loadWordbooks,
    loadWordbookEntries,
    loadDueReviews,
    loadActivity,
    currentWordbookName,
    renderNotes,
  } = ctx

  async function addCurrentWordToWordbook() {
    const term = state.currentRecord?.normalizedTerm || elements.termInput.value.trim()
    if (!state.token) {
      toast('请先登录')
      return
    }
    if (!term) {
      toast('先学习一个单词')
      return
    }
    if (!state.wordbooks.length) {
      await loadWordbooks()
    }
    openAddWordbookModal(term)
  }

  function openAddWordbookModal(term) {
    elements.addWordbookTerm.textContent = term
    renderAddWordbookList(term)
    showModal(elements.addWordbookModal)
  }

  function closeAddWordbookModal() {
    hideModal(elements.addWordbookModal)
  }

  function renderAddWordbookList(term) {
    if (!state.wordbooks.length) {
      elements.addWordbookList.className = 'wordbook-picker-list empty'
      elements.addWordbookList.textContent = '暂无词书，请先在个人信息中创建词书'
      return
    }
    const selectedWordbookId = resolveSelectedWordbookId(state, elements, { preferDefault: true })
    elements.addWordbookList.className = 'wordbook-picker-list'
    elements.addWordbookList.innerHTML = state.wordbooks
      .map(
        (wordbook) => {
          const selected = sameId(wordbook.id, selectedWordbookId)
          return `
          <button class="wordbook-picker-item ${selected ? 'active' : ''}" type="button" data-add-wordbook-id="${escapeHtml(wordbook.id)}" aria-pressed="${selected ? 'true' : 'false'}">
            <strong>${escapeHtml(wordbook.name)}</strong>
            <span>${escapeHtml(wordbook.description || (wordbook.isDefault ? '默认词书' : '自定义词书'))}</span>
            <small>${selected ? '已选 · ' : ''}${wordbook.entryCount || 0} 个单词 · ${wordbook.dueCount || 0} 个待复习</small>
          </button>
        `
        },
      )
      .join('')
    elements.addWordbookList.querySelectorAll('[data-add-wordbook-id]').forEach((button) => {
      button.addEventListener('click', () => addWordToWordbook(term, button.getAttribute('data-add-wordbook-id')))
    })
  }

  async function addWordToWordbook(term, wordbookId) {
    const normalizedWordbookId = normalizeWordbookId(wordbookId)
    if (!normalizedWordbookId) {
      toast('请选择词书')
      return
    }
    const targetWordbook = state.wordbooks.find((wordbook) => sameId(wordbook.id, normalizedWordbookId))
    if (!targetWordbook) {
      toast(`词书不存在：${normalizedWordbookId}`)
      return
    }
    setLoading(true)
    try {
      if (state.preview) {
        const existing = state.wordbookEntries.find((entry) => entry.normalizedTerm === term)
        if (!existing) {
          const entry = {
            id: String(Date.now()),
            term,
            normalizedTerm: term,
            status: 'vague',
            note: '',
            reviewStage: 0,
            masteryScore: 0,
            nextReviewTime: new Date().toISOString(),
            parsed: state.currentRecord?.parsed,
            wordbookId: normalizedWordbookId,
          }
          state.wordbookEntries.unshift(entry)
          state.reviewEntries.unshift(entry)
          state.activity = createPreviewActivity()
          renderWordbookEntries()
          renderNotes(entry)
        }
        syncCurrentWordbookId(state, elements, normalizedWordbookId)
        closeAddWordbookModal()
        renderWordbooks()
        logEvent('wordbook', '预览加入词表', `${term} -> ${currentWordbookName(normalizedWordbookId)}`)
        toast('设计预览：已模拟加入词书')
        return
      }
      syncCurrentWordbookId(state, elements, normalizedWordbookId)
      const entry = await request(`/api/v1/learning/wordbooks/${encodeURIComponent(normalizedWordbookId)}/entries`, {
        method: 'POST',
        body: JSON.stringify({ term }),
      })
      await Promise.allSettled([loadWordbooks(), loadDueReviews()])
      await loadWordbookEntries()
      await loadActivity()
      renderNotes(entry)
      closeAddWordbookModal()
      logEvent('wordbook', '加入单词本', `${term} -> ${currentWordbookName(normalizedWordbookId)}`)
      toast('已保存到词书，若已存在则已更新学习卡')
    } catch (error) {
      logEvent('error', '加入词书失败', error.message)
      toast(`加入词书失败：${error.message}`)
    } finally {
      setLoading(false)
    }
  }

  return {
    addCurrentWordToWordbook,
    openAddWordbookModal,
    closeAddWordbookModal,
    renderAddWordbookList,
    addWordToWordbook,
  }
}
