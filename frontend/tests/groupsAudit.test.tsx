import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { GroupsPage } from '../src/pages/GroupsPage'

const mocks = vi.hoisted(() => ({ role: 'ADMIN', list: vi.fn(), mine: vi.fn(), incoming: vi.fn(), outgoing: vi.fn() }))
const page = (content: unknown[], totalPages = 1) => ({ content, totalPages, totalElements: content.length, page: 0, size: 10 })
vi.mock('../src/context/AuthContext', () => ({ useAuth: () => ({ user: { id: 1 }, hasRole: (role: string) => role === mocks.role }) }))
vi.mock('../src/context/ToastContext', () => ({ useToast: () => ({ showError: vi.fn(), showSuccess: vi.fn() }) }))
vi.mock('../src/api/academic', () => ({ academicApi: { getRegistrationPeriods: async () => page([{
  id: 1, name: 'Đợt A', status: 'ACTIVE', registrationStartAt: '2000-01-01T00:00:00Z', registrationEndAt: '2099-01-01T00:00:00Z', invitationExpiryHours: 48,
}]) } }))
vi.mock('../src/api/groups', () => ({ groupApi: { list: mocks.list, mine: mocks.mine, myInvitations: mocks.incoming, invitationsForGroup: mocks.outgoing } }))
const group = (id: number, status = 'ACTIVE') => ({ id, periodId: 1, periodName: 'Đợt A', name: `Nhóm ${id}`, status,
  members: [{ userId: 1, studentId: 1, fullName: 'Sinh viên A', studentCode: 'SV01', className: 'Lớp A', memberRole: 'LEADER' }] })
beforeEach(() => {
  mocks.role = 'ADMIN'
  mocks.list.mockReset(); mocks.list.mockImplementation(async ({ page: index }) => page([group(index + 1)], 3))
  mocks.mine.mockReset(); mocks.mine.mockResolvedValue([group(1), group(2, 'CLOSED')])
  mocks.incoming.mockReset(); mocks.incoming.mockResolvedValue(page([]))
  mocks.outgoing.mockReset(); mocks.outgoing.mockResolvedValue([])
})
afterEach(cleanup)

it('chỉ tải một trang nhóm và quay về trang đầu khi đổi đợt', async () => {
  render(<GroupsPage />)
  expect(await screen.findByRole('heading', { name: 'Nhóm 1' })).toBeTruthy()
  expect(mocks.list).toHaveBeenCalledOnce()
  expect(mocks.list).toHaveBeenCalledWith({ periodId: undefined, page: 0, size: 10 })
  await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
  expect(await screen.findByRole('heading', { name: 'Nhóm 2' })).toBeTruthy()
  fireEvent.change(screen.getByLabelText('Lọc theo đợt đăng ký'), { target: { value: '1' } })
  await waitFor(() => expect(mocks.list).toHaveBeenLastCalledWith({ periodId: 1, page: 0, size: 10 }))
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
