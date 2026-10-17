import { Routes, Route, Navigate } from 'react-router-dom';
import { ProtectedRoute, RoleRoute } from '@/components/ProtectedRoute';
import Login from '@/pages/Login';
import Register from '@/pages/Register';
import Dashboard from '@/pages/Dashboard';
import NewSession from '@/pages/NewSession';
import SessionDetail from '@/pages/SessionDetail';
import CandidateDetail from '@/pages/CandidateDetail';
import AdminUsers from '@/pages/AdminUsers';
import Analytics from '@/pages/Analytics';

export default function App() {
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
          <ProtectedRoute>
            <NewSession />
          </ProtectedRoute>
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
          <RoleRoute roles={['ADMIN']}>
            <AdminUsers />
          </RoleRoute>
        }
      />
      <Route
        path="/analytics"
        element={
          <RoleRoute roles={['ADMIN', 'HR_CHIEF']}>
            <Analytics />
          </RoleRoute>
        }
      />
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}