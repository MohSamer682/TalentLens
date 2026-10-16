import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { ListOrdered, LogOut } from 'lucide-react';
import { useAuthStore } from '@/store/authStore';
import { cn } from '@/lib/utils';

export function Navbar() {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const links = [
    { to: '/dashboard', label: 'Sessions', match: ['/dashboard', '/sessions'] },
    ...(user?.role === 'ADMIN' ? [{ to: '/admin/users', label: 'Users', match: ['/admin'] }] : []),
  ];

  return (
    <header className="sticky top-0 z-40 border-b border-white/[0.06] bg-pitch text-white">
      <div className="mx-auto flex h-14 max-w-[1280px] items-center gap-2 px-4 sm:gap-6 sm:px-6">
        <Link to="/dashboard" className="flex shrink-0 items-center gap-2 rounded-md">
          <span className="flex size-7 items-center justify-center rounded-md bg-accent text-accent-ink" aria-hidden="true">
            <ListOrdered className="size-4" />
          </span>
          <span className="condensed whitespace-nowrap text-base font-bold uppercase tracking-wide sm:text-lg">Resume Screener</span>
        </Link>

        <nav aria-label="Main" className="flex items-center gap-0.5 sm:gap-1">
          {links.map((l) => (
            <NavLink
              key={l.to}
              to={l.to}
              className={({ isActive }) => {
                const active = isActive || l.match.some((m) => pathname.startsWith(m));
                return cn(
                  'condensed flex h-8 items-center rounded-full px-2.5 text-[15px] font-semibold uppercase tracking-wide transition-colors sm:px-3.5',
                  active ? 'bg-white/[0.12] text-white' : 'text-pitch-ink hover:bg-white/[0.06] hover:text-white'
                );
              }}
            >
              {l.label}
            </NavLink>
          ))}
        </nav>

        <div className="ml-auto flex items-center gap-3">
          {user && (
            <div className="hidden text-right leading-tight sm:block">
              <p className="text-sm font-semibold">{user.fullName}</p>
              <p className="text-xs text-pitch-ink">{user.role === 'ADMIN' ? 'Admin' : 'Recruiter'}</p>
            </div>
          )}
          <button
            type="button"
            onClick={handleLogout}
            className="flex h-9 items-center gap-2 rounded-full px-3 text-sm font-semibold text-pitch-ink transition-colors hover:bg-pitch-raised hover:text-white"
          >
            <LogOut className="size-4" aria-hidden="true" />
            <span className="hidden sm:inline">Sign out</span>
            <span className="sr-only sm:hidden">Sign out</span>
          </button>
        </div>
      </div>
    </header>
  );
}
