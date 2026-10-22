import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { emailNotificationsApi } from '../api/emailNotifications'
import { useRemoteData } from '../hooks/useRemoteData'
import { PageControls } from './PageControls'
import { RemoteState } from './RemoteState'

export function EmailRecipientPreview({
  role,
  onClose,
}: {
  role: 'STUDENT' | 'LECTURER'
  onClose: () => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const load = useCallback(() => emailNotificationsApi.listRecipients({ role, page }), [role, page])
  const { data, loading, error } = useRemoteData(load, revision)

  useEffect(() => {
    const element = dialog.current
    const trigger = document.activeElement as HTMLElement | null
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    element?.showModal()
    return () => {
      element?.close()
      document.body.style.overflow = previousOverflow
      if (trigger?.isConnected) trigger.focus()
    }
  }, [])

  return (
    <dialog
      ref={dialog}
      className="catalog-editor-dialog email-recipient-preview"
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
    >
      <div className="catalog-editor-dialog__heading">
        <h2 id={titleId}>Danh sách người nhận</h2>
        <button type="button" className="btn btn-secondary" onClick={onClose}>
          Đóng
        </button>
      </div>
      <RemoteState
        loading={loading}
        error={error}
        retry={() => setRevision((value) => value + 1)}
      />
      {data && (
        <>
          <p>
            {data.totalElements} {role === 'STUDENT' ? 'sinh viên' : 'giảng viên'} sẽ nhận email.
          </p>
          {data.content.length === 0 ? (
            <p>Không có người nhận trong nhóm này.</p>
          ) : (
            <div className="table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Họ và tên</th>
                    <th>Email</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((person) => (
                    <tr key={person.userId}>
                      <td>{person.fullName}</td>
                      <td>{person.email}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {data.totalPages > 1 && (
            <PageControls
              page={page}
              totalPages={data.totalPages}
              loading={loading}
              onPage={setPage}
              label="Phân trang người nhận email"
            />
          )}
        </>
      )}
    </dialog>
  )
}
