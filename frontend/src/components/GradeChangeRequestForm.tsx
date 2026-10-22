import { useRef, useState } from 'react'
import { evaluationApi } from '../api/evaluation'
import { errorMessage } from '../api/student'
import { useToast } from '../context/ToastContext'

export function GradeChangeRequestForm({ sheetId }: { sheetId: number }) {
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false)
  const sending = useRef(false)
  const { showError, showSuccess } = useToast()
  return (
    <section className="page-card">
      <h3>Yêu cầu điều chỉnh điểm</h3>
      <p>
        Phiếu chỉ được mở lại sau khi yêu cầu được duyệt. Phiếu đã dùng để chốt kết quả không thể mở
        lại.
      </p>
      {sent ? (
        <p role="status">Đã gửi yêu cầu. Theo dõi tại mục Yêu cầu đổi điểm.</p>
      ) : (
        <form
          className="stack-form"
          onSubmit={async (event) => {
            event.preventDefault()
            if (sending.current || !reason.trim()) return
            sending.current = true
            setBusy(true)
            try {
              await evaluationApi.requestGradeChange(sheetId, reason.trim())
              setSent(true)
              showSuccess('Đã gửi yêu cầu đổi điểm.')
            } catch (error) {
              showError(errorMessage(error))
            } finally {
              sending.current = false
              setBusy(false)
            }
          }}
        >
          <label>
            Lý do điều chỉnh
            <textarea
              className="form-textarea"
              required
              maxLength={5000}
              value={reason}
              disabled={busy}
              onChange={(event) => setReason(event.target.value)}
            />
          </label>
          <button className="btn btn-secondary" disabled={busy || !reason.trim()}>
            {busy ? 'Đang gửi...' : 'Gửi yêu cầu đổi điểm'}
          </button>
        </form>
      )}
    </section>
  )
}
