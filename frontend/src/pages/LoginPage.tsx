import { AppLogo } from '../components/AppLogo'

export function LoginPage() {
  return (
    <main className="login-page">
      <section className="login-card" aria-labelledby="login-title">
        <AppLogo />
        <h1 id="login-title">Đăng nhập hệ thống</h1>
        <p>Sử dụng tài khoản được cấp để truy cập các chức năng phù hợp với vai trò của bạn.</p>

        <form className="login-form" onSubmit={(event) => event.preventDefault()}>
          <div className="form-field">
            <label htmlFor="username">Tên đăng nhập</label>
            <input id="username" name="username" autoComplete="username" placeholder="Nhập mã số hoặc email" />
          </div>
          <div className="form-field">
            <label htmlFor="password">Mật khẩu</label>
            <input id="password" name="password" type="password" autoComplete="current-password" placeholder="Nhập mật khẩu" />
          </div>
          <p className="login-card__notice">Chức năng đăng nhập đang chờ endpoint JWT từ Spring Boot; biểu mẫu này mới là khung giao diện.</p>
          <button className="button--primary" type="submit" disabled>Đăng nhập</button>
        </form>
      </section>
    </main>
  )
}
