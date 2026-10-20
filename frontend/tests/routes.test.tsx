import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { AxiosAdapter } from 'axios'
import App from '../src/App'
import { apiClient } from '../src/api/client'
import type { CurrentUser, RoleCode } from '../src/types/domain'

let currentUser: CurrentUser | null
const emptyPage = { content: [], totalPages: 0, totalElements: 0, page: 0, size: 100 }
const originalAdapter = apiClient.defaults.adapter
const requested: string[] = []
const paged = new Set(['/topics', '/groups', '/registration-periods', '/group-invitations/mine', '/topic-registrations', '/notifications', '/announcements', '/evaluation-assignments', '/project-assignments', '/defense-sessions', '/evaluation-committees', '/lecturers'])
const lists = new Set(['/groups/mine', '/admin/academic-appointments', '/assessment-components', '/rubrics', '/student/results'])

const adapter: AxiosAdapter = async config => {
  const url = config.url ?? ''
  requested.push(url)
  let data: unknown
  if (url === '/auth/me') data = currentUser
  else if (url === '/faculties') data = [{ id: 1, code: 'CNTT', name: 'Khoa Công nghệ thông tin' }]
  else if (url === '/departments') data = [{ id: 1, facultyId: 1, code: 'CNPM', name: 'Công nghệ phần mềm' }]
  else if (url === '/semesters') data = [{ id: 1, code: '2026-1', name: 'Học kỳ 1', semesterNo: 1, academicYear: '2026-2027' }]
  else if (url === '/notifications/unread-count') data = { unreadCount: 0 }
  else if (url === '/dashboard') data = { totalTopics: 0, registeringTopics: 0, inProgressTopics: 0, completedTopics: 0, openRegistrationPeriods: 0, unreadNotifications: 0, nextRegistrationCloseAt: null, currentSemester: null, topicTypes: [], recentTopics: [], upcomingDeadlines: [], updatedAt: '2026-10-20T00:00:00Z' }
  else if (paged.has(url)) data = emptyPage
  else if (lists.has(url)) data = []
  else throw new Error(`API chưa được mô phỏng: ${url}`)
  return { data: { success: true, data, message: '', timestamp: '2026-10-20T00:00:00Z' }, status: 200, statusText: 'OK', config, headers: {} }
}

function user(role: RoleCode): CurrentUser {
  return {
    id: 1, fullName: 'Người dùng kiểm thử', username: 'test@demo.local', roles: [role],
    ...(role === 'STUDENT' ? { student: { id: 1, studentCode: 'SV01', className: '01', cohort: '2026', departmentId: 1 } } : {}),
    ...(role === 'LECTURER' ? { lecturer: { id: 1, lecturerCode: 'GV01', departmentId: 1 } } : {}),
    activeAppointments: role === 'LECTURER' ? [{ id: 1, lecturerId: 1, lecturerCode: 'GV01', lecturerName: 'Người dùng kiểm thử', facultyId: 1, positionCode: 'DEAN', startsAt: '2026-01-01T00:00:00Z' }] : [],
  }
}

beforeEach(() => { apiClient.defaults.adapter = adapter; requested.length = 0 })
afterEach(() => { cleanup(); apiClient.defaults.adapter = originalAdapter })

const common = [
  ['/dashboard', 'Hệ thống quản lý đề tài sinh viên'], ['/topics', 'Đề tài & Dự án'],
  ['/groups', 'Nhóm đề tài'], ['/registrations', 'Đăng ký đề tài'],
  ['/account', 'Tài khoản'], ['/announcements', 'Thông báo'],
] as const
const management = [
  ['/progress', 'Tiến độ & Mốc báo cáo'], ['/evaluations', 'Tiêu chuẩn đánh giá'],
  ['/evaluator-assignment', 'Phân công hội đồng'], ['/academic-management', 'Quản lý Học thuật'],
] as const

describe.each(['STUDENT', 'LECTURER', 'ADMIN'] as RoleCode[])('Trang dành cho %s', role => {
  const routes = role === 'STUDENT'
    ? [...common, ['/progress', 'Tiến độ & báo cáo'], ['/results', 'Lịch bảo vệ & kết quả']]
    : role === 'LECTURER'
      ? [...common, ...management, ['/score-sheets', 'Chấm điểm đề tài']]
      : [...common, ...management, ['/admin-users/new', 'Tạo tài khoản'], ['/admin-appointments', 'Bổ nhiệm chức vụ']]

  it.each(routes)('%s hiển thị được với dữ liệu trống', async (path, heading) => {
    currentUser = user(role)
    render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)
    expect(await screen.findByRole('heading', { level: 1, name: heading }, { timeout: 5000 })).toBeTruthy()
    await waitFor(() => expect([...document.querySelectorAll('.spinner')].filter(item => !item.closest('[hidden]'))).toHaveLength(0))
    expect(document.querySelector('.toast-error')).toBeNull()
    expect(document.querySelector('.app-sidebar')).toBeTruthy()
    expect(document.querySelector('.page-content')).toBeTruthy()
  })
})

describe('Trang công khai', () => {
  it.each([
    ['/', /Từ ý tưởng.*đến đề tài hoàn chỉnh/],
    ['/login', /Đăng nhập hệ thống/],
    ['/khong-ton-tai', /Không tìm thấy trang/],
  ])('%s hiển thị được khi chưa đăng nhập', async (path, heading) => {
    currentUser = null
    render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)
    expect(await screen.findByRole('heading', { level: 1, name: heading }, { timeout: 5000 })).toBeTruthy()
  })
})
