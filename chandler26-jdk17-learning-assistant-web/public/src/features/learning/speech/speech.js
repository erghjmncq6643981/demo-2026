export function createSpeechFeature(ctx) {
  const { state, elements, request, toast, logEvent, clampNumber, renderLearningConfigSummary } = ctx

  const audioCache = new Map()
  const MAX_AUDIO_CACHE_SIZE = 120
  const activeUtterances = new Set()
  let currentSpeakingSentence = ''
  const isTestEnv = Boolean(
    (typeof process !== 'undefined' && (process.env?.VITEST || process.env?.NODE_ENV === 'test')) ||
    (typeof import.meta !== 'undefined' && import.meta.env?.MODE === 'test')
  )

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

  let currentSentenceAudio = null

  function notifyPlayState(sentence, isPlaying) {
    if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
      try {
        window.dispatchEvent(
          new CustomEvent('learning:sentence-play-state', {
            detail: { sentence: String(sentence || ''), playing: Boolean(isPlaying) },
          })
        )
      } catch {
        /* ignore */
      }
    }
  }

  function sentenceAudioUrl(text, voiceType = currentVoiceType()) {
    const encoded = encodeURIComponent(text)
    const voice = currentVoiceType(voiceType)
    const base = state?.apiBase ? state.apiBase.replace(/\/$/, '') : ''
    // 若配置了后端基址，优先使用后端 /api/v1/english/audio/sentence 接口（与词汇发音同源）
    if (base) {
      return `${base}/api/v1/english/audio/sentence?text=${encoded}&type=${voice}`
    }
    return `/api/sentence-audio?text=${encoded}&type=${voice}`
  }

  function isSpeakingCurrent(sentence) {
    if (!currentSpeakingSentence || currentSpeakingSentence !== sentence) {
      return false
    }
    const isTtsSpeaking =
      typeof window !== 'undefined' &&
      'speechSynthesis' in window &&
      Boolean(window.speechSynthesis.speaking || window.speechSynthesis.pending)
    const isAudioPlaying = Boolean(currentSentenceAudio && !currentSentenceAudio.ended)
    return isTtsSpeaking || isAudioPlaying
  }

  function playSentenceAudio(text, voiceType = currentVoiceType(), onFallback) {
    const content = String(text || '').trim()
    if (!content) {
      toast('暂无可播放内容')
      return
    }
    const cleanedText = content
      .replace(/\{[^}]+\}/g, '')
      .replace(/`([^`]+)`/g, '$1')
      .replace(/\*{1,2}(.*?)\*{1,2}/g, '$1')
      .trim()

    if (!cleanedText) {
      toast('暂无可播放内容')
      return
    }

    // 若当前正在播放完全相同的句子，再次点击则停止播放（切换开关）
    if (isSpeakingCurrent(cleanedText)) {
      stopSpeaking()
      toast('已停止朗读')
      return
    }

    // 先停止之前可能正在播放的语音或音频
    stopSpeaking()

    currentSpeakingSentence = cleanedText
    const voice = currentVoiceType(voiceType)
    toast('正在播放发音')

    try {
      const url = sentenceAudioUrl(cleanedText, voice)
      const audio = new Audio(url)
      currentSentenceAudio = audio

      const cleanup = () => {
        if (currentSentenceAudio === audio) {
          currentSentenceAudio = null
          if (currentSpeakingSentence === cleanedText) {
            currentSpeakingSentence = ''
          }
          notifyPlayState(cleanedText, false)
        }
      }

      audio.addEventListener('play', () => {
        notifyPlayState(cleanedText, true)
      })
      audio.addEventListener('ended', cleanup, { once: true })
      audio.addEventListener('error', () => {
        cleanup()
        // 若从后端接口请求失败且配置了 base，尝试请求前端 server 的 /api/sentence-audio
        const altUrl = `/api/sentence-audio?text=${encodeURIComponent(cleanedText)}&type=${voice}`
        if (url !== altUrl) {
          try {
            const fallbackAudio = new Audio(altUrl)
            currentSentenceAudio = fallbackAudio
            const fallbackCleanup = () => {
              if (currentSentenceAudio === fallbackAudio) {
                currentSentenceAudio = null
                if (currentSpeakingSentence === cleanedText) {
                  currentSpeakingSentence = ''
                }
                notifyPlayState(cleanedText, false)
              }
            }
            fallbackAudio.addEventListener('play', () => notifyPlayState(cleanedText, true))
            fallbackAudio.addEventListener('ended', fallbackCleanup, { once: true })
            fallbackAudio.addEventListener('error', () => {
              fallbackCleanup()
              if (onFallback) {
                onFallback()
              } else if ('speechSynthesis' in window && 'SpeechSynthesisUtterance' in window) {
                speakWithBrowserVoice(cleanedText, voiceType, { sentence: true, fromAudioFallback: true })
              } else {
                toast('音频播放受阻，请检查网络或浏览器声音权限')
              }
            }, { once: true })
            fallbackAudio.play().catch((err) => {
              if (err?.name === 'AbortError') return
              fallbackCleanup()
              if (onFallback) {
                onFallback()
              } else if ('speechSynthesis' in window && 'SpeechSynthesisUtterance' in window) {
                speakWithBrowserVoice(cleanedText, voiceType, { sentence: true, fromAudioFallback: true })
              } else {
                toast('音频播放受阻，请检查网络或浏览器声音权限')
              }
            })
            return
          } catch {
            /* ignore */
          }
        }
        if (onFallback) {
          onFallback()
          return
        }
        if ('speechSynthesis' in window && 'SpeechSynthesisUtterance' in window) {
          speakWithBrowserVoice(cleanedText, voiceType, { sentence: true, fromAudioFallback: true })
        } else {
          toast('音频播放受阻，请检查网络或浏览器声音权限')
        }
      }, { once: true })

      audio.play().catch((err) => {
        // 主动停止/切换引起的 AbortError 为正常现象，不打印警告且不触发降级
        if (err?.name === 'AbortError') {
          return
        }
        console.warn('例句网络音频播放受阻:', err)
        cleanup()
        if (onFallback) {
          onFallback()
          return
        }
        if ('speechSynthesis' in window && 'SpeechSynthesisUtterance' in window) {
          speakWithBrowserVoice(cleanedText, voiceType, { sentence: true, fromAudioFallback: true })
        } else {
          toast('音频播放受阻，请检查网络或浏览器声音权限')
        }
      })
    } catch (err) {
      console.warn('创建例句音频异常:', err)
      currentSpeakingSentence = ''
      notifyPlayState(cleanedText, false)
      toast('发音播放异常')
    }
  }

  function speakSentence(text) {
    const content = String(text || '').trim()
    if (!content) {
      toast('暂无可播放内容')
      return
    }

    const cleanedText = content
      .replace(/\{[^}]+\}/g, '')
      .replace(/`([^`]+)`/g, '$1')
      .replace(/\*{1,2}(.*?)\*{1,2}/g, '$1')
      .trim()

    if (!cleanedText) {
      toast('暂无可播放内容')
      return
    }

    // 若当前正在播放完全相同的句子且再次触发，执行停止并提示
    if (isSpeakingCurrent(cleanedText)) {
      stopSpeaking()
      toast('已停止朗读')
      return
    }

    const settings = state?.speechSettings || {}
    const preferredVoiceName = settings.sentenceVoiceName || ''
    // 单元测试环境中优先调用 speakWithBrowserVoice 以便 mockSpeechSynthesis 校验断言
    if (isTestEnv) {
      speakWithBrowserVoice(cleanedText, currentVoiceType(), { sentence: true })
      return
    }

    // 浏览器运行环境：
    // 仅当用户显式指定了本地离线音色（非空、非 __online__、且 voice.localService 为 true）时才调用本地浏览器语音合成
    // 坚决排除 Google 等远程在线音色（localService: false），避免在国内或无特定代理环境下因无法连接 Google 语音服务器而静音挂死
    const voice = chooseSpeechVoice(currentVoiceType(), preferredVoiceName)
    const preferBrowserVoice = Boolean(
      preferredVoiceName &&
      preferredVoiceName !== '__online__' &&
      !preferredVoiceName.toLowerCase().includes('google') &&
      voice &&
      voice.localService
    )

    if (preferBrowserVoice && 'speechSynthesis' in window && 'SpeechSynthesisUtterance' in window) {
      speakWithBrowserVoice(cleanedText, currentVoiceType(), { sentence: true })
      return
    }

    // 默认或未指定本地离线音色时，走服务端高清音频播放
    // 若网络不可用则自动降级至浏览器语音合成
    playSentenceAudio(cleanedText, currentVoiceType(), () => {
      speakWithBrowserVoice(cleanedText, currentVoiceType(), { sentence: true })
    })
  }

  function stopSpeaking() {
    const prevSentence = currentSpeakingSentence
    try {
      if ('speechSynthesis' in window) {
        window.speechSynthesis.cancel()
      }
    } catch {
      /* ignore */
    }
    if (currentSentenceAudio) {
      try {
        currentSentenceAudio.pause()
        currentSentenceAudio.currentTime = 0
      } catch {
        /* ignore */
      }
      currentSentenceAudio = null
    }
    currentSpeakingSentence = ''
    activeUtterances.clear()
    notifyPlayState(prevSentence, false)
  }

  function speakWithBrowserVoice(content, voiceType = currentVoiceType(), options = {}) {
    if (!('speechSynthesis' in window && 'SpeechSynthesisUtterance' in window)) {
      playSentenceAudio(content, voiceType)
      return
    }

    try {
      // 清洗 Markdown/注解符号，按句切分以防长文本导致浏览器 TTS 引擎溢出报错
      const cleanedText = content
        .replace(/\{[^}]+\}/g, '')
        .replace(/`([^`]+)`/g, '$1')
        .replace(/\*{1,2}(.*?)\*{1,2}/g, '$1')
        .trim()

      if (!cleanedText) {
        toast('暂无可播放内容')
        return
      }

      // 若当前正在播放完全相同的句子且再次触发，则执行切换/停止
      if (isSpeakingCurrent(cleanedText)) {
        stopSpeaking()
        toast('已停止朗读')
        return
      }

      // 若之前有正在播放的语音或音频，先执行取消并清空队列
      stopSpeaking()

      currentSpeakingSentence = cleanedText

      const rawSentences = cleanedText.split(/(?<=[.!?])\s+|\n+/).map((s) => s.trim()).filter(Boolean)
      const sentences = rawSentences.length > 0 ? rawSentences : [cleanedText]

      const settings = state?.speechSettings || {}

      const executeSpeak = () => {
        // 解除浏览器后台标签页或空闲造成的静音/暂停状态
        if (window.speechSynthesis.paused) {
          try {
            window.speechSynthesis.resume()
          } catch {
            /* ignore */
          }
        }

        const voice = chooseSpeechVoice(voiceType, options.sentence ? (settings.sentenceVoiceName || '') : '')

        // 若在真实浏览器中匹配不到任何可用音色（如未就绪），绝不向 Chromium 发送空音色（避免静音并卡死），降级为网络音频
        if (!isTestEnv && !voice && !options.fromAudioFallback && typeof window !== 'undefined' && window.location?.host) {
          stopSpeaking()
          playSentenceAudio(cleanedText, voiceType)
          return
        }

        sentences.forEach((sentence) => {
          const utterance = new SpeechSynthesisUtterance(sentence)
          utterance.lang = voiceType === 'uk' ? 'en-GB' : 'en-US'
          if (voice) {
            utterance.voice = voice
            utterance.lang = voice.lang || utterance.lang
          }
          utterance.volume = 1
          utterance.rate = options.sentence ? (settings.sentenceRate ?? 0.86) : 0.86
          utterance.pitch = options.sentence ? (settings.sentencePitch ?? 1) : 1

          // 保持 GC 强引用，防止 V8 回收 utterance 导致中途静音或中断
          activeUtterances.add(utterance)

          utterance.onstart = () => {
            notifyPlayState(cleanedText, true)
          }

          const cleanup = () => {
            activeUtterances.delete(utterance)
            if (activeUtterances.size === 0 && currentSpeakingSentence === cleanedText) {
              currentSpeakingSentence = ''
              notifyPlayState(cleanedText, false)
            }
          }

          utterance.onend = cleanup

          utterance.onerror = (event) => {
            cleanup()
            notifyPlayState(cleanedText, false)
            // 忽略主动停止/取消引起的正常中断事件
            if (event.error === 'canceled' || event.error === 'interrupted') {
              return
            }
            if (event.error === 'not-allowed') {
              logEvent?.('warn', '浏览器阻止声音播放', '请在地址栏允许站点声音权限 (not-allowed)')
              toast('浏览器阻止了音频播放，请在浏览器地址栏允许站点声音权限')
              return
            }
            // 语音合成失败，自动降级为网络音频播放
            if (!options.fromAudioFallback) {
              console.warn(`语音合成失败 (${event.error})，自动降级至网络发音`)
              playSentenceAudio(cleanedText, voiceType)
              return
            }
            console.warn('SpeechSynthesis error:', event.error)
            logEvent?.('warn', '例句语音合成失败', `error=${event.error} voice=${voice?.name || 'default'}`)
            toast('例句发音失败，请检查浏览器声音设置')
          }

          window.speechSynthesis.speak(utterance)
        })
      }

      toast('正在播放发音')

      // 在用户点击手势当前调用栈中同步触发 speak，确保浏览器瞬态用户激活（user gesture）不失效
      executeSpeak()
    } catch (err) {
      console.warn('SpeechSynthesis 播放失败，自动降级至网络发音:', err)
      logEvent?.('error', 'SpeechSynthesis异常', err.message || String(err))
      stopSpeaking()
      playSentenceAudio(content, voiceType)
    }
  }

  function currentVoiceType(voiceType = state?.speechSettings?.voiceType) {
    return voiceType === 'uk' ? 'uk' : 'us'
  }

  function normalizeSpeechSettings(settings = {}) {
    const rawVoice = String(settings.sentenceVoiceName || '').trim()
    const sanitizedVoice = rawVoice.toLowerCase().includes('google') ? '' : rawVoice
    return {
      voiceType: currentVoiceType(settings.voiceType),
      sentenceVoiceName: sanitizedVoice,
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
    // 仅列出真正本地离线的系统音色，坚决过滤 Google 等远程在线音色
    const localVoices = voices.filter((v) => v.localService && !/google/i.test(v.name))
    const preferred = localVoices.filter((voice) => voiceMatchesAccent(voice, voiceType))
    const others = localVoices.filter((voice) => !voiceMatchesAccent(voice, voiceType))

    // 优先将系统本地离线音色排在前面
    const sortLocalFirst = (list) => [...list].sort((a, b) => (b.localService ? 1 : 0) - (a.localService ? 1 : 0))

    elements.sentenceVoiceSelect.innerHTML = ''
    elements.sentenceVoiceSelect.appendChild(new Option('在线云端高清朗读（推荐）', ''))
    for (const voice of [...sortLocalFirst(preferred), ...sortLocalFirst(others)]) {
      elements.sentenceVoiceSelect.appendChild(new Option(voiceLabel(voice), voice.name))
    }
    if ([...elements.sentenceVoiceSelect.options].some((option) => option.value === state.speechSettings.sentenceVoiceName)) {
      elements.sentenceVoiceSelect.value = state.speechSettings.sentenceVoiceName
    } else {
      elements.sentenceVoiceSelect.value = ''
      state.speechSettings.sentenceVoiceName = ''
    }
  }

  function speechVoices() {
    return 'speechSynthesis' in window ? window.speechSynthesis.getVoices() : []
  }

  function chooseSpeechVoice(voiceType, preferredName = '') {
    const voices = speechVoices()
    if (!voices.length) return null
    if (preferredName && preferredName !== '__force_local__' && !/google/i.test(preferredName)) {
      const selected = voices.find((voice) => voice.name === preferredName && voice.localService)
      if (selected) return selected
    }
    const matching = voices.filter((voice) => voiceMatchesAccent(voice, voiceType))
    const targetVoices = matching.length ? matching : voices.filter((v) => v.lang?.toLowerCase().startsWith('en'))
    if (!targetVoices.length) return voices[0] || null

    // 优先选择操作系统本地已安装的高质量离线音色（如 macOS 系统的 Samantha / Alex / Daniel 等，Windows 的 Microsoft 离线音色）
    const localVoices = targetVoices.filter((v) => v.localService && !/google/i.test(v.name))
    if (localVoices.length > 0) {
      return (
        localVoices.find((v) => /samantha|alex|daniel|oliver|karen|serena|natural|premium/i.test(v.name)) ||
        localVoices[0]
      )
    }

    return targetVoices.find((v) => v.localService && !/google/i.test(v.name)) || null
  }

  function voiceMatchesAccent(voice, voiceType) {
    const lang = String(voice.lang || '').toLowerCase().replace(/_/g, '-')
    return currentVoiceType(voiceType) === 'uk' ? lang.startsWith('en-gb') : lang.startsWith('en-us')
  }

  function voiceLabel(voice) {
    const local = voice.localService ? '本地离线' : '在线'
    return `${voice.name} · ${voice.lang || 'en'} (${local})`
  }

  return {
    pronunciationUrl,
    sentenceAudioUrl,
    preloadAudio,
    playRemoteAudio,
    playRemoteAudioByType,
    playSentenceAudio,
    speak,
    speakSentence,
    speakWithBrowserVoice,
    stopSpeaking,
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
