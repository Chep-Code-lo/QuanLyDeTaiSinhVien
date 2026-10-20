import React, { useId } from 'react'

interface ConfirmModalProps {
  isOpen: boolean
  title: string
  message: string
  confirmText?: string
  cancelText?: string
  variant?: 'danger' | 'warning' | 'primary'
  loading?: boolean
  onConfirm: () => void
  onCancel: () => void
}

export const ConfirmModal: React.FC<ConfirmModalProps> = ({
  isOpen,
  title,
  message,
  confirmText = 'Xác nhận',
  cancelText = 'Hủy',
  variant = 'primary',
  loading = false,
  onConfirm,
  onCancel,
}) => {
  const titleId = useId()
  const messageId = useId()
  if (!isOpen) return null

  const getConfirmBtnClass = () => {
    switch (variant) {
      case 'danger':
        return 'btn btn-danger'
      case 'warning':
        return 'btn btn-warning'
      default:
        return 'btn btn-primary'
    }
  }

  return (
    <div className="modal-backdrop fade-in" onClick={loading ? undefined : onCancel}>
      <div
        className="modal-content scale-in"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={messageId}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="modal-header">
          <h3 id={titleId} className="modal-title font-semibold text-lg">
            {title}
          </h3>
          <button
            type="button"
            className="btn-close"
            onClick={onCancel}
            disabled={loading}
            aria-label="Đóng hộp thoại"
          >
            &times;
          </button>
        </div>
        <div className="modal-body py-4">
          <p id={messageId} className="text-body">
            {message}
          </p>
        </div>
        <div
          className="modal-footer"
          style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}
        >
          <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={loading}>
            {cancelText}
          </button>
          <button
            type="button"
            className={getConfirmBtnClass()}
            onClick={onConfirm}
            disabled={loading}
          >
            {loading ? 'Đang xử lý...' : confirmText}
          </button>
        </div>
      </div>
    </div>
  )
}
