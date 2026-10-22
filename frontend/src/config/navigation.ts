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

export const navigationGroups = [
  { id: 'general', label: 'Menu chính' },
  { id: 'personal', label: 'Cá nhân' },
  { id: 'projects', label: 'Đề tài & tiến độ' },
  { id: 'evaluation', label: 'Đánh giá & kết quả' },
  { id: 'management', label: 'Quản trị' },
] as const

export type NavigationItem = {
  group: (typeof navigationGroups)[number]['id']
  icon: LucideIcon
  label: string
  to: string
  roles?: RoleCode[]
  appointments?: PositionCode[]
  requireAcademicManagement?: boolean
  adminOnly?: boolean
}

export const navigationItems: NavigationItem[] = [
  { group: 'general', icon: Home, label: 'Tổng quan', to: '/dashboard' },
  { group: 'projects', icon: BookOpen, label: 'Đề tài & Dự án', to: '/topics' },
  { group: 'projects', icon: BookOpen, label: 'Đề xuất & Xét duyệt', to: '/topic-workflow' },
  { group: 'projects', icon: UsersRound, label: 'Nhóm đề tài', to: '/groups' },
  { group: 'projects', icon: ClipboardCheck, label: 'Đăng ký đề tài', to: '/registrations' },
  {
    group: 'projects',
    icon: ClipboardCheck,
    label: 'Đề tài được giao',
    to: '/project-assignments',
    roles: ['LECTURER', 'ADMIN'],
  },
  { group: 'projects', icon: ListChecks, label: 'Tiến độ & Mốc báo cáo', to: '/progress' },
  {
    group: 'evaluation',
    icon: ClipboardCheck,
    label: 'Tiêu chuẩn đánh giá',
    to: '/evaluations',
    roles: ['LECTURER', 'ADMIN'],
  },
  {
    group: 'evaluation',
    icon: Flag,
    label: 'Phân công giảng viên',
    to: '/evaluator-assignment',
    roles: ['LECTURER', 'ADMIN'],
  },
  {
    group: 'evaluation',
    icon: ClipboardCheck,
    label: 'Chấm điểm đề tài',
    to: '/score-sheets',
    roles: ['LECTURER'],
  },
  {
    group: 'evaluation',
    icon: ListChecks,
    label: 'Tổng hợp & Công bố điểm',
    to: '/evaluation-results',
    roles: ['LECTURER', 'ADMIN'],
  },
  {
    group: 'evaluation',
    icon: ListChecks,
    label: 'Yêu cầu đổi điểm',
    to: '/grade-change-requests',
    roles: ['LECTURER', 'ADMIN'],
  },
  {
    group: 'evaluation',
    icon: CalendarCheck,
    label: 'Lịch bảo vệ & Kết quả',
    to: '/results',
    roles: ['STUDENT'],
  },
  {
    group: 'management',
    icon: Building2,
    label: 'Danh mục học thuật',
    to: '/academic-catalog',
    requireAcademicManagement: true,
  },
  {
    group: 'management',
    icon: Building2,
    label: 'Quản lý Học thuật',
    to: '/academic-management',
    requireAcademicManagement: true,
  },
  {
    group: 'management',
    icon: ShieldCheck,
    label: 'Bổ nhiệm chức vụ',
    to: '/admin-appointments',
    adminOnly: true,
  },
  {
    group: 'management',
    icon: UserRound,
    label: 'Quản lý tài khoản',
    to: '/admin-users',
    adminOnly: true,
  },
  {
    group: 'management',
    icon: Mail,
    label: 'Cấu hình email',
    to: '/email-settings',
    roles: ['ADMIN', 'LECTURER'],
  },
  {
    group: 'management',
    icon: ShieldCheck,
    label: 'Nhật ký thao tác',
    to: '/audit-logs',
    adminOnly: true,
  },
  { group: 'personal', icon: Bell, label: 'Thông báo', to: '/announcements' },
  { group: 'personal', icon: UserRound, label: 'Tài khoản', to: '/account' },
]
