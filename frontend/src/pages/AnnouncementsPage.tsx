import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { notificationApi } from '../api/notifications'
import { PageHeader } from '../components/PageHeader'
import { useToast } from '../context/ToastContext'
import type { NotificationResponse } from '../types/domain'

export function AnnouncementsPage() {
  const { showError } = useToast(); const [items, setItems] = useState<NotificationResponse[]>([]); const [loading, setLoading] = useState(true)
  useEffect(() => { notificationApi.list().then((data) => setItems(data.content)).catch((error) => showError(error.response?.data?.message || 'Không thể tải thông báo.')).finally(() => setLoading(false)) }, [showError])
  const markRead = async (item: NotificationResponse) => { if (item.readAt) return; try { const updated = await notificationApi.markRead(item.id); setItems((xs) => xs.map((x) => x.id === item.id ? updated : x)) } catch (error: any) { showError(error.response?.data?.message || 'Không thể cập nhật thông báo.') } }
  return <><PageHeader title="Thông báo" description="Thông báo chính thức và nhắc việc cá nhân phát sinh từ đăng ký, tiến độ, phản biện và công bố kết quả." />
    <section className="page-card">{loading ? <div className="content-state"><span className="spinner" /><p>Đang tải thông báo...</p></div> : items.length === 0 ? <div className="content-state"><strong>Bạn chưa có thông báo nào.</strong></div> : <div className="notification-list">{items.map((item) => <article key={item.id} className={`notification-item${item.readAt ? '' : ' notification-item--unread'}`} onClick={() => markRead(item)}><div><h2>{item.title}</h2><p>{item.content}</p><time>{new Date(item.createdAt).toLocaleString('vi-VN')}</time></div>{item.targetPath && <Link className="text-button" to={item.targetPath}>Mở liên quan</Link>}</article>)}</div>}</section>
  </>
}
