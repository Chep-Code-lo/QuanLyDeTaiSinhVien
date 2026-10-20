import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { Header } from '../components/Header'
import { Sidebar } from '../components/Sidebar'

export function AppLayout() {
  const [mobileOpen, setMobileOpen] = useState(false)
  return (
    <div className={`app-shell${mobileOpen ? ' app-shell--mobile-open' : ''}`}>
      <Sidebar onNavigate={() => setMobileOpen(false)} />
      <div className="app-main">
        <Header onMenuClick={() => setMobileOpen((value) => !value)} />
        <main className="page-content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
