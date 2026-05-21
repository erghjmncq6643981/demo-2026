import { sameId } from '/src/shared/ids.js'
import { escapeHtml } from '/src/shared/text.js'
import { renderModelSelect, renderProviderSelect } from '/src/features/profile/provider.js'

export function createAgentProfileFeature(ctx) {
  const { state, elements, request, setLoading, toast, logEvent, confirmAction, confirmDelete, setConnection, providerCatalog, renderLearningConfigSummary } = ctx

  function loadAgents() {
    if (state.preview) {
      renderAgentConfigs()
      setConnection(true)
      renderLearningAgentOptions()
      renderLearningConfigSummary?.()
      return Promise.resolve()
    }
    return request('/api/v1/ai/agents?enabledOnly=false')
      .then((agents) => {
        state.agentConfigs = Array.isArray(agents) ? agents : []
        renderAgentConfigs()
        renderLearningAgentOptions()
        renderLearningConfigSummary?.()
        if (![...elements.agentSelect.options].some((item) => item.value === state.lastAgentCode)) {
          state.lastAgentCode = elements.agentSelect.value || 'english_vocabulary'
        }
        localStorage.setItem('learning.lastAgentCode', state.lastAgentCode)
        setConnection(true)
      })
      .catch((error) => {
        setConnection(false)
        renderAgentConfigs()
        renderLearningAgentOptions()
        renderLearningConfigSummary?.()
        logEvent('error', 'Agent 加载失败', error.message)
      })
  }

  function renderLearningAgentOptions() {
    if (!elements.agentSelect) return
    const previous = elements.agentSelect.value || state.lastAgentCode || 'english_vocabulary'
    const agents = state.agentConfigs?.length
      ? state.agentConfigs.filter((item) => item.enabled !== false && !item.deleted)
      : [{ code: 'english_vocabulary', name: 'English Vocabulary', enabled: true }]
    elements.agentSelect.innerHTML = ''
    for (const agent of agents) {
      const option = document.createElement('option')
      option.value = agent.code
      option.textContent = `${agent.name || agent.code} (${agent.code})`
      if (agent.enabled === false) {
        option.textContent += ' · 停用'
      }
      elements.agentSelect.appendChild(option)
    }
    elements.agentSelect.value = agents.some((item) => item.code === previous) ? previous : agents[0]?.code || 'english_vocabulary'
    state.lastAgentCode = elements.agentSelect.value || previous
    localStorage.setItem('learning.lastAgentCode', state.lastAgentCode)
    renderLearningConfigSummary?.()
  }

  function renderAgentProviderOptions(selectedProvider = '') {
    renderProviderSelect(elements.agentModelProviderInput, providerCatalog, selectedProvider)
  }

  function syncAgentModelProviderDefaults(options = {}) {
    if (!elements.agentModelProviderInput || !elements.agentModelNameInput) return
    const provider = elements.agentModelProviderInput.value || 'deepseek'
    renderModelSelect(elements.agentModelNameInput, providerCatalog, provider, options)
  }

  function renderAgentConfigs() {
    if (!elements.agentConfigList) return
    const list = state.agentConfigs.filter((item) => !item.deleted)
    if (!list.length) {
      elements.agentConfigList.className = 'model-list empty'
      elements.agentConfigList.textContent = '暂无学习 Agent'
      return
    }
    const aliveCount = list.length
    elements.agentConfigList.className = 'model-list'
    elements.agentConfigList.innerHTML = list
      .map(
        (item) => `
          <div class="model-item agent-item ${item.enabled ? '' : 'disabled'}">
            <div>
              <div class="model-title-line">
                <strong>${escapeHtml(item.name)}</strong>
                <span class="mini-pill ${item.enabled ? 'ok' : ''}">${item.enabled ? '启用' : '停用'}</span>
                <span class="mini-pill">${escapeHtml(item.code)}</span>
              </div>
              <p>${escapeHtml(item.modelProvider || '')} · ${escapeHtml(item.modelName || '')}</p>
              <small>类型 ${escapeHtml(item.type || '')} · 排序 ${item.sequence ?? 0}</small>
            </div>
            <div class="row-actions">
              <button class="icon-action-button" type="button" data-agent-edit="${escapeHtml(item.id)}" title="修改学习 Agent" aria-label="修改学习 Agent">✎</button>
              <button class="icon-action-button" type="button" data-agent-toggle="${escapeHtml(item.id)}" title="${item.enabled ? '停用学习 Agent' : '启用学习 Agent'}" aria-label="${item.enabled ? '停用学习 Agent' : '启用学习 Agent'}">${item.enabled ? '⏸' : '▶'}</button>
              <button class="icon-action-button" type="button" data-agent-clone="${escapeHtml(item.id)}" title="复制学习 Agent" aria-label="复制学习 Agent">⧉</button>
              <button class="danger-icon-button" type="button" data-agent-delete="${escapeHtml(item.id)}" title="${aliveCount <= 1 ? '至少保留一个学习 Agent' : '删除学习 Agent'}" aria-label="${aliveCount <= 1 ? '至少保留一个学习 Agent' : '删除学习 Agent'}" ${aliveCount <= 1 ? 'disabled' : ''}>×</button>
            </div>
          </div>
        `,
      )
      .join('')
    elements.agentConfigList.querySelectorAll('[data-agent-edit]').forEach((button) => {
      button.addEventListener('click', () => openAgentModal(button.getAttribute('data-agent-edit')))
    })
    elements.agentConfigList.querySelectorAll('[data-agent-delete]').forEach((button) => {
      button.addEventListener('click', () => deleteAgentConfig(button.getAttribute('data-agent-delete')))
    })
    elements.agentConfigList.querySelectorAll('[data-agent-toggle]').forEach((button) => {
      button.addEventListener('click', () => toggleAgentEnabled(button.getAttribute('data-agent-toggle')))
    })
    elements.agentConfigList.querySelectorAll('[data-agent-clone]').forEach((button) => {
      button.addEventListener('click', () => cloneAgentConfig(button.getAttribute('data-agent-clone')))
    })
  }

  function openAgentModal(id = null) {
    renderAgentProviderOptions()
    if (id) {
      fillAgentForm(state.agentConfigs.find((item) => sameId(item.id, id)))
      elements.agentModalTitle.textContent = '修改学习 Agent'
    } else {
      resetAgentForm({ keepModalOpen: true })
      elements.agentModalTitle.textContent = '新增学习Agent'
    }
    elements.agentModal.classList.remove('hidden')
  }

  function closeAgentModal() {
    elements.agentModal?.classList.add('hidden')
  }

  function fillAgentForm(agent) {
    state.currentAgentEditId = agent?.id || null
    elements.agentNameInput.value = agent?.name || ''
    elements.agentCodeInput.value = agent?.code || ''
    elements.agentTypeInput.value = agent?.type || 'chat'
    elements.agentIconInput.value = agent?.icon || ''
    renderAgentProviderOptions(agent?.modelProvider || 'deepseek')
    syncAgentModelProviderDefaults({ keepUnknownModel: true, modelName: agent?.modelName || '' })
    elements.agentModelProviderInput.value = agent?.modelProvider || 'deepseek'
    syncAgentModelProviderDefaults({ keepUnknownModel: true, modelName: agent?.modelName || '' })
    elements.agentModelNameInput.value = agent?.modelName || ''
    elements.agentSequenceInput.value = agent?.sequence ?? 0
    elements.agentTemperatureInput.value = agent?.temperature ?? ''
    elements.agentMaxTokensInput.value = agent?.maxTokens ?? ''
    elements.agentDescriptionInput.value = agent?.description || ''
    elements.agentSystemPromptInput.value = agent?.systemPrompt || ''
    elements.agentConcisePromptInput.value = agent?.concisePrompt || ''
    elements.agentWelcomeMessageInput.value = agent?.welcomeMessage || ''
    elements.agentPresetCommandsInput.value = agent?.presetCommands || ''
  }

  function resetAgentForm(options = {}) {
    state.currentAgentEditId = null
    renderAgentProviderOptions('deepseek')
    syncAgentModelProviderDefaults()
    elements.agentNameInput.value = ''
    elements.agentCodeInput.value = ''
    elements.agentTypeInput.value = 'chat'
    elements.agentIconInput.value = ''
    elements.agentModelProviderInput.value = 'deepseek'
    syncAgentModelProviderDefaults()
    elements.agentSequenceInput.value = '0'
    elements.agentTemperatureInput.value = ''
    elements.agentMaxTokensInput.value = ''
    elements.agentDescriptionInput.value = ''
    elements.agentSystemPromptInput.value = ''
    elements.agentConcisePromptInput.value = ''
    elements.agentWelcomeMessageInput.value = ''
    elements.agentPresetCommandsInput.value = ''
    if (!options.keepModalOpen) {
      closeAgentModal()
    }
  }

  function validatePresetCommands() {
    const raw = elements.agentPresetCommandsInput.value.trim()
    if (!raw) return true
    try {
      JSON.parse(raw)
      return true
    } catch {
      toast('预设指令 JSON 格式错误')
      return false
    }
  }

  async function saveAgentConfig() {
    const payload = {
      name: elements.agentNameInput.value.trim(),
      code: elements.agentCodeInput.value.trim(),
      type: elements.agentTypeInput.value.trim() || 'chat',
      icon: elements.agentIconInput.value.trim(),
      modelProvider: elements.agentModelProviderInput.value.trim(),
      modelName: elements.agentModelNameInput.value.trim(),
      sequence: Number(elements.agentSequenceInput.value || 0),
      temperature: elements.agentTemperatureInput.value.trim() === '' ? null : Number(elements.agentTemperatureInput.value),
      maxTokens: elements.agentMaxTokensInput.value.trim() === '' ? null : Number(elements.agentMaxTokensInput.value),
      description: elements.agentDescriptionInput.value.trim(),
      systemPrompt: elements.agentSystemPromptInput.value.trim(),
      concisePrompt: elements.agentConcisePromptInput.value.trim(),
      welcomeMessage: elements.agentWelcomeMessageInput.value.trim(),
      presetCommands: elements.agentPresetCommandsInput.value.trim(),
    }
    if (!payload.name || !payload.code || !payload.modelProvider || !payload.modelName || !validatePresetCommands()) {
      if (!payload.name || !payload.code || !payload.modelProvider || !payload.modelName) toast('请补全学习 Agent 配置')
      return
    }
    if (state.currentAgentEditId) {
      const confirmed = await confirmAction({
        title: '修改学习 Agent',
        message: `确认修改学习 Agent「${payload.name}」？后续学习请求会使用新的配置。`,
        acceptText: '确认修改',
      })
      if (!confirmed) return
    }
    setLoading(true)
    try {
      if (state.preview) {
        const id = state.currentAgentEditId || String(Date.now())
        const existingIndex = state.agentConfigs.findIndex((item) => sameId(item.id, id))
        const next = { ...payload, id, enabled: existingIndex >= 0 ? state.agentConfigs[existingIndex].enabled !== false : true }
        if (existingIndex >= 0) state.agentConfigs.splice(existingIndex, 1, next)
        else state.agentConfigs.push(next)
        renderAgentConfigs()
        renderLearningAgentOptions()
        closeAgentModal()
        toast('设计预览：学习 Agent 已保存')
        return
      }
      const path = state.currentAgentEditId ? `/api/v1/ai/agents/${encodeURIComponent(state.currentAgentEditId)}` : '/api/v1/ai/agents'
      const method = state.currentAgentEditId ? 'PUT' : 'POST'
      await request(path, { method, body: JSON.stringify(payload) })
      await loadAgents()
      closeAgentModal()
      toast('学习 Agent 已保存')
    } catch (error) {
      logEvent('error', '学习 Agent 保存失败', error.message)
      toast(`学习 Agent 保存失败：${error.message}`)
    } finally {
      setLoading(false)
    }
  }

  async function toggleAgentEnabled(id) {
    const agent = state.agentConfigs.find((item) => sameId(item.id, id))
    if (!agent) return
    const targetEnabled = !agent.enabled
    const confirmed = await confirmAction({
      title: targetEnabled ? '启用学习 Agent' : '停用学习 Agent',
      message: `确认${targetEnabled ? '启用' : '停用'}学习 Agent「${agent.name}」？`,
      acceptText: targetEnabled ? '确认启用' : '确认停用',
    })
    if (!confirmed) return
    if (state.preview) {
      agent.enabled = targetEnabled
      renderAgentConfigs()
      renderLearningAgentOptions()
      renderLearningConfigSummary?.()
      return
    }
    await request(`/api/v1/ai/agents/${encodeURIComponent(id)}/${targetEnabled ? 'enable' : 'disable'}`, { method: 'POST' })
    await loadAgents()
    renderLearningConfigSummary?.()
  }

  async function deleteAgentConfig(id) {
    const item = state.agentConfigs.find((agent) => sameId(agent.id, id))
    if (!item) return
    const aliveCount = state.agentConfigs.filter((agent) => !agent.deleted).length
    if (aliveCount <= 1) {
      toast('至少保留一个学习 Agent')
      return
    }
    const confirmed = await confirmDelete({
      title: '删除学习 Agent',
      message: `确认删除学习 Agent「${item.name}」？删除后无法在列表中继续使用。`,
    })
    if (!confirmed) return
    if (state.preview) {
      state.agentConfigs = state.agentConfigs.filter((agent) => !sameId(agent.id, id))
      renderAgentConfigs()
      renderLearningAgentOptions()
      renderLearningConfigSummary?.()
      toast('设计预览：学习 Agent 已删除')
      return
    }
    await request(`/api/v1/ai/agents/${encodeURIComponent(id)}`, { method: 'DELETE' })
    await loadAgents()
    toast('学习 Agent 已删除')
  }

  async function cloneAgentConfig(id) {
    if (state.preview) {
      const agent = state.agentConfigs.find((item) => sameId(item.id, id))
      if (!agent) return
      const clone = { ...agent, id: String(Date.now()), name: `${agent.name} 副本`, code: `${agent.code}-${Date.now()}` }
      state.agentConfigs.push(clone)
      renderAgentConfigs()
      renderLearningAgentOptions()
      renderLearningConfigSummary?.()
      toast('设计预览：学习 Agent 已复制')
      return
    }
    await request(`/api/v1/ai/agents/${encodeURIComponent(id)}/clone`, { method: 'POST' })
    await loadAgents()
  }

  async function changeLearningAgent() {
    const nextCode = elements.agentSelect?.value || ''
    const previousCode = state.lastAgentCode || ''
    if (previousCode && previousCode !== nextCode) {
      const confirmed = await confirmAction({
        title: '修改学习 Agent',
        message: `确认将学习 Agent 从「${previousCode}」切换为「${nextCode}」？后续学习请求会使用新的 Agent。`,
        acceptText: '确认修改',
      })
      if (!confirmed) {
        elements.agentSelect.value = previousCode
        return
      }
    }
    state.lastAgentCode = nextCode
    localStorage.setItem('learning.lastAgentCode', nextCode)
    renderLearningConfigSummary?.()
    logEvent('ai', '修改学习 Agent', nextCode)
  }

  return {
    loadAgents,
    renderLearningAgentOptions,
    renderAgentProviderOptions,
    syncAgentModelProviderDefaults,
    renderAgentConfigs,
    openAgentModal,
    closeAgentModal,
    fillAgentForm,
    resetAgentForm,
    saveAgentConfig,
    toggleAgentEnabled,
    deleteAgentConfig,
    cloneAgentConfig,
    changeLearningAgent,
  }
}
