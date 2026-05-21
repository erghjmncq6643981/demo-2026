export function createFeatureFacade(resolveFeature) {
  return new Proxy(
    {},
    {
      get(_target, property) {
        if (typeof property !== 'string') return undefined
        return (...args) => {
          const feature = resolveFeature()
          const handler = feature?.[property]
          if (typeof handler !== 'function') {
            throw new Error(`Feature method is not available: ${property}`)
          }
          return handler(...args)
        }
      },
    },
  )
}
