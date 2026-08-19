import { hideModal, showModal } from '/src/shared/modal.js'
import { escapeHtml, formatDateTime } from '/src/shared/text.js'

function compact(value, length = 34) {
  const text = String(value ?? '')
  return text.length > length ? `${text.slice(0, length)}…` : text
}

export function createAiSessionAdminFeature(ctx) {
  const { state, elements, request, toast, logEvent } = ctx

  function isAdmin() {
    return state.preview || state.user?.roleCode === 'ADMIN'
  }

  async function loadAiSessions() {
    if (!isAdmin() || !elements.aiSessionRows) return
    if (state.preview) {
      const now = new Date().toISOString()
      renderPage({ total: 2, page: 1, pageSize: 20, items: [
        { id: 'preview-session-1', userId: 1, userName: 'Chandler', title: '词汇卡片：abandon', agentCode: 'english_vocabulary', sceneCode: 'vocabulary_card', callCount: 1, successCount: 1, failedCount: 0, totalTokens: 682, averageLatencyMs: 920, lastProvider: 'preview', lastModelName: 'mock-chat', updateTime: now },
        { id: 'preview-session-2', userId: 2, userName: '学习者', title: '场景材料：机场出发', agentCode: 'english_scene', sceneCode: 'scene_material', callCount: 2, successCount: 1, failedCount: 1, totalTokens: 2240, averageLatencyMs: 1450, lastProvider: 'preview', lastModelName: 'mock-chat', updateTime: now },
      ] })
      return
    }
    const params = new URLSearchParams({ page: String(state.aiSessionPage || 1), pageSize: String(state.aiSessionPageSize || 20) })
    const values = {
      keyword: elements.aiSessionKeywordInput?.value.trim(),
      sceneCode: elements.aiSessionSceneInput?.value.trim(),
      provider: elements.aiSessionProviderInput?.value.trim(),
      success: elements.aiSessionSuccessFilter?.value,
    }
    Object.entries(values).forEach(([key, value]) => { if (value) params.set(key, value) })
    try {
      renderPage(await request(`/api/v1/ai/chat-sessions/admin?${params}`))
    } catch (error) {
      logEvent('error', 'AI 会话加载失败', error.message)
      toast(`AI 会话加载失败：${error.message}`)
    }
  }

  function renderPage(result = {}) {
    const items = Array.isArray(result.items) ? result.items : []
    const total = Number(result.total || 0)
    const page = Number(result.page || state.aiSessionPage || 1)
    const pageSize = Number(result.pageSize || state.aiSessionPageSize || 20)
    state.aiSessionPage = page
    state.aiSessionPageSize = pageSize
    if (elements.aiSessionSummary) elements.aiSessionSummary.textContent = `${total} 条会话`
    if (elements.aiSessionPageInfo) elements.aiSessionPageInfo.textContent = `第 ${page} 页 · 共 ${total} 条`
    if (!items.length) {
      elements.aiSessionRows.innerHTML = '<tr><td colspan="9" class="empty">暂无符合条件的 AI 会话</td></tr>'
      return
    }
    elements.aiSessionRows.innerHTML = items.map((item) => {
      const success = Number(item.failedCount || 0) === 0 && Number(item.callCount || 0) > 0
      const resultLabel = Number(item.failedCount || 0) > 0 ? `失败 ${item.failedCount}` : success ? '成功' : '无调用'
      return `<tr>
        <td class="cell-ellipsis" title="${escapeHtml(item.title || item.id)}"><strong>${escapeHtml(compact(item.title || `会话 #${item.id}`, 28))}</strong><small class="table-subline">#${escapeHtml(item.id)}</small></td>
        <td>${escapeHtml(item.userName || item.userId || '-')}</td>
        <td class="cell-ellipsis" title="${escapeHtml(item.sceneCode || item.businessType || '')}">${escapeHtml(compact(item.sceneCode || item.businessType || '-'))}</td>
        <td class="cell-ellipsis" title="${escapeHtml(`${item.lastProvider || ''} · ${item.lastModelName || ''}`)}">${escapeHtml(compact(`${item.lastProvider || '-'} · ${item.lastModelName || '-'}`, 28))}</td>
        <td><span class="task-status ${success ? 'task-status-completed' : 'task-status-cancelled'}">${escapeHtml(resultLabel)}</span></td>
        <td>${Number(item.totalTokens || 0).toLocaleString()}</td>
        <td>${Number(item.averageLatencyMs || 0)} ms</td>
        <td>${escapeHtml(formatDateTime(item.updateTime) || '-')}</td>
        <td><button class="icon-action-button" type="button" data-ai-session-detail="${escapeHtml(item.id)}" title="查看完整详情" aria-label="查看完整详情">⌕</button></td>
      </tr>`
    }).join('')
    elements.aiSessionRows.querySelectorAll('[data-ai-session-detail]').forEach((button) => {
      button.addEventListener('click', () => openDetail(button.dataset.aiSessionDetail))
    })
  }

  async function openDetail(id) {
    if (!id || !elements.aiSessionDetailModal) return
    try {
      const result = state.preview ? previewDetail(id) : await request(`/api/v1/ai/chat-sessions/admin/${encodeURIComponent(id)}`)
      renderDetail(result)
      showModal(elements.aiSessionDetailModal)
    } catch (error) {
      logEvent('error', 'AI 会话详情加载失败', error.message)
      toast(`AI 会话详情加载失败：${error.message}`)
    }
  }

  function renderDetail(result = {}) {
    const session = result.session || {}
    if (elements.aiSessionDetailTitle) elements.aiSessionDetailTitle.textContent = session.title || `AI 会话 #${session.id || '-'}`
    if (elements.aiSessionDetailMeta) elements.aiSessionDetailMeta.innerHTML = `<span>用户：${escapeHtml(session.userName || session.userId || '-')}</span><span>场景：${escapeHtml(session.sceneCode || '-')}</span><span>Agent：${escapeHtml(session.agentCode || '-')}</span><span>更新时间：${escapeHtml(formatDateTime(session.updateTime) || '-')}</span>`
    const messages = Array.isArray(result.messages) ? result.messages : []
    const calls = Array.isArray(result.calls) ? result.calls : []
    if (elements.aiSessionMessages) elements.aiSessionMessages.innerHTML = messages.length ? messages.map((message) => `<article class="ai-session-message"><div><span class="mini-pill">${escapeHtml(message.role || '-')}</span><small>${escapeHtml(formatDateTime(message.createTime) || '')}</small></div><pre>${escapeHtml(message.content || '')}</pre></article>`).join('') : '<div class="empty">暂无消息</div>'
    if (elements.aiSessionCalls) elements.aiSessionCalls.innerHTML = calls.length ? calls.map((call) => `<article class="ai-session-call"><div class="ai-session-call-head"><strong>${escapeHtml(call.invocationSceneCode || '-')}</strong><span class="task-status ${call.success ? 'task-status-completed' : 'task-status-cancelled'}">${call.success ? '成功' : '失败'}</span></div><p>${escapeHtml(call.provider || '-')} · ${escapeHtml(call.modelName || '-')} · ${Number(call.totalTokens || 0)} tokens · ${Number(call.latencyMs || 0)} ms</p><details><summary>查看请求/响应</summary><pre>${escapeHtml(call.responseJson || call.requestJson || call.errorMessage || '')}</pre></details></article>`).join('') : '<div class="empty">暂无模型调用记录</div>'
  }

  function closeDetail() { hideModal(elements.aiSessionDetailModal) }
  function previewDetail(id) {
    return { session: { id, title: '预览 AI 会话', userName: 'Chandler', sceneCode: 'vocabulary_card', agentCode: 'english_vocabulary', updateTime: new Date().toISOString() }, messages: [{ role: 'user', content: '请生成 abandon 的词汇卡片', createTime: new Date().toISOString() }, { role: 'assistant', content: '{"term":"abandon","is_valid":true}', createTime: new Date().toISOString() }], calls: [{ invocationSceneCode: 'vocabulary_card', provider: 'preview', modelName: 'mock-chat', success: true, totalTokens: 682, latencyMs: 920, responseJson: '{"term":"abandon","is_valid":true}' }] }
  }
  function changeAiSessionPage(offset) { if (offset < 0 && state.aiSessionPage <= 1) return; state.aiSessionPage = Math.max(1, (state.aiSessionPage || 1) + offset); loadAiSessions() }
  function resetAiSessionFilters() { [elements.aiSessionKeywordInput, elements.aiSessionSceneInput, elements.aiSessionProviderInput].forEach((input) => { if (input) input.value = '' }); if (elements.aiSessionSuccessFilter) elements.aiSessionSuccessFilter.value = ''; state.aiSessionPage = 1; loadAiSessions() }
  return { loadAiSessions, renderPage, openDetail, closeDetail, changeAiSessionPage, resetAiSessionFilters }
}
