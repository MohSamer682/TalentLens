import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { ListOrdered, LogOut, Moon, Sun } from 'lucide-react';
import { useAuthStore } from '@/store/authStore';
import { cn } from '@/lib/utils';

export function Navbar() {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const [theme, setTheme] = useState(() => document.documentElement.dataset.theme ?? 'dark');
  const toggleTheme = () => {
    const next = theme === 'dark' ? 'light' : 'dark';
    document.documentElement.dataset.theme = next;
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

  const links = [
    { to: '/dashboard', label: 'Sessions', match: ['/dashboard', '/sessions'] },
    ...(user?.role === 'ADMIN' ? [{ to: '/admin/users', label: 'Users', match: ['/admin'] }] : []),
  ];

  return (
    <header className="sticky top-0 z-40 border-b border-line bg-pitch text-white">
      <div className="mx-auto flex h-14 max-w-[1280px] items-center gap-2 px-4 sm:gap-6 sm:px-6">
        <Link to="/dashboard" aria-label="Resume Screener, all sessions" className="flex shrink-0 items-center gap-2 rounded-md">
          <span className="flex size-7 items-center justify-center rounded bg-mark text-mark-ink" aria-hidden="true">
            <ListOrdered className="size-4" />
          </span>
          <span className="hidden whitespace-nowrap font-display text-[17px] font-bold tracking-tight sm:inline">Resume Screener</span>
        </Link>

        <nav aria-label="Main" className="flex min-w-0 items-center self-stretch overflow-x-auto [scrollbar-width:none] sm:gap-1">
          {links.map((l) => (
            <NavLink
              key={l.to}
              to={l.to}
              className={({ isActive }) => {
                const active = isActive || l.match.some((m) => pathname.startsWith(m));
                return cn(
                  'relative flex h-14 shrink-0 items-center px-2.5 text-[15px] font-semibold transition-colors after:absolute after:inset-x-2.5 after:bottom-0 after:h-[3px] after:rounded-t-sm after:transition-colors sm:px-3',
                  active ? 'text-white after:bg-mark' : 'text-pitch-ink after:bg-transparent hover:text-white'
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
              <p className="text-sm font-semibold">{user.fullName}</p>
              <p className="text-xs text-pitch-ink">{user.role === 'ADMIN' ? 'Admin' : 'Recruiter'}</p>
            </div>
          )}
          <button
            type="button"
            onClick={toggleTheme}
            aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
            className="flex size-9 items-center justify-center rounded-md text-pitch-ink transition-colors hover:bg-pitch-raised hover:text-white"
          >
            {theme === 'dark' ? <Sun className="size-4" aria-hidden="true" /> : <Moon className="size-4" aria-hidden="true" />}
          </button>
          <button
            type="button"
            onClick={handleLogout}
            className="flex h-9 min-w-9 items-center justify-center gap-2 rounded-md px-2 text-sm sm:px-3 font-semibold text-pitch-ink transition-colors hover:bg-pitch-raised hover:text-white"
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
