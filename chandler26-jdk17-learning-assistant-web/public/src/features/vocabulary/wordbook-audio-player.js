/**
 * 单词本顺序播放 / 暂停控制器（磨耳朵随身听模式）。
 *
 * 支持：
 * 1. 顺序或随机朗读单词（严格等待发音 Promise 完成 -> 真实思考跟读留白 -> 可选中文释义 -> 词间缓冲 -> 下一个词）；
 * 2. 杜绝多声音并发（发音前严格停止旧音，暂停/停止立即静音并销毁 Audio）；
 * 3. 灵活调节播放模式（纯英文磨耳朵 vs 英汉双播）、停顿节奏（紧凑/标准/宽松）及播放方式（顺序 vs 随机）；
 * 4. 支持上一个词 ⏮（支持随机历史回退）/ 下一个词 ⏭ 手动切歌式跳读；
 * 5. 正在朗读的行添加高亮光晕并自动平滑滚动进入视口；
 * 6. 随时暂停与恢复（支持点击与空格键快捷操控）。
 */

export function createWordbookAudioPlayer({
  getEntries,
  speakWord,
  speakTerm,
  speakMeaning,
  stopSpeaking,
  onRowChange,
  onStatusChange,
  onStateChange,
  elements,
  toast,
  termPauseMs: customTermPauseMs,
  meaningPauseMs: customMeaningPauseMs,
  nextWordPauseMs: customNextWordPauseMs,
  initialOptions = {},
}) {
  let status = 'IDLE' // 'IDLE' | 'PLAYING' | 'PAUSED'
  let currentIndex = 0
  let playToken = 0 // 用于防止异步竞争与取消残留
  let customEntries = null
  let playHistory = [] // 播放历史栈，用于在随机/顺序模式下点击 prev() 准确定位上一个词

  const options = {
    mode: initialOptions.mode || (speakMeaning ? 'term_and_meaning' : 'term_only'), // 'term_only' | 'term_and_meaning'
    intervalPace: initialOptions.intervalPace || 'normal', // 'normal' | 'compact' | 'relaxed'
    order: initialOptions.order || 'sequence', // 'sequence' | 'random'
    repeatCount: Math.max(1, Math.min(3, Number(initialOptions.repeatCount) || 1)),
    ...initialOptions,
  }

  const wordSpeaker = speakWord || speakTerm
  const handleStatusChange = onStatusChange || onStateChange

  const defaultRowChange = (entry) => {
    if (!elements?.wordbookEntryList) return
    const rows = elements.wordbookEntryList.querySelectorAll('tr.wordbook-row')
    rows?.forEach((r) => r.classList.remove('wordbook-row-speaking'))
    if (entry) {
      const targetRow = elements.wordbookEntryList.querySelector(`tr[data-entry-id="${entry.id}"]`)
      targetRow?.classList.add('wordbook-row-speaking')
    }
  }
  const handleRowChange = onRowChange || defaultRowChange

  function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms))
  }

  function resolvePaceDelays() {
    // 若外部单元测试显式传入了固定 pause 毫秒数，优先保留以确保向后兼容
    if (typeof customTermPauseMs === 'number' || typeof customNextWordPauseMs === 'number') {
      return {
        termPauseMs: typeof customTermPauseMs === 'number' ? customTermPauseMs : 1500,
        meaningPauseMs: typeof customMeaningPauseMs === 'number' ? customMeaningPauseMs : 1200,
        nextWordPauseMs: typeof customNextWordPauseMs === 'number' ? customNextWordPauseMs : 800,
      }
    }

    switch (options.intervalPace) {
      case 'compact':
        return { termPauseMs: 1200, meaningPauseMs: 800, nextWordPauseMs: 1000 }
      case 'relaxed':
        return { termPauseMs: 3000, meaningPauseMs: 1600, nextWordPauseMs: 2000 }
      case 'normal':
      default:
        return { termPauseMs: 2000, meaningPauseMs: 1200, nextWordPauseMs: 1500 }
    }
  }

  function cleanChineseMeaning(entry) {
    if (!entry) return ''
    const raw =
      entry.meaningText ||
      entry.definition ||
      entry.parsed?.definitions?.[0]?.cn ||
      entry.parsed?.definitions?.[0]?.meaning ||
      ''
    if (!raw) return ''
    return String(raw)
      .replace(/^[a-zA-Z]+\.\s*/, '') // 移除词性前缀如 n. v. adj.
      .split(/[；;，,]/)[0] // 仅取前项核心释义，短小精炼适合朗读
      .trim()
  }

  function resolveEntries() {
    if (customEntries && Array.isArray(customEntries)) {
      return customEntries
    }
    return typeof getEntries === 'function' ? getEntries() : []
  }

  function pickNextIndex(entries, current) {
    if (!entries || entries.length <= 1) return 0
    if (options.order === 'random') {
      let nextIdx = current
      let attempts = 0
      while (nextIdx === current && attempts < 10) {
        nextIdx = Math.floor(Math.random() * entries.length)
        attempts++
      }
      return nextIdx
    }
    return current + 1
  }

  function notifyStatus() {
    const entries = resolveEntries()
    const entry = entries[currentIndex] || null
    handleStatusChange?.({
      status,
      currentIndex,
      total: entries.length,
      entry,
      options: { ...options },
    })
  }

  async function playLoop(token) {
    while (token === playToken && status === 'PLAYING') {
      const entries = resolveEntries()
      if (!entries || currentIndex >= entries.length) {
        stop()
        toast?.('本页单词已全部朗读完毕')
        return
      }

      const entry = entries[currentIndex]
      const term = entry.term || entry.normalizedTerm || ''
      const meaning = cleanChineseMeaning(entry)

      // 记录历史访问
      if (!playHistory.length || playHistory[playHistory.length - 1] !== currentIndex) {
        playHistory.push(currentIndex)
      }

      handleRowChange?.(entry, currentIndex)
      notifyStatus()

      const { termPauseMs, meaningPauseMs, nextWordPauseMs } = resolvePaceDelays()
      const repeatTimes = Math.max(1, Math.min(3, Number(options.repeatCount) || 1))

      // 1. 播放英文单词发音（支持循环 1~2 遍）
      for (let r = 0; r < repeatTimes; r++) {
        if (token !== playToken || status !== 'PLAYING') break
        if (typeof wordSpeaker === 'function') {
          await wordSpeaker(term, { silentToast: true })
        }
        if (token !== playToken || status !== 'PLAYING') break
        if (r < repeatTimes - 1) {
          await sleep(600)
        }
      }
      if (token !== playToken || status !== 'PLAYING') break

      // 发音结束后的思考与跟读留白
      await sleep(termPauseMs)
      if (token !== playToken || status !== 'PLAYING') break

      // 2. 播报简明中文释义（仅在开启了英汉双播模式且释义非空时）
      if (options.mode === 'term_and_meaning' && meaning && typeof speakMeaning === 'function') {
        await speakMeaning(meaning, { silentToast: true })
        if (token !== playToken || status !== 'PLAYING') break
        await sleep(meaningPauseMs)
      }
      if (token !== playToken || status !== 'PLAYING') break

      // 3. 单词间平滑过渡间隔
      await sleep(nextWordPauseMs)
      if (token !== playToken || status !== 'PLAYING') break

      // 进入下一个单词（依据方式：顺序 / 随机）
      if (options.order === 'random') {
        currentIndex = pickNextIndex(entries, currentIndex)
      } else {
        currentIndex++
      }
    }
  }

  function start(fromIndexOrEntries = 0, maybeIndex = 0) {
    if (Array.isArray(fromIndexOrEntries)) {
      customEntries = fromIndexOrEntries
      currentIndex = typeof maybeIndex === 'number' ? maybeIndex : 0
    } else {
      currentIndex = typeof fromIndexOrEntries === 'number' ? fromIndexOrEntries : 0
    }

    const entries = resolveEntries()
    if (!entries.length) {
      status = 'IDLE'
      handleRowChange?.(null, -1)
      toast?.('当前列表没有可朗读的单词')
      return Promise.resolve()
    }

    if (currentIndex < 0 || currentIndex >= entries.length) {
      currentIndex = 0
    }

    status = 'PLAYING'
    playToken++
    playHistory = [currentIndex]
    handleRowChange?.(entries[currentIndex], currentIndex)
    notifyStatus()
    return playLoop(playToken)
  }

  function pause() {
    if (status !== 'PLAYING') return
    status = 'PAUSED'
    playToken++
    if (typeof stopSpeaking === 'function') stopSpeaking()
    notifyStatus()
  }

  function resume() {
    if (status !== 'PAUSED') return
    status = 'PLAYING'
    playToken++
    const entries = resolveEntries()
    const entry = entries[currentIndex] || null
    handleRowChange?.(entry, currentIndex)
    notifyStatus()
    return playLoop(playToken)
  }

  function stop() {
    status = 'IDLE'
    currentIndex = 0
    playToken++
    playHistory = []
    if (typeof stopSpeaking === 'function') stopSpeaking()
    handleRowChange?.(null, -1)
    notifyStatus()
  }

  function toggle(entries) {
    if (Array.isArray(entries)) {
      customEntries = entries
    }
    if (status === 'PLAYING') {
      pause()
    } else if (status === 'PAUSED') {
      resume()
    } else {
      start(0)
    }
  }

  function next() {
    const entries = resolveEntries()
    if (!entries.length) return Promise.resolve()
    if (typeof stopSpeaking === 'function') stopSpeaking()
    playToken++
    if (options.order === 'random') {
      currentIndex = pickNextIndex(entries, currentIndex)
    } else {
      currentIndex = Math.min(entries.length - 1, currentIndex + 1)
    }
    playHistory.push(currentIndex)
    status = 'PLAYING'
    handleRowChange?.(entries[currentIndex], currentIndex)
    notifyStatus()
    return playLoop(playToken)
  }

  function prev() {
    const entries = resolveEntries()
    if (!entries.length) return Promise.resolve()
    if (typeof stopSpeaking === 'function') stopSpeaking()
    playToken++
    if (playHistory.length > 1) {
      playHistory.pop() // 移除当前位置
      currentIndex = playHistory[playHistory.length - 1] // 回退到上一个历史位置
    } else {
      currentIndex = Math.max(0, currentIndex - 1)
      playHistory = [currentIndex]
    }
    status = 'PLAYING'
    handleRowChange?.(entries[currentIndex], currentIndex)
    notifyStatus()
    return playLoop(playToken)
  }

  function setOptions(newOpts = {}) {
    Object.assign(options, newOpts)
    if (newOpts.repeatCount != null) {
      options.repeatCount = Math.max(1, Math.min(3, Number(newOpts.repeatCount) || 1))
    }
    notifyStatus()
  }

  return {
    start,
    play: (arg1, arg2) => start(arg1, arg2),
    pause,
    resume,
    stop,
    toggle,
    togglePlay: (arg) => toggle(arg),
    next,
    prev,
    setOptions,
    getOptions: () => ({ ...options }),
    getStatus: () => status,
    getCurrentIndex: () => currentIndex,
    isPlaying: () => status === 'PLAYING',
    isPaused: () => status === 'PAUSED',
    cleanChineseMeaning,
  }
}
