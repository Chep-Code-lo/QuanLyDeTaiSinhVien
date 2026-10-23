import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ChevronDown, FileText, Info, LockKeyhole, Plus, Search, Trash2, X } from 'lucide-react'
import { academicApi } from '../api/academic'
import { evaluationApi } from '../api/evaluation'
import { AnimatedSelect } from '../components/AnimatedSelect'
import { DateTimeInput } from '../components/DateTimeInput'
import { LecturerAutocomplete } from '../components/LecturerAutocomplete'
import { DefenseSchedulePanel } from '../components/DefenseSchedulePanel'
import { DefenseGradingDialog } from '../components/DefenseGradingDialog'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import { useToast } from '../context/ToastContext'
import { useAuth } from '../context/AuthContext'
import { academicAppointments, canManageFaculty } from '../utils/managementAccess'
import { formatDateTime } from '../utils/dateTime'
import { evaluatorRoleLabel } from '../utils/displayLabels'
import type { PageResponse } from '../types/api'
import type {
  AssessmentComponentResponse,
  AssessmentComponentType,
  DefenseSessionResponse,
  EvaluationAssignmentResponse,
  EvaluationCommitteeResponse,
  ProjectAssignmentResponse,
  RegistrationPeriodResponse,
  RubricResponse,
  ReviewProgressResponse,
} from '../types/domain'
import './EvaluatorAssignmentPage.css'

async function allPages<T>(fetchPage: (page: number) => Promise<PageResponse<T>>) {
  const first = await fetchPage(0)
  const items = [...first.content]
  for (let page = 1; page < first.totalPages; page++) items.push(...(await fetchPage(page)).content)
  return items
}

type MemberDraft = {
  key: number
  roleName: string
  lecturerId: number | null
  lecturerName?: string
  lecturerCode?: string
}
const initialMembers = (): MemberDraft[] =>
  ['Chủ tịch', 'Thư ký', 'Ủy viên'].map((roleName, index) => ({
    key: index + 1,
    roleName,
    lecturerId: null,
  }))

const roleLabels: Record<string, string> = {
  CHAIR: 'Chủ tịch',
  SECRETARY: 'Thư ký',
  MEMBER: 'Ủy viên',
}
const modeLabels = {
  REVIEW: 'Phản biện độc lập',
  COMMITTEE: 'Hội đồng bảo vệ',
  SCHEDULE: 'Lịch bảo vệ',
  OTHER: 'Hình thức khác',
}

export function EvaluatorAssignmentPage() {
  const { user } = useAuth()
  const { showSuccess, showError } = useToast()
  const [periods, setPeriods] = useState<RegistrationPeriodResponse[]>([])
  const [periodId, setPeriodId] = useState<number | null>(null)
  const [mode, setMode] = useState<AssessmentComponentType | 'SCHEDULE'>(() =>
    academicAppointments(user).some((a) => a.positionCode === 'DEAN') ? 'COMMITTEE' : 'REVIEW',
  )
  const [projects, setProjects] = useState<ProjectAssignmentResponse[]>([])
  const [components, setComponents] = useState<AssessmentComponentResponse[]>([])
  const [unlockedReviews, setUnlockedReviews] = useState<AssessmentComponentResponse[]>([])
  const [componentId, setComponentId] = useState<number | null>(null)
  const [rubrics, setRubrics] = useState<RubricResponse[]>([])
  const [rubricId, setRubricId] = useState<number | null>(null)
  const [defenses, setDefenses] = useState<DefenseSessionResponse[]>([])
  const [committees, setCommittees] = useState<EvaluationCommitteeResponse[]>([])
  const [committeeId, setCommitteeId] = useState<number | null>(null)
  const [committeeName, setCommitteeName] = useState('')
  const [memberDrafts, setMemberDrafts] = useState<MemberDraft[]>(initialMembers)
  const nextMemberKey = useRef(4)
  const [committeeTask, setCommitteeTask] = useState<'CONFIGURE' | 'ASSIGN'>('CONFIGURE')
  const [gradingDefense, setGradingDefense] = useState<DefenseSessionResponse | null>(null)
  const [scheduleCommitteeId, setScheduleCommitteeId] = useState<number | null>(null)
  const [lecturerId, setLecturerId] = useState<number | null>(null)
  const [projectIds, setProjectIds] = useState<number[]>([])
  const [deadline, setDeadline] = useState('')
  const [loading, setLoading] = useState(true)
  const [rubricsLoading, setRubricsLoading] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [rubricError, setRubricError] = useState('')
  const [reload, setReload] = useState(0)
  const [submitting, setSubmitting] = useState(false)
  const [scheduleBusy, setScheduleBusy] = useState(false)
  const [assignments, setAssignments] = useState<EvaluationAssignmentResponse[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [listLoading, setListLoading] = useState(false)
  const [listError, setListError] = useState('')
  const [listVersion, setListVersion] = useState(0)
  const progressKey = JSON.stringify([periodId, listVersion, reload])
  const [reviewProgress, setReviewProgress] = useState<{
    key: string
    data?: ReviewProgressResponse
    error?: string
  } | null>(null)
  const progress = reviewProgress?.key === progressKey ? reviewProgress : null
  useEffect(() => {
    if (!periodId) return
    let active = true
    evaluationApi
      .reviewProgress(periodId)
      .then((data) => {
        if (active) setReviewProgress({ key: progressKey, data })
      })
      .catch(() => {
        if (active)
          setReviewProgress({ key: progressKey, error: 'Không tải được tiến độ phản biện.' })
      })
    return () => {
      active = false
    }
  }, [periodId, progressKey])
  const [topicOpen, setTopicOpen] = useState(false)
  const [query, setQuery] = useState('')
  const picker = useRef<HTMLDivElement>(null)
  const rubricDialog = useRef<HTMLDialogElement>(null)
  const isCommittee = mode === 'COMMITTEE'
  const committee = committees.find((item) => item.id === committeeId)
  const activePeriod = periods.find((item) => item.id === periodId)
  const maxMembers = activePeriod?.maxCommitteeMembers ?? 5
  const minMembers = activePeriod?.minCommitteeMembers ?? 3
  const assigningTopics = !isCommittee || committeeTask === 'ASSIGN'
  const availableComponents = components.filter((item) => item.componentType === mode)
  const baseModes: (AssessmentComponentType | 'SCHEDULE')[] = components.some(
    (item) => item.componentType === 'OTHER',
  )
    ? ['REVIEW', 'COMMITTEE', 'SCHEDULE', 'OTHER']
    : ['REVIEW', 'COMMITTEE', 'SCHEDULE']
  const modes = baseModes.filter(
    (item) =>
      canManageFaculty(user, activePeriod?.facultyId ?? 0) || item === 'REVIEW' || item === 'OTHER',
  )
  const rubric = rubrics.find((item) => item.id === rubricId)
  const eligibleProjects = projects.filter(
    (project) =>
      !isCommittee ||
      defenses.some(
        (defense) =>
          defense.assignmentId === project.id &&
          defense.committeeId === committeeId &&
          defense.status === 'SCHEDULED',
      ),
  )
  const chosenProjects = eligibleProjects.filter((project) => projectIds.includes(project.id))
  const filteredProjects = eligibleProjects.filter((project) =>
    `${project.topicCode} ${project.topicTitle} ${project.groupName}`
      .toLocaleLowerCase('vi')
      .includes(query.trim().toLocaleLowerCase('vi')),
  )
  const matchingUnavailableProjects =
    isCommittee && !filteredProjects.length && query.trim()
      ? projects.filter((project) =>
          `${project.topicCode} ${project.topicTitle} ${project.groupName}`
            .toLocaleLowerCase('vi')
            .includes(query.trim().toLocaleLowerCase('vi')),
        )
      : []

  const periodKey = JSON.stringify([periodId, reload])
  const [previousPeriodKey, setPreviousPeriodKey] = useState(periodKey)
  if (previousPeriodKey !== periodKey) {
    setPreviousPeriodKey(periodKey)
    setScheduleCommitteeId(null)
    setGradingDefense(null)
    setLoading(!!periodId)
    setLoadError('')
    setProjects([])
    setComponents([])
    setDefenses([])
    setCommittees([])
    setUnlockedReviews([])
    setComponentId(null)
    setRubricId(null)
    setRubrics([])
    setCommitteeTask('CONFIGURE')
    setCommitteeId(null)
    setCommitteeName('')
    setMemberDrafts(initialMembers())
    setProjectIds([])
    setLecturerId(null)
    setDeadline('')
    setPage(0)
  }
  const [previousMode, setPreviousMode] = useState(mode)
  const [previousComponents, setPreviousComponents] = useState(components)
  if (previousMode !== mode || previousComponents !== components) {
    setPreviousMode(mode)
    setPreviousComponents(components)
    setComponentId(components.find((item) => item.componentType === mode)?.id ?? null)
    setProjectIds([])
    setCommitteeTask('CONFIGURE')
    setCommitteeId(null)
    setCommitteeName('')
    setMemberDrafts(initialMembers())
    setRubricId(null)
    setLecturerId(null)
    setTopicOpen(false)
    setQuery('')
  }
  const rubricKey = JSON.stringify([
    componentId,
    components.map((item) => [item.id, item.componentType]),
    isCommittee,
    reload,
  ])
  const [previousRubricKey, setPreviousRubricKey] = useState(rubricKey)
  if (previousRubricKey !== rubricKey) {
    setPreviousRubricKey(rubricKey)
    setRubrics([])
    setRubricError('')
    const hasComponents = isCommittee
      ? components.some((item) => item.componentType === 'COMMITTEE')
      : !!componentId
    setRubricsLoading(hasComponents)
    if (!hasComponents) setRubricId(null)
  }
  const listKey = JSON.stringify([periodId, page, listVersion])
  const [previousListKey, setPreviousListKey] = useState(listKey)
  if (previousListKey !== listKey) {
    setPreviousListKey(listKey)
    setListLoading(!!periodId)
    setListError('')
  }

  useEffect(() => {
    let live = true
    allPages((page) => academicApi.getRegistrationPeriods(undefined, page, 100))
      .then((result) => {
        if (!live) return
        const scoped = result.filter((period) =>
          academicAppointments(user).some((a) => a.facultyId === period.facultyId),
        )
        setPeriods(scoped)
        setPeriodId((current) =>
          scoped.some((item) => item.id === current) ? current : (scoped[0]?.id ?? null),
        )
        if (!scoped.length) setLoading(false)
      })
      .catch(() => {
        if (live) {
          setLoadError('Không tải được đợt đăng ký. Vui lòng thử lại.')
          setLoading(false)
        }
      })
    return () => {
      live = false
    }
  }, [reload, user])

  useEffect(() => {
    if (!periodId) return
    let live = true
    Promise.all([
      allPages((page) => academicApi.getProjectAssignments(periodId, page, 100)),
      evaluationApi.listComponents(periodId),
      allPages((page) => evaluationApi.listDefenses({ periodId, page, size: 100 })),
      allPages((page) => evaluationApi.listCommittees({ periodId, page, size: 100 })),
    ])
      .then(([projectList, componentList, defenseList, committeeList]) => {
        if (!live) return
        setProjects(projectList.filter((item) => item.status !== 'CANCELLED'))
        setComponents(componentList.filter((item) => item.status === 'LOCKED'))
        setUnlockedReviews(
          componentList.filter(
            (item) => item.componentType === 'REVIEW' && item.status !== 'LOCKED',
          ),
        )
        setDefenses(defenseList)
        setCommittees(committeeList)
      })
      .catch(() => {
        if (live) setLoadError('Không tải được dữ liệu phân công. Vui lòng thử lại.')
      })
      .finally(() => {
        if (live) setLoading(false)
      })
    return () => {
      live = false
    }
  }, [periodId, reload])

  useEffect(() => {
    let live = true
    const componentIds = isCommittee
      ? components.filter((item) => item.componentType === 'COMMITTEE').map((item) => item.id)
      : componentId
        ? [componentId]
        : []
    if (!componentIds.length) return
    Promise.all(componentIds.map((id) => evaluationApi.listRubrics(id)))
      .then((lists) => {
        if (!live) return
        const locked = lists.flat().filter((item) => item.status === 'LOCKED')
        setRubrics(locked)
        setRubricId((current) => (locked.some((item) => item.id === current) ? current : null))
      })
      .catch(() => {
        if (live) setRubricError('Không tải được bộ tiêu chí. Vui lòng thử lại.')
      })
      .finally(() => {
        if (live) setRubricsLoading(false)
      })
    return () => {
      live = false
    }
  }, [componentId, components, isCommittee, reload])

  useEffect(() => {
    if (!periodId) return
    let live = true
    evaluationApi
      .listAssignments({ periodId, page, size: 10 })
      .then((result) => {
        if (!live) return
        setAssignments(result.content)
        setTotalPages(result.totalPages)
        setTotalElements(result.totalElements)
      })
      .catch(() => {
        if (live) setListError('Không tải được danh sách phân công.')
      })
      .finally(() => {
        if (live) setListLoading(false)
      })
    return () => {
      live = false
    }
  }, [periodId, page, listVersion])

  useEffect(() => {
    if (!topicOpen) return
    const close = (event: PointerEvent) => {
      if (!picker.current?.contains(event.target as Node)) setTopicOpen(false)
    }
    document.addEventListener('pointerdown', close)
    return () => document.removeEventListener('pointerdown', close)
  }, [topicOpen])

  function selectCommittee(item?: EvaluationCommitteeResponse) {
    setCommitteeId(item?.id ?? null)
    setCommitteeName(item?.name ?? '')
    setMemberDrafts(
      item?.members.length
        ? [...item.members]
            .sort((a, b) => a.slotNo - b.slotNo)
            .map((member) => ({
              key: -member.slotNo,
              lecturerId: member.lecturerId,
              roleName: member.roleName || roleLabels[member.role],
              lecturerName: member.lecturerName,
              lecturerCode: member.lecturerCode,
            }))
        : initialMembers(),
    )
    setRubricId(item?.rubricId ?? null)
    setProjectIds([])
    setTopicOpen(false)
    setQuery('')
  }

  function resetForm() {
    selectCommittee()
    setCommitteeTask('CONFIGURE')
    setLecturerId(null)
    setDeadline('')
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const continueToSchedule =
      (event.nativeEvent as SubmitEvent).submitter?.getAttribute('value') === 'schedule'
    if (submitting) return
    if (
      !rubric ||
      (assigningTopics && (!chosenProjects.length || !deadline)) ||
      (!isCommittee && !lecturerId)
    ) {
      showError('Vui lòng chọn bộ tiêu chí và điền đầy đủ thông tin phân công.')
      return
    }
    if (
      isCommittee &&
      (!committeeName.trim() ||
        memberDrafts.some((member) => !member.roleName.trim() || !member.lecturerId))
    ) {
      showError('Nhập tên hội đồng, vai trò và chọn giảng viên cho từng thành viên.')
      return
    }
    if (isCommittee && (memberDrafts.length < minMembers || memberDrafts.length > maxMembers)) {
      showError('Số thành viên hội đồng phải từ ' + minMembers + ' đến ' + maxMembers + '.')
      return
    }
    const members = isCommittee ? memberDrafts.map((member) => member.lecturerId!) : [lecturerId!]
    if (new Set(members).size !== members.length) {
      showError('Mỗi giảng viên chỉ đảm nhiệm một vai trò trong hội đồng.')
      return
    }
    const due = assigningTopics ? new Date(deadline) : null
    if (due && (!Number.isFinite(due.getTime()) || due.getTime() <= new Date().getTime())) {
      showError('Hạn chót chấm điểm phải sau thời điểm hiện tại.')
      return
    }
    let saved = 0
    let skipped = 0
    let configurationSaved = false
    setSubmitting(true)
    try {
      if (isCommittee && !assigningTopics) {
        const updated = await evaluationApi.saveCommittee(committeeId, {
          periodId: periodId!,
          name: committeeName.trim(),
          rubricId: rubric.id,
          members: memberDrafts.map((member) => ({
            lecturerId: member.lecturerId!,
            roleName: member.roleName.trim(),
          })),
          version: committee?.version,
        })
        configurationSaved = true
        setCommittees((items) => [...items.filter((item) => item.id !== updated.id), updated])
        setCommitteeId(updated.id)
        setCommitteeName(updated.name)
        setDefenses((items) =>
          items.map((item) =>
            item.committeeId === updated.id ? { ...item, committeeName: updated.name } : item,
          ),
        )
        if (!assigningTopics) {
          showSuccess(
            'Đã lưu hội đồng ' + updated.name + ' với ' + updated.members.length + ' thành viên.',
          )
          if (continueToSchedule) {
            setScheduleCommitteeId(updated.id)
            setMode('SCHEDULE')
          }
          return
        }
      }
      const requests = chosenProjects.flatMap((project) =>
        members.map((memberId) => {
          const defense = isCommittee
            ? defenses.find(
                (item) =>
                  item.assignmentId === project.id &&
                  item.committeeId === committeeId &&
                  item.status === 'SCHEDULED',
              )
            : null
          return {
            assignmentId: project.id,
            rubricId: rubric.id,
            lecturerId: memberId,
            defenseId: defense?.id ?? null,
            deadlineAt: due!.toISOString(),
          }
        }),
      )
      if (requests.length > 100)
        throw new Error(
          'Mỗi lần chỉ phân công tối đa 100 giảng viên/đề tài. Vui lòng chọn ít đề tài hơn.',
        )
      const result = await evaluationApi.assignBatch(requests)
      saved = result.assignedCount
      skipped = result.skippedCount
      showSuccess(
        'Đã lưu ' +
          saved +
          ' phân công' +
          (skipped ? '; bỏ qua ' + skipped + ' phân công đã có.' : '.'),
      )
      setProjectIds([])
      setDeadline('')
      setLecturerId(null)
    } catch (error: any) {
      showError(
        (configurationSaved ? 'Đã lưu cấu hình hội đồng. ' : '') +
          (saved ? 'Đã lưu ' + saved + ' phân công. ' : '') +
          (error.response?.data?.message || error.message || 'Không thể lưu phân công.'),
      )
    } finally {
      setSubmitting(false)
      setPage(0)
      setListVersion((value) => value + 1)
    }
  }

  return (
    <div className="evaluator-assignment-page">
      <PageHeader title="Phân công giảng viên" />
      <div className="ea-period">
        <label htmlFor="ea-period">Đợt đăng ký</label>
        <AnimatedSelect
          id="ea-period"
          value={periodId ?? ''}
          disabled={submitting || scheduleBusy || loading}
          onChange={(event) => setPeriodId(Number(event.target.value) || null)}
        >
          {!periods.length && <option value="">Chưa có đợt đăng ký</option>}
          {periods.map((item) => (
            <option key={item.id} value={item.id}>
              {item.name}
            </option>
          ))}
        </AnimatedSelect>
      </div>
      <div className="ea-tabs" role="tablist" aria-label="Hình thức phân công">
        {modes.map((item) => (
          <button
            type="button"
            key={item}
            id={`ea-tab-${item}`}
            role="tab"
            aria-selected={mode === item}
            tabIndex={mode === item ? 0 : -1}
            aria-controls="ea-panel"
            className={mode === item ? 'is-active' : ''}
            disabled={submitting || scheduleBusy}
            onClick={() => setMode(item)}
            onKeyDown={(event) => {
              if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
                const direction = event.key === 'ArrowRight' ? 1 : -1
                const next = modes[(modes.indexOf(item) + direction + modes.length) % modes.length]
                setMode(next)
                document.getElementById(`ea-tab-${next}`)?.focus()
              }
            }}
          >
            {modeLabels[item]}
          </button>
        ))}
      </div>
      {mode === 'SCHEDULE' ? (
        <div id="ea-panel" role="tabpanel" aria-labelledby="ea-tab-SCHEDULE">
          {loading ? (
            <p role="status">Đang tải dữ liệu lịch bảo vệ...</p>
          ) : loadError ? (
            <p role="alert">
              {loadError}
              <button type="button" onClick={() => setReload((value) => value + 1)}>
                Thử lại
              </button>
            </p>
          ) : (
            <DefenseSchedulePanel
              onAssign={setGradingDefense}
              initialCommitteeId={scheduleCommitteeId}
              onBusyChange={setScheduleBusy}
              key={periodId}
              committees={committees}
              projects={projects}
              defenses={defenses}
              periods={periods}
              unlockedReviews={unlockedReviews}
              onScheduled={async (defenseId) => {
                if (!periodId) return
                const [scheduled, saved] = await Promise.all([
                  allPages((page) => evaluationApi.listDefenses({ periodId, page, size: 100 })),
                  allPages((page) => evaluationApi.listCommittees({ periodId, page, size: 100 })),
                ])
                setDefenses(scheduled)
                setCommittees(saved)
                if (defenseId) {
                  const defense = scheduled.find((item) => item.id === defenseId)
                  if (!defense) throw new Error('Chưa tải được lịch vừa lưu.')
                  setGradingDefense(defense)
                }
              }}
            />
          )}
        </div>
      ) : (
        <form
          id="ea-panel"
          role="tabpanel"
          aria-labelledby={`ea-tab-${mode}`}
          className="ea-card"
          onSubmit={handleSubmit}
        >
          {loadError && (
            <div className="ea-notice ea-notice--error" role="alert">
              {loadError}
              <button type="button" onClick={() => setReload((value) => value + 1)}>
                Thử lại
              </button>
            </div>
          )}
          <fieldset disabled={submitting || loading || !!loadError || !periodId}>
            <h2>Thông tin phân công</h2>
            {isCommittee && (
              <div className="ea-task-switch" role="group" aria-label="Thao tác hội đồng">
                <button
                  type="button"
                  className="btn btn-secondary"
                  aria-pressed={committeeTask === 'CONFIGURE'}
                  onClick={() => {
                    selectCommittee(committee)
                    setCommitteeTask('CONFIGURE')
                  }}
                >
                  Cấu hình hội đồng
                </button>
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled={!committee}
                  aria-pressed={committeeTask === 'ASSIGN'}
                  onClick={() => {
                    selectCommittee(committee)
                    setCommitteeTask('ASSIGN')
                  }}
                >
                  Phân công chấm cho hội đồng đã lưu
                </button>
              </div>
            )}
            {loading && (
              <p className="ea-hint" role="status">
                Đang tải dữ liệu phân công...
              </p>
            )}
            {!loading && !loadError && !availableComponents.length && (
              <p className="ea-notice">
                <Info size={17} /> Chưa có thành phần đánh giá đã khóa cho hình thức này. Hãy thiết
                lập tại mục Tiêu chuẩn đánh giá.
              </p>
            )}
            {isCommittee && committees.length > 0 && (
              <div className="ea-field ea-saved-committee">
                <label htmlFor="ea-saved-committee">Hội đồng đã tạo</label>
                <AnimatedSelect
                  id="ea-saved-committee"
                  value={committeeId ?? ''}
                  onChange={(event) => {
                    setCommitteeTask('CONFIGURE')
                    selectCommittee(
                      committees.find((item) => item.id === Number(event.target.value)),
                    )
                  }}
                >
                  <option value="">Tạo hội đồng mới</option>
                  {committees.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name}
                    </option>
                  ))}
                </AnimatedSelect>
              </div>
            )}
            <fieldset disabled={isCommittee && assigningTopics}>
              <div className="ea-grid">
                {isCommittee ? (
                  <>
                    <div className="ea-field">
                      <label htmlFor="ea-committee">
                        Hội đồng <em>*</em>
                      </label>
                      <input
                        id="ea-committee"
                        className="form-input"
                        value={committeeName}
                        required
                        maxLength={200}
                        onChange={(event) => setCommitteeName(event.target.value)}
                        placeholder="Ví dụ: Hội Đồng 1"
                      />
                    </div>
                  </>
                ) : (
                  <>
                    <div className="ea-field">
                      <label htmlFor="ea-component">
                        Thành phần đánh giá <em>*</em>
                      </label>
                      <AnimatedSelect
                        id="ea-component"
                        value={componentId ?? ''}
                        required
                        onChange={(event) => setComponentId(Number(event.target.value) || null)}
                      >
                        <option value="">Chọn thành phần đánh giá</option>
                        {availableComponents.map((item) => (
                          <option key={item.id} value={item.id}>
                            {item.name}
                          </option>
                        ))}
                      </AnimatedSelect>
                    </div>
                    <div className="ea-field">
                      <LecturerAutocomplete
                        label={mode === 'REVIEW' ? 'Giảng viên phản biện' : 'Giảng viên chấm'}
                        value={lecturerId}
                        onChange={setLecturerId}
                        required
                        placeholder="Tìm và chọn giảng viên"
                      />
                    </div>
                  </>
                )}
              </div>
              {isCommittee && (
                <section className="ea-members">
                  <div className="ea-members-heading">
                    <h2>Thành viên hội đồng</h2>
                    <button
                      type="button"
                      className="btn ea-add-member"
                      disabled={memberDrafts.length >= maxMembers}
                      onClick={() =>
                        setMemberDrafts((items) => [
                          ...items,
                          { key: nextMemberKey.current++, roleName: 'Ủy viên', lecturerId: null },
                        ])
                      }
                    >
                      <Plus size={16} /> Thêm thành viên
                    </button>
                  </div>
                  <datalist id="ea-role-suggestions">
                    <option value="Chủ tịch" />
                    <option value="Thư ký" />
                    <option value="Ủy viên" />
                  </datalist>
                  <div className="ea-members-table ea-members-table--editable">
                    <div className="ea-member-head">
                      <span>Vai trò</span>
                      <span>Giảng viên được phân công</span>
                      <span />
                    </div>
                    {memberDrafts.map((member, index) => (
                      <div className="ea-member-row" key={member.key}>
                        <div className="ea-member-role">
                          <input
                            className="form-input"
                            list="ea-role-suggestions"
                            aria-label={'Vai trò thành viên ' + (index + 1)}
                            value={member.roleName}
                            required
                            maxLength={100}
                            placeholder="Nhập vai trò"
                            onChange={(event) =>
                              setMemberDrafts((items) =>
                                items.map((item) =>
                                  item.key === member.key
                                    ? { ...item, roleName: event.target.value }
                                    : item,
                                ),
                              )
                            }
                          />
                        </div>
                        <div className="ea-member-lecturer">
                          <LecturerAutocomplete
                            value={member.lecturerId}
                            inputId={'ea-member-' + member.key}
                            ariaLabel={'Giảng viên thành viên ' + (index + 1)}
                            selectedOption={
                              member.lecturerId && member.lecturerName
                                ? {
                                    id: member.lecturerId,
                                    fullName: member.lecturerName,
                                    lecturerCode: member.lecturerCode ?? '',
                                  }
                                : undefined
                            }
                            excludedIds={memberDrafts
                              .filter((item) => item.key !== member.key && item.lecturerId)
                              .map((item) => item.lecturerId!)}
                            onChange={(id, lecturer) =>
                              setMemberDrafts((items) =>
                                items.map((item) =>
                                  item.key === member.key
                                    ? {
                                        ...item,
                                        lecturerId: id,
                                        lecturerName: lecturer?.fullName,
                                        lecturerCode: lecturer?.lecturerCode,
                                      }
                                    : item,
                                ),
                              )
                            }
                            placeholder="Tìm giảng viên theo tên hoặc mã..."
                            required
                          />
                        </div>
                        <button
                          type="button"
                          className="ea-remove-member"
                          aria-label={'Xóa thành viên ' + (index + 1)}
                          disabled={memberDrafts.length <= minMembers}
                          onClick={() =>
                            setMemberDrafts((items) =>
                              items.filter((item) => item.key !== member.key),
                            )
                          }
                        >
                          <Trash2 size={17} />
                        </button>
                      </div>
                    ))}
                  </div>
                  <p className="ea-hint">
                    Mỗi giảng viên chỉ đảm nhiệm một vai trò. Hội đồng cần một Chủ tịch, một Thư ký
                    và từ {minMembers} đến {maxMembers} thành viên theo đợt đăng ký.
                  </p>
                </section>
              )}
              <section className="ea-rubric">
                <label className="ea-section-label" htmlFor="ea-rubric">
                  Bộ tiêu chí <em>*</em>
                </label>
                <AnimatedSelect
                  id="ea-rubric"
                  value={rubricId ?? ''}
                  required
                  disabled={rubricsLoading}
                  onChange={(event) => setRubricId(Number(event.target.value) || null)}
                >
                  <option value="">
                    {rubricsLoading ? 'Đang tải bộ tiêu chí...' : 'Chọn bộ tiêu chí đã tạo'}
                  </option>
                  {rubrics.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name} · Phiên bản {item.versionNo}
                    </option>
                  ))}
                </AnimatedSelect>
                {!rubricsLoading && !rubrics.length && (
                  <p className="ea-hint">
                    Chưa có bộ tiêu chí phù hợp đã khóa. Tạo và khóa bộ tiêu chí tại mục Tiêu chuẩn
                    đánh giá trước khi phân công.
                  </p>
                )}
                <div className="ea-rubric-bar">
                  <FileText size={24} aria-hidden="true" />
                  <span className="ea-rubric-name">
                    {rubricsLoading && componentId
                      ? 'Đang tải bộ tiêu chí...'
                      : rubric
                        ? `${rubric.name} — ${Math.round(rubric.criteria.reduce((sum, item) => sum + Number(item.maxScore), 0) * 100) / 10} điểm`
                        : rubricError || 'Chưa có bộ tiêu chí đã khóa'}
                  </span>
                  {rubric && (
                    <span className="ea-locked">
                      <LockKeyhole size={12} />
                      Đã khóa
                    </span>
                  )}
                  {rubricError && (
                    <button type="button" onClick={() => setReload((value) => value + 1)}>
                      Thử lại
                    </button>
                  )}
                  <button
                    type="button"
                    disabled={!rubric}
                    className="ea-view-rubric"
                    onClick={() => rubricDialog.current?.showModal()}
                  >
                    <FileText size={18} />
                    Xem tiêu chí
                  </button>
                </div>
              </section>
            </fieldset>
            {assigningTopics ? (
              <>
                <div className="ea-field ea-topics">
                  <label id="ea-topics-label">
                    {isCommittee
                      ? 'Đề tài tham gia bảo vệ'
                      : mode === 'REVIEW'
                        ? 'Đề tài phân công phản biện'
                        : 'Đề tài phân công chấm'}{' '}
                    <em>*</em>
                  </label>
                  <div
                    ref={picker}
                    className="ea-topic-picker"
                    onKeyDown={(event) => {
                      if (event.key === 'Escape') {
                        setTopicOpen(false)
                        picker.current?.querySelector('button')?.focus()
                      }
                    }}
                  >
                    <button
                      type="button"
                      className="ea-topic-trigger"
                      aria-labelledby="ea-topics-label"
                      aria-expanded={topicOpen}
                      aria-controls="ea-topic-options"
                      disabled={isCommittee && !committee}
                      onClick={() => setTopicOpen(!topicOpen)}
                    >
                      <Search size={19} />
                      <span>
                        {chosenProjects.length
                          ? `Đã chọn ${chosenProjects.length} đề tài`
                          : 'Tìm và chọn một hoặc nhiều đề tài'}
                      </span>
                      <ChevronDown size={19} />
                    </button>
                    {topicOpen && (
                      <div className="ea-topic-dropdown" id="ea-topic-options">
                        <div className="ea-topic-search">
                          <Search size={17} />
                          <input
                            autoFocus
                            aria-label="Tìm đề tài"
                            value={query}
                            onChange={(event) => setQuery(event.target.value)}
                            placeholder="Tìm theo mã, tên đề tài hoặc nhóm..."
                          />
                        </div>
                        <div className="ea-topic-options">
                          {filteredProjects.map((project) => (
                            <label key={project.id}>
                              <input
                                type="checkbox"
                                checked={projectIds.includes(project.id)}
                                onChange={() =>
                                  setProjectIds((ids) =>
                                    ids.includes(project.id)
                                      ? ids.filter((id) => id !== project.id)
                                      : [...ids, project.id],
                                  )
                                }
                              />
                              <span>
                                <strong>
                                  {project.topicCode} · {project.topicTitle}
                                </strong>
                                <small>{project.groupName}</small>
                              </span>
                            </label>
                          ))}
                          {!filteredProjects.length && (
                            <div className="ea-topic-unavailable" role="status">
                              {isCommittee &&
                              (matchingUnavailableProjects.length > 0 || !query.trim()) ? (
                                <>
                                  <p>
                                    Chỉ có thể phân công chấm cho đề tài đã xếp lịch bảo vệ vào hội
                                    đồng đang chọn.
                                  </p>
                                  {matchingUnavailableProjects.map((project) => {
                                    const defense = defenses.find(
                                      (item) => item.assignmentId === project.id,
                                    )
                                    const reason = !defense
                                      ? 'Chưa xếp lịch bảo vệ.'
                                      : defense.status === 'SCHEDULED'
                                        ? `Đã xếp lịch ở hội đồng khác: ${defense.committeeName}.`
                                        : defense.status === 'COMPLETED'
                                          ? 'Lịch bảo vệ đã hoàn thành.'
                                          : 'Lịch bảo vệ đã hủy.'
                                    return (
                                      <p key={project.id}>
                                        <strong>
                                          {project.topicCode} · {project.groupName}
                                        </strong>
                                        <br />
                                        {reason}
                                      </p>
                                    )
                                  })}
                                  <button
                                    type="button"
                                    className="btn btn-secondary"
                                    onClick={() => {
                                      setTopicOpen(false)
                                      setMode('SCHEDULE')
                                      document.getElementById('ea-tab-SCHEDULE')?.focus()
                                    }}
                                  >
                                    Mở lịch bảo vệ
                                  </button>
                                </>
                              ) : (
                                <p>Không có đề tài phù hợp.</p>
                              )}
                            </div>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                  {chosenProjects.length ? (
                    <div className="ea-topic-chips">
                      {chosenProjects.map((project) => (
                        <span key={project.id}>
                          {project.topicCode} · {project.topicTitle}
                          <button
                            type="button"
                            aria-label={`Bỏ chọn ${project.topicCode}`}
                            onClick={() =>
                              setProjectIds((ids) => ids.filter((id) => id !== project.id))
                            }
                          >
                            <X size={14} />
                          </button>
                        </span>
                      ))}
                    </div>
                  ) : (
                    <p className="ea-hint">Chưa chọn đề tài nào.</p>
                  )}
                  {isCommittee && committee && (
                    <small>Có thể chọn các đề tài có phiên bảo vệ thuộc {committee.name}.</small>
                  )}
                </div>
                <div className="ea-field ea-deadline">
                  <label htmlFor="ea-deadline">
                    Hạn chót chấm điểm <em>*</em>
                  </label>
                  <DateTimeInput
                    id="ea-deadline"
                    type="datetime-local"
                    value={deadline}
                    onValueChange={setDeadline}
                    required
                  />
                </div>
              </>
            ) : (
              <p className="ea-hint ea-draft-hint">
                Bạn có thể lưu hội đồng và bộ tiêu chí trước khi xếp lịch. Chọn phiên bảo vệ đã có
                để phân công chấm cho các đề tài.
              </p>
            )}
          </fieldset>
          <footer className="ea-actions">
            <span>
              <em>*</em> Thông tin bắt buộc
            </span>
            <div>
              <button
                type="button"
                className="btn ea-cancel"
                disabled={submitting || scheduleBusy || loading}
                onClick={resetForm}
              >
                Hủy
              </button>
              <button
                type="submit"
                className="btn ea-save"
                disabled={
                  submitting ||
                  loading ||
                  !!loadError ||
                  !rubric ||
                  (assigningTopics && !chosenProjects.length)
                }
              >
                {submitting
                  ? 'Đang lưu...'
                  : isCommittee && !assigningTopics
                    ? 'Lưu hội đồng'
                    : 'Lưu phân công'}
              </button>
              {isCommittee && !assigningTopics && (
                <button
                  type="submit"
                  value="schedule"
                  className="btn ea-save"
                  disabled={submitting || loading || !!loadError || !rubric}
                >
                  Lưu & xếp lịch
                </button>
              )}
            </div>
          </footer>
        </form>
      )}
      {gradingDefense && (
        <DefenseGradingDialog
          key={gradingDefense.id}
          defense={gradingDefense}
          committee={committees.find((item) => item.id === gradingDefense.committeeId)}
          onClose={() => setGradingDefense(null)}
          onAssigned={() => {
            setGradingDefense(null)
            setPage(0)
            setListVersion((value) => value + 1)
          }}
        />
      )}
      {periodId && (
        <section className="ea-notice" aria-label="Tiến độ phản biện của đợt" role="status">
          <strong>Tiến độ phản biện của đợt</strong>
          <button
            type="button"
            className="btn btn-secondary btn-sm"
            disabled={!progress}
            onClick={() => setListVersion((value) => value + 1)}
          >
            Làm mới tiến độ phản biện
          </button>
          {!progress ? (
            <p>Đang tải tiến độ phản biện...</p>
          ) : progress.error ? (
            <p>
              {progress.error}{' '}
              <button type="button" onClick={() => setListVersion((value) => value + 1)}>
                Thử lại
              </button>
            </p>
          ) : (
            progress.data && (
              <p>
                Đã nộp {progress.data.submittedCount}/{progress.data.assignedCount} phiếu · Đang
                chấm: {progress.data.draftCount} · Chưa chấm: {progress.data.notStartedCount}
              </p>
            )
          )}
        </section>
      )}
      <section className="ea-list">
        <div className="ea-list-heading">
          <h2>Danh sách phân công</h2>
          <span>{totalElements} phân công</span>
        </div>
        {listLoading ? (
          <p className="ea-empty" role="status">
            Đang tải danh sách phân công...
          </p>
        ) : listError ? (
          <div className="ea-notice ea-notice--error" role="alert">
            {listError}
            <button type="button" onClick={() => setListVersion((value) => value + 1)}>
              Thử lại
            </button>
          </div>
        ) : !assignments.length ? (
          <div className="ea-empty">
            <FileText size={28} />
            <p>Chưa có phân công chấm điểm trong đợt này.</p>
          </div>
        ) : (
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Đề tài / Nhóm</th>
                  <th>Giảng viên chấm</th>
                  <th>Thành phần / Bộ tiêu chí</th>
                  <th>Vai trò</th>
                  <th>Hạn chót</th>
                  <th>Trạng thái</th>
                  <th>Tiến độ chấm</th>
                </tr>
              </thead>
              <tbody>
                {assignments.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <strong>{item.topicTitle}</strong>
                      <small>
                        {item.topicCode} · {item.groupName}
                      </small>
                    </td>
                    <td>
                      {item.lecturerName}
                      <small>{item.lecturerCode}</small>
                    </td>
                    <td>
                      {item.componentName}
                      <small>
                        {item.rubricName} · v{item.rubricVersionNo}
                      </small>
                    </td>
                    <td>{evaluatorRoleLabel(item.evaluatorRole)}</td>
                    <td>{formatDateTime(item.deadlineAt)}</td>
                    <td>
                      <StatusBadge status={item.status} />
                    </td>
                    <td>
                      {item.status !== 'ACTIVE'
                        ? 'Đã hủy'
                        : !item.scoreSheetId
                          ? 'Chưa chấm'
                          : item.scoreSheetStatus === 'DRAFT'
                            ? 'Đang chấm'
                            : item.scoreSheetStatus === 'LOCKED'
                              ? 'Đã nộp (đã khóa)'
                              : item.scoreSheetStatus === 'SUBMITTED'
                                ? 'Đã nộp'
                                : 'Chưa chấm'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {totalPages > 1 && (
          <div className="ea-pagination">
            <span>
              Trang {page + 1} / {totalPages}
            </span>
            <div>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                disabled={page === 0 || listLoading || submitting}
                onClick={() => setPage(page - 1)}
              >
                Trước
              </button>
              <button
                type="button"
                className="btn btn-secondary btn-sm"
                disabled={page >= totalPages - 1 || listLoading || submitting}
                onClick={() => setPage(page + 1)}
              >
                Sau
              </button>
            </div>
          </div>
        )}
      </section>
      <dialog ref={rubricDialog} className="ea-rubric-dialog" aria-labelledby="ea-rubric-title">
        <div className="ea-dialog-heading">
          <h2 id="ea-rubric-title">{rubric?.name}</h2>
          <button
            type="button"
            aria-label="Đóng bộ tiêu chí"
            onClick={() => rubricDialog.current?.close()}
          >
            <X size={22} />
          </button>
        </div>
        <p className="ea-hint">Phiên bản {rubric?.versionNo} · Bộ tiêu chí đã khóa</p>
        <table>
          <thead>
            <tr>
              <th>Tiêu chí đánh giá</th>
              <th>Điểm tối đa</th>
            </tr>
          </thead>
          <tbody>
            {rubric?.criteria.map((criterion) => (
              <tr key={criterion.id}>
                <td>
                  <strong>{criterion.name}</strong>
                  <p>{criterion.description}</p>
                </td>
                <td>{Math.round(Number(criterion.maxScore) * 100) / 10}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <button type="button" className="btn ea-save" onClick={() => rubricDialog.current?.close()}>
          Đóng
        </button>
      </dialog>
    </div>
  )
}
