/** 场景文章真人语音的状态、轮询与播放控制。 */
export function createSceneAudioController({ state, elements, api, activeUnit, sameId, toast, logEvent }) {
  let activeAudio = null
  let activeAudioUnitId = null
  const audioStates = new Map()
  const playbackRates = [1.0, 0.75, 1.25]

  function getUnitAudioState(unitId) {
    if (!unitId) return null
    const key = String(unitId)
    if (!audioStates.has(key)) {
      audioStates.set(key, {
        status: 'idle',
        taskId: null,
        hasAudio: false,
        errorMessage: null,
        pollTimer: null,
      })
    }
    return audioStates.get(key)
  }

  function isUserOnSceneArticle(unitId) {
    if (!unitId || (state.activeView && state.activeView !== 'scenePlanView')) return false
    if (state.sceneChallengeStage && state.sceneChallengeStage !== 'learning') return false
    const current = activeUnit()
    if (!current || !sameId(current.id, unitId)) return false
    return !elements.sceneLearningStage || !elements.sceneLearningStage.classList.contains('hidden')
  }

  function updateButton(unitId) {
    const button = elements.sceneTtsAudioBtn
    const current = activeUnit()
    if (!button || !current || !sameId(current.id, unitId)) return
    const item = getUnitAudioState(unitId)
    if (!item) {
      button.disabled = false
      button.innerHTML = '🎙️ AI 真人朗读'
      button.classList.remove('playing')
      return
    }
    if (item.status === 'playing') {
      button.disabled = false
      button.innerHTML = '⏸ 暂停朗读'
      button.classList.add('playing')
    } else if (item.status === 'paused') {
      button.disabled = false
      button.innerHTML = '▶ 继续朗读'
      button.classList.remove('playing')
    } else if (item.status === 'checking') {
      button.disabled = true
      button.innerHTML = '⏳ 正在检查语音...'
      button.classList.remove('playing')
    } else if (item.status === 'pending' || item.status === 'running') {
      button.disabled = true
      button.innerHTML = '🎙️ 正在生成真人语音...'
      button.classList.remove('playing')
    } else {
      button.disabled = false
      button.innerHTML = '🎙️ AI 真人朗读'
      button.classList.remove('playing')
    }
  }

  function stop() {
    if (activeAudio) {
      activeAudio.pause()
      activeAudio = null
      const previousUnitId = activeAudioUnitId
      activeAudioUnitId = null
      if (previousUnitId) {
        const previous = getUnitAudioState(previousUnitId)
        if (previous && ['playing', 'paused'].includes(previous.status)) previous.status = 'completed'
        updateButton(previousUnitId)
      }
    }
    const current = activeUnit()
    if (current) updateButton(current.id)
    else if (elements.sceneTtsAudioBtn) {
      elements.sceneTtsAudioBtn.disabled = false
      elements.sceneTtsAudioBtn.innerHTML = '🎙️ AI 真人朗读'
      elements.sceneTtsAudioBtn.classList.remove('playing')
    }
  }

  /** 切换场景时停止其他单元的播放，重新渲染当前单元不会打断同一音频。 */
  function stopIfDifferent(unitId) {
    if (activeAudio && !sameId(activeAudioUnitId, unitId)) stop()
  }

  async function syncStatus(unitId) {
    if (!unitId || state.preview) return
    const item = getUnitAudioState(unitId)
    if (!item || ['playing', 'paused'].includes(item.status) || item.pollTimer) return
    try {
      const result = await api.getSceneAudioStatus(unitId)
      if (!result) return
      item.hasAudio = Boolean(result.hasAudio)
      if (result.taskStatus === 'pending' || result.taskStatus === 'running') {
        item.status = result.taskStatus
        item.taskId = result.taskId
        startPolling(unitId)
      } else if (result.hasAudio || result.taskStatus === 'completed') {
        item.status = 'completed'
      } else if (result.taskStatus === 'failed') {
        item.status = 'failed'
        item.errorMessage = result.errorMessage
      } else {
        item.status = 'idle'
      }
      updateButton(unitId)
    } catch {
      // 状态同步失败不打扰文章学习，下一次进入场景时再尝试。
    }
  }

  function startPolling(unitId) {
    const item = getUnitAudioState(unitId)
    if (!item) return
    if (item.pollTimer) window.clearInterval(item.pollTimer)
    let pollCount = 0
    item.pollTimer = window.setInterval(async () => {
      pollCount += 1
      try {
        const result = await api.getSceneAudioStatus(unitId)
        if (!result) return
        if (result.hasAudio || result.taskStatus === 'completed') {
          window.clearInterval(item.pollTimer)
          item.pollTimer = null
          item.hasAudio = true
          item.status = 'completed'
          if (isUserOnSceneArticle(unitId)) {
            toast('AI 真人朗读语音已就绪，开始播放')
            play(unitId)
          } else {
            updateButton(unitId)
          }
          return
        }
        if (result.taskStatus === 'failed') {
          window.clearInterval(item.pollTimer)
          item.pollTimer = null
          item.status = 'failed'
          item.errorMessage = result.errorMessage
          if (sameId(activeUnit()?.id, unitId)) {
            updateButton(unitId)
            toast(`语音生成失败: ${result.errorMessage || '请检查配置后重试'}`)
          }
          return
        }
        item.status = result.taskStatus || 'running'
        item.taskId = result.taskId
        if (sameId(activeUnit()?.id, unitId)) updateButton(unitId)
        if (pollCount >= 60) {
          window.clearInterval(item.pollTimer)
          item.pollTimer = null
          item.status = 'idle'
          if (sameId(activeUnit()?.id, unitId)) {
            updateButton(unitId)
            toast('语音生成超时，请稍后重试')
          }
        }
      } catch (error) {
        logEvent('warn', '轮询场景文章语音状态异常', error.message)
      }
    }, 2500)
  }

  async function play(unitId) {
    if (!isUserOnSceneArticle(unitId)) return
    const item = getUnitAudioState(unitId)
    const base = state?.apiBase ? state.apiBase.replace(/\/$/, '') : ''
    const audioUrl = `${base}/api/v1/english/learning/scene-units/${encodeURIComponent(unitId)}/audio?t=${Date.now()}`
    stop()
    const audio = new Audio(audioUrl)
    audio.playbackRate = getPlaybackRate()
    activeAudio = audio
    activeAudioUnitId = unitId
    item.status = 'playing'
    updateButton(unitId)
    const button = elements.sceneTtsAudioBtn
    audio.addEventListener('play', () => {
      item.status = 'playing'
      if (sameId(activeUnit()?.id, unitId) && button) {
        button.disabled = false
        button.innerHTML = '⏸ 暂停朗读'
        button.classList.add('playing')
      }
    })
    audio.addEventListener('pause', () => {
      if (item.status === 'playing') item.status = 'paused'
      if (sameId(activeUnit()?.id, unitId) && button) {
        button.disabled = false
        button.innerHTML = '▶ 继续朗读'
        button.classList.remove('playing')
      }
    })
    audio.addEventListener('ended', () => {
      item.status = 'completed'
      if (sameId(activeUnit()?.id, unitId) && button) {
        button.disabled = false
        button.innerHTML = '🎙️ AI 真人朗读'
        button.classList.remove('playing')
      }
      activeAudio = null
      activeAudioUnitId = null
    })
    audio.addEventListener('error', (error) => {
      logEvent('error', '音频播放错误', error)
      item.status = 'idle'
      if (sameId(activeUnit()?.id, unitId) && button) {
        button.disabled = false
        button.innerHTML = '🎙️ AI 真人朗读'
        button.classList.remove('playing')
      }
      activeAudio = null
      activeAudioUnitId = null
      if (sameId(activeUnit()?.id, unitId)) toast('音频加载失败，请重试')
    })
    try {
      await audio.play()
    } catch (error) {
      logEvent('warn', '自动播放受阻或失败', error.message)
      item.status = 'paused'
      updateButton(unitId)
    }
  }

  function getPlaybackRate() {
    const saved = Number.parseFloat(localStorage.getItem('learning.sceneTtsRate'))
    return playbackRates.includes(saved) ? saved : 1.0
  }

  function renderRateButton() {
    if (!elements.sceneTtsRateBtn) return
    const rate = getPlaybackRate()
    elements.sceneTtsRateBtn.textContent = `${rate}x`
    elements.sceneTtsRateBtn.title = `当前倍速 ${rate}x，点击切换 (0.75x / 1.0x / 1.25x)`
  }

  function cyclePlaybackRate() {
    const current = getPlaybackRate()
    const next = playbackRates[(playbackRates.indexOf(current) + 1) % playbackRates.length]
    localStorage.setItem('learning.sceneTtsRate', String(next))
    renderRateButton()
    if (activeAudio) activeAudio.playbackRate = next
    toast(`朗读倍速已设为 ${next}x`)
  }

  async function toggle() {
    const unit = activeUnit()
    if (!unit || !unit.id) {
      toast('暂无可朗读的场景文章')
      return
    }
    if (state.preview) {
      toast('预览模式暂不支持 AI 真人朗读')
      return
    }
    const item = getUnitAudioState(unit.id)
    if (activeAudio && sameId(activeAudioUnitId, unit.id)) {
      if (!activeAudio.paused) {
        activeAudio.pause()
        item.status = 'paused'
        updateButton(unit.id)
      } else {
        item.status = 'playing'
        updateButton(unit.id)
        activeAudio.play().catch(() => {})
      }
      return
    }
    if (item.status === 'pending' || item.status === 'running') {
      toast('当前场景文章语音正在后台生成中，请稍候...')
      return
    }
    stop()
    item.status = 'checking'
    updateButton(unit.id)
    try {
      const status = await api.getSceneAudioStatus(unit.id)
      if (status?.hasAudio) {
        item.hasAudio = true
        item.status = 'completed'
        if (isUserOnSceneArticle(unit.id)) await play(unit.id)
        return
      }
      if (status?.taskStatus === 'pending' || status?.taskStatus === 'running') {
        item.status = status.taskStatus
        item.taskId = status.taskId
        updateButton(unit.id)
        toast('当前场景文章语音正在后台生成中，请稍候...')
        startPolling(unit.id)
        return
      }
      item.status = 'running'
      updateButton(unit.id)
      const submitted = await api.generateSceneAudioAsync(unit.id, false)
      item.taskId = submitted?.taskId
      item.status = submitted?.taskStatus || 'pending'
      updateButton(unit.id)
      toast('已提交场景文章语音生成任务，将在后台自动合成')
      startPolling(unit.id)
    } catch (error) {
      logEvent('error', 'AI真人朗读提交失败', error.message)
      item.status = 'idle'
      updateButton(unit.id)
      toast(error.message || 'AI 真人朗读启动失败')
    }
  }

  function clear() {
    stop()
    for (const item of audioStates.values()) {
      if (item.pollTimer) window.clearInterval(item.pollTimer)
      item.pollTimer = null
    }
    audioStates.clear()
  }

  return {
    clear,
    play,
    stop,
    stopIfDifferent,
    syncStatus,
    toggle,
    renderRateButton,
    cyclePlaybackRate,
    updateButton,
  }
}
