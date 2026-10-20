import { apiClient } from './client'
import { getItFaculty } from '../config/facultyScope'
import type { ApiResponse } from '../types/api'
import type {
  AppointmentResponse,
  CreateAppointmentRequest,
  CreateUserRequest,
  UserResponse,
} from '../types/domain'

export const adminApi = {
  createUser: async (payload: CreateUserRequest) => {
    const response = await apiClient.post<ApiResponse<UserResponse>>('/admin/users', payload)
    return response.data.data
  },
  getAppointments: async (lecturerId?: number) => {
    const facultyId = (await getItFaculty()).id
    const response = await apiClient.get<ApiResponse<AppointmentResponse[]>>(
      '/admin/academic-appointments',
      {
        params: { lecturerId },
      },
    )
    return response.data.data.filter((appointment) => appointment.facultyId === facultyId)
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
