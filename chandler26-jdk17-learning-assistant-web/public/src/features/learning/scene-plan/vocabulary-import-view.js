import { formatDateTime } from '/src/shared/text.js'
import {
  ANALYSIS_STATUS_LABELS,
  IMPORT_STATUS_LABELS,
  SOURCE_LABELS,
  asArray,
  number,
} from '/src/features/learning/scene-plan/model.js'

/**
 * 词本导入域的纯视图层，集中处理列表、审核表和分析状态渲染。
 * 导入、审核和任务提交动作通过回调注入，避免工作流文件继续膨胀。
 */
export function createVocabularyImportView({
  state,
  elements,
  escapeHtml,
  sameId,
  onOpenReview,
  onRemove,
  onSaveEntry,
}) {
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
      elements.sceneImportList.textContent = '暂无公共词本管理记录'
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
      button.addEventListener('click', () => onOpenReview(button.dataset.importJobId))
    })
    elements.sceneImportList.querySelectorAll('[data-import-job-edit]').forEach((button) => {
      button.addEventListener('click', (event) => {
        event.stopPropagation()
        onOpenReview(button.dataset.importJobEdit)
      })
    })
    elements.sceneImportList.querySelectorAll('[data-import-job-delete]').forEach((button) => {
      button.addEventListener('click', async (event) => {
        event.stopPropagation()
        await onRemove(button.dataset.importJobDelete)
      })
    })
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
        button.addEventListener('click', () => onSaveEntry(button.dataset.saveImportEntry))
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

  return { renderImportList, renderReview, renderAnalysis }
}
