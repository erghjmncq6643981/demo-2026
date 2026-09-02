import { hideModal, showModal } from '/src/shared/modal.js'
import { playUiTone } from '/src/shared/audio.js'
import {
  ASSESSMENT_LABELS,
  asArray,
  escapeRegExp,
  number,
} from '/src/features/learning/scene-plan/model.js'
import { isWordComplete, nextAssessment, requiredAssessments } from '/src/features/learning/scene-plan/challenge-model.js'

export function createSceneStudy({
  state,
  elements,
  api,
  activeUnit,
  renderCurrentScene,
  escapeHtml,
  sameId,
  toast,
  logEvent,
  completeCurrentUnit,
  backToReading,
  generateRelatedWords,
  promoteWord,
  speak,
  preloadAudio,
  startChallenge,
}) {
  let assessmentFeedback = null

  function currentSceneWord(unit = activeUnit()) {
    const coreWords = asArray(unit?.words).filter((word) => word.tier === 'core')
    return coreWords.find((word) => sameId(word.id, state.currentSceneWordId)) || coreWords[0] || null
  }

  function resetAssessment() {
    assessmentFeedback = null
    state.sceneTypingTyped = ''
  }

  function beginAssessment(word) {
    state.currentSceneWordId = word?.id || null
    state.sceneAssessmentStartedAt = Date.now()
    resetAssessment()
  }

  function renderChallengeWords(coreWords) {
    const spellingCount = coreWords.filter((word) => word.masteryRequirement === 'spelling').length
    const recognitionCount = coreWords.length - spellingCount
    elements.sceneChallengeWordCount.textContent = `${coreWords.length} 词`
    elements.sceneChallengeWords.className = coreWords.length ? 'scene-challenge-prep' : 'scene-challenge-prep empty'
    elements.sceneChallengeWords.innerHTML = coreWords.length
      ? `<div><strong>${coreWords.length}</strong><span>本轮词数</span></div><div><strong>${recognitionCount}</strong><span>含义选择</span></div><div><strong>${spellingCount}</strong><span>拼写检查</span></div><p>挑战开始后逐题作答，不再重复展示完整词表。</p>`
      : '当前没有可挑战词汇'
  }

  function applyStage(stage) {
    state.sceneChallengeStage = stage
    const hasPlan = Boolean(state.currentLearningPlan && activeUnit())
    const inLearning = hasPlan && stage !== 'overview'
    elements.scenePlanToolbar?.classList.toggle('hidden', inLearning)
    elements.scenePlanSidebar?.classList.toggle('hidden', inLearning)
    elements.scenePlanLayout?.classList.toggle('scene-focus-layout', inLearning)
    elements.scenePlanOverview?.classList.toggle('hidden', inLearning)
    elements.sceneLearningStage?.classList.toggle('hidden', !inLearning)
    const showReading = stage === 'learning'
    elements.sceneLearningStage?.querySelector('.scene-unit-header')?.classList.toggle('hidden', false)
    elements.sceneLearningStage?.querySelector('.scene-reading-panel')?.classList.toggle('hidden', !showReading)
    elements.sceneLearningFooter?.classList.toggle('hidden', !showReading)
    elements.sceneChallengeStage?.classList.toggle('hidden', stage !== 'challenge')
    elements.sceneAssessmentPanel?.classList.toggle('hidden', stage !== 'assessment')
  }

  function renderTranslation(translation) {
    if (!translation) return '<p>暂无译文</p>'
    const paragraphs = String(translation)
      .split(/\r?\n+/)
      .map((line) => line.trim())
      .filter(Boolean)
    if (!paragraphs.length) return '<p>暂无译文</p>'
    return paragraphs.map((line) => `<p>${escapeHtml(line)}</p>`).join('')
  }

  function renderLearningText(unit, coreWords) {
    const learningText = unit.learningText || unit.material?.learning_text || unit.material?.learningText || ''
    elements.sceneLearningText.className = learningText ? 'scene-learning-text' : 'scene-learning-text empty'
    elements.sceneLearningText.innerHTML = learningText
      ? annotateUnknownWords(learningText, coreWords.filter((word) => word.firstLearning))
      : '暂无场景材料'
    if (elements.sceneTranslation) {
      elements.sceneTranslation.innerHTML = renderTranslation(unit.translation || unit.material?.translation)
    }
  }

  function generateWordVariants(rawTerm, acceptedSpellings = []) {
    const term = String(rawTerm || '').trim().toLowerCase()
    if (!term) return []
    const variants = new Set([term])
    if (Array.isArray(acceptedSpellings)) {
      acceptedSpellings.forEach((spelling) => {
        const value = String(spelling || '').trim().toLowerCase()
        if (value) variants.add(value)
      })
    }
    if (term.includes(' ') || term.includes('-')) {
      variants.add(term.replace(/-/g, ' '))
      variants.add(term.replace(/\s+/g, '-'))
      return Array.from(variants)
    }
    if (/[bcdfghjklmnpqrstvwxyz]y$/.test(term)) {
      const stem = term.slice(0, -1)
      variants.add(`${stem}ies`)
      variants.add(`${stem}ied`)
      variants.add(`${term}ing`)
      variants.add(`${term}s`)
    } else if (/e$/.test(term)) {
      const stem = term.slice(0, -1)
      variants.add(`${term}s`)
      variants.add(`${term}d`)
      variants.add(`${stem}ing`)
    } else if (/(?:[sxz]|[sc]h)$/.test(term)) {
      variants.add(`${term}es`)
      variants.add(`${term}ed`)
      variants.add(`${term}ing`)
    } else if (term.length <= 4 && /[bcdfghjklmnpqrstvwxyz][aeiou][bcdfghjklmnpqrstvwxyz]$/.test(term)) {
      const lastChar = term.slice(-1)
      variants.add(`${term}s`)
      variants.add(`${term}${lastChar}ed`)
      variants.add(`${term}${lastChar}ing`)
    } else {
      variants.add(`${term}s`)
      variants.add(`${term}es`)
      variants.add(`${term}ed`)
      variants.add(`${term}ing`)
    }
    variants.add(`${term}'s`)
    return Array.from(variants)
  }

  function annotateUnknownWords(text, words) {
    const byVariant = new Map()
    for (const word of words) {
      if (!word?.term) continue
      const variants = generateWordVariants(word.term, word.acceptedSpellings || word.accepted_spellings)
      variants.forEach((variant) => {
        if (!byVariant.has(variant)) byVariant.set(variant, word)
      })
    }
    if (!byVariant.size) {
      return String(text).split(/\r?\n/).filter(Boolean).map((line) => `<p>${escapeHtml(line)}</p>`).join('')
    }
    const terms = [...byVariant.keys()].sort((left, right) => right.length - left.length)
    const pattern = new RegExp(`(?<![A-Za-z])(${terms.map(escapeRegExp).join('|')})(?![A-Za-z])`, 'gi')
    let cursor = 0
    let html = ''
    for (const match of String(text).matchAll(pattern)) {
      const index = match.index ?? 0
      const word = byVariant.get(match[0].toLowerCase())
      html += escapeHtml(String(text).slice(cursor, index))
      html += `<mark class="scene-inline-word" tabindex="0"><strong>${escapeHtml(match[0])}</strong><span>(${escapeHtml(word?.phonetic || '暂无音标')}，${escapeHtml(word?.contextMeaning || word?.meaning || '当前场景含义待补充')})</span></mark>`
      cursor = index + match[0].length
    }
    html += escapeHtml(String(text).slice(cursor))
    return html.split(/\r?\n/).filter(Boolean).map((line) => `<p>${line}</p>`).join('')
  }

  function renderCoreWords(words) {
    elements.sceneCoreCount && (elements.sceneCoreCount.textContent = String(words.length))
    elements.sceneCoreModalCount && (elements.sceneCoreModalCount.textContent = `${words.length} 词`)
    if (!elements.sceneCoreWords) return
    if (!words.length) {
      elements.sceneCoreWords.className = 'scene-core-words empty'
      elements.sceneCoreWords.textContent = '暂无核心词汇'
      return
    }
    elements.sceneCoreWords.className = 'scene-core-words'
    elements.sceneCoreWords.innerHTML = words.map((word) => {
      const passed = new Set(asArray(word.passedAssessments))
      const stages = requiredAssessments(word)
        .map((type) => `<span class="scene-step ${passed.has(type) ? 'done' : ''}" title="${escapeHtml(ASSESSMENT_LABELS[type])}"></span>`)
        .join('')
      return `<button class="scene-core-word ${sameId(word.id, state.currentSceneWordId) ? 'active' : ''} ${isWordComplete(word) ? 'completed' : ''}" type="button" data-scene-word-id="${escapeHtml(word.id)}">
        <span><strong>${escapeHtml(word.term)}</strong><small>${escapeHtml(word.phonetic || '暂无音标')}</small></span>
        <span class="scene-word-requirement">${word.masteryRequirement === 'spelling' ? '会拼写' : '认识'}</span>
        <span class="scene-step-list">${stages}</span>
      </button>`
    }).join('')
    elements.sceneCoreWords.querySelectorAll('[data-scene-word-id]').forEach((button) => {
      button.addEventListener('click', () => {
        state.currentSceneWordId = button.dataset.sceneWordId
        state.sceneAssessmentStartedAt = Date.now()
        resetAssessment()
        renderCoreWords(words)
        renderAssessment(activeUnit())
      })
    })
  }

  function renderRelatedWords(unit) {
    const keyword = elements.sceneRelatedFilter?.value.trim().toLowerCase() || ''
    const tier = elements.sceneTierFilter?.value || ''
    const source = asArray(unit?.relatedWords).map((word) => ({ ...word, standalone: true }))
    const related = source.filter((word) => {
      if (tier && word.categoryCode !== tier) return false
      const haystack = `${word.term || ''} ${word.meaning || ''} ${word.contextMeaning || ''}`.toLowerCase()
      return !keyword || haystack.includes(keyword)
    })
    elements.sceneRelatedCount && (elements.sceneRelatedCount.textContent = String(related.length))
    elements.sceneRelatedModalCount && (elements.sceneRelatedModalCount.textContent = `${related.length} 词`)
    if (!elements.sceneRelatedWords) return
    if (!related.length) {
      elements.sceneRelatedWords.className = 'scene-related-words empty'
      elements.sceneRelatedWords.innerHTML = '<span>暂无场景相关词汇</span><button class="secondary-button compact" type="button" data-generate-related>生成场景相关词汇</button>'
      elements.sceneRelatedWords.querySelector('[data-generate-related]')?.addEventListener('click', () => generateRelatedWords(unit))
      return
    }
    elements.sceneRelatedWords.className = 'scene-related-words'
    elements.sceneRelatedWords.innerHTML = related.map((word) => `<article class="scene-related-word">
      <div><span class="scene-related-title"><strong>${escapeHtml(word.term)}</strong><small>${escapeHtml(word.phonetic || '')}</small></span><p>${escapeHtml(word.contextMeaning || word.meaning || '暂无释义')}</p></div>
      <div class="scene-related-side">${word.categoryName ? `<span class="mini-pill">${escapeHtml(word.categoryName)}</span>` : '<span></span>'}</div>
    </article>`).join('')
    elements.sceneRelatedWords.querySelectorAll('[data-promote-word]').forEach((button) => {
      button.addEventListener('click', () => promoteWord(button.dataset.promoteWord))
    })
  }

  function renderTypingLetters(term, typed = '') {
    return [...String(term || '')]
      .map((letter, index) => {
        const className = index < typed.length ? 'typed' : index === typed.length ? 'current' : ''
        const label = letter === ' ' ? 'Space' : letter
        return `<span class="${className}">${escapeHtml(label)}</span>`
      })
      .join('')
  }

  function shakeTypingBoard() {
    const board = elements.sceneAssessment?.querySelector('.typing-board')
    if (!board) return
    board.classList.remove('shake')
    void board.offsetWidth
    board.classList.add('shake')
  }

  function handleSceneTypingKeydown(event, word) {
    const term = String(word?.term || '').trim()
    if (!term) return
    if (event.key === 'Backspace') {
      event.preventDefault()
      state.sceneTypingTyped = String(state.sceneTypingTyped || '').slice(0, -1)
      renderAssessment(activeUnit())
      return
    }
    if (event.key === 'Escape') {
      event.preventDefault()
      state.sceneTypingTyped = ''
      renderAssessment(activeUnit())
      return
    }
    if (event.key.length !== 1) return
    const typed = String(state.sceneTypingTyped || '')
    const expected = term[typed.length]
    if (!expected) return
    event.preventDefault()
    if (event.key.toLowerCase() === expected.toLowerCase()) {
      state.sceneTypingTyped = typed + expected
      playUiTone('correct')
      renderAssessment(activeUnit())
      if (state.sceneTypingTyped.length === term.length) {
        playUiTone('success')
        window.setTimeout(() => {
          const finishedTerm = state.sceneTypingTyped
          state.sceneTypingTyped = ''
          submitAssessment(finishedTerm)
        }, 120)
      }
      return
    }
    playUiTone('wrong')
    shakeTypingBoard()
  }

  function handleChallengeKeydown(event) {
    const isSceneView = state.activeView === 'scenePlanView' || elements.scenePlanView?.classList.contains('active')
    if (!isSceneView) return
    if (elements.sceneCoreWordsModal && !elements.sceneCoreWordsModal.classList.contains('hidden')) return
    if (elements.sceneRelatedWordsModal && !elements.sceneRelatedWordsModal.classList.contains('hidden')) return
    if (elements.sceneVocabularyPreviewModal && !elements.sceneVocabularyPreviewModal.classList.contains('hidden')) return
    if (elements.vocabularyImportModal && !elements.vocabularyImportModal.classList.contains('hidden')) return
    if (elements.scenePlanModal && !elements.scenePlanModal.classList.contains('hidden')) return

    const activeTag = document.activeElement?.tagName?.toLowerCase()
    const isTypingInInput = ['input', 'textarea'].includes(activeTag)

    if (state.sceneChallengeStage === 'challenge') {
      if ((event.key === 'Enter' || event.key === ' ' || event.code === 'Space' || event.code === 'Enter') && !isTypingInInput) {
        event.preventDefault()
        startChallenge?.()
      }
      return
    }

    const isAssessment = state.sceneChallengeStage === 'assessment' || (elements.sceneAssessmentPanel && !elements.sceneAssessmentPanel.classList.contains('hidden'))
    if (!isAssessment) return

    const unit = activeUnit()
    if (!unit) return
    const coreWords = asArray(unit.words).filter((item) => item.tier === 'core')
    const completedWords = coreWords.filter(isWordComplete)

    if (coreWords.length && completedWords.length === coreWords.length) {
      if (event.key === 'Enter' || event.code === 'Enter' || event.code === 'NumpadEnter') {
        event.preventDefault()
        completeCurrentUnit()
      }
      return
    }

    const word = currentSceneWord(unit)
    if (!word) return
    const type = nextAssessment(word)

    // 1. Copy typing mode takes priority so all letters (including 'r') are typed
    if (type === 'copy_typing') {
      if (event.altKey && (event.key === 'p' || event.key === 'P' || event.code === 'KeyP')) {
        event.preventDefault()
        if (typeof speak === 'function') speak(word.term)
        return
      }
      if (isTypingInInput) return
      handleSceneTypingKeydown(event, word)
      return
    }

    // 2. Pronunciation shortcut: 'r' / 'R' / Alt+P (only in non-typing modes)
    const isPronounceKey = (event.key === 'r' || event.key === 'R' || event.code === 'KeyR' || (event.altKey && (event.key === 'p' || event.key === 'P' || event.code === 'KeyP')))
    if (isPronounceKey && !isTypingInInput) {
      event.preventDefault()
      if (typeof speak === 'function') {
        speak(word.term)
      }
      return
    }

    // 3. Word complete state
    if (!type) {
      if (event.key === ' ' || event.key === 'Enter' || event.code === 'Space' || event.code === 'Enter' || event.code === 'NumpadEnter') {
        event.preventDefault()
        selectNextCoreWord()
      }
      return
    }

    // 4. Meaning choice mode
    if (type === 'meaning_choice' && !isTypingInInput) {
      const assessment = word.assessment || {}
      const options = word._shuffledOptions || asArray(assessment.options)
      let chosenIndex = -1
      const key = event.key?.toLowerCase()
      const code = event.code
      if (key >= '1' && key <= '4') {
        chosenIndex = Number(key) - 1
      } else if (code === 'Digit1' || code === 'Numpad1') {
        chosenIndex = 0
      } else if (code === 'Digit2' || code === 'Numpad2') {
        chosenIndex = 1
      } else if (code === 'Digit3' || code === 'Numpad3') {
        chosenIndex = 2
      } else if (code === 'Digit4' || code === 'Numpad4') {
        chosenIndex = 3
      } else if (['a', 'b', 'c', 'd'].includes(key)) {
        chosenIndex = key.charCodeAt(0) - 97
      } else if (code === 'KeyA') {
        chosenIndex = 0
      } else if (code === 'KeyB') {
        chosenIndex = 1
      } else if (code === 'KeyC') {
        chosenIndex = 2
      } else if (code === 'KeyD') {
        chosenIndex = 3
      }
      if (chosenIndex >= 0 && chosenIndex < options.length) {
        event.preventDefault()
        playUiTone('correct')
        submitAssessment(options[chosenIndex])
      }
      return
    }
  }

  function renderAssessment(unit) {
    const coreWords = asArray(unit?.words).filter((item) => item.tier === 'core')
    const completedWords = coreWords.filter(isWordComplete)
    if (coreWords.length && completedWords.length === coreWords.length) {
      if (elements.sceneAssessmentStage) elements.sceneAssessmentStage.textContent = `${coreWords.length} / ${coreWords.length}`
      if (elements.sceneAssessment) {
        elements.sceneAssessment.className = 'scene-assessment'
        elements.sceneAssessment.innerHTML = `<div class="scene-assessment-complete scene-unit-complete"><span class="scene-check-mark">✓</span><strong>本轮 ${coreWords.length} 个词已全部通过</strong><p>含义识别和要求掌握的拼写项目已写入学习记录。</p><div class="scene-complete-actions"><button class="secondary-button compact" type="button" data-return-reading>回看场景</button><button class="primary-button compact-primary" type="button" data-finish-challenge>完成本场景 (Enter)</button></div></div>`
        elements.sceneAssessment.querySelector('[data-return-reading]')?.addEventListener('click', backToReading)
        elements.sceneAssessment.querySelector('[data-finish-challenge]')?.addEventListener('click', completeCurrentUnit)
      }
      return
    }
    const word = currentSceneWord(unit)
    if (!word) {
      if (elements.sceneAssessment) {
        elements.sceneAssessment.className = 'scene-assessment empty'
        elements.sceneAssessment.textContent = '选择一个核心词开始检查'
      }
      if (elements.sceneAssessmentStage) elements.sceneAssessmentStage.textContent = '未开始'
      return
    }
    preloadAudio?.(word.term)
    const type = nextAssessment(word)
    const passedCount = asArray(word.passedAssessments).filter((item) => requiredAssessments(word).includes(item)).length
    const wordIndex = coreWords.findIndex((item) => sameId(item.id, word.id)) + 1
    if (elements.sceneAssessmentStage) {
      elements.sceneAssessmentStage.textContent = type ? `第 ${wordIndex}/${coreWords.length} 词 · ${passedCount + 1}/${requiredAssessments(word).length}` : '已通过'
    }
    if (elements.sceneAssessment) {
      elements.sceneAssessment.className = 'scene-assessment'
    }
    if (!type) {
      if (elements.sceneAssessment) {
        elements.sceneAssessment.innerHTML = `<div class="scene-assessment-complete"><span class="scene-check-mark">✓</span><strong>${escapeHtml(word.term)} 已完成当前场景检查</strong><p>${escapeHtml(word.meaning || word.contextMeaning || '')}</p><button class="secondary-button compact" type="button" data-next-core-word>检查下一个词 (Space / Enter)</button></div>`
        elements.sceneAssessment.querySelector('[data-next-core-word]')?.addEventListener('click', selectNextCoreWord)
      }
      return
    }
    state.sceneAssessmentType = type
    if (!state.sceneAssessmentStartedAt) state.sceneAssessmentStartedAt = Date.now()
    const feedback = assessmentFeedback ? `<div class="scene-assessment-feedback ${assessmentFeedback.correct ? 'ok' : 'bad'}">${escapeHtml(assessmentFeedback.message)}</div>` : ''
    if (type === 'meaning_choice') {
      const assessment = word.assessment || {}
      if (!word._shuffledOptions || word._shuffledForWordId !== word.id) {
        const rawOptions = asArray(assessment.options)
        const shuffled = [...rawOptions]
        for (let i = shuffled.length - 1; i > 0; i--) {
          const j = Math.floor(Math.random() * (i + 1));
          [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]]
        }
        word._shuffledOptions = shuffled
        word._shuffledForWordId = word.id
      }
      const options = word._shuffledOptions
      if (elements.sceneAssessment) {
        elements.sceneAssessment.innerHTML = `<div class="scene-assessment-prompt"><span class="mini-pill">${ASSESSMENT_LABELS[type]}</span><h4>${escapeHtml(assessment.prompt || `请选择 ${word.term} 在当前场景中的含义`)}</h4><p class="phonetic">${escapeHtml(word.phonetic ? `/${word.phonetic}/` : '暂无音标')}</p></div><div class="scene-choice-list">${options.map((option, index) => `<button type="button" data-scene-answer="${escapeHtml(option)}" data-choice-index="${index}"><span>${String.fromCharCode(65 + index)}</span>${escapeHtml(option)}</button>`).join('')}</div><p class="typing-hint">可按 1~4 或 A~D 键快捷选择 · 按 R 键发音</p>${feedback}`
        elements.sceneAssessment.querySelectorAll('[data-scene-answer]').forEach((button) => button.addEventListener('click', () => {
          playUiTone('correct')
          submitAssessment(button.dataset.sceneAnswer)
        }))
      }
      return
    }
    if (type === 'copy_typing') {
      const term = String(word.term || '').trim()
      const typed = String(state.sceneTypingTyped || '')
      const progressPercent = term.length ? Math.min(100, Math.round((typed.length / term.length) * 100)) : 0
      if (elements.sceneAssessment) {
        elements.sceneAssessment.innerHTML = `<div class="scene-assessment-prompt"><span class="mini-pill">${ASSESSMENT_LABELS[type]}</span><h4 style="cursor: pointer;" data-speak-term="${escapeHtml(word.term)}" title="点击发音">${escapeHtml(word.term)} <span style="font-size: 16px; opacity: 0.8;">🔊</span></h4><p class="phonetic">${escapeHtml(word.phonetic ? `/${word.phonetic}/` : '')} ${escapeHtml(word.contextMeaning || word.meaning || '')}</p></div><div class="typing-board" tabindex="0" aria-label="跟敲单词 ${escapeHtml(word.term)}"><div class="typing-letters">${renderTypingLetters(term, typed)}</div><div class="typing-progress"><span style="width: ${progressPercent}%;"></span></div><p class="typing-hint">键盘直接输入字母 · Backspace 回退 · Alt+P 发音</p></div>${feedback}`
        const board = elements.sceneAssessment.querySelector('.typing-board')
        board?.addEventListener('click', () => board.focus())
        elements.sceneAssessment.querySelectorAll('[data-speak-term]').forEach((el) => {
          el.addEventListener('click', () => speak?.(el.dataset.speakTerm || word.term))
        })
      }
      return
    }
    if (elements.sceneAssessment) {
      elements.sceneAssessment.innerHTML = `<div class="scene-assessment-prompt"><span class="mini-pill">${ASSESSMENT_LABELS[type]}</span><h4>${escapeHtml(word.contextMeaning || word.meaning || '根据含义拼写单词')}</h4><p>${escapeHtml(word.phonetic ? `/${word.phonetic}/` : '')} 请输入对应的英文单词或短语</p></div><form class="scene-spelling-form" data-scene-spelling-form><input data-scene-spelling-input autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="输入英文单词后回车提交" aria-label="${ASSESSMENT_LABELS[type]}答案" /><button class="primary-button compact-primary" type="submit">提交 (Enter)</button></form><p class="typing-hint">输入完成后按 Enter 提交 · Alt+P 发音</p>${feedback}`
      const form = elements.sceneAssessment.querySelector('[data-scene-spelling-form]')
      const input = elements.sceneAssessment.querySelector('[data-scene-spelling-input]')
      form?.addEventListener('submit', (event) => {
        event.preventDefault()
        if (input?.value.trim()) submitAssessment(input.value.trim())
      })
      input?.focus()
      elements.sceneAssessment.querySelectorAll('[data-speak-term]').forEach((el) => {
        el.addEventListener('click', () => speak?.(el.dataset.speakTerm || word.term))
      })
    }
  }

  function selectNextCoreWord() {
    const unit = activeUnit()
    const coreWords = asArray(unit?.words).filter((word) => word.tier === 'core')
    const currentIndex = coreWords.findIndex((word) => sameId(word.id, state.currentSceneWordId))
    const next = coreWords.slice(currentIndex + 1).find((word) => !isWordComplete(word))
      || coreWords.find((word) => !isWordComplete(word))
      || coreWords[(currentIndex + 1) % coreWords.length]
    if (!next) return
    state.currentSceneWordId = next.id
    state.sceneAssessmentStartedAt = Date.now()
    resetAssessment()
    renderCoreWords(coreWords)
    renderAssessment(unit)
  }

  async function submitAssessment(answer) {
    const plan = state.currentLearningPlan
    const unit = activeUnit(plan)
    const word = currentSceneWord(unit)
    const type = nextAssessment(word)
    if (!plan || !unit || !word || !type || !answer) return
    const startedAt = state.sceneAssessmentStartedAt || Date.now()
    try {
      let result
      if (state.preview) {
        const normalizedAnswer = String(answer).trim().toLowerCase()
        const correctAnswer = type === 'meaning_choice' ? word.assessment?.correct_answer || word.contextMeaning || word.meaning : word.term
        const accepted = type === 'meaning_choice' ? [correctAnswer] : asArray(word.acceptedSpellings).length ? word.acceptedSpellings : [word.term]
        const correct = accepted.some((value) => String(value).trim().toLowerCase() === normalizedAnswer)
        if (correct) {
          word.passedAssessments = [...new Set([...asArray(word.passedAssessments), type])]
          word.learningState = isWordComplete(word) ? 'learned' : 'learning'
          word.recognitionScore = type === 'meaning_choice' ? 100 : number(word.recognitionScore)
          word.spellingScore = type !== 'meaning_choice' ? 100 : number(word.spellingScore)
        }
        unit.completedCoreCount = asArray(unit.words).filter((item) => item.tier === 'core' && isWordComplete(item)).length
        result = { correct, correctAnswer, learningState: word.learningState, recognitionScore: word.recognitionScore, spellingScore: word.spellingScore, completedCoreCount: unit.completedCoreCount }
      } else {
        result = await api.submitAssessment(plan.id, unit.id, { unitEntryId: word.id, assessmentType: type, answer, attemptCount: 1, durationMillis: Math.max(0, Date.now() - startedAt) })
      }
      word.learningState = result.learningState
      word.recognitionScore = result.recognitionScore
      word.spellingScore = result.spellingScore
      unit.completedCoreCount = result.completedCoreCount
      if (result.correct) {
        word.passedAssessments = [...new Set([...asArray(word.passedAssessments), type])]
        assessmentFeedback = { correct: true, message: '回答正确，已记录本词学习情况' }
        state.sceneAssessmentStartedAt = Date.now()
        state.sceneTypingTyped = ''
        renderCurrentScene()
      } else {
        assessmentFeedback = { correct: false, message: `还未答对，正确答案：${result.correctAnswer || word.term}` }
        state.sceneTypingTyped = ''
        renderAssessment(unit)
      }
      logEvent('learning', '提交场景词汇检查', `${word.term} · ${ASSESSMENT_LABELS[type]} · ${result.correct ? '正确' : '错误'}`)
    } catch (error) {
      logEvent('error', '场景词汇检查失败', error.message)
      toast(`检查提交失败：${error.message}`)
    }
  }

  function openCoreWordsModal() {
    const unit = activeUnit()
    if (!unit) return
    renderCoreWords(asArray(unit.words).filter((word) => word.tier === 'core'))
    showModal(elements.sceneCoreWordsModal)
  }

  function closeCoreWordsModal() {
    hideModal(elements.sceneCoreWordsModal)
  }

  function openRelatedWordsModal() {
    const unit = activeUnit()
    if (!unit) return
    renderRelatedWords(unit)
    showModal(elements.sceneRelatedWordsModal)
  }

  function closeRelatedWordsModal() {
    hideModal(elements.sceneRelatedWordsModal)
  }

  return {
    currentSceneWord,
    resetAssessment,
    beginAssessment,
    renderChallengeWords,
    applyStage,
    renderLearningText,
    renderCoreWords,
    renderRelatedWords,
    renderAssessment,
    submitAssessment,
    selectNextCoreWord,
    openCoreWordsModal,
    closeCoreWordsModal,
    openRelatedWordsModal,
    closeRelatedWordsModal,
    handleChallengeKeydown,
  }
}
