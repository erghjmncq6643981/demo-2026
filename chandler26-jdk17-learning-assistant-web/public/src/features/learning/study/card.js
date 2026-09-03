import { escapeHtml } from '/src/shared/text.js'
import { playUiTone } from '/src/shared/audio.js'
import {
  bindInlineAudio as bindInlineAudioShared,
  bindStudyTermCards as bindStudyTermCardsShared,
  normalizeArray,
  normalizeDefinitions,
  normalizeExamples,
  readText,
  renderCollocationMini,
  renderRelationItem,
  semanticRelations,
  statusLabel,
  stringifyValue,
  tagLabel,
} from '/src/shared/vocabulary.js'

export function createStudyCardFeature(ctx) {
  const {
    state,
    elements,
    confirmAction,
    setView,
    study,
    renderReviewFocus,
    findEntryForRecord,
    renderNotes,
    speak,
    speakSentence,
    request,
  } = ctx

  function renderRecord(record) {
    state.currentRecord = record
    state.currentSessionId = record?.sessionId || state.currentSessionId
    const parsed = record?.parsed || null
    const baseLemma = record?.lemma || parsed?.lemma
    const term = baseLemma || parsed?.term || record?.term || record?.normalizedTerm || 'Ready'
    if (elements.cacheState) {
      const isAlias = Boolean(record?.isAliasHit || (record?.queriedTerm && String(record.queriedTerm).toLowerCase() !== String(term).toLowerCase()))
      const baseText = record ? (record.cacheHit ? 'CACHE HIT' : 'AI GENERATED') : '等待输入'
      if (isAlias && record?.queriedTerm) {
        elements.cacheState.innerHTML = `${escapeHtml(baseText)} · <span class="alias-notice-tag">关联原形 (检索: ${escapeHtml(record.queriedTerm)})</span>`
      } else {
        elements.cacheState.textContent = baseText
      }
    }
    if (elements.wordTitle) {
      elements.wordTitle.textContent = term
    }
    const ukRaw = parsed?.phonetic?.uk || parsed?.['phonetic.uk'] || parsed?.phonetic_uk || parsed?.uk_phonetic || (typeof parsed?.phonetic === 'string' ? parsed.phonetic : '') || ''
    const usRaw = parsed?.phonetic?.us || parsed?.['phonetic.us'] || parsed?.phonetic_us || parsed?.us_phonetic || ''
    const uk = ukRaw ? (String(ukRaw).startsWith('UK') ? ukRaw : `UK ${ukRaw}`) : ''
    const us = usRaw ? (String(usRaw).startsWith('US') ? usRaw : `US ${usRaw}`) : ''
    renderPhonetics(term, uk, us)
    renderWordbookAssetBar(record)
    renderDefinitions(parsed)
    renderMorphology(term, parsed)
    renderTags(record?.tags)
    renderRelations(record?.relations)
    renderExamples(parsed)
    renderCollocations(parsed)
    renderMemoryTips(parsed)
    renderAiPromptChips(term, parsed)
    renderMiniQuiz(term)
    renderReviewFocus(record)
    renderNotes(findEntryForRecord(record))
  }

  function renderPhonetics(term, uk, us) {
    if (!elements.phoneticLine) return
    const items = [
      { type: 'uk', label: 'UK', text: uk.replace(/^UK\s+/, '') },
      { type: 'us', label: 'US', text: us.replace(/^US\s+/, '') },
    ].filter((item) => item.text)
    if (!items.length) {
      elements.phoneticLine.className = 'phonetic phonetic-actions'
      elements.phoneticLine.textContent = '暂无音标'
      return
    }
    elements.phoneticLine.className = 'phonetic phonetic-actions'
    elements.phoneticLine.innerHTML = items
      .map(
        (item) => `
          <span class="phonetic-item">
            <span>${escapeHtml(item.text)}</span>
            <button class="mini-audio-button" type="button" data-voice-type="${item.type}" title="播放${item.label}发音">${item.label} ▶</button>
          </span>
        `,
      )
      .join('')
    elements.phoneticLine.querySelectorAll('[data-voice-type]').forEach((button) => {
      button.addEventListener('click', () => speak(term, button.getAttribute('data-voice-type')))
    })
  }

  function renderWordbookAssetBar(record) {
    if (!elements.wordbookAssetBar) return
    if (!record) {
      elements.wordbookAssetBar.classList.add('hidden')
      elements.wordbookAssetBar.innerHTML = ''
      return
    }
    const entry = findEntryForRecord(record)
    elements.wordbookAssetBar.classList.remove('hidden')
    if (entry) {
      const wordbook = (state.wordbooks || []).find((wb) => String(wb.id) === String(entry.wordbookId))
      const bookName = wordbook?.name || '个人单词本'
      const statusText = statusLabel(entry.status || 'vague')
      const nextReview = entry.nextReviewTime
        ? new Date(entry.nextReviewTime).toLocaleDateString(undefined, { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
        : '待复习'
      elements.wordbookAssetBar.className = 'wordbook-asset-bar in-wordbook'
      elements.wordbookAssetBar.innerHTML = `
        <div class="asset-bar-left">
          <span class="asset-pill book-pill">📚 《${escapeHtml(bookName)}》</span>
          <span class="asset-pill status-pill status-${entry.status || 'vague'}">状态：${escapeHtml(statusText)}</span>
          <span class="asset-pill review-pill">下次复习：${escapeHtml(nextReview)}</span>
        </div>
        <div class="asset-bar-right">
          <button type="button" class="secondary-button compact" data-asset-transfer>转移词书</button>
        </div>
      `
      elements.wordbookAssetBar.querySelector('[data-asset-transfer]')?.addEventListener('click', () => {
        elements.addToWordbookBtn?.click()
      })
    } else {
      elements.wordbookAssetBar.className = 'wordbook-asset-bar not-in-wordbook'
      elements.wordbookAssetBar.innerHTML = `
        <div class="asset-bar-left">
          <span class="asset-tip">💡 该词尚未收录到个人单词本</span>
        </div>
        <div class="asset-bar-right">
          <button type="button" class="primary-button compact-primary" data-asset-add>＋ 收入单词本</button>
        </div>
      `
      elements.wordbookAssetBar.querySelector('[data-asset-add]')?.addEventListener('click', () => {
        elements.addToWordbookBtn?.click()
      })
    }
  }

  function renderMorphology(term, parsed) {
    if (!elements.morphologyBoard) return
    const morphologyData = parsed?.morphology || parsed?.etymology || null
    let breakdown = []

    if (morphologyData && typeof morphologyData === 'object') {
      if (Array.isArray(morphologyData.parts)) {
        breakdown = morphologyData.parts
      } else if (morphologyData.prefix || morphologyData.root || morphologyData.suffix) {
        if (morphologyData.prefix) breakdown.push({ type: 'prefix', text: morphologyData.prefix, label: '前缀', desc: morphologyData.prefixMeaning || '前缀' })
        if (morphologyData.root) breakdown.push({ type: 'root', text: morphologyData.root, label: '词根', desc: morphologyData.rootMeaning || '核心' })
        if (morphologyData.suffix) breakdown.push({ type: 'suffix', text: morphologyData.suffix, label: '后缀', desc: morphologyData.suffixMeaning || '后缀' })
      }
    }

    if (!breakdown.length) {
      // Heuristic extraction from memory tips or common English affixes
      const tipsText = (normalizeArray(parsed?.memory_tips || parsed?.memoryTips) || []).map((t) => (typeof t === 'string' ? t : t?.content || '')).join(' ')
      const prefixMatch = tipsText.match(/前缀\s*[:：]?\s*([a-zA-Z-]+)\s*[\(（]([^）\)]+)[\)）]/) || tipsText.match(/([a-zA-Z-]+)\s*[\(（]([^\)]*前缀[^\)]*)[\)）]/)
      const rootMatch = tipsText.match(/词根\s*[:：]?\s*([a-zA-Z-]+)\s*[\(（]([^）\)]+)[\)）]/) || tipsText.match(/([a-zA-Z-]+)\s*[\(（]([^\)]*词根[^\)]*)[\)）]/)
      const suffixMatch = tipsText.match(/后缀\s*[:：]?\s*([a-zA-Z-]+)\s*[\(（]([^）\)]+)[\)）]/) || tipsText.match(/([a-zA-Z-]+)\s*[\(（]([^\)]*后缀[^\)]*)[\)）]/)

      if (prefixMatch || rootMatch || suffixMatch) {
        if (prefixMatch) breakdown.push({ type: 'prefix', text: prefixMatch[1], label: '前缀', desc: prefixMatch[2] })
        if (rootMatch) breakdown.push({ type: 'root', text: rootMatch[1], label: '词根', desc: rootMatch[2] })
        if (suffixMatch) breakdown.push({ type: 'suffix', text: suffixMatch[1], label: '后缀', desc: suffixMatch[2] })
      }
    }

    if (!breakdown.length) {
      const cleanTerm = String(term || '').toLowerCase()
      // Rule-based fallback for prominent common patterns
      const commonPrefixes = [
        ['un', '不，无'], ['re', '再次，回'], ['in', '进入，不'], ['im', '不'], ['dis', '分开，相反'],
        ['pre', '在...之前'], ['pro', '向前'], ['sub', '在...下方'], ['trans', '跨越'], ['inter', '在...之间'],
        ['con', '共同'], ['com', '共同'], ['de', '向下，除去'], ['ex', '向外'], ['over', '过度'], ['mis', '错误'],
      ]
      const commonSuffixes = [
        ['able', '可...的 (adj.)'], ['ible', '可...的 (adj.)'], ['tion', '行为/状态 (n.)'], ['sion', '状态 (n.)'],
        ['ment', '动作/结果 (n.)'], ['ness', '性质 (n.)'], ['ity', '状态 (n.)'], ['ive', '具有...性质的 (adj.)'],
        ['ful', '充满...的 (adj.)'], ['less', '无...的 (adj.)'], ['ly', '地 (adv.)'], ['ize', '使成为 (v.)'],
        ['ous', '多...的 (adj.)'], ['al', '...的 (adj./n.)'], ['er', '做...的人 (n.)'], ['or', '人/物 (n.)'],
      ]

      let foundPrefix = null
      let foundSuffix = null
      let remaining = cleanTerm

      for (const [p, desc] of commonPrefixes) {
        if (remaining.length > p.length + 3 && remaining.startsWith(p)) {
          foundPrefix = { type: 'prefix', text: `${p}-`, label: '前缀', desc }
          remaining = remaining.slice(p.length)
          break
        }
      }
      for (const [s, desc] of commonSuffixes) {
        if (remaining.length > s.length + 2 && remaining.endsWith(s)) {
          foundSuffix = { type: 'suffix', text: `-${s}`, label: '后缀', desc }
          remaining = remaining.slice(0, -s.length)
          break
        }
      }
      if (foundPrefix || foundSuffix) {
        if (foundPrefix) breakdown.push(foundPrefix)
        if (remaining.length >= 2) breakdown.push({ type: 'root', text: remaining, label: '词根/主干', desc: '核心语素' })
        if (foundSuffix) breakdown.push(foundSuffix)
      }
    }

    if (!breakdown.length) {
      elements.morphologyBoard.className = 'morphology-board empty'
      elements.morphologyBoard.innerHTML = '<div class="morphology-empty-tip">暂无复杂派生拆分，属于基础单素词</div>'
      return
    }

    elements.morphologyBoard.className = 'morphology-board'
    elements.morphologyBoard.innerHTML = `
      <div class="morphology-chips">
        ${breakdown
          .map(
            (part, idx) => `
              <div class="morphology-chip ${escapeHtml(part.type)}">
                <span class="chip-tag">${escapeHtml(part.label)}</span>
                <strong class="chip-text">${escapeHtml(part.text)}</strong>
                <span class="chip-desc">${escapeHtml(part.desc)}</span>
              </div>
              ${idx < breakdown.length - 1 ? '<span class="morphology-plus">+</span>' : ''}
            `,
          )
          .join('')}
        <span class="morphology-equals">=</span>
        <div class="morphology-result">
          <strong>${escapeHtml(term)}</strong>
        </div>
      </div>
    `
  }

  function renderAiPromptChips(term, _parsed) {
    if (!elements.aiPromptChips) return
    const chips = elements.aiPromptChips.querySelectorAll('.prompt-chip')
    chips.forEach((chip) => {
      chip.onclick = () => {
        const type = chip.getAttribute('data-prompt-type')
        let promptText = ''
        switch (type) {
          case 'synonym_diff':
            promptText = `请帮我详细辨析英语单词「${term}」的易混近义词，指出语感、感情色彩、适用场景及搭配区别。`
            break
          case 'workplace_sentences':
            promptText = `请为单词「${term}」提供 3 个地道的高质量职场/商务实战例句，包含中文翻译和关键用法解析。`
            break
          case 'etymology_story':
            promptText = `请深入讲解单词「${term}」的词源演变背景、构词逻辑，并提供一个生动好记的联想记忆技巧。`
            break
          case 'sentence_eval':
            promptText = `我想用「${term}」进行造句练习。请先给出 1 个场景填空题来引导我完成造句。`
            break
          default:
            promptText = `请解析单词「${term}」的重点用法。`
        }
        askAiMiniChat(promptText)
      }
    })

    if (elements.aiMiniChatForm) {
      elements.aiMiniChatForm.onsubmit = (event) => {
        event.preventDefault()
        const text = elements.aiMiniChatInput?.value.trim()
        if (text) {
          elements.aiMiniChatInput.value = ''
          askAiMiniChat(text)
        }
      }
    }
  }

  async function askAiMiniChat(prompt) {
    if (!elements.aiMiniChatOutput) return
    elements.aiMiniChatOutput.className = 'ai-mini-chat-output loading'
    elements.aiMiniChatOutput.innerHTML = `<div class="mini-chat-query">Q: ${escapeHtml(prompt)}</div><div class="mini-chat-reply">AI 私教思考中...</div>`
    try {
      const response = await request('/api/v1/ai/chat-sessions', {
        method: 'POST',
        body: JSON.stringify({
          agentCode: 'english_vocabulary_plan',
          sceneCode: 'vocabulary_card_single',
          businessType: 'word_qa',
          businessId: state.currentRecord?.id || '0',
          title: `词汇答疑 - ${state.currentRecord?.term || ''}`,
          prompt,
        }),
      })
      const reply = response?.content || response?.message || 'AI 助教已解答完毕。'
      elements.aiMiniChatOutput.className = 'ai-mini-chat-output active'
      elements.aiMiniChatOutput.innerHTML = `
        <div class="mini-chat-query">Q: ${escapeHtml(prompt)}</div>
        <div class="mini-chat-reply">${escapeHtml(reply)}</div>
      `
    } catch (error) {
      elements.aiMiniChatOutput.className = 'ai-mini-chat-output error'
      elements.aiMiniChatOutput.innerHTML = `
        <div class="mini-chat-query">Q: ${escapeHtml(prompt)}</div>
        <div class="mini-chat-error">解答失败：${escapeHtml(error.message)}</div>
      `
    }
  }

  function renderMiniQuiz(term) {
    if (!elements.miniQuizBoard) return
    const cleanTerm = String(term || '').trim()
    if (!cleanTerm || cleanTerm === 'Ready') {
      elements.miniQuizBoard.className = 'mini-quiz-board empty'
      elements.miniQuizBoard.textContent = '输入单词后开启跟敲微测'
      return
    }

    state.studyQuizTyped = ''
    state.studyQuizTarget = cleanTerm

    elements.miniQuizBoard.className = 'mini-quiz-board active'
    elements.miniQuizBoard.innerHTML = `
      <div class="typing-board" tabindex="0" aria-label="跟敲单词 ${escapeHtml(cleanTerm)}">
        <div class="typing-letters"></div>
        <div class="typing-progress"><span></span></div>
        <p class="typing-hint">点击此处直接键盘打字跟敲 · Backspace 回退</p>
      </div>
    `

    const board = elements.miniQuizBoard.querySelector('.typing-board')
    const lettersContainer = elements.miniQuizBoard.querySelector('.typing-letters')
    const progressBar = elements.miniQuizBoard.querySelector('.typing-progress > span')
    const hintText = elements.miniQuizBoard.querySelector('.typing-hint')

    function updateQuizLetters() {
      const target = state.studyQuizTarget || ''
      const typed = state.studyQuizTyped || ''
      const isComplete = typed.length === target.length && typed.toLowerCase() === target.toLowerCase()
      const progressPercent = target.length ? Math.min(100, Math.round((typed.length / target.length) * 100)) : 0

      if (lettersContainer) {
        lettersContainer.innerHTML = target
          .split('')
          .map((char, index) => {
            let letterClass = 'letter-box'
            if (index < typed.length) {
              letterClass += ' typed correct'
            } else if (index === typed.length) {
              letterClass += ' current'
            }
            return `<span class="${letterClass}">${escapeHtml(char)}</span>`
          })
          .join('')
      }
      if (progressBar) {
        progressBar.style.width = `${progressPercent}%`
      }
      if (hintText) {
        hintText.textContent = isComplete ? '🎉 拼写完成！肌肉记忆已激活' : '点击此处直接键盘打字跟敲 · Backspace 回退'
      }
      elements.miniQuizBoard.className = `mini-quiz-board ${isComplete ? 'complete' : 'active'}`
    }

    updateQuizLetters()

    board?.addEventListener('keydown', (event) => {
      if (event.altKey || event.ctrlKey || event.metaKey) return
      const target = state.studyQuizTarget || ''
      if (event.key === 'Backspace') {
        event.preventDefault()
        if (state.studyQuizTyped.length > 0) {
          state.studyQuizTyped = state.studyQuizTyped.slice(0, -1)
          updateQuizLetters()
        }
        return
      }
      if (event.key.length === 1) {
        event.preventDefault()
        const currentIdx = state.studyQuizTyped.length
        const expected = target[currentIdx]
        if (!expected) return
        if (event.key.toLowerCase() === expected.toLowerCase()) {
          state.studyQuizTyped += expected
          playUiTone('correct')
          updateQuizLetters()
          if (state.studyQuizTyped.length === target.length) {
            playUiTone('success')
          }
        } else {
          playUiTone('wrong')
          board.classList.add('shake')
          setTimeout(() => board.classList.remove('shake'), 400)
        }
      }
    })

    if (elements.restartMiniQuizBtn) {
      elements.restartMiniQuizBtn.onclick = () => {
        state.studyQuizTyped = ''
        updateQuizLetters()
        board?.focus()
      }
    }
  }

  function renderDefinitions(parsed) {
    if (!elements.meaningList) return
    const definitions = normalizeDefinitions(parsed)
    if (!definitions.length) {
      elements.meaningList.innerHTML = '<div class="empty">暂无释义</div>'
      return
    }
    elements.meaningList.innerHTML = definitions
      .map(
        (item) => `
          <div class="meaning-item">
            <span class="pos">${escapeHtml(item.pos || 'meaning')}</span>
            <div>
              <p>${escapeHtml(item.cn || '暂无中文释义')}</p>
              <p class="meaning-en">${escapeHtml(item.en || item.extra || '暂无英文释义')}</p>
            </div>
          </div>
        `,
      )
      .join('')
  }

  function renderTags(tags) {
    if (!elements.tagList) return
    const list = Array.isArray(tags) ? tags : []
    if (!list.length) {
      elements.tagList.className = 'chips empty'
      elements.tagList.textContent = '暂无标签'
      return
    }
    elements.tagList.className = 'chips'
    elements.tagList.innerHTML = list.map((tag) => `<span class="chip tag-chip">${escapeHtml(tagLabel(tag))}</span>`).join('')
  }

  function renderRelations(relations) {
    if (!elements.relationList) return
    const list = semanticRelations(relations)
    if (!list.length) {
      elements.relationList.className = 'relation-list empty'
      elements.relationList.textContent = '暂无关联词'
      return
    }
    elements.relationList.className = 'relation-list'
    elements.relationList.innerHTML = list.map((item) => renderRelationItem(item, 'related-term')).join('')
    bindStudyTermCards(elements.relationList, '[data-related-term]', '单词')
    bindInlineAudio(elements.relationList)
  }

  function bindInlineAudio(container) {
    bindInlineAudioShared(container, (text) => speak(text, elements.voiceSelect?.value))
  }

  function bindStudyTermCards(container, selector, label) {
    bindStudyTermCardsShared(container, selector, (term) => confirmStudyTerm(term, label))
  }

  async function confirmStudyTerm(term, label = '单词') {
    const cleanTerm = String(term || '').trim()
    if (!cleanTerm) return
    const confirmed = await confirmAction({
      title: `学习${label}`,
      message: `是否去学习「${cleanTerm}」？当前学习卡会切换到这个${label}。`,
      acceptText: '去学习',
    })
    if (!confirmed) return
    if (elements.termInput) elements.termInput.value = cleanTerm
    setView('studyView')
    study(cleanTerm)
  }

  function renderExamples(parsed) {
    if (!elements.examples) return
    const examples = normalizeExamples(parsed)
    if (!examples.length) {
      elements.examples.className = 'stack empty'
      elements.examples.textContent = '暂无例句'
      return
    }
    elements.examples.className = 'stack'
    elements.examples.innerHTML = examples
      .map(
        (item, index) => `
          <div class="example-item">
            <button class="icon-button" type="button" data-sentence-index="${index}" title="播放例句">▶</button>
            <p class="sentence">${escapeHtml(item.sentence || '')}</p>
            <p class="translation">${escapeHtml(item.translation || '')}</p>
          </div>
        `,
      )
      .join('')
    elements.examples.querySelectorAll('[data-sentence-index]').forEach((button) => {
      button.addEventListener('click', () => speakSentence(examples[Number(button.getAttribute('data-sentence-index'))]?.sentence))
    })
  }

  function renderCollocations(parsed) {
    if (!elements.collocations) return
    const collocations = normalizeArray(parsed?.collocations || parsed?.phrases || parsed?.common_phrases)
    if (!collocations.length) {
      elements.collocations.className = 'collocation-list empty'
      elements.collocations.textContent = '暂无搭配'
      return
    }
    elements.collocations.className = 'collocation-list'
    elements.collocations.innerHTML = collocations.map(renderCollocationMini).join('')
    bindStudyTermCards(elements.collocations, '[data-collocation-term]', '词组')
    bindInlineAudio(elements.collocations)
  }

  function renderMemoryTips(parsed) {
    if (!elements.memoryTips) return
    const tips = normalizeArray(parsed?.memory_tips || parsed?.memoryTips || parsed?.tips || parsed?.memory)
    if (!tips.length) {
      elements.memoryTips.className = 'stack empty'
      elements.memoryTips.textContent = '暂无记忆提示'
      return
    }
    elements.memoryTips.className = 'stack'
    elements.memoryTips.innerHTML = tips.map((item) => `<div class="tip-item">${escapeHtml(readText(item, ['content', 'tip', 'text', 'meaning']) || stringifyValue(item))}</div>`).join('')
  }

  return {
    renderRecord,
    renderPhonetics,
    renderWordbookAssetBar,
    renderMorphology,
    renderDefinitions,
    renderTags,
    renderRelations,
    bindInlineAudio,
    bindStudyTermCards,
    confirmStudyTerm,
    renderExamples,
    renderCollocations,
    renderMemoryTips,
    renderAiPromptChips,
    renderMiniQuiz,
  }
}
