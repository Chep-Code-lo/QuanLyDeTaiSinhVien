import { apiClient } from './client'
import type { ApiResponse } from '../types/api'
import type {
  CreateMilestoneRequest,
  FeedbackRequest,
  MilestoneResponse,
  SubmissionResponse,
} from '../types/domain'

export const milestoneApi = {
  create: async (payload: CreateMilestoneRequest) => {
    const response = await apiClient.post<ApiResponse<MilestoneResponse>>('/milestones', payload)
    return response.data.data
  },
  listByAssignment: async (assignmentId: number) => {
    const response = await apiClient.get<ApiResponse<MilestoneResponse[]>>(`/project-assignments/${assignmentId}/milestones`)
    return response.data.data
  },
  submitFeedback: async (submissionId: number, payload: FeedbackRequest) => {
    const response = await apiClient.post<ApiResponse<SubmissionResponse>>(
      `/report-submissions/${submissionId}/feedback`,
      payload
    )
    return response.data.data
  },
}
