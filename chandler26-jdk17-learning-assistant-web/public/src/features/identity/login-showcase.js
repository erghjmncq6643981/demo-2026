// 登录展示独立于业务数据，无需登录或请求学习接口。
const root = document.querySelector('.login-showcase')
if (root) {
  const slides = [...root.querySelectorAll('[data-showcase-slide]')]
  const dots = [...root.querySelectorAll('[data-showcase-index]')]
  const prevBtn = root.querySelector('[data-showcase-prev]')
  const nextBtn = root.querySelector('[data-showcase-next]')
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
  const INTERVAL_MS = 2000
  const READ_PAUSE_MS = 6000
  let index = 0
  let paused = reducedMotion.matches
  let hovered = false
  let timer = null

  function stop() {
    clearTimeout(timer)
    timer = null
  }

  function schedule(delay = INTERVAL_MS) {
    stop()
    if (paused || hovered || document.hidden || root.contains(document.activeElement)
      || !root.getClientRects().length) return
    timer = setTimeout(() => show(index + 1), delay)
  }

  function show(next) {
    index = (next + slides.length) % slides.length
    slides.forEach((slide, position) => { slide.hidden = position !== index })
    dots.forEach((dot, position) => {
      if (position === index) dot.setAttribute('aria-current', 'true')
      else dot.removeAttribute('aria-current')
    })
    schedule()
  }

  dots.forEach((dot, position) => dot.addEventListener('click', (event) => {
    event.stopPropagation()
    show(position)
  }))
  if (prevBtn) {
    prevBtn.addEventListener('click', (event) => {
      event.stopPropagation()
      show(index - 1)
    })
  }
  if (nextBtn) {
    nextBtn.addEventListener('click', (event) => {
      event.stopPropagation()
      show(index + 1)
    })
  }
  // 点击内容区域给予 6 秒停留阅读时间，随后自动恢复轮播，不再永久锁定
  root.addEventListener('click', () => { schedule(READ_PAUSE_MS) })
  root.addEventListener('mouseenter', () => { hovered = true; stop() })
  root.addEventListener('mouseleave', () => { hovered = false; schedule() })
  root.addEventListener('focusin', stop)
  root.addEventListener('focusout', () => queueMicrotask(schedule))
  root.addEventListener('keydown', (event) => {
    if (!['ArrowLeft', 'ArrowRight'].includes(event.key)) return
    event.preventDefault()
    show(index + (event.key === 'ArrowRight' ? 1 : -1))
  })
  document.addEventListener('visibilitychange', schedule)
  reducedMotion.addEventListener('change', () => {
    paused = reducedMotion.matches
    if (!paused) schedule()
    else stop()
  })
  new MutationObserver(schedule).observe(document.getElementById('loginScreen'), {
    attributes: true, attributeFilter: ['class', 'hidden'],
  })
  window.addEventListener('pagehide', stop)
  window.addEventListener('pageshow', schedule)
  schedule()
}
