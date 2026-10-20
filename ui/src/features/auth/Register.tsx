import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { apiErrorMessage, authApi } from '@/api/client';
import { useAuthStore } from '@/features/auth/authStore';
import { Field, Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Alert } from '@/components/ui/Feedback';
import { AuthLayout } from '@/layouts/AuthLayout';

const registerSchema = z
  .object({
    fullName: z.string().trim().min(1, 'Enter your full name'),
    email: z.string().email('Enter a valid email address'),
    password: z.string().min(8, 'Use at least 8 characters'),
    confirmPassword: z.string().min(1, 'Repeat the password'),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'The two passwords do not match',
    path: ['confirmPassword'],
  });

type RegisterForm = z.infer<typeof registerSchema>;

export default function Register() {
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const login = useAuthStore((s) => s.login);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterForm>({ resolver: zodResolver(registerSchema) });

  const onSubmit = async (data: RegisterForm) => {
    setLoading(true);
    setError('');
    try {
      const res = await authApi.register({ email: data.email, password: data.password, fullName: data.fullName });
      login(res.data.token, res.data.user);
      navigate('/dashboard');
    } catch (err: unknown) {
      setError(apiErrorMessage(err, 'Could not create the account'));
    } finally {
      setLoading(false);
    }
  };

  const invalid = (name: keyof RegisterForm) => ({
    'aria-invalid': !!errors[name],
    'aria-describedby': errors[name] ? `${name}-error` : undefined,
  });

  return (
    <AuthLayout
      title="Create an account"
      subtitle="New accounts start as recruiters. An admin can change your role."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" className="font-semibold text-accent-text underline-offset-2 hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      {error && <Alert className="mb-4">{error}</Alert>}
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        <Field id="fullName" label="Full name" error={errors.fullName?.message}>
          <Input id="fullName" autoComplete="name" {...invalid('fullName')} {...register('fullName')} />
        </Field>
        <Field id="email" label="Work email" error={errors.email?.message}>
          <Input id="email" type="email" autoComplete="email" placeholder="name@company.com" {...invalid('email')} {...register('email')} />
        </Field>
        <Field id="password" label="Password" hint="At least 8 characters." error={errors.password?.message}>
          <Input id="password" type="password" autoComplete="new-password" {...invalid('password')} {...register('password')} />
        </Field>
        <Field id="confirmPassword" label="Repeat password" error={errors.confirmPassword?.message}>
          <Input id="confirmPassword" type="password" autoComplete="new-password" {...invalid('confirmPassword')} {...register('confirmPassword')} />
        </Field>
        <Button type="submit" className="w-full" disabled={loading}>
          {loading ? 'Creating account…' : 'Create account'}
        </Button>
      </form>
    </AuthLayout>
  );
}
