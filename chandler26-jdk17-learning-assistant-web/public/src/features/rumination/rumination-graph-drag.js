/**
 * 星系词汇图谱拖拽吸附与层级归属判定工具
 * 支持拖拽核心/扩展词汇节点至目标节点上方时高亮吸附，并在松开时自动完成父子层级重组（Drag-to-Reparent）。
 */

/**
 * 递归判断 targetId 是否为 ancestorId 的后代节点（防止父子循环引用闭环）。
 *
 * @param {string} targetId 候选目标节点 ID
 * @param {string} ancestorId 正在拖拽的源节点 ID
 * @param {Array<Object>} edges 图谱边集合
 * @returns {boolean} 若 targetId 是 ancestorId 的子孙节点则返回 true
 */
export function isDescendantOf(targetId, ancestorId, edges = []) {
  if (!targetId || !ancestorId || targetId === ancestorId) return false
  const visited = new Set()
  const queue = [ancestorId]
  while (queue.length > 0) {
    const current = queue.shift()
    if (current === targetId) return true
    if (visited.has(current)) continue
    visited.add(current)
    for (const edge of edges) {
      const sId = edge.sourceNode?.id || edge.source
      const tId = edge.targetNode?.id || edge.target
      if (sId === current && tId && !visited.has(tId)) {
        queue.push(tId)
      }
    }
  }
  return false
}

/**
 * 在画布世界坐标系下寻找鼠标/触摸拖拽所悬浮的目标节点（Drop Target）。
 * 枢纽节点本身不支持被拖入其它节点；同时排除了自身与所有后代节点以杜绝环路。
 *
 * @param {number} worldX 当前光标的世界坐标 X
 * @param {number} worldY 当前光标的世界坐标 Y
 * @param {Object} draggedNode 当前正被拖拽的节点对象
 * @param {Array<Object>} nodes 画布全部节点集合
 * @param {Array<Object>} edges 画布全部连接边集合
 * @returns {Object|null} 命中的有效吸附目标节点，未命中则返回 null
 */
export function findDropTarget(worldX, worldY, draggedNode, nodes = [], edges = []) {
  if (!draggedNode || draggedNode.nodeType === 'hub') return null
  const draggedId = draggedNode.id

  // 倒序遍历节点（后渲染者优先响应），匹配带扩展感应区的边界盒
  for (let i = nodes.length - 1; i >= 0; i--) {
    const candidate = nodes[i]
    if (candidate === draggedNode || candidate.id === draggedId) continue
    if (isDescendantOf(candidate.id, draggedId, edges)) continue

    const padX = 14
    const padY = 12
    const hw = ((candidate.width || 84) / 2) + padX
    const hh = ((candidate.height || 38) / 2) + padY
    if (
      worldX >= candidate.x - hw &&
      worldX <= candidate.x + hw &&
      worldY >= candidate.y - hh &&
      worldY <= candidate.y + hh
    ) {
      return candidate
    }
  }
  return null
}

/**
 * 绘制拖拽悬停时的目标吸附反馈：包括连线导引虚线、目标节点呼吸发光框以及悬停提示徽标。
 *
 * @param {CanvasRenderingContext2D} ctx 2D 绘图上下文
 * @param {Object} dropTargetNode 悬停的目标吸附节点
 * @param {Object} draggedNode 正在拖拽的节点
 */
export function drawDropTargetFeedback(ctx, dropTargetNode, draggedNode) {
  if (!dropTargetNode || !draggedNode) return
  const tw = dropTargetNode.width || 84
  const th = dropTargetNode.height || 38
  const tr = dropTargetNode.cornerRadius || th / 2

  // 1. 绘制拖拽连接导引线（由拖拽物指向吸附目标）
  ctx.save()
  ctx.beginPath()
  ctx.moveTo(draggedNode.x, draggedNode.y)
  ctx.lineTo(dropTargetNode.x, dropTargetNode.y)
  ctx.strokeStyle = '#38bdf8'
  ctx.lineWidth = 2
  ctx.setLineDash([5, 4])
  ctx.shadowColor = '#38bdf8'
  ctx.shadowBlur = 8
  ctx.stroke()
  ctx.restore()

  // 2. 目标节点高亮呼吸吸附圈
  ctx.save()
  ctx.translate(dropTargetNode.x, dropTargetNode.y)
  ctx.beginPath()
  ctx.roundRect(-tw / 2 - 8, -th / 2 - 8, tw + 16, th + 16, tr + 8)
  ctx.strokeStyle = '#38bdf8'
  ctx.lineWidth = 2.5
  ctx.setLineDash([6, 4])
  ctx.shadowColor = '#38bdf8'
  ctx.shadowBlur = 14
  ctx.stroke()
  ctx.setLineDash([])

  // 3. 目标节点顶部浮动指示文案
  const label = dropTargetNode.term || dropTargetNode.label || '分类'
  const dropText = `松开归入【${label}】`
  ctx.font = 'bold 11px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif'
  const textW = ctx.measureText(dropText).width + 16
  ctx.fillStyle = 'rgba(15, 23, 42, 0.95)'
  ctx.strokeStyle = '#38bdf8'
  ctx.lineWidth = 1
  ctx.beginPath()
  ctx.roundRect(-textW / 2, -th / 2 - 28, textW, 20, 6)
  ctx.fill()
  ctx.stroke()
  ctx.fillStyle = '#38bdf8'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(dropText, 0, -th / 2 - 18)
  ctx.restore()
}

/**
 * 为拖拽挂载到新父节点的子节点，自适应计算一个无重叠的合理停靠坐标。
 * 避免释放后子节点直接堆叠在父节点中央。
 *
 * @param {Object} childNode 正在被挂载的子节点
 * @param {Object} parentNode 目标父节点
 * @param {Array<Object>} nodes 画布上所有节点
 * @param {Array<Object>} edges 画布上所有连线
 * @returns {{ x: number, y: number }} 自适应计算出的全新坐标
 */
export function calculateReparentPosition(childNode, parentNode, nodes = [], edges = []) {
  if (!parentNode) return { x: Math.round(childNode?.x || 0), y: Math.round(childNode?.y || 0) }

  const childW = childNode?.width || 84
  const childH = childNode?.height || 38
  const parentW = parentNode?.width || (parentNode?.nodeType === 'hub' ? 120 : 84)
  const parentH = parentNode?.height || (parentNode?.nodeType === 'hub' ? 44 : 38)
  const isParentHub = parentNode.nodeType === 'hub' || parentNode.id === 'hub'

  // 1. 矩形防重叠检测辅助函数
  function overlapsAny(cx, cy) {
    const padX = 18
    const padY = 14
    for (const other of nodes) {
      if (other.id === childNode?.id) continue
      const otherW = other.width || (other.nodeType === 'hub' ? 120 : 84)
      const otherH = other.height || (other.nodeType === 'hub' ? 44 : 38)
      const minW = (childW + otherW) / 2 + padX
      const minH = (childH + otherH) / 2 + padY
      if (Math.abs(cx - other.x) < minW && Math.abs(cy - other.y) < minH) {
        return true
      }
    }
    return false
  }

  // 2. 场景 A: 挂载到中心枢纽节点（变为场景核心词）
  if (isParentHub) {
    const hubX = parentNode.x || 0
    const hubY = parentNode.y || 0
    const rawDx = (childNode?.x || 0) - hubX
    const rawDy = (childNode?.y || 0) - hubY
    const releaseAngle = Math.atan2(rawDy, rawDx) || 0
    const baseRadius = 210

    // 沿释放角度在核心轨道同心环与小角度扇区内寻找空闲槽位
    const angleOffsets = [0, 0.22, -0.22, 0.45, -0.45, 0.68, -0.68, 0.92, -0.92, 1.2, -1.2, 1.6, -1.6, Math.PI]
    const radii = [baseRadius, baseRadius + 45, baseRadius + 90]

    for (const r of radii) {
      for (const aOff of angleOffsets) {
        const testAngle = releaseAngle + aOff
        const tx = Math.round(hubX + Math.cos(testAngle) * r)
        const ty = Math.round(hubY + Math.sin(testAngle) * r)
        if (!overlapsAny(tx, ty)) {
          return { x: tx, y: ty }
        }
      }
    }
    return {
      x: Math.round(hubX + Math.cos(releaseAngle) * (baseRadius + 50)),
      y: Math.round(hubY + Math.sin(releaseAngle) * (baseRadius + 50)),
    }
  }

  // 3. 场景 B: 挂载到普通词汇节点（变为该节点的卫星衍生词）
  const px = parentNode.x || 0
  const py = parentNode.y || 0
  const distFromCenter = Math.hypot(px, py)
  const baseAngle = distFromCenter > 15 ? Math.atan2(py, px) : 0

  // 查找父节点已有其他子节点
  const existingChildIds = new Set(
    edges
      .filter((e) => (e.source === parentNode.id || e.sourceNode?.id === parentNode.id) && e.target !== childNode?.id && e.targetNode?.id !== childNode?.id)
      .map((e) => e.targetNode?.id || e.target)
  )
  const siblingNodes = nodes.filter((n) => existingChildIds.has(n.id))

  // 基准距离：至少避开父节点半宽高 + 子节点半宽高 + 充裕间距（约 125px）
  const minSafeDist = Math.max(125, Math.hypot(parentW + childW, parentH + childH) / 2 + 35)

  // 候选发射角：若有兄弟节点则优先在兄弟外侧扩散扇形展开；无兄弟节点时则向背离中心方向扩散
  const angleCandidates = []
  if (siblingNodes.length > 0) {
    const fanStep = 0.38
    for (let i = 1; i <= 6; i++) {
      angleCandidates.push(baseAngle + i * fanStep, baseAngle - i * fanStep)
    }
    angleCandidates.unshift(baseAngle)
  } else {
    angleCandidates.push(0, 0.35, -0.35, 0.7, -0.7, 1.05, -1.05, 1.4, -1.4, Math.PI)
  }

  const distanceSteps = [minSafeDist, minSafeDist + 40, minSafeDist + 80]

  for (const dist of distanceSteps) {
    for (const cand of angleCandidates) {
      const angle = siblingNodes.length > 0 ? cand : baseAngle + cand
      const tx = Math.round(px + Math.cos(angle) * dist)
      const ty = Math.round(py + Math.sin(angle) * dist)
      if (!overlapsAny(tx, ty)) {
        return { x: tx, y: ty }
      }
    }
  }

  // 兜底返回背离中心的外围坐标，确保绝不与父节点重合
  return {
    x: Math.round(px + Math.cos(baseAngle) * (minSafeDist + 20)),
    y: Math.round(py + Math.sin(baseAngle) * (minSafeDist + 20)),
  }
}
