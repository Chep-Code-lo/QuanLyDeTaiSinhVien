export const projectTypeLabel = (type?: string) => {
  switch (type?.toUpperCase()) {
    case 'COURSE':
      return 'Môn học'
    case 'RESEARCH':
      return 'Nghiên cứu khoa học'
    case 'SPECIALIZED':
      return 'Tiểu luận chuyên ngành'
    case 'GRADUATION':
      return 'Khóa luận tốt nghiệp'
    default:
      return type || 'Chưa xác định'
  }
}

export const statusLabel = (status?: string) => {
  switch (status?.toUpperCase()) {
    case 'DRAFT':
      return 'Nháp'
    case 'LOCKED':
      return 'Đã khóa'
    case 'OPEN':
      return 'Đang mở'
    case 'PENDING':
      return 'Chờ xử lý'
    case 'FORMING':
      return 'Đang thành lập'
    case 'ASSIGNED':
      return 'Đã phân công'
    case 'IN_PROGRESS':
      return 'Đang thực hiện'
    case 'SUBMITTED':
      return 'Đã nộp'
    case 'ACCEPTED':
      return 'Đã chấp nhận'
    case 'REVISION_REQUIRED':
      return 'Yêu cầu chỉnh sửa'
    case 'APPROVED':
      return 'Đã duyệt'
    case 'REJECTED':
      return 'Đã từ chối'
    case 'WITHDRAWN':
      return 'Đã rút'
    case 'ACTIVE':
      return 'Đang hoạt động'
    case 'INACTIVE':
      return 'Không hoạt động'
    case 'CANCELLED':
      return 'Đã hủy'
    case 'EXPIRED':
      return 'Hết hạn'
    case 'FINALIZED':
      return 'Đã hoàn tất'
    case 'PUBLISHED':
      return 'Đã công bố'
    case 'ARCHIVED':
      return 'Đã lưu trữ'
    case 'CLOSED':
      return 'Đã đóng'
    case 'CONFIRMED':
      return 'Đã xác nhận'
    case 'SCHEDULED':
      return 'Đã lên lịch'
    case 'COMPLETED':
      return 'Đã hoàn thành'
    default:
      return status || 'Chưa xác định'
  }
}

export const roleLabel = (role?: string) => {
  switch (role?.toUpperCase()) {
    case 'ADMIN':
      return 'Quản trị viên'
    case 'LECTURER':
      return 'Giảng viên'
    case 'STUDENT':
      return 'Sinh viên'
    default:
      return role || 'Chưa xác định'
  }
}

export const componentTypeLabel = (type?: string) => {
  switch (type?.toUpperCase()) {
    case 'REVIEW':
      return 'Chấm phản biện độc lập'
    case 'COMMITTEE':
      return 'Chấm bởi hội đồng'
    case 'OTHER':
      return 'Hình thức khác'
    default:
      return type || 'Chưa xác định'
  }
}

export const evaluatorRoleLabel = (role?: string) => {
  switch (role?.toUpperCase()) {
    case 'REVIEWER':
      return 'Giảng viên phản biện'
    case 'COMMITTEE_MEMBER':
      return 'Thành viên hội đồng'
    case 'SUPERVISOR':
      return 'Giảng viên hướng dẫn'
    case 'EVALUATOR':
      return 'Người chấm'
    default:
      return role || 'Người chấm'
  }
}

export const milestoneTypeLabel = (type?: string) => {
  switch (type?.toUpperCase()) {
    case 'PROPOSAL':
      return 'Đề cương'
    case 'PROGRESS':
      return 'Báo cáo tiến độ'
    case 'FINAL':
      return 'Báo cáo tổng kết'
    default:
      return type || 'Mốc tiến độ'
  }
}
