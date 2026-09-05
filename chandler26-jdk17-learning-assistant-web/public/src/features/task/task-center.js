import { escapeHtml, formatDateTime } from '/src/shared/text.js'
import { hideModal, showModal } from '/src/shared/modal.js'

const STATUS_LABELS = {
  pending: '等待执行',
  running: '生成中',
  retry_wait: '等待重试',
  completed: '已完成',
  partial_failed: '部分失败',
  attention_required: '需要处理',
  failed: '失败',
  cancelled: '已取消',
}

const TYPE_LABELS = {
  scene_material: '场景材料',
  scene_material_regeneration: '材料重新生成',
  scene_related_vocabulary: '场景相关词汇',
  scene_article_audio: '场景文章语音',
  vocabulary_card: '批量词卡',
  vocabulary_catalog_analysis: '词本关联分析',
  article_material: '语境精读材料',
}

const MODE_LABELS = {
  immediate: '立即执行',
  scheduled: '预约执行',
  low_cost_window: '低价时段',
}

export function createTaskCenterProfileFeature(ctx) {
  const { state, elements, request, toast, logEvent, confirmAction, confirmDelete } = ctx
  let refreshTimer = null

  function isAdmin() {
    return state.preview || state.user?.roleCode === 'ADMIN'
  }

  function previewTasks() {
    return [
      {
        id: 'preview-task-1', taskType: 'vocabulary_card', taskName: '机场出发场景 · 批量词卡',
        status: 'running', executionMode: 'low_cost_window', totalCount: 40, successCount: 24,
        failedCount: 0, progressPercent: 60, scheduledTime: new Date().toISOString(),
        createTime: new Date(Date.now() - 3600000).toISOString(), updateTime: new Date().toISOString(),
      },
      {
        id: 'preview-task-2', taskType: 'scene_material', taskName: '下一篇场景材料',
        status: 'pending', executionMode: 'scheduled', totalCount: 1, successCount: 0,
        failedCount: 0, progressPercent: 0, scheduledTime: new Date(Date.now() + 14400000).toISOString(),
        createTime: new Date().toISOString(), updateTime: new Date().toISOString(),
      },
    ]
  }

  function loadAiTasks(options = {}) {
    if (state.preview) {
      state.aiTasks = state.aiTasks?.length ? state.aiTasks : previewTasks()
      state.aiTaskTotal = state.aiTasks.length
      renderAiTasks()
      return Promise.resolve(state.aiTasks)
    }
    if (!state.token) {
      state.aiTasks = []
      state.aiTaskTotal = 0
      renderAiTasks()
      return Promise.resolve([])
    }
    if (options.page) {
      state.aiTaskPage = options.page
    }
    const isExplicitAll = typeof options.all === 'boolean' ? options.all : (state.activeView === 'systemAdminView')
    const shouldLoadAll = (state.user?.roleCode === 'ADMIN') && isExplicitAll
    const filter = elements.aiTaskStatusFilter?.value || ''
    const params = new URLSearchParams({
      page: String(state.aiTaskPage || 1),
      pageSize: String(state.aiTaskPageSize || 20),
    })
    if (shouldLoadAll) params.set('all', 'true')
    if (filter) params.set('status', filter)
    const url = `/api/v1/learning/ai-tasks?${params.toString()}`
    return request(url)
      .then((res) => {
        if (res && Array.isArray(res.items)) {
          state.aiTasks = res.items
          state.aiTaskTotal = Number(res.total ?? res.items.length)
          state.aiTaskPage = Number(res.page ?? 1)
          state.aiTaskPageSize = Number(res.pageSize ?? 20)
        } else if (Array.isArray(res)) {
          state.aiTasks = res
          state.aiTaskTotal = res.length
        } else {
          state.aiTasks = []
          state.aiTaskTotal = 0
        }
        renderAiTasks()
        if (Array.isArray(state.aiTasks)) {
          state.aiTasks.forEach((task) => {
            window.dispatchEvent(new CustomEvent('learning:ai-task-updated', {
              detail: {
                id: task.id, planId: task.planId, status: task.status, taskType: task.taskType,
                businessType: task.businessType, businessId: task.businessId, errorMessage: task.errorMessage,
              },
            }))
          })
        }
        scheduleRefresh()
        return state.aiTasks
      })
      .catch((error) => {
        logEvent('error', 'AI 任务加载失败', error.message)
        toast(`AI 任务加载失败：${error.message}`)
        renderAiTasks()
        return []
      })
  }

  function changePage(delta) {
    const totalPages = Math.max(1, Math.ceil((state.aiTaskTotal || 0) / (state.aiTaskPageSize || 20)))
    const target = Math.max(1, Math.min(totalPages, (state.aiTaskPage || 1) + delta))
    if (target !== state.aiTaskPage) {
      state.aiTaskPage = target
      loadAiTasks()
    }
  }

  function scheduleRefresh() {
    window.clearTimeout(refreshTimer)
    if (!state.aiTasks?.some((task) => ['pending', 'running', 'retry_wait'].includes(task.status))) return
    refreshTimer = window.setTimeout(() => loadAiTasks(), 8000)
  }

  function renderAiTasks() {
    if (!elements.aiTaskList) return
    const tasks = Array.isArray(state.aiTasks) ? state.aiTasks : []
    const active = tasks.filter((task) => ['pending', 'running', 'retry_wait'].includes(task.status)).length
    const failed = tasks.filter((task) => ['failed', 'partial_failed', 'attention_required'].includes(task.status)).length
    if (elements.aiTaskSummary) elements.aiTaskSummary.textContent = `${active} 执行中 · ${failed} 个需处理`

    const totalPages = Math.max(1, Math.ceil((state.aiTaskTotal || 0) / (state.aiTaskPageSize || 20)))
    const currentPage = Math.min(state.aiTaskPage || 1, totalPages)
    if (elements.aiTaskPageInfo) {
      elements.aiTaskPageInfo.textContent = `第 ${currentPage} / ${totalPages} 页 · 共 ${state.aiTaskTotal || 0} 条`
    }
    if (elements.aiTaskPrevBtn) {
      elements.aiTaskPrevBtn.disabled = currentPage <= 1
    }
    if (elements.aiTaskNextBtn) {
      elements.aiTaskNextBtn.disabled = currentPage >= totalPages
    }

    if (!tasks.length) {
      elements.aiTaskList.className = 'ai-task-list empty'
      elements.aiTaskList.textContent = '暂无 AI 任务'
      return
    }
    elements.aiTaskList.className = 'ai-task-list'
    elements.aiTaskList.innerHTML = tasks.map((task) => {
      const total = Number(task.totalCount || 0)
      const progress = Math.max(0, Math.min(100, Number(task.progressPercent || 0)))
      const status = STATUS_LABELS[task.status] || task.status || '未知'
      const canCancel = ['pending', 'running', 'retry_wait'].includes(task.status)
      const canRetry = ['failed', 'partial_failed', 'attention_required', 'cancelled'].includes(task.status)
      const canRun = task.status === 'pending' && task.executionMode !== 'immediate'
      return `
        <article class="ai-task-item">
          <div class="ai-task-main">
            <div class="ai-task-title-line">
              <strong>${escapeHtml(task.taskName || TYPE_LABELS[task.taskType] || 'AI 任务')}</strong>
              <div class="inline-actions">
                ${task.userName ? `<span class="user-badge-mini" title="任务归属人 #${task.ownerUserId || task.userId || ''}">${escapeHtml(task.userName)}</span>` : ''}
                <span class="task-status task-status-${escapeHtml(task.status || '')}">${escapeHtml(status)}</span>
              </div>
            </div>
            <p>${escapeHtml(TYPE_LABELS[task.taskType] || task.taskType || 'AI 任务')} · ${escapeHtml(MODE_LABELS[task.executionMode] || task.executionMode || '立即执行')} · ${escapeHtml(task.triggerType === 'system' ? '系统触发' : task.triggerType === 'admin' ? '管理员触发' : '用户触发')}</p>
            <div class="ai-task-progress"><span style="width:${progress}%"></span></div>
            <small>${total ? `成功 ${Number(task.successCount || 0)} / ${total} · 失败 ${Number(task.failedCount || 0)}` : '等待任务开始'}${task.scheduledTime ? ` · 计划 ${escapeHtml(formatDateTime(task.scheduledTime))}` : ''}</small>
            ${task.errorMessage ? `<small class="ai-task-error">${escapeHtml(task.errorMessage)}</small>` : ''}
          </div>
          <div class="ai-task-side">
            <small>创建 ${escapeHtml(formatDateTime(task.createTime))}</small>
            <div class="inline-actions">
              ${canRun ? `<button class="secondary-button compact" type="button" data-task-run="${escapeHtml(task.id)}">立即执行</button>` : ''}
              <button class="secondary-button compact" type="button" data-task-detail="${escapeHtml(task.id)}">查看详情</button>
              ${canRetry ? `<button class="secondary-button compact" type="button" data-task-retry="${escapeHtml(task.id)}">重试</button>` : ''}
              ${canCancel ? `<button class="ghost-button compact" type="button" data-task-cancel="${escapeHtml(task.id)}">取消</button>` : ''}
              ${isAdmin() ? `<button class="danger-button compact" type="button" data-task-delete="${escapeHtml(task.id)}" title="删除此任务记录">删除</button>` : ''}
            </div>
          </div>
        </article>
      `
    }).join('')
    elements.aiTaskList.querySelectorAll('[data-task-run]').forEach((button) => button.addEventListener('click', () => operateTask(button.dataset.taskRun, 'run-now')))
    elements.aiTaskList.querySelectorAll('[data-task-detail]').forEach((button) => button.addEventListener('click', () => showTaskDetail(button.dataset.taskDetail)))
    elements.aiTaskList.querySelectorAll('[data-task-retry]').forEach((button) => button.addEventListener('click', () => operateTask(button.dataset.taskRetry, 'retry')))
    elements.aiTaskList.querySelectorAll('[data-task-cancel]').forEach((button) => button.addEventListener('click', () => operateTask(button.dataset.taskCancel, 'cancel')))
    elements.aiTaskList.querySelectorAll('[data-task-delete]').forEach((button) => button.addEventListener('click', () => deleteTask(button.dataset.taskDelete)))
  }

  async function deleteTask(taskId) {
    const task = (state.aiTasks || []).find((item) => String(item.id) === String(taskId))
    if (!task) return
    const confirmed = await confirmDelete({
      title: '删除 AI 任务',
      message: `确认删除任务「${task.taskName || '该任务'}」？删除后将不再显示。`,
    })
    if (!confirmed) return
    try {
      if (state.preview) {
        state.aiTasks = state.aiTasks.filter((item) => String(item.id) !== String(taskId))
      } else {
        await request(`/api/v1/learning/ai-tasks/${encodeURIComponent(taskId)}`, { method: 'DELETE' })
        state.aiTasks = state.aiTasks.filter((item) => String(item.id) !== String(taskId))
      }
      renderAiTasks()
      toast('AI 任务已删除')
    } catch (error) {
      logEvent('error', 'AI 任务删除失败', error.message)
      toast(`删除任务失败：${error.message}`)
    }
  }

  async function showTaskDetail(taskId) {
    try {
      const task = state.preview
        ? (state.aiTasks || []).find((item) => String(item.id) === String(taskId))
        : await request(`/api/v1/learning/ai-tasks/${encodeURIComponent(taskId)}`)
      if (!task) return
      const steps = Array.isArray(task.steps) ? task.steps : []
      const body = steps.length
        ? steps.map((step) => {
          const attempts = Array.isArray(step.attempts) ? step.attempts : []
          return `<section class="ai-task-detail-step">
            <div><strong>${escapeHtml(step.stepName || step.stepCode)}</strong><span class="task-status task-status-${escapeHtml(step.status || '')}">${escapeHtml(STATUS_LABELS[step.status] || step.status)}</span></div>
            <p>${Number(step.completedCount || 0)} / ${Number(step.totalCount || 0)} · 尝试 ${Number(step.attemptCount || 0)} 次${step.heartbeatTime ? ` · 心跳 ${escapeHtml(formatDateTime(step.heartbeatTime))}` : ''}</p>
            ${step.errorMessage ? `<small class="ai-task-error">${escapeHtml(step.errorMessage)}</small>` : ''}
            ${attempts.map((attempt) => `<small>第 ${Number(attempt.attemptNo || 0)} 次 · ${escapeHtml(STATUS_LABELS[attempt.status] || attempt.status || '')}${attempt.modelName ? ` · ${escapeHtml(attempt.provider || '')}/${escapeHtml(attempt.modelName)}` : ''}${attempt.totalTokens ? ` · ${Number(attempt.totalTokens)} Token` : ''}</small>`).join('')}
          </section>`
        }).join('')
        : '<p class="empty">该历史任务没有步骤明细</p>'
      if (!elements.aiTaskDetailModal) return
      elements.aiTaskDetailTitle.textContent = task.taskName || '任务详情'
      elements.aiTaskDetailMeta.textContent = `归属人：${task.userName || '-'} · 触发人：${task.triggerUserName || '系统'} · 最近操作：${task.operatorUserName || '-'}`
      elements.aiTaskDetailSteps.innerHTML = body
      showModal(elements.aiTaskDetailModal)
    } catch (error) {
      toast(`任务详情加载失败：${error.message}`)
    }
  }

  async function operateTask(taskId, action) {
    const task = (state.aiTasks || []).find((item) => String(item.id) === String(taskId))
    if (!task) return
    if (action === 'cancel') {
      const confirmed = await confirmAction({
        title: '取消 AI 任务',
        message: `确认取消「${task.taskName || '该任务'}」？已完成的批次会保留。`,
        acceptText: '确认取消',
      })
      if (!confirmed) return
    }
    try {
      if (state.preview) {
        if (action === 'cancel') task.status = 'cancelled'
        if (action === 'retry' || action === 'run-now') task.status = 'pending'
      } else {
        const updated = await request(`/api/v1/learning/ai-tasks/${encodeURIComponent(taskId)}/${action}`, { method: 'POST' })
        state.aiTasks = state.aiTasks.map((item) => String(item.id) === String(taskId) ? updated : item)
        if (action === 'retry' && updated.status !== 'pending') {
          renderAiTasks()
          toast('任务未重新排队，请检查任务状态或重试次数')
          return
        }
      }
      renderAiTasks()
      const current = state.aiTasks.find((item) => String(item.id) === String(taskId))
      if (current) {
        window.dispatchEvent(new CustomEvent('learning:ai-task-updated', {
          detail: {
            id: current.id, planId: current.planId, status: current.status, taskType: current.taskType,
            businessType: current.businessType, businessId: current.businessId, errorMessage: current.errorMessage,
          },
        }))
      }
      scheduleRefresh()
      toast(action === 'cancel' ? '任务已取消' : action === 'retry' ? '任务已重新排队' : '任务已安排立即执行')
    } catch (error) {
      logEvent('error', 'AI 任务操作失败', error.message)
      toast(`任务操作失败：${error.message}`)
    }
  }

  elements.aiTaskDetailCloseBtn?.addEventListener('click', () => hideModal(elements.aiTaskDetailModal))
  elements.aiTaskDetailModal?.addEventListener('click', (event) => {
    if (event.target === elements.aiTaskDetailModal) hideModal(elements.aiTaskDetailModal)
  })
  elements.aiTaskPrevBtn?.addEventListener('click', () => changePage(-1))
  elements.aiTaskNextBtn?.addEventListener('click', () => changePage(1))
  elements.aiTaskStatusFilter?.addEventListener('change', () => {
    state.aiTaskPage = 1
    loadAiTasks()
  })

  return { loadAiTasks, renderAiTasks, operateTask, showTaskDetail, deleteTask, changePage }
}
