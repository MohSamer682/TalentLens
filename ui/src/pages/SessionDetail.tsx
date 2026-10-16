import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Download, Minus, Pencil, Plus, RotateCcw, Trash2, X } from 'lucide-react';
import { apiErrorMessage, sessionsApi } from '@/api/client';
import type { CandidateResultDto, ProgressResponse, ScreeningSession } from '@/types';
import { Button, buttonVariants } from '@/components/ui/Button';
import { StatusBadge } from '@/components/ui/Badge';
import { Panel } from '@/components/ui/Card';
import { Alert, ConfirmButton, SkeletonRows } from '@/components/ui/Feedback';
import { useCountUp } from '@/lib/motion';
import { Pagination } from '@/components/ui/Pagination';
import { StandingsTable } from '@/components/StandingsTable';
import { MainLayout, PageHeader } from '@/layouts/MainLayout';
import { DIMENSIONS, formatDate, readShortlistSize, writeShortlistSize } from '@/lib/utils';

const PAGE_SIZE = 20;

export default function SessionDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [session, setSession] = useState<ScreeningSession | null>(null);
  const [progress, setProgress] = useState<ProgressResponse | null>(null);
  const [candidates, setCandidates] = useState<CandidateResultDto[]>([]);
  const [page, setPage] = useState(0);
  const [totalCandidates, setTotalCandidates] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [shortlist, setShortlist] = useState(5);

  const loadData = useCallback(async () => {
    if (!id) return;
    setLoadError(null);
    try {
      const [sessionRes, candidatesRes] = await Promise.all([
        sessionsApi.get(id),
        sessionsApi.candidates(id, page, PAGE_SIZE),
      ]);
      setSession(sessionRes.data);
      setCandidates(candidatesRes.data.content || []);
      setTotalCandidates(candidatesRes.data.totalElements ?? 0);
      setTotalPages(candidatesRes.data.totalPages ?? 0);
    } catch (err) {
      setLoadError(apiErrorMessage(err, 'Could not load this session'));
    } finally {
      setLoading(false);
    }
  }, [id, page]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    if (id) setShortlist(readShortlistSize(id, 5));
  }, [id]);

  // While scoring, poll progress; once finished (or for a finished session) fetch it once for the failed count.
  useEffect(() => {
    if (!id || !session || session.status === 'PENDING') return;
    let active = true;
    const poll = async () => {
      try {
        const res = await sessionsApi.progress(id);
        if (!active) return;
        setProgress(res.data);
        if (session.status === 'RUNNING' && res.data.status !== 'RUNNING') loadData();
      } catch {
        // progress is supplementary; the next tick retries
      }
    };
    poll();
    if (session.status !== 'RUNNING') return () => { active = false; };
    const timer = setInterval(poll, 3000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [id, session?.status, loadData]); // eslint-disable-line react-hooks/exhaustive-deps

  const act = async (action: () => Promise<void>, fallback: string) => {
    setActionError(null);
    setBusy(true);
    try {
      await action();
    } catch (err) {
      setActionError(apiErrorMessage(err, fallback));
    } finally {
      setBusy(false);
    }
  };

  const handleExport = () =>
    act(async () => {
      const res = await sessionsApi.export(id!);
      window.open(res.data.url, '_blank', 'noopener');
    }, 'Could not create the download link');

  const updateShortlist = (n: number) => {
    const next = Math.max(0, Math.min(totalCandidates, n));
    setShortlist(next);
    if (id) writeShortlistSize(id, next);
  };

  if (loading) {
    return (
      <MainLayout>
        <div className="mb-6 space-y-3" aria-hidden="true">
          <span className="skeleton block h-3 w-24" />
          <span className="skeleton block h-8 w-80 max-w-full" />
          <span className="skeleton block h-4 w-96 max-w-full" />
        </div>
        <Panel>
          <SkeletonRows rows={8} columns={[30, 8, 8, 8, 8, 10]} />
        </Panel>
      </MainLayout>
    );
  }

  if (!session) {
    return (
      <MainLayout>
        <BackLink />
        <Alert
          title="This session could not be opened"
          action={
            loadError && (
              <Button variant="secondary" size="sm" onClick={() => { setLoading(true); loadData(); }}>
                Try again
              </Button>
            )
          }
        >
          {loadError || 'It may have been deleted.'}
        </Alert>
      </MainLayout>
    );
  }

  const failed = progress?.failed ?? 0;
  const remaining = progress ? Math.max(0, progress.total - progress.processed - progress.failed) : 0;

  return (
    <MainLayout>
      <BackLink />

      <PageHeader
        title={session.title}
        meta={
          <>
            <StatusBadge status={session.status} />
            <span>Created {formatDate(session.createdAt)}</span>
            {session.weights && (
              <span className="num">
                Weights:{' '}
                {DIMENSIONS.map((d) => `${d.short} ${session.weights[d.key]}`).join(' · ')}
              </span>
            )}
          </>
        }
        actions={
          <>
            {(session.status === 'PENDING' || session.status === 'FAILED') && (
              <Link to={`/sessions/new/${session.id}?step=1`} className={buttonVariants({ variant: 'secondary' })}>
                <Pencil />
                Edit session
              </Link>
            )}
            {session.status === 'RUNNING' && (
              <ConfirmButton
                label="Stop scoring"
                confirmLabel="Stop scoring now"
                icon={<X />}
                disabled={busy}
                onConfirm={() => act(async () => { await sessionsApi.cancel(session.id); await loadData(); }, 'Could not stop scoring')}
              />
            )}
            <ConfirmButton
              label="Delete"
              confirmLabel="Delete session"
              icon={<Trash2 />}
              disabled={busy}
              onConfirm={() => act(async () => { await sessionsApi.delete(session.id); navigate('/dashboard'); }, 'Could not delete the session')}
            />
            {session.status === 'DONE' && (
              <Button onClick={handleExport} disabled={busy || !session.reportFileKey} title={session.reportFileKey ? undefined : 'The report is still being generated'}>
                <Download />
                Download Excel report
              </Button>
            )}
          </>
        }
      />

      {actionError && <Alert className="mb-4">{actionError}</Alert>}

      {session.status === 'PENDING' && (
        <Alert tone="info" title="Not started yet" action={<Link to={`/sessions/new/${session.id}`} className={buttonVariants({ size: 'sm' })}>Finish setup</Link>}>
          Upload the job description and resumes, then launch scoring.
        </Alert>
      )}

      {session.status === 'RUNNING' && (
        <Panel className="animate-fade-up p-4 sm:p-5" aria-live="polite">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="flex items-center gap-2 font-semibold">
              <span className="relative flex size-2" aria-hidden="true">
                <span className="absolute inset-0 animate-ping rounded-full bg-info" />
                <span className="relative size-2 rounded-full bg-info" />
              </span>
              Scoring resumes
            </h2>
            <p className="text-[13px] text-ink-muted">Updates every 3 seconds. You can leave this page; scoring continues.</p>
          </div>
          <div
            className="mt-3 h-2 overflow-hidden rounded-full bg-line"
            role="progressbar"
            aria-label="Scoring progress"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={progress?.percent ?? 0}
          >
            <div className="stripes h-full animate-stripes rounded-full bg-info transition-[width] duration-700 ease-out" style={{ width: `${Math.max(2, progress?.percent ?? 0)}%` }} />
          </div>
          <dl className="num mt-3 flex flex-wrap gap-x-8 gap-y-1 text-sm">
            <Stat label="Scored" value={progress?.processed ?? 0} />
            <Stat label="Remaining" value={remaining} />
            <Stat label="Could not be read" value={failed} danger={failed > 0} />
            <Stat label="Total" value={progress?.total ?? session.candidateCount} />
          </dl>
        </Panel>
      )}

      {session.status === 'FAILED' && (
        <Alert
          title="Scoring stopped"
          action={
            <div className="flex gap-2">
              <Button
                variant="secondary"
                size="sm"
                disabled={busy}
                onClick={() => act(async () => { await sessionsApi.start(session.id); await loadData(); }, 'Could not restart scoring')}
              >
                <RotateCcw />
                Retry
              </Button>
            </div>
          }
        >
          {session.lastErrorMessage || 'The batch stopped before every resume was scored.'}
        </Alert>
      )}

      {session.status === 'DONE' && (
        <Panel className="animate-fade-up">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-4 py-3 sm:px-5">
            <dl className="num flex flex-wrap gap-x-8 gap-y-1 text-sm">
              <Stat label="Ranked" value={totalCandidates} />
              {failed > 0 && <Stat label="Could not be read" value={failed} danger />}
              {page === 0 && candidates[0] && <Stat label="Top score" value={candidates[0].compositeScore} decimals={1} />}
            </dl>
            {totalCandidates > 0 && (
              <div className="flex items-center gap-2">
                <label htmlFor="shortlist-size" className="text-sm font-semibold">
                  Shortlist line
                </label>
                <div className="flex items-center rounded-md border border-line-strong">
                  <button
                    type="button"
                    onClick={() => updateShortlist(shortlist - 1)}
                    disabled={shortlist <= 0}
                    aria-label="Move shortlist line up"
                    className="flex size-8 items-center justify-center text-ink-muted hover:text-ink disabled:opacity-40"
                  >
                    <Minus className="size-4" />
                  </button>
                  <input
                    id="shortlist-size"
                    type="number"
                    min={0}
                    max={totalCandidates}
                    value={shortlist}
                    onChange={(e) => updateShortlist(Number(e.target.value) || 0)}
                    className="num h-8 w-12 border-x border-line bg-transparent text-center text-sm font-semibold focus:outline-none [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none"
                  />
                  <button
                    type="button"
                    onClick={() => updateShortlist(shortlist + 1)}
                    disabled={shortlist >= totalCandidates}
                    aria-label="Move shortlist line down"
                    className="flex size-8 items-center justify-center text-ink-muted hover:text-ink disabled:opacity-40"
                  >
                    <Plus className="size-4" />
                  </button>
                </div>
              </div>
            )}
          </div>

          {candidates.length > 0 ? (
            <>
              <StandingsTable sessionId={session.id} candidates={candidates} weights={session.weights} shortlistSize={shortlist} />
              <div className="flex flex-col items-center justify-between gap-3 px-4 py-3 text-sm text-ink-muted sm:flex-row sm:px-5">
                <span className="num">
                  {page * PAGE_SIZE + 1}–{page * PAGE_SIZE + candidates.length} of {totalCandidates}
                </span>
                <Pagination page={page} totalPages={totalPages} onPage={setPage} />
              </div>
            </>
          ) : (
            <p className="px-5 py-12 text-center text-sm text-ink-muted">
              No resume could be scored in this session. Check that the files are text-based PDFs, then edit the session and re-upload.
            </p>
          )}
        </Panel>
      )}
    </MainLayout>
  );
}

function BackLink() {
  return (
    <Link to="/dashboard" className="mb-3 inline-block text-sm font-semibold text-ink-muted hover:text-ink">
      ← All sessions
    </Link>
  );
}

function Stat({ label, value, decimals = 0, danger }: { label: string; value: number; decimals?: number; danger?: boolean }) {
  const shown = useCountUp(value);
  return (
    <div className="flex items-baseline gap-1.5">
      <dt className="text-ink-muted">{label}</dt>
      <dd className={danger ? 'font-bold text-danger' : 'font-bold text-ink'}>{shown.toFixed(decimals)}</dd>
    </div>
  );
}
