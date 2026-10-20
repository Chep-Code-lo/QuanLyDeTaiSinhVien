// The API stores and returns LocalDateTime values in UTC without a zone suffix.
export function parseUtcDateTime(value: string): Date {
  return new Date(/(?:Z|[+-]\d{2}:\d{2})$/i.test(value) ? value : `${value}Z`)
}

export function toLocalDateTimeInput(value: Date): string {
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}`
}

const pad = (value: number) => String(value).padStart(2, '0')

function displayDate(value: Date | string | number): Date {
  return value instanceof Date
    ? value
    : typeof value === 'string'
      ? parseUtcDateTime(value)
      : new Date(value)
}

export function formatDate(value: Date | string | number): string {
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return value.split('-').reverse().join('/')
  }
  const date = displayDate(value)
  if (!Number.isFinite(date.getTime())) return '—'
  return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()}`
}

export function formatDateTime(value: Date | string | number): string {
  const date = displayDate(value)
  if (!Number.isFinite(date.getTime())) return '—'
  return `${formatDate(date)} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

export function formatDateInput(value: string, withTime = false): string {
  if (!value) return ''
  const [date, time] = value.split('T')
  const hour = Number(time?.slice(0, 2) || 0)
  const clock = `${pad(hour % 12 || 12)}:${time?.slice(3, 5) || '00'} ${hour >= 12 ? 'PM' : 'AM'}`
  return `${date.split('-').reverse().join('/')}${withTime ? ` ${clock}` : ''}`
}

export function parseDateInput(value: string, withTime = false): string | null {
  const match = value
    .trim()
    .match(
      withTime
        ? /^(\d{1,2})\/(\d{1,2})\/(\d{4})\s+(\d{1,2}):(\d{2})(?:\s+(AM|PM))?$/i
        : /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/,
    )
  if (!match) return null
  const [, dayText, monthText, yearText, hourText = '0', minuteText = '0', period] = match
  const [day, month, year, hour, minute] = [dayText, monthText, yearText, hourText, minuteText].map(
    Number,
  )
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0)
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  if (
    year < 1 ||
    month < 1 ||
    month > 12 ||
    day < 1 ||
    day > days[month - 1] ||
    (period ? hour < 1 || hour > 12 : hour > 23) ||
    minute > 59
  )
    return null
  const canonicalHour = period ? (hour % 12) + (period.toUpperCase() === 'PM' ? 12 : 0) : hour
  return `${yearText}-${pad(month)}-${pad(day)}${withTime ? `T${pad(canonicalHour)}:${pad(minute)}` : ''}`
}
