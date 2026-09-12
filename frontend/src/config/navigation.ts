export type NavigationItem = {
  icon: string
  label: string
  to: string
}

export const navigationItems: NavigationItem[] = [
  { icon: '⌂', label: 'Tổng quan', to: '/' },
  { icon: '◫', label: 'Đề tài', to: '/topics' },
  { icon: '♧', label: 'Nhóm thực hiện', to: '/groups' },
  { icon: '✓', label: 'Đăng ký', to: '/registrations' },
  { icon: '◷', label: 'Tiến độ', to: '/progress' },
  { icon: '★', label: 'Đánh giá', to: '/evaluations' },
  { icon: '◉', label: 'Thông báo', to: '/announcements' },
  { icon: '☻', label: 'Tài khoản', to: '/account' },
]
