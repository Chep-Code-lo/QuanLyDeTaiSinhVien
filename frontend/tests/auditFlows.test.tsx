import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { DashboardPage } from '../src/pages/DashboardPage'
import { DefenseSchedulePanel } from '../src/components/DefenseSchedulePanel'
import { ConfirmModal } from '../src/components/ConfirmModal'
import { defenseDateError, defenseInstant, formatDefenseTime } from '../src/utils/defenseSchedule'
import type { RegistrationPeriodResponse } from '../src/types/domain'

const mocks = vi.hoisted(() => ({ summary: vi.fn(), schedule: vi.fn(), eligible: vi.fn() }))
vi.mock('../src/api/dashboard', () => ({ dashboardApi: { summary: mocks.summary } }))
vi.mock('../src/api/evaluation', () => ({
  evaluationApi: { scheduleDefense: mocks.schedule, defenseEligibility: mocks.eligible },
}))
vi.mock('../src/context/AuthContext', () => ({
  useAuth: () => ({ user: { id: 1, fullName: 'Admin', roles: ['ADMIN'] }, hasRole: () => true }),
}))

const summary = {
  totalTopics: 123,
  registeringTopics: 12,
  inProgressTopics: 100,
  completedTopics: 11,
  openRegistrationPeriods: 1,
  unreadNotifications: 2,
  nextRegistrationCloseAt: null,
  currentSemester: null,
  topicTypes: [],
  recentTopics: [],
  upcomingDeadlines: [],
  updatedAt: '2099-02-20T00:00:00Z',
}
const committee = {
  id: 11,
  periodId: 1,
  name: 'Hội đồng A',
  status: 'DRAFT' as const,
  members: [],
  version: 0,
}
const project = {
  id: 51,
  periodId: 1,
  periodName: 'Đợt A',
  groupId: 21,
  groupName: 'Nhóm A',
  topicId: 31,
  topicCode: 'DT01',
  topicTitle: 'Quản lý đề tài',
  status: 'IN_PROGRESS',
}
const graduationPeriod: RegistrationPeriodResponse = {
  id: 1,
  facultyId: 1,
  facultyName: 'Khoa A',
  semesterId: 1,
  academicYear: '2026-2027',
  semesterNo: 2,
  name: 'KLTN',
  projectType: 'GRADUATION',
  status: 'ACTIVE',
  defenseDate: '2027-05-01',
  proposalStartAt: '',
  proposalEndAt: '',
  registrationStartAt: '',
  registrationEndAt: '',
  minGroupMembers: 1,
  maxGroupMembers: 3,
  invitationExpiryHours: 48,
  minCommitteeMembers: 3,
  maxCommitteeMembers: 5,
  version: 0,
}

beforeEach(() => {
  mocks.summary.mockReset()
  mocks.summary.mockResolvedValue(summary)
  mocks.schedule.mockReset()
  mocks.schedule.mockResolvedValue(71)
  mocks.eligible.mockReset()
  mocks.eligible.mockResolvedValue({ eligible: true, reason: null })
})
afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

async function fillSchedule() {
  fireEvent.change(screen.getByLabelText(/Hội đồng đã lưu/), { target: { value: '11' } })
  fireEvent.change(screen.getByLabelText(/Đề tài \/ nhóm/), { target: { value: '51' } })
  fireEvent.change(screen.getByLabelText(/Địa điểm/), { target: { value: ' A5-305 ' } })
  fireEvent.change(screen.getByLabelText(/Bắt đầu/), { target: { value: '20/02/2099 09:00 AM' } })
  fireEvent.change(screen.getByLabelText(/Kết thúc/), { target: { value: '20/02/2099 10:00 AM' } })
  await waitFor(() =>
    expect(
      (screen.getByRole('button', { name: 'Xếp lịch bảo vệ' }) as HTMLButtonElement).disabled,
    ).toBe(false),
  )
}

it('xác nhận đủ tên đề tài, nhóm, hội đồng và chỉ cho chọn đề tài cùng đợt', async () => {
  render(
    <DefenseSchedulePanel
      committees={[committee]}
      projects={[project, { ...project, id: 52, periodId: 2, topicCode: 'OTHER' }]}
      defenses={[]}
      onScheduled={vi.fn()}
    />,
  )
  await fillSchedule()
  expect(screen.queryByRole('option', { name: /OTHER/ })).toBeNull()
  await userEvent.click(screen.getByRole('button', { name: 'Xếp lịch bảo vệ' }))
  const dialog = screen.getByRole('dialog')
  expect(dialog.textContent).toContain('DT01 · Quản lý đề tài · Nhóm A')
  expect(dialog.textContent).toContain('hội đồng Hội đồng A')
  expect(dialog.textContent).toContain('A5-305')
  expect((screen.getByLabelText(/Địa điểm/) as HTMLInputElement).maxLength).toBe(200)
})

describe('Hồi quy audit', () => {
  it('mặc định ngày KLTN từ cấu hình và gửi giờ Việt Nam thành UTC kể cả lúc gần nửa đêm', async () => {
    render(
      <DefenseSchedulePanel
        periods={[graduationPeriod]}
        committees={[committee]}
        projects={[project]}
        defenses={[]}
        onScheduled={vi.fn()}
      />,
    )
    fireEvent.change(screen.getByLabelText(/Hội đồng đã lưu/), { target: { value: '11' } })
    expect((screen.getByLabelText(/Bắt đầu/) as HTMLInputElement).value).toBe('01/05/2027 08:00')
    expect((screen.getByLabelText(/Kết thúc/) as HTMLInputElement).value).toBe('01/05/2027 09:00')
    fireEvent.change(screen.getByLabelText(/Đề tài \/ nhóm/), { target: { value: '51' } })
    fireEvent.change(screen.getByLabelText(/Địa điểm/), { target: { value: 'A101' } })
    fireEvent.change(screen.getByLabelText(/Bắt đầu/), { target: { value: '01/05/2027 12:15 AM' } })
    fireEvent.change(screen.getByLabelText(/Kết thúc/), {
      target: { value: '01/05/2027 01:15 AM' },
    })
    await waitFor(() =>
      expect(
        (screen.getByRole('button', { name: 'Xếp lịch bảo vệ' }) as HTMLButtonElement).disabled,
      ).toBe(false),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Xếp lịch bảo vệ' }))
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận xếp lịch' }))
    expect(mocks.schedule).toHaveBeenCalledWith(
      expect.objectContaining({
        startsAt: '2027-04-30T17:15:00.000Z',
        endsAt: '2027-04-30T18:15:00.000Z',
      }),
    )
  })

  it.each([
    ['30/04/2027 08:00 AM', '30/04/2027 09:00 AM'],
    ['02/05/2027 08:00 AM', '02/05/2027 09:00 AM'],
    ['01/05/2027 11:30 PM', '02/05/2027 12:30 AM'],
  ])('chặn ngày KLTN sai trước khi gọi API: %s → %s', async (start, end) => {
    render(
      <DefenseSchedulePanel
        periods={[graduationPeriod]}
        committees={[committee]}
        projects={[project]}
        defenses={[]}
        onScheduled={vi.fn()}
      />,
    )
    fireEvent.change(screen.getByLabelText(/Hội đồng đã lưu/), { target: { value: '11' } })
    fireEvent.change(screen.getByLabelText(/Đề tài \/ nhóm/), { target: { value: '51' } })
    fireEvent.change(screen.getByLabelText(/Địa điểm/), { target: { value: 'A101' } })
    fireEvent.change(screen.getByLabelText(/Bắt đầu/), { target: { value: start } })
    fireEvent.change(screen.getByLabelText(/Kết thúc/), { target: { value: end } })
    expect((await screen.findByRole('alert')).textContent).toBe(
      'Phiên bảo vệ phải diễn ra vào ngày 01/05/2027 theo cấu hình đợt KLTN.',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Xếp lịch bảo vệ' }))
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(mocks.schedule).not.toHaveBeenCalled()
  })

  it('không áp ràng buộc ngày cố định khi không có cấu hình, UTC giữ đúng ngày Việt Nam', () => {
    expect(defenseDateError(undefined, '2027-04-30T23:30', '2027-05-01T00:30')).toBe('')
    expect(defenseInstant('2027-05-01T00:00', true).toISOString()).toBe('2027-04-30T17:00:00.000Z')
    expect(defenseInstant('2027-05-01T23:59', true).toISOString()).toBe('2027-05-01T16:59:00.000Z')
    expect(formatDefenseTime('2027-04-30T17:15:00', true)).toBe('01/05/2027 00:15:00')
  })

  it('giữ dữ liệu dashboard khi cập nhật nền thất bại và tránh request trùng', async () => {
    render(
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>,
    )
    await screen.findByText('123', { selector: '.metric-card__value' })
    let rejectRefresh!: (reason: Error) => void
    mocks.summary.mockImplementationOnce(
      () =>
        new Promise((_, reject) => {
          rejectRefresh = reject
        }),
    )
    fireEvent(window, new Event('focus'))
    fireEvent(window, new Event('focus'))
    expect(mocks.summary).toHaveBeenCalledTimes(2)
    expect(screen.getByText('123', { selector: '.metric-card__value' })).toBeTruthy()
    await act(async () => {
      rejectRefresh(new Error('offline'))
    })
    expect(await screen.findByText(/Chưa cập nhật được dữ liệu mới/)).toBeTruthy()
    expect(screen.getByText('123', { selector: '.metric-card__value' })).toBeTruthy()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await waitFor(() => expect(screen.queryByText(/Chưa cập nhật được dữ liệu mới/)).toBeNull())
  })

  it('xác nhận trước khi xếp lịch và giữ dữ liệu nhập khi backend báo trùng phòng', async () => {
    mocks.schedule.mockRejectedValueOnce({
      response: {
        status: 409,
        data: { message: 'Phòng đã có lịch bảo vệ trong khoảng thời gian này.' },
      },
    })
    render(
      <DefenseSchedulePanel
        committees={[committee]}
        projects={[project]}
        defenses={[]}
        onScheduled={vi.fn()}
      />,
    )
    await fillSchedule()
    await userEvent.click(screen.getByRole('button', { name: 'Xếp lịch bảo vệ' }))
    expect(mocks.schedule).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận xếp lịch' }))
    expect((await screen.findByRole('alert')).textContent).toContain('Phòng đã có lịch')
    expect((screen.getByLabelText(/Địa điểm/) as HTMLInputElement).value).toBe(' A5-305 ')
    expect(mocks.schedule).toHaveBeenCalledWith(
      expect.objectContaining({ assignmentId: 51, committeeId: 11, location: 'A5-305' }),
    )
  })

  it('không gửi lịch nếu ngày đang nhập sai dù trước đó đã có giá trị hợp lệ', async () => {
    render(
      <DefenseSchedulePanel
        committees={[committee]}
        projects={[project]}
        defenses={[]}
        onScheduled={vi.fn()}
      />,
    )
    await fillSchedule()
    fireEvent.change(screen.getByLabelText(/Kết thúc/), {
      target: { value: '31/02/2099 10:00 AM' },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Xếp lịch bảo vệ' }))
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(mocks.schedule).not.toHaveBeenCalled()
  })

  it('hiển thị điều kiện phản biện chưa hoàn tất trước khi lưu', async () => {
    mocks.eligible.mockResolvedValue({
      eligible: false,
      reason:
        'Complete every reviewer assignment and submitted review score before scheduling a defense.',
    })
    render(
      <DefenseSchedulePanel
        committees={[committee]}
        projects={[project]}
        defenses={[]}
        onScheduled={vi.fn()}
      />,
    )
    fireEvent.change(screen.getByLabelText(/Hội đồng đã lưu/), { target: { value: '11' } })
    fireEvent.change(screen.getByLabelText(/Đề tài \/ nhóm/), { target: { value: '51' } })
    expect(await screen.findByText(/Đề tài chưa hoàn tất phản biện/)).toBeTruthy()
    expect(
      (screen.getByRole('button', { name: 'Xếp lịch bảo vệ' }) as HTMLButtonElement).disabled,
    ).toBe(true)
  })

  it('không cho tạo lại phiên đã hoàn tất và giải thích khi hết đề tài', () => {
    render(
      <DefenseSchedulePanel
        committees={[committee]}
        projects={[project]}
        defenses={[
          {
            id: 1,
            assignmentId: 51,
            groupId: 21,
            groupName: 'Nhóm A',
            topicId: 31,
            topicCode: 'DT01',
            topicTitle: project.topicTitle,
            committeeId: 11,
            committeeName: 'Hội đồng A',
            periodId: 1,
            periodName: 'Đợt A',
            location: 'A1',
            startsAt: '2099-02-20T09:00:00Z',
            endsAt: '2099-02-20T10:00:00Z',
            status: 'COMPLETED',
            version: 0,
          },
        ]}
        onScheduled={vi.fn()}
      />,
    )
    expect(screen.getByRole('option', { name: 'Không còn đề tài chưa xếp lịch' })).toBeTruthy()
    expect(
      (screen.getByRole('button', { name: 'Xếp lịch bảo vệ' }) as HTMLButtonElement).disabled,
    ).toBe(true)
  })

  it('phân biệt lịch đã lưu với lỗi tải lại danh sách', async () => {
    const refresh = vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValue(undefined)
    render(
      <DefenseSchedulePanel
        committees={[committee]}
        projects={[project]}
        defenses={[]}
        onScheduled={refresh}
      />,
    )
    await fillSchedule()
    await userEvent.click(screen.getByRole('button', { name: 'Xếp lịch bảo vệ' }))
    await userEvent.click(screen.getByRole('button', { name: 'Xác nhận xếp lịch' }))
    expect(await screen.findByText('Đã xếp lịch bảo vệ.')).toBeTruthy()
    expect((await screen.findByRole('alert')).textContent).toContain('Lịch đã lưu')
    await userEvent.click(screen.getByRole('button', { name: 'Tải lại lịch' }))
    await waitFor(() => expect(screen.queryByRole('alert')).toBeNull())
    expect(mocks.schedule).toHaveBeenCalledTimes(1)
  })

  it('giữ focus trong hộp xác nhận và hỗ trợ Escape', async () => {
    const cancel = vi.fn()
    render(
      <>
        <button>Phía sau</button>
        <ConfirmModal
          isOpen
          title="Xác nhận"
          message="Tiếp tục?"
          onCancel={cancel}
          onConfirm={vi.fn()}
        />
      </>,
    )
    const close = screen.getByRole('button', { name: 'Đóng hộp thoại' })
    expect(document.activeElement).toBe(close)
    await userEvent.tab({ shift: true })
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Xác nhận' }))
    await userEvent.keyboard('{Escape}')
    expect(cancel).toHaveBeenCalledOnce()
  })
})
