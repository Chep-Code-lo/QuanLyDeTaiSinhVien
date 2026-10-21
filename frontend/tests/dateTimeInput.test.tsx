import { useState } from 'react'
import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DateTimeInput } from '../src/components/DateTimeInput'

afterEach(cleanup)

describe('DateTimeInput', () => {
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
