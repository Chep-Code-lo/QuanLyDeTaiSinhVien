import { ModulePlaceholder } from '../components/ModulePlaceholder'
import { PageHeader } from '../components/PageHeader'

export function TopicsPage() {
  return (
    <>
      <PageHeader title="Đề tài" description="Tra cứu, đề xuất và theo dõi các đề tài nghiên cứu theo học kỳ." />
      <ModulePlaceholder
        title="Danh sách đề tài"
        description="Bảng dữ liệu, bộ lọc và chi tiết đề tài sẽ được kết nối khi module đề tài ở Spring Boot hoàn thiện."
        actionLabel="Tạo đề tài"
        items={[
          'Danh sách đề tài theo học kỳ và bộ môn.',
          'Tìm kiếm, lọc theo giảng viên hướng dẫn và trạng thái.',
          'Trang chi tiết: mô tả, chỉ tiêu sinh viên và tài liệu đính kèm.',
        ]}
      />
    </>
  )
}
