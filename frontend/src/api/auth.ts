import { apiClient } from './client'
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
    const response = await apiClient.get<ApiResponse<{ headerName: string; parameterName: string; token: string }>>('/auth/csrf')
    return response.data.data
  },
  login: async (payload: LoginPayload) => {
    // Spring Security requires the CSRF cookie/header pair even for session login.
    await authApi.getCsrf()
    const response = await apiClient.post<ApiResponse<LoginResponse>>('/auth/login', payload)
    return response.data.data
  },
  getCurrentUser: async () => {
    const response = await apiClient.get<ApiResponse<CurrentUser>>('/auth/me')
    return response.data.data
  },
  logout: async () => {
    const response = await apiClient.post<ApiResponse<void>>('/auth/logout')
    return response.data
  },
  changePassword: async (payload: { currentPassword: string; newPassword: string }) => {
    const response = await apiClient.post<ApiResponse<void>>('/auth/change-password', payload)
    return response.data
  },
}
