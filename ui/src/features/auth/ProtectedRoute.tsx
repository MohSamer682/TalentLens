import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/features/auth/authStore';
import type { Permission } from '@/types';
import { can, canSeeSessions, homePath } from '@/lib/permissions';

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
  const home = useAuthStore((s) => homePath(s.user));
  const location = useLocation();

  if (!allowed) {
    return <Navigate to={home} state={{ from: location }} replace />;
  }

  return <>{children}</>;
}

/** Sessions belong to people who run or oversee screening. Interviewers are sent to their own home. */
export function SessionsRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  const allowed = useAuthStore((s) => canSeeSessions(s.user));
  const home = useAuthStore((s) => homePath(s.user));
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  if (!allowed) {
    return <Navigate to={home} replace />;
  }

  return <>{children}</>;
}
