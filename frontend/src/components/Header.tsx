import { useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { notificationApi } from '../api/notifications'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { avatarInitial } from '../utils/userDisplay'

type HeaderProps = { onMenuClick: () => void }

export function Header({ onMenuClick }: HeaderProps) {
  const { user, logout } = useAuth()
  const { pathname } = useLocation()
  const { showSuccess } = useToast()
  const [menuOpen, setMenuOpen] = useState(false)
  const [hasUnreadNotifications, setHasUnreadNotifications] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const closeMenu = (event: MouseEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setMenuOpen(false)
    }
    document.addEventListener('mousedown', closeMenu)
    return () => document.removeEventListener('mousedown', closeMenu)
  }, [])

  useEffect(() => {
    if (!user?.id) {
      setHasUnreadNotifications(false)
      return
    }

    let active = true
    let latestRequest = 0
    let pendingRequests = 0
    const refreshUnreadState = () => {
      const request = ++latestRequest
      pendingRequests++
      notificationApi
        .unreadCount()
        .then((count) => {
          if (active && request === latestRequest) setHasUnreadNotifications(count > 0)
        })
        .catch(() => undefined)
        .finally(() => {
          pendingRequests--
        })
    }

    refreshUnreadState()
    const intervalId = window.setInterval(() => {
      if (pendingRequests === 0) refreshUnreadState()
    }, 5_000)
    window.addEventListener('focus', refreshUnreadState)
    window.addEventListener('notifications:changed', refreshUnreadState)

    return () => {
      active = false
      window.clearInterval(intervalId)
      window.removeEventListener('focus', refreshUnreadState)
      window.removeEventListener('notifications:changed', refreshUnreadState)
    }
  }, [user?.id, pathname])

  const handleLogout = async () => {
    await logout()
    showSuccess('Đã đăng xuất thành công')
    window.location.assign('/')
  }

  const getRoleLabel = () => {
    if (user?.roles?.includes('ADMIN')) return 'Quản trị viên'
    if (user?.activeAppointments?.[0]?.positionCode === 'DEAN') return 'Giảng viên · Trưởng khoa'
    if (user?.activeAppointments?.[0]?.positionCode === 'DEPARTMENT_HEAD')
      return 'Giảng viên · Trưởng bộ môn'
    if (user?.roles?.includes('LECTURER')) return 'Giảng viên'
    return 'Sinh viên'
  }

  return (
    <header className="topbar">
      <button
        className="topbar__menu"
        type="button"
        onClick={onMenuClick}
        aria-label="Mở menu điều hướng"
      >
        ☰
      </button>
      <div className="topbar__school">Trường Đại học Công nghệ Kỹ thuật TP.HCM</div>
      <div className="topbar__actions flex items-center gap-3">
        {user ? (
          <>
            <Link
              className="notification-button"
              to="/announcements"
              title="Thông báo"
              aria-label={hasUnreadNotifications ? 'Có thông báo mới' : 'Thông báo'}
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M18 9a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" />
              </svg>
              {hasUnreadNotifications && (
                <span className="notification-button__dot" aria-hidden="true" />
              )}
            </Link>
            <div className="account-menu" ref={menuRef}>
              <button
                className="topbar__account"
                type="button"
                onClick={() => setMenuOpen((value) => !value)}
                aria-expanded={menuOpen}
              >
                <span className="topbar__avatar">{avatarInitial(user.fullName)}</span>
                <span className="topbar__account-copy">
                  <strong>{user.fullName}</strong>
                  <small>{getRoleLabel()}</small>
                </span>
                <span aria-hidden="true">⌄</span>
              </button>
              {menuOpen && (
                <div className="account-menu__dropdown">
                  <Link to="/account" onClick={() => setMenuOpen(false)}>
                    Hồ sơ & cài đặt <span>›</span>
                  </Link>
                  <button type="button" onClick={handleLogout}>
                    Đăng xuất <span>↗</span>
                  </button>
                </div>
              )}
            </div>
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
