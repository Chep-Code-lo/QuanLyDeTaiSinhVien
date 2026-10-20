import assert from 'node:assert/strict'
import test from 'node:test'
import {
  formatDate,
  formatDateTime,
  formatDateInput,
  parseDateInput,
  parseUtcDateTime,
} from '../src/utils/dateTime.ts'

test('input dates always use day/month/year without changing their local hour', () => {
  assert.equal(formatDateInput('2026-10-22T17:21', true), '22/10/2026 05:21 PM')
  assert.equal(parseDateInput('22/10/2026 17:21', true), '2026-10-22T17:21')
  assert.equal(parseDateInput('02/03/2026 07:05', true), '2026-03-02T07:05')
  assert.equal(formatDateInput(parseDateInput('2/3/2026 7:05', true), true), '02/03/2026 07:05 AM')
  assert.equal(formatDateInput('2026-11-15'), '15/11/2026')
  assert.equal(parseDateInput('15/11/2026'), '2026-11-15')
})

test('AM/PM inputs preserve noon, midnight and afternoon API hours', () => {
  assert.equal(formatDateInput('2026-10-22T00:00', true), '22/10/2026 12:00 AM')
  assert.equal(formatDateInput('2026-10-22T12:00', true), '22/10/2026 12:00 PM')
  assert.equal(parseDateInput('22/10/2026 12:00 AM', true), '2026-10-22T00:00')
  assert.equal(parseDateInput('22/10/2026 12:00 PM', true), '2026-10-22T12:00')
  assert.equal(parseDateInput('22/10/2026 05:21 pm', true), '2026-10-22T17:21')
  assert.equal(parseDateInput('22/10/2026 00:00 AM', true), null)
  assert.equal(parseDateInput('22/10/2026 13:00 PM', true), null)
})

test('invalid dates and times cannot become API values', () => {
  for (const value of [
    '31/02/2026 12:00',
    '31/04/2026 12:00',
    '00/01/2026 12:00',
    '01/13/2026 12:00',
    '01/01/0000 12:00',
    '01/01/2026 24:00',
    '01/01/2026 12:60',
    '10/22/2026 12:00',
    '2026-10-22T12:00',
  ]) {
    assert.equal(parseDateInput(value, true), null, value)
  }
  assert.equal(parseDateInput('29/02/2027'), null)
  assert.equal(parseDateInput('29/02/1900'), null)
  assert.equal(parseDateInput('29/02/2028'), '2028-02-29')
  assert.equal(parseDateInput('29/02/2000'), '2000-02-29')
})

test('display dates are padded, day-first and preserve date-only and UTC semantics', () => {
  assert.equal(formatDate('2026-01-05'), '05/01/2026')
  assert.equal(formatDateTime(new Date(2026, 0, 5, 7, 8, 9)), '05/01/2026 07:08:09')
  assert.equal(parseUtcDateTime('2026-10-22T10:21:00').toISOString(), '2026-10-22T10:21:00.000Z')
  assert.equal(
    parseUtcDateTime('2026-10-22T17:21:00+07:00').toISOString(),
    '2026-10-22T10:21:00.000Z',
  )
  assert.equal(formatDateTime('invalid'), '—')
})
