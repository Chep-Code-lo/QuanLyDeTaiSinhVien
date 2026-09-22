import { apiClient } from './client'
import type { ApiResponse, PageResponse } from '../types/api'
import type { TopicResponse, TopicStatus } from '../types/domain'

export const topicApi = {
  list: async (params: { periodId?: number; status?: TopicStatus; page?: number; size?: number } = {}) => {
    const response = await apiClient.get<ApiResponse<PageResponse<TopicResponse>>>('/topics', { params })
    return response.data.data
  },
  create: async (payload: Record<string, unknown>) => (await apiClient.post<ApiResponse<TopicResponse>>('/topics', payload)).data.data,
  review: async (id: number, payload: { version: number; decision: string; comment?: string }) =>
    (await apiClient.patch<ApiResponse<TopicResponse>>(`/topics/${id}/review`, payload)).data.data,
  publish: async (id: number, version: number) =>
    (await apiClient.patch<ApiResponse<TopicResponse>>(`/topics/${id}/publish`, { version })).data.data,
}
