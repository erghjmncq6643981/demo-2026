/**
 * 知识反刍模块 - 新建场景交互弹窗
 * 负责场景名称、提示词收集、图标自适应提取及向外派发场景创建事件。
 */

import { showModal, hideModal } from '/src/shared/modal.js'

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

  function close() {
    if (!elements.addScenarioModal) return
    hideModal(elements.addScenarioModal)
  }

  if (elements.toolbarAddScenarioBtn) {
    elements.toolbarAddScenarioBtn.addEventListener('click', open)
  }
  if (elements.closeScenarioModalBtn) {
    elements.closeScenarioModalBtn.addEventListener('click', close)
  }
  if (elements.cancelScenarioModalBtn) {
    elements.cancelScenarioModalBtn.addEventListener('click', close)
  }
  if (elements.addScenarioModal) {
    elements.addScenarioModal.addEventListener('click', (e) => {
      if (e.target === elements.addScenarioModal) close()
    })
  }

  if (elements.submitCreateScenarioBtn) {
    elements.submitCreateScenarioBtn.addEventListener('click', async () => {
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
    })
  }

  return {
    open,
    close,
  }
}
