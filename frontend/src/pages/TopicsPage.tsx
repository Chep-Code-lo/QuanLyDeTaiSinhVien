import { formatDate } from '../utils/dateTime'
import { AnimatedSelect } from '../components/AnimatedSelect'
import { useEffect, useMemo, useState } from 'react'
import { topicApi } from '../api/topics'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import { useToast } from '../context/ToastContext'
import type { TopicResponse, TopicStatus } from '../types/domain'

export function TopicsPage() {
  const { showError } = useToast()
  const [topics, setTopics] = useState<TopicResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [status, setStatus] = useState<TopicStatus>('')
  const [query, setQuery] = useState('')
  useEffect(() => {
    setLoading(true)
    topicApi
      .list({ status: status || undefined, page, size: 10 })
      .then((data) => {
        setTopics(data.content)
        setTotalPages(data.totalPages)
      })
      .catch((error) =>
        showError(error.response?.data?.message || 'Không thể tải danh sách đề tài.'),
      )
      .finally(() => setLoading(false))
  }, [page, showError, status])
  const visible = useMemo(() => {
    const text = query.trim().toLocaleLowerCase('vi')
    return text
      ? topics.filter((topic) =>
          [
            topic.code,
            topic.title,
            topic.departmentName,
            topic.supervisors.map((x) => x.lecturerName).join(' '),
          ]
            .join(' ')
            .toLocaleLowerCase('vi')
            .includes(text),
        )
      : topics
  }, [query, topics])
  return (
    <>
      <PageHeader title="Đề tài & Dự án" />
      <section className="page-card">
        <div className="topics-toolbar">
          <label className="search-field">
            <span aria-hidden="true">⌕</span>
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Tìm mã, tên đề tài, bộ môn, GVHD..."
            />
          </label>
          <AnimatedSelect
            wrapperClassName="topic-filter"
            value={status}
            onChange={(e) => {
              setPage(0)
              setStatus(e.target.value)
            }}
          >
            <option value="">Tất cả trạng thái</option>
            <option value="DRAFT">Nháp</option>
            <option value="PENDING">Chờ duyệt</option>
            <option value="APPROVED">Đã duyệt</option>
            <option value="PUBLISHED">Đã công bố</option>
            <option value="REJECTED">Từ chối</option>
          </AnimatedSelect>
        </div>
        {loading ? (
          <div className="content-state">
            <span className="spinner" />
            <p>Đang tải danh sách đề tài...</p>
          </div>
        ) : visible.length === 0 ? (
          <div className="content-state">
            <strong>Chưa có đề tài phù hợp.</strong>
            <p>Thử thay đổi bộ lọc hoặc từ khóa tìm kiếm.</p>
          </div>
        ) : (
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Mã đề tài</th>
                  <th>Tên đề tài</th>
                  <th>Bộ môn</th>
                  <th>GVHD</th>
                  <th>Trạng thái</th>
                  <th>Ngày tạo</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((topic) => (
                  <tr key={topic.id}>
                    <td className="topic-code">{topic.code}</td>
                    <td>
                      <strong>{topic.title}</strong>
                      <span className="table-subtext">{topic.periodName}</span>
                    </td>
                    <td>{topic.departmentName}</td>
                    <td>
                      {topic.supervisors.length
                        ? topic.supervisors.map((s) => s.lecturerName).join(', ')
                        : 'Chưa phân công'}
                    </td>
                    <td>
                      <StatusBadge status={topic.status} />
                    </td>
                    <td>{formatDate(topic.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {totalPages > 1 && (
          <div className="table-pagination">
            <span>
              Trang {page + 1} / {totalPages}
            </span>
            <div>
              <button
                className="btn btn-secondary btn-sm"
                disabled={page === 0}
                onClick={() => setPage((value) => value - 1)}
              >
                Trước
              </button>
              <button
                className="btn btn-secondary btn-sm"
                disabled={page >= totalPages - 1}
                onClick={() => setPage((value) => value + 1)}
              >
                Sau
              </button>
            </div>
          </div>
        )}
      </section>
    </>
  )
}
