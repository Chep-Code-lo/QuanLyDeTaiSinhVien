import React, { useCallback, useState } from 'react'
import { vietnameseErrorMessage } from '../utils/errorMessages'
import type { ToastMessage } from './ToastContext'
import { ToastContext } from './ToastContext'

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<ToastMessage[]>([])

  const removeToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id))
  }, [])

  const addToast = useCallback(
    (type: ToastMessage['type'], message: string) => {
      const id = Math.random().toString(36).substring(2, 9)
      setToasts((prev) => [...prev, { id, type, message }])

      setTimeout(() => {
        removeToast(id)
      }, 4000)
    },
    [removeToast],
  )

  const showSuccess = useCallback((msg: string) => addToast('success', msg), [addToast])
  const showError = useCallback((msg: string) => addToast('error', vietnameseErrorMessage(msg)), [addToast])
  const showInfo = useCallback((msg: string) => addToast('info', msg), [addToast])

  return (
    <ToastContext.Provider value={{ toasts, showSuccess, showError, showInfo, removeToast }}>
      {children}
      <div className="toast-container">
        {toasts.map((toast) => (
          <div key={toast.id} className={`toast toast-${toast.type} slide-in`}>
            <span>{toast.message}</span>
            <button type="button" className="toast-close" onClick={() => removeToast(toast.id)}>
              &times;
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}
