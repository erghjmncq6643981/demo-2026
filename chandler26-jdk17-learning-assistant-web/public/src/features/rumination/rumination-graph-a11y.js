/**
 * 知识反刍星系图谱 - 键盘与触屏交互适配器
 * 为图谱画布提供无鼠标右键依赖的方向导航、节点快捷操作与单指触控手势。
 */

function findDirectionalNeighbor(nodes, current, direction) {
  const candidates = nodes.filter((n) => n.id !== current.id && n.nodeType !== 'hub')
  if (!candidates.length) return null
  const dirVec = {
    ArrowLeft: { x: -1, y: 0 },
    ArrowRight: { x: 1, y: 0 },
    ArrowUp: { x: 0, y: -1 },
    ArrowDown: { x: 0, y: 1 },
  }[direction]
  if (!dirVec) return null

  const pickBest = (minCos) => {
    let best = null
    let bestScore = Infinity
    for (const cand of candidates) {
      const dx = cand.x - current.x
      const dy = cand.y - current.y
      const dist = Math.hypot(dx, dy)
      if (dist < 1) continue
      const dot = dx * dirVec.x + dy * dirVec.y
      if (dot <= 0 || dot / dist < minCos) continue
      const perp = Math.abs(dx * dirVec.y - dy * dirVec.x)
      const score = dist + perp * 1.35
      if (score < bestScore) {
        bestScore = score
        best = cand
      }
    }
    return best
  }

  return pickBest(0.45) || pickBest(0.05)
}

/**
 * 绑定图谱的键盘导航与触屏手势，并返回销毁清理函数。
 */
export function bindGraphA11yAndTouch(canvas, bridge) {
  let touchState = null
  let touchMenuTimer = null
  let lastTouchTap = { nodeId: null, time: 0 }

  function clearTouchTimers() {
    if (touchMenuTimer) {
      clearTimeout(touchMenuTimer)
      touchMenuTimer = null
    }
  }

  function onKeyDown(e) {
    if (e.metaKey || e.ctrlKey || e.altKey) return
    const nodes = bridge.getNodes()
    const nonHubNodes = nodes.filter((n) => n.nodeType !== 'hub')
    const focusedId = bridge.getFocusedNodeId()
    const activeNode = nodes.find((n) => n.id === focusedId) || null

    if (e.key === 'ArrowLeft' || e.key === 'ArrowRight' || e.key === 'ArrowUp' || e.key === 'ArrowDown') {
      if (!nonHubNodes.length) return
      e.preventDefault()
      const baseNode = activeNode && activeNode.nodeType !== 'hub' ? activeNode : null
      const nextNode = baseNode ? findDirectionalNeighbor(nodes, baseNode, e.key) || baseNode : nonHubNodes[0]
      if (nextNode) {
        bridge.focusAndSelectNode(nextNode)
      }
      return
    }

    if (e.key === '+' || e.key === '=') {
      e.preventDefault()
      bridge.zoomBy(1.2)
      return
    }
    if (e.key === '-' || e.key === '_') {
      e.preventDefault()
      bridge.zoomBy(0.83)
      return
    }
    if (e.key === '0') {
      e.preventDefault()
      bridge.resetViewport()
      return
    }

    const isMetaOrCtrl = Boolean(e.metaKey || e.ctrlKey)
    if (isMetaOrCtrl && (e.key === 'z' || e.key === 'Z')) {
      e.preventDefault()
      e.stopPropagation()
      if (e.shiftKey) {
        bridge.redo?.()
      } else {
        bridge.undo?.()
      }
      return
    }
    if (isMetaOrCtrl && (e.key === 'y' || e.key === 'Y')) {
      e.preventDefault()
      e.stopPropagation()
      bridge.redo?.()
      return
    }

    if (!activeNode) return

    if (e.key === ' ' || e.code === 'Space') {
      if (activeNode.nodeType === 'hub') return
      e.preventDefault()
      bridge.triggerAction('bag', activeNode)
    } else if (e.key === 'Enter') {
      if (activeNode.nodeType === 'hub') return
      e.preventDefault()
      bridge.pulseNode(activeNode.id)
      bridge.triggerAction('expand', activeNode)
    } else if (e.key === 'e' || e.key === 'E' || e.key === 'F2') {
      e.preventDefault()
      bridge.triggerAction('edit', activeNode)
    } else if ((e.key === 'Tab' && !e.shiftKey) || e.key === 'a' || e.key === 'A') {
      e.preventDefault()
      bridge.triggerAction('addChild', activeNode)
    } else if (e.key === 'Delete' || e.key === 'Backspace') {
      if (activeNode.nodeType === 'hub') return
      e.preventDefault()
      bridge.triggerAction('delete', activeNode)
    } else if (e.key === 'm' || e.key === 'M' || e.key === 'ContextMenu' || (e.shiftKey && e.key === 'F10')) {
      e.preventDefault()
      const pos = bridge.getNodeClientCoords(activeNode)
      bridge.openContextMenu(activeNode, { clientX: pos.x, clientY: pos.y })
    }
  }

  function onWindowKeyDown(e) {
    if (e.defaultPrevented) return
    if (e.target === canvas || canvas.contains?.(e.target)) return
    if (!document.body?.contains(canvas)) return
    const targetTag = e.target?.tagName ? e.target.tagName.toLowerCase() : ''
    if (['input', 'textarea', 'select', 'button'].includes(targetTag) || e.target?.isContentEditable) return
    if (document.querySelector?.('.modal-backdrop:not(.hidden)')) return
    const ruminationView = document.getElementById?.('ruminationView')
    if (ruminationView && !ruminationView.classList.contains('active')) return
    if (!bridge.getFocusedNodeId()) return
    onKeyDown(e)
  }

  function onTouchStart(e) {
    if (e.touches.length !== 1) {
      clearTouchTimers()
      touchState = null
      return
    }
    const touch = e.touches[0]
    const rect = canvas.getBoundingClientRect()
    const node = bridge.getNodeAt(touch.clientX - rect.left, touch.clientY - rect.top)
    clearTouchTimers()
    touchState = {
      startX: touch.clientX,
      startY: touch.clientY,
      lastX: touch.clientX,
      lastY: touch.clientY,
      node: node && node.nodeType !== 'hub' ? node : null,
      dragged: false,
      menuOpened: false,
    }
    if (touchState.node) {
      const targetNode = touchState.node
      touchMenuTimer = setTimeout(() => {
        if (!touchState || touchState.dragged) return
        touchState.menuOpened = true
        bridge.openContextMenu(targetNode, { clientX: touch.clientX, clientY: touch.clientY })
      }, 450)
    }
  }

  function onTouchMove(e) {
    if (!touchState || e.touches.length !== 1) return
    const touch = e.touches[0]
    const totalMove = Math.hypot(touch.clientX - touchState.startX, touch.clientY - touchState.startY)
    if (totalMove > 8) {
      clearTouchTimers()
      if (!touchState.dragged) {
        touchState.dragged = true
        if (touchState.node) bridge.freezePhysics()
      }
      e.preventDefault()
      if (touchState.node) {
        const rect = canvas.getBoundingClientRect()
        const world = bridge.screenToWorld(touch.clientX - rect.left, touch.clientY - rect.top)
        touchState.node.x = world.x
        touchState.node.y = world.y
        touchState.node.vx = 0
        touchState.node.vy = 0
        bridge.requestRedraw()
      } else {
        bridge.panBy(touch.clientX - touchState.lastX, touch.clientY - touchState.lastY)
      }
    }
    touchState.lastX = touch.clientX
    touchState.lastY = touch.clientY
  }

  function onTouchEnd(e) {
    clearTouchTimers()
    if (!touchState) return
    const state = touchState
    touchState = null
    if (state.menuOpened) return
    if (state.dragged && state.node) {
      state.node.fixed = true
      state.node.vx = 0
      state.node.vy = 0
      bridge.finishNodeDrag(state.node)
      return
    }
    if (!state.dragged && state.node) {
      e.preventDefault()
      const now = performance.now()
      const isDoubleTap = lastTouchTap.nodeId === state.node.id && now - lastTouchTap.time < 340
      lastTouchTap = { nodeId: state.node.id, time: now }
      if (isDoubleTap) {
        bridge.pulseNode(state.node.id)
        const nodeScreenPos = bridge.getNodeClientCoords(state.node)
        bridge.openNodeDblClick(state.node, { clientX: state.startX, clientY: state.startY, nodeScreenPos })
      } else {
        bridge.selectNode(state.node)
      }
    }
  }

  function onTouchCancel() {
    clearTouchTimers()
    touchState = null
  }

  if (canvas.style) {
    canvas.style.touchAction = 'none'
  }
  canvas.addEventListener('keydown', onKeyDown)
  window.addEventListener('keydown', onWindowKeyDown)
  canvas.addEventListener('touchstart', onTouchStart, { passive: true })
  canvas.addEventListener('touchmove', onTouchMove, { passive: false })
  canvas.addEventListener('touchend', onTouchEnd, { passive: false })
  canvas.addEventListener('touchcancel', onTouchCancel)

  return function unbindGraphA11yAndTouch() {
    clearTouchTimers()
    canvas.removeEventListener('keydown', onKeyDown)
    window.removeEventListener('keydown', onWindowKeyDown)
    canvas.removeEventListener('touchstart', onTouchStart)
    canvas.removeEventListener('touchmove', onTouchMove)
    canvas.removeEventListener('touchend', onTouchEnd)
    canvas.removeEventListener('touchcancel', onTouchCancel)
  }
}
