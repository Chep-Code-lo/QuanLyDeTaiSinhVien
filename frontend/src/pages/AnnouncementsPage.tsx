import { formatDateTime } from '../utils/dateTime'
import './AnnouncementsPage.css'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { academicApi } from '../api/academic'
import { useCommunicationPage } from '../hooks/useCommunicationPage'
import { announcementApi } from '../api/announcements'
import { notificationApi } from '../api/notifications'
import { PageHeader } from '../components/PageHeader'
import { AnnouncementTable, CommunicationPagination } from '../components/AnnouncementTable'
import { StatusBadge } from '../components/StatusBadge'
import { ConfirmModal } from '../components/ConfirmModal'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import type {
  AnnouncementResponse,
  AnnouncementStatus,
  FacultyResponse,
  NotificationResponse,
} from '../types/domain'

const messageOf = (error: any) =>
  error.response?.data?.message || 'Không thể thực hiện thao tác. Vui lòng thử lại.'

const announcementTabs = [
  { id: 'general', label: 'Thông báo chung' },
  { id: 'personal', label: 'Thông báo cá nhân' },
] as const

export function AnnouncementsPage() {
  const { user, hasAppointment, hasRole } = useAuth()
  const { showError, showSuccess } = useToast()
  const [faculties, setFaculties] = useState<FacultyResponse[]>([])
  const [activeTab, setActiveTab] = useState<'general' | 'personal'>('general')
  const [generalReadState, setGeneralReadState] = useState<NotificationResponse[]>([])
  const [selectedFaculty, setSelectedFaculty] = useState<number>()
  const [managedStatus, setManagedStatus] = useState<AnnouncementStatus | ''>('')
  const [managing, setManaging] = useState(false)
  const [editing, setEditing] = useState<AnnouncementResponse>()
  const [pendingArchive, setPendingArchive] = useState<AnnouncementResponse>()
  const [archiving, setArchiving] = useState(false)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [attachmentFileId, setAttachmentFileId] = useState('')
  const allowedFacultyIds = useMemo(() => {
    return faculties
      .filter(
        (faculty) =>
          hasRole('ADMIN') ||
          user?.activeAppointments.some(
            (item) => item.positionCode === 'DEAN' && item.facultyId === faculty.id,
          ),
      )
      .map((faculty) => faculty.id)
  }, [faculties, user, hasRole])
  const canManage = allowedFacultyIds.length > 0
  const facultyId =
    selectedFaculty && allowedFacultyIds.includes(selectedFaculty)
      ? selectedFaculty
      : allowedFacultyIds[0]
  const canLoadFaculties = hasRole('ADMIN') || hasAppointment('DEAN')

  const fetchGeneral = useCallback(
    (query: { page: number; size: number; search: string }) => announcementApi.list(query),
    [],
  )
  const fetchPersonal = useCallback(
    (query: { page: number; size: number; search: string }) =>
      notificationApi.list(query.page, query.size, query.search),
    [],
  )
  const fetchManaged = useCallback(
    (query: { page: number; size: number; search: string }) =>
      announcementApi.listForManagement({
        ...query,
        facultyId: facultyId!,
        status: managedStatus || undefined,
      }),
    [facultyId, managedStatus],
  )
  const general = useCommunicationPage(String(user?.id), activeTab === 'general', fetchGeneral)
  const personal = useCommunicationPage(String(user?.id), activeTab === 'personal', fetchPersonal)
  const management = useCommunicationPage(
    JSON.stringify([user?.id, facultyId, managedStatus]),
    managing && canManage,
    fetchManaged,
  )
  const announcements = general.items
  const notifications = personal.items
  const managed = management.items
  const refreshLists = () => {
    management.refresh()
    general.refresh()
  }
  useEffect(() => {
    let active = true
    if (canLoadFaculties)
      academicApi
        .getFaculties()
        .then((items) => {
          if (active) setFaculties(items)
        })
        .catch((error) => {
          if (active) showError(messageOf(error))
        })
    return () => {
      active = false
    }
  }, [user?.id, canLoadFaculties, showError])
  useEffect(() => {
    let active = true
    if (activeTab === 'general' && !general.loading && !general.error) {
      notificationApi
        .forAnnouncements(announcements.map((item) => item.id))
        .then((items) => {
          if (active) setGeneralReadState(items)
        })
        .catch(() => {
          if (active) setGeneralReadState([])
        })
    }
    return () => {
      active = false
    }
  }, [activeTab, announcements, general.loading, general.error, user?.id])
  useEffect(() => {
    const updateReadState = (event: Event) => {
      const updated = (event as CustomEvent<{ notification?: NotificationResponse }>).detail
        ?.notification
      if (updated) {
        personal.update((item) => (item.id === updated.id ? updated : item))
        setGeneralReadState((items) =>
          items.map((item) => (item.id === updated.id ? updated : item)),
        )
      }
    }
    window.addEventListener('notifications-updated', updateReadState)
    return () => window.removeEventListener('notifications-updated', updateReadState)
  })
  const pageControls = (label: string, state: typeof general | typeof personal) => (
    <CommunicationPagination
      label={label}
      page={state.query.page}
      size={state.query.size}
      totalPages={state.result?.totalPages ?? 0}
      totalElements={state.result?.totalElements ?? 0}
      loading={state.loading}
      onPage={state.go}
      onSize={state.resize}
    />
  )

  const markRead = async (item: NotificationResponse) => {
    if (item.readAt) return
    try {
      const updated = await notificationApi.markRead(item.id)
      personal.update((candidate) => (candidate.id === item.id ? updated : candidate))
      setGeneralReadState((items) =>
        items.map((candidate) => (candidate.id === item.id ? updated : candidate)),
      )
      window.dispatchEvent(
        new CustomEvent('notifications-updated', { detail: { notification: updated } }),
      )
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
    if (
      optionalAttachment !== null &&
      (!Number.isInteger(optionalAttachment) || optionalAttachment <= 0)
    ) {
      showError('Mã tệp đính kèm không hợp lệ.')
      return
    }
    try {
      if (editing) {
        await announcementApi.update(editing.id, {
          title: title.trim(),
          content: content.trim(),
          attachmentFileId: optionalAttachment,
        })
        showSuccess('Đã cập nhật bản nháp.')
      } else {
        await announcementApi.create({
          facultyId: facultyId!,
          title: title.trim(),
          content: content.trim(),
          attachmentFileId: optionalAttachment,
        })
        showSuccess('Đã tạo thông báo ở trạng thái nháp.')
      }
      resetEditor()
      refreshLists()
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

  const publish = async (announcement: AnnouncementResponse) => {
    try {
      await announcementApi.publish(announcement.id)
      showSuccess('Đã công bố thông báo.')
      refreshLists()
    } catch (error) {
      showError(messageOf(error))
    }
  }

  const archive = async () => {
    if (!pendingArchive || archiving) return
    setArchiving(true)
    try {
      const updated = await announcementApi.archive(pendingArchive.id)
      management.update((item) => (item.id === updated.id ? updated : item))
      if (editing?.id === pendingArchive.id) resetEditor()
      setPendingArchive(undefined)
      showSuccess('Đã lưu trữ thông báo.')
      refreshLists()
    } catch (error) {
      showError(messageOf(error))
    } finally {
      setArchiving(false)
    }
  }

  return (
    <>
      <PageHeader title="Thông báo" />
      <div className="announcement-tabs" role="tablist" aria-label="Loại thông báo">
        {announcementTabs.map((tab, index) => (
          <button
            key={tab.id}
            id={`announcement-tab-${tab.id}`}
            className="announcement-tabs__tab"
            type="button"
            role="tab"
            aria-selected={activeTab === tab.id}
            aria-controls={`announcement-panel-${tab.id}`}
            tabIndex={activeTab === tab.id ? 0 : -1}
            onClick={() => setActiveTab(tab.id)}
            onKeyDown={(event) => {
              let nextIndex: number
              if (event.key === 'ArrowRight') nextIndex = (index + 1) % announcementTabs.length
              else if (event.key === 'ArrowLeft') {
                nextIndex = (index + announcementTabs.length - 1) % announcementTabs.length
              } else if (event.key === 'Home') nextIndex = 0
              else if (event.key === 'End') nextIndex = announcementTabs.length - 1
              else return
              event.preventDefault()
              const nextTab = announcementTabs[nextIndex].id
              setActiveTab(nextTab)
              document.getElementById(`announcement-tab-${nextTab}`)?.focus()
            }}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div
        className="announcement-panel"
        id="announcement-panel-general"
        role="tabpanel"
        aria-labelledby="announcement-tab-general"
        hidden={activeTab !== 'general'}
        tabIndex={0}
      >
        {canManage && (
          <section className="page-card announcement-management-card">
            <div className="content-card__header announcement-management-header">
              <div>
                <h2>Quản lý thông báo khoa</h2>
              </div>
              <button
                className="btn btn-secondary"
                aria-expanded={managing}
                onClick={() => setManaging((value) => !value)}
              >
                {managing ? 'Ẩn quản lý' : 'Mở quản lý'}
              </button>
            </div>
            {managing && (
              <>
                <form className="stack-form announcement-editor" onSubmit={save}>
                  <input
                    className="form-input"
                    value={title}
                    onChange={(event) => setTitle(event.target.value)}
                    placeholder="Tiêu đề thông báo"
                    aria-label="Tiêu đề thông báo"
                    maxLength={250}
                    required
                  />
                  <textarea
                    className="form-textarea"
                    value={content}
                    onChange={(event) => setContent(event.target.value)}
                    placeholder="Nội dung thông báo"
                    aria-label="Nội dung thông báo"
                    required
                  />
                  <input
                    className="form-input"
                    value={attachmentFileId}
                    onChange={(event) => setAttachmentFileId(event.target.value)}
                    placeholder="Mã tệp đính kèm (không bắt buộc)"
                    aria-label="Mã tệp đính kèm (không bắt buộc)"
                    inputMode="numeric"
                  />
                  <div className="announcement-editor__actions">
                    {editing && (
                      <button type="button" className="btn btn-secondary" onClick={resetEditor}>
                        Hủy sửa
                      </button>
                    )}
                    <button type="submit" className="btn btn-primary">
                      {editing ? 'Lưu bản nháp' : 'Tạo bản nháp'}
                    </button>
                  </div>
                </form>
                <div className="form-row">
                  <label>
                    Khoa quản lý
                    <select
                      aria-label="Khoa quản lý"
                      value={facultyId}
                      onChange={(event) => {
                        setSelectedFaculty(Number(event.target.value))
                        management.go(0)
                        resetEditor()
                      }}
                    >
                      {faculties
                        .filter((item) => allowedFacultyIds.includes(item.id))
                        .map((item) => (
                          <option key={item.id} value={item.id}>
                            {item.name}
                          </option>
                        ))}
                    </select>
                  </label>
                  <label>
                    Trạng thái thông báo
                    <select
                      aria-label="Trạng thái thông báo"
                      value={managedStatus}
                      onChange={(event) => {
                        setManagedStatus(event.target.value as AnnouncementStatus | '')
                        management.go(0)
                      }}
                    >
                      <option value="">Tất cả</option>
                      <option value="DRAFT">Nháp</option>
                      <option value="PUBLISHED">Đã công bố</option>
                      <option value="ARCHIVED">Đã lưu trữ</option>
                    </select>
                  </label>
                  <input
                    type="search"
                    maxLength={250}
                    aria-label="Tìm kiếm thông báo quản lý"
                    placeholder="Tìm kiếm thông báo"
                    value={management.query.search}
                    onChange={(event) => management.search(event.target.value)}
                  />
                </div>
                {management.loading ? (
                  <p role="status">Đang tải thông báo...</p>
                ) : management.error ? (
                  <div role="alert">
                    {management.error}
                    <button onClick={management.refresh}>Thử lại</button>
                  </div>
                ) : !managed.length ? (
                  <p>Không tìm thấy thông báo phù hợp.</p>
                ) : null}
                <div className="stack-list">
                  {(!management.loading && !management.error ? managed : []).map((announcement) => (
                    <article
                      key={announcement.id}
                      className="notification-item announcement-management-item"
                    >
                      <div className="announcement-management-copy">
                        <h2>{announcement.title}</h2>
                        <p>{announcement.content}</p>
                        <time>Tạo: {formatDateTime(announcement.createdAt)}</time>
                      </div>
                      <div className="announcement-management-actions">
                        <StatusBadge status={announcement.status} />
                        {announcement.status === 'DRAFT' && (
                          <>
                            <button
                              type="button"
                              className="btn btn-secondary btn-sm"
                              onClick={() => edit(announcement)}
                            >
                              Sửa
                            </button>
                            <button
                              type="button"
                              className="btn btn-primary btn-sm"
                              onClick={() => publish(announcement)}
                            >
                              Công bố
                            </button>
                          </>
                        )}
                        {announcement.status !== 'ARCHIVED' && (
                          <button
                            type="button"
                            className="btn btn-secondary btn-sm"
                            onClick={() => setPendingArchive(announcement)}
                          >
                            Lưu trữ
                          </button>
                        )}
                      </div>
                    </article>
                  ))}
                </div>
                <CommunicationPagination
                  label="Quản lý thông báo khoa"
                  page={management.query.page}
                  size={management.query.size}
                  totalPages={management.result?.totalPages ?? 0}
                  totalElements={management.result?.totalElements ?? 0}
                  loading={management.loading}
                  onPage={management.go}
                  onSize={management.resize}
                />
              </>
            )}
          </section>
        )}
        <section className="page-card announcement-inbox-card">
          <h2 className="section-title">Thông báo chung</h2>
          <AnnouncementTable
            label="Thông báo chung"
            search={general.query.search}
            onSearch={general.search}
            loading={general.loading}
            error={general.error}
            onRetry={general.refresh}
            items={announcements.map((announcement) => ({
              id: announcement.id,
              title: announcement.title,
              content: announcement.content,
              senderName: announcement.createdByName,
              sentAt: announcement.publishedAt ?? announcement.createdAt,
              attachmentPath: announcement.attachmentPath,
              attachmentName: announcement.attachmentName,
              unread: generalReadState.some(
                (item) =>
                  item.targetPath === '/announcements' &&
                  item.title === announcement.title &&
                  item.content === announcement.content &&
                  !item.readAt,
              ),
            }))}
            onOpen={(id) => {
              const announcement = announcements.find((item) => item.id === id)
              if (!announcement) return
              generalReadState
                .filter(
                  (item) =>
                    item.targetPath === '/announcements' &&
                    item.title === announcement.title &&
                    item.content === announcement.content,
                )
                .forEach((item) => {
                  void markRead(item)
                })
            }}
          />
          {pageControls('Thông báo chung', general)}
        </section>
      </div>
      <section
        className="page-card announcement-panel announcement-inbox-card"
        id="announcement-panel-personal"
        role="tabpanel"
        aria-labelledby="announcement-tab-personal"
        hidden={activeTab !== 'personal'}
        tabIndex={0}
      >
        <h2 className="section-title">Thông báo cá nhân</h2>
        <AnnouncementTable
          label="Thông báo cá nhân"
          search={personal.query.search}
          onSearch={personal.search}
          loading={personal.loading}
          error={personal.error}
          onRetry={personal.refresh}
          items={notifications.map((item) => ({
            id: item.id,
            title: item.title,
            content: item.content,
            senderName:
              item.targetPath === '/announcements'
                ? (announcements.find(
                    (announcement) =>
                      announcement.title === item.title && announcement.content === item.content,
                  )?.createdByName ?? 'Hệ thống')
                : 'Hệ thống',
            sentAt: item.createdAt,
            unread: !item.readAt,
          }))}
          onOpen={(id) => {
            const item = notifications.find((notification) => notification.id === id)
            if (item) void markRead(item)
          }}
        />
        {pageControls('Thông báo cá nhân', personal)}
      </section>
      <ConfirmModal
        isOpen={!!pendingArchive}
        title="Xác nhận lưu trữ thông báo"
        message={`Bạn có muốn lưu trữ thông báo “${pendingArchive?.title ?? ''}”? Thông báo được giữ lại trong danh sách quản lý và không hiển thị ở Thông báo chung.`}
        confirmText="Lưu trữ"
        variant="primary"
        loading={archiving}
        onConfirm={archive}
        onCancel={() => {
          if (!archiving) setPendingArchive(undefined)
        }}
      />
    </>
  )
}
