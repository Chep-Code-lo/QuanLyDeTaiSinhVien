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
}

export function AnnouncementTable({ label, items, onOpen }: AnnouncementTableProps) {
  const [search, setSearch] = useState('')
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const selected = items.find(item => item.id === selectedId)
  const query = search.trim().toLocaleLowerCase('vi')
  const visibleItems = items.filter((item) =>
    `${item.title} ${item.senderName}`.toLocaleLowerCase('vi').includes(query),
  )

  return (
    <div className="announcement-inbox">
      <div className="announcement-search">
        <Search size={17} aria-hidden="true" />
        <input
          type="search"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
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
            {visibleItems.length === 0 ? (
              <tr>
                <td colSpan={3} className="announcement-table__empty">
                  Không tìm thấy thông báo phù hợp.
                </td>
              </tr>
            ) : visibleItems.map((item) => {
              return (
                  <tr key={item.id} className={item.unread ? 'announcement-table__row--unread' : undefined}>
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
                    <td><time dateTime={item.sentAt}>{formatDateTime(item.sentAt)}</time></td>
                  </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      {selected && <NotificationDetailDialog item={selected} onClose={() => setSelectedId(null)} />}
    </div>
  )
}
