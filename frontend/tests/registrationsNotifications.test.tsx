import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { RegistrationsPage } from '../src/pages/RegistrationsPage'

const mocks = vi.hoisted(() => ({
  mine: vi.fn(),
  create: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
}))
const page = (content: unknown[]) => ({ content, totalPages: 1, totalElements: content.length })
vi.mock('../src/context/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 1 },
    hasRole: (role: string) => role === 'STUDENT',
    hasAnyRole: () => false,
    hasAppointment: () => false,
  }),
}))
vi.mock('../src/context/ToastContext', () => ({
  useToast: () => ({ showSuccess: mocks.success, showError: mocks.error }),
}))
vi.mock('../src/api/registrations', () => ({
  registrationApi: { mine: mocks.mine, create: mocks.create },
}))
vi.mock('../src/api/groups', () => ({
  groupApi: {
    mine: async () => [
      {
        id: 21,
        periodId: 11,
        name: 'Nhóm A',
        periodName: 'Đợt A',
        status: 'ACTIVE',
        members: [{ userId: 1, memberRole: 'LEADER' }],
      },
    ],
  },
}))
vi.mock('../src/api/academic', () => ({
  academicApi: {
    getRegistrationPeriods: async () =>
      page([
        {
          id: 11,
          name: 'Đợt A',
          status: 'ACTIVE',
          registrationStartAt: '2020-01-01T00:00:00Z',
          registrationEndAt: '2099-01-01T00:00:00Z',
        },
      ]),
  },
}))
vi.mock('../src/api/topics', () => ({
  topicApi: {
    list: async () =>
      page([
        {
          id: 31,
          periodId: 11,
          code: 'DT01',
          title: 'Đề tài A',
          availableForRegistration: true,
        },
      ]),
  },
}))

beforeEach(() => {
  vi.clearAllMocks()
  mocks.mine.mockResolvedValue(page([]))
  mocks.create.mockResolvedValue({ id: 41 })
})
afterEach(cleanup)

it('gửi đăng ký chỉ với nhóm và đề tài, không cần lý do chọn đề tài', async () => {
  render(
    <MemoryRouter>
      <RegistrationsPage />
    </MemoryRouter>,
  )
  await screen.findByRole('option', { name: /Nhóm A/ })
  await waitFor(() =>
    expect((screen.getByLabelText('Nhóm đăng ký đề tài') as HTMLSelectElement).disabled).toBe(
      false,
    ),
  )
  fireEvent.change(screen.getByLabelText('Nhóm đăng ký đề tài'), { target: { value: '21' } })
  await screen.findByRole('option', { name: 'DT01 - Đề tài A' })
  fireEvent.change(screen.getByLabelText('Đề tài cùng đợt đăng ký'), { target: { value: '31' } })
  expect(screen.queryByRole('textbox')).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Gửi đăng ký' }))
  await waitFor(() => expect(mocks.create).toHaveBeenCalledWith({ groupId: 21, topicId: 31 }))
  expect(mocks.error).not.toHaveBeenCalled()
})

it('giữ lý do từ chối trong phản hồi hồ sơ của sinh viên', async () => {
  mocks.mine.mockResolvedValue(
    page([
      {
        id: 41,
        groupId: 21,
        groupName: 'Nhóm A',
        topicCode: 'DT01',
        topicTitle: 'Đề tài A',
        periodName: 'Đợt A',
        registeredByName: 'Trưởng nhóm',
        status: 'REJECTED',
        decisionReason: 'Đề tài chưa phù hợp với định hướng của nhóm.',
      },
    ]),
  )
  render(
    <MemoryRouter>
      <RegistrationsPage />
    </MemoryRouter>,
  )
  expect(await screen.findByText('Đề tài chưa phù hợp với định hướng của nhóm.')).toBeTruthy()
})
