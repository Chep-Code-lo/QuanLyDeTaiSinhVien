import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <main className="not-found-page">
      <section className="not-found-card">
        <span className="not-found-card__code">404</span>
        <h1>Không tìm thấy trang</h1>
        <p>Đường dẫn bạn mở không tồn tại hoặc đã được thay đổi.</p>
        <Link className="button--outline" to="/">Về trang tổng quan</Link>
      </section>
    </main>
  )
}
