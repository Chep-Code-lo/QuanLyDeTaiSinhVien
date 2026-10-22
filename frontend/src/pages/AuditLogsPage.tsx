import { useCallback, useState } from 'react'
import { adminApi } from '../api/admin'
import { PageHeader } from '../components/PageHeader'
import { PageControls } from '../components/PageControls'
import { RemoteState } from '../components/RemoteState'
import { useRemoteData } from '../hooks/useRemoteData'
import { formatDateTime } from '../utils/dateTime'

export function AuditLogsPage() {
  const [query, setQuery] = useState('')
  const [entityType, setEntityType] = useState('')
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const load = useCallback(
    () => adminApi.listAuditLogs({ entityType: entityType || undefined, page, size: 20 }),
    [entityType, page],
  )
  const { data, loading, error } = useRemoteData(load, revision)
  return (
    <>
      <PageHeader title="Nhật ký thao tác" />
      <section className="page-card">
        <form
          className="inline-form"
          onSubmit={(event) => {
            event.preventDefault()
            setPage(0)
            setEntityType(query.trim())
            setRevision((value) => value + 1)
          }}
        >
          <label>
            Loại đối tượng
            <input
              className="form-input"
              value={query}
              placeholder="Ví dụ: TOPIC, USER"
              onChange={(event) => setQuery(event.target.value)}
            />
          </label>
          <button className="btn btn-secondary">Lọc nhật ký</button>
        </form>
        <RemoteState
          loading={loading}
          error={error}
          retry={() => setRevision((value) => value + 1)}
        />
        {data && (
          <>
            {!data.content.length && <p>Không có nhật ký phù hợp.</p>}
            <div className="table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Thời gian</th>
                    <th>Người thực hiện</th>
                    <th>Thao tác</th>
                    <th>Đối tượng</th>
                    <th>Kết quả</th>
                    <th>Thời lượng</th>
                    <th>Mã yêu cầu</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((row) => (
                    <tr key={row.id}>
                      <td>{formatDateTime(row.createdAt)}</td>
                      <td>{row.actorName || 'Hệ thống'}</td>
                      <td>{row.action}</td>
                      <td>
                        {row.entityType} {row.entityId ? `#${row.entityId}` : ''}
                      </td>
                      <td>{row.outcome === 'SUCCESS' ? 'Thành công' : 'Thất bại'}</td>
                      <td>{row.durationMs ?? '—'} ms</td>
                      <td>{row.requestId || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <PageControls
              label="Phân trang nhật ký"
              page={page}
              totalPages={data.totalPages}
              loading={loading}
              onPage={setPage}
            />
          </>
        )}
      </section>
    </>
  )
}
