import { hasAcademicLeadership } from '../utils/managementAccess'
import { useAuth } from '../context/AuthContext'
import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { useRemoteData } from '../hooks/useRemoteData'
import { PageControls } from '../components/PageControls'
import { academicApi } from '../api/academic'
import { evaluationApi } from '../api/evaluation'
import { errorMessage } from '../api/student'
import { PageHeader } from '../components/PageHeader'
import { RemoteState } from '../components/RemoteState'
import { useToast } from '../context/ToastContext'
import type { PageResponse } from '../types/api'
import type { ProjectAssignmentResponse, ResultDetailResponse, ResultStatus } from '../types/domain'
import { formatDateTime } from '../utils/dateTime'

const componentLabels: Record<string, string> = {
  REVIEW: 'Phản biện',
  DEFENSE: 'Bảo vệ trước hội đồng',
  COMMITTEE: 'Hội đồng bảo vệ',
  OTHER: 'Đánh giá khác',
}

const statusLabels: Record<ResultStatus, string> = {
  FINALIZED: 'Đã chốt, chờ công bố',
  PUBLISHED: 'Đã công bố',
  SUPERSEDED: 'Đã thay thế',
}

export function EvaluationResultsPage() {
  const { user } = useAuth()
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const key = `${page}:${revision}`
  const [response, setResponse] = useState<{
    key: string
    data?: PageResponse<ProjectAssignmentResponse>
    error?: string
  } | null>(null)
  const [selected, setSelected] = useState<ProjectAssignmentResponse | null>(null)
  const current = response?.key === key ? response : null
  useEffect(() => {
    let live = true
    academicApi
      .getProjectAssignments(undefined, page, 10)
      .then((data) => {
        if (live) setResponse({ key, data })
      })
      .catch((error) => {
        if (live) setResponse({ key, error: errorMessage(error) })
      })
    return () => {
      live = false
    }
  }, [page, key])
  const go = (next: number) => {
    setPage(next)
    setSelected(null)
  }
  return (
    <>
      <PageHeader
        title={
          hasAcademicLeadership(user)
            ? 'Tổng hợp & Công bố điểm'
            : user?.roles.includes('ADMIN')
              ? 'Tổng hợp kết quả'
              : 'Kết quả đánh giá'
        }
      />
      <section className="page-card">
        <RemoteState
          loading={!current}
          error={current?.error ?? ''}
          retry={() => setRevision((value) => value + 1)}
        />
        {current?.data && (
          <>
            {!current.data.content.length && <p>Chưa có đề tài được giao.</p>}
            <div className="table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Đề tài</th>
                    <th>Nhóm</th>
                    <th>Đợt</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {current.data.content.map((project) => (
                    <tr key={project.id}>
                      <td>
                        {project.topicCode} · {project.topicTitle}
                      </td>
                      <td>{project.groupName}</td>
                      <td>{project.periodName}</td>
                      <td>
                        <button
                          type="button"
                          className="btn btn-secondary"
                          aria-haspopup="dialog"
                          onClick={() => setSelected(project)}
                        >
                          Xem điểm {project.topicCode}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {current.data.totalPages > 1 && (
              <nav className="table-pagination result-pagination" aria-label="Phân trang đề tài">
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled={page === 0}
                  onClick={() => go(page - 1)}
                >
                  Trước
                </button>
                <span>
                  {' '}
                  Trang {page + 1}/{Math.max(1, current.data.totalPages)}{' '}
                </span>
                <button
                  type="button"
                  className="btn btn-secondary"
                  disabled={page + 1 >= current.data.totalPages}
                  onClick={() => go(page + 1)}
                >
                  Sau
                </button>
              </nav>
            )}
          </>
        )}
      </section>
      {selected && (
        <ProjectResults key={selected.id} project={selected} onClose={() => setSelected(null)} />
      )}
    </>
  )
}

function ProjectResults({
  project,
  onClose,
}: {
  project: ProjectAssignmentResponse
  onClose: () => void
}) {
  const { showSuccess, showError } = useToast()
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const key = `${page}:${revision}`
  const [response, setResponse] = useState<{
    key: string
    data?: PageResponse<ResultDetailResponse>
    actions?: { canFinalize: boolean; canPublish: boolean }
    error?: string
  } | null>(null)
  const current = response?.key === key ? response : null
  const [pending, setPending] = useState<{ kind: 'finalize' | 'publish'; id: number } | null>(null)
  const [busy, setBusy] = useState(false)
  const inFlight = useRef(false)
  const dialog = useRef<HTMLDialogElement>(null)
  const closeButton = useRef<HTMLButtonElement>(null)
  const titleId = useId()
  useEffect(() => {
    const element = dialog.current
    const trigger = document.activeElement as HTMLElement | null
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    element?.showModal()
    closeButton.current?.focus()
    return () => {
      element?.close()
      document.body.style.overflow = previousOverflow
      if (trigger?.isConnected) trigger.focus()
    }
  }, [])
  useEffect(() => {
    if (pending) dialog.current?.querySelector<HTMLButtonElement>('[data-cancel]')?.focus()
    else closeButton.current?.focus()
  }, [pending])
  const live = useRef(true)
  useEffect(() => {
    live.current = true
    return () => {
      live.current = false
    }
  }, [])
  useEffect(() => {
    let active = true
    Promise.all([
      evaluationApi.listResults({ assignmentId: project.id, page, size: 10 }),
      evaluationApi.resultActions(project.id),
    ])
      .then(([data, actions]) => {
        if (active) setResponse({ key, data, actions })
      })
      .catch((error) => {
        if (active) setResponse({ key, error: errorMessage(error) })
      })
    return () => {
      active = false
    }
  }, [project.id, page, key])
  async function confirm() {
    if (!pending || inFlight.current || !current?.actions) return
    if (pending.kind === 'finalize' ? !current.actions.canFinalize : !current.actions.canPublish)
      return
    inFlight.current = true
    setBusy(true)
    try {
      if (pending.kind === 'finalize') await evaluationApi.finalizeResult(project.id)
      else await evaluationApi.publishResult(pending.id)
      if (live.current)
        showSuccess(
          pending.kind === 'finalize'
            ? 'Đã chốt kết quả. Kết quả chưa hiển thị cho sinh viên cho đến khi được công bố.'
            : 'Đã công bố kết quả cho sinh viên.',
        )
    } catch (error) {
      if (live.current) showError(errorMessage(error))
    } finally {
      inFlight.current = false
      if (live.current) {
        setBusy(false)
        setPending(null)
        setPage(0)
        setRevision((value) => value + 1)
      }
    }
  }
  const awaitingPublication = current?.data?.content.some((result) => result.status === 'FINALIZED')
  return (
    <dialog
      ref={dialog}
      className="catalog-editor-dialog project-results-dialog"
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault()
        if (inFlight.current) return
        if (pending) setPending(null)
        else onClose()
      }}
    >
      <div className="catalog-editor-dialog__heading">
        <h2 id={titleId}>
          {pending
            ? pending.kind === 'publish'
              ? 'Công bố kết quả'
              : 'Chốt kết quả'
            : `${project.topicCode} · ${project.groupName}`}
        </h2>
        <button
          ref={closeButton}
          type="button"
          className="btn btn-secondary"
          disabled={busy}
          aria-label="Đóng hộp thoại kết quả"
          onClick={() => {
            if (pending) setPending(null)
            else onClose()
          }}
        >
          Đóng
        </button>
      </div>
      {!pending && (
        <>
          <RemoteState
            loading={!current}
            error={current?.error ?? ''}
            retry={() => setRevision((value) => value + 1)}
          />
          <div className="inline-form">
            <button
              type="button"
              className="btn btn-secondary"
              disabled={busy}
              onClick={() => setRevision((value) => value + 1)}
            >
              Làm mới kết quả
            </button>
            {current?.data && current.actions?.canFinalize && (
              <button
                type="button"
                className="btn btn-primary"
                disabled={busy || awaitingPublication || page !== 0}
                onClick={() => setPending({ kind: 'finalize', id: project.id })}
              >
                Chốt kết quả
              </button>
            )}
          </div>
          {current?.data && (
            <>
              {!current.data.content.length && (
                <>
                  <p>
                    Chưa có kết quả được chốt. Cần phân công đủ và nộp đầy đủ phiếu của các thành phần
                    đánh giá bắt buộc trước khi chốt.
                  </p>
                  <ProjectScorePreview projectId={project.id} revision={revision} />
                </>
              )}
              {current.data.content.map((result) => (
                <article key={result.id} className="page-card">
                  <h3>
                    Kết quả lần {result.versionNo} · {statusLabels[result.status]}
                  </h3>
                  <p>
                    Điểm tổng kết: <strong>{Number(result.finalScore).toFixed(2)}/10</strong>
                  </p>
                  <p>
                    Người chốt điểm: {result.finalizedByName} · {formatDateTime(result.finalizedAt)}
                  </p>
                  {result.publishedAt && (
                    <p>
                      Người công bố: {result.publishedByName} · {formatDateTime(result.publishedAt)}
                    </p>
                  )}
                  <div className="table-container">
                    <table className="data-table result-input-table">
                      <thead>
                        <tr>
                          <th>Nội dung đánh giá</th>
                          <th>Phiếu chấm</th>
                          <th>Điểm đánh giá</th>
                        </tr>
                      </thead>
                      <tbody>
                        {result.inputs.map((input) => (
                          <tr key={input.id}>
                            <td>{componentLabels[input.componentCode] ?? input.componentCode}</td>
                            <td>Phiếu số {input.sheetId}</td>
                            <td>{Number(input.scoreSnapshot).toFixed(2)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <p>
                    Sinh viên:{' '}
                    {result.members
                      .map((member) => `${member.studentCodeSnapshot} · ${member.fullNameSnapshot}`)
                      .join('; ')}
                  </p>
                  {result.status === 'FINALIZED' && current.actions?.canPublish && (
                    <button
                      type="button"
                      className="btn btn-primary"
                      disabled={busy}
                      onClick={() => setPending({ kind: 'publish', id: result.id })}
                    >
                      Công bố kết quả
                    </button>
                  )}
                </article>
              ))}
              {current.data.totalPages > 1 && (
                <nav className="table-pagination result-pagination" aria-label="Phân trang kết quả">
                  <button
                    type="button"
                    className="btn btn-secondary"
                    disabled={busy || page === 0}
                    onClick={() => setPage((value) => value - 1)}
                  >
                    Trước
                  </button>
                  <span>
                    {' '}
                    Trang {page + 1}/{Math.max(1, current.data.totalPages)}{' '}
                  </span>
                  <button
                    type="button"
                    className="btn btn-secondary"
                    disabled={busy || page + 1 >= current.data.totalPages}
                    onClick={() => setPage((value) => value + 1)}
                  >
                    Sau
                  </button>
                </nav>
              )}
            </>
          )}
        </>
      )}
      {pending && (
        <>
          <p>
            {pending.kind === 'publish'
              ? `Công bố kết quả ${project.topicCode} cho sinh viên trong nhóm?`
              : `Chốt kết quả ${project.topicCode}? Hệ thống sẽ kiểm tra đủ phiếu và lưu ảnh chụp điểm. Điểm đã chốt chỉ được điều chỉnh qua quy trình được phê duyệt.`}
          </p>
          <div className="project-results-dialog__actions">
            <button
              type="button"
              data-cancel
              className="btn btn-secondary"
              disabled={busy}
              onClick={() => setPending(null)}
            >
              Hủy
            </button>
            <button
              type="button"
              className="btn btn-primary"
              disabled={busy}
              onClick={() => void confirm()}
            >
              {busy ? 'Đang xử lý...' : 'Xác nhận'}
            </button>
          </div>
        </>
      )}
    </dialog>
  )
}

function ProjectScorePreview({ projectId, revision }: { projectId: number; revision: number }) {
  const [page, setPage] = useState(0)
  const [retry, setRetry] = useState(0)
  const load = useCallback(
    () => evaluationApi.listAssignments({ assignmentId: projectId, status: 'ACTIVE', page, size: 10 }),
    [projectId, page],
  )
  const { data, loading, error } = useRemoteData(load, revision + retry)
  const sheetLabels = { DRAFT: 'Đang chấm', SUBMITTED: 'Đã nộp', LOCKED: 'Đã khóa' }
  return (
    <section className="page-card" aria-label="Xem trước điểm">
      <h3>Xem trước điểm</h3>
      <p>Điểm các phiếu đã nộp trong phạm vi bạn được xem. Đây chưa phải kết quả đã chốt hoặc công bố.</p>
      <RemoteState loading={loading} error={error} retry={() => setRetry((value) => value + 1)} />
      {data && (
        <>
          {!data.content.length ? (
            <p>Chưa có phân công chấm trong phạm vi bạn được xem.</p>
          ) : (
            <div className="table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Thành phần</th>
                    <th>Giảng viên</th>
                    <th>Trạng thái phiếu</th>
                    <th>Điểm đã nộp</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((item) => (
                    <tr key={item.id}>
                      <td>{item.componentName}</td>
                      <td>{item.lecturerName}</td>
                      <td>{item.scoreSheetStatus ? sheetLabels[item.scoreSheetStatus] : 'Chưa chấm'}</td>
                      <td>
                        {item.submittedScore != null
                          ? `${Number(item.submittedScore).toFixed(2)}/10`
                          : '—'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {data.totalPages > 1 && (
            <PageControls
              label="Phân trang phiếu điểm xem trước"
              page={page}
              totalPages={data.totalPages}
              loading={loading}
              onPage={setPage}
            />
          )}
        </>
      )}
    </section>
  )
}
