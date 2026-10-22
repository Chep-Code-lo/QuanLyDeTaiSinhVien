import { expect, it } from 'vitest'
import { apiClient } from '../src/api/client'

it.each([
  [
    400,
    'Request validation failed.',
    'Thông tin chưa hợp lệ. Vui lòng kiểm tra lại các trường đã nhập.',
  ],
  [401, 'Email or password is incorrect.', 'Email hoặc mật khẩu không chính xác.'],
  [
    403,
    'You do not have permission to perform this action.',
    'Bạn không có quyền thực hiện thao tác này.',
  ],
  [404, 'User account was not found.', 'Tài khoản này không còn tồn tại.'],
  [
    409,
    'The data was changed by another request. Reload and try again.',
    'Dữ liệu đã được thay đổi bởi yêu cầu khác. Vui lòng tải lại và thử lại.',
  ],
  [429, 'slow down', 'Bạn đang thao tác quá nhanh. Vui lòng chờ một chút rồi thử lại.'],
  [500, 'SELECT private diagnostic', 'Hệ thống gặp lỗi. Vui lòng thử lại sau.'],
] as const)(
  'localizes HTTP %s at the API boundary while preserving the envelope',
  async (status, original, expected) => {
    const failure = {
      message: original,
      response: {
        status,
        data: { success: false, message: original, data: { field: 'Giá trị chưa hợp lệ.' } },
      },
    }
    await expect(
      apiClient.get('/probe', { adapter: () => Promise.reject(failure) }),
    ).rejects.toMatchObject({
      message: expected,
      response: {
        status,
        data: { success: false, message: expected, data: { field: 'Giá trị chưa hợp lệ.' } },
      },
    })
  },
)
