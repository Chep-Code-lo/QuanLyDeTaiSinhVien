import { createContext, useContext } from 'react'
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

export const AuthContext = createContext<AuthContextType | undefined>(undefined)

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
