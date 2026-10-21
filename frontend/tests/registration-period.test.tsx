import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AcademicManagementPage } from '../src/pages/AcademicManagementPage'
import type { RegistrationPeriodResponse } from '../src/types/domain'

const mocks = vi.hoisted(() => ({
  create: vi.fn(),
  changeStatus: vi.fn(),
  error: vi.fn(),
  success: vi.fn(),
  dean: true,
  periods: [] as RegistrationPeriodResponse[],
}))
vi.mock('../src/context/AuthContext', () => ({ useAuth: () => ({
  user: {
    id: 1,
    activeAppointments: [{ id: 1, facultyId: 1, positionCode: 'DEAN' }],
  },
  hasRole: () => false,
  hasAppointment: (code: string) => mocks.dean && code === 'DEAN',
}) }))
vi.mock('../src/context/ToastContext', () => ({ useToast: () => ({ showError: mocks.error, showSuccess: mocks.success }) }))
vi.mock('../src/api/academic', () => ({ academicApi: {
  getFaculties: async () => [{ id: 1, code: 'CNTT', name: 'Khoa CNTT' }],
  getSemesters: async () => [{ id: 2, semesterNo: 1, academicYear: '2099-2100' }],
  getRegistrationPeriods: async () => ({ content: mocks.periods, totalPages: 0 }),
  createRegistrationPeriod: mocks.create,
  changeRegistrationPeriodStatus: mocks.changeStatus,
} }))

beforeEach(() => {
  mocks.dean = true
  mocks.periods = []
  mocks.create.mockReset()
  mocks.error.mockReset()
  mocks.success.mockReset()
  mocks.create.mockImplementation(async payload => ({ ...payload, id: 1, version: 0, status: 'DRAFT' }))
  mocks.changeStatus.mockImplementation(async (_id, payload) => ({
    ...registrationPeriod('CLOSED'),
    status: payload.status,
    version: payload.version + 1,
  }))
})
afterEach(cleanup)

async function openForm(type: string) {
  render(<AcademicManagementPage />)
  await userEvent.click(screen.getByRole('button', { name: /Tạo Đợt Đăng Ký Mới/ }))
  await waitFor(() => expect(screen.getAllByRole('option').some(option => option.getAttribute('value') === '2')).toBe(true))
  await userEvent.selectOptions(screen.getByLabelText(/Khoa quản lý/), '1')
  await userEvent.selectOptions(screen.getByLabelText(/Học kỳ áp dụng/), '2')
  await userEvent.selectOptions(screen.getByLabelText(/Loại đợt/), type)
  fireEvent.change(screen.getByLabelText(/Tên Đợt Đăng Ký/), { target: { value: 'Đợt thử nghiệm' } })
  const times = [
    [/Bắt đầu GV đăng ký/, '01/02/2099 08:00 AM'],
    [/Kết thúc GV đăng ký/, '10/02/2099 08:00 AM'],
    [/Bắt đầu SV đăng ký/, '10/02/2099 08:00 AM'],
    [/Kết thúc SV đăng ký/, '20/02/2099 08:00 AM'],
  ] as const
  times.forEach(([label, value]) => fireEvent.change(screen.getByLabelText(label), { target: { value } }))
}

function setReview() {
  fireEvent.change(screen.getByLabelText(/Hạn chót GVPB/), { target: { value: '25/02/2099 08:00 AM' } })
}

function setDefense() {
  fireEvent.change(screen.getByLabelText(/Ngày báo cáo hội đồng/), { target: { value: '28/02/2099' } })
}

function registrationPeriod(status: RegistrationPeriodResponse['status']): RegistrationPeriodResponse {
  return {
    id: 1,
    facultyId: 1,
    facultyName: 'Khoa CNTT',
    semesterId: 2,
    academicYear: '2099-2100',
    semesterNo: 1,
    name: 'Đợt đã kết thúc',
    projectType: 'COURSE',
    status,
    proposalStartAt: '2099-02-01T08:00:00.000Z',
    proposalEndAt: '2099-02-10T08:00:00.000Z',
    registrationStartAt: '2099-02-10T08:00:00.000Z',
    registrationEndAt: '2099-02-20T08:00:00.000Z',
    minGroupMembers: 1,
    maxGroupMembers: 3,
    invitationExpiryHours: 72,
    minCommitteeMembers: 3,
    maxCommitteeMembers: 5,
    version: 0,
  }
}

describe('Trưởng khoa tạo đợt đăng ký', () => {
  it.each(['COURSE', 'RESEARCH', 'SPECIALIZED', 'GRADUATION'])('lưu đúng lịch và các mốc theo loại %s', async type => {
    await openForm(type)
    const review = type === 'SPECIALIZED' || type === 'GRADUATION'
    expect(!!screen.queryByLabelText(/Hạn chót GVPB/)).toBe(review)
    expect((screen.getByLabelText(/Ngày báo cáo hội đồng/) as HTMLInputElement).disabled).toBe(
      type !== 'GRADUATION',
    )
    if (review) setReview()
    if (type === 'GRADUATION') setDefense()
    await userEvent.click(screen.getByRole('button', { name: 'Tạo Đợt Đăng Ký', exact: true }))
    await waitFor(() => expect(mocks.create).toHaveBeenCalledWith(expect.objectContaining({
      facultyId: 1, semesterId: 2, name: 'Đợt thử nghiệm', projectType: type,
      proposalStartAt: new Date('2099-02-01T08:00').toISOString(),
      proposalEndAt: new Date('2099-02-10T08:00').toISOString(),
      registrationStartAt: new Date('2099-02-10T08:00').toISOString(),
      registrationEndAt: new Date('2099-02-20T08:00').toISOString(),
      reviewDeadlineAt: review ? new Date('2099-02-25T08:00').toISOString() : null,
      defenseDate: type === 'GRADUATION' ? '2099-02-28' : null,
    })))
    const [payload] = mocks.create.mock.calls.at(-1)!
    expect(payload).not.toHaveProperty('minGroupMembers')
    expect(payload).not.toHaveProperty('maxGroupMembers')
    expect(payload).not.toHaveProperty('invitationExpiryHours')
    expect(payload).not.toHaveProperty('minCommitteeMembers')
    expect(payload).not.toHaveProperty('maxCommitteeMembers')
    expect(mocks.error).not.toHaveBeenCalled()
    expect(screen.queryByRole('dialog')).toBeNull()
    if (type === 'GRADUATION') expect(screen.getByText('28/02/2099')).toBeTruthy()
  })

  it('xóa mốc không áp dụng khi đổi KLTN sang TLCN rồi môn học', async () => {
    await openForm('GRADUATION')
    setReview(); setDefense()
    await userEvent.selectOptions(screen.getByLabelText(/Loại đợt/), 'SPECIALIZED')
    expect((screen.getByLabelText(/Ngày báo cáo hội đồng/) as HTMLInputElement).disabled).toBe(true)
    await userEvent.selectOptions(screen.getByLabelText(/Loại đợt/), 'GRADUATION')
    const defenseDate = screen.getByLabelText(/Ngày báo cáo hội đồng/) as HTMLInputElement
    expect(defenseDate.disabled).toBe(false)
    expect(defenseDate.value).toBe('')
    setDefense()
    await userEvent.selectOptions(screen.getByLabelText(/Loại đợt/), 'COURSE')
    await userEvent.selectOptions(screen.getByLabelText(/Loại đợt/), 'GRADUATION')
    expect((screen.getByLabelText(/Hạn chót GVPB/) as HTMLInputElement).value).toBe('')
    expect((screen.getByLabelText(/Ngày báo cáo hội đồng/) as HTMLInputElement).value).toBe('')
    await userEvent.selectOptions(screen.getByLabelText(/Loại đợt/), 'COURSE')
    await userEvent.click(screen.getByRole('button', { name: 'Tạo Đợt Đăng Ký', exact: true }))
    await waitFor(() => expect(mocks.create).toHaveBeenCalledWith(expect.objectContaining({ projectType: 'COURSE', reviewDeadlineAt: null, defenseDate: null })))
  })

  it('chặn lịch GV/SV sai thứ tự', async () => {
    await openForm('COURSE')
    fireEvent.change(screen.getByLabelText(/Bắt đầu SV đăng ký/), { target: { value: '09/02/2099 08:00 AM' } })
    await userEvent.click(screen.getByRole('button', { name: 'Tạo Đợt Đăng Ký', exact: true }))
    expect(mocks.create).not.toHaveBeenCalled()
    expect(mocks.error).toHaveBeenCalledWith(expect.stringContaining('Bắt đầu SV đăng ký'))
  })

  it('bắt buộc đủ hạn phản biện và ngày hội đồng khi tạo KLTN', async () => {
    await openForm('GRADUATION')
    const form = screen.getByLabelText(/Tên Đợt Đăng Ký/).closest('form')!
    fireEvent.submit(form)
    expect(mocks.error).toHaveBeenLastCalledWith('Hãy nhập hạn chót GVPB nộp điểm về Khoa.')
    setReview()
    fireEvent.submit(form)
    expect(mocks.error).toHaveBeenLastCalledWith('Hãy nhập ngày báo cáo hội đồng cho đợt KLTN.')
    expect(mocks.create).not.toHaveBeenCalled()
  })

  it('không hiển thị nút tạo đợt cho người không có quyền trưởng khoa', async () => {
    mocks.dean = false
    render(<AcademicManagementPage />)
    await screen.findByText('Chưa có đợt đăng ký nào.')
    expect(screen.queryByRole('button', { name: /Tạo Đợt Đăng Ký Mới/ })).toBeNull()
  })

  it('cho phép bật lại đợt đã đóng', async () => {
    mocks.periods = [registrationPeriod('CLOSED')]
    render(<AcademicManagementPage />)

    await screen.findByText('Đợt đã kết thúc')

    await userEvent.click(screen.getByRole('button', { name: 'Bật' }))
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận' }))

    await waitFor(() => expect(mocks.changeStatus).toHaveBeenCalledWith(1, {
      status: 'ACTIVE',
      version: 0,
    }))
    expect(screen.getByRole('button', { name: 'Đóng' })).toBeTruthy()
  })

  it('không cho chuyển lại đợt đã hủy', async () => {
    mocks.periods = [registrationPeriod('CANCELLED')]
    render(<AcademicManagementPage />)

    await screen.findByText('Đợt đã kết thúc')

    expect(screen.queryByRole('button', { name: 'Bật' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Đóng' })).toBeNull()
  })
})
