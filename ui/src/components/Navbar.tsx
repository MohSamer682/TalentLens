import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { LogOut, Moon, Sun } from 'lucide-react';
import { useAuthStore } from '@/features/auth/authStore';
import { ROLE_LABEL, cn } from '@/lib/utils';
import { can, canSeeSessions as userCanSeeSessions, homePath } from '@/lib/permissions';
import { BrandMark } from '@/components/BrandMark';

export function Navbar() {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const [theme, setTheme] = useState(() => document.documentElement.dataset.theme ?? 'dark');
  const toggleTheme = () => {
    const next = theme === 'dark' ? 'light' : 'dark';
    document.documentElement.dataset.theme = next;
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', next === 'light' ? '#FFFFFF' : '#080C0B');
    try {
      localStorage.setItem('theme', next);
    } catch {
      /* storage blocked: the choice lasts for this tab only */
    }
    setTheme(next);
  };

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const canSeeSessions = userCanSeeSessions(user);
  const home = homePath(user);

  const links = [
    ...(canSeeSessions ? [{ to: '/dashboard', label: 'Sessions', match: ['/dashboard', '/sessions'] }] : []),
    ...((can(user, 'PIPELINE_MANAGE') || can(user, 'OFFER_APPROVE'))
      ? [{ to: '/pipeline', label: 'Pipeline', match: ['/pipeline'] }]
      : []),
    ...(can(user, 'INTERVIEW_FEEDBACK')
      ? [{ to: '/interviews', label: 'My interviews', match: ['/interviews'] }]
      : []),
    ...(can(user, 'ANALYTICS_VIEW')
      ? [{ to: '/analytics', label: 'Analytics', match: ['/analytics'] }]
      : []),
    ...((can(user, 'USER_MANAGE') || can(user, 'ROLE_MANAGE'))
      ? [{ to: '/admin/users', label: 'Admin', match: ['/admin'] }]
      : []),
  ];

  return (
    <header className="sticky top-0 z-40 border-b border-line glass text-ink">
      <div className="mx-auto flex h-14 max-w-[1280px] items-center gap-2 px-4 sm:gap-6 sm:px-6">
        <Link to={home} aria-label="TalentLens, home" className="flex shrink-0 items-center gap-2 rounded-md">
          <BrandMark size={28} />
          <span className="hidden whitespace-nowrap font-display text-[17px] font-bold tracking-tight text-ink sm:inline">TalentLens</span>
        </Link>

        <nav aria-label="Main" className="flex min-w-0 items-center self-stretch overflow-x-auto [scrollbar-width:none] sm:gap-1">
          {links.map((l) => (
            <NavLink
              key={l.to}
              to={l.to}
              className={({ isActive }) => {
                const active = isActive || l.match.some((m) => pathname.startsWith(m));
                return cn(
                  'relative flex h-14 shrink-0 items-center px-1.5 text-[14px] font-semibold transition-colors after:absolute after:inset-x-1.5 after:bottom-0 after:h-[3px] after:rounded-t-sm after:transition-colors sm:px-3 sm:text-[15px] sm:after:inset-x-3',
                  active ? 'text-ink after:bg-mark' : 'text-ink-muted after:bg-transparent hover:text-ink'
                );
              }}
            >
              {l.label}
            </NavLink>
          ))}
        </nav>

        <div className="ml-auto flex shrink-0 items-center gap-1 sm:gap-3">
          {user && (
            <div className="hidden text-right leading-tight sm:block">
              <p className="text-sm font-semibold text-ink">{user.fullName}</p>
              <p className="text-[12.5px] text-ink-muted">{ROLE_LABEL[user.role] ?? user.role}</p>
            </div>
          )}
          <button
            type="button"
            onClick={toggleTheme}
            aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
            className="flex size-9 items-center justify-center rounded-md text-ink-muted transition-colors hover:bg-surface-raised hover:text-ink"
          >
            {theme === 'dark' ? <Sun className="size-4" aria-hidden="true" /> : <Moon className="size-4" aria-hidden="true" />}
          </button>
          <button
            type="button"
            onClick={handleLogout}
            className="flex h-9 min-w-9 items-center justify-center gap-2 rounded-md px-2 text-sm sm:px-3 font-semibold text-ink-muted transition-colors hover:bg-surface-raised hover:text-ink"
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
