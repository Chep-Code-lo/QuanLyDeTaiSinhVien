import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { academicApi } from '../api/academic'
import { announcementApi } from '../api/announcements'
import { notificationApi } from '../api/notifications'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import type { AnnouncementResponse, FacultyResponse, NotificationResponse } from '../types/domain'

const messageOf = (error: any) => error.response?.data?.message || 'Không thể thực hiện thao tác. Vui lòng thử lại.'

export function AnnouncementsPage() {
  const { user, hasAppointment, hasRole } = useAuth()
  const { showError, showSuccess } = useToast()
  const [announcements, setAnnouncements] = useState<AnnouncementResponse[]>([])
  const [notifications, setNotifications] = useState<NotificationResponse[]>([])
  const [faculties, setFaculties] = useState<FacultyResponse[]>([])
  const [managed, setManaged] = useState<AnnouncementResponse[]>([])
  const [facultyId, setFacultyId] = useState<number>()
  const [loading, setLoading] = useState(true)
  const [managing, setManaging] = useState(false)
  const [editing, setEditing] = useState<AnnouncementResponse>()
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [attachmentFileId, setAttachmentFileId] = useState('')
  const canManage = hasRole('ADMIN') || hasAppointment('DEAN')
  const allowedFacultyIds = useMemo(() => {
    if (hasRole('ADMIN')) return faculties.map((faculty) => faculty.id)
    return [...new Set((user?.activeAppointments ?? []).filter((item) => item.positionCode === 'DEAN').map((item) => item.facultyId))]
  }, [faculties, user, hasRole])

  const loadPublic = async () => {
    setLoading(true)
    try {
      const [official, personal] = await Promise.all([announcementApi.list({ size: 100 }), notificationApi.list(0, 100)])
      setAnnouncements(official.content)
      setNotifications(personal.content)
    } catch (error) {
      showError(messageOf(error))
    } finally {
      setLoading(false)
    }
  }

  const loadManaged = async (id = facultyId) => {
    if (!id || !canManage) return
    try {
      const data = await announcementApi.listForManagement({ facultyId: id, size: 100 })
      setManaged(data.content)
    } catch (error) {
      showError(messageOf(error))
    }
  }

  useEffect(() => {
    void loadPublic()
    if (canManage) academicApi.getFaculties().then(setFaculties).catch(() => undefined)
  }, [user?.id])

  useEffect(() => {
    if (!facultyId && allowedFacultyIds.length) setFacultyId(allowedFacultyIds[0])
  }, [allowedFacultyIds, facultyId])

  useEffect(() => {
    if (managing && facultyId) void loadManaged(facultyId)
  }, [facultyId, managing])

  const markRead = async (item: NotificationResponse) => {
    if (item.readAt) return
    try {
      const updated = await notificationApi.markRead(item.id)
      setNotifications((items) => items.map((candidate) => candidate.id === item.id ? updated : candidate))
    } catch (error) {
      showError(messageOf(error))
    }
  }

  const resetEditor = () => {
    setEditing(undefined)
    setTitle('')
    setContent('')
    setAttachmentFileId('')
  }

  const save = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!title.trim() || !content.trim() || (!editing && !facultyId)) return
    const optionalAttachment = attachmentFileId.trim() ? Number(attachmentFileId) : null
    if (attachmentFileId.trim() && (!Number.isInteger(optionalAttachment) || optionalAttachment <= 0)) {
      showError('Mã tệp đính kèm không hợp lệ.')
      return
    }
    try {
      if (editing) {
        await announcementApi.update(editing.id, { title: title.trim(), content: content.trim(), attachmentFileId: optionalAttachment })
        showSuccess('Đã cập nhật bản nháp.')
      } else {
        await announcementApi.create({ facultyId: facultyId!, title: title.trim(), content: content.trim(), attachmentFileId: optionalAttachment })
        showSuccess('Đã tạo thông báo ở trạng thái nháp.')
      }
      resetEditor()
      await Promise.all([loadManaged(), loadPublic()])
    } catch (error) {
      showError(messageOf(error))
    }
  }

  const edit = (announcement: AnnouncementResponse) => {
    setEditing(announcement)
    setTitle(announcement.title)
    setContent(announcement.content)
    setAttachmentFileId(announcement.attachmentFileId ? String(announcement.attachmentFileId) : '')
  }

  const transition = async (announcement: AnnouncementResponse, action: 'publish' | 'archive' | 'remove') => {
    try {
      if (action === 'publish') await announcementApi.publish(announcement.id)
      if (action === 'archive') await announcementApi.archive(announcement.id)
      if (action === 'remove') await announcementApi.remove(announcement.id)
      showSuccess(action === 'publish' ? 'Đã công bố thông báo.' : action === 'archive' ? 'Đã lưu trữ thông báo.' : 'Đã xóa thông báo.')
      await Promise.all([loadManaged(), loadPublic()])
    } catch (error) {
      showError(messageOf(error))
    }
  }

  return <>
    <PageHeader title="Thông báo" description="Thông báo chính thức theo khoa và các nhắc việc cá nhân." />
    {canManage && <section className="page-card"><div className="content-card__header"><div><h2>Quản lý thông báo khoa</h2><span>Tạo nháp, công bố và lưu trữ thông báo trong phạm vi khoa được phân quyền.</span></div><button className="btn btn-secondary" onClick={() => setManaging((value) => !value)}>{managing ? 'Ẩn quản lý' : 'Mở quản lý'}</button></div>
      {managing && <><div className="inline-form"><select className="form-select" value={facultyId ?? ''} onChange={(event) => setFacultyId(Number(event.target.value) || undefined)}>{allowedFacultyIds.map((id) => <option key={id} value={id}>{faculties.find((faculty) => faculty.id === id)?.name ?? `Khoa #${id}`}</option>)}</select></div>
        <form className="stack-form" onSubmit={save}><input className="form-input" value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Tiêu đề thông báo" maxLength={250} required /><textarea className="form-textarea" value={content} onChange={(event) => setContent(event.target.value)} placeholder="Nội dung thông báo" required /><input className="form-input" value={attachmentFileId} onChange={(event) => setAttachmentFileId(event.target.value)} placeholder="Mã tệp đính kèm (không bắt buộc)" inputMode="numeric" /><div className="inline-form"><button className="btn btn-primary">{editing ? 'Lưu bản nháp' : 'Tạo bản nháp'}</button>{editing && <button type="button" className="btn btn-secondary" onClick={resetEditor}>Hủy sửa</button>}</div></form>
        <div className="stack-list">{managed.map((announcement) => <article key={announcement.id} className="notification-item"><div><h2>{announcement.title} <StatusBadge status={announcement.status} /></h2><p>{announcement.content}</p><time>Tạo: {new Date(announcement.createdAt).toLocaleString('vi-VN')}</time></div><div className="inline-form">{announcement.status === 'DRAFT' && <><button className="text-button" onClick={() => edit(announcement)}>Sửa</button><button className="text-button" onClick={() => transition(announcement, 'publish')}>Công bố</button><button className="text-button" onClick={() => transition(announcement, 'remove')}>Xóa</button></>}{announcement.status === 'PUBLISHED' && <button className="text-button" onClick={() => transition(announcement, 'archive')}>Lưu trữ</button>}{announcement.status === 'ARCHIVED' && <button className="text-button" onClick={() => transition(announcement, 'remove')}>Xóa</button>}</div></article>)}</div></>}</section>}
    <section className="page-card"><h2 className="section-title">Thông báo chính thức</h2>{loading ? <div className="content-state"><span className="spinner" /><p>Đang tải thông báo...</p></div> : announcements.length === 0 ? <div className="content-state"><strong>Chưa có thông báo chính thức.</strong></div> : <div className="notification-list">{announcements.map((announcement) => <article key={announcement.id} className="notification-item"><div><h2>{announcement.title}</h2><p>{announcement.content}</p><small>{announcement.facultyName} · {announcement.createdByName}</small><time>{new Date(announcement.publishedAt ?? announcement.createdAt).toLocaleString('vi-VN')}</time></div>{announcement.attachmentPath && <a className="text-button" href={announcement.attachmentPath}>{announcement.attachmentName || 'Tệp đính kèm'}</a>}</article>)}</div>}</section>
    <section className="page-card"><h2 className="section-title">Nhắc việc cá nhân</h2>{loading ? null : notifications.length === 0 ? <div className="content-state"><strong>Bạn chưa có nhắc việc nào.</strong></div> : <div className="notification-list">{notifications.map((item) => <article key={item.id} className={`notification-item${item.readAt ? '' : ' notification-item--unread'}`} onClick={() => markRead(item)}><div><h2>{item.title}</h2><p>{item.content}</p><time>{new Date(item.createdAt).toLocaleString('vi-VN')}</time></div>{item.targetPath && <Link className="text-button" to={item.targetPath}>Mở liên quan</Link>}</article>)}</div>}</section>
  </>
}
