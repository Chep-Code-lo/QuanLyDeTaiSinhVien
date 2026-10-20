import { useState } from 'react'
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import { PublicHome } from '../pages/PublicHome'
import { StudentProgressPage } from '../pages/StudentProgressPage'
import { StudentResultsPage } from '../pages/StudentResultsPage'
import { ProtectedRoute } from '../components/ProtectedRoute'
import { AppLayout } from '../layouts/AppLayout'
import { AcademicAppointmentsPage } from '../pages/AcademicAppointmentsPage'
import { AcademicManagementPage } from '../pages/AcademicManagementPage'
import { AccountPage } from '../pages/AccountPage'
import { AssessmentSetupWizardPage } from '../pages/AssessmentSetupWizardPage'
import { DashboardPage } from '../pages/DashboardPage'
import { EvaluatorAssignmentPage } from '../pages/EvaluatorAssignmentPage'
import { LoginForm, LoginPage } from '../pages/LoginPage'
import { MilestoneManagementPage } from '../pages/MilestoneManagementPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { TopicsPage } from '../pages/TopicsPage'
import { AnnouncementsPage } from '../pages/AnnouncementsPage'
import { GroupsPage } from '../pages/GroupsPage'
import { RegistrationsPage } from '../pages/RegistrationsPage'
import { useAuth } from '../context/AuthContext'

function PublicHomeRoute() {
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()
  const [loginOpen, setLoginOpen] = useState(false)
  if (isAuthenticated) return <Navigate to="/dashboard" replace />
  return <>
    <PublicHome onLogin={() => setLoginOpen(true)} />
    {loginOpen && <div className="public-login-backdrop" role="dialog" aria-modal="true" aria-label="Đăng nhập hệ thống">
      <LoginForm onClose={() => setLoginOpen(false)} onSuccess={() => navigate('/dashboard')} />
    </div>}
  </>
}

export function AppRoutes() {
  const { hasRole } = useAuth()
  const student = hasRole('STUDENT') && !hasRole('ADMIN') && !hasRole('LECTURER')
  return (
    <Routes>
      <Route path="/" element={<PublicHomeRoute />} />
      <Route path="/login" element={<LoginPage />} />

      {/* Authenticated Layout */}
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="topics" element={<TopicsPage />} />
          <Route path="groups" element={<GroupsPage />} />
          <Route path="registrations" element={<RegistrationsPage />} />
          <Route path="progress" element={student ? <StudentProgressPage /> : <MilestoneManagementPage />} />
          <Route path="announcements" element={<AnnouncementsPage />} />
          <Route path="account" element={<AccountPage />} />

          <Route element={<ProtectedRoute roles={['STUDENT']} />}>
            <Route path="results" element={<StudentResultsPage />} />
          </Route>
          {/* Lecturer & Admin Routes */}
          <Route element={<ProtectedRoute roles={['LECTURER', 'ADMIN']} />}>
            <Route path="evaluations" element={<AssessmentSetupWizardPage />} />
            <Route path="evaluator-assignment" element={<EvaluatorAssignmentPage />} />
          </Route>

          {/* Academic Management Route (DEAN / DEPARTMENT_HEAD / ADMIN) */}
          <Route element={<ProtectedRoute requireAcademicManagement />}>
            <Route path="academic-management" element={<AcademicManagementPage />} />
          </Route>

          {/* Admin Only Route */}
          <Route element={<ProtectedRoute roles={['ADMIN']} />}>
            <Route path="admin-appointments" element={<AcademicAppointmentsPage />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
