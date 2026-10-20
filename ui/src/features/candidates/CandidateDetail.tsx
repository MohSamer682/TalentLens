import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Check, FileText, Loader2, Mail } from 'lucide-react';
import { apiErrorMessage, candidatesApi, sessionsApi } from '@/api/client';
import { useAuthStore } from '@/features/auth/authStore';
import { canReviewSession } from '@/lib/permissions';
import type { CandidateScore, ReviewStatus, ScreeningSession } from '@/types';
import { Button } from '@/components/ui/Button';
import { Panel, PanelHeader } from '@/components/ui/Card';
import { Alert, Loader } from '@/components/ui/Feedback';
import { CandidateNotes } from '@/features/candidates/CandidateNotes';
import { HiringPipeline } from '@/features/pipeline/HiringPipeline';
import { StageBadge } from '@/features/pipeline/StageBadge';
import { MainLayout } from '@/layouts/MainLayout';
import { useCountUp } from '@/lib/motion';
import { DEFAULT_WEIGHTS, DIMENSIONS, cn, formatScore } from '@/lib/utils';

/** The LLM returns strengths/gaps as free text; split on newlines or bullets, never crash on null. */
function toLines(text: string | null | undefined): string[] {
  if (!text || !text.trim()) return [];
  return text
    .split(/\r?\n|\s[•\-*]\s+/)
    .map((line) => line.trim().replace(/^[•\-*]\s+/, ''))
    .filter(Boolean);
}

export default function CandidateDetail() {
  const { sessionId, candidateId } = useParams<{ sessionId: string; candidateId: string }>();
  const [score, setScore] = useState<CandidateScore | null>(null);
  const [session, setSession] = useState<ScreeningSession | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [downloading, setDownloading] = useState(false);
  const [reviewing, setReviewing] = useState(false);
  const me = useAuthStore((s) => s.user);
  const composite = useCountUp(score?.compositeScore ?? 0, 900);

  useEffect(() => {
    if (!sessionId || !candidateId) return;
    let active = true;
    Promise.all([
      sessionsApi.candidateDetail(sessionId, candidateId),
      sessionsApi.get(sessionId).catch(() => null),
    ])
      .then(([scoreRes, sessionRes]) => {
        if (!active) return;
        setScore(scoreRes.data);
        setSession(sessionRes?.data ?? null);
      })
      .catch((err) => active && setError(apiErrorMessage(err, 'Could not load this candidate')))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [sessionId, candidateId]);

  const handleDownloadResume = async () => {
    if (!sessionId || !candidateId) return;
    setError(null);
    setDownloading(true);
    try {
      const res = await sessionsApi.candidateResume(sessionId, candidateId);
      window.open(res.data.url, '_blank', 'noopener');
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not create the resume download link'));
    } finally {
      setDownloading(false);
    }
  };

  const toggleShortlist = async () => {
    if (!sessionId || !candidateId || !score) return;
    const previous = score.reviewStatus;
    const next: ReviewStatus = previous === 'SHORTLISTED' ? 'NONE' : 'SHORTLISTED';
    setError(null);
    setReviewing(true);
    setScore({ ...score, reviewStatus: next });
    try {
      await candidatesApi.review(sessionId, candidateId, next);
    } catch (err) {
      setScore((s) => s && { ...s, reviewStatus: previous });
      setError(apiErrorMessage(err, 'Could not save the shortlist'));
    } finally {
      setReviewing(false);
    }
  };

  const back = (
    <Link
      to={`/sessions/${sessionId}`}
      className="mb-4 inline-block text-sm font-semibold text-ink-muted hover:text-ink transition-colors"
    >
      ← {session ? session.title : 'Back to ranking'}
    </Link>
  );

  if (loading) {
    return (
      <MainLayout>
        <Loader label="Loading candidate…" />
      </MainLayout>
    );
  }

  if (!score) {
    return (
      <MainLayout>
        {back}
        <Alert title="This candidate could not be opened">
          {error || 'They may have been removed from the session.'}
        </Alert>
      </MainLayout>
    );
  }

  const weights = score.weights ?? session?.weights ?? DEFAULT_WEIGHTS;
  const rows = DIMENSIONS.map((d) => {
    const value = score[d.key] ?? 0;
    return { ...d, value, weight: weights[d.key], weighted: (value * weights[d.key]) / 100 };
  });
  const isShortlistedOrBeyond = score.reviewStatus !== 'NONE';
  const canReview = !!session && canReviewSession(me, session);
  const strengths = toLines(score.strengths);
  const gaps = toLines(score.gaps);

  return (
    <MainLayout>
      {back}

      {/* Header Row */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4 border-b border-line pb-6">
        <div className="flex flex-wrap items-center gap-4 min-w-0 flex-1">
          <span
            className={cn(
              'display flex h-14 min-w-14 shrink-0 items-center justify-center px-2 text-[34px] font-bold',
              isShortlistedOrBeyond ? 'marker' : 'rounded-lg bg-sunken text-ink'
            )}
            aria-label={`Rank ${score.rank}`}
          >
            {score.rank}
          </span>
          <div className="min-w-0 flex-1">
            <h1 className="break-words text-[26px] font-bold leading-tight sm:text-[30px] text-ink">
              {score.name || 'Name not found'}
            </h1>
            <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-ink-muted mt-1">
              {score.email ? (
                <a href={`mailto:${score.email}`} className="inline-flex items-center gap-1.5 hover:text-ink">
                  <Mail className="size-4" aria-hidden="true" />
                  {score.email}
                </a>
              ) : (
                <span>No email found in resume</span>
              )}
              {isShortlistedOrBeyond && (
                <StageBadge stage={score.reviewStatus}>
                  <Check className="size-3.5" aria-hidden="true" />
                </StageBadge>
              )}
            </p>
          </div>
        </div>

        {/* Score & Header Actions */}
        <div className="flex items-center gap-6">
          <div className="text-right">
            <p className="font-mono text-[36px] sm:text-[44px] font-bold leading-none text-ink">
              {composite.toFixed(1)}
              <span className="text-xl font-normal text-ink-muted"> / 100</span>
            </p>
          </div>

          <div className="flex items-center gap-2">
            <Button
              variant="secondary"
              onClick={handleDownloadResume}
              disabled={downloading}
            >
              {downloading ? <Loader2 className="size-4 animate-spin" /> : <FileText className="size-4" />}
              {downloading ? 'Opening…' : 'Open resume'}
            </Button>

            {canReview && score.reviewStatus === 'NONE' && (
              <Button
                variant="primary"
                onClick={toggleShortlist}
                disabled={reviewing || session?.status === 'RUNNING'}
              >
                Shortlist
              </Button>
            )}

            {canReview && score.reviewStatus === 'SHORTLISTED' && (
              <Button
                variant="secondary"
                onClick={toggleShortlist}
                disabled={reviewing || session?.status === 'RUNNING'}
                className="text-xs text-ink-muted hover:text-danger"
              >
                Remove from shortlist
              </Button>
            )}
          </div>
        </div>
      </div>

      {error && <Alert className="mb-4">{error}</Alert>}

      {/* Two Columns: Main (flex: 999 1 560px) and Aside (flex: 1 1 340px) */}
      <div className="flex flex-wrap gap-6 items-start">
        {/* Main Column */}
        <div className="flex-[999_1_560px] min-w-0 space-y-6">
          {/* 1. Hiring Pipeline Plate */}
          <HiringPipeline
            sessionId={sessionId!}
            candidateId={candidateId!}
            candidateName={score.name || 'Candidate'}
            currentStage={score.reviewStatus}
            onStageChange={(newStage) => setScore((s) => s && { ...s, reviewStatus: newStage })}
          />

          {/* 2. Strengths and Gaps Plate */}
          <Panel className="animate-fade-up">
            <PanelHeader title="Strengths and gaps" />
            <div className="space-y-5 p-5">
              {score.justification && (
                <p className="leading-relaxed text-sm text-ink max-w-[70ch]">
                  {score.justification.trim()}
                </p>
              )}
              <div className="grid gap-6 grid-cols-1 min-[560px]:grid-cols-2">
                <ReasonList title="Strengths" items={strengths} tone="accent" empty="None listed." />
                <ReasonList title="Gaps to probe" items={gaps} tone="danger" empty="None listed." />
              </div>
            </div>
          </Panel>
        </div>

        {/* Aside Column */}
        <aside className="flex-[1_1_340px] min-w-0 space-y-6">
          {/* 1. Candidate Notes Plate */}
          <CandidateNotes sessionId={sessionId!} candidateId={candidateId!} />

          {/* 2. Score Breakdown Plate */}
          <Panel className="animate-fade-up p-4">
            <h2 className="text-[15px] font-semibold text-ink mb-3">Score breakdown</h2>
            <div className="space-y-3">
              {rows.map((r) => (
                <div key={r.key} className="space-y-1">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-medium text-ink">{r.label}</span>
                    <span className="font-mono text-ink-muted">
                      {formatScore(r.value)} <span className="text-[10px]">({r.weight}%)</span>
                    </span>
                  </div>
                  <div className="h-2 w-full overflow-hidden rounded-full bg-sunken">
                    <div
                      className={cn(
                        'h-full origin-left rounded-full transition-all duration-500',
                        r.value < 50 ? 'bg-danger' : 'bg-accent-bar'
                      )}
                      style={{ width: `${Math.max(0, Math.min(100, r.value))}%` }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </Panel>
        </aside>
      </div>
    </MainLayout>
  );
}

function ReasonList({
  title,
  items,
  tone,
  empty,
}: {
  title: string;
  items: string[];
  tone: 'accent' | 'danger';
  empty: string;
}) {
  return (
    <div>
      <h3 className={cn('mb-2.5 text-xs font-semibold uppercase tracking-wider', tone === 'accent' ? 'text-success' : 'text-warn')}>
        {title}
      </h3>
      {items.length ? (
        <ul className="space-y-2 text-sm">
          {items.map((item, i) => (
            <li key={i} className="flex gap-2.5">
              <span
                aria-hidden="true"
                className={cn(
                  'mt-[7px] size-1.5 shrink-0 rounded-full',
                  tone === 'accent' ? 'bg-success' : 'bg-danger'
                )}
              />
              <span className="text-ink">{item}</span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-ink-muted italic">{empty}</p>
      )}
    </div>
  );
}
