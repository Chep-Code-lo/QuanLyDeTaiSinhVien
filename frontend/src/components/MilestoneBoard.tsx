import { useEffect, useState } from 'react'
import {
  ArrowDownUp, ArrowRight, CalendarDays, ChevronLeft, ChevronRight,
  FileText, Paperclip, PencilLine, Send, Users,
} from 'lucide-react'
import { AnimatedSelect } from './AnimatedSelect'
import { StatusBadge } from './StatusBadge'
import { formatDate, formatDateTime, parseUtcDateTime } from '../utils/dateTime'
import { milestoneCalendarDays, milestoneDayKey, milestoneDeadline } from '../utils/milestoneSchedule'
import type { MilestoneResponse, SubmissionResponse } from '../types/domain'
import './MilestoneBoard.css'

type MilestoneBoardProps = {
  milestones: MilestoneResponse[]
  loading: boolean
  error?: string | null
  onRetry?: () => void
  hasAssignment: boolean
  canManage: boolean
  canEdit: boolean
  reminding: boolean
  onEdit: (milestone: MilestoneResponse) => void
  onFeedback: (submission: SubmissionResponse) => void
  onRemind: () => void
}

export function MilestoneBoard({
  milestones, loading, hasAssignment, canManage, canEdit, reminding,
  onEdit, onFeedback, onRemind, error, onRetry,
}: MilestoneBoardProps) {
  const [sort, setSort] = useState('deadline-asc')
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [month, setMonth] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1))
  const [selectedDay, setSelectedDay] = useState<string | null>(null)
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 60_000)
    return () => window.clearInterval(timer)
  }, [])

  const sorted = [...milestones].sort((a, b) => sort === 'title'
    ? a.title.localeCompare(b.title, 'vi')
    : (parseUtcDateTime(a.dueAt).getTime() - parseUtcDateTime(b.dueAt).getTime()) * (sort === 'deadline-desc' ? -1 : 1))
  const upcoming = milestones.filter((m) => m.status !== 'ACCEPTED' && parseUtcDateTime(m.dueAt) >= now)
    .sort((a, b) => parseUtcDateTime(a.dueAt).getTime() - parseUtcDateTime(b.dueAt).getTime())
  const pending = upcoming.filter((m) => !m.submissions?.length)
  const today = milestoneDayKey(now)
  const focusMilestone = (milestone: MilestoneResponse) => {
    setSelectedDay(milestoneDayKey(parseUtcDateTime(milestone.dueAt)))
    document.getElementById(`milestone-card-${milestone.id}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }

  return (
    <div className="milestone-board">
      <section className="milestone-board__list" aria-labelledby="milestone-list-title">
        <div className="milestone-board__toolbar">
          <h2 id="milestone-list-title">
            Danh sách mốc báo cáo <span className="milestone-board__count">{milestones.length}</span>
          </h2>
          <div className="milestone-board__sort">
            <ArrowDownUp size={14} aria-hidden="true" />
            <AnimatedSelect aria-label="Sắp xếp mốc báo cáo" value={sort} onChange={(event) => setSort(event.target.value)}>
              <option value="deadline-asc">Hạn nộp gần nhất</option>
              <option value="deadline-desc">Hạn nộp xa nhất</option>
              <option value="title">Tên mốc báo cáo</option>
            </AnimatedSelect>
          </div>
        </div>
        {loading ? (
          <div className="milestone-board__empty"><span className="spinner" /><p>Đang tải mốc báo cáo...</p></div>
        ) : error ? (
          <div className="milestone-board__empty" role="alert">
            <FileText size={32} aria-hidden="true" />
            <h3 className="section-title">Không thể tải mốc báo cáo</h3>
            <p>{error}</p>
            {onRetry && <button type="button" className="milestone-board__button" onClick={onRetry}>Thử lại</button>}
          </div>
        ) : milestones.length === 0 ? (
          <div className="milestone-board__empty">
            <FileText size={32} aria-hidden="true" />
            <h3 className="section-title">Chưa có mốc tiến độ nào được tạo</h3>
            <p>{!hasAssignment ? 'Chọn đề tài để xem các mốc báo cáo.' : canManage
              ? 'Bấm “Tạo mốc tiến độ mới” để thiết lập lịch báo cáo.'
              : 'Giảng viên chưa thiết lập mốc tiến độ cho đề tài này.'}</p>
          </div>
        ) : (
          <ol className="milestone-board__timeline">
            {sorted.map((milestone, index) => {
              const deadline = milestoneDeadline(milestone.dueAt, now)
              const reports = [...(milestone.submissions ?? [])].sort((a, b) => b.versionNo - a.versionNo)
              const submitted = reports.length > 0
              const progress = submitted ? 100 : 0
              const expanded = expandedId === milestone.id
              const selected = selectedDay === milestoneDayKey(parseUtcDateTime(milestone.dueAt))
              return (
                <li key={milestone.id} className="milestone-board__step">
                  <span className="milestone-board__number">{String(index + 1).padStart(2, '0')}</span>
                  <article id={`milestone-card-${milestone.id}`} className={`milestone-board__card${selected ? ' milestone-board__card--selected' : ''}`}>
                    <div className="milestone-board__card-heading">
                      <h3>{milestone.title}</h3>
                      {milestone.status !== 'ACCEPTED' && <span className={`milestone-board__tag milestone-board__tag--${deadline.tone}`}>
                        {deadline.tone === 'late' ? 'Đã quá hạn' : deadline.tone === 'soon' ? 'Sắp đến hạn' : 'Theo kế hoạch'}
                      </span>}
                      <StatusBadge status={milestone.status} label={milestone.status === 'OPEN' ? 'Đang mở' : undefined} />
                    </div>
                    <p className="milestone-board__description">{milestone.description}</p>
                    <div className="milestone-board__metrics">
                      <div className="milestone-board__deadline">
                        <span className="milestone-board__icon"><CalendarDays size={20} aria-hidden="true" /></span>
                        <div>
                          <span className="milestone-board__label">Hạn nộp</span>
                          <div className="milestone-board__deadline-value">
                            <time dateTime={milestone.dueAt}>{formatDateTime(milestone.dueAt).slice(0, -3).replace(' ', ' · ')}</time>
                            {milestone.status !== 'ACCEPTED' && <span className={`milestone-board__remaining milestone-board__tag--${deadline.tone}`}>{deadline.label}</span>}
                          </div>
                        </div>
                      </div>
                      <div className="milestone-board__submission-metric">
                        <span className="milestone-board__icon"><Users size={20} aria-hidden="true" /></span>
                        <div className="milestone-board__progress-copy">
                          <span className="milestone-board__label">Báo cáo đã nộp</span>
                          <div className="milestone-board__progress-line">
                            <strong>{submitted ? 1 : 0} / 1 nhóm</strong>
                            <div className="milestone-board__progress" role="progressbar" aria-label={`Tiến độ nộp ${milestone.title}`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress}>
                              <span style={{ width: `${progress}%` }} />
                            </div>
                            <span>{progress}%</span>
                          </div>
                        </div>
                      </div>
                    </div>
                    <div className="milestone-board__card-footer">
                      <div className="milestone-board__file"><Paperclip size={17} aria-hidden="true" /><span>{reports[0]?.file?.originalName ?? (submitted ? `${reports.length} phiên bản báo cáo` : 'Chưa có báo cáo được nộp')}</span></div>
                      <div className="milestone-board__actions">
                        {canManage && <button className="milestone-board__button" type="button" disabled={!canEdit || milestone.status === 'ACCEPTED'} onClick={() => onEdit(milestone)}><PencilLine size={15} aria-hidden="true" />Chỉnh sửa</button>}
                        <button className="milestone-board__button milestone-board__button--primary" type="button" aria-expanded={expanded} aria-controls={expanded ? `milestone-reports-${milestone.id}` : undefined} onClick={() => setExpandedId(expanded ? null : milestone.id)}>
                          {expanded ? 'Ẩn báo cáo' : 'Xem báo cáo'}<ArrowRight size={15} aria-hidden="true" />
                        </button>
                      </div>
                    </div>
                    {expanded && (
                      <div className="milestone-board__reports" id={`milestone-reports-${milestone.id}`}>
                        <h4>Báo cáo đã nộp ({reports.length})</h4>
                        {reports.length === 0 ? <p>Chưa có bản nộp báo cáo nào từ sinh viên.</p> : reports.map((report) => (
                          <div className="milestone-board__report" key={report.id}>
                            <div className="milestone-board__report-heading">
                              <strong>Phiên bản #{report.versionNo} · {report.submittedByName}</strong>
                              {canManage && <button className="milestone-board__button" type="button" onClick={() => onFeedback(report)}>Nhận xét</button>}
                            </div>
                            <time dateTime={report.submittedAt}>{formatDateTime(report.submittedAt)}</time>
                            {report.note && <p>{report.note}</p>}
                            {report.file && <p><Paperclip size={14} aria-hidden="true" /> {report.file.originalName} · {(report.file.sizeBytes / 1024).toFixed(1)} KB</p>}
                            {(report.feedback ?? []).map((feedback) => <div key={feedback.id} className="milestone-board__feedback"><strong>{feedback.lecturerName}:</strong> {feedback.content}<time dateTime={feedback.createdAt}>{formatDateTime(feedback.createdAt)}</time></div>)}
                          </div>
                        ))}
                      </div>
                    )}
                  </article>
                </li>
              )
            })}
          </ol>
        )}
      </section>

      <aside className="milestone-board__sidebar" aria-label="Lịch và nhắc hạn báo cáo">
        <section className="milestone-board__calendar" aria-label="Lịch hạn báo cáo">
          <div className="milestone-board__calendar-heading">
            <h3><CalendarDays size={17} aria-hidden="true" />Tháng {month.getMonth() + 1}, {month.getFullYear()}</h3>
            <div>
              <button type="button" aria-label="Tháng trước" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() - 1, 1))}><ChevronLeft size={16} /></button>
              <button type="button" aria-label="Tháng sau" onClick={() => setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))}><ChevronRight size={16} /></button>
            </div>
          </div>
          <div className="milestone-board__calendar-grid">
            {['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'].map((day) => <span className="milestone-board__weekday" key={day}>{day}</span>)}
            {milestoneCalendarDays(month).map((date, index) => {
              if (!date) return <span key={`blank-${index}`} />
              const key = milestoneDayKey(date)
              const due = milestones.filter((m) => milestoneDayKey(parseUtcDateTime(m.dueAt)) === key)
              return <button
                key={key} type="button"
                className={`${due.length ? 'milestone-board__day--deadline' : ''}${selectedDay === key ? ' milestone-board__day--selected' : ''}${today === key ? ' milestone-board__day--today' : ''}`}
                aria-label={`${formatDate(date)}${due.length ? `, ${due.length} mốc báo cáo` : ''}`}
                aria-pressed={selectedDay === key}
                aria-current={today === key ? 'date' : undefined}
                onClick={() => {
                  setSelectedDay(key)
                  if (due[0]) focusMilestone(due[0])
                }}
              >{date.getDate()}</button>
            })}
          </div>
          <div className="milestone-board__calendar-legend"><span><i />Hạn báo cáo</span><span><i />Ngày đang chọn</span></div>
        </section>
        <section className="milestone-board__upcoming">
          <h3><CalendarDays size={17} aria-hidden="true" />Mốc sắp tới<ChevronRight size={17} aria-hidden="true" /></h3>
          {loading ? <p className="milestone-board__sidebar-note">Đang tải...</p> : error ? <p className="milestone-board__sidebar-note">Chưa tải được dữ liệu mốc báo cáo.</p> : upcoming.length === 0 ? <p className="milestone-board__sidebar-note">Chưa có mốc sắp tới.</p> : upcoming.slice(0, 3).map((milestone) => {
            const date = parseUtcDateTime(milestone.dueAt)
            return <button key={milestone.id} type="button" className="milestone-board__upcoming-item" onClick={() => {
              setMonth(new Date(date.getFullYear(), date.getMonth(), 1))
              focusMilestone(milestone)
            }}>
              <span className="milestone-board__date-chip"><strong>{date.getDate()}</strong><small>TH{date.getMonth() + 1}</small></span>
              <span><strong>{milestone.title}</strong><small>{milestoneDeadline(milestone.dueAt, now).label} để hoàn thành</small></span>
              <ChevronRight size={16} aria-hidden="true" />
            </button>
          })}
        </section>
        {canManage && <section className="milestone-board__reminder">
          <strong>Nhắc nhở sinh viên trước hạn nộp</strong>
          <p>Gửi thông báo nhắc hạn cho nhóm chưa nộp báo cáo.</p>
          <button type="button" className="milestone-board__button" disabled={!canEdit || loading || !!error || !pending.length || reminding} onClick={onRemind}><Send size={15} aria-hidden="true" />{reminding ? 'Đang gửi...' : 'Nhắc nhở'}</button>
        </section>}
      </aside>
    </div>
  )
}
