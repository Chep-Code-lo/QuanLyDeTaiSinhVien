import React, { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { authApi } from '../api/auth'
import type { CurrentUser, PositionCode, RoleCode } from '../types/domain'

interface AuthContextType {
  user: CurrentUser | null
  loading: boolean
  isAuthenticated: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  refreshUser: () => Promise<void>
  hasRole: (role: RoleCode) => boolean
  hasAnyRole: (roles: RoleCode[]) => boolean
  hasAppointment: (position: PositionCode) => boolean
  canAccessAcademicManagement: () => boolean
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [loading, setLoading] = useState<boolean>(true)

  const refreshUser = useCallback(async () => {
    try {
      setLoading(true)
      const currentUser = await authApi.getCurrentUser()
      setUser(currentUser)
    } catch {
      setUser(null)
    } finally {
      setLoading(false)
    }
  }, [])

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
      setUser(null)
    } catch (error) {
      throw error
    }
  }

  const hasRole = (role: RoleCode): boolean => {
    return user?.roles?.includes(role) ?? false
  }

  const hasAnyRole = (roles: RoleCode[]): boolean => {
    return user?.roles?.some((r) => roles.includes(r)) ?? false
  }

  const hasAppointment = (position: PositionCode): boolean => {
    return user?.activeAppointments?.some((apt) => apt.positionCode === position) ?? false
  }

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

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
