import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRuminationAudioController, splitArticleSentences } from '../../public/src/features/rumination/rumination-audio-controller.js'

function createAudioDomElements() {
  document.body.innerHTML = `
    <button id="ruminationTtsAudioBtn" type="button">🎙️ AI 真人朗读</button>
    <button id="ruminationTtsRateBtn" type="button">1x</button>
    <button id="ruminationSpeakArticleBtn" type="button">▶</button>
  `
  return {
    ttsAudioBtn: document.getElementById('ruminationTtsAudioBtn'),
    ttsRateBtn: document.getElementById('ruminationTtsRateBtn'),
    speakArticleBtn: document.getElementById('ruminationSpeakArticleBtn'),
  }
}

describe('知识反刍文章朗读控制器 (rumination-audio-controller)', () => {
  const originalAudio = globalThis.Audio
  const originalFetch = globalThis.fetch
  const originalCreateObjectURL = URL.createObjectURL
  const originalRevokeObjectURL = URL.revokeObjectURL

  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    globalThis.Audio = originalAudio
    globalThis.fetch = originalFetch
    URL.createObjectURL = originalCreateObjectURL
    URL.revokeObjectURL = originalRevokeObjectURL
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  it('splitArticleSentences 优先使用结构化句子，否则按句末标点分句', () => {
    expect(
      splitArticleSentences('Fallback A. Fallback B.', [
        { sentenceText: 'Structured first sentence.' },
        { sentenceText: 'Structured second sentence!' },
      ])
    ).toEqual(['Structured first sentence.', 'Structured second sentence!'])

    expect(
      splitArticleSentences('First line here.   Second line asks?  Third shouts!\n\nFourth ends.')
    ).toEqual(['First line here.', 'Second line asks?', 'Third shouts!', 'Fourth ends.'])

    expect(splitArticleSentences('   ')).toEqual([])
  })

  it('倍速按钮在 1.0x -> 0.75x -> 1.25x -> 1.0x 间循环并持久化到 localStorage', () => {
    const elements = createAudioDomElements()
    const toasts = []
    localStorage.setItem('learning.ruminationTtsRate', '0.75')

    const controller = createRuminationAudioController({
      elements,
      toast: (msg) => toasts.push(msg),
      getCurrentMaterial: () => ({ id: '501', articleContent: 'Hello world.' }),
    })

    expect(controller.getPlaybackRate()).toBe(0.75)
    expect(elements.ttsRateBtn.textContent).toBe('0.75x')

    elements.ttsRateBtn.click()
    expect(controller.getPlaybackRate()).toBe(1.25)
    expect(localStorage.getItem('learning.ruminationTtsRate')).toBe('1.25')
    expect(elements.ttsRateBtn.textContent).toBe('1.25x')

    elements.ttsRateBtn.click()
    expect(controller.getPlaybackRate()).toBe(1)
    expect(elements.ttsRateBtn.textContent).toBe('1x')

    elements.ttsRateBtn.click()
    expect(controller.getPlaybackRate()).toBe(0.75)
    expect(toasts[toasts.length - 1]).toContain('0.75x')
  })

  it('AI 语音接口报错（如未配置 NLS）时恢复按钮状态并温和提示本地朗读替代', async () => {
    const elements = createAudioDomElements()
    const toasts = []
    const controller = createRuminationAudioController({
      elements,
      state: { apiBase: '' },
      api: {
        getRuminationMaterialAudioStatus: vi.fn().mockRejectedValue(new Error('未配置阿里云 NLS 语音服务')),
      },
      toast: (msg) => toasts.push(msg),
      getCurrentMaterial: () => ({ id: '502', hasAudio: false, articleContent: 'Fresh apples arrive today.' }),
    })

    await controller.toggleAiAudio()

    expect(elements.ttsAudioBtn.disabled).toBe(false)
    expect(elements.ttsAudioBtn.textContent).toBe('🎙️ AI 真人朗读')
    expect(toasts.some((m) => m.includes('未配置阿里云 NLS'))).toBe(true)
  })

  it('已生成 AI 音频时携带 Bearer Token 拉取音频流并支持播放/暂停切换', async () => {
    const elements = createAudioDomElements()
    const listeners = {}
    const mockAudio = {
      paused: false,
      playbackRate: 1,
      src: '',
      addEventListener: vi.fn((evt, cb) => {
        listeners[evt] = cb
      }),
      play: vi.fn().mockImplementation(async () => {
        mockAudio.paused = false
      }),
      pause: vi.fn().mockImplementation(() => {
        mockAudio.paused = true
      }),
    }
    globalThis.Audio = vi.fn(() => mockAudio)
    URL.createObjectURL = vi.fn(() => 'blob:mock-audio-503')
    URL.revokeObjectURL = vi.fn()
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      blob: async () => new Blob(['mp3'], { type: 'audio/mpeg' }),
    })

    const toasts = []
    const controller = createRuminationAudioController({
      elements,
      state: { apiBase: '', token: 'jwt-abc' },
      toast: (msg) => toasts.push(msg),
      getCurrentMaterial: () => ({ id: '503', hasAudio: true, articleContent: 'Chop the crisp lettuce.' }),
    })

    await controller.toggleAiAudio()
    expect(globalThis.fetch).toHaveBeenCalledTimes(1)
    expect(globalThis.fetch.mock.calls[0][1].headers.Authorization).toBe('Bearer jwt-abc')
    expect(elements.ttsAudioBtn.textContent).toBe('⏸ 暂停朗读')

    await controller.toggleAiAudio()
    expect(mockAudio.pause).toHaveBeenCalledTimes(1)
    expect(elements.ttsAudioBtn.textContent).toBe('▶ 继续朗读')

    controller.stopAll()
    // 停止清理后即使底层 Audio 因清空 src 异步触发 error，也不应误报播放失败提示
    listeners.error?.()
    expect(toasts).toEqual([])
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-audio-503')
  })

  it('本地实时朗读按句顺序播放，并支持再次点击立刻停止', async () => {
    const elements = createAudioDomElements()
    const toasts = []
    const spoken = []
    const stopSpeaking = vi.fn(() => {
      window.dispatchEvent(
        new CustomEvent('learning:sentence-play-state', {
          detail: { playing: false, sentence: 'Second juicy sentence.' },
        })
      )
    })
    const material = {
      id: '504',
      articleContent: 'First crisp sentence. Second juicy sentence.',
    }

    const controller = createRuminationAudioController({
      elements,
      toast: (msg) => toasts.push(msg),
      speakSentence: (sentence) => {
        spoken.push(sentence)
      },
      stopSpeaking,
      getCurrentMaterial: () => material,
    })

    const runPromise = controller.toggleLocalPlayback()
    await Promise.resolve()
    expect(spoken).toEqual(['First crisp sentence.'])
    expect(elements.speakArticleBtn.classList.contains('playing')).toBe(true)

    // 模拟第一句播放结束事件推进到第二句
    window.dispatchEvent(
      new CustomEvent('learning:sentence-play-state', {
        detail: { playing: false, sentence: 'First crisp sentence.' },
      })
    )
    await Promise.resolve()
    await Promise.resolve()
    expect(spoken).toEqual(['First crisp sentence.', 'Second juicy sentence.'])

    // 再次点击停止本地朗读
    await controller.toggleLocalPlayback()
    expect(stopSpeaking).toHaveBeenCalledTimes(1)
    expect(elements.speakArticleBtn.classList.contains('playing')).toBe(false)
    expect(elements.speakArticleBtn.textContent).toBe('▶')
    expect(toasts).toContain('已停止本地朗读')

    // 清理未完成的 Promise
    await runPromise
  })
})
