import type { CurrentUser } from '../types/domain'

export function canManageDepartment(
  user: CurrentUser | null,
  departmentId: number,
  facultyId: number,
) {
  return (
    !!user &&
    (user.roles.includes('ADMIN') ||
      user.activeAppointments.some((appointment) =>
        appointment.positionCode === 'DEAN'
          ? appointment.facultyId === facultyId
          : appointment.positionCode === 'DEPARTMENT_HEAD' &&
            appointment.departmentId === departmentId,
      ))
  )
}
