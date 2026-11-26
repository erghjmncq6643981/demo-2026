/**
 * 知识反刍模块 - 单向状态存储（RuminationStore）
 * 集中管理知识图谱（节点与连线）、词袋集合、焦点节点、当前场景与词本上下文。
 * 杜绝外部子模块直接篡改原生可变图对象。
 */

import { cleanupOrphanSynonymGroups } from '/src/features/rumination/rumination-synonym-groups.js'

function asArray(val) {
  return Array.isArray(val) ? val : []
}

export function createRuminationStore(initialConfig = {}) {
  let graphData = {
    version: 1,
    wordbookId: null,
    scenarioCode: 'produce_kitchen',
    nodes: [],
    edges: [],
    bagWords: [],
  }
  let selectedNodeIds = []
  let activeFocusNodeId = null
  let activeScenarioCode = initialConfig.activeScenarioCode || 'produce_kitchen'
  let currentWordbookId = initialConfig.currentWordbookId || null
  let currentWorkspaceTab = 'graph'
  const listeners = new Set()

  function notify(actionType, payload) {
    for (const listener of listeners) {
      try {
        listener(actionType, payload)
      } catch (err) {
        console.error(`[RuminationStore] Listener error for ${actionType}:`, err)
      }
    }
  }

  function getGraphData() {
    return graphData
  }

  function setGraphData(data, { syncBag = true } = {}) {
    if (!data || typeof data !== 'object') return graphData
    const nodes = asArray(data.nodes)
    const edges = asArray(data.edges || data.links)
    graphData = {
      ...data,
      nodes,
      edges,
    }
    if (syncBag && Array.isArray(data.bagWords)) {
      selectedNodeIds = [...data.bagWords]
    }
    notify('SET_GRAPH_DATA', graphData)
    return graphData
  }

  function findNode(idOrTerm) {
    if (!idOrTerm) return null
    return graphData.nodes.find((n) => n.id === idOrTerm || n.term === idOrTerm) || null
  }

  function findEdge(source, target) {
    if (!source || !target) return null
    return (
      graphData.edges.find(
        (e) => (e.source === source || e.sourceNode?.id === source) && (e.target === target || e.targetNode?.id === target),
      ) || null
    )
  }

  function addNode({ node, edge }) {
    if (!node || !node.id) return null
    graphData.nodes.push(node)
    if (edge && edge.source && edge.target) {
      graphData.edges.push(edge)
    }
    notify('ADD_NODE', { node, edge })
    return node
  }

  function updateNode(idOrTerm, updates) {
    const target = findNode(idOrTerm)
    if (!target || !updates) return null
    Object.assign(target, updates)
    notify('UPDATE_NODE', { target, updates })
    return target
  }

  function deleteNode(idOrTerm) {
    const target = findNode(idOrTerm)
    if (!target || target.nodeType === 'hub') return null

    const targetId = target.id
    const targetTerm = target.term || target.label || target.id

    graphData.nodes = graphData.nodes.filter((n) => n.id !== targetId && n.term !== targetId)
    graphData.edges = graphData.edges.filter(
      (e) => e.source !== targetId && e.target !== targetId && e.source !== targetTerm && e.target !== targetTerm,
    )
    cleanupOrphanSynonymGroups(graphData.nodes)

    if (activeFocusNodeId === targetId || activeFocusNodeId === targetTerm) {
      activeFocusNodeId = null
    }

    if (isNodeInBag(targetId, targetTerm)) {
      selectedNodeIds = selectedNodeIds.filter((id) => id !== targetId && id !== targetTerm)
    }

    notify('DELETE_NODE', { targetId, targetTerm })
    return target
  }

  function reparentNode(childId, newParentId, defaultRelType = 'peer') {
    if (!childId || !newParentId || childId === newParentId) return false
    const existingEdgeIndex = graphData.edges.findIndex(
      (e) => e.target === childId || (findNode(childId)?.term && e.target === findNode(childId)?.term),
    )
    if (existingEdgeIndex >= 0) {
      const oldEdge = graphData.edges[existingEdgeIndex]
      oldEdge.source = newParentId
      oldEdge.target = childId
      oldEdge.relationType = oldEdge.relationType || defaultRelType
      if (oldEdge.label === '核心') {
        oldEdge.label = ''
      }
    } else {
      graphData.edges.push({
        source: newParentId,
        target: childId,
        relationType: defaultRelType,
        label: '',
      })
    }
    notify('REPARENT_NODE', { childId, newParentId })
    return true
  }

  function updateEdgeLabel(source, target, label) {
    const rawEdge = graphData.edges.find(
      (e) => (e.source === source || e.sourceNode?.id === source) && (e.target === target || e.targetNode?.id === target),
    )
    const trimmed = (label || '').trim()
    const finalLabel = trimmed === '核心' ? '' : trimmed
    if (rawEdge) {
      rawEdge.label = finalLabel
      notify('UPDATE_EDGE_LABEL', { source, target, label: finalLabel })
      return true
    }
    return false
  }

  function updateNodePositions(positionsMap) {
    if (!positionsMap || typeof positionsMap !== 'object') return
    let changed = false
    for (const node of graphData.nodes) {
      const pos = positionsMap[node.id] || positionsMap[node.term]
      if (pos && (node.x !== pos.x || node.y !== pos.y)) {
        node.x = pos.x
        node.y = pos.y
        changed = true
      }
    }
    if (changed) {
      notify('UPDATE_POSITIONS', positionsMap)
    }
  }

  function getSelectedNodeIds() {
    return [...selectedNodeIds]
  }

  function setSelectedNodeIds(next) {
    selectedNodeIds = asArray(next)
    notify('SET_BAG_WORDS', selectedNodeIds)
  }

  function isNodeInBag(id, term) {
    return selectedNodeIds.includes(id) || (term && selectedNodeIds.includes(term))
  }

  function toggleBagNode(id, term) {
    const shouldAdd = !isNodeInBag(id, term)
    if (shouldAdd) {
      const identifier = term || id
      if (!selectedNodeIds.includes(identifier)) {
        selectedNodeIds.push(identifier)
      }
    } else {
      selectedNodeIds = selectedNodeIds.filter((item) => item !== id && item !== term)
    }
    notify('TOGGLE_BAG', { id, term, added: shouldAdd, bag: selectedNodeIds })
    return shouldAdd
  }

  function getActiveFocusNodeId() {
    return activeFocusNodeId
  }

  function setActiveFocus(id) {
    activeFocusNodeId = id || null
    notify('SET_ACTIVE_FOCUS', activeFocusNodeId)
  }

  function resolveFocusedNode() {
    if (!activeFocusNodeId) return null
    return findNode(activeFocusNodeId)
  }

  function getActiveScenarioCode() {
    return activeScenarioCode
  }

  function setActiveScenarioCode(code) {
    activeScenarioCode = code || 'produce_kitchen'
    notify('SET_SCENARIO', activeScenarioCode)
  }

  function getCurrentWordbookId() {
    return currentWordbookId
  }

  function setCurrentWordbookId(id) {
    currentWordbookId = id ? String(id) : null
    notify('SET_WORDBOOK', currentWordbookId)
  }

  function getWorkspaceTab() {
    return currentWorkspaceTab
  }

  function setWorkspaceTab(tab) {
    currentWorkspaceTab = tab || 'graph'
    notify('SET_WORKSPACE_TAB', currentWorkspaceTab)
  }

  function subscribe(listener) {
    if (typeof listener === 'function') {
      listeners.add(listener)
      return () => listeners.delete(listener)
    }
    return () => {}
  }

  return {
    getGraphData,
    setGraphData,
    findNode,
    findEdge,
    addNode,
    updateNode,
    deleteNode,
    reparentNode,
    updateEdgeLabel,
    updateNodePositions,
    getSelectedNodeIds,
    setSelectedNodeIds,
    isNodeInBag,
    toggleBagNode,
    getActiveFocusNodeId,
    setActiveFocus,
    resolveFocusedNode,
    getActiveScenarioCode,
    setActiveScenarioCode,
    getCurrentWordbookId,
    setCurrentWordbookId,
    getWorkspaceTab,
    setWorkspaceTab,
    subscribe,
  }
}
