import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { academicApi } from '../api/academic'
import { topicApi } from '../api/topics'
import { errorMessage } from '../api/student'
import { PageHeader } from '../components/PageHeader'
import { PageControls } from '../components/PageControls'
import { RemoteState } from '../components/RemoteState'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { useRemoteData } from '../hooks/useRemoteData'
import { canManageDepartment } from '../utils/managementAccess'
import { statusLabel } from '../utils/displayLabels'

export function ProjectAssignmentsPage() {
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const [selected, setSelected] = useState<number | null>(null)
  const load = useCallback(() => academicApi.getProjectAssignments(undefined, page, 10), [page])
  const { data, loading, error } = useRemoteData(load, revision)
  return (
    <>
      <PageHeader title="Theo dõi đề tài được giao" />
      <section className="page-card">
        <RemoteState
          loading={loading}
          error={error}
          retry={() => setRevision((value) => value + 1)}
        />
        {data && (
          <>
            {!data.content.length && <p>Chưa có đề tài được giao.</p>}
            <div className="table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Đề tài</th>
                    <th>Nhóm</th>
                    <th>Đợt</th>
                    <th>Trạng thái</th>
                    <th>Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((project) => (
                    <tr key={project.id}>
                      <td>
                        {project.topicCode} · {project.topicTitle}
                      </td>
                      <td>{project.groupName}</td>
                      <td>{project.periodName}</td>
                      <td>{statusLabel(project.status)}</td>
                      <td>
                        <button
                          type="button"
                          className="btn btn-secondary"
                          onClick={() => setSelected(project.id)}
                        >
                          Quản lý {project.topicCode}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <PageControls
              label="Phân trang đề tài được giao"
              page={page}
              totalPages={data.totalPages}
              loading={loading}
              onPage={setPage}
            />
          </>
        )}
      </section>
      {selected && (
        <ProjectStatusEditor
          key={selected}
          id={selected}
          onClose={() => setSelected(null)}
          onChanged={() => setRevision((value) => value + 1)}
        />
      )}
    </>
  )
}
function ProjectStatusEditor({
  id,
  onChanged,
  onClose,
}: {
  id: number
  onChanged: () => void
  onClose: () => void
}) {
  const { user } = useAuth()
  const { showSuccess, showError } = useToast()
  const [revision, setRevision] = useState(0)
  const load = useCallback(async () => {
    const project = await academicApi.getProject(id)
    return { project, topic: await topicApi.get(project.topicId) }
  }, [id])
  const { data, loading, error } = useRemoteData(load, revision)
  const [pending, setPending] = useState<'COMPLETED' | 'CANCELLED' | null>(null)
  const [busy, setBusy] = useState(false)
  const sending = useRef(false)
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
  const canManage =
    !!data && canManageDepartment(user, data.topic.departmentId, data.topic.facultyId)
  return (
    <dialog
      ref={dialog}
      className="catalog-editor-dialog"
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault()
        if (sending.current) return
        if (pending) setPending(null)
        else onClose()
      }}
    >
      <div className="catalog-editor-dialog__heading">
        <h2 id={titleId}>{pending ? 'Xác nhận trạng thái đề tài' : 'Quản lý đề tài'}</h2>
        <button
          ref={closeButton}
          type="button"
          className="btn btn-secondary"
          disabled={busy}
          onClick={() => {
            if (pending) setPending(null)
            else onClose()
          }}
          aria-label="Đóng hộp thoại quản lý đề tài"
        >
          Đóng
        </button>
      </div>
      {!pending && (
        <>
          <RemoteState
            loading={loading}
            error={error}
            retry={() => setRevision((value) => value + 1)}
          />
          {data && (
            <>
              <h2>
                {data.project.topicCode} · {data.project.groupName}
              </h2>
              <p>{statusLabel(data.project.status)}</p>
              {canManage &&
                data.project.status === 'IN_PROGRESS' &&
                data.project.version !== undefined && (
                  <div className="inline-form">
                    <button
                      type="button"
                      className="btn btn-primary"
                      disabled={busy}
                      onClick={() => setPending('COMPLETED')}
                    >
                      Đánh dấu hoàn thành
                    </button>
                    <button
                      type="button"
                      className="btn btn-danger"
                      disabled={busy}
                      onClick={() => setPending('CANCELLED')}
                    >
                      Hủy giao đề tài
                    </button>
                  </div>
                )}
            </>
          )}
        </>
      )}
      {pending && (
        <>
          <p>{`Xác nhận ${pending === 'COMPLETED' ? 'hoàn thành' : 'hủy'} đề tài ${data?.project.topicCode ?? ''}? Thao tác ảnh hưởng trạng thái nhóm và không thể đổi lại qua màn hình này.`}</p>
          <div className="inline-form">
            <button
              type="button"
              className="btn btn-secondary"
              data-cancel
              disabled={busy}
              onClick={() => setPending(null)}
            >
              Hủy
            </button>
            <button
              type="button"
              className={pending === 'CANCELLED' ? 'btn btn-danger' : 'btn btn-primary'}
              disabled={busy}
              onClick={async () => {
                if (
                  !pending ||
                  !data ||
                  !canManage ||
                  data.project.version === undefined ||
                  sending.current
                )
                  return
                sending.current = true
                setBusy(true)
                try {
                  await academicApi.updateProjectStatus(id, {
                    status: pending,
                    version: data.project.version,
                  })
                  showSuccess('Đã cập nhật trạng thái đề tài.')
                  onChanged()
                } catch (error) {
                  showError(errorMessage(error))
                } finally {
                  sending.current = false
                  setBusy(false)
                  setPending(null)
                  setRevision((value) => value + 1)
                }
              }}
            >
              {busy ? 'Đang xử lý...' : 'Xác nhận'}
            </button>
          </div>
        </>
      )}
    </dialog>
  )
}
