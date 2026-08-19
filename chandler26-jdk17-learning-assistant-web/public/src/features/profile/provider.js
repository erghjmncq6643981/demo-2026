import { escapeHtml } from '/src/shared/text.js'

const DEFAULT_CHAT_PATH = '/chat/completions'

export const BUILT_IN_PROVIDERS = {
  deepseek: {
    label: 'DeepSeek (深度求索)',
    baseUrl: 'https://api.deepseek.com',
    chatPath: '/chat/completions',
    models: ['deepseek-v4-flash', 'deepseek-v4-pro'],
  },
  kimi: {
    label: 'Kimi (月之暗面)',
    baseUrl: 'https://api.moonshot.cn/v1',
    chatPath: '/chat/completions',
    models: ['kimi-k3', 'kimi-k2.6', 'kimi-k2.5'],
  },
}

function buildProviderCatalog(modelConfigs = []) {
  const catalog = Object.entries(BUILT_IN_PROVIDERS).reduce((acc, [key, val]) => {
    acc[key] = {
      label: val.label,
      baseUrl: val.baseUrl,
      chatPath: val.chatPath,
      models: [...val.models],
    }
    return acc
  }, {})

  for (const item of (Array.isArray(modelConfigs) ? modelConfigs : [])) {
    if (!item?.provider) continue
    const key = item.provider.toLowerCase()
    if (!catalog[key]) continue
    if (item.baseUrl) catalog[key].baseUrl = item.baseUrl
    if (item.chatPath) catalog[key].chatPath = item.chatPath
    if (item.supported !== false && item.modelName && !catalog[key].models.includes(item.modelName)) {
      catalog[key].models.unshift(item.modelName)
    }
  }

  return catalog
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
  const provider = selectedProvider || input.value || providers[0]?.[0] || 'deepseek'
  let html = providers
    .map(([value, item]) => `<option value="${escapeHtml(value)}" label="${escapeHtml(item.label || value)}">${escapeHtml(item.label || value)}</option>`)
    .join('')
  if (provider && !providersByCode[provider]) {
    html += `<option value="${escapeHtml(provider)}" label="${escapeHtml(provider)}">${escapeHtml(provider)}</option>`
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
