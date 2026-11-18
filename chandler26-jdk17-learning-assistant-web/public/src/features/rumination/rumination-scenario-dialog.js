/**
 * 知识反刍模块 - 新建场景交互弹窗
 * 负责场景名称、提示词收集、图标自适应提取及向外派发场景创建事件。
 */

import { showModal, bindModalDismiss } from '/src/shared/modal.js'

export function createRuminationScenarioDialog({
  elements = {},
  toast = (msg) => console.log(msg),
  onScenarioCreated = null,
} = {}) {
  function open() {
    if (!elements.addScenarioModal) return
    if (elements.newScenarioNameInput) {
      elements.newScenarioNameInput.value = ''
      setTimeout(() => elements.newScenarioNameInput?.focus(), 80)
    }
    if (elements.newScenarioPromptInput) {
      elements.newScenarioPromptInput.value = ''
    }
    showModal(elements.addScenarioModal)
  }

  const close = bindModalDismiss(elements.addScenarioModal, {
    closeButtons: [elements.closeScenarioModalBtn, elements.cancelScenarioModalBtn],
  })

  if (elements.toolbarAddScenarioBtn) {
    elements.toolbarAddScenarioBtn.addEventListener('click', open)
  }

  async function submitCreateScenario() {
    const name = elements.newScenarioNameInput?.value?.trim()
    const prompt = elements.newScenarioPromptInput?.value?.trim()
    if (!name) {
      toast('请输入场景名称（例如：🛒 零售超市与日常购物）')
      elements.newScenarioNameInput?.focus()
      return
    }

    const iconMatch = name.match(/^(\p{Extended_Pictographic})/u)
    const icon = iconMatch ? iconMatch[1] : '🏷️'
    const cleanName = iconMatch ? name.replace(iconMatch[1], '').trim() : name
    const code = `custom_${Date.now()}`

    const opt = document.createElement('option')
    opt.value = code
    opt.textContent = `${icon} ${cleanName}`
    opt.selected = true
    elements.scenarioSelect?.appendChild(opt)

    close()

    if (typeof onScenarioCreated === 'function') {
      await onScenarioCreated({ code, cleanName, icon, prompt })
    }
  }

  if (elements.submitCreateScenarioBtn) {
    elements.submitCreateScenarioBtn.addEventListener('click', submitCreateScenario)
  }

  function handleScenarioModalKeydown(e) {
    if (e.isComposing || e.keyCode === 229) return
    if (e.key === 'Enter') {
      if (e.target === elements.cancelScenarioModalBtn || e.target === elements.closeScenarioModalBtn) return
      const isTextarea = e.target?.tagName?.toLowerCase() === 'textarea'
      if (isTextarea && e.shiftKey && !e.metaKey && !e.ctrlKey) return
      e.preventDefault()
      e.stopPropagation()
      submitCreateScenario()
    } else if (e.key === 'Escape') {
      e.preventDefault()
      e.stopPropagation()
      close()
    }
  }

  elements.addScenarioModal?.addEventListener?.('keydown', handleScenarioModalKeydown)
  elements.newScenarioNameInput?.addEventListener?.('keydown', handleScenarioModalKeydown)
  elements.newScenarioPromptInput?.addEventListener?.('keydown', handleScenarioModalKeydown)

  return {
    open,
    close,
  }
}
