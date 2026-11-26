import { sameId } from '/src/shared/ids.js'
import { escapeHtml } from '/src/shared/text.js'
import { renderMarkdown } from '/src/shared/vocabulary.js'
import { handleTextareaTabIndent } from '/src/features/learning/scene-plan/scene-note.js'

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
    setSidebarCollapsed,
  } = ctx

  const STUDY_NOTE_EMPTY_HTML = '<div class="scene-note-empty-preview"><p class="sub">暂无笔记，双击开始编辑</p></div>'
  const NOTE_DEBOUNCE_DELAY_MS = 2500

  let activeStudyNoteEntry = null
  let lastSavedStudyNote = ''
  let isStudyNoteDirty = false
  let isStudyNoteComposing = false
  let studyNoteAutoSaveTimer = null
  let inFlightStudyNoteSavePromise = null

  function normalizeTermKey(value) {
    return String(value || '').trim().toLowerCase()
  }

  function recordTermKey(record) {
    return normalizeTermKey(record?.normalizedTerm || record?.term || record?.lemma || record?.parsed?.term)
  }

  function entryTermKey(entry) {
    return normalizeTermKey(entry?.normalizedTerm || entry?.term)
  }

  function isEntryMatchingCurrentRecord(entry) {
    if (!state.currentRecord || !entry) return false
    if (state.currentRecord.userEntry?.id && entry.id && sameId(state.currentRecord.userEntry.id, entry.id)) {
      return true
    }
    const rTerm = recordTermKey(state.currentRecord)
    const eTerm = entryTermKey(entry)
    return Boolean(rTerm && eTerm && rTerm === eTerm)
  }

  function resolveStudyNoteEntry(record = state.currentRecord) {
    if (!record) return null
    const existing = findEntryForRecord?.(record) || record.userEntry
    if (existing) return existing
    const term = record.normalizedTerm || record.term || record.lemma || record.parsed?.term || ''
    if (!term) return null
    return { id: null, term, normalizedTerm: term, note: '' }
  }

  function updateStudyNoteStatus(statusText, tone = '', title = '') {
    if (!elements.studyNoteStatus) return
    elements.studyNoteStatus.textContent = statusText
    elements.studyNoteStatus.title = title
    elements.studyNoteStatus.className = tone ? `scene-note-status ${tone}` : 'scene-note-status'
  }

  function setStudyNoteMode(mode) {
    if (!elements.studyNote || !elements.studyNoteInput) return
    const isEdit = mode === 'edit'
    if (!isEdit && isStudyNoteDirty) {
      void flushStudyNoteSave(false)
    }
    const content = elements.studyNoteInput.value || ''
    elements.studyNote.innerHTML = content ? renderMarkdown(content) : STUDY_NOTE_EMPTY_HTML
    elements.studyNote.className = `scene-note-preview${content ? '' : ' empty'}${isEdit ? ' hidden' : ''}`
    elements.studyNoteInput.classList.toggle('hidden', !isEdit)
    if (elements.studyNotePreviewBtn) {
      elements.studyNotePreviewBtn.textContent = isEdit ? '👁️ 预览' : '✏️ 编辑'
    }
    if (isEdit) {
      elements.studyNoteInput.focus()
    }
  }

  function toggleStudyNotePreview() {
    if (!elements.studyNote || !elements.studyNoteInput) return
    const isPreview = !elements.studyNote.classList.contains('hidden')
    setStudyNoteMode(isPreview ? 'edit' : 'preview')
  }

  function renderNotes(entry) {
    state.currentNoteEntry = entry || null
    const html = entry?.note ? renderMarkdown(entry.note) : ''
    if (elements.studyNote) {
      const shouldSyncStudyView = entry === null ? !state.currentRecord : isEntryMatchingCurrentRecord(entry)
      if (shouldSyncStudyView) {
        const note = entry?.note || ''
        if (entry) activeStudyNoteEntry = entry
        lastSavedStudyNote = note
        isStudyNoteDirty = false
        if (elements.studyNoteInput && document.activeElement !== elements.studyNoteInput) {
          elements.studyNoteInput.value = note
        }
        elements.studyNote.className = `scene-note-preview${html ? '' : ' empty'}`
        elements.studyNote.innerHTML = html || STUDY_NOTE_EMPTY_HTML
        if (note.trim()) {
          updateStudyNoteStatus('✓ 已保存', 'saved', '已同步')
        } else {
          updateStudyNoteStatus('')
        }
      }
    }
    if (elements.reviewNote) {
      elements.reviewNote.className = `note-view${html ? '' : ' empty'}`
      elements.reviewNote.innerHTML = html || (entry ? '<span class="empty">暂无笔记，复习时也可以编辑同一份笔记</span>' : '<span class="empty">选择复习单词后查看同一份 Markdown 笔记</span>')
    }
  }

  function handleStudyNoteCompositionStart() {
    isStudyNoteComposing = true
  }

  function handleStudyNoteCompositionEnd() {
    isStudyNoteComposing = false
    handleStudyNoteInput()
  }

  function handleStudyNoteInput() {
    if (isStudyNoteComposing) return
    const targetEntry = activeStudyNoteEntry || resolveStudyNoteEntry(state.currentRecord)
    if (!targetEntry || !elements.studyNoteInput) return
    activeStudyNoteEntry = targetEntry

    const currentContent = elements.studyNoteInput.value || ''
    if (currentContent === lastSavedStudyNote) {
      isStudyNoteDirty = false
      if (studyNoteAutoSaveTimer) {
        clearTimeout(studyNoteAutoSaveTimer)
        studyNoteAutoSaveTimer = null
      }
      if (lastSavedStudyNote.trim()) {
        updateStudyNoteStatus('✓ 已保存', 'saved', '已同步')
      } else {
        updateStudyNoteStatus('')
      }
      return
    }

    isStudyNoteDirty = true
    updateStudyNoteStatus('● 待保存', 'saving')

    if (studyNoteAutoSaveTimer) {
      clearTimeout(studyNoteAutoSaveTimer)
    }
    studyNoteAutoSaveTimer = setTimeout(() => {
      void flushStudyNoteSave(false)
    }, NOTE_DEBOUNCE_DELAY_MS)
  }

  async function flushStudyNoteSave(isManual = false) {
    if (studyNoteAutoSaveTimer) {
      clearTimeout(studyNoteAutoSaveTimer)
      studyNoteAutoSaveTimer = null
    }
    if (inFlightStudyNoteSavePromise) {
      return inFlightStudyNoteSavePromise
    }

    const targetEntry = activeStudyNoteEntry || resolveStudyNoteEntry(state.currentRecord)
    if (!targetEntry || !elements.studyNoteInput) return null

    const note = elements.studyNoteInput.value || ''
    if (!isStudyNoteDirty && note === lastSavedStudyNote && !isManual) {
      return targetEntry
    }

    updateStudyNoteStatus('● 保存中...', 'saving')

    inFlightStudyNoteSavePromise = (async () => {
      try {
        let resolvedEntryId = targetEntry.id || targetEntry.entryId || null
        const termToAdd = targetEntry.term || targetEntry.normalizedTerm || recordTermKey(state.currentRecord)

        if (!resolvedEntryId && !state.preview && typeof request === 'function') {
          const targetWordbookId = state.currentWordbookId || state.wordbooks?.[0]?.id
          if (targetWordbookId && termToAdd) {
            try {
              const created = await request(`/api/v1/learning/wordbooks/${encodeURIComponent(targetWordbookId)}/entries`, {
                method: 'POST',
                body: JSON.stringify({ term: termToAdd, note }),
              })
              if (created?.id) {
                resolvedEntryId = created.id
                targetEntry.id = created.id
                targetEntry.wordbookId = created.wordbookId || targetWordbookId
                if (isEntryMatchingCurrentRecord(targetEntry)) {
                  state.currentRecord.userEntry = { ...created, note }
                }
              }
            } catch {
              // 降级继续
            }
          }
        } else if (!resolvedEntryId && state.preview && termToAdd) {
          const previewEntry = {
            id: `preview-study-${Date.now()}`,
            term: termToAdd,
            normalizedTerm: termToAdd,
            status: 'vague',
            note,
            wordbookId: state.currentWordbookId || state.wordbooks?.[0]?.id || '1',
          }
          state.wordbookEntries = state.wordbookEntries || []
          state.wordbookEntries.unshift(previewEntry)
          resolvedEntryId = previewEntry.id
          targetEntry.id = previewEntry.id
          targetEntry.wordbookId = previewEntry.wordbookId
          if (isEntryMatchingCurrentRecord(targetEntry)) {
            state.currentRecord.userEntry = previewEntry
          }
        }

        const updated = resolvedEntryId
          ? await saveEntry(resolvedEntryId, { note }, { silent: !isManual })
          : null

        if (updated || state.preview) {
          lastSavedStudyNote = note
          isStudyNoteDirty = false
          targetEntry.note = note
          if (state.currentRecord?.userEntry && isEntryMatchingCurrentRecord(targetEntry)) {
            state.currentRecord.userEntry.note = note
          }
          updateStudyNoteStatus('✓ 已保存', 'saved', isManual ? '已手动保存' : '已自动保存')
          if (isManual) {
            toast?.('学习笔记已保存')
          }
          if (elements.studyNote) {
            elements.studyNote.className = `scene-note-preview${note ? '' : ' empty'}${elements.studyNoteInput.classList.contains('hidden') ? '' : ' hidden'}`
            elements.studyNote.innerHTML = note ? renderMarkdown(note) : STUDY_NOTE_EMPTY_HTML
          }
          return updated || targetEntry
        }

        updateStudyNoteStatus('保存失败', 'error', '按 ⌘S 重试')
        return null
      } catch (error) {
        updateStudyNoteStatus('保存失败', 'error', '按 ⌘S 重试')
        toast?.(`学习笔记保存失败：${error.message || error}`)
        return null
      } finally {
        inFlightStudyNoteSavePromise = null
      }
    })()

    return inFlightStudyNoteSavePromise
  }

  function saveStudyNote(isManual = true) {
    return flushStudyNoteSave(isManual)
  }

  function applyStudyEntryToPanel(entry) {
    activeStudyNoteEntry = entry
    if (elements.studyNoteDrawerTitle) {
      elements.studyNoteDrawerTitle.textContent = '学习笔记'
    }
    const note = entry?.note || ''
    lastSavedStudyNote = note
    isStudyNoteDirty = false
    if (elements.studyNoteInput) {
      elements.studyNoteInput.value = note
    }
    if (elements.studyNote) {
      elements.studyNote.innerHTML = note ? renderMarkdown(note) : STUDY_NOTE_EMPTY_HTML
      elements.studyNote.className = `scene-note-preview${note ? '' : ' empty'}`
    }
    if (note.trim()) {
      updateStudyNoteStatus('✓ 已保存', 'saved', '已同步')
    } else {
      updateStudyNoteStatus('')
    }
  }

  function syncStudyNoteForRecord(record) {
    if (isStudyNoteDirty && activeStudyNoteEntry && (!record || entryTermKey(activeStudyNoteEntry) !== recordTermKey(record))) {
      void flushStudyNoteSave(false)
    }
    if (studyNoteAutoSaveTimer) {
      clearTimeout(studyNoteAutoSaveTimer)
      studyNoteAutoSaveTimer = null
    }
    const entry = resolveStudyNoteEntry(record)
    applyStudyEntryToPanel(entry)
    if (isStudyNoteDrawerOpen() && entry) {
      setStudyNoteMode((entry.note || '').trim() ? 'preview' : 'edit')
    }
  }

  function isStudyNoteDrawerOpen() {
    return Boolean(elements.studyNoteDrawer && !elements.studyNoteDrawer.classList.contains('hidden'))
  }

  function openStudyNoteDrawer(shouldEdit = false) {
    if (!elements.studyNoteDrawer) return
    const entry = resolveStudyNoteEntry(state.currentRecord)
    if (!entry) return

    if (isStudyNoteDirty && activeStudyNoteEntry && entryTermKey(activeStudyNoteEntry) !== entryTermKey(entry)) {
      void flushStudyNoteSave(false)
    }
    if (studyNoteAutoSaveTimer) {
      clearTimeout(studyNoteAutoSaveTimer)
      studyNoteAutoSaveTimer = null
    }

    applyStudyEntryToPanel(entry)
    setSidebarCollapsed?.(true, { persist: false })
    elements.studyNoteDrawer.classList.remove('hidden')
    elements.studyNoteDrawerBackdrop?.classList.remove('hidden')
    elements.studySplitLayout?.classList.add('with-note-open')

    if (elements.studyNoteInput) {
      const hasNote = Boolean((entry.note || '').trim())
      setStudyNoteMode(shouldEdit || !hasNote ? 'edit' : 'preview')
    } else if (shouldEdit) {
      editCurrentNote()
    }
  }

  function closeStudyNoteDrawer() {
    if (!elements.studyNoteDrawer) return
    if (isStudyNoteDirty) {
      void flushStudyNoteSave(false)
    }
    if (studyNoteAutoSaveTimer) {
      clearTimeout(studyNoteAutoSaveTimer)
      studyNoteAutoSaveTimer = null
    }
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

  async function editCurrentNote() {
    const isWordCardModalOpen = Boolean(elements.wordbookCardModal && !elements.wordbookCardModal.classList.contains('hidden'))
    if (!isWordCardModalOpen && elements.studyNoteInput) {
      openStudyNoteDrawer(true)
      return
    }
    let entry = (isWordCardModalOpen && state.selectedEntry)
      ? state.selectedEntry
      : (state.currentNoteEntry || findEntryForRecord?.(state.currentRecord) || state.selectedEntry)
    if (!entry && state.currentRecord?.term) {
      const term = state.currentRecord.normalizedTerm || state.currentRecord.term
      try {
        const fetched = await request(`/api/v1/learning/wordbook-entries/by-term?term=${encodeURIComponent(term)}`)
        if (fetched) {
          entry = fetched
          if (state.currentRecord) state.currentRecord.userEntry = fetched
          state.currentNoteEntry = fetched
          renderNotes(fetched)
        }
      } catch (err) {
        // ignored
      }
    }
    if (!entry) {
      toast('请先把单词加入当前词表')
      return
    }
    state.currentNoteEntry = entry
    const focusTarget = state.selectedEntry && sameId(state.selectedEntry.id, entry.id) ? elements.wordbookFocus?.querySelector('.note-view') : null
    if (isWordCardModalOpen && focusTarget) {
      const existingCancelBtn = focusTarget.querySelector('[data-cancel-note]')
      if (existingCancelBtn) {
        existingCancelBtn.click()
        return
      }
      const section = focusTarget.closest('.focus-section')
      const toggleBtn = section?.querySelector('[data-edit-focus-note]')
      const statusEl = section?.querySelector('[data-focus-note-status]')
      const setFocusStatus = (text, tone = '', title = '') => {
        if (!statusEl) return
        statusEl.textContent = text
        statusEl.title = title
        statusEl.className = tone ? `scene-note-status ${tone}` : 'scene-note-status'
      }
      if (toggleBtn) toggleBtn.textContent = '👁️ 预览'
      focusTarget.innerHTML = `
        <div class="note-editor">
          <textarea class="scene-note-input" rows="6" placeholder="支持 Markdown">${escapeHtml(entry.note || '')}</textarea>
          <button type="button" class="sr-only" data-cancel-note>退出编辑</button>
        </div>
      `
      const ta = focusTarget.querySelector('textarea')
      const cancelBtn = focusTarget.querySelector('[data-cancel-note]')
      let lastSaved = entry.note || ''
      let isDirty = false
      let isComposing = false
      let saveTimer = null
      let inFlightPromise = null

      const flushFocusSave = async (isManual = false) => {
        if (saveTimer) {
          clearTimeout(saveTimer)
          saveTimer = null
        }
        if (inFlightPromise) return inFlightPromise
        const content = ta?.value || ''
        if (!isDirty && content === lastSaved && !isManual) return entry
        setFocusStatus('● 保存中...', 'saving')
        inFlightPromise = (async () => {
          try {
            const updated = await saveEntry(entry.id, { note: content }, { silent: !isManual })
            if (updated || state.preview) {
              lastSaved = content
              isDirty = false
              entry.note = content
              setFocusStatus('✓ 已保存', 'saved', isManual ? '已手动保存' : '已自动保存')
              if (isManual) toast?.('笔记已保存')
              return updated || entry
            }
            setFocusStatus('保存失败', 'error', '按 ⌘S 重试')
            return null
          } catch {
            setFocusStatus('保存失败', 'error', '按 ⌘S 重试')
            return null
          } finally {
            inFlightPromise = null
          }
        })()
        return inFlightPromise
      }

      const exitToPreview = async () => {
        await flushFocusSave(false)
        const latestNote = entry.note || lastSaved || ''
        focusTarget.className = `note-view scene-note-preview${latestNote.trim() ? '' : ' empty'}`
        focusTarget.innerHTML = latestNote.trim() ? renderMarkdown(latestNote) : STUDY_NOTE_EMPTY_HTML
        if (toggleBtn) toggleBtn.textContent = '✏️ 编辑'
        if (latestNote.trim()) {
          setFocusStatus('✓ 已保存', 'saved', '已同步')
        } else {
          setFocusStatus('')
        }
      }

      const handleFocusInput = () => {
        if (isComposing) return
        const content = ta?.value || ''
        if (content === lastSaved) {
          isDirty = false
          if (saveTimer) {
            clearTimeout(saveTimer)
            saveTimer = null
          }
          if (lastSaved.trim()) setFocusStatus('✓ 已保存', 'saved', '已同步')
          else setFocusStatus('')
          return
        }
        isDirty = true
        setFocusStatus('● 待保存', 'saving')
        if (saveTimer) clearTimeout(saveTimer)
        saveTimer = setTimeout(() => {
          void flushFocusSave(false)
        }, NOTE_DEBOUNCE_DELAY_MS)
      }

      cancelBtn?.addEventListener('click', () => { void exitToPreview() })
      ta?.addEventListener('compositionstart', () => { isComposing = true })
      ta?.addEventListener('compositionend', () => { isComposing = false; handleFocusInput() })
      ta?.addEventListener('input', handleFocusInput)
      ta?.addEventListener('blur', () => { void flushFocusSave(false) })
      ta?.addEventListener('keydown', (e) => {
        const isSaveKey = (e.metaKey || e.ctrlKey) && (e.key === 's' || e.key === 'S' || e.key === 'Enter')
        if (isSaveKey) {
          e.preventDefault()
          e.stopPropagation()
          void flushFocusSave(true)
          return
        }
        const isToggleEdit = (e.metaKey || e.ctrlKey) && (e.key === 'e' || e.key === 'E')
        if (isToggleEdit || e.key === 'Escape') {
          e.preventDefault()
          e.stopPropagation()
          void exitToPreview()
          return
        }
        if (e.key === 'Tab') {
          e.preventDefault()
          e.stopPropagation()
          handleTextareaTabIndent(ta, Boolean(e.shiftKey))
          handleFocusInput()
        }
      })
      if (ta) {
        ta.focus()
        ta.setSelectionRange(ta.value.length, ta.value.length)
      }
      return
    }
    const textareaHtml = `
      <div class="note-editor">
        <textarea rows="8" placeholder="支持 Markdown">${escapeHtml(entry.note || '')}</textarea>
        <div class="inline-actions">
          <button class="secondary-button compact" type="button" data-save-note title="快捷键: ⌘S / ⌘Enter / ⌘E">保存</button>
          <button class="ghost-button compact" type="button" data-cancel-note title="快捷键: Esc">取消</button>
        </div>
      </div>
    `
    if (elements.studyNote) elements.studyNote.innerHTML = textareaHtml
    if (elements.reviewNote) elements.reviewNote.innerHTML = textareaHtml
    if (focusTarget) focusTarget.innerHTML = textareaHtml
    const targetContainers = [elements.studyNote, elements.reviewNote, focusTarget].filter(Boolean)

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
          if (e.key === 'Tab') {
            e.preventDefault()
            e.stopPropagation()
            handleTextareaTabIndent(ta, Boolean(e.shiftKey))
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

  async function saveEntry(entryId, payload, options = {}) {
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
      if (!options?.silent) toast('设计预览：词条已更新')
      return updated
    }
    try {
      const updated = await request(`/api/v1/learning/wordbook-entries/${encodeURIComponent(entryId)}`, {
        method: 'PUT',
        body: JSON.stringify(payload),
      })
      if (state.currentRecord?.userEntry && sameId(state.currentRecord.userEntry.id, entryId)) {
        state.currentRecord.userEntry = { ...state.currentRecord.userEntry, ...updated }
      }
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
      if (!options?.silent) toast('词条已更新')
      return updated
    } catch (error) {
      logEvent('error', '词条更新失败', error.message)
      toast(`词条更新失败：${error.message}`)
      return null
    }
  }

  if (elements.reviewNote) {
    elements.reviewNote.addEventListener('click', () => {
      if (!elements.reviewNote.querySelector('.note-editor')) {
        editCurrentNote()
      }
    })
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
    setStudyNoteMode,
    toggleStudyNotePreview,
    saveStudyNote,
    flushStudyNoteSave,
    handleStudyNoteInput,
    handleStudyNoteCompositionStart,
    handleStudyNoteCompositionEnd,
    syncStudyNoteForRecord,
  }
}
