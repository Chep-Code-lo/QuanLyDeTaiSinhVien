import { useEffect, useRef, useState } from 'react'
import { ChevronDown, LogOut, Menu } from 'lucide-react'
import { notificationApi } from '../api/notifications'
import { errorMessage } from '../api/student'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { avatarInitial } from '../utils/userDisplay'
import { NotificationMenu } from '../features/notifications/components/NotificationMenu'

type HeaderProps = { onMenuClick: () => void }

export function Header({ onMenuClick }: HeaderProps) {
  const { user, logout, hasRole } = useAuth()
  const management = hasRole('ADMIN') || hasRole('LECTURER')
  const { showSuccess, showError } = useToast()
  const navigate = useNavigate()
  const [search, setSearch] = useState('')
  const [unread, setUnread] = useState<number | null>(null)
  const [loggingOut, setLoggingOut] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const closeMenu = (event: MouseEvent) => { if (!menuRef.current?.contains(event.target as Node)) setMenuOpen(false) }
    document.addEventListener('mousedown', closeMenu)
    return () => document.removeEventListener('mousedown', closeMenu)
  }, [])

  useEffect(() => {
    let active = true
    const refresh = () => notificationApi.unreadCount()
      .then(count=>{if(active)setUnread(count)})
      .catch(()=>{if(active)setUnread(null)})
    void refresh()
    window.addEventListener('notifications-updated',refresh)
    const timer = window.setInterval(refresh,60000)
    return ()=>{active=false;clearInterval(timer);window.removeEventListener('notifications-updated',refresh)}
  }, [user?.id])

  const handleLogout = async () => {
    if(loggingOut)return
    setLoggingOut(true)
    try {
      await logout()
      showSuccess('Đã đăng xuất thành công')
    } catch(e) {
      showError(errorMessage(e))
    } finally {
      setMenuOpen(false)
      setLoggingOut(false)
      navigate('/', { replace: true })
    }
  }

  const getRoleLabel = () => {
    if (user?.roles?.includes('ADMIN')) return 'Quản trị viên'
    if (user?.activeAppointments?.[0]?.positionCode === 'DEAN') return 'Giảng viên · Trưởng khoa'
    if (user?.activeAppointments?.[0]?.positionCode === 'DEPARTMENT_HEAD') return 'Giảng viên · Trưởng bộ môn'
    if (user?.roles?.includes('LECTURER')) return 'Giảng viên'
    return 'Sinh viên'
  }

  return (
    <header className="topbar">
      <button className="topbar__menu" type="button" onClick={onMenuClick} aria-label="Mở menu điều hướng"><Menu size={22} aria-hidden="true" /></button>
      {management ? <div className="topbar__school">Trường Đại học Công nghệ Kỹ thuật TP.HCM</div> : <form className="topbar__search" onSubmit={e=>{e.preventDefault();navigate(`/topics?q=${encodeURIComponent(search.trim())}`)}}>
        <span aria-hidden="true">⌕</span>
        <input type="search" value={search} onChange={e=>setSearch(e.target.value)} placeholder="Tìm đề tài, bộ môn, GVHD..." aria-label="Tìm kiếm đề tài" />
        <button className="text-button" type="submit">Tìm</button>
      </form>}
      <div className="topbar__actions flex items-center gap-3">
        {user ? (
          <>
            <NotificationMenu unread={unread} management={management} onOpen={() => setMenuOpen(false)} />
            <div className="account-menu" ref={menuRef}>
            <button className="topbar__account" type="button" onClick={() => setMenuOpen((value) => !value)} aria-expanded={menuOpen}>
              <span className="topbar__avatar">{avatarInitial(user.fullName)}</span>
              <span className="topbar__account-copy"><strong>{user.fullName}</strong><small>{getRoleLabel()}</small></span><ChevronDown size={16} aria-hidden="true" />
            </button>
            {menuOpen && <div className="account-menu__dropdown"><Link to="/account" onClick={() => setMenuOpen(false)}>Hồ sơ & cài đặt </Link><button type="button" disabled={loggingOut} onClick={handleLogout}>Đăng xuất <LogOut size={16} aria-hidden="true" /></button></div>}
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
