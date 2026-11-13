import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRuminationGraph } from '../../public/src/features/rumination/rumination-graph.js'
import { computeFitViewportTransform } from '../../public/src/features/rumination/rumination-graph-layout.js'
import { showCelebrationEffect, prefersReducedMotion } from '../../public/src/shared/celebration.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const webRoot = path.resolve(__dirname, '../..')

function createMockCanvasContext() {
  return {
    setTransform: vi.fn(),
    scale: vi.fn(),
    clearRect: vi.fn(),
    save: vi.fn(),
    restore: vi.fn(),
    translate: vi.fn(),
    beginPath: vi.fn(),
    moveTo: vi.fn(),
    lineTo: vi.fn(),
    arc: vi.fn(),
    ellipse: vi.fn(),
    quadraticCurveTo: vi.fn(),
    closePath: vi.fn(),
    fill: vi.fn(),
    stroke: vi.fn(),
    fillRect: vi.fn(),
    fillText: vi.fn(),
    roundRect: vi.fn(),
    createLinearGradient: vi.fn(() => ({ addColorStop: vi.fn() })),
    createRadialGradient: vi.fn(() => ({ addColorStop: vi.fn() })),
    measureText: vi.fn((text) => ({ width: String(text || '').length * 8 })),
    setLineDash: vi.fn(),
    font: '',
    fillStyle: '',
    strokeStyle: '',
    lineWidth: 1,
    textAlign: 'left',
    textBaseline: 'alphabetic',
    shadowColor: '',
    shadowBlur: 0,
  }
}

describe('motion governance and rumination graph keyboard/touch accessibility', () => {
  let rafQueue
  let nextRafId
  let originalRaf
  let originalCancelRaf
  let originalMatchMedia
  let originalResizeObserver
  let reducedMotionEnabled

  beforeEach(() => {
    rafQueue = new Map()
    nextRafId = 1
    reducedMotionEnabled = false

    originalRaf = window.requestAnimationFrame
    originalCancelRaf = window.cancelAnimationFrame
    originalMatchMedia = window.matchMedia
    originalResizeObserver = globalThis.ResizeObserver

    window.requestAnimationFrame = vi.fn((cb) => {
      const id = nextRafId++
      rafQueue.set(id, cb)
      return id
    })
    window.cancelAnimationFrame = vi.fn((id) => {
      rafQueue.delete(id)
    })
    window.matchMedia = vi.fn((query) => ({
      matches: query === '(prefers-reduced-motion: reduce)' ? reducedMotionEnabled : false,
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }))
    globalThis.ResizeObserver = class {
      observe() {}
      disconnect() {}
    }
    Object.defineProperty(document, 'hidden', { configurable: true, value: false })
  })

  afterEach(() => {
    window.requestAnimationFrame = originalRaf
    window.cancelAnimationFrame = originalCancelRaf
    window.matchMedia = originalMatchMedia
    globalThis.ResizeObserver = originalResizeObserver
    document.body.innerHTML = ''
  })

  function createGraphHarness({ onNodeSelect = vi.fn(), onNodeAction = vi.fn(), onNodeContextMenu = vi.fn() } = {}) {
    const canvas = document.createElement('canvas')
    canvas.tabIndex = 0
    canvas.getContext = vi.fn(() => createMockCanvasContext())
    canvas.getBoundingClientRect = vi.fn(() => ({
      left: 0,
      top: 0,
      width: 900,
      height: 600,
      right: 900,
      bottom: 600,
    }))
    document.body.appendChild(canvas)

    const graph = createRuminationGraph(canvas, {
      onNodeSelect,
      onNodeAction,
      onNodeContextMenu,
    })

    graph.setData({
      scenarioCode: 'test_scene',
      scenarioName: 'Test Scene',
      nodes: [
        { id: 'hub', nodeType: 'hub', term: 'Center', x: 0, y: 0, fixed: true },
        { id: 'n1', nodeType: 'core', term: 'lettuce', definition: '生菜', x: -120, y: 0, fixed: true },
        { id: 'n2', nodeType: 'core', term: 'cucumber', definition: '黄瓜', x: 140, y: 0, fixed: true },
      ],
      edges: [
        { source: 'hub', target: 'n1', relationType: 'peer' },
        { source: 'hub', target: 'n2', relationType: 'peer' },
      ],
    })

    return { canvas, graph, onNodeSelect, onNodeAction, onNodeContextMenu }
  }

  function flushFrames(limit = 5) {
    let executed = 0
    while (rafQueue.size > 0 && executed < limit) {
      const [[id, cb]] = Array.from(rafQueue.entries())
      rafQueue.delete(id)
      cb(performance.now())
      executed += 1
    }
    return executed
  }

  it('stops requestAnimationFrame when settled or paused and resumes only when visible/resumed', () => {
    const { graph } = createGraphHarness()

    // 所有节点均为 fixed 时，只需单帧绘制，绘制完毕后立即停止 rAF 循环
    flushFrames(5)
    expect(graph.isLoopActive()).toBe(false)
    expect(rafQueue.size).toBe(0)

    // 显式暂停后，即使请求重绘也不再申请动画帧
    graph.pause()
    graph.resetView()
    expect(rafQueue.size).toBe(0)
    expect(graph.isLoopActive()).toBe(false)

    // 恢复激活后重新调度一帧绘制并在静止后停止
    graph.resume()
    expect(graph.isLoopActive()).toBe(true)
    flushFrames(5)
    expect(graph.isLoopActive()).toBe(false)

    // 页面切到后台时立即停帧
    graph.setFocusedNode('n1')
    expect(rafQueue.size).toBeGreaterThan(0)
    Object.defineProperty(document, 'hidden', { configurable: true, value: true })
    document.dispatchEvent(new Event('visibilitychange'))
    expect(rafQueue.size).toBe(0)
    expect(graph.isLoopActive()).toBe(false)

    graph.destroy()
  })

  it('supports directional keyboard selection and all node actions without mouse right-click', () => {
    const onNodeSelect = vi.fn()
    const onNodeAction = vi.fn()
    const onNodeContextMenu = vi.fn()
    const { canvas, graph } = createGraphHarness({ onNodeSelect, onNodeAction, onNodeContextMenu })
    flushFrames(5)

    canvas.focus()
    // 初始按右箭头选中第一个非中心节点并导航至右侧节点
    canvas.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true, cancelable: true }))
    expect(onNodeSelect).toHaveBeenCalledTimes(1)
    expect(onNodeSelect.mock.calls[0][0].id).toBe('n1')

    canvas.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true, cancelable: true }))
    expect(onNodeSelect).toHaveBeenCalledTimes(2)
    expect(onNodeSelect.mock.calls[1][0].id).toBe('n2')

    canvas.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', code: 'Space', bubbles: true, cancelable: true }))
    expect(onNodeAction).toHaveBeenCalledWith('bag', expect.objectContaining({ id: 'n2' }))

    canvas.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }))
    expect(onNodeAction).toHaveBeenCalledWith('expand', expect.objectContaining({ id: 'n2' }))

    canvas.dispatchEvent(new KeyboardEvent('keydown', { key: 'e', bubbles: true, cancelable: true }))
    expect(onNodeAction).toHaveBeenCalledWith('edit', expect.objectContaining({ id: 'n2' }))

    canvas.dispatchEvent(new KeyboardEvent('keydown', { key: 'a', bubbles: true, cancelable: true }))
    expect(onNodeAction).toHaveBeenCalledWith('addChild', expect.objectContaining({ id: 'n2' }))

    canvas.dispatchEvent(new KeyboardEvent('keydown', { key: 'Delete', bubbles: true, cancelable: true }))
    expect(onNodeAction).toHaveBeenCalledWith('delete', expect.objectContaining({ id: 'n2' }))

    canvas.dispatchEvent(new KeyboardEvent('keydown', { key: 'm', bubbles: true, cancelable: true }))
    expect(onNodeContextMenu).toHaveBeenCalledWith(expect.objectContaining({ id: 'n2' }), expect.any(Object))

    graph.destroy()
  })

  it('skips celebration overlay particles and settles graph synchronously when prefers-reduced-motion is reduce', () => {
    reducedMotionEnabled = true
    expect(prefersReducedMotion()).toBe(true)

    const layer = document.createElement('div')
    layer.id = 'celebrationLayer'
    layer.className = 'show'
    layer.innerHTML = '<span>stale</span>'
    document.body.appendChild(layer)
    showCelebrationEffect(layer)
    expect(layer.classList.contains('show')).toBe(false)
    expect(layer.innerHTML).toBe('')

    const canvas = document.createElement('canvas')
    canvas.getContext = vi.fn(() => createMockCanvasContext())
    canvas.getBoundingClientRect = vi.fn(() => ({ left: 0, top: 0, width: 800, height: 500, right: 800, bottom: 500 }))
    document.body.appendChild(canvas)

    const graph = createRuminationGraph(canvas)
    graph.setData({
      nodes: [
        { id: 'hub', nodeType: 'hub', term: 'Center', x: 0, y: 0, fixed: true },
        { id: 'unfixed1', nodeType: 'core', term: 'apple', x: 30, y: 20, fixed: false },
      ],
      edges: [{ source: 'hub', target: 'unfixed1', relationType: 'peer' }],
    })
    flushFrames(3)
    expect(graph.isLoopActive()).toBe(false)
    graph.destroy()
  })

  it('forbids transition: all anywhere in public/styles', () => {
    const stylesDir = path.join(webRoot, 'public/styles')
    const cssFiles = []
    function walk(dir) {
      for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name)
        if (entry.isDirectory()) walk(full)
        else if (entry.name.endsWith('.css')) cssFiles.push(full)
      }
    }
    walk(stylesDir)

    const offenders = []
    for (const file of cssFiles) {
      const content = fs.readFileSync(file, 'utf8')
      if (/transition\s*:\s*all\b/i.test(content)) {
        offenders.push(path.relative(webRoot, file))
      }
    }
    expect(offenders).toEqual([])
  })

  it('auto-fits graph scale and center cleanly across compact and high-resolution viewports', () => {
    const spreadNodes = [
      { id: 'hub', nodeType: 'hub', term: 'Center', x: 0, y: 0, width: 120, height: 30, fixed: true },
      { id: 'top', nodeType: 'core', term: 'lettuce', x: 0, y: -420, width: 100, height: 40, fixed: true },
      { id: 'bottom', nodeType: 'core', term: 'grape', x: 0, y: 420, width: 100, height: 40, fixed: true },
      { id: 'left', nodeType: 'expansion', term: 'chop', x: -520, y: 0, width: 90, height: 34, fixed: true },
      { id: 'right', nodeType: 'expansion', term: 'mashed potato', x: 520, y: 0, width: 140, height: 34, fixed: true },
    ]

    const compactFit = computeFitViewportTransform({ nodes: spreadNodes, width: 900, height: 500 })
    expect(compactFit.scale).toBeGreaterThanOrEqual(0.42)
    expect(compactFit.scale).toBeLessThan(0.55)
    // 顶部与底部节点在缩放后必须处于安全边距内，不再裁切
    const topScreenY = compactFit.y + (-420 - 20) * compactFit.scale
    const bottomScreenY = compactFit.y + (420 + 20) * compactFit.scale
    expect(topScreenY).toBeGreaterThanOrEqual(40)
    expect(bottomScreenY).toBeLessThanOrEqual(460)

    const highResFit = computeFitViewportTransform({ nodes: spreadNodes, width: 2200, height: 1240 })
    expect(highResFit.scale).toBeGreaterThan(compactFit.scale)
    expect(highResFit.scale).toBeLessThanOrEqual(1.28)

    let currentRect = { left: 0, top: 0, width: 900, height: 500, right: 900, bottom: 500 }
    const canvas = document.createElement('canvas')
    canvas.getContext = vi.fn(() => createMockCanvasContext())
    canvas.getBoundingClientRect = vi.fn(() => currentRect)
    document.body.appendChild(canvas)

    const graph = createRuminationGraph(canvas)
    graph.setData({ nodes: spreadNodes, edges: [] })
    const initialTransform = graph.getTransform()
    expect(initialTransform.scale).toBeCloseTo(compactFit.scale, 3)

    // 模拟高分辨率屏幕下容器尺寸变大并触发 resize：未手动缩放前应自动重新适配高分屏视口
    currentRect = { left: 0, top: 0, width: 2200, height: 1240, right: 2200, bottom: 1240 }
    graph.resize()
    const expectedHighResFit = computeFitViewportTransform({ nodes: graph.getNodes(), width: 2200, height: 1240 })
    const highResTransform = graph.getTransform()
    expect(highResTransform.scale).toBeGreaterThan(initialTransform.scale)
    expect(highResTransform.x).toBeCloseTo(expectedHighResFit.x, 1)
    expect(highResTransform.y).toBeCloseTo(expectedHighResFit.y, 1)

    // 手动放大后 resize 应保留用户缩放并平顺保持中心锚点；resetView 后恢复自适应全览
    graph.zoomIn()
    const zoomedScale = graph.getTransform().scale
    currentRect = { left: 0, top: 0, width: 2400, height: 1320, right: 2400, bottom: 1320 }
    graph.resize()
    expect(graph.getTransform().scale).toBeCloseTo(zoomedScale, 3)

    graph.resetView()
    expect(graph.getTransform().scale).toBeLessThanOrEqual(1.28)
    graph.destroy()
  })

  it('triggers onEdgeDblClick when canvas is double-clicked on an edge', () => {
    const onEdgeDblClick = vi.fn()
    const canvas = document.createElement('canvas')
    canvas.getContext = vi.fn(() => createMockCanvasContext())
    canvas.getBoundingClientRect = vi.fn(() => ({
      left: 0,
      top: 0,
      width: 800,
      height: 600,
      right: 800,
      bottom: 600,
    }))
    document.body.appendChild(canvas)

    const graph = createRuminationGraph(canvas, { onEdgeDblClick })
    const nodes = [
      { id: 'hub', nodeType: 'hub', label: '中心', x: 0, y: 0, fixed: true },
      { id: 'fruit', nodeType: 'core', term: 'fruit', x: 100, y: 150, width: 80, height: 36, fixed: true },
      { id: 'apple', nodeType: 'core', term: 'apple', x: 300, y: 150, width: 80, height: 36, fixed: true },
    ]
    const edges = [
      { source: 'fruit', target: 'apple', label: '' },
    ]
    graph.setData({ nodes, edges })
    const { x: tx, y: ty, scale } = graph.getTransform()

    // 连线中点在世界坐标 (200, 150)，计算对应的屏幕坐标
    const screenMidX = tx + 200 * scale
    const screenMidY = ty + 150 * scale

    const dblClickEvent = new MouseEvent('dblclick', {
      clientX: screenMidX,
      clientY: screenMidY,
      bubbles: true,
      cancelable: true,
    })
    canvas.dispatchEvent(dblClickEvent)

    expect(onEdgeDblClick).toHaveBeenCalledTimes(1)
    const [clickedEdge, meta] = onEdgeDblClick.mock.calls[0]
    expect(clickedEdge.source).toBe('fruit')
    expect(clickedEdge.target).toBe('apple')
    expect(meta.clientX).toBe(screenMidX)
    expect(meta.clientY).toBe(screenMidY)

    graph.destroy()
  })
})
