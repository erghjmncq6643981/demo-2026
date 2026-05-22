export function resetModalScroll(modal) {
  if (!modal) return
  modal.scrollTop = 0
  modal.querySelectorAll('.review-modal, .config-modal, [data-modal-scroll]').forEach((item) => {
    item.scrollTop = 0
  })
}

export function showModal(modal) {
  if (!modal) return
  resetModalScroll(modal)
  modal.classList.remove('hidden')
  window.requestAnimationFrame(() => resetModalScroll(modal))
}

export function hideModal(modal) {
  modal?.classList.add('hidden')
}
