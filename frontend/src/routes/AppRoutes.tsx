import { Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from '../components/ProtectedRoute'
import { AppLayout } from '../layouts/AppLayout'
import { AcademicAppointmentsPage } from '../pages/AcademicAppointmentsPage'
import { AcademicManagementPage } from '../pages/AcademicManagementPage'
import { AccountPage } from '../pages/AccountPage'
import { AssessmentSetupWizardPage } from '../pages/AssessmentSetupWizardPage'
import { DashboardPage } from '../pages/DashboardPage'
import { EvaluatorAssignmentPage } from '../pages/EvaluatorAssignmentPage'
import { LoginPage } from '../pages/LoginPage'
import { MilestoneManagementPage } from '../pages/MilestoneManagementPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { TopicsPage } from '../pages/TopicsPage'
import { AnnouncementsPage } from '../pages/AnnouncementsPage'
import { GroupsPage } from '../pages/GroupsPage'
import { RegistrationsPage } from '../pages/RegistrationsPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      {/* Authenticated Layout */}
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="topics" element={<TopicsPage />} />
          <Route path="groups" element={<GroupsPage />} />
          <Route path="registrations" element={<RegistrationsPage />} />
          <Route path="progress" element={<MilestoneManagementPage />} />
          <Route path="announcements" element={<AnnouncementsPage />} />
          <Route path="account" element={<AccountPage />} />

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
