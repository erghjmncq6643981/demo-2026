/**
 * 知识反刍模块 - 图谱坐标同步、串行持久化与 Command+S 保存快捷键管理
 */
export function createRuminationGraphPersistence({
  state,
  api,
  canvas,
  graph,
  toast,
  getElements,
  getCurrentGraphData,
  getSelectedNodeIds,
  getActiveScenarioCode,
  getCurrentWordbookId,
  isSameContext = () => true,
  onVersionConflict = null,
  onBagSynced = null,
}) {
  let saveTimer = null
  let pendingSnapshot = null
  let pendingPositions = new Map()
  let pendingBagCount = 0
  let latestSaveSeq = 0
  let inFlightSavePromise = Promise.resolve()

  function updateSaveStatus(mode, text) {
    const el = getElements()?.saveStatus
    if (!el) return
    el.classList.remove('is-saving', 'is-saved', 'is-error')
    if (mode === 'saving') el.classList.add('is-saving')
    if (mode === 'saved') el.classList.add('is-saved')
    if (mode === 'error') el.classList.add('is-error')
    el.textContent = text
  }

  function isVersionConflictError(err) {
    return err?.status === 409 || err?.errorCode === 'RUMINATION_GRAPH_VERSION_CONFLICT'
  }

  function applyServerVersionIfCurrent(wordbookId, scenarioCode, nextVersion) {
    if (typeof nextVersion !== 'number' || !isSameContext(wordbookId, scenarioCode)) {
      return
    }
    const graphData = getCurrentGraphData()
    if (graphData) {
      graphData.version = nextVersion
    }
  }

  function handleSaveError(seq, wordbookId, scenarioCode, err, keepalive = false) {
    if (seq === latestSaveSeq) {
      if (isVersionConflictError(err)) {
        updateSaveStatus('error', '⚠️ 版本已更新，正在同步...')
        pendingSnapshot = null
        pendingPositions.clear()
        if (!keepalive) {
          toast('⚠️ 图谱已在后台任务或其他标签页更新，正在自动同步最新节点与词袋')
        }
        onVersionConflict?.(wordbookId, scenarioCode, err)
        return
      }
      updateSaveStatus('error', '⚠️ 位置保存失败')
      console.warn('持久化保存星系图谱失败', err)
      if (!keepalive) {
        toast(err?.message || '星系节点位置保存失败，请检查网络后重试')
      }
    }
  }

  // 将画布中最新节点坐标同步回 currentGraphData，保证新增/编辑/裂变与持久化位置一致
  function syncLiveNodePositions() {
    const graphData = getCurrentGraphData()
    const liveNodes = graph?.getNodes ? graph.getNodes() : []
    if (!graphData || !Array.isArray(liveNodes) || liveNodes.length === 0) return
    const liveMap = new Map(liveNodes.map((ln) => [ln.id, ln]))
    for (const raw of graphData.nodes || []) {
      const live = liveMap.get(raw.id)
      if (live && typeof live.x === 'number' && typeof live.y === 'number') {
        if (raw.nodeType === 'hub') {
          raw.x = 0
          raw.y = 0
        } else {
          raw.x = Math.round(live.x)
          raw.y = Math.round(live.y)
          raw.fixed = Boolean(raw.fixed || live.fixed)
        }
      }
    }
  }

  function captureGraphSnapshot() {
    syncLiveNodePositions()
    const graphData = getCurrentGraphData()
    const currentWbId = getCurrentWordbookId()
    if (!currentWbId || !graphData) return null
    const nodesPayload = (graphData.nodes || []).map((n) => ({
      id: n.id,
      nodeType: n.nodeType,
      term: n.term,
      definition: n.definition,
      label: n.label,
      subLabel: n.subLabel,
      dimension: n.dimension,
      entryId: n.entryId,
      status: n.status,
      masteryScore: n.masteryScore,
      reviewStage: n.reviewStage,
      x: typeof n.x === 'number' ? Math.round(n.x) : undefined,
      y: typeof n.y === 'number' ? Math.round(n.y) : undefined,
      fixed: Boolean(n.fixed),
    }))
    const edgesPayload = (graphData.edges || []).map((e) => ({
      source: e.source,
      target: e.target,
      relationType: e.relationType,
      label: e.label,
    }))
    const bagTermsList = (getSelectedNodeIds() || []).map((id) => {
      const node = (graphData.nodes || []).find((n) => n.id === id || n.term === id)
      return node ? node.term || node.label || id : id
    })
    return {
      wordbookId: currentWbId,
      scenarioCode: getActiveScenarioCode(),
      graphNodesJson: JSON.stringify(nodesPayload),
      graphEdgesJson: JSON.stringify(edgesPayload),
      bagTermsJson: JSON.stringify(bagTermsList),
    }
  }

  async function commitGraphSnapshot(snapshot, requestOptions = {}) {
    if (!api?.saveRuminationGraph || !snapshot) return false
    const seq = ++latestSaveSeq
    const { manual = false, ...fetchOptions } = requestOptions
    updateSaveStatus('saving', '⏳ 保存布局中...')
    let succeeded = false
    const executeSave = () => {
      const liveGraphData = isSameContext(snapshot.wordbookId, snapshot.scenarioCode)
        ? getCurrentGraphData()
        : null
      const expectedVersion = typeof liveGraphData?.version === 'number' ? liveGraphData.version : null
      return api.saveRuminationGraph({ ...snapshot, expectedVersion }, fetchOptions)
        .then((res) => {
          succeeded = true
          applyServerVersionIfCurrent(snapshot.wordbookId, snapshot.scenarioCode, res?.version)
          if (seq === latestSaveSeq) {
            updateSaveStatus('saved', '✅ 布局位置已保存')
            if (manual) {
              toast('💾 已保存当前星系图谱与节点位置 (⌘S)')
            }
          }
        })
        .catch((err) => {
          handleSaveError(seq, snapshot.wordbookId, snapshot.scenarioCode, err, Boolean(fetchOptions.keepalive))
        })
    }
    // 页面卸载 keepalive 保底时立即发出，其余场景严格串行避免乱序覆盖
    const run = fetchOptions.keepalive ? executeSave() : inFlightSavePromise.then(executeSave, executeSave)
    inFlightSavePromise = run
    await run
    return succeeded
  }

  async function saveNodePositionsIncremental(positionsList, requestOptions = {}) {
    if (!Array.isArray(positionsList) || positionsList.length === 0) return false
    if (getCurrentGraphData()?.building) return false
    const currentWbId = getCurrentWordbookId()
    const currentScenario = getActiveScenarioCode()
    if (!currentWbId || !currentScenario) return false

    if (!api?.updateRuminationGraphPositions) {
      return saveCurrentGraphDebounced(true, requestOptions)
    }

    for (const item of positionsList) {
      if (item?.nodeId) {
        pendingPositions.set(item.nodeId, {
          nodeId: item.nodeId,
          x: Math.round(item.x),
          y: Math.round(item.y),
          fixed: item.fixed !== false,
        })
      }
    }

    const seq = ++latestSaveSeq
    const fetchOptions = { ...requestOptions }
    updateSaveStatus('saving', '⏳ 同步节点坐标...')
    let succeeded = false

    const executePositionSave = () => {
      const batch = Array.from(pendingPositions.values())
      pendingPositions.clear()
      if (batch.length === 0) {
        succeeded = true
        return Promise.resolve()
      }
      return api.updateRuminationGraphPositions({
        wordbookId: currentWbId,
        scenarioCode: currentScenario,
        positions: batch,
      }, fetchOptions)
        .then((res) => {
          succeeded = true
          applyServerVersionIfCurrent(currentWbId, currentScenario, res?.version)
          if (seq === latestSaveSeq) {
            updateSaveStatus('saved', '✅ 布局位置已保存')
          }
        })
        .catch((err) => {
          handleSaveError(seq, currentWbId, currentScenario, err, Boolean(fetchOptions.keepalive))
        })
    }

    const run = fetchOptions.keepalive
      ? executePositionSave()
      : inFlightSavePromise.then(executePositionSave, executePositionSave)
    inFlightSavePromise = run
    await run
    return succeeded
  }

  async function saveBagItemIncremental({ term, add }) {
    if (!term) return false
    const currentWbId = getCurrentWordbookId()
    const currentScenario = getActiveScenarioCode()
    if (!currentWbId || !currentScenario) return false

    if (!api?.updateRuminationBagItem) {
      return saveCurrentGraphDebounced(true)
    }

    const seq = ++latestSaveSeq
    pendingBagCount += 1
    updateSaveStatus('saving', '⏳ 同步词袋...')
    let succeeded = false

    const executeBagSave = () => api.updateRuminationBagItem({
      wordbookId: currentWbId,
      scenarioCode: currentScenario,
      term,
      add: Boolean(add),
    })
      .then((res) => {
        succeeded = true
        pendingBagCount = Math.max(0, pendingBagCount - 1)
        applyServerVersionIfCurrent(currentWbId, currentScenario, res?.version)
        if (pendingBagCount === 0 && Array.isArray(res?.bagTerms) && isSameContext(currentWbId, currentScenario)) {
          onBagSynced?.(res.bagTerms)
        }
        if (seq === latestSaveSeq) {
          updateSaveStatus('saved', '✅ 词袋已同步')
        }
      })
      .catch((err) => {
        pendingBagCount = Math.max(0, pendingBagCount - 1)
        handleSaveError(seq, currentWbId, currentScenario, err, false)
      })

    const run = inFlightSavePromise.then(executeBagSave, executeBagSave)
    inFlightSavePromise = run
    await run
    return succeeded
  }

  async function flushPendingGraphSave(requestOptions = {}) {
    if (saveTimer) {
      clearTimeout(saveTimer)
      saveTimer = null
    }
    if (pendingPositions.size > 0 && api?.updateRuminationGraphPositions && !pendingSnapshot) {
      const batch = Array.from(pendingPositions.values())
      await saveNodePositionsIncremental(batch, requestOptions)
    }
    if (pendingSnapshot) {
      const snap = pendingSnapshot
      pendingSnapshot = null
      return commitGraphSnapshot(snap, requestOptions)
    }
    await inFlightSavePromise
    return true
  }

  function saveCurrentGraphDebounced(immediate = false, requestOptions = {}) {
    if (getCurrentGraphData()?.building) {
      if (requestOptions.manual) {
        toast('当前场景图谱正在后台构建中，构建完成后将自动保存')
      }
      return Promise.resolve(false)
    }
    const snapshot = captureGraphSnapshot()
    if (!snapshot) {
      if (requestOptions.manual) {
        toast('请先选择单词本后再保存星系图谱')
      }
      return Promise.resolve(false)
    }
    if (saveTimer) {
      clearTimeout(saveTimer)
      saveTimer = null
    }
    if (immediate) {
      pendingSnapshot = null
      return commitGraphSnapshot(snapshot, requestOptions)
    }
    pendingSnapshot = snapshot
    saveTimer = setTimeout(() => {
      saveTimer = null
      const snap = pendingSnapshot
      pendingSnapshot = null
      commitGraphSnapshot(snap, requestOptions)
    }, 200)
    return Promise.resolve(true)
  }

  function saveGraphNow() {
    return saveCurrentGraphDebounced(true, { manual: true })
  }

  function handlePageHide() {
    flushPendingGraphSave({ keepalive: true })
  }

  function handleKeydown(event) {
    if (!event) return
    const isModifier = Boolean(event.metaKey || event.ctrlKey)
    if (!isModifier || event.altKey || event.shiftKey) return
    const keyLower = String(event.key || '').toLowerCase()
    if (keyLower !== 's' && event.code !== 'KeyS') return
    if (!document.body?.contains(canvas)) return

    const ruminationView = document.getElementById('ruminationView') || document.getElementById('articleStudyView')
    const isWorkspaceActive = state?.activeView
      ? (state.activeView === 'ruminationView' || state.activeView === 'articleStudyView')
      : (!ruminationView || ruminationView.classList.contains('active'))
    if (!isWorkspaceActive) return

    if (document.querySelector('.modal-backdrop:not(.hidden)')) return
    const activeEl = document.activeElement
    const activeTag = activeEl ? activeEl.tagName.toLowerCase() : ''
    if (['input', 'textarea'].includes(activeTag) || activeEl?.isContentEditable) return

    event.preventDefault()
    event.stopPropagation()
    saveGraphNow()
  }

  window.addEventListener('pagehide', handlePageHide)
  document.addEventListener('keydown', handleKeydown)

  function destroy() {
    if (saveTimer) {
      clearTimeout(saveTimer)
      saveTimer = null
    }
    window.removeEventListener('pagehide', handlePageHide)
    document.removeEventListener('keydown', handleKeydown)
  }

  return {
    updateSaveStatus,
    syncLiveNodePositions,
    flushPendingGraphSave,
    saveCurrentGraphDebounced,
    saveNodePositionsIncremental,
    saveBagItemIncremental,
    saveGraphNow,
    destroy,
  }
}
