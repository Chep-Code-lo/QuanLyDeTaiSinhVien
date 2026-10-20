import { apiClient } from '../api/client'
import type { ApiResponse } from '../types/api'
import type { FacultyResponse } from '../types/domain'

export function findItFaculty(faculties: FacultyResponse[]): FacultyResponse {
  const normalize = (value: string) =>
    value
      .normalize('NFD')
      .replace(/\p{M}/gu, '')
      .replace(/[^a-z0-9]/gi, '')
      .toUpperCase()
  const faculty =
    faculties.find((item) => normalize(item.code) === 'CNTT') ??
    faculties.find((item) => normalize(item.name).replace(/^KHOA/, '') === 'CONGNGHETHONGTIN')
  if (!faculty)
    throw new Error('Chưa có Khoa Công nghệ thông tin trong danh mục. Vui lòng kiểm tra cấu hình.')
  return faculty
}

let facultyRequest: Promise<FacultyResponse> | undefined

export function getItFaculty(): Promise<FacultyResponse> {
  if (!facultyRequest) {
    facultyRequest = apiClient
      .get<ApiResponse<FacultyResponse[]>>('/faculties')
      .then((response) => findItFaculty(response.data.data))
      .catch((error) => {
        facultyRequest = undefined
        throw error
      })
  }
  return facultyRequest
}
