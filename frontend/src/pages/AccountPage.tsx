import { ModulePlaceholder } from '../components/ModulePlaceholder'
import { PageHeader } from '../components/PageHeader'

export function AccountPage() {
  return (
    <>
      <PageHeader title="Tài khoản" description="Quản lý thông tin hồ sơ, vai trò trong hệ thống và các thiết lập bảo mật." />
      <ModulePlaceholder
        title="Hồ sơ cá nhân"
        description="Thông tin người dùng sẽ được lấy từ endpoint /auth/me sau khi xác thực JWT được triển khai ở backend."
        actionLabel="Chỉnh sửa hồ sơ"
        items={[
          'Thông tin liên hệ, mã sinh viên hoặc mã giảng viên.',
          'Vai trò và quyền được cấp trong hệ thống.',
          'Đổi mật khẩu và quản lý phiên đăng nhập.',
        ]}
      />
    </>
  )
}
