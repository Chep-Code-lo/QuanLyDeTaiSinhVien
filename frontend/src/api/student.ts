import { apiClient } from './client'
import type { ApiResponse, PageResponse } from '../types/api'
import type {
  DefenseSessionResponse,
  ProjectAssignmentResponse,
  RegistrationPeriodResponse,
} from '../types/domain'

export async function allPages<T>(fetchPage: (page: number) => Promise<PageResponse<T>>): Promise<T[]> {
  const first = await fetchPage(0)
  const items = [...first.content]
  for (let page = 1; page < first.totalPages; page++) items.push(...(await fetchPage(page)).content)
  return items
}
export const errorMessage = (error: unknown): string => {
  const e = error as { response?: { status?: number; data?: { message?: string } } }
  if (e.response?.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
  if (e.response?.status === 413) return 'Tệp quá lớn. Vui lòng chọn tệp không vượt quá 20 MB.'
  return e.response?.data?.message || 'Không thể kết nối máy chủ. Vui lòng thử lại.'
}
export function isWindowOpen(period: RegistrationPeriodResponse | undefined, kind: 'proposal' | 'registration') {
  if (!period || period.status !== 'ACTIVE') return false
  const now = Date.now()
  return now >= new Date(period[`${kind}StartAt`]).getTime() && now <= new Date(period[`${kind}EndAt`]).getTime()
}
export const dateText = (value?: string) => value ? new Date(value).toLocaleString('vi-VN') : 'Chưa xác định'
export const projectTypes: Record<string, string> = {
  COURSE: 'Môn học', RESEARCH: 'Nghiên cứu khoa học', SPECIALIZED: 'Tiểu luận chuyên ngành', GRADUATION: 'Khóa luận tốt nghiệp',
}
export interface PublishedResult {
  id: number; assignmentId: number; topicCode: string; topicTitle: string; groupName: string; periodName: string
  finalScore: number; publishedAt: string; versionNo: number
  inputs: { id: number; componentCode: string; scoreSnapshot: number; rubricSnapshot: string; lecturerId?: number; lecturerName?: string; overallComment?: string }[]
  members: { studentId: number; studentCodeSnapshot: string; fullNameSnapshot: string }[]
}
export const studentApi = {
  projectAssignments: async () =>
    (await apiClient.get<ApiResponse<ProjectAssignmentResponse[]>>('/project-assignments/mine')).data.data,
  defenses: async () =>
    (await apiClient.get<ApiResponse<DefenseSessionResponse[]>>('/student/defense-sessions')).data.data,
  results: async () => (await apiClient.get<ApiResponse<PublishedResult[]>>('/student/results')).data.data,
  upload: async (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return (await apiClient.post<ApiResponse<{ id: number }>>('/files', form, {
      headers: { 'Content-Type': undefined }, timeout: 120_000,
    })).data.data
  },
  submit: async (id: number, fileId: number, note: string) =>
    (await apiClient.post(`/milestones/${id}/submissions`, { fileId, note })).data,
  download: async (id: number, name: string) => {
    const response = await apiClient.get(`/files/${id}/download`, { responseType: 'blob' })
    const url = URL.createObjectURL(response.data)
    const a = document.createElement('a')
    a.href = url; a.download = name; a.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  },
}
