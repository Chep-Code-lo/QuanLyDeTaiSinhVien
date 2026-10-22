import { useCallback, useRef, useState } from 'react'
import { evaluationApi } from '../api/evaluation'
import { academicApi } from '../api/academic'
import { topicApi } from '../api/topics'
import { errorMessage } from '../api/student'
import { ConfirmModal } from '../components/ConfirmModal'
import { PageControls } from '../components/PageControls'
import { PageHeader } from '../components/PageHeader'
import { RemoteState } from '../components/RemoteState'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { useRemoteData } from '../hooks/useRemoteData'
import type { GradeChangeResponse } from '../types/domain'
import { canManageDepartment } from '../utils/managementAccess'
import { formatDateTime } from '../utils/dateTime'

const labels = {
  PENDING: 'Chờ duyệt',
  APPROVED: 'Đã duyệt, chờ nộp lại',
  REJECTED: 'Đã từ chối',
  APPLIED: 'Đã áp dụng',
}
export function GradeChangesPage() {
  const [status, setStatus] = useState<GradeChangeResponse['status'] | ''>('')
  const [sheetInput, setSheetInput] = useState('')
  const [sheetId, setSheetId] = useState(0)
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const [selected, setSelected] = useState<number | null>(null)
  const load = useCallback(
    () =>
      evaluationApi.listGradeChanges({
        page,
        size: 10,
        status: status || undefined,
        ...(sheetId ? { sheetId } : {}),
      }),
    [page, status, sheetId],
  )
  const { data, loading, error } = useRemoteData(load, revision)
  return (
    <>
      <PageHeader title="Yêu cầu đổi điểm" />
      <section className="page-card">
        <label>
          Trạng thái yêu cầu
          <select
            className="form-select"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value as typeof status)
              setPage(0)
              setSelected(null)
            }}
          >
            <option value="">Tất cả</option>
            {Object.entries(labels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <form
          className="inline-form"
          onSubmit={(event) => {
            event.preventDefault()
            const value = Number(sheetInput)
            if (sheetInput.trim() && (!Number.isSafeInteger(value) || value <= 0)) return
            setSheetId(value)
            setPage(0)
            setSelected(null)
            setRevision((value) => value + 1)
          }}
        >
          <label>
            Mã phiếu điểm
            <input
              className="form-input"
              type="number"
              min="1"
              step="1"
              value={sheetInput}
              onChange={(event) => setSheetInput(event.target.value)}
              placeholder="Tất cả phiếu"
            />
          </label>
          <button className="btn btn-secondary">Lọc theo phiếu</button>
        </form>
        <RemoteState
          loading={loading}
          error={error}
          retry={() => setRevision((value) => value + 1)}
        />
        {data && (
          <>
            {!data.content.length && <p>Không có yêu cầu đổi điểm trong phạm vi của bạn.</p>}
            <div className="table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Phiếu</th>
                    <th>Người yêu cầu</th>
                    <th>Ngày gửi</th>
                    <th>Trạng thái</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((row) => (
                    <tr key={row.id}>
                      <td>#{row.sheetId}</td>
                      <td>{row.requestedByName}</td>
                      <td>{formatDateTime(row.createdAt)}</td>
                      <td>{labels[row.status]}</td>
                      <td>
                        <button
                          type="button"
                          className="btn btn-secondary"
                          onClick={() => setSelected(row.id)}
                        >
                          Xem yêu cầu #{row.id}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <PageControls
              label="Phân trang yêu cầu đổi điểm"
              page={page}
              totalPages={data.totalPages}
              loading={loading}
              onPage={setPage}
            />
          </>
        )}
      </section>
      {selected && (
        <GradeChangeDetail
          key={selected}
          id={selected}
          onReviewed={() => setRevision((value) => value + 1)}
        />
      )}
    </>
  )
}
function GradeChangeDetail({ id, onReviewed }: { id: number; onReviewed: () => void }) {
  const { user } = useAuth()
  const { showSuccess, showError } = useToast()
  const [revision, setRevision] = useState(0)
  const load = useCallback(async () => {
    const request = await evaluationApi.getGradeChange(id)
    const project = await academicApi.getProject(request.assignmentId)
    const topic = await topicApi.get(project.topicId)
    return { request, topic }
  }, [id])
  const { data, loading, error } = useRemoteData(load, revision)
  const [comment, setComment] = useState('')
  const [pending, setPending] = useState<boolean | null>(null)
  const [busy, setBusy] = useState(false)
  const sending = useRef(false)
  const canReview =
    !!data && canManageDepartment(user, data.topic.departmentId, data.topic.facultyId)
  return (
    <section className="page-card" aria-label="Chi tiết yêu cầu đổi điểm">
      <RemoteState
        loading={loading}
        error={error}
        retry={() => setRevision((value) => value + 1)}
      />
      {data && (
        <>
          <h2>
            Yêu cầu #{id} · {data.topic.code}
          </h2>
          <p>Lý do: {data.request.reason}</p>
          <p>
            {labels[data.request.status]}
            {data.request.reviewedByName && ` · ${data.request.reviewedByName}`}
          </p>
          {data.request.reviewComment && <p>Phản hồi: {data.request.reviewComment}</p>}
          {canReview && data.request.status === 'PENDING' && (
            <>
              <label>
                Ý kiến xét duyệt
                <textarea
                  className="form-textarea"
                  maxLength={5000}
                  value={comment}
                  disabled={busy}
                  onChange={(event) => setComment(event.target.value)}
                />
              </label>
              <div className="inline-form">
                <button
                  type="button"
                  className="btn btn-primary"
                  disabled={busy}
                  onClick={() => setPending(true)}
                >
                  Duyệt yêu cầu
                </button>
                <button
                  type="button"
                  className="btn btn-danger"
                  disabled={busy}
                  onClick={() => setPending(false)}
                >
                  Từ chối yêu cầu
                </button>
              </div>
            </>
          )}
        </>
      )}
      <ConfirmModal
        isOpen={pending !== null}
        title="Xác nhận xét duyệt"
        message={
          pending
            ? 'Duyệt và mở lại phiếu điểm cho giảng viên chỉnh sửa?'
            : 'Từ chối yêu cầu điều chỉnh điểm này?'
        }
        loading={busy}
        onCancel={() => {
          if (!sending.current) setPending(null)
        }}
        onConfirm={async () => {
          if (pending === null || sending.current || !canReview) return
          sending.current = true
          setBusy(true)
          try {
            await evaluationApi.reviewGradeChange(id, {
              approved: pending,
              comment: comment.trim(),
            })
            showSuccess('Đã xử lý yêu cầu đổi điểm.')
            onReviewed()
          } catch (error) {
            showError(errorMessage(error))
          } finally {
            sending.current = false
            setBusy(false)
            setPending(null)
            setRevision((value) => value + 1)
          }
        }}
      />
    </section>
  )
}
