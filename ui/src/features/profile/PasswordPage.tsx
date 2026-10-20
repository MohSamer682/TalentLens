import { useNavigate } from 'react-router-dom';
import { authApi } from '@/api/client';
import { useAuthStore } from '@/features/auth/authStore';
import { PasswordForm } from '@/features/profile/PasswordForm';
import { Alert } from '@/components/ui/Feedback';
import { AuthLayout } from '@/layouts/AuthLayout';
import { homePath } from '@/lib/permissions';

/** Shown instead of everything else while an account still has its temporary password. */
export default function PasswordPage() {
  const navigate = useNavigate();
  const setUser = useAuthStore((s) => s.setUser);
  const logout = useAuthStore((s) => s.logout);

  const changed = async () => {
    const { data } = await authApi.me();
    setUser(data);
    navigate(homePath(data), { replace: true });
  };

  return (
    <AuthLayout
      title="Choose a new password"
      subtitle="Your administrator set a temporary password for you."
      footer={
        <button type="button" className="font-semibold text-accent-text hover:underline" onClick={() => { logout(); navigate('/login'); }}>
          Sign out instead
        </button>
      }
    >
      <Alert tone="info" className="mb-5">Choose a new password to continue.</Alert>
      <PasswordForm onChanged={changed} submitLabel="Save and continue" />
    </AuthLayout>
  );
}
