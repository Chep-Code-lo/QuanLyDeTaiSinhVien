import { useEffect, useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import { Link } from 'react-router-dom'
import { academicApi } from '../api/academic'
import { adminApi } from '../api/admin'
import { AnimatedSelect } from './AnimatedSelect'
import { useToast } from '../context/ToastContext'
import type { CreateUserRequest, DepartmentResponse, UserResponse } from '../types/domain'
import { isValidPassword, PASSWORD_PATTERN, PASSWORD_REQUIREMENTS } from '../utils/passwordPolicy'
import './CreateAccountForm.css'

type CreateAccountFormProps = {
  onCancel: () => void
  onCreated: (user: UserResponse) => void
}

const emptyForm = {
  fullName: '',
  email: '',
  phone: '',
  password: '',
  confirmPassword: '',
  departmentId: '',
  studentCode: '',
  className: '',
  cohort: '',
  lecturerCode: '',
  academicDegree: '',
}

const academicDegrees = ['Thạc Sĩ', 'Tiến Sĩ', 'Phó Giáo Sư', 'Giáo Sư']

function creationError(error: unknown) {
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
    case 'Request validation failed.':
    case 'Thông tin chưa hợp lệ. Vui lòng kiểm tra lại các trường đã nhập.':
      return (
        (isAxiosError(error) && error.response?.data?.data?.password) ||
        'Thông tin chưa hợp lệ. Vui lòng kiểm tra lại các trường đã nhập.'
      )
    case 'You do not have permission to perform this action.':
      return 'Bạn không có quyền tạo tài khoản.'
    case 'The requested change conflicts with existing data.':
      return 'Email hoặc mã hồ sơ đã được sử dụng. Vui lòng kiểm tra lại.'
    default:
      return message || 'Không thể tạo tài khoản. Vui lòng thử lại.'
  }
}

export function CreateAccountForm({ onCancel, onCreated }: CreateAccountFormProps) {
  const { showError, showSuccess } = useToast()
  const [role, setRole] = useState<'STUDENT' | 'LECTURER'>('STUDENT')
  const [form, setForm] = useState(emptyForm)
  const [departments, setDepartments] = useState<DepartmentResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [loadAttempt, setLoadAttempt] = useState(0)
  const [submitting, setSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')

  useEffect(() => {
    let cancelled = false
    academicApi
      .getDepartments()
      .then((items) => {
        if (!cancelled) setDepartments(items)
      })
      .catch(() => {
        if (!cancelled) setLoadError(true)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [loadAttempt])

  const updateField = (field: keyof typeof emptyForm, value: string) => {
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

    setErrorMessage('')
    if (
      !form.fullName.trim() ||
      !form.email.trim() ||
      (role === 'STUDENT' &&
        (!form.studentCode.trim() || !form.className.trim() || !form.cohort.trim())) ||
      (role === 'LECTURER' && !form.lecturerCode.trim())
    ) {
      fail('Vui lòng điền đầy đủ thông tin.')
      return
    }
    if (form.password !== form.confirmPassword) {
      fail('Mật khẩu không khớp.')
      return
    }
    if (!isValidPassword(form.password)) {
      fail(PASSWORD_REQUIREMENTS)
      return
    }

    const departmentId = Number(form.departmentId)
    const selectedDepartment = departments.find((department) => department.id === departmentId)
    if (!selectedDepartment) {
      fail('Vui lòng chọn bộ môn cho tài khoản.')
      return
    }

    const common = {
      fullName: form.fullName.trim(),
      email: form.email.trim(),
      password: form.password,
      phone: form.phone.trim() || undefined,
    }
    const payload: CreateUserRequest =
      role === 'STUDENT'
        ? {
            ...common,
            roles: ['STUDENT'],
            student: {
              departmentId,
              studentCode: form.studentCode.trim(),
              className: form.className.trim(),
              cohort: form.cohort.trim(),
            },
          }
        : {
            ...common,
            roles: ['LECTURER'],
            lecturer: {
              departmentId,
              lecturerCode: form.lecturerCode.trim(),
              academicDegree: form.academicDegree.trim() || undefined,
              specialization: selectedDepartment.name,
            },
          }

    setSubmitting(true)
    try {
      const user = await adminApi.createUser(payload)
      setForm(emptyForm)
      showSuccess(`Đã tạo tài khoản cho ${user.fullName}.`)
      onCreated(user)
    } catch (error) {
      fail(creationError(error))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="create-account-form" onSubmit={submit}>
      {loading && (
        <div className="content-state" role="status">
          <span className="spinner" />
          <p>Đang tải danh sách bộ môn...</p>
        </div>
      )}
      {loadError && (
        <div className="create-account-form__notice" role="alert">
          <p>Không thể tải danh sách bộ môn.</p>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => {
              setLoading(true)
              setLoadError(false)
              setLoadAttempt((value) => value + 1)
            }}
          >
            Thử lại
          </button>
        </div>
      )}
      {!loading && !loadError && departments.length === 0 && (
        <div className="create-account-form__notice" role="status">
          <p>Cần có ít nhất một bộ môn trước khi tạo tài khoản.</p>
          <Link className="btn btn-secondary" to="/academic-management" onClick={onCancel}>
            Quản lý học thuật
          </Link>
        </div>
      )}

      <fieldset
        className="create-account-form__fields"
        disabled={submitting || loading || loadError || departments.length === 0}
      >
        <h2 className="section-title">Thông tin tài khoản</h2>
        <div className="create-account-form__grid">
          <div className="form-group">
            <label className="form-label" htmlFor="create-role">
              Vai trò{' '}
              <span className="text-red" aria-hidden="true">
                *
              </span>
            </label>
            <AnimatedSelect
              id="create-role"
              value={role}
              required
              onChange={(event) => {
                setRole(event.target.value as 'STUDENT' | 'LECTURER')
                setErrorMessage('')
              }}
            >
              <option value="STUDENT">Sinh viên</option>
              <option value="LECTURER">Giảng viên</option>
            </AnimatedSelect>
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="create-name">
              Họ và tên{' '}
              <span className="text-red" aria-hidden="true">
                *
              </span>
            </label>
            <input
              id="create-name"
              className="form-input"
              value={form.fullName}
              onChange={(event) => updateField('fullName', event.target.value)}
              maxLength={150}
              autoComplete="off"
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="create-email">
              Email đăng nhập{' '}
              <span className="text-red" aria-hidden="true">
                *
              </span>
            </label>
            <input
              id="create-email"
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
            <label className="form-label" htmlFor="create-phone">
              Số điện thoại
            </label>
            <input
              id="create-phone"
              className="form-input"
              type="tel"
              value={form.phone}
              onChange={(event) => updateField('phone', event.target.value)}
              maxLength={20}
              autoComplete="off"
            />
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="create-password">
              Mật khẩu{' '}
              <span className="text-red" aria-hidden="true">
                *
              </span>
            </label>
            <input
              id="create-password"
              className="form-input"
              type="password"
              value={form.password}
              onChange={(event) => updateField('password', event.target.value)}
              minLength={8}
              maxLength={100}
              pattern={PASSWORD_PATTERN}
              title={PASSWORD_REQUIREMENTS}
              autoComplete="new-password"
              aria-describedby="create-password-hint"
              required
            />
            <small id="create-password-hint">{PASSWORD_REQUIREMENTS}</small>
          </div>
          <div className="form-group">
            <label className="form-label" htmlFor="create-confirm-password">
              Xác nhận mật khẩu{' '}
              <span className="text-red" aria-hidden="true">
                *
              </span>
            </label>
            <input
              id="create-confirm-password"
              className="form-input"
              type="password"
              value={form.confirmPassword}
              onChange={(event) => updateField('confirmPassword', event.target.value)}
              minLength={8}
              maxLength={100}
              autoComplete="new-password"
              required
            />
          </div>
        </div>

        <h2 className="section-title">Hồ sơ {role === 'STUDENT' ? 'sinh viên' : 'giảng viên'}</h2>
        <div className="create-account-form__grid">
          <div className="form-group">
            <label className="form-label" htmlFor="create-department">
              Bộ môn{' '}
              <span className="text-red" aria-hidden="true">
                *
              </span>
            </label>
            <AnimatedSelect
              id="create-department"
              value={form.departmentId}
              onChange={(event) => updateField('departmentId', event.target.value)}
              required
            >
              <option value="">Chọn bộ môn</option>
              {departments.map((department) => (
                <option key={department.id} value={department.id}>
                  {department.name.trim().toLocaleLowerCase('vi') === 'công nghệ phần mềm'
                    ? 'Công Nghệ Phần Mềm'
                    : department.name}
                </option>
              ))}
            </AnimatedSelect>
          </div>
          {role === 'STUDENT' ? (
            <>
              <div className="form-group">
                <label className="form-label" htmlFor="create-student-code">
                  Mã sinh viên{' '}
                  <span className="text-red" aria-hidden="true">
                    *
                  </span>
                </label>
                <input
                  id="create-student-code"
                  className="form-input"
                  value={form.studentCode}
                  onChange={(event) => updateField('studentCode', event.target.value)}
                  maxLength={20}
                  required
                />
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="create-class">
                  Lớp{' '}
                  <span className="text-red" aria-hidden="true">
                    *
                  </span>
                </label>
                <input
                  id="create-class"
                  className="form-input"
                  value={form.className}
                  onChange={(event) => updateField('className', event.target.value)}
                  maxLength={50}
                  required
                />
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="create-cohort">
                  Khóa{' '}
                  <span className="text-red" aria-hidden="true">
                    *
                  </span>
                </label>
                <input
                  id="create-cohort"
                  className="form-input"
                  placeholder="Ví dụ: 2026"
                  value={form.cohort}
                  onChange={(event) => updateField('cohort', event.target.value)}
                  maxLength={20}
                  required
                />
              </div>
            </>
          ) : (
            <>
              <div className="form-group">
                <label className="form-label" htmlFor="create-lecturer-code">
                  Mã giảng viên{' '}
                  <span className="text-red" aria-hidden="true">
                    *
                  </span>
                </label>
                <input
                  id="create-lecturer-code"
                  className="form-input"
                  value={form.lecturerCode}
                  onChange={(event) => updateField('lecturerCode', event.target.value)}
                  maxLength={20}
                  required
                />
              </div>
              <div className="form-group">
                <label className="form-label" htmlFor="create-degree">
                  Học vị
                </label>
                <AnimatedSelect
                  id="create-degree"
                  value={form.academicDegree}
                  onChange={(event) => updateField('academicDegree', event.target.value)}
                >
                  <option value="">Chọn học vị</option>
                  {academicDegrees.map((degree) => (
                    <option key={degree} value={degree}>
                      {degree}
                    </option>
                  ))}
                </AnimatedSelect>
              </div>
            </>
          )}
        </div>

        {errorMessage && (
          <p className="create-account-form__error" role="alert">
            {errorMessage}
          </p>
        )}
        <div className="create-account-form__actions">
          <button
            type="button"
            className="btn btn-secondary"
            onClick={onCancel}
            disabled={submitting}
          >
            Hủy
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Đang tạo tài khoản...' : 'Tạo tài khoản'}
          </button>
        </div>
      </fieldset>
    </form>
  )
}
