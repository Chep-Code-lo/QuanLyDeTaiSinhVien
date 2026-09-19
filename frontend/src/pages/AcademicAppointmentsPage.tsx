import React, { useEffect, useState } from 'react'
import { academicApi } from '../api/academic'
import { adminApi } from '../api/admin'
import { LecturerAutocomplete } from '../components/LecturerAutocomplete'
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
    startsAt: new Date().toISOString().substring(0, 16),
    endsAt: '',
  })

  useEffect(() => {
    loadAppointments()
    academicApi.getFaculties().then((res) => {
      setFaculties(res)
      if (res.length > 0) {
        setNewAppointment((prev) => ({ ...prev, facultyId: res[0].id }))
      }
    })
    academicApi.getDepartments().then(setDepartments)
  }, [])

  const loadAppointments = () => {
    setLoading(true)
    adminApi
      .getAppointments()
      .then(setAppointments)
      .catch((err) => showError('Lỗi tải danh sách chức vụ: ' + (err.response?.data?.message || err.message)))
      .finally(() => setLoading(false))
  }

  const handleCreateAppointment = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newAppointment.lecturerId || !newAppointment.facultyId || !newAppointment.startsAt) {
      showError('Vui lòng điền đầy đủ thông tin bắt buộc')
      return
    }

    if (newAppointment.positionCode === 'DEPARTMENT_HEAD' && !newAppointment.departmentId) {
      showError('Chức vụ Trưởng Bộ môn (DEPARTMENT_HEAD) yêu cầu chọn Bộ môn!')
      return
    }

    try {
      setSubmitting(true)
      const payload: CreateAppointmentRequest = {
        lecturerId: newAppointment.lecturerId,
        facultyId: newAppointment.facultyId,
        departmentId: newAppointment.positionCode === 'DEPARTMENT_HEAD' ? newAppointment.departmentId : null,
        positionCode: newAppointment.positionCode,
        startsAt: new Date(newAppointment.startsAt).toISOString(),
        endsAt: newAppointment.endsAt ? new Date(newAppointment.endsAt).toISOString() : null,
      }

      const created = await adminApi.createAppointment(payload)
      showSuccess('Tạo Bổ nhiệm Chức vụ thành công!')
      setAppointments((prev) => [created, ...prev])
      setIsModalOpen(false)
      setNewAppointment({
        lecturerId: null,
        facultyId: faculties[0]?.id || 0,
        departmentId: null,
        positionCode: 'DEAN',
        startsAt: new Date().toISOString().substring(0, 16),
        endsAt: '',
      })
    } catch (err: any) {
      showError('Lỗi tạo bổ nhiệm: ' + (err.response?.data?.message || err.message))
    } finally {
      setSubmitting(false)
    }
  }

  const handleEndAppointment = async (id: number) => {
    try {
      const nowIso = new Date().toISOString()
      const updated = await adminApi.endAppointment(id, nowIso)
      showSuccess('Đã kết thúc đợt bổ nhiệm thành công!')
      setAppointments((prev) => prev.map((a) => (a.id === id ? updated : a)))
    } catch (err: any) {
      showError('Lỗi kết thúc bổ nhiệm: ' + (err.response?.data?.message || err.message))
    }
  }

  const filteredDepartments = departments.filter(
    (d) => d.facultyId === Number(newAppointment.facultyId)
  )

  return (
    <div className="academic-appointments-page">
      <div className="page-header">
        <div>
          <span className="page-header__eyebrow">Quản trị Admin</span>
          <h1>Quản lý Bổ nhiệm Chức vụ Học thuật (Academic Appointments)</h1>
          <p>Phân công vai trò Trưởng Khoa (DEAN) và Trưởng Bộ Môn (DEPARTMENT HEAD) cho Giảng viên.</p>
        </div>
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => setIsModalOpen(true)}
        >
          + Bổ nhiệm Chức vụ Mới
        </button>
      </div>

      <div className="page-card">
        <h2 className="font-semibold text-lg text-primary mb-4">
          Danh sách Bổ nhiệm Hiện tại ({appointments.length})
        </h2>

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
                  <th>Khoa trực thuộc</th>
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
                        {item.positionCode === 'DEAN' ? 'Trưởng Khoa (DEAN)' : 'Trưởng Bộ Môn (DEPARTMENT_HEAD)'}
                      </span>
                    </td>
                    <td>ID Khoa: #{item.facultyId}</td>
                    <td>{item.departmentId ? `ID Bộ môn: #${item.departmentId}` : '-'}</td>
                    <td className="text-xs">{new Date(item.startsAt).toLocaleString('vi-VN')}</td>
                    <td className="text-xs">
                      {item.endsAt ? (
                        new Date(item.endsAt).toLocaleString('vi-VN')
                      ) : (
                        <span className="badge badge-success">Đang đảm nhiệm</span>
                      )}
                    </td>
                    <td>
                      {!item.endsAt && (
                        <button
                          type="button"
                          className="btn btn-danger btn-sm"
                          onClick={() => handleEndAppointment(item.id)}
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

      {/* Modal Form Create Appointment */}
      {isModalOpen && (
        <div className="modal-backdrop fade-in" onClick={() => setIsModalOpen(false)}>
          <div className="modal-content scale-in" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="font-semibold text-lg">Tạo Bổ Nhiệm Chức Vụ Mới</h3>
              <button
                type="button"
                className="btn-close"
                onClick={() => setIsModalOpen(false)}
              >
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

                <div className="grid-cols-2">
                  <div className="form-group">
                    <label className="form-label">Chức vụ học thuật <span className="text-red">*</span></label>
                    <select
                      className="form-select font-semibold"
                      value={newAppointment.positionCode}
                      onChange={(e) =>
                        setNewAppointment({
                          ...newAppointment,
                          positionCode: e.target.value as PositionCode,
                        })
                      }
                    >
                      <option value="DEAN">Trưởng Khoa (DEAN)</option>
                      <option value="DEPARTMENT_HEAD">Trưởng Bộ Môn (DEPARTMENT_HEAD)</option>
                    </select>
                  </div>

                  <div className="form-group">
                    <label className="form-label">Khoa phụ trách <span className="text-red">*</span></label>
                    <select
                      className="form-select"
                      value={newAppointment.facultyId}
                      onChange={(e) =>
                        setNewAppointment({
                          ...newAppointment,
                          facultyId: Number(e.target.value),
                          departmentId: null,
                        })
                      }
                      required
                    >
                      {faculties.map((f) => (
                        <option key={f.id} value={f.id}>
                          {f.name} ({f.code})
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                {newAppointment.positionCode === 'DEPARTMENT_HEAD' && (
                  <div className="form-group">
                    <label className="form-label">Bộ môn phụ trách <span className="text-red">*</span></label>
                    <select
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
                    </select>
                  </div>
                )}

                <div className="grid-cols-2">
                  <div className="form-group">
                    <label className="form-label">Ngày bắt đầu <span className="text-red">*</span></label>
                    <input
                      type="datetime-local"
                      className="form-input"
                      value={newAppointment.startsAt}
                      onChange={(e) => setNewAppointment({ ...newAppointment, startsAt: e.target.value })}
                      required
                    />
                  </div>

                  <div className="form-group">
                    <label className="form-label">Ngày kết thúc (Không bắt buộc)</label>
                    <input
                      type="datetime-local"
                      className="form-input"
                      value={newAppointment.endsAt}
                      onChange={(e) => setNewAppointment({ ...newAppointment, endsAt: e.target.value })}
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
                  {submitting ? 'Đang tạo...' : 'Xác nhận Bổ nhiệm'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
