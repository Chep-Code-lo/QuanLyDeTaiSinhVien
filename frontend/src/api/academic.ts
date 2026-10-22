import { apiClient, fetchCsrfToken } from './client'
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
  createSemester: async (payload: {
    academicYear: string
    semesterNo: number
    startsOn: string
    endsOn: string
  }) => {
    const response = await apiClient.post<ApiResponse<SemesterResponse>>('/semesters', payload)
    return response.data.data
  },
  getRegistrationPeriods: async (facultyId?: number, page = 0, size = 20) => {
    const response = await apiClient.get<ApiResponse<PageResponse<RegistrationPeriodResponse>>>(
      '/registration-periods',
      {
        params: { facultyId, page, size },
      },
    )
    return response.data.data
  },
  createRegistrationPeriod: async (payload: CreateRegistrationPeriodRequest) => {
    // A dean can keep a session open for a long time. Refresh the CSRF token at
    // the point of submission so a stale browser token is not reported as a
    // misleading 403 permission error.
    const csrf = await fetchCsrfToken(true)
    const response = await apiClient.post<ApiResponse<RegistrationPeriodResponse>>(
      '/registration-periods',
      payload,
      { headers: { [csrf.headerName]: csrf.token } },
    )
    return response.data.data
  },
  updateRegistrationPeriod: async (id: number, payload: UpdateRegistrationPeriodRequest) => {
    const response = await apiClient.put<ApiResponse<RegistrationPeriodResponse>>(
      `/registration-periods/${id}`,
      payload,
    )
    return response.data.data
  },
  changeRegistrationPeriodStatus: async (
    id: number,
    payload: { status: string; version: number },
  ) => {
    const response = await apiClient.patch<ApiResponse<RegistrationPeriodResponse>>(
      `/registration-periods/${id}/status`,
      payload,
    )
    return response.data.data
  },
  updateFaculty: async (id: number, payload: { name: string; active: boolean }) =>
    (await apiClient.patch<ApiResponse<FacultyResponse>>(`/faculties/${id}`, payload)).data.data,
  updateDepartment: async (id: number, payload: { name: string; active: boolean }) =>
    (await apiClient.patch<ApiResponse<DepartmentResponse>>(`/departments/${id}`, payload)).data
      .data,
  getProject: async (id: number) =>
    (await apiClient.get<ApiResponse<ProjectAssignmentResponse>>(`/project-assignments/${id}`)).data
      .data,
  updateProjectStatus: async (
    id: number,
    payload: { version: number; status: 'COMPLETED' | 'CANCELLED' },
  ) =>
    (
      await apiClient.patch<ApiResponse<ProjectAssignmentResponse>>(
        `/project-assignments/${id}/status`,
        payload,
      )
    ).data.data,
  getProjectAssignments: async (periodId?: number, page = 0, size = 20) => {
    const response = await apiClient.get<ApiResponse<PageResponse<ProjectAssignmentResponse>>>(
      '/project-assignments',
      {
        params: { periodId, page, size },
      },
    )
    return response.data.data
  },
}
