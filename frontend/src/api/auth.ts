import { apiClient, clearCsrfToken, fetchCsrfToken } from './client'
import type { ApiResponse } from '../types/api'
import type { CurrentUser } from '../types/domain'

export type LoginPayload = {
  username: string
  password: string
}

export type LoginResponse = {
  user: CurrentUser
}

export const authApi = {
  getCsrf: async () => {
    return fetchCsrfToken(true)
  },
  login: async (payload: LoginPayload) => {
    const csrf = await fetchCsrfToken(true)
    const response = await apiClient.post<ApiResponse<LoginResponse>>('/auth/login', payload, {
      headers: {
        [csrf.headerName]: csrf.token,
      },
    })
    return response.data.data
  },
  getCurrentUser: async () => {
    const response = await apiClient.get<ApiResponse<CurrentUser>>('/auth/me')
    return response.data.data
  },
  logout: async () => {
    try {
      const response = await apiClient.post<ApiResponse<void>>('/auth/logout')
      return response.data
    } finally {
      clearCsrfToken()
    }
  },
  changePassword: async (payload: { currentPassword: string; newPassword: string }) => {
    const response = await apiClient.post<ApiResponse<void>>('/auth/change-password', payload)
    return response.data
  },
}
