import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { dashboardApi } from '../api/dashboard'
import type { DashboardSummary } from '../api/dashboard'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import { useAuth } from '../context/AuthContext'
import { parseUtcDateTime, formatDateTime } from '../utils/dateTime'
import { greetingName } from '../utils/userDisplay'

const topicTypes = [
  { type: 'GRADUATION', label: 'Khóa luận tốt nghiệp', color: '#155eef', dot: 'blue' },
  { type: 'SPECIALIZED', label: 'Tiểu luận chuyên ngành', color: '#ef4444', dot: 'red' },
  { type: 'RESEARCH', label: 'Nghiên cứu khoa học', color: '#f59e0b', dot: 'yellow' },
  { type: 'COURSE', label: 'Đề tài môn học', color: '#cbd5e1', dot: 'slate' },
] as const
const number = (value: number) => value.toLocaleString('vi-VN')
const dateTime = (value: string) => formatDateTime(value)

export function DashboardPage() {
  const { user, hasRole } = useAuth()
  const isLecturer = hasRole('LECTURER') || hasRole('ADMIN')
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    let active = true
    let pending = false
    const refresh = async () => {
      if (pending) return
      pending = true
      setLoading(true)
      try {
        const data = await dashboardApi.summary()
        if (active) {
          setSummary(data)
          setError(false)
        }
      } catch {
        if (active) {
          setSummary(null)
          setError(true)
        }
      } finally {
        pending = false
        if (active) setLoading(false)
      }
    }
    void refresh()
    const timer = window.setInterval(() => void refresh(), 60_000)
    window.addEventListener('focus', refresh)
    return () => {
      active = false
      window.clearInterval(timer)
      window.removeEventListener('focus', refresh)
    }
  }, [user?.id, revision])

  const counts = topicTypes.map((type) => ({
    ...type,
    count: summary?.topicTypes.find((item) => item.projectType === type.type)?.count ?? 0,
  }))
  let chartOffset = 0
  const chartStops = counts.map((item) => {
    const start = chartOffset
    chartOffset += summary?.totalTopics ? (item.count / summary.totalTopics) * 100 : 0
    return `${item.color} ${start}% ${chartOffset}%`
  })
  const chartBackground = summary?.totalTopics
    ? `conic-gradient(${chartStops.join(', ')})`
    : '#e2e8f0'
  const metrics = [
    {
      label: 'Tổng đề tài',
      value: summary?.totalTopics,
      color: 'blue',
      icon: '▣',
      note: 'Tất cả đề tài trong hệ thống',
    },
    {
      label: 'Đang đăng ký',
      value: summary?.registeringTopics,
      color: 'amber',
      icon: '◷',
      note: 'Đề tài đang mở cho sinh viên đăng ký',
    },
    {
      label: 'Đang thực hiện',
      value: summary?.inProgressTopics,
      color: 'violet',
      icon: '◉',
      note: 'Đề tài đã giao cho nhóm và đang thực hiện',
    },
    {
      label: 'Đã hoàn thành',
      value: summary?.completedTopics,
      color: 'green',
      icon: '✓',
      note: 'Đề tài có trạng thái thực hiện đã hoàn thành',
    },
  ]

  return (
    <>
      <PageHeader title="Hệ thống quản lý đề tài sinh viên" />
      <section className="welcome-panel">
        <div>
          <span className="welcome-panel__eyebrow">
            {summary?.currentSemester
              ? `HỌC KỲ ${summary.currentSemester.semesterNo} - ${summary.currentSemester.academicYear}`
              : 'TỔNG QUAN TOÀN HỆ THỐNG'}
          </span>
          <h2>Chào mừng trở lại, {greetingName(user)}.</h2>
          <p>
            {summary ? (
              <>
                Bạn có <strong>{number(summary.unreadNotifications)} thông báo chưa đọc</strong>.{' '}
                {summary.nextRegistrationCloseAt
                  ? `Đợt đăng ký gần nhất đóng lúc ${dateTime(summary.nextRegistrationCloseAt)}.`
                  : 'Hiện không có đợt đang mở đăng ký đề tài.'}
              </>
            ) : loading ? (
              'Đang tải dữ liệu tổng quan...'
            ) : (
              'Chưa tải được dữ liệu tổng quan.'
            )}
          </p>
          {user?.activeAppointments?.length ? (
            <div className="welcome-panel__roles">
              {user.activeAppointments.map((item) => (
                <span key={item.id}>
                  {item.positionCode === 'DEAN' ? 'Trưởng khoa' : 'Trưởng bộ môn'}
                </span>
              ))}
            </div>
          ) : null}
        </div>
        <Link className="welcome-panel__action" to={isLecturer ? '/progress' : '/registrations'}>
          {isLecturer ? 'Quản lý tiến độ' : 'Khám phá đề tài'} <span>→</span>
        </Link>
      </section>
      {error && (
        <div className="page-card" role="alert">
          Không tải được số liệu. Vui lòng thử lại.{' '}
          <button className="text-button" onClick={() => setRevision((value) => value + 1)}>
            Thử lại
          </button>
        </div>
      )}
      <section className="dashboard-grid" aria-label="Chỉ số tổng quan" aria-busy={loading}>
        {metrics.map((metric) => (
          <article key={metric.label} className={`metric-card metric-card--${metric.color}`}>
            <span className="metric-card__icon">{metric.icon}</span>
            <div>
              <span className="metric-card__label">{metric.label}</span>
              <strong className="metric-card__value">
                {metric.value === undefined ? '—' : number(metric.value)}
              </strong>
              <span className="metric-card__note">{metric.note}</span>
            </div>
          </article>
        ))}
      </section>
      <section className="dashboard-insights">
        <article className="content-card chart-card">
          <div className="content-card__header">
            <div>
              <h2>Thống kê đề tài theo loại</h2>
              <span>
                {summary
                  ? `Toàn hệ thống · Cập nhật ${dateTime(summary.updatedAt)}`
                  : 'Đang chờ số liệu'}
              </span>
            </div>
            <button
              className="text-button"
              disabled={loading}
              onClick={() => setRevision((value) => value + 1)}
            >
              {loading ? 'Đang tải...' : 'Cập nhật'}
            </button>
          </div>
          <div className="chart-layout">
            <div
              className="donut"
              style={{ background: chartBackground }}
              role="img"
              aria-label={
                summary ? `Tổng ${number(summary.totalTopics)} đề tài theo loại` : 'Chưa có số liệu'
              }
            >
              <div>
                <strong>{summary ? number(summary.totalTopics) : '—'}</strong>
                <span>Đề tài</span>
              </div>
            </div>
            <ul className="chart-legend">
              {counts.map((item) => (
                <li key={item.type}>
                  <i className={`dot dot--${item.dot}`} />
                  {item.label} <b>{summary ? number(item.count) : '—'}</b>
                </li>
              ))}
            </ul>
          </div>
        </article>
        <article className="content-card activity-card">
          <div className="content-card__header">
            <div>
              <h2>Thông tin cần theo dõi</h2>
              <span>Dữ liệu hiện tại</span>
            </div>
            {summary && (
              <span className="badge badge-info">
                {number(summary.unreadNotifications)} chưa đọc
              </span>
            )}
          </div>
          <div className="task-list">
            <Link to="/announcements">
              <span className="task-list__icon task-list__icon--blue">✦</span>
              <div>
                <strong>Thông báo của bạn</strong>
                <small>
                  {summary ? `${number(summary.unreadNotifications)} thông báo chưa đọc` : '—'}
                </small>
              </div>
              <b>›</b>
            </Link>
            <Link to="/registrations">
              <span className="task-list__icon task-list__icon--warning">⌛</span>
              <div>
                <strong>Đợt đang mở đăng ký</strong>
                <small>
                  {summary
                    ? `${number(summary.openRegistrationPeriods)} đợt trong toàn hệ thống`
                    : '—'}
                </small>
              </div>
              <b>›</b>
            </Link>
            <Link to="/topics">
              <span className="task-list__icon task-list__icon--red">▣</span>
              <div>
                <strong>Đề tài đang mở đăng ký</strong>
                <small>
                  {summary
                    ? `${number(summary.registeringTopics)} đề tài chưa được giao cho nhóm`
                    : '—'}
                </small>
              </div>
              <b>›</b>
            </Link>
          </div>
        </article>
      </section>
      <section className="dashboard-sections">
        <article className="content-card">
          <div className="content-card__header">
            <div>
              <h2>Đề tài mới cập nhật</h2>
              <span>Danh sách được cập nhật gần đây</span>
            </div>
            <Link className="text-button" to="/topics">
              Xem tất cả
            </Link>
          </div>
          {summary?.recentTopics.map((topic) => (
            <div className="topic-preview" key={topic.id}>
              <div>
                <span className="topic-preview__code">{topic.code}</span>
                <strong>{topic.title}</strong>
                <small>{topic.departmentName}</small>
              </div>
              <StatusBadge status={topic.status} />
            </div>
          ))}
          {summary && summary.recentTopics.length === 0 && (
            <p className="text-secondary">Chưa có đề tài.</p>
          )}
        </article>
        <article className="content-card">
          <div className="content-card__header">
            <div>
              <h2>Lịch đăng ký sắp tới</h2>
              <span>Các mốc của đợt đang hoạt động</span>
            </div>
            <Link className="text-button" to="/registrations">
              Đăng ký đề tài
            </Link>
          </div>
          {summary?.upcomingDeadlines.map((deadline) => {
            const due = parseUtcDateTime(deadline.dueAt)
            const days = Math.max(
              0,
              Math.ceil(
                (due.getTime() - parseUtcDateTime(summary.updatedAt).getTime()) / 86_400_000,
              ),
            )
            return (
              <div className="deadline-item" key={`${deadline.periodId}-${deadline.title}`}>
                <time dateTime={due.toISOString()} title={dateTime(deadline.dueAt)}>
                  <b>{due.getDate()}</b>
                  <span>THG {due.getMonth() + 1}</span>
                </time>
                <div>
                  <strong>{deadline.title}</strong>
                  <small>
                    {deadline.periodName} · {dateTime(deadline.dueAt)}
                  </small>
                </div>
                <span className="badge badge-info">
                  {days === 0 ? 'Hôm nay' : `Còn ${days} ngày`}
                </span>
              </div>
            )
          })}
          {summary && summary.upcomingDeadlines.length === 0 && (
            <p className="text-secondary">Chưa có lịch đăng ký sắp tới.</p>
          )}
        </article>
      </section>
    </>
  )
}
