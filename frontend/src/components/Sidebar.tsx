import { NavLink } from 'react-router-dom'
import { navigationItems } from '../config/navigation'
import { AppLogo } from './AppLogo'

export function Sidebar() {
  return (
    <aside className="sidebar">
      <AppLogo />
      <p className="sidebar__label">Điều hướng</p>
      <nav className="sidebar__nav" aria-label="Điều hướng chính">
        {navigationItems.map((item) => (
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
        Phiên bản giao diện khởi tạo<br />
        Dữ liệu sẽ được lấy từ Spring Boot API.
      </div>
    </aside>
  )
}
