import { useEffect, useId, useRef, useState } from 'react'
import { evaluationApi } from '../api/evaluation'
import { errorMessage } from '../api/student'
import { useToast } from '../context/ToastContext'
import type { DefenseSessionResponse, EvaluationCommitteeResponse } from '../types/domain'
import { DateTimeInput } from './DateTimeInput'

export function DefenseGradingDialog({
  defense,
  committee,
  onClose,
  onAssigned,
}: {
  defense: DefenseSessionResponse
  committee?: EvaluationCommitteeResponse
  onClose: () => void
  onAssigned: () => void
}) {
  const { showSuccess } = useToast()
  const dialog = useRef<HTMLDialogElement>(null)
  const sending = useRef(false)
  const titleId = useId()
  const [deadline, setDeadline] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const eligible =
    defense.status === 'SCHEDULED' && !!committee?.rubricId && !!committee.members.length
  useEffect(() => {
    const element = dialog.current
    const trigger = document.activeElement as HTMLElement | null
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    element?.showModal()
    element?.querySelector<HTMLInputElement>('input')?.focus()
    return () => {
      element?.close()
      document.body.style.overflow = previousOverflow
      if (trigger?.isConnected) trigger.focus()
    }
  }, [])
  return (
    <dialog
      ref={dialog}
      className="catalog-editor-dialog"
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault()
        if (!sending.current) onClose()
      }}
    >
      <div className="catalog-editor-dialog__heading">
        <h2 id={titleId}>Phân công chấm · {defense.topicCode}</h2>
        <button type="button" className="btn btn-secondary" disabled={busy} onClick={onClose}>
          Đóng
        </button>
      </div>
      <p>
        <strong>
          {defense.topicCode} · {defense.groupName}
        </strong>
      </p>
      <p>Hội đồng: {committee?.name ?? defense.committeeName}</p>
      <p>
        Lịch bảo vệ đã được lưu. Phân công sử dụng bộ tiêu chí và các thành viên đã lưu của hội
        đồng.
      </p>
      <ul>
        {committee?.members.map((member) => (
          <li key={member.id}>
            {member.lecturerName} ·{' '}
            {member.roleName ||
              { CHAIR: 'Chủ tịch', SECRETARY: 'Thư ký', MEMBER: 'Ủy viên' }[member.role]}
          </li>
        ))}
      </ul>
      {!eligible && (
        <p role="alert">
          Hội đồng cần có bộ tiêu chí và thành viên hợp lệ, cùng lịch bảo vệ đang hiệu lực trước khi
          phân công chấm.
        </p>
      )}
      {error && (
        <p role="alert">
          {error} Lịch bảo vệ vẫn được giữ lại; bạn có thể sửa hạn chấm và thử lại.
        </p>
      )}
      <form
        className="stack-form"
        onSubmit={async (event) => {
          event.preventDefault()
          const due = new Date(deadline)
          if (
            sending.current ||
            !eligible ||
            !committee?.rubricId ||
            !deadline ||
            !Number.isFinite(due.getTime())
          )
            return
          if (due.getTime() <= Date.now()) {
            setError('Hạn chót chấm điểm phải sau thời điểm hiện tại.')
            return
          }
          sending.current = true
          setBusy(true)
          setError('')
          try {
            const result = await evaluationApi.assignBatch(
              committee.members.map((member) => ({
                assignmentId: defense.assignmentId,
                defenseId: defense.id,
                rubricId: committee.rubricId!,
                lecturerId: member.lecturerId,
                deadlineAt: due.toISOString(),
              })),
            )
            showSuccess(
              `Đã tạo ${result.assignedCount} phân công chấm${result.skippedCount ? `; bỏ qua ${result.skippedCount} phân công đã có` : ''}.`,
            )
            onAssigned()
          } catch (error) {
            setError(errorMessage(error))
          } finally {
            sending.current = false
            setBusy(false)
          }
        }}
      >
        <label>
          Hạn chót chấm điểm *
          <DateTimeInput
            type="datetime-local"
            required
            value={deadline}
            disabled={busy || !eligible}
            onValueChange={setDeadline}
          />
        </label>
        <div className="inline-form">
          <button
            type="submit"
            className="btn btn-primary"
            disabled={busy || !eligible || !deadline}
          >
            {busy ? 'Đang phân công...' : 'Phân công chấm'}
          </button>
          <button type="button" className="btn btn-secondary" disabled={busy} onClick={onClose}>
            Để sau
          </button>
        </div>
      </form>
    </dialog>
  )
}
