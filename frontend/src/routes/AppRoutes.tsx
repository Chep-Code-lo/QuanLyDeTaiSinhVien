import { Route, Routes } from 'react-router-dom'
import { AppLayout } from '../layouts/AppLayout'
import { AccountPage } from '../pages/AccountPage'
import { AnnouncementsPage } from '../pages/AnnouncementsPage'
import { DashboardPage } from '../pages/DashboardPage'
import { EvaluationsPage } from '../pages/EvaluationsPage'
import { GroupsPage } from '../pages/GroupsPage'
import { LoginPage } from '../pages/LoginPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { ProgressPage } from '../pages/ProgressPage'
import { RegistrationsPage } from '../pages/RegistrationsPage'
import { TopicsPage } from '../pages/TopicsPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<AppLayout />}>
        <Route index element={<DashboardPage />} />
        <Route path="topics" element={<TopicsPage />} />
        <Route path="groups" element={<GroupsPage />} />
        <Route path="registrations" element={<RegistrationsPage />} />
        <Route path="progress" element={<ProgressPage />} />
        <Route path="evaluations" element={<EvaluationsPage />} />
        <Route path="announcements" element={<AnnouncementsPage />} />
        <Route path="account" element={<AccountPage />} />
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
