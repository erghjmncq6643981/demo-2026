import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createScenePlanApi } from '../../public/src/features/learning/scene-plan/api.js'

describe('scene plan audio API', () => {
  it('getSceneAudioStatus 构造正确的请求路径', async () => {
    const request = vi.fn().mockResolvedValue({ unitId: 101, hasAudio: true, taskStatus: 'completed' })
    const api = createScenePlanApi(request)

    const res = await api.getSceneAudioStatus(101)
    expect(request).toHaveBeenCalledWith('/api/v1/english/learning/scene-units/101/audio/status')
    expect(res.hasAudio).toBe(true)
  })

  it('generateSceneAudioAsync 构造 POST 异步生成任务请求', async () => {
    const request = vi.fn().mockResolvedValue({ unitId: 202, hasAudio: false, taskStatus: 'pending', taskId: 555 })
    const api = createScenePlanApi(request)

    const res = await api.generateSceneAudioAsync(202, true)
    expect(request).toHaveBeenCalledWith('/api/v1/english/learning/scene-units/202/audio/async?forceRefresh=true', { method: 'POST' })
    expect(res.taskId).toBe(555)
  })
})

describe('sceneAudioController controls and shortcuts', () => {
  let origAudio
  class MockAudio {
    constructor(src) {
      this.src = src
      this.currentTime = 0
      this.playbackRate = 1.0
      this.paused = true
      this._listeners = {}
    }
    addEventListener(event, handler) {
      this._listeners[event] = this._listeners[event] || []
      this._listeners[event].push(handler)
    }
    play() {
      this.paused = false
      this._listeners['play']?.forEach((fn) => fn())
      return Promise.resolve()
    }
    pause() {
      this.paused = true
      this._listeners['pause']?.forEach((fn) => fn())
    }
  }

  beforeEach(() => {
    origAudio = globalThis.Audio
    globalThis.Audio = MockAudio
  })

  afterEach(() => {
    globalThis.Audio = origAudio
  })

  it('updateButton provides shortcut hints in title attribute', async () => {
    const { createSceneAudioController } = await import('../../public/src/features/learning/scene-plan/scene-audio-controller.js')
    const button = document.createElement('button')
    const state = { activeView: 'scenePlanView', sceneChallengeStage: 'learning' }
    const elements = { sceneTtsAudioBtn: button, sceneLearningStage: { classList: { contains: () => false } } }
    const unit = { id: 10 }
    const controller = createSceneAudioController({
      state,
      elements,
      api: { getSceneAudioStatus: vi.fn().mockResolvedValue({ hasAudio: true }) },
      activeUnit: () => unit,
      sameId: (a, b) => String(a) === String(b),
      toast: vi.fn(),
      logEvent: vi.fn(),
    })

    controller.updateButton(10)
    expect(button.innerHTML).toBe('🎙️ AI 真人朗读')
    expect(button.title).toContain('空格或 P 播放/暂停，R 重新播放')

    await controller.play(10)
    expect(button.innerHTML).toBe('⏸ 暂停朗读')
    expect(button.title).toContain('暂停朗读 (快捷键: 空格或 P，R 重新播放)')

    controller.pause()
    expect(button.innerHTML).toBe('▶ 继续朗读')
    expect(button.title).toContain('继续朗读 (快捷键: 空格或 P，R 重新播放)')
  })

  it('toggle and replay control playback and reset progress to beginning', async () => {
    const { createSceneAudioController } = await import('../../public/src/features/learning/scene-plan/scene-audio-controller.js')
    const button = document.createElement('button')
    const toast = vi.fn()
    const state = { activeView: 'scenePlanView', sceneChallengeStage: 'learning' }
    const elements = { sceneTtsAudioBtn: button, sceneLearningStage: { classList: { contains: () => false } } }
    const unit = { id: 20 }
    const controller = createSceneAudioController({
      state,
      elements,
      api: { getSceneAudioStatus: vi.fn().mockResolvedValue({ hasAudio: true }) },
      activeUnit: () => unit,
      sameId: (a, b) => String(a) === String(b),
      toast,
      logEvent: vi.fn(),
    })

    // Start playback
    await controller.toggle()
    expect(controller.isPlaying()).toBe(true)
    expect(controller.isPaused()).toBe(false)
    expect(button.innerHTML).toBe('⏸ 暂停朗读')

    // Pause
    await controller.toggle()
    expect(controller.isPlaying()).toBe(false)
    expect(controller.isPaused()).toBe(true)
    expect(button.innerHTML).toBe('▶ 继续朗读')

    // Replay from paused
    await controller.replay()
    expect(controller.isPlaying()).toBe(true)
    expect(toast).toHaveBeenCalledWith('重新播放')
    expect(button.innerHTML).toBe('⏸ 暂停朗读')
  })
})
