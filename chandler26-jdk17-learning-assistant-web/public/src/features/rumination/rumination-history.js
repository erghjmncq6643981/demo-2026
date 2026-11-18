/**
 * 知识反刍模块 - 历史记录栈（RuminationHistory）
 * 集中管理星系图谱的撤销 (Undo / ⌘Z) 与重做 (Redo / ⌘⇧Z / ⌘Y) 历史快照。
 * 支持节点移动、关联子节点、删除、编辑、边框线型、连线形态与智能整理等画布操作。
 */

export function cloneGraphSnapshot(graphData, bagWords = []) {
  if (!graphData) return null
  const nodes = (graphData.nodes || []).map((n) => ({
    id: n.id,
    term: n.term || n.id,
    label: n.label || n.term || n.id,
    subLabel: n.subLabel || '',
    definition: n.definition || '',
    nodeType: n.nodeType || 'word',
    dimension: n.dimension || 'scene',
    status: n.status || 'reviewing',
    x: typeof n.x === 'number' ? Math.round(n.x) : 0,
    y: typeof n.y === 'number' ? Math.round(n.y) : 0,
    fixed: Boolean(n.fixed),
    borderStyle: n.borderStyle || 'solid',
    collapsed: Boolean(n.collapsed),
    hasChildren: Boolean(n.hasChildren),
    treeDepth: n.treeDepth,
    layoutDirection: n.layoutDirection,
  }))

  const edges = (graphData.edges || []).map((e) => ({
    source: typeof e.source === 'object' ? (e.source?.id || e.source?.term) : e.source,
    target: typeof e.target === 'object' ? (e.target?.id || e.target?.term) : e.target,
    relationType: e.relationType || 'peer',
    label: e.label || '',
    lineStyle: e.lineStyle || 'curve',
  }))

  return {
    nodes: JSON.parse(JSON.stringify(nodes)),
    edges: JSON.parse(JSON.stringify(edges)),
    bagWords: Array.isArray(bagWords) ? [...bagWords] : [],
  }
}

export function createRuminationHistory({
  store,
  graph,
  elements = {},
  toast = () => {},
  saveCurrentGraphDebounced = () => {},
  maxSteps = 30,
} = {}) {
  const undoStack = []
  const redoStack = []
  let isApplying = false

  function getSnapshot() {
    const data = store?.getGraphData?.()
    const bag = store?.getSelectedNodeIds?.() || []
    return cloneGraphSnapshot(data, bag)
  }

  function updateUiButtons() {
    const canUndo = undoStack.length > 0
    const canRedo = redoStack.length > 0
    if (elements.undoBtn) {
      elements.undoBtn.disabled = !canUndo
      elements.undoBtn.classList.toggle('disabled', !canUndo)
      const lastUndoDesc = undoStack[undoStack.length - 1]?.description
      elements.undoBtn.title = canUndo
        ? `撤销：${lastUndoDesc} (⌘Z / Ctrl+Z)`
        : '撤销上一步操作 (⌘Z / Ctrl+Z)'
    }
    if (elements.redoBtn) {
      elements.redoBtn.disabled = !canRedo
      elements.redoBtn.classList.toggle('disabled', !canRedo)
      const lastRedoDesc = redoStack[redoStack.length - 1]?.description
      elements.redoBtn.title = canRedo
        ? `重做：${lastRedoDesc} (⌘⇧Z / Ctrl+Y)`
        : '重做上一步操作 (⌘⇧Z / Ctrl+Y)'
    }
  }

  function recordBefore(description = '操作') {
    if (isApplying) return
    const snapshot = getSnapshot()
    if (!snapshot) return
    undoStack.push({ description, snapshot })
    if (undoStack.length > maxSteps) {
      undoStack.shift()
    }
    redoStack.length = 0
    updateUiButtons()
  }

  function recordAction(description = '操作', preSnapshot = null) {
    if (isApplying) return
    const snapshot = preSnapshot || getSnapshot()
    if (!snapshot) return
    undoStack.push({ description, snapshot })
    if (undoStack.length > maxSteps) {
      undoStack.shift()
    }
    redoStack.length = 0
    updateUiButtons()
  }

  function apply(snapshot, direction, description) {
    if (!snapshot) return
    const curGraph = store?.getGraphData?.() || {}
    store?.setGraphData?.({
      ...curGraph,
      nodes: snapshot.nodes,
      edges: snapshot.edges,
    }, { syncBag: false })

    if (Array.isArray(snapshot.bagWords)) {
      store?.setSelectedNodeIds?.(snapshot.bagWords)
    }

    const currentFocusId = store?.getActiveFocusNodeId?.()
    if (currentFocusId && !snapshot.nodes.some((n) => n.id === currentFocusId || n.term === currentFocusId)) {
      store?.setActiveFocus?.(null)
    }

    graph?.setData?.(store.getGraphData())
    graph?.setSelectedNodes?.(store.getSelectedNodeIds())
    if (store?.getActiveFocusNodeId?.()) {
      graph?.setFocusedNode?.(store.getActiveFocusNodeId())
    }

    saveCurrentGraphDebounced?.(true)
    const label = description || '操作'
    toast(direction === 'undo' ? `↩ 已撤销：${label}` : `↪ 已重做：${label}`)
  }

  function undo() {
    if (undoStack.length === 0 || isApplying) {
      toast('暂无可撤销的操作')
      return null
    }
    isApplying = true
    try {
      const current = getSnapshot()
      const target = undoStack.pop()
      if (current) {
        redoStack.push({ description: target.description, snapshot: current })
      }
      apply(target.snapshot, 'undo', target.description)
      updateUiButtons()
      return target
    } finally {
      isApplying = false
    }
  }

  function redo() {
    if (redoStack.length === 0 || isApplying) {
      toast('暂无可重做的操作')
      return null
    }
    isApplying = true
    try {
      const current = getSnapshot()
      const target = redoStack.pop()
      if (current) {
        undoStack.push({ description: target.description, snapshot: current })
      }
      apply(target.snapshot, 'redo', target.description)
      updateUiButtons()
      return target
    } finally {
      isApplying = false
    }
  }

  function clear() {
    undoStack.length = 0
    redoStack.length = 0
    updateUiButtons()
  }

  elements.undoBtn?.addEventListener?.('click', () => undo())
  elements.redoBtn?.addEventListener?.('click', () => redo())

  updateUiButtons()

  return {
    getSnapshot,
    recordBefore,
    recordAction,
    undo,
    redo,
    clear,
    canUndo: () => undoStack.length > 0,
    canRedo: () => redoStack.length > 0,
    getStackCounts: () => ({ undo: undoStack.length, redo: redoStack.length }),
  }
}
