import { useState } from 'react'
import {
  act,
  cleanup,
  fireEvent,
  render,
  renderHook,
  screen,
  waitFor,
} from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { LecturerAutocomplete } from '../src/components/LecturerAutocomplete'
import { useRequestLoading } from '../src/hooks/useRequestLoading'

const mocks = vi.hoisted(() => ({ list: vi.fn(), change: vi.fn() }))
vi.mock('../src/api/lecturers', () => ({ lecturerApi: { list: mocks.list } }))
const lecturerA = { id: 1, fullName: 'Giảng viên A', lecturerCode: 'GV01' }
const lecturerB = { id: 2, fullName: 'Giảng viên B', lecturerCode: 'GV02' }
const page = (content: unknown[]) => ({ content, totalPages: 1, totalElements: content.length })

beforeEach(() => {
  vi.clearAllMocks()
  mocks.list.mockResolvedValue(page([]))
})
afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

it('hiển thị lựa chọn có sẵn và cập nhật tên từ props mà không tải toàn bộ danh mục', () => {
  const { rerender } = render(
    <LecturerAutocomplete value={1} selectedOption={lecturerA} onChange={mocks.change} />,
  )
  expect(screen.getByText('Giảng viên A')).toBeTruthy()
  rerender(<LecturerAutocomplete value={2} selectedOption={lecturerB} onChange={mocks.change} />)
  expect(screen.getByText('Giảng viên B')).toBeTruthy()
  expect(screen.queryByText('Giảng viên A')).toBeNull()
  expect(mocks.list).not.toHaveBeenCalled()
})

it('không thay lựa chọn mới khi request lấy giảng viên cũ hoàn tất muộn', async () => {
  let resolveOld!: (value: unknown) => void
  mocks.list.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        resolveOld = resolve
      }),
  )
  const { rerender } = render(<LecturerAutocomplete value={1} onChange={mocks.change} />)
  await waitFor(() => expect(mocks.list).toHaveBeenCalledOnce())
  expect(mocks.list).toHaveBeenCalledWith(
    expect.objectContaining({ lecturerId: 1, page: 0, size: 1 }),
  )
  rerender(<LecturerAutocomplete value={2} selectedOption={lecturerB} onChange={mocks.change} />)
  await act(async () => {
    resolveOld(page([lecturerA]))
  })
  expect(screen.getByText('Giảng viên B')).toBeTruthy()
  expect(screen.queryByText('Giảng viên A')).toBeNull()
})

it('debounce tìm kiếm, bỏ kết quả cũ và giữ lựa chọn giảng viên sau khi chọn', async () => {
  vi.useFakeTimers()
  let resolveOld!: (value: unknown) => void
  mocks.list
    .mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveOld = resolve
        }),
    )
    .mockResolvedValueOnce(page([lecturerB]))
  function Picker() {
    const [value, setValue] = useState<number | null>(null)
    return (
      <LecturerAutocomplete
        value={value}
        onChange={(id, lecturer) => {
          setValue(id)
          mocks.change(id, lecturer)
        }}
      />
    )
  }
  render(<Picker />)
  fireEvent.focus(screen.getByRole('textbox'))
  fireEvent.change(screen.getByRole('textbox'), { target: { value: 'A' } })
  await act(async () => {
    await vi.advanceTimersByTimeAsync(300)
  })
  fireEvent.change(screen.getByRole('textbox'), { target: { value: 'B' } })
  expect(screen.getByText('Đang tìm kiếm...')).toBeTruthy()
  await act(async () => {
    await vi.advanceTimersByTimeAsync(300)
  })
  fireEvent.click(screen.getByRole('button', { name: /Giảng viên B/ }))
  await act(async () => {
    resolveOld(page([lecturerA]))
  })
  expect(mocks.change).toHaveBeenCalledWith(2, lecturerB)
  expect(screen.getByText('Giảng viên B')).toBeTruthy()
  expect(screen.queryByText('Giảng viên A')).toBeNull()
  expect(mocks.list).toHaveBeenCalledTimes(2)
})

it('đổi request đánh dấu đang tải ngay và kết quả cũ không hoàn tất request mới', () => {
  const { result, rerender } = renderHook(({ scope }) => useRequestLoading(scope), {
    initialProps: { scope: 'A' },
  })
  const completeA = result.current[1]
  act(() => completeA(false))
  expect(result.current[0]).toBe(false)
  rerender({ scope: 'B' })
  expect(result.current[0]).toBe(true)
  act(() => completeA(false))
  expect(result.current[0]).toBe(true)
  act(() => result.current[1](false))
  expect(result.current[0]).toBe(false)
  act(() => completeA(false))
  expect(result.current[0]).toBe(false)
  rerender({ scope: 'A' })
  expect(result.current[0]).toBe(true)
})
