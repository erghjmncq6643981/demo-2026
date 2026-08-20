import { sameId } from '/src/shared/ids.js'
import { hideModal, showModal } from '/src/shared/modal.js'
import { escapeHtml, formatDateTime } from '/src/shared/text.js'
import { normalizeArray, normalizeDefinitions, readText, statusLabel, stringifyValue } from '/src/shared/vocabulary.js'
import { syncCurrentWordbookId } from '/src/shared/wordbook.js'
import { isRequestAbort } from '/src/shared/latest-request.js'
import { createArticleApi } from '/src/features/reading/article-api.js'
import {
  ARTICLE_WORD_LIMIT,
  articleStatusLabel,
  formatArticleErrorForLog,
  normalizeAnswerValue,
  normalizeArticleError,
  normalizeArticleStage,
  readArticleError,
  scoreArticlePractice,
  splitArticleLines,
} from '/src/features/reading/article-model.js'

export function createWordbookArticleFeature(ctx) {
  const {
    state,
    elements,
    request,
    setLoading,
    toast,
    logEvent,
    confirmAction,
    speakSentence,
  } = ctx
  const api = createArticleApi(request)

  async function changeArticleWordbook(wordbookId) {
    syncCurrentWordbookId(state, elements, wordbookId)
    state.selectedArticleEntryIds = []
    state.currentArticleRecord = null
    state.articleDraftRecord = null
    state.articleStage = 'reading'
    state.articleAnswerSets = {}
    state.articleCheckedRecords = {}
    renderArticleResult(null)
    renderArticleModalPreview(null)
    await Promise.allSettled([loadArticleWords(), loadArticleHistory()])
    logEvent('wordbook', '切换语境精读单词本', currentWordbookName())
  }

  async function openArticleStudyModal() {
    if (!state.currentWordbookId && elements.articleWordbookSelect?.value) {
      syncCurrentWordbookId(state, elements, elements.articleWordbookSelect.value)
    }
    if (!state.currentWordbookId) {
      toast('请先选择单词本')
      return
    }
    state.articleModalOpen = true
    state.articleDraftRecord = null
    state.articlePreviewError = ''
    renderArticleModalPreview(null)
    showModal(elements.articleStudyModal)
    await loadArticleWords()
  }

  function closeArticleStudyModal() {
    state.articleModalOpen = false
    state.articleDraftRecord = null
    state.articlePreviewError = ''
    hideModal(elements.articleStudyModal)
  }

  function loadArticleWords() {
    if (state.preview) {
      state.articleEntries = state.wordbookEntries.slice()
      pruneSelectedArticleEntries()
      renderArticleWords()
      return Promise.resolve()
    }
    if (!state.token || !state.currentWordbookId) {
      state.articleEntries = []
      state.selectedArticleEntryIds = []
      renderArticleWords()
      return Promise.resolve()
    }
    const status = elements.articleStatusFilter?.value || ''
    return api.listEntries(state.currentWordbookId, status)
      .then((entries) => {
        state.articleEntries = Array.isArray(entries) ? entries : []
        pruneSelectedArticleEntries()
        renderArticleWords()
      })
      .catch((error) => {
        if (isRequestAbort(error)) return
        logEvent('error', '语境精读词汇加载失败', error.message)
        toast(`精读词汇加载失败：${error.message}`)
      })
  }

  function loadArticleHistory() {
    if (state.preview) {
      if (!state.articleRecords.length) {
        state.articleRecords = [previewArticleRecord()]
        state.currentArticleRecord = state.articleRecords[0]
      }
      renderArticleHistory()
      renderArticleResult(state.currentArticleRecord)
      return Promise.resolve()
    }
    if (!state.token || !state.currentWordbookId) {
      state.articleRecords = []
      renderArticleHistory()
      renderArticleResult(null)
      return Promise.resolve()
    }
    return api.listRecords(state.currentWordbookId)
      .then((records) => {
        state.articleRecords = Array.isArray(records) ? records : []
        renderArticleHistory()
        if (!state.articleRecords.length) {
          renderArticleResult(null)
        } else if (!state.currentArticleRecord) {
          renderArticleResult(state.articleRecords[0])
        }
      })
      .catch((error) => {
        if (isRequestAbort(error)) return
        logEvent('error', '语境精读历史加载失败', error.message)
      })
  }

  function renderArticleWords() {
    if (!elements.articleWordGrid) return
    const entries = filteredArticleEntries()
    elements.articleSelectedCount.textContent = `已选 ${state.selectedArticleEntryIds.length}`
    if (!entries.length) {
      elements.articleWordGrid.className = 'article-word-grid empty'
      elements.articleWordGrid.textContent = state.token ? '当前筛选下暂无单词' : '登录后查看单词本词汇'
      return
    }
    elements.articleWordGrid.className = 'article-word-grid'
    elements.articleWordGrid.innerHTML = entries.map(renderArticleWordCard).join('')
    elements.articleWordGrid.querySelectorAll('[data-article-entry-id]').forEach((button) => {
      button.addEventListener('click', () => toggleArticleEntry(button.getAttribute('data-article-entry-id')))
    })
  }

  function filteredArticleEntries() {
    const prefix = String(state.articlePrefixFilter || elements.articlePrefixInput?.value || '').trim().toLowerCase()
    const status = elements.articleStatusFilter?.value || ''
    const statusFiltered = status ? state.articleEntries.filter((entry) => (entry.status || 'vague') === status) : state.articleEntries
    return prefix ? statusFiltered.filter((entry) => entryMatchesFilter(entry, prefix)) : statusFiltered
  }

  function renderArticleWordCard(entry) {
    const selected = state.selectedArticleEntryIds.some((id) => sameId(id, entry.id))
    const definition = normalizeDefinitions(entry.parsed || {})[0] || {}
    const meaning = definition.cn || definition.en || '暂无核心含义'
    const pos = definition.pos || 'meaning'
    return `
      <button class="article-word-card ${selected ? 'selected' : ''}" type="button" data-article-entry-id="${escapeHtml(entry.id)}" aria-pressed="${selected}">
        <span class="article-word-topline">
          <strong>${escapeHtml(entry.term || entry.normalizedTerm)}</strong>
          <small>${escapeHtml(statusLabel(entry.status))}</small>
        </span>
        <p>${escapeHtml(pos)} · ${escapeHtml(meaning)}</p>
        <span class="selection-dot" aria-hidden="true"></span>
      </button>
    `
  }

  function toggleArticleEntry(entryId) {
    const exists = state.selectedArticleEntryIds.some((id) => sameId(id, entryId))
    if (exists) {
      state.selectedArticleEntryIds = state.selectedArticleEntryIds.filter((id) => !sameId(id, entryId))
    } else if (state.selectedArticleEntryIds.length >= ARTICLE_WORD_LIMIT) {
      toast(`一次最多选择 ${ARTICLE_WORD_LIMIT} 个单词`)
      return
    } else {
      state.selectedArticleEntryIds.push(entryId)
    }
    state.articleDraftRecord = null
    state.articlePreviewError = ''
    renderArticleWords()
    renderArticleModalPreview(null)
  }

  function clearArticleSelection() {
    state.selectedArticleEntryIds = []
    state.articleDraftRecord = null
    state.articlePreviewError = ''
    renderArticleWords()
    renderArticleModalPreview(null)
  }

  function recommendArticleWords() {
    const priority = { forgotten: 0, vague: 1, familiar: 2 }
    const candidates = [...filteredArticleEntries()]
      .sort((left, right) => {
        const statusDelta = (priority[left.status] ?? 1) - (priority[right.status] ?? 1)
        if (statusDelta !== 0) return statusDelta
        return Number(left.masteryScore || 0) - Number(right.masteryScore || 0)
      })
      .slice(0, Math.min(8, ARTICLE_WORD_LIMIT))
    state.selectedArticleEntryIds = candidates.map((entry) => entry.id)
    state.articleDraftRecord = null
    state.articlePreviewError = ''
    renderArticleWords()
    renderArticleModalPreview(null)
    toast(candidates.length ? `已推荐 ${candidates.length} 个优先学习词` : '当前筛选下没有可推荐词汇')
  }

  async function generateArticlePreview(options = {}) {
    if (!state.selectedArticleEntryIds.length) {
      toast('请先勾选要学习的词汇')
      return
    }
    if (!state.currentWordbookId) {
      toast('请先选择单词本')
      return
    }
    const forceRefresh = Boolean(options.forceRefresh)
    if (forceRefresh) {
      const confirmed = await confirmAction({
        title: '重新生成精读材料',
        message: '确认重新调用 AI 生成一份新材料？已有精读记录仍会保留。',
        acceptText: '确认生成',
      })
      if (!confirmed) return
    }
    const payload = {
      wordbookId: state.currentWordbookId,
      entryIds: state.selectedArticleEntryIds,
      wordCountRange: elements.articleWordCountSelect.value,
      difficulty: elements.articleDifficultySelect.value,
      remark: elements.articleRemarkInput.value.trim(),
      modelConfigId: elements.articleModelSelect?.value || null,
      forceRefresh,
    }
    setLoading(true)
    state.articlePreviewLoading = true
    state.articlePreviewError = ''
    updateArticlePreviewControls()
    try {
      if (state.preview) {
        const record = previewArticleRecord(payload)
        record.cacheHit = !forceRefresh
        state.articleDraftRecord = record
        renderArticleModalPreview(record)
        toast(forceRefresh ? '设计预览：已模拟重新生成文章' : '设计预览：已生成文章预览')
        return
      }
      const record = await api.createStudy(payload)
      state.articleDraftRecord = record
      renderArticleModalPreview(record)
      logEvent(record.cacheHit ? 'cache' : 'ai', record.cacheHit ? '读取语境精读缓存' : 'AI 生成语境精读材料', selectedWordsText(record))
      toast(record.cacheHit ? '已读取精读材料缓存' : '精读材料已生成')
    } catch (error) {
      state.articleDraftRecord = null
      state.articlePreviewError = normalizeArticleError(error)
      logEvent('error', '精读材料生成失败', formatArticleErrorForLog(state.articlePreviewError))
      toast(`精读材料生成失败：${state.articlePreviewError.message}`)
      renderArticleModalPreview(null)
    } finally {
      state.articlePreviewLoading = false
      updateArticlePreviewControls()
      setLoading(false)
    }
  }

  async function saveArticleStudy() {
    if (!state.articleDraftRecord) {
      toast('请先生成文章预览')
      return
    }
    let record = state.articleDraftRecord
    try {
      if (state.preview) {
        record = { ...record, studyStatus: 'in_progress', currentStage: 'reading', startedTime: new Date().toISOString() }
      } else {
        record = await api.updateProgress(record.id, { stage: 'reading' })
      }
      replaceArticleRecord(record)
      state.articleStage = 'reading'
      renderArticleResult(record)
      renderArticleHistory()
      const title = readText(record.parsed, ['title']) || selectedWordsText(record) || '语境精读'
      logEvent('article', '开始语境精读', title)
      closeArticleStudyModal()
      toast('已进入语境精读')
    } catch (error) {
      logEvent('error', '开始语境精读失败', error.message)
      toast(`开始精读失败：${error.message}`)
    }
  }

  function generateArticle(options = {}) {
    return generateArticlePreview(options)
  }

  async function openArticleRecord(recordId) {
    let record = state.articleRecords.find((item) => sameId(item.id, recordId))
    try {
      if (!state.preview && !record?.parsed) {
        record = await api.getRecord(recordId)
      }
      if (!record) return
      const stage = record.studyStatus === 'completed' ? 'check' : normalizeArticleStage(record.currentStage)
      if (!state.preview && record.studyStatus !== 'completed') {
        record = await persistArticleStage(record, stage)
      } else if (state.preview && record.studyStatus !== 'completed') {
        record = { ...record, studyStatus: 'in_progress', currentStage: stage, startedTime: record.startedTime || new Date().toISOString() }
        replaceArticleRecord(record)
      }
      state.articleStage = stage
      renderArticleResult(record)
      renderArticleHistory()
    } catch (error) {
      logEvent('error', '精读记录打开失败', error.message)
      toast(`精读记录打开失败：${error.message}`)
    }
  }

  function renderArticleHistory() {
    if (!elements.articleHistoryList) return
    const records = state.articleRecords || []
    if (!records.length) {
      elements.articleHistoryList.className = 'article-history-list empty'
      elements.articleHistoryList.textContent = '暂无精读记录'
      return
    }
    elements.articleHistoryList.className = 'article-history-list'
    elements.articleHistoryList.innerHTML = records
      .map((record) => {
        const title = readText(record.parsed, ['title']) || selectedWordsText(record) || '语境精读'
        const time = formatDateTime(record.createTime || record.createdAt || record.updateTime)
        return `
          <button class="article-history-item ${state.currentArticleRecord && sameId(state.currentArticleRecord.id, record.id) ? 'active' : ''}" type="button" data-article-record="${escapeHtml(record.id)}">
            <span class="article-history-title"><strong>${escapeHtml(title)}</strong></span>
            <small class="article-history-time">${escapeHtml(time)}</small>
          </button>
        `
      })
      .join('')
    elements.articleHistoryList.querySelectorAll('[data-article-record]').forEach((button) => {
      button.addEventListener('click', () => openArticleRecord(button.getAttribute('data-article-record')))
    })
  }

  function renderArticleResult(record) {
    const previousRecordId = state.currentArticleRecord?.id
    state.currentArticleRecord = record || null
    if (!elements.articleResult) return
    if (!record) {
      elements.articleResultBadge.textContent = '等待生成'
      elements.articleResult.className = 'article-result empty'
      elements.articleResult.textContent = '选择一份精读材料开始学习'
      return
    }
    if (!sameId(previousRecordId, record.id)) {
      state.articleStage = record.studyStatus === 'completed' ? 'check' : normalizeArticleStage(record.currentStage)
    }
    renderArticleContent(elements.articleResult, elements.articleResultBadge, record)
    renderArticleHistory()
  }

  function renderArticleModalPreview(record) {
    if (!elements.articleModalPreview) return
    if (!record) {
      const articleError = readArticleError(state.articlePreviewError)
      const errorMessage = articleError?.message || ''
      elements.articleModalPreviewBadge.textContent = errorMessage ? '生成失败' : state.articlePreviewLoading ? '生成中' : '等待生成'
      elements.articleModalPreview.className = errorMessage ? 'article-result empty article-result-error' : 'article-result empty'
      if (articleError) {
        elements.articleModalPreview.innerHTML = renderArticleError(articleError)
      } else {
        elements.articleModalPreview.textContent = '选择目标词并生成精读材料'
      }
      updateArticlePreviewControls()
      return
    }
    renderArticleContent(elements.articleModalPreview, elements.articleModalPreviewBadge, record, { compact: true })
    updateArticlePreviewControls()
  }

  function updateArticlePreviewControls() {
    if (elements.articlePreviewGenerateBtn) {
      elements.articlePreviewGenerateBtn.disabled = state.articlePreviewLoading
      elements.articlePreviewGenerateBtn.textContent = state.articlePreviewLoading ? '生成中...' : '生成学习材料'
    }
    if (elements.saveArticleStudyBtn) {
      elements.saveArticleStudyBtn.disabled = state.articlePreviewLoading || !state.articleDraftRecord
    }
  }

  function renderArticleContent(container, badge, record, options = {}) {
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
      container.innerHTML = renderArticlePreview(record, title, article, translation, vocabularyFocus)
      container.querySelector('[data-article-speak]')?.addEventListener('click', () => speakSentence(article))
      return
    }
    const stage = record.studyStatus === 'completed' ? state.articleStage : normalizeArticleStage(state.articleStage)
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
        ${renderArticleStageNav(stage, record)}
        <div class="article-stage-body">
          ${stage === 'vocabulary'
            ? renderVocabularyStage(record, vocabularyFocus, grammarPoints, keyPoints, tips)
            : stage === 'check'
              ? renderCheckStage(record)
              : renderReadingStage(record, article, translation)}
        </div>
      </article>
    `
    bindArticleLearningEvents(container, record, article)
  }

  function renderArticlePreview(record, title, article, translation, vocabularyFocus) {
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

  function renderArticleStageNav(stage, record) {
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
      : scoreArticlePractice(practice, answers)
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

  function bindArticleLearningEvents(container, record, article) {
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
        elements.articleResult?.querySelector(`[data-article-focus-word="${cssEscape(term)}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
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

  async function changeArticleStage(stage) {
    const normalized = normalizeArticleStage(stage)
    let record = state.currentArticleRecord
    if (!record || normalized === state.articleStage) return
    try {
      if (state.preview) {
        record = record.studyStatus === 'completed'
          ? record
          : { ...record, studyStatus: 'in_progress', currentStage: normalized, startedTime: record.startedTime || new Date().toISOString() }
        replaceArticleRecord(record)
      } else if (record.studyStatus !== 'completed') {
        record = await persistArticleStage(record, normalized)
      }
      state.articleStage = normalized
      renderArticleResult(record)
    } catch (error) {
      logEvent('error', '精读阶段切换失败', error.message)
      toast(`切换学习阶段失败：${error.message}`)
    }
  }

  async function completeArticleStudy() {
    const record = state.currentArticleRecord
    if (!record) return
    const practice = normalizeArray(record.parsed?.practice || record.parsed?.questions || [])
    const answers = articleAnswers(record)
    const payload = {
      answers: practice.map((_, questionIndex) => ({ questionIndex, answer: String(answers[questionIndex] || '').trim() })),
    }
    try {
      let completed
      if (state.preview) {
        const result = scoreArticlePractice(practice, answers)
        completed = {
          ...record,
          studyStatus: 'completed',
          currentStage: 'completed',
          practiceTotal: result.total,
          practiceCorrect: result.correct,
          practiceScore: result.score,
          completedTime: new Date().toISOString(),
          updateTime: new Date().toISOString(),
        }
      } else {
        completed = await api.complete(record.id, payload)
      }
      replaceArticleRecord(completed)
      state.articleStage = 'check'
      state.articleCheckedRecords[articleRecordKey(completed)] = true
      renderArticleResult(completed)
      renderArticleHistory()
      logEvent('article', '完成语境精读', `${readText(completed.parsed, ['title']) || '精读材料'} · ${completed.practiceScore || 0} 分`)
      toast(`精读完成，阅读检测 ${completed.practiceScore || 0} 分`)
    } catch (error) {
      logEvent('error', '完成语境精读失败', error.message)
      toast(`完成精读失败：${error.message}`)
    }
  }

  async function persistArticleStage(record, stage) {
    const updated = await api.updateProgress(record.id, { stage })
    replaceArticleRecord(updated)
    return updated
  }

  function replaceArticleRecord(record) {
    if (!record) return
    state.articleRecords = [record, ...state.articleRecords.filter((item) => !sameId(item.id, record.id))]
    if (state.articleDraftRecord && sameId(state.articleDraftRecord.id, record.id)) state.articleDraftRecord = record
    if (state.currentArticleRecord && sameId(state.currentArticleRecord.id, record.id)) state.currentArticleRecord = record
  }

  function articleAnswers(record) {
    return state.articleAnswerSets[articleRecordKey(record)] || []
  }

  function setArticleAnswer(record, index, value) {
    const key = articleRecordKey(record)
    const answers = [...(state.articleAnswerSets[key] || [])]
    answers[index] = value
    state.articleAnswerSets[key] = answers
    state.articleCheckedRecords[key] = false
  }

  function articleRecordKey(record) {
    return String(record?.id || 'draft')
  }

  function cssEscape(value) {
    return window.CSS?.escape ? window.CSS.escape(String(value || '')) : String(value || '').replace(/["\\]/g, '\\$&')
  }

  function renderArticleError(error) {
    const meta = [
      error.errorCode ? `错误码：${error.errorCode}` : '',
      error.status ? `HTTP：${error.status}` : '',
    ].filter(Boolean)
    return `
      <div class="article-error-content">
        <strong>精读材料生成失败</strong>
        <p>${escapeHtml(error.message)}</p>
        ${error.suggestion ? `<small>${escapeHtml(error.suggestion)}</small>` : ''}
        ${meta.length ? `<span>${escapeHtml(meta.join(' · '))}</span>` : ''}
      </div>
    `
  }

  function renderVocabularyFocus(items, selectedWords = []) {
    if (!items.length) return ''
    return `
      <section class="article-section">
        <h5>目标词精讲</h5>
        <div class="article-vocabulary-list">
          ${items
            .map((item) => {
              const word = readText(item, ['word', 'term']) || stringifyValue(item)
              const meaning = readText(item, ['meaning', 'translation', 'cn'])
              const usage = readText(item, ['usage', 'explanation', 'tip'])
              const sentence = readText(item, ['sentence', 'example'])
              const translation = readText(item, ['translation', 'sentence_translation', 'sentenceTranslation', 'zh'])
              const selectedWord = selectedWords.find((entry) => normalizeAnswerValue(entry.term || entry.normalizedTerm) === normalizeAnswerValue(word))
              return `
                <article class="article-vocabulary-row" data-article-focus-word="${escapeHtml(word)}">
                  <header><strong>${escapeHtml(word)}</strong><span>${escapeHtml(selectedWord?.partOfSpeech || '')}</span></header>
                  <p>${escapeHtml(meaning || selectedWord?.meaning || '暂无核心含义')}</p>
                  ${usage ? `<div>${escapeHtml(usage)}</div>` : ''}
                  ${sentence ? `<small>${escapeHtml(sentence)}</small>` : ''}
                  ${translation && translation !== meaning ? `<small>${escapeHtml(translation)}</small>` : ''}
                </article>
              `
            })
            .join('')}
        </div>
      </section>
    `
  }

  function renderGrammarPoints(items) {
    if (!items.length) return ''
    return `
      <section class="article-section">
        <h5>语法知识点</h5>
        <div class="article-stack">
          ${items
            .map((item) => {
              const examples = normalizeArray(item?.examples || [])
              return `
                <div class="article-info-block">
                  <strong>${escapeHtml(readText(item, ['title', 'name']) || '语法点')}</strong>
                  <p>${escapeHtml(readText(item, ['explanation', 'description', 'content']) || stringifyValue(item))}</p>
                  ${
                    examples.length
                      ? examples
                          .map((example) => {
                            const sentence = readText(example, ['sentence', 'example', 'text'])
                            const translation = readText(example, ['translation', 'cn', 'zh'])
                            return `<small>${escapeHtml([sentence, translation].filter(Boolean).join(' / '))}</small>`
                          })
                          .join('')
                      : ''
                  }
                </div>
              `
            })
            .join('')}
        </div>
      </section>
    `
  }

  function renderSimpleList(title, items) {
    if (!items.length) return ''
    return `
      <section class="article-section">
        <h5>${escapeHtml(title)}</h5>
        <ul class="article-point-list">
          ${items.map((item) => `<li>${escapeHtml(typeof item === 'string' ? item : stringifyValue(item))}</li>`).join('')}
        </ul>
      </section>
    `
  }

  function renderBilingualArticle(article, translation, options = {}) {
    const englishLines = splitArticleLines(article, 'en')
    const chineseLines = splitArticleLines(translation, 'zh')
    const showTranslation = options.showTranslation !== false
    const lineCount = showTranslation ? Math.max(englishLines.length, chineseLines.length) : englishLines.length
    if (!lineCount) {
      return '<div class="empty">暂无文章内容</div>'
    }
    return `
      <div class="article-bilingual-lines">
        ${Array.from({ length: lineCount })
          .map((_, index) => {
            const english = englishLines[index] || ''
            const chinese = showTranslation ? chineseLines[index] || '' : ''
            return `
              <div class="article-bilingual-pair">
                ${english ? `<p class="article-line-en">${renderHighlightedArticleText(english, options.selectedWords)}</p>` : ''}
                ${chinese ? `<p class="article-line-zh">${escapeHtml(chinese)}</p>` : ''}
              </div>
            `
          })
          .join('')}
      </div>
    `
  }

  function renderHighlightedArticleText(text, selectedWords) {
    const words = (Array.isArray(selectedWords) ? selectedWords : [])
      .filter((item) => String(item.term || item.normalizedTerm || '').trim())
      .sort((left, right) => String(right.term || right.normalizedTerm).length - String(left.term || left.normalizedTerm).length)
    if (!words.length) return escapeHtml(text)
    const byTerm = new Map(words.map((item) => [normalizeAnswerValue(item.term || item.normalizedTerm), item]))
    const source = words.map((item) => String(item.term || item.normalizedTerm).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')
    const pattern = new RegExp(`(${source})`, 'gi')
    return String(text).split(pattern).map((part) => {
      const word = byTerm.get(normalizeAnswerValue(part))
      if (!word) return escapeHtml(part)
      const term = word.term || word.normalizedTerm
      const hint = [term, word.partOfSpeech, word.meaning].filter(Boolean).join(' · ')
      return `<button class="article-target-word" type="button" data-article-target="${escapeHtml(term)}" title="${escapeHtml(hint)}">${escapeHtml(part)}</button>`
    }).join('')
  }

  function pruneSelectedArticleEntries() {
    state.selectedArticleEntryIds = state.selectedArticleEntryIds.filter((id) => state.articleEntries.some((entry) => sameId(entry.id, id)))
  }

  function entryMatchesFilter(entry, prefix) {
    const definitions = normalizeDefinitions(entry.parsed || {})
    const meaning = definitions.map((item) => `${item.pos || ''} ${item.cn || ''} ${item.en || ''}`).join(' ')
    const values = [entry.term, entry.normalizedTerm, entry.parsed?.term, meaning]
    return values.some((value) => String(value || '').trim().toLowerCase().startsWith(prefix))
  }

  function selectedWordsText(record) {
    const words = Array.isArray(record?.selectedWords) ? record.selectedWords : []
    return words.map((item) => item.term || item.normalizedTerm).filter(Boolean).join('、')
  }

  function currentWordbookName(wordbookId = state.currentWordbookId) {
    return state.wordbooks.find((item) => sameId(item.id, wordbookId))?.name || '所选单词本'
  }

  function wordCountLabel(value) {
    return value || '300-500'
  }

  function difficultyLabel(value) {
    return (
      {
        easy: '基础',
        medium: '适中',
        hard: '挑战',
      }[value] || value || '适中'
    )
  }

  function previewArticleRecord(payload = {}) {
    const selectedEntries = state.articleEntries.filter((entry) => state.selectedArticleEntryIds.some((id) => sameId(id, entry.id)))
    const selectedWords = (selectedEntries.length ? selectedEntries : state.wordbookEntries.slice(0, 3)).map((entry) => {
      const definition = normalizeDefinitions(entry.parsed || {})[0] || {}
      return {
        entryId: entry.id,
        term: entry.term || entry.normalizedTerm,
        normalizedTerm: entry.normalizedTerm,
        status: entry.status || 'vague',
        partOfSpeech: definition.pos || 'meaning',
        meaning: definition.cn || '核心含义',
      }
    })
    return {
      id: payload.forceRefresh ? String(Date.now()) : 'preview-article',
      wordbookId: state.currentWordbookId || '1',
      selectedWords,
      wordCountRange: payload.wordCountRange || '300-500',
      difficulty: payload.difficulty || 'medium',
      remark: payload.remark || '偏日常语境，突出语法点。',
      cacheHit: false,
      studyStatus: 'generated',
      currentStage: 'reading',
      practiceTotal: 0,
      practiceCorrect: 0,
      practiceScore: 0,
      provider: 'preview',
      modelName: 'mock-article',
      sessionId: 'preview',
      updateTime: new Date().toISOString(),
      parsed: {
        title: 'A Choice That Changed the Plan',
        article: 'Mia had to abandon an old plan, but she decided to maintain her confidence. Instead of giving up, she compared several options and found a clear contrast between fear and careful action.',
        translation: '米娅不得不放弃一个旧计划，但她决定保持自信。她没有直接放弃，而是比较了几个选择，并看清了恐惧和谨慎行动之间的差别。',
        vocabulary_focus: selectedWords.map((word) => ({
          word: word.term,
          meaning: word.meaning,
          usage: '文章中用于真实语境复现',
          sentence: `Try to use ${word.term} in your own sentence.`,
          translation: `尝试用 ${word.term} 写一个自己的句子。`,
        })),
        grammar_points: [
          {
            title: 'Instead of + doing',
            explanation: 'instead of 后面常接名词或动名词，用来表达“不是……而是……”。',
            examples: [{ sentence: 'Instead of giving up, she tried again.', translation: '她没有放弃，而是又试了一次。' }],
          },
        ],
        key_points: ['把词汇放进连续文章，更容易记住语境。', '注意文章中的转折连接词。'],
        practice: [
          {
            question: 'What did Mia decide to maintain?',
            options: ['Her confidence.', 'Her old plan.', 'Her fear.', 'Her schedule.'],
            correct_answer: 'Her confidence.',
            explanation: '文章第一句说明她决定保持自信。',
          },
          {
            question: 'What did Mia abandon?',
            options: ['An old plan.', 'A new job.', 'A travel guide.', 'A class.'],
            correct_answer: 'An old plan.',
            explanation: '文章开头说明她不得不放弃旧计划。',
          },
          {
            question: 'What contrast did Mia notice?',
            options: ['Fear and careful action.', 'Work and travel.', 'Day and night.', 'Success and money.'],
            correct_answer: 'Fear and careful action.',
            explanation: '文章结尾比较了恐惧与谨慎行动。',
          },
        ],
        study_tips: ['先朗读英文文章，再看中文译文。', '把所选词汇各写一个新句子。'],
      },
    }
  }

  function toggleArticleFocusMode(forceState = null) {
    const next = typeof forceState === 'boolean' ? forceState : !state.articleFocusMode
    state.articleFocusMode = next
    elements.articleStudyToolbar?.classList.toggle('hidden', next)
    elements.articleHistoryPanel?.classList.toggle('hidden', next)
    elements.articleStudyLayout?.classList.toggle('article-focus-layout', next)
    if (elements.toggleArticleFocusModeBtn) {
      elements.toggleArticleFocusModeBtn.textContent = next ? '退出专注' : '专注模式'
      elements.toggleArticleFocusModeBtn.classList.toggle('active', next)
    }
  }

  return {
    changeArticleWordbook,
    loadArticleWords,
    loadArticleHistory,
    renderArticleWords,
    renderArticleHistory,
    renderArticleResult,
    renderArticleModalPreview,
    toggleArticleEntry,
    clearArticleSelection,
    recommendArticleWords,
    openArticleStudyModal,
    closeArticleStudyModal,
    generateArticlePreview,
    generateArticle,
    saveArticleStudy,
    openArticleRecord,
    toggleArticleFocusMode,
  }
}
