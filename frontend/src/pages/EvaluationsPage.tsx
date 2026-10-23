import { GradeChangeRequestForm } from '../components/GradeChangeRequestForm'
import { useEffect, useState, type FormEvent } from 'react'
import { evaluationApi } from '../api/evaluation'
import { allPages, errorMessage } from '../api/student'
import { AnimatedSelect } from '../components/AnimatedSelect'
import { ConfirmModal } from '../components/ConfirmModal'
import { PageHeader } from '../components/PageHeader'
import { SelectionPanel } from '../components/SelectionPanel'
import { RemoteState } from '../components/RemoteState'
import { StatusBadge } from '../components/StatusBadge'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import type {
  EvaluationAssignmentResponse,
  RubricResponse,
  ScoreSheetResponse,
} from '../types/domain'
import { formatDateTime } from '../utils/dateTime'
import { useRequestLoading } from '../hooks/useRequestLoading'

type ScoreDraft = { score: string; comment: string }

export function EvaluationsPage() {
  const { user } = useAuth()
  const [assignments, setAssignments] = useState<EvaluationAssignmentResponse[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const [loading, setLoading] = useRequestLoading(JSON.stringify([user?.lecturer?.id, revision]))
  const selected = assignments.find((item) => item.id === selectedId)

  useEffect(() => {
    let active = true
    allPages((page) =>
      evaluationApi.listAssignments({ lecturerId: user?.lecturer?.id, page, size: 100 }),
    )
      .then((items) => {
        if (!active) return
        setAssignments(items)
        setError('')
        setSelectedId((current) =>
          items.some((item) => item.id === current) ? current : (items[0]?.id ?? null),
        )
      })
      .catch((error) => {
        if (active) setError(errorMessage(error))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [user?.lecturer?.id, revision, setLoading])

  return (
    <>
      <PageHeader title="Chấm điểm đề tài" />
      <RemoteState
        loading={loading}
        error={error}
        retry={() => setRevision((value) => value + 1)}
      />
      {!loading && !error && (
        <SelectionPanel id="score-assignment" label="Phân công chấm điểm của tôi">
          <AnimatedSelect
            id="score-assignment"
            value={selectedId ?? ''}
            disabled={!assignments.length}
            onChange={(event) => setSelectedId(Number(event.target.value) || null)}
          >
            {!assignments.length && <option value="">Chưa có phân công chấm điểm</option>}
            {assignments.map((item) => (
              <option key={item.id} value={item.id}>
                {item.topicCode} · {item.topicTitle} · {item.componentName} ({item.groupName})
              </option>
            ))}
          </AnimatedSelect>
        </SelectionPanel>
      )}
      {!loading && !error && !assignments.length && (
        <section className="page-card">
          <div className="content-state">
            <strong>Chưa có phân công chấm điểm</strong>
            <p>Các đề tài sẽ xuất hiện khi bạn được phân công làm giảng viên chấm.</p>
          </div>
        </section>
      )}
      {!loading && !error && selected && (
        <ScoreSheetEditor key={`${selected.id}-${revision}`} assignment={selected} />
      )}
    </>
  )
}

function ScoreSheetEditor({ assignment }: { assignment: EvaluationAssignmentResponse }) {
  const { showSuccess, showError } = useToast()
  const [rubric, setRubric] = useState<RubricResponse | null>(null)
  const [sheet, setSheet] = useState<ScoreSheetResponse | null>(null)
  const [scores, setScores] = useState<Record<number, ScoreDraft>>({})
  const [comment, setComment] = useState('')
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const [loading, setLoading] = useRequestLoading(
    JSON.stringify([assignment.id, assignment.componentId, assignment.rubricId, revision]),
  )
  const [saving, setSaving] = useState(false)
  const [confirmSubmit, setConfirmSubmit] = useState(false)

  useEffect(() => {
    let active = true
    Promise.all([
      evaluationApi.listRubrics(assignment.componentId),
      evaluationApi.getScoreSheet(assignment.id),
    ])
      .then(([rubrics, result]) => {
        if (!active) return
        const assigned = rubrics.find((item) => item.id === assignment.rubricId)
        if (!assigned) throw new Error('Không tìm thấy bộ tiêu chí được phân công.')
        setRubric(assigned)
        setSheet(result)
        setComment(result.overallComment ?? '')
        setError('')
        setScores(
          Object.fromEntries(
            assigned.criteria.map((criterion) => {
              const saved = result.scores.find((item) => item.criterionId === criterion.id)
              return [
                criterion.id,
                { score: saved ? String(saved.score) : '', comment: saved?.comment ?? '' },
              ]
            }),
          ),
        )
      })
      .catch((error) => {
        if (active) setError(error.response ? errorMessage(error) : error.message)
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [assignment.id, assignment.componentId, assignment.rubricId, revision, setLoading])

  const editable = assignment.status === 'ACTIVE' && sheet?.status === 'DRAFT'
  const valid =
    !!rubric?.criteria.length &&
    rubric.criteria.every((criterion) => {
      const value = scores[criterion.id]?.score
      const score = Number(value)
      return (
        value !== undefined &&
        value.trim() !== '' &&
        Number.isFinite(score) &&
        score >= 0 &&
        score <= Number(criterion.maxScore)
      )
    })
  const total =
    rubric?.criteria.reduce(
      (sum, criterion) => sum + (Number(scores[criterion.id]?.score) || 0),
      0,
    ) ?? 0

  async function save(submit: boolean) {
    if (saving || !editable || !valid || !sheet || !rubric) return
    setSaving(true)
    try {
      const updated = await evaluationApi.saveScoreSheet(assignment.id, {
        version: sheet.version,
        overallComment: comment.trim(),
        submit,
        scores: rubric.criteria.map((criterion) => ({
          criterionId: criterion.id,
          score: Number(scores[criterion.id].score),
          comment: scores[criterion.id].comment.trim(),
        })),
      })
      setSheet(updated)
      setConfirmSubmit(false)
      showSuccess(
        submit
          ? assignment.evaluatorRole === 'REVIEWER'
            ? 'Đã nộp phiếu điểm phản biện. Hệ thống đã thông báo cho người quản lý.'
            : 'Đã nộp phiếu chấm.'
          : 'Đã lưu phiếu chấm nháp.',
      )
    } catch (error) {
      showError(errorMessage(error))
    } finally {
      setSaving(false)
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (valid) setConfirmSubmit(true)
  }

  return (
    <section className="page-card">
      <RemoteState
        loading={loading}
        error={error}
        retry={() => setRevision((value) => value + 1)}
      />
      {!loading && !error && rubric && sheet && (
        <>
          <div className="content-card__header">
            <div>
              <h2>{assignment.topicTitle}</h2>
              <p>
                {assignment.groupName} · {rubric.name} · Phiên bản {rubric.versionNo}
              </p>
            </div>
            <StatusBadge status={sheet.status} />
          </div>
          <p>Hạn chấm: {formatDateTime(assignment.deadlineAt)}</p>
          {!editable && (
            <div className="mb-6">
              <p className="student-notice">
                Phiếu đã nộp, đã khóa hoặc phân công đã hủy. Bạn có thể xem lại điểm và nhận xét.
                Nếu yêu cầu đổi điểm vừa được duyệt, hãy làm mới phiếu để cập nhật trạng thái.
              </p>
              <button
                type="button"
                className="btn btn-secondary"
                onClick={() => setRevision((value) => value + 1)}
              >
                Làm mới phiếu
              </button>
            </div>
          )}
          {sheet.id &&
            assignment.status === 'ACTIVE' &&
            (sheet.status === 'SUBMITTED' || sheet.status === 'LOCKED') && (
              <GradeChangeRequestForm key={`${sheet.id}-${sheet.version}`} sheetId={sheet.id} />
            )}
          <form className="stack-form" onSubmit={handleSubmit}>
            <fieldset className="student-fieldset" disabled={!editable || saving}>
              {rubric.criteria.map((criterion) => (
                <div className="form-group" key={criterion.id}>
                  <label className="form-label" htmlFor={`score-${criterion.id}`}>
                    {criterion.name} (tối đa {criterion.maxScore} điểm)
                  </label>
                  {criterion.description && (
                    <p className="student-prose">{criterion.description}</p>
                  )}
                  <input
                    id={`score-${criterion.id}`}
                    className="form-input"
                    type="number"
                    min="0"
                    max={criterion.maxScore}
                    step="0.01"
                    required
                    value={scores[criterion.id]?.score ?? ''}
                    onChange={(event) =>
                      setScores((previous) => ({
                        ...previous,
                        [criterion.id]: { ...previous[criterion.id], score: event.target.value },
                      }))
                    }
                  />
                  <textarea
                    className="form-textarea"
                    aria-label={`Nhận xét ${criterion.name}`}
                    placeholder="Nhận xét tiêu chí"
                    value={scores[criterion.id]?.comment ?? ''}
                    onChange={(event) =>
                      setScores((previous) => ({
                        ...previous,
                        [criterion.id]: { ...previous[criterion.id], comment: event.target.value },
                      }))
                    }
                  />
                </div>
              ))}
              <label className="form-label" htmlFor="score-overall-comment">
                Nhận xét chung
              </label>
              <textarea
                id="score-overall-comment"
                className="form-textarea"
                value={comment}
                onChange={(event) => setComment(event.target.value)}
              />
            </fieldset>
            <strong>Tổng điểm: {total.toFixed(2)}/10</strong>
            {editable && (
              <div className="inline-form">
                <button
                  className="btn btn-secondary"
                  type="button"
                  disabled={saving || !valid}
                  onClick={() => void save(false)}
                >
                  Lưu nháp
                </button>
                <button className="btn btn-primary" type="submit" disabled={saving || !valid}>
                  {saving ? 'Đang lưu...' : 'Nộp phiếu chấm'}
                </button>
              </div>
            )}
          </form>
        </>
      )}
      <ConfirmModal
        isOpen={confirmSubmit}
        title="Nộp phiếu chấm"
        message="Sau khi nộp, phiếu không thể chỉnh sửa trực tiếp. Xác nhận nộp điểm và nhận xét?"
        confirmText="Nộp phiếu"
        variant="primary"
        loading={saving}
        onConfirm={() => void save(true)}
        onCancel={() => {
          if (!saving) setConfirmSubmit(false)
        }}
      />
    </section>
  )
}
