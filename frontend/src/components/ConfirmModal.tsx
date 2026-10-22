import React, { useEffect, useId, useRef } from 'react'

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
  const contentRef = useRef<HTMLDivElement>(null)
  const latest = useRef({ loading, onCancel })
  useEffect(() => { latest.current = { loading, onCancel } }, [loading, onCancel])
  useEffect(() => {
    if (!isOpen) return
    const previous = document.activeElement as HTMLElement | null
    const focusable = () => Array.from(contentRef.current?.querySelectorAll<HTMLElement>(
      'button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]',
    ) ?? [])
    focusable()[0]?.focus()
    const keydown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        event.stopPropagation()
        if (!latest.current.loading) latest.current.onCancel()
      }
      if (event.key === 'Tab') {
        const elements = focusable()
        const first = elements[0]
        const last = elements[elements.length - 1]
        if (!first) { event.preventDefault(); contentRef.current?.focus(); return }
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
      }
    }
    document.addEventListener('keydown', keydown)
    return () => { document.removeEventListener('keydown', keydown); if (previous?.isConnected) previous.focus() }
  }, [isOpen])
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
        ref={contentRef}
        tabIndex={-1}
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
