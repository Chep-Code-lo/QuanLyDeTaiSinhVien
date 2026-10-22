import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { AcademicManagementPage } from '../src/pages/AcademicManagementPage'

const mocks = vi.hoisted(() => ({ periods: vi.fn(), error: vi.fn() }))
vi.mock('../src/api/academic', () => ({
  academicApi: {
    getRegistrationPeriods: mocks.periods,
    getFaculties: async () => [],
    getSemesters: async () => [],
  },
}))
vi.mock('../src/context/AuthContext', () => ({
  useAuth: () => ({ user: { id: 1 }, hasRole: () => true, hasAppointment: () => false }),
}))
vi.mock('../src/context/ToastContext', () => ({
  useToast: () => ({ showError: mocks.error, showSuccess: vi.fn() }),
}))
const period = {
  id: 1,
  name: 'Đợt đầu',
  projectType: 'COURSE',
  status: 'DRAFT',
  proposalStartAt: '',
  proposalEndAt: '',
  registrationStartAt: '',
  registrationEndAt: '',
  minGroupMembers: 1,
  maxGroupMembers: 3,
  minCommitteeMembers: 3,
  maxCommitteeMembers: 5,
}
beforeEach(() => {
  vi.clearAllMocks()
})
afterEach(cleanup)

it('chỉ tải một trang dù có nhiều đợt, rồi yêu cầu đúng trang tiếp theo', async () => {
  mocks.periods.mockImplementation((_faculty, page) =>
    Promise.resolve({
      content: [{ ...period, name: page ? 'Đợt tiếp' : 'Đợt đầu' }],
      totalPages: 1000,
    }),
  )
  render(<AcademicManagementPage />)
  await screen.findByText('Đợt đầu')
  expect(mocks.periods).toHaveBeenCalledExactlyOnceWith(undefined, 0, 10)
  fireEvent.click(screen.getByRole('button', { name: 'Trang sau' }))
  await screen.findByText('Đợt tiếp')
  expect(mocks.periods).toHaveBeenLastCalledWith(undefined, 1, 10)
  expect(mocks.periods).toHaveBeenCalledTimes(2)
  expect(screen.getByText('Trang 2 / 1000')).toBeTruthy()
})

it('lỗi API không bị hiển thị như danh sách trống và có thể thử lại', async () => {
  mocks.periods
    .mockRejectedValueOnce(new Error('network'))
    .mockResolvedValue({ content: [], totalPages: 0 })
  render(<AcademicManagementPage />)
  expect((await screen.findByRole('alert')).textContent).toContain('Không tải được')
  expect(screen.queryByText('Chưa có đợt đăng ký nào.')).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
  await waitFor(() => expect(screen.queryByRole('alert')).toBeNull())
  expect(screen.getByText('Chưa có đợt đăng ký nào.')).toBeTruthy()
})
