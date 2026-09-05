import { escapeHtml, formatDateTime } from '/src/shared/text.js'

/**
 * 后台定时任务（JOB）管理员端管理模块。
 */
export function createJobManagementFeature(ctx) {
  const { state, elements, request, toast, logEvent } = ctx
  let pollTimer = null

  function isAdmin() {
    return state.preview || state.user?.roleCode === 'ADMIN'
  }

  async function loadJobs() {
    if (!isAdmin() || !elements.systemJobRows) return
    if (state.preview) {
      state.scheduledJobs = state.scheduledJobs?.length ? state.scheduledJobs : previewJobs()
      renderJobs(state.scheduledJobs)
      return
    }
    try {
      const items = await request('/api/v1/admin/jobs')
      state.scheduledJobs = Array.isArray(items) ? items : []
      renderJobs(state.scheduledJobs)
      checkRunningJobs(state.scheduledJobs)
    } catch (error) {
      logEvent('error', '定时任务列表加载失败', error.message)
      toast(`定时任务加载失败：${error.message}`)
    }
  }

  function checkRunningJobs(items) {
    const hasRunning = items.some((item) => Boolean(item.running))
    if (hasRunning && !pollTimer) {
      pollTimer = setTimeout(() => {
        pollTimer = null
        loadJobs()
      }, 3000)
    }
  }

  function renderJobs(items = []) {
    if (!elements.systemJobRows) return
    if (elements.systemJobSummary) {
      const runningCount = items.filter((j) => j.running).length
      elements.systemJobSummary.textContent = runningCount > 0
        ? `${items.length} 个任务 · ${runningCount} 运行中`
        : `${items.length} 个任务`
    }
    if (!items.length) {
      elements.systemJobRows.innerHTML = '<tr><td colspan="8" class="empty">暂无定时任务</td></tr>'
      return
    }

    elements.systemJobRows.innerHTML = items.map((item) => {
      const isRunning = Boolean(item.running)
      const statusClass = isRunning
        ? 'task-status-running'
        : item.lastStatus === 'SUCCESS'
          ? 'task-status-completed'
          : item.lastStatus === 'FAILED'
            ? 'task-status-failed'
            : 'task-status-pending'
      const statusText = isRunning ? '运行中' : item.lastStatus || '空闲'

      return `
        <tr>
          <td>
            <strong>${escapeHtml(item.name || item.jobKey)}</strong>
            <small class="table-subline" title="${escapeHtml(item.description || '')}">${escapeHtml(item.description || '')}</small>
          </td>
          <td><code>${escapeHtml(item.jobKey)}</code></td>
          <td><span class="mini-pill">${escapeHtml(item.cronExpression || '-')}</span></td>
          <td><span class="task-status ${statusClass}">${escapeHtml(statusText)}</span></td>
          <td>${escapeHtml(formatDateTime(item.lastRunTime) || '未执行')}</td>
          <td>${item.lastCostMs != null ? escapeHtml(item.lastCostMs + 'ms') : '-'}</td>
          <td><span class="job-summary-text" title="${escapeHtml(item.lastSummary || '-')}">${escapeHtml(item.lastSummary || '-')}</span></td>
          <td>
            <button
              class="primary-button compact-primary"
              type="button"
              data-job-trigger="${escapeHtml(item.jobKey)}"
              ${isRunning ? 'disabled' : ''}
            >
              ${isRunning ? '执行中...' : '手动执行'}
            </button>
          </td>
        </tr>
      `
    }).join('')

    elements.systemJobRows.querySelectorAll('[data-job-trigger]').forEach((btn) => {
      btn.addEventListener('click', () => triggerJob(btn.dataset.jobTrigger))
    })
  }

  async function triggerJob(jobKey) {
    if (!isAdmin() || !jobKey) return
    const job = state.scheduledJobs?.find((j) => j.jobKey === jobKey)
    const jobName = job?.name || jobKey

    try {
      if (state.preview) {
        state.scheduledJobs = state.scheduledJobs.map((item) => {
          if (item.jobKey === jobKey) {
            return {
              ...item,
              running: true,
              lastStatus: 'RUNNING',
              lastRunTime: new Date().toISOString(),
            }
          }
          return item
        })
        renderJobs(state.scheduledJobs)
        toast(`任务「${jobName}」已手动触发`)
        setTimeout(() => {
          state.scheduledJobs = state.scheduledJobs.map((item) => {
            if (item.jobKey === jobKey) {
              return {
                ...item,
                running: false,
                lastStatus: 'SUCCESS',
                lastCostMs: 820,
                lastSummary: '模拟手动执行完成：全量资源已核验',
              }
            }
            return item
          })
          renderJobs(state.scheduledJobs)
        }, 2000)
        return
      }

      await request(`/api/v1/admin/jobs/${encodeURIComponent(jobKey)}/trigger?async=true`, {
        method: 'POST',
      })
      toast(`任务「${jobName}」已手动触发，后台正在执行`)
      await loadJobs()
    } catch (error) {
      logEvent('error', '手动触发定时任务失败', error.message)
      toast(`触发任务失败：${error.message}`)
    }
  }

  function previewJobs() {
    return [
      {
        jobKey: 'audio_sync',
        name: '音频资源缺省同步 (词汇+场景文章TTS)',
        description: '核验全量词汇库有道发音与场景文章阿里云 TTS AI 朗读音频文件，缺失时自动补全',
        cronExpression: '0 0 3 * * ?',
        running: false,
        lastRunTime: new Date(Date.now() - 3600000 * 4).toISOString(),
        lastCostMs: 16229,
        lastStatus: 'SUCCESS',
        lastSummary: '词汇[扫描=3264, 缺省=76, 补全=151], 场景文章[扫描=12, 缺省=0, 合成=0]',
      },
      {
        jobKey: 'system_log_recovery',
        name: '系统日志 Outbox 补偿',
        description: '定期补偿重试尚未写入最终存储的系统日志 Outbox 待发布记录',
        cronExpression: 'fixedDelay: 30000ms',
        running: false,
        lastRunTime: new Date(Date.now() - 30000).toISOString(),
        lastCostMs: 12,
        lastStatus: 'SUCCESS',
        lastSummary: '本次补偿持久化日志数: 0',
      },
      {
        jobKey: 'ai_task_dispatch',
        name: 'AI 异步任务轮询分发',
        description: '轮询并分发处于等待执行状态的场景材料与批量词卡等 AI 异步任务',
        cronExpression: 'fixedDelay: 5000ms',
        running: false,
        lastRunTime: new Date(Date.now() - 5000).toISOString(),
        lastCostMs: 5,
        lastStatus: 'SUCCESS',
        lastSummary: '已触发到期 AI 异步任务分发检查',
      },
    ]
  }

  return {
    loadJobs,
    triggerJob,
    renderJobs,
  }
}
