import { hideModal, showModal } from '/src/shared/modal.js'
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
}) {
  let assessmentFeedback = null

  function currentSceneWord(unit = activeUnit()) {
    const coreWords = asArray(unit?.words).filter((word) => word.tier === 'core')
    return coreWords.find((word) => sameId(word.id, state.currentSceneWordId)) || coreWords[0] || null
  }

  function resetAssessment() {
    assessmentFeedback = null
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

  function renderLearningText(unit, coreWords) {
    const learningText = unit.learningText || unit.material?.learning_text || unit.material?.learningText || ''
    elements.sceneLearningText.className = learningText ? 'scene-learning-text' : 'scene-learning-text empty'
    elements.sceneLearningText.innerHTML = learningText
      ? annotateUnknownWords(learningText, coreWords.filter((word) => word.firstLearning))
      : '暂无场景材料'
    elements.sceneTranslation.textContent = unit.translation || unit.material?.translation || '暂无译文'
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

  function renderAssessment(unit) {
    const coreWords = asArray(unit?.words).filter((item) => item.tier === 'core')
    const completedWords = coreWords.filter(isWordComplete)
    if (coreWords.length && completedWords.length === coreWords.length) {
      elements.sceneAssessmentStage.textContent = `${coreWords.length} / ${coreWords.length}`
      elements.sceneAssessment.className = 'scene-assessment'
      elements.sceneAssessment.innerHTML = `<div class="scene-assessment-complete scene-unit-complete"><span class="scene-check-mark">✓</span><strong>本轮 ${coreWords.length} 个词已全部通过</strong><p>含义识别和要求掌握的拼写项目已写入学习记录。</p><div class="scene-complete-actions"><button class="secondary-button compact" type="button" data-return-reading>回看场景</button><button class="primary-button compact-primary" type="button" data-finish-challenge>完成本场景</button></div></div>`
      elements.sceneAssessment.querySelector('[data-return-reading]')?.addEventListener('click', backToReading)
      elements.sceneAssessment.querySelector('[data-finish-challenge]')?.addEventListener('click', completeCurrentUnit)
      return
    }
    const word = currentSceneWord(unit)
    if (!word) {
      elements.sceneAssessment.className = 'scene-assessment empty'
      elements.sceneAssessment.textContent = '选择一个核心词开始检查'
      elements.sceneAssessmentStage.textContent = '未开始'
      return
    }
    const type = nextAssessment(word)
    const passedCount = asArray(word.passedAssessments).filter((item) => requiredAssessments(word).includes(item)).length
    const wordIndex = coreWords.findIndex((item) => sameId(item.id, word.id)) + 1
    elements.sceneAssessmentStage.textContent = type ? `第 ${wordIndex}/${coreWords.length} 词 · ${passedCount + 1}/${requiredAssessments(word).length}` : '已通过'
    elements.sceneAssessment.className = 'scene-assessment'
    if (!type) {
      elements.sceneAssessment.innerHTML = `<div class="scene-assessment-complete"><span class="scene-check-mark">✓</span><strong>${escapeHtml(word.term)} 已完成当前场景检查</strong><p>${escapeHtml(word.meaning || word.contextMeaning || '')}</p><button class="secondary-button compact" type="button" data-next-core-word>检查下一个词</button></div>`
      elements.sceneAssessment.querySelector('[data-next-core-word]')?.addEventListener('click', selectNextCoreWord)
      return
    }
    state.sceneAssessmentType = type
    if (!state.sceneAssessmentStartedAt) state.sceneAssessmentStartedAt = Date.now()
    const feedback = assessmentFeedback ? `<div class="scene-assessment-feedback ${assessmentFeedback.correct ? 'ok' : 'bad'}">${escapeHtml(assessmentFeedback.message)}</div>` : ''
    if (type === 'meaning_choice') {
      const assessment = word.assessment || {}
      const options = asArray(assessment.options)
      elements.sceneAssessment.innerHTML = `<div class="scene-assessment-prompt"><span class="mini-pill">${ASSESSMENT_LABELS[type]}</span><h4>${escapeHtml(assessment.prompt || `请选择 ${word.term} 在当前场景中的含义`)}</h4><p class="phonetic">${escapeHtml(word.phonetic || '暂无音标')}</p></div><div class="scene-choice-list">${options.map((option, index) => `<button type="button" data-scene-answer="${escapeHtml(option)}"><span>${String.fromCharCode(65 + index)}</span>${escapeHtml(option)}</button>`).join('')}</div>${feedback}`
      elements.sceneAssessment.querySelectorAll('[data-scene-answer]').forEach((button) => button.addEventListener('click', () => submitAssessment(button.dataset.sceneAnswer)))
      return
    }
    const copyTyping = type === 'copy_typing'
    elements.sceneAssessment.innerHTML = `<div class="scene-assessment-prompt"><span class="mini-pill">${ASSESSMENT_LABELS[type]}</span><h4>${copyTyping ? `跟敲 ${escapeHtml(word.term)}` : escapeHtml(word.contextMeaning || word.meaning || '根据含义拼写单词')}</h4><p>${copyTyping ? '按显示内容完整输入单词或短语' : '输入对应的英文单词或短语'}</p></div><form class="scene-spelling-form" data-scene-spelling-form><input data-scene-spelling-input autocomplete="off" autocapitalize="off" spellcheck="false" aria-label="${ASSESSMENT_LABELS[type]}答案" /><button class="primary-button compact-primary" type="submit">提交</button></form>${feedback}`
    const form = elements.sceneAssessment.querySelector('[data-scene-spelling-form]')
    const input = elements.sceneAssessment.querySelector('[data-scene-spelling-input]')
    form?.addEventListener('submit', (event) => {
      event.preventDefault()
      if (input?.value.trim()) submitAssessment(input.value.trim())
    })
    input?.focus()
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
        renderCurrentScene()
      } else {
        assessmentFeedback = { correct: false, message: `还未答对，正确答案：${result.correctAnswer || word.term}` }
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
  }
}
