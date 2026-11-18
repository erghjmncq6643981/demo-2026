const previousFocusByModal = new WeakMap()

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  'summary',
  '[tabindex]:not([tabindex="-1"])',
].join(', ')

export function isElementVisible(el) {
  if (!el || el.nodeType !== 1) return false
  if (el.classList?.contains('hidden') || el.closest?.('.hidden')) return false
  if (typeof window !== 'undefined' && typeof window.getComputedStyle === 'function') {
    const style = window.getComputedStyle(el)
    if (style.display === 'none' || style.visibility === 'hidden') return false
  }
  return true
}

export function getFocusableElements(container) {
  if (!container || typeof container.querySelectorAll !== 'function') return []
  return Array.from(container.querySelectorAll(FOCUSABLE_SELECTOR)).filter((el) => {
    const tabIndex = el.getAttribute?.('tabindex')
    if (tabIndex === '-1') return false
    return isElementVisible(el)
  })
}

export function getModalDialogSurface(modal) {
  if (!modal) return null
  return modal.querySelector?.('.review-modal, .config-modal, [data-modal-surface]') || modal
}

export function resetModalScroll(modal) {
  if (!modal) return
  modal.scrollTop = 0
  modal.querySelectorAll('.review-modal, .config-modal, [data-modal-scroll]').forEach((item) => {
    item.scrollTop = 0
  })
}

export function focusElementSafely(target) {
  if (!target || typeof target.focus !== 'function') return false
  try {
    target.focus({ preventScroll: true })
    return true
  } catch {
    return false
  }
}

/**
 * 打开弹窗并管理焦点进入：优先聚焦带 autofocus 控件，否则将焦点置于弹窗容器上，
 * 既能让 Tab 按序进入弹窗首项，又避免直接抢占特定语义按钮（如“记住”）的默认回车热键。
 */
export function showModal(modal, options = {}) {
  if (!modal) return
  const wasHidden = modal.classList?.contains('hidden')
  if (wasHidden && typeof document !== 'undefined') {
    const active = document.activeElement
    if (active && active !== document.body && !modal.contains?.(active)) {
      previousFocusByModal.set(modal, active)
    }
  }
  resetModalScroll(modal)
  modal.classList.remove('hidden')
  modal.setAttribute?.('aria-hidden', 'false')

  if (options.preserveFocus) return
  const surface = getModalDialogSurface(modal)
  if (surface && !surface.hasAttribute?.('tabindex')) {
    surface.setAttribute?.('tabindex', '-1')
  }
  const explicitTarget =
    (typeof options.initialFocus === 'function' ? options.initialFocus() : options.initialFocus) ||
    modal.querySelector?.('[autofocus]') ||
    surface ||
    modal
  if (typeof document !== 'undefined' && !modal.contains?.(document.activeElement)) {
    focusElementSafely(explicitTarget)
  }
  if (typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function') {
    window.requestAnimationFrame(() => resetModalScroll(modal))
  }
}

/**
 * 关闭弹窗并将焦点恢复到打开前触发元素（若仍可见且当前无其他更高层元素持焦）。
 */
export function hideModal(modal, options = {}) {
  if (!modal) return
  const activeBeforeHide = typeof document !== 'undefined' ? document.activeElement : null
  const focusWasInside = Boolean(activeBeforeHide && modal.contains?.(activeBeforeHide))
  modal.classList.add('hidden')
  modal.setAttribute?.('aria-hidden', 'true')

  const previous = previousFocusByModal.get(modal)
  previousFocusByModal.delete(modal)
  if (options.restoreFocus === false) return
  if (focusWasInside || activeBeforeHide === document?.body || !activeBeforeHide) {
    if (
      previous &&
      typeof document !== 'undefined' &&
      (!document.contains || document.contains(previous)) &&
      isElementVisible(previous)
    ) {
      focusElementSafely(previous)
    }
  }
}

/**
 * 统一绑定模态弹窗的关闭与遮罩点击行为，消除各业务模块重复手写的样板代码。
 */
export function bindModalDismiss(modal, options = {}) {
  if (!modal) return () => {}
  const { closeButtons = [], onClose = null, closeOnBackdrop = true } = options

  const close = () => {
    hideModal(modal)
    if (typeof onClose === 'function') onClose()
  }

  const buttons = Array.isArray(closeButtons) ? closeButtons : [closeButtons]
  for (const btn of buttons) {
    btn?.addEventListener?.('click', close)
  }

  if (closeOnBackdrop) {
    modal?.addEventListener?.('click', (e) => {
      if (e.target === modal) close()
    })
  }

  return close
}
