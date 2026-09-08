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
    if (elements.studyNote) {
      elements.studyNote.className = `note-view${html ? '' : ' empty'}`
      elements.studyNote.innerHTML = html || fallback
    }
    if (elements.reviewNote) {
      elements.reviewNote.className = `note-view${html ? '' : ' empty'}`
      elements.reviewNote.innerHTML = html || (entry ? '<span class="empty">暂无笔记，复习时也可以编辑同一份笔记</span>' : '<span class="empty">选择复习单词后查看同一份 Markdown 笔记</span>')
    }
  }

  function isStudyNoteDrawerOpen() {
    return Boolean(elements.studyNoteDrawer && !elements.studyNoteDrawer.classList.contains('hidden'))
  }

  function openStudyNoteDrawer(shouldEdit = false) {
    if (!elements.studyNoteDrawer) return
    elements.studyNoteDrawer.classList.remove('hidden')
    elements.studyNoteDrawerBackdrop?.classList.remove('hidden')
    elements.studySplitLayout?.classList.add('with-note-open')
    const entry = state.currentNoteEntry || findEntryForRecord?.(state.currentRecord)
    if (entry && elements.studyNoteDrawerTitle) {
      elements.studyNoteDrawerTitle.textContent = `学习笔记 · ${entry.term || entry.normalizedTerm || ''}`
    }
    if (shouldEdit) {
      editCurrentNote()
    }
  }

  function closeStudyNoteDrawer() {
    if (!elements.studyNoteDrawer) return
    elements.studyNoteDrawer.classList.add('hidden')
    elements.studyNoteDrawerBackdrop?.classList.add('hidden')
    elements.studySplitLayout?.classList.remove('with-note-open')
  }

  function toggleStudyNoteDrawer(forceState) {
    const currentlyOpen = isStudyNoteDrawerOpen()
    const nextState = typeof forceState === 'boolean' ? forceState : !currentlyOpen
    if (nextState) {
      openStudyNoteDrawer()
    } else {
      closeStudyNoteDrawer()
    }
  }

  function editCurrentNote() {
    const isWordCardModalOpen = Boolean(elements.wordbookCardModal && !elements.wordbookCardModal.classList.contains('hidden'))
    const entry = (isWordCardModalOpen && state.selectedEntry)
      ? state.selectedEntry
      : (state.currentNoteEntry || findEntryForRecord?.(state.currentRecord) || state.selectedEntry)
    if (!entry) {
      toast('请先把单词加入当前词表')
      return
    }
    state.currentNoteEntry = entry
    const focusTarget = state.selectedEntry && sameId(state.selectedEntry.id, entry.id) ? elements.wordbookFocus?.querySelector('.note-view') : null
    const textareaHtml = `
      <div class="note-editor">
        <div class="scene-note-status-bar" style="margin-bottom: 6px;">
          <span class="scene-note-auto-badge">快捷键：⌘S / ⌘Enter 保存，⌘E 保存并退出，Esc 取消</span>
        </div>
        <textarea rows="8" placeholder="支持 Markdown，例如：## 记忆点">${escapeHtml(entry.note || '')}</textarea>
        <div class="inline-actions">
          <button class="secondary-button compact" type="button" data-save-note title="快捷键: ⌘S / ⌘Enter / ⌘E">保存笔记 (⌘S)</button>
          <button class="ghost-button compact" type="button" data-cancel-note title="快捷键: Esc">取消</button>
        </div>
      </div>
    `
    let targetContainers = []
    if (isWordCardModalOpen && focusTarget) {
      focusTarget.innerHTML = textareaHtml
      targetContainers = [focusTarget]
    } else {
      if (elements.studyNote) elements.studyNote.innerHTML = textareaHtml
      if (elements.reviewNote) elements.reviewNote.innerHTML = textareaHtml
      if (focusTarget) focusTarget.innerHTML = textareaHtml
      targetContainers = [elements.studyNote, elements.reviewNote, focusTarget].filter(Boolean)
    }

    const cancelAction = () => {
      renderNotes(entry)
      if (state.selectedEntry && sameId(state.selectedEntry.id, entry.id)) renderWordbookFocus(entry)
    }

    targetContainers.forEach((container) => {
      container.querySelectorAll('[data-save-note]').forEach((button) => button.addEventListener('click', () => saveCurrentNote(button)))
      container.querySelectorAll('[data-cancel-note]').forEach((button) => button.addEventListener('click', cancelAction))

      const ta = container.querySelector('textarea')
      if (ta) {
        ta.addEventListener('keydown', (e) => {
          const isSaveKey = (e.metaKey || e.ctrlKey) && (e.key === 's' || e.key === 'S' || e.key === 'Enter')
          const isToggleEdit = (e.metaKey || e.ctrlKey) && (e.key === 'e' || e.key === 'E')
          if (isSaveKey || isToggleEdit) {
            e.preventDefault()
            e.stopPropagation()
            saveCurrentNote(ta)
            return
          }
          if (e.key === 'Escape') {
            e.preventDefault()
            e.stopPropagation()
            cancelAction()
          }
        })
      }
    })

    // 自动聚焦到目标容器的 textarea
    const activeTextarea = targetContainers[0]?.querySelector('textarea')
    if (activeTextarea) {
      activeTextarea.focus()
      activeTextarea.setSelectionRange(activeTextarea.value.length, activeTextarea.value.length)
    }
  }

  async function saveCurrentNote(target) {
    const entry = state.currentNoteEntry
    if (!entry) return
    const editor = target?.closest?.('.note-editor') || document.querySelector('.note-editor')
    const input = editor?.querySelector('textarea')
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
    openStudyNoteDrawer,
    closeStudyNoteDrawer,
    toggleStudyNoteDrawer,
    isStudyNoteDrawerOpen,
  }
}
