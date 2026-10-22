import { apiClient, fetchCsrfToken } from './client'
import type { ApiResponse, PageResponse } from '../types/api'
import type { GroupInvitationResponse, StudentGroupResponse } from '../types/domain'

export const groupApi = {
  list: async (params: { periodId?: number; page?: number; size?: number } = {}) =>
    (await apiClient.get<ApiResponse<PageResponse<StudentGroupResponse>>>('/groups', { params }))
      .data.data,
  mine: async () =>
    (await apiClient.get<ApiResponse<StudentGroupResponse[]>>('/groups/mine')).data.data,
  create: async (payload: { periodId: number; name: string }) => {
    // Refresh the token immediately before creating a group. This avoids using a
    // stale token after the browser restores a long-lived student session.
    const csrf = await fetchCsrfToken(true)
    return (
      await apiClient.post<ApiResponse<StudentGroupResponse>>('/groups', payload, {
        headers: { [csrf.headerName]: csrf.token },
      })
    ).data.data
  },
  removeMember: async (groupId: number, studentId: number) =>
    (
      await apiClient.delete<ApiResponse<StudentGroupResponse>>(
        `/groups/${groupId}/members/${studentId}`,
      )
    ).data.data,
  invite: async (payload: { groupId: number; studentCode: string; expiresAt: string }) =>
    (
      await apiClient.post<ApiResponse<GroupInvitationResponse>>('/group-invitations', {
        ...payload,
        expiresAt: new Date(payload.expiresAt).toISOString(),
      })
    ).data.data,
  myInvitations: async (page = 0, size = 100, status?: string) =>
    (
      await apiClient.get<ApiResponse<PageResponse<GroupInvitationResponse>>>(
        '/group-invitations/mine',
        { params: { page, size, status } },
      )
    ).data.data,
  invitationsForGroup: async (groupId: number) =>
    (
      await apiClient.get<ApiResponse<GroupInvitationResponse[]>>(
        `/group-invitations/groups/${groupId}`,
      )
    ).data.data,
  acceptInvitation: async (id: number) =>
    (await apiClient.patch<ApiResponse<GroupInvitationResponse>>(`/group-invitations/${id}/accept`))
      .data.data,
  rejectInvitation: async (id: number) =>
    (await apiClient.patch<ApiResponse<GroupInvitationResponse>>(`/group-invitations/${id}/reject`))
      .data.data,
  cancelInvitation: async (id: number) =>
    (await apiClient.patch<ApiResponse<GroupInvitationResponse>>(`/group-invitations/${id}/cancel`))
      .data.data,
}
