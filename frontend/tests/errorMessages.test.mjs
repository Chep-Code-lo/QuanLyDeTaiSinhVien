import assert from 'node:assert/strict'
import test from 'node:test'
import { localizeApiError, vietnameseErrorMessage } from '../src/utils/errorMessages.ts'

test('permission and legacy mixed-language toast messages are Vietnamese', () => {
  assert.equal(vietnameseErrorMessage('You do not have permission to perform this action.'), 'Bạn không có quyền thực hiện thao tác này.')
  assert.equal(vietnameseErrorMessage('Không thể tải danh sách mốc tiến độ: You do not have permission to perform this action.'), 'Không thể tải danh sách mốc tiến độ: Bạn không có quyền thực hiện thao tác này.')
})

test('both Axios fields are localized while preserving status and other data', () => {
  const error = {message: 'Request failed with status code 403', response: {status: 403, data: {message: 'You do not have permission to perform this action.', data: {id: 12}}}}
  assert.equal(localizeApiError(error), error)
  assert.equal(error.message, 'Bạn không có quyền thực hiện thao tác này.')
  assert.equal(error.response.data.message, error.message)
  assert.deepEqual(error.response.data.data, {id: 12})
  assert.equal(error.response.status, 403)
})

test('network failures, timeouts and expired sessions have useful Vietnamese messages', () => {
  assert.match(localizeApiError({code: 'ERR_NETWORK', message: 'Network Error'}).message, /kết nối/)
  assert.match(localizeApiError({code: 'ECONNABORTED', message: 'timeout of 15000ms exceeded'}).message, /thời gian chờ/)
  assert.match(localizeApiError({response: {status: 401, data: {}}}).message, /đăng nhập lại/)
  assert.match(vietnameseErrorMessage('Request failed with status code 404'), /Không tìm thấy/)
})

test('Vietnamese business errors stay intact and unknown English server details are not exposed', () => {
  assert.equal(vietnameseErrorMessage('Hạn nộp mới phải ở tương lai.'), 'Hạn nộp mới phải ở tương lai.')
  assert.equal(vietnameseErrorMessage('Internal database exception with sensitive details', 500), 'Hệ thống gặp lỗi. Vui lòng thử lại sau.')
  assert.match(vietnameseErrorMessage(undefined), /Không thể/)
})
