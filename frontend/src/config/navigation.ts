import {
  Bell,
  BookOpen,
  Building2,
  CalendarCheck,
  ClipboardCheck,
  Flag,
  Home,
  ListChecks,
  Mail,
  ShieldCheck,
  UserRound,
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
  { icon: Home, label: 'Tổng quan', to: '/dashboard' },
  { icon: BookOpen, label: 'Đề tài & Dự án', to: '/topics' },
  { icon: UsersRound, label: 'Nhóm đề tài', to: '/groups' },
  { icon: ClipboardCheck, label: 'Đăng ký đề tài', to: '/registrations' },
  { icon: ListChecks, label: 'Tiến độ & Mốc báo cáo', to: '/progress' },
  {
    icon: ClipboardCheck,
    label: 'Tiêu chuẩn đánh giá',
    to: '/evaluations',
    roles: ['LECTURER', 'ADMIN'],
  },
  {
    icon: Flag,
    label: 'Phân công giảng viên',
    to: '/evaluator-assignment',
    roles: ['LECTURER', 'ADMIN'],
  },
  { icon: ClipboardCheck, label: 'Chấm điểm đề tài', to: '/score-sheets', roles: ['LECTURER'] },
  {
    icon: Building2,
    label: 'Quản lý Học thuật',
    to: '/academic-management',
    requireAcademicManagement: true,
  },
  {
    icon: ShieldCheck,
    label: 'Bổ nhiệm chức vụ',
    to: '/admin-appointments',
    adminOnly: true,
  },
  { icon: UserRound, label: 'Quản lý tài khoản', to: '/admin-users', adminOnly: true },
  { icon: Mail, label: 'Cấu hình email', to: '/email-settings', roles: ['ADMIN', 'LECTURER'] },
  { icon: CalendarCheck, label: 'Lịch bảo vệ & Kết quả', to: '/results', roles: ['STUDENT'] },
  { icon: UserRound, label: 'Tài khoản', to: '/account' },
  { icon: Bell, label: 'Thông báo', to: '/announcements' },
]
