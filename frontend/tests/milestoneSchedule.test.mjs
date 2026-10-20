import assert from 'node:assert/strict'
import test from 'node:test'
import { milestoneCalendarDays, milestoneDayKey, milestoneDeadline } from '../src/utils/milestoneSchedule.ts'

test('calendar starts on Monday and includes every date exactly once', () => {
  const days = milestoneCalendarDays(new Date(2026, 9, 1))
  assert.equal(days.length % 7, 0)
  assert.deepEqual(days.slice(0, 3), [null, null, null])
  const dates = days.filter(Boolean)
  assert.equal(dates.length, 31)
  assert.deepEqual(dates.map(date => date.getDate()), Array.from({ length: 31 }, (_, index) => index + 1))
  assert.equal(milestoneDayKey(dates[0]), '2026-10-01')
})

test('calendar handles leap years and navigation across years', () => {
  assert.equal(milestoneCalendarDays(new Date(2028, 1, 1)).filter(Boolean).length, 29)
  assert.equal(milestoneCalendarDays(new Date(2027, 1, 1)).filter(Boolean).length, 28)
  const nextYear = milestoneCalendarDays(new Date(2026, 12, 1)).find(Boolean)
  assert.equal(milestoneDayKey(nextYear), '2027-01-01')
})

test('remaining days follow local calendar dates without adding an extra day for later hours', () => {
  const now = new Date(2026, 9, 20, 0, 3)
  assert.deepEqual(milestoneDeadline(new Date(2026, 9, 22, 0, 0).toISOString(), now), {label: 'Còn 2 ngày', tone: 'soon'})
  assert.deepEqual(milestoneDeadline(new Date(2026, 9, 29, 1, 37).toISOString(), now), {label: 'Còn 9 ngày', tone: 'normal'})
})

test('deadline labels distinguish hours, due now and overdue reports', () => {
  const now = new Date(2026, 9, 20, 10)
  assert.deepEqual(milestoneDeadline(new Date(2026, 9, 20, 12).toISOString(), now), {label: 'Còn 2 giờ', tone: 'soon'})
  assert.deepEqual(milestoneDeadline(now.toISOString(), now), {label: 'Đến hạn', tone: 'soon'})
  assert.deepEqual(milestoneDeadline(new Date(2026, 9, 20, 9).toISOString(), now), {label: 'Quá hạn 1 giờ', tone: 'late'})
  assert.deepEqual(milestoneDeadline(new Date(2026, 9, 18, 10).toISOString(), now), {label: 'Quá hạn 2 ngày', tone: 'late'})
})
