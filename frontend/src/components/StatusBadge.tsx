import React from 'react'
import { componentTypeLabel, statusLabel } from '../utils/displayLabels'

interface StatusBadgeProps {
  status: string
  label?: string
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status, label }) => {
  const getBadgeClass = (st: string) => {
    switch (st?.toUpperCase()) {
      case 'DRAFT':
      case 'OPEN':
      case 'PENDING':
        return 'badge badge-warning'
      case 'LOCKED':
      case 'SUBMITTED':
      case 'ACTIVE':
      case 'ACCEPTED':
      case 'FINALIZED':
      case 'PUBLISHED':
      case 'CONFIRMED':
        return 'badge badge-success'
      case 'REVISION_REQUIRED':
      case 'CANCELLED':
      case 'EXPIRED':
      case 'REJECTED':
      case 'CLOSED':
        return 'badge badge-danger'
      default:
        return 'badge badge-info'
    }
  }

  const getLabel = (st: string) => {
    if (label) return label
    if (['REVIEW', 'COMMITTEE', 'OTHER'].includes(st?.toUpperCase())) return componentTypeLabel(st)
    return statusLabel(st)
  }

  return <span className={getBadgeClass(status)}>{getLabel(status)}</span>
}
