import React, { useCallback, useEffect, useState } from 'react'
import { authApi } from '../api/auth'
import type { CurrentUser, PositionCode, RoleCode } from '../types/domain'
import { AuthContext } from './AuthContext'

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [loading, setLoading] = useState<boolean>(true)

  const refreshUser = useCallback(() => authApi.getCurrentUser()
    .then(setUser)
    .catch(() => setUser(null))
    .finally(() => setLoading(false)), [])

  useEffect(() => {
    refreshUser()
  }, [refreshUser])

  useEffect(() => {
    const expired = () => setUser(null)
    window.addEventListener('session-expired', expired)
    return () => window.removeEventListener('session-expired', expired)
  }, [])

  const login = async (username: string, password: string) => {
    const data = await authApi.login({ username, password })
    setUser(data.user)
  }

  const logout = async () => {
    try {
      await authApi.logout()
    } finally {
      setUser(null)
    }
  }

  const hasRole = useCallback((role: RoleCode): boolean => {
    return user?.roles?.includes(role) ?? false
  }, [user?.roles])

  const hasAnyRole = useCallback((roles: RoleCode[]): boolean => {
    return user?.roles?.some((r) => roles.includes(r)) ?? false
  }, [user?.roles])

  const hasAppointment = useCallback((position: PositionCode): boolean => {
    return user?.activeAppointments?.some((apt) => apt.positionCode === position) ?? false
  }, [user?.activeAppointments])

  const canAccessAcademicManagement = (): boolean => {
    if (!user) return false
    if (hasRole('ADMIN')) return true
    return hasAppointment('DEAN') || hasAppointment('DEPARTMENT_HEAD')
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        isAuthenticated: !!user,
        login,
        logout,
        refreshUser,
        hasRole,
        hasAnyRole,
        hasAppointment,
        canAccessAcademicManagement,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}
