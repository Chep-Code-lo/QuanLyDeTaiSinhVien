import { useEffect, useState } from 'react'
import { Outlet } from 'react-router-dom'
import { Header } from '../components/Header'
import { Sidebar } from '../components/Sidebar'
import { useAuth } from '../context/AuthContext'

export function AppLayout() {
  const { hasRole } = useAuth()
  const management = hasRole('ADMIN') || hasRole('LECTURER')
  const [mobileOpen, setMobileOpen] = useState(false)
  useEffect(() => {
    if (!mobileOpen) return
    const close = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setMobileOpen(false)
    }
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [mobileOpen])
  return (
    <div
      className={`app-shell portal-shell${management ? ' management-shell' : ''}${mobileOpen ? ' app-shell--mobile-open' : ''}`}
    >
      <Sidebar onNavigate={() => setMobileOpen(false)} />
      {mobileOpen && (
        <button
          className="sidebar-backdrop"
          type="button"
          aria-label="Đóng menu điều hướng"
          onClick={() => setMobileOpen(false)}
        />
      )}
      <div className="app-main">
        <Header onMenuClick={() => setMobileOpen((value) => !value)} />
        <main className="page-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
