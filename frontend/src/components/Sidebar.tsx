import { useEffect, useRef, type UIEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { navigationItems } from '../config/navigation'
import { useAuth } from '../context/AuthContext'

type SidebarProps = { onNavigate: () => void }

export function Sidebar({ onNavigate }: SidebarProps) {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const { user, hasAnyRole, hasAppointment, canAccessAcademicManagement } = useAuth()
  const scrollTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(
    () => () => {
      if (scrollTimerRef.current) clearTimeout(scrollTimerRef.current)
    },
    [],
  )

  const handleNavScroll = (event: UIEvent<HTMLElement>) => {
    const nav = event.currentTarget
    nav.classList.add('sidebar__nav--scrolling')
    if (scrollTimerRef.current) clearTimeout(scrollTimerRef.current)
    scrollTimerRef.current = setTimeout(() => {
      nav.classList.remove('sidebar__nav--scrolling')
    }, 650)
  }

  const filteredItems = navigationItems.filter((item) => {
    if (!user) return item.to === '/' || item.to === '/account'

    if (item.adminOnly && !user.roles?.includes('ADMIN')) {
      return false
    }

    if (item.requireAcademicManagement && !canAccessAcademicManagement()) {
      return false
    }

    if (item.roles && item.roles.length > 0) {
      if (!hasAnyRole(item.roles)) return false
    }

    if (item.appointments && item.appointments.length > 0) {
      const hasAppt = item.appointments.some((apt) => hasAppointment(apt))
      if (!hasAppt && !user.roles?.includes('ADMIN')) return false
    }

    return true
  })

  return (
    <aside className="sidebar app-sidebar">
      <Link className="sidebar__brand" to="/dashboard" aria-label="Về trang tổng quan" onClick={onNavigate}>
        <img
          className="sidebar__brand-image"
          src="/HCM-UTE_Logo.png"
          width={104}
          height={104}
          alt="Logo Trường Đại học Công nghệ Kỹ thuật Thành phố Hồ Chí Minh"
        />
      </Link>
      <p className="sidebar__label">Menu chính</p>
      <nav className="sidebar__nav" aria-label="Điều hướng chính" onScroll={handleNavScroll}>
        {filteredItems.map((item) => {
          const Icon = item.icon
          const isActive = pathname === item.to || (item.to !== '/' && pathname.startsWith(`${item.to}/`))

          return (
            <button
              key={item.to}
              type="button"
              className={`sidebar__link${isActive ? ' sidebar__link--active' : ''}`}
              aria-current={isActive ? 'page' : undefined}
              onClick={() => { navigate(item.to); onNavigate() }}
            >
              <span className="sidebar__icon" aria-hidden="true">
                <Icon size={18} strokeWidth={1.8} />
              </span>
              <span className="sidebar__text">{item.label}</span>
            </button>
          )
        })}
      </nav>
      <div className="sidebar__footer">
        <div className="font-semibold text-xs text-white">HCM-UTE</div>
        <div className="text-xs mt-1">Hệ thống quản lý đề tài sinh viên</div>
      </div>
    </aside>
  )
}
