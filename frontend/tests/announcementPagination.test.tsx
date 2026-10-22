import { NotificationDetailDialog } from '../src/features/notifications/components/NotificationDetailDialog'
import { beforeAll, beforeEach, afterEach, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { AnnouncementsPage } from '../src/pages/AnnouncementsPage'

const mocks = vi.hoisted(() => ({
  list: vi.fn(),
  personal: vi.fn(),
  managed: vi.fn(),
  related: vi.fn(),
  read: vi.fn(),
  publish: vi.fn(),
  archive: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  error: vi.fn(),
  success: vi.fn(),
}))
vi.mock('../src/context/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 1, activeAppointments: [] },
    hasRole: (role: string) => role === 'ADMIN',
    hasAppointment: () => false,
  }),
}))
vi.mock('../src/context/ToastContext', () => ({
  useToast: () => ({ showError: mocks.error, showSuccess: mocks.success }),
}))
vi.mock('../src/api/academic', () => ({
  academicApi: {
    getFaculties: async () => [
      { id: 1, name: 'Khoa A' },
      { id: 2, name: 'Khoa B' },
    ],
  },
}))
vi.mock('../src/api/announcements', () => ({
  announcementApi: {
    list: mocks.list,
    listForManagement: mocks.managed,
    publish: mocks.publish,
    archive: mocks.archive,
    create: mocks.create,
    update: mocks.update,
  },
}))
vi.mock('../src/api/notifications', () => ({
  notificationApi: { list: mocks.personal, forAnnouncements: mocks.related, markRead: mocks.read },
}))
const item = (id: number, title = `Thông báo ${id}`) => ({
  id,
  facultyId: 1,
  title,
  content: `Nội dung ${id}`,
  createdByName: 'Người gửi',
  status: 'PUBLISHED',
  createdAt: '2026-10-22T01:00:00Z',
})
const page = (items: ReturnType<typeof item>[], count = 23) => ({
  content: items,
  totalPages: Math.ceil(count / 10),
  totalElements: count,
  page: 0,
  size: 10,
})
const mount = () =>
  render(
    <MemoryRouter>
      <AnnouncementsPage />
    </MemoryRouter>,
  )
beforeAll(() => {
  HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
    this.open = true
  })
  HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
    this.open = false
  })
})
beforeEach(() => {
  vi.resetAllMocks()
  mocks.list.mockResolvedValue(page([item(1)]))
  mocks.managed.mockResolvedValue(page([item(3)]))
  mocks.personal.mockResolvedValue(page([item(2)]))
  mocks.related.mockResolvedValue([])
})
afterEach(cleanup)

it('loads exactly one server page, resets page on search and page size changes', async () => {
  mount()
  await screen.findByRole('button', { name: 'Thông báo 1' })
  expect(mocks.list).toHaveBeenCalledTimes(1)
  expect(mocks.list).toHaveBeenLastCalledWith({ page: 0, size: 10, search: '' })
  expect(mocks.personal).not.toHaveBeenCalled()
  const nav = screen.getByRole('navigation', { name: 'Phân trang thông báo chung' })
  fireEvent.click(within(nav).getByRole('button', { name: 'Sau' }))
  await waitFor(() =>
    expect(mocks.list).toHaveBeenLastCalledWith({ page: 1, size: 10, search: '' }),
  )
  mocks.list.mockResolvedValue(page([item(99, 'Kết quả ngoài trang hiện tại')], 1))
  fireEvent.change(screen.getByRole('searchbox', { name: 'Tìm kiếm thông báo chung' }), {
    target: { value: 'ngoài trang' },
  })
  await screen.findByRole('button', { name: 'Kết quả ngoài trang hiện tại' })
  expect(mocks.list).toHaveBeenLastCalledWith({ page: 0, size: 10, search: 'ngoài trang' })
  fireEvent.change(screen.getByLabelText('Số thông báo mỗi trang - Thông báo chung'), {
    target: { value: '50' },
  })
  await waitFor(() =>
    expect(mocks.list).toHaveBeenLastCalledWith({ page: 0, size: 50, search: 'ngoài trang' }),
  )
})

it('ignores stale search responses and keeps the search box mounted while loading', async () => {
  mount()
  await screen.findByRole('button', { name: 'Thông báo 1' })
  let resolveOld!: (value: unknown) => void
  mocks.list.mockReturnValueOnce(
    new Promise((resolve) => {
      resolveOld = resolve
    }),
  )
  const input = screen.getByRole('searchbox', { name: 'Tìm kiếm thông báo chung' })
  fireEvent.change(input, { target: { value: 'cũ' } })
  await waitFor(() =>
    expect(mocks.list).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'cũ' })),
  )
  expect(screen.getByRole('status').textContent).toContain('Đang tải')
  mocks.list.mockResolvedValueOnce(page([item(9, 'Kết quả mới')], 1))
  fireEvent.change(input, { target: { value: 'mới' } })
  await screen.findByRole('button', { name: 'Kết quả mới' })
  await act(async () => resolveOld(page([item(8, 'Kết quả cũ')], 1)))
  expect(screen.queryByRole('button', { name: 'Kết quả cũ' })).toBeNull()
  expect(screen.getByRole('button', { name: 'Kết quả mới' })).toBeTruthy()
})

it('paginates and searches personal notifications without downloading the entire inbox', async () => {
  mount()
  fireEvent.click(screen.getByRole('tab', { name: 'Thông báo cá nhân' }))
  await screen.findByRole('button', { name: 'Thông báo 2' })
  expect(mocks.personal).toHaveBeenCalledTimes(1)
  expect(mocks.personal).toHaveBeenLastCalledWith(0, 10, '')
  fireEvent.change(screen.getByRole('searchbox', { name: 'Tìm kiếm thông báo cá nhân' }), {
    target: { value: 'riêng tư' },
  })
  await waitFor(() => expect(mocks.personal).toHaveBeenLastCalledWith(0, 10, 'riêng tư'))
})

it('resets management pagination on faculty and status changes', async () => {
  mount()
  await screen.findByRole('button', { name: 'Mở quản lý' })
  fireEvent.click(screen.getByRole('button', { name: 'Mở quản lý' }))
  await screen.findByText('Thông báo 3')
  const nav = screen.getByRole('navigation', { name: 'Phân trang quản lý thông báo khoa' })
  fireEvent.click(within(nav).getByRole('button', { name: 'Sau' }))
  await waitFor(() =>
    expect(mocks.managed).toHaveBeenLastCalledWith(
      expect.objectContaining({ facultyId: 1, page: 1 }),
    ),
  )
  fireEvent.change(screen.getByLabelText('Khoa quản lý'), { target: { value: '2' } })
  await waitFor(() =>
    expect(mocks.managed).toHaveBeenLastCalledWith(
      expect.objectContaining({ facultyId: 2, page: 0 }),
    ),
  )
  fireEvent.change(screen.getByLabelText('Trạng thái thông báo'), { target: { value: 'ARCHIVED' } })
  await waitFor(() =>
    expect(mocks.managed).toHaveBeenLastCalledWith(
      expect.objectContaining({ facultyId: 2, page: 0, status: 'ARCHIVED' }),
    ),
  )
})

it('shows errors with retry and a searchable empty state', async () => {
  mocks.list.mockRejectedValueOnce(new Error('Không thể tải dữ liệu'))
  mount()
  await screen.findByRole('alert')
  mocks.list.mockResolvedValueOnce(page([], 0))
  fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
  await screen.findByText('Không tìm thấy thông báo phù hợp.')
  expect(screen.getByRole('searchbox', { name: 'Tìm kiếm thông báo chung' })).toBeTruthy()
})

it('thông báo cũ hiển thị giờ Việt Nam thay cho mã múi giờ kỹ thuật', () => {
  const content = 'Bắt đầu: 26/10/2026 10:00. Kết thúc: 26/10/2026 11:00 (Asia/Ho_Chi_Minh).'
  const item = { title: 'Lịch bảo vệ đề tài', content, sentAt: '2026-10-23T01:00:00Z' }
  render(<NotificationDetailDialog item={item} onClose={vi.fn()} />)
  expect(
    screen.getByText('Bắt đầu: 26/10/2026 10:00. Kết thúc: 26/10/2026 11:00 (giờ Việt Nam).'),
  ).toBeTruthy()
  expect(screen.queryByText(/Asia\/Ho_Chi_Minh/)).toBeNull()
  expect(item.content).toBe(content)
})
