import type { CurrentUser } from '../types/domain'
import { parseUtcDateTime } from './dateTime.ts'

export function academicAppointments(user: CurrentUser | null) {
  if (!user?.roles?.includes('LECTURER')) return []
  const now = Date.now()
  return (user.activeAppointments ?? []).filter(
    (appointment) =>
      parseUtcDateTime(appointment.startsAt).getTime() <= now &&
      (!appointment.endsAt || parseUtcDateTime(appointment.endsAt).getTime() > now),
  )
}

export function hasAcademicLeadership(user: CurrentUser | null) {
  return academicAppointments(user).some((a) =>
    ['DEAN', 'DEPARTMENT_HEAD'].includes(a.positionCode),
  )
}

export function canManageFaculty(user: CurrentUser | null, facultyId: number) {
  return academicAppointments(user).some(
    (a) => a.positionCode === 'DEAN' && a.facultyId === facultyId,
  )
}

export function canManageDepartment(
  user: CurrentUser | null,
  departmentId: number,
  facultyId: number,
) {
  return (
    !!user &&
    academicAppointments(user).some((appointment) =>
      appointment.positionCode === 'DEAN'
        ? appointment.facultyId === facultyId
        : appointment.positionCode === 'DEPARTMENT_HEAD' &&
          appointment.departmentId === departmentId,
    )
  )
}
