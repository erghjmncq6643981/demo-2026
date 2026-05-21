import { sameId } from '/src/shared/ids.js'
import { escapeHtml } from '/src/shared/text.js'
import { renderMarkdown } from '/src/shared/vocabulary.js'

export function createStudyNotesFeature(ctx) {
  const {
    state,
    elements,
    request,
    toast,
    logEvent,
    findEntryForRecord,
    renderWordbookEntries,
    renderWordbookFocus,
    renderReviewQueue,
  } = ctx

  function renderNotes(entry) {
    state.currentNoteEntry = entry || null
    const html = entry?.note ? renderMarkdown(entry.note) : ''
    const fallback = entry ? '<span class="empty">暂无笔记，点击编辑记录 Markdown</span>' : '<span class="empty">加入或选择单词后可以记录 Markdown 笔记</span>'
    elements.studyNote.className = `note-view${html ? '' : ' empty'}`
    elements.reviewNote.className = `note-view${html ? '' : ' empty'}`
    elements.studyNote.innerHTML = html || fallback
    elements.reviewNote.innerHTML = html || (entry ? '<span class="empty">暂无笔记，复习时也可以编辑同一份笔记</span>' : '<span class="empty">选择复习单词后查看同一份 Markdown 笔记</span>')
  }

  function editCurrentNote() {
    const entry = state.currentNoteEntry || findEntryForRecord(state.currentRecord)
    if (!entry) {
      toast('请先把单词加入当前词表')
      return
    }
    state.currentNoteEntry = entry
    const focusTarget = state.selectedEntry && sameId(state.selectedEntry.id, entry.id) ? elements.wordbookFocus.querySelector('.note-view') : null
    const textarea = `
      <div class="note-editor">
        <textarea rows="8" placeholder="支持 Markdown，例如：## 记忆点">${escapeHtml(entry.note || '')}</textarea>
        <div class="inline-actions">
          <button class="secondary-button compact" type="button" data-save-note>保存笔记</button>
          <button class="ghost-button compact" type="button" data-cancel-note>取消</button>
        </div>
      </div>
    `
    elements.studyNote.innerHTML = textarea
    elements.reviewNote.innerHTML = textarea
    if (focusTarget) focusTarget.innerHTML = textarea
    document.querySelectorAll('[data-save-note]').forEach((button) => button.addEventListener('click', () => saveCurrentNote(button)))
    document.querySelectorAll('[data-cancel-note]').forEach((button) =>
      button.addEventListener('click', () => {
        renderNotes(entry)
        if (state.selectedEntry && sameId(state.selectedEntry.id, entry.id)) renderWordbookFocus(entry)
      }),
    )
  }

  async function saveCurrentNote(button) {
    const entry = state.currentNoteEntry
    if (!entry) return
    const input = button.closest('.note-editor')?.querySelector('textarea')
    const note = input?.value || ''
    await saveEntry(entry.id, { note })
  }

  async function saveEntry(entryId, payload) {
    if (state.preview) {
      for (const list of [state.wordbookEntries, state.reviewEntries]) {
        const entry = list.find((item) => sameId(item.id, entryId))
        if (entry) Object.assign(entry, payload)
      }
      const updated = state.wordbookEntries.find((item) => sameId(item.id, entryId)) || state.reviewEntries.find((item) => sameId(item.id, entryId))
      if (state.selectedEntry && sameId(state.selectedEntry.id, entryId)) {
        state.selectedEntry = { ...state.selectedEntry, ...updated }
      }
      if (state.currentReviewEntry && sameId(state.currentReviewEntry.id, entryId)) {
        state.currentReviewEntry = { ...state.currentReviewEntry, ...updated }
      }
      renderWordbookEntries()
      renderReviewQueue(state.reviewEntries)
      renderNotes(updated)
      toast('设计预览：词条已更新')
      return updated
    }
    try {
      const updated = await request(`/api/v1/learning/wordbook-entries/${encodeURIComponent(entryId)}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      })
      state.wordbookEntries = state.wordbookEntries.map((entry) => (sameId(entry.id, entryId) ? { ...entry, ...updated } : entry))
      state.reviewEntries = state.reviewEntries.map((entry) => (sameId(entry.id, entryId) ? { ...entry, ...updated } : entry))
      if (state.selectedEntry && sameId(state.selectedEntry.id, entryId)) {
        state.selectedEntry = { ...state.selectedEntry, ...updated }
      }
      if (state.currentReviewEntry && sameId(state.currentReviewEntry.id, entryId)) {
        state.currentReviewEntry = { ...state.currentReviewEntry, ...updated }
      }
      renderWordbookEntries()
      renderReviewQueue(state.reviewEntries)
      renderNotes(updated)
      toast('词条已更新')
      return updated
    } catch (error) {
      logEvent('error', '词条更新失败', error.message)
      toast(`词条更新失败：${error.message}`)
      return null
    }
  }

  return {
    renderNotes,
    editCurrentNote,
    saveCurrentNote,
    saveEntry,
  }
}
