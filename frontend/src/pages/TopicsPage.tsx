import { formatDate } from '../utils/dateTime'
import { Fragment, useEffect, useState } from 'react'
import { topicApi } from '../api/topics'
import { useSearchParams } from 'react-router-dom'
import { PageHeader } from '../components/PageHeader'
import { RegistrationTopicForm } from '../components/RegistrationTopicForm'
import { StatusBadge } from '../components/StatusBadge'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { useRequestLoading } from '../hooks/useRequestLoading'
import type { TopicResponse } from '../types/domain'

export function TopicsPage({ embedded = false }: { embedded?: boolean }) {
  const { hasAppointment, hasRole } = useAuth()
  const { showError } = useToast()
  const student = hasRole('STUDENT')
  const canCreateTopic =
    !student && hasRole('LECTURER') && (hasAppointment('DEAN') || hasAppointment('DEPARTMENT_HEAD'))
  const [topics, setTopics] = useState<TopicResponse[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('q') ?? ''
  const [searchInput, setSearchInput] = useState(query)
  const [composing, setComposing] = useState(false)
  const [previousQuery, setPreviousQuery] = useState(query)
  if (previousQuery !== query) {
    setPreviousQuery(query)
    setSearchInput(query)
    setPage(0)
  }
  useEffect(() => {
    if (composing || searchInput === query) return
    const timer = window.setTimeout(() => {
      setPage(0)
      setSearchParams(
        (previous) => {
          const next = new URLSearchParams(previous)
          if (searchInput) next.set('q', searchInput)
          else next.delete('q')
          return next
        },
        { replace: true },
      )
    }, 200)
    return () => window.clearTimeout(timer)
  }, [searchInput, query, composing, setSearchParams])
  const [error, setError] = useState('')
  const [revision, setRevision] = useState(0)
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [loading, setLoading] = useRequestLoading(JSON.stringify([query, page, revision]))
  useEffect(() => {
    let active = true
    const timer = window.setTimeout(() => {
      void topicApi
        .list({ status: 'PUBLISHED', q: query.trim() || undefined, page, size: 10 })
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
        .finally(() => {
          if (active) setLoading(false)
        })
    }, 200)
    return () => {
      active = false
      window.clearTimeout(timer)
    }
  }, [showError, revision, page, query, setLoading])
  return (
    <>
      {!embedded && <PageHeader title="Danh sách đề tài đã công bố" />}
      {canCreateTopic && (
        <RegistrationTopicForm
          onCreated={() => {
            setPage(0)
            setRevision((value) => value + 1)
          }}
        />
      )}
      <section className="page-card">
        <div className="topics-toolbar">
          <label className="search-field">
            <span aria-hidden="true">⌕</span>
            <input
              aria-label="Tìm đề tài"
              spellCheck={false}
              autoCorrect="off"
              autoCapitalize="none"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              onCompositionStart={() => setComposing(true)}
              onCompositionEnd={(event) => {
                setSearchInput(event.currentTarget.value)
                setComposing(false)
              }}
              placeholder="Tìm mã, tên đề tài, bộ môn, GVHD..."
            />
          </label>
        </div>
        {loading ? (
          <div className="content-state">
            <span className="spinner" />
            <p>Đang tải danh sách đề tài...</p>
          </div>
        ) : error ? (
          <div className="content-state" role="alert">
            <p>{error}</p>
            <button className="btn btn-secondary" onClick={() => setRevision((value) => value + 1)}>
              Thử lại
            </button>
          </div>
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
                  <Fragment key={topic.id}>
                    <tr>
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
                      <td>
                        <button
                          type="button"
                          className="text-button"
                          aria-expanded={expandedId === topic.id}
                          onClick={() =>
                            setExpandedId((current) => (current === topic.id ? null : topic.id))
                          }
                        >
                          {expandedId === topic.id ? 'Thu gọn' : 'Xem chi tiết'}
                        </button>
                      </td>
                    </tr>
                    {expandedId === topic.id && (
                      <tr>
                        <td colSpan={7}>
                          <dl className="student-prose">
                            <dt>Mô tả</dt>
                            <dd>{topic.description || 'Chưa có mô tả'}</dd>
                            <dt>Mục tiêu</dt>
                            <dd>{topic.objectives || '—'}</dd>
                            <dt>Kết quả dự kiến</dt>
                            <dd>{topic.expectedOutcomes || '—'}</dd>
                            <dt>Yêu cầu</dt>
                            <dd>{topic.prerequisites || 'Không có yêu cầu bổ sung'}</dd>
                          </dl>
                          {topic.latestReview?.comment && (
                            <p>Phản hồi: {topic.latestReview.comment}</p>
                          )}
                        </td>
                      </tr>
                    )}
                  </Fragment>
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
