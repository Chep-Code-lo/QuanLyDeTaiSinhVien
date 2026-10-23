import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { StudentProgressPage } from '../src/pages/StudentProgressPage'
import { MilestoneBoard } from '../src/components/MilestoneBoard'
import { formatDate } from '../src/utils/dateTime'

const mocks = vi.hoisted(() => ({
  projects: vi.fn(),
  groups: vi.fn(),
  milestones: vi.fn(),
  error: vi.fn(),
  success: vi.fn(),
  upload: vi.fn(),
  submit: vi.fn(),
}))
vi.mock('../src/context/AuthContext', () => ({ useAuth: () => ({ user: { id: 1 } }) }))
vi.mock('../src/context/ToastContext', () => ({
  useToast: () => ({ showError: mocks.error, showSuccess: mocks.success }),
}))
vi.mock('../src/api/student', async (importOriginal) => {
  const original = await importOriginal<typeof import('../src/api/student')>()
  return { ...original, studentApi: { ...original.studentApi, projectAssignments: mocks.projects, upload: mocks.upload, submit: mocks.submit } }
})
vi.mock('../src/api/groups', () => ({ groupApi: { mine: mocks.groups } }))
vi.mock('../src/api/milestones', () => ({ milestoneApi: { listByAssignment: mocks.milestones } }))

const deadline = new Date()
deadline.setHours(10, 0, 0, 0)
const dueAt = deadline.toISOString().replace('Z', '')
const milestones = [1, 2].map((id) => ({
  id,
  assignmentId: id,
  title: `Mốc dự án ${id}`,
  milestoneType: 'PROGRESS' as const,
  description: 'Báo cáo tiến độ',
  dueAt,
  status: 'OPEN' as const,
  version: 0,
  submissions: [],
}))
const projects = [1, 2].map((id) => ({
  id,
  groupId: id,
  topicCode: `DT0${id}`,
  topicTitle: `Đề tài ${id}`,
  groupName: `Nhóm ${id}`,
  status: 'IN_PROGRESS',
}))
const scroll = vi.fn()
const originalScroll = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollIntoView')

beforeEach(() => {
  vi.clearAllMocks()
  mocks.projects.mockResolvedValue(projects)
  mocks.groups.mockResolvedValue(
    [1, 2].map((id) => ({ id, members: [{ userId: 1, memberRole: 'LEADER' }] })),
  )
  mocks.milestones.mockImplementation(async (id: number) =>
    milestones.filter((item) => item.assignmentId === id),
  )
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
    configurable: true,
    value: scroll,
  })
})
afterEach(() => {
  cleanup()
  if (originalScroll) Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', originalScroll)
  else Reflect.deleteProperty(HTMLElement.prototype, 'scrollIntoView')
})

it('hiển thị lịch của sinh viên, chọn ngày đưa tới mốc và vẫn cho trưởng nhóm nộp bài', async () => {
  render(<StudentProgressPage />)
  const calendar = await screen.findByRole('region', { name: 'Lịch hạn báo cáo' })
  const day = within(calendar).getByRole('button', {
    name: `${formatDate(deadline)}, 2 mốc báo cáo`,
  })
  fireEvent.click(day)
  expect(day.getAttribute('aria-pressed')).toBe('true')
  expect(document.getElementById('student-milestone-1')?.className).toContain(
    'student-timeline__item--selected',
  )
  expect(scroll).toHaveBeenCalledOnce()
  expect(screen.getAllByRole('button', { name: 'Nộp báo cáo' })).toHaveLength(2)
  expect(mocks.milestones).toHaveBeenCalledTimes(2)
  fireEvent.click(within(calendar).getByRole('button', { name: 'Tháng sau' }))
  const nextMonth = new Date(deadline.getFullYear(), deadline.getMonth() + 1, 1)
  expect(within(calendar).getByRole('heading').textContent).toBe(
    `Tháng ${nextMonth.getMonth() + 1}, ${nextMonth.getFullYear()}`,
  )
  fireEvent.click(within(calendar).getByRole('button', { name: 'Tháng trước' }))
  expect(
    within(calendar).getByRole('button', { name: `${formatDate(deadline)}, 2 mốc báo cáo` }),
  ).toBeTruthy()
})

it('lịch theo bộ lọc dự án, không tải thêm dữ liệu khi đổi lựa chọn hoặc tháng', async () => {
  render(<StudentProgressPage />)
  await screen.findByRole('region', { name: 'Lịch hạn báo cáo' })
  fireEvent.change(screen.getByLabelText('Dự án của nhóm'), { target: { value: '2' } })
  expect(screen.queryByRole('heading', { name: 'Mốc dự án 1' })).toBeNull()
  const calendar = screen.getByRole('region', { name: 'Lịch hạn báo cáo' })
  fireEvent.click(
    within(calendar).getByRole('button', { name: `${formatDate(deadline)}, 1 mốc báo cáo` }),
  )
  expect(document.getElementById('student-milestone-2')?.className).toContain(
    'student-timeline__item--selected',
  )
  expect(mocks.milestones).toHaveBeenCalledTimes(2)
  expect(mocks.projects).toHaveBeenCalledOnce()
})

it('thử nộp lại báo cáo tái sử dụng tệp đã tải, không tạo thêm tệp mồ côi', async () => {
  mocks.upload.mockResolvedValue({ id: 99 })
  mocks.submit.mockRejectedValueOnce({ response: { data: { message: 'Lỗi nộp báo cáo' } } }).mockResolvedValueOnce({ id: 1 })
  render(<StudentProgressPage />)
  const trigger = (await screen.findAllByRole('button', { name: /Nộp báo cáo/ }))[0]
  fireEvent.click(trigger)
  const dialog = screen.getByRole('dialog')
  fireEvent.change(within(dialog).getByLabelText(/Tệp báo cáo/), {
    target: { files: [new File(['Report'], 'report.txt', { type: 'text/plain' })] },
  })
  fireEvent.submit(within(dialog).getByLabelText(/Tệp báo cáo/).closest('form')!)
  await waitFor(() => expect(mocks.error).toHaveBeenCalledWith('Lỗi nộp báo cáo'))
  fireEvent.submit(within(dialog).getByLabelText(/Tệp báo cáo/).closest('form')!)
  await waitFor(() => expect(mocks.submit).toHaveBeenCalledTimes(2))
  expect(mocks.upload).toHaveBeenCalledTimes(1)
  expect(mocks.submit).toHaveBeenLastCalledWith(1, 99, '')
})

it('thành viên xem được lịch nhưng không có quyền nộp báo cáo hoặc quản lý mốc', async () => {
  mocks.groups.mockResolvedValue(
    [1, 2].map((id) => ({ id, members: [{ userId: 1, memberRole: 'MEMBER' }] })),
  )
  render(<StudentProgressPage />)
  await screen.findByRole('region', { name: 'Lịch hạn báo cáo' })
  expect(screen.queryByRole('button', { name: 'Nộp báo cáo' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Chỉnh sửa' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Nhắc nhở' })).toBeNull()
})

it('vẫn xem và chuyển tháng khi dự án chưa có mốc báo cáo', async () => {
  mocks.milestones.mockResolvedValue([])
  render(<StudentProgressPage />)
  const calendar = await screen.findByRole('region', { name: 'Lịch hạn báo cáo' })
  expect(screen.getByText('Giảng viên chưa tạo mốc báo cáo cho dự án này.')).toBeTruthy()
  expect(calendar.querySelector('.milestone-board__day--deadline')).toBeNull()
  fireEvent.click(within(calendar).getByRole('button', { name: 'Tháng sau' }))
  expect(mocks.milestones).toHaveBeenCalledTimes(2)
})

it('lịch dùng chung vẫn chọn và đánh dấu đúng mốc trên trang quản lý', async () => {
  render(
    <MilestoneBoard
      milestones={[milestones[0]]}
      loading={false}
      hasAssignment
      canManage
      canEdit
      reminding={false}
      onEdit={vi.fn()}
      onFeedback={vi.fn()}
      onRemind={vi.fn()}
    />,
  )
  const calendar = screen.getByRole('region', { name: 'Lịch hạn báo cáo' })
  fireEvent.click(
    within(calendar).getByRole('button', { name: `${formatDate(deadline)}, 1 mốc báo cáo` }),
  )
  await waitFor(() =>
    expect(document.getElementById('milestone-card-1')?.className).toContain(
      'milestone-board__card--selected',
    ),
  )
  expect(scroll).toHaveBeenCalledOnce()
})
