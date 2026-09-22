import { apiClient } from './client'
import type { ApiResponse, PageResponse } from '../types/api'
import type { StudentGroupResponse } from '../types/domain'

export const groupApi = {
  list: async (params: { periodId?: number; page?: number; size?: number } = {}) =>
    (await apiClient.get<ApiResponse<PageResponse<StudentGroupResponse>>>('/groups', { params })).data.data,
  mine: async () => (await apiClient.get<ApiResponse<StudentGroupResponse[]>>('/groups/mine')).data.data,
  create: async (payload: { periodId: number; name: string }) =>
    (await apiClient.post<ApiResponse<StudentGroupResponse>>('/groups', payload)).data.data,
  addMember: async (groupId: number, studentCode: string) =>
    (await apiClient.post<ApiResponse<StudentGroupResponse>>(`/groups/${groupId}/members`, { studentCode })).data.data,
  removeMember: async (groupId: number, studentId: number) =>
    (await apiClient.delete<ApiResponse<StudentGroupResponse>>(`/groups/${groupId}/members/${studentId}`)).data.data,
}
