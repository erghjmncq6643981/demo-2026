import { escapeHtml, formatDateTime } from '/src/shared/text.js'
import { logTypeLabel } from '/src/shared/vocabulary.js'

export function createActivityProfileFeature(ctx) {
  const { state, elements, request, toast, logEvent, confirmDelete, createPreviewActivity } = ctx

  function renderProfileMetrics() {
    const wordbookCount = state.wordbooks.length
    const wordCount = state.wordbooks.reduce((sum, item) => sum + Number(item.entryCount || 0), 0)
    const dueCount = state.wordbooks.reduce((sum, item) => sum + Number(item.dueCount || 0), 0)
    elements.wordbookCount.textContent = String(wordbookCount)
    elements.wordCount.textContent = String(wordCount)
    elements.dueCount.textContent = String(dueCount)
  }

  function loadActivity() {
    if (state.preview) {
      state.activity = state.activity || createPreviewActivity()
      renderActivityHeatmap()
      return Promise.resolve()
    }
    if (!state.token) {
      state.activity = null
      renderActivityHeatmap()
      return Promise.resolve()
    }
    return request('/api/v1/learning/activity?days=180')
      .then((activity) => {
        state.activity = activity
        renderActivityHeatmap()
      })
      .catch((error) => {
        logEvent('error', '学习活跃图加载失败', error.message)
        renderActivityHeatmap()
      })
  }

  function renderActivityHeatmap() {
    if (!elements.activityHeatmap) return
    const items = Array.isArray(state.activity?.items) ? state.activity.items : []
    const learnedTotal = state.activity?.learnedTotal ?? items.reduce((sum, item) => sum + Number(item.learnedCount || 0), 0)
    const reviewTotal = state.activity?.reviewTotal ?? items.reduce((sum, item) => sum + Number(item.reviewCount || 0), 0)
    elements.activitySummary.textContent = `${learnedTotal} 学习 / ${reviewTotal} 复习`
    if (!items.length) {
      elements.activityHeatmap.className = 'activity-heatmap empty'
      elements.activityHeatmap.textContent = state.token || state.preview ? '暂无学习活跃数据' : '登录后查看学习活跃图'
      return
    }
    const maxTotal = Math.max(1, ...items.map((item) => Number(item.totalCount || 0)))
    elements.activityHeatmap.className = 'activity-heatmap'
    elements.activityHeatmap.innerHTML = items
      .map((item) => {
        const total = Number(item.totalCount || 0)
        const level = activityLevel(total, maxTotal)
        const title = `${item.date}: 学习 ${item.learnedCount || 0}，复习 ${item.reviewCount || 0}`
        return `<span data-level="${level}" title="${escapeHtml(title)}" aria-label="${escapeHtml(title)}"></span>`
      })
      .join('')
  }

  function activityLevel(total, maxTotal) {
    if (!total) return 0
    if (total >= maxTotal * 0.75) return 4
    if (total >= maxTotal * 0.45) return 3
    if (total >= maxTotal * 0.2) return 2
    return 1
  }

  function renderSystemLogs() {
    if (!state.systemLogs.length) {
      elements.systemLogList.className = 'log-list empty'
      elements.systemLogList.textContent = '暂无系统日志'
      return
    }
    elements.systemLogList.className = 'log-list'
    elements.systemLogList.innerHTML = state.systemLogs
      .map(
        (item) => `
          <div class="log-item">
            <span>${escapeHtml(logTypeLabel(item.type))}</span>
            <div>
              <strong>${escapeHtml(item.title)}</strong>
              <p>${escapeHtml(item.detail || '')}</p>
              <small>${escapeHtml(formatDateTime(item.time || item.createTime))}${item.source ? ` · ${escapeHtml(item.source)}` : ''}</small>
            </div>
          </div>
        `,
      )
      .join('')
  }

  function loadSystemLogs() {
    if (state.preview) {
      renderSystemLogs()
      return Promise.resolve()
    }
    if (!state.token) {
      state.systemLogs = JSON.parse(localStorage.getItem('learning.systemLogs') || '[]')
      renderSystemLogs()
      return Promise.resolve()
    }
    return request('/api/v1/learning/system-logs?limit=80')
      .then((logs) => {
        state.systemLogs = Array.isArray(logs) ? logs : []
        localStorage.removeItem('learning.systemLogs')
        renderSystemLogs()
      })
      .catch((error) => {
        state.systemLogs = JSON.parse(localStorage.getItem('learning.systemLogs') || '[]')
        renderSystemLogs()
        toast(`系统日志加载失败：${error.message}`)
      })
  }

  async function clearLogs() {
    const confirmed = await confirmDelete({
      title: '清空系统日志',
      message: '确认清空系统日志？清空后当前列表中的日志记录将被移除。',
      acceptText: '确认清空',
    })
    if (!confirmed) return
    state.systemLogs = []
    try {
      if (state.preview || !state.token) {
        localStorage.removeItem('learning.systemLogs')
      } else {
        await request('/api/v1/learning/system-logs', { method: 'DELETE' })
      }
      renderSystemLogs()
      toast('系统日志已清空')
    } catch (error) {
      toast(`系统日志清空失败：${error.message}`)
    }
  }


  return {
    renderProfileMetrics,
    loadActivity,
    renderActivityHeatmap,
    activityLevel,
    renderSystemLogs,
    loadSystemLogs,
    clearLogs,
  }
}
