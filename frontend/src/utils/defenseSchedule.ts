import { formatDate, formatDateTime, parseUtcDateTime } from './dateTime'

// Date-only configuration and form values are Vietnamese calendar dates, never UTC dates.
export function defenseDateError(
  date: string | undefined | null,
  startsAt: string,
  endsAt: string,
): string {
  if (!date || !startsAt || !endsAt) return ''
  return startsAt.slice(0, 10) === date && endsAt.slice(0, 10) === date
    ? ''
    : `Phiên bảo vệ phải diễn ra vào ngày ${formatDate(date)} theo cấu hình đợt KLTN.`
}

export function defenseInstant(value: string, vietnameseDate: boolean): Date {
  // An explicit offset keeps KLTN input stable even when the browser is outside Vietnam.
  return new Date(vietnameseDate ? `${value}+07:00` : value)
}

export function formatDefenseTime(value: string, vietnameseDate: boolean): string {
  if (!vietnameseDate) return formatDateTime(value)
  const date = parseUtcDateTime(value)
  if (!Number.isFinite(date.getTime())) return '—'
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  })
    .format(date)
    .replace(',', '')
}
