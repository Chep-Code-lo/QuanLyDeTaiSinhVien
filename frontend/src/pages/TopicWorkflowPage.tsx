import { isAxiosError } from 'axios'
import { useCallback, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { topicApi } from '../api/topics'
import { errorMessage } from '../api/student'
import { PageHeader } from '../components/PageHeader'
import { PageControls } from '../components/PageControls'
import { RemoteState } from '../components/RemoteState'
import { ConfirmModal } from '../components/ConfirmModal'
import { TopicProposalForm } from '../components/TopicProposalForm'
import { LecturerAutocomplete } from '../components/LecturerAutocomplete'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { useRemoteData } from '../hooks/useRemoteData'
import { canManageDepartment } from '../utils/managementAccess'
import { statusLabel } from '../utils/displayLabels'
import type { TopicResponse, TopicReviewDecision } from '../types/domain'

export function TopicWorkflowPage() {
  const { user } = useAuth()
  const { id } = useParams()
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState('PENDING')
  const [revision, setRevision] = useState(0)
  const [selected, setSelected] = useState<number | null>(Number(id) || null)
  const [creating, setCreating] = useState(false)
  const load = useCallback(
    () => topicApi.list({ page, size: 10, status: status || undefined }),
    [page, status],
  )
  const { data, loading, error } = useRemoteData(load, revision)
  const refresh = () => setRevision((value) => value + 1)
  return (
    <>
      <PageHeader title="Đề xuất & Xét duyệt đề tài" />
      <section className="page-card">
        <div className="topic-workflow-toolbar">
          {(user?.student || user?.lecturer) && (
            <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>
              Đề xuất đề tài
            </button>
          )}
          <label className="topic-workflow-toolbar__filter">
            Trạng thái đề xuất
            <select
              className="form-select"
              value={status}
              onChange={(event) => {
                setStatus(event.target.value)
                setPage(0)
                setSelected(null)
              }}
            >
              <option value="">Tất cả được phép xem</option>
              {[
                'PENDING',
                'REVISION_REQUIRED',
                'APPROVED',
                'PUBLISHED',
                'REJECTED',
                'WITHDRAWN',
                'DRAFT',
              ].map((value) => (
                <option key={value} value={value}>
                  {statusLabel(value)}
                </option>
              ))}
            </select>
          </label>
        </div>
        <RemoteState loading={loading} error={error} retry={refresh} />
        {data && (
          <>
            {!data.content.length && <p>Không có đề xuất phù hợp.</p>}
            <div className="table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Đề tài</th>
                    <th>Người đề xuất</th>
                    <th>Trạng thái</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((topic) => (
                    <tr key={topic.id}>
                      <td>
                        {topic.code} · {topic.title}
                      </td>
                      <td>{topic.proposerName}</td>
                      <td>{statusLabel(topic.status)}</td>
                      <td>
                        <button
                          type="button"
                          className="btn btn-secondary"
                          onClick={() => setSelected(topic.id)}
                        >
                          Xử lý {topic.code}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <PageControls
              label="Phân trang đề xuất"
              page={page}
              totalPages={data.totalPages}
              loading={loading}
              onPage={setPage}
            />
          </>
        )}
      </section>
      {creating && (
        <TopicProposalForm
          onClose={() => setCreating(false)}
          onSaved={() => {
            setCreating(false)
            refresh()
          }}
        />
      )}
      {selected && <TopicWorkflowDetail key={selected} id={selected} onChanged={refresh} />}
    </>
  )
}
function TopicWorkflowDetail({ id, onChanged }: { id: number; onChanged: () => void }) {
  const [revision, setRevision] = useState(0)
  const load = useCallback(() => topicApi.get(id), [id])
  const { data, loading, error } = useRemoteData(load, revision)
  const [editing, setEditing] = useState(false)
  const refresh = () => {
    setEditing(false)
    setRevision((value) => value + 1)
    onChanged()
  }
  return (
    <section className="page-card">
      <RemoteState loading={loading} error={error} retry={refresh} />
      {data && (
        <>
          <h2>
            {data.code} · {data.title}
          </h2>
          <p>{data.description}</p>
          <p>Mục tiêu: {data.objectives}</p>
          <p>Kết quả dự kiến: {data.expectedOutcomes}</p>
          {data.latestReview?.comment && <p>Phản hồi xét duyệt: {data.latestReview.comment}</p>}
          {editing ? (
            <TopicProposalForm topic={data} onClose={() => setEditing(false)} onSaved={refresh} />
          ) : (
            <TopicActions topic={data} onEdit={() => setEditing(true)} onChanged={refresh} />
          )}
        </>
      )}
    </section>
  )
}
function TopicActions({
  topic,
  onEdit,
  onChanged,
}: {
  topic: TopicResponse
  onEdit: () => void
  onChanged: () => void
}) {
  const { user } = useAuth()
  const { showSuccess, showError } = useToast()
  const manager = canManageDepartment(user, topic.departmentId, topic.facultyId)
  const owner = user?.id === topic.proposedBy || !!user?.roles.includes('ADMIN')
  const [action, setAction] = useState<'review' | 'supervisors' | 'withdraw' | 'publish' | ''>('')
  const [decision, setDecision] = useState<TopicReviewDecision>('APPROVED')
  const [comment, setComment] = useState('')
  const savedPrimary = topic.supervisors.find((item) => item.slotNo === 1)
  const savedSecondary = topic.supervisors.find((item) => item.slotNo === 2)
  const [primary, setPrimary] = useState<number | null>(savedPrimary?.lecturerId ?? null)
  const [secondary, setSecondary] = useState<number | null>(savedSecondary?.lecturerId ?? null)
  const [confirm, setConfirm] = useState(false)
  const [busy, setBusy] = useState(false)
  const sending = useRef(false)
  return (
    <>
      <div className="inline-form">
        {owner && ['DRAFT', 'PENDING', 'REVISION_REQUIRED'].includes(topic.status) && (
          <button type="button" className="btn btn-secondary" onClick={onEdit}>
            Sửa đề xuất
          </button>
        )}
        {manager && !['PUBLISHED', 'WITHDRAWN'].includes(topic.status) && (
          <button type="button" className="btn btn-secondary" onClick={() => setAction('review')}>
            Xét duyệt đề xuất
          </button>
        )}
        {manager && ['APPROVED', 'PUBLISHED'].includes(topic.status) && (
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => setAction('supervisors')}
          >
            Phân công GVHD
          </button>
        )}
        {manager && topic.status === 'APPROVED' && (
          <button type="button" className="btn btn-primary" onClick={() => setAction('publish')}>
            Công bố đề tài
          </button>
        )}
        {owner && !['PUBLISHED', 'WITHDRAWN'].includes(topic.status) && (
          <button type="button" className="btn btn-danger" onClick={() => setAction('withdraw')}>
            Rút đề xuất
          </button>
        )}
      </div>
      {action && (
        <form
          className="stack-form"
          onSubmit={(event) => {
            event.preventDefault()
            if (
              !busy &&
              ((action !== 'review' && action !== 'withdraw') || comment.trim()) &&
              (action !== 'supervisors' || (primary && primary !== secondary))
            )
              setConfirm(true)
          }}
        >
          <fieldset className="student-fieldset" disabled={busy}>
            {action === 'review' && (
              <label>
                Kết quả xét duyệt
                <select
                  className="form-select"
                  value={decision}
                  onChange={(event) => setDecision(event.target.value as TopicReviewDecision)}
                >
                  <option value="APPROVED">Duyệt</option>
                  <option value="REVISION_REQUIRED">Yêu cầu sửa</option>
                  <option value="REJECTED">Từ chối</option>
                </select>
              </label>
            )}
            {(action === 'review' || action === 'withdraw') && (
              <label>
                {action === 'review' ? 'Nhận xét xét duyệt' : 'Lý do rút'}
                <textarea
                  required
                  className="form-textarea"
                  value={comment}
                  onChange={(event) => setComment(event.target.value)}
                />
              </label>
            )}
            {action === 'supervisors' && (
              <>
                <LecturerAutocomplete
                  ariaLabel="GVHD chính"
                  label="GVHD chính"
                  required
                  value={primary}
                  facultyId={topic.facultyId}
                  excludedIds={secondary ? [secondary] : []}
                  selectedOption={
                    savedPrimary
                      ? {
                          id: savedPrimary.lecturerId,
                          fullName: savedPrimary.lecturerName,
                          lecturerCode: savedPrimary.lecturerCode,
                        }
                      : undefined
                  }
                  onChange={setPrimary}
                />
                <LecturerAutocomplete
                  ariaLabel="GVHD phụ"
                  label="GVHD phụ"
                  value={secondary}
                  selectedOption={
                    savedSecondary
                      ? {
                          id: savedSecondary.lecturerId,
                          fullName: savedSecondary.lecturerName,
                          lecturerCode: savedSecondary.lecturerCode,
                        }
                      : undefined
                  }
                  facultyId={topic.facultyId}
                  excludedIds={primary ? [primary] : []}
                  onChange={setSecondary}
                />
                <button
                  type="button"
                  className="btn btn-secondary"
                  onClick={() => setSecondary(null)}
                >
                  Bỏ GVHD phụ
                </button>
              </>
            )}
            {action === 'publish' && (
              <p>
                Công bố đề tài đã duyệt để sinh viên đăng ký. Đề tài cần có đủ GVHD theo quy định.
              </p>
            )}
            <button className="btn btn-primary" disabled={busy}>
              Tiếp tục
            </button>
            <button type="button" className="btn btn-secondary" onClick={() => setAction('')}>
              Hủy thao tác
            </button>
          </fieldset>
        </form>
      )}
      <ConfirmModal
        isOpen={confirm}
        title="Xác nhận xử lý đề tài"
        message={`Xác nhận thao tác với đề tài ${topic.code}?`}
        loading={busy}
        onCancel={() => {
          if (!sending.current) setConfirm(false)
        }}
        onConfirm={async () => {
          if (!action || sending.current) return
          sending.current = true
          setBusy(true)
          try {
            if (action === 'review')
              await topicApi.review(topic.id, {
                version: topic.version,
                decision,
                comment: comment.trim(),
              })
            if (action === 'publish') await topicApi.publish(topic.id, topic.version)
            if (action === 'withdraw')
              await topicApi.withdraw(topic.id, { version: topic.version, reason: comment.trim() })
            if (action === 'supervisors' && primary)
              await topicApi.assignSupervisors(topic.id, {
                version: topic.version,
                primaryLecturerId: primary,
                secondaryLecturerId: secondary,
              })
            showSuccess('Đã cập nhật đề tài.')
            setAction('')
            onChanged()
          } catch (error) {
            showError(errorMessage(error))
            if (isAxiosError(error) && error.response?.status === 409) onChanged()
          } finally {
            sending.current = false
            setBusy(false)
            setConfirm(false)
          }
        }}
      />
    </>
  )
}
