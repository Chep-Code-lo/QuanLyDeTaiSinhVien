import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom'
import { Sidebar } from '../src/components/Sidebar'
import { Header } from '../src/components/Header'
import { TopicsPage } from '../src/pages/TopicsPage'
import { EvaluationsPage } from '../src/pages/EvaluationsPage'
import { AssessmentSetupWizardPage } from '../src/pages/AssessmentSetupWizardPage'
import { AnnouncementsPage } from '../src/pages/AnnouncementsPage'
import { LecturerAutocomplete } from '../src/components/LecturerAutocomplete'

const mocks = vi.hoisted(() => ({
  user: {
    id: 1,
    fullName: 'Giảng viên thử nghiệm',
    roles: ['LECTURER', 'ADMIN'],
    lecturer: { id: 7 },
    activeAppointments: [{ positionCode: 'DEAN', facultyId: 1, startsAt: '2020-01-01T00:00:00Z' }],
  },
  showError: vi.fn(),
  showSuccess: vi.fn(),
  logout: vi.fn(),
  topics: vi.fn(),
  periods: vi.fn(),
  faculties: vi.fn(),
  departments: vi.fn(),
  createTopic: vi.fn(),
  assignments: vi.fn(),
  rubrics: vi.fn(),
  components: vi.fn(),
  createComponent: vi.fn(),
  sheet: vi.fn(),
  save: vi.fn(),
  notifications: vi.fn(),
  unread: vi.fn(),
  read: vi.fn(),
  announcements: vi.fn(),
  lecturers: vi.fn(),
}))
vi.mock('../src/context/AuthContext', () => ({
  useAuth: () => ({
    user: mocks.user,
    hasRole: (role: string) => mocks.user.roles.includes(role),
    hasAnyRole: (roles: string[]) => roles.some((role) => mocks.user.roles.includes(role)),
    hasAppointment: (position: string) =>
      mocks.user.roles.includes('LECTURER') &&
      mocks.user.activeAppointments.some((a) => a.positionCode === position),
    canAccessAcademicManagement: () => mocks.user.roles.includes('ADMIN'),
    logout: mocks.logout,
  }),
}))
vi.mock('../src/context/ToastContext', () => ({
  useToast: () => ({ showError: mocks.showError, showSuccess: mocks.showSuccess }),
}))
vi.mock('../src/api/topics', () => ({
  topicApi: { list: mocks.topics, createForRegistration: mocks.createTopic },
}))
vi.mock('../src/api/lecturers', () => ({ lecturerApi: { list: mocks.lecturers } }))
vi.mock('../src/api/academic', () => ({
  academicApi: {
    getRegistrationPeriods: mocks.periods,
    getFaculties: mocks.faculties,
    getDepartments: mocks.departments,
  },
}))
vi.mock('../src/api/evaluation', () => ({
  evaluationApi: {
    reviewProgress: async () => ({
      assignedCount: 3,
      submittedCount: 1,
      draftCount: 1,
      notStartedCount: 1,
    }),
    listAssignments: mocks.assignments,
    listRubrics: mocks.rubrics,
    listComponents: mocks.components,
    createComponent: mocks.createComponent,
    getScoreSheet: mocks.sheet,
    saveScoreSheet: mocks.save,
  },
}))
vi.mock('../src/api/notifications', () => ({
  notificationApi: {
    list: mocks.notifications,
    forAnnouncements: async () => [],
    unreadCount: mocks.unread,
    markRead: mocks.read,
  },
}))
vi.mock('../src/api/announcements', () => ({ announcementApi: { list: mocks.announcements } }))

const page = (content: unknown[], totalPages = 1) => ({
  content,
  totalPages,
  totalElements: content.length,
  page: 0,
  size: 100,
})
const rubric = {
  id: 9,
  componentId: 3,
  name: 'Bộ tiêu chí đã lưu',
  status: 'DRAFT',
  versionNo: 1,
  criteria: [
    {
      id: 12,
      name: 'Chất lượng sản phẩm',
      description: 'Đánh giá sản phẩm',
      maxScore: 10,
      sortOrder: 1,
    },
  ],
}
const assignment = {
  id: 2,
  lecturerId: 7,
  componentId: 3,
  rubricId: 9,
  topicCode: 'DT01',
  topicTitle: 'Đề tài A',
  groupName: 'Nhóm A',
  componentName: 'Phản biện',
  status: 'ACTIVE',
  deadlineAt: '2099-01-01T00:00:00Z',
}
const draft = { id: null, evaluationId: 2, status: 'DRAFT', version: 0, totalScore: 0, scores: [] }

function mount(element: React.ReactNode, route = '/') {
  return render(<MemoryRouter initialEntries={[route]}>{element}</MemoryRouter>)
}

function CurrentPath() {
  return <output data-testid="current-path">{useLocation().pathname}</output>
}

function CurrentSearch() {
  return <output data-testid="current-search">{useLocation().search}</output>
}

function SearchNavigation() {
  const navigate = useNavigate()
  return <button onClick={() => navigate('/topics?q=Đề tài khác')}>Tìm từ URL</button>
}

// jsdom does not implement the native dialog methods; browser checks cover the top layer.
beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
})

beforeEach(() => {
  mocks.user.roles = ['LECTURER', 'ADMIN']
  mocks.logout.mockReset()
  mocks.logout.mockResolvedValue(undefined)
  mocks.topics.mockResolvedValue(page([]))
  mocks.periods.mockResolvedValue(page([{ id: 1, name: 'Đợt A' }]))
  mocks.faculties.mockResolvedValue([])
  mocks.departments.mockResolvedValue([])
  mocks.createTopic.mockResolvedValue({})
  mocks.createComponent.mockReset()
  mocks.createComponent.mockResolvedValue({
    id: 4,
    periodId: 1,
    name: 'Thành phần mới',
    componentType: 'REVIEW',
    status: 'DRAFT',
  })
  mocks.assignments.mockResolvedValue(page([assignment]))
  mocks.components.mockResolvedValue([
    { id: 3, periodId: 1, name: 'Thành phần đã lưu', componentType: 'REVIEW', status: 'DRAFT' },
  ])
  mocks.rubrics.mockResolvedValue([rubric])
  mocks.sheet.mockResolvedValue(draft)
  mocks.save.mockResolvedValue({ ...draft, id: 6, version: 1, status: 'SUBMITTED' })
  mocks.notifications.mockResolvedValue(page([]))
  mocks.announcements.mockResolvedValue(page([]))
  mocks.unread.mockResolvedValue(1)
  mocks.lecturers.mockResolvedValue(page([]))
})
afterEach(cleanup)

describe('Hồi quy frontend', () => {
  it('luôn về trang chủ sau khi đăng xuất, kể cả khi API lỗi', async () => {
    mocks.logout.mockRejectedValueOnce(new Error('offline'))
    mount(
      <>
        <Header onMenuClick={vi.fn()} />
        <CurrentPath />
      </>,
      '/dashboard',
    )

    await userEvent.click(await screen.findByRole('button', { name: /Giảng viên thử nghiệm/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Đăng xuất' }))

    await waitFor(() => expect(screen.getByTestId('current-path').textContent).toBe('/'))
    expect(mocks.showError).toHaveBeenCalled()
  })

  it('hiển thị biểu tượng SVG và nút quản lý tài khoản không có href cho admin', () => {
    const { container } = mount(<Sidebar onNavigate={vi.fn()} />)
    expect(container.querySelectorAll('.sidebar__icon svg').length).toBeGreaterThan(10)
    expect(screen.getByRole('button', { name: 'Quản lý tài khoản' }).hasAttribute('href')).toBe(
      false,
    )
  })

  it('nhóm menu theo quy trình và giữ mỗi chức năng ở đúng một nhóm', () => {
    mount(<Sidebar onNavigate={vi.fn()} />)
    const groups = screen.getAllByRole('group')
    expect(groups.map((group) => group.querySelector('.sidebar__label')?.textContent)).toEqual([
      'Menu chính',
      'Cá nhân',
      'Đề tài & tiến độ',
      'Đánh giá & kết quả',
      'Quản trị',
    ])
    expect(
      within(screen.getByRole('group', { name: 'Đề tài & tiến độ' }))
        .getAllByRole('button')
        .map((button) => button.textContent),
    ).toEqual([
      'Đề tài & đăng ký',
      'Đề xuất & Xét duyệt',
      'Nhóm đề tài',
      'Đề tài được giao',
      'Tiến độ & Mốc báo cáo',
    ])
    expect(
      within(screen.getByRole('group', { name: 'Đánh giá & kết quả' }))
        .getAllByRole('button')
        .map((button) => button.textContent),
    ).toEqual([
      'Tiêu chuẩn đánh giá',
      'Phân công giảng viên',
      'Chấm điểm đề tài',
      'Tổng hợp & Công bố điểm',
      'Yêu cầu đổi điểm',
    ])
    const buttons = screen.getAllByRole('button')
    expect(new Set(buttons.map((button) => button.textContent)).size).toBe(buttons.length)
  })

  it('menu sinh viên ẩn nhóm quản trị và vẫn điều hướng, đánh dấu trang hiện tại', async () => {
    mocks.user.roles = ['STUDENT']
    const onNavigate = vi.fn()
    mount(
      <>
        <Sidebar onNavigate={onNavigate} />
        <CurrentPath />
      </>,
      '/results',
    )
    expect(screen.queryByRole('group', { name: 'Quản trị' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Chấm điểm đề tài' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Yêu cầu đổi điểm' })).toBeNull()
    expect(
      screen.getByRole('button', { name: 'Lịch bảo vệ & Kết quả' }).getAttribute('aria-current'),
    ).toBe('page')
    await userEvent.click(screen.getByRole('button', { name: 'Thông báo' }))
    expect(screen.getByTestId('current-path').textContent).toBe('/announcements')
    expect(onNavigate).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: 'Thông báo' }).getAttribute('aria-current')).toBe(
      'page',
    )
  })

  it('gửi từ khóa URL đến backend và chỉ tải trang đang xem', async () => {
    const topic = (id: number, title: string) => ({
      id,
      title,
      code: `DT${id}`,
      departmentName: 'CNTT',
      supervisors: [],
      status: 'PUBLISHED',
      createdAt: '2026-01-01T00:00:00Z',
    })
    mocks.topics.mockClear()
    mocks.topics.mockImplementation(({ q, page: index, size }) =>
      Promise.resolve(
        page(
          q === 'Mục tiêu' && size === 10
            ? [topic(index + 1, index === 0 ? 'Mục tiêu tìm kiếm' : 'Mục tiêu trang sau')]
            : [],
          2,
        ),
      ),
    )
    mount(<TopicsPage />, '/topics?q=Mục tiêu')
    expect(await screen.findByText('Mục tiêu tìm kiếm')).toBeTruthy()
    expect(screen.queryByText('Không khớp')).toBeNull()
    expect(mocks.topics).toHaveBeenCalledTimes(1)
    expect(mocks.topics).toHaveBeenCalledWith(
      expect.objectContaining({ q: 'Mục tiêu', page: 0, size: 10 }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
    expect(await screen.findByText('Mục tiêu trang sau')).toBeTruthy()
    expect(mocks.topics).toHaveBeenCalledWith(
      expect.objectContaining({ q: 'Mục tiêu', page: 1, size: 10 }),
    )
    fireEvent.change(screen.getByRole('textbox', { name: 'Tìm đề tài' }), {
      target: { value: 'Khác' },
    })
    await waitFor(() =>
      expect(mocks.topics).toHaveBeenLastCalledWith(
        expect.objectContaining({ q: 'Khác', page: 0, size: 10 }),
      ),
    )
  })

  it('không cập nhật URL hoặc tra cứu khi bộ gõ còn đang ghép dấu tiếng Việt', async () => {
    mocks.topics.mockClear()
    mount(
      <>
        <TopicsPage />
        <CurrentSearch />
      </>,
      '/topics?tab=published',
    )
    await screen.findByText('Chưa có đề tài phù hợp.')
    const input = screen.getByRole('textbox', { name: 'Tìm đề tài' }) as HTMLInputElement
    input.focus()
    fireEvent.compositionStart(input)
    fireEvent.change(input, { target: { value: 'Do' } })
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 300))
    })
    expect(mocks.topics).toHaveBeenCalledTimes(1)
    expect(screen.getByTestId('current-search').textContent).toBe('?tab=published')
    fireEvent.change(input, { target: { value: 'Đồ án tiếng Việt' } })
    fireEvent.compositionEnd(input, { data: 'Đồ án tiếng Việt' })
    input.setSelectionRange(3, 3)
    await waitFor(() =>
      expect(mocks.topics).toHaveBeenLastCalledWith({
        status: 'PUBLISHED',
        q: 'Đồ án tiếng Việt',
        page: 0,
        size: 10,
      }),
    )
    expect(input.value).toBe('Đồ án tiếng Việt')
    expect(document.activeElement).toBe(input)
    expect(input.selectionStart).toBe(3)
    expect(input.selectionEnd).toBe(3)
    expect(
      new URLSearchParams(screen.getByTestId('current-search').textContent ?? '').get('q'),
    ).toBe('Đồ án tiếng Việt')
    expect(
      new URLSearchParams(screen.getByTestId('current-search').textContent ?? '').get('tab'),
    ).toBe('published')
  })

  it('đồng bộ từ khóa khi điều hướng và hủy từ khóa đang chờ tra cứu', async () => {
    mount(
      <>
        <TopicsPage />
        <SearchNavigation />
        <CurrentSearch />
      </>,
      '/topics?q=Ban đầu',
    )
    await screen.findByText('Chưa có đề tài phù hợp.')
    const input = screen.getByRole('textbox', { name: 'Tìm đề tài' }) as HTMLInputElement
    fireEvent.change(input, { target: { value: 'Bản nháp chưa tìm' } })
    await userEvent.click(screen.getByRole('button', { name: 'Tìm từ URL' }))
    await waitFor(() => expect(input.value).toBe('Đề tài khác'))
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 450))
    })
    expect(
      new URLSearchParams(screen.getByTestId('current-search').textContent ?? '').get('q'),
    ).toBe('Đề tài khác')
    expect(mocks.topics).toHaveBeenLastCalledWith({
      status: 'PUBLISHED',
      q: 'Đề tài khác',
      page: 0,
      size: 10,
    })
  })

  it('cho phép thử tải lại sau khi tải đề tài thất bại', async () => {
    mocks.topics.mockRejectedValueOnce(new Error('offline'))
    mount(<TopicsPage />)
    await userEvent.click(await screen.findByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Chưa có đề tài phù hợp.')).toBeTruthy()
  })

  it('mở nội dung, mục tiêu và yêu cầu đề tài từ danh sách', async () => {
    mocks.topics.mockResolvedValue(
      page([
        {
          id: 1,
          title: 'Đề tài A',
          code: 'DT1',
          departmentName: 'CNTT',
          supervisors: [],
          status: 'PUBLISHED',
          createdAt: '2026-01-01T00:00:00Z',
          description: 'Mô tả chi tiết',
          objectives: 'Mục tiêu thực tế',
          expectedOutcomes: 'Sản phẩm hoàn chỉnh',
          prerequisites: 'Biết lập trình',
        },
      ]),
    )
    mount(<TopicsPage />)
    await userEvent.click(await screen.findByRole('button', { name: 'Xem chi tiết' }))
    expect(screen.getByText('Mục tiêu thực tế')).toBeTruthy()
    expect(screen.getByText('Biết lập trình')).toBeTruthy()
    await userEvent.click(screen.getByRole('button', { name: 'Thu gọn' }))
    expect(screen.queryByText('Mô tả chi tiết')).toBeNull()
  })

  it('hiển thị lựa chọn Bộ môn bắt buộc khi tạo đề tài', async () => {
    mocks.periods.mockResolvedValue(
      page([
        {
          id: 1,
          name: 'Đợt đang mở',
          facultyId: 1,
          status: 'ACTIVE',
          proposalStartAt: '2000-01-01T00:00:00Z',
          proposalEndAt: '2099-12-31T00:00:00Z',
          registrationStartAt: '2000-01-01T00:00:00Z',
          registrationEndAt: '2099-12-31T00:00:00Z',
        },
      ]),
    )
    mocks.departments.mockResolvedValue([
      { id: 3, facultyId: 1, name: 'Công nghệ phần mềm', active: true },
    ])
    mocks.lecturers.mockResolvedValue(
      page([
        {
          id: 7,
          fullName: 'Giảng viên A',
          lecturerCode: 'GV07',
          departmentName: 'Công nghệ phần mềm',
        },
      ]),
    )

    mount(<TopicsPage />)
    await userEvent.click(screen.getByRole('button', { name: '+ Tạo đề tài' }))

    const department = await screen.findByLabelText(/Bộ môn/)
    expect((department as HTMLSelectElement).value).toBe('')
    expect(screen.getByRole('option', { name: 'Chọn bộ môn' })).toBeTruthy()
  })

  it('khôi phục đúng giảng viên ở trang sau, không nhầm userId với lecturerId', async () => {
    mocks.lecturers.mockImplementation(({ lecturerId }) =>
      Promise.resolve(
        page(
          lecturerId === 7
            ? [{ id: 7, userId: 12, fullName: 'Giảng viên được chọn', lecturerCode: 'GV07' }]
            : [{ id: 1, userId: 7, fullName: 'Giảng viên không được chọn', lecturerCode: 'GV01' }],
          1,
        ),
      ),
    )
    mount(<LecturerAutocomplete value={7} onChange={vi.fn()} />)
    expect(await screen.findByText('Giảng viên được chọn')).toBeTruthy()
    expect(screen.queryByText('Giảng viên không được chọn')).toBeNull()
    expect(mocks.lecturers).toHaveBeenCalledWith(
      expect.objectContaining({ lecturerId: 7, page: 0, size: 1 }),
    )
  })

  it('mở lại thành phần và bộ tiêu chí đã lưu mà không tạo mới', async () => {
    mount(<AssessmentSetupWizardPage />)
    await userEvent.selectOptions(await screen.findByLabelText('Tiếp tục cấu hình đã lưu'), '3')
    expect(await screen.findByDisplayValue('Chất lượng sản phẩm')).toBeTruthy()
    expect(screen.getByRole('button', { name: /Tiếp tục: Khóa bộ tiêu chí/ })).toBeTruthy()
  })

  it('không cấu hình số người chấm khi tạo tiêu chuẩn đánh giá', async () => {
    mocks.components.mockResolvedValue([])
    mount(<AssessmentSetupWizardPage />)

    expect(await screen.findByLabelText(/Tên thành phần/)).toBeTruthy()
    expect(screen.queryByLabelText(/Số người chấm/)).toBeNull()
    fireEvent.change(screen.getByLabelText(/Tên thành phần/), { target: { value: 'Đánh giá mới' } })
    fireEvent.submit(
      screen.getByRole('button', { name: 'Tiếp tục: Tạo bộ tiêu chí' }).closest('form')!,
    )

    await waitFor(() =>
      expect(mocks.createComponent).toHaveBeenCalledWith(
        expect.objectContaining({
          periodId: 1,
          name: 'Đánh giá mới',
          componentType: 'REVIEW',
        }),
      ),
    )
    expect(mocks.createComponent.mock.calls[0][0]).not.toHaveProperty('expectedEvaluators')
  })

  it('kiểm tra giới hạn điểm và nộp phiếu đúng phiên bản sau xác nhận', async () => {
    mount(<EvaluationsPage />)
    const input = await screen.findByLabelText('Chất lượng sản phẩm (tối đa 10 điểm)')
    fireEvent.change(input, { target: { value: '11' } })
    expect(
      (screen.getByRole('button', { name: 'Nộp phiếu chấm' }) as HTMLButtonElement).disabled,
    ).toBe(true)
    fireEvent.change(input, { target: { value: '8.5' } })
    await userEvent.click(screen.getByRole('button', { name: 'Nộp phiếu chấm' }))
    expect(mocks.save).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Nộp phiếu', exact: true }))
    await waitFor(() =>
      expect(mocks.save).toHaveBeenCalledWith(2, {
        version: 0,
        overallComment: '',
        submit: true,
        scores: [{ criterionId: 12, score: 8.5, comment: '' }],
      }),
    )
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Nộp phiếu chấm' })).toBeNull())
  })

  it('nộp phản biện xác nhận đã thông báo quản lý và chuyển phiếu sang chỉ xem', async () => {
    mocks.assignments.mockResolvedValue(page([{ ...assignment, evaluatorRole: 'REVIEWER' }]))
    mount(<EvaluationsPage />)
    fireEvent.change(await screen.findByLabelText('Chất lượng sản phẩm (tối đa 10 điểm)'), {
      target: { value: '8' },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Nộp phiếu chấm' }))
    await userEvent.click(screen.getByRole('button', { name: 'Nộp phiếu', exact: true }))
    await waitFor(() =>
      expect(mocks.showSuccess).toHaveBeenCalledWith(
        'Đã nộp phiếu điểm phản biện. Hệ thống đã thông báo cho người quản lý.',
      ),
    )
    expect(
      screen.getByLabelText('Chất lượng sản phẩm (tối đa 10 điểm)').closest('fieldset')?.disabled,
    ).toBe(true)
  })

  it('phiếu đã nộp chỉ cho xem, không cho sửa hoặc nộp lại', async () => {
    mocks.sheet.mockResolvedValue({
      ...draft,
      id: 6,
      status: 'SUBMITTED',
      scores: [{ criterionId: 12, score: 9 }],
    })
    mount(<EvaluationsPage />)
    const input = await screen.findByLabelText('Chất lượng sản phẩm (tối đa 10 điểm)')
    expect(input.closest('fieldset')?.disabled).toBe(true)
    expect(screen.queryByRole('button', { name: 'Nộp phiếu chấm' })).toBeNull()
  })

  it('lưu nháp rồi nộp bằng phiên bản mới nhất từ máy chủ', async () => {
    mocks.save.mockResolvedValueOnce({ ...draft, id: 6, version: 4, status: 'DRAFT' })
    mount(<EvaluationsPage />)
    fireEvent.change(await screen.findByLabelText('Chất lượng sản phẩm (tối đa 10 điểm)'), {
      target: { value: '7' },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    await waitFor(() => expect(mocks.showSuccess).toHaveBeenCalledWith('Đã lưu phiếu chấm nháp.'))
    await userEvent.click(screen.getByRole('button', { name: 'Nộp phiếu chấm' }))
    await userEvent.click(screen.getByRole('button', { name: 'Nộp phiếu', exact: true }))
    await waitFor(() =>
      expect(mocks.save).toHaveBeenLastCalledWith(
        2,
        expect.objectContaining({ version: 4, submit: true }),
      ),
    )
  })

  it('cập nhật số thông báo trên header ngay khi đánh dấu đã đọc', async () => {
    const notification = {
      id: 4,
      title: 'Thông báo kiểm tra',
      content: 'Nội dung',
      createdAt: '2026-01-01T00:00:00Z',
      readAt: null,
    }
    mocks.notifications.mockResolvedValue(page([notification]))
    mocks.read.mockResolvedValue({ ...notification, readAt: '2026-01-02T00:00:00Z' })
    mount(
      <>
        <Header onMenuClick={vi.fn()} />
        <AnnouncementsPage />
      </>,
    )
    await waitFor(() => expect(mocks.unread).toHaveBeenCalledTimes(1))
    mocks.unread.mockResolvedValue(0)
    await userEvent.click(screen.getByRole('tab', { name: 'Thông báo cá nhân' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Thông báo kiểm tra' }))
    await waitFor(() => expect(mocks.unread).toHaveBeenCalledTimes(2))
    await waitFor(() =>
      expect(
        screen
          .getByRole('button', { name: 'Thông báo', exact: true })
          .querySelector('.notification-button__dot'),
      ).toBeNull(),
    )
  })

  it('chuông mở danh sách tiêu đề, chọn thông báo mở popup nội dung và đánh dấu đã đọc', async () => {
    const notification = {
      id: 41,
      title: 'Lịch bảo vệ mới',
      content: 'Nội dung đầy đủ\nDòng thứ hai',
      targetPath: '/progress',
      createdAt: '2026-01-01T00:00:00Z',
    }
    mocks.notifications.mockResolvedValue(page([notification]))
    mocks.read.mockResolvedValue({ ...notification, readAt: '2026-01-02T00:00:00Z' })
    mount(<Header onMenuClick={vi.fn()} />)
    await userEvent.click(await screen.findByRole('button', { name: 'Có thông báo mới' }))
    expect(await screen.findByRole('button', { name: /Lịch bảo vệ mới/ })).toBeTruthy()
    expect(screen.queryByText(/Nội dung đầy đủ/)).toBeNull()
    expect(mocks.read).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: /Lịch bảo vệ mới/ }))
    expect(await screen.findByRole('dialog', { name: 'Lịch bảo vệ mới' })).toBeTruthy()
    expect(screen.getByText(/Nội dung đầy đủ/)).toBeTruthy()
    expect(screen.queryByText('Mở liên quan')).toBeNull()
    expect(screen.queryByRole('dialog', { name: 'Danh sách thông báo' })).toBeNull()
    await waitFor(() => expect(mocks.read).toHaveBeenCalledWith(41))
    await userEvent.click(screen.getByRole('button', { name: 'Đóng thông báo' }))
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('không gọi đánh dấu đã đọc lần nữa khi xem thông báo đã đọc', async () => {
    mocks.notifications.mockResolvedValue(
      page([
        {
          id: 42,
          title: 'Đã đọc',
          content: 'Chi tiết',
          readAt: '2026-01-02T00:00:00Z',
          createdAt: '2026-01-01T00:00:00Z',
        },
      ]),
    )
    mount(<Header onMenuClick={vi.fn()} />)
    await userEvent.click(await screen.findByRole('button', { name: 'Có thông báo mới' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Đã đọc', exact: true }))
    expect(await screen.findByRole('dialog', { name: 'Đã đọc' })).toBeTruthy()
    expect(mocks.read).not.toHaveBeenCalled()
  })

  it('đồng bộ trạng thái đã đọc từ chuông sang trang thông báo đang mở', async () => {
    const notification = {
      id: 43,
      title: 'Đồng bộ trạng thái',
      content: 'Nội dung',
      createdAt: '2026-01-01T00:00:00Z',
    }
    mocks.notifications.mockResolvedValue(page([notification]))
    mocks.read.mockResolvedValue({ ...notification, readAt: '2026-01-02T00:00:00Z' })
    mount(
      <>
        <Header onMenuClick={vi.fn()} />
        <AnnouncementsPage />
      </>,
    )
    await userEvent.click(screen.getByRole('tab', { name: 'Thông báo cá nhân' }))
    const rowTitle = await screen.findByRole('button', { name: 'Đồng bộ trạng thái' })
    expect(rowTitle.closest('tr')?.classList.contains('announcement-table__row--unread')).toBe(true)
    await userEvent.click(await screen.findByRole('button', { name: 'Có thông báo mới' }))
    await userEvent.click((await screen.findAllByRole('button', { name: 'Đồng bộ trạng thái' }))[0])
    await waitFor(() =>
      expect(rowTitle.closest('tr')?.classList.contains('announcement-table__row--unread')).toBe(
        false,
      ),
    )
  })

  it('cho thử tải lại danh sách khi lỗi và hiển thị trạng thái trống', async () => {
    mocks.notifications.mockRejectedValueOnce(new Error('Không tải được thông báo'))
    mount(<Header onMenuClick={vi.fn()} />)
    await userEvent.click(await screen.findByRole('button', { name: 'Có thông báo mới' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Chưa có thông báo.')).toBeTruthy()
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).toBeNull()
  })
})
