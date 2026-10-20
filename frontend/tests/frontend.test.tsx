import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { Sidebar } from '../src/components/Sidebar'
import { Header } from '../src/components/Header'
import { TopicsPage } from '../src/pages/TopicsPage'
import { EvaluationsPage } from '../src/pages/EvaluationsPage'
import { AssessmentSetupWizardPage } from '../src/pages/AssessmentSetupWizardPage'
import { AnnouncementsPage } from '../src/pages/AnnouncementsPage'
import { LecturerAutocomplete } from '../src/components/LecturerAutocomplete'

const mocks = vi.hoisted(() => ({
  user: { id: 1, fullName: 'Giảng viên thử nghiệm', roles: ['LECTURER', 'ADMIN'], lecturer: { id: 7 }, activeAppointments: [] },
  showError: vi.fn(), showSuccess: vi.fn(),
  topics: vi.fn(), periods: vi.fn(), faculties: vi.fn(),
  assignments: vi.fn(), rubrics: vi.fn(), components: vi.fn(), sheet: vi.fn(), save: vi.fn(),
  notifications: vi.fn(), unread: vi.fn(), read: vi.fn(), announcements: vi.fn(),
  lecturers: vi.fn(),
}))
vi.mock('../src/context/AuthContext', () => ({ useAuth: () => ({
  user: mocks.user, hasRole: (role: string) => mocks.user.roles.includes(role),
  hasAnyRole: (roles: string[]) => roles.some(role => mocks.user.roles.includes(role)),
  hasAppointment: () => false, canAccessAcademicManagement: () => true, logout: vi.fn(),
}) }))
vi.mock('../src/context/ToastContext', () => ({ useToast: () => ({ showError: mocks.showError, showSuccess: mocks.showSuccess }) }))
vi.mock('../src/api/topics', () => ({ topicApi: { list: mocks.topics } }))
vi.mock('../src/api/lecturers', () => ({ lecturerApi: { list: mocks.lecturers } }))
vi.mock('../src/api/academic', () => ({ academicApi: { getRegistrationPeriods: mocks.periods, getFaculties: mocks.faculties } }))
vi.mock('../src/api/evaluation', () => ({ evaluationApi: {
  listAssignments: mocks.assignments, listRubrics: mocks.rubrics, listComponents: mocks.components,
  getScoreSheet: mocks.sheet, saveScoreSheet: mocks.save,
} }))
vi.mock('../src/api/notifications', () => ({ notificationApi: { list: mocks.notifications, unreadCount: mocks.unread, markRead: mocks.read } }))
vi.mock('../src/api/announcements', () => ({ announcementApi: { list: mocks.announcements } }))

const page = (content: unknown[], totalPages = 1) => ({ content, totalPages, totalElements: content.length, page: 0, size: 100 })
const rubric = { id: 9, componentId: 3, name: 'Bộ tiêu chí đã lưu', status: 'DRAFT', versionNo: 1,
  criteria: [{ id: 12, name: 'Chất lượng sản phẩm', description: 'Đánh giá sản phẩm', maxScore: 10, sortOrder: 1 }] }
const assignment = { id: 2, lecturerId: 7, componentId: 3, rubricId: 9, topicCode: 'DT01', topicTitle: 'Đề tài A', groupName: 'Nhóm A', componentName: 'Phản biện', status: 'ACTIVE', deadlineAt: '2099-01-01T00:00:00Z' }
const draft = { id: null, evaluationId: 2, status: 'DRAFT', version: 0, totalScore: 0, scores: [] }

function mount(element: React.ReactNode, route = '/') {
  return render(<MemoryRouter initialEntries={[route]}>{element}</MemoryRouter>)
}

beforeEach(() => {
  mocks.topics.mockResolvedValue(page([]))
  mocks.periods.mockResolvedValue(page([{ id: 1, name: 'Đợt A' }]))
  mocks.faculties.mockResolvedValue([])
  mocks.assignments.mockResolvedValue(page([assignment]))
  mocks.components.mockResolvedValue([{ id: 3, periodId: 1, name: 'Thành phần đã lưu', componentType: 'REVIEW', expectedEvaluators: 1, status: 'DRAFT' }])
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
  it('hiển thị biểu tượng SVG và đường dẫn tạo tài khoản cho admin', () => {
    const { container } = mount(<Sidebar onNavigate={vi.fn()} />)
    expect(container.querySelectorAll('.sidebar__icon svg').length).toBeGreaterThan(10)
    expect(screen.getByRole('link', { name: 'Tạo tài khoản' }).getAttribute('href')).toBe('/admin-users/new')
  })

  it('tìm từ URL trên tất cả các trang đề tài, kể cả dữ liệu trang sau', async () => {
    const topic = (id: number, title: string) => ({ id, title, code: `DT${id}`, departmentName: 'CNTT', supervisors: [], status: 'PUBLISHED', createdAt: '2026-01-01T00:00:00Z' })
    mocks.topics.mockImplementation(({ page: index }) => Promise.resolve(page(index === 0 ? [topic(1, 'Không khớp')] : [topic(2, 'Mục tiêu tìm kiếm')], 2)))
    mount(<TopicsPage />, '/topics?q=Mục tiêu')
    expect(await screen.findByText('Mục tiêu tìm kiếm')).toBeTruthy()
    expect(screen.queryByText('Không khớp')).toBeNull()
    expect(mocks.topics).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }))
  })

  it('cho phép thử tải lại sau khi tải đề tài thất bại', async () => {
    mocks.topics.mockRejectedValueOnce(new Error('offline'))
    mount(<TopicsPage />)
    await userEvent.click(await screen.findByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Chưa có đề tài phù hợp.')).toBeTruthy()
  })

  it('mở nội dung, mục tiêu và yêu cầu đề tài từ danh sách', async () => {
    mocks.topics.mockResolvedValue(page([{ id: 1, title: 'Đề tài A', code: 'DT1', departmentName: 'CNTT', supervisors: [], status: 'PUBLISHED', createdAt: '2026-01-01T00:00:00Z', description: 'Mô tả chi tiết', objectives: 'Mục tiêu thực tế', expectedOutcomes: 'Sản phẩm hoàn chỉnh', prerequisites: 'Biết lập trình' }]))
    mount(<TopicsPage />)
    await userEvent.click(await screen.findByRole('button', { name: 'Xem chi tiết' }))
    expect(screen.getByText('Mục tiêu thực tế')).toBeTruthy()
    expect(screen.getByText('Biết lập trình')).toBeTruthy()
    await userEvent.click(screen.getByRole('button', { name: 'Thu gọn' }))
    expect(screen.queryByText('Mô tả chi tiết')).toBeNull()
  })

  it('khôi phục đúng giảng viên ở trang sau, không nhầm userId với lecturerId', async () => {
    mocks.lecturers.mockImplementation(({ page: index }) => Promise.resolve(page(index === 0
      ? [{ id: 1, userId: 7, fullName: 'Giảng viên không được chọn', lecturerCode: 'GV01' }]
      : [{ id: 7, userId: 12, fullName: 'Giảng viên được chọn', lecturerCode: 'GV07' }], 2)))
    mount(<LecturerAutocomplete value={7} onChange={vi.fn()} />)
    expect(await screen.findByText('Giảng viên được chọn')).toBeTruthy()
    expect(screen.queryByText('Giảng viên không được chọn')).toBeNull()
  })

  it('mở lại thành phần và bộ tiêu chí đã lưu mà không tạo mới', async () => {
    mount(<AssessmentSetupWizardPage />)
    await userEvent.selectOptions(await screen.findByLabelText('Tiếp tục cấu hình đã lưu'), '3')
    expect(await screen.findByDisplayValue('Chất lượng sản phẩm')).toBeTruthy()
    expect(screen.getByRole('button', { name: /Tiếp tục: Khóa bộ tiêu chí/ })).toBeTruthy()
  })

  it('kiểm tra giới hạn điểm và nộp phiếu đúng phiên bản sau xác nhận', async () => {
    mount(<EvaluationsPage />)
    const input = await screen.findByLabelText('Chất lượng sản phẩm (tối đa 10 điểm)')
    fireEvent.change(input, { target: { value: '11' } })
    expect((screen.getByRole('button', { name: 'Nộp phiếu chấm' }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(input, { target: { value: '8.5' } })
    await userEvent.click(screen.getByRole('button', { name: 'Nộp phiếu chấm' }))
    expect(mocks.save).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Nộp phiếu', exact: true }))
    await waitFor(() => expect(mocks.save).toHaveBeenCalledWith(2, { version: 0, overallComment: '', submit: true, scores: [{ criterionId: 12, score: 8.5, comment: '' }] }))
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Nộp phiếu chấm' })).toBeNull())
  })

  it('phiếu đã nộp chỉ cho xem, không cho sửa hoặc nộp lại', async () => {
    mocks.sheet.mockResolvedValue({ ...draft, id: 6, status: 'SUBMITTED', scores: [{ criterionId: 12, score: 9 }] })
    mount(<EvaluationsPage />)
    const input = await screen.findByLabelText('Chất lượng sản phẩm (tối đa 10 điểm)')
    expect(input.closest('fieldset')?.disabled).toBe(true)
    expect(screen.queryByRole('button', { name: 'Nộp phiếu chấm' })).toBeNull()
  })

  it('lưu nháp rồi nộp bằng phiên bản mới nhất từ máy chủ', async () => {
    mocks.save.mockResolvedValueOnce({ ...draft, id: 6, version: 4, status: 'DRAFT' })
    mount(<EvaluationsPage />)
    fireEvent.change(await screen.findByLabelText('Chất lượng sản phẩm (tối đa 10 điểm)'), { target: { value: '7' } })
    await userEvent.click(screen.getByRole('button', { name: 'Lưu nháp' }))
    await waitFor(() => expect(mocks.showSuccess).toHaveBeenCalledWith('Đã lưu phiếu chấm nháp.'))
    await userEvent.click(screen.getByRole('button', { name: 'Nộp phiếu chấm' }))
    await userEvent.click(screen.getByRole('button', { name: 'Nộp phiếu', exact: true }))
    await waitFor(() => expect(mocks.save).toHaveBeenLastCalledWith(2, expect.objectContaining({ version: 4, submit: true })))
  })

  it('cập nhật số thông báo trên header ngay khi đánh dấu đã đọc', async () => {
    const notification = { id: 4, title: 'Thông báo kiểm tra', content: 'Nội dung', createdAt: '2026-01-01T00:00:00Z', readAt: null }
    mocks.notifications.mockResolvedValue(page([notification]))
    mocks.read.mockResolvedValue({ ...notification, readAt: '2026-01-02T00:00:00Z' })
    mount(<><Header onMenuClick={vi.fn()} /><AnnouncementsPage /></>)
    await waitFor(() => expect(mocks.unread).toHaveBeenCalledTimes(1))
    mocks.unread.mockResolvedValue(0)
    await userEvent.click(screen.getByRole('tab', { name: 'Thông báo cá nhân' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Thông báo kiểm tra' }))
    await waitFor(() => expect(mocks.unread).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(screen.getByRole('link', { name: 'Thông báo mới' }).textContent).toBe(''))
  })
})
