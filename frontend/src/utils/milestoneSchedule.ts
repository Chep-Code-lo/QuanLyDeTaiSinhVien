import { parseUtcDateTime } from './dateTime.ts'

export function milestoneDayKey(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export function milestoneCalendarDays(month: Date): (Date | null)[] {
  const start = new Date(month.getFullYear(), month.getMonth(), 1)
  const count = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate()
  const days: (Date | null)[] = Array.from({ length: (start.getDay() + 6) % 7 }, () => null)
  for (let day = 1; day <= count; day++)
    days.push(new Date(month.getFullYear(), month.getMonth(), day))
  while (days.length % 7) days.push(null)
  return days
}

export function milestoneDeadline(dueAt: string, now: Date) {
  const due = parseUtcDateTime(dueAt)
  const remaining = due.getTime() - now.getTime()
  const days = Math.round(
    (Date.UTC(due.getFullYear(), due.getMonth(), due.getDate()) -
      Date.UTC(now.getFullYear(), now.getMonth(), now.getDate())) /
      86_400_000,
  )
  if (remaining < 0)
    return {
      label:
        -remaining < 86_400_000
          ? `Quá hạn ${Math.max(1, Math.ceil(-remaining / 3_600_000))} giờ`
          : `Quá hạn ${Math.abs(days)} ngày`,
      tone: 'late',
    }
  if (remaining === 0) return { label: 'Đến hạn', tone: 'soon' }
  if (days === 0) {
    return { label: `Còn ${Math.max(1, Math.ceil(remaining / 3_600_000))} giờ`, tone: 'soon' }
  }
  return { label: `Còn ${days} ngày`, tone: days <= 3 ? 'soon' : 'normal' }
}
