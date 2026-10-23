import { afterEach, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { AuthProvider } from '../src/context/AuthProvider'
import { useAuth } from '../src/context/AuthContext'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { ProtectedRoute } from '../src/components/ProtectedRoute'

const mocks = vi.hoisted(() => ({ current: vi.fn() }))
vi.mock('../src/api/auth', () => ({ authApi: { getCurrentUser: mocks.current } }))
afterEach(cleanup)

function Access() {
  const { user, hasAppointment, hasRole } = useAuth()
  return <p>{!user ? 'Chưa đăng nhập' : hasAppointment('DEAN') ? 'Trưởng khoa' : hasRole('ADMIN') ? 'Quản trị' : 'Giảng viên'}</p>
}

it('lấy lại quyền khi quay lại cửa sổ, mất chức vụ không cần đăng xuất', async () => {
  const user = { id: 1, roles: ['LECTURER'], activeAppointments: [
    { positionCode: 'DEAN', facultyId: 1, startsAt: '2020-01-01T00:00:00Z' },
  ] }
  mocks.current.mockResolvedValueOnce(user).mockResolvedValue({ ...user, activeAppointments: [] })
  render(<AuthProvider><Access /></AuthProvider>)
  await screen.findByText('Trưởng khoa')
  fireEvent(window, new Event('focus'))
  await screen.findByText('Giảng viên')
  await waitFor(() => expect(mocks.current).toHaveBeenCalledTimes(2))
})

it('admin thuần không vượt qua route yêu cầu chức vụ học thuật', async () => {
  mocks.current.mockResolvedValue({ id: 2, roles: ['ADMIN'], activeAppointments: [] })
  render(<AuthProvider><MemoryRouter><Routes>
    <Route element={<ProtectedRoute appointments={['DEAN']} />}>
      <Route path="/" element={<p>Nghiệp vụ Trưởng khoa</p>} />
    </Route>
  </Routes></MemoryRouter></AuthProvider>)
  await screen.findByText('Không có quyền truy cập')
  expect(screen.queryByText('Nghiệp vụ Trưởng khoa')).toBeNull()
})
