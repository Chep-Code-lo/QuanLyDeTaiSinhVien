import { afterEach, beforeAll, beforeEach, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EvaluationResultsPage } from '../src/pages/EvaluationResultsPage'

const mocks = vi.hoisted(() => ({
  projects: vi.fn(),
  results: vi.fn(),
  actions: vi.fn(),
  finalize: vi.fn(),
  publish: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
}))
vi.mock('../src/api/academic', () => ({ academicApi: { getProjectAssignments: mocks.projects } }))
vi.mock('../src/api/evaluation', () => ({
  evaluationApi: {
    listResults: mocks.results,
    resultActions: mocks.actions,
    finalizeResult: mocks.finalize,
    publishResult: mocks.publish,
  },
}))
vi.mock('../src/context/ToastContext', () => ({
  useToast: () => ({
    showSuccess: mocks.success,
    showError: mocks.error,
  }),
}))
const page = (content: unknown[], totalPages = 1) => ({
  content,
  page: 0,
  size: 10,
  totalPages,
  totalElements: content.length,
})
const project = (id = 1) => ({
  id,
  topicCode: `DT0${id}`,
  topicTitle: 'Đề tài',
  groupName: `Nhóm ${id}`,
  periodName: 'Đợt A',
})
const finalized = {
  id: 7,
  assignmentId: 1,
  versionNo: 1,
  finalScore: 8.5,
  status: 'FINALIZED',
  finalizedByName: 'Chủ tịch',
  finalizedAt: '2026-10-25T04:00:00Z',
  inputs: [{ id: 1, sheetId: 11, componentCode: 'REVIEW', sheetVersion: 1, scoreSnapshot: 9 }],
  members: [{ studentId: 1, studentCodeSnapshot: 'SV01', fullNameSnapshot: 'Sinh viên A' }],
}
beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
})
beforeEach(() => {
  Object.values(mocks).forEach((mock) => mock.mockReset())
  mocks.projects.mockResolvedValue(page([project(), project(2)], 2))
  mocks.results.mockResolvedValue(page([]))
  mocks.actions.mockResolvedValue({ canFinalize: true, canPublish: false })
  mocks.finalize.mockResolvedValue({ id: 7 })
  mocks.publish.mockResolvedValue({ id: 7 })
})
afterEach(cleanup)
async function selectProject() {
  render(<EvaluationResultsPage />)
  await userEvent.click(await screen.findByRole('button', { name: 'Xem điểm DT01' }))
  await screen.findByText(/Chưa có kết quả được chốt/)
}
it('xem điểm mở hộp thoại, khóa cuộn nền và trả focus về nút khi đóng', async () => {
  await selectProject()
  const dialog = screen.getByRole('dialog', { name: 'DT01 · Nhóm 1' })
  expect(dialog.hasAttribute('open')).toBe(true)
  expect(document.body.style.overflow).toBe('hidden')
  fireEvent(dialog, new Event('cancel', { cancelable: true }))
  expect(screen.queryByRole('dialog')).toBeNull()
  expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Xem điểm DT01' }))
  expect(document.body.style.overflow).toBe('')
  expect(mocks.finalize).not.toHaveBeenCalled()
  expect(mocks.publish).not.toHaveBeenCalled()
})
it('hủy xác nhận quay về xem điểm, không đóng hộp thoại hoặc chốt kết quả', async () => {
  await selectProject()
  await userEvent.click(screen.getByRole('button', { name: 'Chốt kết quả' }))
  const dialog = screen.getByRole('dialog', { name: 'Chốt kết quả' })
  expect(screen.getAllByRole('dialog')).toHaveLength(1)
  fireEvent(dialog, new Event('cancel', { cancelable: true }))
  expect(screen.getByRole('dialog', { name: 'DT01 · Nhóm 1' }).hasAttribute('open')).toBe(true)
  expect(mocks.finalize).not.toHaveBeenCalled()
  await userEvent.click(screen.getByRole('button', { name: 'Chốt kết quả' }))
  await userEvent.click(screen.getByRole('button', { name: 'Hủy', exact: true }))
  expect(screen.getByRole('dialog', { name: 'DT01 · Nhóm 1' })).toBeTruthy()
  expect(mocks.finalize).not.toHaveBeenCalled()
})
it('chốt sau xác nhận, chặn gửi trùng và tải lại kết quả đã chốt', async () => {
  let resolve!: (value: unknown) => void
  mocks.finalize.mockImplementation(
    () =>
      new Promise((done) => {
        resolve = done
      }),
  )
  await selectProject()
  expect(mocks.projects).toHaveBeenCalledWith(undefined, 0, 10)
  expect(mocks.results).toHaveBeenCalledWith({ assignmentId: 1, page: 0, size: 10 })
  await userEvent.click(screen.getByRole('button', { name: 'Chốt kết quả' }))
  expect(mocks.finalize).not.toHaveBeenCalled()
  const confirm = within(screen.getByRole('dialog')).getByRole('button', { name: 'Xác nhận' })
  fireEvent.click(confirm)
  fireEvent.click(confirm)
  expect(mocks.finalize).toHaveBeenCalledExactlyOnceWith(1)
  expect(
    (screen.getByRole('button', { name: 'Đóng hộp thoại kết quả' }) as HTMLButtonElement).disabled,
  ).toBe(true)
  fireEvent(screen.getByRole('dialog'), new Event('cancel', { cancelable: true }))
  expect(screen.getByRole('dialog', { name: 'Chốt kết quả' })).toBeTruthy()
  mocks.results.mockResolvedValue(page([finalized]))
  await act(async () => resolve({ id: 7 }))
  await screen.findByText('8.50/10')
  expect(screen.queryByRole('button', { name: 'Công bố kết quả' })).toBeNull()
  expect((screen.getByRole('button', { name: 'Chốt kết quả' }) as HTMLButtonElement).disabled).toBe(
    true,
  )
})
it('người quản lý chỉ công bố sau xác nhận và kết quả chuyển sang đã công bố', async () => {
  mocks.actions.mockResolvedValue({ canFinalize: false, canPublish: true })
  mocks.results.mockResolvedValue(page([finalized]))
  render(<EvaluationResultsPage />)
  await userEvent.click(await screen.findByRole('button', { name: 'Xem điểm DT01' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Công bố kết quả' }))
  expect(screen.queryByRole('button', { name: 'Chốt kết quả' })).toBeNull()
  expect(mocks.publish).not.toHaveBeenCalled()
  mocks.results.mockResolvedValue(
    page([
      {
        ...finalized,
        status: 'PUBLISHED',
        publishedByName: 'Admin',
        publishedAt: '2026-10-25T05:00:00Z',
      },
    ]),
  )
  await userEvent.click(
    within(screen.getByRole('dialog')).getByRole('button', { name: 'Xác nhận' }),
  )
  await screen.findByText('Kết quả lần 1 · Đã công bố')
  expect(mocks.publish).toHaveBeenCalledExactlyOnceWith(7)
  expect(screen.queryByRole('button', { name: 'Công bố kết quả' })).toBeNull()
})
it('giảng viên chỉ được đọc không có nút chốt hoặc công bố', async () => {
  mocks.actions.mockResolvedValue({ canFinalize: false, canPublish: false })
  mocks.results.mockResolvedValue(page([finalized]))
  render(<EvaluationResultsPage />)
  await userEvent.click(await screen.findByRole('button', { name: 'Xem điểm DT01' }))
  await screen.findByText('8.50/10')
  expect(screen.queryByRole('button', { name: 'Chốt kết quả' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Công bố kết quả' })).toBeNull()
})
it('thiếu phiếu bị từ chối, giữ trạng thái chưa chốt và hiện lỗi', async () => {
  mocks.finalize.mockRejectedValue({
    response: { status: 409, data: { message: 'Chưa đủ phiếu điểm.' } },
  })
  await selectProject()
  await userEvent.click(screen.getByRole('button', { name: 'Chốt kết quả' }))
  await userEvent.click(
    within(screen.getByRole('dialog')).getByRole('button', { name: 'Xác nhận' }),
  )
  await waitFor(() => expect(mocks.error).toHaveBeenCalledWith('Chưa đủ phiếu điểm.'))
  await screen.findByText(/Chưa có kết quả được chốt/)
  expect(mocks.success).not.toHaveBeenCalled()
})
it('bỏ qua phản hồi đề tài cũ đến muộn và phân trang ở server', async () => {
  let resolve!: (value: unknown) => void
  mocks.results.mockImplementation(({ assignmentId }: { assignmentId: number }) =>
    assignmentId === 1
      ? new Promise((done) => {
          resolve = done
        })
      : Promise.resolve(page([])),
  )
  render(<EvaluationResultsPage />)
  await userEvent.click(await screen.findByRole('button', { name: 'Xem điểm DT01' }))
  await userEvent.click(screen.getByRole('button', { name: 'Đóng hộp thoại kết quả' }))
  await userEvent.click(screen.getByRole('button', { name: 'Xem điểm DT02' }))
  await screen.findByText(/Chưa có kết quả được chốt/)
  await act(async () => resolve(page([finalized])))
  expect(screen.queryByText('8.50/10')).toBeNull()
  await userEvent.click(screen.getByRole('button', { name: 'Đóng hộp thoại kết quả' }))
  await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
  await waitFor(() => expect(mocks.projects).toHaveBeenLastCalledWith(undefined, 1, 10))
})
it('lỗi tải có thể thử lại và không cho phép thao tác khi chưa xác nhận quyền', async () => {
  mocks.actions.mockRejectedValueOnce({
    response: { data: { message: 'Không tải được quyền thao tác.' } },
  })
  render(<EvaluationResultsPage />)
  await userEvent.click(await screen.findByRole('button', { name: 'Xem điểm DT01' }))
  await screen.findByText('Không tải được quyền thao tác.')
  expect(screen.queryByRole('button', { name: 'Chốt kết quả' })).toBeNull()
  await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
  await screen.findByRole('button', { name: 'Chốt kết quả' })
})

it('hiển thị nhãn điểm bằng tiếng Việt, ẩn phiên bản kỹ thuật và phân trang một trang', async () => {
  mocks.results.mockResolvedValue(
    page([
      {
        ...finalized,
        inputs: [
          ...finalized.inputs,
          { id: 2, sheetId: 12, componentCode: 'DEFENSE', sheetVersion: 0, scoreSnapshot: 8 },
          { id: 3, sheetId: 13, componentCode: 'CUSTOM', sheetVersion: 2, scoreSnapshot: 9 },
        ],
      },
    ]),
  )
  render(<EvaluationResultsPage />)
  await userEvent.click(await screen.findByRole('button', { name: 'Xem điểm DT01' }))
  await screen.findByRole('heading', { name: 'Kết quả lần 1 · Đã chốt, chờ công bố' })
  expect(screen.getByText('Phản biện')).toBeTruthy()
  expect(screen.getByText('Bảo vệ trước hội đồng')).toBeTruthy()
  expect(screen.getByText('CUSTOM')).toBeTruthy()
  expect(screen.getByText('Phiếu số 12')).toBeTruthy()
  expect(screen.queryByText(/phiên bản 0/)).toBeNull()
  expect(screen.queryByRole('navigation', { name: 'Phân trang kết quả' })).toBeNull()
})
it('chỉ hiện phân trang kết quả khi có nhiều trang và lấy đúng trang từ backend', async () => {
  mocks.results.mockResolvedValue(page([finalized], 2))
  render(<EvaluationResultsPage />)
  await userEvent.click(await screen.findByRole('button', { name: 'Xem điểm DT01' }))
  const pager = await screen.findByRole('navigation', { name: 'Phân trang kết quả' })
  await userEvent.click(within(pager).getByRole('button', { name: 'Sau' }))
  await waitFor(() =>
    expect(mocks.results).toHaveBeenLastCalledWith({ assignmentId: 1, page: 1, size: 10 }),
  )
})
