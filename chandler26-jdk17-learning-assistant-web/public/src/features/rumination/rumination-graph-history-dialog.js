/**
 * 知识反刍模块 - 星系图谱变更历史记录与版本回滚下拉面板交互
 */

import { escapeHtml } from '/src/shared/text.js'

function formatDateTime(dateVal) {
  if (!dateVal) return ''
  const d = dateVal instanceof Date ? dateVal : new Date(dateVal)
  if (isNaN(d.getTime())) return String(dateVal)
  const pad = (n) => String(n).padStart(2, '0')
  const y = d.getFullYear()
  const m = pad(d.getMonth() + 1)
  const day = pad(d.getDate())
  const h = pad(d.getHours())
  const min = pad(d.getMinutes())
  const s = pad(d.getSeconds())
  return `${y}-${m}-${day} ${h}:${min}:${s}`
}

function formatTimeOnly(dateVal) {
  if (!dateVal) return '刚刚更新'
  const d = dateVal instanceof Date ? dateVal : new Date(dateVal)
  if (isNaN(d.getTime())) return '刚刚更新'
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export function createRuminationGraphHistoryDialog({
  api,
  store,
  toast = () => {},
  getElements = () => ({}),
  getActiveScenarioCode = () => '',
  getCurrentWordbookId = () => '',
  onGraphRestored = null,
} = {}) {
  let isPanelOpen = false
  let lastUpdateTimeValue = new Date()
  let inFlightRestore = false

  function updateLastSavedTime(time = new Date(), version = null) {
    lastUpdateTimeValue = time instanceof Date ? time : new Date(time)
    const elements = getElements()
    if (!elements?.lastUpdateTime) return
    const timeText = formatTimeOnly(lastUpdateTimeValue)
    elements.lastUpdateTime.textContent = timeText

    if (elements.historyBtn) {
      const fullTime = formatDateTime(lastUpdateTimeValue)
      const verText = version ? ` (v${version})` : ''
      elements.historyBtn.title = `最后更新：${fullTime}${verText}\n点击查看最近 30 次变更记录与版本回滚`
    }
  }

  function open() {
    const elements = getElements()
    if (!elements?.historyPopover) return
    isPanelOpen = true
    elements.historyPopover.classList.remove('hidden')
    elements.historyBtn?.classList.add('is-active')
    elements.historyBtn?.setAttribute('aria-expanded', 'true')
    loadHistoryList()
  }

  function close() {
    const elements = getElements()
    if (!elements?.historyPopover) return
    isPanelOpen = false
    elements.historyPopover.classList.add('hidden')
    elements.historyBtn?.classList.remove('is-active')
    elements.historyBtn?.setAttribute('aria-expanded', 'false')
  }

  function toggle() {
    if (isPanelOpen) {
      close()
    } else {
      open()
    }
  }

  async function loadHistoryList() {
    const elements = getElements()
    if (!elements?.historyList) return

    const wordbookId = getCurrentWordbookId()
    const scenarioCode = getActiveScenarioCode()
    if (!wordbookId || !scenarioCode) {
      elements.historyList.innerHTML = '<div class="history-empty-state"><span>请先选择有效词本与场景</span></div>'
      return
    }

    elements.historyList.innerHTML = '<div class="history-loading-state"><span>正在读取星系历史快照版本...</span></div>'

    try {
      if (!api?.listRuminationGraphHistory) {
        elements.historyList.innerHTML = '<div class="history-empty-state"><span>历史快照查询服务暂未就绪</span></div>'
        return
      }

      const list = await api.listRuminationGraphHistory(wordbookId, scenarioCode)
      if (!Array.isArray(list) || list.length === 0) {
        elements.historyList.innerHTML = '<div class="history-empty-state"><span>暂无历史快照版本记录</span></div>'
        return
      }

      const currentVersion = store?.getGraphData?.()?.version
      renderHistoryItems(list, currentVersion)
    } catch (err) {
      console.warn('获取星系图谱历史变更记录失败', err)
      elements.historyList.innerHTML = `<div class="history-empty-state"><span>获取历史记录失败：${escapeHtml(err?.message || '网络异常')}</span></div>`
    }
  }

  function renderHistoryItems(items, currentVersion) {
    const elements = getElements()
    if (!elements?.historyList) return

    const html = items.map((item) => {
      const isCurrent = typeof currentVersion === 'number' && Number(item.graphVersion) === Number(currentVersion)
      const timeStr = formatDateTime(item.createTime)
      const summary = item.changeSummary || '保存图谱变更'
      const nodeCount = typeof item.nodeCount === 'number' ? item.nodeCount : 0
      const edgeCount = typeof item.edgeCount === 'number' ? item.edgeCount : 0

      return `
        <div class="history-item ${isCurrent ? 'is-current' : ''}" role="listitem" data-history-id="${escapeHtml(item.id)}" data-version="${escapeHtml(item.graphVersion)}">
          <div class="history-item-main">
            <div class="history-item-head">
              <span class="history-version-badge">v${escapeHtml(item.graphVersion)}</span>
              <span class="history-item-time" title="${escapeHtml(timeStr)}">${escapeHtml(timeStr)}</span>
              ${isCurrent ? '<span class="history-current-tag">当前版本</span>' : ''}
            </div>
            <div class="history-item-summary" title="${escapeHtml(summary)}">
              ${escapeHtml(summary)}
            </div>
            <div class="history-item-meta">
              <span class="history-meta-stat">${nodeCount} 节点</span>
              <span class="history-meta-dot">·</span>
              <span class="history-meta-stat">${edgeCount} 关系</span>
            </div>
          </div>
          <div class="history-item-action">
            ${isCurrent
              ? '<span class="history-action-current">已生效</span>'
              : `<button type="button" class="history-rollback-btn" data-history-id="${escapeHtml(item.id)}" data-version="${escapeHtml(item.graphVersion)}" title="回滚恢复至版本 v${escapeHtml(item.graphVersion)}">回滚</button>`
            }
          </div>
        </div>
      `
    }).join('')

    elements.historyList.innerHTML = html

    // 绑定回滚按钮点击事件
    const rollbackBtns = elements.historyList.querySelectorAll('.history-rollback-btn')
    for (const btn of rollbackBtns) {
      btn.addEventListener('click', (e) => {
        e.stopPropagation()
        const historyId = btn.getAttribute('data-history-id')
        const version = btn.getAttribute('data-version')
        handleRollback(historyId, version, btn)
      })
    }
  }

  async function handleRollback(historyId, version, btn) {
    if (!historyId || inFlightRestore) return
    const wordbookId = getCurrentWordbookId()
    const scenarioCode = getActiveScenarioCode()
    if (!wordbookId || !scenarioCode) {
      toast('无法回滚：未找到当前词本或场景')
      return
    }

    inFlightRestore = true
    const originalText = btn.textContent
    btn.disabled = true
    btn.textContent = '回滚中...'

    try {
      if (!api?.restoreRuminationGraphHistory) {
        throw new Error('回滚接口暂不可用')
      }

      const restoredData = await api.restoreRuminationGraphHistory({
        wordbookId,
        scenarioCode,
        historyId,
      })

      if (restoredData && Array.isArray(restoredData.nodes)) {
        onGraphRestored?.(restoredData, version)
        updateLastSavedTime(new Date(), restoredData.version || version)
        toast(`已成功回滚至历史版本 v${version}`)
        await loadHistoryList()
      } else {
        throw new Error('服务端返回回滚数据格式异常')
      }
    } catch (err) {
      console.warn('回滚星系图谱历史版本失败', err)
      toast(err?.message || '版本回滚失败，请重试')
      btn.disabled = false
      btn.textContent = originalText
    } finally {
      inFlightRestore = false
    }
  }

  function handleDocumentClick(e) {
    if (!isPanelOpen) return
    const elements = getElements()
    const popover = elements?.historyPopover
    const triggerBtn = elements?.historyBtn
    if (!popover || !triggerBtn) return

    if (!popover.contains(e.target) && !triggerBtn.contains(e.target)) {
      close()
    }
  }

  function handleKeydown(e) {
    if (isPanelOpen && e.key === 'Escape') {
      close()
    }
  }

  // 绑定初始化事件
  function bindEvents() {
    const elements = getElements()
    if (elements.historyBtn) {
      elements.historyBtn.addEventListener('click', (e) => {
        e.stopPropagation()
        toggle()
      })
    }
    if (elements.historyCloseBtn) {
      elements.historyCloseBtn.addEventListener('click', (e) => {
        e.stopPropagation()
        close()
      })
    }
    if (elements.historyRefreshBtn) {
      elements.historyRefreshBtn.addEventListener('click', (e) => {
        e.stopPropagation()
        loadHistoryList()
      })
    }
    document.addEventListener('click', handleDocumentClick)
    document.addEventListener('keydown', handleKeydown)
  }

  bindEvents()

  return {
    open,
    close,
    toggle,
    updateLastSavedTime,
    refresh: loadHistoryList,
    isOpen: () => isPanelOpen,
    destroy() {
      document.removeEventListener('click', handleDocumentClick)
      document.removeEventListener('keydown', handleKeydown)
    },
  }
}
