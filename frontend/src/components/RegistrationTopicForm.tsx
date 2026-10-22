import { formatDate } from '../utils/dateTime'
import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { academicApi } from '../api/academic'
import { lecturerApi } from '../api/lecturers'
import { topicApi } from '../api/topics'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import type { PageResponse } from '../types/api'
import type {
  DepartmentResponse,
  LecturerDirectoryResponse,
  RegistrationPeriodResponse,
  TopicResponse,
} from '../types/domain'
import { AnimatedSelect } from './AnimatedSelect'
import './RegistrationTopicForm.css'

const emptyDetails = {
  code: '',
  title: '',
  description: '',
  objectives: '',
  expectedOutcomes: '',
  prerequisites: '',
}

const textFields = [
  { name: 'description', label: 'Mô tả đề tài', required: true },
  { name: 'objectives', label: 'Mục tiêu', required: true },
  { name: 'expectedOutcomes', label: 'Kết quả dự kiến', required: true },
  { name: 'prerequisites', label: 'Yêu cầu đối với sinh viên', required: false },
] as const

const messageOf = (error: unknown) => {
  const response = error as { response?: { data?: { message?: string } } }
  return response.response?.data?.message || 'Không thể tải dữ liệu. Vui lòng thử lại.'
}

async function allPages<T>(fetchPage: (page: number) => Promise<PageResponse<T>>) {
  const first = await fetchPage(0)
  const items = [...first.content]
  for (let page = 1; page < first.totalPages; page++) {
    items.push(...(await fetchPage(page)).content)
  }
  return items
}

function serverTimestamp(value: string | undefined) {
  if (!value) return Number.NaN
  return Date.parse(/(?:Z|[+-]\d{2}:\d{2})$/i.test(value) ? value : `${value}Z`)
}

export function RegistrationTopicForm({
  onCreated,
}: {
  onCreated: (topic: TopicResponse) => void
}) {
  const { user, hasRole } = useAuth()
  const { showError, showSuccess } = useToast()
  const admin = hasRole('ADMIN')
  const [expanded, setExpanded] = useState(false)
  const [periods, setPeriods] = useState<RegistrationPeriodResponse[]>([])
  const [departments, setDepartments] = useState<DepartmentResponse[]>([])
  const [lecturers, setLecturers] = useState<LecturerDirectoryResponse[]>([])
  const [periodId, setPeriodId] = useState('')
  const [departmentId, setDepartmentId] = useState('')
  const [primaryId, setPrimaryId] = useState('')
  const [secondaryId, setSecondaryId] = useState('')
  const [details, setDetails] = useState(emptyDetails)
  const [loading, setLoading] = useState(true)
  const [loadingLecturers, setLoadingLecturers] = useState(false)
  const [catalogError, setCatalogError] = useState('')
  const [lecturerError, setLecturerError] = useState('')
  const [reload, setReload] = useState(0)
  const [submitting, setSubmitting] = useState(false)
  const [created, setCreated] = useState<TopicResponse>()
  const [checkedAt, setCheckedAt] = useState(Date.now)

  const allowedDepartments = departments.filter(
    (department) =>
      admin ||
      user?.activeAppointments.some((appointment) =>
        appointment.positionCode === 'DEAN'
          ? appointment.facultyId === department.facultyId
          : appointment.positionCode === 'DEPARTMENT_HEAD' &&
            appointment.departmentId === department.id,
      ),
  )
  const allowedPeriods = periods.filter(
    (period) =>
      period.status === 'ACTIVE' &&
      allowedDepartments.some((department) => department.facultyId === period.facultyId),
  )
  const selectedPeriod =
    allowedPeriods.find((period) => String(period.id) === periodId) ?? allowedPeriods[0]
  const periodDepartments = allowedDepartments.filter(
    (department) => department.facultyId === selectedPeriod?.facultyId,
  )
  const selectedDepartment = periodDepartments.find(
    (department) => String(department.id) === departmentId,
  )
  const facultyId = selectedPeriod?.facultyId
  const proposalStart = serverTimestamp(selectedPeriod?.proposalStartAt)
  const proposalEnd = serverTimestamp(selectedPeriod?.proposalEndAt)
  const proposalEnded = checkedAt > proposalEnd
  const periodNotStarted = checkedAt < proposalStart
  const canManagePeriod =
    admin ||
    user?.activeAppointments.some(
      (appointment) => appointment.positionCode === 'DEAN' && appointment.facultyId === facultyId,
    )

  useEffect(() => {
    if (!expanded) return
    const timer = window.setInterval(() => setCheckedAt(Date.now()), 30_000)
    return () => window.clearInterval(timer)
  }, [expanded])

  useEffect(() => {
    let active = true
    async function loadCatalogs() {
      setLoading(true)
      setCatalogError('')
      try {
        const [loadedDepartments, loadedPeriods] = await Promise.all([
          academicApi.getDepartments(),
          allPages((page) => academicApi.getRegistrationPeriods(undefined, page, 100)),
        ])
        if (active) {
          setDepartments(loadedDepartments)
          setPeriods(loadedPeriods)
        }
      } catch (error) {
        if (active) setCatalogError(messageOf(error))
      } finally {
        if (active) setLoading(false)
      }
    }
    if (expanded) void loadCatalogs()
    return () => {
      active = false
    }
  }, [expanded, reload])

  useEffect(() => {
    let active = true
    async function loadLecturers() {
      setLoadingLecturers(true)
      setLecturerError('')
      setLecturers([])
      try {
        const loaded = await allPages((page) => lecturerApi.list({ facultyId, page, size: 100 }))
        if (active) setLecturers(loaded)
      } catch (error) {
        if (active) setLecturerError(messageOf(error))
      } finally {
        if (active) setLoadingLecturers(false)
      }
    }
    if (expanded && facultyId) void loadLecturers()
    return () => {
      active = false
    }
  }, [expanded, facultyId, reload])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (submitting) return
    if (!selectedPeriod || !selectedDepartment || !primaryId) {
      showError('Vui lòng chọn đợt, bộ môn và giảng viên hướng dẫn chính.')
      return
    }
    if (
      textFields.some((field) => field.required && !details[field.name].trim()) ||
      !details.code.trim() ||
      !details.title.trim()
    ) {
      showError('Vui lòng nhập đầy đủ các thông tin bắt buộc.')
      return
    }
    if (primaryId === secondaryId) {
      showError('Hai giảng viên hướng dẫn phải khác nhau.')
      return
    }
    setSubmitting(true)
    try {
      const topic = await topicApi.createForRegistration({
        periodId: selectedPeriod.id,
        departmentId: selectedDepartment.id,
        code: details.code.trim(),
        title: details.title.trim(),
        description: details.description.trim(),
        objectives: details.objectives.trim(),
        expectedOutcomes: details.expectedOutcomes.trim(),
        prerequisites: details.prerequisites.trim() || undefined,
        primaryLecturerId: Number(primaryId),
        secondaryLecturerId: secondaryId ? Number(secondaryId) : undefined,
      })
      setCreated(topic)
      setDetails(emptyDetails)
      setPrimaryId('')
      setSecondaryId('')
      setExpanded(false)
      onCreated(topic)
      showSuccess(`Đã tạo và công bố đề tài ${topic.code} cho sinh viên đăng ký.`)
    } catch (error) {
      showError(messageOf(error))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="page-card topic-creation">
      <div className="topic-creation__header">
        <h2 className="section-title">Đề tài cho sinh viên đăng ký</h2>
        <button
          type="button"
          className={expanded ? 'btn btn-secondary' : 'btn btn-primary'}
          aria-expanded={expanded}
          aria-controls="registration-topic-form"
          disabled={submitting}
          onClick={() => {
            setCheckedAt(Date.now())
            setExpanded((value) => !value)
          }}
        >
          {expanded ? 'Đóng biểu mẫu' : '+ Tạo đề tài'}
        </button>
      </div>

      {created && (
        <div className="topic-creation__success" role="status">
          <strong>
            {created.code} — {created.title}
          </strong>
          <span>Đã công bố · {created.periodName}</span>
          <Link className="text-button" to="/topics">
            Xem danh sách đề tài
          </Link>
        </div>
      )}

      {expanded && (
        <div id="registration-topic-form" className="topic-creation__body">
          {loading ? (
            <div className="content-state">
              <span className="spinner" />
              <p>Đang tải đợt đăng ký...</p>
            </div>
          ) : catalogError ? (
            <div className="content-state" role="alert">
              <p>{catalogError}</p>
              <button className="btn btn-secondary" onClick={() => setReload((value) => value + 1)}>
                Thử lại
              </button>
            </div>
          ) : !allowedPeriods.length ? (
            <div className="content-state">
              Chưa có đợt đăng ký đang hoạt động trong phạm vi quản lý của bạn.
            </div>
          ) : (
            <form onSubmit={submit}>
              <fieldset className="topic-creation__fields" disabled={submitting}>
                {(proposalEnded || periodNotStarted) && (
                  <div className="topic-creation__notice" role="status">
                    <p>
                      {proposalEnded
                        ? `Đợt “${selectedPeriod?.name}” đã kết thúc thời gian giảng viên đăng ký đề tài ngày ${formatDate(proposalEnd)}.`
                        : `Đợt này bắt đầu nhận đề xuất từ ngày ${formatDate(proposalStart)}.`}
                    </p>
                    {canManagePeriod ? (
                      <Link className="text-button" to="/academic-management">
                        Quản lý đợt đăng ký
                      </Link>
                    ) : (
                      <span>
                        Vui lòng liên hệ Trưởng khoa hoặc Quản trị viên để mở đợt phù hợp.
                      </span>
                    )}
                  </div>
                )}
                <div className="grid-cols-2">
                  <div className="form-group">
                    <label className="form-label" htmlFor="topic-period">
                      Đợt đăng ký <span className="text-red">*</span>
                    </label>
                    <AnimatedSelect
                      id="topic-period"
                      value={selectedPeriod?.id ?? ''}
                      required
                      onChange={(event) => {
                        setPeriodId(event.target.value)
                        setDepartmentId('')
                        setPrimaryId('')
                        setSecondaryId('')
                      }}
                    >
                      {allowedPeriods.map((period) => (
                        <option key={period.id} value={period.id}>
                          {period.name}
                        </option>
                      ))}
                    </AnimatedSelect>
                  </div>
                  <div className="form-group">
                    <label className="form-label" htmlFor="topic-department">
                      Bộ môn <span className="text-red">*</span>
                    </label>
                    <AnimatedSelect
                      id="topic-department"
                      value={selectedDepartment?.id ?? ''}
                      required
                      onChange={(event) => setDepartmentId(event.target.value)}
                    >
                      <option value="">Chọn bộ môn</option>
                      {periodDepartments.map((department) => (
                        <option key={department.id} value={department.id}>
                          {department.name}
                        </option>
                      ))}
                    </AnimatedSelect>
                  </div>
                  <div className="form-group">
                    <label className="form-label" htmlFor="topic-code">
                      Mã đề tài <span className="text-red">*</span>
                    </label>
                    <input
                      id="topic-code"
                      className="form-input"
                      maxLength={30}
                      required
                      value={details.code}
                      onChange={(event) => setDetails({ ...details, code: event.target.value })}
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label" htmlFor="topic-title">
                      Tên đề tài <span className="text-red">*</span>
                    </label>
                    <input
                      id="topic-title"
                      className="form-input"
                      maxLength={300}
                      required
                      value={details.title}
                      onChange={(event) => setDetails({ ...details, title: event.target.value })}
                    />
                  </div>
                </div>
                {textFields.map((field) => (
                  <div className="form-group" key={field.name}>
                    <label className="form-label" htmlFor={`topic-${field.name}`}>
                      {field.label}
                      {field.required ? (
                        <>
                          {' '}
                          <span className="text-red">*</span>
                        </>
                      ) : (
                        ' (không bắt buộc)'
                      )}
                    </label>
                    <textarea
                      id={`topic-${field.name}`}
                      className="form-input"
                      rows={3}
                      required={field.required}
                      value={details[field.name]}
                      onChange={(event) =>
                        setDetails({ ...details, [field.name]: event.target.value })
                      }
                    />
                  </div>
                ))}
                <div className="grid-cols-2">
                  <div className="form-group">
                    <label className="form-label" htmlFor="topic-primary">
                      Giảng viên hướng dẫn chính <span className="text-red">*</span>
                    </label>
                    <AnimatedSelect
                      id="topic-primary"
                      value={primaryId}
                      required
                      disabled={loadingLecturers || !!lecturerError}
                      onChange={(event) => setPrimaryId(event.target.value)}
                    >
                      <option value="">
                        {loadingLecturers ? 'Đang tải giảng viên...' : 'Chọn giảng viên hướng dẫn'}
                      </option>
                      {lecturers.map((lecturer) => (
                        <option
                          key={lecturer.id}
                          value={lecturer.id}
                          disabled={String(lecturer.id) === secondaryId}
                        >
                          {lecturer.fullName} ({lecturer.lecturerCode}) — {lecturer.departmentName}
                        </option>
                      ))}
                    </AnimatedSelect>
                  </div>
                  <div className="form-group">
                    <label className="form-label" htmlFor="topic-secondary">
                      Giảng viên đồng hướng dẫn (không bắt buộc)
                    </label>
                    <AnimatedSelect
                      id="topic-secondary"
                      value={secondaryId}
                      disabled={loadingLecturers || !!lecturerError}
                      onChange={(event) => setSecondaryId(event.target.value)}
                    >
                      <option value="">Không có</option>
                      {lecturers.map((lecturer) => (
                        <option
                          key={lecturer.id}
                          value={lecturer.id}
                          disabled={String(lecturer.id) === primaryId}
                        >
                          {lecturer.fullName} ({lecturer.lecturerCode}) — {lecturer.departmentName}
                        </option>
                      ))}
                    </AnimatedSelect>
                  </div>
                </div>
                {lecturerError ? (
                  <div className="topic-creation__error" role="alert">
                    <span>{lecturerError}</span>
                    <button
                      type="button"
                      className="text-button"
                      onClick={() => setReload((value) => value + 1)}
                    >
                      Tải lại giảng viên
                    </button>
                  </div>
                ) : !loadingLecturers && !lecturers.length ? (
                  <p role="status">
                    Khoa chưa có giảng viên đang hoạt động để phân công hướng dẫn.
                  </p>
                ) : null}
                <div className="topic-creation__footer">
                  <p>Đề tài sẽ được công bố cho sinh viên thuộc đợt đã chọn đăng ký.</p>
                  <button
                    className="btn btn-primary"
                    type="submit"
                    disabled={
                      submitting ||
                      loadingLecturers ||
                      !lecturers.length ||
                      !!lecturerError ||
                      proposalEnded ||
                      periodNotStarted ||
                      !selectedDepartment ||
                      !primaryId
                    }
                  >
                    {submitting ? 'Đang tạo đề tài...' : 'Tạo và công bố'}
                  </button>
                </div>
              </fieldset>
            </form>
          )}
        </div>
      )}
    </section>
  )
}
