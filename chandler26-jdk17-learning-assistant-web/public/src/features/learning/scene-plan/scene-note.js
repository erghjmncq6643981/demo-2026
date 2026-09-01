import { renderMarkdown } from '/src/shared/vocabulary.js'

/**
 * 标准 MD5 字符串摘要计算
 */
export function md5(string) {
  function rotateLeft(lValue, iShiftBits) {
    return (lValue << iShiftBits) | (lValue >>> (32 - iShiftBits))
  }
  function addUnsigned(lX, lY) {
    const lX4 = (lX & 0x40000000)
    const lY4 = (lY & 0x40000000)
    const lX8 = (lX & 0x80000000)
    const lY8 = (lY & 0x80000000)
    const lResult = (lX & 0x3FFFFFFF) + (lY & 0x3FFFFFFF)
    if (lX4 & lY4) return (lResult ^ 0x80000000 ^ lX8 ^ lY8)
    if (lX4 | lY4) {
      if (lResult & 0x40000000) return (lResult ^ 0xC0000000 ^ lX8 ^ lY8)
      return (lResult ^ 0x40000000 ^ lX8 ^ lY8)
    }
    return (lResult ^ lX8 ^ lY8)
  }
  function F(x, y, z) { return (x & y) | ((~x) & z) }
  function G(x, y, z) { return (x & z) | (y & (~z)) }
  function H(x, y, z) { return (x ^ y ^ z) }
  function I(x, y, z) { return (y ^ (x | (~z))) }
  function FF(a, b, c, d, x, s, ac) {
    a = addUnsigned(a, addUnsigned(addUnsigned(F(b, c, d), x), ac))
    return addUnsigned(rotateLeft(a, s), b)
  }
  function GG(a, b, c, d, x, s, ac) {
    a = addUnsigned(a, addUnsigned(addUnsigned(G(b, c, d), x), ac))
    return addUnsigned(rotateLeft(a, s), b)
  }
  function HH(a, b, c, d, x, s, ac) {
    a = addUnsigned(a, addUnsigned(addUnsigned(H(b, c, d), x), ac))
    return addUnsigned(rotateLeft(a, s), b)
  }
  function II(a, b, c, d, x, s, ac) {
    a = addUnsigned(a, addUnsigned(addUnsigned(I(b, c, d), x), ac))
    return addUnsigned(rotateLeft(a, s), b)
  }
  function convertToWordArray(string) {
    let lWordCount
    const lMessageLength = string.length
    const lNumberOfWordsTemp1 = lMessageLength + 8
    const lNumberOfWordsTemp2 = (lNumberOfWordsTemp1 - (lNumberOfWordsTemp1 % 64)) / 64
    const lNumberOfWords = (lNumberOfWordsTemp2 + 1) * 16
    const lWordArray = Array(lNumberOfWords - 1)
    let lBytePosition = 0
    let lByteCount = 0
    while (lByteCount < lMessageLength) {
      lWordCount = (lByteCount - (lByteCount % 4)) / 4
      lBytePosition = (lByteCount % 4) * 8
      lWordArray[lWordCount] = (lWordArray[lWordCount] | (string.charCodeAt(lByteCount) << lBytePosition))
      lByteCount++
    }
    lWordCount = (lByteCount - (lByteCount % 4)) / 4
    lBytePosition = (lByteCount % 4) * 8
    lWordArray[lWordCount] = lWordArray[lWordCount] | (0x80 << lBytePosition)
    lWordArray[lNumberOfWords - 2] = lMessageLength << 3
    lWordArray[lNumberOfWords - 1] = lMessageLength >>> 29
    return lWordArray
  }
  function wordToHex(lValue) {
    let wordToHexValue = '', wordToHexValueTemp = '', lByte, lCount
    for (lCount = 0; lCount <= 3; lCount++) {
      lByte = (lValue >>> (lCount * 8)) & 255
      wordToHexValueTemp = '0' + lByte.toString(16)
      wordToHexValue = wordToHexValue + wordToHexValueTemp.substr(wordToHexValueTemp.length - 2, 2)
    }
    return wordToHexValue
  }
  function utf8Encode(string) {
    string = string.replace(/\r\n/g, '\n')
    let utftext = ''
    for (let n = 0; n < string.length; n++) {
      const c = string.charCodeAt(n)
      if (c < 128) {
        utftext += String.fromCharCode(c)
      } else if ((c > 127) && (c < 2048)) {
        utftext += String.fromCharCode((c >> 6) | 192)
        utftext += String.fromCharCode((c & 63) | 128)
      } else {
        utftext += String.fromCharCode((c >> 12) | 224)
        utftext += String.fromCharCode(((c >> 6) & 63) | 128)
        utftext += String.fromCharCode((c & 63) | 128)
      }
    }
    return utftext
  }

  let x = []
  let k, AA, BB, CC, DD, a, b, c, d
  const S11 = 7, S12 = 12, S13 = 17, S14 = 22
  const S21 = 5, S22 = 9, S23 = 14, S24 = 20
  const S31 = 4, S32 = 11, S33 = 16, S34 = 23
  const S41 = 6, S42 = 10, S43 = 15, S44 = 21

  string = utf8Encode(String(string || ''))
  x = convertToWordArray(string)
  a = 0x67452301; b = 0xEFCDAB89; c = 0x98BADCFE; d = 0x10325476

  for (k = 0; k < x.length; k += 16) {
    AA = a; BB = b; CC = c; DD = d
    a = FF(a, b, c, d, x[k + 0], S11, 0xD76AA478)
    d = FF(d, a, b, c, x[k + 1], S12, 0xE8C7B756)
    c = FF(c, d, a, b, x[k + 2], S13, 0x242070DB)
    b = FF(b, c, d, a, x[k + 3], S14, 0xC1BDCEEE)
    a = FF(a, b, c, d, x[k + 4], S11, 0xF57C0FAF)
    d = FF(d, a, b, c, x[k + 5], S12, 0x4787C62A)
    c = FF(c, d, a, b, x[k + 6], S13, 0xA8304613)
    b = FF(b, c, d, a, x[k + 7], S14, 0xFD469501)
    a = FF(a, b, c, d, x[k + 8], S11, 0x698098D8)
    d = FF(d, a, b, c, x[k + 9], S12, 0x8B44F7AF)
    c = FF(c, d, a, b, x[k + 10], S13, 0xFFFF5BB1)
    b = FF(b, c, d, a, x[k + 11], S14, 0x895CD7BE)
    a = FF(a, b, c, d, x[k + 12], S11, 0x6B901122)
    d = FF(d, a, b, c, x[k + 13], S12, 0xFD987193)
    c = FF(c, d, a, b, x[k + 14], S13, 0xA679438E)
    b = FF(b, c, d, a, x[k + 15], S14, 0x49B40821)
    a = GG(a, b, c, d, x[k + 1], S21, 0xF61E2562)
    d = GG(d, a, b, c, x[k + 6], S22, 0xC040B340)
    c = GG(c, d, a, b, x[k + 11], S23, 0x265E5A51)
    b = GG(b, c, d, a, x[k + 0], S24, 0xE9B6C7AA)
    a = GG(a, b, c, d, x[k + 5], S21, 0xD62F105D)
    d = GG(d, a, b, c, x[k + 10], S22, 0x02441453)
    c = GG(c, d, a, b, x[k + 15], S23, 0xD8A1E681)
    b = GG(b, c, d, a, x[k + 4], S24, 0xE7D3FBC8)
    a = GG(a, b, c, d, x[k + 9], S21, 0x21E1CDE6)
    d = GG(d, a, b, c, x[k + 14], S22, 0xC33707D6)
    c = GG(c, d, a, b, x[k + 3], S23, 0xF4D50D87)
    b = GG(b, c, d, a, x[k + 8], S24, 0x455A14ED)
    a = GG(a, b, c, d, x[k + 13], S21, 0xA9E3E905)
    d = GG(d, a, b, c, x[k + 2], S22, 0xFCEFA3F8)
    c = GG(c, d, a, b, x[k + 7], S23, 0x676F02D9)
    b = GG(b, c, d, a, x[k + 12], S24, 0x8D2A4C8A)
    a = HH(a, b, c, d, x[k + 5], S31, 0xFFFA3942)
    d = HH(d, a, b, c, x[k + 8], S32, 0x8771F681)
    c = HH(c, d, a, b, x[k + 11], S33, 0x6D9D6122)
    b = HH(b, c, d, a, x[k + 14], S34, 0xFDE5380C)
    a = HH(a, b, c, d, x[k + 1], S31, 0xA4BEEA44)
    d = HH(d, a, b, c, x[k + 4], S32, 0x4BDECFA9)
    c = HH(c, d, a, b, x[k + 7], S33, 0xF6BB4B60)
    b = HH(b, c, d, a, x[k + 10], S34, 0xBEBFBC70)
    a = HH(a, b, c, d, x[k + 13], S31, 0x289B7EC6)
    d = HH(d, a, b, c, x[k + 0], S32, 0xEAA127FA)
    c = HH(c, d, a, b, x[k + 3], S33, 0xD4EF3085)
    b = HH(b, c, d, a, x[k + 6], S34, 0x04881D05)
    a = HH(a, b, c, d, x[k + 9], S31, 0xD9D4D039)
    d = HH(d, a, b, c, x[k + 12], S32, 0xE6DB99E5)
    c = HH(c, d, a, b, x[k + 15], S33, 0x1FA27CF8)
    b = HH(b, c, d, a, x[k + 2], S34, 0xC4AC5665)
    a = II(a, b, c, d, x[k + 0], S41, 0xF4292244)
    d = II(d, a, b, c, x[k + 7], S42, 0x432AFF97)
    c = II(c, d, a, b, x[k + 14], S43, 0xAB9423A7)
    b = II(b, c, d, a, x[k + 5], S44, 0xFC93A039)
    a = II(a, b, c, d, x[k + 12], S41, 0x655B59C3)
    d = II(d, a, b, c, x[k + 3], S42, 0x8F0CCC92)
    c = II(c, d, a, b, x[k + 10], S43, 0xFFEFF47D)
    b = II(b, c, d, a, x[k + 1], S44, 0x85845DD1)
    a = II(a, b, c, d, x[k + 8], S41, 0x6FA87E4F)
    d = II(d, a, b, c, x[k + 15], S42, 0xFE2CE6E0)
    c = II(c, d, a, b, x[k + 6], S43, 0xA3014314)
    b = II(b, c, d, a, x[k + 13], S44, 0x4E0811A1)
    a = II(a, b, c, d, x[k + 4], S41, 0xF7537E82)
    d = II(d, a, b, c, x[k + 11], S42, 0xBD3AF235)
    c = II(c, d, a, b, x[k + 2], S43, 0x2AD7D2BB)
    b = II(b, c, d, a, x[k + 9], S44, 0xEB86D391)
    a = addUnsigned(a, AA); b = addUnsigned(b, BB); c = addUnsigned(c, CC); d = addUnsigned(d, DD)
  }
  return (wordToHex(a) + wordToHex(b) + wordToHex(c) + wordToHex(d)).toLowerCase()
}

export function createSceneNote({ state, elements, api, activeUnit, sameId, toast, logEvent }) {
  let saveTimer = null
  let idleRefreshTimer = null
  let isDirty = false
  let isComposing = false
  let lastSavedMd5 = null
  let lastSavedUpdateTime = null
  const DEBOUNCE_DELAY_MS = 2500
  const IDLE_REFRESH_DELAY_MS = 15000

  function formatNoteTime(value) {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return '刚刚'
    const hours = String(date.getHours()).padStart(2, '0')
    const minutes = String(date.getMinutes()).padStart(2, '0')
    const seconds = String(date.getSeconds()).padStart(2, '0')
    return `${hours}:${minutes}:${seconds}`
  }

  function updateButtonText(unit = activeUnit()) {
    const content = state.sceneNote?.content || unit?.note?.content || ''
    const hasNote = Boolean(content && content.trim())
    if (elements.sceneOpenNoteBtnText) {
      elements.sceneOpenNoteBtnText.textContent = hasNote ? '查看笔记' : '添加笔记'
    }
    if (elements.sceneOpenNoteModalBtn) {
      elements.sceneOpenNoteModalBtn.classList.toggle('has-note', hasNote)
      elements.sceneOpenNoteModalBtn.title = hasNote ? '查看并编辑场景笔记' : '记录本篇场景笔记'
    }
  }

  function updateStatusBar(errorMessage = '') {
    if (!elements.sceneNoteStatus) return
    const content = state.sceneNote?.content || ''
    const currentMd5 = md5(content)
    const effectiveTime = state.sceneNote?.updateTime || lastSavedUpdateTime
    if (errorMessage) {
      elements.sceneNoteStatus.textContent = errorMessage
      elements.sceneNoteStatus.className = 'scene-note-status error'
    } else if (isDirty && currentMd5 !== lastSavedMd5) {
      elements.sceneNoteStatus.textContent = '● 待保存'
      elements.sceneNoteStatus.className = 'scene-note-status saving'
    } else if (effectiveTime) {
      elements.sceneNoteStatus.textContent = `✓ 已自动保存 ${formatNoteTime(effectiveTime)}`
      elements.sceneNoteStatus.className = 'scene-note-status saved'
    } else if (content.trim()) {
      elements.sceneNoteStatus.textContent = '未保存'
      elements.sceneNoteStatus.className = 'scene-note-status'
    } else {
      elements.sceneNoteStatus.textContent = '无内容'
      elements.sceneNoteStatus.className = 'scene-note-status'
    }
  }

  function syncPreviewContent(content) {
    if (!elements.sceneNotePreview) return
    elements.sceneNotePreview.innerHTML = content
      ? renderMarkdown(content)
      : '<div class="scene-note-empty-preview"><p>📝 还没有笔记</p><p class="sub">点击右上角「✏️ 编辑」记录本篇材料的重点、难句或个人思考。</p></div>'
  }

  function render(unit = activeUnit(), errorMessage = '') {
    if (!elements.sceneNoteInput || !elements.sceneNotePreview) return
    const content = state.sceneNote?.content || ''

    // 核心原则：编辑状态或聚焦中绝对不强刷输入框内容，仅在非聚焦状态下同步值
    if (document.activeElement !== elements.sceneNoteInput && elements.sceneNoteInput.value !== content) {
      elements.sceneNoteInput.value = content
    }

    const isPreview = state.sceneNoteMode === 'preview'
    syncPreviewContent(content)

    elements.sceneNotePreview.classList.toggle('hidden', !isPreview)
    elements.sceneNoteInput.classList.toggle('hidden', isPreview)

    if (elements.sceneNoteEditBtn) {
      elements.sceneNoteEditBtn.classList.toggle('hidden', !isPreview)
    }
    if (elements.sceneNotePreviewBtn) {
      elements.sceneNotePreviewBtn.classList.toggle('hidden', isPreview)
    }

    updateStatusBar(errorMessage)
    updateButtonText(unit)
  }

  function onIdleRefresh() {
    if (isComposing || isDirty) return
    const unit = activeUnit()
    if (!unit || !elements.sceneNoteInput) return
    const content = elements.sceneNoteInput.value
    // 超过 15s 无编辑且已保存：在后台静默刷新 Markdown 预览与状态
    syncPreviewContent(content)
    updateStatusBar()
  }

  async function load(unit = activeUnit()) {
    if (!unit) {
      state.sceneNote = { content: '', updateTime: null, unitId: null }
      state.sceneNoteMode = 'edit'
      lastSavedMd5 = md5('')
      lastSavedUpdateTime = null
      isDirty = false
      render(null)
      return
    }
    if (sameId(state.sceneNote?.unitId, unit.id)) {
      if (lastSavedMd5 == null) {
        lastSavedMd5 = md5(state.sceneNote?.content || '')
        lastSavedUpdateTime = state.sceneNote?.updateTime || null
      }
      render(unit)
      return
    }
    if (state.preview) {
      const content = unit.note?.content || ''
      const updateTime = unit.note?.updateTime || null
      state.sceneNote = { content, updateTime, unitId: unit.id }
      state.sceneNoteMode = content.trim() ? 'preview' : 'edit'
      lastSavedMd5 = md5(content)
      lastSavedUpdateTime = updateTime
      isDirty = false
      render(unit)
      return
    }
    try {
      const note = await api.getNote(unit.planId, unit.id)
      const content = note?.content || ''
      const updateTime = note?.updateTime || null
      state.sceneNote = { content, updateTime, unitId: unit.id }
      state.sceneNoteMode = content.trim() ? 'preview' : 'edit'
      lastSavedMd5 = md5(content)
      lastSavedUpdateTime = updateTime
      isDirty = false
      render(unit)
    } catch (error) {
      state.sceneNote = { content: '', updateTime: null, unitId: unit.id }
      state.sceneNoteMode = 'edit'
      lastSavedMd5 = md5('')
      lastSavedUpdateTime = null
      isDirty = false
      render(unit, '笔记加载失败，可重试')
      logEvent('error', '场景笔记加载失败', error.message)
    }
  }

  function handleCompositionStart() {
    isComposing = true
  }

  function handleCompositionEnd(event) {
    isComposing = false
    handleInput(event)
  }

  function handleInput(event) {
    if (isComposing) return
    const unit = activeUnit()
    if (!unit || !elements.sceneNoteInput) return
    const content = elements.sceneNoteInput.value
    state.sceneNote = { ...state.sceneNote, content }

    // 核心判定：根据内容 MD5 校验是否产生实质性变更
    const currentMd5 = md5(content)
    if (currentMd5 === lastSavedMd5) {
      isDirty = false
      if (saveTimer) {
        clearTimeout(saveTimer)
        saveTimer = null
      }
      updateStatusBar()
      return
    }

    isDirty = true
    updateStatusBar()

    // 2.5s 停顿自动异步保存
    if (saveTimer) {
      clearTimeout(saveTimer)
    }
    saveTimer = setTimeout(() => {
      void flushSave()
    }, DEBOUNCE_DELAY_MS)

    // 15s 静止观察器：超过 15 秒无编辑时自动执行后台状态整理
    if (idleRefreshTimer) {
      clearTimeout(idleRefreshTimer)
    }
    idleRefreshTimer = setTimeout(() => {
      onIdleRefresh()
    }, IDLE_REFRESH_DELAY_MS)
  }

  function handleKeydown(event) {
    if (!event) return
    // 快捷键 Cmd+S / Ctrl+S 立即手动保存
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 's') {
      event.preventDefault()
      void flushSave(true)
    }
  }

  async function flushSave(showFeedback = false) {
    if (saveTimer) {
      clearTimeout(saveTimer)
      saveTimer = null
    }

    const unit = activeUnit()
    if (!unit || !elements.sceneNoteInput) return
    const content = elements.sceneNoteInput.value
    const currentMd5 = md5(content)

    // 双重校验：若内容 MD5 与最后一次保存一致，跳过多余的网络持久化
    if (currentMd5 === lastSavedMd5) {
      isDirty = false
      updateStatusBar()
      if (showFeedback) toast('笔记已是最新状态')
      return
    }

    isDirty = false
    if (elements.sceneNoteStatus) {
      elements.sceneNoteStatus.textContent = '● 正在保存...'
      elements.sceneNoteStatus.className = 'scene-note-status saving'
    }

    try {
      let savedUpdateTime = new Date().toISOString()
      if (state.preview) {
        state.sceneNote = { content, updateTime: savedUpdateTime, unitId: unit.id }
        unit.note = { content, updateTime: savedUpdateTime }
      } else {
        const note = await api.saveNote(unit.planId, unit.id, content)
        savedUpdateTime = note?.updateTime || savedUpdateTime
        state.sceneNote = { content: note?.content || '', updateTime: savedUpdateTime, unitId: unit.id }
      }
      // 同步更新已持久化内容 MD5 与更新时间
      lastSavedMd5 = currentMd5
      lastSavedUpdateTime = savedUpdateTime

      // 编辑状态下绝不触碰正在焦点的 textarea.value，仅后台更新 Markdown 预览与状态栏
      syncPreviewContent(content)
      updateStatusBar()
      updateButtonText(unit)
      if (showFeedback) toast('✓ 笔记已保存')
    } catch (error) {
      isDirty = true
      updateStatusBar('保存失败，请检查网络')
      logEvent('error', '场景笔记保存失败', error.message)
    }
  }

  function setMode(mode) {
    if (mode === 'preview') {
      void flushSave()
    }
    state.sceneNoteMode = mode
    render(activeUnit())
    if (mode === 'edit' && elements.sceneNoteInput) {
      elements.sceneNoteInput.focus()
    }
  }

  function togglePreview() {
    setMode(state.sceneNoteMode === 'preview' ? 'edit' : 'preview')
  }

  function togglePanel(forceOpen) {
    const unit = activeUnit()
    if (!unit) return

    const nextOpen = typeof forceOpen === 'boolean' ? forceOpen : !state.sceneNotePanelOpen
    state.sceneNotePanelOpen = nextOpen

    if (elements.sceneNotePanel) {
      elements.sceneNotePanel.classList.toggle('hidden', !nextOpen)
    }
    if (elements.sceneStudySplitLayout) {
      elements.sceneStudySplitLayout.classList.toggle('with-note-open', nextOpen)
    }

    if (nextOpen) {
      const content = state.sceneNote?.content || ''
      state.sceneNoteMode = content.trim() ? 'preview' : 'edit'
      render(unit)
      if (state.sceneNoteMode === 'edit' && elements.sceneNoteInput) {
        elements.sceneNoteInput.focus()
      }
    } else {
      void flushSave()
    }
  }

  function openPanel() {
    togglePanel(true)
  }

  function closePanel() {
    togglePanel(false)
  }

  return {
    load,
    render,
    save: flushSave,
    flushSave,
    handleInput,
    handleKeydown,
    handleCompositionStart,
    handleCompositionEnd,
    setMode,
    togglePreview,
    togglePanel,
    openPanel,
    closePanel,
    openModal: openPanel,
    closeModal: closePanel,
    updateButtonText,
  }
}
