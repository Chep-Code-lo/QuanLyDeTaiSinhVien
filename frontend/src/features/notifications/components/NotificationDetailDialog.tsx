import { useEffect, useId, useRef } from 'react'
import { X } from 'lucide-react'
import { formatDateTime } from '../../../utils/dateTime'
import './Notifications.css'

export type NotificationDetail = {
  title: string
  content: string
  sentAt: string
  senderName?: string
  attachmentPath?: string
  attachmentName?: string
}

export function NotificationDetailDialog({
  item,
  onClose,
}: {
  item: NotificationDetail
  onClose: () => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  useEffect(() => {
    const element = dialog.current
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    element?.showModal()
    return () => {
      element?.close()
      document.body.style.overflow = previousOverflow
    }
  }, [])

  return (
    <dialog
      ref={dialog}
      className="notification-detail"
      aria-labelledby={titleId}
      onClose={onClose}
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div className="notification-detail__inner">
        <header className="notification-detail__heading">
          <div>
            <h2 id={titleId}>{item.title}</h2>
            <p>
              {item.senderName ?? 'Hệ thống'} ·{' '}
              <time dateTime={item.sentAt}>{formatDateTime(item.sentAt)}</time>
            </p>
          </div>
          <button
            className="notification-close"
            type="button"
            onClick={onClose}
            aria-label="Đóng thông báo"
          >
            <X size={20} aria-hidden="true" />
          </button>
        </header>
        <div className="notification-detail__content">
          {item.content.replaceAll('(Asia/Ho_Chi_Minh)', '(giờ Việt Nam)')}
        </div>
        {item.attachmentPath && (
          <a className="text-button notification-detail__attachment" href={item.attachmentPath}>
            {item.attachmentName || 'Tệp đính kèm'}
          </a>
        )}
        <footer className="notification-detail__footer">
          <button className="btn btn-secondary" type="button" onClick={onClose}>
            Đóng
          </button>
        </footer>
      </div>
    </dialog>
  )
}
