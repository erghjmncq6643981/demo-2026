import { sameId } from '/src/shared/ids.js'
import { escapeHtml } from '/src/shared/text.js'
import { formatGenerationSourceLabel } from '/src/features/rumination/rumination-material-task.js'

const ACTIVE_STATUSES = new Set(['pending', 'running', 'retry_wait'])
const FAILURE_STATUSES = new Set(['failed', 'attention_required', 'partial_failed'])
const GRAPH_TASK_TYPES = new Set(['rumination_scenario', 'rumination_fission'])

export function createRuminationGraphTaskTracker({
  bannerEl,
  api,
  toast,
  getContext,
  onScenarioCompleted,
  onFissionCompleted,
  onSwitchScenario,
}) {
  const tracked = new Map()
  const pollTimers = new Map()
  const finishedInSession = new Set()

  function clearTimer(taskId) {
    const key = String(taskId)
    const timer = pollTimers.get(key)
    if (timer) {
      clearTimeout(timer)
      pollTimers.delete(key)
    }
  }

  function clearAllTimers() {
    for (const timer of pollTimers.values()) {
      clearTimeout(timer)
    }
    pollTimers.clear()
  }

  function formatTaskHeadline(item) {
    const t = item.task || {}
    if (t.taskType === 'rumination_scenario') {
      return `自定义场景「${item.scenarioIcon ? `${item.scenarioIcon} ` : ''}${item.scenarioName || item.scenarioCode}」`
    }
    return `节点【${item.targetWord || item.parentNodeId}】4D 衍生裂变（${item.scenarioName || item.scenarioCode}）`
  }

  function renderBanner() {
    if (!bannerEl) return
    const ctx = getContext()
    const visibleItems = Array.from(tracked.values()).filter((item) => {
      if (!sameId(item.wordbookId, ctx.wordbookId)) return false
      const st = item.task?.status
      if (ACTIVE_STATUSES.has(st)) return true
      if (FAILURE_STATUSES.has(st)) {
        return item.scenarioCode === ctx.scenarioCode || item.task?.taskType === 'rumination_scenario'
      }
      return false
    })

    if (visibleItems.length === 0) {
      bannerEl.className = 'rumination-task-banner graph-task-banner hidden'
      bannerEl.innerHTML = ''
      return
    }

    visibleItems.sort((a, b) => {
      const aMatch = a.scenarioCode === ctx.scenarioCode ? 0 : 1
      const bMatch = b.scenarioCode === ctx.scenarioCode ? 0 : 1
      if (aMatch !== bMatch) return aMatch - bMatch
      return ACTIVE_STATUSES.has(a.task?.status) ? -1 : 1
    })

    const primary = visibleItems[0]
    const task = primary.task
    const status = task.status
    const headline = formatTaskHeadline(primary)
    const isOtherScenario = primary.scenarioCode && primary.scenarioCode !== ctx.scenarioCode
    const percent = Number.isFinite(Number(task.progressPercent)) ? Number(task.progressPercent) : (status === 'pending' ? 10 : 55)

    let titleHtml = ''
    let subHtml = ''
    let actionsHtml = ''

    if (status === 'pending') {
      titleHtml = `<span>⏳ 排队中：${escapeHtml(headline)}（任务 #${escapeHtml(String(task.id))}）</span>`
      subHtml = isOtherScenario
        ? '您已切换到其他场景；原任务将继续独立构建，完成后会发送通知且结果只归属原场景。'
        : '后台将自动构建并持久化图谱，您可以自由切换场景或刷新页面继续追踪。'
      actionsHtml = `<button type="button" class="secondary-button compact" data-graph-task-action="cancel" data-task-id="${escapeHtml(String(task.id))}">取消</button>`
    } else if (status === 'running' || status === 'retry_wait') {
      titleHtml = `<span>${status === 'retry_wait' ? '🔄 自动重试中' : '⚡ 异步构建中'}：${escapeHtml(headline)}（${percent}%）</span>`
      subHtml = isOtherScenario
        ? '任务结果仅归属原场景与原绑定节点，不会串入当前正在浏览的场景。'
        : '阶段 1 调用默认模型，阶段 2 独立事务落库；即使刷新页面也不丢失进度。'
      actionsHtml = `<button type="button" class="secondary-button compact" data-graph-task-action="cancel" data-task-id="${escapeHtml(String(task.id))}">取消</button>`
    } else if (FAILURE_STATUSES.has(status)) {
      const steps = Array.isArray(task.steps) ? task.steps : []
      const step1Done = steps.some((s) => (s.stepCode === 'generate_scenario' || s.stepCode === 'generate_fission') && s.status === 'completed')
      titleHtml = `<span>❌ ${escapeHtml(headline)} 执行未完成</span>`
      subHtml = `${escapeHtml(task.errorMessage || '任务失败')}${step1Done ? '（阶段 1 检查点已保留，点击重试直接恢复落库，不再重复调用模型）' : ''}`
      actionsHtml = `<button type="button" class="primary-button compact" data-graph-task-action="retry" data-task-id="${escapeHtml(String(task.id))}">🔄 重试任务</button>`
    }

    if (visibleItems.length > 1) {
      subHtml += `（另有 ${visibleItems.length - 1} 个关联后台图谱任务）`
    }

    const showBar = ACTIVE_STATUSES.has(status)
    bannerEl.className = `rumination-task-banner graph-task-banner status-${escapeHtml(status)}`
    bannerEl.innerHTML = `
      <div class="rumination-task-main">
        <div class="rumination-task-title">${titleHtml}</div>
        <div class="rumination-task-sub">${subHtml}</div>
        ${showBar ? `<div class="rumination-task-progress"><span style="width: ${Math.max(8, Math.min(100, percent))}%"></span></div>` : ''}
      </div>
      <div class="rumination-task-actions">
        ${isOtherScenario && primary.scenarioCode ? `<button type="button" class="secondary-button compact" data-graph-task-action="switch" data-scenario-code="${escapeHtml(primary.scenarioCode)}">查看原场景</button>` : ''}
        ${actionsHtml}
      </div>
    `
    bindActions()
  }

  function bindActions() {
    if (!bannerEl) return
    bannerEl.querySelectorAll('[data-graph-task-action]').forEach((btn) => {
      btn.addEventListener('click', async () => {
        const action = btn.getAttribute('data-graph-task-action')
        const taskId = btn.getAttribute('data-task-id')
        if (action === 'switch') {
          const sc = btn.getAttribute('data-scenario-code')
          if (sc && typeof onSwitchScenario === 'function') {
            onSwitchScenario(sc)
          }
          return
        }
        if (action === 'retry' && taskId && api?.retryAiTask) {
          btn.disabled = true
          try {
            const retried = await api.retryAiTask(taskId)
            const existing = tracked.get(String(taskId))
            if (existing) {
              await handleTaskState({ ...existing, task: retried }, true)
            }
            toast('🔄 已提交图谱任务断点续跑重试')
          } catch (err) {
            btn.disabled = false
            toast(err.message || '重试失败')
          }
        }
        if (action === 'cancel' && taskId && api?.cancelAiTask) {
          btn.disabled = true
          try {
            const cancelled = await api.cancelAiTask(taskId)
            const existing = tracked.get(String(taskId))
            if (existing) {
              await handleTaskState({ ...existing, task: cancelled }, false)
            }
            toast('已取消该后台图谱任务')
          } catch (err) {
            btn.disabled = false
            toast(err.message || '取消失败')
          }
        }
      })
    })
  }

  function schedulePoll(taskId) {
    const key = String(taskId)
    clearTimer(key)
    const timer = setTimeout(async () => {
      pollTimers.delete(key)
      const current = tracked.get(key)
      if (!current || !api?.getAiTask) return
      const ctx = getContext()
      if (!sameId(ctx.wordbookId, current.wordbookId)) return
      try {
        const latestTask = await api.getAiTask(key)
        const latestItem = tracked.get(key)
        if (!latestItem || !sameId(getContext().wordbookId, latestItem.wordbookId)) return
        await handleTaskState({ ...latestItem, task: latestTask }, true)
      } catch (err) {
        console.warn('轮询知识反刍图谱任务状态失败', err)
        if (tracked.get(key) && ACTIVE_STATUSES.has(tracked.get(key).task?.status)) {
          schedulePoll(key)
        }
      }
    }, 1200)
    pollTimers.set(key, timer)
  }

  async function finalizeCompleted(item) {
    const taskId = String(item.task.id)
    if (finishedInSession.has(taskId)) return
    finishedInSession.add(taskId)

    const taskType = item.task.taskType
    if (taskType === 'rumination_scenario') {
      const sourceLabel = item.generationSource ? formatGenerationSourceLabel(item.generationSource) : ''
      if (typeof onScenarioCompleted === 'function') {
        await onScenarioCompleted({ ...item, sourceLabel })
      }
      return
    }

    if (taskType === 'rumination_fission' && api?.getRuminationFissionResult) {
      try {
        const fissionRes = await api.getRuminationFissionResult(taskId)
        if (typeof onFissionCompleted === 'function') {
          await onFissionCompleted({ item, fissionResult: fissionRes })
        }
      } catch (err) {
        console.warn('获取裂变异步结果失败', err)
      }
    }
  }

  async function handleTaskState(item, notifyOnComplete) {
    if (!item?.task?.id) return
    const key = String(item.task.id)
    const prevStatus = tracked.get(key)?.task?.status
    tracked.set(key, item)

    const status = item.task.status
    if (ACTIVE_STATUSES.has(status)) {
      renderBanner()
      schedulePoll(key)
      return
    }

    clearTimer(key)
    if (status === 'completed') {
      if (notifyOnComplete || (prevStatus && ACTIVE_STATUSES.has(prevStatus))) {
        await finalizeCompleted(item)
      }
      tracked.delete(key)
      renderBanner()
      return
    }

    renderBanner()
  }

  return {
    async trackSubmission(task, meta) {
      if (!task?.id) return
      const item = {
        task,
        wordbookId: String(meta.wordbookId),
        scenarioCode: meta.scenarioCode || '',
        scenarioName: meta.scenarioName || '',
        scenarioIcon: meta.scenarioIcon || '',
        parentNodeId: meta.parentNodeId || '',
        targetWord: meta.targetWord || '',
        generationSource: meta.generationSource || null,
      }
      await handleTaskState(item, true)
    },
    async recoverForWordbook(wordbookId, scenarioCode) {
      if (!wordbookId || !api?.listRuminationTasks) return
      try {
        const items = await api.listRuminationTasks(wordbookId, scenarioCode)
        if (!sameId(getContext().wordbookId, wordbookId)) return
        for (const existingKey of Array.from(tracked.keys())) {
          const item = tracked.get(existingKey)
          if (!sameId(item?.wordbookId, wordbookId)) {
            clearTimer(existingKey)
            tracked.delete(existingKey)
          }
        }
        if (Array.isArray(items)) {
          for (const item of items) {
            if (!item?.task?.id || !GRAPH_TASK_TYPES.has(item.task.taskType)) continue
            const isActive = ACTIVE_STATUSES.has(item.task.status)
            await handleTaskState(item, isActive)
          }
        }
        renderBanner()
      } catch (err) {
        console.warn('恢复图谱异步任务失败', err)
      }
    },
    async handleExternalUpdate(detail) {
      if (!detail?.id) return
      const existing = tracked.get(String(detail.id))
      if (!existing) return
      await handleTaskState({ ...existing, task: detail }, true)
    },
    rerender: renderBanner,
    resetAll() {
      clearAllTimers()
      tracked.clear()
      renderBanner()
    },
  }
}
