import { useAuthStore } from '@/features/auth/authStore';
import { PortalLayout } from './PortalLayout';

export default function PortalHome() {
  const name = useAuthStore((s) => s.user?.fullName?.split(' ')[0]);
  return (
    <PortalLayout>
      <h1 className="text-[32px] font-bold leading-tight">Hi {name}</h1>
      <p className="mt-2 max-w-prose text-ink-muted">
        You're signed in. Your recruiter will share the job, your interview stages and the schedule here.
      </p>
    </PortalLayout>
  );
}
