import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createRuminationGraphHistoryDialog } from '../../public/src/features/rumination/rumination-graph-history-dialog.js'

describe('Rumination Graph History Dialog & Rollback', () => {
  let root
  let elements
  let mockApi
  let toastMessages
  let mockStore

  beforeEach(() => {
    root = document.createElement('div')
    root.innerHTML = `
      <div class="rumination-graph-header">
        <div class="graph-header-status-wrap">
          <span class="graph-header-save-status is-saved" id="graphSaveStatus">已保存</span>
          <button type="button" class="graph-header-history-btn" id="graphHistoryBtn" aria-haspopup="dialog" aria-expanded="false">
            <span class="history-clock-icon">🕒</span>
            <span class="history-time-text" id="graphLastUpdateTime">刚刚更新</span>
            <span class="history-dropdown-arrow">▾</span>
          </button>
          <div id="ruminationHistoryPopover" class="rumination-history-popover hidden" role="dialog">
            <div class="history-popover-header">
              <span class="history-popover-title">星系变更记录</span>
              <button type="button" class="history-popover-refresh-btn" id="ruminationHistoryRefreshBtn">⟲</button>
              <button type="button" class="history-popover-close-btn" id="ruminationHistoryCloseBtn">×</button>
            </div>
            <div class="history-popover-body" id="ruminationHistoryList"></div>
          </div>
        </div>
        <div class="rumination-graph-controls" id="ruminationGraphControls">
          <button type="button" id="graphZoomInBtn" class="graph-zoom-btn" title="放大" aria-label="放大">＋</button>
          <button type="button" id="graphZoomOutBtn" class="graph-zoom-btn" title="缩小" aria-label="缩小">－</button>
        </div>
      </div>
    `
    document.body.appendChild(root)

    elements = {
      saveStatus: root.querySelector('#graphSaveStatus'),
      historyBtn: root.querySelector('#graphHistoryBtn'),
      lastUpdateTime: root.querySelector('#graphLastUpdateTime'),
      historyPopover: root.querySelector('#ruminationHistoryPopover'),
      historyList: root.querySelector('#ruminationHistoryList'),
      historyRefreshBtn: root.querySelector('#ruminationHistoryRefreshBtn'),
      historyCloseBtn: root.querySelector('#ruminationHistoryCloseBtn'),
      zoomInBtn: root.querySelector('#graphZoomInBtn'),
      zoomOutBtn: root.querySelector('#graphZoomOutBtn'),
    }

    toastMessages = []
    mockStore = {
      getGraphData: vi.fn(() => ({
        version: 576,
        scenarioCode: 'produce_kitchen',
        nodes: [{ id: 'apple', term: 'apple' }],
        edges: [],
      })),
    }

    mockApi = {
      listRuminationGraphHistory: vi.fn(async () => [
        {
          id: 'hist-576',
          graphVersion: 576,
          nodeCount: 51,
          edgeCount: 50,
          changeSummary: '修改节点 strawberry',
          createTime: '2026-11-17T22:26:57',
        },
        {
          id: 'hist-575',
          graphVersion: 575,
          nodeCount: 50,
          edgeCount: 49,
          changeSummary: '修改节点 blueberry',
          createTime: '2026-11-17T22:25:59',
        },
      ]),
      restoreRuminationGraphHistory: vi.fn(async ({ historyId }) => ({
        version: 577,
        scenarioCode: 'produce_kitchen',
        nodes: [{ id: 'apple', term: 'apple' }, { id: 'blueberry', term: 'blueberry' }],
        edges: [],
        bagTerms: ['blueberry'],
      })),
    }
  })

  it('renders zoom in/out controls with icons only and proper labels', () => {
    expect(elements.zoomInBtn.textContent.trim()).toBe('＋')
    expect(elements.zoomInBtn.getAttribute('title')).toBe('放大')
    expect(elements.zoomOutBtn.textContent.trim()).toBe('－')
    expect(elements.zoomOutBtn.getAttribute('title')).toBe('缩小')
  })

  it('updates last saved time display and tooltip correctly', () => {
    const dialog = createRuminationGraphHistoryDialog({
      api: mockApi,
      store: mockStore,
      getElements: () => elements,
    })

    const testDate = new Date('2026-11-17T14:26:50')
    dialog.updateLastSavedTime(testDate, 576)

    expect(elements.lastUpdateTime.textContent).toBe('14:26:50')
    expect(elements.historyBtn.title).toContain('14:26:50')
    expect(elements.historyBtn.title).toContain('v576')

    dialog.destroy()
  })

  it('toggles history popover on click and closes on close button or Esc', async () => {
    const dialog = createRuminationGraphHistoryDialog({
      api: mockApi,
      store: mockStore,
      getElements: () => elements,
      getActiveScenarioCode: () => 'produce_kitchen',
      getCurrentWordbookId: () => 'wb-123',
    })

    expect(elements.historyPopover.classList.contains('hidden')).toBe(true)

    elements.historyBtn.click()
    expect(elements.historyPopover.classList.contains('hidden')).toBe(false)
    expect(elements.historyBtn.getAttribute('aria-expanded')).toBe('true')
    expect(mockApi.listRuminationGraphHistory).toHaveBeenCalledWith('wb-123', 'produce_kitchen')

    elements.historyCloseBtn.click()
    expect(elements.historyPopover.classList.contains('hidden')).toBe(true)
    expect(elements.historyBtn.getAttribute('aria-expanded')).toBe('false')

    elements.historyBtn.click()
    expect(elements.historyPopover.classList.contains('hidden')).toBe(false)

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(elements.historyPopover.classList.contains('hidden')).toBe(true)

    dialog.destroy()
  })

  it('renders history items with version badge, summary, and marks current version', async () => {
    const dialog = createRuminationGraphHistoryDialog({
      api: mockApi,
      store: mockStore,
      getElements: () => elements,
      getActiveScenarioCode: () => 'produce_kitchen',
      getCurrentWordbookId: () => 'wb-123',
    })

    dialog.open()
    await new Promise((resolve) => setTimeout(resolve, 10))

    const items = elements.historyList.querySelectorAll('.history-item')
    expect(items.length).toBe(2)

    // Current version item (576)
    expect(items[0].classList.contains('is-current')).toBe(true)
    expect(items[0].querySelector('.history-current-tag').textContent).toBe('当前版本')
    expect(items[0].querySelector('.history-action-current').textContent).toBe('已生效')
    expect(items[0].querySelector('.history-item-summary').textContent.trim()).toBe('修改节点 strawberry')

    // Historical version item (575) has rollback button
    expect(items[1].classList.contains('is-current')).toBe(false)
    const rollbackBtn = items[1].querySelector('.history-rollback-btn')
    expect(rollbackBtn).not.toBeNull()
    expect(rollbackBtn.textContent.trim()).toBe('回滚')
    expect(rollbackBtn.getAttribute('data-history-id')).toBe('hist-575')

    dialog.destroy()
  })

  it('executes rollback and notifies callback when rollback button is clicked', async () => {
    const onRestored = vi.fn()
    const dialog = createRuminationGraphHistoryDialog({
      api: mockApi,
      store: mockStore,
      toast: (msg) => toastMessages.push(msg),
      getElements: () => elements,
      getActiveScenarioCode: () => 'produce_kitchen',
      getCurrentWordbookId: () => 'wb-123',
      onGraphRestored: onRestored,
    })

    dialog.open()
    await new Promise((resolve) => setTimeout(resolve, 10))

    const rollbackBtn = elements.historyList.querySelectorAll('.history-rollback-btn')[0]
    expect(rollbackBtn).not.toBeNull()

    rollbackBtn.click()
    await new Promise((resolve) => setTimeout(resolve, 10))

    expect(mockApi.restoreRuminationGraphHistory).toHaveBeenCalledWith({
      wordbookId: 'wb-123',
      scenarioCode: 'produce_kitchen',
      historyId: 'hist-575',
    })

    expect(onRestored).toHaveBeenCalled()
    expect(toastMessages).toContain('已成功回滚至历史版本 v575')

    dialog.destroy()
  })
})
