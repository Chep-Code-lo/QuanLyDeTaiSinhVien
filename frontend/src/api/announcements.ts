import { apiClient } from './client'
import { getItFaculty } from '../config/facultyScope'
import type { ApiResponse, PageResponse } from '../types/api'
import type { AnnouncementResponse, AnnouncementStatus } from '../types/domain'

type AnnouncementPayload = {
  facultyId: number
  title: string
  content: string
  attachmentFileId?: number | null
}

export const announcementApi = {
  list: async (params: { facultyId?: number; page?: number; size?: number } = {}) =>
    (
      await apiClient.get<ApiResponse<PageResponse<AnnouncementResponse>>>('/announcements', {
        params: { ...params, facultyId: params.facultyId ?? (await getItFaculty()).id },
      })
    ).data.data,
  listForManagement: async (params: {
    facultyId: number
    status?: AnnouncementStatus
    page?: number
    size?: number
  }) =>
    (
      await apiClient.get<ApiResponse<PageResponse<AnnouncementResponse>>>(
        '/announcements/manage',
        { params },
      )
    ).data.data,
  get: async (id: number) =>
    (await apiClient.get<ApiResponse<AnnouncementResponse>>(`/announcements/${id}`)).data.data,
  create: async (payload: AnnouncementPayload) =>
    (await apiClient.post<ApiResponse<AnnouncementResponse>>('/announcements', payload)).data.data,
  update: async (id: number, payload: Omit<AnnouncementPayload, 'facultyId'>) =>
    (await apiClient.put<ApiResponse<AnnouncementResponse>>(`/announcements/${id}`, payload)).data
      .data,
  publish: async (id: number) =>
    (await apiClient.patch<ApiResponse<AnnouncementResponse>>(`/announcements/${id}/publish`)).data
      .data,
  archive: async (id: number) =>
    (await apiClient.patch<ApiResponse<AnnouncementResponse>>(`/announcements/${id}/archive`)).data
      .data,
  remove: async (id: number) => apiClient.delete(`/announcements/${id}`),
}