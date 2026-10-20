import type { PositionCode, RoleCode } from '../types/domain'

export type NavigationItem = {
  icon: string
  label: string
  to: string
  roles?: RoleCode[]
  appointments?: PositionCode[]
  requireAcademicManagement?: boolean
  adminOnly?: boolean
}

export const navigationItems: NavigationItem[] = [
  { icon: '⌂', label: 'Tổng quan', to: '/dashboard' },
  { icon: '◫', label: 'Đề tài & Dự án', to: '/topics' },
  { icon: '◉', label: 'Nhóm đề tài', to: '/groups' },
  { icon: '✓', label: 'Đăng ký đề tài', to: '/registrations' },
  { icon: '◷', label: 'Tiến độ & Milestone', to: '/progress' },
  { icon: '★', label: 'Setup Đánh giá', to: '/evaluations', roles: ['LECTURER', 'ADMIN'] },
  { icon: '⚑', label: 'Gán Evaluator', to: '/evaluator-assignment', roles: ['LECTURER', 'ADMIN'] },
  {
    icon: '🏢',
    label: 'Quản lý Học thuật',
    to: '/academic-management',
    requireAcademicManagement: true,
  },
  {
    icon: '👑',
    label: 'Gán Chức vụ (Admin)',
    to: '/admin-appointments',
    adminOnly: true,
  },
  { icon: '★', label: 'Lịch bảo vệ & Kết quả', to: '/results', roles: ['STUDENT'] },
  { icon: '☻', label: 'Tài khoản', to: '/account' },
  { icon: '◌', label: 'Thông báo', to: '/announcements' },
]
