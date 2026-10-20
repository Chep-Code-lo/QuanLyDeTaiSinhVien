import assert from 'node:assert/strict'
import test from 'node:test'
import { registrationWindowError } from '../src/utils/registrationWindow.ts'

process.env.TZ = 'Asia/Ho_Chi_Minh'

const period = {
  status: 'ACTIVE',
  registrationStartAt: '2026-10-05T17:51:00',
  registrationEndAt: '2026-10-19T17:51:00',
}

test('registration window uses UTC API dates and matches inclusive server boundaries', () => {
  const start = Date.parse('2026-10-05T17:51:00Z')
  const end = Date.parse('2026-10-19T17:51:00Z')
  assert.match(registrationWindowError(period, start - 1), /chưa đến thời gian/)
  assert.equal(registrationWindowError(period, start), '')
  assert.equal(registrationWindowError(period, end), '')
  assert.match(registrationWindowError(period, end + 1), /hết hạn/)
})

test('LTW expiry reports the actual deadline on 20 October in Vietnam', () => {
  assert.equal(
    registrationWindowError(period, Date.parse('2026-10-19T17:54:00Z')),
    'Đợt đã hết hạn đăng ký lúc 20/10/2026 00:51:00.',
  )
})

test('closed and draft periods block registration even inside their time window', () => {
  const now = Date.parse('2026-10-10T00:00:00Z')
  for (const status of ['CLOSED', 'DRAFT']) {
    assert.match(registrationWindowError({ ...period, status }, now), /chưa được mở hoặc đã đóng/)
  }
})

test('invalid or reversed schedules cannot enable registration', () => {
  assert.match(
    registrationWindowError({ ...period, registrationStartAt: 'invalid' }),
    /tải lại trang/,
  )
  assert.match(
    registrationWindowError({
      ...period,
      registrationStartAt: period.registrationEndAt,
      registrationEndAt: period.registrationStartAt,
    }),
    /tải lại trang/,
  )
})
