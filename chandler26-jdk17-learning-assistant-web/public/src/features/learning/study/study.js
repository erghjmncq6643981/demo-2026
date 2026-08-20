import { previewRecord } from '/src/shared/vocabulary.js'
import { createStudyCardFeature } from '/src/features/learning/study/card.js'
import { createStudyNotesFeature } from '/src/features/learning/study/notes.js'
import { createStudyWordbookFeature } from '/src/features/learning/study/wordbook.js'

export function createStudyFeature(ctx) {
  const {
    state,
    elements,
    request,
    setLoading,
    toast,
    logEvent,
    confirmAction,
    setView,
    createPreviewActivity,
    renderReviewFocus,
    renderReviewQueue,
    renderWordbookEntries,
    renderWordbookFocus,
    renderWordbooks,
    loadWordbooks,
    loadWordbookEntries,
    loadDueReviews,
    loadActivity,
    currentWordbookName,
    speak,
    speakSentence,
  } = ctx

  const feature = {}

  async function study(term, options = {}) {
    const value = String(term || '').trim()
    if (!value) {
      toast('先输入一个英语单词')
      return
    }
    const forceRefresh = Boolean(options.forceRefresh)
    const modelConfigId = options.modelConfigId !== undefined ? options.modelConfigId : elements.studyModelSelect?.value || null
    setLoading(true)
    try {
      if (state.preview) {
        const record = previewRecord(value)
        record.cacheHit = !forceRefresh
        feature.renderRecord(record)
        logEvent(forceRefresh ? 'ai' : 'cache', forceRefresh ? '预览重新生成词汇卡片' : '预览词汇卡片', record.normalizedTerm)
        toast(forceRefresh ? '设计预览：已模拟重新生成' : '设计预览：已展示模拟学习卡片')
        return
      }
      const record = await request('/api/v1/english/vocabularies/study', {
        method: 'POST',
        body: JSON.stringify({
          term: value,
          agentCode: elements.agentSelect.value,
          templateCode: elements.templateSelect.value,
          modelConfigId,
          forceRefresh,
        }),
      })
      feature.renderRecord(record)
      logEvent(record.cacheHit ? 'cache' : 'ai', record.cacheHit ? '读取词汇缓存' : 'AI 生成词汇卡片', record.normalizedTerm || value)
      if (record?.normalizedTerm && record.normalizedTerm !== value.toLowerCase()) {
        logEvent('cache', '已匹配标准单词', `${value} -> ${record.normalizedTerm}`)
        toast(`已匹配到：${record.normalizedTerm}`)
        return
      }
      toast(record.cacheHit ? '已从数据库缓存读取' : 'AI 已生成并保存到数据库')
    } catch (error) {
      logEvent('error', '学习请求失败', error.message)
      const match = await showBestMatch(value)
      if (!match) {
        toast(`学习请求失败：${error.message}`)
      }
    } finally {
      setLoading(false)
    }
  }

  function regenerateStudyCard() {
    const term = state.currentRecord?.normalizedTerm || elements.termInput.value
    study(term, { forceRefresh: true })
  }

  async function showBestMatch(term) {
    if (state.preview) return false
    try {
      const match = await request(`/api/v1/english/vocabularies/${encodeURIComponent(term)}/best-match`)
      if (!match?.record) return false
      feature.renderRecord(match.record)
      logEvent('cache', '已展示最匹配单词', `${term} -> ${match.normalizedTerm} · ${match.matchScore}`)
      toast(`未直接命中，已展示最匹配：${match.normalizedTerm}`)
      return true
    } catch (error) {
      logEvent('error', '最匹配单词查询失败', error.message)
      return false
    }
  }

  function findEntryForRecord(record) {
    const term = record?.normalizedTerm || record?.term || record?.parsed?.term
    if (!term) return null
    return state.wordbookEntries.find((entry) => entry.normalizedTerm === term || entry.term === term) || null
  }

  const cardFeature = createStudyCardFeature({
    state,
    elements,
    confirmAction,
    setView,
    study,
    renderReviewFocus,
    findEntryForRecord,
    renderNotes: (...args) => feature.renderNotes(...args),
    speak,
    speakSentence,
  })
  const wordbookFeature = createStudyWordbookFeature({
    state,
    elements,
    request,
    setLoading,
    toast,
    logEvent,
    createPreviewActivity,
    renderWordbookEntries,
    renderWordbooks,
    loadWordbooks,
    loadWordbookEntries,
    loadDueReviews,
    loadActivity,
    currentWordbookName,
    renderNotes: (...args) => feature.renderNotes(...args),
  })
  const notesFeature = createStudyNotesFeature({
    state,
    elements,
    request,
    toast,
    logEvent,
    findEntryForRecord,
    renderWordbookEntries,
    renderWordbookFocus,
    renderReviewQueue,
  })

  Object.assign(feature, {
    study,
    regenerateStudyCard,
    showBestMatch,
    findEntryForRecord,
    ...cardFeature,
    ...wordbookFeature,
    ...notesFeature,
  })
  return feature
}
