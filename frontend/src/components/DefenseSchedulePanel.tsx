import { useEffect, useState, type FormEvent } from 'react'
import { evaluationApi } from '../api/evaluation'
import { AnimatedSelect } from './AnimatedSelect'
import { DateTimeInput } from './DateTimeInput'
import { ConfirmModal } from './ConfirmModal'
import { formatDate, formatDateInput, formatDateTime } from '../utils/dateTime'
import { defenseDateError, defenseInstant, formatDefenseTime } from '../utils/defenseSchedule'
import { vietnameseErrorMessage } from '../utils/errorMessages'
import type {
  AssessmentComponentResponse,
  DefenseSessionResponse,
  EvaluationCommitteeResponse,
  ProjectAssignmentResponse,
  RegistrationPeriodResponse,
} from '../types/domain'

type Props = {
  committees: EvaluationCommitteeResponse[]
  projects: ProjectAssignmentResponse[]
  defenses: DefenseSessionResponse[]
  periods?: RegistrationPeriodResponse[]
  unlockedReviews?: AssessmentComponentResponse[]
  onAssign?: (defense: DefenseSessionResponse) => void
  onScheduled: (defenseId?: number) => Promise<void>
  initialCommitteeId?: number | null
  onBusyChange?: (busy: boolean) => void
}

export function DefenseSchedulePanel({
  committees,
  projects,
  defenses,
  periods = [],
  unlockedReviews = [],
  onAssign,
  onScheduled,
  onBusyChange,
  initialCommitteeId,
}: Props) {
  const [committeeId, setCommitteeId] = useState(
    initialCommitteeId ? String(initialCommitteeId) : '',
  )
  const [projectId, setProjectId] = useState('')
  const [location, setLocation] = useState('')
  const [times, setTimes] = useState<{ key: string; startsAt: string; endsAt: string } | null>(null)
  const committee = committees.find((item) => String(item.id) === committeeId)
  const period = periods.find((item) => item.id === committee?.periodId)
  const graduation = period?.projectType === 'GRADUATION'
  const fixedDate = graduation ? period.defenseDate : undefined
  const missingDate = graduation && !fixedDate
  const timeKey = JSON.stringify([committeeId, fixedDate])
  const defaultTimes = {
    startsAt: fixedDate ? `${fixedDate}T08:00` : '',
    endsAt: fixedDate ? `${fixedDate}T09:00` : '',
  }
  const { startsAt, endsAt } = times?.key === timeKey ? times : defaultTimes
  const setTime = (field: 'startsAt' | 'endsAt', value: string) =>
    setTimes((previous) => ({
      ...(previous?.key === timeKey ? previous : defaultTimes),
      key: timeKey,
      [field]: value,
    }))
  const setStartsAt = (value: string) => setTime('startsAt', value)
  const setEndsAt = (value: string) => setTime('endsAt', value)
  const dateError = defenseDateError(fixedDate, startsAt, endsAt)
  const [saving, setSaving] = useState(false)
  useEffect(() => {
    onBusyChange?.(saving)
    return () => onBusyChange?.(false)
  }, [saving, onBusyChange])
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [refreshNeeded, setRefreshNeeded] = useState(false)
  const [savedDefenseId, setSavedDefenseId] = useState<number>()
  const [confirming, setConfirming] = useState(false)
  const eligibilityKey = JSON.stringify([
    projectId,
    committeeId,
    location,
    startsAt,
    endsAt,
    graduation,
  ])
  const [eligibilityResult, setEligibility] = useState<{
    key: string
    eligible: boolean
    reason: string | null
  } | null>(null)
  const eligibility = eligibilityResult?.key === eligibilityKey ? eligibilityResult : null
  const checking = !!projectId && !!committeeId && !eligibility
  useEffect(() => {
    let live = true
    if (!projectId || !committeeId) return
    evaluationApi
      .defenseEligibility(
        Number(projectId),
        Number(committeeId),
        location.trim() &&
          startsAt &&
          endsAt &&
          Number.isFinite(defenseInstant(startsAt, graduation).getTime()) &&
          Number.isFinite(defenseInstant(endsAt, graduation).getTime())
          ? {
              location: location.trim(),
              startsAt: defenseInstant(startsAt, graduation).toISOString(),
              endsAt: defenseInstant(endsAt, graduation).toISOString(),
            }
          : undefined,
      )
      .then((result) => {
        if (live) setEligibility({ ...result, key: eligibilityKey })
      })
      .catch(() => {
        if (live)
          setEligibility({
            key: eligibilityKey,
            eligible: false,
            reason: 'Không kiểm tra được điều kiện bảo vệ. Vui lòng chọn lại đề tài để thử lại.',
          })
      })
    return () => {
      live = false
    }
  }, [projectId, committeeId, eligibilityKey, location, startsAt, endsAt, graduation])
  // MySQL currently allows one session per project, including completed/cancelled sessions.
  const available = projects.filter(
    (project) =>
      (!committee || project.periodId === committee.periodId) &&
      project.status !== 'CANCELLED' &&
      project.status !== 'COMPLETED' &&
      !defenses.some((defense) => defense.assignmentId === project.id),
  )
  const savedCommittees = committees.filter((committee) => committee.status !== 'COMPLETED')
  const selectedProject = available.find((project) => String(project.id) === projectId)

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (
      saving ||
      refreshNeeded ||
      checking ||
      !eligibility?.eligible ||
      missingDate ||
      unlockedReviews.length
    )
      return
    setError('')
    setSuccess('')
    if (!location.trim()) {
      setError('Vui lòng nhập địa điểm bảo vệ.')
      return
    }
    if (dateError) {
      setError(dateError)
      return
    }
    const start = defenseInstant(startsAt, graduation)
    const end = defenseInstant(endsAt, graduation)
    if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime()) || start >= end) {
      setError('Thời gian kết thúc phải sau thời gian bắt đầu.')
      return
    }
    setConfirming(true)
  }

  async function save() {
    if (saving || missingDate || unlockedReviews.length) return
    if (dateError) {
      setError(dateError)
      setConfirming(false)
      return
    }
    const start = defenseInstant(startsAt, graduation)
    const end = defenseInstant(endsAt, graduation)
    setSaving(true)
    try {
      const defenseId = await evaluationApi.scheduleDefense({
        assignmentId: Number(projectId),
        committeeId: Number(committeeId),
        location: location.trim(),
        startsAt: start.toISOString(),
        endsAt: end.toISOString(),
      })
      setSavedDefenseId(defenseId)
      setSuccess('Đã xếp lịch bảo vệ.')
      setProjectId('')
      setLocation('')
      setStartsAt(fixedDate ? `${fixedDate}T08:00` : '')
      setEndsAt(fixedDate ? `${fixedDate}T09:00` : '')
      try {
        await onScheduled(defenseId)
      } catch {
        setRefreshNeeded(true)
        setError(
          'Lịch đã lưu nhưng chưa tải lại được danh sách. Bấm tải lại lịch trước khi tiếp tục.',
        )
      }
    } catch (caught) {
      const failure = caught as {
        message?: string
        response?: { status?: number; data?: { message?: string } }
      }
      setError(
        vietnameseErrorMessage(
          failure.response?.data?.message ?? failure.message,
          failure.response?.status,
        ),
      )
    } finally {
      setSaving(false)
      setConfirming(false)
    }
  }

  return (
    <section className="ea-card ea-schedule-card" aria-labelledby="defense-schedule-title">
      <h2 id="defense-schedule-title">Lịch bảo vệ</h2>
      {!savedCommittees.length && (
        <p className="ea-notice">
          Chưa có hội đồng khả dụng. Tạo và lưu hội đồng trong mục Hội đồng bảo vệ trước.
        </p>
      )}
      {unlockedReviews.length > 0 && (
        <div className="ea-notice" role="status">
          <p>
            Chưa thể xếp lịch: phần Phản biện độc lập còn bản nháp:{' '}
            {unlockedReviews.map((item) => `“${item.name}” (${item.code})`).join(', ')}.
          </p>
          <p>
            Bộ tiêu chí hội đồng đã khóa là cấu hình riêng. Vui lòng hoàn tất và khóa phần Phản biện
            độc lập tại Tiêu chuẩn đánh giá trước khi xếp lịch.
          </p>
        </div>
      )}
      {error && (
        <p className="ea-notice ea-notice--error" role="alert">
          {error}
        </p>
      )}
      {dateError && dateError !== error && (
        <p className="ea-notice ea-notice--error" role="alert">
          {dateError}
        </p>
      )}
      {missingDate && (
        <p className="ea-notice ea-notice--error" role="alert">
          Đợt KLTN chưa cấu hình ngày bảo vệ. Vui lòng cập nhật cấu hình đợt trước khi xếp lịch.
        </p>
      )}
      {fixedDate && (
        <p className="ea-notice">
          Ngày bảo vệ KLTN: {formatDate(fixedDate)}. Nhập giờ Việt Nam; thời gian bắt đầu và kết
          thúc phải trong ngày này.
        </p>
      )}
      {success && (
        <p className="ea-notice" role="status">
          {success}
        </p>
      )}
      {checking && <p role="status">Đang kiểm tra điều kiện bảo vệ...</p>}
      {eligibility && !eligibility.eligible && (
        <p className="ea-notice" role="status">
          {vietnameseErrorMessage(eligibility.reason)}
        </p>
      )}
      {refreshNeeded && (
        <button
          type="button"
          className="btn btn-secondary"
          disabled={saving}
          onClick={async () => {
            setSaving(true)
            try {
              await onScheduled(savedDefenseId)
              setRefreshNeeded(false)
              setError('')
            } catch {
              setError('Chưa tải lại được lịch. Vui lòng thử lại.')
            } finally {
              setSaving(false)
            }
          }}
        >
          Tải lại lịch
        </button>
      )}
      <form onSubmit={submit}>
        <fieldset disabled={saving || refreshNeeded || !savedCommittees.length}>
          <div className="ea-grid">
            <div className="ea-field">
              <label htmlFor="schedule-committee">Hội đồng đã lưu *</label>
              <AnimatedSelect
                id="schedule-committee"
                required
                value={committeeId}
                onChange={(event) => {
                  setCommitteeId(event.target.value)
                  setProjectId('')
                  setEligibility(null)
                  setError('')
                }}
              >
                <option value="">Chọn hội đồng</option>
                {savedCommittees.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </AnimatedSelect>
            </div>
            <div className="ea-field">
              <label htmlFor="schedule-project">Đề tài / nhóm *</label>
              <AnimatedSelect
                id="schedule-project"
                required
                value={projectId}
                onChange={(event) => {
                  setProjectId(event.target.value)
                  setEligibility(null)
                  setError('')
                }}
              >
                <option value="">
                  {available.length ? 'Chọn đề tài chưa có lịch' : 'Không còn đề tài chưa xếp lịch'}
                </option>
                {available.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.topicCode} · {item.topicTitle} · {item.groupName}
                  </option>
                ))}
              </AnimatedSelect>
            </div>
            <div className="ea-field">
              <label htmlFor="schedule-location">Địa điểm *</label>
              <input
                id="schedule-location"
                className="form-input"
                required
                maxLength={200}
                value={location}
                onChange={(event) => setLocation(event.target.value)}
              />
            </div>
            <div className="ea-field">
              <label htmlFor="schedule-start">Bắt đầu *</label>
              <DateTimeInput
                id="schedule-start"
                type="datetime-local"
                required
                min={fixedDate ? `${fixedDate}T00:00` : undefined}
                max={fixedDate ? `${fixedDate}T23:59` : undefined}
                aria-invalid={!!dateError}
                value={startsAt}
                onValueChange={setStartsAt}
              />
            </div>
            <div className="ea-field">
              <label htmlFor="schedule-end">Kết thúc *</label>
              <DateTimeInput
                id="schedule-end"
                type="datetime-local"
                required
                min={fixedDate ? `${fixedDate}T00:00` : undefined}
                max={fixedDate ? `${fixedDate}T23:59` : undefined}
                aria-invalid={!!dateError}
                value={endsAt}
                onValueChange={setEndsAt}
              />
            </div>
          </div>
          <div className="ea-defense-schedule__actions">
            <button
              className="btn ea-save"
              disabled={
                !available.length ||
                !committeeId ||
                !projectId ||
                checking ||
                !eligibility?.eligible ||
                missingDate ||
                !!dateError ||
                unlockedReviews.length > 0
              }
            >
              {saving ? 'Đang xếp lịch...' : 'Xếp lịch bảo vệ'}
            </button>
          </div>
        </fieldset>
      </form>
      <h2 className="ea-scheduled-title">Lịch đã xếp</h2>

      {!defenses.length ? (
        <p className="ea-empty">Chưa có lịch bảo vệ trong đợt này.</p>
      ) : (
        <div className="table-container">
          <table className="data-table ea-scheduled-table">
            <thead>
              <tr>
                <th>Đề tài / nhóm</th>
                <th>Hội đồng</th>
                <th>Địa điểm</th>
                <th>Bắt đầu</th>
                <th>Kết thúc</th>
                <th>Trạng thái</th>
                {onAssign && <th>Chấm điểm</th>}
              </tr>
            </thead>
            <tbody>
              {defenses.map((item) => {
                const vietnameseDate = periods.some(
                  (period) => period.id === item.periodId && period.projectType === 'GRADUATION',
                )
                return (
                  <tr key={item.id}>
                    <td>
                      {item.topicCode} · {item.groupName}
                    </td>
                    <td>{item.committeeName}</td>
                    <td>{item.location}</td>
                    <td>{formatDefenseTime(item.startsAt, vietnameseDate)}</td>
                    <td>{formatDefenseTime(item.endsAt, vietnameseDate)}</td>
                    <td>
                      {item.status === 'SCHEDULED'
                        ? 'Đã xếp lịch'
                        : item.status === 'COMPLETED'
                          ? 'Hoàn tất'
                          : 'Đã hủy'}
                    </td>
                    {onAssign && (
                      <td>
                        {item.status === 'SCHEDULED' && (
                          <button
                            type="button"
                            className="btn btn-secondary"
                            disabled={saving}
                            onClick={() => onAssign(item)}
                          >
                            Phân công chấm
                          </button>
                        )}
                      </td>
                    )}
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      <ConfirmModal
        isOpen={confirming}
        title="Xác nhận xếp lịch bảo vệ"
        message={`Xếp lịch cho ${selectedProject?.topicCode ?? ''} · ${selectedProject?.topicTitle ?? ''} · ${selectedProject?.groupName ?? ''}, hội đồng ${committee?.name ?? ''}, tại ${location}, từ ${graduation ? formatDateInput(startsAt, true) : formatDateTime(startsAt)} đến ${graduation ? formatDateInput(endsAt, true) : formatDateTime(endsAt)}${graduation ? ' (giờ Việt Nam)' : ''}?`}
        confirmText="Xác nhận xếp lịch"
        variant="primary"
        loading={saving}
        onConfirm={() => void save()}
        onCancel={() => {
          if (!saving) setConfirming(false)
        }}
      />
    </section>
  )
}
