import { hideModal, showModal } from '/src/shared/modal.js'
import { escapeHtml, formatDateTime } from '/src/shared/text.js'
import { createAiSessionAdminFeature } from '/src/features/system/ai-sessions.js'

const ROLE_LABELS = { USER: '普通用户', ADMIN: '系统管理员' }

export function createSystemManagementFeature(ctx) {
  const { state, elements, request, setLoading, toast, logEvent, confirmDelete } = ctx
  const aiSessions = createAiSessionAdminFeature(ctx)

  function isAdmin() {
    return state.preview || state.user?.roleCode === 'ADMIN'
  }

  function mountPanels() {
    if (!elements.systemManagedPanels) return
    for (const id of ['aiTaskPanel', 'modelManagePanel', 'agentManagePanel', 'systemLogPanel']) {
      const panel = document.getElementById(id)
      if (panel && panel.parentElement !== elements.systemManagedPanels) {
        panel.classList.add('system-section')
        elements.systemManagedPanels.appendChild(panel)
      }
    }
    const importList = elements.sceneImportList
    renderSystemTab(state.activeSystemTab)
  }

  function renderAdminEntry() {
    document.querySelectorAll('.admin-only').forEach((item) => item.classList.toggle('hidden', !isAdmin()))
    if (!isAdmin() && state.activeView === 'systemAdminView') {
      state.activeView = 'profileView'
    }
  }

  function renderSystemTab(tabId = state.activeSystemTab) {
    const fallback = document.getElementById(tabId) ? tabId : 'adminUserPanel'
    state.activeSystemTab = fallback
    localStorage.setItem('learning.systemTab', fallback)
    document.querySelectorAll('[data-system-tab]').forEach((button) => {
      button.classList.toggle('active', button.dataset.systemTab === fallback)
    })
    elements.systemAdminView?.querySelectorAll('.system-section').forEach((section) => {
      section.classList.toggle('active', section.id === fallback)
    })
    if (fallback === 'adminUserPanel') loadUsers()
    if (fallback === 'modelManagePanel') aiSessions.loadAiSessions()
    if (fallback === 'aiTaskPanel') ctx.loadAiTasks?.({ all: true })
  }

  async function loadUsers() {
    if (!isAdmin() || !elements.adminUserRows) return
    if (state.preview) {
      state.adminUsers = state.adminUsers.length ? state.adminUsers : previewUsers()
      renderUsers({ total: state.adminUsers.length, page: 1, pageSize: 20, items: state.adminUsers })
      return
    }
    const params = new URLSearchParams({
      page: String(state.adminUserPage || 1),
      pageSize: String(state.adminUserPageSize || 20),
    })
    const keyword = elements.adminUserKeywordInput?.value.trim()
    const roleCode = elements.adminUserRoleFilter?.value
    const enabled = elements.adminUserEnabledFilter?.value
    if (keyword) params.set('keyword', keyword)
    if (roleCode) params.set('roleCode', roleCode)
    if (enabled) params.set('enabled', enabled)
    try {
      const result = await request(`/api/v1/admin/users?${params}`)
      state.adminUsers = Array.isArray(result?.items) ? result.items : []
      renderUsers(result || { total: 0, page: state.adminUserPage, pageSize: state.adminUserPageSize, items: [] })
    } catch (error) {
      logEvent('error', '用户中心加载失败', error.message)
      toast(`用户中心加载失败：${error.message}`)
    }
  }

  function renderUsers(result) {
    const items = Array.isArray(result?.items) ? result.items : []
    const total = Number(result?.total || items.length)
    const page = Number(result?.page || state.adminUserPage || 1)
    const pageSize = Number(result?.pageSize || state.adminUserPageSize || 20)
    state.adminUserPage = page
    state.adminUserPageSize = pageSize
    if (elements.adminUserSummary) elements.adminUserSummary.textContent = `${total} 位用户`
    if (elements.adminUserPageInfo) elements.adminUserPageInfo.textContent = `第 ${page} 页 · 共 ${total} 位`
    if (!items.length) {
      elements.adminUserRows.innerHTML = '<tr><td colspan="8" class="empty">暂无符合条件的用户</td></tr>'
      return
    }
    elements.adminUserRows.innerHTML = items.map((item) => `
      <tr>
        <td><strong>${escapeHtml(item.nickname || item.username)}</strong><small class="table-subline">@${escapeHtml(item.username || '')}</small></td>
        <td><span class="mini-pill ${item.roleCode === 'ADMIN' ? 'ok' : ''}">${escapeHtml(item.roleLabel || ROLE_LABELS[item.roleCode] || item.roleCode || '普通用户')}</span></td>
        <td><span class="task-status ${item.enabled ? 'task-status-completed' : 'task-status-cancelled'}">${item.enabled ? '正常' : '停用'}</span></td>
        <td>${Number(item.learningPlanCount || 0)}</td>
        <td>${Number(item.wordbookCount || 0)}</td>
        <td>${escapeHtml(formatDateTime(item.lastLoginTime) || '未登录')}</td>
        <td>${escapeHtml(formatDateTime(item.createTime) || '-')}</td>
        <td><div class="row-actions"><button class="icon-action-button" type="button" data-admin-edit="${escapeHtml(item.id)}" title="修改用户" aria-label="修改用户">✎</button><button class="icon-action-button" type="button" data-admin-reset="${escapeHtml(item.id)}" title="重置密码" aria-label="重置密码">↻</button><button class="danger-icon-button" type="button" data-admin-delete="${escapeHtml(item.id)}" title="注销用户" aria-label="注销用户">×</button></div></td>
      </tr>
    `).join('')
    elements.adminUserRows.querySelectorAll('[data-admin-edit]').forEach((button) => button.addEventListener('click', () => openUserModal(button.dataset.adminEdit)))
    elements.adminUserRows.querySelectorAll('[data-admin-reset]').forEach((button) => button.addEventListener('click', () => resetPassword(button.dataset.adminReset)))
    elements.adminUserRows.querySelectorAll('[data-admin-delete]').forEach((button) => button.addEventListener('click', () => deleteUser(button.dataset.adminDelete)))
  }

  function openUserModal(id = null) {
    state.currentAdminUserEditId = id ? String(id) : null
    const user = state.adminUsers.find((item) => String(item.id) === String(id))
    elements.adminUserModalTitle.textContent = user ? '修改用户' : '新增用户'
    elements.adminUserUsernameInput.value = user?.username || ''
    elements.adminUserUsernameInput.disabled = Boolean(user)
    elements.adminUserNicknameInput.value = user?.nickname || ''
    elements.adminUserPasswordInput.value = ''
    elements.adminUserPasswordField.classList.toggle('hidden', Boolean(user))
    elements.adminUserRoleInput.value = user?.roleCode || 'USER'
    elements.adminUserEnabledInput.value = String(user?.enabled !== false)
    showModal(elements.adminUserModal)
  }

  function closeUserModal() {
    hideModal(elements.adminUserModal)
  }

  async function saveUser() {
    const id = state.currentAdminUserEditId
    const payload = {
      nickname: elements.adminUserNicknameInput.value.trim(),
      roleCode: elements.adminUserRoleInput.value,
      enabled: elements.adminUserEnabledInput.value === 'true',
    }
    if (!id) {
      payload.username = elements.adminUserUsernameInput.value.trim()
      payload.password = elements.adminUserPasswordInput.value
      if (!payload.username || payload.password.length < 6) {
        toast('请输入用户名和至少 6 位初始密码')
        return
      }
    }
    setLoading(true)
    try {
      if (state.preview) {
        const next = { id: id || `preview-${Date.now()}`, username: payload.username || elements.adminUserUsernameInput.value, nickname: payload.nickname, roleCode: payload.roleCode, roleLabel: ROLE_LABELS[payload.roleCode], enabled: payload.enabled, learningPlanCount: 0, wordbookCount: 1, createTime: new Date().toISOString() }
        state.adminUsers = id ? state.adminUsers.map((item) => String(item.id) === String(id) ? { ...item, ...next } : item) : [next, ...state.adminUsers]
      } else {
        await request(id ? `/api/v1/admin/users/${encodeURIComponent(id)}` : '/api/v1/admin/users', { method: id ? 'PUT' : 'POST', body: JSON.stringify(payload) })
      }
      closeUserModal()
      toast(id ? '用户已更新' : '用户已创建')
      await loadUsers()
    } catch (error) {
      toast(`保存用户失败：${error.message}`)
    } finally {
      setLoading(false)
    }
  }

  async function resetPassword(id) {
    const user = state.adminUsers.find((item) => String(item.id) === String(id))
    const password = window.prompt(`为「${user?.nickname || user?.username || '该用户'}」设置新密码（至少 6 位）`)
    if (password === null) return
    if (password.length < 6) { toast('新密码至少 6 位'); return }
    try {
      if (!state.preview) await request(`/api/v1/admin/users/${encodeURIComponent(id)}/reset-password`, { method: 'POST', body: JSON.stringify({ password }) })
      toast('密码已重置')
    } catch (error) { toast(`重置密码失败：${error.message}`) }
  }

  async function deleteUser(id) {
    const user = state.adminUsers.find((item) => String(item.id) === String(id))
    const confirmed = await confirmDelete({ title: '注销用户', message: `确认注销「${user?.nickname || user?.username || '该用户'}」？账户将无法登录，但学习历史和审计记录会保留。` })
    if (!confirmed) return
    try {
      if (state.preview) state.adminUsers = state.adminUsers.filter((item) => String(item.id) !== String(id))
      else await request(`/api/v1/admin/users/${encodeURIComponent(id)}`, { method: 'DELETE' })
      toast('用户已注销')
      await loadUsers()
    } catch (error) { toast(`注销用户失败：${error.message}`) }
  }

  function changePage(offset) {
    if (offset < 0 && state.adminUserPage <= 1) return
    state.adminUserPage = Math.max(1, state.adminUserPage + offset)
    loadUsers()
  }

  function previewUsers() {
    return [
      { id: 9001, username: 'admin', nickname: '系统管理员', roleCode: 'ADMIN', roleLabel: '系统管理员', enabled: true, learningPlanCount: 0, wordbookCount: 1, lastLoginTime: new Date().toISOString(), createTime: new Date(Date.now() - 86400000 * 20).toISOString() },
      { id: 9002, username: 'chandler', nickname: 'Chandler', roleCode: 'USER', roleLabel: '普通用户', enabled: true, learningPlanCount: 2, wordbookCount: 3, lastLoginTime: new Date(Date.now() - 3600000).toISOString(), createTime: new Date(Date.now() - 86400000 * 8).toISOString() },
    ]
  }

  return { isAdmin, mountPanels, renderAdminEntry, renderSystemTab, loadUsers, renderUsers, openUserModal, closeUserModal, saveUser, changePage, ...aiSessions }
}
