import { useState } from 'react'
import { Search } from 'lucide-react'
import { formatDateTime } from '../utils/dateTime'
import { NotificationDetailDialog } from '../features/notifications/components/NotificationDetailDialog'

export type AnnouncementTableItem = {
  id: number
  title: string
  content: string
  senderName: string
  sentAt: string
  unread?: boolean
  attachmentPath?: string
  attachmentName?: string
}

type AnnouncementTableProps = {
  label: string
  items: AnnouncementTableItem[]
  onOpen: (id: number) => void
  search: string
  onSearch: (search: string) => void
  loading: boolean
  error: string
  onRetry: () => void
}

export function AnnouncementTable({
  label,
  items,
  onOpen,
  search,
  onSearch,
  loading,
  error,
  onRetry,
}: AnnouncementTableProps) {
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const selected = items.find((item) => item.id === selectedId)

  return (
    <div className="announcement-inbox">
      <div className="announcement-search">
        <Search size={17} aria-hidden="true" />
        <input
          type="search"
          maxLength={250}
          value={search}
          onChange={(event) => onSearch(event.target.value)}
          placeholder="Tìm kiếm thông báo"
          aria-label={`Tìm kiếm ${label.toLocaleLowerCase('vi')}`}
        />
      </div>
      <div className="announcement-table-container">
        <table className="announcement-table" aria-label={label}>
          <thead>
            <tr>
              <th scope="col">Tiêu đề</th>
              <th scope="col">Người gửi</th>
              <th scope="col">Thời gian gửi</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={3} role="status">
                  Đang tải thông báo...
                </td>
              </tr>
            ) : error ? (
              <tr>
                <td colSpan={3}>
                  <p role="alert">{error}</p>
                  <button type="button" onClick={onRetry}>
                    Thử lại
                  </button>
                </td>
              </tr>
            ) : items.length === 0 ? (
              <tr>
                <td colSpan={3} className="announcement-table__empty">
                  Không tìm thấy thông báo phù hợp.
                </td>
              </tr>
            ) : (
              items.map((item) => {
                return (
                  <tr
                    key={item.id}
                    className={item.unread ? 'announcement-table__row--unread' : undefined}
                  >
                    <td>
                      <button
                        className="announcement-table__title"
                        type="button"
                        aria-haspopup="dialog"
                        onClick={() => {
                          setSelectedId(item.id)
                          onOpen(item.id)
                        }}
                      >
                        {item.title}
                      </button>
                    </td>
                    <td>{item.senderName}</td>
                    <td>
                      <time dateTime={item.sentAt}>{formatDateTime(item.sentAt)}</time>
                    </td>
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </div>
      {selected && <NotificationDetailDialog item={selected} onClose={() => setSelectedId(null)} />}
    </div>
  )
}

export function CommunicationPagination({
  label,
  page,
  size,
  totalPages,
  totalElements,
  loading,
  onPage,
  onSize,
}: {
  label: string
  page: number
  size: number
  totalPages: number
  totalElements: number
  loading: boolean
  onPage: (page: number) => void
  onSize: (size: number) => void
}) {
  return (
    <nav className="table-pagination" aria-label={`Phân trang ${label.toLocaleLowerCase('vi')}`}>
      <label>
        Số thông báo mỗi trang
        <select
          className="form-select"
          aria-label={`Số thông báo mỗi trang - ${label}`}
          value={size}
          onChange={(event) => onSize(Number(event.target.value))}
        >
          {[10, 20, 50].map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
      </label>
      <span>
        Trang {page + 1}/{Math.max(totalPages, 1)} · {totalElements} thông báo
      </span>
      <button
        type="button"
        className="btn btn-secondary btn-sm"
        disabled={loading || page === 0}
        onClick={() => onPage(page - 1)}
      >
        Trước
      </button>
      <button
        type="button"
        className="btn btn-secondary btn-sm"
        disabled={loading || page + 1 >= totalPages}
        onClick={() => onPage(page + 1)}
      >
        Sau
      </button>
    </nav>
  )
}
