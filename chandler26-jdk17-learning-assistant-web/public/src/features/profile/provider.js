import { escapeHtml } from '/src/shared/text.js'

const DEFAULT_CHAT_PATH = '/chat/completions'

function buildProviderCatalog(modelConfigs = []) {
  return (Array.isArray(modelConfigs) ? modelConfigs : []).reduce((catalog, item) => {
    if (!item?.provider) return catalog
    if (!catalog[item.provider]) {
      catalog[item.provider] = {
        label: item.provider,
        baseUrl: item.baseUrl || '',
        chatPath: item.chatPath || DEFAULT_CHAT_PATH,
        models: [],
      }
    }
    if (item.modelName && !catalog[item.provider].models.includes(item.modelName)) {
      catalog[item.provider].models.push(item.modelName)
    }
    return catalog
  }, {})
}

function writeOptions(input, optionsHtml) {
  if (input.tagName === 'SELECT') {
    input.innerHTML = optionsHtml
    return
  }
  if (input.list) {
    input.list.innerHTML = optionsHtml
  }
}

export function renderProviderSelect(input, modelConfigs = [], selectedProvider = '') {
  if (!input) return ''
  const providersByCode = buildProviderCatalog(modelConfigs)
  const providers = Object.entries(providersByCode)
  const provider = selectedProvider || input.value || providers[0]?.[0] || ''
  let html = providers
    .map(([value, item]) => `<option value="${escapeHtml(value)}">${escapeHtml(item.label || value)}</option>`)
    .join('')
  if (provider && !providersByCode[provider]) {
    html += `<option value="${escapeHtml(provider)}">${escapeHtml(provider)}</option>`
  }
  if (!html && input.tagName === 'SELECT') {
    html = '<option value="">请先维护模型配置</option>'
  }
  writeOptions(input, html)
  input.value = provider
  return provider
}

export function renderModelSelect(input, modelConfigs = [], provider, options = {}) {
  if (!input) return ''
  const providersByCode = buildProviderCatalog(modelConfigs)
  const config = providersByCode[provider] || {}
  const currentModel = options.modelName || input.value
  const models = [...(config.models || [])]
  if (options.keepUnknownModel && currentModel && !models.includes(currentModel)) {
    models.unshift(currentModel)
  }
  writeOptions(input, models.map((model) => `<option value="${escapeHtml(model)}">${escapeHtml(model)}</option>`).join(''))
  input.value = models.includes(currentModel) ? currentModel : models[0] || ''
  return input.value
}

export function providerDefaults(modelConfigs = [], provider) {
  const providersByCode = buildProviderCatalog(modelConfigs)
  return providersByCode[provider] || { baseUrl: '', chatPath: DEFAULT_CHAT_PATH, models: [] }
}
