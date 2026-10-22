import { useEffect, useState } from 'react'
import { useRequestLoading } from './useRequestLoading'
import { errorMessage } from '../api/student'
import type { PageResponse } from '../types/api'

export type CommunicationQuery = { page: number; size: number; search: string }

export function useCommunicationPage<T>(
  scope: string,
  enabled: boolean,
  fetchPage: (query: CommunicationQuery) => Promise<PageResponse<T>>,
) {
  const [query, setQuery] = useState<CommunicationQuery>({ page: 0, size: 10, search: '' })
  const [lastScope, setLastScope] = useState(scope)
  if (lastScope !== scope) {
    setLastScope(scope)
    setQuery((current) => ({ ...current, page: 0 }))
  }
  const [revision, setRevision] = useState(0)
  const [result, setResult] = useState<PageResponse<T> | null>(null)
  const [error, setError] = useState('')
  const key = JSON.stringify([scope, enabled, query, revision])
  const [loading, setLoading] = useRequestLoading(key, enabled)
  useEffect(() => {
    if (!enabled) return
    let active = true
    fetchPage(query)
      .then((value) => {
        if (!active) return
        if (value.totalPages > 0 && query.page >= value.totalPages) {
          setQuery((current) => ({ ...current, page: value.totalPages - 1 }))
          return
        }
        setResult(value)
        setError('')
      })
      .catch((failure) => {
        if (active) setError(errorMessage(failure))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [scope, enabled, query, revision, fetchPage, setLoading])
  return {
    query,
    loading,
    error,
    result,
    items: result?.content ?? [],
    search: (search: string) => setQuery((current) => ({ ...current, search, page: 0 })),
    resize: (size: number) => setQuery((current) => ({ ...current, size, page: 0 })),
    go: (page: number) => setQuery((current) => ({ ...current, page })),
    refresh: () => setRevision((current) => current + 1),
    update: (replace: (item: T) => T) =>
      setResult((current) =>
        current ? { ...current, content: current.content.map(replace) } : current,
      ),
  }
}
