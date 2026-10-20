const messages: Record<string, string> = {
  'You do not have permission to perform this action.': 'Bạn không có quyền thực hiện thao tác này.',
  'Authentication is required.': 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
  'Email or password is incorrect.': 'Email hoặc mật khẩu không chính xác.',
  'Email is already in use.': 'Email này đã được sử dụng.',
  'The account is not active.': 'Tài khoản đã bị khóa hoặc ngừng hoạt động.',
  'Request validation failed.': 'Thông tin chưa hợp lệ. Vui lòng kiểm tra lại các trường đã nhập.',
  'The request payload is invalid.': 'Dữ liệu gửi lên không hợp lệ. Vui lòng kiểm tra lại.',
  'The requested change conflicts with existing data.': 'Thay đổi bị trùng hoặc xung đột với dữ liệu hiện có.',
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
    for (const [english, vietnamese] of Object.entries(messages)) translated = translated.split(english).join(vietnamese)
    translated = translated.replace(/Request failed with status code (\d+)/g, (_, code: string) =>
      statusMessages[Number(code)] ?? 'Hệ thống gặp lỗi. Vui lòng thử lại sau.',
    ).replace(/timeout of \d+ms exceeded|timeout exceeded|Request aborted/gi, 'Kết nối quá thời gian chờ. Vui lòng thử lại.')
    if (/[À-ỹĐđ]/.test(translated)) return translated
  }
  return statusMessages[status ?? 0] ?? (status && status >= 500
    ? 'Hệ thống gặp lỗi. Vui lòng thử lại sau.'
    : 'Không thể thực hiện thao tác. Vui lòng thử lại.')
}

type ApiError = {
  message?: string
  code?: string
  response?: { status?: number; data?: { message?: string } }
}

export function localizeApiError(error: ApiError): ApiError {
  const message = error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT'
    ? 'Kết nối quá thời gian chờ. Vui lòng thử lại.'
    : !error.response && error.code === 'ERR_NETWORK'
      ? 'Không thể kết nối đến máy chủ. Vui lòng kiểm tra kết nối mạng.'
      : vietnameseErrorMessage(error.response?.data?.message ?? error.message, error.response?.status)
  error.message = message
  if (error.response?.data && typeof error.response.data === 'object') error.response.data.message = message
  return error
}
