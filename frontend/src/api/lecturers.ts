import { apiClient } from './client'
import { getItFaculty } from '../config/facultyScope'
import type { ApiResponse, PageResponse } from '../types/api'
import type { LecturerDirectoryResponse } from '../types/domain'

export interface ListLecturersParams {
  search?: string
  facultyId?: number
  departmentId?: number
  page?: number
  size?: number
}

export const lecturerApi = {
  list: async (params: ListLecturersParams = {}) => {
    const response = await apiClient.get<ApiResponse<PageResponse<LecturerDirectoryResponse>>>(
      '/lecturers',
      {
        params: { ...params, facultyId: params.facultyId ?? (await getItFaculty()).id },
      },
    )
    return response.data.data
  },
}
