import React, { useEffect, useState } from 'react'
import { academicApi } from '../api/academic'
import { StatusBadge } from '../components/StatusBadge'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import type {
  CreateRegistrationPeriodRequest,
  DepartmentResponse,
  FacultyResponse,
  RegistrationPeriodResponse,
  SemesterResponse,
} from '../types/domain'

export const AcademicManagementPage: React.FC = () => {
  const { user, hasRole, hasAppointment } = useAuth()
  const { showSuccess, showError } = useToast()

  const [activeTab, setActiveTab] = useState<'PERIODS' | 'FACULTIES' | 'DEPARTMENTS'>('PERIODS')

  // Registration Periods state
  const [periods, setPeriods] = useState<RegistrationPeriodResponse[]>([])
  const [loadingPeriods, setLoadingPeriods] = useState(false)
  const [isPeriodModalOpen, setIsPeriodModalOpen] = useState(false)
  const [submittingPeriod, setSubmittingPeriod] = useState(false)

  // Catalog state
  const [faculties, setFaculties] = useState<FacultyResponse[]>([])
  const [departments, setDepartments] = useState<DepartmentResponse[]>([])
  const [semesters, setSemesters] = useState<SemesterResponse[]>([])

  // Form State for Registration Period creation
  const [newPeriod, setNewPeriod] = useState<CreateRegistrationPeriodRequest>({
    facultyId: 0,
    semesterId: 0,
    name: '',
    startsAt: '',
    endsAt: '',
  })

  // Load catalogs
  useEffect(() => {
    academicApi.getFaculties().then(setFaculties)
    academicApi.getDepartments().then(setDepartments)
    academicApi.getSemesters().then((res) => {
      setSemesters(res)
      if (res.length > 0) {
        setNewPeriod((prev) => ({ ...prev, semesterId: res[0].id }))
      }
    })
  }, [])

  // Set default faculty for period form based on faculty selection or user appointment
  useEffect(() => {
    if (faculties.length > 0 && newPeriod.facultyId === 0) {
      const userDeanAppt = user?.activeAppointments?.find((a) => a.positionCode === 'DEAN')
      setNewPeriod((prev) => ({
        ...prev,
        facultyId: userDeanAppt ? userDeanAppt.facultyId : faculties[0].id,
      }))
    }
  }, [faculties, user])

  // Load registration periods
  const loadRegistrationPeriods = () => {
    setLoadingPeriods(true)
    academicApi
      .getRegistrationPeriods()
      .then((res) => setPeriods(res.content))
      .catch((err) => showError('Lỗi tải đợt đăng ký: ' + (err.response?.data?.message || err.message)))
      .finally(() => setLoadingPeriods(false))
  }

  useEffect(() => {
    loadRegistrationPeriods()
  }, [])

  const handleCreatePeriod = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newPeriod.name.trim() || !newPeriod.startsAt || !newPeriod.endsAt) {
      showError('Vui lòng điền đầy đủ thông tin đợt đăng ký')
      return
    }

    try {
      setSubmittingPeriod(true)
      const created = await academicApi.createRegistrationPeriod({
        facultyId: Number(newPeriod.facultyId),
        semesterId: Number(newPeriod.semesterId),
        name: newPeriod.name.trim(),
        startsAt: new Date(newPeriod.startsAt).toISOString(),
        endsAt: new Date(newPeriod.endsAt).toISOString(),
      })
      showSuccess('Tạo Đợt đăng ký thành công (DRAFT)!')
      setPeriods((prev) => [created, ...prev])
      setIsPeriodModalOpen(false)
      setNewPeriod({
        facultyId: faculties[0]?.id || 0,
        semesterId: semesters[0]?.id || 0,
        name: '',
        startsAt: '',
        endsAt: '',
      })
    } catch (err: any) {
      showError('Lỗi tạo đợt đăng ký: ' + (err.response?.data?.message || err.message))
    } finally {
      setSubmittingPeriod(false)
    }
  }

  const handleChangePeriodStatus = async (id: number, currentStatus: string) => {
    const nextStatus = currentStatus === 'DRAFT' ? 'ACTIVE' : currentStatus === 'ACTIVE' ? 'CLOSED' : 'DRAFT'
    try {
      const updated = await academicApi.changeRegistrationPeriodStatus(id, nextStatus)
      showSuccess(`Đã cập nhật trạng thái đợt đăng ký sang ${nextStatus}!`)
      setPeriods((prev) => prev.map((p) => (p.id === id ? updated : p)))
    } catch (err: any) {
      showError('Lỗi đổi trạng thái: ' + (err.response?.data?.message || err.message))
    }
  }

  const isDean = hasAppointment('DEAN')
  const isAdmin = hasRole('ADMIN')

  return (
    <div className="academic-management-page">
      <div className="page-header">
        <div>
          <span className="page-header__eyebrow">Quản lý Học thuật</span>
          <h1>Chức năng Quản lý Học thuật (Academic Scope)</h1>
          <p>
            Dành cho Quản trị viên và Giảng viên đảm nhiệm vai trò{' '}
            <strong className="text-primary">Trưởng Khoa (DEAN)</strong> /{' '}
            <strong className="text-primary">Trưởng Bộ Môn (DEPARTMENT HEAD)</strong>.
          </p>
        </div>
        <div className="flex gap-2">
          {user?.activeAppointments?.map((apt) => (
            <span key={apt.id} className="badge badge-success text-sm py-2 px-3">
              Vị trí: {apt.positionCode === 'DEAN' ? 'Trưởng Khoa' : 'Trưởng Bộ Môn'}
            </span>
          ))}
        </div>
      </div>

      {/* Tabs Navigation */}
      <div className="flex gap-4 border-b border-gray-200 mb-6">
        <button
          type="button"
          className={`py-3 px-4 font-semibold text-sm border-b-2 transition-colors ${
            activeTab === 'PERIODS' ? 'border-primary text-primary' : 'border-transparent text-secondary'
          }`}
          onClick={() => setActiveTab('PERIODS')}
        >
          📅 Đợt Đăng Ký Đề Tài
        </button>
        <button
          type="button"
          className={`py-3 px-4 font-semibold text-sm border-b-2 transition-colors ${
            activeTab === 'FACULTIES' ? 'border-primary text-primary' : 'border-transparent text-secondary'
          }`}
          onClick={() => setActiveTab('FACULTIES')}
        >
          🏫 Danh mục Khoa ({faculties.length})
        </button>
        <button
          type="button"
          className={`py-3 px-4 font-semibold text-sm border-b-2 transition-colors ${
            activeTab === 'DEPARTMENTS' ? 'border-primary text-primary' : 'border-transparent text-secondary'
          }`}
          onClick={() => setActiveTab('DEPARTMENTS')}
        >
          📚 Danh mục Bộ môn ({departments.length})
        </button>
      </div>

      {/* TAB 1: REGISTRATION PERIODS */}
      {activeTab === 'PERIODS' && (
        <div className="page-card fade-in">
          <div className="flex justify-between items-center mb-4">
            <h2 className="font-semibold text-lg text-primary">Danh sách Đợt Đăng Ký Đề Tài ({periods.length})</h2>
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
                    <th>Khoa quản lý</th>
                    <th>Học kỳ</th>
                    <th>Thời gian bắt đầu</th>
                    <th>Thời gian kết thúc</th>
                    <th>Trạng thái</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {periods.map((p, idx) => (
                    <tr key={p.id}>
                      <td>{idx + 1}</td>
                      <td className="font-semibold text-primary">{p.name}</td>
                      <td>{p.facultyName}</td>
                      <td>{p.semesterName}</td>
                      <td className="text-xs">{new Date(p.startsAt).toLocaleString('vi-VN')}</td>
                      <td className="text-xs">{new Date(p.endsAt).toLocaleString('vi-VN')}</td>
                      <td>
                        <StatusBadge status={p.status} />
                      </td>
                      <td>
                        {(isAdmin || isDean) && (
                          <button
                            type="button"
                            className="btn btn-outline btn-sm"
                            onClick={() => handleChangePeriodStatus(p.id, p.status)}
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
      )}

      {/* TAB 2: FACULTIES */}
      {activeTab === 'FACULTIES' && (
        <div className="page-card fade-in">
          <h2 className="font-semibold text-lg text-primary mb-4">Danh sách Khoa đào tạo</h2>
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Mã Khoa</th>
                  <th>Tên Khoa</th>
                </tr>
              </thead>
              <tbody>
                {faculties.map((f, idx) => (
                  <tr key={f.id}>
                    <td>{idx + 1}</td>
                    <td className="font-semibold">{f.code}</td>
                    <td className="text-primary font-bold">{f.name}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* TAB 3: DEPARTMENTS */}
      {activeTab === 'DEPARTMENTS' && (
        <div className="page-card fade-in">
          <h2 className="font-semibold text-lg text-primary mb-4">Danh sách Bộ môn trực thuộc</h2>
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Mã Bộ môn</th>
                  <th>Tên Bộ môn</th>
                  <th>Trực thuộc Khoa</th>
                </tr>
              </thead>
              <tbody>
                {departments.map((d, idx) => (
                  <tr key={d.id}>
                    <td>{idx + 1}</td>
                    <td className="font-semibold">{d.code}</td>
                    <td className="text-primary font-bold">{d.name}</td>
                    <td>{d.facultyName}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

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
                  <label className="form-label">Tên Đợt Đăng Ký <span className="text-red">*</span></label>
                  <input
                    type="text"
                    className="form-input"
                    placeholder="Ví dụ: Đợt đăng ký Đồ án tốt nghiệp HK1 2026..."
                    value={newPeriod.name}
                    onChange={(e) => setNewPeriod({ ...newPeriod, name: e.target.value })}
                    required
                  />
                </div>

                <div className="grid-cols-2">
                  <div className="form-group">
                    <label className="form-label">Khoa phụ trách <span className="text-red">*</span></label>
                    <select
                      className="form-select"
                      value={newPeriod.facultyId}
                      onChange={(e) => setNewPeriod({ ...newPeriod, facultyId: Number(e.target.value) })}
                      required
                    >
                      {faculties.map((f) => (
                        <option key={f.id} value={f.id}>
                          {f.name} ({f.code})
                        </option>
                      ))}
                    </select>
                  </div>

                  <div className="form-group">
                    <label className="form-label">Học kỳ áp dụng <span className="text-red">*</span></label>
                    <select
                      className="form-select"
                      value={newPeriod.semesterId}
                      onChange={(e) => setNewPeriod({ ...newPeriod, semesterId: Number(e.target.value) })}
                      required
                    >
                      {semesters.map((s) => (
                        <option key={s.id} value={s.id}>
                          {s.name} - {s.academicYear}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>

                <div className="grid-cols-2">
                  <div className="form-group">
                    <label className="form-label">Thời gian Bắt đầu <span className="text-red">*</span></label>
                    <input
                      type="datetime-local"
                      className="form-input"
                      value={newPeriod.startsAt}
                      onChange={(e) => setNewPeriod({ ...newPeriod, startsAt: e.target.value })}
                      required
                    />
                  </div>

                  <div className="form-group">
                    <label className="form-label">Thời gian Kết thúc <span className="text-red">*</span></label>
                    <input
                      type="datetime-local"
                      className="form-input"
                      value={newPeriod.endsAt}
                      onChange={(e) => setNewPeriod({ ...newPeriod, endsAt: e.target.value })}
                      required
                    />
                  </div>
                </div>
              </div>

              <div className="modal-footer flex gap-3 justify-end">
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
