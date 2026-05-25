import { hideModal, showModal } from '/src/shared/modal.js'

export function createAccountProfileFeature(ctx) {
  const { state, elements, request, setLoading, toast, logEvent, updateAuthView } = ctx

  function openAccountModal() {
    elements.accountNicknameInput.value = state.user?.nickname || state.user?.username || ''
    elements.accountUsernameInput.value = state.user?.username || ''
    resetSecurityEditor()
    renderSecuritySummary()
    setAccountModalTab('accountBasicPanel')
    showModal(elements.accountModal)
  }

  function closeAccountModal() {
    resetSecurityEditor()
    hideModal(elements.accountModal)
  }

  async function saveAccountProfile() {
    const nickname = elements.accountNicknameInput.value.trim()
    if (!nickname) {
      toast('请输入昵称')
      return
    }
    await saveAccount({ nickname }, '资料已更新', '更新账户资料')
  }

  async function saveAccountSecurity() {
    const modes = []
    const payload = {}
    if (hasPasswordInput()) {
      const passwordPayload = readPasswordPayload()
      if (!passwordPayload) return
      Object.assign(payload, passwordPayload)
      modes.push('password')
    }
    const phone = elements.accountPhoneInput.value.trim()
    if (phone) {
      if (!/^[0-9+\-()\s]{3,32}$/.test(phone)) {
        toast('手机号码格式不正确')
        return
      }
      payload.phone = phone
      modes.push('phone')
    }
    const email = elements.accountEmailInput.value.trim()
    if (email) {
      if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
        toast('联系邮箱格式不正确')
        return
      }
      payload.email = email
      modes.push('email')
    }
    if (!modes.length) {
      toast('没有可保存的修改')
      return
    }
    await saveAccount(payload, '安全设置已更新', securityLogTitle(modes))
  }

  function hasPasswordInput() {
    return Boolean(
      elements.accountCurrentPasswordInput.value ||
        elements.accountNewPasswordInput.value ||
        elements.accountConfirmPasswordInput.value,
    )
  }

  function readPasswordPayload() {
    const currentPassword = elements.accountCurrentPasswordInput.value
    const newPassword = elements.accountNewPasswordInput.value
    const confirmPassword = elements.accountConfirmPasswordInput.value
    if (newPassword.length < 6) {
      toast('新密码至少 6 位')
      return null
    }
    if (newPassword !== confirmPassword) {
      toast('两次输入的新密码不一致')
      return null
    }
    if (!currentPassword) {
      toast('修改密码需要输入当前密码')
      return null
    }
    return { currentPassword, newPassword }
  }

  async function saveAccount(payload, successMessage, logTitle) {
    setLoading(true)
    try {
      if (state.preview) {
        state.user = { ...(state.user || {}), ...payload }
        if (payload.phone) {
          state.user.phoneMasked = maskPhonePreview(payload.phone)
          delete state.user.phone
        }
        if (payload.email) {
          state.user.emailMasked = maskEmailPreview(payload.email)
          delete state.user.email
        }
        delete state.user.currentPassword
        delete state.user.newPassword
        localStorage.setItem('learning.user', JSON.stringify(state.user))
        updateAuthView()
        renderSecuritySummary()
        resetSecurityEditor()
        closeAccountModal()
        toast(`设计预览：${successMessage}`)
        return
      }
      const user = await request('/api/v1/learning/auth/me', {
        method: 'PUT',
        body: JSON.stringify(payload),
      })
      state.user = user
      localStorage.setItem('learning.user', JSON.stringify(state.user))
      updateAuthView()
      renderSecuritySummary()
      resetSecurityEditor()
      closeAccountModal()
      logEvent('auth', logTitle, state.user?.username || '')
      toast(successMessage)
    } catch (error) {
      logEvent('error', '账户更新失败', error.message)
      toast(`账户更新失败：${error.message}`)
    } finally {
      setLoading(false)
    }
  }

  function setAccountModalTab(tabId) {
    const fallback = document.getElementById(tabId) ? tabId : 'accountBasicPanel'
    elements.accountModal.querySelectorAll('[data-account-tab]').forEach((button) => {
      button.classList.toggle('active', button.dataset.accountTab === fallback)
    })
    elements.accountModal.querySelectorAll('.account-tab-panel').forEach((panel) => {
      panel.classList.toggle('active', panel.id === fallback)
    })
  }

  function openAccountSecurityEditor(mode) {
    if (!securityModes().includes(mode)) return
    const openModes = new Set(openedSecurityModes())
    const willOpen = !openModes.has(mode)
    if (willOpen) {
      openModes.add(mode)
    } else {
      openModes.delete(mode)
      resetSecurityInputs(mode)
    }
    state.accountSecurityEditModes = [...openModes]
    syncSecurityEditors()
  }

  function cancelAccountSecurityEditor() {
    closeAccountModal()
  }

  function resetSecurityEditor() {
    state.accountSecurityEditModes = []
    resetSecurityInputs()
    syncSecurityEditors()
  }

  function resetSecurityInputs(mode = '') {
    if (!mode || mode === 'password') {
      elements.accountCurrentPasswordInput.value = ''
      elements.accountNewPasswordInput.value = ''
      elements.accountConfirmPasswordInput.value = ''
      updateAccountPasswordStrength()
    }
    if (!mode || mode === 'phone') elements.accountPhoneInput.value = ''
    if (!mode || mode === 'email') elements.accountEmailInput.value = ''
  }

  function renderSecuritySummary() {
    const phone = state.user?.phoneMasked || ''
    const email = state.user?.emailMasked || ''
    elements.accountPasswordValue.textContent = '未展示'
    elements.accountPhoneValue.textContent = phone || '未绑定'
    elements.accountEmailValue.textContent = email || '未绑定'
    elements.accountPhoneValue.classList.toggle('account-security-row-value-muted', !phone)
    elements.accountEmailValue.classList.toggle('account-security-row-value-muted', !email)
  }

  function syncSecurityEditors() {
    const openModes = openedSecurityModes()
    elements.accountModal.querySelectorAll('.account-security-editor-panel').forEach((panel) => {
      const open = openModes.includes(panel.dataset.securityPanel)
      panel.classList.toggle('hidden', !open)
      panel.classList.toggle('active', open)
    })
    elements.accountModal.querySelectorAll('.account-security-row').forEach((row) => {
      const active = openModes.includes(row.dataset.securityMode)
      row.classList.toggle('active', active)
      const button = row.querySelector('.icon-action-button')
      if (button) {
        button.classList.toggle('active', active)
        button.setAttribute('aria-pressed', String(active))
        button.textContent = active ? '−' : '✎'
      }
    })
  }

  function openedSecurityModes() {
    return Array.isArray(state.accountSecurityEditModes) ? state.accountSecurityEditModes.filter((mode) => securityModes().includes(mode)) : []
  }

  function securityModes() {
    return ['password', 'phone', 'email']
  }

  function securityLogTitle(modes) {
    const map = {
      password: '更新账户密码',
      phone: '更新手机号码',
      email: '更新联系邮箱',
    }
    return modes.map((mode) => map[mode]).filter(Boolean).join('、') || '更新安全设置'
  }

  function maskPhonePreview(phone) {
    const value = String(phone || '').trim()
    if (!value) return ''
    if (value.length <= 7) return '****'
    return `${value.slice(0, 3)}****${value.slice(-4)}`
  }

  function maskEmailPreview(email) {
    const value = String(email || '').trim()
    const atIndex = value.indexOf('@')
    if (atIndex <= 0) return '****'
    const name = value.slice(0, atIndex)
    const domain = value.slice(atIndex)
    if (name.length <= 2) return `${name.slice(0, 1)}****${domain}`
    return `${name.slice(0, 2)}****${domain}`
  }

  function updateAccountPasswordStrength() {
    const password = elements.accountNewPasswordInput?.value || ''
    const result = passwordStrength(password)
    elements.accountPasswordStrength.textContent = `密码强度：${result.label}`
    elements.accountPasswordStrength.dataset.level = result.level
  }

  function passwordStrength(password) {
    if (!password) return { label: '未输入', level: 'empty' }
    let score = 0
    if (password.length >= 6) score += 1
    if (password.length >= 10) score += 1
    if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score += 1
    if (/\d/.test(password)) score += 1
    if (/[^A-Za-z0-9]/.test(password)) score += 1
    if (score <= 1) return { label: '弱', level: 'weak' }
    if (score <= 3) return { label: '中', level: 'medium' }
    return { label: '强', level: 'strong' }
  }

  return {
    openAccountModal,
    closeAccountModal,
    setAccountModalTab,
    openAccountSecurityEditor,
    cancelAccountSecurityEditor,
    saveAccountProfile,
    saveAccountSecurity,
    updateAccountPasswordStrength,
  }
}
