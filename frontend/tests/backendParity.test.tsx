import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { CreateAccountForm } from '../src/components/CreateAccountForm'
import { UserManagementPage } from '../src/pages/UserManagementPage'
import { AcademicCatalogPage } from '../src/pages/AcademicCatalogPage'
import { AuditLogsPage } from '../src/pages/AuditLogsPage'
import { GradeChangesPage } from '../src/pages/GradeChangesPage'
import { ProjectAssignmentsPage } from '../src/pages/ProjectAssignmentsPage'
import { TopicWorkflowPage } from '../src/pages/TopicWorkflowPage'
import { TopicProposalForm } from '../src/components/TopicProposalForm'
import { AccountAccessForm } from '../src/components/AccountAccessForm'
import { GradeChangeRequestForm } from '../src/components/GradeChangeRequestForm'
import { EmailDeliveryDetail } from '../src/components/EmailDeliveryDetail'
import { EmailScopePicker } from '../src/components/EmailScopePicker'
import type { CurrentUser, UserResponse } from '../src/types/domain'

const mocks = vi.hoisted(() => ({
  user: null as CurrentUser | null,
  success: vi.fn(),
  error: vi.fn(),
  faculties: vi.fn(),
  departments: vi.fn(),
  semesters: vi.fn(),
  createSemester: vi.fn(),
  createDepartment: vi.fn(),
  createFaculty: vi.fn(),
  updateFaculty: vi.fn(),
  updateDepartment: vi.fn(),
  periods: vi.fn(),
  projects: vi.fn(),
  project: vi.fn(),
  status: vi.fn(),
  audit: vi.fn(),
  topics: vi.fn(),
  topic: vi.fn(),
  createTopic: vi.fn(),
  updateTopic: vi.fn(),
  reviewTopic: vi.fn(),
  publishTopic: vi.fn(),
  withdrawTopic: vi.fn(),
  supervisors: vi.fn(),
  gradeList: vi.fn(),
  grade: vi.fn(),
  gradeRequest: vi.fn(),
  gradeReview: vi.fn(),
  roles: vi.fn(),
  userStatus: vi.fn(),
  log: vi.fn(),
  users: vi.fn(),
  getUser: vi.fn(),
  createUser: vi.fn(),
}))
vi.mock('../src/api/academic', () => ({
  academicApi: {
    getFaculties: mocks.faculties,
    getDepartments: mocks.departments,
    getSemesters: mocks.semesters,
    createSemester: mocks.createSemester,
    createDepartment: mocks.createDepartment,
    createFaculty: mocks.createFaculty,
    updateFaculty: mocks.updateFaculty,
    updateDepartment: mocks.updateDepartment,
    getRegistrationPeriods: mocks.periods,
    getProjectAssignments: mocks.projects,
    getProject: mocks.project,
    updateProjectStatus: mocks.status,
  },
}))
vi.mock('../src/api/topics', () => ({
  topicApi: {
    list: mocks.topics,
    get: mocks.topic,
    create: mocks.createTopic,
    update: mocks.updateTopic,
    review: mocks.reviewTopic,
    publish: mocks.publishTopic,
    withdraw: mocks.withdrawTopic,
    assignSupervisors: mocks.supervisors,
  },
}))
vi.mock('../src/api/evaluation', () => ({
  evaluationApi: {
    listGradeChanges: mocks.gradeList,
    getGradeChange: mocks.grade,
    requestGradeChange: mocks.gradeRequest,
    reviewGradeChange: mocks.gradeReview,
  },
}))
vi.mock('../src/api/admin', () => ({
  adminApi: {
    updateRoles: mocks.roles,
    updateStatus: mocks.userStatus,
    listAuditLogs: mocks.audit,
    listUsers: mocks.users,
    getUser: mocks.getUser,
    createUser: mocks.createUser,
  },
}))
vi.mock('../src/api/emailNotifications', () => ({ emailNotificationsApi: { getLog: mocks.log } }))
vi.mock('../src/api/lecturers', () => ({ lecturerApi: { list: async () => page([]) } }))
vi.mock('../src/context/AuthContext', () => ({ useAuth: () => ({ user: mocks.user }) }))
vi.mock('../src/context/ToastContext', () => ({
  useToast: () => ({ showSuccess: mocks.success, showError: mocks.error }),
}))
const page = (content: unknown[], totalPages = 1) => ({
  content,
  totalPages,
  totalElements: content.length,
  page: 0,
  size: 10,
})
const topic = {
  id: 1,
  departmentId: 2,
  facultyId: 1,
  code: 'DT01',
  title: 'Đề tài A',
  proposedBy: 10,
  version: 3,
  status: 'PENDING',
  supervisors: [],
  description: 'Mô tả',
  objectives: 'Mục tiêu',
  expectedOutcomes: 'Kết quả',
  visibilityType: 'OPEN',
  periodId: 1,
}
const project = {
  id: 1,
  topicId: 1,
  topicCode: 'DT01',
  groupName: 'Nhóm A',
  status: 'IN_PROGRESS',
  version: 3,
}
const request = {
  id: 4,
  sheetId: 5,
  assignmentId: 1,
  evaluationId: 7,
  requestedBy: 10,
  requestedByName: 'GV A',
  reason: 'Cần sửa nhận xét',
  status: 'PENDING',
  createdAt: '2026-10-22T01:00:00Z',
}
const lecturer = (): CurrentUser => ({
  id: 10,
  username: 'gv@example.edu.vn',
  fullName: 'GV A',
  roles: ['LECTURER'],
  lecturer: { id: 1, lecturerCode: 'GV01', departmentId: 2 },
  activeAppointments: [],
})
beforeEach(() => {
  for (const value of Object.values(mocks))
    if (typeof value === 'function' && 'mockReset' in value) value.mockReset()
  mocks.user = { ...lecturer(), roles: ['ADMIN', 'LECTURER'] }
  mocks.faculties.mockResolvedValue([
    { id: 1, code: 'CNTT', name: 'Công nghệ thông tin', active: true },
  ])
  mocks.departments.mockResolvedValue([
    { id: 2, facultyId: 1, code: 'PM', name: 'Phần mềm', active: true },
  ])
  mocks.semesters.mockResolvedValue([])
  mocks.periods.mockResolvedValue(page([{ id: 1, name: 'Đợt A' }]))
  mocks.projects.mockResolvedValue(page([project]))
  mocks.project.mockResolvedValue(project)
  mocks.topics.mockResolvedValue(page([topic]))
  mocks.topic.mockResolvedValue(topic)
  mocks.gradeList.mockResolvedValue(page([request]))
  mocks.grade.mockResolvedValue(request)
  mocks.gradeRequest.mockResolvedValue(4)
  mocks.gradeReview.mockResolvedValue(undefined)
  mocks.audit.mockResolvedValue(page([]))
  mocks.users.mockResolvedValue(page([]))
})
afterEach(cleanup)
async function confirm() {
  await userEvent.click(
    within(screen.getByRole('dialog')).getByRole('button', { name: 'Xác nhận' }),
  )
}
it('học kỳ gửi đúng academicYear, semesterNo và ngày theo DTO backend', async () => {
  render(<AcademicCatalogPage />)
  await userEvent.click(await screen.findByRole('button', { name: 'Thêm học kỳ' }))
  fireEvent.change(screen.getByLabelText('Năm học'), { target: { value: '2026-2027' } })
  fireEvent.change(screen.getByLabelText('Số học kỳ'), { target: { value: '2' } })
  fireEvent.change(screen.getByLabelText('Ngày bắt đầu'), { target: { value: '2027-01-01' } })
  fireEvent.change(screen.getByLabelText('Ngày kết thúc'), { target: { value: '2027-05-01' } })
  await userEvent.click(screen.getByRole('button', { name: 'Lưu danh mục' }))
  await waitFor(() =>
    expect(mocks.createSemester).toHaveBeenCalledExactlyOnceWith({
      academicYear: '2026-2027',
      semesterNo: 2,
      startsOn: '2027-01-01',
      endsOn: '2027-05-01',
    }),
  )
})
it('trưởng bộ môn chỉ sửa đúng bộ môn và không tạo khoa, học kỳ hoặc bộ môn', async () => {
  mocks.user = {
    ...lecturer(),
    activeAppointments: [
      {
        id: 1,
        lecturerId: 1,
        lecturerCode: 'GV01',
        lecturerName: 'GV A',
        positionCode: 'DEPARTMENT_HEAD',
        facultyId: 1,
        departmentId: 2,
        startsAt: '2026-01-01',
      },
    ],
  }
  mocks.departments.mockResolvedValue([
    { id: 2, facultyId: 1, code: 'PM', name: 'Phần mềm' },
    { id: 3, facultyId: 1, code: 'HT', name: 'Hệ thống' },
  ])
  render(<AcademicCatalogPage />)
  await screen.findByRole('button', { name: 'Sửa bộ môn PM' })
  expect(screen.queryByRole('button', { name: 'Sửa bộ môn HT' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Thêm khoa' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Thêm học kỳ' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Thêm bộ môn' })).toBeNull()
})
it('cập nhật khoa giữ nguyên mã và gửi name/active', async () => {
  render(<AcademicCatalogPage />)
  await userEvent.click(await screen.findByRole('button', { name: 'Sửa khoa CNTT' }))
  fireEvent.change(screen.getByLabelText('Tên danh mục'), { target: { value: 'Khoa CNTT mới' } })
  await userEvent.click(screen.getByRole('checkbox', { name: 'Đang hoạt động' }))
  await userEvent.click(screen.getByRole('button', { name: 'Lưu danh mục' }))
  expect(mocks.updateFaculty).toHaveBeenCalledExactlyOnceWith(1, {
    name: 'Khoa CNTT mới',
    active: false,
  })
})
it('cấp quyền Admin giữ nguyên vai trò và chỉ gửi sau xác nhận', async () => {
  const account = {
    id: 20,
    fullName: 'GV B',
    status: 'ACTIVE',
    roles: ['LECTURER'],
  } as UserResponse
  render(<AccountAccessForm account={account} onUpdated={vi.fn()} onClose={vi.fn()} />)
  await userEvent.click(screen.getByRole('checkbox', { name: 'Quyền quản trị viên' }))
  await userEvent.click(screen.getByRole('button', { name: 'Cập nhật quyền' }))
  expect(mocks.roles).not.toHaveBeenCalled()
  await confirm()
  expect(mocks.roles).toHaveBeenCalledExactlyOnceWith(20, ['LECTURER', 'ADMIN'])
})
it('khóa tài khoản thất bại không báo thành công hoặc cập nhật giao diện', async () => {
  mocks.userStatus.mockRejectedValue({
    response: { data: { message: 'Phải giữ ít nhất một Admin hoạt động.' } },
  })
  const updated = vi.fn()
  render(
    <AccountAccessForm
      account={{ id: 20, fullName: 'Admin', status: 'ACTIVE', roles: ['ADMIN'] } as UserResponse}
      onUpdated={updated}
      onClose={vi.fn()}
    />,
  )
  fireEvent.change(screen.getByLabelText('Trạng thái tài khoản'), { target: { value: 'LOCKED' } })
  await userEvent.click(screen.getByRole('button', { name: 'Cập nhật trạng thái' }))
  await confirm()
  await waitFor(() =>
    expect(mocks.error).toHaveBeenCalledWith('Phải giữ ít nhất một Admin hoạt động.'),
  )
  expect(updated).not.toHaveBeenCalled()
  expect(mocks.success).not.toHaveBeenCalled()
})
it('yêu cầu đổi điểm chặn gửi trùng và giữ đúng sheetId/lý do', async () => {
  let resolve!: (id: number) => void
  mocks.gradeRequest.mockImplementation(
    () =>
      new Promise((done) => {
        resolve = done
      }),
  )
  render(<GradeChangeRequestForm sheetId={5} />)
  fireEvent.change(screen.getByLabelText('Lý do điều chỉnh'), {
    target: { value: ' Sửa điểm tiêu chí 1 ' },
  })
  const button = screen.getByRole('button', { name: 'Gửi yêu cầu đổi điểm' })
  fireEvent.click(button)
  fireEvent.click(button)
  expect(mocks.gradeRequest).toHaveBeenCalledExactlyOnceWith(5, 'Sửa điểm tiêu chí 1')
  await act(async () => resolve(4))
  expect(await screen.findByRole('status')).toBeTruthy()
})
it('giảng viên chỉ đọc không có nút duyệt yêu cầu', async () => {
  mocks.user = lecturer()
  render(<GradeChangesPage />)
  await userEvent.click(await screen.findByRole('button', { name: 'Xem yêu cầu #4' }))
  await screen.findByText('Lý do: Cần sửa nhận xét')
  expect(screen.queryByRole('button', { name: 'Duyệt yêu cầu' })).toBeNull()
})
it.each([true, false])(
  'người quản lý duyệt/từ chối qua xác nhận, approved=%s',
  async (approved) => {
    render(<GradeChangesPage />)
    await userEvent.click(await screen.findByRole('button', { name: 'Xem yêu cầu #4' }))
    fireEvent.change(await screen.findByLabelText('Ý kiến xét duyệt'), {
      target: { value: 'Đã kiểm tra' },
    })
    await userEvent.click(
      screen.getByRole('button', { name: approved ? 'Duyệt yêu cầu' : 'Từ chối yêu cầu' }),
    )
    expect(mocks.gradeReview).not.toHaveBeenCalled()
    await confirm()
    expect(mocks.gradeReview).toHaveBeenCalledExactlyOnceWith(4, {
      approved,
      comment: 'Đã kiểm tra',
    })
  },
)
it('nhật ký lọc/phân trang trên server, bỏ qua phản hồi cũ', async () => {
  let resolve!: (data: unknown) => void
  mocks.audit.mockImplementation(({ entityType }: { entityType?: string }) =>
    entityType === 'TOPIC'
      ? new Promise((done) => {
          resolve = done
        })
      : Promise.resolve(page([], 2)),
  )
  render(<AuditLogsPage />)
  await screen.findByText('Không có nhật ký phù hợp.')
  await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
  await waitFor(() =>
    expect(mocks.audit).toHaveBeenLastCalledWith({ page: 1, size: 20, entityType: undefined }),
  )
  fireEvent.change(screen.getByLabelText('Loại đối tượng'), { target: { value: 'TOPIC' } })
  await userEvent.click(screen.getByRole('button', { name: 'Lọc nhật ký' }))
  fireEvent.change(screen.getByLabelText('Loại đối tượng'), { target: { value: 'USER' } })
  await userEvent.click(screen.getByRole('button', { name: 'Lọc nhật ký' }))
  await screen.findByText('Không có nhật ký phù hợp.')
  await act(async () =>
    resolve(
      page([
        { id: 1, action: 'CŨ', entityType: 'TOPIC', outcome: 'SUCCESS', createdAt: '2026-01-01' },
      ]),
    ),
  )
  expect(screen.queryByText('CŨ')).toBeNull()
  expect(mocks.audit).toHaveBeenLastCalledWith({ page: 0, size: 20, entityType: 'USER' })
})
it('cập nhật trạng thái đề tài dùng version và chỉ gửi sau xác nhận', async () => {
  render(<ProjectAssignmentsPage />)
  await userEvent.click(await screen.findByRole('button', { name: 'Quản lý DT01' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Đánh dấu hoàn thành' }))
  expect(mocks.status).not.toHaveBeenCalled()
  await confirm()
  expect(mocks.status).toHaveBeenCalledExactlyOnceWith(1, { status: 'COMPLETED', version: 3 })
})
it('giảng viên không quản lý không đổi trạng thái đề tài', async () => {
  mocks.user = lecturer()
  render(<ProjectAssignmentsPage />)
  await userEvent.click(await screen.findByRole('button', { name: 'Quản lý DT01' }))
  await screen.findByRole('heading', { name: 'DT01 · Nhóm A' })
  expect(screen.queryByRole('button', { name: 'Đánh dấu hoàn thành' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Hủy giao đề tài' })).toBeNull()
})
it('gửi đề xuất đúng DTO, giữ Unicode và không công bố tự động', async () => {
  mocks.user = lecturer()
  render(<TopicProposalForm onSaved={vi.fn()} onClose={vi.fn()} />)
  fireEvent.change(await screen.findByLabelText('Đợt đề xuất'), { target: { value: '1' } })
  fireEvent.change(screen.getByLabelText('Mã đề xuất'), { target: { value: 'DX01' } })
  for (const label of ['Tên đề xuất', 'Mô tả đề xuất', 'Mục tiêu đề xuất', 'Kết quả dự kiến'])
    fireEvent.change(screen.getByLabelText(label), { target: { value: 'Đề xuất tiếng Việt 😀' } })
  await userEvent.click(screen.getByRole('button', { name: 'Gửi đề xuất' }))
  expect(mocks.createTopic).toHaveBeenCalledWith(
    expect.objectContaining({
      periodId: 1,
      code: 'DX01',
      title: 'Đề xuất tiếng Việt 😀',
      visibilityType: 'OPEN',
      reservedGroupId: null,
    }),
  )
  expect(mocks.publishTopic).not.toHaveBeenCalled()
})
it('chủ đề xuất sửa được nội dung nhưng không tự xét duyệt', async () => {
  mocks.user = lecturer()
  render(
    <MemoryRouter>
      <TopicWorkflowPage />
    </MemoryRouter>,
  )
  await userEvent.click(await screen.findByRole('button', { name: 'Xử lý DT01' }))
  await screen.findByRole('button', { name: 'Sửa đề xuất' })
  expect(screen.queryByRole('button', { name: 'Xét duyệt đề xuất' })).toBeNull()
  await userEvent.click(screen.getByRole('button', { name: 'Sửa đề xuất' }))
  fireEvent.change(screen.getByLabelText('Tên đề xuất'), { target: { value: 'Nội dung đã sửa' } })
  await userEvent.click(screen.getByRole('button', { name: 'Lưu đề xuất' }))
  expect(mocks.updateTopic).toHaveBeenCalledWith(
    1,
    expect.objectContaining({ version: 3, title: 'Nội dung đã sửa' }),
  )
})
it('người quản lý yêu cầu sửa đề xuất và gửi đúng phiên bản', async () => {
  render(
    <MemoryRouter>
      <TopicWorkflowPage />
    </MemoryRouter>,
  )
  await userEvent.click(await screen.findByRole('button', { name: 'Xử lý DT01' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Xét duyệt đề xuất' }))
  fireEvent.change(screen.getByLabelText('Kết quả xét duyệt'), {
    target: { value: 'REVISION_REQUIRED' },
  })
  fireEvent.change(screen.getByLabelText('Nhận xét xét duyệt'), {
    target: { value: 'Bổ sung mục tiêu' },
  })
  await userEvent.click(screen.getByRole('button', { name: 'Tiếp tục' }))
  await confirm()
  expect(mocks.reviewTopic).toHaveBeenCalledExactlyOnceWith(1, {
    version: 3,
    decision: 'REVISION_REQUIRED',
    comment: 'Bổ sung mục tiêu',
  })
})
it('Trưởng khoa chỉ thấy phạm vi email khoa mình quản lý', async () => {
  mocks.user = {
    ...lecturer(),
    activeAppointments: [
      {
        id: 1,
        lecturerId: 1,
        lecturerCode: 'GV01',
        lecturerName: 'GV A',
        positionCode: 'DEAN',
        facultyId: 1,
        startsAt: '2026-01-01',
      },
    ],
  }
  mocks.faculties.mockResolvedValue([
    { id: 1, name: 'CNTT' },
    { id: 2, name: 'Kinh tế' },
  ])
  const onChange = vi.fn()
  render(
    <EmailScopePicker
      value={{ scopeType: 'LECTURER', scopeId: 10 }}
      disabled={false}
      onChange={onChange}
    />,
  )
  await screen.findByRole('option', { name: 'Khoa CNTT' })
  expect(screen.queryByRole('option', { name: 'Khoa Kinh tế' })).toBeNull()
  fireEvent.change(screen.getByLabelText('Phạm vi cấu hình email'), {
    target: { value: 'FACULTY:1' },
  })
  expect(onChange).toHaveBeenCalledWith({ scopeType: 'FACULTY', scopeId: 1 })
})
it('lịch sử email hiển thị các lần gửi bằng văn bản, không chạy nội dung lỗi HTML', async () => {
  mocks.log.mockResolvedValue({
    log: { subject: 'Kiểm tra', recipientEmail: 'gv@example.edu.vn' },
    attempts: [
      {
        id: 1,
        attemptNo: 1,
        status: 'FAILED',
        createdAt: '2026-10-22',
        errorMessage: '<script>unsafe()</script>',
      },
    ],
  })
  render(<EmailDeliveryDetail id={8} onClose={vi.fn()} />)
  expect(await screen.findByText('<script>unsafe()</script>')).toBeTruthy()
  expect(document.querySelector('script')).toBeNull()
  expect(mocks.log).toHaveBeenCalledWith(8)
})

it('người quản lý công bố và rút đề xuất bằng version hiện tại, không đổi nghiệp vụ', async () => {
  mocks.topic.mockResolvedValue({ ...topic, status: 'APPROVED' })
  render(
    <MemoryRouter>
      <TopicWorkflowPage />
    </MemoryRouter>,
  )
  await userEvent.click(await screen.findByRole('button', { name: 'Xử lý DT01' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Công bố đề tài' }))
  await userEvent.click(screen.getByRole('button', { name: 'Tiếp tục' }))
  expect(mocks.publishTopic).not.toHaveBeenCalled()
  await confirm()
  expect(mocks.publishTopic).toHaveBeenCalledExactlyOnceWith(1, 3)
})
it('chủ đề xuất gửi lý do khi rút đề tài chưa công bố', async () => {
  mocks.user = lecturer()
  render(
    <MemoryRouter>
      <TopicWorkflowPage />
    </MemoryRouter>,
  )
  await userEvent.click(await screen.findByRole('button', { name: 'Xử lý DT01' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Rút đề xuất' }))
  fireEvent.change(screen.getByLabelText('Lý do rút'), {
    target: { value: 'Đổi hướng nghiên cứu' },
  })
  await userEvent.click(screen.getByRole('button', { name: 'Tiếp tục' }))
  await confirm()
  expect(mocks.withdrawTopic).toHaveBeenCalledExactlyOnceWith(1, {
    version: 3,
    reason: 'Đổi hướng nghiên cứu',
  })
})
it('đổi bộ lọc yêu cầu trả về trang đầu và không duyệt phạm vi khoa khác', async () => {
  mocks.user = {
    ...lecturer(),
    activeAppointments: [
      {
        id: 1,
        lecturerId: 1,
        lecturerCode: 'GV01',
        lecturerName: 'GV A',
        positionCode: 'DEAN',
        facultyId: 8,
        startsAt: '2026-01-01',
      },
    ],
  }
  mocks.gradeList.mockResolvedValue(page([request], 2))
  render(<GradeChangesPage />)
  await userEvent.click(await screen.findByRole('button', { name: 'Sau' }))
  await waitFor(() =>
    expect(mocks.gradeList).toHaveBeenLastCalledWith({ page: 1, size: 10, status: undefined }),
  )
  fireEvent.change(screen.getByLabelText('Trạng thái yêu cầu'), { target: { value: 'PENDING' } })
  await waitFor(() =>
    expect(mocks.gradeList).toHaveBeenLastCalledWith({ page: 0, size: 10, status: 'PENDING' }),
  )
  await userEvent.click(screen.getByRole('button', { name: 'Xem yêu cầu #4' }))
  await screen.findByText('Lý do: Cần sửa nhận xét')
  expect(screen.queryByRole('button', { name: 'Duyệt yêu cầu' })).toBeNull()
})

it('tra cứu tài khoản ẩn bằng ID để Admin có thể mở lại', async () => {
  mocks.userStatus.mockResolvedValue({
    id: 20,
    fullName: 'Tài khoản ngừng hoạt động',
    status: 'ACTIVE',
    roles: ['LECTURER'],
  })
  mocks.getUser.mockResolvedValue({
    id: 20,
    fullName: 'Tài khoản ngừng hoạt động',
    email: 'inactive@example.edu.vn',
    status: 'DISABLED',
    roles: ['LECTURER'],
  })
  render(<UserManagementPage />)
  fireEvent.change(screen.getByLabelText('ID tài khoản'), { target: { value: '20' } })
  await userEvent.click(screen.getByRole('button', { name: 'Tra cứu tài khoản' }))
  expect(
    await screen.findByRole('heading', { name: 'Quyền truy cập · Tài khoản ngừng hoạt động' }),
  ).toBeTruthy()
  expect(mocks.getUser).toHaveBeenCalledExactlyOnceWith(20)
  fireEvent.change(screen.getByLabelText('Trạng thái tài khoản'), { target: { value: 'ACTIVE' } })
  await userEvent.click(screen.getByRole('button', { name: 'Cập nhật trạng thái' }))
  await confirm()
  expect(mocks.userStatus).toHaveBeenCalledExactlyOnceWith(20, 'ACTIVE')
})
it('tạo Admin thuần không yêu cầu hồ sơ học vụ hoặc danh mục bộ môn', async () => {
  mocks.departments.mockResolvedValue([])
  mocks.createUser.mockResolvedValue({ id: 20, fullName: 'Admin kiểm thử', roles: ['ADMIN'] })
  render(
    <MemoryRouter>
      <CreateAccountForm onCancel={vi.fn()} onCreated={vi.fn()} />
    </MemoryRouter>,
  )
  await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản quản trị viên' }))
  expect(screen.queryByLabelText(/Mã giảng viên/)).toBeNull()
  expect(screen.queryByLabelText(/Bộ môn/)).toBeNull()
  fireEvent.change(screen.getByLabelText(/Họ và tên/), { target: { value: 'Admin kiểm thử' } })
  fireEvent.change(screen.getByLabelText(/Email đăng nhập/), {
    target: { value: 'admin-test@example.edu.vn' },
  })
  const synthetic = ['Fixture', 7, '!'].join('')
  fireEvent.change(screen.getByLabelText(/^Mật khẩu/), { target: { value: synthetic } })
  fireEvent.change(screen.getByLabelText(/Xác nhận mật khẩu/), { target: { value: synthetic } })
  await userEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản' }))
  const submitted = mocks.createUser.mock.calls[0]?.[0]
  expect(submitted?.roles).toEqual(['ADMIN'])
  expect(submitted?.student).toBeUndefined()
  expect(submitted?.lecturer).toBeUndefined()
})

it('lọc yêu cầu đổi điểm theo mã phiếu được gửi về backend', async () => {
  render(<GradeChangesPage />)
  await screen.findByRole('button', { name: 'Xem yêu cầu #4' })
  fireEvent.change(screen.getByLabelText('Mã phiếu điểm'), { target: { value: '5' } })
  await userEvent.click(screen.getByRole('button', { name: 'Lọc theo phiếu' }))
  await waitFor(() =>
    expect(mocks.gradeList).toHaveBeenLastCalledWith({
      page: 0,
      size: 10,
      status: undefined,
      sheetId: 5,
    }),
  )
})
