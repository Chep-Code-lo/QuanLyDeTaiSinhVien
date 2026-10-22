import { apiClient } from './client'
import type { ApiResponse } from '../types/api'
import type { ProjectType } from '../types/domain'

export interface DashboardSummary {
  totalTopics: number
  registeringTopics: number
  inProgressTopics: number
  completedTopics: number
  openRegistrationPeriods: number
  unreadNotifications: number
  nextRegistrationCloseAt: string | null
  currentSemester: { semesterNo: number; academicYear: string } | null
  topicTypes: { projectType: ProjectType; count: number }[]
  recentTopics: {
    id: number
    code: string
    title: string
    departmentName: string
    status: string
  }[]
  upcomingDeadlines: { periodId: number; periodName: string; title: string; dueAt: string }[]
  updatedAt: string
}

export const dashboardApi = {
  summary: async () => (await apiClient.get<ApiResponse<DashboardSummary>>('/dashboard')).data.data,
}
