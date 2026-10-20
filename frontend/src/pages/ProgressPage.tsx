import { ModulePlaceholder } from '../components/ModulePlaceholder'
import { PageHeader } from '../components/PageHeader'

export function ProgressPage() {
  return (
    <>
      <PageHeader title="Tiến độ & Mốc báo cáo" />
      <ModulePlaceholder
        title="Mốc tiến độ"
        description="Khu vực này là khung cho lịch sử báo cáo và các công việc theo từng giai đoạn của đề tài."
        actionLabel="Cập nhật tiến độ"
        items={[
          'Dòng thời gian các mốc cần hoàn thành.',
          'Nộp báo cáo, tệp minh chứng và ghi chú của nhóm.',
          'Nhận xét, yêu cầu chỉnh sửa từ giảng viên hướng dẫn.',
        ]}
      />
    </>
  )
}
