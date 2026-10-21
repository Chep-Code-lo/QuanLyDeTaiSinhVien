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
  EmailRecipientResponse,
  EmailQueuedResponse,
  SendEmailRequest,
  EmailTemplateResponse,
  SaveEmailTemplateRequest,
  NotificationScopeType,
} from '../types/domain'

const systemScope = { scopeType: 'SYSTEM' as const, scopeId: 0 }
type EmailScope = { scopeType: NotificationScopeType; scopeId: number }

export const emailNotificationsApi = {
  listTemplates: async (page = 0) => {
    const response = await apiClient.get<ApiResponse<PageResponse<EmailTemplateResponse>>>('/email-templates', { params: { page, size: 20 } })
    return response.data.data
  },
  createTemplate: async (payload: SaveEmailTemplateRequest) => {
    const response = await apiClient.post<ApiResponse<EmailTemplateResponse>>('/email-templates', payload)
    return response.data.data
  },
  updateTemplate: async (id: number, payload: SaveEmailTemplateRequest) => {
    const response = await apiClient.put<ApiResponse<EmailTemplateResponse>>(`/email-templates/${id}`, payload)
    return response.data.data
  },
  sendTemplate: async (id: number, payload: SendEmailRequest) => {
    const response = await apiClient.post<ApiResponse<EmailQueuedResponse>>(`/email-templates/${id}/send`, payload)
    return response.data.data
  },
  listRecipients: async ({ role, search, page = 0 }: { role?: 'STUDENT' | 'LECTURER'; search?: string; page?: number } = {}) => {
    const response = await apiClient.get<ApiResponse<PageResponse<EmailRecipientResponse>>>('/email-recipients', {
      params: { role, search, page, size: 20 },
    })
    return response.data.data
  },
  send: async (eventType: EmailNotificationEventType, payload: SendEmailRequest) => {
    const response = await apiClient.post<ApiResponse<EmailQueuedResponse>>(
      `/notification-settings/${eventType}/send`, payload,
    )
    return response.data.data
  },
  listSettings: async (scope: EmailScope = systemScope) => {
    const response = await apiClient.get<ApiResponse<EmailNotificationSettingResponse[]>>('/notification-settings', {
      params: scope,
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
