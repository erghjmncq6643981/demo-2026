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
    const mode = state.accountSecurityEditMode
    if (!mode) {
      toast('请选择要修改的安全项')
      return
    }
    const payload = {}
    if (mode === 'password') {
      const passwordPayload = readPasswordPayload()
      if (!passwordPayload) return
      Object.assign(payload, passwordPayload)
    }
    if (mode === 'phone') {
      const phone = elements.accountPhoneInput.value.trim()
      if (!phone) {
        toast('请输入手机号码')
        return
      }
      if (!/^[0-9+\-()\s]{3,32}$/.test(phone)) {
        toast('手机号码格式不正确')
        return
      }
      payload.phone = phone
    }
    if (mode === 'email') {
      const email = elements.accountEmailInput.value.trim()
      if (!email) {
        toast('请输入联系邮箱')
        return
      }
      if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
        toast('联系邮箱格式不正确')
        return
      }
      payload.email = email
    }
    await saveAccount(payload, '安全设置已更新', securityLogTitle(mode))
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
    const meta = securityModeMeta(mode)
    if (!meta) return
    state.accountSecurityEditMode = mode
    elements.accountSecurityEditor.classList.remove('hidden')
    elements.accountSecurityEditorTitle.textContent = meta.title
    elements.accountSecurityEditorDescription.textContent = meta.description
    elements.accountModal.querySelectorAll('.account-security-editor-panel').forEach((panel) => {
      panel.classList.toggle('active', panel.dataset.securityPanel === mode)
    })
    elements.accountModal.querySelectorAll('.account-security-row').forEach((row) => {
      row.classList.toggle('active', row.dataset.securityMode === mode)
    })
    resetSecurityInputs(mode)
  }

  function cancelAccountSecurityEditor() {
    resetSecurityEditor()
  }

  function resetSecurityEditor() {
    state.accountSecurityEditMode = ''
    elements.accountSecurityEditor?.classList.add('hidden')
    elements.accountModal.querySelectorAll('.account-security-editor-panel').forEach((panel) => {
      panel.classList.remove('active')
    })
    elements.accountModal.querySelectorAll('.account-security-row').forEach((row) => {
      row.classList.remove('active')
    })
    resetSecurityInputs()
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

  function securityModeMeta(mode) {
    const map = {
      password: {
        title: '修改密码',
        description: '输入当前密码后设置新密码，保存后需要使用新密码登录。',
      },
      phone: {
        title: '修改手机号码',
        description: '请输入新的手机号码，保存后页面只展示后端返回的脱敏号码。',
      },
      email: {
        title: '修改联系邮箱',
        description: '请输入新的联系邮箱，保存后页面只展示后端返回的脱敏邮箱。',
      },
    }
    return map[mode] || null
  }

  function securityLogTitle(mode) {
    const map = {
      password: '更新账户密码',
      phone: '更新手机号码',
      email: '更新联系邮箱',
    }
    return map[mode] || '更新安全设置'
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
