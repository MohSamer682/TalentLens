import { useEffect } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { refreshCurrentUser } from '@/api/client';
import { PermissionRoute, ProtectedRoute, SessionsRoute } from '@/components/ProtectedRoute';
import { useAuthStore } from '@/store/authStore';
import { homePath } from '@/lib/permissions';
import Login from '@/pages/Login';
import Register from '@/pages/Register';
import Dashboard from '@/pages/Dashboard';
import NewSession from '@/pages/NewSession';
import SessionDetail from '@/pages/SessionDetail';
import CandidateDetail from '@/pages/CandidateDetail';
import Pipeline from '@/pages/Pipeline';
import MyInterviews from '@/pages/MyInterviews';
import InterviewFeedback from '@/pages/InterviewFeedback';
import AdminUsers from '@/pages/AdminUsers';
import AdminRoles from '@/pages/AdminRoles';
import Analytics from '@/pages/Analytics';

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
  );
}