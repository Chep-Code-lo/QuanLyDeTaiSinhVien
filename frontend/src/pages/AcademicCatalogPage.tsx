import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { academicApi } from '../api/academic'
import { errorMessage } from '../api/student'
import { PageHeader } from '../components/PageHeader'
import { RemoteState } from '../components/RemoteState'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { useRemoteData } from '../hooks/useRemoteData'
import { canManageDepartment } from '../utils/managementAccess'
import type { FacultyResponse, DepartmentResponse } from '../types/domain'
import { formatDate } from '../utils/dateTime'
import { DateTimeInput } from '../components/DateTimeInput'

type CatalogKind = 'faculty' | 'department' | 'semester'
export function AcademicCatalogPage() {
  const { user } = useAuth()
  const admin = !!user?.roles.includes('ADMIN')
  const [revision, setRevision] = useState(0)
  const load = useCallback(async () => {
    const [faculties, departments, semesters] = await Promise.all([
      academicApi.getFaculties(),
      academicApi.getDepartments(),
      academicApi.getSemesters(),
    ])
    return { faculties, departments, semesters }
  }, [])
  const { data, loading, error } = useRemoteData(load, revision)
  const [editor, setEditor] = useState<{
    kind: CatalogKind
    item?: FacultyResponse | DepartmentResponse
  } | null>(null)
  const managedFaculties =
    data?.faculties.filter(
      (faculty) =>
        admin ||
        user?.activeAppointments.some(
          (appointment) =>
            appointment.positionCode === 'DEAN' && appointment.facultyId === faculty.id,
        ),
    ) ?? []
  return (
    <>
      <PageHeader title="Danh mục học thuật" />
      <RemoteState
        loading={loading}
        error={error}
        retry={() => setRevision((value) => value + 1)}
      />
      {data && (
        <>
          <section className="page-card academic-catalog-section">
            <h2>Khoa</h2>
            {admin && (
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => setEditor({ kind: 'faculty' })}
              >
                Thêm khoa
              </button>
            )}
            <div className="table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Mã khoa</th>
                    <th>Tên khoa</th>
                    <th>Trạng thái</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {data.faculties.map((item) => (
                    <tr key={item.id}>
                      <td>{item.code}</td>
                      <td>{item.name}</td>
                      <td>{item.active === false ? 'Ngừng hoạt động' : 'Hoạt động'}</td>
                      <td>
                        {admin && (
                          <button
                            type="button"
                            className="btn btn-secondary"
                            onClick={() => setEditor({ kind: 'faculty', item })}
                          >
                            Sửa khoa {item.code}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
          <section className="page-card academic-catalog-section">
            <h2>Bộ môn</h2>
            {managedFaculties.some((item) => item.active !== false) && (
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => setEditor({ kind: 'department' })}
              >
                Thêm bộ môn
              </button>
            )}
            <div className="table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Mã bộ môn</th>
                    <th>Tên bộ môn</th>
                    <th>Khoa</th>
                    <th>Trạng thái</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {data.departments.map((item) => (
                    <tr key={item.id}>
                      <td>{item.code}</td>
                      <td>{item.name}</td>
                      <td>{item.facultyName}</td>
                      <td>{item.active === false ? 'Ngừng hoạt động' : 'Hoạt động'}</td>
                      <td>
                        {(admin || canManageDepartment(user, item.id, item.facultyId)) && (
                          <button
                            type="button"
                            className="btn btn-secondary"
                            onClick={() => setEditor({ kind: 'department', item })}
                          >
                            Sửa bộ môn {item.code}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
          <section className="page-card academic-catalog-section">
            <h2>Học kỳ</h2>
            {admin && (
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => setEditor({ kind: 'semester' })}
              >
                Thêm học kỳ
              </button>
            )}
            <div className="table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Năm học</th>
                    <th>Học kỳ</th>
                    <th>Bắt đầu</th>
                    <th>Kết thúc</th>
                  </tr>
                </thead>
                <tbody>
                  {data.semesters.map((item) => (
                    <tr key={item.id}>
                      <td>{item.academicYear}</td>
                      <td>{item.semesterNo}</td>
                      <td>{item.startsOn ? formatDate(item.startsOn) : '—'}</td>
                      <td>{item.endsOn ? formatDate(item.endsOn) : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
      {editor && (
        <CatalogEditor
          key={`${editor.kind}:${editor.item?.id ?? 'new'}`}
          kind={editor.kind}
          item={editor.item}
          faculties={managedFaculties.filter((item) => item.active !== false)}
          onClose={() => setEditor(null)}
          onSaved={() => {
            setEditor(null)
            setRevision((value) => value + 1)
          }}
        />
      )}
    </>
  )
}
function CatalogEditor({
  kind,
  item,
  faculties,
  onClose,
  onSaved,
}: {
  kind: CatalogKind
  item?: FacultyResponse | DepartmentResponse
  faculties: FacultyResponse[]
  onClose: () => void
  onSaved: () => void
}) {
  const { showError, showSuccess } = useToast()
  const [name, setName] = useState(item?.name ?? '')
  const [code, setCode] = useState('')
  const [active, setActive] = useState(item?.active !== false)
  const [facultyId, setFacultyId] = useState(faculties[0]?.id ?? 0)
  const [academicYear, setAcademicYear] = useState('')
  const [semesterNo, setSemesterNo] = useState(1)
  const [startsOn, setStartsOn] = useState('')
  const [endsOn, setEndsOn] = useState('')
  const [busy, setBusy] = useState(false)
  const sending = useRef(false)
  const dialog = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const catalogName = { faculty: 'khoa', department: 'bộ môn', semester: 'học kỳ' }[kind]
  useEffect(() => {
    const element = dialog.current
    const trigger = document.activeElement as HTMLElement | null
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    element?.showModal()
    element?.querySelector<HTMLInputElement>('input:not([disabled])')?.focus()
    return () => {
      element?.close()
      document.body.style.overflow = previousOverflow
      if (trigger?.isConnected) trigger.focus()
    }
  }, [])
  return (
    <dialog
      ref={dialog}
      className="catalog-editor-dialog"
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault()
        if (!sending.current) onClose()
      }}
    >
      <div className="catalog-editor-dialog__heading">
        <h2 id={titleId}>
          {item ? `Chỉnh sửa ${catalogName} ${item.code}` : `Thêm ${catalogName}`}
        </h2>
        <button
          type="button"
          className="btn btn-secondary"
          disabled={busy}
          onClick={onClose}
          aria-label="Đóng biểu mẫu danh mục"
        >
          Đóng
        </button>
      </div>
      <form
        className="stack-form"
        onSubmit={async (event) => {
          event.preventDefault()
          if (
            sending.current ||
            (kind === 'semester'
              ? !academicYear.trim() || !startsOn || !endsOn || endsOn < startsOn
              : !name.trim() || (!item && !code.trim()))
          )
            return
          sending.current = true
          setBusy(true)
          try {
            if (kind === 'faculty') {
              if (item) await academicApi.updateFaculty(item.id, { name: name.trim(), active })
              else await academicApi.createFaculty({ code: code.trim(), name: name.trim() })
            }
            if (kind === 'department') {
              if (item) await academicApi.updateDepartment(item.id, { name: name.trim(), active })
              else
                await academicApi.createDepartment({
                  facultyId,
                  code: code.trim(),
                  name: name.trim(),
                })
            }
            if (kind === 'semester')
              await academicApi.createSemester({
                academicYear: academicYear.trim(),
                semesterNo,
                startsOn,
                endsOn,
              })
            showSuccess('Đã lưu danh mục học thuật.')
            onSaved()
          } catch (error) {
            showError(errorMessage(error))
          } finally {
            sending.current = false
            setBusy(false)
          }
        }}
      >
        <fieldset className="student-fieldset" disabled={busy}>
          {kind === 'semester' ? (
            <>
              <label>
                Năm học
                <input
                  className="form-input"
                  required
                  maxLength={20}
                  value={academicYear}
                  onChange={(event) => setAcademicYear(event.target.value)}
                  placeholder="2026-2027"
                />
              </label>
              <label>
                Số học kỳ
                <select
                  className="form-select"
                  value={semesterNo}
                  onChange={(event) => setSemesterNo(Number(event.target.value))}
                >
                  {[1, 2, 3].map((value) => (
                    <option key={value}>{value}</option>
                  ))}
                </select>
              </label>
              <label>
                Ngày bắt đầu
                <DateTimeInput
                  type="date"
                  className="form-input"
                  required
                  value={startsOn}
                  onValueChange={setStartsOn}
                />
              </label>
              <label>
                Ngày kết thúc
                <DateTimeInput
                  type="date"
                  className="form-input"
                  required
                  min={startsOn}
                  value={endsOn}
                  onValueChange={setEndsOn}
                />
              </label>
            </>
          ) : (
            <>
              {!item && (
                <>
                  <label>
                    Mã danh mục
                    <input
                      className="form-input"
                      required
                      maxLength={20}
                      value={code}
                      onChange={(event) => setCode(event.target.value)}
                    />
                  </label>
                  {kind === 'department' && (
                    <label>
                      Khoa quản lý
                      <select
                        className="form-select"
                        required
                        value={facultyId}
                        onChange={(event) => setFacultyId(Number(event.target.value))}
                      >
                        {faculties.map((faculty) => (
                          <option key={faculty.id} value={faculty.id}>
                            {faculty.name}
                          </option>
                        ))}
                      </select>
                    </label>
                  )}
                </>
              )}
              <label>
                Tên danh mục
                <input
                  className="form-input"
                  required
                  maxLength={150}
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                />
              </label>
              {item && (
                <label className="catalog-active-control">
                  <input
                    type="checkbox"
                    checked={active}
                    onChange={(event) => setActive(event.target.checked)}
                  />
                  <span>{active ? 'Đang hoạt động' : 'Ngừng hoạt động'}</span>
                </label>
              )}
            </>
          )}
          <div className="inline-form">
            <button className="btn btn-primary">{busy ? 'Đang lưu...' : 'Lưu danh mục'}</button>
            <button type="button" className="btn btn-secondary" onClick={onClose}>
              Hủy danh mục
            </button>
          </div>
        </fieldset>
      </form>
    </dialog>
  )
}
