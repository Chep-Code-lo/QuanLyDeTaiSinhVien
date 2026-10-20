import axios from 'axios'
import type { InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from '../types/api'

type CsrfResponse = { headerName: string; parameterName: string; token: string }

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

export const apiClient = axios.create({
  baseURL: apiBaseUrl,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15_000,
})

const csrfClient = axios.create({
  baseURL: apiBaseUrl,
  withCredentials: true,
  timeout: 15_000,
})

let csrfToken: CsrfResponse | null = null
let csrfTokenPromise: Promise<CsrfResponse> | null = null

function getCookie(name: string): string | null {
  const value = `; ${document.cookie}`
  const parts = value.split(`; ${name}=`)
  if (parts.length === 2) return parts.pop()?.split(';').shift() || null
  return null
}

function isMutatingRequest(method?: string): boolean {
  return ['post', 'put', 'patch', 'delete'].includes((method ?? '').toLowerCase())
}

function hasRequestHeader(config: InternalAxiosRequestConfig, headerName: string): boolean {
  if (typeof config.headers.has === 'function') {
    return config.headers.has(headerName)
  }

  return Object.keys(config.headers).some((name) => name.toLowerCase() === headerName.toLowerCase())
}

export async function fetchCsrfToken(forceRefresh = false): Promise<CsrfResponse> {
  if (csrfToken && !forceRefresh) {
    return csrfToken
  }

  if (!csrfTokenPromise || forceRefresh) {
    csrfTokenPromise = csrfClient
      .get<ApiResponse<CsrfResponse>>('/auth/csrf')
      .then((response) => {
        csrfToken = response.data.data
        return csrfToken
      })
      .finally(() => {
        csrfTokenPromise = null
      })
  }

  return csrfTokenPromise
}

export function clearCsrfToken(): void {
  csrfToken = null
  csrfTokenPromise = null
}

apiClient.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  if (isMutatingRequest(config.method) && !hasRequestHeader(config, 'X-XSRF-TOKEN')) {
    if (!csrfToken) {
      const tokenResponse = await fetchCsrfToken()
      config.headers[tokenResponse.headerName] = tokenResponse.token
      return config
    }

    const xsrfToken = getCookie('XSRF-TOKEN')
    if (xsrfToken) {
      config.headers['X-XSRF-TOKEN'] = xsrfToken
    } else {
      config.headers[csrfToken.headerName] = csrfToken.token
    }
  }

  return config
})

// The backend stores LocalDateTime values using Clock.systemUTC().
// Preserve that timezone for browser comparisons and datetime-local inputs.
function normalizeUtcDates(value: unknown): void {
  if (!value || typeof value !== 'object') return
  for (const [key, child] of Object.entries(value)) {
    if (typeof child === 'string' && key.endsWith('At') &&
        /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?$/.test(child)) {
      (value as Record<string, unknown>)[key] = `${child}Z`
    } else if (typeof child === 'object') normalizeUtcDates(child)
  }
}
apiClient.interceptors.response.use(
  (response) => { normalizeUtcDates(response.data); return response },
  (error) => {
    if (error.response?.status === 401) {
      // Clear local auth token if unauthenticated
      tokenStorage.clear()
      window.dispatchEvent(new Event('session-expired'))
      clearCsrfToken()
    }
    if (error.response?.status === 403) {
      clearCsrfToken()
    }
    return Promise.reject(error)
  }
)

export const tokenStorage = {
  // Kept as a compatibility no-op: authentication is session-cookie based, never JWT based.
  clear: () => undefined,
  get: () => null,
  set: (_token: string) => undefined,
}
