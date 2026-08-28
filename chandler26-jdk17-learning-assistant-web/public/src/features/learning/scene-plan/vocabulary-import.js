import { showModal, hideModal } from '/src/shared/modal.js'
import { formatDateTime } from '/src/shared/text.js'
import {
  ANALYSIS_STATUS_LABELS,
  IMPORT_STATUS_LABELS,
  SOURCE_LABELS,
  asArray,
  number,
} from '/src/features/learning/scene-plan/model.js'
import { parsePreviewMarkdown as parseMarkdown } from '/src/features/learning/scene-plan/markdown-parser.js'

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

  function open() {
    renderSourceOptions()
    state.currentVocabularyImport = null
    state.currentVocabularyAnalysis = null
    state.vocabularyImportPage = 1
    elements.vocabularyImportFile.value = ''
    elements.vocabularyImportName.value = ''
    elements.vocabularyImportPurpose.value = ''
    elements.vocabularyImportSourceType.value = 'self_study'
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
    if (state.preview || !state.token) {
      renderImportList()
      renderSourceOptions()
      return
    }
    const imports = await catalogApi.listImports(
      state.vocabularyImportHistoryPage,
      state.vocabularyImportHistoryPageSize,
    )
    applyHistoryPage(imports)
    renderImportList()
    renderSourceOptions()
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

  function renderImportList() {
    if (!elements.sceneImportList) return
    const canManageCatalogs = state.preview || state.user?.roleCode === 'ADMIN'
    const imports = asArray(state.vocabularyImports)
    const page = number(state.vocabularyImportHistoryPage) || 1
    const pageSize = number(state.vocabularyImportHistoryPageSize) || 20
    const total = number(state.vocabularyImportHistoryTotal) || imports.length
    const pages = Math.max(1, Math.ceil(total / pageSize))
    if (elements.vocabularyImportHistoryPageInfo) {
      elements.vocabularyImportHistoryPageInfo.textContent = `第 ${page} / ${pages} 页 · ${total} 条`
    }
    if (elements.vocabularyImportHistoryPrevBtn) elements.vocabularyImportHistoryPrevBtn.disabled = page <= 1
    if (elements.vocabularyImportHistoryNextBtn) elements.vocabularyImportHistoryNextBtn.disabled = page >= pages
    if (!imports.length) {
      elements.sceneImportList.className = 'scene-import-list empty'
      elements.sceneImportList.textContent = '暂无导入记录'
      return
    }
    elements.sceneImportList.className = 'scene-import-list'
    elements.sceneImportList.innerHTML = imports.map((item) => `
      <div class="scene-import-card ${sameId(item.jobId, state.currentVocabularyImport?.jobId) ? 'active' : ''}">
        <button class="scene-import-main" type="button" data-import-job-id="${escapeHtml(item.jobId)}">
          <span class="scene-item-topline">
            <strong>${escapeHtml(item.catalogName)}</strong>
            <small class="import-status ${item.status}">${escapeHtml(IMPORT_STATUS_LABELS[item.status] || item.status)}</small>
          </span>
          <span>${escapeHtml(SOURCE_LABELS[item.sourceType] || item.sourceType || '公共词本')} · ${number(item.totalCount)} 词 · ${number(item.pendingWarningCount)} 个待确认</span>
          <small>导入人：${escapeHtml(item.importerName || (item.importerUserId ? `用户 #${item.importerUserId}` : '系统管理员'))} · ${escapeHtml(formatDateTime(item.createTime) || '时间未知')}</small>
        </button>
        ${canManageCatalogs ? `
        <div class="row-actions">
          <button class="icon-action-button" type="button" data-import-job-edit="${escapeHtml(item.jobId)}" title="编辑词表" aria-label="编辑词表">✎</button>
          <button class="danger-icon-button" type="button" data-import-job-delete="${escapeHtml(item.jobId)}" title="删除导入记录">×</button>
        </div>` : ''}
      </div>
    `).join('')
    elements.sceneImportList.querySelectorAll('[data-import-job-id]').forEach((button) => {
      button.addEventListener('click', () => openReview(button.dataset.importJobId))
    })
    elements.sceneImportList.querySelectorAll('[data-import-job-edit]').forEach((button) => {
      button.addEventListener('click', (event) => {
        event.stopPropagation()
        openReview(button.dataset.importJobEdit)
      })
    })
    elements.sceneImportList.querySelectorAll('[data-import-job-delete]').forEach((button) => {
      button.addEventListener('click', async (event) => {
        event.stopPropagation()
        await remove(button.dataset.importJobDelete)
      })
    })
  }

  async function openReview(jobId) {
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
      if (source.status === 'published' && source.catalogVersionId) {
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
      if (state.currentVocabularyImport.status === 'published') {
        await loadAnalysis(state.currentVocabularyImport.catalogVersionId, { quiet: true })
      }
    } catch (error) {
      logEvent('error', '词表审核数据加载失败', error.message)
      toast(`词表审核加载失败：${error.message}`)
    }
  }

  function renderReview() {
    const current = state.currentVocabularyImport
    if (!current) return
    const canManageCatalogs = state.preview || state.user?.roleCode === 'ADMIN'
    const isPublicCatalogView = current.status === 'published' || !canManageCatalogs
    if (elements.vocabularyImportEyebrow) {
      elements.vocabularyImportEyebrow.textContent = isPublicCatalogView ? 'Public Vocabulary' : 'Vocabulary Import'
    }
    if (elements.vocabularyImportTitle) {
      elements.vocabularyImportTitle.textContent = isPublicCatalogView
        ? (current.catalogName || '公共词本详情')
        : (current.jobId ? '审核 Markdown 词表' : '导入 Markdown 词表')
    }
    elements.vocabularyImportForm?.classList.toggle('hidden', isPublicCatalogView)
    elements.vocabularyCatalogMetaBanner?.classList.toggle('hidden', !isPublicCatalogView)
    if (isPublicCatalogView) {
      if (elements.catalogMetaSourceType) elements.catalogMetaSourceType.textContent = SOURCE_LABELS[current.sourceType] || current.sourceType || '公共词本'
      if (elements.catalogMetaPurpose) elements.catalogMetaPurpose.textContent = current.learningPurpose || '官方精选公共词表'
      if (elements.catalogMetaTotal) elements.catalogMetaTotal.textContent = `${number(current.totalCount)} 词`
    }
    if (!isPublicCatalogView) {
      elements.vocabularyImportName.value = current.catalogName || ''
      elements.vocabularyImportSourceType.value = current.sourceType || 'self_study'
      elements.vocabularyImportPurpose.value = current.learningPurpose || ''
      elements.vocabularyImportFile.disabled = false
      elements.vocabularyImportName.disabled = false
      elements.vocabularyImportSourceType.disabled = false
      elements.vocabularyImportPurpose.disabled = false
      const placeholder = document.getElementById('fileUploadPlaceholder')
      if (placeholder) placeholder.textContent = current.fileName || '已导入文件'
      elements.startVocabularyImportBtn.classList.toggle('hidden', current.jobId != null)
      elements.saveVocabularyImportMetadataBtn.classList.toggle('hidden', current.jobId == null)
    }
    elements.vocabularyImportSummary.textContent = `${number(current.totalCount)} 个词 · ${escapeHtml(current.catalogName || '')}`
    elements.vocabularyWarningSummary?.classList.toggle('hidden', isPublicCatalogView)
    if (elements.vocabularyWarningSummary) {
      elements.vocabularyWarningSummary.textContent = `${number(current.pendingWarningCount)} 个待确认`
      elements.vocabularyWarningSummary.classList.toggle('ok', number(current.pendingWarningCount) === 0)
    }
    elements.vocabularyWarningOnlyLabel?.classList.toggle('hidden', isPublicCatalogView)
    if (elements.publishVocabularyImportBtn) {
      elements.publishVocabularyImportBtn.classList.toggle('hidden', isPublicCatalogView)
      elements.publishVocabularyImportBtn.disabled = isPublicCatalogView || number(current.pendingWarningCount) > 0
    }
    if (elements.vocabularyBatchConfirmBtn) {
      elements.vocabularyBatchConfirmBtn.classList.toggle('hidden', isPublicCatalogView)
      elements.vocabularyBatchConfirmBtn.disabled = isPublicCatalogView || number(current.pendingWarningCount) === 0
    }
    const items = asArray(current.items)
    if (isPublicCatalogView) {
      elements.vocabularyReviewThead.innerHTML = '<tr><th>序号</th><th>单词</th><th>音标</th><th>释义</th></tr>'
      elements.vocabularyReviewRows.innerHTML = items.length ? items.map((item) => `
        <tr><td>${number(item.sourceOrder)}</td><td><strong>${escapeHtml(item.approvedTerm || item.effectiveTerm || item.originalTerm || '')}</strong></td><td>${escapeHtml(item.phonetic || '')}</td><td>${escapeHtml(item.definition || '')}</td></tr>
      `).join('') : '<tr><td colspan="4" class="empty">没有符合条件的词条</td></tr>'
    } else {
      elements.vocabularyReviewThead.innerHTML = '<tr><th>序号</th><th>原词</th><th>建议 / 人工确认</th><th>音标</th><th>释义</th><th>状态</th></tr>'
      elements.vocabularyReviewRows.innerHTML = items.length ? items.map((item) => `
        <tr class="${item.suspicious ? 'warning' : ''}"><td>${number(item.sourceOrder)}</td><td><strong>${escapeHtml(item.originalTerm)}</strong></td><td><div class="vocabulary-correction-field"><input value="${escapeHtml(item.approvedTerm || item.suggestedTerm || item.originalTerm || '')}" data-import-entry-input="${escapeHtml(item.id)}" />${item.suspicious ? `<button class="secondary-button compact" type="button" data-save-import-entry="${escapeHtml(item.id)}">确认</button>` : ''}</div></td><td>${escapeHtml(item.phonetic || '')}</td><td>${escapeHtml(item.definition || '')}</td><td><span class="mini-pill ${item.reviewStatus === 'confirmed' || !item.suspicious ? 'ok' : ''}">${item.suspicious ? (item.reviewStatus === 'confirmed' ? '已确认' : '疑似断词') : '正常'}</span></td></tr>
      `).join('') : '<tr><td colspan="6" class="empty">没有符合条件的词条</td></tr>'
      elements.vocabularyReviewRows.querySelectorAll('[data-save-import-entry]').forEach((button) => {
        button.addEventListener('click', () => saveEntry(button.dataset.saveImportEntry))
      })
    }
    const page = number(current.page) || 1
    const pageSize = number(current.pageSize) || state.vocabularyImportPageSize
    const pages = Math.max(1, Math.ceil(number(current.filteredTotal) / pageSize))
    elements.vocabularyPageInfo.textContent = `第 ${page} / ${pages} 页 · ${number(current.filteredTotal)} 条`
    elements.vocabularyPrevPageBtn.disabled = page <= 1
    elements.vocabularyNextPageBtn.disabled = page >= pages
    if (canManageCatalogs && current.status === 'published') renderAnalysis(current)
    else elements.vocabularyAnalysisAction?.classList.add('hidden')
  }

  function renderAnalysis(current = state.currentVocabularyImport) {
    if (!elements.vocabularyAnalysisAction) return
    const published = current?.status === 'published'
    elements.vocabularyAnalysisAction.classList.toggle('hidden', !published)
    if (!published) return
    const analysis = sameId(state.currentVocabularyAnalysis?.catalogVersionId, current.catalogVersionId)
      ? state.currentVocabularyAnalysis : null
    const status = analysis?.status || 'not_started'
    const analyzed = number(analysis?.analyzedCount)
    const total = number(analysis?.publishedCount) || number(current.totalCount)
    const pending = Math.max(0, number(analysis?.unanalyzedCount) || (total - analyzed))
    const groups = number(analysis?.groupCount)
    const isAllAnalyzed = total > 0 && analyzed >= total && pending === 0
    const effectiveStatus = isAllAnalyzed ? 'completed' : status
    elements.vocabularyAnalysisStatus.textContent = `词本关联分析：${ANALYSIS_STATUS_LABELS[effectiveStatus] || effectiveStatus} · ${analyzed}/${total} 词${groups ? ` · ${groups} 组` : ''}`
    const running = !isAllAnalyzed && (status === 'pending' || status === 'running')
    elements.triggerVocabularyAnalysisBtn.disabled = running || pending === 0 || analysis?.canTrigger === false || isAllAnalyzed
    elements.triggerVocabularyAnalysisBtn.textContent = running ? (status === 'running' ? '分析中...' : '等待执行') : (pending > 0 ? `分析剩余 ${pending} 词` : '分析已完成')
  }

  async function loadAnalysis(catalogVersionId, options = {}) {
    if (!catalogVersionId) return null
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
    const current = state.currentVocabularyImport
    if (!current || current.status !== 'published') return
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
    const current = state.currentVocabularyImport
    if (!current || current.status === 'published') return
    if (number(current.pendingWarningCount) > 0) {
      toast('请先确认所有疑似断词')
      return
    }
    const confirmed = await confirmAction({
      title: '发布公共词本',
      message: `确认将「${current.catalogName}」发布为公共词本？发布后可用于新建学习计划，导入阶段不会批量生成 AI 词卡。`,
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
        state.currentVocabularyAnalysis = { catalogId: current.catalogId, catalogVersionId: current.catalogVersionId, status: 'not_started', publishedCount: number(current.totalCount), analyzedCount: 0, unanalyzedCount: number(current.totalCount), canTrigger: number(current.totalCount) > 0 }
      } else {
        state.currentVocabularyImport = await catalogApi.publish(current.jobId)
        await loadAnalysis(state.currentVocabularyImport.catalogVersionId, { quiet: true })
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
    previousPage,
    nextPage,
    previousHistoryPage,
    nextHistoryPage,
  }
}
