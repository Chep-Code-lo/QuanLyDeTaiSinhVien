import { apiClient } from './client'
import type { ApiResponse } from '../types/api'
import type {
  CreateMilestoneRequest,
  FeedbackRequest,
  MilestoneResponse,
  MilestoneReminderResponse,
  UpdateMilestoneRequest,
  SubmissionResponse,
} from '../types/domain'

export const milestoneApi = {
  update: async (id: number, payload: UpdateMilestoneRequest) =>
    (await apiClient.put<ApiResponse<MilestoneResponse>>(`/milestones/${id}`, payload)).data.data,
  remind: async (assignmentId: number) =>
    (await apiClient.post<ApiResponse<MilestoneReminderResponse>>(
      `/project-assignments/${assignmentId}/milestones/reminders`,
    )).data.data,
  create: async (payload: CreateMilestoneRequest) => {
    const response = await apiClient.post<ApiResponse<MilestoneResponse>>('/milestones', payload)
    return response.data.data
  },
  listByAssignment: async (assignmentId: number) => {
    const response = await apiClient.get<ApiResponse<MilestoneResponse[]>>(
      `/project-assignments/${assignmentId}/milestones`,
    )
    return response.data.data
  },
  submitFeedback: async (submissionId: number, payload: FeedbackRequest) => {
    const response = await apiClient.post<ApiResponse<SubmissionResponse>>(
      `/report-submissions/${submissionId}/feedback`,
      payload,
    )
    return response.data.data
  },
}
