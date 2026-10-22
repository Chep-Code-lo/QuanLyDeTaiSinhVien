import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { RegistrationsPage } from '../src/pages/RegistrationsPage'

const mocks = vi.hoisted(() => ({
  list: vi.fn(),
  topics: vi.fn(),
  periods: vi.fn(),
  groups: vi.fn(),
  error: vi.fn(),
}))
vi.mock('../src/api/registrations', () => ({ registrationApi: { list: mocks.list } }))
vi.mock('../src/api/topics', () => ({ topicApi: { list: mocks.topics } }))
vi.mock('../src/api/academic', () => ({ academicApi: { getRegistrationPeriods: mocks.periods } }))
vi.mock('../src/api/groups', () => ({ groupApi: { mine: mocks.groups } }))
vi.mock('../src/context/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 1 },
    hasRole: () => false,
    hasAnyRole: () => true,
    hasAppointment: () => false,
  }),
}))
vi.mock('../src/context/ToastContext', () => ({
  useToast: () => ({ showError: mocks.error, showSuccess: vi.fn() }),
}))
beforeEach(() => {
  vi.clearAllMocks()
})
afterEach(cleanup)

it('phân trang hồ sơ giảng viên và không tải dữ liệu dành cho biểu mẫu sinh viên', async () => {
  mocks.list.mockImplementation(({ page }) =>
    Promise.resolve({
      totalPages: 1000,
      content: [
        {
          id: page + 1,
          topicTitle: page ? 'Đề tài trang sau' : 'Đề tài trang đầu',
          status: 'REJECTED',
          groupName: 'Nhóm kiểm thử',
          decisionReason: 'Chưa đạt',
        },
      ],
    }),
  )
  render(<RegistrationsPage />)
  await screen.findByText('Đề tài trang đầu')
  expect(mocks.list).toHaveBeenCalledExactlyOnceWith({ page: 0, size: 10 })
  expect(mocks.topics).not.toHaveBeenCalled()
  expect(mocks.periods).not.toHaveBeenCalled()
  expect(mocks.groups).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'Sau' }))
  await screen.findByText('Đề tài trang sau')
  expect(mocks.list).toHaveBeenLastCalledWith({ page: 1, size: 10 })
  expect(mocks.list).toHaveBeenCalledTimes(2)
})

it('không đánh đồng lỗi tải hồ sơ với trạng thái không có hồ sơ', async () => {
  mocks.list
    .mockRejectedValueOnce(new Error('network'))
    .mockResolvedValue({ content: [], totalPages: 0 })
  render(<RegistrationsPage />)
  await screen.findByRole('alert')
  expect(screen.queryByText('Chưa có hồ sơ đăng ký')).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
  await screen.findByText('Chưa có hồ sơ đăng ký')
  expect(mocks.list).toHaveBeenCalledTimes(2)
})
