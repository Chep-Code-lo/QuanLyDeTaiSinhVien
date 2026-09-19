import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'

export function Header() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const { showSuccess } = useToast()

  const handleLogout = async () => {
    await logout()
    showSuccess('Đã đăng xuất thành công')
    navigate('/login')
  }

  const getInitials = (name?: string) => {
    if (!name) return 'U'
    const parts = name.trim().split(' ')
    return parts[parts.length - 1].substring(0, 2).toUpperCase()
  }

  return (
    <header className="topbar">
      <label className="topbar__search">
        <span aria-hidden="true">⌕</span>
        <input type="search" placeholder="Tìm kiếm đề tài, giảng viên, sinh viên..." aria-label="Tìm kiếm toàn hệ thống" />
      </label>
      <div className="topbar__context">
              <span>Quản lý đề tài HCMUTE</span>
        <strong>
          {user ? (
            <>
              {user.fullName} ({user.username})
              {user.roles?.map((r) => (
                <span key={r} className="badge badge-info ml-1">
                  {r}
                </span>
              ))}
              {user.activeAppointments?.map((apt) => (
                <span key={apt.id} className="badge badge-success ml-1">
                  {apt.positionCode === 'DEAN' ? 'Trưởng Khoa' : 'Trưởng Bộ Môn'}
                </span>
              ))}
            </>
          ) : (
            'Chưa đăng nhập'
          )}
        </strong>
      </div>
      <div className="topbar__actions flex items-center gap-3">
        {user ? (
          <>
            <Link className="notification-button" to="/announcements" title="Thông báo" aria-label="Thông báo mới">♧<span>3</span></Link>
            <Link className="topbar__account" to="/account" title="Quản lý tài khoản">
              <span className="topbar__avatar">{getInitials(user.fullName)}</span>
              <span>{user.fullName}</span>
            </Link>
            <button
              type="button"
              className="btn btn-outline btn-sm"
              onClick={handleLogout}
              title="Đăng xuất"
            >
              Đăng xuất
            </button>
          </>
        ) : (
          <Link to="/login" className="btn btn-primary btn-sm">
            Đăng nhập
          </Link>
        )}
      </div>
    </header>
  )
}
