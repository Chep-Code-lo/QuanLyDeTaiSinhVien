import React, { useEffect, useState } from 'react'
import { academicApi } from '../api/academic'
import { evaluationApi } from '../api/evaluation'
import { ConfirmModal } from '../components/ConfirmModal'
import { StatusBadge } from '../components/StatusBadge'
import { Stepper } from '../components/Stepper'
import { useToast } from '../context/ToastContext'
import type {
  AssessmentComponentResponse,
  AssessmentComponentType,
  CriterionRequest,
  RegistrationPeriodResponse,
  RubricResponse,
} from '../types/domain'

export const AssessmentSetupWizardPage: React.FC = () => {
  const { showSuccess, showError } = useToast()

  const [currentStep, setCurrentStep] = useState<number>(1)
  const [periods, setPeriods] = useState<RegistrationPeriodResponse[]>([])
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null)

  // Step 1 State: Assessment Component
  const [components, setComponents] = useState<AssessmentComponentResponse[]>([])
  const [selectedComponent, setSelectedComponent] = useState<AssessmentComponentResponse | null>(null)
  const [newComponentCode, setNewComponentCode] = useState('')
  const [newComponentName, setNewComponentName] = useState('')
  const [newComponentType, setNewComponentType] = useState<AssessmentComponentType>('REVIEW')
  const [newExpectedEvaluators, setNewExpectedEvaluators] = useState<number>(2)
  const [creatingComponent, setCreatingComponent] = useState(false)

  // Step 2 & 3 State: Rubric & Lock Rubric
  const [, setRubrics] = useState<RubricResponse[]>([])
  const [selectedRubric, setSelectedRubric] = useState<RubricResponse | null>(null)
  const [newRubricName, setNewRubricName] = useState('')
  const [criteria, setCriteria] = useState<CriterionRequest[]>([
    { name: 'Nội dung thực hiện', description: 'Đánh giá nội dung và kết quả đề tài', maxScore: 5.0, sortOrder: 1 },
    { name: 'Hình thức báo cáo', description: 'Trình bày tệp tài liệu và chuẩn hóa', maxScore: 5.0, sortOrder: 2 },
  ])
  const [creatingRubric, setCreatingRubric] = useState(false)

  // Confirm Modal state for locking
  const [confirmModal, setConfirmModal] = useState<{
    isOpen: boolean
    type: 'LOCK_RUBRIC' | 'LOCK_COMPONENT' | null
    title: string
    message: string
  }>({ isOpen: false, type: null, title: '', message: '' })
  const [locking, setLocking] = useState(false)

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

  // Load components when period changes
  useEffect(() => {
    if (!selectedPeriodId) return
    evaluationApi
      .listComponents(selectedPeriodId)
      .then((res) => {
        setComponents(res)
      })
      .catch(() => setComponents([]))
  }, [selectedPeriodId])

  // Load rubrics when selected component changes
  useEffect(() => {
    if (!selectedComponent) {
      setRubrics([])
      return
    }
    evaluationApi
      .listRubrics(selectedComponent.id)
      .then((res) => {
        setRubrics(res)
        if (res.length > 0) {
          const locked = res.find((r) => r.status === 'LOCKED')
          setSelectedRubric(locked || res[0])
        }
      })
      .catch(() => setRubrics([]))
  }, [selectedComponent])

  // Step 1: Create Component
  const handleCreateComponent = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedPeriodId) {
      showError('Vui lòng chọn đợt đăng ký')
      return
    }
    try {
      setCreatingComponent(true)
      const created = await evaluationApi.createComponent({
        periodId: selectedPeriodId,
        code: newComponentCode.trim(),
        name: newComponentName.trim(),
        componentType: newComponentType,
        expectedEvaluators: newExpectedEvaluators,
      })
      showSuccess('Tạo Assessment Component thành công!')
      setComponents((prev) => [...prev, created])
      setSelectedComponent(created)
      setCurrentStep(2)
      setNewComponentCode('')
      setNewComponentName('')
    } catch (err: any) {
      showError('Lỗi tạo Assessment Component: ' + (err.response?.data?.message || err.message))
    } finally {
      setCreatingComponent(false)
    }
  }

  // Step 2 Criteria helper
  const handleAddCriterion = () => {
    setCriteria((prev) => [
      ...prev,
      { name: '', description: '', maxScore: 2.0, sortOrder: prev.length + 1 },
    ])
  }

  const handleRemoveCriterion = (index: number) => {
    setCriteria((prev) => prev.filter((_, i) => i !== index))
  }

  const handleCriterionChange = (index: number, field: keyof CriterionRequest, value: any) => {
    setCriteria((prev) =>
      prev.map((c, i) => (i === index ? { ...c, [field]: value } : c))
    )
  }

  const totalCriteriaScore = criteria.reduce((sum, c) => sum + (Number(c.maxScore) || 0), 0)

  // Step 2: Create Rubric
  const handleCreateRubric = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!selectedComponent) {
      showError('Vui lòng chọn Assessment Component')
      return
    }

    if (totalCriteriaScore !== 10.0) {
      showError(`Tổng điểm các tiêu chí phải bằng đúng 10.0 điểm! (Hiện tại: ${totalCriteriaScore.toFixed(2)} điểm)`)
      return
    }

    try {
      setCreatingRubric(true)
      const created = await evaluationApi.createRubric({
        componentId: selectedComponent.id,
        name: newRubricName.trim() || `Rubric ${selectedComponent.name}`,
        criteria,
      })
      showSuccess('Tạo Rubric thành công!')
      setRubrics((prev) => [...prev, created])
      setSelectedRubric(created)
      setCurrentStep(3)
    } catch (err: any) {
      showError('Lỗi tạo Rubric: ' + (err.response?.data?.message || err.message))
    } finally {
      setCreatingRubric(false)
    }
  }

  // Step 3 & 4 Confirm Actions
  const openLockRubricModal = () => {
    if (!selectedRubric) return
    setConfirmModal({
      isOpen: true,
      type: 'LOCK_RUBRIC',
      title: 'Xác nhận KHÓA Rubric',
      message: `Bạn có chắc chắn muốn KHÓA Rubric "${selectedRubric.name}"? Khi đã Khóa, tiêu chí và điểm số sẽ cố định không thể chỉnh sửa!`,
    })
  }

  const openLockComponentModal = () => {
    if (!selectedComponent) return
    setConfirmModal({
      isOpen: true,
      type: 'LOCK_COMPONENT',
      title: 'Xác nhận KHÓA Assessment Component',
      message: `Bạn có chắc chắn muốn KHÓA Component "${selectedComponent.name}"? Khi đã Khóa, Component mới đủ điều kiện để nhận phân công Chấm điểm (Evaluator Assignment). Thao tác này không thể hoàn tác!`,
    })
  }

  const handleConfirmLock = async () => {
    try {
      setLocking(true)
      if (confirmModal.type === 'LOCK_RUBRIC' && selectedRubric) {
        const locked = await evaluationApi.lockRubric(selectedRubric.id)
        showSuccess('Đã KHÓA Rubric thành công 🔒!')
        setSelectedRubric(locked)
        setRubrics((prev) => prev.map((r) => (r.id === locked.id ? locked : r)))
        setCurrentStep(4)
      } else if (confirmModal.type === 'LOCK_COMPONENT' && selectedComponent) {
        const locked = await evaluationApi.lockComponent(selectedComponent.id)
        showSuccess('Đã KHÓA Assessment Component thành công 🔒!')
        setSelectedComponent(locked)
        setComponents((prev) => prev.map((c) => (c.id === locked.id ? locked : c)))
      }
    } catch (err: any) {
      showError('Lỗi khi khóa: ' + (err.response?.data?.message || err.message))
    } finally {
      setLocking(false)
      setConfirmModal({ isOpen: false, type: null, title: '', message: '' })
    }
  }

  const stepsList = [
    { number: 1, title: 'Tạo Assessment Component', description: 'Định nghĩa thành phần đánh giá' },
    { number: 2, title: 'Tạo Rubric', description: 'Thiết lập tiêu chí (Tổng điểm = 10.0)' },
    { number: 3, title: 'Khóa Rubric', description: 'Xác nhận cố định tiêu chí 🔒' },
    { number: 4, title: 'Khóa Component', description: 'Hoàn tất & Kích hoạt cho gán evaluator 🔒' },
  ]

  return (
    <div className="assessment-setup-page">
      <div className="page-header">
        <div>
          <span className="page-header__eyebrow">Quy trình Đánh giá</span>
          <h1>Setup Thành phần Đánh giá & Rubric (Wizard)</h1>
          <p>Thiết lập chuẩn theo 4 bước bắt buộc: Component ➔ Rubric ➔ Lock Rubric ➔ Lock Component.</p>
        </div>
      </div>

      {/* Stepper Wizard Indicator */}
      <Stepper steps={stepsList} currentStep={currentStep} />

      {/* Select Period & Component Toolbar */}
      <div className="page-card mb-6 grid-cols-2">
        <div className="form-group mb-0">
          <label className="form-label">Chọn Đợt đăng ký:</label>
          <select
            className="form-select font-semibold"
            value={selectedPeriodId ?? ''}
            onChange={(e) => setSelectedPeriodId(Number(e.target.value))}
          >
            {periods.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} ({p.facultyName})
              </option>
            ))}
          </select>
        </div>

        <div className="form-group mb-0">
          <label className="form-label">Chọn Component hiện có (nếu có):</label>
          <select
            className="form-select font-semibold"
            value={selectedComponent?.id ?? ''}
            onChange={(e) => {
              const found = components.find((c) => c.id === Number(e.target.value))
              if (found) {
                setSelectedComponent(found)
                if (found.status === 'LOCKED') setCurrentStep(4)
                else setCurrentStep(2)
              }
            }}
          >
            <option value="">-- Tạo Component mới (Bước 1) --</option>
            {components.map((c) => (
              <option key={c.id} value={c.id}>
                [{c.code}] {c.name} - {c.componentType} (Trạng thái: {c.status})
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* STEP 1: Create Component */}
      {currentStep === 1 && (
        <div className="page-card fade-in">
          <h2 className="font-semibold text-lg text-primary mb-4">
            Bước 1: Tạo Assessment Component mới
          </h2>
          <form onSubmit={handleCreateComponent} className="space-y-4">
            <div className="grid-cols-2">
              <div className="form-group">
                <label className="form-label">Mã Component (Code) <span className="text-red">*</span></label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="Ví dụ: PB_2026, HD_2026..."
                  value={newComponentCode}
                  onChange={(e) => setNewComponentCode(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label">Tên Component <span className="text-red">*</span></label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="Ví dụ: Đánh giá Phản biện đồ án..."
                  value={newComponentName}
                  onChange={(e) => setNewComponentName(e.target.value)}
                  required
                />
              </div>
            </div>

            <div className="grid-cols-2">
              <div className="form-group">
                <label className="form-label">Loại Component (Component Type) <span className="text-red">*</span></label>
                <select
                  className="form-select font-semibold"
                  value={newComponentType}
                  onChange={(e) => setNewComponentType(e.target.value as AssessmentComponentType)}
                >
                  <option value="REVIEW">REVIEW (Chấm phản biện / Đánh giá độc lập)</option>
                  <option value="COMMITTEE">COMMITTEE (Hội đồng chấm bảo vệ)</option>
                  <option value="OTHER">OTHER (Loại khác)</option>
                </select>
                <span className="text-xs text-secondary mt-1 block">
                  * COMMITTEE yêu cầu bắt buộc đính kèm Defense Session khi gán evaluator.
                </span>
              </div>

              <div className="form-group">
                <label className="form-label">Số lượng Evaluator dự kiến (1 - 5) <span className="text-red">*</span></label>
                <input
                  type="number"
                  min={1}
                  max={5}
                  className="form-input"
                  value={newExpectedEvaluators}
                  onChange={(e) => setNewExpectedEvaluators(Number(e.target.value))}
                  required
                />
              </div>
            </div>

            <div className="flex justify-end mt-4">
              <button type="submit" className="btn btn-primary" disabled={creatingComponent}>
                {creatingComponent ? 'Đang khởi tạo...' : 'Tiếp tục ➔ Bước 2 (Tạo Rubric)'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* STEP 2: Create Rubric */}
      {currentStep === 2 && selectedComponent && (
        <div className="page-card fade-in">
          <div className="flex justify-between items-center mb-4 border-b pb-3">
            <div>
              <h2 className="font-semibold text-lg text-primary">
                Bước 2: Tạo Rubric cho Component [{selectedComponent.code}] {selectedComponent.name}
              </h2>
              <p className="text-sm text-secondary">
                Yêu cầu: Tổng maxScore của các tiêu chí phải đúng <strong>10.0 điểm</strong>.
              </p>
            </div>
            <StatusBadge status={selectedComponent.status} />
          </div>

          <form onSubmit={handleCreateRubric} className="space-y-4">
            <div className="form-group">
              <label className="form-label">Tên Rubric <span className="text-red">*</span></label>
              <input
                type="text"
                className="form-input"
                placeholder="Ví dụ: Bảng điểm phản biện tiêu chuẩn 2026"
                value={newRubricName}
                onChange={(e) => setNewRubricName(e.target.value)}
                required
              />
            </div>

            {/* Criteria List */}
            <div className="form-group">
              <div className="flex justify-between items-center mb-2">
                <label className="form-label mb-0">Danh sách Tiêu chí Đánh giá (Criteria):</label>
                <button
                  type="button"
                  className="btn btn-outline btn-sm"
                  onClick={handleAddCriterion}
                >
                  + Thêm Tiêu chí
                </button>
              </div>

              <div className="space-y-3">
                {criteria.map((c, idx) => (
                  <div key={idx} className="p-3 border rounded-lg bg-gray-50 grid grid-cols-12 gap-3 items-center">
                    <div className="col-span-4">
                      <input
                        type="text"
                        className="form-input text-sm"
                        placeholder="Tên tiêu chí"
                        value={c.name}
                        onChange={(e) => handleCriterionChange(idx, 'name', e.target.value)}
                        required
                      />
                    </div>
                    <div className="col-span-5">
                      <input
                        type="text"
                        className="form-input text-sm"
                        placeholder="Mô tả tiêu chí"
                        value={c.description}
                        onChange={(e) => handleCriterionChange(idx, 'description', e.target.value)}
                        required
                      />
                    </div>
                    <div className="col-span-2">
                      <input
                        type="number"
                        step="0.1"
                        min="0.1"
                        max="10.0"
                        className="form-input text-sm font-semibold"
                        placeholder="Điểm max"
                        value={c.maxScore}
                        onChange={(e) => handleCriterionChange(idx, 'maxScore', Number(e.target.value))}
                        required
                      />
                    </div>
                    <div className="col-span-1 text-right">
                      {criteria.length > 1 && (
                        <button
                          type="button"
                          className="btn-clear text-red"
                          onClick={() => handleRemoveCriterion(idx)}
                          title="Xóa tiêu chí"
                        >
                          &times;
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>

              <div className="mt-3 flex justify-between items-center p-3 bg-blue-50 text-blue-900 rounded-lg">
                <span className="font-semibold text-sm">Tổng điểm hiện tại:</span>
                <span className={`font-bold text-base ${totalCriteriaScore === 10.0 ? 'text-green-700' : 'text-red-700'}`}>
                  {totalCriteriaScore.toFixed(2)} / 10.0 điểm
                  {totalCriteriaScore !== 10.0 && ' (Cần bằng đúng 10.0 điểm)'}
                </span>
              </div>
            </div>

            <div className="flex justify-between items-center mt-6">
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => setCurrentStep(1)}
              >
                ← Quay lại Bước 1
              </button>
              <button
                type="submit"
                className="btn btn-primary"
                disabled={creatingRubric || totalCriteriaScore !== 10.0}
              >
                {creatingRubric ? 'Đang lưu...' : 'Tiếp tục ➔ Bước 3 (Lock Rubric)'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* STEP 3: Lock Rubric */}
      {currentStep === 3 && selectedRubric && (
        <div className="page-card fade-in">
          <div className="flex justify-between items-center mb-4 border-b pb-3">
            <div>
              <h2 className="font-semibold text-lg text-primary">
                Bước 3: Xem lại & Khóa Rubric [{selectedRubric.name}]
              </h2>
              <p className="text-sm text-secondary">
                Kiểm tra các tiêu chí trước khi tiến hành Khóa (Lock). Khi đã khóa, Rubric mới có thể dùng để chấm điểm.
              </p>
            </div>
            <StatusBadge status={selectedRubric.status} />
          </div>

          <div className="table-container mb-4">
            <table className="data-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Tên tiêu chí</th>
                  <th>Mô tả</th>
                  <th>Điểm tối đa</th>
                </tr>
              </thead>
              <tbody>
                {selectedRubric.criteria?.map((cr, idx) => (
                  <tr key={cr.id || idx}>
                    <td>{idx + 1}</td>
                    <td className="font-semibold">{cr.name}</td>
                    <td>{cr.description}</td>
                    <td className="font-bold text-primary">{cr.maxScore} điểm</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex justify-between items-center mt-6">
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => setCurrentStep(2)}
              disabled={selectedRubric.status === 'LOCKED'}
            >
              ← Quay lại Bước 2
            </button>
            {selectedRubric.status === 'LOCKED' ? (
              <button
                type="button"
                className="btn btn-primary"
                onClick={() => setCurrentStep(4)}
              >
                Đã Khóa 🔒 ➔ Tiếp tục Bước 4 (Lock Component)
              </button>
            ) : (
              <button
                type="button"
                className="btn btn-danger"
                onClick={openLockRubricModal}
              >
                🔒 Khóa Rubric Ngay
              </button>
            )}
          </div>
        </div>
      )}

      {/* STEP 4: Lock Component */}
      {currentStep === 4 && selectedComponent && (
        <div className="page-card fade-in">
          <div className="flex justify-between items-center mb-4 border-b pb-3">
            <div>
              <h2 className="font-semibold text-lg text-primary">
                Bước 4: Hoàn tất & Khóa Assessment Component [{selectedComponent.name}]
              </h2>
              <p className="text-sm text-secondary">
                Khóa Component để sẵn sàng cho việc phân công Giảng viên chấm điểm (Evaluator Assignment).
              </p>
            </div>
            <StatusBadge status={selectedComponent.status} />
          </div>

          <div className="p-4 bg-gray-50 rounded-lg space-y-2 mb-6">
            <div><strong>Mã Component:</strong> {selectedComponent.code}</div>
            <div><strong>Loại Component:</strong> {selectedComponent.componentType}</div>
            <div><strong>Số người chấm dự kiến:</strong> {selectedComponent.expectedEvaluators} giảng viên</div>
            <div><strong>Rubric liên kết:</strong> {selectedRubric?.name ?? 'Đã khóa'}</div>
          </div>

          <div className="flex justify-between items-center">
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => setCurrentStep(3)}
            >
              ← Quay lại Bước 3
            </button>
            {selectedComponent.status === 'LOCKED' ? (
              <div className="flex items-center gap-3">
                <span className="badge badge-success text-sm py-2 px-3">
                  🔒 Assessment Component ĐÃ KHÓA & KÍCH HOẠT THÀNH CÔNG!
                </span>
              </div>
            ) : (
              <button
                type="button"
                className="btn btn-danger"
                onClick={openLockComponentModal}
              >
                🔒 Khóa Assessment Component Ngay
              </button>
            )}
          </div>
        </div>
      )}

      {/* Confirm Modal */}
      <ConfirmModal
        isOpen={confirmModal.isOpen}
        title={confirmModal.title}
        message={confirmModal.message}
        variant="danger"
        confirmText="Xác nhận Khóa 🔒"
        loading={locking}
        onConfirm={handleConfirmLock}
        onCancel={() => setConfirmModal({ isOpen: false, type: null, title: '', message: '' })}
      />
    </div>
  )
}
