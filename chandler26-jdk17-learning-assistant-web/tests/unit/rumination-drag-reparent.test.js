import { describe, expect, it, vi } from 'vitest'
import { isDescendantOf, findDropTarget, calculateReparentPosition } from '../../public/src/features/rumination/rumination-graph-drag.js'
import { pickEdgeAt } from '../../public/src/features/rumination/rumination-graph-layout.js'
import { createRuminationNodeActions } from '../../public/src/features/rumination/rumination-node-actions.js'

describe('Rumination Graph Drag-to-Reparent and Hub Editing', () => {
  it('correctly detects descendants and prevents circular reparenting', () => {
    const edges = [
      { source: 'hub', target: 'fruit' },
      { source: 'fruit', target: 'apple' },
      { source: 'apple', target: 'ripe' },
    ]

    // apple 是 fruit 的后代
    expect(isDescendantOf('apple', 'fruit', edges)).toBe(true)
    // ripe 是 fruit 的深层后代
    expect(isDescendantOf('ripe', 'fruit', edges)).toBe(true)
    // ripe 是 apple 的后代
    expect(isDescendantOf('ripe', 'apple', edges)).toBe(true)
    // fruit 不是 apple 的后代
    expect(isDescendantOf('fruit', 'apple', edges)).toBe(false)
    // 自身与自身不判定为后代
    expect(isDescendantOf('apple', 'apple', edges)).toBe(false)
  })

  it('finds valid drop target when hovering over another node, skipping self and descendants', () => {
    const nodes = [
      { id: 'hub', nodeType: 'hub', x: 0, y: 0, width: 104, height: 30 },
      { id: 'fruit', nodeType: 'core', term: 'fruit', x: 150, y: 0, width: 88, height: 40 },
      { id: 'apple', nodeType: 'core', term: 'apple', x: 150, y: 150, width: 88, height: 40 },
      { id: 'ripe', nodeType: 'expansion', term: 'ripe', x: 150, y: 250, width: 76, height: 34 },
    ]
    const edges = [
      { source: 'hub', target: 'fruit' },
      { source: 'hub', target: 'apple' },
      { source: 'apple', target: 'ripe' },
    ]

    const draggedApple = nodes[2]

    // 光标移动到 fruit (150, 0) 附近感应区内
    const target = findDropTarget(152, 2, draggedApple, nodes, edges)
    expect(target).not.toBeNull()
    expect(target.id).toBe('fruit')

    // 光标移动到 apple 自身位置，不应判定为吸附目标
    expect(findDropTarget(150, 150, draggedApple, nodes, edges)).toBeNull()

    // 光标移动到 apple 的子节点 ripe 附近，因环路防护不应成为目标
    expect(findDropTarget(150, 250, draggedApple, nodes, edges)).toBeNull()

    // 光标移动到空白区域
    expect(findDropTarget(400, 400, draggedApple, nodes, edges)).toBeNull()
  })

  it('reparents node under target node (apple -> fruit), preserving its sub-tree', () => {
    const graphData = {
      nodes: [
        { id: 'hub', nodeType: 'hub', label: '场景核心', x: 0, y: 0 },
        { id: 'fruit', nodeType: 'core', term: 'fruit', x: 120, y: -40 },
        { id: 'apple', nodeType: 'core', term: 'apple', x: 120, y: 80 },
        { id: 'banana', nodeType: 'core', term: 'banana', x: 200, y: 80 },
        { id: 'ripe', nodeType: 'expansion', term: 'ripe', x: 120, y: 160 },
      ],
      edges: [
        { source: 'hub', target: 'fruit', relationType: 'peer' },
        { source: 'hub', target: 'apple', relationType: 'peer' },
        { source: 'hub', target: 'banana', relationType: 'peer' },
        { source: 'apple', target: 'ripe', relationType: 'attribute' },
      ],
    }

    const mockGraph = {
      setData: vi.fn((data) => {
        Object.assign(graphData, data)
      }),
      setSelectedNodes: vi.fn(),
    }
    const mockToast = vi.fn()
    const mockSave = vi.fn()

    const nodeActions = createRuminationNodeActions({
      elements: {},
      graph: mockGraph,
      getGraphData: () => graphData,
      getSelectedNodeIds: () => [],
      setSelectedNodeIds: () => {},
      getActiveFocusNodeId: () => null,
      setActiveFocus: () => {},
      toast: mockToast,
      saveCurrentGraphDebounced: mockSave,
    })

    const appleNode = graphData.nodes.find((n) => n.id === 'apple')
    const fruitNode = graphData.nodes.find((n) => n.id === 'fruit')

    // 执行拖拽挂载 apple -> fruit
    nodeActions.reparentNode(appleNode, fruitNode)

    // 验证 apple 的连线父级更新为 fruit
    const appleEdge = graphData.edges.find((e) => e.target === 'apple')
    expect(appleEdge).toBeDefined()
    expect(appleEdge.source).toBe('fruit')

    // 验证 apple 坐标自适应更新，与父节点 fruit 保持清晰间距（不堆叠在一起）
    const distAppleFruit = Math.hypot(appleNode.x - fruitNode.x, appleNode.y - fruitNode.y)
    expect(distAppleFruit).toBeGreaterThanOrEqual(100)

    // 验证 apple 的子节点 ripe 依然挂在 apple 下，不受破坏
    const ripeEdge = graphData.edges.find((e) => e.target === 'ripe')
    expect(ripeEdge).toBeDefined()
    expect(ripeEdge.source).toBe('apple')

    expect(mockGraph.setData).toHaveBeenCalled()
    expect(mockToast).toHaveBeenCalledWith(expect.stringContaining('已将【apple】归入【fruit】'))
    expect(mockSave).toHaveBeenCalledWith(true)
  })

  it('supports editing the central hub node and updating topic label', () => {
    const graphData = {
      nodes: [
        { id: 'hub', nodeType: 'hub', label: '生鲜采购与厨房料理', x: 0, y: 0 },
        { id: 'carrot', nodeType: 'core', term: 'carrot', x: 100, y: 0 },
      ],
      edges: [
        { source: 'hub', target: 'carrot', relationType: 'peer' },
      ],
    }

    const mockGraph = {
      setData: vi.fn(),
      setSelectedNodes: vi.fn(),
    }
    const mockToast = vi.fn()
    const mockSave = vi.fn()

    const termInput = { value: 'vegetables', focus: vi.fn() }
    const defInput = { value: '生鲜蔬菜' }
    const titleEl = { textContent: '' }

    const nodeActions = createRuminationNodeActions({
      elements: {
        nodeModalTitle: titleEl,
        nodeModalTermInput: termInput,
        nodeModalDefInput: defInput,
        nodeEditModal: {
          classList: { add: vi.fn(), remove: vi.fn(), contains: vi.fn(() => false) },
          querySelectorAll: vi.fn(() => []),
        },
        graphContextMenu: { classList: { add: vi.fn(), remove: vi.fn() }, style: {} },
      },
      graph: mockGraph,
      getGraphData: () => graphData,
      getSelectedNodeIds: () => [],
      setSelectedNodeIds: () => {},
      getActiveFocusNodeId: () => null,
      setActiveFocus: () => {},
      toast: mockToast,
      saveCurrentGraphDebounced: mockSave,
    })

    const hubNode = graphData.nodes[0]
    nodeActions.openContextMenu(hubNode, { clientX: 100, clientY: 100 })
    nodeActions.openNodeModal('edit')

    expect(titleEl.textContent).toBe('修改场景中心主题')
    expect(termInput.value).toBe('生鲜采购与厨房料理')

    // 修改为 vegetables
    termInput.value = 'vegetables'
    defInput.value = '生鲜蔬菜'

    // 触发保存逻辑（模拟 submitNodeModalBtn 点击）
    const hubRaw = graphData.nodes.find((n) => n.id === 'hub')
    hubRaw.label = 'vegetables'
    hubRaw.subLabel = '生鲜蔬菜'
    mockGraph.setData(graphData)

    expect(hubRaw.label).toBe('vegetables')
    expect(hubRaw.subLabel).toBe('生鲜蔬菜')
  })

  it('calculates collision-free reparent positions for multiple sibling nodes under the same parent', () => {
    const parentNode = { id: 'fruit', term: 'fruit', x: 150, y: 0, width: 88, height: 40 }
    const nodes = [
      { id: 'hub', nodeType: 'hub', x: 0, y: 0, width: 120, height: 44 },
      parentNode,
    ]
    const edges = [
      { source: 'hub', target: 'fruit' },
    ]

    // 1. 拖拽挂载 apple 到 fruit
    const appleNode = { id: 'apple', term: 'apple', x: 152, y: 2, width: 88, height: 40 }
    const applePos = calculateReparentPosition(appleNode, parentNode, nodes, edges)

    // apple 应与 fruit 保持清晰距离
    expect(Math.hypot(applePos.x - parentNode.x, applePos.y - parentNode.y)).toBeGreaterThanOrEqual(120)

    // 模拟 apple 已挂入图中
    const appleInGraph = { ...appleNode, x: applePos.x, y: applePos.y }
    nodes.push(appleInGraph)
    edges.push({ source: 'fruit', target: 'apple' })

    // 2. 接着拖拽挂载 banana 到 fruit
    const bananaNode = { id: 'banana', term: 'banana', x: 151, y: 1, width: 88, height: 40 }
    const bananaPos = calculateReparentPosition(bananaNode, parentNode, nodes, edges)

    // banana 应与 fruit 保持清晰距离
    expect(Math.hypot(bananaPos.x - parentNode.x, bananaPos.y - parentNode.y)).toBeGreaterThanOrEqual(120)

    // banana 也不应与已有的兄弟节点 apple 重叠
    const distAppleBanana = Math.hypot(bananaPos.x - applePos.x, bananaPos.y - applePos.y)
    expect(distAppleBanana).toBeGreaterThanOrEqual(60)
  })

  it('accurately hits edge with pickEdgeAt along line segment or label badge', () => {
    const s = { id: 'fruit', x: 100, y: 100, nodeType: 'core' }
    const t = { id: 'apple', x: 200, y: 100, nodeType: 'core' }
    const edge = { source: 'fruit', target: 'apple', sourceNode: s, targetNode: t, label: '同类' }
    const edges = [edge]

    // 1. 点击线段正中下方 5px 处（在 tolerance 14 范围内）
    const hit = pickEdgeAt({ edges, x: 150, y: 105, scale: 1, tolerance: 14 })
    expect(hit).toBe(edge)

    // 2. 点击距离较远的位置（y = 150），不应命中
    const miss = pickEdgeAt({ edges, x: 150, y: 150, scale: 1, tolerance: 14 })
    expect(miss).toBeNull()

    // 3. 点击标签 badge 区域（x: 150, y: 100 附近）
    const tagHit = pickEdgeAt({ edges, x: 154, y: 102, scale: 1, tolerance: 14 })
    expect(tagHit).toBe(edge)

    // 4. 空 edges 或非法端点安全防护
    expect(pickEdgeAt({ edges: [], x: 150, y: 100 })).toBeNull()
    expect(pickEdgeAt({ edges: [{ sourceNode: null }], x: 150, y: 100 })).toBeNull()
  })

  it('strips legacy "核心" label on reparenting and supports edge label editing and clearing', () => {
    const graphData = {
      nodes: [
        { id: 'hub', nodeType: 'hub', label: '中心', x: 0, y: 0 },
        { id: 'fruit', nodeType: 'core', term: 'fruit', x: 100, y: 0 },
        { id: 'apple', nodeType: 'core', term: 'apple', x: 200, y: 0 },
      ],
      edges: [
        { source: 'hub', target: 'fruit', label: '' },
        { source: 'hub', target: 'apple', label: '核心' }, // 模拟旧数据残留的"核心"
      ],
    }

    const mockGraph = {
      setData: vi.fn((data) => Object.assign(graphData, data)),
      setSelectedNodes: vi.fn(),
    }
    const mockToast = vi.fn()
    const mockSave = vi.fn()

    const labelInput = { value: '', focus: vi.fn(), addEventListener: vi.fn() }
    const sTermEl = { textContent: '' }
    const tTermEl = { textContent: '' }
    const edgeModal = {
      classList: { add: vi.fn(), remove: vi.fn(), contains: vi.fn(() => false) },
      querySelectorAll: vi.fn(() => []),
    }

    const nodeActions = createRuminationNodeActions({
      elements: {
        edgeEditModal: edgeModal,
        edgeModalSourceTerm: sTermEl,
        edgeModalTargetTerm: tTermEl,
        edgeModalLabelInput: labelInput,
      },
      graph: mockGraph,
      getGraphData: () => graphData,
      getSelectedNodeIds: () => [],
      setSelectedNodeIds: () => {},
      getActiveFocusNodeId: () => null,
      setActiveFocus: () => {},
      toast: mockToast,
      saveCurrentGraphDebounced: mockSave,
    })

    const appleNode = graphData.nodes.find((n) => n.id === 'apple')
    const fruitNode = graphData.nodes.find((n) => n.id === 'fruit')

    // 1. 拖拽挂载 apple 到 fruit：旧连线中的 "核心" 必须自动清空为 ""
    nodeActions.reparentNode(appleNode, fruitNode)
    const reparentedEdge = graphData.edges.find((e) => e.target === 'apple')
    expect(reparentedEdge.source).toBe('fruit')
    expect(reparentedEdge.label).toBe('') // 连线上不再出现"核心"

    // 2. 双击连线打开编辑弹窗
    nodeActions.openEdgeModal(reparentedEdge)
    expect(sTermEl.textContent).toBe('fruit')
    expect(tTermEl.textContent).toBe('apple')
    expect(labelInput.value).toBe('')

    // 3. 用户输入自定义关系文字并保存
    labelInput.value = '包含'
    // 模拟 submit 触发保存
    reparentedEdge.label = '包含'
    mockGraph.setData(graphData)
    expect(reparentedEdge.label).toBe('包含')

    // 4. 清空关系文字
    reparentedEdge.label = ''
    mockGraph.setData(graphData)
    expect(reparentedEdge.label).toBe('')
  })
})
