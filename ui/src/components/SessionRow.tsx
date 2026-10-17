import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Trash2, X } from 'lucide-react';
import { apiErrorMessage, sessionsApi } from '@/api/client';
import type { ProgressResponse, ScreeningSession } from '@/types';
import { StatusBadge } from '@/components/ui/Badge';
import { ConfirmButton } from '@/components/ui/Feedback';
import { formatRelativeDate, formatDate } from '@/lib/utils';
import { stagger } from '@/lib/motion';

/** One session in the dashboard list. Polls its own progress while scoring and asks the list to reload when it ends. */
export function SessionRow({ session, index = 0, onChanged }: { session: ScreeningSession; index?: number; onChanged: () => void }) {
  const [progress, setProgress] = useState<ProgressResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (session.status !== 'RUNNING') return;
    let active = true;
    const poll = async () => {
      try {
        const res = await sessionsApi.progress(session.id);
        if (!active) return;
        setProgress(res.data);
        if (res.data.status !== 'RUNNING') onChanged();
      } catch {
        // a missed poll is retried on the next tick
      }
    };
    poll();
    const timer = setInterval(poll, 3000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [session.id, session.status, onChanged]);

  const run = async (action: () => Promise<unknown>, fallback: string) => {
    setError(null);
    setBusy(true);
    try {
      await action();
      onChanged();
    } catch (err) {
      setError(apiErrorMessage(err, fallback));
    } finally {
      setBusy(false);
    }
  };

  const href =
    session.status === 'DONE' || session.status === 'RUNNING'
      ? `/sessions/${session.id}`
      : `/sessions/new/${session.id}`;

  return (
    <li style={stagger(index, 40)} className="group relative grid animate-fade-up grid-cols-[1fr_auto] items-center gap-x-4 gap-y-2 border-b border-line px-4 py-3 last:border-b-0 transition-colors hover:bg-ground sm:px-5 md:grid-cols-[minmax(0,1fr)_120px_90px_110px_minmax(150px,200px)_88px]">
      <div className="min-w-0">
        <Link
          to={href}
          className="block truncate font-semibold text-ink after:absolute after:inset-0 after:content-[''] focus-visible:outline-none group-focus-within:underline"
        >
          {session.title}
        </Link>
        {session.description && <p className="truncate text-[13px] text-ink-muted">{session.description}</p>}
      </div>

      {/* On phones these three sit on one meta line; from md up each becomes its own grid column. */}
      <div className="col-span-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm md:contents">
        <div>
          <StatusBadge status={session.status} />
        </div>
        <p className="num text-ink-muted md:text-ink">
          {session.candidateCount} <span className="md:hidden">resumes</span>
        </p>
        <p className="text-ink-muted" title={formatDate(session.createdAt)}>
          {formatRelativeDate(session.createdAt)}
        </p>
      </div>

      <div className="col-span-2 min-w-0 text-[13px] md:col-span-1">
        {session.status === 'RUNNING' && (
          <div className="space-y-1">
            <div
              className="h-1.5 overflow-hidden rounded-full bg-line"
              role="progressbar"
              aria-label={`Scoring progress for ${session.title}`}
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={progress?.percent ?? 0}
            >
              <div className="stripes h-full animate-stripes rounded-full bg-info transition-[width] duration-700 ease-out" style={{ width: `${Math.max(4, progress?.percent ?? 0)}%` }} />
            </div>
            <p className="num text-ink-muted">
              {progress ? `${progress.processed + progress.failed} of ${progress.total} read` : 'Starting…'}
              {progress && progress.failed > 0 && <span className="text-danger"> · {progress.failed} failed</span>}
            </p>
          </div>
        )}
        {session.status === 'FAILED' && (
          <p className="truncate text-danger" title={session.lastErrorMessage}>
            {session.lastErrorMessage || 'Scoring stopped'}
          </p>
        )}
        {session.status === 'PENDING' && <p className="text-ink-muted">{session.jdFileKey ? 'Ready to launch' : 'Needs a job description'}</p>}
        {session.status === 'DONE' && (
          <p className={session.shortlistedCount ? 'font-semibold text-mark-text' : 'text-ink-muted'}>
            {session.shortlistedCount ? `${session.shortlistedCount} shortlisted` : 'Ranked, nobody shortlisted yet'}
          </p>
        )}
        {error && (
          <p role="alert" className="text-danger">
            {error}
          </p>
        )}
      </div>

      {/* Actions sit above the row's stretched link */}
      <div className="relative z-10 col-start-2 row-start-1 flex items-center justify-end gap-1 md:col-start-auto md:row-start-auto">
        {session.status === 'RUNNING' && (
          <ConfirmButton
            compact
            label="Stop scoring"
            confirmLabel="Stop"
            icon={<X />}
            disabled={busy}
            onConfirm={() => run(() => sessionsApi.cancel(session.id), 'Could not stop scoring')}
          />
        )}
        <ConfirmButton
          compact
          label={`Delete ${session.title}`}
          confirmLabel="Delete"
          icon={<Trash2 />}
          disabled={busy}
          onConfirm={() => run(() => sessionsApi.delete(session.id), 'Could not delete the session')}
        />
      </div>
    </li>
  );
}
