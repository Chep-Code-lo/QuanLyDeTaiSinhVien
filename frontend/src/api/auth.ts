import { apiClient } from './client'
import type { ApiResponse } from '../types/api'

export type LoginPayload = {
  username: string
  password: string
}

export type CurrentUser = {
  id: string
  fullName: string
  username: string
  roles: string[]
}

export type LoginResponse = {
  accessToken: string
  user: CurrentUser
}

/**
 * Contract for the future Spring Boot authentication endpoints.
 * The UI intentionally does not call these functions until authentication is implemented.
 */
export const authApi = {
  getCurrentUser: async () => {
    const response = await apiClient.get<ApiResponse<CurrentUser>>('/auth/me')
    return response.data.data
  },
  login: async (payload: LoginPayload) => {
    const response = await apiClient.post<ApiResponse<LoginResponse>>('/auth/login', payload)
    return response.data.data
  },
}
