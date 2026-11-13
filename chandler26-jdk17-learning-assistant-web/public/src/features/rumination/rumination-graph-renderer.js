/**
 * 知识反刍 - 暗夜星系图谱画布渲染器
 * 负责星空暗夜渐变、环境星尘、多维关系连线、语义胶囊节点、焦点高亮及吸附反馈的纯 Canvas 绘制。
 */

import { drawDropTargetFeedback } from '/src/features/rumination/rumination-graph-drag.js'

export const RELATION_CONFIG = {
  peer: { color: '#10b981', label: '同类', glow: 'rgba(16, 185, 129, 0.4)' },
  action: { color: '#38bdf8', label: '动作', glow: 'rgba(56, 189, 248, 0.4)' },
  downstream: { color: '#f97316', label: '加工', glow: 'rgba(249, 115, 22, 0.4)' },
  attribute: { color: '#c084fc', label: '感官', glow: 'rgba(192, 132, 252, 0.4)' },
}

export function drawNode(ctx, node, {
  selectedNodeIds = new Set(),
  focusedNodeId = null,
  hoveredNode = null,
  illuminatedNodeIds = new Set(),
  reducedMotion = false,
  pulseState = null,
} = {}) {
  const isSelected = selectedNodeIds.has(node.id)
  const isFocused = focusedNodeId === node.id
  const isHovered = hoveredNode === node
  const isIlluminated = illuminatedNodeIds.has(node.id)
  const isHub = node.nodeType === 'hub'
  const isExpansion = node.nodeType === 'expansion'

  const w = node.width || 84
  const h = node.height || 38
  const r = node.cornerRadius || h / 2

  ctx.save()
  ctx.translate(node.x, node.y)

  // 1. 外发光光环 (Selected / Illuminated / Hovered)
  if (isSelected || isIlluminated) {
    ctx.beginPath()
    ctx.roundRect(-w / 2 - 4, -h / 2 - 4, w + 8, h + 8, r + 4)
    ctx.fillStyle = isIlluminated ? 'rgba(16, 185, 129, 0.25)' : 'rgba(245, 158, 11, 0.28)'
    ctx.shadowColor = isIlluminated ? '#10b981' : '#f59e0b'
    ctx.shadowBlur = 12
    ctx.fill()
  } else if (isHovered) {
    ctx.beginPath()
    ctx.roundRect(-w / 2 - 2, -h / 2 - 2, w + 4, h + 4, r + 2)
    ctx.fillStyle = 'rgba(56, 189, 248, 0.15)'
    ctx.fill()
  }

  // 1b. 当前左键焦点独立外描边（与词袋成员高亮视觉分离，明确操作目标）
  if (isFocused) {
    ctx.save()
    ctx.beginPath()
    ctx.roundRect(-w / 2 - 6, -h / 2 - 6, w + 12, h + 12, r + 6)
    ctx.strokeStyle = '#f8fafc'
    ctx.lineWidth = 2
    ctx.shadowColor = '#38bdf8'
    ctx.shadowBlur = 10
    ctx.stroke()
    ctx.restore()
  }

  // 1c. 双击节点时的扩散光环反馈（系统允许动效时才绘制扩散动画）
  if (!reducedMotion && pulseState && pulseState.nodeId === node.id) {
    const elapsed = performance.now() - pulseState.startedAt
    const t = Math.max(0, Math.min(1, elapsed / pulseState.duration))
    const easeOut = 1 - Math.pow(1 - t, 3)
    const expand = 6 + easeOut * 18
    ctx.save()
    ctx.beginPath()
    ctx.roundRect(-w / 2 - expand, -h / 2 - expand, w + expand * 2, h + expand * 2, r + expand)
    ctx.strokeStyle = `rgba(56, 189, 248, ${(1 - t) * 0.78})`
    ctx.lineWidth = 2.5 - t * 1.2
    ctx.shadowColor = '#38bdf8'
    ctx.shadowBlur = 14 * (1 - t)
    ctx.stroke()
    ctx.restore()
  }

  // 2. 胶囊卡片主体
  ctx.beginPath()
  ctx.roundRect(-w / 2, -h / 2, w, h, r)

  if (isHub) {
    const hubGrad = ctx.createLinearGradient(-w / 2, -h / 2, w / 2, h / 2)
    hubGrad.addColorStop(0, 'rgba(6, 78, 59, 0.95)')
    hubGrad.addColorStop(1, 'rgba(6, 95, 70, 0.95)')
    ctx.fillStyle = hubGrad
  } else if (isSelected) {
    ctx.fillStyle = 'rgba(30, 41, 59, 0.96)'
  } else {
    ctx.fillStyle = isExpansion ? 'rgba(15, 23, 42, 0.90)' : 'rgba(15, 23, 42, 0.94)'
  }
  ctx.fill()

  // 3. 边框渲染
  ctx.lineWidth = isSelected ? 2.5 : isHub ? 1.5 : isExpansion ? 1.5 : 2
  if (isExpansion && !isSelected) {
    ctx.setLineDash([4, 3])
  }
  if (isSelected) {
    ctx.strokeStyle = '#f59e0b'
    ctx.shadowColor = '#f59e0b'
    ctx.shadowBlur = 10
  } else if (isIlluminated) {
    ctx.strokeStyle = '#10b981'
    ctx.shadowColor = '#10b981'
    ctx.shadowBlur = 10
  } else if (isHub) {
    ctx.strokeStyle = '#34d399'
    ctx.shadowColor = '#34d399'
    ctx.shadowBlur = 8
  } else {
    if (node.status === 'mastered') ctx.strokeStyle = '#10b981'
    else if (node.status === 'vague') ctx.strokeStyle = '#f59e0b'
    else ctx.strokeStyle = '#38bdf8'
  }
  ctx.stroke()
  ctx.setLineDash([])

  // 4. 内容排版
  if (isHub) {
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    const mainLabel = node.label || node.term || '场景核心'
    const sub = node.subLabel || node.definition
    if (sub && sub !== mainLabel) {
      ctx.fillStyle = '#ffffff'
      ctx.font = 'bold 12px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      ctx.fillText(mainLabel, 0, -6)
      ctx.fillStyle = '#a7f3d0'
      ctx.font = '10px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      ctx.fillText(sub, 0, 8)
    } else {
      ctx.fillStyle = '#ffffff'
      ctx.font = 'bold 12px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      ctx.fillText(mainLabel, 0, 0)
    }
  } else {
    const textOffsetX = isExpansion ? -8 : 0
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'

    // 英文词汇（高亮、加粗、绝不被圆挡住）
    ctx.fillStyle = isSelected ? '#fbbf24' : '#ffffff'
    ctx.font = 'bold 13px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
    const defText = node.displayDefinition || node.definition
    const hasDef = Boolean(defText)
    ctx.fillText(node.term || node.label || '', textOffsetX, hasDef ? -7 : 0)

    // 中文释义（清爽副文本，清晰完整）
    if (hasDef) {
      ctx.fillStyle = isSelected ? '#fde68a' : '#94a3b8'
      ctx.font = '10px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      ctx.fillText(defText, textOffsetX, 8)
    }

    // 词袋徽章与 4D 衍生节点动作徽章
    if (isSelected) {
      // 在词袋中的词汇：右上角展示金色对勾徽章
      const badgeX = w / 2 - 12
      const badgeY = -h / 2 + 10
      ctx.beginPath()
      ctx.arc(badgeX, badgeY, 8, 0, Math.PI * 2)
      ctx.fillStyle = '#f59e0b'
      ctx.shadowColor = '#f59e0b'
      ctx.shadowBlur = 6
      ctx.fill()

      ctx.fillStyle = '#ffffff'
      ctx.font = 'bold 10px sans-serif'
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'
      ctx.fillText('✓', badgeX, badgeY)
    }
  }

  ctx.restore()
}

export function drawGraphScene({
  ctx,
  width,
  height,
  transform,
  stars = [],
  nodes = [],
  edges = [],
  selectedNodeIds = new Set(),
  focusedNodeId = null,
  hoveredNode = null,
  illuminatedNodeIds = new Set(),
  reducedMotion = false,
  pulseState = null,
  isDraggingNode = false,
  draggedNode = null,
  currentDropTarget = null,
  relationConfig = RELATION_CONFIG,
}) {
  ctx.clearRect(0, 0, width, height)

  // 绘制深空渐变背景
  const bgGradient = ctx.createRadialGradient(
    transform.x,
    transform.y,
    50 * transform.scale,
    transform.x,
    transform.y,
    Math.max(width, height) * 0.8
  )
  bgGradient.addColorStop(0, '#151d32')
  bgGradient.addColorStop(0.5, '#0e1424')
  bgGradient.addColorStop(1, '#080c16')
  ctx.fillStyle = bgGradient
  ctx.fillRect(0, 0, width, height)

  ctx.save()
  ctx.translate(transform.x, transform.y)
  ctx.scale(transform.scale, transform.scale)

  // 绘制环境星尘
  ctx.fillStyle = '#64748b'
  for (const star of stars) {
    ctx.globalAlpha = star.alpha
    ctx.beginPath()
    ctx.arc(star.x, star.y, star.radius, 0, Math.PI * 2)
    ctx.fill()
  }
  ctx.globalAlpha = 1

  // 绘制连线
  for (const edge of edges) {
    const s = edge.sourceNode
    const t = edge.targetNode
    if (!s || !t) continue
    const isConnectedSelected = selectedNodeIds.has(s.id) && selectedNodeIds.has(t.id)
    const relConf = relationConfig[edge.relationType] || { color: '#64748b', glow: 'rgba(100,116,139,0.3)', label: '' }
    const isHubEdge = s.nodeType === 'hub' || t.nodeType === 'hub'

    ctx.save()
    ctx.lineWidth = isConnectedSelected ? 3 : isHubEdge ? 1.2 : 1.5
    ctx.strokeStyle = isConnectedSelected ? '#fbbf24' : isHubEdge ? 'rgba(52, 211, 153, 0.4)' : relConf.color
    ctx.globalAlpha = isConnectedSelected ? 0.95 : isHubEdge ? 0.5 : 0.65

    if (isConnectedSelected) {
      ctx.shadowColor = '#fbbf24'
      ctx.shadowBlur = 10
    }

    ctx.beginPath()
    ctx.moveTo(s.x, s.y)
    ctx.lineTo(t.x, t.y)
    ctx.stroke()
    ctx.restore()

    // 仅当明确指定了具体的实体关系微标签时才展示（默认绝对不显示“核心”等内置类型）
    const rawLabel = edge.label ? String(edge.label).trim() : ''
    const tag = !isHubEdge && rawLabel && rawLabel !== '核心' ? rawLabel : null
    const edgeDist = Math.hypot(t.x - s.x, t.y - s.y)
    if (tag && edgeDist > 50) {
      const mx = (s.x + t.x) / 2
      const my = (s.y + t.y) / 2
      ctx.save()
      ctx.font = '10px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
      const tagWidth = ctx.measureText(tag).width + 12
      ctx.fillStyle = 'rgba(15, 23, 42, 0.92)'
      ctx.beginPath()
      ctx.roundRect(mx - tagWidth / 2, my - 8, tagWidth, 16, 8)
      ctx.fill()
      ctx.strokeStyle = relConf.color
      ctx.lineWidth = 1
      ctx.globalAlpha = 0.85
      ctx.stroke()
      ctx.fillStyle = '#e2e8f0'
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'
      ctx.fillText(tag, mx, my)
      ctx.restore()
    }
  }

  // 绘制节点
  for (const node of nodes) {
    drawNode(ctx, node, {
      selectedNodeIds,
      focusedNodeId,
      hoveredNode,
      illuminatedNodeIds,
      reducedMotion,
      pulseState,
    })
  }

  // 绘制拖拽吸附反馈
  if (isDraggingNode && draggedNode && currentDropTarget) {
    drawDropTargetFeedback(ctx, currentDropTarget, draggedNode)
  }

  ctx.restore()
}
