/**
 * 知识反刍模块 - 右键上下文菜单、浮动操作栏与手工节点维护弹窗控制
 */
import { showModal, hideModal, bindModalDismiss } from '/src/shared/modal.js'
import { calculateReparentPosition } from '/src/features/rumination/rumination-graph-drag.js'
import { RELATION_CONFIG } from '/src/features/rumination/rumination-graph-renderer.js'
import { computeEdgeRouting } from '/src/features/rumination/rumination-graph-layout.js'
import { hasSlashSynonyms, splitSlashNodeToSynonymGroup, addSynonymNodeToTarget, removeNodeFromSynonymGroup } from '/src/features/rumination/rumination-synonym-groups.js'

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
  history,
}) {
  let contextMenuTargetNode = null
  let nodeModalMode = 'add'

  function closeContextMenu() {
    if (elements.graphContextMenu) {
      elements.graphContextMenu.classList.add('hidden')
    }
    closeNodeBorderStyleMenu()
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

  let selectedGraphEdge = null

  function syncEdgeActionBar(edge) {
    selectedGraphEdge = edge || null
    if (!elements.edgeActionBar) return
    if (!edge) {
      elements.edgeActionBar.classList.add('hidden')
      return
    }
    if (elements.nodeActionBar) {
      elements.nodeActionBar.classList.add('hidden')
    }
    const relConf = RELATION_CONFIG[edge.relationType] || { label: '关联' }
    const displayLabel = (edge.label && edge.label !== '核心') ? edge.label : relConf.label
    if (elements.edgeBarRelationLabel) {
      elements.edgeBarRelationLabel.textContent = displayLabel
    }
    if (elements.edgeBarCurveText) {
      const isStraight = edge.lineStyle === 'straight' || edge.isCurved === false
      elements.edgeBarCurveText.textContent = isStraight ? '― 直线' : '∿ 曲线'
    }
    if (elements.edgeBarArrowText) {
      const hasArrow = edge.showArrow !== false && (edge.relationType === 'action' || edge.relationType === 'downstream' || edge.showArrow === true)
      elements.edgeBarArrowText.textContent = hasArrow ? '➔ 箭头' : '• 端点'
    }
    elements.edgeActionBar.classList.remove('hidden')
  }

  function closeEdgeActionBar() {
    selectedGraphEdge = null
    if (elements.edgeActionBar) {
      elements.edgeActionBar.classList.add('hidden')
    }
    closeEdgeInlineEditor()
  }

  function syncActionBar() {
    if (elements.edgeActionBar) {
      elements.edgeActionBar.classList.add('hidden')
    }
    if (elements.nodeActionBar) {
      elements.nodeActionBar.classList.add('hidden')
    }
  }

  const BORDER_STYLE_LABELS = {
    solid: '实线',
    dashed: '虚线',
    dotted: '点线',
    dashdot: '点划线',
  }

  function syncNodeBorderStyleUI(style) {
    const curStyle = style || 'solid'
    const label = BORDER_STYLE_LABELS[curStyle] || '实线'
    if (elements.barBorderStyleText) {
      elements.barBorderStyleText.textContent = label
    }
    if (elements.barBorderStyleIcon) {
      elements.barBorderStyleIcon.className = `border-style-indicator ${curStyle}-indicator`
    }
    if (elements.nodeBorderStyleMenu) {
      elements.nodeBorderStyleMenu.querySelectorAll('.border-style-menu-item').forEach((item) => {
        const itemStyle = item.getAttribute('data-style')
        if (itemStyle === curStyle) {
          item.classList.add('active')
        } else {
          item.classList.remove('active')
        }
      })
    }
    if (elements.ctxBorderStyleText) {
      elements.ctxBorderStyleText.textContent = `边框: ${label}`
    }
  }

  function toggleNodeBorderStyleMenu() {
    if (!elements.nodeBorderStyleMenu) return
    const isHidden = elements.nodeBorderStyleMenu.classList.contains('hidden')
    if (isHidden) {
      elements.nodeBorderStyleMenu.classList.remove('hidden')
    } else {
      elements.nodeBorderStyleMenu.classList.add('hidden')
    }
  }

  function closeNodeBorderStyleMenu() {
    if (elements.nodeBorderStyleMenu) {
      elements.nodeBorderStyleMenu.classList.add('hidden')
    }
  }

  function changeNodeBorderStyle(targetNode, newStyle) {
    if (!targetNode) return
    const graphData = store?.getGraphData ? store.getGraphData() : getGraphData()
    if (!graphData) return
    history?.recordBefore?.('修改边框线型')

    const style = newStyle || 'solid'
    targetNode.borderStyle = style

    const rawNode = (store?.findNode ? store.findNode(targetNode.id) : null)
      || graphData.nodes?.find((n) => n.id === targetNode.id)
      || targetNode
    if (rawNode) {
      rawNode.borderStyle = style
    }

    syncNodeBorderStyleUI(style)
    closeNodeBorderStyleMenu()
    if (graph?.requestRedraw) {
      graph.requestRedraw()
    } else if (graph?.setData) {
      graph.setData(store?.getGraphData ? store.getGraphData() : graphData)
    }
    saveCurrentGraphDebounced?.(true)
    const label = BORDER_STYLE_LABELS[style] || '实线'
    toast(`✨ 节点【${targetNode.term || targetNode.label || targetNode.id}】边框已修改为【${label}】`)
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
    history?.recordBefore?.('删除节点')
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

    if (elements.ctxExpandNodeBtn) {
      elements.ctxExpandNodeBtn.style.display = isHub ? 'none' : 'flex'
    }
    if (elements.ctxAddSynonymBtn) elements.ctxAddSynonymBtn.style.display = isHub ? 'none' : 'flex'
    const canSplit = !isHub && hasSlashSynonyms(node.term)
    if (elements.ctxSplitSynonymBtn) {
      elements.ctxSplitSynonymBtn.style.display = canSplit ? 'flex' : 'none'
      elements.ctxSplitSynonymBtn.classList.toggle('hidden', !canSplit)
    }
    const inSynGroup = !isHub && Boolean(node.synonymGroupId)
    if (elements.ctxLeaveSynonymGroupBtn) {
      elements.ctxLeaveSynonymGroupBtn.style.display = inSynGroup ? 'flex' : 'none'
      elements.ctxLeaveSynonymGroupBtn.classList.toggle('hidden', !inSynGroup)
    }
    if (elements.ctxAddChildText) {
      elements.ctxAddChildText.textContent = isHub ? '新增核心' : '关联'
    }
    if (elements.ctxEditNodeBtn) {
      elements.ctxEditNodeBtn.style.display = 'flex'
    }
    const ctxEditTextEl = elements.ctxEditNodeBtn?.querySelector('span:last-child')
    if (ctxEditTextEl) {
      ctxEditTextEl.textContent = isHub ? '修改主题' : '编辑'
    }
    if (elements.ctxToggleBagBtn) {
      elements.ctxToggleBagBtn.style.display = isHub ? 'none' : 'flex'
    }
    if (elements.ctxToggleBagText) {
      elements.ctxToggleBagText.textContent = inBag ? '移出' : '词袋'
    }
    if (elements.ctxDangerDivider) {
      elements.ctxDangerDivider.style.display = isHub ? 'none' : 'block'
    }
    if (elements.ctxDeleteNodeBtn) {
      elements.ctxDeleteNodeBtn.style.display = isHub ? 'none' : 'flex'
    }
    if (elements.ctxBorderStyleText) {
      elements.ctxBorderStyleText.textContent = `边框: ${BORDER_STYLE_LABELS[node.borderStyle || 'solid'] || '实线'}`
    }

    const menu = elements.graphContextMenu
    menu.classList.remove('hidden')
    const menuWidth = 124
    const menuHeight = isHub ? 140 : (280 + (canSplit ? 30 : 0) + (inSynGroup ? 30 : 0))
    let left = pos.clientX + 4
    let top = pos.clientY + 4
    if (left + menuWidth > window.innerWidth) left = pos.clientX - menuWidth - 4
    if (top + menuHeight > window.innerHeight) top = pos.clientY - menuHeight - 4

    menu.style.left = `${Math.max(10, left)}px`
    menu.style.top = `${Math.max(10, top)}px`
  }

  function syncSynonymModalFields() {
    const dimVal = elements.nodeModalDimensionSelect?.value || 'peer'
    const isAddSyn = nodeModalMode === 'add' && dimVal === 'synonym'
    const isEditSyn = nodeModalMode === 'edit' && Boolean(contextMenuTargetNode?.synonymGroupId)
    if (elements.nodeModalSynonymGroupField) {
      elements.nodeModalSynonymGroupField.style.display = (isAddSyn || isEditSyn) ? 'flex' : 'none'
      elements.nodeModalSynonymGroupField.classList.toggle('hidden', !(isAddSyn || isEditSyn))
    }
  }

  function openNodeModal(mode, defaultDimension = 'peer') {
    nodeModalMode = mode
    closeContextMenu()
    if (!contextMenuTargetNode) return

    const isHub = contextMenuTargetNode.nodeType === 'hub'
    const dimLabel = elements.nodeModalDimensionSelect?.closest('label')

    if (elements.nodeModalTitle) {
      elements.nodeModalTitle.textContent = mode === 'add'
        ? (isHub ? '新增考纲核心词' : (defaultDimension === 'synonym' ? '新增近义词（同簇聚合）' : '新增关联节点'))
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
        elements.nodeModalTermInput.placeholder = defaultDimension === 'synonym' ? '例如：quilt / duvet' : (isHub ? '例如：vegetables / fruit / meat' : '例如：strawberry / peel / cozy')
      }
      if (elements.nodeModalDefInput) {
        elements.nodeModalDefInput.value = ''
        elements.nodeModalDefInput.placeholder = defaultDimension === 'synonym' ? '如：夹棉被（可注明与原词差异）' : '例如：草莓；留空将自动补全'
      }
      if (dimLabel) dimLabel.style.display = 'flex'
      if (elements.nodeModalDimensionSelect) elements.nodeModalDimensionSelect.value = defaultDimension
      if (elements.nodeModalSynonymGroupInput) elements.nodeModalSynonymGroupInput.value = contextMenuTargetNode.synonymGroupLabel || ''
      syncSynonymModalFields()
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
        syncSynonymModalFields()
        showModal(elements.nodeEditModal, { initialFocus: elements.nodeModalTermInput })
      } else {
        if (elements.nodeModalTermInput) elements.nodeModalTermInput.value = contextMenuTargetNode.term || ''
        if (elements.nodeModalDefInput) elements.nodeModalDefInput.value = contextMenuTargetNode.definition || ''
        if (dimLabel) dimLabel.style.display = 'flex'
        if (elements.nodeModalDimensionSelect) elements.nodeModalDimensionSelect.value = contextMenuTargetNode.dimension || 'peer'
        if (elements.nodeModalSynonymGroupInput) elements.nodeModalSynonymGroupInput.value = contextMenuTargetNode.synonymGroupLabel || ''
        syncSynonymModalFields()
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
    history?.recordBefore?.('重新关联父节点')

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

  elements.ctxExpandNodeBtn?.addEventListener('click', () => {
    const target = contextMenuTargetNode; closeContextMenu()
    if (target && target.nodeType !== 'hub') handleNodeAction('expand', target)
  })
  elements.ctxAddChildBtn?.addEventListener('click', () => openNodeModal('add'))
  elements.ctxAddSynonymBtn?.addEventListener('click', () => openNodeModal('add', 'synonym'))
  elements.ctxSplitSynonymBtn?.addEventListener('click', () => {
    const target = contextMenuTargetNode; closeContextMenu()
    if (!target || target.nodeType === 'hub') return
    syncLiveNodePositions?.(); history?.recordBefore?.('拆分为近义词组')
    const gd = store?.getGraphData ? store.getGraphData() : getGraphData()
    const res = splitSlashNodeToSynonymGroup({ graphData: gd, node: target, store })
    if (!res) return
    graph.setData(store?.getGraphData ? store.getGraphData() : gd); syncActionBar()
    toast(`✨ 已拆分为近义词簇【${res.groupLabel}】`)
    saveCurrentGraphDebounced?.(true, { changeSummary: `拆分近义词组 ${res.groupLabel}` })
  })
  elements.ctxLeaveSynonymGroupBtn?.addEventListener('click', () => {
    const target = contextMenuTargetNode; closeContextMenu()
    if (!target?.synonymGroupId) return
    syncLiveNodePositions?.(); history?.recordBefore?.('移出近义词组')
    const gd = store?.getGraphData ? store.getGraphData() : getGraphData()
    if (!removeNodeFromSynonymGroup({ graphData: gd, nodeId: target.id, store })) return
    graph.setData(store?.getGraphData ? store.getGraphData() : gd); syncActionBar()
    toast(`已将【${target.term}】移出近义词簇`)
    saveCurrentGraphDebounced?.(true, { changeSummary: `移出近义词组 ${target.term}` })
  })
  elements.ctxEditNodeBtn?.addEventListener('click', () => openNodeModal('edit'))
  elements.ctxToggleBagBtn?.addEventListener('click', () => { closeContextMenu(); toggleBagForNode(contextMenuTargetNode) })
  elements.ctxDeleteNodeBtn?.addEventListener('click', () => { closeContextMenu(); deleteNode(contextMenuTargetNode) })
  elements.nodeModalDimensionSelect?.addEventListener('change', syncSynonymModalFields)

  elements.barToggleBagBtn?.addEventListener('click', () => handleNodeAction('bag'))
  elements.barExpandNodeBtn?.addEventListener('click', () => handleNodeAction('expand'))
  elements.barEditNodeBtn?.addEventListener('click', () => handleNodeAction('edit'))
  elements.barAddChildBtn?.addEventListener('click', () => handleNodeAction('addChild'))
  elements.barDeleteNodeBtn?.addEventListener('click', () => handleNodeAction('delete'))
  elements.barClearFocusBtn?.addEventListener('click', () => {
    setActiveFocus(null)
    syncActionBar()
  })

  if (elements.ctxBorderStyleBtn) {
    elements.ctxBorderStyleBtn.addEventListener('click', () => {
      if (!contextMenuTargetNode) return
      const styles = ['solid', 'dashed', 'dotted', 'dashdot']
      const curStyle = contextMenuTargetNode.borderStyle || 'solid'
      const nextIdx = (styles.indexOf(curStyle) + 1) % styles.length
      const nextStyle = styles[nextIdx]
      closeContextMenu()
      changeNodeBorderStyle(contextMenuTargetNode, nextStyle)
    })
  }

  elements.barBorderStyleBtn?.addEventListener('click', (e) => {
    e.stopPropagation()
    toggleNodeBorderStyleMenu()
  })

  elements.nodeBorderStyleMenu?.querySelectorAll('.border-style-menu-item').forEach((item) => {
    item.addEventListener('click', (e) => {
      e.stopPropagation()
      const focusedNode = resolveFocusedNode()
      const target = focusedNode || contextMenuTargetNode
      const style = item.getAttribute('data-style')
      if (target && style) {
        changeNodeBorderStyle(target, style)
      }
    })
  })

  bindModalDismiss(elements.nodeEditModal, {
    closeButtons: [elements.closeNodeModalBtn, elements.cancelNodeModalBtn],
  })

  function submitNodeModal() {
    const termVal = elements.nodeModalTermInput?.value?.trim()
    const defVal = elements.nodeModalDefInput?.value?.trim()
    const dimVal = elements.nodeModalDimensionSelect?.value || 'peer'

    if (!termVal) {
      toast('请输入内容')
      elements.nodeModalTermInput?.focus()
      return
    }

    const graphData = store?.getGraphData ? store.getGraphData() : getGraphData()
    if (nodeModalMode === 'add') {
      if (!contextMenuTargetNode) return
      syncLiveNodePositions?.()
      history?.recordBefore?.(dimVal === 'synonym' ? '新增近义词' : '新增关联')
      if (dimVal === 'synonym' && contextMenuTargetNode.nodeType !== 'hub') {
        const res = addSynonymNodeToTarget({
          graphData,
          targetNode: contextMenuTargetNode,
          term: termVal,
          definition: defVal,
          groupLabel: elements.nodeModalSynonymGroupInput?.value,
          store,
        })
        if (res) {
          graph.setData(store?.getGraphData ? store.getGraphData() : graphData); syncActionBar()
          toast(`✨ 已将【${termVal}】加入近义词簇【${res.groupLabel}】`)
          saveCurrentGraphDebounced(true, { changeSummary: `新增近义词 ${termVal}` })
        }
        closeNodeModal(); return
      }
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
      const currentNodes = store?.getGraphData ? store.getGraphData().nodes : graphData.nodes
      const currentEdges = store?.getGraphData ? store.getGraphData().edges : graphData.edges
      if (typeof contextMenuTargetNode.x === 'number' && typeof contextMenuTargetNode.y === 'number') {
        const pos = calculateReparentPosition(newNode, contextMenuTargetNode, currentNodes, currentEdges)
        newNode.x = pos.x
        newNode.y = pos.y
        newNode.fixed = true
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
      saveCurrentGraphDebounced(true, { changeSummary: `新增节点 ${termVal}` })
    } else if (nodeModalMode === 'edit') {
      if (!contextMenuTargetNode) return
      syncLiveNodePositions?.()
      history?.recordBefore?.('编辑词汇')
      const isHub = contextMenuTargetNode.nodeType === 'hub'
      const targetRaw = (store?.findNode ? store.findNode(contextMenuTargetNode.id) : null) || graphData.nodes.find((n) => n.id === contextMenuTargetNode.id) || contextMenuTargetNode
      if (isHub) {
        targetRaw.label = termVal
        targetRaw.term = termVal
        if (defVal) targetRaw.subLabel = defVal
        graph.setData(store?.getGraphData ? store.getGraphData() : graphData)
        syncActionBar()
        toast(`已更新场景中心主题为【${termVal}】`)
        saveCurrentGraphDebounced(true, { changeSummary: `更新中心主题 ${termVal}` })
        closeNodeModal()
        return
      }
      const oldTerm = contextMenuTargetNode.term
      targetRaw.term = termVal
      targetRaw.definition = defVal || termVal
      targetRaw.dimension = dimVal
      if (targetRaw.synonymGroupId && elements.nodeModalSynonymGroupInput) {
        const nextGroupLabel = elements.nodeModalSynonymGroupInput.value?.trim()
        if (nextGroupLabel) {
          (store?.getGraphData ? store.getGraphData().nodes : graphData.nodes)?.forEach((n) => {
            if (n.synonymGroupId === targetRaw.synonymGroupId) n.synonymGroupLabel = nextGroupLabel
          })
        }
      }
      const currentEdges = store?.getGraphData ? store.getGraphData().edges : graphData.edges
      const edge = currentEdges.find(
        (e) => e.target === targetRaw.id || (oldTerm && e.target === oldTerm)
      )
      if (edge) edge.relationType = dimVal
      graph.setData(store?.getGraphData ? store.getGraphData() : graphData)
      syncActionBar()
      toast(`已更新【${termVal}】释义与属性`)
      saveCurrentGraphDebounced(true, { changeSummary: `修改节点 ${termVal}` })
    }
    closeNodeModal()
  }

  if (elements.submitNodeModalBtn) {
    elements.submitNodeModalBtn.addEventListener('click', submitNodeModal)
  }

  function handleNodeModalKeydown(e) {
    if (e.isComposing || e.keyCode === 229) return
    if (e.key === 'Enter') {
      if (e.target === elements.cancelNodeModalBtn || e.target === elements.closeNodeModalBtn) return
      e.preventDefault()
      e.stopPropagation()
      submitNodeModal()
    } else if (e.key === 'Escape') {
      e.preventDefault()
      e.stopPropagation()
      closeNodeModal()
    }
  }

  elements.nodeEditModal?.addEventListener?.('keydown', handleNodeModalKeydown)
  elements.nodeModalTermInput?.addEventListener?.('keydown', handleNodeModalKeydown)
  elements.nodeModalDefInput?.addEventListener?.('keydown', handleNodeModalKeydown)
  elements.nodeModalSynonymGroupInput?.addEventListener?.('keydown', handleNodeModalKeydown)
  elements.nodeModalDimensionSelect?.addEventListener?.('keydown', handleNodeModalKeydown)

  let currentEditingEdge = null

  function openEdgeInlineEditor(edge, pos) {
    if (!edge) return
    currentEditingEdge = edge
    closeContextMenu()
    if (!elements.edgeInlineEditor) return

    const s = edge.sourceNode
    const t = edge.targetNode
    const canvasWrap = elements.canvasWrap || elements.canvas?.parentElement
    if (!s || !t || !canvasWrap) return

    const route = computeEdgeRouting(s, t, edge)
    const transform = graph?.getTransform ? graph.getTransform() : { x: 0, y: 0, scale: 1 }
    const canvas = elements.canvas || canvasWrap.querySelector('canvas')
    const canvasRect = canvas?.getBoundingClientRect ? canvas.getBoundingClientRect() : { left: 0, top: 0 }
    const wrapRect = canvasWrap.getBoundingClientRect ? canvasWrap.getBoundingClientRect() : { left: 0, top: 0 }

    const screenX = canvasRect.left + transform.x + route.midX * transform.scale
    const screenY = canvasRect.top + transform.y + route.midY * transform.scale

    const relX = screenX - wrapRect.left
    const relY = screenY - wrapRect.top

    elements.edgeInlineEditor.style.left = `${Math.round(relX)}px`
    elements.edgeInlineEditor.style.top = `${Math.round(relY)}px`

    const currentLabel = edge.label && edge.label !== '核心' ? edge.label : ''
    if (elements.edgeInlineInput) {
      elements.edgeInlineInput.value = currentLabel
    }

    elements.edgeInlineEditor.classList.remove('hidden')
    setTimeout(() => {
      elements.edgeInlineInput?.focus()
      elements.edgeInlineInput?.select()
    }, 20)
  }

  function closeEdgeInlineEditor() {
    currentEditingEdge = null
    if (elements.edgeInlineEditor) {
      elements.edgeInlineEditor.classList.add('hidden')
    }
  }

  function commitEdgeInlineLabel(newLabel) {
    if (!currentEditingEdge) return
    saveEdgeLabel(newLabel)
    closeEdgeInlineEditor()
  }

  function closeEdgeModal() {
    closeEdgeInlineEditor()
    if (elements.edgeEditModal) {
      hideModal(elements.edgeEditModal)
    }
    currentEditingEdge = null
  }

  function openEdgeModal(edge, pos) {
    if (elements.edgeModalSourceTerm || elements.edgeModalTargetTerm || elements.edgeModalLabelInput) {
      const gd = store?.getGraphData ? store.getGraphData() : getGraphData()
      if (elements.edgeModalSourceTerm) {
        const s = edge.sourceNode || gd?.nodes?.find((n) => n.id === edge.source)
        elements.edgeModalSourceTerm.textContent = s?.term || s?.label || edge.source || ''
      }
      if (elements.edgeModalTargetTerm) {
        const t = edge.targetNode || gd?.nodes?.find((n) => n.id === edge.target)
        elements.edgeModalTargetTerm.textContent = t?.term || t?.label || edge.target || ''
      }
      if (elements.edgeModalLabelInput) {
        elements.edgeModalLabelInput.value = edge.label && edge.label !== '核心' ? edge.label : ''
      }
    }
    // 零弹窗遮挡：直接打开 ProcessOn 式连线就地就位输入框
    openEdgeInlineEditor(edge, pos)
  }

  function matchEdgeEnds(edge, sId, tId, sTerm, tTerm) {
    return (edge.source === sId || (sTerm && edge.source === sTerm)) && (edge.target === tId || (tTerm && edge.target === tTerm))
  }

  function applyEdgeMutationAndRefresh(edgeRef, graphData, mutator) {
    const sId = edgeRef.source || edgeRef.sourceNode?.id
    const tId = edgeRef.target || edgeRef.targetNode?.id
    const sTerm = edgeRef.sourceNode?.term
    const tTerm = edgeRef.targetNode?.term
    const rawEdge = graphData?.edges?.find((e) => matchEdgeEnds(e, sId, tId, sTerm, tTerm))
    mutator(rawEdge)
    graph?.setData?.(store?.getGraphData ? store.getGraphData() : graphData)
    const nextEdge = (graph?.getEdges ? graph.getEdges() : []).find((e) => matchEdgeEnds(e, sId, tId, sTerm, tTerm))
    if (nextEdge) {
      selectedGraphEdge = nextEdge
      graph?.setSelectedEdge?.(nextEdge)
    }
  }

  function saveEdgeLabel(newLabel) {
    if (!currentEditingEdge) return
    const graphData = store?.getGraphData ? store.getGraphData() : getGraphData()
    if (!graphData || !Array.isArray(graphData.edges)) return
    history?.recordBefore?.('修改连线文字')

    const trimmed = (newLabel || '').trim()
    const finalLabel = trimmed === '核心' ? '' : trimmed
    const sId = currentEditingEdge.source || currentEditingEdge.sourceNode?.id
    const tId = currentEditingEdge.target || currentEditingEdge.targetNode?.id

    if (store?.updateEdgeLabel) store.updateEdgeLabel(sId, tId, finalLabel)
    currentEditingEdge.label = finalLabel
    if (selectedGraphEdge) {
      selectedGraphEdge.label = finalLabel
      syncEdgeActionBar(selectedGraphEdge)
    }
    applyEdgeMutationAndRefresh(currentEditingEdge, graphData, (rawEdge) => {
      if (!store?.updateEdgeLabel && rawEdge) rawEdge.label = finalLabel
    })
    saveCurrentGraphDebounced?.(true)
    toast(finalLabel ? `已更新连线关系文字为【${finalLabel}】` : '已清除连线关系文字')
    closeEdgeModal()
  }

  bindModalDismiss(elements.edgeEditModal, {
    closeButtons: [elements.closeEdgeModalBtn, elements.cancelEdgeModalBtn],
    onClose: () => { currentEditingEdge = null },
  })
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

  if (elements.tidyBtn) {
    elements.tidyBtn.addEventListener('click', () => {
      if (!graph?.tidyLayout) return
      history?.recordBefore?.('整理图谱布局')
      graph.tidyLayout()
      syncLiveNodePositions?.()
      saveCurrentGraphDebounced?.(true)
      toast('✨ 画布已智能整理并等距排布')
    })
  }

  elements.edgeBarToggleCurveBtn?.addEventListener('click', () => {
    if (!selectedGraphEdge) return
    history?.recordBefore?.('修改连线形态')
    const nextCurved = selectedGraphEdge.lineStyle === 'straight' || selectedGraphEdge.isCurved === false
    selectedGraphEdge.isCurved = nextCurved
    selectedGraphEdge.lineStyle = nextCurved ? 'curve' : 'straight'
    syncEdgeActionBar(selectedGraphEdge)
    const graphData = store?.getGraphData ? store.getGraphData() : getGraphData()
    applyEdgeMutationAndRefresh(selectedGraphEdge, graphData, (rawEdge) => {
      if (rawEdge) {
        rawEdge.isCurved = nextCurved
        rawEdge.lineStyle = nextCurved ? 'curve' : 'straight'
      }
    })
    saveCurrentGraphDebounced?.(true)
    toast(nextCurved ? '∿ 已切换为平滑贝塞尔曲线' : '― 已切换为直线连线')
  })

  elements.edgeBarToggleArrowBtn?.addEventListener('click', () => {
    if (!selectedGraphEdge) return
    history?.recordBefore?.('修改连线箭头')
    const currentHasArrow = selectedGraphEdge.showArrow !== false && (
      selectedGraphEdge.relationType === 'action'
      || selectedGraphEdge.relationType === 'downstream'
      || selectedGraphEdge.showArrow === true
    )
    const nextShowArrow = !currentHasArrow
    selectedGraphEdge.showArrow = nextShowArrow
    syncEdgeActionBar(selectedGraphEdge)
    const graphData = store?.getGraphData ? store.getGraphData() : getGraphData()
    applyEdgeMutationAndRefresh(selectedGraphEdge, graphData, (rawEdge) => {
      if (rawEdge) rawEdge.showArrow = nextShowArrow
    })
    saveCurrentGraphDebounced?.(true)
    toast(nextShowArrow ? '➔ 已开启关系端点箭头' : '• 已切换为极简端点')
  })

  if (elements.edgeBarEditLabelBtn) {
    elements.edgeBarEditLabelBtn.addEventListener('click', () => {
      if (!selectedGraphEdge) return
      openEdgeInlineEditor(selectedGraphEdge)
    })
  }

  elements.edgeInlineConfirmBtn?.addEventListener('click', () => {
    commitEdgeInlineLabel(elements.edgeInlineInput?.value)
  })
  elements.edgeInlineClearBtn?.addEventListener('click', () => {
    commitEdgeInlineLabel('')
  })
  elements.edgeInlineInput?.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      commitEdgeInlineLabel(elements.edgeInlineInput?.value)
    } else if (e.key === 'Escape') {
      e.preventDefault()
      closeEdgeInlineEditor()
    }
  })
  if (elements.edgeInlineQuickTags) {
    elements.edgeInlineQuickTags.querySelectorAll('.edge-mini-tag').forEach((btn) => {
      btn.addEventListener('click', () => {
        const tag = btn.getAttribute('data-tag')
        if (tag) {
          commitEdgeInlineLabel(tag)
        }
      })
    })
  }

  if (elements.edgeBarDeleteBtn) {
    elements.edgeBarDeleteBtn.addEventListener('click', () => {
      if (!selectedGraphEdge) return
      history?.recordBefore?.('删除连线')
      const sId = selectedGraphEdge.source || selectedGraphEdge.sourceNode?.id
      const tId = selectedGraphEdge.target || selectedGraphEdge.targetNode?.id
      const sTerm = selectedGraphEdge.sourceNode?.term
      const tTerm = selectedGraphEdge.targetNode?.term
      const graphData = store?.getGraphData ? store.getGraphData() : getGraphData()
      if (graphData && Array.isArray(graphData.edges)) {
        graphData.edges = graphData.edges.filter((e) =>
          !( (e.source === sId || (sTerm && e.source === sTerm))
             && (e.target === tId || (tTerm && e.target === tTerm)) )
        )
      }
      closeEdgeActionBar()
      graph?.setSelectedEdge?.(null)
      graph?.setData?.(store?.getGraphData ? store.getGraphData() : graphData)
      saveCurrentGraphDebounced?.(true)
      toast('已删除所选连线')
    })
  }

  if (elements.edgeBarClearFocusBtn) {
    elements.edgeBarClearFocusBtn.addEventListener('click', () => {
      closeEdgeActionBar()
      graph?.setSelectedEdge?.(null)
    })
  }

  window.addEventListener('click', (e) => {
    if (elements.graphContextMenu && !elements.graphContextMenu.contains(e.target)) {
      closeContextMenu()
    }
    if (elements.edgeInlineEditor && !elements.edgeInlineEditor.classList.contains('hidden')) {
      if (!elements.edgeInlineEditor.contains(e.target) && e.target !== elements.edgeBarEditLabelBtn && !elements.edgeBarEditLabelBtn?.contains(e.target)) {
        closeEdgeInlineEditor()
      }
    }
    if (elements.nodeBorderStyleMenu && !elements.nodeBorderStyleMenu.classList.contains('hidden')) {
      if (!elements.nodeBorderStyleMenu.contains(e.target) && !elements.barBorderStyleBtn?.contains(e.target)) {
        closeNodeBorderStyleMenu()
      }
    }
  })

  return {
    openContextMenu,
    closeContextMenu,
    openNodeModal,
    closeNodeModal,
    openEdgeModal,
    closeEdgeModal,
    openEdgeInlineEditor,
    closeEdgeInlineEditor,
    syncActionBar,
    syncEdgeActionBar,
    closeEdgeActionBar,
    handleNodeAction,
    reparentNode,
    changeNodeBorderStyle,
    syncNodeBorderStyleUI,
    toggleNodeBorderStyleMenu,
    closeNodeBorderStyleMenu,
  }
}
