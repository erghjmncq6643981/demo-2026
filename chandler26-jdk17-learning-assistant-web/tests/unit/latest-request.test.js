import { describe, expect, it, vi } from 'vitest'
import { createLatestRequest } from '../../public/src/shared/latest-request.js'

describe('createLatestRequest', () => {
  it('同一 key 的新请求会取消旧请求', () => {
    const signals = []
    const request = vi.fn((_path, options) => {
      signals.push(options.signal)
      return new Promise(() => {})
    })
    const latest = createLatestRequest(request)

    void latest.latest('plan', '/first')
    void latest.latest('plan', '/second')

    expect(signals[0].aborted).toBe(true)
    expect(signals[1].aborted).toBe(false)
  })

  it('不同 key 的请求可以并行', () => {
    const signals = []
    const latest = createLatestRequest((_path, options) => {
      signals.push(options.signal)
      return new Promise(() => {})
    })

    void latest.latest('plan', '/plan')
    void latest.latest('calendar', '/calendar')

    expect(signals.every((signal) => !signal.aborted)).toBe(true)
  })
})
