const messages: Record<string, string> = {
  'The data was changed by another request. Reload and try again.':
    'Dữ liệu đã được thay đổi bởi yêu cầu khác. Vui lòng tải lại và thử lại.',
  'Student code is already in use.': 'Mã sinh viên này đã được sử dụng.',
  'Lecturer code is already in use.': 'Mã giảng viên này đã được sử dụng.',
  'Department was not found.': 'Bộ môn không còn tồn tại. Vui lòng tải lại trang.',
  'User account was not found.': 'Tài khoản này không còn tồn tại.',
  'You cannot delete the account currently in use.': 'Bạn không thể xóa tài khoản đang đăng nhập.',
  'At least one active administrator account is required.':
    'Hệ thống phải luôn có ít nhất một quản trị viên đang hoạt động.',

  'The current password is incorrect.': 'Mật khẩu hiện tại không chính xác.',
  'The new password must differ from the current password.':
    'Mật khẩu mới phải khác mật khẩu hiện tại.',
  'Defense start time must be before end time.': 'Thời gian kết thúc phải sau thời gian bắt đầu.',
  'The committee already has a defense in this time range.':
    'Hội đồng đã có lịch bảo vệ trùng giờ. Vui lòng chọn thời gian khác.',
  'A topic supervisor cannot be on its defense committee.':
    'Hội đồng có giảng viên hướng dẫn của đề tài này. Vui lòng chọn hội đồng khác.',
  'A committee needs the configured number of members, one chair and one secretary.':
    'Hội đồng phải đủ số thành viên theo cấu hình, đúng một Chủ tịch, một Thư ký và không trùng giảng viên.',
  'The committee and project assignment must belong to the same registration period.':
    'Hội đồng và đề tài phải thuộc cùng đợt đăng ký.',
  'Lock every review component before scheduling a defense.':
    'Phần phản biện độc lập chưa khóa. Vui lòng hoàn tất cấu hình phản biện tại Tiêu chuẩn đánh giá.',
  'Complete every reviewer assignment and submitted review score before scheduling a defense.':
    'Đề tài chưa hoàn tất phản biện: cần đủ phân công và đầy đủ phiếu điểm đã nộp trước khi xếp lịch bảo vệ.',
  'Project or committee already has a completed defense configuration.':
    'Đề tài đã có phiên bảo vệ hoặc hội đồng đã hoàn tất. Vui lòng chọn lại.',
  'You do not have permission to perform this action.':
    'Bạn không có quyền thực hiện thao tác này.',
  'Authentication is required.': 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
  'Email or password is incorrect.': 'Email hoặc mật khẩu không chính xác.',
  'Email is already in use.': 'Email này đã được sử dụng.',
  'The account is not active.': 'Tài khoản đã bị khóa hoặc ngừng hoạt động.',
  'Request validation failed.': 'Thông tin chưa hợp lệ. Vui lòng kiểm tra lại các trường đã nhập.',
  'The request payload is invalid.': 'Dữ liệu gửi lên không hợp lệ. Vui lòng kiểm tra lại.',
  'The requested change conflicts with existing data.':
    'Thay đổi bị trùng hoặc xung đột với dữ liệu hiện có.',
  'An unexpected server error occurred.': 'Hệ thống gặp lỗi. Vui lòng thử lại sau.',
  'Network Error': 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng.',
  'Failed to fetch': 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng.',
}

const statusMessages: Record<number, string> = {
  400: 'Dữ liệu chưa hợp lệ. Vui lòng kiểm tra lại thông tin đã nhập.',
  401: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
  403: 'Bạn không có quyền thực hiện thao tác này.',
  404: 'Không tìm thấy dữ liệu được yêu cầu.',
  405: 'Thao tác này chưa được hệ thống hỗ trợ.',
  409: 'Dữ liệu đã thay đổi hoặc bị trùng. Vui lòng tải lại và thử lại.',
  413: 'Tệp tải lên vượt quá dung lượng cho phép.',
  429: 'Bạn đang thao tác quá nhanh. Vui lòng chờ một chút rồi thử lại.',
}

export function vietnameseErrorMessage(message: unknown, status?: number): string {
  if (typeof message === 'string') {
    let translated = message
    for (const [english, vietnamese] of Object.entries(messages))
      translated = translated.split(english).join(vietnamese)
    translated = translated
      .replace(
        /Request failed with status code (\d+)/g,
        (_, code: string) =>
          statusMessages[Number(code)] ?? 'Hệ thống gặp lỗi. Vui lòng thử lại sau.',
      )
      .replace(
        /timeout of \d+ms exceeded|timeout exceeded|Request aborted/gi,
        'Kết nối quá thời gian chờ. Vui lòng thử lại.',
      )
    if (/[À-ỹĐđ]/.test(translated)) return translated
  }
  return (
    statusMessages[status ?? 0] ??
    (status && status >= 500
      ? 'Hệ thống gặp lỗi. Vui lòng thử lại sau.'
      : 'Không thể thực hiện thao tác. Vui lòng thử lại.')
  )
}

type ApiError = {
  message?: string
  code?: string
  response?: { status?: number; data?: { message?: string } }
}

export function localizeApiError(error: ApiError): ApiError {
  const message =
    error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT'
      ? 'Kết nối quá thời gian chờ. Vui lòng thử lại.'
      : !error.response && error.code === 'ERR_NETWORK'
        ? 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng.'
        : vietnameseErrorMessage(
            error.response?.data?.message ?? error.message,
            error.response?.status,
          )
  error.message = message
  if (error.response?.data && typeof error.response.data === 'object')
    error.response.data.message = message
  return error
}
