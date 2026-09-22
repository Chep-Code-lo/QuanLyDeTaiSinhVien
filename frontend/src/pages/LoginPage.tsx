import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AppLogo } from '../components/AppLogo'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'

type LoginFormProps = {
  onSuccess?: () => void
  onClose?: () => void
}

export function LoginForm({ onSuccess, onClose }: LoginFormProps) {
  const { login } = useAuth()
  const { showSuccess, showError } = useToast()
  const navigate = useNavigate()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!username.trim() || !password) {
      showError('Vui lòng nhập tên đăng nhập và mật khẩu')
      return
    }

    try {
      setSubmitting(true)
      await login(username.trim(), password)
      showSuccess('Đăng nhập hệ thống thành công!')
      if (onSuccess) onSuccess()
      else navigate('/dashboard')
    } catch (err: any) {
      showError(err.response?.data?.message || 'Đăng nhập thất bại. Vui lòng kiểm tra lại thông tin.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
      <section className="login-card" aria-labelledby="login-title">
        {onClose && <button className="login-card__close" type="button" aria-label="Đóng đăng nhập" onClick={onClose}>×</button>}
        <AppLogo />
        <h1 id="login-title">Đăng nhập hệ thống</h1>
        <p>Hệ thống quản lý đề tài của sinh viên Trường Đại học Công Nghệ Kỹ Thuật Thành phố Hồ Chí Minh.</p>

        <form className="login-form" onSubmit={handleSubmit}>
          <div className="form-field">
            <label htmlFor="username">Tên đăng nhập / Email</label>
            <input
              id="username"
              name="username"
              type="text"
              autoComplete="username"
              placeholder="Nhập username hoặc email"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </div>
          <div className="form-field">
            <label htmlFor="password">Mật khẩu</label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              placeholder="Nhập mật khẩu"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>

          <button className="button--primary mt-2" type="submit" disabled={submitting}>
            {submitting ? 'Đang xác thực...' : 'Đăng nhập'}
          </button>
        </form>
      </section>
  )
}

export function LoginPage() {
  return <main className="login-page"><LoginForm /></main>
}
