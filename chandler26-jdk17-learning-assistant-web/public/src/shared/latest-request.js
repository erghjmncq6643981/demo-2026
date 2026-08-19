/**
 * 为可重复加载的界面请求提供“最后一次请求生效”语义。
 * 新请求开始时会取消同一 key 的旧请求，避免旧响应覆盖用户刚切换后的状态。
 */
export function createLatestRequest(request) {
  const controllers = new Map()

  async function latest(key, path, options = {}) {
    controllers.get(key)?.abort()
    const controller = new AbortController()
    controllers.set(key, controller)
    try {
      return await request(path, { ...options, signal: controller.signal })
    } finally {
      if (controllers.get(key) === controller) controllers.delete(key)
    }
  }

  function cancel(key) {
    controllers.get(key)?.abort()
    controllers.delete(key)
  }

  function cancelAll() {
    controllers.forEach((controller) => controller.abort())
    controllers.clear()
  }

  return { latest, cancel, cancelAll }
}

export function isRequestAbort(error) {
  return error?.name === 'AbortError'
}
