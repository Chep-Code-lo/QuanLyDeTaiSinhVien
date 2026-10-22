import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
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
    listAssignments: mocks.assignments,
    scheduleDefense: mocks.scheduleDefense,
    saveCommittee: mocks.saveCommittee,
    assign: mocks.assign,
    assignBatch: mocks.assignBatch,
    defenseEligibility: mocks.eligibility,
  },
}))
vi.mock('../src/api/lecturers', () => ({ lecturerApi: { list: mocks.lecturers } }))
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

  it('xếp lịch bằng hội đồng đã lưu rồi phân công chấm riêng, không cần chọn phiên bảo vệ', async () => {
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
    await userEvent.click(screen.getByRole('tab', { name: 'Hội đồng bảo vệ' }))
    fireEvent.change(screen.getByLabelText('Hội đồng đã tạo'), {
      target: { value: String(committee.id) },
    })
    await userEvent.click(
      screen.getByRole('button', { name: 'Phân công chấm cho hội đồng đã lưu' }),
    )
    await userEvent.click(screen.getByRole('button', { name: /Đề tài tham gia bảo vệ/ }))
    await userEvent.click(screen.getByRole('checkbox'))
    fireEvent.change(screen.getByLabelText(/Hạn chót chấm điểm/), {
      target: { value: '21/02/2099 10:00' },
    })
    await userEvent.click(screen.getByRole('button', { name: 'Lưu phân công' }))
    await waitFor(() => expect(mocks.assignBatch).toHaveBeenCalledOnce())
    expect(mocks.assignBatch.mock.calls[0][0]).toHaveLength(3)
    expect(mocks.assignBatch.mock.calls[0][0]).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ assignmentId: project.id, defenseId: 71 }),
      ]),
    )
    expect(mocks.saveCommittee).not.toHaveBeenCalled()
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
