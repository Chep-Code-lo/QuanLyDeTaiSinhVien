import { useCallback, useRef, useState } from 'react'
import { topicApi } from '../api/topics'
import { groupApi } from '../api/groups'
import { errorMessage } from '../api/student'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'
import { useRemoteData } from '../hooks/useRemoteData'
import { RegistrationPeriodPicker } from './RegistrationPeriodPicker'
import { RemoteState } from './RemoteState'
import { PageControls } from './PageControls'
import type { TopicInput, TopicResponse } from '../types/domain'

export function TopicProposalForm({
  topic,
  onSaved,
  onClose,
}: {
  topic?: TopicResponse
  onSaved: () => void
  onClose: () => void
}) {
  const { user } = useAuth()
  const { showSuccess, showError } = useToast()
  const [periodId, setPeriodId] = useState(topic?.periodId ?? 0)
  const [code, setCode] = useState('')
  const [input, setInput] = useState<TopicInput>({
    title: topic?.title ?? '',
    description: topic?.description ?? '',
    objectives: topic?.objectives ?? '',
    expectedOutcomes: topic?.expectedOutcomes ?? '',
    prerequisites: topic?.prerequisites ?? '',
    visibilityType: topic?.visibilityType === 'RESERVED' ? 'RESERVED' : 'OPEN',
    reservedGroupId: topic?.reservedGroupId ?? null,
  })
  const [busy, setBusy] = useState(false)
  const sending = useRef(false)
  return (
    <section className="page-card">
      <h2>{topic ? `Chỉnh sửa ${topic.code}` : 'Đề xuất đề tài mới'}</h2>
      <form
        className="stack-form"
        onSubmit={async (event) => {
          event.preventDefault()
          if (
            sending.current ||
            !periodId ||
            !input.title.trim() ||
            !input.description.trim() ||
            !input.objectives.trim() ||
            !input.expectedOutcomes.trim() ||
            (!topic && !code.trim()) ||
            (input.visibilityType === 'RESERVED' && !input.reservedGroupId)
          )
            return
          sending.current = true
          setBusy(true)
          try {
            if (topic) await topicApi.update(topic.id, { ...input, version: topic.version })
            else await topicApi.create({ ...input, code: code.trim(), periodId })
            showSuccess(topic ? 'Đã cập nhật đề tài.' : 'Đã gửi đề xuất để xét duyệt.')
            onSaved()
          } catch (error) {
            showError(errorMessage(error))
          } finally {
            sending.current = false
            setBusy(false)
          }
        }}
      >
        <fieldset className="student-fieldset" disabled={busy}>
          {!topic && (
            <>
              <RegistrationPeriodPicker
                value={periodId}
                disabled={busy}
                onChange={(id) => {
                  setPeriodId(id)
                  setInput((value) => ({ ...value, reservedGroupId: null }))
                }}
              />
              <label>
                Mã đề xuất
                <input
                  className="form-input"
                  required
                  maxLength={30}
                  value={code}
                  onChange={(event) => setCode(event.target.value)}
                />
              </label>
            </>
          )}
          <label>
            Tên đề xuất
            <input
              className="form-input"
              required
              maxLength={300}
              value={input.title}
              onChange={(event) => setInput({ ...input, title: event.target.value })}
            />
          </label>
          {(['description', 'objectives', 'expectedOutcomes', 'prerequisites'] as const).map(
            (field) => (
              <label key={field}>
                {
                  {
                    description: 'Mô tả đề xuất',
                    objectives: 'Mục tiêu đề xuất',
                    expectedOutcomes: 'Kết quả dự kiến',
                    prerequisites: 'Yêu cầu bổ sung',
                  }[field]
                }
                <textarea
                  className="form-textarea"
                  required={field !== 'prerequisites'}
                  value={input[field] ?? ''}
                  onChange={(event) => setInput({ ...input, [field]: event.target.value })}
                />
              </label>
            ),
          )}
          <label>
            Phạm vi đăng ký
            <select
              className="form-select"
              value={input.visibilityType}
              onChange={(event) =>
                setInput({
                  ...input,
                  visibilityType: event.target.value as TopicInput['visibilityType'],
                  reservedGroupId: null,
                })
              }
            >
              <option value="OPEN">Công khai</option>
              <option value="RESERVED">Dành cho nhóm chỉ định</option>
            </select>
          </label>
          {input.visibilityType === 'RESERVED' && periodId > 0 && (
            <ReservedGroupPicker
              key={periodId}
              periodId={periodId}
              student={
                !!user?.roles.includes('STUDENT') &&
                !user.roles.includes('LECTURER') &&
                !user.roles.includes('ADMIN')
              }
              value={input.reservedGroupId ?? 0}
              onChange={(id) => setInput({ ...input, reservedGroupId: id || null })}
            />
          )}
          <div className="inline-form">
            <button className="btn btn-primary" disabled={busy || !periodId}>
              {busy ? 'Đang lưu...' : topic ? 'Lưu đề xuất' : 'Gửi đề xuất'}
            </button>
            <button type="button" className="btn btn-secondary" onClick={onClose}>
              Hủy chỉnh sửa
            </button>
          </div>
        </fieldset>
      </form>
    </section>
  )
}
function ReservedGroupPicker({
  periodId,
  student,
  value,
  onChange,
}: {
  periodId: number
  student: boolean
  value: number
  onChange: (id: number) => void
}) {
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const load = useCallback(async () => {
    if (!student) return groupApi.list({ periodId, page, size: 10 })
    const content = (await groupApi.mine()).filter((group) => group.periodId === periodId)
    return { content, totalPages: 1, page: 0, size: content.length, totalElements: content.length }
  }, [periodId, student, page])
  const { data, loading, error } = useRemoteData(load, revision)
  return (
    <div>
      <label>
        Nhóm được chỉ định
        <select
          className="form-select"
          required
          value={value}
          disabled={loading}
          onChange={(event) => onChange(Number(event.target.value))}
        >
          <option value={0}>Chọn nhóm</option>
          {value > 0 && !data?.content.some((group) => group.id === value) && (
            <option value={value}>Nhóm #{value}</option>
          )}
          {data?.content.map((group) => (
            <option key={group.id} value={group.id}>
              {group.name}
            </option>
          ))}
        </select>
      </label>
      <RemoteState
        loading={loading}
        error={error}
        retry={() => setRevision((value) => value + 1)}
      />
      {data && !student && (
        <PageControls
          label="Phân trang nhóm chỉ định"
          page={page}
          totalPages={data.totalPages}
          loading={loading}
          onPage={(next) => {
            setPage(next)
            onChange(0)
          }}
        />
      )}
    </div>
  )
}
