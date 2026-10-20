import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { ApiRequestError, apiErrorMessage, authApi } from '@/api/client';
import { useAuthStore, type Door } from '@/features/auth/authStore';
import { Field, Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Alert } from '@/components/ui/Feedback';
import { homePath, signInPath } from '@/lib/permissions';

const schema = z.object({
  email: z.string().email('Enter a valid email address'),
  password: z.string().min(1, 'Enter your password'),
});

type Values = z.infer<typeof schema>;

const OTHER: Record<Door, { door: Door; label: string }> = {
  staff: { door: 'candidate', label: 'Go to candidate sign-in' },
  candidate: { door: 'staff', label: 'Go to staff sign-in' },
};

/** The form both sign-in pages share; each page posts to its own door. */
export function SignInForm({ door, emailLabel }: { door: Door; emailLabel: string }) {
  const [error, setError] = useState<{ message: string; wrongDoor: boolean } | null>(null);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const login = useAuthStore((s) => s.login);

  const back = (location.state as { from?: Location })?.from;
  const from = back?.pathname ? back.pathname + (back.search ?? '') + (back.hash ?? '') : null;

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema) });

  const onSubmit = async (data: Values) => {
    setLoading(true);
    setError(null);
    try {
      const res = await (door === 'staff' ? authApi.staffLogin(data) : authApi.login(data));
      login(res.data.token, res.data.user);
      navigate(from ?? homePath(res.data.user), { replace: true });
    } catch (err: unknown) {
      setError({
        message: apiErrorMessage(err, 'Email or password is incorrect'),
        wrongDoor: err instanceof ApiRequestError && err.code === 'WRONG_PORTAL',
      });
    } finally {
      setLoading(false);
    }
  };

  const other = OTHER[door];
  return (
    <>
      {error && (
        <Alert className="mb-4">
          {error.message}
          {error.wrongDoor && (
            <Link to={signInPath(other.door)} className="mt-1 block font-semibold text-accent-text underline-offset-2 hover:underline">
              {other.label}
            </Link>
          )}
        </Alert>
      )}
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        <Field id="email" label={emailLabel} error={errors.email?.message}>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            placeholder="name@company.com"
            aria-invalid={!!errors.email}
            aria-describedby={errors.email ? 'email-error' : undefined}
            {...register('email')}
          />
        </Field>
        <Field id="password" label="Password" error={errors.password?.message}>
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            aria-invalid={!!errors.password}
            aria-describedby={errors.password ? 'password-error' : undefined}
            {...register('password')}
          />
        </Field>
        <Button type="submit" className="w-full" disabled={loading}>
          {loading ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>
    </>
  );
}
