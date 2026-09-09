// 登录展示独立于业务数据，无需登录或请求学习接口。
const root = document.querySelector('.login-showcase')
if (root) {
  const slides = [...root.querySelectorAll('[data-showcase-slide]')]
  const dots = [...root.querySelectorAll('[data-showcase-index]')]
  const pauseButton = root.querySelector('[data-showcase-pause]')
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
  let index = 0
  let paused = reducedMotion.matches
  let hovered = false
  let timer = null

  function stop() {
    clearTimeout(timer)
    timer = null
  }

  function schedule() {
    stop()
    if (paused || hovered || document.hidden || root.contains(document.activeElement)
      || !root.getClientRects().length) return
    timer = setTimeout(() => show(index + 1), 6000)
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

  function updatePause() {
    pauseButton.textContent = paused ? '播放' : '暂停'
    pauseButton.setAttribute('aria-label', paused ? '播放自动轮播' : '暂停自动轮播')
    schedule()
  }

  dots.forEach((dot, position) => dot.addEventListener('click', () => show(position)))
  root.querySelector('[data-showcase-prev]').addEventListener('click', () => show(index - 1))
  root.querySelector('[data-showcase-next]').addEventListener('click', () => show(index + 1))
  pauseButton.addEventListener('click', () => { paused = !paused; updatePause() })
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
  reducedMotion.addEventListener('change', () => { paused = reducedMotion.matches; updatePause() })
  new MutationObserver(schedule).observe(document.getElementById('loginScreen'), {
    attributes: true, attributeFilter: ['class', 'hidden'],
  })
  window.addEventListener('pagehide', stop)
  window.addEventListener('pageshow', schedule)
  updatePause()
}
