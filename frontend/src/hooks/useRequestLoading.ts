import { useCallback, useState } from 'react'

// A changed request key is pending immediately, without a second render from an effect.
export function useRequestLoading(key: string, enabled = true) {
  const [request, setRequest] = useState({ key, loading: true })
  if (request.key !== key) setRequest({ key, loading: true })
  const setLoading = useCallback((loading: boolean) => {
    setRequest(current => current.key === key ? { key, loading } : current)
  }, [key])
  return [enabled && (request.key !== key || request.loading), setLoading] as const
}
