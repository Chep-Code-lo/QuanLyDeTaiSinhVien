import { apiClient } from './client'
import type { ApiResponse } from '../types/api'
import type { AppointmentResponse, CreateAppointmentRequest } from '../types/domain'

export const adminApi = {
  getAppointments: async (lecturerId?: number) => {
    const response = await apiClient.get<ApiResponse<AppointmentResponse[]>>('/admin/academic-appointments', {
      params: { lecturerId },
    })
    return response.data.data
  },
  createAppointment: async (payload: CreateAppointmentRequest) => {
    const response = await apiClient.post<ApiResponse<AppointmentResponse>>('/admin/academic-appointments', payload)
    return response.data.data
  },
  endAppointment: async (id: number, endsAt: string) => {
    const response = await apiClient.patch<ApiResponse<AppointmentResponse>>(`/admin/academic-appointments/${id}/end`, {
      endsAt,
    })
    return response.data.data
  },
}
