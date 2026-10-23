import { useState } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DateTimeInput } from '../src/components/DateTimeInput'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

function CalendarHarness({
  withTime = false,
  required = false,
}: {
  withTime?: boolean
  required?: boolean
}) {
  const [value, setValue] = useState(withTime ? '2028-02-15T14:30' : '2028-02-15')
  return (
    <>
      <DateTimeInput
        type={withTime ? 'datetime-local' : 'date'}
        aria-label="Ngày chọn"
        value={value}
        onValueChange={setValue}
        min="2028-02-10"
        max="2028-03-20"
        required={required}
      />
      <output data-testid="value">{value}</output>
    </>
  )
}

describe('DateTimeInput', () => {
  it.each([false, true])(
    'lịch tiếng Việt chọn ngày hợp lệ và giữ giờ hiện tại: %s',
    async (withTime) => {
      render(<CalendarHarness withTime={withTime} />)
      await userEvent.click(screen.getByRole('button', { name: 'Mở lịch chọn ngày' }))
      const calendar = screen.getByRole('dialog', { name: 'Lịch chọn ngày' })
      expect((within(calendar).getByLabelText('Tháng') as HTMLSelectElement).value).toBe('1')
      expect(within(calendar).getByText('T2')).toBeTruthy()
      expect(within(calendar).getByText('CN')).toBeTruthy()
      expect(
        (within(calendar).getByRole('button', { name: '09/02/2028' }) as HTMLButtonElement)
          .disabled,
      ).toBe(true)
      await userEvent.click(within(calendar).getByRole('button', { name: '29/02/2028' }))
      expect(screen.getByTestId('value').textContent).toBe(
        withTime ? '2028-02-29T14:30' : '2028-02-29',
      )
      const input = screen.getByLabelText('Ngày chọn') as HTMLInputElement
      expect(input.value).toBe(withTime ? '29/02/2028 14:30' : '29/02/2028')
      expect(document.activeElement).toBe(input)
      expect(screen.queryByRole('dialog')).toBeNull()
    },
  )

  it('đổi tháng, giới hạn ngày, đóng bằng Escape và xóa ngày', async () => {
    render(<CalendarHarness />)
    await userEvent.click(screen.getByRole('button', { name: 'Mở lịch chọn ngày' }))
    const calendar = screen.getByRole('dialog')
    await userEvent.click(within(calendar).getByRole('button', { name: 'Tháng sau' }))
    expect(
      (within(calendar).getByRole('button', { name: '21/03/2028' }) as HTMLButtonElement).disabled,
    ).toBe(true)
    fireEvent.keyDown(calendar, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.getByTestId('value').textContent).toBe('2028-02-15')
    await userEvent.click(screen.getByRole('button', { name: 'Mở lịch chọn ngày' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Xóa' }))
    expect(screen.getByTestId('value').textContent).toBe('')
  })

  it('không cho xóa ngày bắt buộc và không mở lịch khi vô hiệu hóa', async () => {
    const { unmount } = render(<CalendarHarness required />)
    await userEvent.click(screen.getByRole('button', { name: 'Mở lịch chọn ngày' }))
    expect(within(screen.getByRole('dialog')).queryByRole('button', { name: 'Xóa' })).toBeNull()
    unmount()
    render(<DateTimeInput type="date" disabled onValueChange={vi.fn()} />)
    await userEvent.click(screen.getByRole('button', { name: 'Mở lịch chọn ngày' }))
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it.each([
    [100, 400, 1200, 412],
    [500, 1000, 1200, 184],
    [10, 250, 360, 10],
  ])('lịch ưu tiên cạnh ô nhập và tránh mép màn hình: %s', async (left, right, width, expected) => {
    vi.stubGlobal('innerWidth', width)
    vi.stubGlobal('innerHeight', 800)
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (
      this: HTMLElement,
    ) {
      return (
        this.classList.contains('date-calendar')
          ? { left: 0, top: 0, right: 304, bottom: 350, width: 304, height: 350 }
          : { left, right, top: 150, bottom: 190, width: right - left, height: 40 }
      ) as DOMRect
    })
    render(<CalendarHarness />)
    await userEvent.click(screen.getByRole('button', { name: 'Mở lịch chọn ngày' }))
    const calendar = screen.getByRole('dialog')
    expect(parseFloat(calendar.style.left)).toBe(Math.max(12, expected))
    expect(calendar.style.visibility).toBe('visible')
    expect(calendar.getAttribute('popover')).toBe('auto')
    expect(parseFloat(calendar.style.left) + 304).toBeLessThanOrEqual(width - 12)
  })

  it('cho phép nhập 00:00 theo định dạng 24 giờ', async () => {
    const user = userEvent.setup()
    function InputHarness() {
      const [value, setValue] = useState('')
      return (
        <>
          <DateTimeInput
            type="datetime-local"
            aria-label="Hạn nộp"
            value={value}
            onValueChange={setValue}
          />
          <output data-testid="canonical-value">{value}</output>
        </>
      )
    }

    render(<InputHarness />)

    const input = screen.getByLabelText('Hạn nộp')
    await user.click(input)
    await user.keyboard('221020260000')

    await waitFor(() => {
      expect((input as HTMLInputElement).value).toBe('22/10/2026 00:00')
      expect(screen.getByTestId('canonical-value').textContent).toBe('2026-10-22T00:00')
    })
    expect((input as HTMLInputElement).checkValidity()).toBe(true)
  })
})
