import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import type { AxiosAdapter } from 'axios'
import App from '../src/app/App'
import { apiClient } from '../src/api/client'
import type { CurrentUser, EmailTemplateResponse, RoleCode } from '../src/types/domain'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

// Load the actual styles: rendering routes alone did not catch the broken shell.
const stylesheet = document.createElement('style')
beforeAll(() => {
  stylesheet.textContent = ['styles/base.css', 'styles/app.css', 'styles/student-workspace.css', 'styles/ui.css', 'pages/AnnouncementsPage.css', 'pages/EmailSettingsPage.css']
    .map(file => readFileSync(resolve(process.cwd(), 'src', file), 'utf8')).join('\n')
  document.head.append(stylesheet)
})
afterAll(() => stylesheet.remove())

let currentUser: CurrentUser | null
const emptyPage = { content: [], totalPages: 0, totalElements: 0, page: 0, size: 100 }
const emailSettings = [{
  id: 1,
  eventType: 'TASK_ASSIGNED' as const,
  enabled: false,
  senderName: 'Hệ thống quản lý đề tài sinh viên',
  fromEmail: 'thongbao@example.edu.vn',
  subjectTemplate: 'Nhiệm vụ mới: {{taskName}}',
  bodyTemplate: 'Chào {{studentName}},\nBạn có nhiệm vụ mới.',
  recipientGroups: ['DIRECT_PARTICIPANTS'] as const,
  scopeType: 'SYSTEM' as const,
  scopeId: 0,
  version: 0,
  allowedVariables: ['studentName', 'taskName'],
}]
const resultEmailSetting = {
  ...emailSettings[0], id: 2, eventType: 'RESULT_PUBLISHED' as const,
  subjectTemplate: 'Kết quả đánh giá: {{topicName}}',
  bodyTemplate: 'Điểm tổng kết: {{finalScore}}/10. Xem kết quả: {{resultUrl}}',
  allowedVariables: ['studentName', 'finalScore', 'resultUrl'],
}
const originalAdapter = apiClient.defaults.adapter
const requested: string[] = []
const sentEmails: { recipientRoles: string[]; recipientUserIds: number[] }[] = []
const savedTemplates: EmailTemplateResponse[] = []
const settingScopes: unknown[] = []
const emailRecipients = [
  { userId: 10, fullName: 'Sinh viên An', email: 'an@example.edu.vn', role: 'STUDENT' },
  { userId: 11, fullName: 'Sinh viên Bình', email: 'binh@example.edu.vn', role: 'STUDENT' },
  { userId: 12, fullName: 'Giảng viên Cường', email: 'cuong@example.edu.vn', role: 'LECTURER' },
]
const paged = new Set(['/topics', '/groups', '/registration-periods', '/group-invitations/mine', '/topic-registrations', '/topic-registrations/mine', '/notifications', '/announcements', '/evaluation-assignments', '/project-assignments', '/defense-sessions', '/evaluation-committees', '/lecturers', '/admin/users'])
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
  else if (url === '/notification-settings') {
    settingScopes.push(config.params)
    const items = currentUser?.roles.includes('ADMIN') ? [...emailSettings, resultEmailSetting] : emailSettings
    data = items.map(setting => ({ ...setting, ...config.params }))
  }
  else if (url === '/notification-settings/RESULT_PUBLISHED' && config.method === 'put') {
    data = { ...resultEmailSetting, ...JSON.parse(config.data) }
  }
  else if (url === '/email-templates' && config.method === 'get') {
    data = { ...emptyPage, content: savedTemplates, totalElements: savedTemplates.length, totalPages: 1 }
  }
  else if (url === '/email-templates' && config.method === 'post') {
    data = { ...JSON.parse(config.data), id: 100, version: 0, updatedAt: '2026-10-21T00:00:00' }
    savedTemplates.push(data as EmailTemplateResponse)
  }
  else if (/^\/email-templates\/\d+$/.test(url) && config.method === 'put') {
    const index = savedTemplates.findIndex(template => url.endsWith(`/${template.id}`))
    data = { ...savedTemplates[index], ...JSON.parse(config.data), version: savedTemplates[index].version + 1 }
    savedTemplates[index] = data as EmailTemplateResponse
  }
  else if (url === '/email-logs') data = emptyPage
  else if (url === '/email-recipients') {
    const role = config.params?.role
    const search = (config.params?.search ?? '').toLowerCase()
    const content = emailRecipients.filter(person => (!currentUser?.roles.includes('LECTURER') || currentUser.roles.includes('ADMIN') || person.role === 'STUDENT')
      && (!role || person.role === role)
      && `${person.fullName} ${person.email}`.toLowerCase().includes(search))
    data = { ...emptyPage, content, totalElements: content.length, totalPages: 1 }
  }
  else if (url.endsWith('/send') && (url.startsWith('/notification-settings/') || url.startsWith('/email-templates/'))) {
    const payload = JSON.parse(config.data)
    sentEmails.push(payload)
    data = { recipientCount: payload.recipientUserIds.length || 2 }
  }
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

beforeEach(() => {
  apiClient.defaults.adapter = adapter
  apiClient.defaults.headers.common['X-XSRF-TOKEN'] = 'test-csrf'
  requested.length = 0
  sentEmails.length = 0
  savedTemplates.length = 0
  settingScopes.length = 0
})
afterEach(() => {
  cleanup()
  apiClient.defaults.adapter = originalAdapter
  delete apiClient.defaults.headers.common['X-XSRF-TOKEN']
})

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
    ? [...common.filter(([path]) => path !== '/topics'), ['/topics', 'Danh sách đề tài đã công bố'], ['/progress', 'Tiến độ & báo cáo'], ['/results', 'Lịch bảo vệ & kết quả']]
    : role === 'LECTURER'
      ? [...common, ...management, ['/score-sheets', 'Chấm điểm đề tài'], ['/email-settings', 'Cấu hình email']]
      : [...common, ...management, ['/admin-users', 'Quản lý tài khoản'], ['/email-settings', 'Cấu hình email'], ['/admin-appointments', 'Bổ nhiệm chức vụ']]

  it.each(routes)('%s hiển thị được với dữ liệu trống', async (path, heading) => {
    currentUser = user(role)
    render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)
    expect(await screen.findByRole('heading', { level: 1, name: heading }, { timeout: 5000 })).toBeTruthy()
    await waitFor(() => expect([...document.querySelectorAll('.spinner')].filter(item => !item.closest('[hidden]'))).toHaveLength(0))
    expect(document.querySelector('.toast-error')).toBeNull()
    expect(document.querySelector('.app-sidebar')).toBeTruthy()
    expect(document.querySelector('.page-content')).toBeTruthy()
    expect(document.querySelector('.app-shell')?.classList.contains('management-shell')).toBe(role !== 'STUDENT')
    const shell = getComputedStyle(document.querySelector('.app-shell')!)
    expect(shell.display).toBe('grid')
    expect(shell.gridTemplateColumns).toBe('224px minmax(0, 1fr)')
    const logo = getComputedStyle(document.querySelector('.sidebar__brand-image')!)
    expect(logo.width).toBe('104px')
    expect(logo.height).toBe(logo.width)
    expect(getComputedStyle(document.querySelector('.app-main')!).minWidth).toBe('0px')
  })
})

describe('Quản lý tài khoản', () => {
  it('mở biểu mẫu tạo tài khoản trong popup', async () => {
    currentUser = user('ADMIN')
    render(<MemoryRouter initialEntries={['/admin-users']}><App /></MemoryRouter>)

    await userEvent.click(await screen.findByRole('button', { name: 'Tạo tài khoản' }))

    expect(await screen.findByRole('dialog', { name: 'Tạo tài khoản mới' })).toBeTruthy()
    expect(await screen.findByLabelText(/Vai trò/)).toBeTruthy()
  })
})

describe('Cấu hình email', () => {
  it('giảng viên có menu email và sử dụng phạm vi cá nhân', async () => {
    currentUser = user('LECTURER')
    render(<MemoryRouter initialEntries={['/email-settings']}><App /></MemoryRouter>)
    expect(await screen.findByLabelText('Nội dung thư')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Cấu hình email' })).toBeTruthy()
    expect(settingScopes).toContainEqual({ scopeType: 'LECTURER', scopeId: 1 })
    await userEvent.selectOptions(screen.getByLabelText('Gửi đến'), 'STUDENT')
    expect(await screen.findByText(/sinh viên đang hướng dẫn/)).toBeTruthy()
    expect(screen.queryByRole('option', { name: 'Giảng viên', exact: true })).toBeNull()
  })

  it('mẫu công bố điểm chỉ cấu hình gửi tự động cho sinh viên của kết quả', async () => {
    currentUser = user('ADMIN')
    render(<MemoryRouter initialEntries={['/email-settings']}><App /></MemoryRouter>)
    await userEvent.click(await screen.findByRole('button', { name: /Công bố kết quả điểm/ }))
    expect((screen.getByLabelText('Nội dung thư') as HTMLTextAreaElement).value).toContain('{{finalScore}}')
    expect(screen.queryByLabelText('Gửi đến')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Gửi email' })).toBeNull()
    expect(screen.getByText(/Người nhận là sinh viên có tên trong kết quả đã chốt/)).toBeTruthy()
    await userEvent.click(screen.getByRole('checkbox', { name: 'Đang tắt' }))
    await userEvent.click(screen.getByRole('button', { name: 'Lưu cấu hình' }))
    expect(await screen.findByRole<HTMLInputElement>('checkbox', { name: 'Đang bật' })).toBeTruthy()
    await waitFor(() => expect(requested).toContain('/notification-settings/RESULT_PUBLISHED'))
    expect(sentEmails).toHaveLength(0)
  })

  it.each(['ADMIN', 'LECTURER'] as RoleCode[])('%s tạo, mở lại, sửa và gửi mẫu email riêng', async role => {
    currentUser = user(role)
    const firstRender = render(<MemoryRouter initialEntries={['/email-settings']}><App /></MemoryRouter>)
    await screen.findByLabelText('Nội dung thư')
    await userEvent.click(screen.getByRole('button', { name: 'Tạo mẫu mới' }))
    await userEvent.type(screen.getByLabelText('Tên mẫu email'), 'Nhắc lịch họp')
    await userEvent.type(screen.getByLabelText('Tiêu đề thư'), 'Lịch họp tuần này')
    await userEvent.type(screen.getByLabelText('Nội dung thư'), 'Mời tham gia họp lúc 8 giờ.')
    await userEvent.click(screen.getByRole('button', { name: 'Tạo mẫu', exact: true }))
    await waitFor(() => expect(savedTemplates).toHaveLength(1))
    await screen.findByRole('button', { name: 'Lưu mẫu' })
    expect(savedTemplates[0].bodyTemplate).toBe('Mời tham gia họp lúc 8 giờ.')
    firstRender.unmount()
    render(<MemoryRouter initialEntries={['/email-settings']}><App /></MemoryRouter>)
    await userEvent.click(await screen.findByRole('button', { name: /Nhắc lịch họp/ }))
    expect((screen.getByLabelText('Nội dung thư') as HTMLTextAreaElement).value).toBe('Mời tham gia họp lúc 8 giờ.')
    await userEvent.type(screen.getByLabelText('Tiêu đề thư'), ' - cập nhật')
    await userEvent.click(screen.getByRole('button', { name: 'Lưu mẫu' }))
    await waitFor(() => expect(savedTemplates[0].version).toBe(1))
    await userEvent.selectOptions(screen.getByLabelText('Gửi đến'), 'STUDENT')
    const sendButton = screen.getByRole<HTMLButtonElement>('button', { name: 'Gửi email' })
    await waitFor(() => expect(sendButton.disabled).toBe(false))
    await userEvent.click(sendButton)
    await waitFor(() => expect(requested).toContain('/email-templates/100/send'))
    expect(sentEmails).toEqual([{ recipientRoles: ['STUDENT'], recipientUserIds: [] }])
    expect(requested.some(url => url.endsWith('/test'))).toBe(false)
  })

  it('bỏ hướng dẫn SMTP, cách sử dụng, biến mẫu và gửi thử tài khoản hiện tại', async () => {
    currentUser = user('ADMIN')
    render(<MemoryRouter initialEntries={['/email-settings']}><App /></MemoryRouter>)

    expect(await screen.findByLabelText('Nội dung thư')).toBeTruthy()
    expect(screen.queryByRole('heading', { name: 'Cách sử dụng' })).toBeNull()
    expect(screen.queryByText('SMTP được cấu hình bằng biến môi trường')).toBeNull()
    expect(screen.queryByText('Biến có thể dùng')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Gửi thử' })).toBeNull()
    expect(await screen.findByLabelText('Gửi đến')).toBeTruthy()
    expect(screen.queryByText('Nội dung thư (HTML)')).toBeNull()
  })

  it.each([['STUDENT', 'Sinh viên'], ['LECTURER', 'Giảng viên']])('gửi đúng nhóm %s', async (role, label) => {
    currentUser = user('ADMIN')
    render(<MemoryRouter initialEntries={['/email-settings']}><App /></MemoryRouter>)
    await userEvent.selectOptions(await screen.findByLabelText('Gửi đến'), role)
    await screen.findByText(new RegExp(`Gửi tới toàn bộ .* ${label.toLowerCase()}`))
    const sendButton = screen.getByRole<HTMLButtonElement>('button', { name: 'Gửi email' })
    await waitFor(() => expect(sendButton.disabled).toBe(false))
    await userEvent.click(sendButton)
    await waitFor(() => expect(sentEmails).toEqual([{ recipientRoles: [role], recipientUserIds: [] }]))
    expect(requested.some(url => url.endsWith('/test'))).toBe(false)
  })

  it('tìm và gửi đúng người cụ thể, không tự thêm tài khoản đăng nhập', async () => {
    currentUser = user('ADMIN')
    render(<MemoryRouter initialEntries={['/email-settings']}><App /></MemoryRouter>)
    const search = await screen.findByLabelText('Tìm người nhận')
    await userEvent.type(search, 'binh@example.edu.vn')
    await userEvent.click(await screen.findByRole('checkbox', { name: /Sinh viên Bình/ }))
    const sendButton = screen.getByRole<HTMLButtonElement>('button', { name: 'Gửi email' })
    await waitFor(() => expect(sendButton.disabled).toBe(false))
    await userEvent.click(sendButton)
    await waitFor(() => expect(sentEmails).toEqual([{ recipientRoles: [], recipientUserIds: [11] }]))
    expect(requested.some(url => url.endsWith('/test'))).toBe(false)
  })

  it('không cho gửi khi chưa chọn người hoặc khi nội dung chưa lưu', async () => {
    currentUser = user('ADMIN')
    render(<MemoryRouter initialEntries={['/email-settings']}><App /></MemoryRouter>)
    const sendButton = await screen.findByRole<HTMLButtonElement>('button', { name: 'Gửi email' })
    expect(sendButton.disabled).toBe(true)
    await userEvent.click(await screen.findByRole('checkbox', { name: /Sinh viên An/ }))
    await waitFor(() => expect(sendButton.disabled).toBe(false))
    await userEvent.type(screen.getByLabelText('Tiêu đề thư'), ' thay đổi')
    expect(sendButton.disabled).toBe(true)
    expect(sentEmails).toHaveLength(0)
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
    expect(document.querySelector('.management-shell')).toBeNull()
  })

  it('điều hướng nút đăng nhập từ trang chủ tới trang đăng nhập riêng', async () => {
    currentUser = null
    render(<MemoryRouter initialEntries={['/']}><App /></MemoryRouter>)

    await userEvent.click(await screen.findByRole('button', { name: 'Đăng nhập' }))

    expect(await screen.findByRole('heading', { name: 'Đăng nhập hệ thống' })).toBeTruthy()
    expect(screen.queryByText(/Từ ý tưởng.*đến đề tài hoàn chỉnh/)).toBeNull()
  })

  it('trở về trang chủ khi đăng xuất từ trang được bảo vệ', async () => {
    currentUser = user('ADMIN')
    render(<MemoryRouter initialEntries={['/dashboard']}><App /></MemoryRouter>)

    expect(await screen.findByRole('heading', { name: 'Hệ thống quản lý đề tài sinh viên' })).toBeTruthy()
    window.dispatchEvent(new Event('session-expired'))

    expect(await screen.findByRole('heading', { name: /Từ ý tưởng.*đến đề tài hoàn chỉnh/ })).toBeTruthy()
    expect(screen.queryByRole('heading', { name: 'Đăng nhập hệ thống' })).toBeNull()
  })
})
