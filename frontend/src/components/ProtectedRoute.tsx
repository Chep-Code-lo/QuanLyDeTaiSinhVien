import React from 'react'
import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import type { PositionCode, RoleCode } from '../types/domain'
import { hasAcademicLeadership } from '../utils/managementAccess'

interface ProtectedRouteProps {
  roles?: RoleCode[]
  appointments?: PositionCode[]
  requireAcademicManagement?: boolean
  requireAcademicLeadership?: boolean
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({
  roles,
  appointments,
  requireAcademicManagement,
  requireAcademicLeadership,
}) => {
  const {
    user,
    loading,
    isAuthenticated,
    hasAnyRole,
    hasAppointment,
    canAccessAcademicManagement,
  } = useAuth()

  if (loading) {
    return (
      <div className="flex-center py-12">
        <div className="spinner" />
        <span className="ml-3 text-secondary">Đang tải thông tin đăng nhập...</span>
      </div>
    )
  }

  if (!isAuthenticated) {
    return <Navigate to="/" replace />
  }

  if (
    (requireAcademicManagement && !canAccessAcademicManagement()) ||
    (requireAcademicLeadership && !hasAcademicLeadership(user))
  ) {
    return (
      <div className="page-card error-container text-center py-12">
        <h2>Không có quyền truy cập</h2>
        <p className="text-secondary mt-2">
          Chức năng Quản lý Học thuật chỉ dành cho Quản trị viên hoặc Giảng viên có nhiệm vụ quản lý
          (Trưởng khoa / Trưởng bộ môn).
        </p>
      </div>
    )
  }

  if (roles && roles.length > 0 && !hasAnyRole(roles)) {
    return (
      <div className="page-card error-container text-center py-12">
        <h2>Không có quyền truy cập</h2>
        <p className="text-secondary mt-2">Bạn không có vai trò phù hợp để xem nội dung này.</p>
      </div>
    )
  }

  if (appointments && appointments.length > 0) {
    const hasAnyAppt = appointments.some((apt) => hasAppointment(apt))
    if (!hasAnyAppt) {
      return (
        <div className="page-card error-container text-center py-12">
          <h2>Không có quyền truy cập</h2>
          <p className="text-secondary mt-2">
            Chức năng này yêu cầu vị trí đảm nhiệm tương ứng (Trưởng Khoa / Trưởng Bộ Môn).
          </p>
        </div>
      )
    }
  }

  return <Outlet />
}
