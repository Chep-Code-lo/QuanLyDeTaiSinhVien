import { Fragment, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import {
  ArrowLeft,
  Check,
  CheckCircle2,
  FileText,
  Info,
  LockKeyhole,
  Plus,
  Trash2,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import { academicApi } from '../api/academic'
import { evaluationApi } from '../api/evaluation'
import { allPages } from '../api/student'
import { AnimatedSelect } from '../components/AnimatedSelect'
import { ConfirmModal } from '../components/ConfirmModal'
import { PageHeader } from '../components/PageHeader'
import { useAuth } from '../context/AuthContext'
import { canManageFaculty } from '../utils/managementAccess'
import { useToast } from '../context/ToastContext'
import { useRequestLoading } from '../hooks/useRequestLoading'
import { componentTypeLabel } from '../utils/displayLabels'
import {
  criterionScore,
  defaultAssessmentCriteria,
  fromApiCriteria,
  rubricTotal,
  toApiCriteria,
  validRubric,
} from '../utils/assessmentRubric'
import type { AssessmentCriterion } from '../utils/assessmentRubric'
import type {
  AssessmentComponentResponse,
  AssessmentComponentType,
  RegistrationPeriodResponse,
  RubricResponse,
} from '../types/domain'
import './AssessmentSetupWizardPage.css'

const steps = ['Tạo thành phần', 'Tạo bộ tiêu chí', 'Khóa bộ tiêu chí', 'Khóa thành phần']
const scoreLabel = (score: number) =>
  Number.isFinite(score) ? score.toLocaleString('vi-VN', { maximumFractionDigits: 1 }) : '—'
const errorMessage = (error: unknown) =>
  error instanceof Error ? error.message : 'Vui lòng thử lại.'

export function AssessmentSetupWizardPage() {
  const { user } = useAuth()
  const { showSuccess, showError } = useToast()
  const [step, setStep] = useState(1)
  const [periods, setPeriods] = useState<RegistrationPeriodResponse[]>([])
  const [periodId, setPeriodId] = useState<number | null>(null)
  const [component, setComponent] = useState<AssessmentComponentResponse | null>(null)
  const [components, setComponents] = useState<AssessmentComponentResponse[]>([])
  const [rubric, setRubric] = useState<RubricResponse | null>(null)
  const [name, setName] = useState('')
  const [componentType, setComponentType] = useState<AssessmentComponentType>('REVIEW')
  const [criteria, setCriteria] = useState<AssessmentCriterion[]>(defaultAssessmentCriteria)
  const [rubricName, setRubricName] = useState('')
  const [loadingPeriods, setLoadingPeriods] = useState(true)
  const [loadingRubric, setLoadingRubric] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [revision, setRevision] = useState(0)
  const [loadingComponents, setLoadingComponents] = useRequestLoading(
    JSON.stringify([periodId, revision]),
    !!periodId,
  )
  const [busy, setBusy] = useState(false)
  const [checkedRubric, setCheckedRubric] = useState(false)
  const [checkedComponent, setCheckedComponent] = useState(false)
  const [detailsVisible, setDetailsVisible] = useState(false)
  const [lockTarget, setLockTarget] = useState<'rubric' | 'component' | null>(null)

  const componentId = component?.id
  useEffect(() => {
    let active = true
    allPages((page) => academicApi.getRegistrationPeriods(undefined, page, 100))
      .then((response) => {
        if (!active) return
        setPeriods(response)
      })
      .catch((error: unknown) => {
        if (active) setLoadError(`Không tải được đợt đăng ký. ${errorMessage(error)}`)
      })
      .finally(() => {
        if (active) setLoadingPeriods(false)
      })
    return () => {
      active = false
    }
  }, [revision])

  useEffect(() => {
    if (!periodId) return
    let active = true
    evaluationApi
      .listComponents(periodId)
      .then((items) => {
        if (active) setComponents(items)
      })
      .catch((error) => {
        if (active) setLoadError(`Không tải được thành phần đánh giá. ${errorMessage(error)}`)
      })
      .finally(() => {
        if (active) setLoadingComponents(false)
      })
    return () => {
      active = false
    }
  }, [periodId, revision, setLoadingComponents])

  useEffect(() => {
    if (!componentId) return
    let active = true
    evaluationApi
      .listRubrics(componentId)
      .then((response) => {
        if (!active) return
        const selected = response.find((item) => item.status === 'LOCKED') || response[0] || null
        setRubric(selected)
        if (selected) {
          setCriteria(fromApiCriteria(selected.criteria))
          setRubricName(selected.name)
          setStep(selected.status === 'LOCKED' ? 4 : 2)
        } else {
          setStep(2)
        }
      })
      .catch((error: unknown) => {
        if (active) setLoadError(`Không tải được bộ tiêu chí. ${errorMessage(error)}`)
      })
      .finally(() => {
        if (active) setLoadingRubric(false)
      })
    return () => {
      active = false
    }
  }, [componentId, revision])

  const resetSelection = () => {
    setLoadingRubric(false)
    setLoadError('')
    setComponent(null)
    setRubric(null)
    setName('')
    setComponentType('REVIEW')
    setCriteria(defaultAssessmentCriteria())
    setRubricName('')
    setCheckedRubric(false)
    setCheckedComponent(false)
    setDetailsVisible(false)
    setStep(1)
  }

  const createComponent = async (event: FormEvent) => {
    event.preventDefault()
    if (busy) return
    if (component) {
      setStep(component.status === 'LOCKED' || rubric?.status === 'LOCKED' ? 4 : 2)
      return
    }
    if (!periodId || !name.trim()) {
      showError('Vui lòng chọn đợt đăng ký và nhập tên thành phần.')
      return
    }
    setBusy(true)
    try {
      const created = await evaluationApi.createComponent({
        periodId,
        name: name.trim(),
        code: `${componentType}_${Date.now().toString(36).toUpperCase()}`,
        componentType,
      })
      setComponent(created)
      setComponents((items) => [...items, created])
      setLoadingRubric(true)
      setStep(2)
      showSuccess('Đã tạo thành phần đánh giá.')
    } catch (error: unknown) {
      showError(errorMessage(error))
    } finally {
      setBusy(false)
    }
  }

  const total = rubricTotal(criteria)
  const isValid = validRubric(criteria)
  const rubricLocked = rubric?.status === 'LOCKED'
  const componentLocked = component?.status === 'LOCKED'
  const childCount = criteria.reduce((sum, item) => sum + item.children.length, 0)
  const savedCriteria = rubric ? fromApiCriteria(rubric.criteria) : null
  const dirty =
    !rubric ||
    rubricName.trim() !== rubric.name ||
    JSON.stringify(toApiCriteria(criteria)) !== JSON.stringify(toApiCriteria(savedCriteria!))
  const readyToLock = !!rubric && validRubric(savedCriteria || []) && !dirty
  const periodName =
    periods.find((period) => period.id === periodId)?.name || component?.periodName || '—'

  const saveRubric = async (advance: boolean) => {
    if (!component || !isValid || busy) return
    if (rubricLocked || !dirty) {
      if (advance) setStep(3)
      return
    }
    setBusy(true)
    try {
      const created = await evaluationApi.createRubric({
        componentId: component.id,
        name: rubricName.trim() || `Bộ tiêu chí ${component.name}`,
        criteria: toApiCriteria(criteria),
      })
      setRubric(created)
      setRubricName(created.name)
      setCriteria(fromApiCriteria(created.criteria))
      setCheckedRubric(false)
      if (advance) setStep(3)
      showSuccess('Đã lưu bộ tiêu chí.')
    } catch (error: unknown) {
      showError(errorMessage(error))
    } finally {
      setBusy(false)
    }
  }

  const confirmLock = async () => {
    if (busy) return
    setBusy(true)
    try {
      if (lockTarget === 'rubric' && rubric && checkedRubric && readyToLock) {
        setRubric(await evaluationApi.lockRubric(rubric.id))
        setStep(4)
        setCheckedComponent(false)
        showSuccess('Đã khóa bộ tiêu chí.')
      } else if (lockTarget === 'component' && component && checkedComponent && rubricLocked) {
        const locked = await evaluationApi.lockComponent(component.id)
        setComponent(locked)
        showSuccess('Đã khóa thành phần đánh giá. Sẵn sàng phân công giảng viên chấm.')
      }
    } catch (error: unknown) {
      showError(errorMessage(error))
    } finally {
      setBusy(false)
      setLockTarget(null)
    }
  }

  const updateCriterion = (
    groupIndex: number,
    childIndex: number | null,
    changes: Partial<AssessmentCriterion>,
  ) => {
    setCriteria((previous) =>
      previous.map((group, index) =>
        index !== groupIndex
          ? group
          : childIndex === null
            ? { ...group, ...changes }
            : {
                ...group,
                children: group.children.map((child, index) =>
                  index === childIndex ? { ...child, ...changes } : child,
                ),
              },
      ),
    )
    setCheckedRubric(false)
  }
  const addChild = (index: number) =>
    setCriteria((previous) =>
      previous.map((group, groupIndex) =>
        groupIndex !== index
          ? group
          : {
              ...group,
              score: 0,
              children: [
                ...group.children,
                { name: '', score: group.children.length ? 0 : group.score, children: [] },
              ],
            },
      ),
    )
  const removeCriterion = (groupIndex: number, childIndex: number | null) =>
    setCriteria((previous) =>
      childIndex === null
        ? previous.filter((_, index) => index !== groupIndex)
        : previous.map((group, index) =>
            index !== groupIndex
              ? group
              : {
                  ...group,
                  score: 0,
                  children: group.children.filter((_, index) => index !== childIndex),
                },
          ),
    )

  const contextBar = (
    <div className="assessment-context">
      <div>
        <span>Đợt:</span>
        <strong>{periodName}</strong>
      </div>
      <div>
        <span>Thành phần:</span>
        <strong>{component?.name}</strong>
      </div>
    </div>
  )

  const criteriaTable = (editable: boolean, expanded: boolean) => (
    <div className={`assessment-table-scroll${isValid ? ' assessment-table-scroll--valid' : ''}`}>
      <table className={`assessment-table ${editable ? 'assessment-table--editable' : ''}`}>
        <thead>
          <tr>
            <th scope="col">Mã</th>
            <th scope="col">{editable || expanded ? 'Tiêu chí' : 'Nhóm tiêu chí'}</th>
            <th scope="col">Điểm tối đa</th>
            {editable && (
              <th scope="col">
                <span className="assessment-sr-only">Thao tác</span>
              </th>
            )}
          </tr>
        </thead>
        <tbody>
          {criteria.map((group, groupIndex) => (
            <Fragment key={groupIndex}>
              <tr className={group.children.length ? 'assessment-table__group' : ''}>
                <td>TC{groupIndex + 1}</td>
                <td>
                  {editable ? (
                    <input
                      aria-label={`Tên tiêu chí TC${groupIndex + 1}`}
                      value={group.name}
                      maxLength={220}
                      placeholder="Nhập tên tiêu chí"
                      onChange={(event) =>
                        updateCriterion(groupIndex, null, { name: event.target.value })
                      }
                    />
                  ) : (
                    <>
                      {group.name}
                      {!expanded && group.children.length > 0 && (
                        <small>{group.children.length} tiêu chí con</small>
                      )}
                    </>
                  )}
                </td>
                <td>
                  {editable && !group.children.length ? (
                    <input
                      type="number"
                      min="0.1"
                      max="100"
                      step="0.1"
                      aria-label={`Điểm tối đa TC${groupIndex + 1}`}
                      value={Number.isNaN(group.score) ? '' : group.score}
                      onChange={(event) =>
                        updateCriterion(groupIndex, null, { score: event.target.valueAsNumber })
                      }
                    />
                  ) : (
                    scoreLabel(criterionScore(group))
                  )}
                </td>
                {editable && (
                  <td>
                    <div className="assessment-row-actions">
                      <button
                        type="button"
                        onClick={() => addChild(groupIndex)}
                        title="Thêm tiêu chí con"
                        aria-label={`Thêm tiêu chí con cho TC${groupIndex + 1}`}
                      >
                        <Plus size={15} />
                      </button>
                      <button
                        type="button"
                        onClick={() => removeCriterion(groupIndex, null)}
                        title="Xóa tiêu chí"
                        aria-label={`Xóa TC${groupIndex + 1}`}
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </td>
                )}
              </tr>
              {expanded &&
                group.children.map((child, childIndex) => (
                  <tr
                    key={childIndex}
                    className={`assessment-table__child ${childIndex === group.children.length - 1 ? 'assessment-table__child--last' : ''}`}
                  >
                    <td>
                      <span>
                        TC{groupIndex + 1}.{childIndex + 1}
                      </span>
                    </td>
                    <td>
                      {editable ? (
                        <input
                          aria-label={`Tên tiêu chí TC${groupIndex + 1}.${childIndex + 1}`}
                          value={child.name}
                          maxLength={220}
                          placeholder="Nhập tên tiêu chí con"
                          onChange={(event) =>
                            updateCriterion(groupIndex, childIndex, { name: event.target.value })
                          }
                        />
                      ) : (
                        child.name
                      )}
                    </td>
                    <td>
                      {editable ? (
                        <input
                          type="number"
                          min="0.1"
                          max="100"
                          step="0.1"
                          aria-label={`Điểm tối đa TC${groupIndex + 1}.${childIndex + 1}`}
                          value={Number.isNaN(child.score) ? '' : child.score}
                          onChange={(event) =>
                            updateCriterion(groupIndex, childIndex, {
                              score: event.target.valueAsNumber,
                            })
                          }
                        />
                      ) : (
                        scoreLabel(child.score)
                      )}
                    </td>
                    {editable && (
                      <td>
                        <div className="assessment-row-actions">
                          <button
                            type="button"
                            onClick={() => removeCriterion(groupIndex, childIndex)}
                            title="Xóa tiêu chí con"
                            aria-label={`Xóa TC${groupIndex + 1}.${childIndex + 1}`}
                          >
                            <Trash2 size={14} />
                          </button>
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
            </Fragment>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <th scope="row" colSpan={2}>
              Tổng điểm
            </th>
            <td colSpan={editable ? 2 : 1}>
              <div className="assessment-total">
                <strong>{scoreLabel(total)}</strong>
                {editable && (
                  <span className={isValid ? 'assessment-valid' : 'assessment-invalid'}>
                    {isValid && <CheckCircle2 size={17} />}
                    {isValid ? 'Hợp lệ' : '/ 100 điểm'}
                  </span>
                )}
              </div>
            </td>
          </tr>
        </tfoot>
      </table>
    </div>
  )

  const canConfigure =
    canManageFaculty(user, periods.find((period) => period.id === periodId)?.facultyId ?? 0)

  if (!canConfigure)
    return (
      <div className="assessment-setup-page">
        <PageHeader title="Tiêu chuẩn đánh giá" />
        <section className="page-card">
          <AnimatedSelect
            id="assessment-period"
            aria-label="Đợt đăng ký"
            value={periodId ?? ''}
            onChange={(event) => {
              setPeriodId(Number(event.target.value) || null)
              resetSelection()
            }}
          >
            <option value="">Chọn đợt đăng ký</option>
            {periods.map((period) => (
              <option key={period.id} value={period.id}>
                {period.name}
              </option>
            ))}
          </AnimatedSelect>
          <label htmlFor="assessment-existing">Thành phần đánh giá</label>
          <AnimatedSelect
            id="assessment-existing"
            value={component?.id ?? ''}
            onChange={(event) => {
              const saved = components.find((item) => item.id === Number(event.target.value))
              setComponent(saved ?? null)
              setRubric(null)
              setLoadingRubric(!!saved)
            }}
          >
            <option value="">Chọn thành phần đánh giá</option>
            {components.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </AnimatedSelect>
          {loadError && <p role="alert">{loadError}</p>}
          {(loadingPeriods || loadingRubric || loadingComponents) && (
            <p role="status">Đang tải cấu hình đánh giá…</p>
          )}
          {rubric && (
            <>
              <h2>{rubric.name}</h2>
              {criteriaTable(false, true)}
            </>
          )}
          {periodId && !loadingComponents && components.length === 0 && (
            <p>Chưa có thành phần đánh giá đã khóa.</p>
          )}
        </section>
      </div>
    )

  return (
    <div className="assessment-setup-page">
      <PageHeader title="Tiêu chuẩn đánh giá" />
      <ol className="assessment-steps" aria-label="Các bước thiết lập đánh giá">
        {steps.map((title, index) => (
          <li
            key={title}
            className={`${step === index + 1 ? 'is-current' : ''} ${step > index + 1 || componentLocked ? 'is-complete' : ''}`}
            aria-current={step === index + 1 ? 'step' : undefined}
          >
            <span className="assessment-steps__circle">
              {step > index + 1 || componentLocked ? (
                <Check size={25} strokeWidth={2} />
              ) : (
                index + 1
              )}
            </span>
            <span className="assessment-steps__title">{title}</span>
          </li>
        ))}
      </ol>
      {loadError && (
        <div className="assessment-alert assessment-alert--warning" role="alert">
          <Info size={22} />
          <div>{loadError}</div>
          <button
            className="assessment-text-button"
            type="button"
            onClick={() => {
              setLoadError('')
              setLoadingPeriods(true)
              setLoadingRubric(!!componentId)
              setRevision((value) => value + 1)
            }}
          >
            Thử lại
          </button>
        </div>
      )}
      {loadingPeriods || loadingRubric || loadingComponents ? (
        <div className="assessment-card assessment-loading" role="status">
          Đang tải cấu hình đánh giá…
        </div>
      ) : (
        <>
          {step === 1 && (
            <section className="assessment-card" aria-labelledby="assessment-step-title">
              <div className="assessment-card__heading">
                <h2 id="assessment-step-title">Tạo thành phần đánh giá</h2>
                <span className="assessment-step-badge">Bước 1/4</span>
              </div>
              <form onSubmit={createComponent}>
                <div className="assessment-fields">
                  <div className="assessment-field">
                    <label htmlFor="assessment-period">Đợt đăng ký</label>
                    <AnimatedSelect
                      id="assessment-period"
                      value={periodId ?? ''}
                      disabled={busy}
                      onChange={(event) => {
                        resetSelection()
                        setPeriodId(Number(event.target.value) || null)
                      }}
                    >
                      {!periods.length && <option value="">Chưa có đợt đăng ký</option>}
                      {periods.map((period) => (
                        <option key={period.id} value={period.id}>
                          {period.name}
                        </option>
                      ))}
                    </AnimatedSelect>
                  </div>
                  {!component && !loadingComponents && components.length > 0 && (
                    <div className="assessment-field">
                      <label htmlFor="assessment-existing">Tiếp tục cấu hình đã lưu</label>
                      <AnimatedSelect
                        id="assessment-existing"
                        value=""
                        disabled={busy}
                        onChange={(event) => {
                          const saved = components.find(
                            (item) => item.id === Number(event.target.value),
                          )
                          if (!saved) return
                          setLoadError('')
                          setRubric(null)
                          setCheckedRubric(false)
                          setCheckedComponent(false)
                          setComponent(saved)
                          setName(saved.name)
                          setComponentType(saved.componentType)
                          setLoadingRubric(true)
                        }}
                      >
                        <option value="">Tạo mới hoặc chọn thành phần đã lưu</option>
                        {components.map((item) => (
                          <option key={item.id} value={item.id}>
                            {item.name} · {item.status === 'LOCKED' ? 'Đã khóa' : 'Bản nháp'}
                          </option>
                        ))}
                      </AnimatedSelect>
                    </div>
                  )}
                  <div className="assessment-field">
                    <label htmlFor="assessment-name">
                      Tên thành phần <span>*</span>
                    </label>
                    <input
                      id="assessment-name"
                      value={name}
                      onChange={(event) => setName(event.target.value)}
                      required
                      maxLength={150}
                      disabled={!!component || busy}
                      placeholder="Ví dụ: Đánh giá phản biện đồ án"
                    />
                  </div>
                  <div className="assessment-field">
                    <label htmlFor="assessment-type">
                      Hình thức <span>*</span>
                    </label>
                    <AnimatedSelect
                      id="assessment-type"
                      value={componentType}
                      disabled={!!component || busy}
                      onChange={(event) => {
                        setComponentType(event.target.value as AssessmentComponentType)
                      }}
                    >
                      <option value="REVIEW">Phản biện độc lập</option>
                      <option value="COMMITTEE">Hội đồng chấm bảo vệ</option>
                      <option value="OTHER">Hình thức khác</option>
                    </AnimatedSelect>
                  </div>
                  {componentType === 'COMMITTEE' && (
                    <p className="assessment-help">
                      Khi phân công, thành phần này cần gắn với phiên bảo vệ và các thành viên của
                      hội đồng.
                    </p>
                  )}
                  <dl className="assessment-fixed-rule">
                    <dt>Cách tổng hợp điểm (cố định)</dt>
                    <dd>
                      Trung bình cộng điểm các giảng viên thực tế được phân công trong từng thành
                      phần. Điểm cuối là trung bình cộng điểm các thành phần.
                    </dd>
                  </dl>
                  {component && (
                    <p className="assessment-help">
                      Thành phần đã được lưu. Chọn “Tiếp tục” để xem cấu hình.
                    </p>
                  )}
                </div>
                <footer className="assessment-actions assessment-actions--end">
                  <button
                    className="assessment-button assessment-button--primary"
                    type="submit"
                    disabled={busy || !periodId || !!loadError}
                  >
                    {busy
                      ? 'Đang lưu…'
                      : componentLocked || rubricLocked
                        ? 'Tiếp tục: Khóa thành phần'
                        : 'Tiếp tục: Tạo bộ tiêu chí'}
                  </button>
                </footer>
              </form>
            </section>
          )}
          {step === 2 && component && (
            <section
              className="assessment-card assessment-card--table"
              aria-labelledby="assessment-step-title"
            >
              <div className="assessment-card__heading">
                <div>
                  <h2 id="assessment-step-title">Tạo bộ tiêu chí</h2>
                  <p>Bước 2/4</p>
                </div>
                {!rubricLocked && (
                  <button
                    className="assessment-button assessment-button--outline"
                    type="button"
                    disabled={busy}
                    onClick={() =>
                      setCriteria((previous) => [...previous, { name: '', score: 0, children: [] }])
                    }
                  >
                    <Plus size={20} />
                    Thêm tiêu chí
                  </button>
                )}
              </div>
              {contextBar}
              <fieldset className="assessment-editor" disabled={busy || rubricLocked}>
                {criteriaTable(!rubricLocked, true)}
              </fieldset>
              {!isValid && (
                <p className="assessment-validation" role="status">
                  Điền đầy đủ tên tiêu chí, điểm từ 0,1 đến 100 (tối đa một chữ số thập phân) và
                  tổng điểm bằng 100.
                </p>
              )}
              <details className="assessment-advanced">
                <summary>Tên bộ tiêu chí (tùy chọn)</summary>
                <div className="assessment-field">
                  <label className="assessment-sr-only" htmlFor="assessment-rubric-name">
                    Tên bộ tiêu chí
                  </label>
                  <input
                    id="assessment-rubric-name"
                    value={rubricName}
                    maxLength={200}
                    disabled={busy || rubricLocked}
                    placeholder={`Bộ tiêu chí ${component.name}`}
                    onChange={(event) => setRubricName(event.target.value)}
                  />
                </div>
              </details>
              <footer className="assessment-actions">
                <button
                  type="button"
                  className="assessment-button assessment-button--outline"
                  disabled={busy}
                  onClick={() => setStep(1)}
                >
                  <ArrowLeft size={18} />
                  Quay lại
                </button>
                <div className="assessment-actions__right">
                  {!rubricLocked && (
                    <button
                      type="button"
                      className="assessment-button assessment-button--outline"
                      disabled={busy || !isValid || !dirty || !!loadError}
                      onClick={() => void saveRubric(false)}
                    >
                      {busy ? 'Đang lưu…' : 'Lưu bộ tiêu chí'}
                    </button>
                  )}
                  <button
                    type="button"
                    className="assessment-button assessment-button--primary"
                    disabled={busy || !isValid || !!loadError}
                    onClick={() => void saveRubric(true)}
                  >
                    Tiếp tục: Khóa bộ tiêu chí
                  </button>
                </div>
              </footer>
            </section>
          )}
          {step === 3 && rubric && (
            <section
              className="assessment-card assessment-card--table"
              aria-labelledby="assessment-step-title"
            >
              <div className="assessment-card__heading">
                <div>
                  <h2 id="assessment-step-title">Khóa bộ tiêu chí</h2>
                  <p>Bước 3/4</p>
                </div>
                <span
                  className={`assessment-status ${rubricLocked ? 'assessment-status--success' : ''}`}
                >
                  <LockKeyhole size={16} />
                  {rubricLocked ? 'Đã khóa' : 'Chưa khóa'}
                </span>
              </div>
              {contextBar}
              {criteriaTable(false, detailsVisible)}
              <button
                type="button"
                className="assessment-text-button assessment-detail-toggle"
                onClick={() => setDetailsVisible((previous) => !previous)}
              >
                <FileText size={16} />
                {detailsVisible ? 'Thu gọn tiêu chí con' : 'Xem chi tiết bộ tiêu chí'}
              </button>
              <div
                className={`assessment-alert ${readyToLock ? 'assessment-alert--success' : 'assessment-alert--warning'}`}
              >
                <CheckCircle2 size={20} />
                <strong>
                  {readyToLock
                    ? 'Tổng điểm hợp lệ · Đã có đủ tiêu chí'
                    : 'Bộ tiêu chí chưa hợp lệ. Quay lại để kiểm tra và lưu đủ 100 điểm.'}
                </strong>
              </div>
              {!rubricLocked && (
                <>
                  <div className="assessment-alert assessment-alert--warning">
                    <LockKeyhole size={21} />
                    <div>
                      <strong>Sau khi khóa bộ tiêu chí</strong>
                      <p>Cấu trúc tiêu chí và thang điểm sẽ không được chỉnh sửa trực tiếp.</p>
                    </div>
                  </div>
                  <label className="assessment-checkbox">
                    <input
                      type="checkbox"
                      checked={checkedRubric}
                      onChange={(event) => setCheckedRubric(event.target.checked)}
                      disabled={busy}
                    />
                    Tôi đã kiểm tra cấu trúc tiêu chí và thang điểm.
                  </label>
                </>
              )}
              <footer className="assessment-actions">
                <button
                  type="button"
                  className="assessment-button assessment-button--outline"
                  disabled={busy}
                  onClick={() => setStep(2)}
                >
                  <ArrowLeft size={18} />
                  Quay lại
                </button>
                {rubricLocked ? (
                  <button
                    type="button"
                    className="assessment-button assessment-button--primary"
                    onClick={() => setStep(4)}
                  >
                    Tiếp tục: Khóa thành phần
                  </button>
                ) : (
                  <button
                    type="button"
                    className="assessment-button assessment-button--primary"
                    disabled={busy || !checkedRubric || !readyToLock || !!loadError}
                    onClick={() => setLockTarget('rubric')}
                  >
                    Khóa bộ tiêu chí
                  </button>
                )}
              </footer>
            </section>
          )}
          {step === 4 && component && (
            <section className="assessment-card" aria-labelledby="assessment-step-title">
              <div className="assessment-card__heading">
                <h2 id="assessment-step-title">Khóa thành phần đánh giá</h2>
                <span className="assessment-step-badge">Bước 4/4</span>
              </div>
              <dl className="assessment-summary">
                <div>
                  <dt>Đợt đăng ký</dt>
                  <dd>
                    {periodName}
                    <span
                      className={`assessment-status ${componentLocked ? 'assessment-status--success' : ''}`}
                    >
                      {componentLocked ? 'Đã khóa' : 'Chờ xác nhận'}
                    </span>
                  </dd>
                </div>
                <div>
                  <dt>Tên thành phần</dt>
                  <dd>{component.name}</dd>
                </div>
                <div>
                  <dt>Hình thức đánh giá</dt>
                  <dd>{componentTypeLabel(component.componentType)}</dd>
                </div>
                <div>
                  <dt>Cách tổng hợp điểm</dt>
                  <dd>Trung bình cộng điểm các giảng viên đã được phân công.</dd>
                </div>
                <div>
                  <dt>Bộ tiêu chí</dt>
                  <dd>
                    {rubric
                      ? `${criteria.length} nhóm · ${childCount} tiêu chí con · ${scoreLabel(total)} điểm`
                      : 'Chưa có bộ tiêu chí'}
                    {rubric && (
                      <button
                        type="button"
                        className="assessment-text-button"
                        onClick={() => {
                          setDetailsVisible(true)
                          setStep(3)
                        }}
                      >
                        <FileText size={17} />
                        Xem bộ tiêu chí
                      </button>
                    )}
                  </dd>
                </div>
              </dl>
              <div
                className={`assessment-alert ${rubricLocked ? 'assessment-alert--success' : 'assessment-alert--warning'}`}
              >
                <CheckCircle2 size={30} />
                <div>
                  <strong>{rubricLocked ? 'Bộ tiêu chí đã khóa' : 'Bộ tiêu chí chưa khóa'}</strong>
                  <p>
                    {rubricLocked
                      ? 'Cấu trúc và thang điểm đã được xác nhận ở bước 3.'
                      : 'Vui lòng hoàn tất khóa bộ tiêu chí ở bước 3.'}
                  </p>
                </div>
              </div>
              <div
                className={`assessment-alert ${componentLocked ? 'assessment-alert--success' : 'assessment-alert--info'}`}
              >
                <Info size={30} />
                <div>
                  <strong>
                    {componentLocked
                      ? 'Sẵn sàng phân công giảng viên chấm'
                      : 'Xác nhận sẵn sàng phân công'}
                  </strong>
                  <p>
                    {componentLocked
                      ? 'Thành phần đánh giá đã được khóa và cấu hình hoàn tất.'
                      : 'Khóa thành phần để xác nhận cấu hình đã hoàn tất và sẵn sàng phân công giảng viên chấm.'}
                  </p>
                  {!componentLocked && (
                    <label className="assessment-checkbox">
                      <input
                        type="checkbox"
                        checked={checkedComponent}
                        onChange={(event) => setCheckedComponent(event.target.checked)}
                        disabled={busy || !rubricLocked}
                      />
                      Tôi xác nhận cấu hình đánh giá đã đầy đủ và chính xác.
                    </label>
                  )}
                </div>
              </div>
              <footer className="assessment-actions">
                <button
                  type="button"
                  className="assessment-button assessment-button--outline"
                  disabled={busy}
                  onClick={() => setStep(rubric ? 3 : 2)}
                >
                  <ArrowLeft size={18} />
                  Quay lại
                </button>
                {componentLocked ? (
                  <Link
                    className="assessment-button assessment-button--primary"
                    to="/evaluator-assignment"
                  >
                    Phân công giảng viên
                  </Link>
                ) : (
                  <button
                    type="button"
                    className="assessment-button assessment-button--primary"
                    disabled={busy || !checkedComponent || !rubricLocked || !!loadError}
                    onClick={() => setLockTarget('component')}
                  >
                    Khóa thành phần đánh giá
                  </button>
                )}
              </footer>
            </section>
          )}
        </>
      )}
      <ConfirmModal
        isOpen={lockTarget !== null}
        title={
          lockTarget === 'rubric'
            ? 'Xác nhận khóa bộ tiêu chí'
            : 'Xác nhận khóa thành phần đánh giá'
        }
        message={
          lockTarget === 'rubric'
            ? `Khóa bộ tiêu chí “${rubric?.name}”? Cấu trúc và thang điểm sẽ không thể chỉnh sửa trực tiếp.`
            : `Khóa thành phần “${component?.name}” để phân công giảng viên chấm? Thao tác này không thể hoàn tác.`
        }
        confirmText={lockTarget === 'rubric' ? 'Khóa bộ tiêu chí' : 'Khóa thành phần'}
        variant="primary"
        loading={busy}
        onConfirm={() => void confirmLock()}
        onCancel={() => setLockTarget(null)}
      />
    </div>
  )
}
