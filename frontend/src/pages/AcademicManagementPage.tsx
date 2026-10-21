import { formatDateInput, formatDateTime } from '../utils/dateTime'
import { DateTimeInput } from '../components/DateTimeInput'
import { AnimatedSelect } from '../components/AnimatedSelect'
import React, { useEffect, useState } from 'react'
import { academicApi } from '../api/academic'
import { allPages } from '../api/student'
import { StatusBadge } from '../components/StatusBadge'
import { ConfirmModal } from '../components/ConfirmModal'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { statusLabel, projectTypeLabel } from '../utils/displayLabels'
import './AcademicManagementPage.css'
import type {
  CreateRegistrationPeriodRequest,
  FacultyResponse,
  RegistrationPeriodResponse,
  SemesterResponse,
} from '../types/domain'

type NextPeriodStatus = 'ACTIVE' | 'CLOSED'

const nextPeriodStatus = (status: RegistrationPeriodResponse['status']): NextPeriodStatus | null => {
  if (status === 'DRAFT') return 'ACTIVE'
  if (status === 'ACTIVE') return 'CLOSED'
  if (status === 'CLOSED') return 'ACTIVE'
  return null
}

const periodStatusActionLabel = (status: NextPeriodStatus) => (status === 'ACTIVE' ? 'Bật' : 'Đóng')

export const AcademicManagementPage: React.FC = () => {
  const { user, hasRole, hasAppointment } = useAuth()
  const { showSuccess, showError } = useToast()
  const isDean = hasAppointment('DEAN')
  const isAdmin = hasRole('ADMIN')

  // Registration Periods state
  const [periods, setPeriods] = useState<RegistrationPeriodResponse[]>([])
  const [loadingPeriods, setLoadingPeriods] = useState(false)
  const [isPeriodModalOpen, setIsPeriodModalOpen] = useState(false)
  const [submittingPeriod, setSubmittingPeriod] = useState(false)
  const [pendingStatusChange, setPendingStatusChange] = useState<{
    period: RegistrationPeriodResponse
    nextStatus: NextPeriodStatus
  } | null>(null)
  const [changingPeriodStatus, setChangingPeriodStatus] = useState(false)

  // Catalog state
  const [faculties, setFaculties] = useState<FacultyResponse[]>([])
  const [semesters, setSemesters] = useState<SemesterResponse[]>([])
  const managedFaculties = faculties.filter(
    (faculty) =>
      isAdmin ||
      user?.activeAppointments.some(
        (appointment) =>
          appointment.positionCode === 'DEAN' && appointment.facultyId === faculty.id,
      ),
  )

  // Form State for Registration Period creation
  const [newPeriod, setNewPeriod] = useState<CreateRegistrationPeriodRequest>({
    facultyId: 0,
    semesterId: 0,
    name: '',
    projectType: 'COURSE',
    proposalStartAt: '',
    proposalEndAt: '',
    registrationStartAt: '',
    registrationEndAt: '',
    reviewDeadlineAt: null,
    defenseDate: null,
  })
  const requiresReview = newPeriod.projectType === 'SPECIALIZED' || newPeriod.projectType === 'GRADUATION'
  const requiresDefense = newPeriod.projectType === 'GRADUATION'

  const scheduleErrors: Partial<Record<keyof CreateRegistrationPeriodRequest, string>> = {}
  const {
    proposalStartAt,
    proposalEndAt,
    registrationStartAt,
    registrationEndAt,
    reviewDeadlineAt,
  } = newPeriod
  if (proposalStartAt && proposalEndAt && proposalStartAt >= proposalEndAt) {
    scheduleErrors.proposalEndAt = 'Kết thúc GV đăng ký phải sau thời điểm bắt đầu GV đăng ký.'
  }
  if (proposalEndAt && registrationStartAt && proposalEndAt > registrationStartAt) {
    scheduleErrors.registrationStartAt =
      'Bắt đầu SV đăng ký phải từ thời điểm kết thúc GV đăng ký trở đi.'
  }
  if (registrationStartAt && registrationEndAt && registrationStartAt >= registrationEndAt) {
    scheduleErrors.registrationEndAt = 'Đóng đăng ký phải sau thời điểm mở đăng ký của sinh viên.'
  }
  if (
    requiresReview &&
    registrationEndAt &&
    reviewDeadlineAt &&
    reviewDeadlineAt <= registrationEndAt
  ) {
    scheduleErrors.reviewDeadlineAt =
      'Hạn GVPB nộp điểm phải sau thời điểm đóng đăng ký của sinh viên.'
  }

  // Load catalogs
  useEffect(() => {
    academicApi
      .getFaculties()
      .then(setFaculties)
      .catch((error) => showError(error.message))
    academicApi.getSemesters().then(setSemesters)
  }, [])

  // Load registration periods
  const loadRegistrationPeriods = () => {
    setLoadingPeriods(true)
    allPages(page => academicApi.getRegistrationPeriods(undefined, page, 100))
      .then(setPeriods)
      .catch((err) =>
        showError('Lỗi tải đợt đăng ký: ' + (err.response?.data?.message || err.message)),
      )
      .finally(() => setLoadingPeriods(false))
  }

  useEffect(() => {
    loadRegistrationPeriods()
  }, [])

  const handleCreatePeriod = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (submittingPeriod) return
    if (!newPeriod.facultyId) {
      showError('Vui lòng chọn khoa quản lý đợt đăng ký.')
      return
    }
    if (requiresReview && !newPeriod.reviewDeadlineAt) {
      showError('Hãy nhập hạn chót GVPB nộp điểm về Khoa.')
      return
    }
    if (requiresDefense && !newPeriod.defenseDate) {
      showError('Hãy nhập ngày báo cáo hội đồng cho đợt KLTN.')
      return
    }
    if (
      !newPeriod.name.trim() ||
      !newPeriod.proposalStartAt ||
      !newPeriod.proposalEndAt ||
      !newPeriod.registrationStartAt ||
      !newPeriod.registrationEndAt
    ) {
      showError('Hãy nhập tên đợt và đủ thời gian đăng ký của GV, SV.')
      return
    }
    const firstScheduleError = Object.entries(scheduleErrors)[0]
    if (firstScheduleError) {
      showError(firstScheduleError[1])
      const field = e.currentTarget.elements.namedItem(firstScheduleError[0])
      if (field instanceof HTMLInputElement) field.focus()
      return
    }
    const hasCustomGroupSize =
      newPeriod.minGroupMembers !== undefined || newPeriod.maxGroupMembers !== undefined
    if (
      hasCustomGroupSize &&
      (newPeriod.minGroupMembers === undefined || newPeriod.maxGroupMembers === undefined)
    ) {
      showError(
        'Hãy nhập đủ số thành viên tối thiểu và tối đa của nhóm, hoặc để trống để dùng cấu hình hệ thống.',
      )
      return
    }
    if (
      newPeriod.minGroupMembers !== undefined &&
      newPeriod.maxGroupMembers !== undefined &&
      newPeriod.minGroupMembers > newPeriod.maxGroupMembers
    ) {
      showError('Số thành viên tối thiểu của nhóm không được lớn hơn số tối đa.')
      return
    }
    const hasCustomCommitteeSize =
      newPeriod.minCommitteeMembers !== undefined || newPeriod.maxCommitteeMembers !== undefined
    if (
      hasCustomCommitteeSize &&
      (newPeriod.minCommitteeMembers === undefined || newPeriod.maxCommitteeMembers === undefined)
    ) {
      showError(
        'Hãy nhập đủ số thành viên tối thiểu và tối đa của hội đồng, hoặc để trống để dùng cấu hình hệ thống.',
      )
      return
    }
    if (
      newPeriod.minCommitteeMembers !== undefined &&
      newPeriod.maxCommitteeMembers !== undefined &&
      newPeriod.minCommitteeMembers > newPeriod.maxCommitteeMembers
    ) {
      showError('Số thành viên tối thiểu của hội đồng không được lớn hơn số tối đa.')
      return
    }

    try {
      setSubmittingPeriod(true)
      const created = await academicApi.createRegistrationPeriod({
        ...newPeriod,
        name: newPeriod.name.trim(),
        proposalStartAt: new Date(newPeriod.proposalStartAt).toISOString(),
        proposalEndAt: new Date(newPeriod.proposalEndAt).toISOString(),
        registrationStartAt: new Date(newPeriod.registrationStartAt).toISOString(),
        registrationEndAt: new Date(newPeriod.registrationEndAt).toISOString(),
        reviewDeadlineAt: requiresReview && newPeriod.reviewDeadlineAt
          ? new Date(newPeriod.reviewDeadlineAt).toISOString()
          : null,
        defenseDate: requiresDefense ? newPeriod.defenseDate : null,
      })
      showSuccess('Tạo đợt đăng ký thành công ở trạng thái nháp!')
      setPeriods((prev) => [created, ...prev])
      setIsPeriodModalOpen(false)
      setNewPeriod({
        facultyId: 0,
        semesterId: 0,
        name: '',
        projectType: 'COURSE',
        proposalStartAt: '',
        proposalEndAt: '',
        registrationStartAt: '',
        registrationEndAt: '',
        reviewDeadlineAt: null,
        defenseDate: null,
      })
    } catch (err: any) {
      showError('Lỗi tạo đợt đăng ký: ' + (err.response?.data?.message || err.message))
    } finally {
      setSubmittingPeriod(false)
    }
  }

  const handleChangePeriodStatus = async () => {
    if (!pendingStatusChange || changingPeriodStatus) return
    const { period, nextStatus } = pendingStatusChange
    setChangingPeriodStatus(true)
    try {
      const updated = await academicApi.changeRegistrationPeriodStatus(period.id, {
        status: nextStatus,
        version: period.version,
      })
      showSuccess(`Đã cập nhật trạng thái đợt đăng ký sang “${statusLabel(nextStatus)}”!`)
      setPeriods((prev) => prev.map((p) => (p.id === period.id ? updated : p)))
      setPendingStatusChange(null)
    } catch (err: any) {
      showError('Lỗi đổi trạng thái: ' + (err.response?.data?.message || err.message))
    } finally {
      setChangingPeriodStatus(false)
    }
  }

  return (
    <div className="academic-management-page">
      <div className="page-header">
        <div>
          <h1>Quản lý Học thuật</h1>
        </div>
        <div className="flex gap-2">
          {user?.activeAppointments?.map((apt) => (
            <span key={apt.id} className="badge badge-success text-sm py-2 px-3">
              Vị trí: {apt.positionCode === 'DEAN' ? 'Trưởng Khoa' : 'Trưởng Bộ Môn'}
            </span>
          ))}
        </div>
      </div>

      <div className="page-card fade-in">
        <div className="registration-periods-header">
          <h2 className="section-title">Danh sách Đợt Đăng Ký Đề Tài</h2>
          {(isAdmin || isDean) && (
            <button
              type="button"
              className="btn btn-primary"
              onClick={() => setIsPeriodModalOpen(true)}
            >
              + Tạo Đợt Đăng Ký Mới
            </button>
          )}
        </div>

        {loadingPeriods ? (
          <div className="text-center py-8">
            <div className="spinner" />
            <p className="text-secondary mt-2">Đang tải danh sách đợt đăng ký...</p>
          </div>
        ) : periods.length === 0 ? (
          <p className="text-secondary text-center py-6">Chưa có đợt đăng ký nào.</p>
        ) : (
          <div className="table-container">
            <table className="data-table registration-periods-table">
              <colgroup>
                <col className="registration-periods-table__number-column" />
                <col className="registration-periods-table__period-column" />
                <col className="registration-periods-table__schedule-column" />
                <col className="registration-periods-table__milestone-column" />
                <col className="registration-periods-table__rules-column" />
                <col className="registration-periods-table__status-column" />
                <col className="registration-periods-table__action-column" />
              </colgroup>
              <thead>
                <tr>
                  <th scope="col">#</th>
                  <th scope="col">Đợt đăng ký</th>
                  <th scope="col">Thời gian đăng ký</th>
                  <th scope="col">Mốc đánh giá</th>
                  <th scope="col">Quy tắc áp dụng</th>
                  <th scope="col">Trạng thái</th>
                  <th scope="col">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {periods.map((p, idx) => {
                  const nextStatus = nextPeriodStatus(p.status)
                  return <tr key={p.id}>
                    <td>{idx + 1}</td>
                    <td className="registration-periods-table__period">
                      <strong>{p.name}</strong>
                      <span>
                        {projectTypeLabel(p.projectType)} · HK {p.semesterNo} · {p.academicYear}
                      </span>
                    </td>
                    <td className="registration-periods-table__schedule">
                      <span>
                        <strong>GV:</strong> {formatDateTime(p.proposalStartAt)} – {formatDateTime(p.proposalEndAt)}
                      </span>
                      <span>
                        <strong>SV:</strong> {formatDateTime(p.registrationStartAt)} – {formatDateTime(p.registrationEndAt)}
                      </span>
                    </td>
                    <td className="registration-periods-table__milestones">
                      <span>
                        <strong>GVPB:</strong>{' '}
                        {p.projectType === 'SPECIALIZED' || p.projectType === 'GRADUATION'
                          ? (p.reviewDeadlineAt ? formatDateTime(p.reviewDeadlineAt) : 'Chưa thiết lập')
                          : '—'}
                      </span>
                      <span>
                        <strong>Hội đồng:</strong>{' '}
                        {p.projectType === 'GRADUATION'
                          ? (p.defenseDate ? formatDateInput(p.defenseDate, false) : 'Chưa thiết lập')
                          : '—'}
                      </span>
                    </td>
                    <td className="registration-periods-table__rules">
                      <span>Nhóm: {p.minGroupMembers}–{p.maxGroupMembers} SV</span>
                      <span>Lời mời: {p.invitationExpiryHours} giờ</span>
                      <span>Hội đồng: {p.minCommitteeMembers}–{p.maxCommitteeMembers} GV</span>
                    </td>
                    <td className="registration-period-status">
                      <StatusBadge status={p.status} />
                    </td>
                    <td className="registration-periods-table__action">
                      {(isAdmin || isDean) && nextStatus && (
                        <button
                          type="button"
                          className="btn btn-outline btn-sm"
                          disabled={changingPeriodStatus}
                          onClick={() =>
                            setPendingStatusChange({
                              period: p,
                              nextStatus,
                            })
                          }
                        >
                          {periodStatusActionLabel(nextStatus)}
                        </button>
                      )}
                    </td>
                  </tr>
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <ConfirmModal
        isOpen={pendingStatusChange !== null}
        title="Xác nhận chuyển trạng thái"
        message={
          pendingStatusChange
            ? `Bạn có chắc muốn chuyển đợt đăng ký “${pendingStatusChange.period.name}” từ “${statusLabel(pendingStatusChange.period.status)}” sang “${statusLabel(pendingStatusChange.nextStatus)}” không?`
            : ''
        }
        loading={changingPeriodStatus}
        onConfirm={handleChangePeriodStatus}
        onCancel={() => {
          if (!changingPeriodStatus) setPendingStatusChange(null)
        }}
      />

      {/* Modal Create Registration Period */}
      {isPeriodModalOpen && (
        <div className="modal-backdrop fade-in" onClick={() => setIsPeriodModalOpen(false)}>
          <div className="modal-content registration-period-modal scale-in" role="dialog" aria-modal="true" aria-labelledby="period-dialog-title" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 id="period-dialog-title" className="font-semibold text-lg">Tạo Đợt Đăng Ký Đề Tài Mới</h3>
              <button
                type="button"
                className="btn-close"
                onClick={() => setIsPeriodModalOpen(false)}
              >
                &times;
              </button>
            </div>
            <form onSubmit={handleCreatePeriod}>
              <div className="modal-body py-4 space-y-4">
                <div className="form-group">
                  <label className="form-label" htmlFor="period-faculty">
                    Khoa quản lý <span className="text-red">*</span>
                  </label>
                  <AnimatedSelect
                    id="period-faculty"
                    className="form-select"
                    value={newPeriod.facultyId || ''}
                    onChange={(event) =>
                      setNewPeriod({
                        ...newPeriod,
                        facultyId: Number(event.target.value) || 0,
                      })
                    }
                    required
                  >
                    <option value="">-- Chọn khoa --</option>
                    {managedFaculties.map((faculty) => (
                      <option key={faculty.id} value={faculty.id}>
                        {faculty.name} ({faculty.code})
                      </option>
                    ))}
                  </AnimatedSelect>
                </div>

                <div className="form-group">
                  <label className="form-label" htmlFor="period-name">
                    Tên Đợt Đăng Ký <span className="text-red">*</span>
                  </label>
                  <input
                    type="text"
                    id="period-name"
                    maxLength={200}
                    className="form-input"
                    placeholder="Ví dụ: Đợt đăng ký Đồ án tốt nghiệp HK1 2026..."
                    value={newPeriod.name}
                    onChange={(e) => setNewPeriod({ ...newPeriod, name: e.target.value })}
                    required
                  />
                </div>

                <div className="form-group">
                  <label className="form-label" htmlFor="period-semester">
                    Học kỳ áp dụng <span className="text-red">*</span>
                  </label>
                  <AnimatedSelect
                    id="period-semester"
                    className="form-select"
                    value={newPeriod.semesterId}
                    onChange={(e) =>
                      setNewPeriod({
                        ...newPeriod,
                        semesterId: Number(e.target.value),
                      })
                    }
                    required
                  >
                    <option value="">-- Chọn học kỳ --</option>
                    {semesters.map((s) => (
                      <option key={s.id} value={s.id}>
                        Học kỳ {s.semesterNo} - {s.academicYear}
                      </option>
                    ))}
                  </AnimatedSelect>
                </div>

                <div className="form-group">
                  <label className="form-label" htmlFor="period-type">
                    Loại đợt <span className="text-red">*</span>
                  </label>
                  <AnimatedSelect
                    id="period-type"
                    className="form-select"
                    value={newPeriod.projectType}
                    onChange={(e) => {
                      const projectType = e.target.value as CreateRegistrationPeriodRequest['projectType']
                      setNewPeriod(previous => ({
                        ...previous,
                        projectType,
                        reviewDeadlineAt: projectType === 'SPECIALIZED' || projectType === 'GRADUATION' ? previous.reviewDeadlineAt : null,
                        defenseDate: projectType === 'GRADUATION' ? previous.defenseDate : null,
                      }))
                    }}
                  >
                    <option value="COURSE">Môn học</option>
                    <option value="RESEARCH">Nghiên cứu khoa học</option>
                    <option value="SPECIALIZED">Tiểu luận chuyên ngành</option>
                    <option value="GRADUATION">Khóa luận tốt nghiệp</option>
                  </AnimatedSelect>
                </div>
                <div className="grid-cols-2">
                  <div className="form-group">
                    <label className="form-label" htmlFor="period-proposal-start">
                      Bắt đầu GV đăng ký <span className="text-red">*</span>
                    </label>
                    <DateTimeInput
                      type="datetime-local"
                      className="form-input"
                      id="period-proposal-start"
                      name="proposalStartAt"
                      value={newPeriod.proposalStartAt}
                      onValueChange={(value) =>
                        setNewPeriod({
                          ...newPeriod,
                          proposalStartAt: value,
                        })
                      }
                      required
                    />
                  </div>

                  <div className="form-group">
                    <label className="form-label" htmlFor="period-proposal-end">
                      Kết thúc GV đăng ký <span className="text-red">*</span>
                    </label>
                    <DateTimeInput
                      type="datetime-local"
                      className="form-input"
                      id="period-proposal-end"
                      name="proposalEndAt"
                      aria-invalid={Boolean(scheduleErrors.proposalEndAt)}
                      aria-describedby={
                        scheduleErrors.proposalEndAt ? 'period-proposal-end-error' : undefined
                      }
                      value={newPeriod.proposalEndAt}
                      onValueChange={(value) =>
                        setNewPeriod({
                          ...newPeriod,
                          proposalEndAt: value,
                        })
                      }
                      required
                    />
                    {scheduleErrors.proposalEndAt && (
                      <p id="period-proposal-end-error" className="form-error">
                        {scheduleErrors.proposalEndAt}
                      </p>
                    )}
                  </div>
                </div>
                <div className="grid-cols-2">
                  <div className="form-group">
                    <label className="form-label" htmlFor="period-registration-start">
                      Bắt đầu SV đăng ký <span className="text-red">*</span>
                    </label>
                    <DateTimeInput
                      type="datetime-local"
                      className="form-input"
                      id="period-registration-start"
                      name="registrationStartAt"
                      aria-invalid={Boolean(scheduleErrors.registrationStartAt)}
                      aria-describedby={
                        scheduleErrors.registrationStartAt
                          ? 'period-registration-start-error'
                          : undefined
                      }
                      value={newPeriod.registrationStartAt}
                      onValueChange={(value) =>
                        setNewPeriod({
                          ...newPeriod,
                          registrationStartAt: value,
                        })
                      }
                      required
                    />
                    {scheduleErrors.registrationStartAt && (
                      <p id="period-registration-start-error" className="form-error">
                        {scheduleErrors.registrationStartAt}
                      </p>
                    )}
                  </div>
                  <div className="form-group">
                    <label className="form-label" htmlFor="period-registration-end">
                      Kết thúc SV đăng ký <span className="text-red">*</span>
                    </label>
                    <DateTimeInput
                      type="datetime-local"
                      className="form-input"
                      id="period-registration-end"
                      name="registrationEndAt"
                      aria-invalid={Boolean(scheduleErrors.registrationEndAt)}
                      aria-describedby={
                        scheduleErrors.registrationEndAt
                          ? 'period-registration-end-error'
                          : undefined
                      }
                      value={newPeriod.registrationEndAt}
                      onValueChange={(value) =>
                        setNewPeriod({
                          ...newPeriod,
                          registrationEndAt: value,
                        })
                      }
                      required
                    />
                    {scheduleErrors.registrationEndAt && (
                      <p id="period-registration-end-error" className="form-error">
                        {scheduleErrors.registrationEndAt}
                      </p>
                    )}
                  </div>
                </div>
                <div className={requiresReview ? 'grid-cols-2' : undefined}>
                  {requiresReview && (
                    <div className="form-group">
                      <label className="form-label" htmlFor="period-review-deadline">
                        Hạn chót GVPB nộp điểm về Khoa <span className="text-red">*</span>
                      </label>
                      <DateTimeInput
                        type="datetime-local"
                        className="form-input"
                        id="period-review-deadline"
                        name="reviewDeadlineAt"
                        aria-invalid={Boolean(scheduleErrors.reviewDeadlineAt)}
                        aria-describedby={
                          scheduleErrors.reviewDeadlineAt ? 'period-review-deadline-error' : undefined
                        }
                        value={newPeriod.reviewDeadlineAt || ''}
                        onValueChange={(value) =>
                          setNewPeriod({
                            ...newPeriod,
                            reviewDeadlineAt: value || null,
                          })
                        }
                        required
                      />
                      {scheduleErrors.reviewDeadlineAt && (
                        <p id="period-review-deadline-error" className="form-error">
                          {scheduleErrors.reviewDeadlineAt}
                        </p>
                      )}
                    </div>
                  )}
                  <div className="form-group">
                    <label className="form-label" htmlFor="period-defense-date">
                      Ngày báo cáo hội đồng
                      {requiresDefense && (
                        <>
                          {' '}
                          <span className="text-red">*</span>
                        </>
                      )}
                    </label>
                    <DateTimeInput
                      type="date"
                      id="period-defense-date"
                      name="defenseDate"
                      className="form-input"
                      value={newPeriod.defenseDate || ''}
                      disabled={!requiresDefense}
                      onValueChange={(value) =>
                        setNewPeriod({
                          ...newPeriod,
                          defenseDate: value || null,
                        })
                      }
                      required={requiresDefense}
                    />
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-label">
                    Quy tắc nhóm thực hiện (để trống để dùng cấu hình hệ thống)
                  </label>
                  <div className="grid-cols-2">
                    <div className="form-group">
                      <label className="form-label">
                        Số thành viên tối thiểu <span className="text-red">*</span>
                      </label>
                      <input
                        type="number"
                        min="1"
                        max="10"
                        step="1"
                        className="form-input"
                        value={newPeriod.minGroupMembers ?? ''}
                        onChange={(e) =>
                          setNewPeriod({
                            ...newPeriod,
                            minGroupMembers: e.target.value ? Number(e.target.value) : undefined,
                          })
                        }
                      />
                    </div>
                    <div className="form-group">
                      <label className="form-label">
                        Số thành viên tối đa <span className="text-red">*</span>
                      </label>
                      <input
                        type="number"
                        min="1"
                        max="10"
                        step="1"
                        className="form-input"
                        value={newPeriod.maxGroupMembers ?? ''}
                        onChange={(e) =>
                          setNewPeriod({
                            ...newPeriod,
                            maxGroupMembers: e.target.value ? Number(e.target.value) : undefined,
                          })
                        }
                      />
                    </div>
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-label">
                    Thời hạn lời mời tham gia nhóm (giờ)
                  </label>
                  <input
                    type="number"
                    min="1"
                    max="720"
                    step="1"
                    className="form-input"
                    value={newPeriod.invitationExpiryHours ?? ''}
                    onChange={(e) =>
                      setNewPeriod({
                        ...newPeriod,
                        invitationExpiryHours: e.target.value ? Number(e.target.value) : undefined,
                      })
                    }
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">
                    Quy tắc hội đồng (để trống để dùng cấu hình hệ thống)
                  </label>
                  <div className="grid-cols-2">
                    <div className="form-group">
                      <label className="form-label">
                        Số thành viên tối thiểu <span className="text-red">*</span>
                      </label>
                      <input
                        type="number"
                        min="3"
                        max="5"
                        step="1"
                        className="form-input"
                        value={newPeriod.minCommitteeMembers ?? ''}
                        onChange={(e) =>
                          setNewPeriod({
                            ...newPeriod,
                            minCommitteeMembers: e.target.value ? Number(e.target.value) : undefined,
                          })
                        }
                      />
                    </div>
                    <div className="form-group">
                      <label className="form-label">
                        Số thành viên tối đa <span className="text-red">*</span>
                      </label>
                      <input
                        type="number"
                        min="3"
                        max="5"
                        step="1"
                        className="form-input"
                        value={newPeriod.maxCommitteeMembers ?? ''}
                        onChange={(e) =>
                          setNewPeriod({
                            ...newPeriod,
                            maxCommitteeMembers: e.target.value ? Number(e.target.value) : undefined,
                          })
                        }
                      />
                    </div>
                  </div>
                </div>

              </div>

              <div
                className="modal-footer"
                style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}
              >
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setIsPeriodModalOpen(false)}
                  disabled={submittingPeriod}
                >
                  Hủy
                </button>
                <button type="submit" className="btn btn-primary" disabled={submittingPeriod}>
                  {submittingPeriod ? 'Đang khởi tạo...' : 'Tạo Đợt Đăng Ký'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
