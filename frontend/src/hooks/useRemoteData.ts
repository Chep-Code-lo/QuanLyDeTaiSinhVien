import { useEffect, useState } from 'react'
import { errorMessage } from '../api/student'

// Callers memoize the loader with every query parameter; revision explicitly invalidates the cached result.
export function useRemoteData<T>(load: () => Promise<T>, revision = 0) {
  const [response, setResponse] = useState<{
    load: typeof load
    revision: number
    data?: T
    error?: string
  } | null>(null)
  const current = response?.load === load && response.revision === revision ? response : null
  useEffect(() => {
    let active = true
    load()
      .then((data) => {
        if (active) setResponse({ load, revision, data })
      })
      .catch((error) => {
        if (active) setResponse({ load, revision, error: errorMessage(error) })
      })
    return () => {
      active = false
    }
  }, [load, revision])
  return { data: current?.data, error: current?.error ?? '', loading: !current }
}
