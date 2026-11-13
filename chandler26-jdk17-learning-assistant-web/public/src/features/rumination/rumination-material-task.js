import { sameId } from '/src/shared/ids.js'
import { escapeHtml } from '/src/shared/text.js'

const ACTIVE_STATUSES = new Set(['pending', 'running', 'retry_wait'])
const FAILURE_STATUSES = new Set(['failed', 'attention_required', 'partial_failed'])

export function formatGenerationSourceLabel(source) {
  return source === 'fallback' ? '⚙️ 本地语义降级' : '🤖 AI 模型生成'
}

export function createRuminationMaterialTaskTracker({
  bannerEl,
  api,
  toast,
  getContext,
  onMaterialCompleted,
  onStatusChange,
}) {
  let pollTimer = null
  let activeTask = null
  let sessionSubmittedTaskId = null
  let sessionCompletedBannerTaskId = null

  function clearPoll() {
    if (pollTimer) {
      clearTimeout(pollTimer)
      pollTimer = null
    }
  }

  function isSameContext(wordbookId, scenarioCode) {
    const ctx = getContext()
    return sameId(ctx.wordbookId, wordbookId) && String(ctx.scenarioCode || '') === String(scenarioCode || '')
  }

  function isTaskActive() {
    return Boolean(activeTask && ACTIVE_STATUSES.has(activeTask.status))
  }

  function renderBanner(task, detailMaterial = null) {
    if (!bannerEl) return
    if (!task) {
      bannerEl.className = 'rumination-task-banner hidden'
      bannerEl.innerHTML = ''
      return
    }

    const status = task.status || 'pending'
    const steps = Array.isArray(task.steps) ? task.steps : []
    const step1Done = steps.some((s) => s.stepCode === 'generate_content' && s.status === 'completed')
    const percent = Number.isFinite(Number(task.progressPercent)) ? Number(task.progressPercent) : (status === 'completed' ? 100 : step1Done ? 50 : 12)

    let titleHtml = ''
    let subHtml = ''
    let actionsHtml = ''

    if (status === 'pending') {
      titleHtml = `<span>⏳ 已进入后台生成队列（任务 #${escapeHtml(String(task.id))}）</span>`
      subHtml = '已锁定提交时词袋与场景快照；等待期间向词袋新加入的词汇不会被清空，刷新页面后仍可追踪。'
      actionsHtml = `<button type="button" class="secondary-button compact" data-task-action="cancel" data-task-id="${escapeHtml(String(task.id))}">取消任务</button>`
    } else if (status === 'running') {
      const stageText = step1Done ? '步骤 2/2：正在落库材料与学习结果检查并同步词袋状态' : '步骤 1/2：正在调用 AI 编织场景材料与学习结果检查'
      titleHtml = `<span>⚡ ${stageText}（${percent}%）</span>`
      subHtml = '生成期间继续添加词汇到词袋不会丢失；若切到其他场景，原任务结果仍会正确沉淀在当前场景。'
      actionsHtml = `<button type="button" class="secondary-button compact" data-task-action="cancel" data-task-id="${escapeHtml(String(task.id))}">取消任务</button>`
    } else if (status === 'retry_wait') {
      titleHtml = `<span>🔄 系统正在自动重试任务（${escapeHtml(String(task.id))}）</span>`
      subHtml = step1Done
        ? '阶段 1 生成结果已保存检查点，本次重试将直接恢复落库，不会重复消耗 AI 模型。'
        : (task.errorMessage || '任务等待自动重试中...')
    } else if (FAILURE_STATUSES.has(status)) {
      titleHtml = `<span>❌ 材料生成任务未完成</span>`
      subHtml = `${escapeHtml(task.errorMessage || '执行异常，请重试。')}（词袋词汇已完整保留；${step1Done ? '已保留阶段 1 检查点，重试将直接从落库阶段继续，不再重复调用模型' : '可直接重新重试'}）`
      actionsHtml = `<button type="button" class="primary-button compact" data-task-action="retry" data-task-id="${escapeHtml(String(task.id))}">🔄 重试任务</button>`
    } else if (status === 'completed') {
      const source = detailMaterial?.generationSource || (sessionCompletedBannerTaskId ? 'ai' : '')
      const sourceBadge = source
        ? `<span class="rumination-source-pill ${source === 'fallback' ? 'is-fallback' : 'is-ai'}">${formatGenerationSourceLabel(source)}</span>`
        : ''
      const regenerated = detailMaterial?.bagConsumed === false
      titleHtml = `<span>✅ ${regenerated ? '场景材料与学习结果检查已重新生成完成' : '场景材料与学习结果检查已生成完成'}</span>${sourceBadge}`
      subHtml = regenerated
        ? '当前场景材料与学习结果检查已覆盖更新，当前图谱词袋保持不变。'
        : '已按提交时词袋快照结算，生成期间新加入词袋的词汇已为您保留。'
      actionsHtml = `<button type="button" class="secondary-button compact" data-task-action="dismiss">知道了</button>`
    } else if (status === 'cancelled') {
      titleHtml = '<span>⏹ 已取消本次材料生成任务</span>'
      subHtml = '原词袋内词汇未发生变动，您可以调整词袋后随时重新编织。'
      actionsHtml = `<button type="button" class="secondary-button compact" data-task-action="dismiss">收起</button>`
    }

    const showBar = ACTIVE_STATUSES.has(status)
    bannerEl.className = `rumination-task-banner status-${escapeHtml(status)}`
    bannerEl.innerHTML = `
      <div class="rumination-task-main">
        <div class="rumination-task-title">${titleHtml}</div>
        <div class="rumination-task-sub">${subHtml}</div>
        ${showBar ? `<div class="rumination-task-progress"><span style="width: ${Math.max(8, Math.min(100, percent))}%"></span></div>` : ''}
      </div>
      ${actionsHtml ? `<div class="rumination-task-actions">${actionsHtml}</div>` : ''}
    `
    bindBannerActions()
  }

  function bindBannerActions() {
    if (!bannerEl) return
    bannerEl.querySelectorAll('[data-task-action]').forEach((btn) => {
      btn.addEventListener('click', async () => {
        const action = btn.getAttribute('data-task-action')
        const taskId = btn.getAttribute('data-task-id')
        if (action === 'dismiss') {
          sessionCompletedBannerTaskId = null
          if (!isTaskActive()) {
            renderBanner(null)
          }
          return
        }
        if (action === 'retry' && taskId && api?.retryAiTask) {
          btn.disabled = true
          try {
            const retried = await api.retryAiTask(taskId)
            const ctx = getContext()
            sessionSubmittedTaskId = String(retried?.id || taskId)
            applyTaskUpdate(retried, ctx.wordbookId, ctx.scenarioCode, true)
            toast('🔄 已提交断点续跑重试，阶段 1 已完成时不会重复消耗模型')
          } catch (err) {
            btn.disabled = false
            toast(err.message || '重试任务失败')
          }
        }
        if (action === 'cancel' && taskId && api?.cancelAiTask) {
          btn.disabled = true
          try {
            const cancelled = await api.cancelAiTask(taskId)
            const ctx = getContext()
            applyTaskUpdate(cancelled, ctx.wordbookId, ctx.scenarioCode, false)
            toast('已取消生成任务，词袋内容保持不变')
          } catch (err) {
            btn.disabled = false
            toast(err.message || '取消任务失败')
          }
        }
      })
    })
  }

  async function finalizeCompletedTask(task, expectedWb, expectedSc) {
    if (!isSameContext(expectedWb, expectedSc)) return
    let material = null
    if (task.businessId && api?.getRuminationMaterialDetail) {
      try {
        material = await api.getRuminationMaterialDetail(task.businessId)
      } catch (err) {
        console.warn('获取新生成场景材料详情失败', err)
      }
    }
    if (!isSameContext(expectedWb, expectedSc)) return
    sessionCompletedBannerTaskId = String(task.id)
    renderBanner(task, material)
    if (typeof onMaterialCompleted === 'function') {
      await onMaterialCompleted(task, material)
    }
  }

  function schedulePoll(taskId, expectedWb, expectedSc) {
    clearPoll()
    pollTimer = setTimeout(async () => {
      if (!isSameContext(expectedWb, expectedSc) || !api?.getAiTask) return
      try {
        const latest = await api.getAiTask(taskId)
        if (!isSameContext(expectedWb, expectedSc)) return
        await applyTaskUpdate(latest, expectedWb, expectedSc, true)
      } catch (err) {
        if (!isSameContext(expectedWb, expectedSc)) return
        console.warn('轮询知识反刍任务状态失败，稍后重试', err)
        if (isTaskActive()) {
          schedulePoll(taskId, expectedWb, expectedSc)
        }
      }
    }, 1200)
  }

  async function applyTaskUpdate(task, expectedWb, expectedSc, shouldNotifyOnComplete) {
    if (!task || !isSameContext(expectedWb, expectedSc)) return
    const prevWasActive = Boolean(activeTask && ACTIVE_STATUSES.has(activeTask.status) && sameId(activeTask.id, task.id))
    activeTask = task
    onStatusChange?.(activeTask)

    if (ACTIVE_STATUSES.has(task.status)) {
      renderBanner(task)
      schedulePoll(task.id, expectedWb, expectedSc)
      return
    }

    clearPoll()
    if (task.status === 'completed') {
      const finishedMs = task.finishedTime ? Date.parse(task.finishedTime) : 0
      const finishedWithinWindow = Number.isFinite(finishedMs) && finishedMs > 0 && (Date.now() - finishedMs) < 120000
      const finishedInSession = prevWasActive || (sessionSubmittedTaskId && sameId(sessionSubmittedTaskId, task.id))
      if (finishedInSession || shouldNotifyOnComplete) {
        await finalizeCompletedTask(task, expectedWb, expectedSc)
      } else if (finishedWithinWindow && task.businessId && api?.getRuminationMaterialDetail) {
        try {
          const detail = await api.getRuminationMaterialDetail(task.businessId)
          if (isSameContext(expectedWb, expectedSc)) {
            renderBanner(task, detail)
          }
        } catch (err) {
          console.warn('获取最近已完成材料来源详情失败', err)
          renderBanner(null)
        }
      } else {
        renderBanner(null)
      }
      return
    }

    renderBanner(task)
  }

  async function startSubmittedTask(task) {
    sessionSubmittedTaskId = task?.id ? String(task.id) : null
    const ctx = getContext()
    await applyTaskUpdate(task, ctx.wordbookId, ctx.scenarioCode, true)
    return task
  }

  return {
    async submit(payload) {
      const task = await api.submitRuminationMaterialTask(payload)
      return startSubmittedTask(task)
    },
    async regenerate(materialId, requestId = '') {
      const task = await api.regenerateRuminationMaterialTask(materialId, requestId)
      return startSubmittedTask(task)
    },
    async recoverForScenario(wordbookId, scenarioCode) {
      clearPoll()
      activeTask = null
      if (!wordbookId || !scenarioCode || !api?.getRuminationMaterialTask) {
        renderBanner(null)
        return null
      }
      try {
        const latest = await api.getRuminationMaterialTask(wordbookId, scenarioCode)
        if (!isSameContext(wordbookId, scenarioCode)) return null
        if (!latest) {
          renderBanner(null)
          return null
        }
        const isActive = ACTIVE_STATUSES.has(latest.status)
        await applyTaskUpdate(latest, wordbookId, scenarioCode, isActive)
        return latest
      } catch (err) {
        console.warn('恢复场景异步任务状态失败', err)
        return null
      }
    },
    async handleExternalUpdate(detail) {
      if (!detail || !detail.id || !activeTask || !sameId(detail.id, activeTask.id)) return
      const ctx = getContext()
      await applyTaskUpdate(detail, ctx.wordbookId, ctx.scenarioCode, true)
    },
    resetView() {
      clearPoll()
      activeTask = null
      onStatusChange?.(null)
      renderBanner(null)
    },
    isTaskActive,
    getActiveTask: () => activeTask,
  }
}
