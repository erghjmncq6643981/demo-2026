/**
 * 知识反刍模块 - 主工作台交互控制器
 * 负责 2D 星系图谱拓扑驱动、真实词本数据持久化交互、反刍词袋动态汇聚、场景材料编织与微环境探针即时结算。
 */

import { createRuminationGraph } from '/src/features/rumination/rumination-graph.js'
import { createRuminationMaterialView } from '/src/features/rumination/rumination-material-view.js'
import { createRuminationPracticeView } from '/src/features/rumination/rumination-practice-view.js'
import { createRuminationAudioController } from '/src/features/rumination/rumination-audio-controller.js'
import { createRuminationMaterialTaskTracker } from '/src/features/rumination/rumination-material-task.js'
import { createRuminationGraphTaskTracker } from '/src/features/rumination/rumination-graph-task.js'
import { collectRuminationElements } from '/src/features/rumination/rumination-elements.js'
import { createRuminationNodeActions } from '/src/features/rumination/rumination-node-actions.js'
import { createRuminationGraphPersistence } from '/src/features/rumination/rumination-persistence.js'
import { createRuminationStore } from '/src/features/rumination/rumination-store.js'
import { createRuminationScenarioDialog } from '/src/features/rumination/rumination-scenario-dialog.js'
import {
  PRODUCE_GRAPH_DATA,
  FISSION_EXPANSIONS,
  getPresetGraphData,
} from '/src/features/rumination/rumination-data.js'
import { sameId } from '/src/shared/ids.js'
import { escapeHtml } from '/src/shared/text.js'

export function initRuminationWorkspace(ctx = {}) {
  const {
    state = {},
    api = null,
    toast = (msg) => console.log(msg),
    speakSentence = () => {},
    stopSpeaking = () => {},
    openQuickLookup = null,
  } = ctx

  const canvas = document.getElementById('ruminationGraphCanvas')
  if (!canvas) return null

  function getCurrentWordbookId() {
    return state.currentWordbookId || document.getElementById('articleWordbookSelect')?.value || ''
  }

  // 核心单向状态仓储：单一真实来源，默认全宽呈现大图星系
  const store = createRuminationStore({
    activeScenarioCode: 'produce_kitchen',
    currentWordbookId: getCurrentWordbookId(),
  })
  store.setGraphData(JSON.parse(JSON.stringify(PRODUCE_GRAPH_DATA)), { syncBag: false })

  let latestLoadSeq = 0
  let isHostViewActive = !state?.activeView || state.activeView === 'ruminationView' || state.activeView === 'articleStudyView'

  function isSameContext(wordbookId, scenarioCode) {
    return sameId(getCurrentWordbookId(), wordbookId) && String(store.getActiveScenarioCode() || '') === String(scenarioCode || '')
  }

  function persistScenarioPreference(wordbookId, scenarioCode) {
    try {
      if (wordbookId && scenarioCode && typeof sessionStorage !== 'undefined') {
        sessionStorage.setItem(`rumination_scenario_${String(wordbookId)}`, String(scenarioCode))
      }
    } catch (err) {
      console.debug('保存场景偏好失败', err)
    }
  }

  function restoreScenarioPreference(wordbookId) {
    try {
      if (wordbookId && typeof sessionStorage !== 'undefined') {
        return sessionStorage.getItem(`rumination_scenario_${String(wordbookId)}`) || ''
      }
    } catch (err) {
      console.debug('读取场景偏好失败', err)
    }
    return ''
  }

  let graph = null
  let nodeActions = null
  let graphPersistence = null
  let practiceView = null
  function syncLiveNodePositions() {
    graphPersistence?.syncLiveNodePositions?.()
  }
  function flushPendingGraphSave(options) {
    return graphPersistence?.flushPendingGraphSave?.(options)
  }
  function saveCurrentGraphDebounced(immediate = false, options = {}) {
    return graphPersistence?.saveCurrentGraphDebounced?.(immediate, options)
  }
  function updateSaveStatus(mode, text) {
    graphPersistence?.updateSaveStatus?.(mode, text)
  }
  function setActiveFocus(nodeId) {
    store.setActiveFocus(nodeId)
    graph?.setFocusedNode?.(store.getActiveFocusNodeId())
    nodeActions?.syncActionBar?.()
  }

  function syncGraphLifecycle() {
    if (isHostViewActive && store.getWorkspaceTab() === 'graph') {
      graph?.resume?.()
    } else {
      graph?.pause?.()
    }
  }

  // 初始化图谱引擎（点击单词选中并聚焦，不弹任何多余提示或侧边栏）
  graph = createRuminationGraph(canvas, {
    onNodeSelect: (node) => {
      if (node.nodeType === 'hub') {
        return
      }
      setActiveFocus(node.id)
      nodeActions?.closeContextMenu()
    },
    onNodeDblClick: (node, pos) => {
      if (!node || node.nodeType === 'hub') return
      nodeActions?.closeContextMenu()
      const term = (node.term || node.label || '').trim()
      if (!term) return
      const scenarioLabel = elements.scenarioSelect?.options[elements.scenarioSelect.selectedIndex]?.text || store.getGraphData()?.scenarioName || ''
      const launcher = typeof openQuickLookup === 'function'
        ? openQuickLookup
        : (typeof window !== 'undefined' ? window.__openQuickLookup : null)
      if (typeof launcher === 'function') {
        launcher(term, {
          mode: 'node',
          origin: pos?.nodeScreenPos || (pos ? { x: pos.clientX, y: pos.clientY } : null),
          scenarioLabel,
          node: {
            id: node.id,
            term,
            nodeType: node.nodeType,
            dimension: node.dimension,
            definition: node.definition,
            status: node.status,
          },
        })
      } else {
        toast(`词汇【${term}】`)
      }
    },
    onNodeContextMenu: (node, pos) => {
      nodeActions?.openContextMenu(node, pos)
    },
    onNodeAction: (action, node) => {
      nodeActions?.handleNodeAction?.(action, node)
    },
    onBackgroundContextMenu: () => {
      nodeActions?.closeContextMenu()
    },
    onEdgeDblClick: (edge, pos) => {
      nodeActions?.openEdgeModal?.(edge, pos)
    },
    onNodeReparent: (child, parent) => nodeActions?.reparentNode?.(child, parent),
    onNodeDragEnd: (node, pos) => {
      const nextX = Math.round(pos.x), nextY = Math.round(pos.y)
      const raw = store.findNode(node.id)
      if (raw) { raw.x = nextX; raw.y = nextY; raw.fixed = true }
      if (graphPersistence?.saveNodePositionsIncremental) {
        graphPersistence.saveNodePositionsIncremental([{ nodeId: node.id, x: nextX, y: nextY, fixed: true }])
      } else {
        saveCurrentGraphDebounced(true)
      }
    },
  })

  // 挂载初始数据到图谱
  graph.setData(store.getGraphData(), { isNewScenario: true })
  graph.setSelectedNodes(store.getSelectedNodeIds())
  syncGraphLifecycle()

  const elements = collectRuminationElements(document)

  let audioController = null
  const launchPractice = (openAction) => {
    audioController?.stopAll?.()
    openAction?.()
  }

  let taskTracker = null

  function createRequestId(prefix = 'rum') {
    return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  }

  // 场景材料与学习结果检查视图管理器（分治解耦，遵循治理规范）
  const materialView = createRuminationMaterialView(elements, {
    api,
    state,
    toast,
    speakSentence,
    isContextActive: (wb, sc) => isSameContext(wb, sc),
    isTaskActive: () => Boolean(taskTracker?.isTaskActive?.()),
    onRegenerateMaterial: async (mat) => {
      if (!mat?.id || !api?.regenerateRuminationMaterialTask) return
      materialView.setRegeneratingState(mat.id, true)
      try {
        await flushPendingGraphSave()
        await taskTracker.regenerate(mat.id, createRequestId('rum_regen'))
        toast('🔄 已提交覆盖重新生成任务，完成后将替换当前材料（图谱词袋保持不变）')
      } catch (err) {
        console.error('提交重新生成场景材料失败', err)
        toast(err.message || '重新生成失败，请稍后重试')
      } finally {
        materialView.setRegeneratingState(null, false)
        renderBag()
      }
    },
    onMaterialChanged: (material) => {
      const activeMaterial = material !== undefined ? material : materialView.getCurrentMaterialRecord?.()
      audioController?.syncForMaterial?.(activeMaterial)
      if (practiceView?.isOpen?.()) {
        practiceView.closePractice()
      }
    },
    onPracticeSentence: (mat, sentenceIdx) => launchPractice(() => practiceView?.startSingleSentence(mat, sentenceIdx)),
    onStartRecommendedFromProbe: (mat) => {
      switchWorkspaceTab('material')
      const targetMat = mat || materialView.getCurrentMaterialRecord?.()
      if (targetMat) {
        launchPractice(() => practiceView?.startRecommended(targetMat))
      }
    },
    onProbeIlluminated: (targetWord) => {
      const targetNode = store.getGraphData().nodes.find(
        (n) => n.term?.toLowerCase() === targetWord.toLowerCase() || n.id === targetWord.replace(/\s+/g, '_')
      )
      if (targetNode) {
        graph.illuminateNode(targetNode.id)
      }
    },
  })

  practiceView = createRuminationPracticeView(elements, {
    api,
    state,
    toast,
    speakSentence,
    onPracticeUpdated: (updatedMat) => materialView.refreshPracticeUi(updatedMat),
    onClose: () => materialView.refreshPracticeUi(),
  })

  audioController = createRuminationAudioController({
    elements,
    api,
    state,
    toast,
    speakSentence,
    stopSpeaking,
    getCurrentMaterial: () => materialView.getCurrentMaterialRecord?.(),
  })

  async function onAsyncMaterialReady(_task, material) {
    if (!material) return
    if (!isSameContext(material.wordbookId, material.scenarioCode)) return
    materialView?.setRegeneratingState?.(null, false)
    const bagConsumed = material.bagConsumed !== false
    if (bagConsumed) {
      const consumedTerms = new Set(
        (Array.isArray(material.targetWords) ? material.targetWords : [])
          .map((w) => String(w || '').trim().toLowerCase())
          .filter(Boolean)
      )
      const graphData = store.getGraphData()
      const remainingNodeIds = store.getSelectedNodeIds().filter((id) => {
        const node = graphData.nodes.find((n) => n.id === id || n.term === id)
        const term = String(node ? (node.term || node.label || id) : id).trim().toLowerCase()
        return !consumedTerms.has(term)
      })
      store.setSelectedNodeIds(remainingNodeIds)
      if (typeof material.graphVersion === 'number') {
        graphData.version = material.graphVersion
      }
      graph.setSelectedNodes(store.getSelectedNodeIds())
    }
    renderBag()
    await materialView.loadMaterials(material.wordbookId, material.scenarioCode, material.id)
    const sourceText = material.generationSource === 'fallback' ? '本地语义降级' : 'AI 模型生成'
    toast(
      bagConsumed
        ? `🎉 场景材料生成完成（${sourceText}），仅结算提交快照，新加词袋词汇已保留！`
        : `🔄 当前场景材料已覆盖重新生成（${sourceText}），图谱词袋保持不变！`
    )
  }

  taskTracker = createRuminationMaterialTaskTracker({
    bannerEl: elements.taskBanner,
    api,
    toast,
    getContext: () => ({
      wordbookId: getCurrentWordbookId(),
      scenarioCode: store.getActiveScenarioCode(),
    }),
    onMaterialCompleted: onAsyncMaterialReady,
    onStatusChange: () => renderBag(),
  })

  function applyFissionResultToCanvas(fissionResult, item) {
    const sourceLabel = fissionResult.aiPowered ? 'AI 模型生成' : '本地语义降级'
    const originScenarioLabel = item?.scenarioName || fissionResult.scenarioCode
    if (!isSameContext(fissionResult.wordbookId, fissionResult.scenarioCode)) {
      toast(`✨ 后台任务：场景「${originScenarioLabel}」中的【${fissionResult.targetWord}】完成 4D 衍生裂变（${sourceLabel}），结果已归属原场景！`)
      return
    }
    const graphData = store.getGraphData()
    const liveParent = graphData.nodes.find(
      (n) => n.id === fissionResult.parentNodeId && n.nodeType !== 'hub'
    )
    if (!liveParent || fissionResult.applyStatus !== 'applied') {
      toast(`【${fissionResult.targetWord}】原父节点已移除，跳过衍生挂载`)
      return
    }
    syncLiveNodePositions()
    let addedCount = 0
    const addedTerms = []
    if (Array.isArray(fissionResult.nodes)) {
      for (const node of fissionResult.nodes) {
        const exists = graphData.nodes.some(
          (n) => n.id === node.id || (n.term && n.term.toLowerCase() === node.term?.toLowerCase())
        )
        if (!exists) {
          const relEdge = (fissionResult.edges || []).find((e) => e.target === node.id)
          const edgeToAdd = relEdge
            ? { ...relEdge, source: liveParent.id }
            : { source: liveParent.id, target: node.id, relationType: node.dimension || 'action', label: '' }
          store.addNode({ node: { ...node }, edge: edgeToAdd })
          addedCount++
          addedTerms.push(node.term)
        }
      }
    }
    if (typeof fissionResult.graphVersion === 'number') {
      graphData.version = fissionResult.graphVersion
    }
    if (addedCount > 0) {
      graph.setData(store.getGraphData())
      graph.setSelectedNodes(store.getSelectedNodeIds())
      toast(`✨ 【${fissionResult.targetWord}】完成 4D 衍生裂变（${sourceLabel}），新增 ${addedCount} 个关联词汇 (${addedTerms.join(', ')})`)
    } else {
      toast(`【${fissionResult.targetWord}】裂变完成（${sourceLabel}），图谱已包含相关词汇`)
    }
  }

  let switchScenario = () => {}
  let switchWorkspaceTab = () => {}
  const graphTaskTracker = createRuminationGraphTaskTracker({
    bannerEl: elements.graphTaskBanner,
    api,
    toast,
    getContext: () => ({
      wordbookId: getCurrentWordbookId(),
      scenarioCode: store.getActiveScenarioCode(),
    }),
    onSwitchScenario: (scCode) => switchScenario(scCode),
    onScenarioCompleted: async (completedItem) => {
      const wbId = completedItem.wordbookId
      const scCode = completedItem.scenarioCode
      await syncScenarios(wbId)
      const sourceText = completedItem.sourceLabel ? `（${completedItem.sourceLabel}）` : ''
      if (isSameContext(wbId, scCode)) {
        await loadGraphData(wbId, scCode)
        toast(`🎉 自定义场景「${completedItem.scenarioName || scCode}」星系构建完成${sourceText}，已加载已保存图谱！`)
      } else {
        toast(`🎉 后台任务：自定义场景「${completedItem.scenarioName || scCode}」构建完成${sourceText}，结果已归属原场景！`)
      }
    },
    onFissionCompleted: ({ item, fissionResult }) => {
      if (fissionResult) {
        applyFissionResultToCanvas(fissionResult, item)
      }
    },
  })

  function mapBagTermsToIds(bagTerms) {
    if (!Array.isArray(bagTerms) || bagTerms.length === 0) return []
    const graphData = store.getGraphData()
    return bagTerms
      .map((term) => {
        const node = graphData.nodes.find((n) => n.term?.toLowerCase() === term.toLowerCase() || n.id === term)
        return node ? node.id : term
      })
      .filter(Boolean)
  }

  let toggleNodeInBag = () => {}

  // 图谱坐标同步、串行持久化与 Command+S 保存快捷键（抽离保持单文件紧凑）
  graphPersistence = createRuminationGraphPersistence({
    state,
    api,
    canvas,
    graph,
    toast,
    getElements: () => elements,
    getCurrentGraphData: () => store.getGraphData(),
    getSelectedNodeIds: () => store.getSelectedNodeIds(),
    getActiveScenarioCode: () => store.getActiveScenarioCode(),
    getCurrentWordbookId,
    isSameContext,
    onVersionConflict: (wbId, scCode) => {
      if (isSameContext(wbId, scCode)) {
        loadGraphData(wbId, scCode)
      }
    },
    onBagSynced: (bagTerms) => {
      store.setSelectedNodeIds(mapBagTermsToIds(bagTerms))
      graph.setSelectedNodes(store.getSelectedNodeIds())
      renderBag()
    },
  })

  // 同步当前词本下的场景列表（包含系统预置与用户自定义场景）
  async function syncScenarios(wordbookId) {
    if (!api?.listRuminationScenarios || !wordbookId || !elements.scenarioSelect) return
    try {
      const scenarios = await api.listRuminationScenarios(wordbookId)
      if (Array.isArray(scenarios) && scenarios.length > 0) {
        const currentVal = elements.scenarioSelect.value || store.getActiveScenarioCode()
        elements.scenarioSelect.innerHTML = ''
        scenarios.forEach((s) => {
          const opt = document.createElement('option')
          opt.value = s.code
          opt.textContent = `${s.icon ? s.icon + ' ' : ''}${s.name}`
          elements.scenarioSelect.appendChild(opt)
        })
        if (scenarios.some((s) => s.code === currentVal)) {
          elements.scenarioSelect.value = currentVal
        } else {
          elements.scenarioSelect.value = scenarios[0].code
          store.setActiveScenarioCode(scenarios[0].code)
        }
      }
    } catch (err) {
      console.warn('同步场景列表失败', err)
    }
  }

  // 从后端异步加载星系图谱拓扑
  async function loadGraphData(wordbookId, scenarioCode) {
    const seq = ++latestLoadSeq
    setActiveFocus(null)
    let loaded = false
    await syncScenarios(wordbookId)
    if (seq !== latestLoadSeq || !sameId(getCurrentWordbookId(), wordbookId)) return
    const resolvedScenario = elements.scenarioSelect?.value || scenarioCode || store.getActiveScenarioCode()
    store.setActiveScenarioCode(resolvedScenario)
    persistScenarioPreference(wordbookId, resolvedScenario)
    if (api?.getRuminationGraph && wordbookId) {
      try {
        const backendGraph = await api.getRuminationGraph(wordbookId, resolvedScenario)
        if (seq !== latestLoadSeq || !isSameContext(wordbookId, resolvedScenario)) return
        if (backendGraph && Array.isArray(backendGraph.nodes) && backendGraph.nodes.length) {
          store.setGraphData(backendGraph, { syncBag: false })
          graph.setData(store.getGraphData(), { isNewScenario: true })

          store.setSelectedNodeIds(mapBagTermsToIds(backendGraph.bagTerms))
          graph.setSelectedNodes(store.getSelectedNodeIds())
          renderBag()
          const hasCustomCoords = backendGraph.nodes.some((n) => n.nodeType !== 'hub' && Boolean(n.fixed))
          updateSaveStatus(hasCustomCoords ? 'saved' : 'idle', hasCustomCoords ? '✅ 已恢复自定义布局' : '💾 自动保存 · ⌘S 手动保存')
          loaded = true
        }
      } catch (err) {
        console.warn('读取后端星系图谱拓扑失败，降级本地数据', err)
      }
    }
    if (seq !== latestLoadSeq || !isSameContext(wordbookId, resolvedScenario)) return
    if (!loaded) {
      store.setGraphData(JSON.parse(JSON.stringify(getPresetGraphData(resolvedScenario))), { syncBag: false })
      graph.setData(store.getGraphData(), { isNewScenario: true })
      store.setSelectedNodeIds([])
      graph.setSelectedNodes(store.getSelectedNodeIds())
      renderBag()
    }
    await Promise.all([
      materialView.loadMaterials(wordbookId, resolvedScenario),
      taskTracker.recoverForScenario(wordbookId, resolvedScenario),
      graphTaskTracker.recoverForWordbook(wordbookId, resolvedScenario),
    ])
    renderBag()
  }

  switchScenario = (code) => {
    if (!code || code === store.getActiveScenarioCode()) return
    flushPendingGraphSave()
    practiceView?.closePractice()
    updateSaveStatus('idle', '💾 自动保存 · ⌘S 手动保存')
    store.setActiveScenarioCode(code)
    setActiveFocus(null)
    taskTracker.resetView()
    if (elements.scenarioSelect) {
      const hasOpt = Array.from(elements.scenarioSelect.options || []).some((o) => o.value === code)
      if (hasOpt) elements.scenarioSelect.value = code
    }
    persistScenarioPreference(getCurrentWordbookId(), code)

    const currentWbId = getCurrentWordbookId()
    if (currentWbId) {
      loadGraphData(currentWbId, store.getActiveScenarioCode())
    } else {
      store.setGraphData(JSON.parse(JSON.stringify(getPresetGraphData(store.getActiveScenarioCode()))), { syncBag: false })
      graph.setData(store.getGraphData(), { isNewScenario: true })
      store.setSelectedNodeIds([])
      graph.setSelectedNodes(store.getSelectedNodeIds())
      renderBag()
    }
    graphTaskTracker.rerender()
    toast(`已切换场景：${elements.scenarioSelect?.options[elements.scenarioSelect.selectedIndex]?.text || code}`)
    graph.resetView()
  }

  // 顶部工具条场景选择器同步
  if (elements.scenarioSelect) {
    elements.scenarioSelect.addEventListener('change', () => {
      switchScenario(elements.scenarioSelect.value)
    })
  }

  // 新建场景弹窗交互逻辑（抽离独立对话框，职责单一）
  createRuminationScenarioDialog({
    elements,
    toast,
    onScenarioCreated: async ({ code, cleanName, icon, prompt }) => {
      switchWorkspaceTab('graph')
      store.setActiveScenarioCode(code)
      setActiveFocus(null)
      taskTracker.resetView()
      persistScenarioPreference(getCurrentWordbookId(), code)

      const currentWbId = getCurrentWordbookId()
      if (api?.submitCustomScenarioTask && currentWbId) {
        store.setGraphData({
          scenarioCode: code,
          scenarioName: cleanName,
          scenarioIcon: icon,
          building: true,
          nodes: [{
            id: 'hub',
            nodeType: 'hub',
            term: cleanName,
            label: `${icon} ${cleanName}`,
            subLabel: '后台构建中',
            status: 'mastered',
          }],
          edges: [],
          bagTerms: [],
        })
        store.setSelectedNodeIds([])
        graph.setData(store.getGraphData(), { isNewScenario: true })
        graph.setSelectedNodes(store.getSelectedNodeIds())
        renderBag()
        materialView.loadMaterials(currentWbId, code)
        try {
          const task = await api.submitCustomScenarioTask({
            wordbookId: currentWbId,
            scenarioCode: code,
            scenarioName: cleanName,
            scenarioIcon: icon,
            scenarioPrompt: prompt || cleanName,
            requestId: `sc_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
          })
          await graphTaskTracker.trackSubmission(task, {
            wordbookId: currentWbId,
            scenarioCode: code,
            scenarioName: cleanName,
            scenarioIcon: icon,
          })
          toast(`🚀 已提交自定义场景「${icon} ${cleanName}」异步构建任务，刷新或切场景后仍可继续！`)
          return
        } catch (err) {
          console.warn('提交自定义场景异步任务失败，回退同步加载', err)
          toast(err.message || '提交自定义场景构建失败')
        }
      } else if (api?.initCustomScenario && currentWbId) {
        try {
          const res = await api.initCustomScenario({
            wordbookId: currentWbId,
            scenarioCode: code,
            scenarioName: cleanName,
            scenarioIcon: icon,
            scenarioPrompt: prompt || cleanName,
          })
          if (!isSameContext(currentWbId, code)) return
          if (res && Array.isArray(res.nodes) && res.nodes.length) {
            store.setGraphData(res)
            store.setSelectedNodeIds([])
            graph.setData(store.getGraphData(), { isNewScenario: true })
            graph.setSelectedNodes(store.getSelectedNodeIds())
            renderBag()
            materialView.loadMaterials(currentWbId, code)
            toast(`🎉 已切换至「${icon} ${cleanName}」！`)
            return
          }
        } catch (err) {
          console.warn('调用后端初始化自定义场景宇宙失败，降级本地数据', err)
        }
      }
      if (isSameContext(currentWbId, code)) {
        loadGraphData(currentWbId, store.getActiveScenarioCode())
      }
    },
  })

  toggleNodeInBag = (nodeIdOrTerm, explicitAdd) => {
    if (!nodeIdOrTerm) return
    const node = store.findNode(nodeIdOrTerm)
    if (node?.nodeType === 'hub') return
    const targetId = node ? node.id : nodeIdOrTerm
    const term = node ? node.term || node.label || targetId : targetId
    const inBag = store.isNodeInBag(targetId, term)
    const shouldAdd = typeof explicitAdd === 'boolean' ? explicitAdd : !inBag
    if (shouldAdd === inBag) return

    if (shouldAdd) {
      store.toggleBagNode(targetId, term)
      toast(`已将【${term}】加入反刍词袋`)
    } else {
      store.toggleBagNode(targetId, term)
      toast(`已将【${term}】从词袋中移除`)
    }
    graph.setSelectedNodes(store.getSelectedNodeIds())
    renderBag()
    if (graphPersistence?.saveBagItemIncremental) {
      graphPersistence.saveBagItemIncremental({ term: node?.term || term, add: shouldAdd })
    } else {
      saveCurrentGraphDebounced(true)
    }
  }

  // 右键菜单与手动节点维护逻辑（抽离保持单文件紧凑）
  nodeActions = createRuminationNodeActions({
    store,
    elements,
    graph,
    toast,
    getGraphData: () => store.getGraphData(),
    getSelectedNodeIds: () => store.getSelectedNodeIds(),
    setSelectedNodeIds: (next) => store.setSelectedNodeIds(next),
    getActiveFocusNodeId: () => store.getActiveFocusNodeId(),
    setActiveFocus,
    isNodeInBag: (id, term) => store.isNodeInBag(id, term),
    onToggleBag: (id) => toggleNodeInBag(id),
    onExpandNode: (node) => {
      if (node?.id) setActiveFocus(node.id)
      elements.expandMoreBtn?.click()
    },
    syncLiveNodePositions,
    renderBag,
    saveCurrentGraphDebounced,
  })

  // 图谱工具栏按钮
  if (elements.resetBtn) elements.resetBtn.addEventListener('click', () => graph.resetView())
  if (elements.zoomInBtn) elements.zoomInBtn.addEventListener('click', () => graph.zoomIn())
  if (elements.zoomOutBtn) elements.zoomOutBtn.addEventListener('click', () => graph.zoomOut())

  // 点击【🎒 词袋】按钮：操作当前选中的单词（增量同步词袋，不覆盖并发裂变结果）
  if (elements.toggleBagBtn) {
    elements.toggleBagBtn.addEventListener('click', () => {
      const activeFocus = store.getActiveFocusNodeId()
      if (!activeFocus) {
        toast('请先在星系图谱中点击选择一个单词，再点击「🎒 词袋」加入或移除')
        return
      }
      toggleNodeInBag(activeFocus)
    })
  }

  // AI 衍生裂变按钮（优先提交持久化异步任务绑定原词本/场景/父节点，预览模式下本地词库兜底）
  if (elements.expandMoreBtn) {
    elements.expandMoreBtn.addEventListener('click', async () => {
      const currentGraph = store.getGraphData()
      if (currentGraph?.building) {
        toast('当前自定义场景仍在后台构建图谱，请等待构建完成后再执行衍生裂变')
        return
      }
      let targetParentNode = null
      const activeFocus = store.getActiveFocusNodeId()
      const selectedIds = store.getSelectedNodeIds()
      if (activeFocus) {
        targetParentNode = store.findNode(activeFocus)
      }
      if (!targetParentNode && selectedIds.length > 0) {
        const lastSelectedId = selectedIds[selectedIds.length - 1]
        targetParentNode = store.findNode(lastSelectedId)
      }

      if (!targetParentNode || targetParentNode.nodeType === 'hub') {
        toast('请先在星系图谱中选择一个词汇（如 fever、peel 或 cough），再点击衍生裂变！')
        return
      }

      syncLiveNodePositions()
      const currentWbId = getCurrentWordbookId()
      const requestScenarioCode = store.getActiveScenarioCode()
      const requestParentId = targetParentNode.id
      const targetTerm = targetParentNode.term || requestParentId
      const termKey = targetTerm.toLowerCase()
      const existingTerms = currentGraph.nodes.map((n) => n.term).filter(Boolean)

      const originBtnHtml = elements.expandMoreBtn.innerHTML
      elements.expandMoreBtn.disabled = true
      elements.expandMoreBtn.innerHTML = '<span>⏳ 提交裂变任务...</span>'

      try {
        if (api?.submitRuminationFissionTask && currentWbId) {
          await flushPendingGraphSave()
          const task = await api.submitRuminationFissionTask({
            wordbookId: currentWbId,
            scenarioCode: requestScenarioCode,
            scenarioName: currentGraph.scenarioName || '',
            parentNodeId: requestParentId,
            targetWord: targetTerm,
            targetDefinition: targetParentNode.definition || '',
            existingTerms,
            requestId: `fis_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
          })
          await graphTaskTracker.trackSubmission(task, {
            wordbookId: currentWbId,
            scenarioCode: requestScenarioCode,
            scenarioName: currentGraph.scenarioName || requestScenarioCode,
            parentNodeId: requestParentId,
            targetWord: targetTerm,
          })
          toast(`✨ 已提交【${targetTerm}】4D 衍生裂变异步任务，切场景或刷新不丢失`)
          return
        }

        let addedCount = 0
        const addedTerms = []
        const expansions = FISSION_EXPANSIONS[termKey]
        if (expansions && expansions.length > 0) {
          for (const item of expansions) {
            const exists = store.getGraphData().nodes.some(
              (n) => n.id === item.node.id || n.term?.toLowerCase() === item.node.term?.toLowerCase()
            )
            if (!exists) {
              store.addNode({
                node: { ...item.node },
                edge: {
                  source: targetParentNode.id,
                  target: item.node.id,
                  relationType: item.edge.relationType,
                  label: '',
                },
              })
              addedCount++
              addedTerms.push(item.node.term)
            }
          }
        }
        if (addedCount > 0) {
          graph.setData(store.getGraphData())
          toast(`✨ 成功为【${targetTerm}】衍生出 ${addedCount} 个关联词汇 (${addedTerms.join(', ')})`)
          saveCurrentGraphDebounced(true)
        } else {
          toast(`【${targetTerm}】暂无可新增的关联词汇，可右击节点手动新增`)
        }
      } catch (err) {
        console.error('提交节点衍生裂变失败', err)
        toast(err.message || '提交节点衍生裂变失败，请稍后重试')
      } finally {
        elements.expandMoreBtn.disabled = false
        elements.expandMoreBtn.innerHTML = originBtnHtml
      }
    })
  }

  // 渲染反刍词袋
  function renderBag() {
    const selectedNodeIds = store.getSelectedNodeIds()
    const currentGraph = store.getGraphData()
    if (elements.bagCounter) {
      elements.bagCounter.textContent = selectedNodeIds.length
    }

    if (elements.bagChipsWrap) {
      const selectedNodes = selectedNodeIds
        .map((id) => currentGraph.nodes.find((n) => n.id === id || n.term === id) || { id, term: id, label: id })
        .filter(Boolean)

      if (selectedNodes.length === 0) {
        elements.bagChipsWrap.innerHTML = '<span class="bag-empty-tip">0/5 词 · 图谱加词</span>'
      } else {
        elements.bagChipsWrap.innerHTML = selectedNodes
          .map(
            (node) => `
          <span class="bag-chip">
            <strong>${escapeHtml(node.term || node.label)}</strong>
            <button class="remove-chip-btn" data-remove-id="${escapeHtml(node.id)}" type="button" title="从词袋移除">×</button>
          </span>
        `
          )
          .join('')

        elements.bagChipsWrap.querySelectorAll('.remove-chip-btn').forEach((btn) => {
          btn.addEventListener('click', (e) => {
            e.stopPropagation()
            const removeId = btn.getAttribute('data-remove-id')
            toggleNodeInBag(removeId, false)
          })
        })
      }
    }

    // 编织场景材料按钮状态（>= 5 词激活；后台任务运行中保持禁用但允许继续往词袋加词）
    if (elements.generateMaterialBtn) {
      const count = selectedNodeIds.length
      if (taskTracker.isTaskActive()) {
        elements.generateMaterialBtn.disabled = true
        elements.generateMaterialBtn.innerHTML = `<span>⏳ 异步生成中（可继续向词袋加词 · 当前 ${count} 词）</span>`
      } else if (count >= 5) {
        elements.generateMaterialBtn.disabled = false
        elements.generateMaterialBtn.innerHTML = `<span>🚀 编织场景材料 (已选 ${count} 词)</span>`
      } else {
        elements.generateMaterialBtn.disabled = true
        elements.generateMaterialBtn.innerHTML = `<span>🚀 编织场景材料 (${count}/5 词)</span>`
      }
    }

    materialView?.syncRegenerateButtons?.()
    nodeActions?.syncActionBar?.()
  }

  // 星系工作台头部子 Tab 切换（星系图谱 / 场景材料）
  switchWorkspaceTab = (tab = 'graph') => {
    const isMaterial = tab === 'material' || tab === 'probe'
    const isGraph = !isMaterial
    store.setWorkspaceTab(isGraph ? 'graph' : 'material')

    const tabStates = [
      [elements.btnNavGalaxyGraph, isGraph],
      [elements.btnNavScenarioMaterial, isMaterial],
    ]
    tabStates.forEach(([btn, active]) => {
      if (!btn) return
      btn.classList.toggle('active', active)
      btn.setAttribute('aria-selected', active ? 'true' : 'false')
    })

    elements.graphCanvasWrap?.classList.toggle('hidden', !isGraph)
    elements.graphControls?.classList.toggle('hidden', !isGraph)
    elements.scenarioMaterialPanel?.classList.toggle('hidden', !isMaterial)

    if (!isGraph) {
      nodeActions?.closeContextMenu()
      syncGraphLifecycle()
    } else {
      audioController?.stopAll?.()
      syncGraphLifecycle()
      if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
        window.requestAnimationFrame(() => graph?.resize?.())
      }
    }
  }

  elements.btnNavGalaxyGraph?.addEventListener('click', () => switchWorkspaceTab('graph'))
  elements.btnNavScenarioMaterial?.addEventListener('click', () => switchWorkspaceTab('material'))

  // 编织场景材料按钮点击（提交持久化异步任务，不阻断交互且不误清空新增词袋）
  if (elements.generateMaterialBtn) {
    elements.generateMaterialBtn.addEventListener('click', async () => {
      const currentWbId = getCurrentWordbookId()
      if (!currentWbId) {
        toast('请先选择当前词本')
        return
      }
      const currentGraph = store.getGraphData()
      const targetWords = store.getSelectedNodeIds().map((id) => {
        const node = currentGraph.nodes.find((n) => n.id === id || n.term === id)
        return node ? node.term || node.label || id : id
      })
      if (targetWords.length < 5) {
        toast('词袋中目标词汇至少需要 5 个才能编织场景材料')
        return
      }
      elements.generateMaterialBtn.disabled = true
      elements.generateMaterialBtn.innerHTML = '<span>⏳ 正在提交异步生成任务...</span>'
      try {
        await flushPendingGraphSave()
        if (api?.submitRuminationMaterialTask) {
          await taskTracker.submit({
            wordbookId: currentWbId,
            scenarioCode: store.getActiveScenarioCode(),
            targetWords,
            requestId: createRequestId('rum'),
          })
          toast('🚀 已提交异步编织任务，生成期间可继续加入新词或刷新页面追踪！')
        } else if (api?.generateRuminationMaterial) {
          const newMaterial = await api.generateRuminationMaterial({
            wordbookId: currentWbId,
            scenarioCode: store.getActiveScenarioCode(),
            targetWords,
          })
          await onAsyncMaterialReady(null, newMaterial)
        }
      } catch (err) {
        console.error('提交编织场景材料失败', err)
        toast(err.message || '编织场景材料失败，请稍后重试')
      } finally {
        renderBag()
      }
    })
  }

  // 初始化执行
  renderBag()

  // 暴露对外的生命周期与词本切换响应
  const workspaceInstance = {
    store,
    graph,
    renderBag,
    getGraphData: () => store.getGraphData(),
    getSelectedNodeIds: () => store.getSelectedNodeIds(),
    handleTaskUpdate: (detail) => {
      taskTracker.handleExternalUpdate(detail)
      graphTaskTracker.handleExternalUpdate(detail)
    },
    selectNode: (node) => {
      if (!node) return
      setActiveFocus(node.id)
    },
    closePractice: () => practiceView?.closePractice?.(),
    setGraphActive: (active) => {
      isHostViewActive = Boolean(active)
      syncGraphLifecycle()
    },
    saveGraphNow: () => graphPersistence?.saveGraphNow?.(),
    reload(wordbookId) {
      audioController?.stopAll?.()
      if (wordbookId) {
        store.setCurrentWordbookId(wordbookId)
        flushPendingGraphSave()
        updateSaveStatus('idle', '💾 自动保存 · ⌘S 手动保存')
        setActiveFocus(null)
        const remembered = restoreScenarioPreference(wordbookId)
        if (remembered) {
          store.setActiveScenarioCode(remembered)
          if (elements.scenarioSelect) {
            const exists = Array.from(elements.scenarioSelect.options || []).some((o) => o.value === remembered)
            if (exists) elements.scenarioSelect.value = remembered
          }
        }
        loadGraphData(wordbookId, store.getActiveScenarioCode())
      }
    },
    destroy() {
      audioController?.stopAll?.()
      graphPersistence?.destroy?.()
      graph?.destroy?.()
    },
  }
  return workspaceInstance
}
