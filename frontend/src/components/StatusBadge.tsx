import React from 'react'

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
    switch (st?.toUpperCase()) {
      case 'PENDING': return 'Chờ duyệt'
      case 'APPROVED': return 'Đã duyệt'
      case 'REJECTED': return 'Từ chối'
      case 'WITHDRAWN': return 'Đã rút'
      case 'FORMING': return 'Đang lập nhóm'
      case 'IN_PROGRESS': return 'Đang thực hiện'
      case 'COMPLETED': return 'Hoàn thành'
      case 'SCHEDULED': return 'Đã xếp lịch'
      case 'DRAFT':
        return 'Nháp'
      case 'LOCKED':
        return 'Đã khóa 🔒'
      case 'OPEN':
        return 'Mở'
      case 'SUBMITTED':
        return 'Đã nộp'
      case 'ACCEPTED':
        return 'Đã chấp nhận'
      case 'REVISION_REQUIRED':
        return 'Yêu cầu chỉnh sửa'
      case 'ACTIVE':
        return 'Hoạt động'
      case 'CANCELLED':
        return 'Đã hủy'
      case 'EXPIRED':
        return 'Hết hạn'
      case 'PUBLISHED':
        return 'Đã công bố'
      case 'ARCHIVED':
        return 'Đã lưu trữ'
      case 'CLOSED':
        return 'Đóng'
      case 'REVIEW':
        return 'Đánh giá Độc lập (Review)'
      case 'COMMITTEE':
        return 'Hội đồng (Committee)'
      default:
        return st
    }
  }

  return <span className={getBadgeClass(status)}>{getLabel(status)}</span>
}
