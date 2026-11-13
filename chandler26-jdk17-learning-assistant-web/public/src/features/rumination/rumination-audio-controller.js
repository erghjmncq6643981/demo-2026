/**
 * 知识反刍场景材料全文朗读与 AI 真人语音播放控制器。
 * 支持阿里云 NLS AI 真人合成语音、倍速调节 (0.75x / 1.0x / 1.25x)，以及本地分句连续快速朗读。
 */

import { sameId } from '/src/shared/ids.js'

const RATE_STORAGE_KEY = 'learning.ruminationTtsRate'
const PLAYBACK_RATES = [1.0, 0.75, 1.25]

export function splitArticleSentences(articleText = '', practiceSentences = []) {
  const preSplit = Array.isArray(practiceSentences)
    ? practiceSentences.map((s) => String(s?.sentenceText || '').trim()).filter(Boolean)
    : []
  if (preSplit.length > 0) {
    return preSplit
  }
  const normalized = String(articleText || '')
    .replace(/\s+/g, ' ')
    .trim()
  if (!normalized) return []
  return normalized
    .split(/(?<=[.!?])\s+/)
    .map((s) => s.trim())
    .filter(Boolean)
}

export function createRuminationAudioController(options = {}) {
  const {
    elements = {},
    api = null,
    state = {},
    toast = () => {},
    speakSentence = () => {},
    stopSpeaking = () => {},
    getCurrentMaterial = () => null,
  } = options

  const materialAudioStates = new Map()
  let activeAudio = null
  let activeObjectUrl = null
  let activeMaterialId = null
  let pollTimer = null
  let pollMaterialId = null
  let pollAttempts = 0

  let localRunId = 0
  let localPlaying = false
  let localMaterialId = null

  function getAudioState(materialId) {
    const key = String(materialId || '')
    if (!key) return { status: 'idle', hasAudio: false, taskId: null }
    if (!materialAudioStates.has(key)) {
      materialAudioStates.set(key, { status: 'idle', hasAudio: false, taskId: null })
    }
    return materialAudioStates.get(key)
  }

  function getPlaybackRate() {
    if (typeof localStorage === 'undefined') return 1.0
    const saved = Number.parseFloat(localStorage.getItem(RATE_STORAGE_KEY))
    return PLAYBACK_RATES.includes(saved) ? saved : 1.0
  }

  function renderRateButton() {
    const btn = elements.ttsRateBtn
    if (!btn) return
    const rate = getPlaybackRate()
    btn.textContent = `${rate}x`
    btn.title = `当前倍速 ${rate}x，点击切换 (0.75x / 1.0x / 1.25x)`
  }

  function updateLocalButtonUi(isPlaying) {
    const btn = elements.speakArticleBtn
    if (!btn) return
    btn.textContent = isPlaying ? '⏹' : '▶'
    btn.title = isPlaying ? '停止本地朗读' : '本地实时分句朗读全文 / 再次点击停止'
    btn.setAttribute('aria-label', btn.title)
    btn.classList.toggle('playing', Boolean(isPlaying))
  }

  function updateAiButtonUi(materialId = getCurrentMaterial()?.id) {
    const btn = elements.ttsAudioBtn
    if (!btn) return
    const current = getCurrentMaterial()
    if (!materialId || !current || !sameId(current.id, materialId)) {
      btn.disabled = !current?.id
      btn.textContent = '🎙️ AI 真人朗读'
      btn.classList.remove('playing')
      return
    }
    const item = getAudioState(materialId)
    if (item.status === 'checking') {
      btn.disabled = true
      btn.textContent = '⏳ 检查音频中...'
      btn.classList.remove('playing')
    } else if (item.status === 'pending' || item.status === 'running') {
      btn.disabled = true
      btn.textContent = '⏳ 语音生成中...'
      btn.classList.remove('playing')
    } else if (item.status === 'playing' && activeAudio && !activeAudio.paused && sameId(activeMaterialId, materialId)) {
      btn.disabled = false
      btn.textContent = '⏸ 暂停朗读'
      btn.classList.add('playing')
    } else if (item.status === 'paused' && activeAudio && sameId(activeMaterialId, materialId)) {
      btn.disabled = false
      btn.textContent = '▶ 继续朗读'
      btn.classList.remove('playing')
    } else {
      btn.disabled = false
      btn.textContent = '🎙️ AI 真人朗读'
      btn.classList.remove('playing')
    }
  }

  function clearPolling() {
    if (pollTimer) {
      clearTimeout(pollTimer)
      pollTimer = null
    }
    pollMaterialId = null
    pollAttempts = 0
  }

  function stopAiAudio() {
    const prevId = activeMaterialId
    const audioToStop = activeAudio
    // 先解除 activeAudio 引用，避免清空 src 触发浏览器异步 error 事件误报播放失败
    activeAudio = null
    activeMaterialId = null
    if (audioToStop) {
      try {
        audioToStop.pause()
        if (typeof audioToStop.removeAttribute === 'function') {
          audioToStop.removeAttribute('src')
        } else {
          audioToStop.src = ''
        }
        audioToStop.load?.()
      } catch {
        /* ignore */
      }
    }
    if (activeObjectUrl && typeof URL !== 'undefined' && typeof URL.revokeObjectURL === 'function') {
      try {
        URL.revokeObjectURL(activeObjectUrl)
      } catch {
        /* ignore */
      }
      activeObjectUrl = null
    }
    activeMaterialId = null
    if (prevId) {
      const item = getAudioState(prevId)
      if (item.status === 'playing' || item.status === 'paused') {
        item.status = item.hasAudio ? 'completed' : 'idle'
      }
      updateAiButtonUi(prevId)
    }
  }

  function stopLocalQueue() {
    localRunId += 1
    if (localPlaying) {
      localPlaying = false
      localMaterialId = null
      try {
        stopSpeaking()
      } catch {
        /* ignore */
      }
      try {
        if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
          window.speechSynthesis.cancel()
        }
      } catch {
        /* ignore */
      }
    }
    updateLocalButtonUi(false)
  }

  function stopAll() {
    clearPolling()
    stopAiAudio()
    stopLocalQueue()
  }

  async function fetchAndPlayAiAudio(materialId) {
    const current = getCurrentMaterial()
    if (!current || !sameId(current.id, materialId)) return
    stopLocalQueue()
    stopAiAudio()

    const item = getAudioState(materialId)
    const base = state?.apiBase ? String(state.apiBase).replace(/\/$/, '') : ''
    const url = `${base}/api/v1/learning/rumination/materials/${encodeURIComponent(materialId)}/audio?t=${Date.now()}`
    const token = state?.token || (typeof localStorage !== 'undefined' ? localStorage.getItem('learning.token') : '') || ''
    const headers = token ? { Authorization: `Bearer ${token}` } : {}

    try {
      const resp = await fetch(url, { headers })
      if (!resp.ok) {
        throw new Error(`HTTP ${resp.status}`)
      }
      const blob = await resp.blob()
      const currentAfter = getCurrentMaterial()
      if (!currentAfter || !sameId(currentAfter.id, materialId)) return

      const objectUrl = URL.createObjectURL(blob)
      const audio = new Audio(objectUrl)
      audio.playbackRate = getPlaybackRate()
      activeObjectUrl = objectUrl
      activeAudio = audio
      activeMaterialId = materialId
      item.hasAudio = true
      item.status = 'playing'
      updateAiButtonUi(materialId)

      const targetAudio = audio
      targetAudio.addEventListener('play', () => {
        if (activeAudio !== targetAudio) return
        item.status = 'playing'
        updateAiButtonUi(materialId)
      })
      targetAudio.addEventListener('pause', () => {
        if (activeAudio !== targetAudio) return
        if (item.status === 'playing') {
          item.status = 'paused'
        }
        updateAiButtonUi(materialId)
      })
      targetAudio.addEventListener('ended', () => {
        if (activeAudio !== targetAudio) return
        item.status = 'completed'
        stopAiAudio()
      })
      targetAudio.addEventListener('error', () => {
        if (activeAudio !== targetAudio) return
        item.status = 'idle'
        stopAiAudio()
        if (sameId(getCurrentMaterial()?.id, materialId)) {
          toast('音频播放失败，可尝试点击右侧 ▶ 本地实时朗读')
        }
      })

      await audio.play()
    } catch {
      item.status = 'idle'
      stopAiAudio()
      if (sameId(getCurrentMaterial()?.id, materialId)) {
        toast('加载 AI 音频失败，可使用右侧 ▶ 本地实时朗读')
      }
    }
  }

  function startPolling(materialId) {
    clearPolling()
    pollMaterialId = materialId
    pollAttempts = 0

    const poll = async () => {
      if (!sameId(pollMaterialId, materialId) || !sameId(getCurrentMaterial()?.id, materialId)) {
        clearPolling()
        return
      }
      pollAttempts += 1
      if (pollAttempts > 45) {
        clearPolling()
        const item = getAudioState(materialId)
        item.status = 'idle'
        updateAiButtonUi(materialId)
        toast('语音合成超时，请稍后再试或使用 ▶ 本地实时朗读')
        return
      }
      try {
        const res = await api?.getRuminationMaterialAudioStatus?.(materialId)
        const item = getAudioState(materialId)
        if (res?.hasAudio || res?.taskStatus === 'completed') {
          clearPolling()
          item.hasAudio = true
          item.status = 'completed'
          updateAiButtonUi(materialId)
          await fetchAndPlayAiAudio(materialId)
          return
        }
        if (res?.taskStatus === 'failed' || res?.taskStatus === 'cancelled') {
          clearPolling()
          item.status = 'idle'
          updateAiButtonUi(materialId)
          toast(res?.errorMessage || 'AI 语音合成失败，可使用右侧 ▶ 本地实时朗读')
          return
        }
        item.status = res?.taskStatus || 'running'
        updateAiButtonUi(materialId)
        pollTimer = setTimeout(poll, 2200)
      } catch {
        clearPolling()
        const item = getAudioState(materialId)
        item.status = 'idle'
        updateAiButtonUi(materialId)
      }
    }

    pollTimer = setTimeout(poll, 2200)
  }

  async function toggleAiAudio() {
    const material = getCurrentMaterial()
    if (!material?.id) {
      toast('暂无可朗读的场景文章')
      return
    }
    if (state?.preview) {
      toast('预览模式暂不支持 AI 真人朗读')
      return
    }
    const item = getAudioState(material.id)
    if (activeAudio && sameId(activeMaterialId, material.id)) {
      if (!activeAudio.paused) {
        activeAudio.pause()
        item.status = 'paused'
        updateAiButtonUi(material.id)
      } else {
        item.status = 'playing'
        updateAiButtonUi(material.id)
        activeAudio.play().catch(() => {})
      }
      return
    }
    if (item.status === 'pending' || item.status === 'running') {
      toast('当前文章 AI 语音正在生成中，请稍候...')
      return
    }
    if (item.hasAudio || material.hasAudio || item.status === 'completed') {
      await fetchAndPlayAiAudio(material.id)
      return
    }

    stopLocalQueue()
    item.status = 'checking'
    updateAiButtonUi(material.id)

    try {
      const status = await api?.getRuminationMaterialAudioStatus?.(material.id)
      if (status?.hasAudio) {
        item.hasAudio = true
        item.status = 'completed'
        await fetchAndPlayAiAudio(material.id)
        return
      }
      if (status?.taskStatus === 'pending' || status?.taskStatus === 'running') {
        item.status = status.taskStatus
        item.taskId = status.taskId
        updateAiButtonUi(material.id)
        toast('当前文章语音正在后台生成中，请稍候...')
        startPolling(material.id)
        return
      }
      item.status = 'running'
      updateAiButtonUi(material.id)
      const submitted = await api?.generateRuminationMaterialAudioAsync?.(material.id, false)
      if (submitted?.hasAudio) {
        item.hasAudio = true
        item.status = 'completed'
        await fetchAndPlayAiAudio(material.id)
        return
      }
      item.taskId = submitted?.taskId
      item.status = submitted?.taskStatus || 'pending'
      updateAiButtonUi(material.id)
      toast('已提交全文 AI 语音合成任务，完成后将自动播放')
      startPolling(material.id)
    } catch (error) {
      item.status = 'idle'
      updateAiButtonUi(material.id)
      toast(error?.message || 'AI 真人朗读不可用，可点击右侧 ▶ 使用实时朗读')
    }
  }

  function cyclePlaybackRate() {
    const current = getPlaybackRate()
    const next = PLAYBACK_RATES[(PLAYBACK_RATES.indexOf(current) + 1) % PLAYBACK_RATES.length]
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(RATE_STORAGE_KEY, String(next))
    }
    renderRateButton()
    if (activeAudio) {
      activeAudio.playbackRate = next
    }
    toast(`朗读倍速已设为 ${next}x`)
  }

  function speakSentenceAndAwait(sentence, myRunId) {
    return new Promise((resolve) => {
      if (myRunId !== localRunId || typeof window === 'undefined') {
        resolve(false)
        return
      }
      let settled = false
      let started = false
      let guardTimer = null

      const finish = (ok) => {
        if (settled) return
        settled = true
        if (guardTimer) clearTimeout(guardTimer)
        window.removeEventListener('learning:sentence-play-state', onState)
        resolve(ok)
      }

      const onState = (event) => {
        if (myRunId !== localRunId) {
          finish(false)
          return
        }
        const detail = event?.detail || {}
        if (detail.playing) {
          started = true
          if (guardTimer) clearTimeout(guardTimer)
          guardTimer = setTimeout(() => finish(true), 25000)
          return
        }
        if (started || String(detail.sentence || '').trim() === sentence.trim()) {
          finish(true)
        }
      }

      window.addEventListener('learning:sentence-play-state', onState)
      guardTimer = setTimeout(() => finish(true), 3500)
      try {
        speakSentence(sentence)
      } catch {
        finish(false)
      }
    })
  }

  async function toggleLocalPlayback() {
    const material = getCurrentMaterial()
    if (!material?.articleContent) {
      toast('暂无可朗读的场景文章')
      return
    }
    if (localPlaying && sameId(localMaterialId, material.id)) {
      stopLocalQueue()
      toast('已停止本地朗读')
      return
    }

    stopAll()
    const sentences = splitArticleSentences(material.articleContent, material.practiceSentences)
    if (sentences.length === 0) {
      toast('暂无可朗读内容')
      return
    }

    const myRunId = ++localRunId
    localPlaying = true
    localMaterialId = material.id
    updateLocalButtonUi(true)

    for (const sentence of sentences) {
      if (myRunId !== localRunId || !sameId(getCurrentMaterial()?.id, material.id)) {
        break
      }
      await speakSentenceAndAwait(sentence, myRunId)
    }

    if (myRunId === localRunId) {
      localPlaying = false
      localMaterialId = null
      updateLocalButtonUi(false)
    }
  }

  function syncForMaterial(material) {
    stopAll()
    renderRateButton()
    if (material?.id && material.hasAudio) {
      const item = getAudioState(material.id)
      item.hasAudio = true
      if (item.status === 'idle') {
        item.status = 'completed'
      }
    }
    updateAiButtonUi(material?.id)
  }

  function bindEvents() {
    elements.ttsAudioBtn?.addEventListener('click', toggleAiAudio)
    elements.ttsRateBtn?.addEventListener('click', cyclePlaybackRate)
    elements.speakArticleBtn?.addEventListener('click', toggleLocalPlayback)
    renderRateButton()
  }

  bindEvents()

  return {
    syncForMaterial,
    toggleAiAudio,
    toggleLocalPlayback,
    cyclePlaybackRate,
    stopAll,
    getPlaybackRate,
  }
}
