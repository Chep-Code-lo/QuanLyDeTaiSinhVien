const messages: Record<string, string> = {
  'Invitation expiry must be in the future.': 'Hạn lời mời phải sau thời điểm hiện tại.',
  'Invitation expiry must be within the configured invitation lifetime and registration window.':
    'Hạn lời mời vượt quá số giờ cho phép hoặc thời điểm đóng đăng ký. Hãy chọn lại hạn lời mời.',
  'Student was not found.': 'Không tìm thấy sinh viên có mã số này. Hãy kiểm tra lại mã số sinh viên.',
  'This student already has a pending invitation to the group.':
    'Sinh viên này đã có lời mời vào nhóm đang chờ phản hồi.',
  'Student already belongs to an active group in this registration period.':
    'Sinh viên này đã tham gia một nhóm trong đợt đăng ký này.',
  'Student already belongs to an active group.':
    'Bạn đã thuộc một nhóm trong đợt đăng ký này, không thể tạo hoặc tham gia nhóm khác.',
  'All group members must belong to the same faculty as the registration period.':
    'Sinh viên được mời phải thuộc cùng khoa với đợt đăng ký.',
  'The group has reached its configured maximum number of members.':
    'Nhóm đã đủ số thành viên tối đa của đợt đăng ký.',
  'Group invitations are only available in an active registration period.':
    'Chỉ có thể mời sinh viên khi đợt đăng ký đang hoạt động.',
  'Group invitations are only available during the registration window.':
    'Chỉ có thể mời sinh viên trong thời gian mở đăng ký.',
  'Student groups can only be created in an active registration period.':
    'Đợt đăng ký chưa hoạt động hoặc đã đóng, không thể tạo nhóm mới.',
  'Student groups can only be managed during the registration window.':
    'Chỉ có thể tạo nhóm trong thời gian mở đăng ký.',
  'Student must belong to the same faculty as the registration period.':
    'Bạn cần thuộc cùng khoa với đợt đăng ký để tạo nhóm.',
  'Group size limits must be between 3 and 5.':
    'Giới hạn số thành viên nhóm phải nằm trong khoảng 3–5 sinh viên.',
  'A group must have 3 to 5 active students to register a topic.':
    'Nhóm phải có đủ 3–5 thành viên đang tham gia và đáp ứng giới hạn của đợt để đăng ký đề tài.',
  'A group must have 3 to 5 active students before project assignment.':
    'Nhóm phải có đủ 3–5 thành viên đang tham gia và đáp ứng giới hạn của đợt trước khi được giao đề tài.',
  'This score sheet cannot receive another change request.':
    'Phiếu đã có yêu cầu đang chờ xử lý hoặc chưa đủ điều kiện xin đổi điểm.',
  'Only a pending request may be reviewed.':
    'Chỉ yêu cầu đang chờ duyệt mới được xét duyệt. Vui lòng tải lại.',
  'Cannot reopen or edit a score sheet captured by a result version; finalized and published evidence is immutable.':
    'Phiếu đã được dùng để chốt kết quả nên không thể mở lại hoặc sửa điểm.',
  'Topics can only be proposed during the proposal window.':
    'Chỉ được đề xuất đề tài trong thời gian tiếp nhận đề xuất của đợt.',
  'Topic proposer must belong to the same faculty as the registration period.':
    'Hãy chọn đợt thuộc khoa của người đề xuất.',
  'Topic code is already used in this registration period.':
    'Mã đề tài đã được sử dụng trong đợt này.',
  'The topic was changed by another user. Reload and try again.':
    'Đề tài vừa được cập nhật bởi người khác. Vui lòng tải lại trước khi thao tác.',
  'Published topics cannot be withdrawn.': 'Không thể rút đề tài đã công bố.',
  'Faculty code is already in use.': 'Mã khoa đã được sử dụng.',
  'Department code is already in use in this faculty.': 'Mã bộ môn đã được sử dụng trong khoa này.',
  'Cannot add a department to an inactive faculty.':
    'Không thể thêm bộ môn vào khoa đã ngừng hoạt động.',
  'Save this email setting before sending a test message.':
    'Hãy lưu cấu hình email trước khi gửi thử.',

  'This assignment has no evaluation assignments.': 'Đề tài chưa được phân công chấm điểm.',
  'Configure the required assessment components before finalization.':
    'Cần cấu hình thành phần đánh giá bắt buộc trước khi chốt kết quả.',
  'Cannot finalize without an active defense session.':
    'Cần có phiên bảo vệ đang có hiệu lực trước khi chốt kết quả.',
  "Only this project's academic manager can finalize a result without a committee.":
    'Chỉ người quản lý học thuật đúng phạm vi mới được chốt kết quả đề tài không có hội đồng.',
  'Cannot finalize a result for a group with no active members.':
    'Không thể chốt kết quả cho nhóm không còn thành viên đang tham gia.',
  'A score sheet must contain every rubric criterion before finalization.':
    'Cần chấm đầy đủ tiêu chí trong mỗi phiếu trước khi chốt kết quả.',
  'A score sheet rubric must total exactly 10 points before finalization.':
    'Tổng điểm tối đa của bộ tiêu chí phải bằng 10 trước khi chốt kết quả.',

  "Only the chair of this project's defense committee can finalize its result.":
    'Chỉ chủ tịch hội đồng bảo vệ của đề tài mới được chốt kết quả.',
  'A finalized result is already awaiting publication or supersession.':
    'Đã có kết quả được chốt đang chờ công bố hoặc thay thế.',
  'All required assessment components must be locked before finalization.':
    'Cần khóa tất cả thành phần đánh giá bắt buộc trước khi chốt kết quả.',
  'Cannot finalize while a required evaluator assignment is missing.':
    'Chưa thể chốt: có thành phần đánh giá bắt buộc chưa được phân công giảng viên.',
  'Cannot finalize while a committee member assignment is missing.':
    'Chưa thể chốt: chưa phân công chấm đầy đủ cho các thành viên hội đồng.',
  'Cannot finalize while a required score sheet is missing.':
    'Chưa thể chốt: còn phiếu điểm bắt buộc chưa được nộp.',
  'Only a finalized result can be published.': 'Chỉ có thể công bố kết quả đã được chốt.',

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
