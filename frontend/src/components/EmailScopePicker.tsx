import { useCallback, useState } from 'react'
import { academicApi } from '../api/academic'
import { useAuth } from '../context/AuthContext'
import { useRemoteData } from '../hooks/useRemoteData'
import { RemoteState } from './RemoteState'
import type { NotificationScopeType } from '../types/domain'

export interface EmailScopeSelection {
  scopeType: NotificationScopeType
  scopeId: number
}
export function EmailScopePicker({
  value,
  onChange,
  disabled,
}: {
  value: EmailScopeSelection
  onChange: (scope: EmailScopeSelection) => void
  disabled: boolean
}) {
  const { user } = useAuth()
  const admin = !!user?.roles.includes('ADMIN')
  const hasFaculty =
    admin || !!user?.activeAppointments.some((item) => item.positionCode === 'DEAN')
  const [revision, setRevision] = useState(0)
  const load = useCallback(
    () => (hasFaculty ? academicApi.getFaculties() : Promise.resolve([])),
    [hasFaculty],
  )
  const { data, loading, error } = useRemoteData(load, revision)
  if (!hasFaculty) return null
  const faculties =
    data?.filter(
      (faculty) =>
        admin ||
        user?.activeAppointments.some(
          (item) => item.positionCode === 'DEAN' && item.facultyId === faculty.id,
        ),
    ) ?? []
  return (
    <section className="page-card">
      <select
        aria-label="Phạm vi cấu hình email"
        className="form-select"
        disabled={disabled || loading}
        value={`${value.scopeType}:${value.scopeId}`}
        onChange={(event) => {
          const [scopeType, scopeId] = event.target.value.split(':')
          onChange({ scopeType: scopeType as NotificationScopeType, scopeId: Number(scopeId) })
        }}
      >
        {admin ? (
          <option value="SYSTEM:0">Toàn hệ thống</option>
        ) : (
          <option value={`LECTURER:${user?.id}`}>Cá nhân tôi</option>
        )}
        {faculties.map((faculty) => (
          <option key={faculty.id} value={`FACULTY:${faculty.id}`}>
            {/^khoa\s/iu.test(faculty.name.trim())
              ? faculty.name.trim()
              : `Khoa ${faculty.name.trim()}`}
          </option>
        ))}
      </select>
      <RemoteState
        loading={loading}
        error={error}
        retry={() => setRevision((value) => value + 1)}
      />
    </section>
  )
}
