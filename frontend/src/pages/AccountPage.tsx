import { useState } from 'react'
import { authApi } from '../api/auth'
import { PageHeader } from '../components/PageHeader'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { roleLabel } from '../utils/displayLabels'
import { isValidPassword, PASSWORD_PATTERN, PASSWORD_REQUIREMENTS } from '../utils/passwordPolicy'
import { avatarInitial } from '../utils/userDisplay'

export function AccountPage() {
  const { user } = useAuth()
  const { showError, showSuccess } = useToast()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const changePassword = async (event: React.FormEvent) => {
    event.preventDefault()
    if (submitting) return
    if (!isValidPassword(newPassword)) {
      showError(PASSWORD_REQUIREMENTS)
      return
    }
    try {
      setSubmitting(true)
      await authApi.changePassword({ currentPassword, newPassword })
      setCurrentPassword('')
      setNewPassword('')
      showSuccess('Đã đổi mật khẩu thành công.')
    } catch (error: any) {
      showError(
        error.response?.data?.data?.newPassword ||
          error.response?.data?.message ||
          'Không thể đổi mật khẩu. Vui lòng kiểm tra lại.',
      )
    } finally {
      setSubmitting(false)
    }
  }
  return (
    <>
      <PageHeader title="Tài khoản" />
      <div className="account-layout">
        <section className="page-card account-profile">
          <div className="profile-avatar">{avatarInitial(user?.fullName)}</div>
          <div>
            <h2>{user?.fullName}</h2>
            <p>{user?.username}</p>
          </div>
          <dl>
            <div>
              <dt>Vai trò</dt>
              <dd>{user?.roles.map(roleLabel).join(', ') || '—'}</dd>
            </div>
            <div>
              <dt>Chức vụ đang đảm nhiệm</dt>
              <dd>
                {user?.activeAppointments.length
                  ? user.activeAppointments
                      .map((item) =>
                        item.positionCode === 'DEAN' ? 'Trưởng khoa' : 'Trưởng bộ môn',
                      )
                      .join(', ')
                  : 'Không có'}
              </dd>
            </div>
            <div>
              <dt>Mã hồ sơ</dt>
              <dd>{user?.student?.studentCode || user?.lecturer?.lecturerCode || '—'}</dd>
            </div>
          </dl>
        </section>
        <section className="page-card">
          <h2 className="section-title">Đổi mật khẩu</h2>
          <p className="section-description" id="new-password-hint">
            {PASSWORD_REQUIREMENTS}
          </p>
          <form className="account-form" onSubmit={changePassword}>
            <div className="form-group">
              <label className="form-label" htmlFor="currentPassword">
                Mật khẩu hiện tại
              </label>
              <input
                className="form-input"
                id="currentPassword"
                type="password"
                autoComplete="current-password"
                required
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
              />
            </div>
            <div className="form-group">
              <label className="form-label" htmlFor="newPassword">
                Mật khẩu mới
              </label>
              <input
                className="form-input"
                id="newPassword"
                type="password"
                autoComplete="new-password"
                minLength={8}
                maxLength={100}
                pattern={PASSWORD_PATTERN}
                title={PASSWORD_REQUIREMENTS}
                aria-describedby="new-password-hint"
                required
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
              />
            </div>
            <button className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Đang xử lý...' : 'Cập nhật mật khẩu'}
            </button>
          </form>
        </section>
      </div>
    </>
  )
}
