import { afterEach, beforeAll, beforeEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EmailRecipientPreview } from '../src/components/EmailRecipientPreview'

const listRecipients = vi.hoisted(() => vi.fn())
vi.mock('../src/api/emailNotifications', () => ({ emailNotificationsApi: { listRecipients } }))
beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open')
  }
})
beforeEach(() => {
  listRecipients.mockReset()
})
afterEach(cleanup)

it('shows all recipient pages for the selected group without changing the email selection', async () => {
  listRecipients.mockImplementation(({ page }) =>
    Promise.resolve({
      content: [
        {
          userId: page + 1,
          fullName: page ? 'Sinh viên Bình' : 'Sinh viên An',
          email: page ? 'binh@example.edu.vn' : 'an@example.edu.vn',
        },
      ],
      totalElements: 2,
      totalPages: 2,
    }),
  )
  render(<EmailRecipientPreview role="STUDENT" onClose={vi.fn()} />)
  await screen.findByText('an@example.edu.vn')
  expect(screen.getByText('2 sinh viên sẽ nhận email.')).toBeTruthy()
  await userEvent.click(screen.getByRole('button', { name: 'Sau' }))
  await screen.findByText('binh@example.edu.vn')
  expect(screen.queryByText('an@example.edu.vn')).toBeNull()
  expect(listRecipients).toHaveBeenLastCalledWith({ role: 'STUDENT', page: 1 })
  await userEvent.click(screen.getByRole('button', { name: 'Trước' }))
  await screen.findByText('an@example.edu.vn')
})

it('shows errors and retries the same group, including an empty result', async () => {
  listRecipients
    .mockRejectedValueOnce(new Error('Không tải được danh sách.'))
    .mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0 })
  render(<EmailRecipientPreview role="LECTURER" onClose={vi.fn()} />)
  await screen.findByRole('alert')
  await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
  await screen.findByText('Không có người nhận trong nhóm này.')
  expect(listRecipients).toHaveBeenLastCalledWith({ role: 'LECTURER', page: 0 })
})

it('allows closing while loading and ignores late responses', async () => {
  let resolve!: (result: unknown) => void
  listRecipients.mockImplementation(
    () =>
      new Promise((done) => {
        resolve = done
      }),
  )
  const close = vi.fn()
  const { unmount } = render(<EmailRecipientPreview role="STUDENT" onClose={close} />)
  fireEvent(screen.getByRole('dialog'), new Event('cancel', { cancelable: true }))
  expect(close).toHaveBeenCalledOnce()
  unmount()
  resolve({ content: [], totalElements: 0, totalPages: 0 })
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  expect(document.body.style.overflow).toBe('')
})
