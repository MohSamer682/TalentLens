import { useEffect } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { refreshCurrentUser } from '@/api/client';
import { PermissionRoute, ProtectedRoute } from '@/components/ProtectedRoute';
import Login from '@/pages/Login';
import Register from '@/pages/Register';
import Dashboard from '@/pages/Dashboard';
import NewSession from '@/pages/NewSession';
import SessionDetail from '@/pages/SessionDetail';
import CandidateDetail from '@/pages/CandidateDetail';
import AdminUsers from '@/pages/AdminUsers';
import AdminRoles from '@/pages/AdminRoles';
import Analytics from '@/pages/Analytics';

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
          <ProtectedRoute>
            <Dashboard />
          </ProtectedRoute>
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
          <ProtectedRoute>
            <SessionDetail />
          </ProtectedRoute>
        }
      />
      <Route
        path="/sessions/:sessionId/candidate/:candidateId"
        element={
          <ProtectedRoute>
            <CandidateDetail />
          </ProtectedRoute>
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
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}