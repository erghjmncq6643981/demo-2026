import { describe, expect, it } from 'vitest'
import {
  BUILT_IN_PROVIDERS,
  providerDefaults,
  renderModelSelect,
  renderProviderSelect,
} from '../../public/src/features/ai/model/provider-catalog.js'

describe('AI Provider Catalog (provider-catalog.js)', () => {
  it('includes qwen and glm in BUILT_IN_PROVIDERS with expected configuration', () => {
    expect(BUILT_IN_PROVIDERS.qwen).toBeDefined()
    expect(BUILT_IN_PROVIDERS.qwen.label).toContain('Qwen')
    expect(BUILT_IN_PROVIDERS.qwen.baseUrl).toBe('https://dashscope.aliyuncs.com/compatible-mode/v1')
    expect(BUILT_IN_PROVIDERS.qwen.chatPath).toBe('/chat/completions')
    expect(BUILT_IN_PROVIDERS.qwen.models).toContain('qwen3.8-flash')

    expect(BUILT_IN_PROVIDERS.glm).toBeDefined()
    expect(BUILT_IN_PROVIDERS.glm.label).toContain('GLM')
    expect(BUILT_IN_PROVIDERS.glm.baseUrl).toBe('https://open.bigmodel.cn/api/paas/v4')
    expect(BUILT_IN_PROVIDERS.glm.chatPath).toBe('/chat/completions')
    expect(BUILT_IN_PROVIDERS.glm.models).toContain('GLM-5.3-Flash')
  })

  it('renders provider select options containing qwen and glm', () => {
    const select = document.createElement('select')
    const selected = renderProviderSelect(select, [], 'qwen')

    expect(selected).toBe('qwen')
    const options = Array.from(select.options).map((opt) => opt.value)
    expect(options).toContain('deepseek')
    expect(options).toContain('kimi')
    expect(options).toContain('qwen')
    expect(options).toContain('glm')
  })

  it('populates model select for qwen and glm providers', () => {
    const select = document.createElement('select')

    const qwenModel = renderModelSelect(select, [], 'qwen')
    expect(qwenModel).toBe('qwen3.8-flash')
    expect(Array.from(select.options).map((opt) => opt.value)).toContain('qwen3.8-flash')

    const glmModel = renderModelSelect(select, [], 'glm')
    expect(glmModel).toBe('GLM-5.3-Flash')
    expect(Array.from(select.options).map((opt) => opt.value)).toContain('GLM-5.3-Flash')
  })

  it('returns provider defaults for qwen and glm', () => {
    const qwenDefaults = providerDefaults([], 'qwen')
    expect(qwenDefaults.baseUrl).toBe('https://dashscope.aliyuncs.com/compatible-mode/v1')
    expect(qwenDefaults.chatPath).toBe('/chat/completions')
    expect(qwenDefaults.models).toContain('qwen3.8-flash')

    const glmDefaults = providerDefaults([], 'glm')
    expect(glmDefaults.baseUrl).toBe('https://open.bigmodel.cn/api/paas/v4')
    expect(glmDefaults.chatPath).toBe('/chat/completions')
    expect(glmDefaults.models).toContain('GLM-5.3-Flash')
  })
})
