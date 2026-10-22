import { useState } from 'react'
import { Download } from 'lucide-react'
import { errorMessage, studentApi } from '../api/student'
import { useToast } from '../context/ToastContext'

export function RemoteState({
  loading,
  error,
  retry,
}: {
  loading: boolean
  error: string
  retry: () => void
}) {
  if (loading)
    return (
      <div className="content-state" role="status">
        <span className="spinner" />
        <p>Đang tải dữ liệu...</p>
      </div>
    )
  if (error)
    return (
      <div className="content-state" role="alert">
        <p>{error}</p>
        <button className="btn btn-secondary" onClick={retry}>
          Thử lại
        </button>
      </div>
    )
  return null
}
export function DownloadButton({ id, name }: { id: number; name: string }) {
  const [busy, setBusy] = useState(false)
  const { showError } = useToast()
  return (
    <button
      className="text-button"
      disabled={busy}
      onClick={async () => {
        setBusy(true)
        try {
          await studentApi.download(id, name)
        } catch (e) {
          showError(errorMessage(e))
        } finally {
          setBusy(false)
        }
      }}
    >
      <Download size={16} aria-hidden="true" />
      {busy ? 'Đang tải...' : `Tải ${name}`}
    </button>
  )
}
