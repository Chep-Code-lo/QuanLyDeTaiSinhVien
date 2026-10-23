import { StrictMode } from 'react'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
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
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
  stylesheet.textContent = [
    'styles/base.css',
    'styles/app.css',
    'styles/student-workspace.css',
    'styles/ui.css',
    'pages/AnnouncementsPage.css',
    'pages/EmailSettingsPage.css',
  ]
    .map((file) => readFileSync(resolve(process.cwd(), 'src', file), 'utf8'))
    .join('\n')
  document.head.append(stylesheet)
})
afterAll(() => stylesheet.remove())

let currentUser: CurrentUser | null
const emptyPage = { content: [], totalPages: 0, totalElements: 0, page: 0, size: 100 }
const emailSettings = [
  {
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
  },
]
const resultEmailSetting = {
  ...emailSettings[0],
  id: 2,
  eventType: 'RESULT_PUBLISHED' as const,
  subjectTemplate: 'Kết quả đánh giá: {{topicName}}',
  bodyTemplate: 'Điểm tổng kết: {{finalScore}}/10. Xem kết quả: {{resultUrl}}',
  allowedVariables: ['studentName', 'finalScore', 'resultUrl'],
}
const originalAdapter = apiClient.defaults.adapter
const requested: string[] = []
const topicQueries: Record<string, unknown>[] = []
const sentEmails: { recipientRoles: string[]; recipientUserIds: number[] }[] = []
const savedTemplates: EmailTemplateResponse[] = []
const settingScopes: unknown[] = []
const testEmails: unknown[] = []
let delayedSettings: Promise<void> | null = null
let logFixture: Record<string, unknown>[][] = []
const logRequests: number[] = []
const emailRecipients = [
  { userId: 10, fullName: 'Sinh viên An', email: 'an@example.edu.vn', role: 'STUDENT' },
  { userId: 11, fullName: 'Sinh viên Bình', email: 'binh@example.edu.vn', role: 'STUDENT' },
  { userId: 12, fullName: 'Giảng viên Cường', email: 'cuong@example.edu.vn', role: 'LECTURER' },
]
const paged = new Set([
  '/topics',
  '/groups',
  '/registration-periods',
  '/group-invitations/mine',
  '/topic-registrations',
  '/topic-registrations/mine',
  '/notifications',
  '/announcements',
  '/evaluation-assignments',
  '/project-assignments',
  '/project-assignments/supervised',
  '/defense-sessions',
  '/evaluation-committees',
  '/lecturers',
  '/admin/users',
  '/admin/audit-logs',
  '/grade-change-requests',
])
const lists = new Set([
  '/groups/mine',
  '/admin/academic-appointments',
  '/assessment-components',
  '/rubrics',
  '/student/results',
])

const adapter: AxiosAdapter = async (config) => {
  const url = config.url ?? ''
  requested.push(url)
  if (url === '/topics') topicQueries.push({ ...config.params })
  let data: unknown
  if (url === '/auth/me') data = currentUser
  else if (url === '/faculties') data = [{ id: 1, code: 'CNTT', name: 'Khoa Công nghệ thông tin' }]
  else if (url === '/departments')
    data = [{ id: 1, facultyId: 1, code: 'CNPM', name: 'Công nghệ phần mềm' }]
  else if (url === '/semesters')
    data = [{ id: 1, code: '2026-1', name: 'Học kỳ 1', semesterNo: 1, academicYear: '2026-2027' }]
  else if (url === '/notifications/unread-count') data = { unreadCount: 0 }
  else if (url === '/dashboard')
    data = {
      totalTopics: 0,
      registeringTopics: 0,
      inProgressTopics: 0,
      completedTopics: 0,
      openRegistrationPeriods: 0,
      unreadNotifications: 0,
      nextRegistrationCloseAt: null,
      currentSemester: null,
      topicTypes: [],
      recentTopics: [],
      upcomingDeadlines: [],
      updatedAt: '2026-10-20T00:00:00Z',
    }
  else if (url === '/notification-settings') {
    settingScopes.push(config.params)
    const items = currentUser?.roles.includes('ADMIN')
      ? [...emailSettings, resultEmailSetting]
      : emailSettings
    data = items.map((setting) => ({
      ...setting,
      ...config.params,
      ...(config.params?.scopeType === 'FACULTY' ? { senderName: 'Người gửi khoa' } : {}),
    }))
    if (delayedSettings) {
      const pending = delayedSettings
      delayedSettings = null
      await pending
    }
  } else if (url === '/notification-settings/RESULT_PUBLISHED' && config.method === 'put') {
    data = { ...resultEmailSetting, ...JSON.parse(config.data) }
  } else if (url === '/email-templates' && config.method === 'get') {
    data = {
      ...emptyPage,
      content: savedTemplates,
      totalElements: savedTemplates.length,
      totalPages: 1,
    }
  } else if (url === '/email-templates' && config.method === 'post') {
    data = { ...JSON.parse(config.data), id: 100, version: 0, updatedAt: '2026-10-21T00:00:00' }
    savedTemplates.push(data as EmailTemplateResponse)
  } else if (/^\/email-templates\/\d+$/.test(url) && config.method === 'put') {
    const index = savedTemplates.findIndex((template) => url.endsWith(`/${template.id}`))
    data = {
      ...savedTemplates[index],
      ...JSON.parse(config.data),
      version: savedTemplates[index].version + 1,
    }
    savedTemplates[index] = data as EmailTemplateResponse
  } else if (url.endsWith('/test') && url.startsWith('/notification-settings/')) {
    testEmails.push(JSON.parse(config.data))
    data = { outboxId: 99, recipientEmail: currentUser?.username, status: 'PENDING' }
  } else if (url === '/email-logs') {
    const page = config.params?.page ?? 0
    logRequests.push(page)
    data = {
      ...emptyPage,
      page,
      content: logFixture[page] ?? [],
      totalPages: logFixture.length,
      totalElements: logFixture.flat().length,
    }
  } else if (/^\/email-logs\/\d+$/.test(url)) {
    const id = Number(url.split('/').at(-1))
    data = { log: logFixture.flat().find((row) => row.id === id), attempts: [] }
  } else if (url === '/email-recipients') {
    const role = config.params?.role
    const search = (config.params?.search ?? '').toLowerCase()
    const content = emailRecipients.filter(
      (person) =>
        (!currentUser?.roles.includes('LECTURER') ||
          currentUser.roles.includes('ADMIN') ||
          person.role === 'STUDENT') &&
        (!role || person.role === role) &&
        `${person.fullName} ${person.email}`.toLowerCase().includes(search),
    )
    data = { ...emptyPage, content, totalElements: content.length, totalPages: 1 }
  } else if (
    url.endsWith('/send') &&
    (url.startsWith('/notification-settings/') || url.startsWith('/email-templates/'))
  ) {
    const payload = JSON.parse(config.data)
    sentEmails.push(payload)
    data = { recipientCount: payload.recipientUserIds.length || 2 }
  } else if (paged.has(url)) data = emptyPage
  else if (lists.has(url)) data = []
  else throw new Error(`API chưa được mô phỏng: ${url}`)
  return {
    data: { success: true, data, message: '', timestamp: '2026-10-20T00:00:00Z' },
    status: 200,
    statusText: 'OK',
    config,
    headers: {},
  }
}

function user(role: RoleCode): CurrentUser {
  return {
    id: 1,
    fullName: 'Người dùng kiểm thử',
    username: 'test@demo.local',
    roles: [role],
    ...(role === 'STUDENT'
      ? {
          student: { id: 1, studentCode: 'SV01', className: '01', cohort: '2026', departmentId: 1 },
        }
      : {}),
    ...(role === 'LECTURER' ? { lecturer: { id: 1, lecturerCode: 'GV01', departmentId: 1 } } : {}),
    activeAppointments:
      role === 'LECTURER'
        ? [
            {
              id: 1,
              lecturerId: 1,
              lecturerCode: 'GV01',
              lecturerName: 'Người dùng kiểm thử',
              facultyId: 1,
              positionCode: 'DEAN',
              startsAt: '2026-01-01T00:00:00Z',
            },
          ]
        : [],
  }
}

beforeEach(() => {
  apiClient.defaults.adapter = adapter
  apiClient.defaults.headers.common['X-XSRF-TOKEN'] = 'test-csrf'
  requested.length = 0
  topicQueries.length = 0
  sentEmails.length = 0
  savedTemplates.length = 0
  settingScopes.length = 0
  testEmails.length = 0
  delayedSettings = null
  logFixture = []
  logRequests.length = 0
})
afterEach(() => {
  cleanup()
  apiClient.defaults.adapter = originalAdapter
  delete apiClient.defaults.headers.common['X-XSRF-TOKEN']
})

const common = [
  ['/dashboard', 'Hệ thống quản lý đề tài sinh viên'],
  ['/topics', 'Đề tài & đăng ký'],
  ['/groups', 'Nhóm đề tài'],
  ['/registrations', 'Đề tài & đăng ký'],
  ['/account', 'Tài khoản'],
  ['/topic-workflow', 'Đề xuất & Xét duyệt đề tài'],
  ['/announcements', 'Thông báo'],
] as const
const management = [
  ['/progress', 'Tiến độ & Mốc báo cáo'],
  ['/evaluations', 'Tiêu chuẩn đánh giá'],
  ['/evaluator-assignment', 'Phân công giảng viên'],
  ['/evaluation-results', 'Tổng hợp & Công bố điểm'],
  ['/grade-change-requests', 'Yêu cầu đổi điểm'],
  ['/project-assignments', 'Đề tài được giao'],
  ['/academic-catalog', 'Danh mục học thuật'],
  ['/academic-management', 'Quản lý Học thuật'],
] as const

describe.each(['STUDENT', 'LECTURER', 'ADMIN'] as RoleCode[])('Trang dành cho %s', (role) => {
  const routes =
    role === 'STUDENT'
      ? [
          ...common,
          ['/progress', 'Tiến độ & báo cáo'],
          ['/results', 'Lịch bảo vệ & kết quả'],
          ['/results/7', 'Lịch bảo vệ & kết quả'],
        ]
      : role === 'LECTURER'
        ? [...common, ...management, ['/score-sheets', 'Chấm điểm đề tài']]
        : [
            ...common.filter(([path]) => path !== '/topic-workflow'),
            ...management.filter(
              ([path]) => !['/project-assignments', '/evaluator-assignment'].includes(path),
            ),
            ['/admin-users', 'Quản lý tài khoản'],
            ['/email-settings', 'Cấu hình email'],
            ['/admin-appointments', 'Bổ nhiệm chức vụ'],
            ['/audit-logs', 'Nhật ký thao tác'],
          ]

  it.each(routes)('%s hiển thị được với dữ liệu trống', async (path, heading) => {
    currentUser = user(role)
    render(
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>,
    )
    expect(
      await screen.findByRole(
        'heading',
        {
          level: 1,
          name: role === 'ADMIN' && path === '/evaluation-results' ? 'Tổng hợp kết quả' : heading,
        },
        { timeout: 5000 },
      ),
    ).toBeTruthy()
    await waitFor(() =>
      expect(
        [...document.querySelectorAll('.spinner')].filter((item) => !item.closest('[hidden]')),
      ).toHaveLength(0),
    )
    expect(document.querySelector('.toast-error')).toBeNull()
    expect(document.querySelector('.app-sidebar')).toBeTruthy()
    expect(document.querySelector('.page-content')).toBeTruthy()
    if (path === '/registrations') {
      expect(screen.getByRole('tab', { name: 'Hồ sơ đăng ký' }).getAttribute('aria-selected')).toBe(
        'true',
      )
      expect(
        screen.getByRole('button', { name: 'Đề tài & đăng ký' }).getAttribute('aria-current'),
      ).toBe('page')
    }
    expect(document.querySelector('.app-shell')?.classList.contains('management-shell')).toBe(
      role !== 'STUDENT',
    )
    const shell = getComputedStyle(document.querySelector('.app-shell')!)
    expect(shell.display).toBe('grid')
    expect(shell.gridTemplateColumns).toBe('224px minmax(0, 1fr)')
    const logo = getComputedStyle(document.querySelector('.sidebar__brand-image')!)
    expect(logo.width).toBe('104px')
    expect(logo.height).toBe(logo.width)
    expect(getComputedStyle(document.querySelector('.app-main')!).minWidth).toBe('0px')
  })
})

describe('Đề tài và hồ sơ đăng ký', () => {
  it.each(['ADMIN', 'LECTURER', 'STUDENT'] as RoleCode[])(
    '%s dùng hai tab trong một trang và chỉ có một nơi tạo đề tài',
    async (role) => {
      currentUser = user(role)
      render(
        <MemoryRouter initialEntries={['/topics?q=Kiểm thử']}>
          <App />
        </MemoryRouter>,
      )
      await screen.findByText('Chưa có đề tài phù hợp.')
      expect(topicQueries.at(-1)).toMatchObject({
        status: 'PUBLISHED',
        q: 'Kiểm thử',
        page: 0,
        size: 10,
      })
      expect(screen.queryByRole('button', { name: 'Đăng ký đề tài', exact: true })).toBeNull()
      expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
      expect(screen.queryAllByRole('button', { name: '+ Tạo đề tài', exact: true })).toHaveLength(
        role === 'LECTURER' ? 1 : 0,
      )
      await userEvent.click(screen.getByRole('tab', { name: 'Hồ sơ đăng ký' }))
      await screen.findByText('Chưa có hồ sơ đăng ký')
      expect(requested).toContain(
        role === 'STUDENT' ? '/topic-registrations/mine' : '/topic-registrations',
      )
      expect(screen.queryByRole('button', { name: '+ Tạo đề tài', exact: true })).toBeNull()
      expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
      fireEvent.keyDown(screen.getByRole('tab', { name: 'Hồ sơ đăng ký' }), { key: 'Home' })
      await screen.findByText('Chưa có đề tài phù hợp.')
      expect(
        screen.getByRole('tab', { name: 'Đề tài đã công bố' }).getAttribute('aria-selected'),
      ).toBe('true')
      expect((screen.getByRole('textbox', { name: 'Tìm đề tài' }) as HTMLInputElement).value).toBe(
        'Kiểm thử',
      )
    },
  )
  it('giảng viên không giữ chức vụ quản lý không được tạo đề tài trực tiếp', async () => {
    currentUser = { ...user('LECTURER'), activeAppointments: [] }
    render(
      <MemoryRouter initialEntries={['/topics']}>
        <App />
      </MemoryRouter>,
    )
    await screen.findByText('Chưa có đề tài phù hợp.')
    expect(screen.queryByRole('button', { name: '+ Tạo đề tài', exact: true })).toBeNull()
    await userEvent.click(screen.getByRole('tab', { name: 'Hồ sơ đăng ký' }))
    await screen.findByText('Chưa có hồ sơ đăng ký')
    expect(screen.queryByRole('button', { name: '+ Tạo đề tài', exact: true })).toBeNull()
  })
})

describe('Quản lý tài khoản', () => {
  it('mở biểu mẫu tạo tài khoản trong popup', async () => {
    currentUser = user('ADMIN')
    render(
      <MemoryRouter initialEntries={['/admin-users']}>
        <App />
      </MemoryRouter>,
    )

    await userEvent.click(await screen.findByRole('button', { name: 'Tạo tài khoản' }))

    expect(await screen.findByRole('dialog', { name: 'Tạo tài khoản mới' })).toBeTruthy()
    expect(await screen.findByLabelText(/Vai trò/)).toBeTruthy()
  })
})

describe('Cấu hình email', () => {
  it('ordinary lecturers and deans cannot open email configuration', async () => {
    currentUser = user('LECTURER')
    render(
      <MemoryRouter initialEntries={['/email-settings']}>
        <App />
      </MemoryRouter>,
    )
    await screen.findByRole('heading', { name: 'Không có quyền truy cập' })
    expect(screen.queryByRole('button', { name: 'Cấu hình email' })).toBeNull()
    expect(settingScopes).toEqual([])
  })

  it('mẫu công bố điểm chỉ cấu hình gửi tự động cho sinh viên của kết quả', async () => {
    currentUser = user('ADMIN')
    render(
      <MemoryRouter initialEntries={['/email-settings']}>
        <App />
      </MemoryRouter>,
    )
    await userEvent.click(await screen.findByRole('button', { name: /Công bố kết quả điểm/ }))
    expect((screen.getByLabelText('Nội dung thư') as HTMLTextAreaElement).value).toContain(
      '{{finalScore}}',
    )
    expect(screen.queryByLabelText('Gửi đến')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Gửi email' })).toBeNull()
    expect(screen.getByText(/Người nhận là sinh viên có tên trong kết quả đã chốt/)).toBeTruthy()
    await userEvent.click(screen.getByRole('checkbox', { name: 'Đang tắt' }))
    await userEvent.click(screen.getByRole('button', { name: 'Lưu cấu hình' }))
    expect(await screen.findByRole<HTMLInputElement>('checkbox', { name: 'Đang bật' })).toBeTruthy()
    await waitFor(() => expect(requested).toContain('/notification-settings/RESULT_PUBLISHED'))
    expect(sentEmails).toHaveLength(0)
  })

  it.each(['ADMIN'] as RoleCode[])('%s tạo, mở lại, sửa và gửi mẫu email riêng', async (role) => {
    const actor = userEvent.setup()
    currentUser = user(role)
    const firstRender = render(
      <MemoryRouter initialEntries={['/email-settings']}>
        <App />
      </MemoryRouter>,
    )
    await screen.findByLabelText('Nội dung thư')
    await userEvent.click(screen.getByRole('button', { name: 'Tạo mẫu mới' }))
    // This workflow checks persistence and recipients; pasting avoids a timer and
    // a full editor render for every character on slower CI runners.
    await actor.click(screen.getByLabelText('Tên mẫu email'))
    await actor.paste('Nhắc lịch họp')
    await actor.click(screen.getByLabelText('Tiêu đề thư'))
    await actor.paste('Lịch họp tuần này')
    await actor.click(screen.getByLabelText('Nội dung thư'))
    await actor.paste('Mời tham gia họp lúc 8 giờ.')
    await userEvent.click(screen.getByRole('button', { name: 'Tạo mẫu', exact: true }))
    await waitFor(() => expect(savedTemplates).toHaveLength(1))
    await screen.findByRole('button', { name: 'Lưu mẫu' })
    expect(savedTemplates[0].bodyTemplate).toBe('Mời tham gia họp lúc 8 giờ.')
    firstRender.unmount()
    render(
      <MemoryRouter initialEntries={['/email-settings']}>
        <App />
      </MemoryRouter>,
    )
    await userEvent.click(await screen.findByRole('button', { name: /Nhắc lịch họp/ }))
    expect((screen.getByLabelText('Nội dung thư') as HTMLTextAreaElement).value).toBe(
      'Mời tham gia họp lúc 8 giờ.',
    )
    await actor.click(screen.getByLabelText('Tiêu đề thư'))
    await actor.paste(' - cập nhật')
    await userEvent.click(screen.getByRole('button', { name: 'Lưu mẫu' }))
    await waitFor(() => expect(savedTemplates[0].version).toBe(1))
    expect(savedTemplates[0].subjectTemplate).toBe('Lịch họp tuần này - cập nhật')
    await userEvent.selectOptions(screen.getByLabelText('Gửi đến'), 'STUDENT')
    const sendButton = screen.getByRole<HTMLButtonElement>('button', { name: 'Gửi email' })
    await waitFor(() => expect(sendButton.disabled).toBe(false))
    await userEvent.click(sendButton)
    await waitFor(() => expect(requested).toContain('/email-templates/100/send'))
    expect(sentEmails).toEqual([{ recipientRoles: ['STUDENT'], recipientUserIds: [] }])
    expect(requested.some((url) => url.endsWith('/test'))).toBe(false)
  })

  it('bỏ hướng dẫn SMTP, cách sử dụng, biến mẫu và gửi thử tài khoản hiện tại', async () => {
    currentUser = user('ADMIN')
    render(
      <MemoryRouter initialEntries={['/email-settings']}>
        <App />
      </MemoryRouter>,
    )

    expect(await screen.findByLabelText('Nội dung thư')).toBeTruthy()
    expect(screen.queryByRole('heading', { name: 'Cách sử dụng' })).toBeNull()
    expect(screen.queryByText('SMTP được cấu hình bằng biến môi trường')).toBeNull()
    expect(screen.queryByText('Biến có thể dùng')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Gửi thử' })).toBeNull()
    expect(await screen.findByLabelText('Gửi đến')).toBeTruthy()
    expect(screen.queryByText('Nội dung thư (HTML)')).toBeNull()
  })

  it.each([
    ['STUDENT', 'Sinh viên'],
    ['LECTURER', 'Giảng viên'],
  ])('gửi đúng nhóm %s', async (role, label) => {
    currentUser = user('ADMIN')
    render(
      <MemoryRouter initialEntries={['/email-settings']}>
        <App />
      </MemoryRouter>,
    )
    await userEvent.selectOptions(await screen.findByLabelText('Gửi đến'), role)
    await screen.findByText(new RegExp(`Gửi tới toàn bộ .* ${label.toLowerCase()}`))
    const recipientPreview = screen.getByRole('button', { name: 'Xem danh sách người nhận' })
    await userEvent.click(recipientPreview)
    const recipientDialog = screen.getByRole('dialog', { name: 'Danh sách người nhận' })
    await within(recipientDialog).findByText(
      role === 'STUDENT' ? 'an@example.edu.vn' : 'cuong@example.edu.vn',
    )
    expect(
      within(recipientDialog).queryByText(
        role === 'STUDENT' ? 'cuong@example.edu.vn' : 'an@example.edu.vn',
      ),
    ).toBeNull()
    expect(sentEmails).toEqual([])
    await userEvent.click(within(recipientDialog).getByRole('button', { name: 'Đóng' }))
    expect(document.activeElement).toBe(recipientPreview)
    const sendButton = screen.getByRole<HTMLButtonElement>('button', { name: 'Gửi email' })
    await waitFor(() => expect(sendButton.disabled).toBe(false))
    await userEvent.click(sendButton)
    await waitFor(() =>
      expect(sentEmails).toEqual([{ recipientRoles: [role], recipientUserIds: [] }]),
    )
    expect(requested.some((url) => url.endsWith('/test'))).toBe(false)
  })

  it('tìm và gửi đúng người cụ thể, không tự thêm tài khoản đăng nhập', async () => {
    currentUser = user('ADMIN')
    render(
      <MemoryRouter initialEntries={['/email-settings']}>
        <App />
      </MemoryRouter>,
    )
    const search = await screen.findByLabelText('Tìm người nhận')
    await userEvent.type(search, 'binh@example.edu.vn')
    await userEvent.click(await screen.findByRole('checkbox', { name: /Sinh viên Bình/ }))
    const sendButton = screen.getByRole<HTMLButtonElement>('button', { name: 'Gửi email' })
    await waitFor(() => expect(sendButton.disabled).toBe(false))
    await userEvent.click(sendButton)
    await waitFor(() =>
      expect(sentEmails).toEqual([{ recipientRoles: [], recipientUserIds: [11] }]),
    )
    expect(requested.some((url) => url.endsWith('/test'))).toBe(false)
  })

  it('không cho gửi khi chưa chọn người hoặc khi nội dung chưa lưu', async () => {
    currentUser = user('ADMIN')
    render(
      <MemoryRouter initialEntries={['/email-settings']}>
        <App />
      </MemoryRouter>,
    )
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
    render(
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>,
    )
    expect(
      await screen.findByRole(
        'heading',
        {
          level: 1,
          name: heading,
        },
        { timeout: 5000 },
      ),
    ).toBeTruthy()
    expect(document.querySelector('.management-shell')).toBeNull()
  })

  it('điều hướng nút đăng nhập từ trang chủ tới trang đăng nhập riêng', async () => {
    currentUser = null
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>,
    )

    await userEvent.click(await screen.findByRole('button', { name: 'Đăng nhập' }))

    expect(await screen.findByRole('heading', { name: 'Đăng nhập hệ thống' })).toBeTruthy()
    expect(screen.queryByText(/Từ ý tưởng.*đến đề tài hoàn chỉnh/)).toBeNull()
  })

  it('trở về trang chủ khi đăng xuất từ trang được bảo vệ', async () => {
    currentUser = user('ADMIN')
    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <App />
      </MemoryRouter>,
    )

    expect(
      await screen.findByRole('heading', { name: 'Hệ thống quản lý đề tài sinh viên' }),
    ).toBeTruthy()
    window.dispatchEvent(new Event('session-expired'))

    expect(
      await screen.findByRole('heading', { name: /Từ ý tưởng.*đến đề tài hoàn chỉnh/ }),
    ).toBeTruthy()
    expect(screen.queryByRole('heading', { name: 'Đăng nhập hệ thống' })).toBeNull()
  })
})

it('sinh viên không truy cập được màn hình quản lý tổng hợp và công bố điểm', async () => {
  currentUser = user('STUDENT')
  render(
    <MemoryRouter initialEntries={['/evaluation-results']}>
      <App />
    </MemoryRouter>,
  )
  expect(await screen.findByRole('heading', { name: 'Không có quyền truy cập' })).toBeTruthy()
  expect(requested.some((path) => path === '/results' || path.includes('/results/actions'))).toBe(
    false,
  )
})

it.each(['/grade-change-requests', '/project-assignments', '/audit-logs', '/academic-catalog'])(
  'sinh viên không truy cập nghiệp vụ quản lý %s',
  async (path) => {
    currentUser = user('STUDENT')
    render(
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>,
    )
    expect(await screen.findByRole('heading', { name: 'Không có quyền truy cập' })).toBeTruthy()
  },
)

it.each(['ADMIN'] as RoleCode[])(
  '%s gửi thử email đến chính tài khoản với đúng scope sau xác nhận',
  async (role) => {
    currentUser = user(role)
    render(
      <MemoryRouter initialEntries={['/email-settings']}>
        <App />
      </MemoryRouter>,
    )
    const button = await screen.findByRole('button', { name: 'Gửi thử đến tài khoản của tôi' })
    await userEvent.click(button)
    expect(testEmails).toHaveLength(0)
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận' }))
    await waitFor(() =>
      expect(testEmails).toEqual([
        { scopeType: role === 'ADMIN' ? 'SYSTEM' : 'LECTURER', scopeId: role === 'ADMIN' ? 0 : 1 },
      ]),
    )
  },
)
it('Admin chuyển phạm vi email khoa, không gửi thủ công nhầm cấu hình cá nhân', async () => {
  currentUser = user('ADMIN')
  render(
    <MemoryRouter initialEntries={['/email-settings']}>
      <App />
    </MemoryRouter>,
  )
  const picker = await screen.findByLabelText('Phạm vi cấu hình email')
  await screen.findByRole('option', { name: /Khoa Công nghệ thông tin/ })
  await userEvent.selectOptions(picker, 'FACULTY:1')
  await waitFor(() => expect(settingScopes).toContainEqual({ scopeType: 'FACULTY', scopeId: 1 }))
  await screen.findByText(/Cấu hình của khoa áp dụng cho email tự động/)
  expect(screen.queryByRole('button', { name: /^Gửi email$/ })).toBeNull()
})

it('phản hồi cấu hình scope cũ đến muộn không ghi đè scope khoa trong StrictMode', async () => {
  let release!: () => void
  delayedSettings = new Promise((resolve) => {
    release = resolve
  })
  currentUser = user('ADMIN')
  render(
    <StrictMode>
      <MemoryRouter initialEntries={['/email-settings']}>
        <App />
      </MemoryRouter>
    </StrictMode>,
  )
  const picker = await screen.findByLabelText('Phạm vi cấu hình email')
  await screen.findByRole('option', { name: /Khoa Công nghệ thông tin/ })
  await userEvent.selectOptions(picker, 'FACULTY:1')
  const sender = await screen.findByLabelText('Tên người gửi')
  await waitFor(() => expect((sender as HTMLInputElement).value).toBe('Người gửi khoa'))
  await act(async () => release())
  expect((screen.getByLabelText('Tên người gửi') as HTMLInputElement).value).toBe('Người gửi khoa')
})

it('nút xem lần gửi mở hộp thoại đúng email, đóng và trả focus về nút', async () => {
  const row = {
    id: 26,
    eventType: 'TASK_ASSIGNED',
    eventKey: 'task:26',
    recipientName: 'GV A',
    recipientEmail: 'a@example.edu.vn',
    subject: 'Email kiểm thử',
    status: 'PENDING',
    attemptCount: 0,
    createdAt: '2026-10-22',
  }
  logFixture = [[row, { ...row, id: 25 }]]
  currentUser = user('ADMIN')
  render(
    <MemoryRouter initialEntries={['/email-settings']}>
      <App />
    </MemoryRouter>,
  )
  const trigger = await screen.findByRole('button', { name: 'Xem lần gửi #26' })
  await userEvent.click(trigger)
  const dialog = screen.getByRole('dialog', { name: 'Lịch sử gửi email #26' })
  expect(dialog.hasAttribute('open')).toBe(true)
  expect(document.body.style.overflow).toBe('hidden')
  await within(dialog).findByText('Email đang chờ xử lý, chưa có lần gửi.')
  expect(requested).toContain('/email-logs/26')
  await userEvent.click(within(dialog).getByRole('button', { name: 'Đóng chi tiết email' }))
  expect(screen.queryByRole('dialog')).toBeNull()
  expect(document.activeElement).toBe(trigger)
  expect(document.body.style.overflow).toBe('')
  await userEvent.click(screen.getByRole('button', { name: 'Xem lần gửi #25' }))
  const nextDialog = screen.getByRole('dialog', { name: 'Lịch sử gửi email #25' })
  await within(nextDialog).findByText('Email đang chờ xử lý, chưa có lần gửi.')
  expect(requested).toContain('/email-logs/25')
  fireEvent(nextDialog, new Event('cancel', { cancelable: true }))
  expect(screen.queryByRole('dialog')).toBeNull()
})
it('phân trang lịch sử email tại server không làm mất cấu hình đang sửa', async () => {
  const row = {
    id: 1,
    eventType: 'TASK_ASSIGNED',
    eventKey: 'task:1',
    recipientName: 'GV A',
    recipientEmail: 'a@example.edu.vn',
    subject: 'Email trang một',
    status: 'PENDING',
    attemptCount: 0,
    createdAt: '2026-10-22',
  }
  logFixture = [[row], [{ ...row, id: 2, subject: 'Email trang hai' }]]
  currentUser = user('ADMIN')
  render(
    <MemoryRouter initialEntries={['/email-settings']}>
      <App />
    </MemoryRouter>,
  )
  await screen.findByText('Email trang một')
  const subject = await screen.findByLabelText('Tiêu đề thư')
  fireEvent.change(subject, { target: { value: 'Bản nháp chưa lưu' } })
  const settingCount = settingScopes.length
  const pager = screen.getByRole('navigation', { name: 'Phân trang lịch sử email' })
  await userEvent.click(within(pager).getByRole('button', { name: 'Sau' }))
  await screen.findByText('Email trang hai')
  expect(logRequests).toEqual([0, 1])
  expect((screen.getByLabelText('Tiêu đề thư') as HTMLInputElement).value).toBe('Bản nháp chưa lưu')
  expect(settingScopes.length).toBe(settingCount)
})

it.each([
  ['ADMIN', '/project-assignments'],
  ['ADMIN', '/topic-workflow'],
  ['ADMIN', '/evaluator-assignment'],
  ['ADMIN', '/score-sheets'],
  ['LECTURER', '/email-settings'],
  ['LECTURER', '/admin-users'],
  ['LECTURER', '/evaluator-assignment'],
] as const)('%s cannot bypass the route guard at %s', async (role, path) => {
  currentUser = { ...user(role), activeAppointments: [] }
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )
  await screen.findByRole('heading', { name: 'Không có quyền truy cập' })
  expect(requested).not.toContain('/email-templates')
  expect(requested).not.toContain('/evaluation-committees')
  if (role === 'ADMIN') {
    const academic = screen.getByRole('group', { name: 'Giám sát học thuật' })
    expect(within(academic).getByRole('button', { name: 'Tiêu chuẩn đánh giá' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Phân công giảng viên' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Đề tài được giao' })).toBeNull()
  }
})

it('expired dean and ADMIN plus LECTURER without appointment cannot assign evaluators', async () => {
  currentUser = {
    ...user('LECTURER'),
    roles: ['ADMIN', 'LECTURER'],
    activeAppointments: [
      { ...user('LECTURER').activeAppointments[0], endsAt: '2020-02-01T00:00:00Z' },
    ],
  }
  render(
    <MemoryRouter initialEntries={['/evaluator-assignment']}>
      <App />
    </MemoryRouter>,
  )
  await screen.findByRole('heading', { name: 'Không có quyền truy cập' })
  expect(screen.queryByRole('button', { name: 'Phân công giảng viên' })).toBeNull()
})

it('ordinary lecturers can read locked catalogs but cannot configure assessment', async () => {
  currentUser = { ...user('LECTURER'), activeAppointments: [] }
  render(
    <MemoryRouter initialEntries={['/evaluations']}>
      <App />
    </MemoryRouter>,
  )
  await screen.findByText('Bộ tiêu chí đã khóa dùng cho các nhiệm vụ chấm điểm được phân công.')
  expect(screen.queryByRole('button', { name: /Tiếp tục: Tạo/ })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Phân công giảng viên' })).toBeNull()
})
