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
      this.play = vi.fn().mockImplementation(() => {
        this.paused = false
        return Promise.resolve()
      })
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

  it('falls back gracefully to direct sentence audio when browser speech synthesis throws', () => {
    mockSpeechSynthesis.speak.mockImplementation(() => {
      throw new Error('Speech synthesis error: DOMException')
    })
    const speech = createSpeechFeature(ctx)

    speech.speakSentence('Another test sentence.')

    expect(global.Audio).toHaveBeenCalledWith(expect.stringContaining('/api/sentence-audio?text=Another%20test%20sentence.'))
  })

  it('handles missing speechSettings safely without throwing exceptions', () => {
    const speech = createSpeechFeature({
      ...ctx,
      state: {},
    })

    expect(() => speech.speakSentence('Sentence without settings.')).not.toThrow()
    expect(mockSpeechSynthesis.speak).toHaveBeenCalled()
  })

  it('toggles playback off when clicking the same sentence currently playing', () => {
    const speech = createSpeechFeature(ctx)

    speech.speakSentence('Repeated test sentence.')
    expect(mockSpeechSynthesis.speak).toHaveBeenCalledTimes(1)
    expect(ctx.toast).toHaveBeenLastCalledWith('正在播放发音')

    mockSpeechSynthesis.speaking = true
    speech.speakSentence('Repeated test sentence.')

    expect(mockSpeechSynthesis.cancel).toHaveBeenCalled()
    expect(ctx.toast).toHaveBeenLastCalledWith('已停止朗读')
  })

  it('cancels previous audio and plays the new sentence when switching sentences while speaking', () => {
    const speech = createSpeechFeature(ctx)

    speech.speakSentence('First sentence.')
    expect(mockSpeechSynthesis.speak).toHaveBeenCalledTimes(1)

    mockSpeechSynthesis.speaking = true
    speech.speakSentence('Second sentence.')

    expect(mockSpeechSynthesis.cancel).toHaveBeenCalled()
    expect(mockSpeechSynthesis.speak).toHaveBeenCalledTimes(2)
    expect(ctx.toast).toHaveBeenLastCalledWith('正在播放发音')
  })

  it('resumes paused speech synthesis before speaking', () => {
    mockSpeechSynthesis.paused = true
    mockSpeechSynthesis.resume = vi.fn().mockImplementation(() => {
      mockSpeechSynthesis.paused = false
    })
    const speech = createSpeechFeature(ctx)

    speech.speakSentence('Resume when paused.')
    expect(mockSpeechSynthesis.resume).toHaveBeenCalled()
    expect(mockSpeechSynthesis.speak).toHaveBeenCalled()
  })

  it('matches voice accent regardless of hyphen or underscore in lang code', () => {
    const speech = createSpeechFeature(ctx)
    expect(speech.voiceMatchesAccent({ lang: 'en_US' }, 'us')).toBe(true)
    expect(speech.voiceMatchesAccent({ lang: 'en-US' }, 'us')).toBe(true)
    expect(speech.voiceMatchesAccent({ lang: 'en_GB' }, 'uk')).toBe(true)
    expect(speech.voiceMatchesAccent({ lang: 'en-GB' }, 'uk')).toBe(true)
    expect(speech.voiceMatchesAccent({ lang: 'en_US' }, 'uk')).toBe(false)
  })

  it('prefers local offline voices over Google cloud voices to avoid network blocking', () => {
    mockSpeechSynthesis.getVoices = vi.fn().mockReturnValue([
      { name: 'Google US English', lang: 'en-US', localService: false },
      { name: 'Samantha', lang: 'en-US', localService: true },
      { name: 'Alex', lang: 'en-US', localService: true },
      { name: 'Daniel', lang: 'en-GB', localService: true },
    ])
    const speech = createSpeechFeature(ctx)
    const voice = speech.chooseSpeechVoice('us')
    expect(voice.name).toBe('Samantha')
    expect(voice.localService).toBe(true)
  })

  it('prefers British local voice Daniel over Google UK voices', () => {
    mockSpeechSynthesis.getVoices = vi.fn().mockReturnValue([
      { name: 'Google UK English Female', lang: 'en-GB', localService: false },
      { name: 'Daniel', lang: 'en-GB', localService: true },
    ])
    const speech = createSpeechFeature(ctx)
    const voice = speech.chooseSpeechVoice('uk')
    expect(voice.name).toBe('Daniel')
    expect(voice.localService).toBe(true)
  })

  it('constructs correct sentence audio url with voice type', () => {
    const speech = createSpeechFeature(ctx)
    expect(speech.sentenceAudioUrl('Hello world', 'us')).toBe('/api/sentence-audio?text=Hello%20world&type=us')
    expect(speech.sentenceAudioUrl('Hello world', 'uk')).toBe('/api/sentence-audio?text=Hello%20world&type=uk')
  })

  it('plays sentence audio and toggles off on repeated play', () => {
    const speech = createSpeechFeature(ctx)
    speech.playSentenceAudio('Play this sentence')
    expect(global.Audio).toHaveBeenCalledWith('/api/sentence-audio?text=Play%20this%20sentence&type=us')
    expect(ctx.toast).toHaveBeenLastCalledWith('正在播放发音')

    speech.playSentenceAudio('Play this sentence')
    expect(ctx.toast).toHaveBeenLastCalledWith('已停止朗读')
  })
})
