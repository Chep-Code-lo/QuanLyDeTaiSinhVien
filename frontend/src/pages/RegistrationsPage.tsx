import { ModulePlaceholder } from '../components/ModulePlaceholder'
import { PageHeader } from '../components/PageHeader'

export function RegistrationsPage() {
  return (
    <>
      <PageHeader title="Đăng ký đề tài" description="Theo dõi nguyện vọng đăng ký và quyết định phê duyệt của giảng viên hoặc bộ môn." />
      <ModulePlaceholder
        title="Hồ sơ đăng ký"
        description="Luồng gửi và duyệt đăng ký sẽ được mở khi các endpoint nghiệp vụ đã sẵn sàng."
        actionLabel="Đăng ký đề tài"
        items={[
          'Danh sách nguyện vọng theo từng đợt đăng ký.',
          'Trạng thái chờ duyệt, đã duyệt hoặc cần bổ sung thông tin.',
          'Lịch sử phản hồi từ giảng viên hướng dẫn.',
        ]}
      />
    </>
  )
}
