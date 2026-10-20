import { useEffect, useRef, type UIEvent } from 'react'
import { Link, NavLink } from 'react-router-dom'
import { navigationItems } from '../config/navigation'
import { useAuth } from '../context/AuthContext'

type SidebarProps = { onNavigate: () => void }

export function Sidebar({ onNavigate }: SidebarProps) {
  const { user, hasAnyRole, hasAppointment, canAccessAcademicManagement } = useAuth()
  const isStudent = user?.roles?.includes('STUDENT') &&
    !user.roles.includes('LECTURER') && !user.roles.includes('ADMIN')
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

    if (isStudent && item.to === '/dashboard') return false

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
  }).sort((left, right) => {
    if (!isStudent) return 0
    if (left.to === '/announcements') return -1
    if (right.to === '/announcements') return 1
    return 0
  })

  return (
    <aside className="sidebar app-sidebar">
      <Link className="sidebar__brand" to={isStudent ? '/announcements' : '/dashboard'} aria-label={isStudent ? 'Về trang thông báo' : 'Về trang tổng quan'} onClick={onNavigate}>
        <img
          className="sidebar__brand-image"
          src="/HCM-UTE_Logo.png"
          width={88}
          height={88}
          alt="Logo Trường Đại học Công nghệ Kỹ thuật Thành phố Hồ Chí Minh"
        />
      </Link>
      <p className="sidebar__label">Menu chính</p>
      <nav className="sidebar__nav" aria-label="Điều hướng chính" onScroll={handleNavScroll}>
        {filteredItems.map((item) => {
          const Icon = item.icon
          const label = isStudent
            ? ({
                '/topics': 'Đề tài đã công bố',
                '/progress': 'Tiến độ & Báo cáo',
              } as Record<string, string>)[item.to] ?? item.label
            : item.label

          return (
            <NavLink
              key={item.to}
              className={({ isActive }) =>
                `sidebar__link${isActive ? ' sidebar__link--active' : ''}`
              }
              end={item.to === '/'}
              to={item.to}
              onClick={onNavigate}
            >
              <span className="sidebar__icon" aria-hidden="true">
                <Icon size={18} strokeWidth={1.8} />
              </span>
              <span>{label}</span>
            </NavLink>
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
