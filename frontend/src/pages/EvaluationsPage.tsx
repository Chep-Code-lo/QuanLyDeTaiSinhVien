import { ModulePlaceholder } from '../components/ModulePlaceholder'
import { PageHeader } from '../components/PageHeader'

export function EvaluationsPage() {
  return (
    <>
      <PageHeader title="Tiêu chuẩn đánh giá" />
      <ModulePlaceholder
        title="Kết quả đánh giá"
        description="Bảng điểm và nhận xét hội đồng sẽ hiển thị theo đúng quyền của sinh viên, giảng viên và quản trị viên."
        items={[
          'Lịch các đợt đánh giá và thành phần hội đồng.',
          'Tiêu chí, điểm thành phần và nhận xét.',
          'Kết quả cuối cùng sau khi được công bố.',
        ]}
      />
    </>
  )
}
