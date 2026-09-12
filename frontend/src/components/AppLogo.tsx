import { Link } from 'react-router-dom'

export function AppLogo() {
  return (
    <Link className="app-logo" to="/" aria-label="Quản lý đề tài sinh viên">
      <span className="app-logo__mark" aria-hidden="true">
        ĐT
      </span>
      <span className="app-logo__copy">
        <strong>Quản lý đề tài</strong>
        <small>Sinh viên</small>
      </span>
    </Link>
  )
}
