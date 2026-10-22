import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EvaluatorAssignmentPage } from '../src/pages/EvaluatorAssignmentPage'

const mocks = vi.hoisted(() => ({ saveCommittee: vi.fn(), assignBatch: vi.fn(), success: vi.fn(), error: vi.fn(), periods: vi.fn() }))
const page = (content: unknown[]) => ({ content, totalPages: 1, totalElements: content.length, page: 0, size: 100 })
const committee = { id: 11, periodId: 1, name: 'Hội đồng A', rubricId: 7, version: 0, status: 'DRAFT', members: [
  { id: 1, lecturerId: 3, lecturerName: 'Giảng viên 3', lecturerCode: 'GV03', role: 'CHAIR', roleName: 'Chủ tịch', slotNo: 1 },
  { id: 2, lecturerId: 4, lecturerName: 'Giảng viên 4', lecturerCode: 'GV04', role: 'SECRETARY', roleName: 'Thư ký', slotNo: 2 },
  { id: 3, lecturerId: 5, lecturerName: 'Giảng viên 5', lecturerCode: 'GV05', role: 'MEMBER', roleName: 'Ủy viên', slotNo: 3 },
] }
vi.mock('../src/api/academic', () => ({ academicApi: {
  getRegistrationPeriods: mocks.periods,
  getProjectAssignments: async () => page([{ id: 51, topicId: 31, topicCode: 'DT01', topicTitle: 'Đề tài A', groupName: 'Nhóm A', status: 'IN_PROGRESS' }]),
} }))
vi.mock('../src/api/evaluation', () => ({ evaluationApi: {
  listComponents: async () => [{ id: 5, componentType: 'COMMITTEE', status: 'LOCKED' }],
  listRubrics: async () => [{ id: 7, componentId: 5, name: 'Bộ tiêu chí A', versionNo: 1, status: 'LOCKED', criteria: [{ id: 1, maxScore: 10 }] }],
  listDefenses: async () => page([{ id: 71, assignmentId: 51, committeeId: 11, status: 'SCHEDULED' }]),
  listCommittees: async () => page([committee]),
  listAssignments: async () => page([]),
  saveCommittee: mocks.saveCommittee, assignBatch: mocks.assignBatch,
} }))
vi.mock('../src/api/lecturers', () => ({ lecturerApi: { list: async () => page([]) } }))
vi.mock('../src/context/ToastContext', () => ({ useToast: () => ({ showSuccess: mocks.success, showError: mocks.error }) }))

beforeEach(() => {
  mocks.periods.mockReset(); mocks.periods.mockResolvedValue(page([{ id: 1, name: 'Đợt A', minCommitteeMembers: 3, maxCommitteeMembers: 5 }]))
  mocks.saveCommittee.mockReset(); mocks.saveCommittee.mockResolvedValue(committee)
  mocks.assignBatch.mockReset(); mocks.assignBatch.mockResolvedValue({ assignedCount: 3, skippedCount: 0 })
  mocks.success.mockReset(); mocks.error.mockReset()
})
afterEach(cleanup)

it('truyền defenseDate từ API đợt vào form lịch của hội đồng KLTN', async () => {
  mocks.periods.mockResolvedValue(page([{ id: 1, name: 'Đợt KLTN', projectType: 'GRADUATION', defenseDate: '2027-05-01' }]))
  render(<EvaluatorAssignmentPage />)
  await screen.findByRole('option', { name: 'Đợt KLTN' })
  await userEvent.click(screen.getByRole('tab', { name: 'Lịch bảo vệ' }))
  const picker = await screen.findByLabelText(/Hội đồng đã lưu/)
  fireEvent.change(picker, { target: { value: '11' } })
  expect((screen.getByLabelText(/Bắt đầu/) as HTMLInputElement).value).toBe('01/05/2027 08:00')
  expect((screen.getByLabelText(/Kết thúc/) as HTMLInputElement).value).toBe('01/05/2027 09:00')
})

async function selectSavedCommittee() {
  const picker = await screen.findByLabelText('Hội đồng đã tạo')
  await waitFor(() => expect(screen.queryByText('Đang tải dữ liệu phân công...')).toBeNull())
  fireEvent.change(picker, { target: { value: '11' } })
  await waitFor(() => expect((screen.getByRole('combobox', { name: /Bộ tiêu chí/ }) as HTMLSelectElement).value).toBe('7'))
}

it('lưu hội đồng riêng, không đòi phiên bảo vệ hoặc tự tạo phân công', async () => {
  render(<EvaluatorAssignmentPage />)
  await selectSavedCommittee()
  expect(screen.queryByLabelText('Phiên bảo vệ')).toBeNull()
  expect(screen.queryByLabelText(/Hạn chót chấm điểm/)).toBeNull()
  await userEvent.click(screen.getByRole('button', { name: 'Lưu hội đồng' }))
  await waitFor(() => expect(mocks.saveCommittee).toHaveBeenCalledOnce())
  expect(mocks.assignBatch).not.toHaveBeenCalled()
  expect(screen.getByRole('tab', { name: 'Lịch bảo vệ' })).toBeTruthy()
})

it('lỗi phân công theo lô giữ lựa chọn và không lưu lại hội đồng', async () => {
  mocks.assignBatch.mockRejectedValueOnce({ response: { data: { message: 'Phân công không hợp lệ.' } } })
  render(<EvaluatorAssignmentPage />)
  await selectSavedCommittee()
  await userEvent.click(screen.getByRole('button', { name: 'Phân công chấm cho hội đồng đã lưu' }))
  await userEvent.click(screen.getByRole('button', { name: /Đề tài tham gia bảo vệ/ }))
  await userEvent.click(screen.getByRole('checkbox'))
  fireEvent.change(screen.getByLabelText(/Hạn chót chấm điểm/), { target: { value: '21/02/2099 10:00 AM' } })
  await userEvent.click(screen.getByRole('button', { name: 'Lưu phân công' }))
  await waitFor(() => expect(mocks.error).toHaveBeenCalledWith('Phân công không hợp lệ.'))
  expect(mocks.success).not.toHaveBeenCalled()
  expect(mocks.saveCommittee).not.toHaveBeenCalled()
  expect(mocks.assignBatch.mock.calls[0][0]).toHaveLength(3)
  expect(mocks.assignBatch.mock.calls[0][0]).toEqual(expect.arrayContaining([expect.objectContaining({ assignmentId: 51, defenseId: 71 })]))
  expect(screen.getByText('Đã chọn 1 đề tài')).toBeTruthy()
})
