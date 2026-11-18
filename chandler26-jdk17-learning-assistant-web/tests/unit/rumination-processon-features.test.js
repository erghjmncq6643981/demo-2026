import { describe, expect, it, vi } from 'vitest'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import {
  computeNodeMetrics,
  computeEdgeRouting,
  pickEdgeAt,
  tidyUpGalaxyLayout,
  computeGalaxyLayout,
  computeMindMapLayout,
  computeNodeCollapseTogglePoint,
} from '../../public/src/features/rumination/rumination-graph-layout.js'
import { RELATION_CONFIG, drawNode } from '../../public/src/features/rumination/rumination-graph-renderer.js'
import { createRuminationNodeActions } from '../../public/src/features/rumination/rumination-node-actions.js'
import { createRuminationGraph } from '../../public/src/features/rumination/rumination-graph.js'

describe('ProcessOn-Inspired Knowledge Rumination Features', () => {
  const mockCtx = {
    font: '',
    measureText: (txt) => ({ width: String(txt || '').length * 8 }),
  }

  describe('Dim 1: Double-line Card Nodes & Smooth Bézier Routing', () => {
    it('computes modern double-line card metrics with height 38 and cornerRadius 9', () => {
      const coreNode = { id: 'carrot', nodeType: 'core', term: 'carrot', definition: '胡萝卜' }
      const metrics = computeNodeMetrics(mockCtx, coreNode)
      expect(metrics.height).toBe(38)
      expect(metrics.cornerRadius).toBe(9)
      expect(metrics.halfH).toBe(19)
      expect(metrics.width).toBeGreaterThanOrEqual(92)

      const hubNode = { id: 'hub', nodeType: 'hub', label: '生鲜采购' }
      const hubMetrics = computeNodeMetrics(mockCtx, hubNode)
      expect(hubMetrics.height).toBe(42)
      expect(hubMetrics.cornerRadius).toBe(12)
    })

    it('computes edge routing intersecting rectangular card boundaries instead of node centers', () => {
      const source = { id: 'hub', nodeType: 'hub', x: 0, y: 0, width: 120, height: 42 }
      const target = { id: 'apple', nodeType: 'core', x: 200, y: 0, width: 100, height: 38 }
      const edge = { source: 'hub', target: 'apple', relationType: 'peer' }

      const route = computeEdgeRouting(source, target, edge)
      expect(route.isCurved).toBe(true)
      // 起点应当位于 source 卡片右侧边缘 (x = 60)，而不是中心 (x = 0)
      expect(route.startX).toBeCloseTo(60, 0)
      expect(route.startY).toBeCloseTo(0, 0)
      // 终点应当位于 target 卡片左侧边缘 (x = 200 - 50 = 150)，而不是中心 (x = 200)
      expect(route.endX).toBeCloseTo(148, 0)
      expect(route.endY).toBeCloseTo(0, 0)
      // 拥有贝塞尔控制点与中点
      expect(route.cp1X).toBeDefined()
      expect(route.cp2X).toBeDefined()
      expect(route.midX).toBeGreaterThan(route.startX)
      expect(route.midX).toBeLessThan(route.endX)
    })

    it('supports straight line routing when lineStyle is straight', () => {
      const source = { id: 'hub', nodeType: 'hub', x: 0, y: 0, width: 100, height: 40 }
      const target = { id: 'leaf', nodeType: 'core', x: 0, y: 200, width: 80, height: 38 }
      const edge = { source: 'hub', target: 'leaf', lineStyle: 'straight' }

      const route = computeEdgeRouting(source, target, edge)
      expect(route.isCurved).toBe(false)
      expect(route.startX).toBeCloseTo(0, 0)
      expect(route.startY).toBeCloseTo(20, 0)
      expect(route.endX).toBeCloseTo(0, 0)
      expect(route.endY).toBeCloseTo(179, 0)
      expect(route.midY).toBeCloseTo((route.startY + route.endY) / 2, 0)
    })

    it('accurately picks edges along smooth curved trajectory', () => {
      const s = { id: 's', x: 0, y: 0, width: 80, height: 38 }
      const t = { id: 't', x: 200, y: 100, width: 80, height: 38 }
      const edge = { source: 's', target: 't', sourceNode: s, targetNode: t, relationType: 'action' }
      const edges = [edge]

      const route = computeEdgeRouting(s, t, edge)
      // 点击在曲线中点位置附近
      const hitMid = pickEdgeAt({ edges, x: route.midX, y: route.midY, scale: 1, tolerance: 16 })
      expect(hitMid).toBe(edge)

      // 点击在远离连线的空白处
      const miss = pickEdgeAt({ edges, x: 500, y: 500, scale: 1, tolerance: 16 })
      expect(miss).toBeNull()
    })
  })

  describe('Dim 2: Semantic Markers & Arrow Diversity', () => {
    it('provides semantic configurations for relation types', () => {
      expect(RELATION_CONFIG.peer.label).toBe('同类')
      expect(RELATION_CONFIG.action.label).toBe('动作')
      expect(RELATION_CONFIG.attribute.label).toBe('感官')
      expect(RELATION_CONFIG.downstream.label).toBe('加工')
    })
  })

  describe('Dim 3: In-place Contextual Edge Action Bar & Maintenance', () => {
    function setupActionBarFixture() {
      const root = document.createElement('div')
      root.innerHTML = `
        <div id="ruminationCanvasWrap" style="position: relative; width: 800px; height: 600px;">
          <canvas id="ruminationGraphCanvas" width="800" height="600"></canvas>
          <div id="nodeActionBar" class="hidden">
            <span id="actionBarNodeTerm"></span>
          </div>
          <div id="ruminationEdgeActionBar" class="hidden">
            <span id="edgeBarRelationLabel"></span>
            <button id="edgeBarToggleCurveBtn"><span id="edgeBarCurveText"></span></button>
            <button id="edgeBarToggleArrowBtn"><span id="edgeBarArrowText"></span></button>
            <button id="edgeBarEditLabelBtn"><span id="edgeBarLabelText"></span></button>
            <button id="edgeBarDeleteBtn"></button>
            <button id="edgeBarClearFocusBtn"></button>
          </div>
          <button id="graphTidyBtn"></button>
          <div id="ruminationEdgeInlineEditor" class="hidden">
            <input id="edgeInlineInput" />
            <button id="edgeInlineConfirmBtn"></button>
            <button id="edgeInlineClearBtn"></button>
            <div id="edgeInlineQuickTags">
              <button class="edge-mini-tag" data-tag="搭配"></button>
            </div>
          </div>
          <div id="graphContextMenu" class="hidden">
            <button id="ctxExpandNodeBtn"></button>
            <button id="ctxAddChildBtn"><span id="ctxAddChildText"></span></button>
            <button id="ctxEditNodeBtn"><span></span><span id="ctxEditText"></span></button>
            <button id="ctxToggleBagBtn"><span id="ctxToggleBagText"></span></button>
            <button id="ctxDeleteNodeBtn"></button>
          </div>
          <div id="nodeEditModal" class="hidden"></div>
          <div id="edgeEditModal" class="hidden">
            <span id="edgeModalSourceTerm"></span>
            <span id="edgeModalTargetTerm"></span>
            <input id="edgeModalLabelInput" />
          </div>
        </div>
      `
      document.body.appendChild(root)

      const elements = {
        canvasWrap: root.querySelector('#ruminationCanvasWrap'),
        canvas: root.querySelector('#ruminationGraphCanvas'),
        nodeActionBar: root.querySelector('#nodeActionBar'),
        actionBarNodeTerm: root.querySelector('#actionBarNodeTerm'),
        edgeActionBar: root.querySelector('#ruminationEdgeActionBar'),
        edgeBarRelationLabel: root.querySelector('#edgeBarRelationLabel'),
        edgeBarToggleCurveBtn: root.querySelector('#edgeBarToggleCurveBtn'),
        edgeBarCurveText: root.querySelector('#edgeBarCurveText'),
        edgeBarToggleArrowBtn: root.querySelector('#edgeBarToggleArrowBtn'),
        edgeBarArrowText: root.querySelector('#edgeBarArrowText'),
        edgeBarEditLabelBtn: root.querySelector('#edgeBarEditLabelBtn'),
        edgeBarLabelText: root.querySelector('#edgeBarLabelText'),
        edgeBarDeleteBtn: root.querySelector('#edgeBarDeleteBtn'),
        edgeBarClearFocusBtn: root.querySelector('#edgeBarClearFocusBtn'),
        edgeInlineEditor: root.querySelector('#ruminationEdgeInlineEditor'),
        edgeInlineInput: root.querySelector('#edgeInlineInput'),
        edgeInlineConfirmBtn: root.querySelector('#edgeInlineConfirmBtn'),
        edgeInlineClearBtn: root.querySelector('#edgeInlineClearBtn'),
        edgeInlineQuickTags: root.querySelector('#edgeInlineQuickTags'),
        tidyBtn: root.querySelector('#graphTidyBtn'),
        graphContextMenu: root.querySelector('#graphContextMenu'),
        ctxExpandNodeBtn: root.querySelector('#ctxExpandNodeBtn'),
        ctxAddChildBtn: root.querySelector('#ctxAddChildBtn'),
        ctxAddChildText: root.querySelector('#ctxAddChildText'),
        ctxEditNodeBtn: root.querySelector('#ctxEditNodeBtn'),
        ctxEditText: root.querySelector('#ctxEditText'),
        ctxToggleBagBtn: root.querySelector('#ctxToggleBagBtn'),
        ctxToggleBagText: root.querySelector('#ctxToggleBagText'),
        ctxDeleteNodeBtn: root.querySelector('#ctxDeleteNodeBtn'),
        nodeEditModal: root.querySelector('#nodeEditModal'),
        edgeEditModal: root.querySelector('#edgeEditModal'),
        edgeModalSourceTerm: root.querySelector('#edgeModalSourceTerm'),
        edgeModalTargetTerm: root.querySelector('#edgeModalTargetTerm'),
        edgeModalLabelInput: root.querySelector('#edgeModalLabelInput'),
      }

      const graphData = {
        nodes: [
          { id: 'hub', nodeType: 'hub', label: '中心', x: 0, y: 0 },
          { id: 'soup', nodeType: 'core', term: 'soup', x: 100, y: 100 },
          { id: 'boil', nodeType: 'expansion', term: 'boil', x: 200, y: 100 },
        ],
        edges: [
          { source: 'hub', target: 'soup', relationType: 'peer' },
          { source: 'soup', target: 'boil', relationType: 'action', label: '制作动作' },
        ],
      }

      const mockStore = {
        getGraphData: vi.fn(() => graphData),
        findNode: (id) => graphData.nodes.find((n) => n.id === id),
        getSelectedNodeIds: vi.fn(() => []),
        setSelectedNodeIds: vi.fn(),
        getActiveFocusNodeId: vi.fn(() => null),
        setActiveFocus: vi.fn(),
      }

      const mockGraph = {
        setData: vi.fn(),
        setSelectedNodes: vi.fn(),
        setSelectedEdge: vi.fn(),
        tidyLayout: vi.fn(() => graphData.nodes),
      }

      const toast = vi.fn()
      const saveCurrentGraphDebounced = vi.fn()

      const actions = createRuminationNodeActions({
        elements,
        graph: mockGraph,
        toast,
        store: mockStore,
        saveCurrentGraphDebounced,
      })

      return { root, elements, graphData, mockGraph, mockStore, toast, saveCurrentGraphDebounced, actions }
    }

    it('opens and closes edge action bar, displaying correct relation label and state', () => {
      const { elements, graphData, actions } = setupActionBarFixture()
      const testEdge = graphData.edges[1]

      actions.syncEdgeActionBar(testEdge)
      expect(elements.edgeActionBar.classList.contains('hidden')).toBe(false)
      expect(elements.edgeBarRelationLabel.textContent).toBe('制作动作')
      expect(elements.edgeBarCurveText.textContent).toBe('∿ 曲线')
      expect(elements.edgeBarArrowText.textContent).toBe('➔ 箭头')

      actions.closeEdgeActionBar()
      expect(elements.edgeActionBar.classList.contains('hidden')).toBe(true)
    })

    it('toggles curve and straight line on click and computes straight edge routing', () => {
      const { elements, graphData, actions, saveCurrentGraphDebounced, toast } = setupActionBarFixture()
      const testEdge = graphData.edges[1]
      const soupNode = graphData.nodes[1]
      const boilNode = graphData.nodes[2]

      actions.syncEdgeActionBar(testEdge)
      elements.edgeBarToggleCurveBtn.click()

      expect(testEdge.isCurved).toBe(false)
      expect(testEdge.lineStyle).toBe('straight')
      expect(elements.edgeBarCurveText.textContent).toBe('― 直线')
      // 验证连线实际几何路由严格变为直线形态 (isCurved = false)
      const straightRoute = computeEdgeRouting(soupNode, boilNode, testEdge)
      expect(straightRoute.isCurved).toBe(false)
      expect(saveCurrentGraphDebounced).toHaveBeenCalledWith(true)
      expect(toast).toHaveBeenCalledWith('― 已切换为直线连线')

      elements.edgeBarToggleCurveBtn.click()
      expect(testEdge.isCurved).toBe(true)
      expect(testEdge.lineStyle).toBe('curve')
      expect(elements.edgeBarCurveText.textContent).toBe('∿ 曲线')
      const curvedRoute = computeEdgeRouting(soupNode, boilNode, testEdge)
      expect(curvedRoute.isCurved).toBe(true)
      expect(toast).toHaveBeenCalledWith('∿ 已切换为平滑贝塞尔曲线')
    })

    it('opens in-place inline editor on clicking text button without any modal dialog popup', () => {
      const { elements, graphData, actions, saveCurrentGraphDebounced, toast } = setupActionBarFixture()
      const testEdge = graphData.edges[1]
      testEdge.sourceNode = graphData.nodes[1]
      testEdge.targetNode = graphData.nodes[2]

      actions.syncEdgeActionBar(testEdge)
      elements.edgeBarEditLabelBtn.click()

      // 就地悬浮输入框打开，零弹窗遮挡
      expect(elements.edgeInlineEditor.classList.contains('hidden')).toBe(false)
      expect(elements.edgeInlineInput.value).toBe('制作动作')

      // 实时修改为新文本并点击确认按钮
      elements.edgeInlineInput.value = '精心炖煮'
      elements.edgeInlineConfirmBtn.click()

      expect(testEdge.label).toBe('精心炖煮')
      expect(elements.edgeBarRelationLabel.textContent).toBe('精心炖煮')
      expect(elements.edgeInlineEditor.classList.contains('hidden')).toBe(true)
      expect(saveCurrentGraphDebounced).toHaveBeenCalledWith(true)
      expect(toast).toHaveBeenCalledWith('已更新连线关系文字为【精心炖煮】')

      // 再次打开并通过快捷便签快速填入
      actions.openEdgeInlineEditor(testEdge)
      expect(elements.edgeInlineEditor.classList.contains('hidden')).toBe(false)
      const tagBtn = elements.edgeInlineQuickTags.querySelector('[data-tag="搭配"]')
      tagBtn.click()

      expect(testEdge.label).toBe('搭配')
      expect(elements.edgeInlineEditor.classList.contains('hidden')).toBe(true)
      expect(toast).toHaveBeenCalledWith('已更新连线关系文字为【搭配】')
    })

    it('toggles arrow endpoint on click', () => {
      const { elements, graphData, actions, saveCurrentGraphDebounced, toast } = setupActionBarFixture()
      const testEdge = graphData.edges[1]

      actions.syncEdgeActionBar(testEdge)
      elements.edgeBarToggleArrowBtn.click()

      expect(testEdge.showArrow).toBe(false)
      expect(elements.edgeBarArrowText.textContent).toBe('• 端点')
      expect(toast).toHaveBeenCalledWith('• 已切换为极简端点')

      elements.edgeBarToggleArrowBtn.click()
      expect(testEdge.showArrow).toBe(true)
      expect(elements.edgeBarArrowText.textContent).toBe('➔ 箭头')
      expect(toast).toHaveBeenCalledWith('➔ 已开启关系端点箭头')
    })

    it('deletes selected edge via action bar button', () => {
      const { elements, graphData, actions, mockGraph, saveCurrentGraphDebounced, toast } = setupActionBarFixture()
      const testEdge = graphData.edges[1]

      actions.syncEdgeActionBar(testEdge)
      elements.edgeBarDeleteBtn.click()

      expect(graphData.edges.some((e) => e.target === 'boil')).toBe(false)
      expect(mockGraph.setSelectedEdge).toHaveBeenCalledWith(null)
      expect(saveCurrentGraphDebounced).toHaveBeenCalledWith(true)
      expect(toast).toHaveBeenCalledWith('已删除所选连线')
      expect(elements.edgeActionBar.classList.contains('hidden')).toBe(true)
    })
  })

  describe('Dim 4: One-Click Intelligent Graph Tidy-Up', () => {
    it('tidyUpGalaxyLayout cleans distorted coordinates and places all nodes with fixed true', () => {
      const messyData = {
        nodes: [
          { id: 'hub', nodeType: 'hub', label: '中心', x: 999, y: 999 },
          { id: 'a', nodeType: 'core', term: 'apple', x: 2, y: 3 },
          { id: 'b', nodeType: 'core', term: 'banana', x: 2, y: 4 },
        ],
        edges: [
          { source: 'hub', target: 'a' },
          { source: 'hub', target: 'b' },
        ],
      }

      const tidied = tidyUpGalaxyLayout(mockCtx, messyData)
      expect(tidied.nodes.length).toBe(3)
      const hub = tidied.nodes.find((n) => n.nodeType === 'hub')
      expect(hub.x).toBe(0)
      expect(hub.y).toBe(0)

      const a = tidied.nodes.find((n) => n.id === 'a')
      const b = tidied.nodes.find((n) => n.id === 'b')
      expect(a.fixed).toBe(true)
      expect(b.fixed).toBe(true)
      // 两节点应当被等距展开，不再重叠 (2,3) 和 (2,4)
      const dist = Math.hypot(a.x - b.x, a.y - b.y)
      expect(dist).toBeGreaterThan(80)
    })
  })

  describe('Dim 5: Deep Multi-level Knowledge Tree & Universal Node Expansion', () => {
    it('supports 3+ levels in BFS radial tree placement', () => {
      const multiLevelData = {
        nodes: [
          { id: 'hub', nodeType: 'hub', label: 'Kitchen' },
          { id: 'cook', nodeType: 'core', term: 'cook' },
          { id: 'bake', nodeType: 'expansion', term: 'bake' },
          { id: 'oven', nodeType: 'expansion', term: 'oven' },
        ],
        edges: [
          { source: 'hub', target: 'cook' },
          { source: 'cook', target: 'bake' },
          { source: 'bake', target: 'oven' },
        ],
      }

      const layout = computeGalaxyLayout(mockCtx, multiLevelData)
      const hub = layout.placedNodes.find((n) => n.id === 'hub')
      const cook = layout.placedNodes.find((n) => n.id === 'cook')
      const bake = layout.placedNodes.find((n) => n.id === 'bake')
      const oven = layout.placedNodes.find((n) => n.id === 'oven')

      expect(cook.level).toBeGreaterThanOrEqual(1)
      expect(bake.level).toBeGreaterThan(cook.level)
      expect(oven.level).toBeGreaterThan(bake.level)

      const cookDist = Math.hypot(cook.x, cook.y)
      const bakeDist = Math.hypot(bake.x, bake.y)
      const ovenDist = Math.hypot(oven.x, oven.y)
      expect(bakeDist).toBeGreaterThan(cookDist)
      expect(ovenDist).toBeGreaterThan(bakeDist)
    })

    it('displays expansion item in context menu for both core and expansion nodes', () => {
      const root = document.createElement('div')
      root.innerHTML = `
        <div id="graphContextMenu" class="hidden">
          <button id="ctxExpandNodeBtn"></button>
          <button id="ctxAddChildBtn"><span id="ctxAddChildText"></span></button>
          <button id="ctxEditNodeBtn"><span></span><span id="ctxEditText"></span></button>
          <button id="ctxToggleBagBtn"><span id="ctxToggleBagText"></span></button>
          <button id="ctxDeleteNodeBtn"></button>
        </div>
      `
      document.body.appendChild(root)
      const elements = {
        graphContextMenu: root.querySelector('#graphContextMenu'),
        ctxExpandNodeBtn: root.querySelector('#ctxExpandNodeBtn'),
        ctxAddChildBtn: root.querySelector('#ctxAddChildBtn'),
        ctxAddChildText: root.querySelector('#ctxAddChildText'),
        ctxEditNodeBtn: root.querySelector('#ctxEditNodeBtn'),
        ctxEditText: root.querySelector('#ctxEditText'),
        ctxToggleBagBtn: root.querySelector('#ctxToggleBagBtn'),
        ctxToggleBagText: root.querySelector('#ctxToggleBagText'),
        ctxDeleteNodeBtn: root.querySelector('#ctxDeleteNodeBtn'),
      }

      const actions = createRuminationNodeActions({
        elements,
        store: { getGraphData: () => ({ nodes: [], edges: [] }), getSelectedNodeIds: () => [] },
      })

      // Core 节点
      actions.openContextMenu({ id: 'c1', nodeType: 'core', term: 'apple' }, { clientX: 100, clientY: 100 })
      expect(elements.ctxExpandNodeBtn.style.display).toBe('flex')

      // Expansion 二级/深层节点，同样可触发裂变展开！
      actions.openContextMenu({ id: 'e1', nodeType: 'expansion', term: 'ripe' }, { clientX: 100, clientY: 100 })
      expect(elements.ctxExpandNodeBtn.style.display).toBe('flex')

      // Hub 中心节点不显示裂变展开
      actions.openContextMenu({ id: 'hub', nodeType: 'hub', label: '中心' }, { clientX: 100, clientY: 100 })
      expect(elements.ctxExpandNodeBtn.style.display).toBe('none')
    })
  })

  describe('Dim 6: Node Border Style Customization (Default Solid, Supports Modification)', () => {
    it('defaults to solid border line for both core and expansion nodes without line dashes', () => {
      const lineDashes = []
      const mockCanvasCtx = {
        save: vi.fn(),
        restore: vi.fn(),
        translate: vi.fn(),
        beginPath: vi.fn(),
        closePath: vi.fn(),
        roundRect: vi.fn(),
        fill: vi.fn(),
        stroke: vi.fn(),
        fillText: vi.fn(),
        measureText: () => ({ width: 40 }),
        setLineDash: vi.fn((dash) => lineDashes.push([...dash])),
      }

      const coreNode = { id: 'c1', nodeType: 'core', term: 'apple', x: 0, y: 0, width: 90, height: 38 }
      drawNode(mockCanvasCtx, coreNode, { selectedNodeIds: new Set() })
      // 默认实线：setLineDash 传入空数组 []
      expect(lineDashes).toContainEqual([])

      lineDashes.length = 0
      const expNode = { id: 'e1', nodeType: 'expansion', term: 'ripe', x: 100, y: 0, width: 90, height: 38 }
      drawNode(mockCanvasCtx, expNode, { selectedNodeIds: new Set() })
      // 衍生节点也默认实线，不再硬编码虚线
      expect(lineDashes).toContainEqual([])
    })

    it('renders dashed, dotted, and dashdot border styles when specified on node', () => {
      const lineDashes = []
      const mockCanvasCtx = {
        save: vi.fn(),
        restore: vi.fn(),
        translate: vi.fn(),
        beginPath: vi.fn(),
        closePath: vi.fn(),
        roundRect: vi.fn(),
        fill: vi.fn(),
        stroke: vi.fn(),
        fillText: vi.fn(),
        measureText: () => ({ width: 40 }),
        setLineDash: vi.fn((dash) => lineDashes.push([...dash])),
      }

      const dashedNode = { id: 'n1', nodeType: 'core', term: 'carrot', borderStyle: 'dashed', x: 0, y: 0, width: 90, height: 38 }
      drawNode(mockCanvasCtx, dashedNode, { selectedNodeIds: new Set() })
      expect(lineDashes).toContainEqual([5, 4])

      lineDashes.length = 0
      const dottedNode = { id: 'n2', nodeType: 'expansion', term: 'chop', borderStyle: 'dotted', x: 0, y: 0, width: 90, height: 38 }
      drawNode(mockCanvasCtx, dottedNode, { selectedNodeIds: new Set() })
      expect(lineDashes).toContainEqual([2, 3])

      lineDashes.length = 0
      const dashdotNode = { id: 'n3', nodeType: 'core', term: 'onion', borderStyle: 'dashdot', x: 0, y: 0, width: 90, height: 38 }
      drawNode(mockCanvasCtx, dashdotNode, { selectedNodeIds: new Set() })
      expect(lineDashes).toContainEqual([6, 3, 2, 3])
    })

    it('modifies node border style via action bar, updates UI indicator, and saves graph', () => {
      const root = document.createElement('div')
      root.innerHTML = `
        <div id="ruminationNodeActionBar" class="hidden">
          <span id="actionBarNodeTerm"></span>
          <button id="barBorderStyleBtn">
            <span id="barBorderStyleIcon" class="border-style-indicator solid-indicator"></span>
            <span id="barBorderStyleText">实线</span>
          </button>
          <div id="nodeBorderStyleMenu" class="hidden">
            <button class="border-style-menu-item active" data-style="solid"><span class="menu-check">✓</span></button>
            <button class="border-style-menu-item" data-style="dashed"><span class="menu-check">✓</span></button>
            <button class="border-style-menu-item" data-style="dotted"><span class="menu-check">✓</span></button>
            <button class="border-style-menu-item" data-style="dashdot"><span class="menu-check">✓</span></button>
          </div>
        </div>
      `
      document.body.appendChild(root)
      const graphData = {
        nodes: [{ id: 'apple', term: 'apple', nodeType: 'core', borderStyle: 'solid' }],
        edges: [],
      }
      const mockGraph = {
        setData: vi.fn(),
        requestRedraw: vi.fn(),
      }
      const mockToast = vi.fn()
      const mockSave = vi.fn()

      const elements = {
        nodeActionBar: root.querySelector('#ruminationNodeActionBar'),
        actionBarNodeTerm: root.querySelector('#actionBarNodeTerm'),
        barBorderStyleBtn: root.querySelector('#barBorderStyleBtn'),
        barBorderStyleIcon: root.querySelector('#barBorderStyleIcon'),
        barBorderStyleText: root.querySelector('#barBorderStyleText'),
        nodeBorderStyleMenu: root.querySelector('#nodeBorderStyleMenu'),
      }

      const actions = createRuminationNodeActions({
        elements,
        graph: mockGraph,
        store: { getGraphData: () => graphData, findNode: (id) => graphData.nodes.find((n) => n.id === id) },
        getGraphData: () => graphData,
        getActiveFocusNodeId: () => 'apple',
        toast: mockToast,
        saveCurrentGraphDebounced: mockSave,
      })

      // 切换边框样式为虚线
      const target = graphData.nodes[0]
      actions.changeNodeBorderStyle(target, 'dashed')

      expect(target.borderStyle).toBe('dashed')
      expect(elements.barBorderStyleText.textContent).toBe('虚线')
      expect(elements.barBorderStyleIcon.className).toContain('dashed-indicator')
      expect(mockGraph.requestRedraw).toHaveBeenCalled()
      expect(mockSave).toHaveBeenCalledWith(true)
      expect(mockToast).toHaveBeenCalledWith(expect.stringContaining('虚线'))

      // 切换为点线
      actions.changeNodeBorderStyle(target, 'dotted')
      expect(target.borderStyle).toBe('dotted')
      expect(elements.barBorderStyleText.textContent).toBe('点线')
      expect(elements.barBorderStyleIcon.className).toContain('dotted-indicator')
    })
  })

  describe('Dim 7: Strict Action Ordering Alignment (关联 -> 边框 -> 编辑 -> 词袋 -> 裂变 -> 删除)', () => {
    it('verifies exact DOM button sequence in context menu and action bar', () => {
      const currentDir = path.dirname(fileURLToPath(import.meta.url))
      const htmlPath = path.resolve(currentDir, '../../templates/views/rumination.html')
      const html = fs.readFileSync(htmlPath, 'utf-8')

      // 验证上下文菜单 #graphContextMenu 中的按钮出现顺序
      const ctxOrder = [
        'id="ctxAddChildBtn"',
        'id="ctxBorderStyleBtn"',
        'id="ctxEditNodeBtn"',
        'id="ctxToggleBagBtn"',
        'id="ctxExpandNodeBtn"',
        'id="ctxDeleteNodeBtn"',
      ]
      let lastIndex = -1
      for (const idStr of ctxOrder) {
        const index = html.indexOf(idStr)
        expect(index).toBeGreaterThan(lastIndex)
        lastIndex = index
      }

      // 验证已彻底移除中间悬浮操作栏 #ruminationNodeActionBar，防止 Tab 抢占焦点
      expect(html).not.toContain('id="ruminationNodeActionBar"')

      // 验证 Tab / A 键作为添加子节点的高频快捷键提示
      expect(html).toContain('title="新增关联节点 (Tab / A)"')
      expect(html).toContain('A 加关联')
    })
  })

  describe('Dim 8: 4-Directional Mind Map Tree Layout (方案1)', () => {
    it('positions tree nodes in 4 clean quadrants without overlapping (Up, Down, Left, Right)', () => {
      const roomGraph = {
        nodes: [
          { id: 'hub', nodeType: 'hub', term: 'room', label: '🏠 房间格局与起居空间' },
          { id: 'types_of_rooms', nodeType: 'core', term: 'types of rooms', label: '房间类型' },
          { id: 'parts_of_rooms', nodeType: 'core', term: 'parts of rooms', label: '房间组成' },
          { id: 'actions_descriptions', nodeType: 'core', term: 'actions & descriptions', label: '动作与描述' },
          { id: 'common_items', nodeType: 'core', term: 'common items in a room', label: '房间设备及用品' },
          // Types of rooms children (Up)
          { id: 'bedroom_type', nodeType: 'expansion', term: 'bedroom' },
          { id: 'kitchen_type', nodeType: 'expansion', term: 'kitchen' },
          // Parts of rooms children (Down)
          { id: 'door', nodeType: 'expansion', term: 'door' },
          { id: 'window', nodeType: 'expansion', term: 'window' },
          // Actions children (Left)
          { id: 'clean', nodeType: 'expansion', term: 'clean' },
          // Items children (Right)
          { id: 'living_room_item', nodeType: 'expansion', term: 'living room' },
          { id: 'sofa', nodeType: 'expansion', term: 'sofa' },
        ],
        edges: [
          { source: 'hub', target: 'types_of_rooms' },
          { source: 'hub', target: 'parts_of_rooms' },
          { source: 'hub', target: 'actions_descriptions' },
          { source: 'hub', target: 'common_items' },
          { source: 'types_of_rooms', target: 'bedroom_type' },
          { source: 'types_of_rooms', target: 'kitchen_type' },
          { source: 'parts_of_rooms', target: 'door' },
          { source: 'parts_of_rooms', target: 'window' },
          { source: 'actions_descriptions', target: 'clean' },
          { source: 'common_items', target: 'living_room_item' },
          { source: 'living_room_item', target: 'sofa' },
        ],
      }

      const layout = computeMindMapLayout(mockCtx, roomGraph)
      const hub = layout.placedNodes.find((n) => n.id === 'hub')
      const types = layout.placedNodes.find((n) => n.id === 'types_of_rooms')
      const parts = layout.placedNodes.find((n) => n.id === 'parts_of_rooms')
      const actions = layout.placedNodes.find((n) => n.id === 'actions_descriptions')
      const items = layout.placedNodes.find((n) => n.id === 'common_items')

      expect(hub.x).toBe(0)
      expect(hub.y).toBe(0)

      // 验证四大象限独立分布且绝对无交叉
      expect(types.y).toBeLessThan(0) // UP
      expect(parts.y).toBeGreaterThan(0) // DOWN
      expect(actions.x).toBeLessThan(0) // LEFT
      expect(items.x).toBeGreaterThan(0) // RIGHT

      // 验证深层子节点的单向辐射
      const bedroomType = layout.placedNodes.find((n) => n.id === 'bedroom_type')
      expect(bedroomType.y).toBeLessThan(types.y)

      const door = layout.placedNodes.find((n) => n.id === 'door')
      expect(door.y).toBeGreaterThan(parts.y)

      const clean = layout.placedNodes.find((n) => n.id === 'clean')
      expect(clean.x).toBeLessThan(actions.x)

      const sofa = layout.placedNodes.find((n) => n.id === 'sofa')
      const livingRoom = layout.placedNodes.find((n) => n.id === 'living_room_item')
      expect(livingRoom.x).toBeGreaterThan(items.x)
      expect(sofa.x).toBeGreaterThan(livingRoom.x)
    })

    it('tidyUpGalaxyLayout automatically delegates to computeMindMapLayout when tree hierarchy exists', () => {
      const treeGraph = {
        nodes: [
          { id: 'hub', nodeType: 'hub', term: 'appliances' },
          { id: 'pc', nodeType: 'core', term: 'PC' },
          { id: 'laptop', nodeType: 'expansion', term: 'laptop' },
          { id: 'stove', nodeType: 'core', term: 'stove' },
          { id: 'gas_stove', nodeType: 'expansion', term: 'gas stove' },
          { id: 'dish', nodeType: 'core', term: 'dish' },
        ],
        edges: [
          { source: 'hub', target: 'pc' },
          { source: 'pc', target: 'laptop', relationType: 'downstream' },
          { source: 'hub', target: 'stove' },
          { source: 'stove', target: 'gas_stove', relationType: 'downstream' },
          { source: 'hub', target: 'dish' },
        ],
      }
      const tidied = tidyUpGalaxyLayout(mockCtx, treeGraph)
      expect(tidied.nodes.length).toBe(6)
      const hub = tidied.nodes.find((n) => n.id === 'hub')
      expect(hub.x).toBe(0)
      expect(hub.y).toBe(0)
      const laptop = tidied.nodes.find((n) => n.id === 'laptop')
      const pc = tidied.nodes.find((n) => n.id === 'pc')
      expect(laptop.fixed).toBe(true)
      expect(Math.abs(laptop.x - pc.x)).toBeGreaterThan(30)
    })
  })

  describe('Dim 9: Node Branch Collapse & Expand (+/- 交互) (方案3)', () => {
    it('computeNodeCollapseTogglePoint correctly docks to edge based on node direction', () => {
      const nodeRight = { width: 100, height: 40, direction: 'right' }
      const ptRight = computeNodeCollapseTogglePoint(nodeRight)
      expect(ptRight.dir).toBe('right')
      expect(ptRight.x).toBe(60) // 100/2 + 10
      expect(ptRight.y).toBe(0)

      const nodeLeft = { width: 100, height: 40, direction: 'left' }
      const ptLeft = computeNodeCollapseTogglePoint(nodeLeft)
      expect(ptLeft.dir).toBe('left')
      expect(ptLeft.x).toBe(-60)
      expect(ptLeft.y).toBe(0)

      const nodeUp = { width: 100, height: 40, direction: 'up' }
      const ptUp = computeNodeCollapseTogglePoint(nodeUp)
      expect(ptUp.dir).toBe('up')
      expect(ptUp.x).toBe(0)
      expect(ptUp.y).toBe(-30) // -40/2 - 10

      const nodeDown = { width: 100, height: 40, direction: 'down' }
      const ptDown = computeNodeCollapseTogglePoint(nodeDown)
      expect(ptDown.dir).toBe('down')
      expect(ptDown.x).toBe(0)
      expect(ptDown.y).toBe(30)
    })

    it('collapses and expands node branches, hiding and restoring child elements', () => {
      const origResizeObserver = globalThis.ResizeObserver
      globalThis.ResizeObserver = class {
        observe() {}
        disconnect() {}
      }

      const canvas = document.createElement('canvas')
      canvas.width = 800
      canvas.height = 600
      canvas.getContext = vi.fn(() => ({
        clearRect: vi.fn(),
        save: vi.fn(),
        restore: vi.fn(),
        translate: vi.fn(),
        scale: vi.fn(),
        beginPath: vi.fn(),
        moveTo: vi.fn(),
        lineTo: vi.fn(),
        arc: vi.fn(),
        fill: vi.fn(),
        stroke: vi.fn(),
        fillRect: vi.fn(),
        fillText: vi.fn(),
        roundRect: vi.fn(),
        createLinearGradient: vi.fn(() => ({ addColorStop: vi.fn() })),
        createRadialGradient: vi.fn(() => ({ addColorStop: vi.fn() })),
        measureText: vi.fn((text) => ({ width: String(text || '').length * 8 })),
        setLineDash: vi.fn(),
        setTransform: vi.fn(),
      }))
      canvas.getBoundingClientRect = () => ({ left: 0, top: 0, width: 800, height: 600 })
      document.body.appendChild(canvas)

      let toastMsg = ''
      const graph = createRuminationGraph(canvas, {
        onToast: (msg) => { toastMsg = msg },
      })

      graph.setData({
        nodes: [
          { id: 'hub', nodeType: 'hub', term: 'room', x: 0, y: 0 },
          { id: 'kitchen', nodeType: 'core', term: 'kitchen', x: 100, y: 0 },
          { id: 'pot', nodeType: 'expansion', term: 'pot', x: 200, y: 0 },
          { id: 'pan', nodeType: 'expansion', term: 'pan', x: 200, y: 50 },
        ],
        edges: [
          { source: 'hub', target: 'kitchen' },
          { source: 'kitchen', target: 'pot' },
          { source: 'kitchen', target: 'pan' },
        ],
      })

      expect(graph.isNodeCollapsed('kitchen')).toBe(false)
      graph.toggleCollapse('kitchen')
      expect(graph.isNodeCollapsed('kitchen')).toBe(true)
      expect(toastMsg).toContain('已折叠「kitchen」分支 (2 个词汇)')

      graph.toggleCollapse('kitchen')
      expect(graph.isNodeCollapsed('kitchen')).toBe(false)
      expect(toastMsg).toContain('已展开「kitchen」分支')

      graph.collapseAll()
      expect(graph.isNodeCollapsed('kitchen')).toBe(true)

      graph.expandAll()
      expect(graph.isNodeCollapsed('kitchen')).toBe(false)

      graph.destroy()
      globalThis.ResizeObserver = origResizeObserver
    })
  })

  describe('Dim 10: Direct Enter Key Submission in Add/Edit Node Modal', () => {
    it('submits add/edit node modal directly when pressing Enter in term or definition input', () => {
      const root = document.createElement('div')
      root.innerHTML = `
        <div id="ruminationNodeEditModal" class="hidden">
          <h3 id="nodeModalTitle"></h3>
          <div id="nodeModalParentInfo"><strong id="nodeModalParentTerm"></strong></div>
          <label><input id="nodeModalTermInput" type="text" /></label>
          <label><input id="nodeModalDefInput" type="text" /></label>
          <label><select id="nodeModalDimensionSelect">
            <option value="action">动作</option>
            <option value="peer">同类</option>
          </select></label>
          <button id="cancelNodeModalBtn" type="button">取消</button>
          <button id="submitNodeModalBtn" type="button">保存</button>
        </div>
      `
      document.body.appendChild(root)

      const graphData = {
        nodes: [
          { id: 'hub', nodeType: 'hub', term: '果蔬', x: 0, y: 0 },
          { id: 'berry', nodeType: 'expansion', term: 'berry', definition: '浆果', x: 200, y: 100 },
        ],
        edges: [{ source: 'hub', target: 'berry', relationType: 'peer' }],
      }
      const mockGraph = { setData: vi.fn(), requestRedraw: vi.fn() }
      const mockToast = vi.fn()
      const mockSave = vi.fn()

      const elements = {
        nodeEditModal: root.querySelector('#ruminationNodeEditModal'),
        nodeModalTitle: root.querySelector('#nodeModalTitle'),
        nodeModalParentInfo: root.querySelector('#nodeModalParentInfo'),
        nodeModalParentTerm: root.querySelector('#nodeModalParentTerm'),
        nodeModalTermInput: root.querySelector('#nodeModalTermInput'),
        nodeModalDefInput: root.querySelector('#nodeModalDefInput'),
        nodeModalDimensionSelect: root.querySelector('#nodeModalDimensionSelect'),
        cancelNodeModalBtn: root.querySelector('#cancelNodeModalBtn'),
        submitNodeModalBtn: root.querySelector('#submitNodeModalBtn'),
      }

      const actions = createRuminationNodeActions({
        elements,
        graph: mockGraph,
        store: {
          getGraphData: () => graphData,
          findNode: (id) => graphData.nodes.find((n) => n.id === id),
        },
        getGraphData: () => graphData,
        getActiveFocusNodeId: () => 'berry',
        toast: mockToast,
        saveCurrentGraphDebounced: mockSave,
      })

      // 1. 新增节点弹窗中在中文释义输入框按 Enter 直接提交
      actions.handleNodeAction('addChild', graphData.nodes[1])
      expect(elements.nodeEditModal.classList.contains('hidden')).toBe(false)

      elements.nodeModalTermInput.value = 'strawberry'
      elements.nodeModalDefInput.value = '草莓'
      elements.nodeModalDimensionSelect.value = 'peer'

      const enterEvent = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      elements.nodeModalDefInput.dispatchEvent(enterEvent)

      expect(enterEvent.defaultPrevented).toBe(true)
      expect(elements.nodeEditModal.classList.contains('hidden')).toBe(true)
      expect(graphData.nodes.some((n) => n.term === 'strawberry' && n.definition === '草莓')).toBe(true)
      expect(mockSave).toHaveBeenCalledWith(true, expect.objectContaining({ changeSummary: '新增节点 strawberry' }))

      // 2. 编辑节点弹窗中按 Enter 直接提交更新
      const addedNode = graphData.nodes.find((n) => n.term === 'strawberry')
      actions.handleNodeAction('edit', addedNode)
      expect(elements.nodeEditModal.classList.contains('hidden')).toBe(false)

      elements.nodeModalDefInput.value = '新鲜草莓'
      const editEnterEvent = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })
      elements.nodeModalDefInput.dispatchEvent(editEnterEvent)

      expect(editEnterEvent.defaultPrevented).toBe(true)
      expect(elements.nodeEditModal.classList.contains('hidden')).toBe(true)
      expect(addedNode.definition).toBe('新鲜草莓')
    })
  })
})
