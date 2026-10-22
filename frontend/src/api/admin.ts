import { apiClient } from './client'
import type { ApiResponse, PageResponse } from '../types/api'
import type {
  AppointmentResponse,
  CreateAppointmentRequest,
  CreateUserRequest,
  UpdateUserRequest,
  UserResponse,
  UserStatus,
  RoleCode,
  AuditLogResponse,
} from '../types/domain'

export const adminApi = {
  listUsers: async ({
    search,
    page = 0,
    size = 20,
  }: { search?: string; page?: number; size?: number } = {}) => {
    const response = await apiClient.get<ApiResponse<PageResponse<UserResponse>>>('/admin/users', {
      params: { search, page, size },
    })
    return response.data.data
  },
  getUser: async (id: number) =>
    (await apiClient.get<ApiResponse<UserResponse>>(`/admin/users/${id}`)).data.data,
  createUser: async (payload: CreateUserRequest) => {
    const response = await apiClient.post<ApiResponse<UserResponse>>('/admin/users', payload)
    return response.data.data
  },
  updateUser: async (id: number, payload: UpdateUserRequest) => {
    const response = await apiClient.put<ApiResponse<UserResponse>>(`/admin/users/${id}`, payload)
    return response.data.data
  },
  updateStatus: async (id: number, status: UserStatus) =>
    (await apiClient.patch<ApiResponse<UserResponse>>(`/admin/users/${id}/status`, { status })).data
      .data,
  updateRoles: async (id: number, roles: RoleCode[]) =>
    (await apiClient.put<ApiResponse<UserResponse>>(`/admin/users/${id}/roles`, { roles })).data
      .data,
  listAuditLogs: async (params: { entityType?: string; page: number; size: number }) =>
    (
      await apiClient.get<ApiResponse<PageResponse<AuditLogResponse>>>('/admin/audit-logs', {
        params,
      })
    ).data.data,
  deleteUser: async (id: number) => {
    await apiClient.delete(`/admin/users/${id}`)
  },
  getAppointments: async (lecturerId?: number) => {
    const response = await apiClient.get<ApiResponse<AppointmentResponse[]>>(
      '/admin/academic-appointments',
      {
        params: { lecturerId },
      },
    )
    return response.data.data
  },
  createAppointment: async (payload: CreateAppointmentRequest) => {
    const response = await apiClient.post<ApiResponse<AppointmentResponse>>(
      '/admin/academic-appointments',
      payload,
    )
    return response.data.data
  },
  endAppointment: async (id: number, endsAt: string) => {
    const response = await apiClient.patch<ApiResponse<AppointmentResponse>>(
      `/admin/academic-appointments/${id}/end`,
      {
        endsAt,
      },
    )
    return response.data.data
  },
}
