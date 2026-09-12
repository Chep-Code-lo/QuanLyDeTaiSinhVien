import { ModulePlaceholder } from '../components/ModulePlaceholder'
import { PageHeader } from '../components/PageHeader'

export function AnnouncementsPage() {
  return (
    <>
      <PageHeader title="Thông báo" description="Tập trung các nhắc nhở về đợt đăng ký, thời hạn nộp báo cáo và thông tin từ bộ môn." />
      <ModulePlaceholder
        title="Hộp thư thông báo"
        description="Thông báo sẽ được phân loại theo thời gian và trạng thái đã đọc sau khi backend cung cấp dữ liệu."
        items={[
          'Thông báo chung của khoa hoặc bộ môn.',
          'Nhắc việc cá nhân liên quan đến nhóm và đề tài.',
          'Liên kết nhanh đến nội dung cần xử lý.',
        ]}
      />
    </>
  )
}
