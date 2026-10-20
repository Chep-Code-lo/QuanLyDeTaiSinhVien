import { formatDateTime } from '../utils/dateTime'
import { DateTimeInput } from '../components/DateTimeInput'
import { AnimatedSelect } from '../components/AnimatedSelect'
import React, { useEffect, useState } from 'react'
import { academicApi } from '../api/academic'
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

const DEFAULT_PERIOD_RULES = {
  minGroupMembers: 1,
  maxGroupMembers: 3,
  invitationExpiryHours: 72,
  minCommitteeMembers: 3,
  maxCommitteeMembers: 5,
} as const

export const AcademicManagementPage: React.FC = () => {
  const { user, hasRole, hasAppointment } = useAuth()
  const { showSuccess, showError } = useToast()

  // Registration Periods state
  const [periods, setPeriods] = useState<RegistrationPeriodResponse[]>([])
  const [loadingPeriods, setLoadingPeriods] = useState(false)
  const [isPeriodModalOpen, setIsPeriodModalOpen] = useState(false)
  const [submittingPeriod, setSubmittingPeriod] = useState(false)
  const [pendingStatusChange, setPendingStatusChange] = useState<{
    period: RegistrationPeriodResponse
    nextStatus: 'DRAFT' | 'ACTIVE' | 'CLOSED'
  } | null>(null)
  const [changingPeriodStatus, setChangingPeriodStatus] = useState(false)

  // Catalog state
  const [faculties, setFaculties] = useState<FacultyResponse[]>([])
  const [semesters, setSemesters] = useState<SemesterResponse[]>([])

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
    ...DEFAULT_PERIOD_RULES,
  })

  const scheduleErrors: Partial<Record<keyof CreateRegistrationPeriodRequest, string>> = {}
  const {
    proposalStartAt,
    proposalEndAt,
    registrationStartAt,
    registrationEndAt,
    reviewDeadlineAt,
  } = newPeriod
  if (proposalStartAt && proposalEndAt && proposalStartAt >= proposalEndAt) {
    scheduleErrors.proposalEndAt = 'Đóng đề xuất phải sau thời điểm mở đề xuất đề tài.'
  }
  if (proposalEndAt && registrationStartAt && proposalEndAt > registrationStartAt) {
    scheduleErrors.registrationStartAt =
      'Mở đăng ký của sinh viên phải từ thời điểm đóng đề xuất đề tài trở đi.'
  }
  if (registrationStartAt && registrationEndAt && registrationStartAt >= registrationEndAt) {
    scheduleErrors.registrationEndAt = 'Đóng đăng ký phải sau thời điểm mở đăng ký của sinh viên.'
  }
  if (
    (newPeriod.projectType === 'SPECIALIZED' || newPeriod.projectType === 'GRADUATION') &&
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
    academicApi.getSemesters().then((res) => {
      setSemesters(res)
      if (res.length > 0) {
        setNewPeriod((prev) => ({ ...prev, semesterId: res[0].id }))
      }
    })
  }, [])

  // This portal always creates periods for the configured IT faculty.
  useEffect(() => {
    if (faculties.length > 0 && newPeriod.facultyId === 0) {
      setNewPeriod((prev) => ({
        ...prev,
        facultyId: faculties[0].id,
      }))
    }
  }, [faculties, user])

  // Load registration periods
  const loadRegistrationPeriods = () => {
    setLoadingPeriods(true)
    academicApi
      .getRegistrationPeriods()
      .then((res) => setPeriods(res.content))
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
    if (!newPeriod.facultyId) {
      showError('Chưa tải được cấu hình Khoa CNTT. Vui lòng tải lại trang.')
      return
    }
    if (
      !newPeriod.name.trim() ||
      !newPeriod.proposalStartAt ||
      !newPeriod.proposalEndAt ||
      !newPeriod.registrationStartAt ||
      !newPeriod.registrationEndAt
    ) {
      showError('Hãy nhập đủ hai giai đoạn: đề xuất đề tài và đăng ký đề tài.')
      return
    }
    const firstScheduleError = Object.entries(scheduleErrors)[0]
    if (firstScheduleError) {
      showError(firstScheduleError[1])
      const field = e.currentTarget.elements.namedItem(firstScheduleError[0])
      if (field instanceof HTMLInputElement) field.focus()
      return
    }
    if (newPeriod.minGroupMembers > newPeriod.maxGroupMembers) {
      showError('Số thành viên tối thiểu của nhóm không được lớn hơn số tối đa.')
      return
    }
    if (newPeriod.minCommitteeMembers > newPeriod.maxCommitteeMembers) {
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
        reviewDeadlineAt: newPeriod.reviewDeadlineAt
          ? new Date(newPeriod.reviewDeadlineAt).toISOString()
          : null,
      })
      showSuccess('Tạo đợt đăng ký thành công ở trạng thái nháp!')
      setPeriods((prev) => [created, ...prev])
      setIsPeriodModalOpen(false)
      setNewPeriod({
        facultyId: faculties[0]?.id || 0,
        semesterId: semesters[0]?.id || 0,
        name: '',
        projectType: 'COURSE',
        proposalStartAt: '',
        proposalEndAt: '',
        registrationStartAt: '',
        registrationEndAt: '',
        reviewDeadlineAt: null,
        defenseDate: null,
        ...DEFAULT_PERIOD_RULES,
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

  const isDean = hasAppointment('DEAN')
  const isAdmin = hasRole('ADMIN')

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
            <table className="data-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Tên đợt đăng ký</th>
                  <th>Loại đợt</th>
                  <th>Học kỳ</th>
                  <th>Thời gian bắt đầu</th>
                  <th>Thời gian kết thúc</th>
                  <th>Quy tắc áp dụng</th>
                  <th>Trạng thái</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {periods.map((p, idx) => (
                  <tr key={p.id}>
                    <td>{idx + 1}</td>
                    <td className="font-semibold text-primary">{p.name}</td>
                    <td>{projectTypeLabel(p.projectType)}</td>
                    <td>
                      HK {p.semesterNo} · {p.academicYear}
                    </td>
                    <td className="text-xs">
                      Đề xuất: {formatDateTime(p.proposalStartAt)}
                      <br />
                      Đăng ký: {formatDateTime(p.registrationStartAt)}
                    </td>
                    <td className="text-xs">
                      Đề xuất: {formatDateTime(p.proposalEndAt)}
                      <br />
                      Đăng ký: {formatDateTime(p.registrationEndAt)}
                    </td>
                    <td className="text-xs">
                      Nhóm: {p.minGroupMembers}–{p.maxGroupMembers} SV
                      <br />
                      Lời mời: {p.invitationExpiryHours} giờ
                      <br />
                      Hội đồng: {p.minCommitteeMembers}–{p.maxCommitteeMembers} GV
                    </td>
                    <td className="registration-period-status">
                      <StatusBadge status={p.status} />
                    </td>
                    <td>
                      {(isAdmin || isDean) && (
                        <button
                          type="button"
                          className="btn btn-outline btn-sm"
                          disabled={changingPeriodStatus}
                          onClick={() =>
                            setPendingStatusChange({
                              period: p,
                              nextStatus:
                                p.status === 'DRAFT'
                                  ? 'ACTIVE'
                                  : p.status === 'ACTIVE'
                                    ? 'CLOSED'
                                    : 'DRAFT',
                            })
                          }
                        >
                          Chuyển trạng thái
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
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
          <div className="modal-content scale-in" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="font-semibold text-lg">Tạo Đợt Đăng Ký Đề Tài Mới</h3>
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
                  <label className="form-label">
                    Tên Đợt Đăng Ký <span className="text-red">*</span>
                  </label>
                  <input
                    type="text"
                    className="form-input"
                    placeholder="Ví dụ: Đợt đăng ký Đồ án tốt nghiệp HK1 2026..."
                    value={newPeriod.name}
                    onChange={(e) => setNewPeriod({ ...newPeriod, name: e.target.value })}
                    required
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">
                    Học kỳ áp dụng <span className="text-red">*</span>
                  </label>
                  <AnimatedSelect
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
                    {semesters.map((s) => (
                      <option key={s.id} value={s.id}>
                        Học kỳ {s.semesterNo} - {s.academicYear}
                      </option>
                    ))}
                  </AnimatedSelect>
                </div>

                <div className="form-group">
                  <label className="form-label">
                    Loại đợt <span className="text-red">*</span>
                  </label>
                  <AnimatedSelect
                    className="form-select"
                    value={newPeriod.projectType}
                    onChange={(e) =>
                      setNewPeriod({
                        ...newPeriod,
                        projectType: e.target
                          .value as CreateRegistrationPeriodRequest['projectType'],
                      })
                    }
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
                      Mở đề xuất đề tài <span className="text-red">*</span>
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
                      Đóng đề xuất đề tài <span className="text-red">*</span>
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
                      Mở đăng ký của SV <span className="text-red">*</span>
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
                          : 'period-schedule-hint'
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
                      Đóng đăng ký của SV <span className="text-red">*</span>
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
                <p id="period-schedule-hint" className="text-sm text-secondary">
                  Đóng đề xuất đề tài trước hoặc cùng lúc mở đăng ký cho sinh viên.
                </p>
                <div className="form-group">
                  <label className="form-label">Quy tắc nhóm thực hiện</label>
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
                        value={newPeriod.minGroupMembers}
                        onChange={(e) =>
                          setNewPeriod({
                            ...newPeriod,
                            minGroupMembers: Number(e.target.value),
                          })
                        }
                        required
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
                        value={newPeriod.maxGroupMembers}
                        onChange={(e) =>
                          setNewPeriod({
                            ...newPeriod,
                            maxGroupMembers: Number(e.target.value),
                          })
                        }
                        required
                      />
                    </div>
                  </div>
                </div>
                <div className="form-group">
                  <label className="form-label">
                    Thời hạn lời mời tham gia nhóm (giờ) <span className="text-red">*</span>
                  </label>
                  <input
                    type="number"
                    min="1"
                    max="720"
                    step="1"
                    className="form-input"
                    value={newPeriod.invitationExpiryHours}
                    onChange={(e) =>
                      setNewPeriod({
                        ...newPeriod,
                        invitationExpiryHours: Number(e.target.value),
                      })
                    }
                    required
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Quy tắc hội đồng</label>
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
                        value={newPeriod.minCommitteeMembers}
                        onChange={(e) =>
                          setNewPeriod({
                            ...newPeriod,
                            minCommitteeMembers: Number(e.target.value),
                          })
                        }
                        required
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
                        value={newPeriod.maxCommitteeMembers}
                        onChange={(e) =>
                          setNewPeriod({
                            ...newPeriod,
                            maxCommitteeMembers: Number(e.target.value),
                          })
                        }
                        required
                      />
                    </div>
                  </div>
                </div>
                {(newPeriod.projectType === 'SPECIALIZED' ||
                  newPeriod.projectType === 'GRADUATION') && (
                  <div className="form-group">
                    <label className="form-label" htmlFor="period-review-deadline">
                      Hạn GVPB nộp điểm <span className="text-red">*</span>
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
                {newPeriod.projectType === 'GRADUATION' && (
                  <div className="form-group">
                    <label className="form-label">
                      Ngày báo cáo hội đồng <span className="text-red">*</span>
                    </label>
                    <DateTimeInput
                      type="date"
                      className="form-input"
                      value={newPeriod.defenseDate || ''}
                      onValueChange={(value) =>
                        setNewPeriod({
                          ...newPeriod,
                          defenseDate: value || null,
                        })
                      }
                      required
                    />
                  </div>
                )}
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
