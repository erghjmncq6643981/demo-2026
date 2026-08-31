import { renderMarkdown } from '/src/shared/vocabulary.js'

export function createSceneNote({ state, elements, api, activeUnit, sameId, toast, logEvent }) {
  let saveTimer = null
  let isDirty = false

  function formatNoteTime(value) {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return '刚刚'
    const hours = String(date.getHours()).padStart(2, '0')
    const minutes = String(date.getMinutes()).padStart(2, '0')
    const seconds = String(date.getSeconds()).padStart(2, '0')
    return `${hours}:${minutes}:${seconds}`
  }

  function updateButtonText(unit = activeUnit()) {
    const content = state.sceneNote?.content || unit?.note?.content || ''
    const hasNote = Boolean(content && content.trim())
    if (elements.sceneOpenNoteBtnText) {
      elements.sceneOpenNoteBtnText.textContent = hasNote ? '查看笔记' : '添加笔记'
    }
    if (elements.sceneOpenNoteModalBtn) {
      elements.sceneOpenNoteModalBtn.classList.toggle('has-note', hasNote)
      elements.sceneOpenNoteModalBtn.title = hasNote ? '查看并编辑场景笔记' : '记录本篇场景笔记'
    }
  }

  function render(unit = activeUnit(), errorMessage = '') {
    if (!elements.sceneNoteInput || !elements.sceneNotePreview) return
    const content = state.sceneNote?.content || ''
    elements.sceneNoteInput.value = content

    const isPreview = state.sceneNoteMode === 'preview'
    elements.sceneNotePreview.innerHTML = content
      ? renderMarkdown(content)
      : '<div class="scene-note-empty-preview"><p>📝 还没有笔记</p><p class="sub">点击右上角「✏️ 编辑」记录本篇材料的重点、难句或个人思考。</p></div>'

    elements.sceneNotePreview.classList.toggle('hidden', !isPreview)
    elements.sceneNoteInput.classList.toggle('hidden', isPreview)

    if (elements.sceneNoteEditBtn) {
      elements.sceneNoteEditBtn.classList.toggle('hidden', !isPreview)
    }
    if (elements.sceneNotePreviewBtn) {
      elements.sceneNotePreviewBtn.classList.toggle('hidden', isPreview)
    }

    if (elements.sceneNoteStatus) {
      if (errorMessage) {
        elements.sceneNoteStatus.textContent = errorMessage
        elements.sceneNoteStatus.className = 'scene-note-status error'
      } else if (state.sceneNote?.updateTime) {
        elements.sceneNoteStatus.textContent = `✓ 已自动保存 ${formatNoteTime(state.sceneNote.updateTime)}`
        elements.sceneNoteStatus.className = 'scene-note-status saved'
      } else if (content.trim()) {
        elements.sceneNoteStatus.textContent = '未保存'
        elements.sceneNoteStatus.className = 'scene-note-status'
      } else {
        elements.sceneNoteStatus.textContent = '无内容'
        elements.sceneNoteStatus.className = 'scene-note-status'
      }
    }

    updateButtonText(unit)
  }

  async function load(unit = activeUnit()) {
    if (!unit) {
      state.sceneNote = { content: '', updateTime: null, unitId: null }
      state.sceneNoteMode = 'edit'
      render(null)
      return
    }
    if (sameId(state.sceneNote?.unitId, unit.id)) {
      render(unit)
      return
    }
    if (state.preview) {
      const content = unit.note?.content || ''
      state.sceneNote = { content, updateTime: unit.note?.updateTime || null, unitId: unit.id }
      state.sceneNoteMode = content.trim() ? 'preview' : 'edit'
      render(unit)
      return
    }
    try {
      const note = await api.getNote(unit.planId, unit.id)
      const content = note?.content || ''
      state.sceneNote = { content, updateTime: note?.updateTime || null, unitId: unit.id }
      state.sceneNoteMode = content.trim() ? 'preview' : 'edit'
      render(unit)
    } catch (error) {
      state.sceneNote = { content: '', updateTime: null, unitId: unit.id }
      state.sceneNoteMode = 'edit'
      render(unit, '笔记加载失败，可重试')
      logEvent('error', '场景笔记加载失败', error.message)
    }
  }

  function handleInput(event) {
    const unit = activeUnit()
    if (!unit || !elements.sceneNoteInput) return
    const content = elements.sceneNoteInput.value
    state.sceneNote = { ...state.sceneNote, content }
    isDirty = true

    if (elements.sceneNoteStatus) {
      elements.sceneNoteStatus.textContent = '● 正在保存...'
      elements.sceneNoteStatus.className = 'scene-note-status saving'
    }

    if (saveTimer) {
      clearTimeout(saveTimer)
    }
    saveTimer = setTimeout(() => {
      void flushSave()
    }, 800)
  }

  async function flushSave() {
    if (saveTimer) {
      clearTimeout(saveTimer)
      saveTimer = null
    }
    if (!isDirty) return
    const unit = activeUnit()
    if (!unit || !elements.sceneNoteInput) return
    const content = elements.sceneNoteInput.value
    isDirty = false

    if (elements.sceneNoteStatus) {
      elements.sceneNoteStatus.textContent = '● 正在保存...'
      elements.sceneNoteStatus.className = 'scene-note-status saving'
    }

    try {
      if (state.preview) {
        state.sceneNote = { content, updateTime: new Date().toISOString(), unitId: unit.id }
        unit.note = { content, updateTime: state.sceneNote.updateTime }
      } else {
        const note = await api.saveNote(unit.planId, unit.id, content)
        state.sceneNote = { content: note?.content || '', updateTime: note?.updateTime || new Date().toISOString(), unitId: unit.id }
      }
      render(unit)
    } catch (error) {
      isDirty = true
      render(unit, '保存失败，请检查网络')
      logEvent('error', '场景笔记保存失败', error.message)
    }
  }

  function setMode(mode) {
    if (mode === 'preview') {
      void flushSave()
    }
    state.sceneNoteMode = mode
    render(activeUnit())
    if (mode === 'edit' && elements.sceneNoteInput) {
      elements.sceneNoteInput.focus()
    }
  }

  function togglePreview() {
    setMode(state.sceneNoteMode === 'preview' ? 'edit' : 'preview')
  }

  function togglePanel(forceOpen) {
    const unit = activeUnit()
    if (!unit) return

    const nextOpen = typeof forceOpen === 'boolean' ? forceOpen : !state.sceneNotePanelOpen
    state.sceneNotePanelOpen = nextOpen

    if (elements.sceneNotePanel) {
      elements.sceneNotePanel.classList.toggle('hidden', !nextOpen)
    }
    if (elements.sceneStudySplitLayout) {
      elements.sceneStudySplitLayout.classList.toggle('with-note-open', nextOpen)
    }

    if (nextOpen) {
      const content = state.sceneNote?.content || ''
      // If note has existing content, open in preview mode; otherwise open in edit mode
      state.sceneNoteMode = content.trim() ? 'preview' : 'edit'
      render(unit)
      if (state.sceneNoteMode === 'edit' && elements.sceneNoteInput) {
        elements.sceneNoteInput.focus()
      }
    } else {
      void flushSave()
    }
  }

  function openPanel() {
    togglePanel(true)
  }

  function closePanel() {
    togglePanel(false)
  }

  return {
    load,
    render,
    save: flushSave,
    flushSave,
    handleInput,
    setMode,
    togglePreview,
    togglePanel,
    openPanel,
    closePanel,
    openModal: openPanel,
    closeModal: closePanel,
    updateButtonText,
  }
}
