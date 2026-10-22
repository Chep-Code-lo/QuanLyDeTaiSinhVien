import assert from 'node:assert/strict'
import test from 'node:test'
import { canManageDepartment } from '../src/utils/managementAccess.ts'

test('object scope matches Admin, dean faculty and head department', () => {
  const base = { roles: ['LECTURER'], activeAppointments: [] }
  assert.equal(canManageDepartment(null, 2, 1), false)
  assert.equal(canManageDepartment(base, 2, 1), false)
  assert.equal(canManageDepartment({ ...base, roles: ['ADMIN'] }, 2, 1), true)
  const dean = { ...base, activeAppointments: [{ positionCode: 'DEAN', facultyId: 1 }] }
  assert.equal(canManageDepartment(dean, 2, 1), true)
  assert.equal(canManageDepartment(dean, 3, 9), false)
  const head = {
    ...base,
    activeAppointments: [{ positionCode: 'DEPARTMENT_HEAD', departmentId: 2, facultyId: 1 }],
  }
  assert.equal(canManageDepartment(head, 2, 1), true)
  assert.equal(canManageDepartment(head, 3, 1), false)
})
