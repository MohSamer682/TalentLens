import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import type { Permission } from '@/types';
import { can } from '@/lib/permissions';

export function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <>{children}</>;
}

export function PermissionRoute({ permission, children }: { permission: Permission; children: React.ReactNode }) {
  const allowed = useAuthStore((s) => can(s.user, permission));
  const location = useLocation();

  if (!allowed) {
    return <Navigate to="/dashboard" state={{ from: location }} replace />;
  }

  return <>{children}</>;
}
