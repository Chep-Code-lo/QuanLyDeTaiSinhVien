import { useEffect, useId, useRef, useState } from 'react'
import { adminApi } from '../api/admin'
import { errorMessage } from '../api/student'
import { useToast } from '../context/ToastContext'
import type { UserResponse, UserStatus } from '../types/domain'

export function AccountAccessForm({
  account,
  onUpdated,
  onClose,
}: {
  account: UserResponse
  onUpdated: (user: UserResponse) => void
  onClose: () => void
}) {
  const [status, setStatus] = useState(account.status)
  const [admin, setAdmin] = useState(account.roles.includes('ADMIN'))
  const [pending, setPending] = useState(false)
  const [busy, setBusy] = useState(false)
  const sending = useRef(false)
  const dialog = useRef<HTMLDialogElement>(null)
  const closeButton = useRef<HTMLButtonElement>(null)
  const titleId = useId()
  useEffect(() => {
    const element = dialog.current
    const trigger = document.activeElement as HTMLElement | null
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    element?.showModal()
    closeButton.current?.focus()
    return () => {
      element?.close()
      document.body.style.overflow = previousOverflow
      if (trigger?.isConnected) trigger.focus()
    }
  }, [])
  useEffect(() => {
    if (pending) dialog.current?.querySelector<HTMLButtonElement>('[data-cancel]')?.focus()
    else closeButton.current?.focus()
  }, [pending])
  const { showError, showSuccess } = useToast()
  const roles: UserResponse['roles'] = account.roles.filter((role) => role !== 'ADMIN')
  if (admin) roles.push('ADMIN')
  return (
    <dialog
      ref={dialog}
      className="catalog-editor-dialog account-access-dialog"
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault()
        if (sending.current) return
        if (pending) setPending(false)
        else onClose()
      }}
    >
      <div className="catalog-editor-dialog__heading">
        <h2 id={titleId}>
          {pending ? 'Thay đổi quyền truy cập' : `Quyền truy cập · ${account.fullName}`}
        </h2>
        <button
          ref={closeButton}
          type="button"
          className="btn btn-secondary"
          disabled={busy}
          aria-label="Đóng quyền truy cập"
          onClick={() => {
            if (pending) setPending(false)
            else onClose()
          }}
        >
          Đóng
        </button>
      </div>
      {!pending && (
        <div className="account-access-dialog__form">
          <label>
            Trạng thái tài khoản
            <select
              className="form-select"
              value={status}
              disabled={busy}
              onChange={(event) => setStatus(event.target.value as UserStatus)}
            >
              <option value="ACTIVE">Hoạt động</option>
              <option value="LOCKED">Khóa</option>
              <option value="DISABLED">Ngừng hoạt động</option>
            </select>
          </label>
          <label className="account-access-dialog__admin">
            <input
              type="checkbox"
              checked={admin}
              disabled={busy}
              onChange={(event) => setAdmin(event.target.checked)}
            />{' '}
            Quyền quản trị viên
          </label>

          <button
            type="button"
            className="btn btn-primary"
            disabled={
              busy ||
              !roles.length ||
              (status === account.status && admin === account.roles.includes('ADMIN'))
            }
            onClick={() => setPending(true)}
          >
            Lưu thay đổi
          </button>
        </div>
      )}
      {pending && (
        <>
          <p>Xác nhận lưu thay đổi quyền truy cập của {account.fullName}?</p>
          <ul>
            {status !== account.status && (
              <li>
                Trạng thái tài khoản:{' '}
                {{ ACTIVE: 'Hoạt động', LOCKED: 'Khóa', DISABLED: 'Ngừng hoạt động' }[status]}
              </li>
            )}
            {admin !== account.roles.includes('ADMIN') && (
              <li>Quyền quản trị viên: {admin ? 'Cấp quyền' : 'Thu hồi quyền'}</li>
            )}
          </ul>
          <div className="account-access-dialog__actions">
            <button
              type="button"
              data-cancel
              className="btn btn-secondary"
              disabled={busy}
              onClick={() => setPending(false)}
            >
              Hủy
            </button>
            <button
              type="button"
              className="btn btn-primary"
              disabled={busy}
              onClick={async () => {
                if (!pending || sending.current) return
                sending.current = true
                setBusy(true)
                try {
                  const updated = await adminApi.updateAccess(account.id, { status, roles })
                  showSuccess('Đã cập nhật quyền truy cập.')
                  onUpdated(updated)
                } catch (error) {
                  showError(errorMessage(error))
                } finally {
                  sending.current = false
                  setBusy(false)
                  setPending(false)
                }
              }}
            >
              {busy ? 'Đang xử lý...' : 'Xác nhận'}
            </button>
          </div>
        </>
      )}
    </dialog>
  )
}
