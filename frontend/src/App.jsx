import { Navigate, Route, Routes } from 'react-router-dom'

import AdminGuard from './components/admin/AdminGuard'
import AdminDashboardPage from './pages/admin/AdminDashboardPage'
import AdminExpensesPage from './pages/admin/AdminExpensesPage'
import AdminLoginPage from './pages/admin/AdminLoginPage'
import AdminTasksPage from './pages/admin/AdminTasksPage'
import AdminUsersPage from './pages/admin/AdminUsersPage'
import DashboardPage from './pages/DashboardPage'
import ExpenseDetailPage from './pages/ExpenseDetailPage'
import ExpensesPage from './pages/ExpensesPage'
import ForbiddenPage from './pages/ForbiddenPage'
import HistoryPage from './pages/HistoryPage'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'
import ProfilePage from './pages/ProfilePage'
import ProjectDetailPage from './pages/ProjectDetailPage'
import ProjectMembersPage from './pages/ProjectMembersPage'
import ProjectsPage from './pages/ProjectsPage'
import RegisterPage from './pages/RegisterPage'
import TasksPage from './pages/TasksPage'
import TaskDetailPage from './pages/TaskDetailPage'
import TaskCreatePage from './pages/TaskCreatePage'
import SubmissionReviewPage from './pages/SubmissionReviewPage'

function RequireAuth({ children }) {
  const accessToken = localStorage.getItem('accessToken')
  return accessToken ? children : <Navigate to="/login" replace />
}

function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/403" element={<ForbiddenPage />} />
      <Route path="/404" element={<NotFoundPage />} />
      <Route path="/admin" element={<Navigate to="/admin/login" replace />} />
      <Route path="/admin/login" element={<AdminLoginPage />} />
      <Route
        path="/admin/dashboard"
        element={
          <AdminGuard>
            <AdminDashboardPage />
          </AdminGuard>
        }
      />
      <Route
        path="/admin/users"
        element={
          <AdminGuard>
            <AdminUsersPage />
          </AdminGuard>
        }
      />
      <Route
        path="/admin/tasks"
        element={
          <AdminGuard>
            <AdminTasksPage />
          </AdminGuard>
        }
      />
      <Route
        path="/admin/expenses"
        element={
          <AdminGuard>
            <AdminExpensesPage />
          </AdminGuard>
        }
      />
      <Route
        path="/dashboard"
        element={
          <RequireAuth>
            <DashboardPage />
          </RequireAuth>
        }
      />
      <Route
        path="/history"
        element={
          <RequireAuth>
            <HistoryPage />
          </RequireAuth>
        }
      />
      <Route
        path="/profile"
        element={
          <RequireAuth>
            <ProfilePage />
          </RequireAuth>
        }
      />
      <Route path="/tasks" element={<RequireAuth><TasksPage /></RequireAuth>} />
      <Route path="/tasks/new" element={<RequireAuth><TaskCreatePage /></RequireAuth>} />
      <Route path="/tasks/:taskId" element={<RequireAuth><TaskDetailPage /></RequireAuth>} />
      <Route path="/submissions/:submissionId/review" element={<RequireAuth><SubmissionReviewPage /></RequireAuth>} />
      <Route path="/projects" element={<RequireAuth><ProjectsPage /></RequireAuth>} />
      <Route path="/projects/:projectId" element={<RequireAuth><ProjectDetailPage /></RequireAuth>} />
      <Route path="/projects/:projectId/members" element={<RequireAuth><ProjectMembersPage /></RequireAuth>} />
      <Route path="/expenses" element={<RequireAuth><ExpensesPage /></RequireAuth>} />
      <Route path="/expenses/:expenseId" element={<RequireAuth><ExpenseDetailPage /></RequireAuth>} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

export default App
