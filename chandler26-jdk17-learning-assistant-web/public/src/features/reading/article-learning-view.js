import { escapeHtml } from '/src/shared/text.js'
import { normalizeArray, readText, stringifyValue } from '/src/shared/vocabulary.js'
import { normalizeAnswerValue } from '/src/features/reading/article-model.js'
import {
  renderBilingualArticle,
  renderGrammarPoints,
  renderSimpleList,
  renderVocabularyFocus,
} from '/src/features/reading/article-render.js'

/**
 * 文章学习阶段的视图与事件绑定。
 * 请求、阶段状态和提交动作由 article.js 编排，视图模块只负责 HTML 与 DOM 事件。
 */
export function createArticleLearningView({
  state,
  speakSentence,
  currentWordbookName,
  changeArticleStage,
  setArticleAnswer,
  articleAnswers,
  articleRecordKey,
  completeArticleStudy,
  renderArticleResult,
  cssEscape,
  articleStatusLabel,
  wordCountLabel,
  difficultyLabel,
}) {
  function renderContent(container, badge, record, options = {}) {
    const parsed = record.parsed || {}
    const title = readText(parsed, ['title']) || '语境精读'
    const article = readText(parsed, ['article', 'text', 'content'])
    const translation = readText(parsed, ['translation', 'translation_cn', 'translationCn', 'cn', 'zh'])
    const vocabularyFocus = normalizeArray(parsed.vocabulary_focus || parsed.vocabularyFocus || parsed.words || [])
    const grammarPoints = normalizeArray(parsed.grammar_points || parsed.grammarPoints || [])
    const keyPoints = normalizeArray(parsed.key_points || parsed.keyPoints || [])
    const tips = normalizeArray(parsed.study_tips || parsed.studyTips || parsed.tips || [])

    badge.textContent = `${articleStatusLabel(record.studyStatus)} · ${wordCountLabel(record.wordCountRange)} · ${difficultyLabel(record.difficulty)}`
    container.className = `article-result${options.compact ? ' article-result-preview' : ''}`
    if (options.compact) {
      container.innerHTML = renderPreview(record, title, article, translation, vocabularyFocus)
      container.querySelector('[data-article-speak]')?.addEventListener('click', () => speakSentence(article))
      return
    }
    const stage = record.studyStatus === 'completed' ? state.articleStage : normalizeStage(state.articleStage)
    container.innerHTML = `
      <article class="article-learning-card">
        <header class="article-learning-head">
          <div>
            <p class="eyebrow">${escapeHtml(currentWordbookName(record.wordbookId))}</p>
            <h4>${escapeHtml(title)}</h4>
            <div class="article-target-summary">${renderTargetWordChips(record.selectedWords)}</div>
          </div>
          <button class="icon-button" type="button" data-article-speak title="朗读英文文章" aria-label="朗读英文文章">▶</button>
        </header>
        ${renderStageNav(stage, record)}
        <div class="article-stage-body">
          ${stage === 'vocabulary'
            ? renderVocabularyStage(record, vocabularyFocus, grammarPoints, keyPoints, tips)
            : stage === 'check'
              ? renderCheckStage(record)
              : renderReadingStage(record, article, translation)}
        </div>
      </article>
    `
    bindEvents(container, record, article)
  }

  function renderPreview(record, title, article, translation, vocabularyFocus) {
    return `
      <article class="article-learning-card article-learning-card-preview">
        <header class="article-learning-head">
          <div>
            <p class="eyebrow">${escapeHtml(currentWordbookName(record.wordbookId))}</p>
            <h4>${escapeHtml(title)}</h4>
            <div class="article-target-summary">${renderTargetWordChips(record.selectedWords)}</div>
          </div>
          <button class="icon-button" type="button" data-article-speak title="朗读英文文章" aria-label="朗读英文文章">▶</button>
        </header>
        <section class="article-section">
          <h5>双语正文</h5>
          ${renderBilingualArticle(article, translation, { showTranslation: true, selectedWords: record.selectedWords })}
        </section>
        ${renderVocabularyFocus(vocabularyFocus)}
      </article>
    `
  }

  function renderStageNav(stage, record) {
    const stages = [
      { code: 'reading', number: 1, label: '通读文章' },
      { code: 'vocabulary', number: 2, label: '词汇精讲' },
      { code: 'check', number: 3, label: '阅读检测' },
    ]
    const activeIndex = Math.max(0, stages.findIndex((item) => item.code === stage))
    const progress = record.studyStatus === 'completed' ? 100 : ((activeIndex + 1) / stages.length) * 100
    return `
      <div class="article-stage-shell">
        <nav class="article-stage-nav" aria-label="精读阶段">
          ${stages.map((item, index) => `
            <button class="article-stage-tab ${item.code === stage ? 'active' : ''} ${index < activeIndex || record.studyStatus === 'completed' ? 'done' : ''}"
              type="button" data-article-stage="${item.code}" aria-current="${item.code === stage ? 'step' : 'false'}">
              <span>${item.number}</span><strong>${item.label}</strong>
            </button>
          `).join('')}
        </nav>
        <div class="article-stage-progress" aria-hidden="true"><span style="width:${progress}%"></span></div>
      </div>
    `
  }

  function renderReadingStage(record, article, translation) {
    const showTranslation = state.articleReadingMode === 'bilingual'
    return `
      <section class="article-stage-section article-reading-stage">
        <div class="article-stage-toolbar">
          <h5>英文原文</h5>
          <div class="article-mode-switch" role="group" aria-label="译文显示方式">
            <button type="button" class="${showTranslation ? '' : 'active'}" data-article-reading-mode="english">仅英文</button>
            <button type="button" class="${showTranslation ? 'active' : ''}" data-article-reading-mode="bilingual">双语</button>
          </div>
        </div>
        ${renderBilingualArticle(article, translation, { showTranslation, selectedWords: record.selectedWords })}
        <div class="article-stage-actions">
          <button class="primary-button" type="button" data-article-next-stage="vocabulary">进入词汇精讲</button>
        </div>
      </section>
    `
  }

  function renderVocabularyStage(record, vocabularyFocus, grammarPoints, keyPoints, tips) {
    return `
      <section class="article-stage-section article-vocabulary-stage">
        ${renderVocabularyFocus(vocabularyFocus, record.selectedWords)}
        ${renderGrammarPoints(grammarPoints)}
        ${renderSimpleList('阅读要点', keyPoints)}
        ${renderSimpleList('复习建议', tips)}
        <div class="article-stage-actions article-stage-actions-split">
          <button class="secondary-button" type="button" data-article-next-stage="reading">返回通读</button>
          <button class="primary-button" type="button" data-article-next-stage="check">开始阅读检测</button>
        </div>
      </section>
    `
  }

  function renderCheckStage(record) {
    const parsed = record.parsed || {}
    const practice = normalizeArray(parsed.practice || parsed.questions || [])
    if (!practice.length) {
      return '<section class="article-stage-section"><div class="empty">当前材料没有可用的阅读检测</div></section>'
    }
    const answers = articleAnswers(record)
    const completed = record.studyStatus === 'completed'
    const checked = completed || Boolean(state.articleCheckedRecords[articleRecordKey(record)])
    const result = completed
      ? {
          total: Number(record.practiceTotal || practice.length),
          correct: Number(record.practiceCorrect || 0),
          score: Number(record.practiceScore || 0),
        }
      : scorePractice(practice, answers)
    const allAnswered = practice.every((_, index) => String(answers[index] || '').trim())
    return `
      <section class="article-stage-section article-check-stage">
        <div class="article-check-heading">
          <div><h5>阅读检测</h5><span>${practice.length} 题</span></div>
          ${checked ? `<strong>${result.score} 分</strong>` : `<strong>${answers.filter((answer) => String(answer || '').trim()).length}/${practice.length}</strong>`}
        </div>
        <div class="article-practice-list">
          ${practice.map((item, index) => renderPracticeQuestion(item, index, answers[index], checked, completed)).join('')}
        </div>
        ${checked ? renderPracticeResult(result, completed) : ''}
        <div class="article-stage-actions article-stage-actions-split">
          <button class="secondary-button" type="button" data-article-next-stage="vocabulary">返回词汇精讲</button>
          ${completed
            ? '<button class="primary-button" type="button" data-article-next-stage="reading">再次阅读</button>'
            : checked
              ? '<span class="article-check-commands"><button class="secondary-button" type="button" data-article-reset-check>重新作答</button><button class="primary-button" type="button" data-article-complete>完成本次精读</button></span>'
              : `<button class="primary-button" type="button" data-article-check ${allAnswered ? '' : 'disabled'}>检查答案</button>`}
        </div>
      </section>
    `
  }

  function renderPracticeQuestion(item, index, selectedAnswer, checked, completed) {
    const question = readText(item, ['question', 'stem']) || `问题 ${index + 1}`
    const options = normalizeArray(item?.options || [])
    const correctAnswer = readText(item, ['correct_answer', 'correctAnswer', 'answer'])
    const explanation = readText(item, ['explanation', 'analysis'])
    const selected = String(selectedAnswer || '')
    const correct = normalizeAnswerValue(selected) === normalizeAnswerValue(correctAnswer)
    const questionState = checked && !completed ? (correct ? 'correct' : 'incorrect') : ''
    return `
      <article class="article-practice-question ${questionState}">
        <header><span>${index + 1}</span><strong>${escapeHtml(question)}</strong></header>
        ${options.length
          ? `<div class="article-practice-options">
              ${options.map((option) => {
                const value = stringifyValue(option)
                const optionSelected = normalizeAnswerValue(value) === normalizeAnswerValue(selected)
                const optionCorrect = normalizeAnswerValue(value) === normalizeAnswerValue(correctAnswer)
                const classes = [optionSelected ? 'selected' : '', checked && optionCorrect ? 'correct' : '', checked && optionSelected && !optionCorrect ? 'incorrect' : ''].filter(Boolean).join(' ')
                return `<button type="button" class="${classes}" data-article-answer-index="${index}" data-article-answer="${escapeHtml(value)}" ${checked ? 'disabled' : ''}>${escapeHtml(value)}</button>`
              }).join('')}
            </div>`
          : `<label class="article-free-answer"><span class="sr-only">第 ${index + 1} 题答案</span><input data-article-free-answer="${index}" value="${escapeHtml(selected)}" placeholder="输入答案" ${checked ? 'disabled' : ''} /></label>`}
        ${checked ? `<div class="article-answer-feedback"><strong>${correct || completed ? '正确答案' : '本题未答对'}：${escapeHtml(correctAnswer || '暂无')}</strong>${explanation ? `<p>${escapeHtml(explanation)}</p>` : ''}</div>` : ''}
      </article>
    `
  }

  function renderPracticeResult(result, completed) {
    return `
      <div class="article-check-result ${result.score >= 60 ? 'passed' : 'needs-review'}">
        <strong>${completed ? '本次精读已完成' : result.score >= 60 ? '检测通过' : '建议回看文章'}</strong>
        <span>${result.correct}/${result.total} 题正确 · ${result.score} 分</span>
      </div>
    `
  }

  function renderTargetWordChips(words) {
    const values = Array.isArray(words) ? words : []
    return values.length
      ? values.map((item) => `<span>${escapeHtml(item.term || item.normalizedTerm)}</span>`).join('')
      : '<span>暂无目标词</span>'
  }

  function bindEvents(container, record, article) {
    container.querySelector('[data-article-speak]')?.addEventListener('click', () => speakSentence(article))
    container.querySelectorAll('[data-article-stage], [data-article-next-stage]').forEach((button) => {
      button.addEventListener('click', () => changeArticleStage(button.dataset.articleStage || button.dataset.articleNextStage))
    })
    container.querySelectorAll('[data-article-reading-mode]').forEach((button) => {
      button.addEventListener('click', () => {
        state.articleReadingMode = button.dataset.articleReadingMode
        renderArticleResult(record)
      })
    })
    container.querySelectorAll('[data-article-target]').forEach((button) => {
      button.addEventListener('click', async () => {
        const term = button.dataset.articleTarget
        await changeArticleStage('vocabulary')
        document.querySelector(`[data-article-focus-word="${cssEscape(term)}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      })
    })
    container.querySelectorAll('[data-article-answer-index]').forEach((button) => {
      button.addEventListener('click', () => {
        setArticleAnswer(record, Number(button.dataset.articleAnswerIndex), button.dataset.articleAnswer)
        renderArticleResult(record)
      })
    })
    container.querySelectorAll('[data-article-free-answer]').forEach((input) => {
      input.addEventListener('input', () => {
        setArticleAnswer(record, Number(input.dataset.articleFreeAnswer), input.value)
        const practice = normalizeArray(record.parsed?.practice || record.parsed?.questions || [])
        const allAnswered = practice.every((_, index) => String(articleAnswers(record)[index] || '').trim())
        container.querySelector('[data-article-check]')?.toggleAttribute('disabled', !allAnswered)
      })
    })
    container.querySelector('[data-article-check]')?.addEventListener('click', () => {
      state.articleCheckedRecords[articleRecordKey(record)] = true
      renderArticleResult(record)
    })
    container.querySelector('[data-article-reset-check]')?.addEventListener('click', () => {
      state.articleCheckedRecords[articleRecordKey(record)] = false
      state.articleAnswerSets[articleRecordKey(record)] = []
      renderArticleResult(record)
    })
    container.querySelector('[data-article-complete]')?.addEventListener('click', completeArticleStudy)
  }

  function scorePractice(practice, answers) {
    const correct = practice.reduce((count, item, index) => {
      const expected = readText(item, ['correct_answer', 'correctAnswer', 'answer'])
      return count + (normalizeAnswerValue(answers[index]) === normalizeAnswerValue(expected) ? 1 : 0)
    }, 0)
    const total = practice.length
    return { total, correct, score: total ? Math.round((correct * 100) / total) : 0 }
  }

  function normalizeStage(stage) {
    return ['reading', 'vocabulary', 'check'].includes(stage) ? stage : 'reading'
  }

  return { renderContent, renderTargetWordChips }
}
