import { NavLink } from 'react-router-dom';
import { cn } from '@/lib/utils';

const TABS = [
  { to: '/admin/users', label: 'Users' },
  { to: '/admin/roles', label: 'Roles and permissions' },
];

/** The admin area's two screens. Both need admin-only permissions, so every admin sees both tabs. */
export function AdminTabs() {
  return (
    <nav aria-label="Admin" className="-mt-2 mb-6 flex gap-1 overflow-x-auto border-b border-line [scrollbar-width:none]">
      {TABS.map((t) => (
        <NavLink
          key={t.to}
          to={t.to}
          className={({ isActive }) =>
            cn(
              'relative flex h-11 shrink-0 items-center px-3 text-[15px] font-semibold transition-colors after:absolute after:inset-x-3 after:bottom-[-1px] after:h-[3px] after:rounded-t-sm',
              isActive ? 'text-ink after:bg-mark' : 'text-ink-muted after:bg-transparent hover:text-ink'
            )
          }
        >
          {t.label}
        </NavLink>
      ))}
    </nav>
  );
}
