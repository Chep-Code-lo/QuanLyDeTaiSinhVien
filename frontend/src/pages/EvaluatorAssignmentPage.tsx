import React, { useEffect, useState } from 'react'
import { academicApi } from '../api/academic'
import { evaluationApi } from '../api/evaluation'
import { LecturerAutocomplete } from '../components/LecturerAutocomplete'
import { StatusBadge } from '../components/StatusBadge'
import { useToast } from '../context/ToastContext'
import type {
  AssessmentComponentResponse,
  CreateEvaluationRequest,
  DefenseSessionResponse,
  EvaluationAssignmentResponse,
  ProjectAssignmentResponse,
  RegistrationPeriodResponse,
  RubricResponse,
} from '../types/domain'

export const EvaluatorAssignmentPage: React.FC = () => {
  const { showSuccess, showError } = useToast()

  const [periods, setPeriods] = useState<RegistrationPeriodResponse[]>([])
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null)

  // Assignments list state
  const [evaluationAssignments, setEvaluationAssignments] = useState<EvaluationAssignmentResponse[]>([])
  const [loadingAssignments, setLoadingAssignments] = useState(false)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)

  // Form State for Assigning Evaluator
  const [projectAssignments, setProjectAssignments] = useState<ProjectAssignmentResponse[]>([])
  const [selectedAssignmentId, setSelectedAssignmentId] = useState<number | null>(null)

  const [components, setComponents] = useState<AssessmentComponentResponse[]>([])
  const [selectedComponentId, setSelectedComponentId] = useState<number | null>(null)

  const [rubrics, setRubrics] = useState<RubricResponse[]>([])
  const [selectedRubricId, setSelectedRubricId] = useState<number | null>(null)

  const [selectedLecturerId, setSelectedLecturerId] = useState<number | null>(null)

  const [defenses, setDefenses] = useState<DefenseSessionResponse[]>([])
  const [selectedDefenseId, setSelectedDefenseId] = useState<number | null>(null)

  const [deadlineAt, setDeadlineAt] = useState('')
  const [submitting, setSubmitting] = useState(false)

  // Selected Component object
  const activeComponent = components.find((c) => c.id === selectedComponentId)
  const isCommittee = activeComponent?.componentType === 'COMMITTEE'

  // Load registration periods
  useEffect(() => {
    academicApi
      .getRegistrationPeriods()
      .then((res) => {
        setPeriods(res.content)
        if (res.content.length > 0) {
          setSelectedPeriodId(res.content[0].id)
        }
      })
      .catch((err) => {
        showError('Lỗi tải đợt đăng ký: ' + (err.response?.data?.message || err.message))
      })
  }, [showError])

  // Load evaluation assignments & dropdown options when selectedPeriodId changes
  useEffect(() => {
    if (!selectedPeriodId) return

    setLoadingAssignments(true)
    evaluationApi
      .listAssignments({ periodId: selectedPeriodId, page, size: 10 })
      .then((res) => {
        setEvaluationAssignments(res.content)
        setTotalPages(res.totalPages)
      })
      .catch((err) => {
        showError('Lỗi tải danh sách phân công: ' + (err.response?.data?.message || err.message))
      })
      .finally(() => setLoadingAssignments(false))

    // Load project assignments for dropdown
    academicApi.getProjectAssignments(selectedPeriodId, 0, 100).then((res) => {
      setProjectAssignments(res.content)
    })

    // Load components (only locked components can accept evaluators)
    evaluationApi.listComponents(selectedPeriodId).then((res) => {
      const lockedOnly = res.filter((c) => c.status === 'LOCKED')
      setComponents(lockedOnly)
    })

    // Load defense sessions
    evaluationApi.listDefenses({ periodId: selectedPeriodId, size: 100 }).then((res) => {
      setDefenses(res.content)
    })
  }, [selectedPeriodId, page, showError])

  // Load locked rubrics when selected component changes
  useEffect(() => {
    if (!selectedComponentId) {
      setRubrics([])
      setSelectedRubricId(null)
      return
    }
    evaluationApi.listRubrics(selectedComponentId).then((res) => {
      const lockedOnly = res.filter((r) => r.status === 'LOCKED')
      setRubrics(lockedOnly)
      if (lockedOnly.length > 0) {
        setSelectedRubricId(lockedOnly[0].id)
      }
    })
  }, [selectedComponentId])

  const handleAssignEvaluator = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedAssignmentId || !selectedRubricId || !selectedLecturerId || !deadlineAt) {
      showError('Vui lòng điền đầy đủ thông tin phân công')
      return
    }

    if (isCommittee && !selectedDefenseId) {
      showError('Component loại COMMITTEE yêu cầu BẮT BUỘC chọn Phiên bảo vệ (Defense Session)!')
      return
    }

    try {
      setSubmitting(true)
      const payload: CreateEvaluationRequest = {
        assignmentId: selectedAssignmentId,
        rubricId: selectedRubricId,
        lecturerId: selectedLecturerId,
        defenseId: isCommittee ? selectedDefenseId : null,
        deadlineAt: new Date(deadlineAt).toISOString(),
      }

      await evaluationApi.assign(payload)
      showSuccess('Gán Người chấm / Evaluator thành công!')

      // Refresh list
      const res = await evaluationApi.listAssignments({ periodId: selectedPeriodId!, page: 0, size: 10 })
      setEvaluationAssignments(res.content)
      setTotalPages(res.totalPages)
      setPage(0)

      // Reset form
      setSelectedLecturerId(null)
      setSelectedDefenseId(null)
      setDeadlineAt('')
    } catch (err: any) {
      showError('Lỗi gán evaluator: ' + (err.response?.data?.message || err.message))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="evaluator-assignment-page">
      <div className="page-header">
        <div>
          <span className="page-header__eyebrow">Phân công Chấm điểm</span>
          <h1>Gán Evaluator / Người Chấm Đồ Án</h1>
          <p>
            Phân công Giảng viên phản biện hoặc Thành viên hội đồng chấm đề tài. Tự động kiểm soát linh hoạt trường Defense theo loại Component.
          </p>
        </div>
      </div>

      {/* Select Period Filter Toolbar */}
      <div className="page-card mb-6">
        <div className="form-group mb-0">
          <label className="form-label">Chọn Đợt đăng ký học thuật:</label>
          <select
            className="form-select font-semibold"
            value={selectedPeriodId ?? ''}
            onChange={(e) => {
              setSelectedPeriodId(Number(e.target.value))
              setPage(0)
            }}
          >
            {periods.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} ({p.facultyName})
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Form Assign Evaluator */}
      <div className="page-card mb-6">
        <h2 className="font-semibold text-lg text-primary mb-4 border-b pb-2">
          + Phân công Người Chấm mới (Create Evaluation Assignment)
        </h2>

        <form onSubmit={handleAssignEvaluator} className="space-y-4">
          <div className="grid-cols-2">
            <div className="form-group">
              <label className="form-label">Đề tài / Dự án phân công <span className="text-red">*</span></label>
              <select
                className="form-select"
                value={selectedAssignmentId ?? ''}
                onChange={(e) => setSelectedAssignmentId(Number(e.target.value) || null)}
                required
              >
                <option value="">-- Chọn Đề tài / Dự án --</option>
                {projectAssignments.map((pa) => (
                  <option key={pa.id} value={pa.id}>
                    [{pa.topicCode}] {pa.topicTitle} - Nhóm: {pa.groupName}
                  </option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label className="form-label">Assessment Component (Đã khóa 🔒) <span className="text-red">*</span></label>
              <select
                className="form-select"
                value={selectedComponentId ?? ''}
                onChange={(e) => setSelectedComponentId(Number(e.target.value) || null)}
                required
              >
                <option value="">-- Chọn Assessment Component --</option>
                {components.map((c) => (
                  <option key={c.id} value={c.id}>
                    [{c.code}] {c.name} ({c.componentType})
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="grid-cols-2">
            <div className="form-group">
              <label className="form-label">Rubric Chấm điểm (Đã khóa 🔒) <span className="text-red">*</span></label>
              <select
                className="form-select"
                value={selectedRubricId ?? ''}
                onChange={(e) => setSelectedRubricId(Number(e.target.value) || null)}
                required
              >
                {rubrics.length === 0 ? (
                  <option value="">-- Vui lòng chọn Component đã khóa trước --</option>
                ) : (
                  rubrics.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.name} (v{r.versionNo})
                    </option>
                  ))
                )}
              </select>
            </div>

            <div className="form-group">
              <LecturerAutocomplete
                label="Giảng viên chấm (Evaluator)"
                required
                value={selectedLecturerId}
                onChange={(id) => setSelectedLecturerId(id)}
                placeholder="Tìm giảng viên chấm theo tên, mã..."
              />
            </div>
          </div>

          {/* DYNAMIC FIELD DEFENSE SESSION */}
          <div className="grid-cols-2">
            <div className="form-group">
              <label className="form-label">
                Phiên Bảo vệ (Defense Session){' '}
                {isCommittee ? (
                  <span className="text-red">* (Bắt buộc cho COMMITTEE)</span>
                ) : (
                  <span className="text-secondary">(Tắt cho REVIEW - payload defenseId = null)</span>
                )}
              </label>

              {isCommittee ? (
                <select
                  className="form-select border-primary"
                  value={selectedDefenseId ?? ''}
                  onChange={(e) => setSelectedDefenseId(Number(e.target.value) || null)}
                  required
                >
                  <option value="">-- Chọn Defense Session --</option>
                  {defenses.map((d) => (
                    <option key={d.id} value={d.id}>
                      Hội đồng: {d.committeeName} - Đề tài: {d.topicTitle} ({new Date(d.startsAt).toLocaleString('vi-VN')})
                    </option>
                  ))}
                </select>
              ) : (
                <input
                  type="text"
                  className="form-input bg-gray-100 text-secondary"
                  value="Tự động ẩn & gửi defenseId = null (Component Type: REVIEW / OTHER)"
                  disabled
                />
              )}
            </div>

            <div className="form-group">
              <label className="form-label">Hạn chót chấm điểm (Deadline) <span className="text-red">*</span></label>
              <input
                type="datetime-local"
                className="form-input"
                value={deadlineAt}
                onChange={(e) => setDeadlineAt(e.target.value)}
                required
              />
            </div>
          </div>

          <div className="flex justify-end pt-2">
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Đang phân công...' : ' Gán Evaluator'}
            </button>
          </div>
        </form>
      </div>

      {/* Evaluation Assignments Data Table */}
      <div className="page-card">
        <h2 className="font-semibold text-lg mb-4">
          Danh sách Phân công Chấm điểm Hiện tại ({evaluationAssignments.length})
        </h2>

        {loadingAssignments ? (
          <div className="text-center py-8">
            <div className="spinner" />
            <p className="text-secondary mt-2">Đang tải danh sách phân công...</p>
          </div>
        ) : evaluationAssignments.length === 0 ? (
          <p className="text-secondary text-center py-6">Chưa có phân công chấm điểm nào trong đợt này.</p>
        ) : (
          <>
            <div className="table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Đề tài / Dự án</th>
                    <th>Component</th>
                    <th>Rubric</th>
                    <th>Giảng viên chấm</th>
                    <th>Vai trò</th>
                    <th>Defense ID</th>
                    <th>Hạn chót</th>
                    <th>Trạng thái</th>
                  </tr>
                </thead>
                <tbody>
                  {evaluationAssignments.map((item, idx) => (
                    <tr key={item.id}>
                      <td>{page * 10 + idx + 1}</td>
                      <td>
                        <div className="font-semibold text-primary">{item.topicTitle}</div>
                        <div className="text-xs text-secondary">Nhóm: {item.groupName}</div>
                      </td>
                      <td>
                        <span className="font-semibold">{item.componentName}</span>
                        <div className="text-xs text-secondary">[{item.componentCode}]</div>
                      </td>
                      <td>{item.rubricName} (v{item.rubricVersionNo})</td>
                      <td className="font-semibold text-dark-blue">{item.lecturerName} ({item.lecturerCode})</td>
                      <td>
                        <span className="badge badge-info">{item.evaluatorRole}</span>
                      </td>
                      <td>{item.defenseId ? `#${item.defenseId}` : <span className="text-secondary">null</span>}</td>
                      <td className="text-xs">{new Date(item.deadlineAt).toLocaleString('vi-VN')}</td>
                      <td>
                        <StatusBadge status={item.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination Controls */}
            {totalPages > 1 && (
              <div className="flex justify-between items-center mt-4">
                <span className="text-sm text-secondary">
                  Trang {page + 1} / {totalPages}
                </span>
                <div className="flex gap-2">
                  <button
                    type="button"
                    className="btn btn-secondary btn-sm"
                    disabled={page === 0}
                    onClick={() => setPage((p) => p - 1)}
                  >
                    ← Trang trước
                  </button>
                  <button
                    type="button"
                    className="btn btn-secondary btn-sm"
                    disabled={page >= totalPages - 1}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    Trang sau →
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
