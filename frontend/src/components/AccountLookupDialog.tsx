import { useEffect, useId, useRef, useState } from 'react'
import { isAxiosError } from 'axios'
import { adminApi } from '../api/admin'
import type { UserResponse } from '../types/domain'

export function AccountLookupDialog({
  onFound,
  onClose,
}: {
  onFound: (account: UserResponse) => void
  onClose: () => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const input = useRef<HTMLInputElement>(null)
  const sending = useRef(false)
  const titleId = useId()
  const [identifier, setIdentifier] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    const element = dialog.current
    const trigger = document.activeElement as HTMLElement | null
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    element?.showModal()
    input.current?.focus()
    return () => {
      element?.close()
      document.body.style.overflow = previousOverflow
      if (trigger?.isConnected) trigger.focus()
    }
  }, [])
  return (
    <dialog
      ref={dialog}
      className="catalog-editor-dialog"
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault()
        if (!sending.current) onClose()
      }}
    >
      <div className="catalog-editor-dialog__heading">
        <h2 id={titleId}>Tra cứu tài khoản</h2>
        <button type="button" className="btn btn-secondary" disabled={busy} onClick={onClose}>
          Đóng
        </button>
      </div>
      <p>
        Nhập đầy đủ email, mã sinh viên hoặc mã giảng viên để tra cứu tài khoản, bao gồm tài khoản
        đang hoạt động, bị khóa hoặc ngừng hoạt động.
      </p>
      <form
        className="stack-form"
        onSubmit={async (event) => {
          event.preventDefault()
          const query = identifier.trim()
          if (!query || sending.current) return
          sending.current = true
          setBusy(true)
          setError('')
          try {
            onFound(await adminApi.lookupUser(query))
          } catch (error) {
            setError(
              isAxiosError(error)
                ? error.response?.data?.message || 'Không tìm thấy tài khoản.'
                : 'Không tải được tài khoản.',
            )
          } finally {
            sending.current = false
            setBusy(false)
          }
        }}
      >
        <label>
          Email / Mã sinh viên / Mã giảng viên
          <input
            ref={input}
            className="form-input"
            type="text"
            maxLength={255}
            autoComplete="off"
            autoCapitalize="none"
            spellCheck={false}
            placeholder="Ví dụ: sv@example.edu.vn, 24162012 hoặc GV003"
            required
            disabled={busy}
            value={identifier}
            onChange={(event) => setIdentifier(event.target.value)}
          />
        </label>
        {error && <p role="alert">{error}</p>}
        <div className="inline-form">
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Đang tra cứu...' : 'Tra cứu tài khoản'}
          </button>
          <button type="button" className="btn btn-secondary" disabled={busy} onClick={onClose}>
            Hủy
          </button>
        </div>
      </form>
    </dialog>
  )
}
