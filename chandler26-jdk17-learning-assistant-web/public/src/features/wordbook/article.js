import { sameId } from '/src/shared/ids.js'
import { hideModal, showModal } from '/src/shared/modal.js'
import { escapeHtml, formatDateTime } from '/src/shared/text.js'
import { normalizeArray, normalizeDefinitions, readText, statusLabel, stringifyValue } from '/src/shared/vocabulary.js'
import { syncCurrentWordbookId } from '/src/shared/wordbook.js'

const ARTICLE_WORD_LIMIT = 20
const ERROR_CODE_AI_MODEL_BALANCE_INSUFFICIENT = 'AI_MODEL_BALANCE_INSUFFICIENT'
const ERROR_CODE_AI_MODEL_CALL_FAILED = 'AI_MODEL_CALL_FAILED'

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

  function setWordbookTab(tabId, options = {}) {
    const fallback = document.getElementById(tabId) ? tabId : 'wordbookWordsPanel'
    state.activeWordbookTab = fallback
    localStorage.setItem('learning.wordbookTab', fallback)
    document.querySelectorAll('.wordbook-tab').forEach((button) => {
      const active = button.dataset.wordbookTab === fallback
      button.classList.toggle('active', active)
      if (active) button.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' })
    })
    document.querySelectorAll('.wordbook-tab-panel').forEach((panel) => panel.classList.toggle('active', panel.id === fallback))
    if (fallback === 'articleStudyPanel' && !options.skipLoad) {
      loadArticleWords()
      loadArticleHistory()
    }
  }

  async function changeArticleWordbook(wordbookId) {
    syncCurrentWordbookId(state, elements, wordbookId)
    state.selectedArticleEntryIds = []
    state.currentArticleRecord = null
    state.articleDraftRecord = null
    renderArticleResult(null)
    renderArticleModalPreview(null)
    await Promise.allSettled([loadArticleWords(), loadArticleHistory()])
    logEvent('wordbook', '切换文章学习单词本', currentWordbookName())
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
    const query = status ? `?status=${encodeURIComponent(status)}` : ''
    return request(`/api/v1/learning/wordbooks/${encodeURIComponent(state.currentWordbookId)}/entries${query}`)
      .then((entries) => {
        state.articleEntries = Array.isArray(entries) ? entries : []
        pruneSelectedArticleEntries()
        renderArticleWords()
      })
      .catch((error) => {
        logEvent('error', '文章学习词汇加载失败', error.message)
        toast(`文章学习词汇加载失败：${error.message}`)
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
    return request(`/api/v1/learning/articles?wordbookId=${encodeURIComponent(state.currentWordbookId)}&limit=10`)
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
        logEvent('error', '文章学习历史加载失败', error.message)
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
        title: '重新生成文章',
        message: '确认重新调用 AI 生成一篇新的文章？已有文章记录仍会保留。',
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
      const record = await request('/api/v1/learning/articles/study', {
        method: 'POST',
        body: JSON.stringify(payload),
      })
      state.articleDraftRecord = record
      renderArticleModalPreview(record)
      logEvent(record.cacheHit ? 'cache' : 'ai', record.cacheHit ? '读取文章学习缓存' : 'AI 生成文章学习材料', selectedWordsText(record))
      toast(record.cacheHit ? '已读取文章学习预览缓存' : '文章预览已生成')
    } catch (error) {
      state.articleDraftRecord = null
      state.articlePreviewError = normalizeArticleError(error)
      logEvent('error', '文章生成失败', formatArticleErrorForLog(state.articlePreviewError))
      toast(`文章生成失败：${state.articlePreviewError.message}`)
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
    if (state.preview) {
      state.articleRecords = [
        state.articleDraftRecord,
        ...state.articleRecords.filter((item) => !sameId(item.id, state.articleDraftRecord.id)),
      ]
      renderArticleHistory()
    } else {
      await loadArticleHistory()
    }
    renderArticleResult(state.articleDraftRecord)
    const title = readText(state.articleDraftRecord.parsed, ['title']) || selectedWordsText(state.articleDraftRecord) || '文章学习'
    logEvent('article', '保存文章学习材料', title)
    closeArticleStudyModal()
    toast('文章已保存到文章主体')
  }

  function generateArticle(options = {}) {
    return generateArticlePreview(options)
  }

  async function openArticleRecord(recordId) {
    const existing = state.articleRecords.find((record) => sameId(record.id, recordId))
    if (state.preview || existing?.parsed) {
      renderArticleResult(existing)
      return
    }
    try {
      const record = await request(`/api/v1/learning/articles/${encodeURIComponent(recordId)}`)
      renderArticleResult(record)
      renderArticleHistory()
    } catch (error) {
      logEvent('error', '文章记录打开失败', error.message)
      toast(`文章记录打开失败：${error.message}`)
    }
  }

  function renderArticleHistory() {
    if (!elements.articleHistoryList) return
    const records = filteredArticleRecords()
    if (!records.length) {
      elements.articleHistoryList.className = 'article-history-list empty'
      elements.articleHistoryList.textContent = state.articleRecords.length ? '没有匹配的文章' : '暂无文章记录'
      return
    }
    elements.articleHistoryList.className = 'article-history-list'
    elements.articleHistoryList.innerHTML = records
      .map((record) => {
        const title = readText(record.parsed, ['title']) || selectedWordsText(record) || '文章学习'
        return `
          <button class="article-history-item ${state.currentArticleRecord && sameId(state.currentArticleRecord.id, record.id) ? 'active' : ''}" type="button" data-article-record="${escapeHtml(record.id)}">
            <strong>${escapeHtml(title)}</strong>
            <span>${escapeHtml(wordCountLabel(record.wordCountRange))} · ${escapeHtml(difficultyLabel(record.difficulty))}</span>
            <small>${escapeHtml(formatDateTime(record.updateTime || record.createTime))}</small>
          </button>
        `
      })
      .join('')
    elements.articleHistoryList.querySelectorAll('[data-article-record]').forEach((button) => {
      button.addEventListener('click', () => openArticleRecord(button.getAttribute('data-article-record')))
    })
  }

  function filteredArticleRecords() {
    const query = String(state.articleHistoryFilter || elements.articleHistorySearchInput?.value || '').trim().toLowerCase()
    if (!query) return state.articleRecords
    return state.articleRecords.filter((record) => {
      const title = readText(record.parsed, ['title']) || ''
      const article = readText(record.parsed, ['article', 'text', 'content']) || ''
      const values = [
        title,
        article,
        selectedWordsText(record),
        record.remark,
        wordCountLabel(record.wordCountRange),
        difficultyLabel(record.difficulty),
      ]
      return values.some((value) => String(value || '').toLowerCase().includes(query))
    })
  }

  function renderArticleResult(record) {
    state.currentArticleRecord = record || null
    if (!elements.articleResult) return
    if (!record) {
      elements.articleResultBadge.textContent = '等待生成'
      elements.articleResult.className = 'article-result empty'
      elements.articleResult.textContent = '选择或生成一篇文章后开始学习'
      return
    }
    renderArticleContent(elements.articleResult, elements.articleResultBadge, record)
    elements.rawJson.textContent = record.parsed && Object.keys(record.parsed).length ? JSON.stringify(record.parsed, null, 2) : record.rawContent || '{}'
    if (elements.sessionIdBadge) {
      elements.sessionIdBadge.textContent = `${record.provider || 'AI'} · ${record.modelName || 'article'} · #${record.sessionId || '-'}`
    }
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
        elements.articleModalPreview.textContent = '选择词汇并生成预览后，可保存到文章主体中继续学习'
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
      elements.articlePreviewGenerateBtn.textContent = state.articlePreviewLoading ? '生成中...' : '生成预览'
    }
    if (elements.saveArticleStudyBtn) {
      elements.saveArticleStudyBtn.disabled = state.articlePreviewLoading || !state.articleDraftRecord
    }
  }

  function renderArticleContent(container, badge, record, options = {}) {
    const parsed = record.parsed || {}
    const title = readText(parsed, ['title']) || '文章学习'
    const article = readText(parsed, ['article', 'text', 'content'])
    const translation = readText(parsed, ['translation', 'translation_cn', 'translationCn', 'cn', 'zh'])
    const vocabularyFocus = normalizeArray(parsed.vocabulary_focus || parsed.vocabularyFocus || parsed.words || [])
    const grammarPoints = normalizeArray(parsed.grammar_points || parsed.grammarPoints || [])
    const keyPoints = normalizeArray(parsed.key_points || parsed.keyPoints || [])
    const practice = normalizeArray(parsed.practice || parsed.questions || [])
    const tips = normalizeArray(parsed.study_tips || parsed.studyTips || parsed.tips || [])

    badge.textContent = `${record.cacheHit ? '缓存' : 'AI'} · ${wordCountLabel(record.wordCountRange)} · ${difficultyLabel(record.difficulty)}`
    container.className = `article-result${options.compact ? ' article-result-preview' : ''}`
    container.innerHTML = `
      <article class="article-learning-card${options.compact ? ' article-learning-card-preview' : ''}">
        <header class="article-learning-head">
          <div>
            <p class="eyebrow">${escapeHtml(currentWordbookName(record.wordbookId))}</p>
            <h4>${escapeHtml(title)}</h4>
            <span>${escapeHtml(selectedWordsText(record))}</span>
          </div>
          <button class="icon-button" type="button" data-article-speak title="播放文章">▶</button>
        </header>
        <section class="article-section">
          <h5>双语正文</h5>
          ${renderBilingualArticle(article, translation)}
        </section>
        ${renderVocabularyFocus(vocabularyFocus)}
        ${renderGrammarPoints(grammarPoints)}
        ${renderSimpleList('重点知识', keyPoints)}
        ${renderPractice(practice)}
        ${renderSimpleList('学习建议', tips)}
      </article>
    `
    container.querySelector('[data-article-speak]')?.addEventListener('click', () => speakSentence(article))
  }

  function normalizeArticleError(error) {
    const message = String(error?.message || '文章生成失败').trim()
    const errorCode = String(error?.errorCode || '').trim()
    const status = error?.status || ''
    return {
      message,
      errorCode,
      status,
      suggestion: articleErrorSuggestion(errorCode, status),
    }
  }

  function readArticleError(error) {
    if (!error) return null
    if (typeof error === 'string') {
      const message = error.trim()
      return message ? { message, errorCode: '', status: '', suggestion: '' } : null
    }
    const message = String(error.message || '').trim()
    return message ? error : null
  }

  function renderArticleError(error) {
    const meta = [
      error.errorCode ? `错误码：${error.errorCode}` : '',
      error.status ? `HTTP：${error.status}` : '',
    ].filter(Boolean)
    return `
      <div class="article-error-content">
        <strong>文章生成失败</strong>
        <p>${escapeHtml(error.message)}</p>
        ${error.suggestion ? `<small>${escapeHtml(error.suggestion)}</small>` : ''}
        ${meta.length ? `<span>${escapeHtml(meta.join(' · '))}</span>` : ''}
      </div>
    `
  }

  function articleErrorSuggestion(errorCode, status) {
    if (errorCode === ERROR_CODE_AI_MODEL_BALANCE_INSUFFICIENT) {
      return '可以先切换到其它启用模型，或补充当前供应商账户余额后重试。'
    }
    if (errorCode === ERROR_CODE_AI_MODEL_CALL_FAILED) {
      return '请检查模型配置、Base URL、API Key 或稍后重试。'
    }
    if (Number(status) >= 500) {
      return '服务端返回异常，请查看个人信息里的系统日志或后端日志定位原因。'
    }
    return ''
  }

  function formatArticleErrorForLog(error) {
    return [
      error.message,
      error.errorCode ? `错误码：${error.errorCode}` : '',
      error.status ? `HTTP：${error.status}` : '',
    ].filter(Boolean).join('；')
  }

  function renderVocabularyFocus(items) {
    if (!items.length) return ''
    return `
      <section class="article-section">
        <h5>词汇用法</h5>
        <div class="article-mini-grid">
          ${items
            .map((item) => {
              const word = readText(item, ['word', 'term']) || stringifyValue(item)
              const meaning = readText(item, ['meaning', 'translation', 'cn'])
              const usage = readText(item, ['usage', 'explanation', 'tip'])
              const sentence = readText(item, ['sentence', 'example'])
              const translation = readText(item, ['translation', 'sentence_translation', 'sentenceTranslation', 'zh'])
              return `
                <div class="article-mini-card">
                  <strong>${escapeHtml(word)}</strong>
                  <p>${escapeHtml([meaning, usage].filter(Boolean).join(' · ') || '暂无说明')}</p>
                  ${sentence ? `<small>${escapeHtml(sentence)}</small>` : ''}
                  ${translation ? `<small>${escapeHtml(translation)}</small>` : ''}
                </div>
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

  function renderPractice(items) {
    if (!items.length) return ''
    return `
      <section class="article-section">
        <h5>练习题</h5>
        <div class="article-stack">
          ${items
            .map(
              (item, index) => `
                <div class="article-info-block">
                  <strong>${index + 1}. ${escapeHtml(readText(item, ['question', 'stem']) || '练习题')}</strong>
                  <p>${escapeHtml(readText(item, ['answer']) || '暂无答案')}</p>
                  <small>${escapeHtml(readText(item, ['explanation', 'analysis']) || '')}</small>
                </div>
              `,
            )
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

  function renderBilingualArticle(article, translation) {
    const englishLines = splitArticleLines(article, 'en')
    const chineseLines = splitArticleLines(translation, 'zh')
    const lineCount = Math.max(englishLines.length, chineseLines.length)
    if (!lineCount) {
      return '<div class="empty">暂无文章内容</div>'
    }
    return `
      <div class="article-bilingual-lines">
        ${Array.from({ length: lineCount })
          .map((_, index) => {
            const english = englishLines[index] || ''
            const chinese = chineseLines[index] || ''
            return `
              <div class="article-bilingual-pair">
                ${english ? `<p class="article-line-en">${escapeHtml(english)}</p>` : ''}
                ${chinese ? `<p class="article-line-zh">${escapeHtml(chinese)}</p>` : ''}
              </div>
            `
          })
          .join('')}
      </div>
    `
  }

  function splitArticleLines(text, language) {
    const normalized = String(text || '')
      .replace(/\s+/g, ' ')
      .trim()
    if (!normalized) return []
    const pattern = language === 'zh'
      ? /[^。！？!?]+[。！？!?]+|[^。！？!?]+$/g
      : /[^.!?]+[.!?]+(?:["')\]]+)?|[^.!?]+$/g
    return (normalized.match(pattern) || [normalized])
      .map((line) => line.trim())
      .filter(Boolean)
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
        practice: [{ question: 'What did Mia decide to maintain?', answer: 'Her confidence.', explanation: '文章第二句直接给出答案。' }],
        study_tips: ['先朗读英文文章，再看中文译文。', '把所选词汇各写一个新句子。'],
      },
    }
  }

  return {
    setWordbookTab,
    changeArticleWordbook,
    loadArticleWords,
    loadArticleHistory,
    renderArticleWords,
    renderArticleHistory,
    renderArticleResult,
    renderArticleModalPreview,
    toggleArticleEntry,
    clearArticleSelection,
    openArticleStudyModal,
    closeArticleStudyModal,
    generateArticlePreview,
    generateArticle,
    saveArticleStudy,
    openArticleRecord,
  }
}
