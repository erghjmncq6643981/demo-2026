import { escapeHtml, formatDateTime } from '/src/shared/text.js'

const STATUS_LABELS = {
  pending: '等待执行',
  running: '生成中',
  completed: '已完成',
  partial_failed: '部分失败',
  failed: '失败',
  cancelled: '已取消',
}

const TYPE_LABELS = {
  scene_material: '场景材料',
  vocabulary_card: '批量词卡',
  vocabulary_catalog_analysis: '词本关联分析',
}

const MODE_LABELS = {
  immediate: '立即执行',
  scheduled: '预约执行',
  low_cost_window: '低价时段',
}

export function createTaskCenterProfileFeature(ctx) {
  const { state, elements, request, toast, logEvent, confirmAction } = ctx
  let refreshTimer = null

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

  function loadAiTasks() {
    if (state.preview) {
      state.aiTasks = state.aiTasks?.length ? state.aiTasks : previewTasks()
      renderAiTasks()
      return Promise.resolve(state.aiTasks)
    }
    if (!state.token) {
      state.aiTasks = []
      renderAiTasks()
      return Promise.resolve([])
    }
    return request('/api/v1/learning/ai-tasks?limit=80')
      .then((tasks) => {
        state.aiTasks = Array.isArray(tasks) ? tasks : []
        renderAiTasks()
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

  function scheduleRefresh() {
    window.clearTimeout(refreshTimer)
    if (!state.aiTasks?.some((task) => ['pending', 'running'].includes(task.status))) return
    refreshTimer = window.setTimeout(() => loadAiTasks(), 8000)
  }

  function renderAiTasks() {
    if (!elements.aiTaskList) return
    const filter = elements.aiTaskStatusFilter?.value || ''
    const tasks = (Array.isArray(state.aiTasks) ? state.aiTasks : []).filter((task) => !filter || task.status === filter)
    const active = (state.aiTasks || []).filter((task) => ['pending', 'running'].includes(task.status)).length
    const failed = (state.aiTasks || []).filter((task) => ['failed', 'partial_failed'].includes(task.status)).length
    if (elements.aiTaskSummary) elements.aiTaskSummary.textContent = `${active} 执行中 · ${failed} 个需处理`
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
      const canCancel = ['pending', 'running'].includes(task.status)
      const canRetry = ['failed', 'partial_failed', 'cancelled'].includes(task.status)
      const canRun = task.status === 'pending' && task.executionMode !== 'immediate'
      return `
        <article class="ai-task-item">
          <div class="ai-task-main">
            <div class="ai-task-title-line">
              <strong>${escapeHtml(task.taskName || TYPE_LABELS[task.taskType] || 'AI 任务')}</strong>
              <span class="task-status task-status-${escapeHtml(task.status || '')}">${escapeHtml(status)}</span>
            </div>
            <p>${escapeHtml(TYPE_LABELS[task.taskType] || task.taskType || 'AI 任务')} · ${escapeHtml(MODE_LABELS[task.executionMode] || task.executionMode || '立即执行')}</p>
            <div class="ai-task-progress"><span style="width:${progress}%"></span></div>
            <small>${total ? `成功 ${Number(task.successCount || 0)} / ${total} · 失败 ${Number(task.failedCount || 0)}` : '等待任务开始'}${task.scheduledTime ? ` · 计划 ${escapeHtml(formatDateTime(task.scheduledTime))}` : ''}</small>
            ${task.errorMessage ? `<small class="ai-task-error">${escapeHtml(task.errorMessage)}</small>` : ''}
          </div>
          <div class="ai-task-side">
            <small>创建 ${escapeHtml(formatDateTime(task.createTime))}</small>
            <div class="inline-actions">
              ${canRun ? `<button class="secondary-button compact" type="button" data-task-run="${escapeHtml(task.id)}">立即执行</button>` : ''}
              ${canRetry ? `<button class="secondary-button compact" type="button" data-task-retry="${escapeHtml(task.id)}">重试</button>` : ''}
              ${canCancel ? `<button class="ghost-button compact" type="button" data-task-cancel="${escapeHtml(task.id)}">取消</button>` : ''}
            </div>
          </div>
        </article>
      `
    }).join('')
    elements.aiTaskList.querySelectorAll('[data-task-run]').forEach((button) => button.addEventListener('click', () => operateTask(button.dataset.taskRun, 'run-now')))
    elements.aiTaskList.querySelectorAll('[data-task-retry]').forEach((button) => button.addEventListener('click', () => operateTask(button.dataset.taskRetry, 'retry')))
    elements.aiTaskList.querySelectorAll('[data-task-cancel]').forEach((button) => button.addEventListener('click', () => operateTask(button.dataset.taskCancel, 'cancel')))
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
      scheduleRefresh()
      toast(action === 'cancel' ? '任务已取消' : action === 'retry' ? '任务已重新排队' : '任务已安排立即执行')
    } catch (error) {
      logEvent('error', 'AI 任务操作失败', error.message)
      toast(`任务操作失败：${error.message}`)
    }
  }

  return { loadAiTasks, renderAiTasks, operateTask }
}
