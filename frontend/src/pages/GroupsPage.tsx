import { ModulePlaceholder } from '../components/ModulePlaceholder'
import { PageHeader } from '../components/PageHeader'

export function GroupsPage() {
  return (
    <>
      <PageHeader title="Nhóm thực hiện" description="Theo dõi thành viên, lời mời tham gia và đề tài được phân công cho từng nhóm." />
      <ModulePlaceholder
        title="Quản lý nhóm"
        description="Giao diện này sẽ hiển thị nhóm hiện tại và trạng thái tham gia của từng sinh viên sau khi API được kết nối."
        actionLabel="Tạo nhóm"
        items={[
          'Thông tin trưởng nhóm và danh sách thành viên.',
          'Mời, chấp nhận hoặc từ chối lời mời vào nhóm.',
          'Liên kết nhóm với đề tài đã được duyệt.',
        ]}
      />
    </>
  )
}
