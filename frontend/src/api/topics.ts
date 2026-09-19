import { apiClient } from './client'
import type { ApiResponse, PageResponse } from '../types/api'
import type { TopicResponse, TopicStatus } from '../types/domain'

export const topicApi = {
  list: async (params: { periodId?: number; status?: TopicStatus; page?: number; size?: number } = {}) => {
    const response = await apiClient.get<ApiResponse<PageResponse<TopicResponse>>>('/topics', { params })
    return response.data.data
  },
}
