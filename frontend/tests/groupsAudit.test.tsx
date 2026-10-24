import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { GroupsPage } from '../src/pages/GroupsPage'

const mocks = vi.hoisted(() => ({
  role: 'ADMIN',
  list: vi.fn(),
  mine: vi.fn(),
  incoming: vi.fn(),
  outgoing: vi.fn(),
  periods: vi.fn(),
  create: vi.fn(),
  error: vi.fn(),
}))
const page = (content: unknown[], totalPages = 1) => ({
  content,
  totalPages,
  totalElements: content.length,
  page: 0,
  size: 10,
})
vi.mock('../src/context/AuthContext', () => ({
  useAuth: () => ({ user: { id: 1 }, hasRole: (role: string) => role === mocks.role }),
}))
vi.mock('../src/context/ToastContext', () => ({
  useToast: () => ({ showError: mocks.error, showSuccess: vi.fn() }),
}))
vi.mock('../src/api/academic', () => ({
  academicApi: {
    getRegistrationPeriods: mocks.periods,
  },
}))
vi.mock('../src/api/groups', () => ({
  groupApi: {
    list: mocks.list,
    mine: mocks.mine,
    myInvitations: mocks.incoming,
    invitationsForGroup: mocks.outgoing,
    create: mocks.create,
  },
}))
const openPeriod = {
  id: 1,
  name: 'Đợt A',
  status: 'ACTIVE',
  registrationStartAt: '2000-01-01T00:00:00Z',
  registrationEndAt: '2099-01-01T00:00:00Z',
  invitationExpiryHours: 48,
}
const group = (id: number, status = 'ACTIVE') => ({
  id,
  periodId: 1,
  periodName: 'Đợt A',
  name: `Nhóm ${id}`,
  status,
  members: [
    {
      userId: 1,
      studentId: 1,
      fullName: 'Sinh viên A',
      studentCode: 'SV01',
      className: 'Lớp A',
      memberRole: 'LEADER',
    },
  ],
})
beforeEach(() => {
  mocks.role = 'ADMIN'
  mocks.periods.mockReset()
  mocks.periods.mockResolvedValue(page([openPeriod]))
  mocks.create.mockReset()
  mocks.error.mockReset()
  mocks.list.mockReset()
  mocks.list.mockImplementation(async ({ page: index }) => page([group(index + 1)], 3))
  mocks.mine.mockReset()
  mocks.mine.mockResolvedValue([group(1), group(2, 'CLOSED')])
  mocks.incoming.mockReset()
  mocks.incoming.mockResolvedValue(page([]))
  mocks.outgoing.mockReset()
  mocks.outgoing.mockResolvedValue([])
})
afterEach(cleanup)

it('chỉ hiện đợt đang mở cho tạo nhóm, giữ lịch sử nhóm đã đóng', async () => {
  mocks.role = 'STUDENT'
  mocks.periods.mockResolvedValue(page([
    openPeriod,
    { ...openPeriod, id: 2, name: 'Đã đóng', status: 'CLOSED' },
    { ...openPeriod, id: 3, name: 'Đã hủy', status: 'CANCELLED' },
    { ...openPeriod, id: 4, name: 'Bản nháp', status: 'DRAFT' },
    { ...openPeriod, id: 5, name: 'Hết hạn', registrationEndAt: '2001-01-01T00:00:00Z' },
    { ...openPeriod, id: 6, name: 'Chưa đến hạn', registrationStartAt: '2098-01-01T00:00:00Z' },
  ]))
  render(<GroupsPage />)
  await screen.findByRole('heading', { name: 'Nhóm 2' })
  await waitFor(() => expect(screen.getAllByRole('option')).toHaveLength(2))
  expect(screen.getByRole('option', { name: /Đợt A/ })).toBeTruthy()
  for (const name of ['Đã đóng', 'Đã hủy', 'Bản nháp', 'Hết hạn', 'Chưa đến hạn'])
    expect(screen.queryByRole('option', { name: new RegExp(name) })).toBeNull()
})

it('không có đợt mở thì báo rõ và khóa nút tạo nhóm', async () => {
  mocks.role = 'STUDENT'
  mocks.periods.mockResolvedValue(page([{ ...openPeriod, status: 'CLOSED' }]))
  render(<GroupsPage />)
  await screen.findByText('Hiện không có đợt đang mở đăng ký cho sinh viên để tạo nhóm.')
  expect((screen.getByRole('button', { name: 'Tạo nhóm' }) as HTMLButtonElement).disabled).toBe(true)
})

it('bộ lọc quản lý vẫn cho xem nhóm của đợt đã đóng', async () => {
  mocks.periods.mockResolvedValue(page([{ ...openPeriod, name: 'Đợt đã đóng', status: 'CLOSED' }]))
  render(<GroupsPage />)
  expect(await screen.findByRole('option', { name: 'Đợt đã đóng' })).toBeTruthy()
})

it('tạo nhóm thất bại hiển thị lý do cụ thể từ backend', async () => {
  mocks.role = 'STUDENT'
  mocks.create.mockRejectedValue({ isAxiosError: true, response: { status: 400,
    data: { message: 'Student already belongs to an active group.' } } })
  render(<GroupsPage />)
  await screen.findByRole('option', { name: /Đợt A/ })
  await userEvent.selectOptions(screen.getByLabelText('Đợt đăng ký tạo nhóm'), '1')
  fireEvent.change(screen.getByPlaceholderText('Tên nhóm'), { target: { value: 'Nhóm mới' } })
  await userEvent.click(screen.getByRole('button', { name: 'Tạo nhóm' }))
  await waitFor(() => expect(mocks.error).toHaveBeenCalledWith(
    'Bạn đã thuộc một nhóm trong đợt đăng ký này, không thể tạo hoặc tham gia nhóm khác.'))
})

it('chỉ tải một trang nhóm và quay về trang đầu khi đổi đợt', async () => {
  render(<GroupsPage />)
  expect(await screen.findByRole('heading', { name: 'Nhóm 1' })).toBeTruthy()
  expect(mocks.list).toHaveBeenCalledOnce()
  expect(mocks.list).toHaveBeenCalledWith({ periodId: undefined, page: 0, size: 10 })
  await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
  expect(await screen.findByRole('heading', { name: 'Nhóm 2' })).toBeTruthy()
  fireEvent.change(screen.getByLabelText('Lọc theo đợt đăng ký'), { target: { value: '1' } })
  await waitFor(() =>
    expect(mocks.list).toHaveBeenLastCalledWith({ periodId: 1, page: 0, size: 10 }),
  )
})

it('chỉ lấy lời mời đang chờ và không tải lịch sử bị ẩn của nhóm đã đóng', async () => {
  mocks.role = 'STUDENT'
  render(<GroupsPage />)
  await screen.findByRole('heading', { name: 'Nhóm 1' })
  expect(mocks.incoming).toHaveBeenCalledWith(0, 100, 'PENDING')
  expect(mocks.outgoing).toHaveBeenCalledOnce()
  expect(mocks.outgoing).toHaveBeenCalledWith(1)
  expect(mocks.list).not.toHaveBeenCalled()
})
