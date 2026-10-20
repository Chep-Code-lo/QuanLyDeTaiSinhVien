import { formatDateTime, toLocalDateTimeInput } from '../utils/dateTime'
import { DateTimeInput } from '../components/DateTimeInput'
import { AnimatedSelect } from '../components/AnimatedSelect'
import React, { useEffect, useState } from 'react'
import { academicApi } from '../api/academic'
import { adminApi } from '../api/admin'
import { LecturerAutocomplete } from '../components/LecturerAutocomplete'
import { ConfirmModal } from '../components/ConfirmModal'
import { useToast } from '../context/ToastContext'
import type {
  AppointmentResponse,
  CreateAppointmentRequest,
  DepartmentResponse,
  FacultyResponse,
  PositionCode,
} from '../types/domain'

export const AcademicAppointmentsPage: React.FC = () => {
  const { showSuccess, showError } = useToast()

  const [appointments, setAppointments] = useState<AppointmentResponse[]>([])
  const [loading, setLoading] = useState(false)

  const [faculties, setFaculties] = useState<FacultyResponse[]>([])
  const [departments, setDepartments] = useState<DepartmentResponse[]>([])

  // Modal State
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [appointmentToEnd, setAppointmentToEnd] = useState<AppointmentResponse | null>(null)
  const [endingAppointment, setEndingAppointment] = useState(false)

  const [newAppointment, setNewAppointment] = useState<{
    lecturerId: number | null
    facultyId: number
    departmentId: number | null
    positionCode: PositionCode
    startsAt: string
    endsAt: string
  }>({
    lecturerId: null,
    facultyId: 0,
    departmentId: null,
    positionCode: 'DEAN',
    startsAt: toLocalDateTimeInput(new Date()),
    endsAt: '',
  })

  useEffect(() => {
    loadAppointments()
    academicApi
      .getFaculties()
      .then((res) => {
        setFaculties(res)
        if (res.length > 0) {
          setNewAppointment((prev) => ({ ...prev, facultyId: res[0].id }))
        }
      })
      .catch((error) => showError(error.message))
  }, [])

  const loadAppointments = () => {
    setLoading(true)
    Promise.all([adminApi.getAppointments(), academicApi.getDepartments()])
      .then(([items, catalog]) => {
        setAppointments(items)
        setDepartments(catalog)
      })
      .catch((err) =>
        showError('Lỗi tải danh sách chức vụ: ' + (err.response?.data?.message || err.message)),
      )
      .finally(() => setLoading(false))
  }

  const handleCreateAppointment = async (e: React.FormEvent) => {
    e.preventDefault()
    if (submitting) return
    if (!newAppointment.lecturerId || !newAppointment.facultyId || !newAppointment.startsAt) {
      showError('Vui lòng điền đầy đủ thông tin bắt buộc')
      return
    }

    if (newAppointment.positionCode === 'DEPARTMENT_HEAD' && !newAppointment.departmentId) {
      showError('Chức vụ Trưởng bộ môn yêu cầu chọn bộ môn phụ trách!')
      return
    }
    if (newAppointment.endsAt && new Date(newAppointment.endsAt) <= new Date(newAppointment.startsAt)) {
      showError('Ngày kết thúc phải sau ngày bắt đầu bổ nhiệm.')
      return
    }

    try {
      setSubmitting(true)
      const payload: CreateAppointmentRequest = {
        lecturerId: newAppointment.lecturerId,
        facultyId: newAppointment.facultyId,
        departmentId:
          newAppointment.positionCode === 'DEPARTMENT_HEAD' ? newAppointment.departmentId : null,
        positionCode: newAppointment.positionCode,
        startsAt: new Date(newAppointment.startsAt).toISOString(),
        endsAt: newAppointment.endsAt ? new Date(newAppointment.endsAt).toISOString() : null,
      }

      const created = await adminApi.createAppointment(payload)
      showSuccess('Tạo quyết định bổ nhiệm thành công!')
      setAppointments((prev) => [created, ...prev])
      setIsModalOpen(false)
      setNewAppointment({
        lecturerId: null,
        facultyId: faculties[0]?.id || 0,
        departmentId: null,
        positionCode: 'DEAN',
        startsAt: toLocalDateTimeInput(new Date()),
        endsAt: '',
      })
    } catch (err: any) {
      showError('Lỗi tạo bổ nhiệm: ' + (err.response?.data?.message || err.message))
    } finally {
      setSubmitting(false)
    }
  }

  const handleEndAppointment = async () => {
    if (!appointmentToEnd || endingAppointment) return
    const id = appointmentToEnd.id
    setEndingAppointment(true)
    try {
      const nowIso = new Date().toISOString()
      const updated = await adminApi.endAppointment(id, nowIso)
      showSuccess('Đã kết thúc đợt bổ nhiệm thành công!')
      setAppointments((prev) => prev.map((a) => (a.id === id ? updated : a)))
      setAppointmentToEnd(null)
    } catch (err: any) {
      showError('Lỗi kết thúc bổ nhiệm: ' + (err.response?.data?.message || err.message))
    } finally {
      setEndingAppointment(false)
    }
  }

  const filteredDepartments = departments.filter(
    (d) => d.facultyId === Number(newAppointment.facultyId),
  )

  return (
    <div className="academic-appointments-page">
      <div className="page-header">
        <div>
          <h1>Bổ nhiệm chức vụ</h1>
        </div>
      </div>

      <div className="page-card">
        <div className="appointments-list-header">
          <h2 className="section-title">Danh sách bổ nhiệm hiện tại</h2>
          <button type="button" className="btn btn-primary" onClick={() => setIsModalOpen(true)}>
            + Tạo quyết định bổ nhiệm
          </button>
        </div>

        {loading ? (
          <div className="text-center py-8">
            <div className="spinner" />
            <p className="text-secondary mt-2">Đang tải danh sách bổ nhiệm...</p>
          </div>
        ) : appointments.length === 0 ? (
          <p className="text-secondary text-center py-6">Chưa có quyết định bổ nhiệm nào.</p>
        ) : (
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Giảng viên</th>
                  <th>Chức vụ đảm nhiệm</th>
                  <th>Bộ môn</th>
                  <th>Bắt đầu</th>
                  <th>Kết thúc</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {appointments.map((item, idx) => (
                  <tr key={item.id}>
                    <td>{idx + 1}</td>
                    <td className="font-bold text-dark-blue">
                      {item.lecturerName} ({item.lecturerCode})
                    </td>
                    <td>
                      <span
                        className={`badge ${
                          item.positionCode === 'DEAN' ? 'badge-success' : 'badge-info'
                        }`}
                      >
                        {item.positionCode === 'DEAN' ? 'Trưởng khoa' : 'Trưởng bộ môn'}
                      </span>
                    </td>
                    <td>
                      {item.departmentId
                        ? departments.find((department) => department.id === item.departmentId)
                            ?.name || 'Bộ môn không còn trong danh mục'
                        : 'Không áp dụng'}
                    </td>
                    <td className="text-xs">{formatDateTime(item.startsAt)}</td>
                    <td className="text-xs">
                      {item.endsAt ? (
                        formatDateTime(item.endsAt)
                      ) : (
                        <span className="badge badge-success">Đang đảm nhiệm</span>
                      )}
                    </td>
                    <td>
                      {!item.endsAt && (
                        <button
                          type="button"
                          className="btn btn-outline btn-sm"
                          onClick={() => setAppointmentToEnd(item)}
                          disabled={endingAppointment}
                        >
                          Kết thúc bổ nhiệm
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
        isOpen={appointmentToEnd !== null}
        title="Xác nhận kết thúc bổ nhiệm"
        message={
          appointmentToEnd
            ? `Bạn có chắc muốn kết thúc bổ nhiệm ${appointmentToEnd.positionCode === 'DEAN' ? 'Trưởng khoa' : 'Trưởng bộ môn'} của ${appointmentToEnd.lecturerName} (${appointmentToEnd.lecturerCode})?`
            : ''
        }
        confirmText="Kết thúc bổ nhiệm"
        variant="danger"
        loading={endingAppointment}
        onConfirm={handleEndAppointment}
        onCancel={() => {
          if (!endingAppointment) setAppointmentToEnd(null)
        }}
      />

      {/* Modal Form Create Appointment */}
      {isModalOpen && (
        <div className="modal-backdrop fade-in" onClick={() => { if (!submitting) setIsModalOpen(false) }}>
          <div className="modal-content scale-in" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="font-semibold text-lg">Tạo quyết định bổ nhiệm mới</h3>
              <button type="button" className="btn-close" disabled={submitting} onClick={() => setIsModalOpen(false)}>
                &times;
              </button>
            </div>
            <form onSubmit={handleCreateAppointment}>
              <div className="modal-body py-4 space-y-4">
                <div className="form-group">
                  <LecturerAutocomplete
                    label="Giảng viên được bổ nhiệm"
                    required
                    value={newAppointment.lecturerId}
                    onChange={(id) => setNewAppointment({ ...newAppointment, lecturerId: id })}
                    placeholder="Tìm giảng viên theo tên, mã..."
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">
                    Chức vụ học thuật <span className="text-red">*</span>
                  </label>
                  <AnimatedSelect
                    className="form-select font-semibold"
                    value={newAppointment.positionCode}
                    onChange={(e) =>
                      setNewAppointment({
                        ...newAppointment,
                        positionCode: e.target.value as PositionCode,
                      })
                    }
                  >
                    <option value="DEAN">Trưởng khoa</option>
                    <option value="DEPARTMENT_HEAD">Trưởng bộ môn</option>
                  </AnimatedSelect>
                </div>

                {newAppointment.positionCode === 'DEPARTMENT_HEAD' && (
                  <div className="form-group">
                    <label className="form-label">
                      Bộ môn phụ trách <span className="text-red">*</span>
                    </label>
                    <AnimatedSelect
                      className="form-select"
                      value={newAppointment.departmentId ?? ''}
                      onChange={(e) =>
                        setNewAppointment({
                          ...newAppointment,
                          departmentId: Number(e.target.value) || null,
                        })
                      }
                      required
                    >
                      <option value="">-- Chọn Bộ môn --</option>
                      {filteredDepartments.map((d) => (
                        <option key={d.id} value={d.id}>
                          {d.name} ({d.code})
                        </option>
                      ))}
                    </AnimatedSelect>
                  </div>
                )}

                <div className="grid-cols-2">
                  <div className="form-group">
                    <label className="form-label">
                      Ngày bắt đầu <span className="text-red">*</span>
                    </label>
                    <DateTimeInput
                      type="datetime-local"
                      className="form-input"
                      value={newAppointment.startsAt}
                      onValueChange={(value) =>
                        setNewAppointment({ ...newAppointment, startsAt: value })
                      }
                      required
                    />
                  </div>

                  <div className="form-group">
                    <label className="form-label">Ngày kết thúc (Không bắt buộc)</label>
                    <DateTimeInput
                      type="datetime-local"
                      className="form-input"
                      value={newAppointment.endsAt}
                      onValueChange={(value) =>
                        setNewAppointment({ ...newAppointment, endsAt: value })
                      }
                    />
                  </div>
                </div>
              </div>

              <div className="modal-footer flex gap-3 justify-end">
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setIsModalOpen(false)}
                  disabled={submitting}
                >
                  Hủy
                </button>
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                  {submitting ? 'Đang tạo...' : 'Xác nhận bổ nhiệm'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
