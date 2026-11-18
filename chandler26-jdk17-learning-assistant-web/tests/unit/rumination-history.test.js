import { describe, it, expect, vi, beforeEach } from 'vitest'
import { cloneGraphSnapshot, createRuminationHistory } from '../../public/src/features/rumination/rumination-history.js'

describe('Rumination History & Undo/Redo Engine', () => {
  let mockStore
  let mockGraph
  let mockElements
  let mockToast
  let mockSaveDebounced

  beforeEach(() => {
    let currentGraph = {
      scenarioCode: 'test_scenario',
      nodes: [
        { id: 'hub', term: 'core', label: 'Core Hub', nodeType: 'hub', x: 0, y: 0, fixed: true, borderStyle: 'solid' },
        { id: 'node_1', term: 'apple', label: 'apple', definition: 'a fruit', nodeType: 'word', x: 100, y: 50, fixed: true, borderStyle: 'solid' },
      ],
      edges: [
        { source: 'hub', target: 'node_1', relationType: 'peer', label: '包含', lineStyle: 'curve' },
      ],
    }
    let bagWords = ['node_1']
    let activeFocus = 'node_1'

    mockStore = {
      getGraphData: vi.fn(() => currentGraph),
      setGraphData: vi.fn((data) => { currentGraph = data; return currentGraph }),
      getSelectedNodeIds: vi.fn(() => [...bagWords]),
      setSelectedNodeIds: vi.fn((words) => { bagWords = [...words] }),
      getActiveFocusNodeId: vi.fn(() => activeFocus),
      setActiveFocus: vi.fn((id) => { activeFocus = id }),
    }

    mockGraph = {
      setData: vi.fn(),
      setSelectedNodes: vi.fn(),
      setFocusedNode: vi.fn(),
    }

    mockElements = {
      undoBtn: { disabled: true, classList: { toggle: vi.fn() }, title: '' },
      redoBtn: { disabled: true, classList: { toggle: vi.fn() }, title: '' },
    }

    mockToast = vi.fn()
    mockSaveDebounced = vi.fn()
  })

  it('correctly clones graph snapshot into deep copy without engine properties', () => {
    const rawData = {
      nodes: [
        { id: 'n1', term: 'test', x: 10.5, y: -20.2, vx: 0.5, vy: -0.2, borderStyle: 'dashed' },
      ],
      edges: [
        { source: { id: 'hub' }, target: { id: 'n1' }, relationType: 'action', label: '作用', lineStyle: 'straight' },
      ],
    }
    const snapshot = cloneGraphSnapshot(rawData, ['n1'])
    expect(snapshot.nodes[0].id).toBe('n1')
    expect(snapshot.nodes[0].borderStyle).toBe('dashed')
    expect(snapshot.nodes[0].x).toBe(11) // Math.round
    expect(snapshot.nodes[0].y).toBe(-20)
    expect(snapshot.nodes[0].vx).toBeUndefined()
    expect(snapshot.edges[0].source).toBe('hub')
    expect(snapshot.edges[0].target).toBe('n1')
    expect(snapshot.edges[0].lineStyle).toBe('straight')
    expect(snapshot.bagWords).toEqual(['n1'])
  })

  it('initializes with empty stacks and disabled UI buttons', () => {
    const history = createRuminationHistory({
      store: mockStore,
      graph: mockGraph,
      elements: mockElements,
      toast: mockToast,
      saveCurrentGraphDebounced: mockSaveDebounced,
    })

    expect(history.canUndo()).toBe(false)
    expect(history.canRedo()).toBe(false)
    expect(mockElements.undoBtn.disabled).toBe(true)
    expect(mockElements.redoBtn.disabled).toBe(true)
  })

  it('records before action and successfully performs undo and redo', () => {
    const history = createRuminationHistory({
      store: mockStore,
      graph: mockGraph,
      elements: mockElements,
      toast: mockToast,
      saveCurrentGraphDebounced: mockSaveDebounced,
    })

    // 1. Record before modifying node
    history.recordBefore('修改边框线型')
    expect(history.canUndo()).toBe(true)
    expect(history.canRedo()).toBe(false)
    expect(mockElements.undoBtn.disabled).toBe(false)

    // Simulate mutation: change node_1 border to dashed
    const data = mockStore.getGraphData()
    data.nodes[1].borderStyle = 'dashed'

    // 2. Undo operation
    const undone = history.undo()
    expect(undone.description).toBe('修改边框线型')
    expect(mockToast).toHaveBeenCalledWith(expect.stringContaining('已撤销：修改边框线型'))
    expect(mockStore.setGraphData).toHaveBeenCalled()
    expect(mockGraph.setData).toHaveBeenCalled()
    expect(mockSaveDebounced).toHaveBeenCalledWith(true)

    // In undone state, borderStyle should be restored to solid
    const restored = mockStore.getGraphData()
    expect(restored.nodes[1].borderStyle).toBe('solid')
    expect(history.canUndo()).toBe(false)
    expect(history.canRedo()).toBe(true)
    expect(mockElements.redoBtn.disabled).toBe(false)

    // 3. Redo operation
    const redone = history.redo()
    expect(redone.description).toBe('修改边框线型')
    expect(mockToast).toHaveBeenCalledWith(expect.stringContaining('已重做：修改边框线型'))
    const redoneData = mockStore.getGraphData()
    expect(redoneData.nodes[1].borderStyle).toBe('dashed')
    expect(history.canUndo()).toBe(true)
    expect(history.canRedo()).toBe(false)
  })

  it('supports recordAction with preSnapshot (used for node drag)', () => {
    const history = createRuminationHistory({
      store: mockStore,
      graph: mockGraph,
      elements: mockElements,
      toast: mockToast,
      saveCurrentGraphDebounced: mockSaveDebounced,
    })

    // Capture pre-drag snapshot when mouse down
    const preDragSnapshot = history.getSnapshot()
    expect(preDragSnapshot.nodes[1].x).toBe(100)

    // Drag moves node to (250, 180)
    mockStore.getGraphData().nodes[1].x = 250
    mockStore.getGraphData().nodes[1].y = 180

    // On drag end, record action with preDragSnapshot
    history.recordAction('移动节点', preDragSnapshot)
    expect(history.canUndo()).toBe(true)

    // Undo should restore position back to (100, 50)
    history.undo()
    expect(mockStore.getGraphData().nodes[1].x).toBe(100)
    expect(mockStore.getGraphData().nodes[1].y).toBe(50)

    // Redo should restore moved position (250, 180)
    history.redo()
    expect(mockStore.getGraphData().nodes[1].x).toBe(250)
    expect(mockStore.getGraphData().nodes[1].y).toBe(180)
  })

  it('respects maxSteps and shifts older snapshots', () => {
    const history = createRuminationHistory({
      store: mockStore,
      graph: mockGraph,
      elements: mockElements,
      toast: mockToast,
      saveCurrentGraphDebounced: mockSaveDebounced,
      maxSteps: 3,
    })

    history.recordBefore('动作 1')
    history.recordBefore('动作 2')
    history.recordBefore('动作 3')
    history.recordBefore('动作 4')

    const counts = history.getStackCounts()
    expect(counts.undo).toBe(3) // limited to maxSteps 3
  })

  it('shows graceful toast notifications when undo/redo is unavailable', () => {
    const history = createRuminationHistory({
      store: mockStore,
      graph: mockGraph,
      elements: mockElements,
      toast: mockToast,
      saveCurrentGraphDebounced: mockSaveDebounced,
    })

    history.undo()
    expect(mockToast).toHaveBeenCalledWith('暂无可撤销的操作')

    history.redo()
    expect(mockToast).toHaveBeenCalledWith('暂无可重做的操作')
  })

  it('clears stacks when clear() is invoked (e.g. on scenario change)', () => {
    const history = createRuminationHistory({
      store: mockStore,
      graph: mockGraph,
      elements: mockElements,
      toast: mockToast,
      saveCurrentGraphDebounced: mockSaveDebounced,
    })

    history.recordBefore('动作 1')
    expect(history.canUndo()).toBe(true)

    history.clear()
    expect(history.canUndo()).toBe(false)
    expect(history.canRedo()).toBe(false)
    expect(mockElements.undoBtn.disabled).toBe(true)
  })
})
