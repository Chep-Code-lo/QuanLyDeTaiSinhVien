import { apiClient } from './client'
import type { ApiResponse, PageResponse } from '../types/api'
import type {
  AssessmentComponentResponse,
  CreateAssessmentComponentRequest,
  CreateDefenseRequest,
  CreateEvaluationRequest,
  CreateRubricRequest,
  DefenseSessionResponse,
  EvaluationCommitteeResponse,
  SaveCommitteeRequest,
  EvaluationAssignmentResponse,
  EvaluationStatus,
  RubricResponse,
  SaveSheetRequest,
  ScoreSheetResponse,
} from '../types/domain'

export interface ListAssignmentsParams {
  periodId?: number
  assignmentId?: number
  lecturerId?: number
  status?: EvaluationStatus
  page?: number
  size?: number
}

export interface ListDefensesParams {
  periodId?: number
  committeeId?: number
  page?: number
  size?: number
}

export const evaluationApi = {
  // Assessment Components
  listComponents: async (periodId: number) => {
    const response = await apiClient.get<ApiResponse<AssessmentComponentResponse[]>>(
      '/assessment-components',
      {
        params: { periodId },
      },
    )
    return response.data.data
  },
  createComponent: async (payload: CreateAssessmentComponentRequest) => {
    const response = await apiClient.post<ApiResponse<AssessmentComponentResponse>>(
      '/assessment-components',
      payload,
    )
    return response.data.data
  },
  lockComponent: async (id: number) => {
    const response = await apiClient.patch<ApiResponse<AssessmentComponentResponse>>(
      `/assessment-components/${id}/lock`,
    )
    return response.data.data
  },

  // Rubrics
  listRubrics: async (componentId: number) => {
    const response = await apiClient.get<ApiResponse<RubricResponse[]>>('/rubrics', {
      params: { componentId },
    })
    return response.data.data
  },
  createRubric: async (payload: CreateRubricRequest) => {
    const response = await apiClient.post<ApiResponse<RubricResponse>>('/rubrics', payload)
    return response.data.data
  },
  lockRubric: async (id: number) => {
    const response = await apiClient.patch<ApiResponse<RubricResponse>>(`/rubrics/${id}/lock`)
    return response.data.data
  },

  // Evaluation Assignments
  listAssignments: async (params: ListAssignmentsParams = {}) => {
    const response = await apiClient.get<ApiResponse<PageResponse<EvaluationAssignmentResponse>>>(
      '/evaluation-assignments',
      {
        params,
      },
    )
    return response.data.data
  },
  assign: async (payload: CreateEvaluationRequest) => {
    const response = await apiClient.post<ApiResponse<number>>('/evaluation-assignments', payload)
    return response.data.data
  },
  assignBatch: async (assignments: CreateEvaluationRequest[]) => {
    const response = await apiClient.post<
      ApiResponse<{ assignedCount: number; skippedCount: number }>
    >('/evaluation-assignments/batch', { assignments })
    return response.data.data
  },
  defenseEligibility: async (
    assignmentId: number,
    committeeId: number,
    proposed?: { location: string; startsAt: string; endsAt: string },
  ) => {
    const response = await apiClient.get<ApiResponse<{ eligible: boolean; reason: string | null }>>(
      '/defense-sessions/eligibility',
      { params: { assignmentId, committeeId, ...proposed } },
    )
    return response.data.data
  },

  listCommittees: async (params: { periodId?: number; page?: number; size?: number } = {}) => {
    const response = await apiClient.get<ApiResponse<PageResponse<EvaluationCommitteeResponse>>>(
      '/evaluation-committees',
      { params },
    )
    return response.data.data
  },

  saveCommittee: async (id: number | null, payload: SaveCommitteeRequest) => {
    const response = id
      ? await apiClient.put<ApiResponse<EvaluationCommitteeResponse>>(
          '/evaluation-committees/' + id + '/configuration',
          payload,
        )
      : await apiClient.post<ApiResponse<EvaluationCommitteeResponse>>(
          '/evaluation-committees/configuration',
          payload,
        )
    return response.data.data
  },

  // Defense Sessions
  listDefenses: async (params: ListDefensesParams = {}) => {
    const response = await apiClient.get<ApiResponse<PageResponse<DefenseSessionResponse>>>(
      '/defense-sessions',
      {
        params,
      },
    )
    return response.data.data
  },
  scheduleDefense: async (payload: CreateDefenseRequest) => {
    const response = await apiClient.post<ApiResponse<number>>('/defense-sessions', payload)
    return response.data.data
  },

  // Score Sheets
  getScoreSheet: async (assignmentId: number) => {
    const response = await apiClient.get<ApiResponse<ScoreSheetResponse>>(
      `/evaluation-assignments/${assignmentId}/score-sheet`,
    )
    return response.data.data
  },
  saveScoreSheet: async (assignmentId: number, payload: SaveSheetRequest) => {
    const response = await apiClient.put<ApiResponse<ScoreSheetResponse>>(
      `/evaluation-assignments/${assignmentId}/score-sheet`,
      payload,
    )
    return response.data.data
  },
}
