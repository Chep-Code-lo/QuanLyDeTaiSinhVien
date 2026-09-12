import { Link } from 'react-router-dom'

export function Header() {
  return (
    <header className="topbar">
      <div className="topbar__context">
        <span>Hệ thống quản lý đề tài sinh viên</span>
        <strong>Học kỳ hiện tại</strong>
      </div>
      <div className="topbar__actions">
        <Link className="topbar__link" to="/announcements" aria-label="Xem thông báo">
          ♧
        </Link>
        <Link className="topbar__account" to="/account">
          <span className="topbar__avatar" aria-hidden="true">
            SV
          </span>
          <span>Tài khoản</span>
        </Link>
      </div>
    </header>
  )
}
