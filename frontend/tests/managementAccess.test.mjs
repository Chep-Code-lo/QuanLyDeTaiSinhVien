import assert from 'node:assert/strict'
import test from 'node:test'
import { canManageDepartment } from '../src/utils/managementAccess.ts'

test('academic actions require a current appointment rather than ADMIN', () => {
  const base = { roles: ['LECTURER'], activeAppointments: [] }
  assert.equal(canManageDepartment(null, 2, 1), false)
  assert.equal(canManageDepartment(base, 2, 1), false)
  assert.equal(canManageDepartment({ ...base, roles: ['ADMIN'] }, 2, 1), false)
  const dean = {
    ...base,
    activeAppointments: [{ positionCode: 'DEAN', facultyId: 1, startsAt: '2020-01-01T00:00:00Z' }],
  }
  assert.equal(canManageDepartment(dean, 2, 1), true)
  assert.equal(canManageDepartment(dean, 3, 9), false)
  const head = {
    ...base,
    activeAppointments: [
      {
        positionCode: 'DEPARTMENT_HEAD',
        departmentId: 2,
        facultyId: 1,
        startsAt: '2020-01-01T00:00:00Z',
      },
    ],
  }
  assert.equal(canManageDepartment(head, 2, 1), true)
  assert.equal(canManageDepartment(head, 3, 1), false)
})

test('expired, future and role-less appointments do not grant authority', () => {
  const appointment = { positionCode: 'DEAN', facultyId: 1, startsAt: '2020-01-01T00:00:00Z' }
  assert.equal(
    canManageDepartment({ roles: ['ADMIN'], activeAppointments: [appointment] }, 2, 1),
    false,
  )
  assert.equal(
    canManageDepartment({ roles: ['ADMIN', 'LECTURER'], activeAppointments: [appointment] }, 2, 1),
    true,
  )
  assert.equal(
    canManageDepartment(
      {
        roles: ['LECTURER'],
        activeAppointments: [{ ...appointment, endsAt: '2020-02-01T00:00:00Z' }],
      },
      2,
      1,
    ),
    false,
  )
  assert.equal(
    canManageDepartment(
      {
        roles: ['LECTURER'],
        activeAppointments: [{ ...appointment, startsAt: '2099-01-01T00:00:00Z' }],
      },
      2,
      1,
    ),
    false,
  )
})
