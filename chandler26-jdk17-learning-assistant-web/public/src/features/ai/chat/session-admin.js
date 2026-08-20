import { hideModal, showModal } from '/src/shared/modal.js'
import { escapeHtml, formatDateTime } from '/src/shared/text.js'

const PREVIEW_LENGTH = 320
const RAW_PREVIEW_LENGTH = 2400
const TREE_ITEM_LIMIT = 24

function compact(value, length = PREVIEW_LENGTH) {
  const text = String(value ?? '').trim()
  return text.length > length ? `${text.slice(0, length)}...` : text
}

function payloadInfo(value) {
  const raw = String(value ?? '')
  if (!raw.trim()) return { raw, parsed: null, pretty: '', valid: false }
  try {
    const parsed = JSON.parse(raw)
    return { raw, parsed, pretty: JSON.stringify(parsed, null, 2), valid: true }
  } catch {
    return { raw, parsed: null, pretty: raw, valid: false }
  }
}

function payloadLabel(value) {
  if (Array.isArray(value)) return `数组 · ${value.length} 项`
  if (value && typeof value === 'object') return `对象 · ${Object.keys(value).length} 个字段`
  if (value === null) return 'null'
  return typeof value
}

function renderJsonTree(value, key = '', depth = 0) {
  const label = key ? `<span class="json-tree-key">${escapeHtml(key)}</span>` : ''
  if (value === null || typeof value !== 'object') {
    const text = typeof value === 'string' ? value : String(value)
    if (text.length > PREVIEW_LENGTH) {
      return `<div class="json-tree-leaf">${label}<details><summary>${escapeHtml(compact(text))} <small>${text.length} 字符</small></summary><pre>${escapeHtml(text)}</pre></details></div>`
    }
    return `<div class="json-tree-leaf">${label}<span class="json-tree-value">${escapeHtml(typeof value === 'string' ? `"${text}"` : text)}</span></div>`
  }
  const entries = Array.isArray(value) ? value.map((item, index) => [`[${index}]`, item]) : Object.entries(value)
  const visibleEntries = entries.slice(0, TREE_ITEM_LIMIT)
  const remain = entries.length - visibleEntries.length
  const children = visibleEntries.map(([childKey, childValue]) => renderJsonTree(childValue, childKey, depth + 1)).join('')
  const remainTip = remain > 0 ? `<div class="json-tree-more">其余 ${remain} 项已折叠</div>` : ''
  return `<details class="json-tree-node"${depth < 1 ? ' open' : ''}><summary>${label}<span>${payloadLabel(value)}</span></summary><div class="json-tree-children">${children}${remainTip}</div></details>`
}

export function createAiSessionAdminFeature(ctx) {
  const { state, elements, request, toast, logEvent } = ctx
  let detail = null
  let activeDetailTab = 'overview'
  let selectedCallId = null
  const payloadStore = new Map()

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
    const params = new URLSearchParams({ page: String(state.aiSessionPage || 1), pageSize: String(state.aiSessionPageSize || 20) })
    const keyword = elements.aiSessionKeywordInput?.value.trim()
    const sceneCode = elements.aiSessionSceneInput?.value.trim()
    const provider = elements.aiSessionProviderInput?.value.trim()
    const success = elements.aiSessionSuccessFilter?.value
    if (keyword) params.set('keyword', keyword)
    if (sceneCode) params.set('sceneCode', sceneCode)
    if (provider) params.set('provider', provider)
    if (success) params.set('success', success)
    try {
      renderAiSessions(await request(`/api/v1/ai/chat-sessions/admin?${params}`))
    } catch (error) {
      logEvent('error', 'AI 会话加载失败', error.message)
      toast(`AI 会话加载失败：${error.message}`)
    }
  }

  function renderAiSessions(result = {}) {
    const items = Array.isArray(result.items) ? result.items : []
    const total = Number(result.total || items.length)
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
      const callCount = Number(item.callCount || 0)
      const successCount = Number(item.successCount || 0)
      const failedCount = Number(item.failedCount || 0)
      const statusPill = failedCount > 0
        ? `<span class="task-status task-status-failed">失败 · ${failedCount}/${callCount}</span>`
        : `<span class="task-status task-status-completed">成功 · ${successCount}/${callCount}</span>`
      const title = item.title || `会话 #${item.id}`
      const model = `${item.lastProvider || '-'} / ${item.lastModelName || '-'}`
      return `<tr>
        <td class="cell-ellipsis" title="${escapeHtml(title)}"><strong>${escapeHtml(compact(title, 28))}</strong><small class="table-subline">ID: ${escapeHtml(item.id)} · ${item.messageCount || 0} 条消息</small></td>
        <td>${escapeHtml(item.userName || `用户 #${item.userId}`)}</td>
        <td><span class="mini-pill">${escapeHtml(item.sceneCode || '-')}</span></td>
        <td class="cell-ellipsis" title="${escapeHtml(model)}"><span class="mini-pill">${escapeHtml(compact(model, 28))}</span></td>
        <td>${statusPill}</td><td>${Number(item.totalTokens || 0).toLocaleString()}</td><td>${item.averageLatencyMs != null ? `${item.averageLatencyMs} ms` : '-'}</td>
        <td>${escapeHtml(formatDateTime(item.updateTime || item.createTime) || '-')}</td>
        <td><div class="row-actions"><button class="icon-action-button" type="button" data-ai-session-detail="${escapeHtml(item.id)}" title="查看会话诊断" aria-label="查看会话诊断">⌕</button></div></td>
      </tr>`
    }).join('')
    elements.aiSessionRows.querySelectorAll('[data-ai-session-detail]').forEach((button) => {
      button.addEventListener('click', () => openDetail(button.dataset.aiSessionDetail))
    })
  }

  function resetAiSessionFilters() {
    ;[elements.aiSessionKeywordInput, elements.aiSessionSceneInput, elements.aiSessionProviderInput].forEach((input) => { if (input) input.value = '' })
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
    if (!id || !elements.aiSessionDetailModal) return
    try {
      detail = state.preview ? previewAiSessionDetail(id) : await request(`/api/v1/ai/chat-sessions/admin/${encodeURIComponent(id)}`)
      activeDetailTab = 'overview'
      selectedCallId = detail?.calls?.[0]?.id ?? null
      renderDetail()
      showModal(elements.aiSessionDetailModal)
    } catch (error) {
      logEvent('error', 'AI 会话详情加载失败', error.message)
      toast(`加载会话详情失败：${error.message}`)
    }
  }

  function closeDetail() {
    hideModal(elements.aiSessionDetailModal)
    detail = null
    payloadStore.clear()
  }

  function renderDetail() {
    if (!detail) return
    const session = detail.session || {}
    if (elements.aiSessionDetailTitle) elements.aiSessionDetailTitle.textContent = session.title || `AI 会话 #${session.id || '-'}`
    if (elements.aiSessionDetailMeta) elements.aiSessionDetailMeta.innerHTML = `<div class="ai-session-meta-grid">
      <div><label>用户</label><span>${escapeHtml(session.userName || session.userId || '-')}</span></div>
      <div><label>业务场景</label><span>${escapeHtml(session.sceneCode || '-')}</span></div>
      <div><label>Agent</label><span>${escapeHtml(session.agentCode || '-')}</span></div>
      <div><label>业务对象</label><span>${escapeHtml(session.businessType || '-')}: ${escapeHtml(session.businessId || '-')}</span></div>
      <div><label>最近模型</label><span>${escapeHtml(session.lastProvider || '-')} / ${escapeHtml(session.lastModelName || '-')}</span></div>
      <div><label>会话更新时间</label><span>${escapeHtml(formatDateTime(session.updateTime) || '-')}</span></div>
    </div>`
    renderDetailTabs()
    renderDetailPanel()
  }

  function renderDetailTabs() {
    if (!elements.aiSessionDetailTabs) return
    elements.aiSessionDetailTabs.querySelectorAll('[data-ai-session-detail-tab]').forEach((button) => {
      const active = button.dataset.aiSessionDetailTab === activeDetailTab
      button.classList.toggle('active', active)
      button.setAttribute('aria-selected', String(active))
      button.onclick = () => {
        activeDetailTab = button.dataset.aiSessionDetailTab
        renderDetailTabs()
        renderDetailPanel()
      }
    })
  }

  function renderDetailPanel() {
    if (!elements.aiSessionDetailContent || !detail) return
    payloadStore.clear()
    const panel = activeDetailTab === 'messages' ? renderMessagesPanel()
      : activeDetailTab === 'calls' ? renderCallsPanel()
        : activeDetailTab === 'raw' ? renderRawPanel() : renderOverviewPanel()
    elements.aiSessionDetailContent.innerHTML = panel
    bindDetailPanelEvents()
  }

  function renderOverviewPanel() {
    const session = detail.session || {}
    const calls = Array.isArray(detail.calls) ? detail.calls : []
    const messages = Array.isArray(detail.messages) ? detail.messages : []
    const failedCount = calls.filter((call) => !call.success).length
    const repairedCount = calls.filter((call) => ['repaired', 'repaired_balanced'].includes(readAudit(call.responseJson)?.structuredParseStage)).length
    const latest = calls[0]
    return `<section class="ai-session-overview">
      <div class="ai-session-kpi-grid">
        ${metricCard('模型调用', `${calls.length} 次`, failedCount ? `${failedCount} 次失败` : '全部成功')}
        ${metricCard('消息记录', `${messages.length} 条`, `${messages.filter((message) => message.role === 'assistant').length} 条模型回复`)}
        ${metricCard('Token 消耗', Number(session.totalTokens || 0).toLocaleString(), '会话累计')}
        ${metricCard('平均耗时', session.averageLatencyMs != null ? `${session.averageLatencyMs} ms` : '-', repairedCount ? `${repairedCount} 次解析修复` : '未触发解析修复')}
      </div>
      <div class="ai-session-overview-grid">
        <section class="ai-session-summary-card"><div class="panel-heading compact-heading"><div><p class="eyebrow">Latest Call</p><h4>最近一次模型调用</h4></div></div>${latest ? renderCallSummary(latest, true) : '<p class="empty">本会话尚无模型调用审计。</p>'}</section>
        <section class="ai-session-summary-card"><div class="panel-heading compact-heading"><div><p class="eyebrow">Audit Policy</p><h4>审计与排障</h4></div></div><p class="ai-session-help">调用审计默认只保存 Token、耗时、解析器和请求/响应摘要。完整模型正文仅在服务端显式开启审计正文留存时出现；会话消息仍用于追踪学习业务流。</p><button class="secondary-button compact" type="button" data-ai-session-switch-tab="calls">查看模型调用</button></section>
      </div>
    </section>`
  }

  function renderMessagesPanel() {
    const messages = Array.isArray(detail.messages) ? detail.messages : []
    if (!messages.length) return '<p class="empty">暂无消息记录</p>'
    return `<section class="ai-session-message-list">${messages.map((message, index) => {
      const key = `message-${message.id ?? index}`
      const body = String(message.content || '')
      payloadStore.set(key, body)
      const parsed = payloadInfo(body)
      return `<article class="ai-session-message-card role-${escapeHtml(message.role || 'unknown')}">
        <header class="ai-session-message-header"><div><span class="role-badge">${escapeHtml(message.role || '-')}</span><strong>${message.sequence != null ? `第 ${message.sequence} 条` : '消息'}</strong></div><small>${escapeHtml(formatDateTime(message.createTime) || '-')} · ${body.length} 字符</small></header>
        <p class="ai-session-message-preview">${escapeHtml(compact(body)) || '-'}</p>
        <div class="ai-session-message-actions">${parsed.valid ? `<span class="mini-pill">${escapeHtml(payloadLabel(parsed.parsed))}</span>` : '<span class="mini-pill">文本</span>'}<button class="text-action-button" type="button" data-ai-session-copy-key="${escapeHtml(key)}">复制</button><button class="text-action-button" type="button" data-ai-session-open-raw="${escapeHtml(key)}">查看完整内容</button></div>
      </article>`
    }).join('')}</section>`
  }

  function renderCallsPanel() {
    const calls = Array.isArray(detail.calls) ? detail.calls : []
    if (!calls.length) return '<p class="empty">暂无模型调用审计</p>'
    const selected = calls.find((call) => String(call.id) === String(selectedCallId)) || calls[0]
    selectedCallId = selected.id
    return `<section class="ai-call-workbench"><aside class="ai-call-list" aria-label="模型调用列表">${calls.map((call, index) => renderCallListItem(call, index)).join('')}</aside><article class="ai-call-inspector">${renderCallSummary(selected, false)}${renderPayloadInspector('请求审计', selected.requestJson, `call-${selected.id}-request`)}${renderPayloadInspector('响应审计', selected.responseJson, `call-${selected.id}-response`)}</article></section>`
  }

  function renderRawPanel() {
    const messages = Array.isArray(detail.messages) ? detail.messages : []
    const calls = Array.isArray(detail.calls) ? detail.calls : []
    const contentStored = calls.some((call) => readAudit(call.requestJson)?.contentStored || readAudit(call.responseJson)?.contentStored)
    return `<section class="ai-raw-panel"><div class="ai-raw-notice ${contentStored ? 'available' : ''}"><strong>${contentStored ? '本会话包含受控留存的调用正文' : '当前调用审计未留存模型请求/响应正文'}</strong><span>${contentStored ? '原始数据按需展开，并可单独复制。' : '下方仍可查看会话消息和模型调用摘要；后续排障可由管理员在受控环境开启审计正文留存。'}</span></div>
      <div class="ai-raw-section"><h4>会话消息</h4>${messages.length ? messages.map((message, index) => renderRawPayload(`raw-message-${message.id ?? index}`, `消息 ${message.sequence ?? index + 1} · ${message.role || '-'}`, message.content)).join('') : '<p class="empty">暂无消息</p>'}</div>
      <div class="ai-raw-section"><h4>模型调用审计</h4>${calls.length ? calls.map((call, index) => `${renderRawPayload(`raw-request-${call.id ?? index}`, `请求审计 · ${call.provider || '-'} / ${call.modelName || '-'}`, call.requestJson)}${renderRawPayload(`raw-response-${call.id ?? index}`, `响应审计 · ${call.success ? '成功' : '失败'}`, call.responseJson || call.errorMessage)}`).join('') : '<p class="empty">暂无调用记录</p>'}</div>
    </section>`
  }

  function renderCallListItem(call, index) {
    const audit = readAudit(call.responseJson)
    const parseStage = audit?.structuredParseStage
    return `<button class="ai-call-list-item ${String(call.id) === String(selectedCallId) ? 'active' : ''}" type="button" data-ai-session-call-id="${escapeHtml(call.id)}"><span class="task-status ${call.success ? 'task-status-completed' : 'task-status-failed'}">${call.success ? '成功' : '失败'}</span><strong>调用 ${index + 1} · ${escapeHtml(call.provider || '-')} / ${escapeHtml(call.modelName || '-')}</strong><small>${escapeHtml(call.invocationSceneCode || '-')} · ${Number(call.totalTokens || 0).toLocaleString()} Token · ${call.latencyMs || 0} ms</small>${parseStage ? `<em>解析：${escapeHtml(parseStage)}</em>` : ''}</button>`
  }

  function renderCallSummary(call, compactView) {
    const audit = readAudit(call.responseJson) || {}
    const repairs = Array.isArray(audit.structuredRepairs) ? audit.structuredRepairs : []
    return `<header class="ai-call-inspector-header ${compactView ? 'compact' : ''}"><div class="ai-call-title-row"><span class="task-status ${call.success ? 'task-status-completed' : 'task-status-failed'}">${call.success ? '调用成功' : '调用失败'}</span><strong>${escapeHtml(call.provider || '-')} / ${escapeHtml(call.modelName || '-')}</strong><span class="mini-pill">${escapeHtml(call.invocationSceneCode || '-')}</span></div><div class="ai-call-metrics"><span>输入 ${Number(call.promptTokens || 0).toLocaleString()} Token</span><span>输出 ${Number(call.completionTokens || 0).toLocaleString()} Token</span><span>总计 ${Number(call.totalTokens || 0).toLocaleString()} Token</span><span>${call.latencyMs != null ? `${call.latencyMs} ms` : '耗时未记录'}</span><span>${escapeHtml(formatDateTime(call.createTime) || '-')}</span></div>${audit.structuredParser ? `<div class="ai-parse-diagnostics"><span>解析器：${escapeHtml(audit.structuredParser)}</span><span>阶段：${escapeHtml(audit.structuredParseStage || '-')}</span>${repairs.length ? `<span>修复：${escapeHtml(repairs.join('、'))}</span>` : '<span>未改写模型输出</span>'}</div>` : ''}${call.errorMessage ? `<div class="call-error">${escapeHtml(call.errorMessage)}</div>` : ''}</header>`
  }

  function renderPayloadInspector(title, payload, key) {
    const info = payloadInfo(payload)
    payloadStore.set(key, info.raw)
    if (!info.raw) return `<section class="ai-payload-inspector"><header><h4>${escapeHtml(title)}</h4></header><p class="empty">暂无${escapeHtml(title)}数据</p></section>`
    return `<section class="ai-payload-inspector"><header><div><p class="eyebrow">${info.valid ? 'Structured Audit' : 'Plain Text'}</p><h4>${escapeHtml(title)}</h4></div><div class="inline-actions"><span class="mini-pill">${info.valid ? escapeHtml(payloadLabel(info.parsed)) : `${info.raw.length} 字符`}</span><button class="icon-action-button" type="button" title="复制当前数据" aria-label="复制当前数据" data-ai-session-copy-key="${escapeHtml(key)}">⧉</button></div></header>${info.valid ? `<div class="json-tree">${renderJsonTree(info.parsed)}</div>` : `<pre class="payload-text-preview">${escapeHtml(compact(info.raw, RAW_PREVIEW_LENGTH))}</pre>`}${renderRawPayload(`${key}-raw`, '查看原始文本', info.raw, true)}</section>`
  }

  function renderRawPayload(key, title, payload, nested = false) {
    const raw = String(payload ?? '')
    payloadStore.set(key, raw)
    return `<article class="ai-raw-payload ${nested ? 'nested' : ''}"><header><strong>${escapeHtml(title)}</strong><span>${raw.length} 字符</span><button class="text-action-button" type="button" data-ai-session-copy-key="${escapeHtml(key)}">复制</button></header><div class="ai-raw-payload-body" data-ai-session-raw-target="${escapeHtml(key)}"><pre>${escapeHtml(compact(raw, RAW_PREVIEW_LENGTH) || '-')}</pre>${raw.length > RAW_PREVIEW_LENGTH ? `<button class="text-action-button" type="button" data-ai-session-expand-raw="${escapeHtml(key)}">展开全部</button>` : ''}</div></article>`
  }

  function bindDetailPanelEvents() {
    if (!elements.aiSessionDetailContent) return
    elements.aiSessionDetailContent.querySelectorAll('[data-ai-session-call-id]').forEach((button) => button.addEventListener('click', () => { selectedCallId = button.dataset.aiSessionCallId; renderDetailPanel() }))
    elements.aiSessionDetailContent.querySelectorAll('[data-ai-session-switch-tab]').forEach((button) => button.addEventListener('click', () => { activeDetailTab = button.dataset.aiSessionSwitchTab; renderDetailTabs(); renderDetailPanel() }))
    elements.aiSessionDetailContent.querySelectorAll('[data-ai-session-copy-key]').forEach((button) => button.addEventListener('click', async () => {
      const value = payloadStore.get(button.dataset.aiSessionCopyKey)
      if (!value) return toast('当前没有可复制的数据')
      try { await navigator.clipboard.writeText(value); toast('已复制完整数据') } catch { toast('复制失败，请在原始数据中手动选择') }
    }))
    elements.aiSessionDetailContent.querySelectorAll('[data-ai-session-open-raw]').forEach((button) => button.addEventListener('click', () => { activeDetailTab = 'raw'; renderDetailTabs(); renderDetailPanel() }))
    elements.aiSessionDetailContent.querySelectorAll('[data-ai-session-expand-raw]').forEach((button) => button.addEventListener('click', () => {
      const key = button.dataset.aiSessionExpandRaw
      const target = elements.aiSessionDetailContent.querySelector(`[data-ai-session-raw-target="${CSS.escape(key)}"]`)
      if (target) target.innerHTML = `<pre>${escapeHtml(payloadStore.get(key) || '-')}</pre>`
    }))
  }

  function readAudit(payload) {
    const info = payloadInfo(payload)
    return info.valid && info.parsed && typeof info.parsed === 'object' && !Array.isArray(info.parsed) ? info.parsed : null
  }

  function metricCard(label, value, note) {
    return `<article class="ai-session-kpi"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong><small>${escapeHtml(note)}</small></article>`
  }

  function previewAiSessions() {
    const now = new Date().toISOString()
    return [
      { id: 1, userId: 9002, userName: 'chandler', title: '机场出发场景材料', agentCode: 'english_vocabulary_plan', sceneCode: 'vocabulary_scene_unit', businessType: 'learning_plan', businessId: '1001', messageCount: 2, callCount: 2, successCount: 2, failedCount: 0, totalTokens: 1250, averageLatencyMs: 820, lastProvider: 'deepseek', lastModelName: 'deepseek-chat', createTime: now, updateTime: now },
      { id: 2, userId: 9003, userName: '学习者', title: 'abandon 学习卡生成', agentCode: 'english_vocabulary', sceneCode: 'vocabulary_card_single', businessType: 'word_card', businessId: 'abandon', messageCount: 2, callCount: 1, successCount: 0, failedCount: 1, totalTokens: 0, averageLatencyMs: 1450, lastProvider: 'moonshot', lastModelName: 'moonshot-v1-8k', createTime: now, updateTime: now },
    ]
  }

  function previewAiSessionDetail(id) {
    const sessions = previewAiSessions()
    const session = sessions.find((item) => String(item.id) === String(id)) || sessions[0]
    const now = session.updateTime
    const requestAudit = JSON.stringify({ contentStored: false, invocationScene: session.sceneCode, provider: session.lastProvider, model: session.lastModelName, maxTokens: 16000, messages: [{ role: 'system', characters: 216 }, { role: 'user', characters: 1280 }] })
    const kimiRequestAudit = JSON.stringify({ contentStored: false, invocationScene: session.sceneCode, provider: 'moonshot', model: 'moonshot-v1-8k', maxTokens: 8000, messages: [{ role: 'system', characters: 216 }, { role: 'user', characters: 1280 }] })
    const responseAudit = JSON.stringify({ contentStored: false, contentCharacters: 5620, promptTokens: 820, completionTokens: 430, totalTokens: 1250, finishReason: 'stop', structuredParser: 'deepseek-json', structuredParseStage: 'raw', structuredRepairs: [] })
    return { session, messages: [
      { id: 2, role: 'assistant', sequence: 2, content: '{"title":"机场出发","learning_text":"At the airport...","translation":"在机场...","vocabulary":[{"term":"boarding pass","meaning":"登机牌"}]}', createTime: now },
      { id: 1, role: 'user', sequence: 1, content: '请根据学习目的和本批候选词生成一个可学习、可检查的机场出发场景材料。', createTime: now },
    ], calls: [
      { id: 101, provider: session.lastProvider, modelName: session.lastModelName, invocationSceneCode: session.sceneCode, latencyMs: 820, promptTokens: 820, completionTokens: 430, totalTokens: 1250, success: true, requestJson: requestAudit, responseJson: responseAudit, createTime: now },
      { id: 100, provider: 'moonshot', modelName: 'moonshot-v1-8k', invocationSceneCode: session.sceneCode, latencyMs: 1440, promptTokens: 780, completionTokens: 0, totalTokens: 780, success: false, errorMessage: 'AI 返回内容格式无效', requestJson: kimiRequestAudit, responseJson: JSON.stringify({ contentStored: false, structuredParser: 'kimi-json', structuredParseStage: 'repaired', structuredRepairs: ['normalized_structural_punctuation', 'removed_trailing_comma'], parseError: 'Unexpected character at position 321' }), createTime: now },
    ] }
  }

  return { loadAiSessions, renderAiSessions, resetAiSessionFilters, changeAiSessionPage, openDetail, closeDetail }
}
