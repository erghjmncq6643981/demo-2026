import { hideModal, showModal } from '/src/shared/modal.js'

export function createAccountProfileFeature(ctx) {
  const { state, elements, request, setLoading, toast, logEvent, updateAuthView } = ctx

  function openAccountModal() {
    elements.accountNicknameInput.value = state.user?.nickname || state.user?.username || ''
    elements.accountUsernameInput.value = state.user?.username || ''
    elements.accountPhoneInput.value = state.user?.phone || ''
    elements.accountEmailInput.value = state.user?.email || ''
    elements.accountCurrentPasswordInput.value = ''
    elements.accountNewPasswordInput.value = ''
    elements.accountConfirmPasswordInput.value = ''
    updateAccountPasswordStrength()
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
    const phone = elements.accountPhoneInput.value.trim()
    const email = elements.accountEmailInput.value.trim()
    const currentPassword = elements.accountCurrentPasswordInput.value
    const newPassword = elements.accountNewPasswordInput.value
    const confirmPassword = elements.accountConfirmPasswordInput.value
    if (newPassword || confirmPassword || currentPassword) {
      if (newPassword.length < 6) {
        toast('新密码至少 6 位')
        return
      }
      if (newPassword !== confirmPassword) {
        toast('两次输入的新密码不一致')
        return
      }
      if (!currentPassword) {
        toast('修改密码需要输入当前密码')
        return
      }
    }
    if (email && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
      toast('联系邮箱格式不正确')
      return
    }
    if (phone && !/^[0-9+\-()\s]{3,32}$/.test(phone)) {
      toast('手机号码格式不正确')
      return
    }
    const payload = { phone, email }
    if (newPassword) {
      payload.currentPassword = currentPassword
      payload.newPassword = newPassword
    }
    await saveAccount(payload, '安全设置已更新', '更新安全设置')
  }

  async function saveAccount(payload, successMessage, logTitle) {
    setLoading(true)
    try {
      if (state.preview) {
        state.user = { ...(state.user || {}), ...payload }
        delete state.user.currentPassword
        delete state.user.newPassword
        localStorage.setItem('learning.user', JSON.stringify(state.user))
        updateAuthView()
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
    saveAccountProfile,
    saveAccountSecurity,
    updateAccountPasswordStrength,
  }
}
