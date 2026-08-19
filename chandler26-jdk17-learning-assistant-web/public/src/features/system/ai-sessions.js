import { hideModal, showModal } from '/src/shared/modal.js'
import { escapeHtml, formatDateTime } from '/src/shared/text.js'

export function createAiSessionAdminFeature(ctx) {
  const { state, elements, request, toast, logEvent } = ctx

  function isAdmin() {
    return state.preview || state.user?.roleCode === 'ADMIN'
  }

  async function loadAiSessions() {
    if (!isAdmin() || !elements.aiSessionRows) return
    if (state.preview) {
      const items = previewAiSessions()
      renderAiSessions({ total: items.length, page: 1, pageSize: 20, items })
      return
    }
    const params = new URLSearchParams({
      page: String(state.aiSessionPage || 1),
      pageSize: String(state.aiSessionPageSize || 20),
    })
    const keyword = elements.aiSessionKeywordInput?.value.trim()
    const sceneCode = elements.aiSessionSceneInput?.value.trim()
    const provider = elements.aiSessionProviderInput?.value.trim()
    const success = elements.aiSessionSuccessFilter?.value
    if (keyword) params.set('keyword', keyword)
    if (sceneCode) params.set('sceneCode', sceneCode)
    if (provider) params.set('provider', provider)
    if (success) params.set('success', success)

    try {
      const result = await request(`/api/v1/ai/chat-sessions/admin?${params}`)
      renderAiSessions(result || { total: 0, page: state.aiSessionPage, pageSize: state.aiSessionPageSize, items: [] })
    } catch (error) {
      logEvent('error', 'AI 会话加载失败', error.message)
      toast(`AI 会话加载失败：${error.message}`)
    }
  }

  function renderAiSessions(result) {
    const items = Array.isArray(result?.items) ? result.items : []
    const total = Number(result?.total || items.length)
    const page = Number(result?.page || state.aiSessionPage || 1)
    const pageSize = Number(result?.pageSize || state.aiSessionPageSize || 20)
    state.aiSessionPage = page
    state.aiSessionPageSize = pageSize

    if (elements.aiSessionSummary) elements.aiSessionSummary.textContent = `${total} 条会话`
    if (elements.aiSessionPageInfo) elements.aiSessionPageInfo.textContent = `第 ${page} 页 · 共 ${total} 条`

    if (!items.length) {
      elements.aiSessionRows.innerHTML = '<tr><td colspan="9" class="empty">暂无符合条件的 AI 会话</td></tr>'
      return
    }

    elements.aiSessionRows.innerHTML = items.map((item) => {
      const callCount = Number(item.callCount || 0)
      const successCount = Number(item.successCount || 0)
      const failedCount = Number(item.failedCount || 0)
      const isFailed = failedCount > 0
      const statusPill = isFailed
        ? `<span class="task-status task-status-failed">失败 · ${failedCount}/${callCount}</span>`
        : `<span class="task-status task-status-completed">成功 · ${successCount}/${callCount}</span>`

      return `
        <tr>
          <td><strong>${escapeHtml(item.title || `会话 #${item.id}`)}</strong><small class="table-subline">ID: ${escapeHtml(item.id)} · ${item.messageCount || 0} 条消息</small></td>
          <td>${escapeHtml(item.userName || `用户 #${item.userId}`)}</td>
          <td><span class="mini-pill">${escapeHtml(item.sceneCode || '-')}</span></td>
          <td><span class="mini-pill">${escapeHtml(item.lastProvider || '-')}/${escapeHtml(item.lastModelName || '-')}</span></td>
          <td>${statusPill}</td>
          <td>${Number(item.totalTokens || 0).toLocaleString()}</td>
          <td>${item.averageLatencyMs != null ? `${item.averageLatencyMs} ms` : '-'}</td>
          <td>${escapeHtml(formatDateTime(item.updateTime || item.createTime) || '-')}</td>
          <td><div class="row-actions"><button class="icon-action-button" type="button" data-ai-session-detail="${escapeHtml(item.id)}" title="查看详情" aria-label="查看详情">👁</button></div></td>
        </tr>
      `
    }).join('')

    elements.aiSessionRows.querySelectorAll('[data-ai-session-detail]').forEach((button) => {
      button.addEventListener('click', () => openDetail(button.dataset.aiSessionDetail))
    })
  }

  function resetAiSessionFilters() {
    if (elements.aiSessionKeywordInput) elements.aiSessionKeywordInput.value = ''
    if (elements.aiSessionSceneInput) elements.aiSessionSceneInput.value = ''
    if (elements.aiSessionProviderInput) elements.aiSessionProviderInput.value = ''
    if (elements.aiSessionSuccessFilter) elements.aiSessionSuccessFilter.value = ''
    state.aiSessionPage = 1
    loadAiSessions()
  }

  function changeAiSessionPage(offset) {
    if (offset < 0 && state.aiSessionPage <= 1) return
    state.aiSessionPage = Math.max(1, state.aiSessionPage + offset)
    loadAiSessions()
  }

  async function openDetail(id) {
    if (!id) return
    if (state.preview) {
      renderDetail(previewAiSessionDetail(id))
      showModal(elements.aiSessionDetailModal)
      return
    }
    try {
      const detail = await request(`/api/v1/ai/chat-sessions/admin/${encodeURIComponent(id)}`)
      renderDetail(detail)
      showModal(elements.aiSessionDetailModal)
    } catch (error) {
      toast(`加载会话详情失败：${error.message}`)
    }
  }

  function closeDetail() {
    hideModal(elements.aiSessionDetailModal)
  }

  function renderDetail(detail) {
    if (!detail) return
    const session = detail.session || {}
    const messages = Array.isArray(detail.messages) ? detail.messages : []
    const calls = Array.isArray(detail.calls) ? detail.calls : []

    if (elements.aiSessionDetailTitle) {
      elements.aiSessionDetailTitle.textContent = session.title || `AI 会话 #${session.id}`
    }

    if (elements.aiSessionDetailMeta) {
      elements.aiSessionDetailMeta.innerHTML = `
        <div class="ai-session-meta-grid">
          <div><label>会话 ID</label><span>${escapeHtml(session.id || '-')}</span></div>
          <div><label>用户</label><span>${escapeHtml(session.userName || session.userId || '-')}</span></div>
          <div><label>场景</label><span><span class="mini-pill">${escapeHtml(session.sceneCode || '-')}</span></span></div>
          <div><label>Agent</label><span>${escapeHtml(session.agentCode || '-')}</span></div>
          <div><label>业务类型/ID</label><span>${escapeHtml(session.businessType || '-')}: ${escapeHtml(session.businessId || '-')}</span></div>
          <div><label>模型</label><span>${escapeHtml(session.lastProvider || '-')}/${escapeHtml(session.lastModelName || '-')}</span></div>
          <div><label>总 Token</label><span>${Number(session.totalTokens || 0).toLocaleString()}</span></div>
          <div><label>平均耗时</label><span>${session.averageLatencyMs != null ? `${session.averageLatencyMs} ms` : '-'}</span></div>
          <div><label>创建时间</label><span>${escapeHtml(formatDateTime(session.createTime) || '-')}</span></div>
        </div>
      `
    }

    if (elements.aiSessionMessages) {
      if (!messages.length) {
        elements.aiSessionMessages.innerHTML = '<p class="empty">暂无消息记录</p>'
      } else {
        elements.aiSessionMessages.innerHTML = messages.map((msg) => `
          <div class="ai-session-message-card role-${escapeHtml(msg.role)}">
            <div class="ai-session-message-header">
              <span class="role-badge">${escapeHtml(msg.role)}</span>
              <small>${escapeHtml(formatDateTime(msg.createTime) || '')}</small>
            </div>
            <pre class="ai-session-message-content">${escapeHtml(msg.content || '')}</pre>
          </div>
        `).join('')
      }
    }

    if (elements.aiSessionCalls) {
      if (!calls.length) {
        elements.aiSessionCalls.innerHTML = '<p class="empty">暂无模型调用审计</p>'
      } else {
        elements.aiSessionCalls.innerHTML = calls.map((call) => `
          <div class="ai-session-call-card ${call.success ? 'success' : 'failed'}">
            <div class="ai-session-call-header">
              <span class="task-status ${call.success ? 'task-status-completed' : 'task-status-failed'}">${call.success ? '成功' : '失败'}</span>
              <strong>${escapeHtml(call.provider || '')} / ${escapeHtml(call.modelName || '')}</strong>
              <small>耗时 ${call.latencyMs || 0} ms · Token: ${Number(call.totalTokens || 0)}</small>
            </div>
            ${call.errorMessage ? `<p class="call-error">${escapeHtml(call.errorMessage)}</p>` : ''}
          </div>
        `).join('')
      }
    }
  }

  function previewAiSessions() {
    return [
      {
        id: 1,
        userId: 9002,
        userName: 'chandler',
        title: 'abandon 学习卡生成',
        agentCode: 'english_vocabulary',
        sceneCode: 'vocabulary_card',
        businessType: 'word_card',
        businessId: 'abandon',
        messageCount: 2,
        callCount: 1,
        successCount: 1,
        failedCount: 0,
        totalTokens: 1250,
        averageLatencyMs: 820,
        lastProvider: 'moonshot',
        lastModelName: 'moonshot-v1-8k',
        createTime: new Date().toISOString(),
        updateTime: new Date().toISOString(),
      },
    ]
  }

  function previewAiSessionDetail(id) {
    const session = previewAiSessions()[0]
    return {
      session,
      messages: [
        { id: 1, role: 'user', content: '请为单词 abandon 生成学习卡片 JSON', createTime: session.createTime },
        { id: 2, role: 'assistant', content: '{"term":"abandon","definitions":[...]}', createTime: session.updateTime },
      ],
      calls: [
        { id: 101, provider: 'moonshot', modelName: 'moonshot-v1-8k', latencyMs: 820, totalTokens: 1250, success: true },
      ],
    }
  }

  return {
    loadAiSessions,
    renderAiSessions,
    resetAiSessionFilters,
    changeAiSessionPage,
    openDetail,
    closeDetail,
  }
}
