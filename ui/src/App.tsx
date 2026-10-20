import { Suspense, lazy, useEffect } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { refreshCurrentUser } from '@/api/client';
import { CandidateRoute, PermissionRoute, ProtectedRoute, SessionsRoute } from '@/features/auth/ProtectedRoute';
import { useAuthStore } from '@/features/auth/authStore';
import { homePath, signInPath } from '@/lib/permissions';
import Login from '@/features/auth/Login';
import StaffLogin from '@/features/auth/StaffLogin';
import Register from '@/features/auth/Register';
import { Loader } from '@/components/ui/Feedback';

// Login and Register load up front; every other page is fetched when first visited
const Dashboard = lazy(() => import('@/features/sessions/Dashboard'));
const NewSession = lazy(() => import('@/features/sessions/NewSession'));
const SessionDetail = lazy(() => import('@/features/sessions/SessionDetail'));
const CandidateDetail = lazy(() => import('@/features/candidates/CandidateDetail'));
const Pipeline = lazy(() => import('@/features/pipeline/PipelinePage'));
const MyInterviews = lazy(() => import('@/features/pipeline/MyInterviews'));
const InterviewFeedback = lazy(() => import('@/features/pipeline/InterviewFeedback'));
const AdminUsers = lazy(() => import('@/features/admin/AdminUsers'));
const RolesOverview = lazy(() => import('@/features/admin/roles/RolesOverview'));
const RoleDetail = lazy(() => import('@/features/admin/roles/RoleDetail'));
const Analytics = lazy(() => import('@/features/analytics/AnalyticsPage'));
const ProfilePage = lazy(() => import('@/features/profile/ProfilePage'));
const PasswordPage = lazy(() => import('@/features/profile/PasswordPage'));
const InvitePage = lazy(() => import('@/features/portal/InvitePage'));
const ApplicationsPage = lazy(() => import('@/features/portal/ApplicationsPage'));
const ApplicationPage = lazy(() => import('@/features/portal/ApplicationPage'));

function RootRedirect() {
  const user = useAuthStore((s) => s.user);
  const door = useAuthStore((s) => s.door);
  return <Navigate to={user ? homePath(user) : signInPath(door)} replace />;
}

export default function App() {
  // Permissions stored at sign-in go stale when an admin edits a role; refresh them once per load.
  useEffect(() => {
    refreshCurrentUser();
  }, []);

  return (
    <Suspense fallback={<Loader label="Loading…" />}>
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/admins" element={<StaffLogin />} />
      <Route path="/register" element={<Register />} />
      <Route path="/invite" element={<InvitePage />} />
      <Route
        path="/portal"
        element={
          <CandidateRoute>
            <ApplicationsPage />
          </CandidateRoute>
        }
      />
      <Route
        path="/portal/applications/:id"
        element={
          <CandidateRoute>
            <ApplicationPage />
          </CandidateRoute>
        }
      />
      <Route
        path="/dashboard"
        element={
          <SessionsRoute>
            <Dashboard />
          </SessionsRoute>
        }
      />
      <Route
        path="/sessions/new/:id?"
        element={
          <PermissionRoute permission="SESSION_RUN">
            <NewSession />
          </PermissionRoute>
        }
      />
      <Route
        path="/sessions/:id"
        element={
          <SessionsRoute>
            <SessionDetail />
          </SessionsRoute>
        }
      />
      <Route
        path="/sessions/:sessionId/candidate/:candidateId"
        element={
          <SessionsRoute>
            <CandidateDetail />
          </SessionsRoute>
        }
      />
      <Route
        path="/sessions/:sessionId/candidates/:candidateId"
        element={
          <SessionsRoute>
            <CandidateDetail />
          </SessionsRoute>
        }
      />
      <Route
        path="/pipeline"
        element={
          <ProtectedRoute>
            <Pipeline />
          </ProtectedRoute>
        }
      />
      <Route
        path="/interviews"
        element={
          <PermissionRoute permission="INTERVIEW_FEEDBACK">
            <MyInterviews />
          </PermissionRoute>
        }
      />
      <Route
        path="/interviews/:interviewId/feedback"
        element={
          <PermissionRoute permission="INTERVIEW_FEEDBACK">
            <InterviewFeedback />
          </PermissionRoute>
        }
      />
      <Route
        path="/admin/users"
        element={
          <PermissionRoute permission="USER_MANAGE">
            <AdminUsers />
          </PermissionRoute>
        }
      />
      <Route
        path="/admin/roles"
        element={
          <PermissionRoute permission="ROLE_MANAGE">
            <RolesOverview />
          </PermissionRoute>
        }
      />
      <Route
        path="/admin/roles/:role"
        element={
          <PermissionRoute permission="ROLE_MANAGE">
            <RoleDetail />
          </PermissionRoute>
        }
      />
      <Route
        path="/analytics"
        element={
          <PermissionRoute permission="ANALYTICS_VIEW">
            <Analytics />
          </PermissionRoute>
        }
      />
      <Route
        path="/profile"
        element={
          <ProtectedRoute>
            <ProfilePage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/profile/password"
        element={
          <ProtectedRoute>
            <PasswordPage />
          </ProtectedRoute>
        }
      />
      <Route path="/" element={<RootRedirect />} />
      <Route path="*" element={<RootRedirect />} />
    </Routes>
    </Suspense>
  );
}