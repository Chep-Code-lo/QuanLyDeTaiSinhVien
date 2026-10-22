import { apiClient } from './client'
import type { ApiResponse, PageResponse } from '../types/api'
import type {
  CreateRegistrationTopicRequest,
  CreateTopicRequest,
  TopicInput,
  TopicReviewDecision,
  TopicResponse,
  TopicStatus,
} from '../types/domain'

export const topicApi = {
  list: async (
    params: {
      periodId?: number
      status?: TopicStatus
      q?: string
      page?: number
      size?: number
    } = {},
  ) => {
    const response = await apiClient.get<ApiResponse<PageResponse<TopicResponse>>>('/topics', {
      params,
    })
    return response.data.data
  },
  get: async (id: number) =>
    (await apiClient.get<ApiResponse<TopicResponse>>(`/topics/${id}`)).data.data,
  update: async (id: number, payload: TopicInput & { version: number }) =>
    (await apiClient.put<ApiResponse<TopicResponse>>(`/topics/${id}`, payload)).data.data,
  assignSupervisors: async (
    id: number,
    payload: { version: number; primaryLecturerId: number; secondaryLecturerId?: number | null },
  ) =>
    (await apiClient.put<ApiResponse<TopicResponse>>(`/topics/${id}/supervisors`, payload)).data
      .data,
  withdraw: async (id: number, payload: { version: number; reason: string }) =>
    (await apiClient.patch<ApiResponse<TopicResponse>>(`/topics/${id}/withdraw`, payload)).data
      .data,
  create: async (payload: CreateTopicRequest) =>
    (await apiClient.post<ApiResponse<TopicResponse>>('/topics', payload)).data.data,
  createForRegistration: async (payload: CreateRegistrationTopicRequest) =>
    (await apiClient.post<ApiResponse<TopicResponse>>('/topics/for-registration', payload)).data
      .data,
  review: async (
    id: number,
    payload: { version: number; decision: TopicReviewDecision; comment: string },
  ) =>
    (await apiClient.patch<ApiResponse<TopicResponse>>(`/topics/${id}/review`, payload)).data.data,
  publish: async (id: number, version: number) =>
    (await apiClient.patch<ApiResponse<TopicResponse>>(`/topics/${id}/publish`, { version })).data
      .data,
}
