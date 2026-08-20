import { sameId } from '/src/shared/ids.js'
import { hideModal, showModal } from '/src/shared/modal.js'
import { escapeHtml, escapeRegExp } from '/src/shared/text.js'

export function createPromptTemplateManagementFeature(ctx) {
  const { state, elements, request, setLoading, toast, logEvent, confirmAction, confirmDelete, renderLearningConfigSummary } = ctx

  function loadPromptTemplates() {
    if (state.preview) {
      renderTemplateOptions()
      renderTemplateConfigs()
      renderLearningConfigSummary?.()
      return Promise.resolve()
    }
    return request('/api/v1/ai/prompt-templates?type=user&enabledOnly=false')
      .then((templates) => {
        state.promptTemplates = Array.isArray(templates) ? templates : []
        renderTemplateOptions()
        renderTemplateConfigs()
        renderLearningConfigSummary?.()
      })
      .catch((error) => {
        logEvent('error', '模板加载失败', error.message)
        renderTemplateOptions()
        renderTemplateConfigs()
        renderLearningConfigSummary?.()
      })
  }

  function renderTemplateOptions() {
    if (!elements.templateSelect) return
    const previous = elements.templateSelect.value || state.lastTemplateCode || 'english_vocab_card_json'
    const templates = state.promptTemplates.filter((item) => item.enabled !== false && !item.deleted).length
      ? state.promptTemplates.filter((item) => item.enabled !== false && !item.deleted)
      : [
          { code: 'english_vocab_card_json', name: '词汇卡片 JSON' },
          { code: 'english_vocab_quiz_json', name: '练习题 JSON' },
        ]
    elements.templateSelect.innerHTML = ''
    for (const template of templates) {
      const option = document.createElement('option')
      option.value = template.code
      option.textContent = `${template.name || template.code} (${template.code})`
      elements.templateSelect.appendChild(option)
    }
    elements.templateSelect.value = templates.some((item) => item.code === previous) ? previous : templates[0]?.code || ''
    renderSelectedTemplate()
    renderLearningConfigSummary?.()
  }

  function renderTemplateConfigs() {
    if (!elements.templateConfigList) return
    const list = state.promptTemplates.filter((item) => !item.deleted)
    if (!list.length) {
      elements.templateConfigList.className = 'model-list empty'
      elements.templateConfigList.textContent = '暂无学习 Agent 模板'
      return
    }
    const aliveCount = list.length
    elements.templateConfigList.className = 'model-list'
    elements.templateConfigList.innerHTML = list
      .map(
        (item) => `
          <div class="model-item template-item ${item.enabled ? '' : 'disabled'}">
            <div>
              <div class="model-title-line">
                <strong>${escapeHtml(item.name)}</strong>
                <span class="mini-pill ${item.enabled ? 'ok' : ''}">${item.enabled ? '启用' : '停用'}</span>
                <span class="mini-pill">${escapeHtml(item.code)}</span>
              </div>
              <p>${escapeHtml(item.type || '')} · ${escapeHtml(item.tags || '')}</p>
              <small>排序 ${item.sequence ?? 0} · 占位符 ${templatePlaceholders(item).length}</small>
            </div>
            <div class="row-actions">
              <button class="icon-action-button" type="button" data-template-edit="${escapeHtml(item.id)}" title="修改模板" aria-label="修改模板">✎</button>
              <button class="icon-action-button" type="button" data-template-clone="${escapeHtml(item.id)}" title="复制模板" aria-label="复制模板">⧉</button>
              <button class="danger-icon-button" type="button" data-template-delete="${escapeHtml(item.id)}" title="${aliveCount <= 1 ? '至少保留一个学习 Agent 模板' : '删除模板'}" aria-label="${aliveCount <= 1 ? '至少保留一个学习 Agent 模板' : '删除模板'}" ${aliveCount <= 1 ? 'disabled' : ''}>×</button>
            </div>
          </div>
        `,
      )
      .join('')
    elements.templateConfigList.querySelectorAll('[data-template-edit]').forEach((button) => {
      button.addEventListener('click', () => openTemplateModal(button.getAttribute('data-template-edit')))
    })
    elements.templateConfigList.querySelectorAll('[data-template-delete]').forEach((button) => {
      button.addEventListener('click', () => deletePromptTemplate(button.getAttribute('data-template-delete')))
    })
    elements.templateConfigList.querySelectorAll('[data-template-clone]').forEach((button) => {
      button.addEventListener('click', () => clonePromptTemplate(button.getAttribute('data-template-clone')))
    })
  }

  function openTemplateModal(id = null) {
    if (id) {
      fillTemplateForm(state.promptTemplates.find((item) => sameId(item.id, id)))
      elements.templateModalTitle.textContent = '修改学习 Agent 模板'
    } else {
      resetTemplateForm({ keepModalOpen: true })
      elements.templateModalTitle.textContent = '新增学习Agent模板'
    }
    showModal(elements.templateModal)
  }

  function closeTemplateModal() {
    hideModal(elements.templateModal)
  }

  function resetTemplateForm(options = {}) {
    state.currentTemplateEditId = null
    state.currentTemplate = null
    elements.templateNameInput.value = ''
    elements.templateCodeInput.value = ''
    elements.templateTypeInput.value = 'user'
    elements.templateSequenceInput.value = '0'
    elements.templateTagsInput.value = ''
    elements.templateDescriptionInput.value = ''
    elements.templateExampleInput.value = ''
    elements.templateExampleOutput.value = ''
    elements.templateContentInput.value = ''
    renderTemplatePlaceholders([])
    elements.templateValidationMessage.textContent = '模板内容需要包含声明的占位符。'
    if (!options.keepModalOpen) {
      closeTemplateModal()
    }
  }

  async function deletePromptTemplate(id) {
    const item = state.promptTemplates.find((template) => sameId(template.id, id))
    if (!item) return
    const aliveCount = state.promptTemplates.filter((template) => !template.deleted).length
    if (aliveCount <= 1) {
      toast('至少保留一个学习 Agent 模板')
      return
    }
    const confirmed = await confirmDelete({
      title: '删除学习 Agent 模板',
      message: `确认删除模板「${item.name}」？删除后无法继续用于学习卡生成。`,
    })
    if (!confirmed) return
    if (state.preview) {
      state.promptTemplates = state.promptTemplates.filter((template) => !sameId(template.id, id))
      renderTemplateConfigs()
      renderTemplateOptions()
      renderLearningConfigSummary?.()
      toast('设计预览：学习 Agent 模板已删除')
      return
    }
    await request(`/api/v1/ai/prompt-templates/${encodeURIComponent(id)}`, { method: 'DELETE' })
    await loadPromptTemplates()
    toast('学习 Agent 模板已删除')
  }

  async function clonePromptTemplate(id) {
    const item = state.promptTemplates.find((template) => sameId(template.id, id))
    if (!item) return
    if (state.preview) {
      const clone = { ...item, id: String(Date.now()), name: `${item.name} 副本`, code: `${item.code}-${Date.now()}` }
      state.promptTemplates.push(clone)
      renderTemplateConfigs()
      renderTemplateOptions()
      renderLearningConfigSummary?.()
      toast('设计预览：模板已复制')
      return
    }
    const createdId = await request(`/api/v1/ai/prompt-templates/${encodeURIComponent(id)}/clone`, { method: 'POST' })
    await loadPromptTemplates()
    if (createdId) {
      const clone = state.promptTemplates.find((template) => sameId(template.id, createdId))
      if (clone) {
        elements.templateSelect.value = clone.code || item.code
        fillTemplateForm(clone)
      }
    }
  }

  async function renderSelectedTemplate() {
    const code = elements.templateSelect?.value
    if (!code) return
    const previousCode = state.lastTemplateCode
    if (previousCode && previousCode !== code) {
      const confirmed = await confirmAction({
        title: '切换学习 Agent 模板',
        message: `确认从模板「${previousCode}」切换到「${code}」？未保存的模板编辑内容不会自动保存。`,
        acceptText: '确认切换',
      })
      if (!confirmed) {
        elements.templateSelect.value = previousCode
        return
      }
    }
    let template = state.promptTemplates.find((item) => item.code === code)
    if (!template && !state.preview) {
      try {
        template = await request(`/api/v1/ai/prompt-templates/code/${encodeURIComponent(code)}`)
        if (template) {
          state.promptTemplates = [template, ...state.promptTemplates.filter((item) => item.code !== code)]
        }
      } catch (error) {
        logEvent('error', '模板详情加载失败', error.message)
      }
    }
    state.currentTemplate = template || null
    fillTemplateForm(template)
    localStorage.setItem('learning.lastTemplateCode', state.lastTemplateCode || code)
    renderLearningConfigSummary?.()
  }

  function fillTemplateForm(template) {
    if (!elements.templateNameInput) return
    state.currentTemplate = template || null
    state.currentTemplateEditId = template?.id || null
    state.lastTemplateCode = template?.code || elements.templateSelect?.value || ''
    if (state.lastTemplateCode) localStorage.setItem('learning.lastTemplateCode', state.lastTemplateCode)
    elements.templateNameInput.value = template?.name || ''
    elements.templateCodeInput.value = template?.code || ''
    elements.templateTypeInput.value = template?.type || 'user'
    elements.templateSequenceInput.value = template?.sequence ?? 0
    elements.templateTagsInput.value = template?.tags || ''
    elements.templateDescriptionInput.value = template?.description || ''
    elements.templateExampleInput.value = template?.exampleInput || ''
    elements.templateExampleOutput.value = template?.exampleOutput || ''
    elements.templateContentInput.value = template?.content || ''
    const placeholders = templatePlaceholders(template)
    renderTemplatePlaceholders(placeholders)
    elements.templateSummary.textContent = template
      ? `${template.description || '暂无描述'} · ${placeholders.length ? `占位符 ${placeholders.join(', ')}` : '暂无占位符'}`
      : '选择模板后查看完整信息。'
    validateTemplatePlaceholders({ quiet: true })
  }

  function templatePlaceholders(template = state.currentTemplate) {
    const declared = parseTemplateVariables(template?.variables)
    const fromContent = extractPlaceholders(template?.content || '')
    return [...new Set([...declared, ...fromContent])]
  }

  function parseTemplateVariables(variables) {
    if (!variables) return []
    try {
      const parsed = typeof variables === 'string' ? JSON.parse(variables) : variables
      const list = Array.isArray(parsed) ? parsed : Object.values(parsed)
      return list.map((item) => (typeof item === 'string' ? item : item?.name)).filter(Boolean)
    } catch {
      return extractPlaceholders(String(variables))
    }
  }

  function extractPlaceholders(content) {
    return [...String(content || '').matchAll(/\{\{\s*([a-zA-Z0-9_.-]+)\s*\}\}/g)].map((match) => match[1])
  }

  function renderTemplatePlaceholders(placeholders) {
    if (!placeholders.length) {
      elements.templatePlaceholderList.className = 'chips empty'
      elements.templatePlaceholderList.textContent = '暂无占位符'
      return
    }
    elements.templatePlaceholderList.className = 'chips'
    elements.templatePlaceholderList.innerHTML = placeholders.map((item) => `<span class="chip">${escapeHtml(`{{${item}}}`)}</span>`).join('')
  }

  function validateTemplatePlaceholders(options = {}) {
    const content = elements.templateContentInput?.value || ''
    const required = parseTemplateVariables(state.currentTemplate?.variables)
    const missing = required.filter((name) => !new RegExp(`\\{\\{\\s*${escapeRegExp(name)}\\s*\\}\\}`).test(content))
    if (!required.length && !extractPlaceholders(content).includes('term')) {
      missing.push('term')
    }
    const ok = missing.length === 0
    elements.templateValidationMessage.className = `validation-message ${ok ? 'ok' : 'bad'}`
    elements.templateValidationMessage.textContent = ok ? '占位符校验通过' : `缺少占位符：${missing.map((name) => `{{${name}}}`).join('、')}`
    if (!ok && !options.quiet) toast(elements.templateValidationMessage.textContent)
    return ok
  }

  async function savePromptTemplate() {
    if (!validateTemplatePlaceholders()) return
    const editId = state.currentTemplateEditId || state.currentTemplate?.id || null
    const payload = {
      name: elements.templateNameInput.value.trim(),
      code: elements.templateCodeInput.value.trim(),
      type: elements.templateTypeInput.value.trim() || 'user',
      tags: elements.templateTagsInput.value.trim(),
      content: elements.templateContentInput.value.trim(),
      variables: state.currentTemplate?.variables || JSON.stringify(extractPlaceholders(elements.templateContentInput.value).map((name) => ({ name, required: true }))),
      description: elements.templateDescriptionInput.value.trim(),
      exampleInput: elements.templateExampleInput.value.trim(),
      exampleOutput: elements.templateExampleOutput.value.trim(),
      publicTemplate: Boolean(state.currentTemplate?.publicTemplate),
      sequence: Number(elements.templateSequenceInput.value || 0),
    }
    if (!payload.name || !payload.code || !payload.content) {
      toast('请补全模板名称、编码和内容')
      return
    }
    const confirmed = await confirmAction({
      title: editId ? '修改学习 Agent 模板' : '新增学习 Agent 模板',
      message: editId
        ? `确认修改学习 Agent 模板「${payload.name}」？保存后后续学习卡片生成会使用新的模板内容。`
        : `确认新增学习 Agent 模板「${payload.name}」？保存后可以在学习页切换使用。`,
      acceptText: editId ? '确认修改' : '确认新增',
    })
    if (!confirmed) return
    setLoading(true)
    try {
      if (state.preview) {
        const template = editId ? state.promptTemplates.find((item) => sameId(item.id, editId)) : { id: String(Date.now()) }
        Object.assign(template, payload)
        if (!editId) state.promptTemplates.unshift(template)
        renderTemplateOptions()
        renderTemplateConfigs()
        elements.templateSelect.value = payload.code
        fillTemplateForm(template)
        toast('设计预览：模板已保存')
        return
      }
      const path = editId ? `/api/v1/ai/prompt-templates/${encodeURIComponent(editId)}` : '/api/v1/ai/prompt-templates'
      const method = editId ? 'PUT' : 'POST'
      await request(path, { method, body: JSON.stringify(payload) })
      await loadPromptTemplates()
      elements.templateSelect.value = payload.code
      await renderSelectedTemplate()
      toast('模板已保存')
    } catch (error) {
      logEvent('error', '模板保存失败', error.message)
      toast(`模板保存失败：${error.message}`)
    } finally {
      setLoading(false)
    }
  }

  return {
    loadPromptTemplates,
    renderTemplateOptions,
    renderTemplateConfigs,
    openTemplateModal,
    closeTemplateModal,
    resetTemplateForm,
    deletePromptTemplate,
    clonePromptTemplate,
    renderSelectedTemplate,
    fillTemplateForm,
    templatePlaceholders,
    parseTemplateVariables,
    extractPlaceholders,
    renderTemplatePlaceholders,
    validateTemplatePlaceholders,
    savePromptTemplate,
  }
}
