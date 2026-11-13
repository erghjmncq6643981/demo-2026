/**
 * 知识反刍模块 - 右键上下文菜单、浮动操作栏与手工节点维护弹窗控制
 */
import { showModal, hideModal } from '/src/shared/modal.js'
import { calculateReparentPosition } from '/src/features/rumination/rumination-graph-drag.js'

export function createRuminationNodeActions({
  elements,
  graph,
  toast,
  store,
  getGraphData = () => store?.getGraphData?.(),
  getSelectedNodeIds = () => store?.getSelectedNodeIds?.() || [],
  setSelectedNodeIds = (next) => store?.setSelectedNodeIds?.(next),
  getActiveFocusNodeId = () => store?.getActiveFocusNodeId?.(),
  setActiveFocus = (id) => store?.setActiveFocus?.(id),
  isNodeInBag = (id, term) => store?.isNodeInBag?.(id, term),
  onToggleBag,
  onExpandNode,
  syncLiveNodePositions,
  renderBag,
  saveCurrentGraphDebounced,
}) {
  let contextMenuTargetNode = null
  let nodeModalMode = 'add'

  function closeContextMenu() {
    if (elements.graphContextMenu) {
      elements.graphContextMenu.classList.add('hidden')
    }
  }

  function closeNodeModal() {
    hideModal(elements.nodeEditModal)
  }

  function resolveFocusedNode() {
    const focusId = getActiveFocusNodeId?.()
    if (!focusId) return null
    const graphData = getGraphData()
    return graphData?.nodes?.find((n) => n.id === focusId || n.term === focusId) || null
  }

  function syncActionBar() {
    if (!elements.nodeActionBar) return
    const focusedNode = resolveFocusedNode()
    if (!focusedNode) {
      elements.nodeActionBar.classList.add('hidden')
      return
    }
    const isHub = focusedNode.nodeType === 'hub'
    const term = focusedNode.label || focusedNode.term || focusedNode.id
    const inBag = !isHub && typeof isNodeInBag === 'function' ? isNodeInBag(focusedNode.id, term) : false
    if (elements.actionBarNodeTerm) {
      elements.actionBarNodeTerm.textContent = term
    }
    if (elements.barToggleBagBtn) {
      elements.barToggleBagBtn.style.display = isHub ? 'none' : 'inline-flex'
    }
    if (elements.barToggleBagText) {
      elements.barToggleBagText.textContent = inBag ? '移出词袋' : '词袋'
    }
    if (elements.barExpandNodeBtn) {
      elements.barExpandNodeBtn.style.display = isHub ? 'none' : 'inline-flex'
    }
    if (elements.barDeleteNodeBtn) {
      elements.barDeleteNodeBtn.style.display = isHub ? 'none' : 'inline-flex'
    }
    if (elements.barEditNodeBtn) {
      elements.barEditNodeBtn.style.display = 'inline-flex'
      elements.barEditNodeBtn.title = isHub ? '修改中心主题 (E)' : '修改词汇释义 (E)'
    }
    if (elements.barAddChildBtn) {
      elements.barAddChildBtn.style.display = 'inline-flex'
      elements.barAddChildBtn.title = isHub ? '新增核心词 (A)' : '新增关联节点 (A)'
    }
    elements.nodeActionBar.classList.remove('hidden')
  }

  function toggleBagForNode(node) {
    if (!node || node.nodeType === 'hub') return
    if (typeof onToggleBag === 'function') {
      onToggleBag(node.id)
      syncActionBar()
      return
    }
    const term = node.term || node.label || node.id
    const targetId = node.id
    const currentSelected = getSelectedNodeIds()
    if (isNodeInBag(targetId, term)) {
      setSelectedNodeIds(currentSelected.filter((id) => id !== targetId && id !== term))
      toast(`已将【${term}】从词袋中移除`)
    } else {
      setSelectedNodeIds([...currentSelected, targetId])
      toast(`已将【${term}】加入词袋`)
    }
    graph.setSelectedNodes(getSelectedNodeIds())
    renderBag()
    syncActionBar()
    saveCurrentGraphDebounced(true)
  }

  function deleteNode(node) {
    if (!node || node.nodeType === 'hub') return
    const term = node.term || node.label || node.id
    const targetId = node.id
    const graphData = getGraphData()

    syncLiveNodePositions?.()

    if (store?.deleteNode) {
      store.deleteNode(targetId)
      graph.setSelectedNodes(getSelectedNodeIds())
      renderBag?.()
      graph.setData(store.getGraphData())
    } else {
      graphData.nodes = graphData.nodes.filter((n) => n.id !== targetId && n.term !== targetId)
      graphData.edges = graphData.edges.filter(
        (e) => e.source !== targetId && e.target !== targetId && e.source !== term && e.target !== term,
      )

      const focusId = getActiveFocusNodeId()
      if (focusId === targetId || focusId === term) {
        setActiveFocus(null)
      }
      if (isNodeInBag(targetId, term)) {
        setSelectedNodeIds(getSelectedNodeIds().filter((id) => id !== targetId && id !== term))
        graph.setSelectedNodes(getSelectedNodeIds())
        renderBag?.()
      }

      graph.setData(graphData)
    }
    syncActionBar()
    toast(`已从图谱中移除【${term}】`)
    saveCurrentGraphDebounced(true)
  }

  function openContextMenu(node, pos) {
    contextMenuTargetNode = node
    if (!elements.graphContextMenu) return

    const term = node.label || node.term || node.id
    if (elements.ctxNodeTerm) elements.ctxNodeTerm.textContent = term
    if (elements.ctxNodeType) {
      elements.ctxNodeType.textContent = node.nodeType === 'hub' ? '场景核心' : node.nodeType === 'core' ? '考纲核心' : '4D衍生'
    }

    const isHub = node.nodeType === 'hub'
    const inBag = !isHub && typeof isNodeInBag === 'function' ? isNodeInBag(node.id, term) : false

    if (elements.ctxAddChildText) {
      elements.ctxAddChildText.textContent = isHub ? '新增核心词' : '新增关联'
    }
    if (elements.ctxEditNodeBtn) {
      elements.ctxEditNodeBtn.style.display = 'flex'
    }
    const ctxEditTextEl = elements.ctxEditNodeBtn?.querySelector('span:last-child')
    if (ctxEditTextEl) {
      ctxEditTextEl.textContent = isHub ? '修改中心主题' : '编辑释义'
    }
    if (elements.ctxToggleBagBtn) {
      elements.ctxToggleBagBtn.style.display = isHub ? 'none' : 'flex'
    }
    if (elements.ctxToggleBagText) {
      elements.ctxToggleBagText.textContent = inBag ? '移出词袋' : '加入词袋'
    }
    if (elements.ctxDangerDivider) {
      elements.ctxDangerDivider.style.display = isHub ? 'none' : 'block'
    }
    if (elements.ctxDeleteNodeBtn) {
      elements.ctxDeleteNodeBtn.style.display = isHub ? 'none' : 'flex'
    }

    const menu = elements.graphContextMenu
    menu.classList.remove('hidden')
    const menuWidth = 180
    const menuHeight = isHub ? 80 : 185
    let left = pos.clientX + 4
    let top = pos.clientY + 4
    if (left + menuWidth > window.innerWidth) left = pos.clientX - menuWidth - 4
    if (top + menuHeight > window.innerHeight) top = pos.clientY - menuHeight - 4

    menu.style.left = `${Math.max(10, left)}px`
    menu.style.top = `${Math.max(10, top)}px`
  }

  function openNodeModal(mode) {
    nodeModalMode = mode
    closeContextMenu()
    if (!contextMenuTargetNode) return

    const isHub = contextMenuTargetNode.nodeType === 'hub'
    const dimLabel = elements.nodeModalDimensionSelect?.closest('label')

    if (elements.nodeModalTitle) {
      elements.nodeModalTitle.textContent = mode === 'add'
        ? (isHub ? '新增考纲核心词' : '新增关联节点')
        : (isHub ? '修改场景中心主题' : '修改词汇释义')
    }
    if (elements.nodeModalParentInfo) {
      elements.nodeModalParentInfo.style.display = mode === 'add' ? 'block' : 'none'
    }
    if (elements.nodeModalParentTerm) {
      elements.nodeModalParentTerm.textContent = contextMenuTargetNode.term || contextMenuTargetNode.label || '场景中心'
    }

    if (mode === 'add') {
      if (elements.nodeModalTermInput) {
        elements.nodeModalTermInput.value = ''
        elements.nodeModalTermInput.placeholder = isHub ? '例如：vegetables / fruit / meat' : '例如：whisk / roast / thermometer'
      }
      if (elements.nodeModalDefInput) {
        elements.nodeModalDefInput.value = ''
        elements.nodeModalDefInput.placeholder = '例如：蔬菜 / 水果；留空自动补全'
      }
      if (dimLabel) dimLabel.style.display = 'flex'
      if (elements.nodeModalDimensionSelect) {
        elements.nodeModalDimensionSelect.value = isHub ? 'peer' : 'action'
      }
      showModal(elements.nodeEditModal, { initialFocus: elements.nodeModalTermInput })
    } else {
      if (isHub) {
        if (elements.nodeModalTermInput) {
          elements.nodeModalTermInput.value = contextMenuTargetNode.label || contextMenuTargetNode.term || ''
          elements.nodeModalTermInput.placeholder = '例如：vegetable 或 生鲜蔬菜采购'
        }
        if (elements.nodeModalDefInput) {
          elements.nodeModalDefInput.value = contextMenuTargetNode.subLabel || contextMenuTargetNode.definition || ''
          elements.nodeModalDefInput.placeholder = '可选副标，例如：场景核心'
        }
        if (dimLabel) dimLabel.style.display = 'none'
        showModal(elements.nodeEditModal, { initialFocus: elements.nodeModalTermInput })
      } else {
        if (elements.nodeModalTermInput) elements.nodeModalTermInput.value = contextMenuTargetNode.term || ''
        if (elements.nodeModalDefInput) elements.nodeModalDefInput.value = contextMenuTargetNode.definition || ''
        if (dimLabel) dimLabel.style.display = 'flex'
        if (elements.nodeModalDimensionSelect) {
          elements.nodeModalDimensionSelect.value = contextMenuTargetNode.dimension || 'peer'
        }
        showModal(elements.nodeEditModal, { initialFocus: elements.nodeModalDefInput })
      }
    }
  }

  function handleNodeAction(action, node) {
    const target = node || resolveFocusedNode()
    if (!target) return
    const isHub = target.nodeType === 'hub'
    closeContextMenu()
    if (action === 'bag' && !isHub) {
      toggleBagForNode(target)
    } else if (action === 'expand' && !isHub) {
      setActiveFocus(target.id)
      onExpandNode?.(target)
    } else if (action === 'edit') {
      contextMenuTargetNode = target
      openNodeModal('edit')
    } else if (action === 'addChild') {
      contextMenuTargetNode = target
      openNodeModal('add')
    } else if (action === 'delete' && !isHub) {
      deleteNode(target)
    }
  }

  function reparentNode(childNode, newParentNode) {
    if (!childNode || !newParentNode || childNode.id === newParentNode.id) return
    const graphData = store?.getGraphData ? store.getGraphData() : getGraphData()
    if (!graphData || !Array.isArray(graphData.edges) || !Array.isArray(graphData.nodes)) return

    const childId = childNode.id
    const childTerm = childNode.term
    const newParentId = newParentNode.id
    const isNewParentHub = newParentNode.nodeType === 'hub'

    syncLiveNodePositions?.()

    const existingEdgeIndex = graphData.edges.findIndex(
      (e) => e.target === childId || (childTerm && e.target === childTerm)
    )
    const defaultRelType = isNewParentHub ? 'peer' : (childNode.dimension || 'peer')

    if (store?.reparentNode) {
      store.reparentNode(childId, newParentId, defaultRelType)
    } else if (existingEdgeIndex >= 0) {
      const oldEdge = graphData.edges[existingEdgeIndex]
      oldEdge.source = newParentId
      oldEdge.target = childId
      oldEdge.relationType = oldEdge.relationType || defaultRelType
      if (oldEdge.label === '核心') {
        oldEdge.label = ''
      }
    } else {
      graphData.edges.push({
        source: newParentId,
        target: childId,
        relationType: defaultRelType,
        label: '',
      })
    }

    const currentNodes = store?.getGraphData ? store.getGraphData().nodes : graphData.nodes
    const currentEdges = store?.getGraphData ? store.getGraphData().edges : graphData.edges
    const childRaw = store?.findNode ? store.findNode(childId) : currentNodes.find((n) => n.id === childId || (childTerm && n.term === childTerm))
    if (childRaw) {
      if (isNewParentHub) {
        childRaw.nodeType = 'core'
      } else {
        childRaw.nodeType = 'expansion'
        childRaw.dimension = childRaw.dimension || defaultRelType
      }
      // 自适应计算不与父节点及周边节点重叠的最佳停靠坐标
      const newPos = calculateReparentPosition(
        childNode,
        newParentNode,
        currentNodes,
        currentEdges
      )
      childRaw.fixed = true
      childRaw.x = newPos.x
      childRaw.y = newPos.y
      childNode.x = newPos.x
      childNode.y = newPos.y
    }

    graph.setData(store?.getGraphData ? store.getGraphData() : graphData)
    syncActionBar()
    const childLabel = childNode.term || childNode.label || '节点'
    const parentLabel = newParentNode.term || newParentNode.label || '目标分类'
    toast(`已将【${childLabel}】归入【${parentLabel}】`)
    saveCurrentGraphDebounced(true)
  }

  if (elements.ctxAddChildBtn) {
    elements.ctxAddChildBtn.addEventListener('click', () => openNodeModal('add'))
  }
  if (elements.ctxEditNodeBtn) {
    elements.ctxEditNodeBtn.addEventListener('click', () => openNodeModal('edit'))
  }
  if (elements.ctxToggleBagBtn) {
    elements.ctxToggleBagBtn.addEventListener('click', () => {
      closeContextMenu()
      toggleBagForNode(contextMenuTargetNode)
    })
  }
  if (elements.ctxDeleteNodeBtn) {
    elements.ctxDeleteNodeBtn.addEventListener('click', () => {
      closeContextMenu()
      deleteNode(contextMenuTargetNode)
    })
  }

  elements.barToggleBagBtn?.addEventListener('click', () => handleNodeAction('bag'))
  elements.barExpandNodeBtn?.addEventListener('click', () => handleNodeAction('expand'))
  elements.barEditNodeBtn?.addEventListener('click', () => handleNodeAction('edit'))
  elements.barAddChildBtn?.addEventListener('click', () => handleNodeAction('addChild'))
  elements.barDeleteNodeBtn?.addEventListener('click', () => handleNodeAction('delete'))
  elements.barClearFocusBtn?.addEventListener('click', () => {
    setActiveFocus(null)
    syncActionBar()
  })

  elements.closeNodeModalBtn?.addEventListener?.('click', closeNodeModal)
  elements.cancelNodeModalBtn?.addEventListener?.('click', closeNodeModal)
  elements.nodeEditModal?.addEventListener?.('click', (e) => {
    if (e.target === elements.nodeEditModal) closeNodeModal()
  })

  if (elements.submitNodeModalBtn) {
    elements.submitNodeModalBtn.addEventListener('click', () => {
      const termVal = elements.nodeModalTermInput?.value?.trim()
      const defVal = elements.nodeModalDefInput?.value?.trim()
      const dimVal = elements.nodeModalDimensionSelect?.value || 'action'

      if (!termVal) {
        toast('请输入内容')
        elements.nodeModalTermInput?.focus()
        return
      }

      const graphData = store?.getGraphData ? store.getGraphData() : getGraphData()
      if (nodeModalMode === 'add') {
        if (!contextMenuTargetNode) return
        syncLiveNodePositions?.()
        const isCore = contextMenuTargetNode.nodeType === 'hub'
        const newId = `custom_${termVal.toLowerCase().replace(/\s+/g, '_')}_${Date.now().toString(36)}`
        const newNode = {
          id: newId,
          nodeType: isCore ? 'core' : 'expansion',
          term: termVal,
          definition: defVal || termVal,
          dimension: dimVal,
          status: 'unlearned',
          masteryScore: 0,
          reviewStage: 0,
        }
        const newEdge = {
          source: contextMenuTargetNode.id,
          target: newId,
          relationType: dimVal,
          label: '',
        }
        if (store?.addNode) {
          store.addNode({ node: newNode, edge: newEdge })
        } else {
          graphData.nodes.push(newNode)
          graphData.edges.push(newEdge)
        }
        graph.setData(store?.getGraphData ? store.getGraphData() : graphData)
        toast(`已将【${termVal}】挂载至【${contextMenuTargetNode.term || contextMenuTargetNode.label}】`)
        saveCurrentGraphDebounced(true)
      } else if (nodeModalMode === 'edit') {
        if (!contextMenuTargetNode) return
        syncLiveNodePositions?.()
        const isHub = contextMenuTargetNode.nodeType === 'hub'
        const targetRaw = (store?.findNode ? store.findNode(contextMenuTargetNode.id) : null) || graphData.nodes.find((n) => n.id === contextMenuTargetNode.id) || contextMenuTargetNode
        if (isHub) {
          targetRaw.label = termVal
          targetRaw.term = termVal
          if (defVal) targetRaw.subLabel = defVal
          graph.setData(store?.getGraphData ? store.getGraphData() : graphData)
          syncActionBar()
          toast(`已更新场景中心主题为【${termVal}】`)
          saveCurrentGraphDebounced(true)
          closeNodeModal()
          return
        }
        const oldTerm = contextMenuTargetNode.term
        targetRaw.term = termVal
        targetRaw.definition = defVal || termVal
        targetRaw.dimension = dimVal
        const currentEdges = store?.getGraphData ? store.getGraphData().edges : graphData.edges
        const edge = currentEdges.find(
          (e) => e.target === targetRaw.id || (oldTerm && e.target === oldTerm)
        )
        if (edge) edge.relationType = dimVal
        graph.setData(store?.getGraphData ? store.getGraphData() : graphData)
        syncActionBar()
        toast(`已更新【${termVal}】释义与属性`)
        saveCurrentGraphDebounced(true)
      }
      closeNodeModal()
    })
  }

  let currentEditingEdge = null

  function closeEdgeModal() {
    hideModal(elements.edgeEditModal)
    currentEditingEdge = null
  }

  function openEdgeModal(edge) {
    if (!edge) return
    currentEditingEdge = edge
    closeContextMenu()

    const graphData = getGraphData()
    const sNode = graphData?.nodes?.find((n) => n.id === edge.source || n.term === edge.source) || edge.sourceNode
    const tNode = graphData?.nodes?.find((n) => n.id === edge.target || n.term === edge.target) || edge.targetNode
    const sTerm = sNode?.label || sNode?.term || edge.source || '起点'
    const tTerm = tNode?.label || tNode?.term || edge.target || '终点'

    if (elements.edgeModalSourceTerm) elements.edgeModalSourceTerm.textContent = sTerm
    if (elements.edgeModalTargetTerm) elements.edgeModalTargetTerm.textContent = tTerm
    const currentLabel = edge.label && edge.label !== '核心' ? edge.label : ''
    if (elements.edgeModalLabelInput) {
      elements.edgeModalLabelInput.value = currentLabel
    }

    showModal(elements.edgeEditModal, { initialFocus: elements.edgeModalLabelInput })
  }

  function saveEdgeLabel(newLabel) {
    if (!currentEditingEdge) return
    const graphData = store?.getGraphData ? store.getGraphData() : getGraphData()
    if (!graphData || !Array.isArray(graphData.edges)) return

    const trimmed = (newLabel || '').trim()
    const finalLabel = trimmed === '核心' ? '' : trimmed

    if (store?.updateEdgeLabel) {
      store.updateEdgeLabel(
        currentEditingEdge.source || currentEditingEdge.sourceNode?.id,
        currentEditingEdge.target || currentEditingEdge.targetNode?.id,
        finalLabel,
      )
    } else {
      const rawEdge = graphData.edges.find((e) =>
        (e.source === currentEditingEdge.source || e.source === currentEditingEdge.sourceNode?.id)
        && (e.target === currentEditingEdge.target || e.target === currentEditingEdge.targetNode?.id)
      )
      if (rawEdge) {
        rawEdge.label = finalLabel
      }
    }
    currentEditingEdge.label = finalLabel

    graph?.setData?.(store?.getGraphData ? store.getGraphData() : graphData)
    saveCurrentGraphDebounced?.(true)
    if (finalLabel) {
      toast(`已更新连线关系文字为【${finalLabel}】`)
    } else {
      toast('已清除连线关系文字')
    }
    closeEdgeModal()
  }

  elements.closeEdgeModalBtn?.addEventListener?.('click', closeEdgeModal)
  elements.cancelEdgeModalBtn?.addEventListener?.('click', closeEdgeModal)
  elements.clearEdgeModalBtn?.addEventListener?.('click', () => saveEdgeLabel(''))
  elements.submitEdgeModalBtn?.addEventListener?.('click', () => saveEdgeLabel(elements.edgeModalLabelInput?.value))
  elements.edgeModalLabelInput?.addEventListener?.('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      saveEdgeLabel(elements.edgeModalLabelInput?.value)
    }
  })
  if (elements.edgeEditModal) {
    elements.edgeEditModal.querySelectorAll('.compact-tag-btn').forEach((btn) => {
      btn.addEventListener('click', () => {
        const tag = btn.getAttribute('data-edge-tag')
        if (elements.edgeModalLabelInput) {
          elements.edgeModalLabelInput.value = tag
          elements.edgeModalLabelInput.focus()
        }
      })
    })
  }

  window.addEventListener('click', (e) => {
    if (elements.graphContextMenu && !elements.graphContextMenu.contains(e.target)) {
      closeContextMenu()
    }
  })

  return {
    openContextMenu,
    closeContextMenu,
    openNodeModal,
    closeNodeModal,
    openEdgeModal,
    closeEdgeModal,
    syncActionBar,
    handleNodeAction,
    reparentNode,
  }
}
