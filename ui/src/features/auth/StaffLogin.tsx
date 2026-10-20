import { Link, Navigate, useLocation } from 'react-router-dom';
import { AuthLayout } from '@/layouts/AuthLayout';
import { Alert } from '@/components/ui/Feedback';
import { Button } from '@/components/ui/Button';
import { homePath } from '@/lib/permissions';
import { useAuthStore } from './authStore';
import { SignInForm } from './SignInForm';

export default function StaffLogin() {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const location = useLocation();
  const back = (location.state as { from?: Location })?.from;
  const home = user ? homePath(user) : null;
  // Signing in re-renders this page with a user; send them where they were going, as the form does
  if (home && home !== '/admins') {
    return <Navigate to={back?.pathname ? back.pathname + (back.search ?? '') + (back.hash ?? '') : home} replace />;
  }
  return (
    <AuthLayout
      title="TalentLens for teams"
      subtitle="Sign in with your work account. Accounts are created by your administrator."
      footer={
        <>
          Looking for your application?{' '}
          <Link to="/login" className="font-semibold text-accent-text underline-offset-2 hover:underline">
            Candidate sign-in
          </Link>
        </>
      }
    >
      {user ? (
        // Signed in, but the role holds no permission yet, so there is nowhere to send them
        <Alert
          tone="info"
          title="Your account has no access yet"
          action={
            <Button variant="secondary" size="sm" onClick={logout}>
              Sign out
            </Button>
          }
        >
          Ask your administrator to give your role a permission.
        </Alert>
      ) : (
        <SignInForm door="staff" emailLabel="Work email" />
      )}
    </AuthLayout>
  );
}
