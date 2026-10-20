import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { academicApi } from '../api/academic'
import { topicApi } from '../api/topics'
import { registrationApi } from '../api/registrations'
import { milestoneApi } from '../api/milestones'
import { allPages, dateText, errorMessage, isWindowOpen, projectTypes } from '../api/student'
import { PageHeader } from '../components/PageHeader'
import { RemoteState } from '../components/RemoteState'
import { StatusBadge } from '../components/StatusBadge'
import { useAuth } from '../context/AuthContext'
import type { MilestoneResponse, ProjectAssignmentResponse, RegistrationPeriodResponse, TopicResponse } from '../types/domain'

export function DashboardPage() {
  const { user, hasRole } = useAuth()
  const [data, setData] = useState<{ topics: TopicResponse[]; projects: ProjectAssignmentResponse[]; periods: RegistrationPeriodResponse[]; milestones: MilestoneResponse[]; pending: number }>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const student = hasRole('STUDENT') && !hasRole('ADMIN') && !hasRole('LECTURER')
  const load = useCallback(async () => {
    setLoading(true); setError('')
    try {
      const [topics, projects, periods, pending] = await Promise.all([
        allPages(page => topicApi.list({ status: 'PUBLISHED', page, size: 100 })),
        allPages(page => academicApi.getProjectAssignments(undefined, page, 100)),
        allPages(page => academicApi.getRegistrationPeriods(undefined, page, 100)),
        registrationApi.list({ status: 'PENDING', size: 1 }),
      ])
      const milestones = student ? (await Promise.all(projects.map(p => milestoneApi.listByAssignment(p.id)))).flat() : []
      setData({ topics, projects, periods, milestones, pending: pending.totalElements })
    } catch (e) { setError(errorMessage(e)); setData(undefined) } finally { setLoading(false) }
  }, [student])
  useEffect(() => { void load() }, [load])
  const deadlines = data?.milestones.filter(m => ['OPEN', 'REVISION_REQUIRED'].includes(m.status)).sort((a,b) => a.dueAt.localeCompare(b.dueAt)) ?? []
  return <>
    <PageHeader title="Tổng quan" description="Dữ liệu hiện tại trong phạm vi tài khoản của bạn." />
    <section className="welcome-panel"><div><span className="welcome-panel__eyebrow">CỔNG QUẢN LÝ ĐỀ TÀI</span><h2>Chào {user?.fullName}.</h2><p>Theo dõi đợt đăng ký, đề tài và các mốc thực hiện của bạn tại đây.</p></div><Link className="welcome-panel__action" to="/topics">Khám phá đề tài →</Link></section>
    <RemoteState loading={loading} error={error} retry={load} />
    {data && !loading && <>
      <section className="dashboard-grid">{[
        ['Đề tài đã công bố', data.topics.length, '/topics', 'blue'],
        ['Đăng ký chờ duyệt', data.pending, '/registrations', 'amber'],
        [student ? 'Dự án của nhóm' : 'Dự án', data.projects.length, '/progress', 'violet'],
        ['Đã hoàn thành', data.projects.filter(p => p.status === 'COMPLETED').length, '/progress', 'green'],
      ].map(([label,count,to,color]) => <Link key={label} to={String(to)} className={`metric-card metric-card--${color}`}><div><span className="metric-card__label">{label}</span><strong className="metric-card__value">{count}</strong><span className="metric-card__note">Xem chi tiết →</span></div></Link>)}</section>
      <section className="dashboard-sections"><article className="content-card"><h2>Đợt đang nhận đăng ký</h2>{data.periods.filter(p => isWindowOpen(p,'registration')).map(p => <div className="topic-preview" key={p.id}><div><strong>{p.name}</strong><small>{projectTypes[p.projectType]} · Đến {dateText(p.registrationEndAt)}</small></div><Link to="/registrations" className="text-button">Đăng ký</Link></div>)}{!data.periods.some(p => isWindowOpen(p,'registration')) && <p>Hiện không có đợt đang nhận đăng ký.</p>}<h3>Đợt nhận đề xuất</h3>{data.periods.filter(p => isWindowOpen(p,'proposal')).map(p => <p key={p.id}>{p.name} · Đến {dateText(p.proposalEndAt)} <Link to="/topics">Đề xuất</Link></p>)}{!data.periods.some(p => isWindowOpen(p,'proposal')) && <p>Hiện không có đợt nhận đề xuất.</p>}</article>
      <article className="content-card"><h2>{student ? 'Báo cáo cần theo dõi' : 'Dự án gần đây'}</h2>{student ? deadlines.slice(0,5).map(m => <div className="topic-preview" key={m.id}><div><strong>{m.title}</strong><small>{dateText(m.dueAt)} {new Date(m.dueAt).getTime() < Date.now() ? '· Quá hạn' : ''}</small></div><Link to="/progress" className="text-button">Xem</Link></div>) : data.projects.slice(0,5).map(p => <p key={p.id}>{p.topicTitle} · <StatusBadge status={p.status} /></p>)}{student && !deadlines.length && <p>Không có báo cáo đang chờ nộp hoặc yêu cầu chỉnh sửa.</p>}</article></section>
      <section className="page-card"><h2>Đề tài đã công bố gần đây</h2>{data.topics.slice(0,5).map(t => <div className="topic-preview" key={t.id}><div><strong>{t.title}</strong><small>{t.code} · {t.departmentName}</small></div><Link to={`/topics?topic=${t.id}`} className="text-button">Chi tiết</Link></div>)}{!data.topics.length && <p>Chưa có đề tài được công bố trong phạm vi của bạn.</p>}</section>
    </>}
  </>
}
