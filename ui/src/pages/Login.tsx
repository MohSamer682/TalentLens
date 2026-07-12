import { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import axios from 'axios';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { authApi } from '@/api/client';
import { useAuthStore } from '@/store/authStore';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Briefcase, ArrowRight } from 'lucide-react';

const loginSchema = z.object({
  email: z.string().email('Invalid email address'),
  password: z.string().min(1, 'Password is required'),
});

type LoginForm = z.infer<typeof loginSchema>;

export default function Login() {
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const login = useAuthStore((s) => s.login);

  const from = (location.state as { from?: Location })?.from?.pathname || '/dashboard';

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
  });

  const onSubmit = async (data: LoginForm) => {
    setLoading(true);
    setError('');
    try {
      const res = await authApi.login(data);
      login(res.data.token, res.data.user);
      navigate(from, { replace: true });
    } catch (err: unknown) {
      if (axios.isAxiosError(err)) {
        setError(err.response?.data?.message || 'Invalid email or password');
      } else {
        setError('An error occurred');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="min-h-screen relative flex items-center justify-center p-6 bg-surface">
      <div className="w-full max-w-md z-10">
        <div className="glass-card rounded-xl p-10 md:p-14 shadow-2xl ghost-border">
          <div className="flex flex-col items-center mb-10">
            <div className="w-14 h-14 rounded-2xl bg-surface-container-lowest shadow-sm flex items-center justify-center mb-6 ghost-border">
              <Briefcase className="w-8 h-8 text-primary" />
            </div>
            <h1 className="text-2xl font-bold tracking-tight text-on-surface text-center">
              Resume Screener
            </h1>
            <p className="text-on-surface-variant text-sm mt-2 font-medium opacity-70">
              Elevating Talent Acquisition
            </p>
          </div>

          {error && (
            <div className="mb-6 p-4 bg-red-50 border border-red-100 rounded-2xl text-red-600 text-sm">
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
            <div className="space-y-2">
              <label
                className="block text-[0.6875rem] uppercase tracking-wider font-semibold text-on-surface-variant px-1"
                htmlFor="email"
              >
                Corporate Email
              </label>
              <div className="relative group">
                <Input
                  id="email"
                  type="email"
                  placeholder="name@company.com"
                  {...register('email')}
                />
              </div>
              {errors.email && (
                <p className="text-red-500 text-xs">{errors.email.message}</p>
              )}
            </div>

            <div className="space-y-2">
              <div className="flex justify-between items-center px-1">
                <label
                  className="text-[0.6875rem] uppercase tracking-wider font-semibold text-on-surface-variant"
                  htmlFor="password"
                >
                  Security Key
                </label>
              </div>
              <Input
                id="password"
                type="password"
                placeholder="••••••••"
                {...register('password')}
              />
              {errors.password && (
                <p className="text-red-500 text-xs">{errors.password.message}</p>
              )}
            </div>

            <div className="pt-4">
              <Button
                type="submit"
                className="w-full"
                disabled={loading}
              >
                {loading ? (
                  'Signing in...'
                ) : (
                  <>
                    <span>Access Platform</span>
                    <ArrowRight className="w-5 h-5" />
                  </>
                )}
              </Button>
            </div>
          </form>

          <div className="mt-10 text-center">
            <p className="text-sm text-on-surface-variant">
              New to Resume Screener?{' '}
              <Link
                to="/register"
                className="text-primary font-bold hover:underline underline-offset-4"
              >
                Request Access
              </Link>
            </p>
          </div>
        </div>

        <div className="mt-12 text-center">
          <div className="flex justify-center gap-6 mb-4">
            <span className="text-[0.6875rem] font-medium text-on-surface-variant uppercase tracking-widest">
              Privacy
            </span>
            <span className="text-[0.6875rem] font-medium text-on-surface-variant uppercase tracking-widest">
              Security
            </span>
            <span className="text-[0.6875rem] font-medium text-on-surface-variant uppercase tracking-widest">
              Contact
            </span>
          </div>
          <p className="text-[0.625rem] text-on-surface-variant/40 tracking-widest uppercase">
            © 2024 Resume Screener. Intelligence Driven Recruitment.
          </p>
        </div>
      </div>
    </main>
  );
}