export function createAccountProfileFeature(ctx) {
  const { state, elements, request, setLoading, toast, logEvent, updateAuthView } = ctx

  function openAccountModal() {
    elements.accountNicknameInput.value = state.user?.nickname || state.user?.username || ''
    elements.accountCurrentPasswordInput.value = ''
    elements.accountNewPasswordInput.value = ''
    elements.accountConfirmPasswordInput.value = ''
    elements.accountModal.classList.remove('hidden')
  }

  function closeAccountModal() {
    elements.accountModal?.classList.add('hidden')
  }

  async function saveAccountProfile() {
    const nickname = elements.accountNicknameInput.value.trim()
    const currentPassword = elements.accountCurrentPasswordInput.value
    const newPassword = elements.accountNewPasswordInput.value
    const confirmPassword = elements.accountConfirmPasswordInput.value
    if (!nickname) {
      toast('请输入昵称')
      return
    }
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
    setLoading(true)
    try {
      const payload = { nickname }
      if (newPassword) {
        payload.currentPassword = currentPassword
        payload.newPassword = newPassword
      }
      if (state.preview) {
        state.user = { ...(state.user || {}), nickname }
        localStorage.setItem('learning.user', JSON.stringify(state.user))
        updateAuthView()
        closeAccountModal()
        toast('设计预览：账户信息已更新')
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
      logEvent('auth', '更新账户信息', nickname)
      toast('账户信息已更新')
    } catch (error) {
      logEvent('error', '账户更新失败', error.message)
      toast(`账户更新失败：${error.message}`)
    } finally {
      setLoading(false)
    }
  }


  return {
    openAccountModal,
    closeAccountModal,
    saveAccountProfile,
  }
}
