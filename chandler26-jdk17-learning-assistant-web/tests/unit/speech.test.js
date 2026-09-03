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

  it('generates accurate audio endpoint URLs for US and UK voices', () => {
    const speech = createSpeechFeature(ctx)
    expect(speech.pronunciationUrl('apple', 'us')).toBe('/api/v1/english/audio/us/apple')
    expect(speech.pronunciationUrl('banana', 'uk')).toBe('/api/v1/english/audio/uk/banana')
  })

  it('preloads audio into memory cache and reuses existing instance', () => {
    const speech = createSpeechFeature(ctx)

    // 1. Preload word
    speech.preloadAudio('negotiation', 'us')
    expect(global.Audio).toHaveBeenCalledTimes(1)
    expect(mockAudioInstances[0].src).toContain('/api/v1/english/audio/us/negotiation')
    expect(mockAudioInstances[0].preload).toBe('auto')

    // 2. Preload same word again should hit cache without creating new Audio
    speech.preloadAudio('negotiation', 'us')
    expect(global.Audio).toHaveBeenCalledTimes(1)
  })

  it('triggers fast fallback to direct Youdao dictionary audio when backend stalls', () => {
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

    // Fast-forward 750ms
    vi.advanceTimersByTime(750)

    // Direct Youdao audio instance should be created as fallback
    expect(global.Audio).toHaveBeenCalledWith(expect.stringContaining('https://dict.youdao.com/dictvoice?audio=difficult'))
  })

  it('triggers sentence pronunciation with browser voice synthesis', () => {
    const speech = createSpeechFeature(ctx)

    speech.speakSentence('This is a test sentence.')

    expect(mockSpeechSynthesis.speak).toHaveBeenCalled()
    expect(ctx.toast).toHaveBeenCalledWith('正在播放发音')
  })
})
