import { NavLink } from 'react-router-dom'
import { navigationItems } from '../config/navigation'
import { useAuth } from '../context/AuthContext'
import { AppLogo } from './AppLogo'

export function Sidebar() {
  const { user, hasAnyRole, hasAppointment, canAccessAcademicManagement } = useAuth()

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
    <aside className="sidebar">
      <AppLogo />
      <p className="sidebar__label">Menu chính</p>
      <nav className="sidebar__nav" aria-label="Điều hướng chính">
        {filteredItems.map((item) => (
          <NavLink
            key={item.to}
            className={({ isActive }) =>
              `sidebar__link${isActive ? ' sidebar__link--active' : ''}`
            }
            end={item.to === '/'}
            to={item.to}
          >
            <span className="sidebar__icon" aria-hidden="true">
              {item.icon}
            </span>
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>
      <div className="sidebar__footer">
        <div className="font-semibold text-xs text-white">HCMUTE</div>
        <div className="text-xs mt-1">Student Project Management</div>
      </div>
    </aside>
  )
}
