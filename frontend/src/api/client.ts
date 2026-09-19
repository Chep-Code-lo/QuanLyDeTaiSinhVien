import axios from 'axios'

const ACCESS_TOKEN_KEY = 'ql-de-tai.access-token'

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15_000,
})

function getCookie(name: string): string | null {
  const value = `; ${document.cookie}`
  const parts = value.split(`; ${name}=`)
  if (parts.length === 2) return parts.pop()?.split(';').shift() || null
  return null
}

apiClient.interceptors.request.use((config) => {
  const token = window.localStorage.getItem(ACCESS_TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  const xsrfToken = getCookie('XSRF-TOKEN')
  if (xsrfToken) {
    config.headers['X-XSRF-TOKEN'] = xsrfToken
  }

  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Clear local auth token if unauthenticated
      tokenStorage.clear()
    }
    return Promise.reject(error)
  }
)

export const tokenStorage = {
  clear: () => window.localStorage.removeItem(ACCESS_TOKEN_KEY),
  get: () => window.localStorage.getItem(ACCESS_TOKEN_KEY),
  set: (token: string) => window.localStorage.setItem(ACCESS_TOKEN_KEY, token),
}
