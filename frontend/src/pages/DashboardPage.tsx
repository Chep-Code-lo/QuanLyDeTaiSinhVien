import { Link } from 'react-router-dom'
import { PageHeader } from '../components/PageHeader'

const metrics = [
  { label: 'Đề tài của bạn', note: 'Chờ đồng bộ từ API', value: '—' },
  { label: 'Nhóm thực hiện', note: 'Chờ đồng bộ từ API', value: '—' },
  { label: 'Mốc tiến độ', note: 'Chờ đồng bộ từ API', value: '—' },
  { label: 'Thông báo mới', note: 'Chờ đồng bộ từ API', value: '—' },
]

const quickLinks = [
  { label: 'Khám phá đề tài', to: '/topics' },
  { label: 'Quản lý nhóm', to: '/groups' },
  { label: 'Theo dõi tiến độ', to: '/progress' },
  { label: 'Xem thông báo', to: '/announcements' },
]

export function DashboardPage() {
  return (
    <>
      <PageHeader
        title="Tổng quan"
        description="Trang khởi đầu để theo dõi đề tài, nhóm thực hiện và các mốc quan trọng trong học kỳ."
      />

      <section className="dashboard-grid" aria-label="Chỉ số tổng quan">
        {metrics.map((metric) => (
          <article className="metric-card" key={metric.label}>
            <span className="metric-card__label">{metric.label}</span>
            <strong className="metric-card__value">{metric.value}</strong>
            <span className="metric-card__note">{metric.note}</span>
          </article>
        ))}
      </section>

      <section className="dashboard-sections">
        <article className="content-card">
          <div className="content-card__header">
            <h2>Truy cập nhanh</h2>
            <span>Chọn một khu vực để bắt đầu</span>
          </div>
          <div className="quick-links">
            {quickLinks.map((link) => (
              <Link className="quick-link" key={link.to} to={link.to}>
                <span>{link.label}</span>
                <span className="quick-link__arrow" aria-hidden="true">→</span>
              </Link>
            ))}
          </div>
        </article>

        <article className="content-card">
          <div className="content-card__header">
            <h2>Trạng thái hệ thống</h2>
            <span>Khởi tạo</span>
          </div>
          <ul className="status-list">
            <li><span className="status-dot status-dot--success" aria-hidden="true" />Giao diện React đã sẵn sàng.</li>
            <li><span className="status-dot status-dot--success" aria-hidden="true" />Lớp gọi Spring Boot API đã được chuẩn bị.</li>
            <li><span className="status-dot status-dot--warning" aria-hidden="true" />Chờ hoàn thiện API và xác thực tài khoản.</li>
          </ul>
        </article>
      </section>
    </>
  )
}
