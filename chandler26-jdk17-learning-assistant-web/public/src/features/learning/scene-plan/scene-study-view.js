import {
  ASSESSMENT_LABELS,
  asArray,
  escapeRegExp,
} from '/src/features/learning/scene-plan/model.js'
import { isWordComplete, requiredAssessments } from '/src/features/learning/scene-plan/challenge-model.js'

/**
 * 场景学习的展示层，负责文章、词汇列表和挑战准备区渲染。
 * 评测状态机仍由 scene-study.js 管理，所有交互通过回调回到状态层。
 */
export function createSceneStudyView({
  state,
  elements,
  activeUnit,
  escapeHtml,
  sameId,
  resetAssessment,
  renderAssessment,
  generateRelatedWords,
  promoteWord,
}) {
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
    elements.sceneLearningStage?.classList.toggle('in-challenge-stage', stage === 'challenge' || stage === 'assessment')
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

  return {
    renderChallengeWords,
    applyStage,
    renderLearningText,
    renderCoreWords,
    renderRelatedWords,
    renderTypingLetters,
  }
}
