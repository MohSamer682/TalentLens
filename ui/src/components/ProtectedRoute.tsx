import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import type { Role } from '@/types';

export function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <>{children}</>;
}

export function RoleRoute({ roles, children }: { roles: Role[]; children: React.ReactNode }) {
  const user = useAuthStore((s) => s.user);
  const location = useLocation();

  if (!user || !roles.includes(user.role)) {
    return <Navigate to="/dashboard" state={{ from: location }} replace />;
  }

  return <>{children}</>;
}