import { ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { LogOut } from 'lucide-react';
import { BrandMark } from '@/components/BrandMark';
import { useAuthStore } from '@/features/auth/authStore';
import { getInitials } from '@/lib/utils';

/** The candidate's shell: no staff navigation, just home, their profile and sign out. */
export function PortalLayout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();
  return (
    <div className="min-h-screen bg-ground">
      <header className="sticky top-0 z-40 border-b border-line glass text-ink">
        <div className="mx-auto flex h-14 max-w-[960px] items-center gap-3 px-4 sm:px-6">
          <Link to="/portal" aria-label="TalentLens, your applications" className="flex items-center gap-2 rounded-md">
            <BrandMark size={28} />
            <span className="font-display text-[17px] font-bold tracking-tight">TalentLens</span>
          </Link>
          <div className="ml-auto flex items-center gap-1">
            <Link to="/profile" className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-sunken">
              <span aria-hidden="true" className="flex size-7 items-center justify-center rounded bg-sunken text-[12px] font-bold text-ink-muted">
                {getInitials(user?.fullName ?? '')}
              </span>
              <span className="hidden sm:inline">{user?.fullName}</span>
            </Link>
            <button
              type="button"
              onClick={() => {
                logout();
                navigate('/login');
              }}
              className="flex items-center gap-1.5 rounded-md px-2 py-1.5 text-sm text-ink-muted hover:bg-sunken hover:text-ink"
            >
              <LogOut className="size-4" aria-hidden="true" />
              <span className="hidden sm:inline">Sign out</span>
              <span className="sr-only sm:hidden">Sign out</span>
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto w-full max-w-[960px] px-4 py-8 sm:px-6 sm:py-10">{children}</main>
    </div>
  );
}
