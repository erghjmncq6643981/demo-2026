import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { createSpeechFeature } from '../../public/src/features/learning/speech/speech.js'

describe('Speech Feature with Preload, Cache, and Fast Fallback', () => {
  let ctx
  let mockSpeechSynthesis
  let mockAudioInstances

  beforeEach(() => {
    vi.useFakeTimers()
    mockAudioInstances = []

    // Mock Audio
    global.Audio = vi.fn().mockImplementation(function (src) {
      this.src = src
      this.preload = 'none'
      this.paused = true
      this.currentTime = 0
      this._listeners = {}
      this.addEventListener = vi.fn((event, handler) => {
        this._listeners[event] = handler
      })
      this.removeEventListener = vi.fn((event) => {
        delete this._listeners[event]
      })
      this.play = vi.fn().mockReturnValue(Promise.resolve())
      this.pause = vi.fn().mockImplementation(() => {
        this.paused = true
      })
      mockAudioInstances.push(this)
    })

    // Mock SpeechSynthesis
    mockSpeechSynthesis = {
      speak: vi.fn(),
      cancel: vi.fn(),
      getVoices: vi.fn().mockReturnValue([]),
      addEventListener: vi.fn(),
    }
    global.window = global.window || {}
    global.window.speechSynthesis = mockSpeechSynthesis
    global.SpeechSynthesisUtterance = vi.fn().mockImplementation(function (text) {
      this.text = text
      this.lang = 'en-US'
      this.rate = 1
      this.pitch = 1
    })

    ctx = {
      state: {
        speechSettings: {
          voiceType: 'us',
          sentenceVoiceName: '',
          sentenceRate: 0.78,
          sentencePitch: 1,
        },
      },
      elements: {},
      request: vi.fn(),
      toast: vi.fn(),
      logEvent: vi.fn(),
      clampNumber: (v, min, max, d) => (typeof v === 'number' ? Math.max(min, Math.min(max, v)) : d),
      renderLearningConfigSummary: vi.fn(),
    }
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('generates accurate Youdao dictionary URLs for US and UK voices', () => {
    const speech = createSpeechFeature(ctx)
    expect(speech.pronunciationUrl('apple', 'us')).toBe('https://dict.youdao.com/dictvoice?audio=apple&type=2')
    expect(speech.pronunciationUrl('banana', 'uk')).toBe('https://dict.youdao.com/dictvoice?audio=banana&type=1')
  })

  it('preloads audio into memory cache and reuses existing instance', () => {
    const speech = createSpeechFeature(ctx)

    // 1. Preload word
    speech.preloadAudio('negotiation', 'us')
    expect(global.Audio).toHaveBeenCalledTimes(1)
    expect(mockAudioInstances[0].src).toContain('audio=negotiation')
    expect(mockAudioInstances[0].preload).toBe('auto')

    // 2. Preload same word again should hit cache without creating new Audio
    speech.preloadAudio('negotiation', 'us')
    expect(global.Audio).toHaveBeenCalledTimes(1)

    // 3. Speak the word should play cached instance
    speech.speak('negotiation')
    expect(mockAudioInstances[0].play).toHaveBeenCalled()
    expect(global.Audio).toHaveBeenCalledTimes(1)
  })

  it('triggers 180ms fast fallback to browser native voice when remote audio stalls', () => {
    const speech = createSpeechFeature(ctx)

    // Audio play does not trigger 'playing' event and stays paused
    global.Audio = vi.fn().mockImplementation(function (src) {
      this.src = src
      this.paused = true
      this.currentTime = 0
      this.addEventListener = vi.fn()
      this.removeEventListener = vi.fn()
      this.play = vi.fn().mockReturnValue(new Promise(() => {})) // Never resolves
      this.pause = vi.fn()
    })

    speech.speak('difficult')

    // Fast-forward 180ms
    vi.advanceTimersByTime(180)

    expect(mockSpeechSynthesis.speak).toHaveBeenCalled()
    expect(ctx.toast).toHaveBeenCalledWith('正在播放发音')
  })

  it('triggers immediate browser voice fallback on remote play rejection', async () => {
    const speech = createSpeechFeature(ctx)

    global.Audio = vi.fn().mockImplementation(function (src) {
      this.src = src
      this.paused = true
      this.currentTime = 0
      this.addEventListener = vi.fn()
      this.removeEventListener = vi.fn()
      this.play = vi.fn().mockRejectedValue(new Error('Network error'))
      this.pause = vi.fn()
    })

    speech.speak('instant')
    await Promise.resolve()

    expect(mockSpeechSynthesis.speak).toHaveBeenCalled()
  })
})
