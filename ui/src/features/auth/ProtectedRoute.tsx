import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/features/auth/authStore';
import type { Permission } from '@/types';
import { can, canSeeSessions, forcedPath, homePath, isCandidate, signInPath } from '@/lib/permissions';

export function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  const door = useAuthStore((s) => s.door);
  const location = useLocation();

  const forced = useAuthStore((s) => forcedPath(s.user, location.pathname));

  // Shared pages such as the profile send a signed-out visitor to the door they last used
  if (!isAuthenticated) {
    return <Navigate to={signInPath(door)} state={{ from: location }} replace />;
  }
  if (forced) {
    return <Navigate to={forced} replace />;
  }

  return <>{children}</>;
}

export function PermissionRoute({ permission, children }: { permission: Permission; children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  const allowed = useAuthStore((s) => can(s.user, permission));
  const home = useAuthStore((s) => homePath(s.user));
  const location = useLocation();
  const forced = useAuthStore((s) => forcedPath(s.user, location.pathname));

  if (!isAuthenticated) {
    return <Navigate to="/admins" state={{ from: location }} replace />;
  }
  if (forced) {
    return <Navigate to={forced} replace />;
  }
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

  const forced = useAuthStore((s) => forcedPath(s.user, location.pathname));

  if (!isAuthenticated) {
    return <Navigate to="/admins" state={{ from: location }} replace />;
  }
  if (forced) {
    return <Navigate to={forced} replace />;
  }
  if (!allowed) {
    return <Navigate to={home} replace />;
  }

  return <>{children}</>;
}

/** The portal is for candidates; staff who open it go back to their own home. */
export function CandidateRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  const candidate = useAuthStore((s) => isCandidate(s.user));
  const home = useAuthStore((s) => homePath(s.user));
  const location = useLocation();
  const forced = useAuthStore((s) => forcedPath(s.user, location.pathname));

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  if (forced) {
    return <Navigate to={forced} replace />;
  }
  if (!candidate) {
    return <Navigate to={home} replace />;
  }

  return <>{children}</>;
}
