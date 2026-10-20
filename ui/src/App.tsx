import { Suspense, lazy, useEffect } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { refreshCurrentUser } from '@/api/client';
import { PermissionRoute, ProtectedRoute, SessionsRoute } from '@/features/auth/ProtectedRoute';
import { useAuthStore } from '@/features/auth/authStore';
import { homePath } from '@/lib/permissions';
import Login from '@/features/auth/Login';
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
const AdminRoles = lazy(() => import('@/features/admin/AdminRoles'));
const Analytics = lazy(() => import('@/features/analytics/AnalyticsPage'));

function RootRedirect() {
  const user = useAuthStore((s) => s.user);
  return <Navigate to={user ? homePath(user) : '/login'} replace />;
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
      <Route path="/register" element={<Register />} />
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
            <AdminRoles />
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
      <Route path="/" element={<RootRedirect />} />
      <Route path="*" element={<RootRedirect />} />
    </Routes>
    </Suspense>
  );
}