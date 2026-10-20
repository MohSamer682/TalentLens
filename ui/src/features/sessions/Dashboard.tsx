import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus, Search } from 'lucide-react';
import { apiErrorMessage, sessionsApi } from '@/api/client';
import type { ScreeningSession } from '@/types';
import { SessionRow } from '@/features/sessions/SessionRow';
import { Button, buttonVariants } from '@/components/ui/Button';
import { Alert, SkeletonRows } from '@/components/ui/Feedback';
import { Segmented } from '@/components/ui/Segmented';
import { Panel } from '@/components/ui/Card';
import { MainLayout, PageHeader } from '@/layouts/MainLayout';
import { cn } from '@/lib/utils';
import { canSwitchScope, defaultScope, useCan } from '@/lib/permissions';
import { useAuthStore } from '@/features/auth/authStore';

type Filter = 'ALL' | ScreeningSession['status'];

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: 'All' },
  { value: 'DONE', label: 'Ranked' },
  { value: 'RUNNING', label: 'Scoring' },
  { value: 'PENDING', label: 'Not started' },
  { value: 'FAILED', label: 'Failed' },
];

export default function Dashboard() {
  const user = useAuthStore((s) => s.user);
  const [sessions, setSessions] = useState<ScreeningSession[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<Filter>('ALL');
  const canRun = useCan('SESSION_RUN');
  const canSwitch = canSwitchScope(user);
  const isViewerOnly = defaultScope(user) === 'EVERYONE';
  const [everyone, setEveryone] = useState(() => isViewerOnly);

  const loadSessions = useCallback(async () => {
    setLoadError(null);
    try {
      // shortcut: search/filter run client-side over the first 500 sessions; move them server-side past that.
      const res = await sessionsApi.list(0, 500, everyone);
      setSessions(res.data.content || []);
    } catch (err) {
      setLoadError(apiErrorMessage(err, 'Could not load sessions'));
    } finally {
      setLoading(false);
    }
  }, [everyone]);

  useEffect(() => {
    loadSessions();
  }, [loadSessions]);

  const counts = useMemo(() => {
    const c: Record<Filter, number> = { ALL: sessions.length, PENDING: 0, RUNNING: 0, DONE: 0, FAILED: 0 };
    sessions.forEach((s) => c[s.status]++);
    return c;
  }, [sessions]);

  const query = search.trim().toLowerCase();
  const visible = sessions.filter(
    (s) =>
      (filter === 'ALL' || s.status === filter) &&
      (s.title.toLowerCase().includes(query) || (s.ownerName && s.ownerName.toLowerCase().includes(query)))
  );

  return (
    <MainLayout>
      <PageHeader
        title={isViewerOnly ? 'All sessions' : 'Screening sessions'}
        meta={
          !loading &&
          !loadError && (
            <span>
              {isViewerOnly
                ? "Every recruiter's sessions. You can open them; recruiters run them."
                : 'One session per opening: a job description, its resumes, and a ranked shortlist.'}
            </span>
          )
        }
        actions={
          <>
            {canSwitch && (
              <Segmented
                label="Whose sessions"
                value={everyone ? 'EVERYONE' : 'MINE'}
                onChange={(v) => {
                  setLoading(true);
                  setEveryone(v === 'EVERYONE');
                }}
                options={[
                  { value: 'MINE', label: 'Mine' },
                  { value: 'EVERYONE', label: 'All recruiters' },
                ]}
              />
            )}
            {canRun && (
              <Link to="/sessions/new" className={buttonVariants()}>
                <Plus />
                New session
              </Link>
            )}
          </>
        }
      />

      {loading ? (
        <Panel>
          <div className="flex items-center justify-between gap-3 border-b border-line p-3 sm:px-5">
            <span className="skeleton h-9 w-80 rounded-full" />
            <span className="skeleton hidden h-9 w-72 md:block" />
          </div>
          <SkeletonRows rows={5} columns={[32, 10, 8, 10, 16]} />
        </Panel>
      ) : loadError ? (
        <Alert
          title="Sessions could not be loaded"
          action={
            <Button variant="secondary" size="sm" onClick={() => { setLoading(true); loadSessions(); }}>
              Try again
            </Button>
          }
        >
          {loadError}
        </Alert>
      ) : sessions.length === 0 ? (
        <Panel className="px-6 py-14 text-center">
          <h2 className="text-lg font-semibold">{everyone ? 'No recruiter has a session yet' : 'No sessions yet'}</h2>
          <p className="mx-auto mt-1 max-w-md text-sm text-ink-muted">
            {canRun
              ? 'Create a session for an opening, upload the job description and the resumes, set what this role values, and the candidates come back ranked.'
              : 'Your role can review sessions but not create them. Ask an admin if you need to run screenings yourself.'}
          </p>
          {canRun && (
            <Link to="/sessions/new" className={cn(buttonVariants(), 'mt-5')}>
              <Plus />
              New session
            </Link>
          )}
        </Panel>
      ) : (
        <Panel>
          <div className="flex flex-col gap-3 border-b border-line p-3 sm:px-5 md:flex-row md:items-center md:justify-between">
            <div className="-mx-1 overflow-x-auto px-1 py-0.5">
              <Segmented
                label="Filter by status"
                value={filter}
                onChange={setFilter}
                options={FILTERS.map((f) => ({ ...f, count: counts[f.value] }))}
              />
            </div>
            <div className="relative md:w-72">
              <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-muted" aria-hidden="true" />
              <input
                type="search"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder={!canSwitch ? 'Search by role or recruiter' : 'Search by title'}
                aria-label={!canSwitch ? 'Search by role or recruiter' : 'Search sessions by title'}
                className="h-9 w-full rounded-md border border-line bg-surface dark:bg-sunken pl-9 pr-3 text-sm placeholder:text-ink-muted focus:border-accent-text focus:outline-none focus:ring-2 focus:ring-accent-text/25 transition-shadow"
              />
            </div>
          </div>

          <div
            aria-hidden="true"
            className="hidden grid-cols-[minmax(0,1fr)_120px_90px_110px_minmax(150px,200px)_88px] gap-x-4 border-b border-line bg-sunken px-5 py-2 text-[13px] font-medium text-ink-muted md:grid"
          >
            <span>Session</span>
            <span>Status</span>
            <span>Resumes</span>
            <span>Created</span>
            <span>Progress</span>
            <span />
          </div>

          {visible.length > 0 ? (
            <ul>
              {visible.map((s, i) => (
                <SessionRow key={s.id} session={s} index={i} onChanged={loadSessions} />
              ))}
            </ul>
          ) : (
            <div className="px-6 py-12 text-center text-sm">
              <p className="font-semibold">No sessions match</p>
              <p className="mt-1 text-ink-muted">
                {query ? `Nothing titled “${search.trim()}”` : 'No sessions in this status'}
                {filter !== 'ALL' && ' under this filter'}.{' '}
                <button
                  type="button"
                  className="font-semibold text-accent-text underline underline-offset-2"
                  onClick={() => {
                    setSearch('');
                    setFilter('ALL');
                  }}
                >
                  Clear filters
                </button>
              </p>
            </div>
          )}
        </Panel>
      )}
    </MainLayout>
  );
}
