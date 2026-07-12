import { Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { getInitials } from '@/lib/utils';
import { Button } from '@/components/ui/Button';
import { Briefcase, LogOut } from 'lucide-react';

export function Navbar() {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <header className="sticky top-0 z-50 glass px-8 py-4 border-b border-surface-container">
      <div className="max-w-[1400px] mx-auto flex items-center justify-between">
        <Link to="/dashboard" className="flex items-center gap-3">
          <div className="bg-gradient-to-br from-primary to-secondary p-2 rounded-xl text-white">
            <Briefcase className="w-5 h-5" />
          </div>
          <h1 className="text-xl font-extrabold tracking-tight text-on-surface">
            Resume Screener
          </h1>
        </Link>

        <div className="flex items-center gap-6">
          {user && (
            <div className="hidden md:flex flex-col items-end">
              <p className="text-sm font-bold text-on-surface">{user.fullName}</p>
              <p className="text-xs text-on-surface-variant">{user.email}</p>
            </div>
          )}

          <div className="flex items-center gap-4">
            {user && (
              <div className="w-10 h-10 rounded-full bg-primary-container flex items-center justify-center text-primary font-bold text-sm">
                {getInitials(user.fullName)}
              </div>
            )}
            <Button variant="ghost" size="icon" onClick={handleLogout}>
              <LogOut className="w-5 h-5" />
            </Button>
          </div>
        </div>
      </div>
    </header>
  );
}