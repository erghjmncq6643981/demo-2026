/**
 * 知识反刍 - 2D 暗夜星系力导向图谱引擎 (Vanilla HTML5 Canvas)
 * 纯原生 ES Module，零外部运行时依赖，支持高帧率力导向物理弹簧、视口拖拽缩放与多维语义连线渲染。
 */

import { computeGalaxyLayout, computeFitViewportTransform, pickEdgeAt, tidyUpGalaxyLayout, computeNodeCollapseTogglePoint } from '/src/features/rumination/rumination-graph-layout.js'
import { bindGraphA11yAndTouch } from '/src/features/rumination/rumination-graph-a11y.js'
import { prefersReducedMotion } from '/src/shared/celebration.js'
import { findDropTarget } from '/src/features/rumination/rumination-graph-drag.js'
import { drawGraphScene, RELATION_CONFIG } from '/src/features/rumination/rumination-graph-renderer.js'

export { RELATION_CONFIG }

export function createRuminationGraph(canvas, options = {}) {
  const ctx = canvas.getContext('2d')
  let width = 0
  let height = 0
  let dpr = window.devicePixelRatio || 1

  // 物理与状态数据
  let nodes = []
  let edges = []
  const nodeMap = new Map()
  let selectedNodeIds = new Set()
  const illuminatedNodeIds = new Set()
  let focusedNodeId = null

  // 视口变换与交互拖拽状态
  const transform = { x: 0, y: 0, scale: 1 }
  let hasFittedViewport = false, hasUserTransformed = false, isPanning = false, panStart = { x: 0, y: 0 }
  let draggedNode = null, potentialNode = null, hoveredNode = null, isDraggingNode = false, dragStartPos = { x: 0, y: 0 }
  let currentDropTarget = null, longPressTimer = null, lastTapState = { nodeId: null, time: 0 }

  // 动画循环与状态控制
  let animationFrameId = null, isSleeping = false, isPaused = false, isDestroyed = false
  let isPageHidden = typeof document !== 'undefined' ? Boolean(document.hidden) : false
  let reducedMotion = prefersReducedMotion(), alpha = 0.5, needsRender = true, pulseState = null

  // 事件回调
  const onNodeSelect = options.onNodeSelect || (() => {})
  const onNodeDblClick = options.onNodeDblClick || (() => {})
  const onNodeContextMenu = options.onNodeContextMenu || (() => {})
  const onBackgroundContextMenu = options.onBackgroundContextMenu || (() => {})
  const onNodeDragStart = options.onNodeDragStart || (() => {})
  const onNodeDragEnd = options.onNodeDragEnd || (() => {})
  const onNodeReparent = options.onNodeReparent || (() => {})
  const onNodeAction = options.onNodeAction || (() => {})
  const onEdgeDblClick = options.onEdgeDblClick || (() => {})
  const onEdgeSelect = options.onEdgeSelect || (() => {})
  const onEdgeDeselect = options.onEdgeDeselect || (() => {})
  const onUndo = options.onUndo || (() => {})
  const onRedo = options.onRedo || (() => {})
  let selectedEdge = null

  // 星尘粒子背景
  let stars = []
  let starSpread = { w: 0, h: 0 }
  function initStars() {
    stars = []
    const spreadW = Math.max(2400, width * 1.8)
    const spreadH = Math.max(1600, height * 1.8)
    starSpread = { w: spreadW, h: spreadH }
    const count = Math.min(90, Math.max(45, Math.round((spreadW * spreadH) / 64000)))
    for (let i = 0; i < count; i++) {
      stars.push({
        x: (Math.random() - 0.5) * spreadW,
        y: (Math.random() - 0.5) * spreadH,
        radius: Math.random() * 1.5 + 0.5,
        alpha: Math.random() * 0.5 + 0.2,
      })
    }
  }

  function fitToViewport({ redraw = true } = {}) {
    const { visibleNodes } = getVisibleElements()
    const targetNodes = visibleNodes.length > 0 ? visibleNodes : nodes
    const fitted = computeFitViewportTransform({ nodes: targetNodes, width, height })
    if (!fitted) {
      hasFittedViewport = false
      return false
    }
    transform.scale = fitted.scale
    transform.x = fitted.x
    transform.y = fitted.y
    hasFittedViewport = true
    if (redraw) {
      requestRedraw()
    }
    return true
  }

  function resetCamera() {
    hasUserTransformed = false
    hasFittedViewport = false
    if (!fitToViewport()) {
      transform.x = width / 2
      transform.y = height / 2
      transform.scale = 1
      requestRedraw()
    }
  }

  function resize() {
    const rect = canvas.getBoundingClientRect()
    if (!rect.width || !rect.height) return
    const nextWidth = rect.width
    const nextHeight = rect.height
    const nextDpr = window.devicePixelRatio || 1
    const prevWidth = width
    const prevHeight = height
    const sizeChanged = Math.abs(nextWidth - width) > 0.5 || Math.abs(nextHeight - height) > 0.5 || nextDpr !== dpr

    width = nextWidth
    height = nextHeight
    dpr = nextDpr
    canvas.width = Math.floor(width * dpr)
    canvas.height = Math.floor(height * dpr)
    ctx.setTransform(1, 0, 0, 1, 0, 0)
    ctx.scale(dpr, dpr)

    if (stars.length > 0 && (width * 1.5 > starSpread.w || height * 1.5 > starSpread.h)) {
      initStars()
    }

    if (nodes.length > 0 && (!hasFittedViewport || (!hasUserTransformed && sizeChanged))) {
      fitToViewport({ redraw: false })
    } else if (hasUserTransformed && prevWidth > 0 && prevHeight > 0 && sizeChanged) {
      transform.x += (width - prevWidth) / 2
      transform.y += (height - prevHeight) / 2
    } else if (transform.x === 0 && transform.y === 0) {
      transform.x = width / 2
      transform.y = height / 2
    }

    needsRender = true
    ensureLoopRunning()
  }

  function setGraphData(graphData) {
    const rawNodes = graphData.nodes || []
    if (focusedNodeId && !rawNodes.some((n) => n.id === focusedNodeId)) {
      focusedNodeId = null
    }

    const layout = computeGalaxyLayout(ctx, graphData)
    nodeMap.clear()
    layout.nodeMap.forEach((v, k) => nodeMap.set(k, v))
    nodes = layout.placedNodes
    edges = layout.edges

    if (selectedEdge) {
      const sId = selectedEdge.source || selectedEdge.sourceNode?.id
      const tId = selectedEdge.target || selectedEdge.targetNode?.id
      const sTerm = selectedEdge.sourceNode?.term
      const tTerm = selectedEdge.targetNode?.term
      selectedEdge = edges.find((e) =>
        (e.source === sId || (sTerm && e.source === sTerm))
        && (e.target === tId || (tTerm && e.target === tTerm))
      ) || null
    }

    if (!hasFittedViewport) {
      hasUserTransformed = false
    }

    initStars()
    const hasUnfixedNodes = nodes.some((n) => n.nodeType !== 'hub' && !n.fixed)
    if (hasUnfixedNodes && !reducedMotion) {
      // 仅对尚无固定坐标的初始/新增节点执行短暂避让退火，消除微小重叠后彻底静止
      alpha = 0.5
      isSleeping = false
    } else if (hasUnfixedNodes && reducedMotion) {
      // 减少动态效果：同步推演完退火布局并直接静止展示，避免多帧漂移
      alpha = 0.5
      isSleeping = false
      for (let step = 0; step < 40 && !isSleeping; step += 1) {
        updatePhysics()
      }
      freezePhysics()
    } else {
      freezePhysics()
    }

    if (!hasFittedViewport) {
      fitToViewport({ redraw: false })
    }
    requestRedraw()
  }

  function updatePhysics() {
    if (isSleeping) return

    // 模拟退火降温完成，彻底冻结所有运动，确保画面绝对清晰静止
    if (alpha < 0.005) {
      isSleeping = true
      for (const node of nodes) {
        node.vx = 0
        node.vy = 0
      }
      if (!hasUserTransformed) {
        fitToViewport({ redraw: false })
      }
      needsRender = true
      return
    }

    const kSpring = 0.03 * alpha
    const damping = 0.55

    // 1. 局部防重叠排斥力 (基于矩形 AABB 包围盒防重叠)
    for (let i = 0; i < nodes.length; i++) {
      const n1 = nodes[i]
      for (let j = i + 1; j < nodes.length; j++) {
        const n2 = nodes[j]
        const dx = n2.x - n1.x
        const dy = n2.y - n1.y
        const minW = (n1.halfW || 40) + (n2.halfW || 40) + 18
        const minH = (n1.halfH || 18) + (n2.halfH || 18) + 14
        if (Math.abs(dx) < minW && Math.abs(dy) < minH) {
          const overlapX = minW - Math.abs(dx)
          const overlapY = minH - Math.abs(dy)
          const signX = dx >= 0 ? 1 : -1
          const signY = dy >= 0 ? 1 : -1
          const fx = signX * overlapX * 0.15 * alpha
          const fy = signY * overlapY * 0.15 * alpha
          if (n1 !== draggedNode && n1.nodeType !== 'hub' && !n1.fixed) {
            n1.vx -= fx
            n1.vy -= fy
          }
          if (n2 !== draggedNode && n2.nodeType !== 'hub' && !n2.fixed) {
            n2.vx += fx
            n2.vy += fy
          }
        }
      }
    }

    // 2. 连线弹簧引力 (温和维持轨道间距)
    for (const edge of edges) {
      const s = edge.sourceNode
      const t = edge.targetNode
      const dx = t.x - s.x
      const dy = t.y - s.y
      const dist = Math.sqrt(dx * dx + dy * dy) || 1
      const isHubConn = s.nodeType === 'hub' || t.nodeType === 'hub'
      const targetLen = isHubConn ? 185 : (edge.relationType === 'peer' ? 150 : 135)
      const displacement = dist - targetLen
      const force = displacement * kSpring
      const fx = (dx / dist) * force
      const fy = (dy / dist) * force

      if (s !== draggedNode && s.nodeType !== 'hub' && !s.fixed) {
        s.vx += fx
        s.vy += fy
      }
      if (t !== draggedNode && t.nodeType !== 'hub' && !t.fixed) {
        t.vx += fx
        t.vy += fy
      }
    }

    // 3. 速度衰减与位移更新
    for (const node of nodes) {
      if (node === draggedNode || node.nodeType === 'hub' || node.fixed) continue

      node.vx *= damping
      node.vy *= damping

      const maxV = 2.5 * alpha
      node.vx = Math.max(-maxV, Math.min(maxV, node.vx))
      node.vy = Math.max(-maxV, Math.min(maxV, node.vy))

      node.x += node.vx
      node.y += node.vy
    }

    // 退火衰减 (约 35 帧内平滑收敛为零)
    alpha *= 0.90
    needsRender = true
  }

  function updateTreeHierarchy() {
    const childrenMap = new Map()
    for (const n of nodes) {
      childrenMap.set(n.id, [])
    }
    for (const e of edges) {
      const sId = e.sourceNode?.id || e.source
      const tId = e.targetNode?.id || e.target
      if (sId && tId && sId !== tId && childrenMap.has(sId)) {
        const list = childrenMap.get(sId)
        if (!list.includes(tId)) list.push(tId)
      }
    }
    for (const n of nodes) {
      const cList = childrenMap.get(n.id) || []
      n.hasChildren = cList.length > 0
      n.childCount = cList.length
    }
    return childrenMap
  }

  function getDescendants(nodeId, childrenMap) {
    const set = new Set()
    const queue = [nodeId]
    while (queue.length > 0) {
      const curr = queue.shift()
      const children = childrenMap.get(curr) || []
      for (const childId of children) {
        if (!set.has(childId)) {
          set.add(childId)
          queue.push(childId)
        }
      }
    }
    return set
  }

  function getVisibleElements() {
    const childrenMap = updateTreeHierarchy()
    const hiddenNodeIds = new Set()
    for (const n of nodes) {
      if (n.collapsed && n.hasChildren) {
        const desc = getDescendants(n.id, childrenMap)
        desc.forEach((id) => hiddenNodeIds.add(id))
      }
    }
    const visibleNodes = nodes.filter((n) => !hiddenNodeIds.has(n.id))
    const visibleEdges = edges.filter(
      (e) => !hiddenNodeIds.has(e.sourceNode?.id || e.source) && !hiddenNodeIds.has(e.targetNode?.id || e.target)
    )
    return { visibleNodes, visibleEdges, hiddenNodeIds, childrenMap }
  }

  function getCollapseToggleAt(sx, sy) {
    const { x, y } = screenToWorld(sx, sy)
    const { visibleNodes } = getVisibleElements()
    for (let i = visibleNodes.length - 1; i >= 0; i--) {
      const node = visibleNodes[i]
      if (!node.hasChildren || node.nodeType === 'hub') continue
      const toggle = computeNodeCollapseTogglePoint(node)
      const wx = node.x + toggle.x
      const wy = node.y + toggle.y
      if (Math.hypot(x - wx, y - wy) <= 13) {
        return node
      }
    }
    return null
  }

  function toggleCollapseNode(nodeId) {
    const target = nodeMap.get(nodeId)
    if (!target || !target.hasChildren) return
    target.collapsed = !target.collapsed
    const childrenMap = updateTreeHierarchy()
    const descCount = getDescendants(target.id, childrenMap).size
    if (typeof options.onToast === 'function') {
      if (target.collapsed) {
        options.onToast(`已折叠「${target.term || target.label}」分支 (${descCount} 个词汇)`)
      } else {
        options.onToast(`已展开「${target.term || target.label}」分支`)
      }
    }
    if (typeof options.onNodeCollapseToggle === 'function') {
      options.onNodeCollapseToggle(target, target.collapsed)
    }
    requestRedraw()
  }

  function render() {
    const { visibleNodes, visibleEdges } = getVisibleElements()
    drawGraphScene({
      ctx,
      width,
      height,
      transform,
      stars,
      nodes: visibleNodes,
      edges: visibleEdges,
      selectedNodeIds,
      focusedNodeId,
      hoveredNode,
      selectedEdge,
      illuminatedNodeIds,
      reducedMotion,
      pulseState,
      isDraggingNode,
      draggedNode,
      currentDropTarget,
      relationConfig: RELATION_CONFIG,
    })
  }

  function stopLoop() {
    if (animationFrameId !== null) {
      cancelAnimationFrame(animationFrameId)
      animationFrameId = null
    }
  }

  function ensureLoopRunning() {
    if (isDestroyed || isPaused || isPageHidden) return
    if (animationFrameId !== null) return
    if (!isSleeping || needsRender || (!reducedMotion && pulseState)) {
      animationFrameId = requestAnimationFrame(loop)
    }
  }

  function requestRedraw() {
    needsRender = true
    ensureLoopRunning()
  }

  function pulseNode(nodeId) {
    if (!nodeId || !nodeMap.has(nodeId)) return
    if (reducedMotion) {
      pulseState = null
      requestRedraw()
      return
    }
    pulseState = { nodeId, startedAt: performance.now(), duration: 480 }
    requestRedraw()
  }

  function loop() {
    animationFrameId = null
    if (isDestroyed || isPaused || isPageHidden) return
    updatePhysics()
    const hasActivePulse = Boolean(
      !reducedMotion && pulseState && performance.now() - pulseState.startedAt < pulseState.duration,
    )
    if (pulseState && !hasActivePulse) {
      pulseState = null
      needsRender = true
    }
    if (needsRender || !isSleeping || hasActivePulse) {
      render()
      if (isSleeping && !hasActivePulse) {
        needsRender = false
      }
    }
    // 休眠静止且无重绘需求时彻底停止 rAF 调度，避免空转
    if (!isSleeping || needsRender || hasActivePulse) {
      animationFrameId = requestAnimationFrame(loop)
    }
  }

  function pause() {
    isPaused = true
    stopLoop()
  }

  function resume() {
    isPaused = false
    requestRedraw()
  }

  function freezePhysics() {
    alpha = 0
    isSleeping = true
    for (const node of nodes) {
      node.vx = 0
      node.vy = 0
    }
    needsRender = true
  }

  // 坐标转换：屏幕像素 ➔ 画布世界坐标
  function screenToWorld(sx, sy) {
    return {
      x: (sx - transform.x) / transform.scale,
      y: (sy - transform.y) / transform.scale,
    }
  }

  function getNodeAt(sx, sy) {
    const { x, y } = screenToWorld(sx, sy)
    const { visibleNodes } = getVisibleElements()
    for (let i = visibleNodes.length - 1; i >= 0; i--) {
      const node = visibleNodes[i]
      const halfW = (node.width || 80) / 2 + 6
      const halfH = (node.height || 36) / 2 + 6
      if (
        x >= node.x - halfW &&
        x <= node.x + halfW &&
        y >= node.y - halfH &&
        y <= node.y + halfH
      ) {
        return node
      }
    }
    return null
  }

  function getEdgeAt(sx, sy) {
    const { x, y } = screenToWorld(sx, sy)
    const { visibleEdges } = getVisibleElements()
    return pickEdgeAt({ edges: visibleEdges, x, y, scale: transform.scale, tolerance: 14 })
  }

  // 交互事件监听
  function onPointerDown(e) {
    if (e.button === 2) {
      // 鼠标右键由 contextmenu 统一派发，屏蔽 mousedown 拖拽/视口移动
      return
    }
    // 阻止浏览器默认文本选择与拖动行为，避免松手事件丢失；同时显式聚焦画布以响应 Tab/A/Enter 等键盘快捷键
    e.preventDefault()
    if (typeof canvas.focus === 'function') {
      try {
        canvas.focus({ preventScroll: true })
      } catch {
        canvas.focus()
      }
    }

    const rect = canvas.getBoundingClientRect()
    const sx = e.clientX - rect.left
    const sy = e.clientY - rect.top

    // 优先判定是否点击了分支折叠/展开按钮 (+ / -)（方案 3）
    const collapseHit = getCollapseToggleAt(sx, sy)
    if (collapseHit) {
      toggleCollapseNode(collapseHit.id)
      return
    }

    const clickedNode = getNodeAt(sx, sy)

    if (clickedNode) {
      if (selectedEdge) {
        selectedEdge = null
        onEdgeDeselect()
        requestRedraw()
      }
      const now = performance.now()
      const isRapidSecondTap = lastTapState.nodeId === clickedNode.id && (now - lastTapState.time) < 340
      lastTapState = { nodeId: clickedNode.id, time: now }

      potentialNode = clickedNode
      dragStartPos = { x: e.clientX, y: e.clientY, isRapidSecondTap }
      isDraggingNode = false
      draggedNode = null

      if (longPressTimer) {
        clearTimeout(longPressTimer)
        longPressTimer = null
      }

      if (!isRapidSecondTap && clickedNode.nodeType !== 'hub') {
        longPressTimer = setTimeout(() => {
          if (potentialNode) {
            isDraggingNode = true
            draggedNode = potentialNode
            canvas.style.cursor = 'move'
            freezePhysics()
            onNodeDragStart(draggedNode)
          }
        }, 180)
      }
    } else {
      const clickedEdge = getEdgeAt(sx, sy)
      if (clickedEdge) {
        selectedEdge = clickedEdge
        focusedNodeId = null
        selectedNodeIds.clear()
        const s = clickedEdge.sourceNode
        const t = clickedEdge.targetNode
        const midWorld = { x: (s.x + t.x) / 2, y: (s.y + t.y) / 2 }
        const edgeMidScreenPos = {
          x: rect.left + transform.x + midWorld.x * transform.scale,
          y: rect.top + transform.y + midWorld.y * transform.scale,
        }
        onEdgeSelect(clickedEdge, { clientX: e.clientX, clientY: e.clientY, edgeMidScreenPos })
        requestRedraw()
      } else {
        if (selectedEdge) {
          selectedEdge = null
          onEdgeDeselect()
          requestRedraw()
        }
        isPanning = true
        panStart = { x: e.clientX - transform.x, y: e.clientY - transform.y }
        canvas.style.cursor = 'grabbing'
      }
    }
  }

  function onPointerMove(e) {
    const rect = canvas.getBoundingClientRect()
    const sx = e.clientX - rect.left
    const sy = e.clientY - rect.top

    if (potentialNode && !isDraggingNode && potentialNode.nodeType !== 'hub') {
      const moveThreshold = dragStartPos.isRapidSecondTap ? 14 : 6
      const moveDist = Math.hypot(e.clientX - dragStartPos.x, e.clientY - dragStartPos.y)
      if (moveDist > moveThreshold) {
        if (longPressTimer) {
          clearTimeout(longPressTimer)
          longPressTimer = null
        }
        isDraggingNode = true
        draggedNode = potentialNode
        canvas.style.cursor = 'move'
        freezePhysics()
        onNodeDragStart(draggedNode)
      }
    }

    if (isDraggingNode && draggedNode) {
      const world = screenToWorld(sx, sy)
      draggedNode.x = world.x
      draggedNode.y = world.y
      draggedNode.vx = 0
      draggedNode.vy = 0
      const nextDropTarget = findDropTarget(world.x, world.y, draggedNode, nodes, edges)
      if (nextDropTarget !== currentDropTarget) {
        currentDropTarget = nextDropTarget
      }
      requestRedraw()
    } else if (isPanning) {
      hasUserTransformed = true
      transform.x = e.clientX - panStart.x
      transform.y = e.clientY - panStart.y
      requestRedraw()
    } else {
      const prevHovered = hoveredNode
      hoveredNode = getNodeAt(sx, sy)
      const isOverEdge = !hoveredNode && Boolean(getEdgeAt(sx, sy))
      if (hoveredNode !== prevHovered || isOverEdge) {
        canvas.style.cursor = (hoveredNode || isOverEdge) ? 'pointer' : 'grab'
        requestRedraw()
      }
    }
  }

  function onPointerUp() {
    if (longPressTimer) {
      clearTimeout(longPressTimer)
      longPressTimer = null
    }

    if (isDraggingNode && draggedNode) {
      const dropTarget = currentDropTarget
      currentDropTarget = null
      draggedNode.fixed = true
      draggedNode.vx = 0
      draggedNode.vy = 0
      if (dropTarget) {
        onNodeReparent(draggedNode, dropTarget)
      } else {
        onNodeDragEnd(draggedNode, { x: draggedNode.x, y: draggedNode.y })
      }
      draggedNode = null
      isDraggingNode = false
      potentialNode = null
      canvas.style.cursor = 'grab'
      requestRedraw()
    } else if (potentialNode) {
      focusedNodeId = potentialNode.id
      onNodeSelect(potentialNode)
      potentialNode = null
      draggedNode = null
      isDraggingNode = false
      canvas.style.cursor = 'pointer'
      requestRedraw()
    }

    isPanning = false
    if (!hoveredNode) {
      canvas.style.cursor = 'grab'
    }
  }

  function onWheel(e) {
    e.preventDefault()
    hasUserTransformed = true
    const isZoom = e.metaKey || e.ctrlKey
    if (isZoom) {
      // 按住 Command 或 Ctrl 键：以鼠标位置为中心进行缩放
      const rect = canvas.getBoundingClientRect()
      const sx = e.clientX - rect.left
      const sy = e.clientY - rect.top

      const zoomFactor = e.deltaY < 0 ? 1.12 : 0.89
      const newScale = Math.max(0.4, Math.min(2.5, transform.scale * zoomFactor))

      transform.x = sx - (sx - transform.x) * (newScale / transform.scale)
      transform.y = sy - (sy - transform.y) * (newScale / transform.scale)
      transform.scale = newScale
    } else {
      // 未按住 Command 键：上下滚动星系图谱 (视口平移)
      transform.y -= e.deltaY
      if (Math.abs(e.deltaX) > 0) {
        transform.x -= e.deltaX
      }
    }
    requestRedraw()
  }

  function onContextMenu(e) {
    e.preventDefault()
    const rect = canvas.getBoundingClientRect()
    const sx = e.clientX - rect.left
    const sy = e.clientY - rect.top
    const clickedNode = getNodeAt(sx, sy)
    if (clickedNode) {
      onNodeContextMenu(clickedNode, { clientX: e.clientX, clientY: e.clientY })
    } else {
      onBackgroundContextMenu({ clientX: e.clientX, clientY: e.clientY })
    }
  }

  function getNodeClientCoords(node) {
    const rect = canvas.getBoundingClientRect()
    return {
      x: rect.left + transform.x + node.x * transform.scale,
      y: rect.top + transform.y + node.y * transform.scale,
    }
  }

  function ensureNodeInView(node) {
    if (!node || !width || !height) return
    const sx = transform.x + node.x * transform.scale
    const sy = transform.y + node.y * transform.scale
    const padX = 72
    const padY = 56
    if (sx < padX) transform.x += padX - sx
    else if (sx > width - padX) transform.x -= sx - (width - padX)
    if (sy < padY) transform.y += padY - sy
    else if (sy > height - padY) transform.y -= sy - (height - padY)
  }

  function onDblClick(e) {
    if (e.button !== 0) return
    if (longPressTimer) {
      clearTimeout(longPressTimer)
      longPressTimer = null
    }
    isDraggingNode = false
    draggedNode = null
    potentialNode = null

    const rect = canvas.getBoundingClientRect()
    const sx = e.clientX - rect.left
    const sy = e.clientY - rect.top
    const clickedNode = getNodeAt(sx, sy)
    if (clickedNode && clickedNode.nodeType !== 'hub') {
      pulseNode(clickedNode.id)
      const nodeScreenPos = getNodeClientCoords(clickedNode)
      onNodeDblClick(clickedNode, {
        clientX: e.clientX,
        clientY: e.clientY,
        nodeScreenPos,
      })
    } else if (!clickedNode) {
      const clickedEdge = getEdgeAt(sx, sy)
      if (clickedEdge) {
        const s = clickedEdge.sourceNode
        const t = clickedEdge.targetNode
        const midWorld = { x: (s.x + t.x) / 2, y: (s.y + t.y) / 2 }
        const edgeMidScreenPos = {
          x: rect.left + transform.x + midWorld.x * transform.scale,
          y: rect.top + transform.y + midWorld.y * transform.scale,
        }
        onEdgeDblClick(clickedEdge, {
          clientX: e.clientX,
          clientY: e.clientY,
          edgeMidScreenPos,
        })
      }
    }
  }

  function onVisibilityChange() {
    isPageHidden = Boolean(document.hidden)
    if (isPageHidden) {
      stopLoop()
    } else if (!isPaused) {
      requestRedraw()
    }
  }

  const motionQuery = typeof window.matchMedia === 'function' ? window.matchMedia('(prefers-reduced-motion: reduce)') : null
  function onMotionPreferenceChange(event) {
    reducedMotion = Boolean(event.matches)
    if (reducedMotion) {
      pulseState = null
      freezePhysics()
    }
    requestRedraw()
  }

  const unbindA11yAndTouch = bindGraphA11yAndTouch(canvas, {
    getNodes: () => nodes,
    getFocusedNodeId: () => focusedNodeId,
    getNodeAt,
    getNodeClientCoords,
    screenToWorld,
    freezePhysics,
    requestRedraw,
    pulseNode,
    selectNode: (node) => onNodeSelect(node),
    focusAndSelectNode: (node) => {
      focusedNodeId = node.id
      ensureNodeInView(node)
      onNodeSelect(node)
      requestRedraw()
    },
    zoomBy: (factor) => {
      hasUserTransformed = true
      transform.scale = Math.max(0.4, Math.min(2.5, transform.scale * factor))
      requestRedraw()
    },
    panBy: (dx, dy) => {
      hasUserTransformed = true
      transform.x += dx
      transform.y += dy
      requestRedraw()
    },
    resetViewport: resetCamera,
    triggerAction: (action, node) => onNodeAction(action, node),
    openContextMenu: (node, coords) => onNodeContextMenu(node, coords),
    openNodeDblClick: (node, payload) => onNodeDblClick(node, payload),
    finishNodeDrag: (node) => {
      onNodeDragEnd(node, { x: node.x, y: node.y })
      requestRedraw()
    },
    undo: () => onUndo(),
    redo: () => onRedo(),
  })

  // 绑定事件
  canvas.addEventListener('mousedown', onPointerDown)
  canvas.addEventListener('dblclick', onDblClick)
  canvas.addEventListener('contextmenu', onContextMenu)
  window.addEventListener('mousemove', onPointerMove)
  window.addEventListener('mouseup', onPointerUp)
  canvas.addEventListener('wheel', onWheel, { passive: false })
  window.addEventListener('resize', resize)
  document.addEventListener('visibilitychange', onVisibilityChange)
  if (motionQuery?.addEventListener) {
    motionQuery.addEventListener('change', onMotionPreferenceChange)
  }

  const resizeObserver = new ResizeObserver(() => {
    resize()
  })
  resizeObserver.observe(canvas)

  // 启动与初始尺寸
  resize()
  ensureLoopRunning()

  return {
    setData(data, options = {}) {
      if (options.isNewScenario) {
        hasFittedViewport = false
        hasUserTransformed = false
      }
      setGraphData(data)
    },
    toggleSelectNode(nodeId) {
      if (selectedNodeIds.has(nodeId)) selectedNodeIds.delete(nodeId)
      else selectedNodeIds.add(nodeId)
      requestRedraw()
    },
    setSelectedNodes(ids) { selectedNodeIds = new Set(ids); requestRedraw() },
    setFocusedNode(nodeId) { focusedNodeId = nodeId && nodeMap.has(nodeId) ? nodeId : null; requestRedraw() },
    illuminateNode(nodeId) { illuminatedNodeIds.add(nodeId); requestRedraw() },
    pulseNode,
    resize,
    pause,
    resume,
    isLoopActive: () => animationFrameId !== null,
    resetView: resetCamera,
    zoomIn() {
      hasUserTransformed = true
      transform.scale = Math.min(2.5, transform.scale * 1.2)
      requestRedraw()
    },
    zoomOut() {
      hasUserTransformed = true
      transform.scale = Math.max(0.4, transform.scale * 0.83)
      requestRedraw()
    },
    getNodes: () => nodes,
    getEdges: () => edges,
    getSelectedEdge: () => selectedEdge,
    setSelectedEdge(edge) {
      selectedEdge = edge
      requestRedraw()
    },
    tidyLayout() {
      const cleanData = {
        nodes: nodes.map((n) => ({
          ...n,
          fixed: n.nodeType === 'hub',
          x: n.nodeType === 'hub' ? 0 : undefined,
          y: n.nodeType === 'hub' ? 0 : undefined,
        })),
        edges,
      }
      const layout = tidyUpGalaxyLayout(ctx, cleanData)
      nodeMap.clear()
      layout.nodes.forEach((n) => {
        nodeMap.set(n.id, n)
        if (n.term) nodeMap.set(n.term, n)
      })
      nodes = layout.nodes
      edges = layout.edges
      for (const n of nodes) {
        n.fixed = true
        n.vx = 0
        n.vy = 0
      }
      hasFittedViewport = false
      hasUserTransformed = false
      fitToViewport({ redraw: false })
      freezePhysics()
      requestRedraw()
      return nodes
    },
    toggleCollapse: toggleCollapseNode,
    expandAll() {
      nodes.forEach((n) => { n.collapsed = false })
      requestRedraw()
    },
    collapseAll() {
      nodes.forEach((n) => {
        if (n.hasChildren && n.nodeType !== 'hub') n.collapsed = true
      })
      requestRedraw()
    },
    isNodeCollapsed: (nodeId) => Boolean(nodeMap.get(nodeId)?.collapsed),
    getTransform: () => ({ ...transform }),
    triggerSelectNode(nodeId) {
      const target = nodeMap.get(nodeId)
      if (target) onNodeSelect(target)
    },
    destroy() {
      isDestroyed = true
      stopLoop()
      unbindA11yAndTouch()
      if (longPressTimer) {
        clearTimeout(longPressTimer)
        longPressTimer = null
      }
      resizeObserver.disconnect()
      canvas.removeEventListener('mousedown', onPointerDown)
      canvas.removeEventListener('dblclick', onDblClick)
      canvas.removeEventListener('contextmenu', onContextMenu)
      window.removeEventListener('mousemove', onPointerMove)
      window.removeEventListener('mouseup', onPointerUp)
      canvas.removeEventListener('wheel', onWheel)
      window.removeEventListener('resize', resize)
      document.removeEventListener('visibilitychange', onVisibilityChange)
      if (motionQuery?.removeEventListener) {
        motionQuery.removeEventListener('change', onMotionPreferenceChange)
      }
    },
  }
}
