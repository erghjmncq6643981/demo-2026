import { escapeHtml } from '/src/shared/text.js'

const DEFAULT_CHAT_PATH = '/chat/completions'

export const BUILT_IN_PROVIDERS = {
  deepseek: {
    label: 'DeepSeek (深度求索)',
    baseUrl: 'https://api.deepseek.com',
    chatPath: '/chat/completions',
    models: ['deepseek-chat', 'deepseek-reasoner'],
  },
  qwen: {
    label: '阿里通义千问 (Qwen)',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    chatPath: '/chat/completions',
    models: ['qwen-max', 'qwen-plus', 'qwen-turbo', 'qwen2.5-72b-instruct'],
  },
  kimi: {
    label: '月之暗面 Kimi (Moonshot)',
    baseUrl: 'https://api.moonshot.cn/v1',
    chatPath: '/chat/completions',
    models: ['moonshot-v1-8k', 'moonshot-v1-32k', 'moonshot-v1-128k'],
  },
  siliconflow: {
    label: '硅基流动 (SiliconFlow)',
    baseUrl: 'https://api.siliconflow.cn/v1',
    chatPath: '/chat/completions',
    models: ['deepseek-ai/DeepSeek-V3', 'deepseek-ai/DeepSeek-R1', 'Qwen/Qwen2.5-72B-Instruct', 'Pro/deepseek-ai/DeepSeek-V3'],
  },
  zhipu: {
    label: '智谱 AI (GLM)',
    baseUrl: 'https://open.bigmodel.cn/api/paas/v4',
    chatPath: '/chat/completions',
    models: ['glm-4-plus', 'glm-4-flash', 'glm-4'],
  },
  doubao: {
    label: '字节跳动豆包 (Doubao)',
    baseUrl: 'https://ark.cn-beijing.volces.com/api/v3',
    chatPath: '/chat/completions',
    models: ['doubao-pro-32k', 'doubao-lite-32k'],
  },
  openai: {
    label: 'OpenAI',
    baseUrl: 'https://api.openai.com/v1',
    chatPath: '/chat/completions',
    models: ['gpt-4o', 'gpt-4o-mini', 'gpt-4-turbo', 'gpt-3.5-turbo'],
  },
  ollama: {
    label: 'Ollama (本地私有模型)',
    baseUrl: 'http://localhost:11434/v1',
    chatPath: '/chat/completions',
    models: ['qwen2.5:7b', 'deepseek-r1:8b', 'llama3.1:8b'],
  },
  custom: {
    label: '自定义兼容接口 (Custom)',
    baseUrl: '',
    chatPath: '/chat/completions',
    models: [],
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
    if (!catalog[key]) {
      catalog[key] = {
        label: item.provider,
        baseUrl: item.baseUrl || '',
        chatPath: item.chatPath || DEFAULT_CHAT_PATH,
        models: [],
      }
    } else {
      if (item.baseUrl) catalog[key].baseUrl = item.baseUrl
      if (item.chatPath) catalog[key].chatPath = item.chatPath
    }
    if (item.modelName && !catalog[key].models.includes(item.modelName)) {
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
