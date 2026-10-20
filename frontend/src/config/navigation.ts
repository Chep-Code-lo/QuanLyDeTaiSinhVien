import {
  Bell,
  Building2,
  CircleUserRound,
  ClipboardCheck,
  Clock3,
  Crown,
  Flag,
  FolderKanban,
  House,
  Star,
  UserPlus,
  UsersRound,
  type LucideIcon,
} from 'lucide-react'
import type { PositionCode, RoleCode } from '../types/domain'

export type NavigationItem = {
  icon: LucideIcon
  label: string
  to: string
  roles?: RoleCode[]
  appointments?: PositionCode[]
  requireAcademicManagement?: boolean
  adminOnly?: boolean
}

export const navigationItems: NavigationItem[] = [
  { icon: House, label: 'Tổng quan', to: '/dashboard' },
  { icon: Bell, label: 'Thông báo', to: '/announcements' },
  { icon: FolderKanban, label: 'Đề tài & Dự án', to: '/topics' },
  { icon: UsersRound, label: 'Nhóm đề tài', to: '/groups' },
  { icon: ClipboardCheck, label: 'Đăng ký đề tài', to: '/registrations' },
  { icon: Clock3, label: 'Tiến độ & Mốc báo cáo', to: '/progress' },
  { icon: Star, label: 'Tiêu chuẩn đánh giá', to: '/evaluations', roles: ['LECTURER', 'ADMIN'] },
  {
    icon: Flag,
    label: 'Phân công hội đồng',
    to: '/evaluator-assignment',
    roles: ['LECTURER', 'ADMIN'],
  },
  {
    icon: Building2,
    label: 'Quản lý Học thuật',
    to: '/academic-management',
    requireAcademicManagement: true,
  },
  { icon: Crown, label: 'Bổ nhiệm chức vụ', to: '/admin-appointments', adminOnly: true },
  { icon: UserPlus, label: 'Tạo tài khoản', to: '/admin-users/new', adminOnly: true },
  { icon: CircleUserRound, label: 'Hồ sơ cá nhân', to: '/account' },
]
