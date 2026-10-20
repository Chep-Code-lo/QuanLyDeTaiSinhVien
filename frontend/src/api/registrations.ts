import { apiClient } from './client'
import type { ApiResponse, PageResponse } from '../types/api'
import type { TopicRegistrationResponse } from '../types/domain'

export const registrationApi = {
  list: async (params: { periodId?: number; status?: string; page?: number; size?: number } = {}) =>
    (
      await apiClient.get<ApiResponse<PageResponse<TopicRegistrationResponse>>>(
        '/topic-registrations',
        { params },
      )
    ).data.data,
  create: async (payload: { groupId: number; topicId: number; motivation?: string }) =>
    (await apiClient.post<ApiResponse<TopicRegistrationResponse>>('/topic-registrations', payload))
      .data.data,
  review: async (
    id: number,
    payload: { version: number; status: 'APPROVED' | 'REJECTED'; decisionReason?: string },
  ) =>
    (
      await apiClient.patch<ApiResponse<TopicRegistrationResponse>>(
        `/topic-registrations/${id}/review`,
        payload,
      )
    ).data.data,
  withdraw: async (id: number, payload: { version: number; decisionReason: string }) =>
    (
      await apiClient.patch<ApiResponse<TopicRegistrationResponse>>(
        `/topic-registrations/${id}/withdraw`,
        payload,
      )
    ).data.data,
}