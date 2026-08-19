import { hideModal, showModal } from '/src/shared/modal.js'
import { escapeHtml } from '/src/shared/text.js'

function optionText(select) {
  return select?.selectedOptions?.[0]?.textContent || ''
}

function currentAgent(state, elements) {
  const code = elements.agentSelect?.value || state.lastAgentCode || ''
  return state.agentConfigs?.find((agent) => agent.code === code) || { code, name: optionText(elements.agentSelect) || code }
}

function currentTemplate(state, elements) {
  const code = elements.templateSelect?.value || state.lastTemplateCode || ''
  return (
    state.promptTemplates?.find((template) => template.code === code) ||
    (state.currentTemplate?.code === code ? state.currentTemplate : null) ||
    { code, name: optionText(elements.templateSelect) || code }
  )
}

function voiceLabel(voiceType) {
  return voiceType === 'uk' ? '英音' : '美音'
}

export function createLearningConfigProfileFeature(ctx) {
  const { state, elements, request, toast, logEvent } = ctx

  function renderLearningConfigSummary() {
    if (!elements.learningConfigList) return
    const agent = currentAgent(state, elements)
    const template = currentTemplate(state, elements)
    const speech = state.speechSettings || {}
    const sentenceRate = Number(speech.sentenceRate || 0.78).toFixed(2)
    const sentencePitch = Number(speech.sentencePitch || 1).toFixed(2)
    const sentenceVoice = speech.sentenceVoiceName || '自动选择英语音色'

    elements.learningConfigList.className = 'model-list'
    elements.learningConfigList.innerHTML = `
      <div class="model-item learning-config-item">
        <div>
          <div class="model-title-line">
            <strong>学习卡生成与朗读</strong>
            <span class="mini-pill ok">${escapeHtml(voiceLabel(speech.voiceType))}</span>
            <span class="mini-pill">${escapeHtml(template.code || '未选择模板')}</span>
          </div>
          <p>${escapeHtml(agent.name || agent.code || '未选择学习 Agent')} · ${escapeHtml(template.name || template.code || '未选择模板')}</p>
          <small>句子朗读 ${escapeHtml(sentenceVoice)} · 语速 ${sentenceRate}x · 音调 ${sentencePitch}</small>
        </div>
        <div class="row-actions">
          <button class="icon-action-button" type="button" data-learning-config-edit title="修改学习配置" aria-label="修改学习配置">✎</button>
        </div>
      </div>
    `
    elements.learningConfigList.querySelector('[data-learning-config-edit]')?.addEventListener('click', openLearningConfigModal)
  }

  function openLearningConfigModal() {
    renderLearningConfigSummary()
    showModal(elements.learningConfigModal)
  }

  async function loadLearningSettings() {
    if (state.preview || !state.token) {
      renderLearningConfigSummary()
      return
    }
    try {
      const settings = await request('/api/v1/learning/preferences/learning-settings')
      if (settings?.agentCode) {
        state.lastAgentCode = settings.agentCode
        if ([...elements.agentSelect.options].some((option) => option.value === settings.agentCode)) {
          elements.agentSelect.value = settings.agentCode
        }
      }
      if (settings?.templateCode) {
        state.lastTemplateCode = settings.templateCode
        if ([...elements.templateSelect.options].some((option) => option.value === settings.templateCode)) {
          elements.templateSelect.value = settings.templateCode
        }
      }
      renderLearningConfigSummary()
    } catch (error) {
      logEvent('error', '学习设置加载失败', error.message)
    }
  }

  async function saveLearningSettings() {
    const agentCode = elements.agentSelect?.value || state.lastAgentCode || ''
    const templateCode = elements.templateSelect?.value || state.lastTemplateCode || ''
    if (!agentCode || !templateCode) {
      toast('请选择学习 Agent 和模板')
      return false
    }
    state.lastAgentCode = agentCode
    state.lastTemplateCode = templateCode
    localStorage.setItem('learning.lastAgentCode', agentCode)
    localStorage.setItem('learning.lastTemplateCode', templateCode)
    if (state.preview || !state.token) {
      renderLearningConfigSummary()
      toast('设计预览：学习设置已保存')
      return true
    }
    try {
      const settings = await request('/api/v1/learning/preferences/learning-settings', {
        method: 'PUT',
        body: JSON.stringify({ agentCode, templateCode }),
      })
      if (settings?.agentCode) state.lastAgentCode = settings.agentCode
      if (settings?.templateCode) state.lastTemplateCode = settings.templateCode
      renderLearningConfigSummary()
      toast('学习设置已保存')
      return true
    } catch (error) {
      logEvent('error', '学习设置保存失败', error.message)
      toast(`学习设置保存失败：${error.message}`)
      return false
    }
  }

  function closeLearningConfigModal() {
    hideModal(elements.learningConfigModal)
  }

  return {
    renderLearningConfigSummary,
    loadLearningSettings,
    saveLearningSettings,
    openLearningConfigModal,
    closeLearningConfigModal,
  }
}
