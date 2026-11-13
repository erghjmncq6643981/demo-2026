const CONFETTI_COLORS = ['#818cf8', '#60a5fa', '#7dd3a8', '#facc6b', '#fb7185']

export function prefersReducedMotion() {
  return Boolean(
    typeof window !== 'undefined' &&
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches,
  )
}

/**
 * 在全局彩带浮层播放全屏喝彩动效；当系统开启“减少动态效果”时自动退化为静态无粒子通知。
 */
export function showCelebrationEffect(
  layerEl = typeof document !== 'undefined' ? document.getElementById('celebrationLayer') : null,
) {
  if (!layerEl || !layerEl.classList) return
  if (prefersReducedMotion()) {
    layerEl.classList.remove('show')
    layerEl.innerHTML = ''
    return
  }
  layerEl.innerHTML = Array.from({ length: 34 }, (_, index) => {
    const left = Math.round(Math.random() * 100)
    const delay = Math.round(Math.random() * 260)
    const color = CONFETTI_COLORS[index % CONFETTI_COLORS.length]
    return `<span style="left:${left}%; animation-delay:${delay}ms; background:${color}"></span>`
  }).join('')
  layerEl.classList.remove('show')
  void layerEl.offsetWidth
  layerEl.classList.add('show')
  window.clearTimeout(showCelebrationEffect.timer)
  showCelebrationEffect.timer = window.setTimeout(() => {
    layerEl.classList.remove('show')
    layerEl.innerHTML = ''
  }, 1500)
}
