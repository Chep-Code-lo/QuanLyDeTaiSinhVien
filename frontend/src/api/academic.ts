import { apiClient } from './client'
import type { ApiResponse, PageResponse } from '../types/api'
import type {
  CreateRegistrationPeriodRequest,
  DepartmentResponse,
  FacultyResponse,
  ProjectAssignmentResponse,
  RegistrationPeriodResponse,
  SemesterResponse,
  UpdateRegistrationPeriodRequest,
} from '../types/domain'

export const academicApi = {
  getFaculties: async () => {
    const response = await apiClient.get<ApiResponse<FacultyResponse[]>>('/faculties')
    return response.data.data
  },
  createFaculty: async (payload: { code: string; name: string }) => {
    const response = await apiClient.post<ApiResponse<FacultyResponse>>('/faculties', payload)
    return response.data.data
  },
  getDepartments: async (facultyId?: number) => {
    const response = await apiClient.get<ApiResponse<DepartmentResponse[]>>('/departments', {
      params: { facultyId },
    })
    return response.data.data
  },
  createDepartment: async (payload: { facultyId: number; code: string; name: string }) => {
    const response = await apiClient.post<ApiResponse<DepartmentResponse>>('/departments', payload)
    return response.data.data
  },
  getSemesters: async () => {
    const response = await apiClient.get<ApiResponse<SemesterResponse[]>>('/semesters')
    return response.data.data
  },
  createSemester: async (payload: { code: string; name: string; academicYear: string }) => {
    const response = await apiClient.post<ApiResponse<SemesterResponse>>('/semesters', payload)
    return response.data.data
  },
  getRegistrationPeriods: async (facultyId?: number, page = 0, size = 20) => {
    const response = await apiClient.get<ApiResponse<PageResponse<RegistrationPeriodResponse>>>('/registration-periods', {
      params: { facultyId, page, size },
    })
    return response.data.data
  },
  createRegistrationPeriod: async (payload: CreateRegistrationPeriodRequest) => {
    const response = await apiClient.post<ApiResponse<RegistrationPeriodResponse>>('/registration-periods', payload)
    return response.data.data
  },
  updateRegistrationPeriod: async (id: number, payload: UpdateRegistrationPeriodRequest) => {
    const response = await apiClient.put<ApiResponse<RegistrationPeriodResponse>>(`/registration-periods/${id}`, payload)
    return response.data.data
  },
  changeRegistrationPeriodStatus: async (id: number, payload: { status: string; version: number }) => {
    const response = await apiClient.patch<ApiResponse<RegistrationPeriodResponse>>(`/registration-periods/${id}/status`, payload)
    return response.data.data
  },
  getProjectAssignments: async (periodId?: number, page = 0, size = 20) => {
    const response = await apiClient.get<ApiResponse<PageResponse<ProjectAssignmentResponse>>>('/project-assignments', {
      params: { periodId, page, size },
    })
    return response.data.data
  },
}
