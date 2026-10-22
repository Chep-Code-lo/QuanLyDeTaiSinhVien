import { useCallback, useEffect, useState } from 'react'
import { Upload } from 'lucide-react'
import { groupApi } from '../api/groups'
import { milestoneApi } from '../api/milestones'
import { dateText, errorMessage, studentApi } from '../api/student'
import { AnimatedSelect } from '../components/AnimatedSelect'
import { PageHeader } from '../components/PageHeader'
import { DownloadButton, RemoteState } from '../components/RemoteState'
import { SelectionPanel } from '../components/SelectionPanel'
import { StatusBadge } from '../components/StatusBadge'
import { MilestoneCalendar } from '../components/MilestoneCalendar'
import { parseUtcDateTime } from '../utils/dateTime'
import { milestoneDayKey } from '../utils/milestoneSchedule'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import type { MilestoneResponse, ProjectAssignmentResponse, StudentGroupResponse } from '../types/domain'

const milestoneLabels: Record<MilestoneResponse['milestoneType'], string> = {
  PROPOSAL: 'Đề cương',
  PROGRESS: 'Báo cáo tiến độ',
  FINAL: 'Báo cáo cuối kỳ & mã nguồn',
}

export function StudentProgressPage() {
  const { user } = useAuth()
  const { showSuccess, showError } = useToast()
  const [projects, setProjects] = useState<ProjectAssignmentResponse[]>([])
  const [groups, setGroups] = useState<StudentGroupResponse[]>([])
  const [milestones, setMilestones] = useState<MilestoneResponse[]>([])
  const [selected, setSelected] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [uploading, setUploading] = useState(false)
  const [target, setTarget] = useState<MilestoneResponse>()
  const [file, setFile] = useState<File>()
  const [note, setNote] = useState('')
  const [currentTime, setCurrentTime] = useState(() => Date.now())
  const [month, setMonth] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1))
  const [selectedDay, setSelectedDay] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const [projectRows, groupRows] = await Promise.all([
        studentApi.projectAssignments(),
        groupApi.mine(),
      ])
      const milestoneRows = (
        await Promise.all(projectRows.map((project) => milestoneApi.listByAssignment(project.id)))
      ).flat()
      setProjects(projectRows)
      setGroups(groupRows)
      setMilestones(milestoneRows)
    } catch (loadError) {
      setError(errorMessage(loadError))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    const timer = window.setInterval(() => setCurrentTime(Date.now()), 30_000)
    return () => window.clearInterval(timer)
  }, [])

  const canSubmit = (milestone: MilestoneResponse) => {
    const project = projects.find((item) => item.id === milestone.assignmentId)
    return Boolean(
      project?.status === 'IN_PROGRESS' &&
        milestone.status !== 'ACCEPTED' &&
        groups
          .find((group) => group.id === project.groupId)
          ?.members.some((member) => member.userId === user?.id && member.memberRole === 'LEADER'),
    )
  }

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!target || !file || uploading) return
    if (!file.size || file.size > 20 * 1024 * 1024) {
      showError('Chọn tệp có nội dung, tối đa 20 MB.')
      return
    }

    setUploading(true)
    try {
      const uploaded = await studentApi.upload(file)
      await studentApi.submit(target.id, uploaded.id, note)
      showSuccess('Báo cáo đã được lưu trên hệ thống.')
      setTarget(undefined)
      setFile(undefined)
      setNote('')
      await load()
    } catch (submitError) {
      showError(errorMessage(submitError))
    } finally {
      setUploading(false)
    }
  }

  const visible = milestones
    .filter((milestone) => !selected || milestone.assignmentId === Number(selected))
    .sort((left, right) => new Date(left.dueAt).getTime() - new Date(right.dueAt).getTime())

  return (
    <>
      <PageHeader
        title="Tiến độ & báo cáo"
      />
      <RemoteState loading={loading} error={error} retry={load} />

      {!loading && !error && (
        <>
          <SelectionPanel
            id="student-project"
            label="Dự án của nhóm"
            description="Theo dõi mốc báo cáo của các dự án bạn tham gia."
          >
            <AnimatedSelect
              id="student-project"
              value={selected}
              disabled={!projects.length}
              onChange={(event) => { setSelected(event.target.value); setSelectedDay(null) }}
            >
              <option value="">
                {projects.length ? 'Tất cả dự án của tôi' : 'Chưa có dự án được phân công'}
              </option>
              {projects.map((project) => (
                <option key={project.id} value={project.id}>
                  {project.topicCode} · {project.topicTitle} ({project.groupName})
                </option>
              ))}
            </AnimatedSelect>
          </SelectionPanel>

          {!projects.length && (
            <section className="page-card">
              <div className="content-state">
                <strong>Nhóm của bạn chưa được giao đề tài</strong>
                <p>Báo cáo sẽ xuất hiện sau khi đăng ký được duyệt và giảng viên tạo mốc.</p>
              </div>
            </section>
          )}

          {projects.length > 0 && (
            <p className="text-secondary">
              {visible.filter((milestone) => milestone.status === 'ACCEPTED').length}/
              {visible.length} mốc đã được chấp nhận.
            </p>
          )}

          {projects.length > 0 && !visible.length && (
            <section className="page-card">
              <p>Giảng viên chưa tạo mốc báo cáo cho dự án này.</p>
            </section>
          )}

          {projects.length > 0 && <div className="milestone-board student-progress-layout">
          <div className="student-timeline">
            {visible.map((milestone, index) => {
              const project = projects.find((item) => item.id === milestone.assignmentId)
              const isPastDue =
                new Date(milestone.dueAt).getTime() < currentTime &&
                milestone.status !== 'ACCEPTED'

              return (
                <section id={`student-milestone-${milestone.id}`} className={`page-card student-timeline__item${selectedDay === milestoneDayKey(parseUtcDateTime(milestone.dueAt)) ? ' student-timeline__item--selected' : ''}`} key={milestone.id}>
                  <span className="student-timeline__marker" aria-hidden="true">
                    {index + 1}
                  </span>
                  <div className="content-card__header">
                    <div>
                      <span className="student-milestone-type">
                        {milestoneLabels[milestone.milestoneType]}
                      </span>
                      <h2>{milestone.title}</h2>
                      <small>{project?.topicTitle}</small>
                    </div>
                    <StatusBadge status={milestone.status} />
                  </div>
                  <p className="student-prose">{milestone.description}</p>
                  <p>
                    <strong>Hạn nộp:</strong> {dateText(milestone.dueAt)}
                    {isPastDue ? ' · Đã qua hạn' : ''}
                  </p>
                  {milestone.milestoneType === 'FINAL' && (
                    <p className="student-notice">
                      Tệp nộp cuối kỳ cần bao gồm báo cáo hoàn chỉnh và mã nguồn sản phẩm
                      (có thể đóng gói thành một tệp ZIP).
                    </p>
                  )}
                  {canSubmit(milestone) ? (
                    <button
                      className="btn btn-primary"
                      disabled={uploading}
                      onClick={() => {
                        setTarget(milestone)
                        setFile(undefined)
                        setNote('')
                      }}
                    >
                      <Upload size={16} aria-hidden="true" />
                      {milestone.submissions.length ? 'Nộp phiên bản mới' : 'Nộp báo cáo'}
                    </button>
                  ) : (
                    <p className="student-notice">
                      {milestone.status === 'ACCEPTED'
                        ? 'Mốc đã được chấp nhận, không thể nộp lại.'
                        : project?.status !== 'IN_PROGRESS'
                          ? 'Dự án đã đóng, không thể nộp thêm.'
                          : 'Bạn là thành viên: chỉ trưởng nhóm được nộp báo cáo.'}
                    </p>
                  )}
                  {milestone.submissions.map((submission) => (
                    <details key={submission.id} className="student-submission">
                      <summary>
                        Phiên bản {submission.versionNo} · {dateText(submission.submittedAt)} ·{' '}
                        {submission.submittedByName}
                      </summary>
                      {submission.file && (
                        <DownloadButton
                          id={submission.file.id}
                          name={submission.file.originalName}
                        />
                      )}
                      <p className="student-prose">{submission.note}</p>
                      {submission.feedback.length ? (
                        submission.feedback.map((feedback) => (
                          <blockquote key={feedback.id}>
                            <strong>{feedback.lecturerName}</strong> · {dateText(feedback.createdAt)}
                            <p className="student-prose">{feedback.content}</p>
                          </blockquote>
                        ))
                      ) : (
                        <p>Chưa có phản hồi.</p>
                      )}
                    </details>
                  ))}
                </section>
              )
            })}
          </div>
          <aside className="milestone-board__sidebar" aria-label="Lịch báo cáo của sinh viên">
            <MilestoneCalendar milestones={visible} month={month} selectedDay={selectedDay} now={new Date(currentTime)}
              onMonthChange={setMonth} onSelectDay={(day, due) => {
                setSelectedDay(day)
                if (due[0]) document.getElementById(`student-milestone-${due[0].id}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
              }} />
          </aside>
          </div>}
        </>
      )}

      {target && (
        <div className="student-dialog-backdrop">
          <section
            className="student-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="report-title"
          >
            <h2 id="report-title">Nộp: {target.title}</h2>
            <form className="stack-form" onSubmit={submit}>
              <fieldset disabled={uploading} className="student-fieldset">
                <label>
                  Tệp báo cáo (tối đa 20 MB)
                  <input
                    type="file"
                    required
                    onChange={(event) => setFile(event.target.files?.[0])}
                  />
                </label>
                <label>
                  Ghi chú
                  <textarea
                    className="form-input"
                    maxLength={5000}
                    value={note}
                    onChange={(event) => setNote(event.target.value)}
                  />
                </label>
                {new Date(target.dueAt).getTime() < currentTime && (
                  <p className="student-notice">
                    Đã qua hạn. Hệ thống ghi nhận thời gian nộp thực tế để giảng viên xem xét.
                  </p>
                )}
                <div className="inline-form">
                  <button className="btn btn-primary" disabled={!file}>
                    <Upload size={16} aria-hidden="true" />
                    {uploading ? 'Đang tải và lưu...' : 'Xác nhận nộp'}
                  </button>
                  <button
                    className="btn btn-secondary"
                    type="button"
                    onClick={() => setTarget(undefined)}
                  >
                    Hủy
                  </button>
                </div>
              </fieldset>
            </form>
          </section>
        </div>
      )}
    </>
  )
}
