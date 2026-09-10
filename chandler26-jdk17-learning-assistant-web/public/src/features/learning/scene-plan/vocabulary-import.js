import { showModal, hideModal } from '/src/shared/modal.js'
import { SOURCE_LABELS, asArray, number } from '/src/features/learning/scene-plan/model.js'
import { parsePreviewMarkdown as parseMarkdown } from '/src/features/learning/scene-plan/markdown-parser.js'
import { createVocabularyImportView } from '/src/features/learning/scene-plan/vocabulary-import-view.js'

/**
 * 公共词本导入、审核、发布和关联分析工作流。
 * 该模块只管理词本导入域状态，不参与场景计划和学习状态编排。
 */
export function createVocabularyImportWorkflow({
  state,
  elements,
  catalogApi,
  renderSourceOptions,
  setButtonLoading,
  toast,
  logEvent,
  confirmAction,
  escapeHtml,
  sameId,
  loadWordbooks,
}) {
  let importSearchTimer = null
  const importView = createVocabularyImportView({
    state,
    elements,
    escapeHtml,
    sameId,
    onOpenReview: (jobId) => openReview(jobId),
    onRemove: (jobId) => remove(jobId),
    onSaveEntry: (entryId) => saveEntry(entryId),
  })

  async function ensureDataTags() {
    if (state.preview) {
      if (!state.vocabularyDataTags?.length) {
        state.vocabularyDataTags = [
          { code: 'primary_to_middle', label: '小升初' },
          { code: 'ncee', label: '高考' },
          { code: 'self_study', label: '自考' },
          { code: 'toefl', label: '托福' },
          { code: 'cet4', label: '四级' },
          { code: 'cet6', label: '六级' },
          { code: 'ielts', label: '雅思' },
        ]
      }
      renderTagOptions()
      return
    }
    if (!state.token) return
    if (!state.vocabularyDataTags?.length) {
      try {
        const tags = await catalogApi.listTags()
        if (Array.isArray(tags) && tags.length) {
          state.vocabularyDataTags = tags
          tags.forEach((tag) => {
            if (tag?.code && tag?.label) SOURCE_LABELS[tag.code] = tag.label
          })
        }
      } catch (error) {
        logEvent('warn', '获取词汇数据标签失败', error.message)
      }
    }
    renderTagOptions()
  }

  function renderTagOptions(selected) {
    if (!elements.vocabularyImportSourceType) return
    const tags = asArray(state.vocabularyDataTags)
    if (!tags.length) return
    const current = selected || elements.vocabularyImportSourceType.value || tags[0]?.code
    elements.vocabularyImportSourceType.innerHTML = tags.map((tag) =>
      `<option value="${escapeHtml(tag.code)}">${escapeHtml(tag.label || tag.name)}</option>`
    ).join('')
    elements.vocabularyImportSourceType.value = tags.some((t) => t.code === current) ? current : (tags[0]?.code || '')
  }

  function renderImportList() {
    importView.renderImportList()
  }

  function renderReview() {
    importView.renderReview()
  }

  function renderAnalysis(current = state.currentVocabularyImport) {
    importView.renderAnalysis(current)
  }

  async function open() {
    await ensureDataTags()
    renderSourceOptions()
    state.currentVocabularyImport = null
    state.currentVocabularyAnalysis = null
    state.vocabularyImportPage = 1
    if (elements.vocabularyWarningOnly) elements.vocabularyWarningOnly.checked = false
    if (elements.vocabularyImportKeyword) elements.vocabularyImportKeyword.value = ''
    elements.vocabularyImportFile.value = ''
    elements.vocabularyImportName.value = ''
    elements.vocabularyImportPurpose.value = ''
    elements.vocabularyImportSourceType.value = state.vocabularyDataTags?.[0]?.code || 'self_study'
    elements.vocabularyImportFile.disabled = false
    elements.vocabularyImportName.disabled = false
    elements.vocabularyImportSourceType.disabled = false
    elements.vocabularyImportPurpose.disabled = false
    const placeholder = document.getElementById('fileUploadPlaceholder')
    if (placeholder) placeholder.textContent = '选择 Markdown 文件'
    elements.startVocabularyImportBtn.classList.remove('hidden')
    elements.saveVocabularyImportMetadataBtn.classList.add('hidden')
    elements.startVocabularyImportBtn.disabled = false
    elements.saveVocabularyImportMetadataBtn.disabled = false
    elements.vocabularyReviewSection.classList.add('hidden')
    showModal(elements.vocabularyImportModal)
  }

  function close() {
    hideModal(elements.vocabularyImportModal)
  }

  async function start() {
    const file = elements.vocabularyImportFile.files?.[0]
    const catalogName = elements.vocabularyImportName.value.trim()
    const sourceType = elements.vocabularyImportSourceType.value
    if (!file) {
      toast('请选择 Markdown 文件')
      return
    }
    if (!catalogName) {
      toast('请输入词表名称')
      return
    }
    setButtonLoading(elements.startVocabularyImportBtn, true, '解析中...')
    try {
      const content = await file.text()
      let result
      if (state.preview) {
        const allItems = parseMarkdown(content)
        const warningCount = allItems.filter((item) => item.suspicious).length
        result = {
          jobId: Date.now(),
          catalogId: Date.now(),
          catalogVersionId: Date.now(),
          catalogName,
          sourceType,
          learningPurpose: elements.vocabularyImportPurpose.value.trim(),
          fileName: file.name,
          status: 'reviewing',
          totalCount: allItems.length,
          warningCount,
          reviewedWarningCount: 0,
          pendingWarningCount: warningCount,
          page: 1,
          pageSize: state.vocabularyImportPageSize,
          filteredTotal: allItems.length,
          items: allItems,
          _allItems: allItems,
          createTime: new Date().toISOString(),
        }
        state.vocabularyImports.unshift(result)
      } else {
        result = await catalogApi.importMarkdown({
          catalogName,
          sourceType,
          learningPurpose: elements.vocabularyImportPurpose.value.trim(),
          fileName: file.name,
          content,
        })
      }
      state.currentVocabularyImport = result
      state.vocabularyImportPage = 1
      if (elements.vocabularyWarningOnly) elements.vocabularyWarningOnly.checked = false
      if (elements.vocabularyImportKeyword) elements.vocabularyImportKeyword.value = ''
      elements.vocabularyReviewSection.classList.remove('hidden')
      await loadReview(result.jobId)
      await reloadHistory()
      logEvent('vocabulary', '导入 Markdown 词表', `${catalogName} · ${number(result.totalCount)} 词`)
      toast(`已解析 ${number(result.totalCount)} 个词，请确认疑似断词后发布`)
    } catch (error) {
      logEvent('error', '词表导入失败', error.message)
      toast(`词表导入失败：${error.message}`)
    } finally {
      setButtonLoading(elements.startVocabularyImportBtn, false)
    }
  }

  async function reloadHistory() {
    if (state.preview) {
      if (!asArray(state.vocabularyImports).length) {
        const catalog = state.publicVocabularyCatalogs?.[0] || {
          catalogId: 1,
          catalogVersionId: 1,
          catalogName: '自考英语（二）全部词汇',
          sourceType: 'self_study',
          totalCount: 5087,
        }
        state.vocabularyImports = [
          { jobId: 1, ...catalog, importerUserId: 1, importerName: '系统管理员', status: 'published', fileName: '自学考试(二)全部词汇5087_正序版.md', warningCount: 3, reviewedWarningCount: 3, pendingWarningCount: 0, items: [], filteredTotal: 0, page: 1, pageSize: state.vocabularyImportPageSize || 20, createTime: new Date(Date.now() - 86400000 * 5).toISOString() },
          { jobId: 2, catalogId: 2, catalogVersionId: 2, catalogName: '大学英语四级核心词汇', sourceType: 'cet4', totalCount: 3260, importerUserId: 2, importerName: '内容管理员', status: 'reviewing', fileName: 'cet4-core.md', warningCount: 8, reviewedWarningCount: 5, pendingWarningCount: 3, items: [], filteredTotal: 0, page: 1, pageSize: state.vocabularyImportPageSize || 20, createTime: new Date(Date.now() - 86400000 * 2).toISOString() },
        ]
        state.vocabularyImportHistoryTotal = state.vocabularyImports.length
        state.vocabularyImportHistoryPage = 1
      }
      renderImportList()
      renderSourceOptions()
      return
    }
    if (!state.token) {
      renderImportList()
      renderSourceOptions()
      return
    }
    try {
      const imports = await catalogApi.listImports(
        state.vocabularyImportHistoryPage || 1,
        state.vocabularyImportHistoryPageSize || 20,
      )
      applyHistoryPage(imports)
      renderImportList()
      renderSourceOptions()
    } catch (error) {
      logEvent('error', '公共词本管理列表加载失败', error.message)
      toast(`公共词本管理列表加载失败：${error.message}`)
    }
  }

  function applyHistoryPage(result) {
    if (Array.isArray(result)) {
      state.vocabularyImports = result
      state.vocabularyImportHistoryTotal = result.length
      state.vocabularyImportHistoryPage = 1
      return
    }
    state.vocabularyImports = asArray(result?.items)
    state.vocabularyImportHistoryTotal = number(result?.total) || state.vocabularyImports.length
    state.vocabularyImportHistoryPage = number(result?.page) || state.vocabularyImportHistoryPage || 1
    state.vocabularyImportHistoryPageSize = number(result?.pageSize)
      || state.vocabularyImportHistoryPageSize || 20
  }

  async function openReview(jobId) {
    await ensureDataTags()
    state.currentVocabularyAnalysis = null
    state.vocabularyImportPage = 1
    elements.vocabularyWarningOnly.checked = false
    elements.vocabularyImportKeyword.value = ''
    renderSourceOptions()
    showModal(elements.vocabularyImportModal)
    elements.vocabularyReviewSection.classList.remove('hidden')
    await loadReview(jobId)
  }

  async function loadReview(jobId = state.currentVocabularyImport?.jobId) {
    if (!jobId) return
    if (state.preview) {
      const source = asArray(state.vocabularyImports).find((item) => sameId(item.jobId, jobId))
        || state.currentVocabularyImport
      if (!source) return
      const allItems = asArray(source._allItems).length ? source._allItems : asArray(source.items)
      const keyword = elements.vocabularyImportKeyword.value.trim().toLowerCase()
      const warningOnly = Boolean(elements.vocabularyWarningOnly.checked)
      const filtered = allItems.filter((item) => {
        if (warningOnly && !item.suspicious) return false
        const haystack = `${item.originalTerm || ''} ${item.approvedTerm || ''} ${item.definition || ''}`.toLowerCase()
        return !keyword || haystack.includes(keyword)
      })
      const pageSize = number(state.vocabularyImportPageSize) || 100
      const pages = Math.max(1, Math.ceil(filtered.length / pageSize))
      state.vocabularyImportPage = Math.min(Math.max(1, number(state.vocabularyImportPage) || 1), pages)
      const start = (state.vocabularyImportPage - 1) * pageSize
      source.page = state.vocabularyImportPage
      source.pageSize = pageSize
      source.filteredTotal = filtered.length
      source.items = filtered.slice(start, start + pageSize)
      source.pendingWarningCount = allItems.filter((item) => item.suspicious && item.reviewStatus !== 'confirmed').length
      source.reviewedWarningCount = allItems.filter((item) => item.suspicious && item.reviewStatus === 'confirmed').length
      state.currentVocabularyImport = source
      if (source.catalogVersionId && !state.currentVocabularyAnalysis) {
        const total = number(source.totalCount)
        state.currentVocabularyAnalysis = {
          catalogId: source.catalogId,
          catalogVersionId: source.catalogVersionId,
          status: 'not_started',
          publishedCount: total,
          analyzedCount: 0,
          unanalyzedCount: total,
          canTrigger: total > 0,
        }
      }
      renderReview()
      renderImportList()
      return
    }
    try {
      const params = new URLSearchParams({
        warningOnly: String(Boolean(elements.vocabularyWarningOnly.checked)),
        page: String(state.vocabularyImportPage || 1),
        pageSize: String(state.vocabularyImportPageSize || 100),
      })
      const keyword = elements.vocabularyImportKeyword.value.trim()
      if (keyword) params.set('keyword', keyword)
      state.currentVocabularyImport = await catalogApi.getImport(jobId, params)
      renderReview()
      const canManageCatalogs = state.preview || state.user?.roleCode === 'ADMIN'
      if (canManageCatalogs && state.currentVocabularyImport?.catalogVersionId) {
        await loadAnalysis(state.currentVocabularyImport.catalogVersionId, { quiet: true })
      }
    } catch (error) {
      logEvent('error', '词表审核数据加载失败', error.message)
      toast(`词表审核加载失败：${error.message}`)
    }
  }

  async function loadAnalysis(catalogVersionId, options = {}) {
    if (!catalogVersionId) return null
    const canManageCatalogs = state.preview || state.user?.roleCode === 'ADMIN'
    if (!canManageCatalogs) return null
    if (state.preview) {
      renderAnalysis()
      return state.currentVocabularyAnalysis
    }
    try {
      const analysis = await catalogApi.getAnalysis(catalogVersionId)
      state.currentVocabularyAnalysis = analysis
      renderAnalysis()
      return analysis
    } catch (error) {
      if (!options.quiet) toast(`词本关联分析状态加载失败：${error.message}`)
      logEvent('error', '词本关联分析状态加载失败', error.message)
      return null
    }
  }

  async function triggerAnalysis() {
    const canManageCatalogs = state.preview || state.user?.roleCode === 'ADMIN'
    if (!canManageCatalogs) return
    const current = state.currentVocabularyImport
    if (!current || !current.catalogVersionId) return
    const pendingWarnings = number(current.pendingWarningCount)
    if (pendingWarnings > 0) {
      toast(`仍有 ${pendingWarnings} 个疑似断词未确认，请先完成断词确认后再开始分析`)
      return
    }
    const pending = number(state.currentVocabularyAnalysis?.unanalyzedCount) || number(current.totalCount)
    if (pending <= 0) return
    const confirmed = await confirmAction({
      title: '分析公共词本',
      message: `将对「${current.catalogName}」中尚未分析的 ${pending} 个词进行批量语义分析。分析在后台执行，可在任务中心查看进度。`,
      acceptText: '开始分析',
    })
    if (!confirmed) return
    setButtonLoading(elements.triggerVocabularyAnalysisBtn, true, '提交中...')
    try {
      state.currentVocabularyAnalysis = state.preview
        ? { ...state.currentVocabularyAnalysis, status: 'pending', canTrigger: false }
        : await catalogApi.triggerAnalysis(current.catalogVersionId, { executionMode: 'immediate', batchSize: 25 })
      renderAnalysis()
      logEvent('vocabulary', '触发公共词本关联分析', `${current.catalogName} · ${pending} 词`)
      toast('词本关联分析任务已创建，可在个人信息 - 任务中心查看进度')
    } catch (error) {
      logEvent('error', '公共词本关联分析触发失败', error.message)
      toast(`词本关联分析触发失败：${error.message}`)
    } finally {
      setButtonLoading(elements.triggerVocabularyAnalysisBtn, false)
      renderAnalysis()
    }
  }

  async function saveEntry(entryId) {
    const current = state.currentVocabularyImport
    const input = elements.vocabularyReviewRows.querySelector(`[data-import-entry-input="${CSS.escape(String(entryId))}"]`)
    const approvedTerm = input?.value.trim()
    if (!current || !approvedTerm) return
    try {
      if (state.preview) {
        const item = asArray(current._allItems).find((entry) => sameId(entry.id, entryId))
        if (item) {
          item.approvedTerm = approvedTerm
          item.effectiveTerm = approvedTerm
          item.reviewStatus = 'confirmed'
        }
      } else await catalogApi.updateEntry(current.jobId, entryId, approvedTerm)
      await loadReview()
      await reloadHistory()
      toast('修正已确认')
    } catch (error) {
      logEvent('error', '疑似断词修正失败', error.message)
      toast(`修正失败：${error.message}`)
    }
  }

  async function confirmAll() {
    const current = state.currentVocabularyImport
    if (!current || number(current.pendingWarningCount) === 0) return
    const confirmed = await confirmAction({
      title: '采用全部建议',
      message: `将为剩余 ${number(current.pendingWarningCount)} 个疑似断词采用系统建议，仍可在发布前逐条修改。`,
      acceptText: '采用建议',
    })
    if (!confirmed) return
    try {
      if (state.preview) {
        asArray(current._allItems).filter((item) => item.suspicious && item.reviewStatus !== 'confirmed').forEach((item) => {
          item.approvedTerm = item.suggestedTerm || item.originalTerm
          item.effectiveTerm = item.approvedTerm
          item.reviewStatus = 'confirmed'
        })
      } else await catalogApi.confirmWarnings(current.jobId)
      await loadReview()
      await reloadHistory()
      toast('已确认全部疑似断词')
    } catch (error) {
      logEvent('error', '批量确认疑似断词失败', error.message)
      toast(`批量确认失败：${error.message}`)
    }
  }

  async function publish() {
    const canManageCatalogs = state.preview || state.user?.roleCode === 'ADMIN'
    if (!canManageCatalogs) return
    const current = state.currentVocabularyImport
    if (!current || current.status === 'published') return
    if (number(current.pendingWarningCount) > 0) {
      toast('请先确认所有疑似断词')
      return
    }

    const analysis = sameId(state.currentVocabularyAnalysis?.catalogVersionId, current.catalogVersionId)
      ? state.currentVocabularyAnalysis : null
    const total = number(current.totalCount)
    const analyzed = number(analysis?.analyzedCount)
    const unanalyzed = Math.max(0, number(analysis?.unanalyzedCount) || (total - analyzed))
    const isAllAnalyzed = total > 0 && analyzed >= total && unanalyzed === 0

    let message = `确认将「${current.catalogName}」发布为公共词本？发布后可用于新建学习计划，导入阶段不会批量生成 AI 词卡。`
    if (!isAllAnalyzed) {
      message = `当前词本尚未完成 AI 语义关联分析（已完成 ${analyzed}/${total} 词）。未分析的词本在学习者场景规划中暂无语义分组支持。\n\n建议先点击下方「开始分析」完成后再发布。确定要直接发布吗？`
    }

    const confirmed = await confirmAction({
      title: isAllAnalyzed ? '发布公共词本' : '发布未完全分析的词本',
      message,
      acceptText: '确认发布',
    })
    if (!confirmed) return
    setButtonLoading(elements.publishVocabularyImportBtn, true, '发布中...')
    try {
      if (state.preview) {
        current.status = 'published'
        const catalog = { catalogId: current.catalogId, catalogVersionId: current.catalogVersionId, catalogName: current.catalogName, sourceType: current.sourceType, learningPurpose: current.learningPurpose, status: 'published', totalCount: current.totalCount, publishedTime: new Date().toISOString() }
        state.publicVocabularyCatalogs = [catalog, ...state.publicVocabularyCatalogs.filter((item) => !sameId(item.catalogVersionId, catalog.catalogVersionId))]
        state.currentVocabularyImport = current
      } else {
        state.currentVocabularyImport = await catalogApi.publish(current.jobId)
        const canManageCatalogs = state.preview || state.user?.roleCode === 'ADMIN'
        if (canManageCatalogs && state.currentVocabularyImport?.catalogVersionId) {
          await loadAnalysis(state.currentVocabularyImport.catalogVersionId, { quiet: true })
        }
      }
      await Promise.allSettled([reloadHistory(), loadWordbooks?.()])
      renderReview()
      renderSourceOptions()
      logEvent('vocabulary', '发布公共词本', current.catalogName)
      toast('公共词本已发布，可在学习计划中选择')
    } catch (error) {
      logEvent('error', '词表发布失败', error.message)
      toast(`词表发布失败：${error.message}`)
    } finally {
      setButtonLoading(elements.publishVocabularyImportBtn, false)
    }
  }

  async function remove(jobId) {
    const job = asArray(state.vocabularyImports).find((item) => sameId(item.jobId, jobId))
    if (!job) return
    const confirmed = await confirmAction({
      title: '删除导入记录',
      message: `确认删除公共词表导入记录「${job.catalogName}」？删除后对应的公共词本及词条关系将被清除。`,
    })
    if (!confirmed) return
    try {
      await catalogApi.deleteImport(jobId)
      toast('导入记录已删除')
      if (sameId(state.currentVocabularyImport?.jobId, jobId)) {
        state.currentVocabularyImport = null
        elements.vocabularyReviewSection.classList.add('hidden')
        close()
      }
      await reloadHistory()
    } catch (error) {
      logEvent('error', '删除导入记录失败', error.message)
      toast(`删除导入记录失败：${error.message}`)
    }
  }

  async function saveMetadata() {
    const current = state.currentVocabularyImport
    if (!current || !current.jobId) return
    const catalogName = elements.vocabularyImportName.value.trim()
    const sourceType = elements.vocabularyImportSourceType.value
    const learningPurpose = elements.vocabularyImportPurpose.value.trim()
    if (!catalogName) {
      toast('请输入词表名称')
      return
    }
    setButtonLoading(elements.saveVocabularyImportMetadataBtn, true, '保存中...')
    try {
      if (state.preview) {
        current.catalogName = catalogName
        current.sourceType = sourceType
        current.learningPurpose = learningPurpose
      } else state.currentVocabularyImport = await catalogApi.updateImport(current.jobId, { catalogName, sourceType, learningPurpose })
      toast('词表信息已更新')
      await reloadHistory()
      renderReview()
    } catch (error) {
      logEvent('error', '保存词表信息失败', error.message)
      toast(`词表信息更新失败：${error.message}`)
    } finally {
      setButtonLoading(elements.saveVocabularyImportMetadataBtn, false)
    }
  }

  function changeSearch() {
    window.clearTimeout(importSearchTimer)
    importSearchTimer = window.setTimeout(() => {
      state.vocabularyImportPage = 1
      loadReview()
    }, 280)
  }

  function changeWarningOnly() {
    state.vocabularyImportPage = 1
    loadReview()
  }

  function previousPage() {
    state.vocabularyImportPage = Math.max(1, number(state.vocabularyImportPage) - 1)
    loadReview()
  }

  function nextPage() {
    state.vocabularyImportPage = number(state.vocabularyImportPage) + 1
    loadReview()
  }

  function previousHistoryPage() {
    if (state.vocabularyImportHistoryPage <= 1) return
    state.vocabularyImportHistoryPage = Math.max(1, state.vocabularyImportHistoryPage - 1)
    reloadHistory()
  }

  function nextHistoryPage() {
    const total = number(state.vocabularyImportHistoryTotal)
    const pageSize = number(state.vocabularyImportHistoryPageSize) || 20
    const pages = Math.max(1, Math.ceil(total / pageSize))
    if (state.vocabularyImportHistoryPage >= pages) return
    state.vocabularyImportHistoryPage += 1
    reloadHistory()
  }

  return {
    open,
    close,
    start,
    remove,
    saveMetadata,
    loadReview,
    openReview,
    confirmAll,
    publish,
    triggerAnalysis,
    reloadHistory,
    applyHistoryPage,
    renderImportList,
    renderReview,
    renderAnalysis,
    loadAnalysis,
    changeSearch,
    changeWarningOnly,
    previousPage,
    nextPage,
    previousHistoryPage,
    nextHistoryPage,
  }
}
