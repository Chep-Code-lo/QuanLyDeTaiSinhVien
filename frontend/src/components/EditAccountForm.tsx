import { useEffect, useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import { academicApi } from '../api/academic'
import { adminApi } from '../api/admin'
import { AnimatedSelect } from './AnimatedSelect'
import { useToast } from '../context/ToastContext'
import { useRequestLoading } from '../hooks/useRequestLoading'
import type { DepartmentResponse, UpdateUserRequest, UserResponse } from '../types/domain'
import { roleLabel } from '../utils/displayLabels'
import './EditAccountForm.css'

type EditAccountFormProps = {
  user: UserResponse
  onCancel: () => void
  onUpdated: (user: UserResponse) => void
}

type AccountForm = {
  fullName: string
  email: string
  phone: string
  studentDepartmentId: string
  studentCode: string
  className: string
  cohort: string
  lecturerDepartmentId: string
  lecturerCode: string
  academicDegree: string
  specialization: string
}

const academicDegrees = ['Thạc Sĩ', 'Tiến Sĩ', 'Phó Giáo Sư', 'Giáo Sư']

function formFromUser(user: UserResponse): AccountForm {
  return {
    fullName: user.fullName,
    email: user.email,
    phone: user.phone ?? '',
    studentDepartmentId: user.student ? String(user.student.departmentId) : '',
    studentCode: user.student?.studentCode ?? '',
    className: user.student?.className ?? '',
    cohort: user.student?.cohort ?? '',
    lecturerDepartmentId: user.lecturer ? String(user.lecturer.departmentId) : '',
    lecturerCode: user.lecturer?.lecturerCode ?? '',
    academicDegree: user.lecturer?.academicDegree ?? '',
    specialization: user.lecturer?.specialization ?? '',
  }
}

function updateError(error: unknown): string {
  const message = isAxiosError(error) ? error.response?.data?.message : undefined
  switch (message) {
    case 'Email is already in use.':
      return 'Email này đã được sử dụng. Vui lòng nhập email khác.'
    case 'Student code is already in use.':
      return 'Mã sinh viên này đã được sử dụng.'
    case 'Lecturer code is already in use.':
      return 'Mã giảng viên này đã được sử dụng.'
    case 'Department was not found.':
      return 'Bộ môn không còn tồn tại. Vui lòng tải lại trang.'
    case 'The account information was updated by another user. Refresh and try again.':
      return 'Thông tin tài khoản vừa được người khác cập nhật. Vui lòng tải lại danh sách.'
    case 'Request validation failed.':
      return 'Thông tin chưa hợp lệ. Vui lòng kiểm tra lại các trường đã nhập.'
    default:
      return message || 'Không thể cập nhật tài khoản. Vui lòng thử lại.'
  }
}

export function EditAccountForm({ user, onCancel, onUpdated }: EditAccountFormProps) {
  const { showError, showSuccess } = useToast()
  const [form, setForm] = useState(() => formFromUser(user))
  const [formUser, setFormUser] = useState(user)
  const [departments, setDepartments] = useState<DepartmentResponse[]>([])
  const [departmentError, setLoadError] = useState(false)
  const [loadAttempt, setLoadAttempt] = useState(0)
  const [submitting, setSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')

  const hasStudentProfile = Boolean(user.student)
  const hasLecturerProfile = Boolean(user.lecturer)
  const requiresDepartments = hasStudentProfile || hasLecturerProfile
  const loadError = requiresDepartments && departmentError
  const [loading, setLoading] = useRequestLoading(JSON.stringify([requiresDepartments, loadAttempt]), requiresDepartments)

  if (formUser !== user) {
    setFormUser(user)
    setForm(formFromUser(user))
    setErrorMessage('')
  }

  useEffect(() => {
    if (!requiresDepartments) return

    let active = true
    academicApi
      .getDepartments()
      .then((items) => {
        if (active) { setDepartments(items); setLoadError(false) }
      })
      .catch(() => {
        if (active) setLoadError(true)
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [loadAttempt, requiresDepartments, setLoading])

  const updateField = (field: keyof AccountForm, value: string) => {
    setForm((previous) => ({ ...previous, [field]: value }))
    setErrorMessage('')
  }

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (submitting) return

    const fail = (message: string) => {
      setErrorMessage(message)
      showError(message)
    }

    if (!form.fullName.trim() || !form.email.trim()) {
      fail('Vui lòng điền họ tên và email.')
      return
    }

    const payload: UpdateUserRequest = {
      version: user.version,
      fullName: form.fullName.trim(),
      email: form.email.trim(),
      phone: form.phone.trim() || undefined,
    }

    if (hasStudentProfile) {
      const departmentId = Number(form.studentDepartmentId)
      if (!departmentId || !form.studentCode.trim() || !form.className.trim() || !form.cohort.trim()) {
        fail('Vui lòng điền đầy đủ hồ sơ sinh viên.')
        return
      }
      payload.student = {
        departmentId,
        studentCode: form.studentCode.trim(),
        className: form.className.trim(),
        cohort: form.cohort.trim(),
      }
    }

    if (hasLecturerProfile) {
      const departmentId = Number(form.lecturerDepartmentId)
      if (!departmentId || !form.lecturerCode.trim()) {
        fail('Vui lòng điền đầy đủ hồ sơ giảng viên.')
        return
      }
      payload.lecturer = {
        departmentId,
        lecturerCode: form.lecturerCode.trim(),
        academicDegree: form.academicDegree.trim() || undefined,
        specialization: form.specialization.trim() || undefined,
      }
    }

    setSubmitting(true)
    try {
      const updated = await adminApi.updateUser(user.id, payload)
      showSuccess(`Đã cập nhật tài khoản ${updated.fullName}.`)
      onUpdated(updated)
    } catch (error) {
      fail(updateError(error))
    } finally {
      setSubmitting(false)
    }
  }

  const unavailableDepartments = requiresDepartments && !loading && !loadError && departments.length === 0

  return (
    <form className="account-edit-form" onSubmit={submit}>
      {loading && (
        <div className="content-state" role="status">
          <span className="spinner" />
          <p>Đang tải danh sách bộ môn...</p>
        </div>
      )}
      {loadError && (
        <div className="account-edit-form__notice" role="alert">
          <p>Không thể tải danh sách bộ môn.</p>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => setLoadAttempt((value) => value + 1)}
          >
            Thử lại
          </button>
        </div>
      )}
      {unavailableDepartments && (
        <div className="account-edit-form__notice" role="alert">
          <p>Chưa có bộ môn nào để cập nhật hồ sơ học vụ.</p>
        </div>
      )}

      <fieldset disabled={submitting || loading || loadError || unavailableDepartments} className="account-edit-form__fields">
        <p className="account-edit-form__roles">Vai trò: {user.roles.map((role) => roleLabel(role)).join(', ')}</p>
        <h2 className="section-title">Thông tin tài khoản</h2>
        <div className="account-edit-form__grid">
          <div className="form-group">
            <label className="form-label" htmlFor="edit-account-name">
              Họ và tên <span className="text-red" aria-hidden="true">*</span>
            </label>
            <input
              id="edit-account-name"
              className="form-input"
              value={form.fullName}
              onChange={(event) => updateField('fullName', event.target.value)}
              maxLength={150}
              autoComplete="off"
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="edit-account-email">
              Email đăng nhập <span className="text-red" aria-hidden="true">*</span>
            </label>
            <input
              id="edit-account-email"
              className="form-input"
              type="email"
              value={form.email}
              onChange={(event) => updateField('email', event.target.value)}
              maxLength={254}
              autoComplete="off"
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="edit-account-phone">Số điện thoại</label>
            <input
              id="edit-account-phone"
              className="form-input"
              type="tel"
              value={form.phone}
              onChange={(event) => updateField('phone', event.target.value)}
              maxLength={20}
              autoComplete="off"
            />
          </div>
        </div>

        {hasStudentProfile && (
          <>
            <h2 className="section-title">Hồ sơ sinh viên</h2>
            <div className="account-edit-form__grid">
              <div className="form-group">
                <label className="form-label" htmlFor="edit-student-department">
                  Bộ môn <span className="text-red" aria-hidden="true">*</span>
                </label>
                <AnimatedSelect
                  id="edit-student-department"
                  value={form.studentDepartmentId}
                  onChange={(event) => updateField('studentDepartmentId', event.target.value)}
                  required
                >
                  <option value="">Chọn bộ môn</option>
                  {departments.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}
                </AnimatedSelect>
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="edit-student-code">
                  Mã sinh viên <span className="text-red" aria-hidden="true">*</span>
                </label>
                <input
                  id="edit-student-code"
                  className="form-input"
                  value={form.studentCode}
                  onChange={(event) => updateField('studentCode', event.target.value)}
                  maxLength={20}
                  required
                />
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="edit-student-class">
                  Lớp <span className="text-red" aria-hidden="true">*</span>
                </label>
                <input
                  id="edit-student-class"
                  className="form-input"
                  value={form.className}
                  onChange={(event) => updateField('className', event.target.value)}
                  maxLength={50}
                  required
                />
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="edit-student-cohort">
                  Khóa <span className="text-red" aria-hidden="true">*</span>
                </label>
                <input
                  id="edit-student-cohort"
                  className="form-input"
                  value={form.cohort}
                  onChange={(event) => updateField('cohort', event.target.value)}
                  maxLength={20}
                  required
                />
              </div>
            </div>
          </>
        )}

        {hasLecturerProfile && (
          <>
            <h2 className="section-title">Hồ sơ giảng viên</h2>
            <div className="account-edit-form__grid">
              <div className="form-group">
                <label className="form-label" htmlFor="edit-lecturer-department">
                  Bộ môn <span className="text-red" aria-hidden="true">*</span>
                </label>
                <AnimatedSelect
                  id="edit-lecturer-department"
                  value={form.lecturerDepartmentId}
                  onChange={(event) => updateField('lecturerDepartmentId', event.target.value)}
                  required
                >
                  <option value="">Chọn bộ môn</option>
                  {departments.map((department) => <option key={department.id} value={department.id}>{department.name}</option>)}
                </AnimatedSelect>
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="edit-lecturer-code">
                  Mã giảng viên <span className="text-red" aria-hidden="true">*</span>
                </label>
                <input
                  id="edit-lecturer-code"
                  className="form-input"
                  value={form.lecturerCode}
                  onChange={(event) => updateField('lecturerCode', event.target.value)}
                  maxLength={20}
                  required
                />
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="edit-lecturer-degree">Học vị</label>
                <AnimatedSelect
                  id="edit-lecturer-degree"
                  value={form.academicDegree}
                  onChange={(event) => updateField('academicDegree', event.target.value)}
                >
                  <option value="">Chọn học vị</option>
                  {academicDegrees.map((degree) => <option key={degree} value={degree}>{degree}</option>)}
                </AnimatedSelect>
              </div>
              <div className="form-group account-edit-form__wide-field">
                <label className="form-label" htmlFor="edit-lecturer-specialization">Chuyên môn</label>
                <textarea
                  id="edit-lecturer-specialization"
                  className="form-input account-edit-form__textarea"
                  value={form.specialization}
                  onChange={(event) => updateField('specialization', event.target.value)}
                  maxLength={5000}
                  rows={3}
                />
              </div>
            </div>
          </>
        )}

        {errorMessage && <p className="account-edit-form__error" role="alert">{errorMessage}</p>}
        <div className="account-edit-form__actions">
          <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={submitting}>Hủy</button>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Đang lưu...' : 'Lưu thay đổi'}
          </button>
        </div>
      </fieldset>
    </form>
  )
}
