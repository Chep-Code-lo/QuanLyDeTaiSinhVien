import type { RegistrationPeriodResponse } from '../types/domain.ts'
import { formatDateTime, parseUtcDateTime } from './dateTime.ts'

export function registrationWindowError(
  period: Pick<RegistrationPeriodResponse, 'status' | 'registrationStartAt' | 'registrationEndAt'>,
  now = Date.now(),
): string {
  const start = parseUtcDateTime(period.registrationStartAt).getTime()
  const end = parseUtcDateTime(period.registrationEndAt).getTime()
  if (!Number.isFinite(start) || !Number.isFinite(end) || start > end)
    return 'Chưa xác định được thời gian đăng ký. Vui lòng tải lại trang.'
  if (now > end) return `Đợt đã hết hạn đăng ký lúc ${formatDateTime(period.registrationEndAt)}.`
  if (period.status !== 'ACTIVE') return 'Đợt đăng ký chưa được mở hoặc đã đóng.'
  if (now < start)
    return `Đợt chưa đến thời gian đăng ký. Mở đăng ký lúc ${formatDateTime(period.registrationStartAt)}.`
  return ''
}
