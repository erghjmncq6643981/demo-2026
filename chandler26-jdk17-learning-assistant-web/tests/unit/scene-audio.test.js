import { describe, expect, it, vi } from 'vitest'
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
