import { sameId } from '/src/shared/ids.js'
import { hideModal, showModal } from '/src/shared/modal.js'
import { escapeHtml } from '/src/shared/text.js'
import { providerDefaults, renderModelSelect, renderProviderSelect } from '/src/features/ai/model/provider-catalog.js'

export function createModelManagementFeature(ctx) {
  const { state, elements, request, setLoading, toast, logEvent, confirmAction, confirmDelete, renderAgentConfigs } = ctx
  const testingModelIds = new Set()

  function loadModelConfigs() {
    if (state.preview) {
      renderModelConfigs()
      renderStudyModelOptions()
      return Promise.resolve()
    }
    const path = state.user?.roleCode === 'ADMIN' ? '/api/v1/ai/model-configs' : '/api/v1/ai/model-configs/available'
    return request(path)
      .then((configs) => {
        state.modelConfigs = Array.isArray(configs) ? configs : []
        renderModelConfigs()
        renderStudyModelOptions()
        renderAgentConfigs?.()
      })
      .catch((error) => {
        logEvent('error', '模型配置加载失败', error.message)
        renderStudyModelOptions()
      })
  }

  function renderStudyModelOptions() {
    const selects = [elements.studyModelSelect, elements.articleModelSelect].filter(Boolean)
    if (!selects.length) return
    selects.forEach((select) => {
      select.innerHTML = '<option value="">使用 Agent 绑定模型</option>'
    })
    const enabled = state.modelConfigs.filter((item) => item.enabled)
    if (!enabled.length) {
      selects.forEach((select) => {
        select.innerHTML = '<option value="">使用 Agent 绑定模型</option>'
      })
      return
    }
    for (const select of selects) {
      for (const item of enabled) {
        const option = document.createElement('option')
        option.value = String(item.id)
        option.textContent = `${item.name} · ${item.modelDisplayName || item.modelName}${item.isDefault ? ' · 默认' : ''}`
        select.appendChild(option)
      }
      select.value = ''
    }
  }

  function renderProviderOptions(selectedProvider = '') {
    return renderProviderSelect(elements.modelProviderInput, state.modelConfigs, selectedProvider)
  }

  function syncModelProviderDefaults(options = {}) {
    const provider = elements.modelProviderInput.value || renderProviderOptions()
    const config = providerDefaults(state.modelConfigs, provider)
    renderModelSelect(elements.modelModelNameInput, state.modelConfigs, provider, options)
    if (!options.keepValues) {
      elements.modelBaseUrlInput.value = config.baseUrl || ''
      elements.modelChatPathInput.value = config.chatPath || '/chat/completions'
    }
  }

  function openModelModal(id = null) {
    renderProviderOptions()
    if (id) {
      editModelConfig(id, { openModal: false })
      elements.modelModalTitle.textContent = '编辑模型'
    } else {
      resetModelForm({ keepModalOpen: true })
      elements.modelModalTitle.textContent = '新增模型'
    }
    showModal(elements.modelConfigModal)
  }

  function closeModelModal() {
    hideModal(elements.modelConfigModal)
  }

  function renderModelConfigs() {
    renderStudyModelOptions()
    if (!elements.modelConfigList) return
    const list = state.modelConfigs
    if (!list.length) {
      elements.modelConfigList.className = 'model-list empty'
      elements.modelConfigList.textContent = '暂无模型配置'
      return
    }
    elements.modelConfigList.className = 'model-list'
    elements.modelConfigList.innerHTML = list
      .map((item) => {
        const boundAgents = (state.agentConfigs || []).filter((agent) => !agent.deleted && sameId(agent.modelConfigId, item.id))
        const boundAgentNames = boundAgents.length ? boundAgents.map((agent) => agent.name) : (item.boundAgentNames || [])
        const boundAgentCount = boundAgents.length || Number(item.boundAgentCount || 0)
        const testing = testingModelIds.has(String(item.id))
        const testResult = item.connectionTestResult
        return `
          <div class="model-item ${item.enabled ? '' : 'disabled'}">
            <div>
              <div class="model-title-line">
                <strong>${escapeHtml(item.name)}</strong>
                <span class="mini-pill ${item.enabled ? 'ok' : ''}">${item.enabled ? '启用' : '停用'}</span>
                ${item.isDefault ? '<span class="mini-pill ok">默认</span>' : ''}
              </div>
              <p>${escapeHtml(item.providerName || item.provider)} · ${escapeHtml(item.modelDisplayName || item.modelName)} · ${escapeHtml(item.baseUrl || '')}</p>
              <small>${item.supported === false ? '历史模型：不可用于新任务' : `${escapeHtml(item.apiProtocol || '-')} · 上下文 ${Number(item.contextWindowTokens || 0).toLocaleString()} Token`} · 优先级 ${item.sequence ?? 0} · ${escapeHtml(item.apiKeyMasked || '')}</small>
              ${boundAgentCount > 0 ? `<small>关联 Agent：${escapeHtml(boundAgentNames.join('、'))}</small>` : '<small>暂未关联 Agent</small>'}
              <div class="model-usage-row">
                <span>调用 <strong>${Number(item.callCount || 0).toLocaleString()}</strong></span>
                <span>成功 <strong>${Number(item.successCount || 0).toLocaleString()}</strong></span>
                <span>失败 <strong>${Number(item.failedCount || 0).toLocaleString()}</strong></span>
                <span>Token <strong>${Number(item.totalTokens || 0).toLocaleString()}</strong></span>
                <span>平均 <strong>${Number(item.averageLatencyMs || 0)} ms</strong></span>
              </div>
              ${testResult ? `
                <div class="model-test-result ${testResult.success ? 'success' : 'failed'}" role="status">
                  <strong>${testResult.success ? '连接有效' : '连接失败'}</strong>
                  <span>${escapeHtml(testResult.message || '')}${testResult.latencyMs == null ? '' : ` · ${Number(testResult.latencyMs)} ms`}</span>
                  ${testResult.responsePreview ? `<small>响应：${escapeHtml(testResult.responsePreview)}</small>` : ''}
                </div>
              ` : ''}
            </div>
            <div class="row-actions">
              <button class="secondary-button compact" type="button" data-model-test="${escapeHtml(item.id)}" ${testing || item.supported === false ? 'disabled' : ''}>${testing ? '测试中…' : '测试连接'}</button>
              <button class="icon-action-button" type="button" data-model-toggle="${escapeHtml(item.id)}" title="${boundAgentCount > 0 && item.enabled ? '请先更换关联 Agent 的模型' : item.enabled ? '停用模型' : '启用模型'}" aria-label="${item.enabled ? '停用模型' : '启用模型'}" ${(item.supported === false && !item.enabled) || (boundAgentCount > 0 && item.enabled) ? 'disabled' : ''}>${item.enabled ? '⏸' : '▶'}</button>
              <button class="icon-action-button" type="button" data-model-edit="${escapeHtml(item.id)}" title="编辑模型" aria-label="编辑模型">✎</button>
              <button class="danger-icon-button" type="button" data-model-delete="${escapeHtml(item.id)}" title="${boundAgentCount > 0 ? '请先更换关联 Agent 的模型' : '删除模型'}" ${boundAgentCount > 0 ? 'disabled' : ''}>×</button>
            </div>
          </div>
        `
      })
      .join('')
    elements.modelConfigList.querySelectorAll('[data-model-edit]').forEach((button) => {
      button.addEventListener('click', () => openModelModal(button.getAttribute('data-model-edit')))
    })
    elements.modelConfigList.querySelectorAll('[data-model-test]').forEach((button) => {
      button.addEventListener('click', () => testModelConfig(button.getAttribute('data-model-test')))
    })
    elements.modelConfigList.querySelectorAll('[data-model-toggle]').forEach((button) => {
      button.addEventListener('click', () => toggleModelConfig(button.getAttribute('data-model-toggle')))
    })
    elements.modelConfigList.querySelectorAll('[data-model-delete]').forEach((button) => {
      button.addEventListener('click', () => deleteModelConfig(button.getAttribute('data-model-delete')))
    })
  }

  function editModelConfig(id) {
    const item = state.modelConfigs.find((model) => sameId(model.id, id))
    if (!item) return
    renderProviderOptions(item.provider || '')
    state.currentModelEditId = item.id
    elements.modelNameInput.value = item.name || ''
    elements.modelProviderInput.value = item.provider || ''
    syncModelProviderDefaults({ keepValues: true, keepUnknownModel: true, modelName: item.modelName || '' })
    elements.modelModelNameInput.value = item.modelName || ''
    elements.modelBaseUrlInput.value = item.baseUrl || ''
    elements.modelChatPathInput.value = item.chatPath || '/chat/completions'
    elements.modelApiKeyInput.value = ''
    elements.modelApiKeyInput.placeholder = item.apiKeyMasked || 'API KEY'
    elements.modelSequenceInput.value = item.sequence ?? 0
    elements.modelDefaultInput.checked = Boolean(item.isDefault)
    elements.modelEnabledInput.checked = Boolean(item.enabled)
    syncModelToggleButtons()
  }

  function resetModelForm(options = {}) {
    state.currentModelEditId = null
    const provider = renderProviderOptions('')
    elements.modelNameInput.value = ''
    elements.modelProviderInput.value = provider
    syncModelProviderDefaults()
    elements.modelApiKeyInput.value = ''
    elements.modelApiKeyInput.placeholder = 'API KEY'
    elements.modelSequenceInput.value = '0'
    elements.modelDefaultInput.checked = false
    elements.modelEnabledInput.checked = true
    syncModelToggleButtons()
    if (!options.keepModalOpen) {
      closeModelModal()
    }
  }

  function syncModelToggleButtons() {
    syncToggleButton(elements.modelDefaultToggleBtn, elements.modelDefaultInput, '已设默认', '默认模型')
    syncToggleButton(elements.modelEnabledToggleBtn, elements.modelEnabledInput, '已启用', '已停用')
  }

  function syncToggleButton(button, input, activeLabel, inactiveLabel) {
    if (!button || !input) return
    const active = Boolean(input.checked)
    button.classList.toggle('active', active)
    button.setAttribute('aria-pressed', String(active))
    const label = button.querySelector('span')
    if (label) label.textContent = active ? activeLabel : inactiveLabel
  }

  function toggleModelFlag(input) {
    if (!input) return
    input.checked = !input.checked
    syncModelToggleButtons()
  }

  async function saveModelConfig(options = {}) {
    const payload = {
      name: elements.modelNameInput.value.trim(),
      provider: elements.modelProviderInput.value.trim(),
      modelName: elements.modelModelNameInput.value.trim(),
      baseUrl: elements.modelBaseUrlInput.value.trim(),
      chatPath: elements.modelChatPathInput.value.trim() || '/chat/completions',
      apiKey: elements.modelApiKeyInput.value.trim(),
      sequence: Number(elements.modelSequenceInput.value || 0),
      isDefault: elements.modelDefaultInput.checked,
      enabled: elements.modelEnabledInput.checked,
    }
    if (!payload.name || !payload.provider || !payload.modelName || !payload.baseUrl || (!payload.apiKey && !state.currentModelEditId)) {
      toast('请补全模型配置')
      return
    }
    if (state.currentModelEditId) {
      const confirmed = await confirmAction({
        title: '修改模型配置',
        message: `确认修改模型「${payload.name}」？保存后学习页将使用新的模型配置。`,
        acceptText: '确认修改',
      })
      if (!confirmed) return
    }
    setLoading(true)
    try {
      if (state.preview) {
        const id = state.currentModelEditId || String(Date.now())
        const existingIndex = state.modelConfigs.findIndex((item) => sameId(item.id, id))
        const next = { ...payload, id, apiKeyMasked: payload.apiKey ? `${payload.apiKey.slice(0, 6)}****` : 'sk-****' }
        if (next.isDefault) state.modelConfigs.forEach((item) => (item.isDefault = false))
        if (existingIndex >= 0) state.modelConfigs.splice(existingIndex, 1, next)
        else state.modelConfigs.push(next)
        renderModelConfigs()
        resetModelForm()
        if (options.testAfterSave) {
          await testModelConfig(id)
        } else {
          toast('设计预览：模型配置已保存')
        }
        return
      }
      const path = state.currentModelEditId ? `/api/v1/ai/model-configs/${encodeURIComponent(state.currentModelEditId)}` : '/api/v1/ai/model-configs'
      const method = state.currentModelEditId ? 'PUT' : 'POST'
      const saved = await request(path, { method, body: JSON.stringify(payload) })
      await loadModelConfigs()
      resetModelForm()
      if (options.testAfterSave && saved?.id) {
        await testModelConfig(saved.id)
      } else {
        toast('模型配置已保存')
      }
    } catch (error) {
      logEvent('error', '模型配置保存失败', error.message)
      toast(`模型配置保存失败：${error.message}`)
    } finally {
      setLoading(false)
    }
  }

  async function testModelConfig(id) {
    const item = state.modelConfigs.find((model) => sameId(model.id, id))
    if (!item || testingModelIds.has(String(item.id))) return
    const key = String(item.id)
    testingModelIds.add(key)
    item.connectionTestResult = null
    renderModelConfigs()
    try {
      const result = state.preview
        ? {
            success: true,
            provider: item.provider,
            modelName: item.modelName,
            latencyMs: 428,
            message: '连接成功，模型已返回有效响应',
            responsePreview: 'OK',
          }
        : await request(`/api/v1/ai/model-configs/${encodeURIComponent(item.id)}/test`, { method: 'POST' })
      item.connectionTestResult = result || { success: false, message: '未收到测试结果' }
      if (item.connectionTestResult.success) {
        toast(`连接成功 · ${item.modelDisplayName || item.modelName} · ${Number(item.connectionTestResult.latencyMs || 0)} ms`)
      } else {
        toast(`连接失败：${item.connectionTestResult.message || '请检查模型配置'}`)
      }
    } catch (error) {
      item.connectionTestResult = { success: false, message: error.message || '模型连接测试失败' }
      logEvent('error', '模型连接测试失败', `${item.name}：${error.message}`)
      toast(`连接失败：${error.message}`)
    } finally {
      testingModelIds.delete(key)
      renderModelConfigs()
    }
  }

  async function toggleModelConfig(id) {
    const item = state.modelConfigs.find((model) => sameId(model.id, id))
    if (!item) return
    const targetEnabled = !item.enabled
    const confirmed = await confirmAction({
      title: targetEnabled ? '启用模型' : '停用模型',
      message: `确认${targetEnabled ? '启用' : '停用'}模型「${item.name}」？${targetEnabled ? '启用后学习页可以选择该模型。' : '停用后学习页将不能选择该模型。'}`,
      acceptText: targetEnabled ? '确认启用' : '确认停用',
    })
    if (!confirmed) return
    if (state.preview) {
      item.enabled = targetEnabled
      renderModelConfigs()
      return
    }
    await request(`/api/v1/ai/model-configs/${encodeURIComponent(id)}/${item.enabled ? 'disable' : 'enable'}`, { method: 'POST' })
    await loadModelConfigs()
  }

  async function setDefaultModelConfig(id) {
    const item = state.modelConfigs.find((model) => sameId(model.id, id))
    if (!item) return
    if (state.preview) {
      state.modelConfigs.forEach((model) => (model.isDefault = sameId(model.id, id)))
      renderModelConfigs()
      return
    }
    await request(`/api/v1/ai/model-configs/${encodeURIComponent(id)}/priority`, {
      method: 'POST',
      body: JSON.stringify({ sequence: item.sequence ?? 0, isDefault: true }),
    })
    await loadModelConfigs()
  }

  async function deleteModelConfig(id) {
    const item = state.modelConfigs.find((model) => sameId(model.id, id))
    if (!item) return
    const confirmed = await confirmDelete({
      title: '删除模型配置',
      message: `确认删除模型配置「${item.name}」？删除后学习页将不能再选择这个模型。`,
    })
    if (!confirmed) return
    if (state.preview) {
      state.modelConfigs = state.modelConfigs.filter((model) => !sameId(model.id, id))
      renderModelConfigs()
      toast('设计预览：模型配置已删除')
      return
    }
    await request(`/api/v1/ai/model-configs/${encodeURIComponent(id)}`, { method: 'DELETE' })
    await loadModelConfigs()
    toast('模型配置已删除')
  }


  return {
    loadModelConfigs,
    renderStudyModelOptions,
    renderProviderOptions,
    syncModelProviderDefaults,
    openModelModal,
    closeModelModal,
    renderModelConfigs,
    editModelConfig,
    resetModelForm,
    syncModelToggleButtons,
    syncToggleButton,
    toggleModelFlag,
    saveModelConfig,
    testModelConfig,
    toggleModelConfig,
    setDefaultModelConfig,
    deleteModelConfig,
  }
}
