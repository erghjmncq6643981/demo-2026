export function playUiTone(type) {
  try {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext
    if (!AudioContextClass) return
    const context = playUiTone.context || new AudioContextClass()
    playUiTone.context = context
    const oscillator = context.createOscillator()
    const gain = context.createGain()
    const now = context.currentTime
    const config = {
      correct: { frequency: 520, duration: 0.045, gain: 0.025, type: 'sine' },
      wrong: { frequency: 150, duration: 0.12, gain: 0.05, type: 'square' },
      success: { frequency: 720, duration: 0.16, gain: 0.045, type: 'triangle' },
    }[type] || { frequency: 360, duration: 0.08, gain: 0.03, type: 'sine' }
    oscillator.type = config.type
    oscillator.frequency.setValueAtTime(config.frequency, now)
    gain.gain.setValueAtTime(config.gain, now)
    gain.gain.exponentialRampToValueAtTime(0.0001, now + config.duration)
    oscillator.connect(gain)
    gain.connect(context.destination)
    oscillator.start(now)
    oscillator.stop(now + config.duration)
  } catch {
    // 音效是交互增强，浏览器限制或静音时不影响正常流程
  }
}
