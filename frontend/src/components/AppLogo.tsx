import { Link } from 'react-router-dom'

export function AppLogo() {
  return (
    <Link className="app-logo" to="/" aria-label="Quản lý đề tài HCMUTE">
      <span className="app-logo__mark" aria-hidden="true">
        UTE
      </span>
      <span className="app-logo__copy">
        <strong>Quản lý đề tài HCMUTE</strong>
        <small>Student Project Management</small>
      </span>
    </Link>
  )
}
