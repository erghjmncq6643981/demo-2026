/**
 * 知识反刍星系图谱 - 极坐标多环同心轨道与 BFS 无碰撞防重叠布局算法（纯计算模块）
 */
export { RELATION_CONFIG } from './rumination-graph-renderer.js'
import { buildSynonymGroupBoxMap, computeSynonymGroups, isInternalSynonymEdge, isSynonymClusterCompact, measureSynonymCluster, packSynonymGroupMembers, sortSynonymMembersForPacking } from './rumination-synonym-groups.js'

const hasSavedCoord = (node) =>
  typeof node?.x === 'number' && Number.isFinite(node.x)
  && typeof node?.y === 'number' && Number.isFinite(node.y)
  && (Boolean(node.fixed) || node.x !== 0 || node.y !== 0)

function prepareSynonymClusters(ctx, rawNodes = [], rawEdges = []) {
  const byGroup = new Map()
  for (const n of rawNodes) {
    if (!n || n.nodeType === 'hub' || !n.synonymGroupId) continue
    if (!byGroup.has(n.synonymGroupId)) byGroup.set(n.synonymGroupId, [])
    byGroup.get(n.synonymGroupId).push(n)
  }
  const incomingNonSynTargets = new Set(
    rawEdges.filter((e) => e.relationType !== 'synonym').map((e) => e.target)
  )
  const clusters = []
  const satelliteIds = new Set()
  const anchorByMemberId = new Map()
  const clusterMetricsByAnchorId = new Map()
  const memberMetricsById = new Map()

  for (const [groupId, members] of byGroup.entries()) {
    if (members.length < 2) continue
    const anchor = members.find((m) => incomingNonSynTargets.has(m.id) || (m.term && incomingNonSynTargets.has(m.term)))
      || members.find((m) => m.nodeType === 'core')
      || members[0]
    for (const m of members) {
      memberMetricsById.set(m.id, computeNodeMetrics(ctx, m))
      anchorByMemberId.set(m.id, anchor)
      if (m.id !== anchor.id) satelliteIds.add(m.id)
    }
    const clusterMetrics = measureSynonymCluster(members, (m) => memberMetricsById.get(m.id))
    clusterMetricsByAnchorId.set(anchor.id, clusterMetrics)
    clusters.push({ groupId, anchor, members, clusterMetrics, allFixed: members.every(hasSavedCoord) })
  }
  return { clusters, satelliteIds, anchorByMemberId, clusterMetricsByAnchorId, memberMetricsById }
}

function unpackSynonymClusters(placedNodes, nodeMap, clusterInfo, { preserveAllFixed = false } = {}) {
  const { clusters, memberMetricsById } = clusterInfo
  for (const c of clusters) {
    const anchorLive = nodeMap.get(c.anchor.id) || c.anchor
    const liveMembers = c.members.map((m) => {
      const live = nodeMap.get(m.id)
      const indMetrics = memberMetricsById.get(m.id)
      const obj = live ? Object.assign(live, indMetrics) : { ...m, ...indMetrics, vx: 0, vy: 0, mass: 1, level: anchorLive.level || 2 }
      if (!live) {
        placedNodes.push(obj)
        nodeMap.set(obj.id, obj)
        if (obj.term) nodeMap.set(obj.term, obj)
      }
      return obj
    })
    if (preserveAllFixed && c.allFixed) {
      const ordered = sortSynonymMembersForPacking(liveMembers)
      const curGroup = computeSynonymGroups(ordered)[0]
      if (!curGroup || isSynonymClusterCompact(ordered, (m) => memberMetricsById.get(m.id), { x: curGroup.centerX, y: curGroup.centerY })) {
        continue
      }
      packSynonymGroupMembers(ordered, { x: curGroup.centerX, y: curGroup.centerY }, (m) => memberMetricsById.get(m.id))
      continue
    }
    packSynonymGroupMembers(liveMembers, { x: anchorLive.x || 0, y: anchorLive.y || 0 }, (m) => memberMetricsById.get(m.id))
  }
}

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
    const textW = ctx?.measureText ? ctx.measureText(label).width : 0
    ctx.font = '10px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
    const subW = sub && ctx?.measureText ? ctx.measureText(sub).width : 0
    const maxW = Math.max(textW, subW)
    const calculatedW = Math.max(maxW + 32, 110)
    const width = typeof n.width === 'number' && n.width > 0 ? n.width : calculatedW
    const height = typeof n.height === 'number' && n.height > 0 ? n.height : 42
    const cornerRadius = 12
    return { width, height, halfW: width / 2, halfH: height / 2, cornerRadius, radius: cornerRadius }
  }

  const isExpansion = n.nodeType === 'expansion'
  const conciseDef = formatConciseDefinition(n.definition)
  n.displayDefinition = conciseDef

  ctx.font = 'bold 12.5px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
  const termW = ctx?.measureText ? ctx.measureText(n.term || n.label || '').width : 0
  ctx.font = '10px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
  const defW = conciseDef && ctx?.measureText ? ctx.measureText(conciseDef || '').width : 0
  const textW = Math.max(termW, defW)

  const badgeW = isExpansion ? 18 : 0
  const calculatedW = Math.max(textW + 28 + badgeW, isExpansion ? 82 : 92)
  const width = typeof n.width === 'number' && n.width > 0 ? n.width : calculatedW
  const height = typeof n.height === 'number' && n.height > 0 ? n.height : 38
  const cornerRadius = 9
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
  const clusterInfo = prepareSynonymClusters(ctx, rawNodes, rawEdges)
  const getLayoutMetrics = (n) => clusterInfo.clusterMetricsByAnchorId.get(n.id) || computeNodeMetrics(ctx, n)

  const hub = rawNodes.find((n) => n.nodeType === 'hub') || { id: 'hub', nodeType: 'hub', label: '场景核心' }
  const coreNodes = rawNodes.filter((n) => n.nodeType === 'core' && !clusterInfo.satelliteIds.has(n.id))

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
    const metrics = getLayoutMetrics(cNode)
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
  let unplacedNodes = rawNodes.filter((n) => !nodeMap.has(n.id) && !clusterInfo.satelliteIds.has(n.id))
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

  unpackSynonymClusters(placedNodes, nodeMap, clusterInfo, { preserveAllFixed: true })

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
 * 计算连线起点、终点在卡片边界上的交点，及平滑贝塞尔曲线控制点与切线角度。
 */
export function computeEdgeRouting(s, t, edge = {}) {
  const dx = t.x - s.x
  const dy = t.y - s.y
  const angle = Math.atan2(dy, dx)

  const sw = (s.width || 88) / 2
  const sh = (s.height || 38) / 2
  let startX = s.x
  let startY = s.y
  if (Math.abs(dx) > 1e-3 || Math.abs(dy) > 1e-3) {
    const sTan = Math.abs(Math.tan(angle))
    if (sTan <= sh / sw) {
      startX = s.x + Math.sign(dx) * sw
      startY = s.y + Math.sign(dx) * sw * Math.tan(angle)
    } else {
      startY = s.y + Math.sign(dy) * sh
      startX = s.x + (Math.sign(dy) * sh) / Math.tan(angle)
    }
  }

  const tw = (t.width || 88) / 2 + 2
  const th = (t.height || 38) / 2 + 2
  let endX = t.x
  let endY = t.y
  if (Math.abs(dx) > 1e-3 || Math.abs(dy) > 1e-3) {
    const tTan = Math.abs(Math.tan(angle))
    if (tTan <= th / tw) {
      endX = t.x - Math.sign(dx) * tw
      endY = t.y - Math.sign(dx) * tw * Math.tan(angle)
    } else {
      endY = t.y - Math.sign(dy) * th
      endX = t.x - (Math.sign(dy) * th) / Math.tan(angle)
    }
  }

  const isStraight = edge?.lineStyle === 'straight' || edge?.isCurved === false
  if (isStraight) {
    return {
      isCurved: false,
      startX,
      startY,
      endX,
      endY,
      midX: (startX + endX) / 2,
      midY: (startY + endY) / 2,
      endAngle: angle,
    }
  }

  const cdx = endX - startX
  const cdy = endY - startY
  let cp1X, cp1Y, cp2X, cp2Y
  if (Math.abs(cdx) >= Math.abs(cdy) * 0.55) {
    cp1X = startX + cdx * 0.5
    cp1Y = startY
    cp2X = startX + cdx * 0.5
    cp2Y = endY
  } else {
    cp1X = startX
    cp1Y = startY + cdy * 0.5
    cp2X = endX
    cp2Y = startY + cdy * 0.5
  }

  const midX = 0.125 * startX + 0.375 * cp1X + 0.375 * cp2X + 0.125 * endX
  const midY = 0.125 * startY + 0.375 * cp1Y + 0.375 * cp2Y + 0.125 * endY
  const endAngle = Math.atan2(endY - cp2Y, endX - cp2X)

  return {
    isCurved: true,
    startX,
    startY,
    cp1X,
    cp1Y,
    cp2X,
    cp2Y,
    endX,
    endY,
    midX,
    midY,
    endAngle,
  }
}

/**
 * 在世界坐标 (x, y) 处拾取命中的连线（支持线条投影距离与标签胶囊区域判定）。
 */
export function pickEdgeAt({ edges = [], nodes = [], x, y, scale = 1, tolerance = 14 }) {
  let bestEdge = null
  let bestDist = Infinity
  const boxNodes = nodes.length ? nodes : Array.from(new Set(edges.flatMap((e) => [e.sourceNode, e.targetNode]).filter(Boolean)))
  const { boxByNodeId } = buildSynonymGroupBoxMap(boxNodes)

  for (const edge of edges) {
    const s = edge.sourceNode
    const t = edge.targetNode
    if (!s || !t || isInternalSynonymEdge(edge)) continue

    const isHubEdge = s.nodeType === 'hub' || t.nodeType === 'hub'
    const rawLabel = edge.label ? String(edge.label).trim() : ''
    const tag = !isHubEdge && rawLabel && rawLabel !== '核心' ? rawLabel : null
    const sameGroup = Boolean(s.synonymGroupId && s.synonymGroupId === t.synonymGroupId)
    const routeSource = (!sameGroup && boxByNodeId.has(s.id)) ? boxByNodeId.get(s.id) : s
    const routeTarget = (!sameGroup && boxByNodeId.has(t.id)) ? boxByNodeId.get(t.id) : t
    const route = computeEdgeRouting(routeSource, routeTarget, edge)

    if (tag) {
      const estimatedTagW = tag.length * 12 + 14
      if (Math.abs(x - route.midX) <= estimatedTagW / 2 + 4 && Math.abs(y - route.midY) <= 12) {
        return edge
      }
    }

    if (route.isCurved) {
      const samples = [0.15, 0.35, 0.5, 0.65, 0.85]
      for (const st of samples) {
        const u = 1 - st
        const px = u * u * u * route.startX + 3 * u * u * st * route.cp1X + 3 * u * st * st * route.cp2X + st * st * st * route.endX
        const py = u * u * u * route.startY + 3 * u * u * st * route.cp1Y + 3 * u * st * st * route.cp2Y + st * st * st * route.endY
        const dist = Math.hypot(x - px, y - py)
        const screenDist = dist * scale
        if (screenDist <= tolerance && screenDist < bestDist) {
          bestDist = screenDist
          bestEdge = edge
        }
      }
    } else {
      const dx = route.endX - route.startX
      const dy = route.endY - route.startY
      const lenSq = dx * dx + dy * dy
      if (lenSq > 0) {
        const u = ((x - route.startX) * dx + (y - route.startY) * dy) / lenSq
        if (u >= 0.05 && u <= 0.95) {
          const projX = route.startX + u * dx
          const projY = route.startY + u * dy
          const dist = Math.hypot(x - projX, y - projY)
          const screenDist = dist * scale
          if (screenDist <= tolerance && screenDist < bestDist) {
            bestDist = screenDist
            bestEdge = edge
          }
        }
      }
    }
  }

  return bestEdge
}

/**
 * 计算节点折叠展开按钮相对节点中心的局部坐标与方位。
 */
export function computeNodeCollapseTogglePoint(node) {
  const w = node.width || 84
  const h = node.height || 38
  const dir = node.direction || (node.x >= 0 ? 'right' : 'left')
  if (dir === 'right') return { x: w / 2 + 10, y: 0, dir: 'right' }
  if (dir === 'left') return { x: -w / 2 - 10, y: 0, dir: 'left' }
  if (dir === 'up') return { x: 0, y: -h / 2 - 10, dir: 'up' }
  if (dir === 'down') return { x: 0, y: h / 2 + 10, dir: 'down' }
  return { x: w / 2 + 10, y: 0, dir: 'right' }
}

/**
 * 四向树状思维导图布局算法（方案 1：根节点居中，四大主干向东、南、西、北 4 个象限单向舒展，彻底消除交叉线）。
 */
export function computeMindMapLayout(ctx, graphData, options = {}) {
  const rawNodes = graphData?.nodes || []
  const rawEdges = graphData?.edges || []
  if (rawNodes.length === 0) return { placedNodes: [], edges: [], nodeMap: new Map() }

  const nodeMap = new Map()
  const placedNodes = []
  const clusterInfo = prepareSynonymClusters(ctx, rawNodes, rawEdges)
  const getLayoutMetrics = (n) => clusterInfo.clusterMetricsByAnchorId.get(n.id) || computeNodeMetrics(ctx, n)

  // 1. 查找中心 Hub 根节点
  const hub = rawNodes.find((n) => n.nodeType === 'hub') || rawNodes[0]
  const hubMetrics = computeNodeMetrics(ctx, hub)
  const hubNode = {
    ...hub,
    ...hubMetrics,
    x: 0,
    y: 0,
    fixed: true,
    level: 0,
  }
  placedNodes.push(hubNode)
  nodeMap.set(hubNode.id, hubNode)
  if (hubNode.term) nodeMap.set(hubNode.term, hubNode)

  // 2. 构建邻接父子映射（严格先按唯一 ID 索引，避免同 term 重名节点相互覆盖）
  const nodeById = new Map()
  for (const n of rawNodes) {
    nodeById.set(n.id, n)
  }
  const resolveNode = (key) => {
    if (nodeById.has(key)) return nodeById.get(key)
    return rawNodes.find((n) => n.term === key) || null
  }

  const childrenMap = new Map()
  for (const n of rawNodes) {
    childrenMap.set(n.id, [])
  }

  for (const e of rawEdges) {
    if (e.relationType === 'synonym') continue
    const rawS = resolveNode(e.source)
    const rawT = resolveNode(e.target)
    const sNode = rawS ? (clusterInfo.anchorByMemberId.get(rawS.id) || rawS) : null
    const tNode = rawT ? (clusterInfo.anchorByMemberId.get(rawT.id) || rawT) : null
    if (sNode && tNode && sNode.id !== tNode.id) {
      if (!childrenMap.has(sNode.id)) childrenMap.set(sNode.id, [])
      const list = childrenMap.get(sNode.id)
      if (!list.some((child) => child.id === tNode.id)) {
        list.push(tNode)
      }
    }
  }

  // 3. 提取从中心直连的第 1 层主干分支
  let hubChildren = childrenMap.get(hub.id) || []
  if (hubChildren.length === 0) {
    hubChildren = rawNodes.filter((n) => n.nodeType === 'core' && !clusterInfo.satelliteIds.has(n.id))
  }

  // 4. 智能识别分支象限方向 (up / down / left / right)
  function detectBranchDirection(branchRoot, idx, total) {
    const text = `${branchRoot.id || ''} ${branchRoot.term || ''} ${branchRoot.label || ''} ${branchRoot.definition || ''}`.toLowerCase()
    if (/types of rooms|房间类型|type|类型/.test(text)) return 'up'
    if (/parts of rooms|房间组成|part|组成|结构/.test(text)) return 'down'
    if (/action|动作|description|描述|属性/.test(text)) return 'left'
    if (/common item|item|物品|设备|用品|备用/.test(text)) return 'right'

    // 家用设备分支语义自适应
    if (/stove|cook|kitchen|oven|炉|灶|烤|面包|咖啡/.test(text)) return 'up'
    if (/pc|phone|computer|lamp|vacuum|电|电脑|计算|话|灯|吸尘/.test(text)) return 'left'
    if (/dish|air|heater|camera|dish-washing|dryer|碟|盘|空|热|暖|摄|相|麦/.test(text)) return 'right'
    if (/modulator|other|调/.test(text)) return 'down'

    // 默认分配
    if (total === 2) return idx === 0 ? 'left' : 'right'
    if (total === 3) return idx === 0 ? 'up' : idx === 1 ? 'left' : 'right'
    if (total === 4) return ['up', 'down', 'left', 'right'][idx]
    return idx % 2 === 0 ? 'right' : 'left'
  }

  const branchesByDir = { up: [], down: [], left: [], right: [] }
  hubChildren.forEach((child, idx) => {
    const dir = detectBranchDirection(child, idx, hubChildren.length)
    branchesByDir[dir].push(child)
  })

  // 5. 递归排布子树
  // 5.1 水平方向单向排布 (向右 / 向左)
  function layoutHorizontalSubtree(parent, dirX, depth) {
    const children = childrenMap.get(parent.id) || []
    const metrics = getLayoutMetrics(parent)
    Object.assign(parent, metrics)

    if (children.length === 0) {
      parent.subtreeHeight = parent.height + 22
      return
    }

    let totalH = 0
    children.forEach((c) => {
      layoutHorizontalSubtree(c, dirX, depth + 1)
      totalH += c.subtreeHeight || 44
    })
    parent.subtreeHeight = Math.max(parent.height + 22, totalH)
  }

  function assignHorizontalPositions(parent, dirX, baseX, startY) {
    const children = childrenMap.get(parent.id) || []
    if (children.length === 0) return

    let currentY = startY
    children.forEach((c) => {
      const cMetrics = getLayoutMetrics(c)
      const hSpan = c.subtreeHeight || (c.height + 22)
      const cY = currentY + hSpan / 2
      const cX = baseX + dirX * ((parent.halfW || 44) + 40 + (cMetrics.halfW || 44))

      c.x = cX
      c.y = cY
      c.fixed = true
      c.level = (parent.level || 1) + 1
      c.direction = dirX > 0 ? 'right' : 'left'
      Object.assign(c, cMetrics)
      placedNodes.push(c)
      nodeMap.set(c.id, c)
      if (c.term) nodeMap.set(c.term, c)

      assignHorizontalPositions(c, dirX, cX, currentY)
      currentY += hSpan
    })

    if (parent.nodeType !== 'hub' && children.length > 0) {
      parent.y = (children[0].y + children[children.length - 1].y) / 2
    }
  }

  // 5.2 垂直方向排布 (向上 / 向下)
  function layoutVerticalSubtree(parent, dirY, depth) {
    const children = childrenMap.get(parent.id) || []
    const metrics = getLayoutMetrics(parent)
    Object.assign(parent, metrics)

    if (children.length === 0) {
      parent.subtreeWidth = parent.width + 24
      return
    }

    let totalW = 0
    children.forEach((c) => {
      layoutVerticalSubtree(c, dirY, depth + 1)
      totalW += c.subtreeWidth || 100
    })
    parent.subtreeWidth = Math.max(parent.width + 24, totalW)
  }

  function assignVerticalPositions(parent, dirY, startX, baseY) {
    const children = childrenMap.get(parent.id) || []
    if (children.length === 0) return

    const isMultiLeaf = children.length >= 6 && children.every((c) => (childrenMap.get(c.id) || []).length === 0)
    if (isMultiLeaf) {
      const row1Count = Math.ceil(children.length / 2)
      const row1 = children.slice(0, row1Count)
      const row2 = children.slice(row1Count)
      const colW = 110
      const row1StartX = -(row1.length - 1) * (colW / 2)
      const row2StartX = -(row2.length - 1) * (colW / 2)
      const row1Y = baseY + dirY * ((parent.halfH || 20) + 38 + 20)
      const row2Y = row1Y + dirY * 50

      row1.forEach((c, idx) => {
        const cMetrics = getLayoutMetrics(c)
        c.x = row1StartX + idx * colW
        c.y = row1Y
        c.fixed = true
        c.level = (parent.level || 1) + 1
        c.direction = dirY < 0 ? 'up' : 'down'
        Object.assign(c, cMetrics)
        placedNodes.push(c)
        nodeMap.set(c.id, c)
        if (c.term) nodeMap.set(c.term, c)
      })

      row2.forEach((c, idx) => {
        const cMetrics = getLayoutMetrics(c)
        c.x = row2StartX + idx * colW
        c.y = row2Y
        c.fixed = true
        c.level = (parent.level || 1) + 2
        c.direction = dirY < 0 ? 'up' : 'down'
        Object.assign(c, cMetrics)
        placedNodes.push(c)
        nodeMap.set(c.id, c)
        if (c.term) nodeMap.set(c.term, c)
      })
      return
    }

    let currentX = startX
    children.forEach((c) => {
      const cMetrics = getLayoutMetrics(c)
      const wSpan = c.subtreeWidth || (c.width + 24)
      const cX = currentX + wSpan / 2
      const cY = baseY + dirY * ((parent.halfH || 20) + 40 + (cMetrics.halfH || 20))

      c.x = cX
      c.y = cY
      c.fixed = true
      c.level = (parent.level || 1) + 1
      c.direction = dirY < 0 ? 'up' : 'down'
      Object.assign(c, cMetrics)
      placedNodes.push(c)
      nodeMap.set(c.id, c)
      if (c.term) nodeMap.set(c.term, c)

      assignVerticalPositions(c, dirY, currentX, cY)
      currentX += wSpan
    })
  }

  // 6. 展开排布四大象限
  // 6.1 东向分支 (RIGHT)
  if (branchesByDir.right.length > 0) {
    let rightTotalH = 0
    branchesByDir.right.forEach((root) => {
      layoutHorizontalSubtree(root, 1, 1)
      rightTotalH += root.subtreeHeight || 50
    })
    let startY = -rightTotalH / 2
    branchesByDir.right.forEach((root) => {
      const metrics = getLayoutMetrics(root)
      const span = root.subtreeHeight || 50
      root.x = (hubNode.halfW || 55) + 48 + (metrics.halfW || 44)
      root.y = startY + span / 2
      root.fixed = true
      root.level = 1
      root.direction = 'right'
      Object.assign(root, metrics)
      placedNodes.push(root)
      nodeMap.set(root.id, root)
      if (root.term) nodeMap.set(root.term, root)

      assignHorizontalPositions(root, 1, root.x, startY)
      startY += span
    })
  }

  // 6.2 西向分支 (LEFT)
  if (branchesByDir.left.length > 0) {
    let leftTotalH = 0
    branchesByDir.left.forEach((root) => {
      layoutHorizontalSubtree(root, -1, 1)
      leftTotalH += root.subtreeHeight || 50
    })
    let startY = -leftTotalH / 2
    branchesByDir.left.forEach((root) => {
      const metrics = getLayoutMetrics(root)
      const span = root.subtreeHeight || 50
      root.x = -(hubNode.halfW || 55) - 48 - (metrics.halfW || 44)
      root.y = startY + span / 2
      root.fixed = true
      root.level = 1
      root.direction = 'left'
      Object.assign(root, metrics)
      placedNodes.push(root)
      nodeMap.set(root.id, root)
      if (root.term) nodeMap.set(root.term, root)

      assignHorizontalPositions(root, -1, root.x, startY)
      startY += span
    })
  }

  // 6.3 北向分支 (UP)
  if (branchesByDir.up.length > 0) {
    let upTotalW = 0
    branchesByDir.up.forEach((root) => {
      layoutVerticalSubtree(root, -1, 1)
      upTotalW += root.subtreeWidth || 100
    })
    let startX = -upTotalW / 2
    branchesByDir.up.forEach((root) => {
      const metrics = getLayoutMetrics(root)
      const span = root.subtreeWidth || 100
      root.x = startX + span / 2
      root.y = -(hubNode.halfH || 20) - 52 - (metrics.halfH || 20)
      root.fixed = true
      root.level = 1
      root.direction = 'up'
      Object.assign(root, metrics)
      placedNodes.push(root)
      nodeMap.set(root.id, root)
      if (root.term) nodeMap.set(root.term, root)

      assignVerticalPositions(root, -1, startX, root.y)
      startX += span
    })
  }

  // 6.4 南向分支 (DOWN)
  if (branchesByDir.down.length > 0) {
    let downTotalW = 0
    branchesByDir.down.forEach((root) => {
      layoutVerticalSubtree(root, 1, 1)
      downTotalW += root.subtreeWidth || 100
    })
    let startX = -downTotalW / 2
    branchesByDir.down.forEach((root) => {
      const metrics = getLayoutMetrics(root)
      const span = root.subtreeWidth || 100
      root.x = startX + span / 2
      root.y = (hubNode.halfH || 20) + 52 + (metrics.halfH || 20)
      root.fixed = true
      root.level = 1
      root.direction = 'down'
      Object.assign(root, metrics)
      placedNodes.push(root)
      nodeMap.set(root.id, root)
      if (root.term) nodeMap.set(root.term, root)

      assignVerticalPositions(root, 1, startX, root.y)
      startX += span
    })
  }

  // 7. 处理其余非簇卫星节点（如有）
  rawNodes.forEach((n) => {
    if (!nodeMap.has(n.id) && !clusterInfo.satelliteIds.has(n.id)) {
      const metrics = getLayoutMetrics(n)
      n.x = (placedNodes.length % 5) * 110 - 200
      n.y = Math.floor(placedNodes.length / 5) * 50 + 340
      n.fixed = true
      Object.assign(n, metrics)
      placedNodes.push(n)
      nodeMap.set(n.id, n)
    }
  })

  // 7b. 将近义词簇锚点还原为独立词节点并紧凑排布同簇兄弟
  unpackSynonymClusters(placedNodes, nodeMap, clusterInfo, { preserveAllFixed: false })

  // 8. 组装最终 edges
  unpackSynonymClusters(placedNodes, nodeMap, clusterInfo, { preserveAllFixed: true })

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
 * 一键智能整理图谱布局（清除手动错乱坐标，自适应按导图树状或极坐标扇形等距排列）。
 */
export function tidyUpGalaxyLayout(ctx, graphData) {
  if (!graphData || !Array.isArray(graphData.nodes)) return graphData
  const rawNodes = graphData.nodes
  const rawEdges = Array.isArray(graphData.edges) ? graphData.edges : []

  // 判断是否具备多层级思维导图结构（含有 expansion 衍生节点或深度多叉树关系）
  const hasTreeDepth = rawNodes.length >= 6 && (
    rawNodes.some((n) => n.nodeType === 'expansion')
    || rawEdges.some((e) => e.relationType === 'downstream' || e.relationType === 'action')
  )

  const cleanData = {
    ...graphData,
    nodes: rawNodes.map((n) => ({
      ...n,
      fixed: n.nodeType === 'hub',
      x: n.nodeType === 'hub' ? 0 : undefined,
      y: n.nodeType === 'hub' ? 0 : undefined,
    })),
    edges: rawEdges,
  }

  const layout = hasTreeDepth
    ? computeMindMapLayout(ctx, cleanData)
    : computeGalaxyLayout(ctx, cleanData)

  const placed = layout.placedNodes || []
  placed.forEach((n) => {
    n.fixed = true
  })
  return {
    ...graphData,
    nodes: placed,
    edges: layout.edges || cleanData.edges,
  }
}
