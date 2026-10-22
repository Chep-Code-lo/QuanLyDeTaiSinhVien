import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import { ProtectedRoute } from '../components/ProtectedRoute'
import { AppLayout } from '../layouts/AppLayout'
import { LoginPage } from '../pages/LoginPage'
import { useAuth } from '../context/AuthContext'

const PublicHome = lazy(() =>
  import('../pages/PublicHome').then((module) => ({ default: module.PublicHome })),
)
const StudentProgressPage = lazy(() =>
  import('../pages/StudentProgressPage').then((module) => ({
    default: module.StudentProgressPage,
  })),
)
const StudentResultsPage = lazy(() =>
  import('../pages/StudentResultsPage').then((module) => ({ default: module.StudentResultsPage })),
)
const AcademicAppointmentsPage = lazy(() =>
  import('../pages/AcademicAppointmentsPage').then((module) => ({
    default: module.AcademicAppointmentsPage,
  })),
)
const AcademicManagementPage = lazy(() =>
  import('../pages/AcademicManagementPage').then((module) => ({
    default: module.AcademicManagementPage,
  })),
)
const AccountPage = lazy(() =>
  import('../pages/AccountPage').then((module) => ({ default: module.AccountPage })),
)
const UserManagementPage = lazy(() =>
  import('../pages/UserManagementPage').then((module) => ({ default: module.UserManagementPage })),
)
const EmailSettingsPage = lazy(() =>
  import('../pages/EmailSettingsPage').then((module) => ({ default: module.EmailSettingsPage })),
)
const AssessmentSetupWizardPage = lazy(() =>
  import('../pages/AssessmentSetupWizardPage').then((module) => ({
    default: module.AssessmentSetupWizardPage,
  })),
)
const DashboardPage = lazy(() =>
  import('../pages/DashboardPage').then((module) => ({ default: module.DashboardPage })),
)
const EvaluatorAssignmentPage = lazy(() =>
  import('../pages/EvaluatorAssignmentPage').then((module) => ({
    default: module.EvaluatorAssignmentPage,
  })),
)
const AuditLogsPage = lazy(() =>
  import('../pages/AuditLogsPage').then((module) => ({ default: module.AuditLogsPage })),
)
const GradeChangesPage = lazy(() =>
  import('../pages/GradeChangesPage').then((module) => ({ default: module.GradeChangesPage })),
)
const TopicWorkflowPage = lazy(() =>
  import('../pages/TopicWorkflowPage').then((module) => ({ default: module.TopicWorkflowPage })),
)
const AcademicCatalogPage = lazy(() =>
  import('../pages/AcademicCatalogPage').then((module) => ({
    default: module.AcademicCatalogPage,
  })),
)
const ProjectAssignmentsPage = lazy(() =>
  import('../pages/ProjectAssignmentsPage').then((module) => ({
    default: module.ProjectAssignmentsPage,
  })),
)
const EvaluationResultsPage = lazy(() =>
  import('../pages/EvaluationResultsPage').then((module) => ({
    default: module.EvaluationResultsPage,
  })),
)
const EvaluationsPage = lazy(() =>
  import('../pages/EvaluationsPage').then((module) => ({ default: module.EvaluationsPage })),
)
const MilestoneManagementPage = lazy(() =>
  import('../pages/MilestoneManagementPage').then((module) => ({
    default: module.MilestoneManagementPage,
  })),
)
const NotFoundPage = lazy(() =>
  import('../pages/NotFoundPage').then((module) => ({ default: module.NotFoundPage })),
)
const TopicsPage = lazy(() =>
  import('../pages/TopicsPage').then((module) => ({ default: module.TopicsPage })),
)
const AnnouncementsPage = lazy(() =>
  import('../pages/AnnouncementsPage').then((module) => ({ default: module.AnnouncementsPage })),
)
const GroupsPage = lazy(() =>
  import('../pages/GroupsPage').then((module) => ({ default: module.GroupsPage })),
)
const RegistrationsPage = lazy(() =>
  import('../pages/RegistrationsPage').then((module) => ({ default: module.RegistrationsPage })),
)

function PublicHomeRoute() {
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />
  }

  return <PublicHome onLogin={() => navigate('/login')} />
}

export function AppRoutes() {
  const { hasRole } = useAuth()
  const student = hasRole('STUDENT') && !hasRole('ADMIN') && !hasRole('LECTURER')

  return (
    <Suspense
      fallback={
        <div className="content-state" role="status">
          <span className="spinner" />
        </div>
      }
    >
      <Routes>
        <Route path="/" element={<PublicHomeRoute />} />
        <Route path="/login" element={<LoginPage />} />

        {/* Authenticated Layout */}
        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route path="dashboard" element={<DashboardPage />} />
            <Route path="topics" element={<TopicsPage />} />
            <Route path="topics/:id" element={<TopicWorkflowPage />} />
            <Route path="topic-workflow" element={<TopicWorkflowPage />} />
            <Route path="groups" element={<GroupsPage />} />
            <Route path="registrations" element={<RegistrationsPage />} />

            {/* Progress based on user role */}
            <Route
              path="progress"
              element={student ? <StudentProgressPage /> : <MilestoneManagementPage />}
            />

            <Route path="announcements" element={<AnnouncementsPage />} />
            <Route path="account" element={<AccountPage />} />

            {/* Student Routes */}
            <Route element={<ProtectedRoute roles={['STUDENT']} />}>
              <Route path="results" element={<StudentResultsPage />} />
              <Route path="results/:id" element={<Navigate to="/results" replace />} />
            </Route>

            {/* Lecturer & Admin Routes */}
            <Route element={<ProtectedRoute roles={['LECTURER']} />}>
              <Route path="score-sheets" element={<EvaluationsPage />} />
            </Route>
            <Route element={<ProtectedRoute roles={['LECTURER', 'ADMIN']} />}>
              <Route path="email-settings" element={<EmailSettingsPage />} />
              <Route path="evaluations" element={<AssessmentSetupWizardPage />} />
              <Route path="evaluator-assignment" element={<EvaluatorAssignmentPage />} />
              <Route path="evaluation-results" element={<EvaluationResultsPage />} />
              <Route path="grade-change-requests" element={<GradeChangesPage />} />
              <Route path="project-assignments" element={<ProjectAssignmentsPage />} />
            </Route>

            {/* Academic Management Routes */}
            <Route element={<ProtectedRoute requireAcademicManagement />}>
              <Route path="academic-management" element={<AcademicManagementPage />} />
              <Route path="academic-catalog" element={<AcademicCatalogPage />} />
            </Route>

            {/* Admin Only Routes */}
            <Route element={<ProtectedRoute roles={['ADMIN']} />}>
              <Route path="admin-users" element={<UserManagementPage />} />
              <Route path="audit-logs" element={<AuditLogsPage />} />
              <Route path="admin-users/new" element={<Navigate to="/admin-users" replace />} />
              <Route path="admin-appointments" element={<AcademicAppointmentsPage />} />
            </Route>
          </Route>
        </Route>

        {/* Not Found */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  )
}
