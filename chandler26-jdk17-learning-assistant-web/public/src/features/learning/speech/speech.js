export function createSpeechFeature(ctx) {
  const { state, elements, request, toast, logEvent, clampNumber, renderLearningConfigSummary } = ctx

  const audioCache = new Map()
  const MAX_AUDIO_CACHE_SIZE = 120

  function pronunciationUrl(text, type = 'us') {
    const encoded = encodeURIComponent(text)
    const voice = type === 'uk' ? 'uk' : 'us'
    const base = state?.apiBase ? state.apiBase.replace(/\/$/, '') : ''
    return `${base}/api/v1/english/audio/${voice}/${encoded}`
  }

  function directYoudaoUrl(text, type = 'us') {
    const encoded = encodeURIComponent(text)
    const youdaoType = type === 'uk' ? 1 : 2
    return `https://dict.youdao.com/dictvoice?audio=${encoded}&type=${youdaoType}`
  }

  function getCachedAudio(content, voiceType = 'us') {
    const key = `${voiceType}:${content.toLowerCase()}`
    let audio = audioCache.get(key)
    if (!audio && typeof Audio !== 'undefined') {
      try {
        audio = new Audio(pronunciationUrl(content, voiceType))
        audio.preload = 'auto'
        if (audioCache.size >= MAX_AUDIO_CACHE_SIZE) {
          const oldestKey = audioCache.keys().next().value
          audioCache.delete(oldestKey)
        }
        audioCache.set(key, audio)
      } catch {
        /* ignore */
      }
    }
    return audio
  }

  function preloadAudio(text, voiceType = currentVoiceType()) {
    const content = String(text || '').trim()
    if (!content) return
    getCachedAudio(content, voiceType)
    // 触发后端异步预拉取
    try {
      fetch(pronunciationUrl(content, voiceType), { method: 'GET' }).catch(() => {})
    } catch {
      /* ignore */
    }
  }

  function speak(text, voiceType = currentVoiceType()) {
    const content = String(text || '').trim()
    if (!content) {
      toast('暂无可播放内容')
      return
    }

    const voice = voiceType === 'uk' ? 'uk' : 'us'
    const backendUrl = pronunciationUrl(content, voice)
    const directUrl = directYoudaoUrl(content, voice)

    let fallbackDone = false
    const playDirectYoudao = () => {
      if (fallbackDone) return
      fallbackDone = true
      try {
        const directAudio = new Audio(directUrl)
        directAudio.play().then(() => {
          toast(`正在播放有道${voice === 'uk' ? '英音' : '美音'}发音`)
        }).catch(() => {
          toast('音频播放受阻，请检查网络或浏览器声音权限')
        })
        // 触发后端缺省补充持久化保存
        fetch(backendUrl, { method: 'GET' }).catch(() => {})
      } catch {
        toast('发音播放异常')
      }
    }

    try {
      const backendAudio = new Audio(backendUrl)
      let timeoutId = window.setTimeout(() => {
        if (backendAudio.paused || backendAudio.currentTime === 0) {
          try {
            backendAudio.pause()
          } catch {
            /* ignore */
          }
          playDirectYoudao()
        }
      }, 700)

      backendAudio.addEventListener('playing', () => {
        if (timeoutId) {
          window.clearTimeout(timeoutId)
          timeoutId = null
        }
        toast(`正在播放${voice === 'uk' ? '英音' : '美音'}发音`)
      }, { once: true })

      backendAudio.addEventListener('error', () => {
        if (timeoutId) {
          window.clearTimeout(timeoutId)
          timeoutId = null
        }
        playDirectYoudao()
      }, { once: true })

      backendAudio.play().catch(() => {
        if (timeoutId) {
          window.clearTimeout(timeoutId)
          timeoutId = null
        }
        playDirectYoudao()
      })
    } catch {
      playDirectYoudao()
    }
  }

  function playRemoteAudioByType(content, voiceType = currentVoiceType()) {
    return speak(content, voiceType)
  }

  function playRemoteAudio(content) {
    return speak(content, currentVoiceType())
  }

  function speakSentence(text) {
    const content = String(text || '').trim()
    if (!content) {
      toast('暂无可播放内容')
      return
    }
    if ('speechSynthesis' in window && 'SpeechSynthesisUtterance' in window) {
      speakWithBrowserVoice(content, currentVoiceType(), { sentence: true })
      return
    }
    speak(content, currentVoiceType())
  }

  function speakWithBrowserVoice(content, voiceType = currentVoiceType(), options = {}) {
    if (!('speechSynthesis' in window && 'SpeechSynthesisUtterance' in window)) return
    window.speechSynthesis.cancel()
    const utterance = new SpeechSynthesisUtterance(content)
    utterance.lang = voiceType === 'uk' ? 'en-GB' : 'en-US'
    const voice = chooseSpeechVoice(voiceType, options.sentence ? state.speechSettings.sentenceVoiceName : '')
    if (voice) {
      utterance.voice = voice
      utterance.lang = voice.lang || utterance.lang
    }
    utterance.rate = options.sentence ? state.speechSettings.sentenceRate : 0.86
    utterance.pitch = options.sentence ? state.speechSettings.sentencePitch : 1
    utterance.onerror = () => toast('浏览器阻止了音频播放，请检查站点声音权限')
    window.speechSynthesis.speak(utterance)
    toast('正在播放发音')
  }

  function currentVoiceType(voiceType = state.speechSettings.voiceType) {
    return voiceType === 'uk' ? 'uk' : 'us'
  }

  function normalizeSpeechSettings(settings = {}) {
    return {
      voiceType: currentVoiceType(settings.voiceType),
      sentenceVoiceName: String(settings.sentenceVoiceName || '').trim(),
      sentenceRate: clampNumber(settings.sentenceRate, 0.55, 1.15, 0.78),
      sentencePitch: clampNumber(settings.sentencePitch, 0.8, 1.2, 1),
    }
  }

  function persistSpeechSettingsLocal() {
    localStorage.setItem('learning.voiceType', state.speechSettings.voiceType)
    localStorage.setItem('learning.sentenceVoiceName', state.speechSettings.sentenceVoiceName)
    localStorage.setItem('learning.sentenceRate', String(state.speechSettings.sentenceRate))
    localStorage.setItem('learning.sentencePitch', String(state.speechSettings.sentencePitch))
  }

  function speechPreferencePayload() {
    return {
      voiceType: state.speechSettings.voiceType,
      sentenceVoiceName: state.speechSettings.sentenceVoiceName,
      sentenceRate: state.speechSettings.sentenceRate,
      sentencePitch: state.speechSettings.sentencePitch,
    }
  }

  function applySpeechSettings(settings = {}) {
    state.speechSettings = normalizeSpeechSettings({ ...state.speechSettings, ...settings })
    if (elements.voiceSelect) {
      elements.voiceSelect.value = currentVoiceType()
    }
    if (elements.sentenceRateInput) {
      elements.sentenceRateInput.value = String(state.speechSettings.sentenceRate)
    }
    if (elements.sentencePitchInput) {
      elements.sentencePitchInput.value = String(state.speechSettings.sentencePitch)
    }
    populateSentenceVoices()
    renderSpeechSettingValues()
    renderLearningConfigSummary?.()
  }

  async function loadSpeechPreferences() {
    if (state.preview || !state.token) {
      applySpeechSettings(state.speechSettings)
      return
    }
    try {
      const preferences = await request('/api/v1/learning/preferences/speech')
      applySpeechSettings(preferences || {})
      persistSpeechSettingsLocal()
    } catch (error) {
      applySpeechSettings(state.speechSettings)
      logEvent('error', '发音设置加载失败', error.message)
    }
  }

  async function saveSpeechPreferences({ quiet = false } = {}) {
    persistSpeechSettingsLocal()
    if (state.preview || !state.token) return true
    try {
      const preferences = await request('/api/v1/learning/preferences/speech', {
        method: 'PUT',
        body: JSON.stringify(speechPreferencePayload()),
      })
      applySpeechSettings(preferences || {})
      persistSpeechSettingsLocal()
      renderLearningConfigSummary?.()
      if (!quiet) toast('发音设置已保存')
      return true
    } catch (error) {
      logEvent('error', '发音设置保存失败', error.message)
      if (!quiet) toast(`发音设置暂未保存到服务端：${error.message}`)
      return false
    }
  }

  function scheduleSpeechPreferenceSave() {
    persistSpeechSettingsLocal()
    window.clearTimeout(state.speechSaveTimer)
    state.speechSaveTimer = window.setTimeout(() => {
      saveSpeechPreferences({ quiet: true })
    }, 500)
  }

  function initSpeechSettings() {
    applySpeechSettings(state.speechSettings)
    if (elements.voiceSelect) {
      elements.voiceSelect.addEventListener('change', () => {
        state.speechSettings.voiceType = currentVoiceType(elements.voiceSelect.value)
        populateSentenceVoices()
        renderLearningConfigSummary?.()
        scheduleSpeechPreferenceSave()
      })
    }
    if (elements.sentenceRateInput) {
      elements.sentenceRateInput.addEventListener('input', () => {
        state.speechSettings.sentenceRate = clampNumber(elements.sentenceRateInput.value, 0.55, 1.15, 0.78)
        renderSpeechSettingValues()
        renderLearningConfigSummary?.()
        scheduleSpeechPreferenceSave()
      })
    }
    if (elements.sentencePitchInput) {
      elements.sentencePitchInput.addEventListener('input', () => {
        state.speechSettings.sentencePitch = clampNumber(elements.sentencePitchInput.value, 0.8, 1.2, 1)
        renderSpeechSettingValues()
        renderLearningConfigSummary?.()
        scheduleSpeechPreferenceSave()
      })
    }
    elements.sentenceVoiceSelect?.addEventListener('change', () => {
      state.speechSettings.sentenceVoiceName = elements.sentenceVoiceSelect.value
      renderLearningConfigSummary?.()
      scheduleSpeechPreferenceSave()
    })
    elements.testSentenceVoiceBtn?.addEventListener('click', () => {
      speakSentence('They had to abandon the project due to lack of funds.')
    })
    if ('speechSynthesis' in window) {
      window.speechSynthesis.addEventListener?.('voiceschanged', populateSentenceVoices)
      window.setTimeout(populateSentenceVoices, 250)
    }
    populateSentenceVoices()
    renderSpeechSettingValues()
  }

  function renderSpeechSettingValues() {
    if (elements.sentenceRateValue) {
      elements.sentenceRateValue.textContent = `${state.speechSettings.sentenceRate.toFixed(2)}x`
    }
    if (elements.sentencePitchValue) {
      elements.sentencePitchValue.textContent = state.speechSettings.sentencePitch.toFixed(2)
    }
  }

  function populateSentenceVoices() {
    if (!elements.sentenceVoiceSelect) return
    const voices = speechVoices().filter((voice) => voice.lang?.toLowerCase().startsWith('en'))
    const voiceType = currentVoiceType()
    const preferred = voices.filter((voice) => voiceMatchesAccent(voice, voiceType))
    const others = voices.filter((voice) => !voiceMatchesAccent(voice, voiceType))
    elements.sentenceVoiceSelect.innerHTML = ''
    elements.sentenceVoiceSelect.appendChild(new Option('自动选择更自然的英语音色', ''))
    for (const voice of [...preferred, ...others]) {
      elements.sentenceVoiceSelect.appendChild(new Option(voiceLabel(voice), voice.name))
    }
    if ([...elements.sentenceVoiceSelect.options].some((option) => option.value === state.speechSettings.sentenceVoiceName)) {
      elements.sentenceVoiceSelect.value = state.speechSettings.sentenceVoiceName
    } else {
      elements.sentenceVoiceSelect.value = ''
    }
  }

  function speechVoices() {
    return 'speechSynthesis' in window ? window.speechSynthesis.getVoices() : []
  }

  function chooseSpeechVoice(voiceType, preferredName = '') {
    const voices = speechVoices()
    if (!voices.length) return null
    if (preferredName) {
      const selected = voices.find((voice) => voice.name === preferredName)
      if (selected) return selected
    }
    const matching = voices.filter((voice) => voiceMatchesAccent(voice, voiceType))
    return (
      matching.find((voice) => /natural|premium|google|samantha|daniel|aria|jenny/i.test(voice.name)) ||
      matching[0] ||
      voices.find((voice) => voice.lang?.toLowerCase().startsWith('en')) ||
      null
    )
  }

  function voiceMatchesAccent(voice, voiceType) {
    const lang = String(voice.lang || '').toLowerCase()
    return currentVoiceType(voiceType) === 'uk' ? lang.startsWith('en-gb') : lang.startsWith('en-us')
  }

  function voiceLabel(voice) {
    const local = voice.localService ? '本地' : '在线'
    return `${voice.name} · ${voice.lang || 'en'} · ${local}`
  }

  return {
    pronunciationUrl,
    preloadAudio,
    playRemoteAudio,
    playRemoteAudioByType,
    speak,
    speakSentence,
    speakWithBrowserVoice,
    currentVoiceType,
    normalizeSpeechSettings,
    persistSpeechSettingsLocal,
    speechPreferencePayload,
    applySpeechSettings,
    loadSpeechPreferences,
    saveSpeechPreferences,
    scheduleSpeechPreferenceSave,
    initSpeechSettings,
    renderSpeechSettingValues,
    populateSentenceVoices,
    speechVoices,
    chooseSpeechVoice,
    voiceMatchesAccent,
    voiceLabel,
  }
}
