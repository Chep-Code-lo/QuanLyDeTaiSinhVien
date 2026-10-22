import { formatDate } from '../utils/dateTime'
import { AnimatedSelect } from '../components/AnimatedSelect'
import { Fragment, useEffect, useState } from 'react'
import { topicApi } from '../api/topics'
import { useSearchParams } from 'react-router-dom'
import { PageHeader } from '../components/PageHeader'
import { RegistrationTopicForm } from '../components/RegistrationTopicForm'
import { StatusBadge } from '../components/StatusBadge'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { useRequestLoading } from '../hooks/useRequestLoading'
import type { TopicResponse, TopicStatus } from '../types/domain'

export function TopicsPage() {
  const { hasAppointment, hasRole } = useAuth()
  const { showError } = useToast()
  const student = hasRole('STUDENT')
  const canCreateTopic =
    !student && (hasRole('ADMIN') || hasAppointment('DEAN') || hasAppointment('DEPARTMENT_HEAD'))
  const [topics, setTopics] = useState<TopicResponse[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [status, setStatus] = useState<TopicStatus>(student ? 'PUBLISHED' : '')
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('q') ?? ''
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [loading, setLoading] = useRequestLoading(JSON.stringify([student, status, query, page, revision]))
  useEffect(() => {
    let active = true
    const visibleStatus = student ? 'PUBLISHED' : status || undefined
    const timer = window.setTimeout(() => { void topicApi.list({ status: visibleStatus, q: query.trim() || undefined, page, size: 10 })
      .then((data) => {
        if (!active) return
        setTopics(data.content)
        setError('')
        setTotalPages(data.totalPages)
        if (data.totalPages > 0 && page >= data.totalPages) setPage(data.totalPages - 1)
      })
      .catch((error) => {
        if (!active) return
        const message = error.response?.data?.message || 'Không thể tải danh sách đề tài.'
        setError(message)
        showError(message)
      })
      .finally(() => { if (active) setLoading(false) })
    }, 200)
    return () => { active = false; window.clearTimeout(timer) }
  }, [showError, status, revision, student, page, query, setLoading])
  return (
    <>
      <PageHeader
        title={student ? 'Danh sách đề tài đã công bố' : 'Đề tài & Dự án'}
      />
      {canCreateTopic && (
        <RegistrationTopicForm onCreated={() => { setPage(0); setRevision((value) => value + 1) }} />
      )}
      <section className="page-card">
        <div className="topics-toolbar">
          <label className="search-field">
            <span aria-hidden="true">⌕</span>
            <input
              aria-label="Tìm đề tài"
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
        ) : topics.length === 0 ? (
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
                {topics.map((topic) => (
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
        {totalPages > 1 && (
          <div className="table-pagination">
            <span>
              Trang {page + 1} / {totalPages}
            </span>
            <div>
              <button
                className="btn btn-secondary btn-sm"
                disabled={loading || page === 0}
                onClick={() => setPage(page - 1)}
              >
                Trước
              </button>
              <button
                className="btn btn-secondary btn-sm"
                disabled={loading || page >= totalPages - 1}
                onClick={() => setPage(page + 1)}
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
