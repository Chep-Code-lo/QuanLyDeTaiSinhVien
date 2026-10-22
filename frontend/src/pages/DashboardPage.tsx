import { useEffect, useRef, useState } from 'react'
import { Bell, BookOpen, Check, Clock, FolderOpen, ListChecks } from 'lucide-react'
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
type TopicType = (typeof topicTypes)[number]['type']
type TooltipPosition = { x: number; y: number; side: 'left' | 'right' }
const number = (value: number) => value.toLocaleString('vi-VN')
const dateTime = (value: string) => formatDateTime(value)

export function DashboardPage() {
  const { user, hasRole } = useAuth()
  const isLecturer = hasRole('LECTURER') || hasRole('ADMIN')
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [revision, setRevision] = useState(0)
  const [hoveredTopicType, setHoveredTopicType] = useState<TopicType | null>(null)
  const [tooltipPosition, setTooltipPosition] = useState<TooltipPosition | null>(null)
  const chartWrapperRef = useRef<HTMLDivElement>(null)
  const lastUserId = useRef(user?.id)

  useEffect(() => {
    let active = true
    let pending = false
    let loaded = lastUserId.current === user?.id && summary !== null
    if (lastUserId.current !== user?.id) setSummary(null)
    lastUserId.current = user?.id
    setLoading(!loaded)
    const refresh = async () => {
      if (pending) return
      pending = true
      if (!loaded) setLoading(true)
      try {
        const data = await dashboardApi.summary()
        if (active) {
          setSummary(data)
          loaded = true
          setError(false)
        }
      } catch {
        if (active) {
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
  const chartSegments = counts.map((item) => {
    const percentage = summary?.totalTopics ? (item.count / summary.totalTopics) * 100 : 0
    const segment = { ...item, percentage, offset: chartOffset }
    chartOffset += percentage
    return segment
  })
  const updateTooltipPosition = (clientX: number, clientY: number) => {
    const bounds = chartWrapperRef.current?.getBoundingClientRect()
    if (!bounds) return

    const x = Math.max(0, Math.min(clientX - bounds.left, bounds.width))
    const y = Math.max(0, Math.min(clientY - bounds.top, bounds.height))
    setTooltipPosition({ x, y, side: x > bounds.width / 2 ? 'left' : 'right' })
  }
  const focusTopicSegment = (segment: (typeof chartSegments)[number]) => {
    const bounds = chartWrapperRef.current?.getBoundingClientRect()
    setHoveredTopicType(segment.type)
    if (!bounds) return

    const angle = ((segment.offset + segment.percentage / 2) / 100) * Math.PI * 2 - Math.PI / 2
    const radius = Math.min(bounds.width, bounds.height) * 0.42
    const x = bounds.width / 2 + radius * Math.cos(angle)
    const y = bounds.height / 2 + radius * Math.sin(angle)
    setTooltipPosition({ x, y, side: x > bounds.width / 2 ? 'left' : 'right' })
  }
  const clearHoveredTopic = () => {
    setHoveredTopicType(null)
    setTooltipPosition(null)
  }
  const hoveredTopic = counts.find((item) => item.type === hoveredTopicType)
  const hoveredPercentage =
    summary?.totalTopics && hoveredTopic
      ? Math.round((hoveredTopic.count / summary.totalTopics) * 100)
      : 0
  const chartTooltip = hoveredTopic
    ? `${number(hoveredTopic.count)} đề tài · ${hoveredPercentage}% tổng số`
    : summary
      ? `${number(summary.totalTopics)} đề tài thuộc ${number(counts.filter((item) => item.count > 0).length)} loại hình`
      : 'Đang tải dữ liệu thống kê'
  const metrics = [
    {
      label: 'Tổng đề tài',
      value: summary?.totalTopics,
      color: 'blue',
      icon: BookOpen,
    },
    {
      label: 'Đang đăng ký',
      value: summary?.registeringTopics,
      color: 'amber',
      icon: Clock,
    },
    {
      label: 'Đang thực hiện',
      value: summary?.inProgressTopics,
      color: 'violet',
      icon: ListChecks,
    },
    {
      label: 'Đã hoàn thành',
      value: summary?.completedTopics,
      color: 'green',
      icon: Check,
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
          <p role={error && !loading ? 'status' : undefined}>
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
              <>Chưa tải được dữ liệu tổng quan.{' '}{error && <button className="welcome-panel__retry" type="button" onClick={() => setRevision((value) => value + 1)}>Thử lại</button>}</>
            )}
          </p>
          {summary && error && <p role="status">Chưa cập nhật được dữ liệu mới. Đang hiển thị dữ liệu lần tải thành công gần nhất. <button type="button" className="welcome-panel__retry" onClick={() => setRevision((value) => value + 1)}>Thử lại</button></p>}
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
          {isLecturer ? 'Quản lý tiến độ' : 'Khám phá đề tài'}
        </Link>
      </section>
      <section className="dashboard-grid" aria-label="Chỉ số tổng quan" aria-busy={loading}>
        {metrics.map((metric) => (
          <article key={metric.label} className={`metric-card metric-card--${metric.color}`}>
            <span className="metric-card__icon"><metric.icon size={20} strokeWidth={1.8} aria-hidden="true" /></span>
            <div>
              <span className="metric-card__label">{metric.label}</span>
              <strong className="metric-card__value">
                {metric.value === undefined ? '—' : number(metric.value)}
              </strong>
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
              ref={chartWrapperRef}
              className={`donut-wrapper${hoveredTopic ? ' donut-wrapper--segment-active' : ''}`}
            >
              <svg
                className="donut"
                viewBox="0 0 100 100"
                width="166"
                height="166"
                preserveAspectRatio="xMidYMid meet"
                shapeRendering="geometricPrecision"
                role="group"
                aria-label="Biểu đồ phân bố đề tài theo loại"
              >
                <g transform="rotate(-90 50 50)">
                  <circle className="donut__track" cx="50" cy="50" r="42" fill="none" strokeWidth="16" />
                  {chartSegments.filter((item) => item.percentage > 0).map((item) => (
                    <circle
                      key={item.type}
                      className={`donut__segment${hoveredTopicType === item.type ? ' donut__segment--active' : ''}`}
                      cx="50"
                      cy="50"
                      r="42"
                      fill="none"
                      stroke={item.color}
                      strokeWidth="16"
                      pathLength="100"
                      strokeDasharray={`${item.percentage} ${100 - item.percentage}`}
                      strokeDashoffset={-item.offset}
                      tabIndex={0}
                      role="img"
                      aria-label={`${item.label}: ${number(item.count)} đề tài`}
                      aria-describedby="dashboard-topic-chart-tooltip"
                      onPointerEnter={(event) => {
                        setHoveredTopicType(item.type)
                        updateTooltipPosition(event.clientX, event.clientY)
                      }}
                      onPointerMove={(event) => updateTooltipPosition(event.clientX, event.clientY)}
                      onPointerLeave={clearHoveredTopic}
                      onFocus={() => focusTopicSegment(item)}
                      onBlur={clearHoveredTopic}
                    />
                  ))}
                </g>
                <circle className="donut__center" cx="50" cy="50" r="34" />
                <text className="donut__value" x="50" y="47" textAnchor="middle" dominantBaseline="middle">
                  {summary ? number(summary.totalTopics) : '—'}
                </text>
                <text className="donut__label" x="50" y="62" textAnchor="middle" dominantBaseline="middle">
                  Đề tài
                </text>
              </svg>
              <div
                className={`donut__tooltip${tooltipPosition?.side === 'left' ? ' donut__tooltip--left' : ''}`}
                id="dashboard-topic-chart-tooltip"
                role="tooltip"
                style={tooltipPosition ? { left: `${tooltipPosition.x}px`, top: `${tooltipPosition.y}px` } : undefined}
              >
                <strong>
                  {hoveredTopic
                    ? hoveredTopic.label
                    : summary
                      ? `Tổng ${number(summary.totalTopics)} đề tài`
                      : 'Đang tải số liệu'}
                </strong>
                <span>{chartTooltip}</span>
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
              <span className="task-list__icon task-list__icon--blue"><Bell size={18} aria-hidden="true" /></span>
              <div>
                <strong>Thông báo của bạn</strong>
                <small>
                  {summary ? `${number(summary.unreadNotifications)} thông báo chưa đọc` : '—'}
                </small>
              </div>
            </Link>
            <Link to="/registrations">
              <span className="task-list__icon task-list__icon--warning"><Clock size={18} aria-hidden="true" /></span>
              <div>
                <strong>Đợt đang mở đăng ký</strong>
                <small>
                  {summary
                    ? `${number(summary.openRegistrationPeriods)} đợt trong toàn hệ thống`
                    : '—'}
                </small>
              </div>
            </Link>
            <Link to="/topics">
              <span className="task-list__icon task-list__icon--red"><FolderOpen size={18} aria-hidden="true" /></span>
              <div>
                <strong>Đề tài đang mở đăng ký</strong>
                <small>
                  {summary
                    ? `${number(summary.registeringTopics)} đề tài chưa được giao cho nhóm`
                    : '—'}
                </small>
              </div>
            </Link>
          </div>
        </article>
      </section>
      <section className="dashboard-sections">
        <article className="content-card">
          <div className="content-card__header">
            <div>
              <h2>Đề tài mới công bố</h2>
              <span>Danh sách đề tài đã công bố gần đây</span>
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
