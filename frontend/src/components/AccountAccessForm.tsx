import { useRef, useState } from 'react'
import { adminApi } from '../api/admin'
import { errorMessage } from '../api/student'
import { ConfirmModal } from './ConfirmModal'
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
  const [pending, setPending] = useState<'status' | 'roles' | null>(null)
  const [busy, setBusy] = useState(false)
  const sending = useRef(false)
  const { showError, showSuccess } = useToast()
  const roles: UserResponse['roles'] = account.roles.filter((role) => role !== 'ADMIN')
  if (admin) roles.push('ADMIN')
  return (
    <section className="page-card" aria-label="Quyền truy cập tài khoản">
      <h2>Quyền truy cập · {account.fullName}</h2>
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
      <button
        type="button"
        className="btn btn-secondary"
        disabled={busy || status === account.status}
        onClick={() => setPending('status')}
      >
        Cập nhật trạng thái
      </button>
      <label>
        <input
          type="checkbox"
          checked={admin}
          disabled={busy}
          onChange={(event) => setAdmin(event.target.checked)}
        />{' '}
        Quyền quản trị viên
      </label>
      <p>Vai trò sinh viên/giảng viên được giữ theo hồ sơ học vụ của tài khoản.</p>
      <button
        type="button"
        className="btn btn-primary"
        disabled={busy || !roles.length || admin === account.roles.includes('ADMIN')}
        onClick={() => setPending('roles')}
      >
        Cập nhật quyền
      </button>
      <button type="button" className="btn btn-secondary" disabled={busy} onClick={onClose}>
        Đóng quyền truy cập
      </button>
      <ConfirmModal
        isOpen={!!pending}
        title="Thay đổi quyền truy cập"
        message={`Xác nhận thay đổi ${pending === 'roles' ? 'quyền quản trị' : 'trạng thái đăng nhập'} của ${account.fullName}?`}
        loading={busy}
        onCancel={() => {
          if (!sending.current) setPending(null)
        }}
        onConfirm={async () => {
          if (!pending || sending.current) return
          sending.current = true
          setBusy(true)
          try {
            const updated =
              pending === 'status'
                ? await adminApi.updateStatus(account.id, status)
                : await adminApi.updateRoles(account.id, roles)
            showSuccess('Đã cập nhật quyền truy cập.')
            onUpdated(updated)
          } catch (error) {
            showError(errorMessage(error))
          } finally {
            sending.current = false
            setBusy(false)
            setPending(null)
          }
        }}
      />
    </section>
  )
}
