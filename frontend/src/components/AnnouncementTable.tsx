import { Fragment, useId, useState } from 'react'
import { Search } from 'lucide-react'
import { Link } from 'react-router-dom'
import { formatDateTime } from '../utils/dateTime'

export type AnnouncementTableItem = {
  id: number
  title: string
  content: string
  senderName: string
  sentAt: string
  unread?: boolean
  attachmentPath?: string
  attachmentName?: string
  targetPath?: string
}

type AnnouncementTableProps = {
  label: string
  items: AnnouncementTableItem[]
  onOpen: (id: number) => void
}

export function AnnouncementTable({ label, items, onOpen }: AnnouncementTableProps) {
  const tableId = useId()
  const [search, setSearch] = useState('')
  const [expandedId, setExpandedId] = useState<number | null>(null)
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
              const expanded = expandedId === item.id
              const detailsId = `${tableId}-details-${item.id}`
              return (
                <Fragment key={item.id}>
                  <tr className={item.unread ? 'announcement-table__row--unread' : undefined}>
                    <td>
                      <button
                        className="announcement-table__title"
                        type="button"
                        aria-expanded={expanded}
                        aria-controls={expanded ? detailsId : undefined}
                        onClick={() => {
                          setExpandedId(expanded ? null : item.id)
                          if (!expanded) onOpen(item.id)
                        }}
                      >
                        {item.title}
                      </button>
                    </td>
                    <td>{item.senderName}</td>
                    <td><time dateTime={item.sentAt}>{formatDateTime(item.sentAt)}</time></td>
                  </tr>
                  {expanded && (
                    <tr className="announcement-table__details">
                      <td colSpan={3}>
                        <div id={detailsId}>
                          <p className="announcement-table__content">{item.content}</p>
                          {(item.attachmentPath || item.targetPath) && (
                            <div className="announcement-table__links">
                              {item.attachmentPath && (
                                <a className="text-button" href={item.attachmentPath}>
                                  {item.attachmentName || 'Tệp đính kèm'}
                                </a>
                              )}
                              {item.targetPath && item.targetPath !== '/announcements' && (
                                <Link className="text-button" to={item.targetPath}>
                                  Mở liên quan
                                </Link>
                              )}
                            </div>
                          )}
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
