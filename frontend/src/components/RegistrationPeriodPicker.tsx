import { useCallback, useState } from 'react'
import { academicApi } from '../api/academic'
import { useRemoteData } from '../hooks/useRemoteData'
import { PageControls } from './PageControls'
import { RemoteState } from './RemoteState'

export function RegistrationPeriodPicker({
  value,
  onChange,
  disabled = false,
}: {
  value: number
  onChange: (id: number) => void
  disabled?: boolean
}) {
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const load = useCallback(() => academicApi.getRegistrationPeriods(undefined, page, 10), [page])
  const { data, loading, error } = useRemoteData(load, revision)
  return (
    <div>
      <label>
        Đợt đề xuất
        <select
          className="form-select"
          required
          value={value}
          disabled={disabled || loading}
          onChange={(event) => onChange(Number(event.target.value))}
        >
          <option value={0}>Chọn đợt</option>
          {data?.content.map((period) => (
            <option key={period.id} value={period.id}>
              {period.name}
            </option>
          ))}
        </select>
      </label>
      <RemoteState
        loading={loading}
        error={error}
        retry={() => setRevision((value) => value + 1)}
      />
      {data && (
        <PageControls
          label="Phân trang đợt đề xuất"
          page={page}
          totalPages={data.totalPages}
          loading={disabled || loading}
          onPage={(next) => {
            setPage(next)
            onChange(0)
          }}
        />
      )}
    </div>
  )
}
