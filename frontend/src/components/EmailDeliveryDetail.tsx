import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { emailNotificationsApi } from '../api/emailNotifications'
import { useRemoteData } from '../hooks/useRemoteData'
import { RemoteState } from './RemoteState'
import { formatDateTime } from '../utils/dateTime'

export function EmailDeliveryDetail({ id, onClose }: { id: number; onClose: () => void }) {
  const [revision, setRevision] = useState(0)
  const load = useCallback(() => emailNotificationsApi.getLog(id), [id])
  const { data, loading, error } = useRemoteData(load, revision)
  const dialog = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  useEffect(() => {
    const element = dialog.current
    const trigger = document.activeElement as HTMLElement | null
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    element?.showModal()
    element?.querySelector<HTMLButtonElement>('button')?.focus()
    return () => {
      element?.close()
      document.body.style.overflow = previousOverflow
      if (trigger?.isConnected) trigger.focus()
    }
  }, [])
  return (
    <dialog
      ref={dialog}
      className="catalog-editor-dialog email-delivery-dialog"
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
    >
      <div className="catalog-editor-dialog__heading">
        <h2 id={titleId}>Lịch sử gửi email #{id}</h2>
        <button type="button" className="btn btn-secondary" onClick={onClose}>
          Đóng chi tiết email
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
            {data.log.subject} · {data.log.recipientEmail}
          </p>
          {!data.attempts.length && <p>Email đang chờ xử lý, chưa có lần gửi.</p>}
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Lần gửi</th>
                  <th>Thời gian</th>
                  <th>Kết quả</th>
                  <th>Thông tin lỗi</th>
                </tr>
              </thead>
              <tbody>
                {data.attempts.map((attempt) => (
                  <tr key={attempt.id}>
                    <td>{attempt.attemptNo}</td>
                    <td>{formatDateTime(attempt.createdAt)}</td>
                    <td>
                      {
                        {
                          PENDING: 'Chờ gửi',
                          SENDING: 'Đang gửi',
                          SENT: 'Đã gửi',
                          FAILED: 'Thất bại',
                        }[attempt.status]
                      }
                    </td>
                    <td>{attempt.errorMessage || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </dialog>
  )
}
