import { apiClient } from './client'
import type { ApiResponse, PageResponse } from '../types/api'
import type {
  EmailLogResponse,
  EmailNotificationEventType,
  EmailNotificationSettingResponse,
  EmailPreviewResponse,
  EmailTestQueuedResponse,
  PreviewEmailNotificationSettingRequest,
  UpdateEmailNotificationSettingRequest,
} from '../types/domain'

const systemScope = { scopeType: 'SYSTEM' as const, scopeId: 0 }

export const emailNotificationsApi = {
  listSettings: async () => {
    const response = await apiClient.get<ApiResponse<EmailNotificationSettingResponse[]>>('/notification-settings', {
      params: systemScope,
    })
    return response.data.data
  },
  updateSetting: async (eventType: EmailNotificationEventType, payload: UpdateEmailNotificationSettingRequest) => {
    const response = await apiClient.put<ApiResponse<EmailNotificationSettingResponse>>(
      `/notification-settings/${eventType}`,
      payload,
    )
    return response.data.data
  },
  preview: async (eventType: EmailNotificationEventType, payload: PreviewEmailNotificationSettingRequest) => {
    const response = await apiClient.post<ApiResponse<EmailPreviewResponse>>(
      `/notification-settings/${eventType}/preview`,
      payload,
    )
    return response.data.data
  },
  sendTest: async (eventType: EmailNotificationEventType) => {
    const response = await apiClient.post<ApiResponse<EmailTestQueuedResponse>>(
      `/notification-settings/${eventType}/test`,
      systemScope,
    )
    return response.data.data
  },
  listLogs: async ({ page = 0, size = 10 }: { page?: number; size?: number } = {}) => {
    const response = await apiClient.get<ApiResponse<PageResponse<EmailLogResponse>>>('/email-logs', {
      params: { page, size },
    })
    return response.data.data
  },
}
