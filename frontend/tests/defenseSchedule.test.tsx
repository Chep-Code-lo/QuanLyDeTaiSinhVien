import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EvaluatorAssignmentPage } from '../src/pages/EvaluatorAssignmentPage'

const mocks = vi.hoisted(() => ({
  periods: vi.fn(),
  projects: vi.fn(),
  components: vi.fn(),
  defenses: vi.fn(),
  committees: vi.fn(),
  rubrics: vi.fn(),
  assignments: vi.fn(),
  scheduleDefense: vi.fn(),
  saveCommittee: vi.fn(),
  assign: vi.fn(),
  assignBatch: vi.fn(),
  eligibility: vi.fn(),
  lecturers: vi.fn(),
  showError: vi.fn(),
  showSuccess: vi.fn(),
}))

vi.mock('../src/api/academic', () => ({
  academicApi: {
    getRegistrationPeriods: mocks.periods,
    getProjectAssignments: mocks.projects,
  },
}))
vi.mock('../src/api/evaluation', () => ({
  evaluationApi: {
    listComponents: mocks.components,
    listDefenses: mocks.defenses,
    listCommittees: mocks.committees,
    listRubrics: mocks.rubrics,
    reviewProgress: async () => ({
      assignedCount: 3,
      submittedCount: 1,
      draftCount: 1,
      notStartedCount: 1,
    }),
    listAssignments: mocks.assignments,
    scheduleDefense: mocks.scheduleDefense,
    saveCommittee: mocks.saveCommittee,
    assign: mocks.assign,
    assignBatch: mocks.assignBatch,
    defenseEligibility: mocks.eligibility,
  },
}))
vi.mock('../src/api/lecturers', () => ({ lecturerApi: { list: mocks.lecturers } }))
const academicManager = vi.hoisted(() => ({
  roles: ['LECTURER'],
  activeAppointments: [{ positionCode: 'DEAN', facultyId: 1, startsAt: '2020-01-01T00:00:00Z' }],
}))
vi.mock('../src/context/AuthContext', () => ({ useAuth: () => ({ user: academicManager }) }))
vi.mock('../src/context/ToastContext', () => ({
  useToast: () => ({ showError: mocks.showError, showSuccess: mocks.showSuccess }),
}))

const page = (content: unknown[]) => ({
  content,
  totalPages: 1,
  totalElements: content.length,
  page: 0,
  size: 100,
})
const period = {
  id: 1,
  facultyId: 1,
  facultyName: 'Khoa CNTT',
  semesterId: 1,
  academicYear: '2099-2100',
  semesterNo: 1,
  name: 'Đợt Khóa luận',
  projectType: 'GRADUATION',
  status: 'ACTIVE',
  proposalStartAt: '2099-01-01T00:00:00Z',
  proposalEndAt: '2099-01-02T00:00:00Z',
  registrationStartAt: '2099-01-02T00:00:00Z',
  registrationEndAt: '2099-01-03T00:00:00Z',
  reviewDeadlineAt: '2099-01-04T00:00:00Z',
  defenseDate: '2099-02-20',
  minGroupMembers: 1,
  maxGroupMembers: 3,
  invitationExpiryHours: 72,
  minCommitteeMembers: 3,
  maxCommitteeMembers: 5,
  version: 0,
}
const project = {
  id: 51,
  periodId: 1,
  periodName: period.name,
  groupId: 21,
  groupName: 'Nhóm Alpha',
  topicId: 31,
  topicCode: 'KLTN-01',
  topicTitle: 'Hệ thống quản lý bảo vệ',
  status: 'IN_PROGRESS',
}
const committee = {
  id: 11,
  periodId: 1,
  name: 'Hội đồng chấm',
  status: 'DRAFT' as const,
  rubricId: 7,
  version: 0,
  members: [
    {
      id: 1,
      lecturerId: 3,
      lecturerCode: 'GV03',
      lecturerName: 'Giảng viên 3',
      slotNo: 1,
      role: 'CHAIR' as const,
      roleName: 'Chủ tịch',
    },
    {
      id: 2,
      lecturerId: 4,
      lecturerCode: 'GV04',
      lecturerName: 'Giảng viên 4',
      slotNo: 2,
      role: 'SECRETARY' as const,
      roleName: 'Thư ký',
    },
    {
      id: 3,
      lecturerId: 5,
      lecturerCode: 'GV05',
      lecturerName: 'Giảng viên 5',
      slotNo: 3,
      role: 'MEMBER' as const,
      roleName: 'Ủy viên',
    },
  ],
}

let scheduled: Record<string, unknown>[]

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
})
beforeEach(() => {
  scheduled = []
  mocks.showError.mockReset()
  mocks.showSuccess.mockReset()
  mocks.saveCommittee.mockReset()
  mocks.assign.mockReset()
  mocks.assign.mockResolvedValue(81)
  mocks.assignBatch.mockReset()
  mocks.assignBatch.mockResolvedValue({ assignedCount: 3, skippedCount: 0 })
  mocks.eligibility.mockReset()
  mocks.eligibility.mockResolvedValue({ eligible: true, reason: null })
  mocks.periods.mockResolvedValue(page([period]))
  mocks.projects.mockResolvedValue(page([project]))
  mocks.components.mockResolvedValue([
    {
      id: 5,
      periodId: 1,
      code: 'HD',
      name: 'Hội đồng',
      componentType: 'COMMITTEE',
      status: 'LOCKED',
    },
  ])
  mocks.committees.mockResolvedValue(page([committee]))
  mocks.rubrics.mockResolvedValue([
    {
      id: 7,
      componentId: 5,
      componentCode: 'HD',
      componentName: 'Hội đồng',
      name: 'Tiêu chí hội đồng',
      versionNo: 1,
      status: 'LOCKED',
      criteria: [{ id: 1, name: 'Kết quả', description: '', maxScore: 10, sortOrder: 1 }],
    },
  ])
  mocks.assignments.mockResolvedValue(page([]))
  mocks.lecturers.mockResolvedValue(page([]))
  mocks.defenses.mockImplementation(async () => page(scheduled))
  mocks.scheduleDefense.mockImplementation(async (payload) => {
    mocks.committees.mockResolvedValue(page([{ ...committee, status: 'CONFIRMED', version: 1 }]))
    scheduled = [
      {
        id: 71,
        assignmentId: project.id,
        groupId: project.groupId,
        groupName: project.groupName,
        topicId: project.topicId,
        topicCode: project.topicCode,
        topicTitle: project.topicTitle,
        committeeId: committee.id,
        committeeName: committee.name,
        periodId: period.id,
        periodName: period.name,
        location: payload.location,
        startsAt: payload.startsAt,
        endsAt: payload.endsAt,
        status: 'SCHEDULED',
        version: 0,
      },
    ]
    return 71
  })
})

afterEach(cleanup)

async function openSchedule() {
  await screen.findByLabelText('Hội đồng đã tạo')
  await waitFor(() => expect(screen.queryByText('Đang tải dữ liệu phân công...')).toBeNull())
  await userEvent.click(screen.getByRole('tab', { name: 'Lịch bảo vệ' }))
  fireEvent.change(await screen.findByLabelText('Hội đồng đã lưu *'), {
    target: { value: String(committee.id) },
  })
}

async function fillSchedule() {
  fireEvent.change(screen.getByLabelText(/Đề tài \/ nhóm/), {
    target: { value: String(project.id) },
  })
  fireEvent.change(screen.getByLabelText(/Địa điểm/), { target: { value: 'Phòng A101' } })
  fireEvent.change(screen.getByLabelText(/Bắt đầu/), { target: { value: '20/02/2099 09:00' } })
  fireEvent.change(screen.getByLabelText(/Kết thúc/), { target: { value: '20/02/2099 10:00' } })
  await waitFor(() =>
    expect(
      (screen.getByRole('button', { name: 'Xếp lịch bảo vệ' }) as HTMLButtonElement).disabled,
    ).toBe(false),
  )
}

async function confirmSchedule() {
  await userEvent.click(screen.getByRole('button', { name: 'Xếp lịch bảo vệ' }))
  await userEvent.click(screen.getByRole('button', { name: 'Xác nhận xếp lịch' }))
}

describe('Xếp lịch bảo vệ', () => {
  it('giải thích đề tài đã giao chưa có lịch bảo vệ và mở được trang xếp lịch', async () => {
    render(<EvaluatorAssignmentPage />)
    await screen.findByLabelText('Hội đồng đã tạo')
    await waitFor(() => expect(screen.queryByText('Đang tải dữ liệu phân công...')).toBeNull())
    fireEvent.change(screen.getByLabelText('Hội đồng đã tạo'), {
      target: { value: String(committee.id) },
    })
    await userEvent.click(
      screen.getByRole('button', { name: 'Phân công chấm cho hội đồng đã lưu' }),
    )
    await userEvent.click(screen.getByRole('button', { name: /Đề tài tham gia bảo vệ/ }))
    fireEvent.change(screen.getByLabelText('Tìm đề tài'), { target: { value: project.topicCode } })
    await screen.findByText('Chưa xếp lịch bảo vệ.')
    expect(screen.queryByRole('checkbox')).toBeNull()
    expect(mocks.assignBatch).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Mở lịch bảo vệ' }))
    expect(screen.getByRole('tab', { name: 'Lịch bảo vệ' }).getAttribute('aria-selected')).toBe(
      'true',
    )
    await screen.findByRole('option', { name: new RegExp(project.topicCode) })
  })
  it.each(['SCHEDULED', 'COMPLETED', 'CANCELLED'] as const)(
    'không phân công nhầm đề tài có lịch khác phạm vi: %s',
    async (status) => {
      mocks.defenses.mockResolvedValue(
        page([
          {
            id: 71,
            assignmentId: project.id,
            committeeId: 99,
            committeeName: 'Hội đồng khác',
            status,
          },
        ]),
      )
      render(<EvaluatorAssignmentPage />)
      await screen.findByLabelText('Hội đồng đã tạo')
      await waitFor(() => expect(screen.queryByText('Đang tải dữ liệu phân công...')).toBeNull())
      fireEvent.change(screen.getByLabelText('Hội đồng đã tạo'), {
        target: { value: String(committee.id) },
      })
      await userEvent.click(
        screen.getByRole('button', { name: 'Phân công chấm cho hội đồng đã lưu' }),
      )
      await userEvent.click(screen.getByRole('button', { name: /Đề tài tham gia bảo vệ/ }))
      fireEvent.change(screen.getByLabelText('Tìm đề tài'), {
        target: { value: project.topicCode },
      })
      await screen.findByText(
        status === 'SCHEDULED'
          ? 'Đã xếp lịch ở hội đồng khác: Hội đồng khác.'
          : status === 'COMPLETED'
            ? 'Lịch bảo vệ đã hoàn thành.'
            : 'Lịch bảo vệ đã hủy.',
      )
      expect(screen.queryByRole('checkbox')).toBeNull()
      expect(mocks.assignBatch).not.toHaveBeenCalled()
    },
  )
  it('kiểm tra lại phòng/khung giờ và bỏ qua kết quả cũ đến muộn', async () => {
    render(<EvaluatorAssignmentPage />)
    await openSchedule()
    await fillSchedule()
    expect(mocks.eligibility).toHaveBeenLastCalledWith(project.id, committee.id, {
      location: 'Phòng A101',
      startsAt: '2099-02-20T02:00:00.000Z',
      endsAt: '2099-02-20T03:00:00.000Z',
    })
    let resolveOld!: (value: { eligible: boolean; reason: string | null }) => void
    mocks.eligibility.mockImplementation((_assignment, _committee, proposed) =>
      proposed?.location === 'A102'
        ? new Promise((resolve) => {
            resolveOld = resolve
          })
        : Promise.resolve({
            eligible: false,
            reason: 'Phòng đã có lịch bảo vệ trong khoảng thời gian này.',
          }),
    )
    fireEvent.change(screen.getByLabelText(/Địa điểm/), { target: { value: 'A102' } })
    await waitFor(() => expect(resolveOld).toBeDefined())
    fireEvent.change(screen.getByLabelText(/Địa điểm/), { target: { value: 'A103' } })
    await screen.findByText('Phòng đã có lịch bảo vệ trong khoảng thời gian này.')
    await act(async () => resolveOld({ eligible: true, reason: null }))
    expect(
      (screen.getByRole('button', { name: 'Xếp lịch bảo vệ' }) as HTMLButtonElement).disabled,
    ).toBe(true)
    expect(mocks.scheduleDefense).not.toHaveBeenCalled()
  })

  it('báo rõ phản biện chưa khóa trước khi người dùng bấm xếp lịch', async () => {
    mocks.components.mockResolvedValue([
      {
        id: 5,
        periodId: 1,
        code: 'HD',
        name: 'Hội đồng',
        componentType: 'COMMITTEE',
        status: 'LOCKED',
      },
      {
        id: 6,
        periodId: 1,
        code: 'PB',
        name: 'Phản biện',
        componentType: 'REVIEW',
        status: 'DRAFT',
      },
    ])
    render(<EvaluatorAssignmentPage />)
    await openSchedule()
    expect(screen.getByText(/Chưa thể xếp lịch: phần Phản biện độc lập còn bản nháp:/)).toBeTruthy()
    expect(screen.getByText(/“Phản biện” \(PB\)/)).toBeTruthy()
    expect(screen.getByText(/Bộ tiêu chí hội đồng đã khóa là cấu hình riêng/)).toBeTruthy()
    expect(
      (screen.getByRole('button', { name: 'Xếp lịch bảo vệ' }) as HTMLButtonElement).disabled,
    ).toBe(true)
    expect(mocks.scheduleDefense).not.toHaveBeenCalled()
  })

  it('xếp lịch xong mở phân công chấm với đúng đề tài và hội đồng, không quay lại tab cũ', async () => {
    render(<EvaluatorAssignmentPage />)
    await openSchedule()
    await fillSchedule()
    await confirmSchedule()
    await screen.findByText('Đã xếp lịch bảo vệ.')
    expect(mocks.scheduleDefense).toHaveBeenCalledWith({
      assignmentId: project.id,
      committeeId: committee.id,
      location: 'Phòng A101',
      startsAt: '2099-02-20T02:00:00.000Z',
      endsAt: '2099-02-20T03:00:00.000Z',
    })
    expect(screen.queryByLabelText('Phiên bảo vệ')).toBeNull()
    expect(screen.getByText('Phòng A101')).toBeTruthy()
    const dialog = await screen.findByRole('dialog', { name: 'Phân công chấm · KLTN-01' })
    expect(screen.getByRole('tab', { name: 'Lịch bảo vệ' }).getAttribute('aria-selected')).toBe(
      'true',
    )
    expect(within(dialog).getByText(/Hội đồng: Hội đồng chấm/)).toBeTruthy()
    expect(within(dialog).getByText('KLTN-01 · Nhóm Alpha')).toBeTruthy()
    fireEvent.change(screen.getByLabelText(/Hạn chót chấm điểm/), {
      target: { value: '21/02/2099 10:00' },
    })
    await userEvent.click(
      within(dialog).getByRole('button', { name: 'Phân công chấm', exact: true }),
    )
    await waitFor(() => expect(mocks.assignBatch).toHaveBeenCalledOnce())
    expect(mocks.assignBatch.mock.calls[0][0]).toHaveLength(3)
    expect(mocks.assignBatch.mock.calls[0][0]).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ assignmentId: project.id, defenseId: 71 }),
      ]),
    )
    expect(mocks.saveCommittee).not.toHaveBeenCalled()
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('Lưu & xếp lịch chọn sẵn đúng hội đồng vừa lưu', async () => {
    mocks.saveCommittee.mockResolvedValue({ ...committee, version: 1 })
    render(<EvaluatorAssignmentPage />)
    const picker = await screen.findByLabelText('Hội đồng đã tạo')
    await waitFor(() => expect(screen.queryByText('Đang tải dữ liệu phân công...')).toBeNull())
    fireEvent.change(picker, { target: { value: String(committee.id) } })
    await userEvent.click(screen.getByRole('button', { name: 'Lưu & xếp lịch' }))
    const schedule = (await screen.findByLabelText('Hội đồng đã lưu *')) as HTMLSelectElement
    expect(schedule.value).toBe(String(committee.id))
    expect(mocks.saveCommittee).toHaveBeenCalledOnce()
    expect(mocks.scheduleDefense).not.toHaveBeenCalled()
    expect(mocks.assignBatch).not.toHaveBeenCalled()
  })

  it('phân công lỗi giữ lịch đã lưu, thử lại không xếp lịch trùng và chặn gửi đồng thời', async () => {
    let reject!: (error: unknown) => void
    mocks.assignBatch.mockImplementationOnce(
      () =>
        new Promise((_, fail) => {
          reject = fail
        }),
    )
    render(<EvaluatorAssignmentPage />)
    await openSchedule()
    await fillSchedule()
    await confirmSchedule()
    const dialog = await screen.findByRole('dialog', { name: 'Phân công chấm · KLTN-01' })
    fireEvent.change(within(dialog).getByLabelText(/Hạn chót chấm điểm/), {
      target: { value: '21/02/2099 10:00' },
    })
    await userEvent.click(
      within(dialog).getByRole('button', { name: 'Phân công chấm', exact: true }),
    )
    fireEvent.submit(dialog.querySelector('form')!)
    fireEvent(dialog, new Event('cancel', { cancelable: true }))
    expect(mocks.assignBatch).toHaveBeenCalledOnce()
    expect(
      (within(dialog).getByRole('button', { name: 'Để sau' }) as HTMLButtonElement).disabled,
    ).toBe(true)
    await act(async () => reject({ response: { data: { message: 'Phân công không hợp lệ.' } } }))
    expect((await within(dialog).findByRole('alert')).textContent).toContain(
      'Lịch bảo vệ vẫn được giữ lại',
    )
    expect((within(dialog).getByLabelText(/Hạn chót chấm điểm/) as HTMLInputElement).value).toBe(
      '21/02/2099 10:00',
    )
    await userEvent.click(
      within(dialog).getByRole('button', { name: 'Phân công chấm', exact: true }),
    )
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(mocks.scheduleDefense).toHaveBeenCalledOnce()
    expect(mocks.assignBatch).toHaveBeenCalledTimes(2)
    expect(mocks.assignBatch.mock.calls[1][0]).toEqual(
      committee.members.map((member) => ({
        assignmentId: project.id,
        defenseId: 71,
        rubricId: 7,
        lecturerId: member.lecturerId,
        // datetime-local follows the browser timezone, including UTC on CI.
        deadlineAt: new Date(2099, 1, 21, 10, 0).toISOString(),
      })),
    )
  })

  it('Để sau không tạo phân công, mở lại trực tiếp từ lịch đã xếp', async () => {
    render(<EvaluatorAssignmentPage />)
    await openSchedule()
    await fillSchedule()
    await confirmSchedule()
    await screen.findByRole('dialog', { name: 'Phân công chấm · KLTN-01' })
    await userEvent.click(screen.getByRole('button', { name: 'Để sau' }))
    expect(mocks.assignBatch).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Phân công chấm', exact: true }))
    await screen.findByRole('dialog', { name: 'Phân công chấm · KLTN-01' })
    expect(mocks.scheduleDefense).toHaveBeenCalledOnce()
  })

  it('lịch đã lưu nhưng tải lại lỗi: chỉ tải lại dữ liệu rồi mở phân công, không tạo lịch trùng', async () => {
    mocks.defenses.mockResolvedValueOnce(page([])).mockRejectedValueOnce(new Error('offline'))
    render(<EvaluatorAssignmentPage />)
    await openSchedule()
    await fillSchedule()
    await confirmSchedule()
    await screen.findByText(
      'Lịch đã lưu nhưng chưa tải lại được danh sách. Bấm tải lại lịch trước khi tiếp tục.',
    )
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(mocks.scheduleDefense).toHaveBeenCalledOnce()
    await userEvent.click(screen.getByRole('button', { name: 'Tải lại lịch' }))
    await screen.findByRole('dialog', { name: 'Phân công chấm · KLTN-01' })
    expect(mocks.scheduleDefense).toHaveBeenCalledOnce()
    expect(mocks.assignBatch).not.toHaveBeenCalled()
  })

  it('không phân công khi hạn chấm đã qua', async () => {
    render(<EvaluatorAssignmentPage />)
    await openSchedule()
    await fillSchedule()
    await confirmSchedule()
    const dialog = await screen.findByRole('dialog', { name: 'Phân công chấm · KLTN-01' })
    fireEvent.change(within(dialog).getByLabelText(/Hạn chót chấm điểm/), {
      target: { value: '01/01/2000 10:00' },
    })
    await userEvent.click(
      within(dialog).getByRole('button', { name: 'Phân công chấm', exact: true }),
    )
    expect((await within(dialog).findByRole('alert')).textContent).toContain(
      'Hạn chót chấm điểm phải sau thời điểm hiện tại.',
    )
    expect(mocks.assignBatch).not.toHaveBeenCalled()
    expect(mocks.scheduleDefense).toHaveBeenCalledOnce()
  })

  it('giải thích xung đột giảng viên hướng dẫn tại chỗ, giữ dữ liệu để sửa và thử lại', async () => {
    mocks.scheduleDefense.mockRejectedValueOnce({
      response: {
        status: 400,
        data: {
          message: 'A topic supervisor cannot be on its defense committee.',
        },
      },
    })
    render(<EvaluatorAssignmentPage />)
    await openSchedule()
    await fillSchedule()
    await confirmSchedule()
    expect((await screen.findByRole('alert')).textContent).toContain('giảng viên hướng dẫn')
    expect(mocks.showError).not.toHaveBeenCalled()
    expect((screen.getByLabelText(/Địa điểm/) as HTMLInputElement).value).toBe('Phòng A101')
    await confirmSchedule()
    await screen.findByText('Đã xếp lịch bảo vệ.')
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('xóa chủ tịch trong hội đồng bốn người rồi chuyển vai trò cho người còn lại', async () => {
    mocks.saveCommittee.mockResolvedValueOnce({ ...committee, version: 1 })
    mocks.committees.mockResolvedValue(page([{
      ...committee,
      members: [...committee.members, {
        id: 4, lecturerId: 6, lecturerCode: 'GV06', lecturerName: 'Giảng viên 6',
        slotNo: 4, role: 'MEMBER', roleName: 'Ủy viên',
      }],
    }]))
    render(<EvaluatorAssignmentPage />)
    const picker = await screen.findByLabelText('Hội đồng đã tạo')
    await waitFor(() => expect(screen.queryByText('Đang tải dữ liệu phân công...')).toBeNull())
    fireEvent.change(picker, { target: { value: String(committee.id) } })
    await userEvent.click(screen.getByRole('button', { name: 'Xóa thành viên 1', exact: true }))
    const role = screen.getByRole('combobox', { name: 'Vai trò thành viên 2' })
    expect(within(role).getByRole('option', { name: 'Chủ tịch' })).toBeTruthy()
    await userEvent.selectOptions(role, 'Chủ tịch')
    expect((role as HTMLSelectElement).value).toBe('Chủ tịch')
    await userEvent.click(screen.getByRole('button', { name: 'Lưu hội đồng' }))
    await waitFor(() => expect(mocks.saveCommittee).toHaveBeenCalledWith(
      committee.id,
      expect.objectContaining({ members: [
        { lecturerId: 4, roleName: 'Thư ký' },
        { lecturerId: 5, roleName: 'Chủ tịch' },
        { lecturerId: 6, roleName: 'Ủy viên' },
      ] }),
    ))
  })

  it('lỗi lưu cấu hình không xếp lịch; sửa và lưu hội đồng thành công trước khi chuyển qua lịch', async () => {
    mocks.saveCommittee.mockRejectedValueOnce({
      response: { status: 400, data: { message: 'Hội đồng cần đúng một Chủ tịch và một Thư ký.' } },
    })
    render(<EvaluatorAssignmentPage />)
    const picker = await screen.findByLabelText('Hội đồng đã tạo')
    await waitFor(() => expect(screen.queryByText('Đang tải dữ liệu phân công...')).toBeNull())
    fireEvent.change(picker, { target: { value: String(committee.id) } })
    fireEvent.change(screen.getByLabelText('Vai trò thành viên 1'), {
      target: { value: 'Ủy viên' },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Lưu hội đồng' }))
    await waitFor(() =>
      expect(mocks.showError).toHaveBeenCalledWith(expect.stringContaining('Chủ tịch')),
    )
    expect(mocks.scheduleDefense).not.toHaveBeenCalled()
    expect(mocks.saveCommittee).toHaveBeenCalledWith(
      committee.id,
      expect.objectContaining({
        members: expect.arrayContaining([{ lecturerId: 3, roleName: 'Ủy viên' }]),
        version: 0,
      }),
    )
    mocks.saveCommittee.mockResolvedValueOnce({ ...committee, name: 'Hội đồng mới', version: 1 })
    fireEvent.change(screen.getByLabelText('Vai trò thành viên 1'), {
      target: { value: 'Chủ tịch' },
    })
    fireEvent.change(screen.getByLabelText(/^Hội đồng \*/), { target: { value: 'Hội đồng mới' } })
    await userEvent.click(screen.getByRole('button', { name: 'Lưu hội đồng' }))
    await waitFor(() =>
      expect(mocks.showSuccess).toHaveBeenCalledWith(expect.stringContaining('Hội đồng mới')),
    )
    await openSchedule()
    await fillSchedule()
    await confirmSchedule()
    await screen.findByText('Đã xếp lịch bảo vệ.')
    expect(mocks.saveCommittee).toHaveBeenCalledTimes(2)
    expect(mocks.scheduleDefense).toHaveBeenCalledOnce()
  })
})
