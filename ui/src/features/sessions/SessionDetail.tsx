import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Download, FilePlus2, Pencil, Play, RefreshCw, RotateCcw, Trash2, X } from 'lucide-react';
import { apiErrorMessage, candidatesApi, sessionsApi } from '@/api/client';
import type { CandidateResultDto, LlmState, LlmStatus, ProgressResponse, ReviewStatus, ScreeningSession } from '@/types';
import { Button, buttonVariants } from '@/components/ui/Button';
import { StatusBadge } from '@/components/ui/Badge';
import { Panel } from '@/components/ui/Card';
import { Alert, ConfirmButton, SkeletonRows } from '@/components/ui/Feedback';
import { useCountUp } from '@/lib/motion';
import { Pagination } from '@/components/ui/Pagination';
import { StandingsTable } from '@/features/sessions/StandingsTable';
import { ResumeList } from '@/features/sessions/ResumeList';
import { MainLayout, PageHeader } from '@/layouts/MainLayout';
import { JobSummary } from '@/features/sessions/JobSummary';
import { DIMENSIONS, formatDate } from '@/lib/utils';
import { toggle, toggleAll } from '@/features/candidates/selection';
import { Segmented } from '@/components/ui/Segmented';
import { ShortlistBar } from '@/features/candidates/ShortlistBar';
import { useAuthStore } from '@/features/auth/authStore';
import { canChangeSession, canReviewSession } from '@/lib/permissions';

const PAGE_SIZE = 20;

type View = 'ALL' | 'SHORTLISTED' | 'FAILED' | 'PENDING';

export default function SessionDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const me = useAuthStore((s) => s.user);
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
  const [view, setView] = useState<View>('ALL');
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [topN, setTopN] = useState(5);
  const [notice, setNotice] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    if (!id) return;
    setLoadError(null);
    try {
      const [sessionRes, candidatesRes] = await Promise.all([
        sessionsApi.get(id),
        sessionsApi.candidates(id, page, PAGE_SIZE, view === 'SHORTLISTED' ? 'SHORTLISTED' : undefined),
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
  }, [id, page, view]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    if (selected.size === 0) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setSelected(new Set());
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [selected.size]);

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
    // Keyed on the session object so every reload (after scoring, adding or removing resumes) refreshes counts
  }, [id, session, loadData]); // eslint-disable-line react-hooks/exhaustive-deps

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

  const reviewSelected = (status: ReviewStatus) =>
    act(async () => {
      const { data } = await candidatesApi.reviewMany(id!, [...selected], status);
      setSelected(new Set());
      setNotice(`${data.changed} ${status === 'SHORTLISTED' ? 'added to' : 'removed from'} the shortlist. ${data.shortlistedTotal} shortlisted in total.`);
      await loadData();
    }, 'Could not save the shortlist');

  const shortlistTop = () =>
    act(async () => {
      const { data } = await candidatesApi.shortlistTop(id!, topN);
      setNotice(`${data.changed} added to the shortlist. ${data.shortlistedTotal} shortlisted in total.`);
      await loadData();
    }, 'Could not save the shortlist');

  const changeView = (next: View) => {
    setView(next);
    setPage(0);
    setSelected(new Set());
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
  const unscored = progress ? Math.max(0, progress.total - progress.processed) : 0;
  const finished = session.status === 'DONE' || session.status === 'FAILED';
  // Hidden rather than disabled: someone viewing another recruiter's session only reads it.
  const canChange = canChangeSession(me, session);
  const canReview = canReviewSession(me, session);
  const score = (scope: 'UNSCORED' | 'ALL') =>
    act(async () => { await sessionsApi.start(session.id, scope); await loadData(); }, 'Could not start scoring');

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
            {session.llm && <LlmStatusLine llm={session.llm} />}
          </>
        }
        actions={
          <>
            {canChange && (session.status === 'PENDING' || session.status === 'FAILED') && (
              <Link to={`/sessions/new/${session.id}?step=1`} className={buttonVariants({ variant: 'secondary' })}>
                <Pencil />
                Edit session
              </Link>
            )}
            {canChange && session.status === 'RUNNING' && (
              <ConfirmButton
                label="Stop scoring"
                confirmLabel="Stop scoring now"
                icon={<X />}
                disabled={busy}
                onConfirm={() => act(async () => { await sessionsApi.cancel(session.id); await loadData(); }, 'Could not stop scoring')}
              />
            )}
            {canChange && finished && (
              <Link to={`/sessions/new/${session.id}?step=2`} className={buttonVariants({ variant: 'secondary' })}>
                <FilePlus2 />
                Add resumes
              </Link>
            )}
            {canChange && finished && (progress?.total ?? 0) > 0 && (
              <ConfirmButton
                label="Re-score all"
                confirmLabel={`Re-score all ${progress?.total} resumes`}
                icon={<RefreshCw />}
                disabled={busy}
                onConfirm={() => score('ALL')}
              />
            )}
            {canChange && (
              <ConfirmButton
                label="Delete"
                confirmLabel="Delete session"
                icon={<Trash2 />}
                disabled={busy}
                onConfirm={() => act(async () => { await sessionsApi.delete(session.id); navigate('/dashboard'); }, 'Could not delete the session')}
              />
            )}
            {canChange && session.status === 'DONE' && unscored > 0 && (
              <Button onClick={() => score('UNSCORED')} disabled={busy}>
                <Play />
                Score {unscored} unscored resume{unscored === 1 ? '' : 's'}
              </Button>
            )}
            {session.status === 'DONE' && (
              <Button variant={unscored > 0 ? 'secondary' : 'primary'} onClick={handleExport} disabled={busy || !session.reportFileKey} title={session.reportFileKey ? undefined : 'The report is still being generated'}>
                <Download />
                Download Excel report
              </Button>
            )}
          </>
        }
      />

      {session.job && <JobSummary job={session.job} />}

      {actionError && <Alert className="mb-4">{actionError}</Alert>}
      {notice && (
        <Alert tone="success" className="mb-4" action={<Button variant="ghost" size="sm" onClick={() => setNotice(null)}>Dismiss</Button>}>
          {notice}
        </Alert>
      )}

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
            canChange && (
              <div className="flex gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={busy}
                  onClick={() => score('UNSCORED')}
                >
                  <RotateCcw />
                  Retry
                </Button>
              </div>
            )
          }
        >
          {session.lastErrorMessage || 'The batch stopped before every resume was scored.'}
        </Alert>
      )}

      {session.status === 'DONE' && (
        <Panel className="animate-fade-up">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-4 py-3 sm:px-5">
            <div className="-mx-1 max-w-full overflow-x-auto px-1 py-0.5">
              <Segmented<View>
                label="Show"
                value={view}
                onChange={changeView}
                options={[
                  { value: 'ALL', label: 'All', count: (progress?.processed ?? 0) || undefined },
                  { value: 'SHORTLISTED', label: 'Shortlisted', count: session.shortlistedCount ?? 0 },
                  ...(failed > 0 ? [{ value: 'FAILED' as View, label: 'Could not be read', count: failed }] : []),
                  ...(remaining > 0 ? [{ value: 'PENDING' as View, label: 'Waiting', count: remaining }] : []),
                ]}
              />
            </div>
            {canReview && (view === 'ALL' || view === 'SHORTLISTED') && (progress?.processed ?? 0) > 0 && (
              <form
                className="flex items-center gap-2 text-sm"
                onSubmit={(e) => {
                  e.preventDefault();
                  shortlistTop();
                }}
              >
                <label htmlFor="shortlist-top" className="font-semibold">
                  Shortlist top
                </label>
                <input
                  id="shortlist-top"
                  type="number"
                  min={1}
                  max={200}
                  value={topN}
                  onChange={(e) => setTopN(Math.max(1, Math.min(200, Number(e.target.value) || 1)))}
                  className="num h-9 w-16 rounded-md border border-line-strong bg-sunken text-center text-sm font-semibold focus:border-accent-text focus:outline-none"
                />
                <Button type="submit" variant="secondary" size="sm" className="h-9" disabled={busy}>
                  Apply
                </Button>
              </form>
            )}
          </div>

          {view === 'FAILED' || view === 'PENDING' ? (
            <div className="p-4 sm:p-5">
              <ResumeList
                sessionId={session.id}
                status={view}
                title={(n) => (view === 'FAILED' ? `${n} resume${n === 1 ? '' : 's'} could not be read` : `${n} resume${n === 1 ? '' : 's'} waiting to be scored`)}
                canRemove={canChange}
                open
                refreshKey={session}
                onRemoved={loadData}
              />
            </div>
          ) : candidates.length > 0 ? (
            <>
              <StandingsTable
                sessionId={session.id}
                candidates={candidates}
                weights={session.weights}
                {...(canReview && {
                  selected,
                  onToggle: (cid: string) => setSelected((s) => toggle(s, cid)),
                  onToggleAll: (ids: string[]) => setSelected((s) => toggleAll(s, ids)),
                })}
              />
              <div className="flex flex-col items-center justify-between gap-3 px-4 py-3 text-sm text-ink-muted sm:flex-row sm:px-5">
                <span className="num">
                  {page * PAGE_SIZE + 1}–{page * PAGE_SIZE + candidates.length} of {totalCandidates}
                </span>
                <Pagination page={page} totalPages={totalPages} onPage={setPage} />
              </div>
              <ShortlistBar
                count={selected.size}
                busy={busy}
                onShortlist={() => reviewSelected('SHORTLISTED')}
                onRemove={() => reviewSelected('NONE')}
                onClear={() => setSelected(new Set())}
              />
            </>
          ) : view === 'SHORTLISTED' ? (
            <p className="px-5 py-12 text-center text-sm text-ink-muted">
              Nobody is shortlisted yet. Select candidates in All, or shortlist the top few with the box above.
            </p>
          ) : (
            <p className="px-5 py-12 text-center text-sm text-ink-muted">
              No resume could be scored in this session. Check that the files are text-based PDFs, then remove them and add new ones.
            </p>
          )}
        </Panel>
      )}

      {session.status === 'FAILED' && failed > 0 && (
        <div className="mt-4">
          <ResumeList
            sessionId={session.id}
            status="FAILED"
            title={(n) => `${n} resume${n === 1 ? '' : 's'} could not be read`}
            canRemove
            refreshKey={session}
            onRemoved={loadData}
          />
        </div>
      )}
    </MainLayout>
  );
}

const LLM_STATE: Record<LlmState, { label: string; dot: string }> = {
  AVAILABLE: { label: 'Available', dot: 'bg-success' },
  REACHABLE: { label: 'Reachable', dot: 'bg-info' },
  MODEL_NOT_FOUND: { label: 'Model not found', dot: 'bg-danger' },
  AUTH_FAILED: { label: 'API key rejected', dot: 'bg-danger' },
  UNREACHABLE: { label: 'Unreachable', dot: 'bg-danger' },
  NOT_CONFIGURED: { label: 'Not configured', dot: 'bg-danger' },
  UNKNOWN: { label: 'Status unknown', dot: 'bg-ink-muted' },
};

function LlmStatusLine({ llm }: { llm: LlmStatus }) {
  const s = LLM_STATE[llm.state] ?? LLM_STATE.UNKNOWN;
  return (
    <span className="inline-flex items-center gap-1.5" title={llm.message}>
      <span aria-hidden="true" className={`size-1.5 rounded-full ${s.dot}`} />
      <span>
        Model {llm.model ?? 'unknown'}
        {llm.provider ? ` on ${llm.provider}` : ''} · {s.label}
      </span>
    </span>
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
