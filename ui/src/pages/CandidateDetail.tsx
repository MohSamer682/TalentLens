import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Check, FileText, Loader2, Mail } from 'lucide-react';
import { apiErrorMessage, candidatesApi, sessionsApi } from '@/api/client';
import { useAuthStore } from '@/store/authStore';
import { canReviewSession } from '@/lib/permissions';
import type { CandidateScore, ScreeningSession } from '@/types';
import { Tag } from '@/components/ui/Badge';
import { Panel, PanelHeader } from '@/components/ui/Card';
import { Alert, Loader } from '@/components/ui/Feedback';
import { Button } from '@/components/ui/Button';
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
      sessionsApi.get(sessionId).catch(() => null), // the title is a nicety; the score page works without it
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

  // Optimistic: the chip flips at once and flips back if the save fails
  const toggleShortlist = async () => {
    if (!sessionId || !candidateId || !score) return;
    const previous = score.reviewStatus;
    const next = previous === 'SHORTLISTED' ? 'NONE' : 'SHORTLISTED';
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
    <Link to={`/sessions/${sessionId}`} className="mb-3 inline-block text-sm font-semibold text-ink-muted hover:text-ink">
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
        <Alert title="This candidate could not be opened">{error || 'They may have been removed from the session.'}</Alert>
      </MainLayout>
    );
  }

  const weights = score.weights ?? session?.weights ?? DEFAULT_WEIGHTS;
  const rows = DIMENSIONS.map((d) => {
    const value = score[d.key] ?? 0;
    return { ...d, value, weight: weights[d.key], weighted: (value * weights[d.key]) / 100 };
  });
  const total = rows.reduce((sum, r) => sum + r.weighted, 0);
  const inZone = score.reviewStatus === 'SHORTLISTED';
  // Without the session (it failed to load) the owner is unknown, so the toggle stays hidden.
  const canReview = !!session && canReviewSession(me, session);
  const strengths = toLines(score.strengths);
  const gaps = toLines(score.gaps);

  return (
    <MainLayout>
      {back}

      <div className="mb-6 flex flex-wrap items-center gap-4">
        <span
          className={cn(
            'display flex h-14 min-w-14 shrink-0 items-center justify-center px-2 text-[34px] font-bold',
            inZone ? 'marker' : 'rounded-lg bg-sunken text-ink'
          )}
          aria-label={`Rank ${score.rank}`}
        >
          {score.rank}
        </span>
        <div className="min-w-0 flex-1">
          <h1 className="break-words text-[26px] font-bold leading-tight sm:text-[32px]">{score.name || 'Name not found'}</h1>
          <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-ink-muted">
            {score.email ? (
              <a href={`mailto:${score.email}`} className="inline-flex items-center gap-1.5 hover:text-ink">
                <Mail className="size-4" aria-hidden="true" />
                {score.email}
              </a>
            ) : (
              <span>No email found in resume</span>
            )}
            {inZone && (
              <span className="inline-flex items-center gap-1.5 rounded-full bg-mark-soft px-2.5 py-0.5 font-semibold text-mark-text">
                <Check className="size-3.5" aria-hidden="true" />
                Shortlisted
              </span>
            )}
          </p>
        </div>
        <div className="text-right">
          <p className="display text-[40px] font-bold leading-none">{composite.toFixed(1)}</p>
          <p className="text-[13px] text-ink-muted">weighted score of 100</p>
        </div>
      </div>

      {error && <Alert className="mb-4">{error}</Alert>}

      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="min-w-0 space-y-5">
          <Panel className="animate-fade-up">
            <PanelHeader title="Score breakdown" />
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="border-b border-line bg-sunken">
                  <tr className="text-[13px] font-medium text-ink-muted">
                    <th scope="col" className="px-4 py-2 text-left sm:pl-5">Dimension</th>
                    <th scope="col" className="px-3 py-2 text-left">Score</th>
                    <th scope="col" className="px-3 py-2 text-right">Weight</th>
                    <th scope="col" className="px-4 py-2 text-right sm:pr-5">Points</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r, i) => (
                    <tr key={r.key} className="border-b border-line">
                      <th scope="row" className="px-4 py-2.5 text-left font-medium sm:pl-5">{r.label}</th>
                      <td className="px-3 py-2.5">
                        <div className="flex items-center gap-3">
                          <span className="hidden h-2 w-full max-w-48 overflow-hidden rounded-full bg-line sm:block" aria-hidden="true">
                            <span
                              className={cn('block h-full origin-left animate-grow-x rounded-full', r.value < 50 ? 'bg-danger' : 'bg-accent-bar')}
                              style={{ width: `${Math.max(0, Math.min(100, r.value))}%`, animationDelay: `${120 + i * 70}ms` }}
                            />
                          </span>
                          <span className={cn('num w-9 text-right font-semibold', r.value < 50 && 'text-danger')}>
                            {formatScore(r.value)}
                          </span>
                        </div>
                      </td>
                      <td className="num px-3 py-2.5 text-right text-ink-muted">{r.weight}%</td>
                      <td className="num px-4 py-2.5 text-right font-semibold sm:pr-5">{r.weighted.toFixed(1)}</td>
                    </tr>
                  ))}
                </tbody>
                <tfoot>
                  <tr>
                    <th scope="row" colSpan={3} className="px-4 py-2.5 text-right font-semibold sm:pl-5">
                      Weighted total
                    </th>
                    <td className="num px-4 py-2.5 text-right text-base font-bold sm:pr-5">{total.toFixed(1)}</td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </Panel>

          <Panel className="animate-fade-up" style={{ animationDelay: '80ms' }}>
            <PanelHeader title="Why this score" />
            <div className="space-y-5 p-4 sm:p-5">
              <p className="max-w-[70ch] leading-relaxed">
                {score.justification?.trim() || 'The model returned no justification for this candidate.'}
              </p>
              <div className="grid gap-5 md:grid-cols-2">
                <ReasonList title="Strengths" items={strengths} tone="accent" empty="None listed." />
                <ReasonList title="Gaps" items={gaps} tone="danger" empty="None listed." />
              </div>
            </div>
          </Panel>
        </div>

        <aside className="min-w-0 space-y-5">
          <Panel className="animate-fade-up p-4">
            <h2 className="font-semibold">{inZone ? 'On the shortlist' : 'Not shortlisted yet'}</h2>
            <p className={cn('mt-1 text-[13px] text-ink-muted', canReview && 'mb-3')}>
              Everyone working on this session sees the same shortlist.
            </p>
            {canReview && <Button
              variant={inZone ? 'secondary' : 'primary'}
              className="w-full"
              onClick={toggleShortlist}
              disabled={reviewing || session?.status === 'RUNNING'}
              aria-pressed={inZone}
            >
              {inZone ? 'Remove from shortlist' : 'Shortlist'}
            </Button>}
          </Panel>
          <Panel className="animate-fade-up p-4" style={{ animationDelay: '60ms' }}>
            <h2 className="mb-1 font-semibold">Original resume</h2>
            <p className="mb-3 text-[13px] text-ink-muted">Opens the PDF from your company storage. The link expires after 15 minutes.</p>
            <Button variant="secondary" className="w-full" onClick={handleDownloadResume} disabled={downloading}>
              {downloading ? <Loader2 className="animate-spin" /> : <FileText />}
              {downloading ? 'Creating link…' : 'Open resume PDF'}
            </Button>
          </Panel>

          <Panel className="animate-fade-up p-4" style={{ animationDelay: '120ms' }}>
            <h2 className="mb-3 font-semibold">Skills found</h2>
            {(score.skills ?? []).length > 0 ? (
              <div className="flex flex-wrap gap-1.5">
                {score.skills.map((skill) => (
                  <Tag key={skill}>{skill}</Tag>
                ))}
              </div>
            ) : (
              <p className="text-sm text-ink-muted">No skills were extracted from this resume.</p>
            )}
          </Panel>
        </aside>
      </div>
    </MainLayout>
  );
}

function ReasonList({ title, items, tone, empty }: { title: string; items: string[]; tone: 'accent' | 'danger'; empty: string }) {
  return (
    <div>
      <h3 className="mb-2 text-sm font-semibold text-ink">{title}</h3>
      {items.length ? (
        <ul className="space-y-2 text-sm">
          {items.map((item, i) => (
            <li key={i} className="flex gap-2.5">
              <span aria-hidden="true" className={cn('mt-[7px] size-1.5 shrink-0 rounded-full', tone === 'accent' ? 'bg-success' : 'bg-danger')} />
              <span>{item}</span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-ink-muted">{empty}</p>
      )}
    </div>
  );
}
