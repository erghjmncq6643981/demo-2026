/**
 * 知识反刍星系图谱 - 极坐标多环同心轨道与 BFS 无碰撞防重叠布局算法（纯计算模块）
 */

export function formatConciseDefinition(def) {
  if (!def) return ''
  const clean = String(def).replace(/（[^）]*）/g, '').replace(/\([^)]*\)/g, '').trim()
  const parts = clean.split(/[；;,，/]/).map((s) => s.trim()).filter(Boolean)
  if (parts.length > 0) {
    if (parts.length >= 2 && `${parts[0]}/${parts[1]}`.length <= 7) {
      return `${parts[0]}/${parts[1]}`
    }
    return parts[0].slice(0, 7)
  }
  return clean.slice(0, 7)
}

export function computeNodeMetrics(ctx, n) {
  if (n.nodeType === 'hub') {
    const label = n.label || n.term || '场景核心'
    const sub = n.subLabel || n.definition
    ctx.font = 'bold 12px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
    const textW = ctx.measureText(label).width
    ctx.font = '10px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
    const subW = sub ? ctx.measureText(sub).width : 0
    const maxW = Math.max(textW, subW)
    const width = Math.max(maxW + 28, 104)
    const height = sub && sub !== label ? 38 : 30
    const cornerRadius = height / 2
    return { width, height, halfW: width / 2, halfH: height / 2, cornerRadius, radius: cornerRadius }
  }

  const isExpansion = n.nodeType === 'expansion'
  const conciseDef = formatConciseDefinition(n.definition)
  n.displayDefinition = conciseDef

  ctx.font = 'bold 13px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
  const termW = ctx.measureText(n.term || n.label || '').width
  ctx.font = '10px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
  const defW = ctx.measureText(conciseDef || '').width
  const textW = Math.max(termW, defW)

  const badgeW = isExpansion ? 22 : 0
  const width = Math.max(textW + 28 + badgeW, isExpansion ? 76 : 88)
  const height = isExpansion ? 34 : 40
  const cornerRadius = height / 2
  return { width, height, halfW: width / 2, halfH: height / 2, cornerRadius, radius: width / 2 }
}

function buildOrbitAssignments(coreCount) {
  if (coreCount <= 8) {
    return [{ radius: 185, count: coreCount, offset: 0, phase: -Math.PI / 2 }]
  }
  if (coreCount <= 18) {
    const inner = 7
    const outer = coreCount - inner
    return [
      { radius: 175, count: inner, offset: 0, phase: -Math.PI / 2 },
      { radius: 295, count: outer, offset: inner, phase: -Math.PI / 2 + Math.PI / outer },
    ]
  }
  const inner = 6
  const mid = 10
  const outer = coreCount - inner - mid
  return [
    { radius: 165, count: inner, offset: 0, phase: -Math.PI / 2 },
    { radius: 280, count: mid, offset: inner, phase: -Math.PI / 2 + Math.PI / mid },
    { radius: 400, count: outer, offset: inner + mid, phase: -Math.PI / 2 + Math.PI / outer },
  ]
}

export function computeGalaxyLayout(ctx, graphData) {
  const rawNodes = graphData?.nodes || []
  const rawEdges = graphData?.edges || []
  const nodeMap = new Map()
  const placedNodes = []

  const hub = rawNodes.find((n) => n.nodeType === 'hub') || { id: 'hub', nodeType: 'hub', label: '场景核心' }
  const coreNodes = rawNodes.filter((n) => n.nodeType === 'core')

  function overlapsAny(x, y, metrics) {
    const padX = 18
    const padY = 14
    for (const existing of placedNodes) {
      const minW = (metrics.halfW || 44) + (existing.halfW || 44) + padX
      const minH = (metrics.halfH || 18) + (existing.halfH || 18) + padY
      if (Math.abs(x - existing.x) < minW && Math.abs(y - existing.y) < minH) {
        return true
      }
    }
    return false
  }

  function resolveClearPosition(baseAngle, baseDist, metrics) {
    const angleOffsets = [0, 0.24, -0.24, 0.48, -0.48, 0.74, -0.74, 1.0, -1.0]
    for (let ring = 0; ring < 5; ring++) {
      const dist = baseDist + ring * 54
      for (const offset of angleOffsets) {
        const angle = baseAngle + offset
        const x = Math.cos(angle) * dist
        const y = Math.sin(angle) * dist
        if (!overlapsAny(x, y, metrics)) {
          return { x, y }
        }
      }
    }
    return {
      x: Math.cos(baseAngle) * (baseDist + 160),
      y: Math.sin(baseAngle) * (baseDist + 160),
    }
  }

  // 1. 恒星枢纽置于坐标原点
  const hubMetrics = computeNodeMetrics(ctx, hub)
  const hubNode = {
    ...hub,
    ...hubMetrics,
    x: 0,
    y: 0,
    vx: 0,
    vy: 0,
    mass: 8,
    level: 0,
  }
  placedNodes.push(hubNode)
  nodeMap.set(hubNode.id, hubNode)

  const orbitAssignments = buildOrbitAssignments(Math.max(1, coreNodes.length))

  // 2. 预置所有已保存坐标的非枢纽节点
  const hasSavedCoord = (node) =>
    typeof node?.x === 'number'
    && Number.isFinite(node.x)
    && typeof node?.y === 'number'
    && Number.isFinite(node.y)
    && (Boolean(node.fixed) || node.x !== 0 || node.y !== 0)

  for (const rawNode of rawNodes) {
    if (rawNode.nodeType === 'hub' || nodeMap.has(rawNode.id) || !hasSavedCoord(rawNode)) {
      continue
    }
    const metrics = computeNodeMetrics(ctx, rawNode)
    const savedObj = {
      ...rawNode,
      ...metrics,
      x: rawNode.x,
      y: rawNode.y,
      vx: 0,
      vy: 0,
      fixed: Boolean(rawNode.fixed || true),
      mass: rawNode.nodeType === 'core' ? 1.5 : 1,
      level: rawNode.nodeType === 'core' ? 1 : 2,
    }
    placedNodes.push(savedObj)
    nodeMap.set(savedObj.id, savedObj)
    if (savedObj.term) nodeMap.set(savedObj.term, savedObj)
  }

  // 3. 尚无坐标的核心词按同心行星轨道排布
  const unsavedCoreNodes = coreNodes.filter((cNode) => !nodeMap.has(cNode.id))
  unsavedCoreNodes.forEach((cNode, idx) => {
    const coreIdx = coreNodes.indexOf(cNode)
    const effectiveIdx = coreIdx >= 0 ? coreIdx : idx
    const orbit = orbitAssignments.find((o) => effectiveIdx >= o.offset && effectiveIdx < o.offset + o.count) || orbitAssignments[0]
    const localIdx = effectiveIdx - orbit.offset
    const coreAngle = (localIdx / Math.max(1, orbit.count)) * Math.PI * 2 + orbit.phase
    const metrics = computeNodeMetrics(ctx, cNode)
    const clearPos = resolveClearPosition(coreAngle, orbit.radius, metrics)

    const nodeObj = {
      ...cNode,
      ...metrics,
      x: clearPos.x,
      y: clearPos.y,
      vx: 0,
      vy: 0,
      fixed: Boolean(cNode.fixed),
      mass: 1.5,
      coreAngle,
      level: orbit.radius > 300 ? 3 : orbit.radius > 200 ? 2 : 1,
    }
    placedNodes.push(nodeObj)
    nodeMap.set(nodeObj.id, nodeObj)
    if (nodeObj.term) nodeMap.set(nodeObj.term, nodeObj)
  })

  // 4. BFS 沿父节点径向外侧扇形展开未放置的 4D 衍生/裂变节点
  let unplacedNodes = rawNodes.filter((n) => !nodeMap.has(n.id))
  while (unplacedNodes.length > 0) {
    const parentToChildren = new Map()
    const newlyPlacedIds = new Set()

    for (const node of unplacedNodes) {
      const edgeAsTarget = rawEdges.find(
        (e) => (e.target === node.id || e.target === node.term) && nodeMap.has(e.source)
      )
      const edgeAsSource = !edgeAsTarget
        ? rawEdges.find((e) => (e.source === node.id || e.source === node.term) && nodeMap.has(e.target))
        : null
      const edge = edgeAsTarget || edgeAsSource
      if (edge) {
        const parentKey = edgeAsTarget ? edge.source : edge.target
        const parentNode = nodeMap.get(parentKey)
        if (parentNode) {
          if (!parentToChildren.has(parentNode)) parentToChildren.set(parentNode, [])
          parentToChildren.get(parentNode).push({ node, edge })
          newlyPlacedIds.add(node.id)
        }
      }
    }

    if (parentToChildren.size > 0) {
      for (const [parent, childItems] of parentToChildren.entries()) {
        const k = childItems.length
        const pAngle = parent.x === 0 && parent.y === 0 ? 0 : Math.atan2(parent.y, parent.x)
        const pDist = Math.hypot(parent.x, parent.y)
        const childDist = Math.max(pDist + 125, 315)
        const stepAngle = k > 1 ? Math.max(0.32, Math.min(0.42, 1.35 / (k - 1))) : 0

        childItems.forEach(({ node: childNode }, cIdx) => {
          const fanOffset = k > 1 ? (cIdx - (k - 1) / 2) * stepAngle : 0
          const childAngle = pAngle + fanOffset
          const metrics = computeNodeMetrics(ctx, childNode)
          const clearPos = resolveClearPosition(childAngle, childDist, metrics)

          const placedObj = {
            ...childNode,
            ...metrics,
            x: clearPos.x,
            y: clearPos.y,
            vx: 0,
            vy: 0,
            fixed: Boolean(childNode.fixed),
            mass: 1,
            level: (parent.level || 1) + 1,
          }
          placedNodes.push(placedObj)
          nodeMap.set(placedObj.id, placedObj)
          if (placedObj.term) nodeMap.set(placedObj.term, placedObj)
        })
      }
      unplacedNodes = unplacedNodes.filter((n) => !newlyPlacedIds.has(n.id))
    } else {
      const orphanCount = unplacedNodes.length
      unplacedNodes.forEach((orphan, oIdx) => {
        const orphanAngle = (oIdx / orphanCount) * Math.PI * 2 + Math.PI / 4
        const metrics = computeNodeMetrics(ctx, orphan)
        const clearPos = resolveClearPosition(orphanAngle, 330, metrics)

        const placedObj = {
          ...orphan,
          ...metrics,
          x: clearPos.x,
          y: clearPos.y,
          vx: 0,
          vy: 0,
          fixed: Boolean(orphan.fixed),
          mass: 1,
          level: 2,
        }
        placedNodes.push(placedObj)
        nodeMap.set(placedObj.id, placedObj)
        if (placedObj.term) nodeMap.set(placedObj.term, placedObj)
      })
      break
    }
  }

  const edges = rawEdges
    .map((e) => {
      const s = nodeMap.get(e.source)
      const t = nodeMap.get(e.target)
      if (!s || !t) return null
      return { ...e, sourceNode: s, targetNode: t }
    })
    .filter(Boolean)

  return { placedNodes, edges, nodeMap }
}

/**
 * 计算自适应画布视口的最优缩放比例与平移偏移，使所有节点完整居中且避开顶部操作栏与底部图例。
 */
export function computeFitViewportTransform({ nodes, width, height }) {
  if (!width || !height || !Array.isArray(nodes) || nodes.length === 0) {
    return null
  }

  let minX = Infinity
  let maxX = -Infinity
  let minY = Infinity
  let maxY = -Infinity
  for (const n of nodes) {
    const hw = (n.width ? n.width / 2 : n.halfW) || 44
    const hh = (n.height ? n.height / 2 : n.halfH) || 20
    if (n.x - hw < minX) minX = n.x - hw
    if (n.x + hw > maxX) maxX = n.x + hw
    if (n.y - hh < minY) minY = n.y - hh
    if (n.y + hh > maxY) maxY = n.y + hh
  }

  const padX = Math.min(48, Math.max(24, width * 0.04))
  const padTop = height > 520 ? 64 : 46
  const padBottom = width < 560 ? 96 : (height > 520 ? 60 : 54)
  const availW = Math.max(120, width - padX * 2)
  const availH = Math.max(120, height - padTop - padBottom)
  const spanW = Math.max(1, maxX - minX)
  const spanH = Math.max(1, maxY - minY)
  const maxAutoScale = nodes.length <= 2 ? 1.0 : 1.28
  const scale = Math.max(0.42, Math.min(maxAutoScale, availW / spanW, availH / spanH))
  const worldCenterX = (minX + maxX) / 2
  const worldCenterY = (minY + maxY) / 2
  const screenCenterX = padX + availW / 2
  const screenCenterY = padTop + availH / 2

  return {
    scale,
    x: screenCenterX - worldCenterX * scale,
    y: screenCenterY - worldCenterY * scale,
  }
}

/**
 * 在世界坐标 (x, y) 处拾取命中的连线（支持线条投影距离与标签胶囊区域判定）。
 */
export function pickEdgeAt({ edges = [], x, y, scale = 1, tolerance = 14 }) {
  let bestEdge = null
  let bestDist = Infinity

  for (const edge of edges) {
    const s = edge.sourceNode
    const t = edge.targetNode
    if (!s || !t) continue

    const dx = t.x - s.x
    const dy = t.y - s.y
    const lenSq = dx * dx + dy * dy
    if (lenSq === 0) continue

    const isHubEdge = s.nodeType === 'hub' || t.nodeType === 'hub'
    const rawLabel = edge.label ? String(edge.label).trim() : ''
    const tag = !isHubEdge && rawLabel && rawLabel !== '核心' ? rawLabel : null
    const edgeDist = Math.hypot(dx, dy)

    if (tag && edgeDist > 50) {
      const mx = (s.x + t.x) / 2
      const my = (s.y + t.y) / 2
      const estimatedTagW = tag.length * 12 + 14
      if (Math.abs(x - mx) <= estimatedTagW / 2 + 4 && Math.abs(y - my) <= 12) {
        return edge
      }
    }

    const u = ((x - s.x) * dx + (y - s.y) * dy) / lenSq
    if (u < 0.05 || u > 0.95) continue

    const projX = s.x + u * dx
    const projY = s.y + u * dy
    const dist = Math.hypot(x - projX, y - projY)
    const screenDist = dist * scale
    if (screenDist <= tolerance && screenDist < bestDist) {
      bestDist = screenDist
      bestEdge = edge
    }
  }

  return bestEdge
}
