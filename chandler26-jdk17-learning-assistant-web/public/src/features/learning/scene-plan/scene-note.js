import { hideModal, showModal } from '/src/shared/modal.js'
import { renderMarkdown } from '/src/shared/vocabulary.js'

export function createSceneNote({ state, elements, api, activeUnit, sameId, toast, logEvent }) {
  function formatNoteTime(value) {
    const date = new Date(value)
    return Number.isNaN(date.getTime()) ? '刚刚' : date.toLocaleString('zh-CN', { hour12: false })
  }

  function render(unit, errorMessage = '') {
    if (!elements.sceneNoteInput || !elements.sceneNotePreview) return
    const content = state.sceneNote?.content || ''
    elements.sceneNoteInput.value = content
    elements.sceneNotePreview.innerHTML = content
      ? renderMarkdown(content)
      : '<span class="empty">还没有笔记，记录本篇材料的重点、疑问或例句。</span>'
    elements.sceneNotePreview.classList.toggle('hidden', state.sceneNoteMode !== 'preview')
    elements.sceneNoteInput.classList.toggle('hidden', state.sceneNoteMode === 'preview')
    elements.sceneNotePreviewBtn.textContent = state.sceneNoteMode === 'preview' ? '编辑笔记' : '预览 Markdown'
    elements.sceneNoteStatus.textContent = errorMessage || (state.sceneNote?.updateTime ? `已保存 ${formatNoteTime(state.sceneNote.updateTime)}` : '未保存')
    elements.sceneNoteStatus.classList.toggle('error', Boolean(errorMessage))
    elements.sceneNoteSaveBtn.disabled = !unit || state.sceneNoteMode === 'preview'
  }

  async function load(unit = activeUnit()) {
    if (!unit) {
      state.sceneNote = { content: '', updateTime: null, unitId: null }
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
      render(unit)
      return
    }
    try {
      const note = await api.getNote(unit.planId, unit.id)
      state.sceneNote = { content: note?.content || '', updateTime: note?.updateTime || null, unitId: unit.id }
      render(unit)
    } catch (error) {
      state.sceneNote = { content: '', updateTime: null, unitId: unit.id }
      render(unit, '笔记加载失败，可重试')
      logEvent('error', '场景笔记加载失败', error.message)
    }
  }

  function togglePreview() {
    const nextMode = state.sceneNoteMode === 'preview' ? 'edit' : 'preview'
    if (nextMode === 'preview' && elements.sceneNoteInput) {
      state.sceneNote = { ...state.sceneNote, content: elements.sceneNoteInput.value }
    }
    state.sceneNoteMode = nextMode
    render(activeUnit())
  }

  async function save() {
    const unit = activeUnit()
    if (!unit || !elements.sceneNoteInput) return
    const content = elements.sceneNoteInput.value
    elements.sceneNoteSaveBtn.disabled = true
    elements.sceneNoteStatus.textContent = '保存中...'
    try {
      if (state.preview) {
        state.sceneNote = { content, updateTime: new Date().toISOString(), unitId: unit.id }
        unit.note = { content, updateTime: state.sceneNote.updateTime }
      } else {
        const note = await api.saveNote(unit.planId, unit.id, content)
        state.sceneNote = { content: note?.content || '', updateTime: note?.updateTime || new Date().toISOString(), unitId: unit.id }
      }
      render(unit)
      toast('场景材料笔记已保存')
    } catch (error) {
      render(unit, '保存失败，请重试')
      toast(`场景笔记保存失败：${error.message}`)
    } finally {
      elements.sceneNoteSaveBtn.disabled = false
    }
  }

  function openModal() {
    const unit = activeUnit()
    if (!unit) return
    void load(unit)
    showModal(elements.sceneNoteModal)
  }

  function closeModal() {
    hideModal(elements.sceneNoteModal)
  }

  return {
    load,
    render,
    save,
    togglePreview,
    openModal,
    closeModal,
  }
}
