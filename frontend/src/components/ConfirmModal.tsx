import React from 'react'

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
    <div className="modal-backdrop fade-in" onClick={onCancel}>
      <div className="modal-content scale-in" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title font-semibold text-lg">{title}</h3>
          <button type="button" className="btn-close" onClick={onCancel}>
            &times;
          </button>
        </div>
        <div className="modal-body py-4">
          <p className="text-body">{message}</p>
        </div>
        <div className="modal-footer flex gap-3 justify-end">
          <button type="button" className="btn btn-secondary" onClick={onCancel} disabled={loading}>
            {cancelText}
          </button>
          <button type="button" className={getConfirmBtnClass()} onClick={onConfirm} disabled={loading}>
            {loading ? 'Đang xử lý...' : confirmText}
          </button>
        </div>
      </div>
    </div>
  )
}
