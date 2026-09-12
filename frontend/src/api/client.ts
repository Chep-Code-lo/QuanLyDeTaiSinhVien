import axios from 'axios'

const ACCESS_TOKEN_KEY = 'ql-de-tai.access-token'

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 15_000,
})

apiClient.interceptors.request.use((config) => {
  const token = window.localStorage.getItem(ACCESS_TOKEN_KEY)

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

export const tokenStorage = {
  clear: () => window.localStorage.removeItem(ACCESS_TOKEN_KEY),
  get: () => window.localStorage.getItem(ACCESS_TOKEN_KEY),
  set: (token: string) => window.localStorage.setItem(ACCESS_TOKEN_KEY, token),
}
