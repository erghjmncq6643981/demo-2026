import { escapeHtml } from '/src/shared/text.js'

export function renderProviderSelect(input, providerCatalog, selectedProvider = '') {
  if (!input) return ''
  const provider = selectedProvider || input.value || 'deepseek'
  let html = Object.entries(providerCatalog)
    .map(([value, item]) => `<option value="${escapeHtml(value)}">${escapeHtml(item.label)} (${escapeHtml(value)})</option>`)
    .join('')
  if (provider && !providerCatalog[provider]) {
    html += `<option value="${escapeHtml(provider)}">${escapeHtml(provider)} (自定义)</option>`
  }
  input.innerHTML = html
  input.value = provider
  return provider
}

export function renderModelSelect(input, providerCatalog, provider, options = {}) {
  if (!input) return ''
  const config = providerCatalog[provider] || {}
  const currentModel = options.modelName || input.value
  const models = [...(config.models || [])]
  if (options.keepUnknownModel && currentModel && !models.includes(currentModel)) {
    models.unshift(currentModel)
  }
  input.innerHTML = models.map((model) => `<option value="${escapeHtml(model)}">${escapeHtml(model)}</option>`).join('')
  input.value = models.includes(currentModel) ? currentModel : models[0] || ''
  return input.value
}

export function providerDefaults(providerCatalog, provider) {
  return providerCatalog[provider] || { baseUrl: '', chatPath: '/chat/completions', models: [] }
}
