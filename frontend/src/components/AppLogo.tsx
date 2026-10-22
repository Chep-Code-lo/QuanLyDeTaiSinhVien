import { Link } from 'react-router-dom'

export function AppLogo() {
  return (
    <Link className="app-logo" to="/" aria-label="Quản lý đề tài HCMUTE">
      <img
        className="app-logo__image"
        src="/HCM-UTE_Logo.png"
        width={72}
        height={72}
        alt="Logo HCMUTE"
      />
      <span className="app-logo__copy">
        <strong>Quản lý đề tài HCMUTE</strong>
        <small>Hệ thống quản lý đề tài sinh viên</small>
      </span>
    </Link>
  )
}
