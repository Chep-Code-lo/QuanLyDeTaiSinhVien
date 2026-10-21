import { formatDate } from '../utils/dateTime'
import { AnimatedSelect } from '../components/AnimatedSelect'
import { Fragment, useEffect, useMemo, useState } from 'react'
import { topicApi } from '../api/topics'
import { allPages } from '../api/student'
import { useSearchParams } from 'react-router-dom'
import { PageHeader } from '../components/PageHeader'
import { RegistrationTopicForm } from '../components/RegistrationTopicForm'
import { StatusBadge } from '../components/StatusBadge'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import type { TopicResponse, TopicStatus } from '../types/domain'

export function TopicsPage() {
  const { hasAppointment, hasRole } = useAuth()
  const { showError } = useToast()
  const student = hasRole('STUDENT')
  const canCreateTopic =
    !student && (hasRole('ADMIN') || hasAppointment('DEAN') || hasAppointment('DEPARTMENT_HEAD'))
  const [topics, setTopics] = useState<TopicResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState<TopicStatus>(student ? 'PUBLISHED' : '')
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('q') ?? ''
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const [expandedId, setExpandedId] = useState<number | null>(null)
  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    const visibleStatus = student ? 'PUBLISHED' : status || undefined
    allPages(page => topicApi.list({ status: visibleStatus, page, size: 100 }))
      .then((data) => {
        if (!active) return
        setTopics(data)
      })
      .catch((error) => {
        if (!active) return
        const message = error.response?.data?.message || 'Không thể tải danh sách đề tài.'
        setError(message)
        showError(message)
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [showError, status, revision, student])
  const filtered = useMemo(() => {
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
  const filteredTotalPages = Math.ceil(filtered.length / 10)
  const currentPage = Math.min(page, Math.max(0, filteredTotalPages - 1))
  const visible = filtered.slice(currentPage * 10, (currentPage + 1) * 10)
  return (
    <>
      <PageHeader
        title={student ? 'Danh sách đề tài đã công bố' : 'Đề tài & Dự án'}
        description={student ? 'Chỉ hiển thị các đề tài đã được khoa công bố và có thể xem để đăng ký.' : undefined}
      />
      {canCreateTopic && (
        <RegistrationTopicForm onCreated={(topic) => setTopics((current) => [topic, ...current])} />
      )}
      <section className="page-card">
        <div className="topics-toolbar">
          <label className="search-field">
            <span aria-hidden="true">⌕</span>
            <input
              value={query}
              onChange={(e) => { setPage(0); setSearchParams(previous => { const next = new URLSearchParams(previous); if (e.target.value) next.set('q', e.target.value); else next.delete('q'); return next }, { replace: true }) }}
              placeholder="Tìm mã, tên đề tài, bộ môn, GVHD..."
            />
          </label>
          {!student && <AnimatedSelect
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
          </AnimatedSelect>}
        </div>
        {loading ? (
          <div className="content-state">
            <span className="spinner" />
            <p>Đang tải danh sách đề tài...</p>
          </div>
        ) : error ? (
          <div className="content-state" role="alert"><p>{error}</p><button className="btn btn-secondary" onClick={() => setRevision(value => value + 1)}>Thử lại</button></div>
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
                  <th>Chi tiết</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((topic) => (
                  <Fragment key={topic.id}><tr>
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
                    <td><button type="button" className="text-button" aria-expanded={expandedId === topic.id} onClick={() => setExpandedId(current => current === topic.id ? null : topic.id)}>{expandedId === topic.id ? 'Thu gọn' : 'Xem chi tiết'}</button></td>
                  </tr>
                  {expandedId === topic.id && <tr><td colSpan={7}>
                    <dl className="student-prose"><dt>Mô tả</dt><dd>{topic.description || 'Chưa có mô tả'}</dd><dt>Mục tiêu</dt><dd>{topic.objectives || '—'}</dd><dt>Kết quả dự kiến</dt><dd>{topic.expectedOutcomes || '—'}</dd><dt>Yêu cầu</dt><dd>{topic.prerequisites || 'Không có yêu cầu bổ sung'}</dd></dl>
                    {topic.latestReview?.comment && <p>Phản hồi: {topic.latestReview.comment}</p>}
                  </td></tr>}</Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {filteredTotalPages > 1 && (
          <div className="table-pagination">
            <span>
              Trang {currentPage + 1} / {filteredTotalPages}
            </span>
            <div>
              <button
                className="btn btn-secondary btn-sm"
                disabled={loading || currentPage === 0}
                onClick={() => setPage(currentPage - 1)}
              >
                Trước
              </button>
              <button
                className="btn btn-secondary btn-sm"
                disabled={loading || currentPage >= filteredTotalPages - 1}
                onClick={() => setPage(currentPage + 1)}
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
