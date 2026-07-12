import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { sessionsApi } from '@/api/client';
import type { ScreeningSession } from '@/types';
import { SessionCard } from '@/components/SessionCard';
import { Button } from '@/components/ui/Button';
import { MainLayout } from '@/layouts/MainLayout';
import { Plus, Search, FileStack } from 'lucide-react';

type FilterStatus = 'ALL' | 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';

export default function Dashboard() {
  const [sessions, setSessions] = useState<ScreeningSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<FilterStatus>('ALL');

  useEffect(() => {
    loadSessions();
  }, []);

  const loadSessions = async () => {
    try {
      const res = await sessionsApi.list();
      setSessions(res.data.content || []);
    } catch (err) {
      console.error('Failed to load sessions:', err);
    } finally {
      setLoading(false);
    }
  };

  const filteredSessions = sessions.filter((s) => {
    const matchesSearch = s.title
      .toLowerCase()
      .includes(search.toLowerCase());
    const matchesFilter = filter === 'ALL' || s.status === filter;
    return matchesSearch && matchesFilter;
  });

  const filters: { label: string; value: FilterStatus }[] = [
    { label: 'All', value: 'ALL' },
    { label: 'Pending', value: 'PENDING' },
    { label: 'Running', value: 'RUNNING' },
    { label: 'Done', value: 'DONE' },
    { label: 'Failed', value: 'FAILED' },
  ];

  return (
    <MainLayout maxWidth="max-w-[1400px]">
      <section className="space-y-8">
        <div className="flex flex-col md:flex-row md:items-end justify-between gap-6">
          <div className="space-y-2">
            <h2 className="text-6xl font-black tracking-tighter text-on-surface leading-none">
              Screening Sessions
            </h2>
            <p className="text-on-surface-variant text-xl">
              Manage and monitor your candidate evaluation pipelines.
            </p>
          </div>
          <Link to="/sessions/new">
            <Button>
              <Plus className="w-5 h-5" />
              New Session
            </Button>
          </Link>
        </div>

        <div className="flex flex-col md:flex-row gap-6 items-center justify-between">
          <div className="w-full max-w-md relative group">
            <div className="absolute left-4 top-1/2 -translate-y-1/2 text-on-surface-variant group-focus-within:text-primary">
              <Search className="w-5 h-5" />
            </div>
            <input
              className="w-full h-14 pl-12 pr-4 bg-surface-container-lowest border border-surface-container rounded-2xl focus:ring-4 focus:ring-primary/10 focus:border-primary/20 transition-all placeholder:text-on-surface-variant/60"
              placeholder="Search sessions..."
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>

          <nav className="flex p-1.5 bg-surface-container-low/50 rounded-2xl overflow-x-auto border border-surface-container-low">
            {filters.map((f) => (
              <button
                key={f.value}
                onClick={() => setFilter(f.value)}
                className={`px-8 py-2.5 rounded-xl text-sm font-semibold transition-colors ${
                  filter === f.value
                    ? 'bg-white shadow-sm text-primary'
                    : 'text-on-surface-variant hover:text-on-surface'
                }`}
              >
                {f.label}
              </button>
            ))}
          </nav>
        </div>

        {loading ? (
          <div className="flex items-center justify-center py-24">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary" />
          </div>
        ) : filteredSessions.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
            {filteredSessions.map((session) => (
              <SessionCard key={session.id} session={session} onDelete={loadSessions} />
            ))}
          </div>
        ) : (
          <section className="py-24 border-t border-surface-container">
            <div className="flex flex-col items-center justify-center text-center space-y-8 max-w-lg mx-auto">
              <div className="relative">
                <div className="size-40 bg-surface-container-low rounded-full flex items-center justify-center text-on-surface-variant/20 shadow-inner">
                  <FileStack className="!text-7xl" />
                </div>
                <div className="absolute bottom-2 right-2 size-14 bg-white rounded-full shadow-xl flex items-center justify-center text-primary">
                  <div className="size-10 bg-primary/10 rounded-full flex items-center justify-center">
                    <Plus className="w-5 h-5" />
                  </div>
                </div>
              </div>
              <div className="space-y-3">
                <h4 className="text-3xl font-black tracking-tight">
                  No archived sessions
                </h4>
                <p className="text-on-surface-variant text-lg font-medium">
                  Everything is up to date. Create your first screening to
                  start evaluating candidates with AI precision.
                </p>
              </div>
              <Link to="/sessions/new">
                <Button>New Session</Button>
              </Link>
            </div>
          </section>
        )}
      </section>

      <footer className="mt-24 pt-12 border-t border-surface-container flex flex-col md:flex-row justify-between items-center gap-8">
        <div className="flex items-center gap-3 text-on-surface-variant text-sm font-bold uppercase tracking-wider">
          <div className="size-2 bg-green-500 rounded-full animate-pulse" />
          <span>System Status: All systems operational</span>
        </div>
        <div className="flex gap-10">
          <span className="text-[11px] font-black uppercase tracking-[0.2em] text-on-surface-variant">
            Privacy
          </span>
          <span className="text-[11px] font-black uppercase tracking-[0.2em] text-on-surface-variant">
            Terms
          </span>
          <span className="text-[11px] font-black uppercase tracking-[0.2em] text-on-surface-variant">
            Support
          </span>
        </div>
      </footer>
    </MainLayout>
  );
}