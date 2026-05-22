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
  const { state, elements } = ctx

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

  function closeLearningConfigModal() {
    hideModal(elements.learningConfigModal)
  }

  return {
    renderLearningConfigSummary,
    openLearningConfigModal,
    closeLearningConfigModal,
  }
}
