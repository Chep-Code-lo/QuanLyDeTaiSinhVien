import { apiClient } from './client'
import type { ApiResponse, PageResponse } from '../types/api'
import type { NotificationResponse } from '../types/domain'

export const notificationApi = {
  list: async (page = 0, size = 50) =>
    (
      await apiClient.get<ApiResponse<PageResponse<NotificationResponse>>>('/notifications', {
        params: { page, size },
      })
    ).data.data,
  unreadCount: async () =>
    (await apiClient.get<ApiResponse<{ unreadCount: number }>>('/notifications/unread-count')).data
      .data.unreadCount,
  markRead: async (id: number) =>
    (await apiClient.patch<ApiResponse<NotificationResponse>>(`/notifications/${id}/read`)).data
      .data,
}