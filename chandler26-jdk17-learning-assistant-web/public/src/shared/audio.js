function scheduleTone(context, startTime, { frequency, duration, gain: peakGain, type }) {
  const oscillator = context.createOscillator()
  const gain = context.createGain()
  oscillator.type = type
  oscillator.frequency.setValueAtTime(frequency, startTime)
  gain.gain.setValueAtTime(Math.max(0.0001, peakGain), startTime)
  gain.gain.exponentialRampToValueAtTime(0.0001, startTime + duration)
  oscillator.connect(gain)
  gain.connect(context.destination)
  oscillator.start(startTime)
  oscillator.stop(startTime + duration)
}

export function playUiTone(type) {
  try {
    const AudioContextClass = window.AudioContext || window.webkitAudioContext
    if (!AudioContextClass) return
    const context = playUiTone.context || new AudioContextClass()
    playUiTone.context = context
    if (context.state === 'suspended') {
      void context.resume?.()
    }
    const now = context.currentTime
    if (type === 'success' || type === 'celebrate') {
      scheduleTone(context, now, { frequency: 523.25, duration: 0.11, gain: 0.04, type: 'triangle' })
      scheduleTone(context, now + 0.075, { frequency: 659.25, duration: 0.12, gain: 0.045, type: 'triangle' })
      scheduleTone(context, now + 0.15, { frequency: 783.99, duration: 0.2, gain: 0.05, type: 'triangle' })
      return
    }
    const config = {
      correct: { frequency: 520, duration: 0.045, gain: 0.025, type: 'sine' },
      wrong: { frequency: 150, duration: 0.12, gain: 0.05, type: 'square' },
    }[type] || { frequency: 360, duration: 0.08, gain: 0.03, type: 'sine' }
    scheduleTone(context, now, config)
  } catch {
    // 音效是交互增强，浏览器限制或静音时不影响正常流程
  }
}
