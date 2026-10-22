import { useEffect, useId, useRef, useState } from 'react'
import { Bell, X } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { notificationApi } from '../../../api/notifications'
import { errorMessage } from '../../../api/student'
import type { NotificationResponse } from '../../../types/domain'
import { useToast } from '../../../context/ToastContext'
import { NotificationDetailDialog } from './NotificationDetailDialog'

export function NotificationMenu({ unread, management, onOpen }: { unread: number | null; management: boolean; onOpen: () => void }) {
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<NotificationResponse[]>([])
  const [selected, setSelected] = useState<NotificationResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const root = useRef<HTMLDivElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const panelId = useId()
  const navigate = useNavigate()
  const { showError } = useToast()

  useEffect(() => {
    if (!open) return
    let active = true
    notificationApi.list(0, 8).then(page => { if (active) setItems(page.content) })
      .catch(e => { if (active) setError(errorMessage(e)) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [open, revision])

  useEffect(() => {
    if (!open) return
    const outside = (event: MouseEvent) => { if (!root.current?.contains(event.target as Node)) setOpen(false) }
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') { setOpen(false); trigger.current?.focus() } }
    document.addEventListener('mousedown', outside)
    document.addEventListener('keydown', escape)
    return () => { document.removeEventListener('mousedown', outside); document.removeEventListener('keydown', escape) }
  }, [open])

  const openDetail = async (item: NotificationResponse) => {
    setOpen(false); setSelected(item)
    if (item.readAt) return
    try {
      const updated = await notificationApi.markRead(item.id)
      setItems(previous => previous.map(candidate => candidate.id === item.id ? updated : candidate))
      setSelected(previous => previous?.id === item.id ? updated : previous)
      window.dispatchEvent(new CustomEvent('notifications-updated', { detail: { notification: updated } }))
    } catch (e) { showError(errorMessage(e)) }
  }

  return <div className="notification-menu" ref={root}>
    <button ref={trigger} className="notification-button" type="button" title="Thông báo" aria-label={unread ? 'Có thông báo mới' : 'Thông báo'} aria-expanded={open} aria-haspopup="dialog" aria-controls={open ? panelId : undefined} onClick={() => { if (!open) { setLoading(true); setError('') }; setOpen(value => !value); onOpen() }}>
      <Bell size={18} aria-hidden="true" />{unread !== null && unread > 0 && (management ? <span className="notification-button__dot" aria-hidden="true" /> : <span>{unread}</span>)}
    </button>
    {open && <section className="notification-popover" id={panelId} role="dialog" aria-label="Danh sách thông báo">
      <header className="notification-popover__heading"><h2>Thông báo</h2><button className="notification-close" type="button" aria-label="Đóng danh sách thông báo" onClick={() => { setOpen(false); trigger.current?.focus() }}><X size={18} aria-hidden="true" /></button></header>
      <div className="notification-popover__list">
        {loading ? <p className="notification-popover__state" role="status">Đang tải thông báo...</p> : error ? <div className="notification-popover__state" role="alert"><p>{error}</p><button type="button" className="text-button" onClick={() => { setLoading(true); setError(''); setRevision(value => value + 1) }}>Thử lại</button></div> : !items.length ? <p className="notification-popover__state">Chưa có thông báo.</p> : <ul>{items.map(item => <li key={item.id}><button className={`notification-popover__item${!item.readAt ? ' notification-popover__item--unread' : ''}`} type="button" onClick={() => void openDetail(item)}><span>{item.title}</span>{!item.readAt && <i aria-label="Chưa đọc" />}</button></li>)}</ul>}
      </div>
      <footer><button className="text-button" type="button" onClick={() => { setOpen(false); navigate('/announcements') }}>Xem tất cả thông báo</button></footer>
    </section>}
    {selected && <NotificationDetailDialog item={{ ...selected, sentAt: selected.createdAt }} onClose={() => { setSelected(null); trigger.current?.focus() }} />}
  </div>
}
