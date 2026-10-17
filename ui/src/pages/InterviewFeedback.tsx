import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { CheckCircle2, Clock, FileText, Loader2 } from 'lucide-react';
import { apiErrorMessage, pipelineApi } from '@/api/client';
import type { InterviewDetail, InterviewRecommendation } from '@/types';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Panel } from '@/components/ui/Card';
import { Alert, Loader } from '@/components/ui/Feedback';
import { InterviewPlaceText } from '@/components/InterviewPlace';
import { KIND_LABEL } from '@/lib/pipeline';
import { MainLayout } from '@/layouts/MainLayout';
import { cn, formatRelativeDate, formatScore } from '@/lib/utils';

const RECOMMENDATIONS: {
  value: InterviewRecommendation;
  label: string;
  sub: string;
  tone: 'success' | 'warn' | 'danger';
}[] = [
  { value: 'STRONG_HIRE', label: 'Strong hire', sub: 'Raise the bar', tone: 'success' },
  { value: 'HIRE', label: 'Hire', sub: 'Meets the bar', tone: 'success' },
  { value: 'NO_HIRE', label: 'No hire', sub: 'Below the bar', tone: 'warn' },
  { value: 'STRONG_NO_HIRE', label: 'Strong no hire', sub: 'Clear concerns', tone: 'danger' },
];

function toLines(text: string | null | undefined): string[] {
  if (!text || !text.trim()) return [];
  return text
    .split(/\r?\n|\s[•\-*]\s+/)
    .map((line) => line.trim().replace(/^[•\-*]\s+/, ''))
    .filter(Boolean);
}

function formatTimeRange(startIso: string, durationMinutes: number): string {
  try {
    const start = new Date(startIso);
    const end = new Date(start.getTime() + durationMinutes * 60 * 1000);
    const dateStr = start.toLocaleDateString('en-GB', {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
    });
    const startTime = start.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    const endTime = end.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    return `${dateStr}, ${startTime} – ${endTime}`;
  } catch {
    return startIso;
  }
}

export default function InterviewFeedback() {
  const { interviewId } = useParams<{ interviewId: string }>();

  const [interview, setInterview] = useState<InterviewDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Form states
  const [recommendation, setRecommendation] = useState<InterviewRecommendation | null>(null);
  const [rating, setRating] = useState<number | null>(null);
  const [assessment, setAssessment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);

  useEffect(() => {
    if (!interviewId) return;
    let active = true;
    pipelineApi
      .getInterview(interviewId)
      .then((res) => {
        if (!active) return;
        setInterview(res.data);
        if (res.data.recommendation) setRecommendation(res.data.recommendation);
        if (res.data.rating) setRating(res.data.rating);
        if (res.data.feedbackAssessment) setAssessment(res.data.feedbackAssessment);
      })
      .catch((err) => {
        if (active) setError(apiErrorMessage(err, 'Could not load interview'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [interviewId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!interview || !recommendation || !rating || assessment.trim().length < 20) return;
    setShowConfirm(true);
  };

  const handleConfirmSubmit = async () => {
    if (!interview || !recommendation || !rating) return;
    setError(null);
    setSubmitting(true);
    try {
      await pipelineApi.submitFeedback(interview.id, {
        recommendation,
        rating,
        assessment: assessment.trim(),
      });
      // Reload interview details
      const updated = await pipelineApi.getInterview(interview.id);
      setInterview(updated.data);
      setShowConfirm(false);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not submit feedback'));
      setShowConfirm(false);
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <MainLayout>
        <Loader label="Loading interview feedback…" />
      </MainLayout>
    );
  }

  if (!interview) {
    return (
      <MainLayout>
        <Link to="/interviews" className="mb-4 inline-block text-sm font-semibold text-ink-muted hover:text-ink">
          ← My interviews
        </Link>
        <Alert title="Interview not found">{error || 'This interview could not be loaded.'}</Alert>
      </MainLayout>
    );
  }

  const isCompleted = interview.status === 'COMPLETED';
  const now = new Date();
  const startTime = new Date(interview.scheduledStart);
  const isBeforeStart = now < startTime;
  const isFeedbackDue = !isCompleted && !isBeforeStart;

  const statusTone = isCompleted ? 'success' : isFeedbackDue ? 'warn' : 'neutral';
  const statusLabel = isCompleted ? 'Submitted' : isFeedbackDue ? 'Feedback due' : 'Scheduled';

  const strengths = toLines(interview.strengths);
  const gaps = toLines(interview.gaps);
  const isFormValid = recommendation != null && rating != null && assessment.trim().length >= 20;

  return (
    <MainLayout>
      <Link
        to="/interviews"
        className="mb-4 inline-block text-sm font-semibold text-ink-muted hover:text-ink transition-colors"
      >
        ← My interviews
      </Link>

      {/* Header */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4 border-b border-line pb-6">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-[30px] font-bold text-ink">{interview.candidateName}</h1>
            <Badge tone={statusTone}>{statusLabel}</Badge>
          </div>
          <p className="mt-1 text-sm text-ink-muted flex flex-wrap items-center gap-x-3 gap-y-1">
            <span>{interview.sessionTitle}</span>
            <span>·</span>
            <span>{KIND_LABEL[interview.kind] ?? interview.kind} interview</span>
            <span>·</span>
            <span>{formatTimeRange(interview.scheduledStart, interview.durationMinutes)}</span>
            <span>·</span>
            <InterviewPlaceText placeType={interview.placeType} location={interview.location} />
          </p>
        </div>
      </div>

      {error && <Alert className="mb-6">{error}</Alert>}

      {/* Two columns layout: Left Aside (340px) and Right Form Main */}
      <div className="flex flex-wrap gap-6 items-start">
        {/* Left Column (340px basis) */}
        <div className="w-full lg:w-[340px] shrink-0 space-y-6">
          {/* From the recruiter */}
          <Panel className="animate-fade-up p-5">
            <h2 className="text-[15px] font-semibold text-ink mb-1">
              From the recruiter
              {interview.recruiterName && (
                <span className="font-normal text-xs text-ink-muted block mt-0.5">
                  Scheduled by {interview.recruiterName}
                </span>
              )}
            </h2>
            <div className="mt-3 text-sm text-ink">
              {interview.noteToInterviewer?.trim() ? (
                <p className="whitespace-pre-wrap rounded-lg bg-sunken p-3 text-xs leading-relaxed">
                  {interview.noteToInterviewer.trim()}
                </p>
              ) : (
                <p className="text-xs text-ink-muted italic">No specific notes provided.</p>
              )}
            </div>
          </Panel>

          {/* AI Screen */}
          <Panel className="animate-fade-up p-5 space-y-4">
            <div className="flex items-center justify-between border-b border-line pb-3">
              <h2 className="text-[15px] font-semibold text-ink">AI screen</h2>
              {interview.rank && (
                <span className="font-mono text-xs font-semibold px-2 py-0.5 rounded-full bg-sunken text-ink">
                  Rank #{interview.rank}
                </span>
              )}
            </div>

            <div className="flex items-baseline gap-2">
              <span className="font-mono text-[36px] font-bold text-ink leading-none">
                {interview.compositeScore != null ? formatScore(interview.compositeScore) : '–'}
              </span>
              <span className="text-sm text-ink-muted">/ 100</span>
            </div>

            {/* Strengths */}
            <div>
              <h3 className="text-xs font-semibold uppercase tracking-wider text-success mb-2">Strengths</h3>
              {strengths.length > 0 ? (
                <ul className="space-y-1.5 text-xs text-ink">
                  {strengths.map((s, i) => (
                    <li key={i} className="flex gap-2">
                      <span aria-hidden="true" className="mt-1 size-1.5 rounded-full bg-success shrink-0" />
                      <span>{s}</span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-xs text-ink-muted italic">None highlighted</p>
              )}
            </div>

            {/* Gaps to probe */}
            <div>
              <h3 className="text-xs font-semibold uppercase tracking-wider text-warn mb-2">Gaps to probe</h3>
              {gaps.length > 0 ? (
                <ul className="space-y-1.5 text-xs text-ink">
                  {gaps.map((g, i) => (
                    <li key={i} className="flex gap-2">
                      <span aria-hidden="true" className="mt-1 size-1.5 rounded-full bg-warn shrink-0" />
                      <span>{g}</span>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-xs text-ink-muted italic">None highlighted</p>
              )}
            </div>

            {/* Open resume button */}
            {interview.resumeUrl && (
              <div className="pt-2 border-t border-line">
                <Button
                  variant="secondary"
                  size="sm"
                  className="w-full"
                  onClick={() => window.open(interview.resumeUrl!, '_blank', 'noopener')}
                >
                  <FileText className="size-4" />
                  Open resume PDF
                </Button>
              </div>
            )}
          </Panel>
        </div>

        {/* Right Column: Feedback Form or Read-only feedback */}
        <div className="min-w-0 flex-1">
          <Panel className="animate-fade-up p-6">
            <div className="border-b border-line pb-4 mb-6">
              <h2 className="text-base font-semibold text-ink">Your feedback</h2>
              <p className="text-xs text-ink-muted mt-0.5">Visible to recruiters and the HR chief</p>
            </div>

            {isCompleted ? (
              /* Read-only Feedback View */
              <div className="space-y-6">
                <div>
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-muted mb-2">
                    Recommendation
                  </h3>
                  <div className="inline-flex">
                    <Badge tone={statusTone} className="text-sm px-3 py-1">
                      {RECOMMENDATIONS.find((r) => r.value === interview.recommendation)?.label ??
                        interview.recommendation}
                    </Badge>
                  </div>
                </div>

                <div>
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-muted mb-2">
                    Overall rating
                  </h3>
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-xl font-bold text-ink">{interview.rating} / 5</span>
                    <span className="text-xs text-ink-muted">
                      ({interview.rating === 1 ? 'Poor' : interview.rating === 3 ? 'Meets bar' : interview.rating === 5 ? 'Exceptional' : 'Rated'})
                    </span>
                  </div>
                </div>

                <div>
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-muted mb-2">
                    Assessment
                  </h3>
                  <div className="rounded-xl border border-line bg-surface p-4 text-sm text-ink leading-relaxed whitespace-pre-wrap">
                    {interview.feedbackAssessment}
                  </div>
                  <p className="text-xs text-ink-muted mt-2">
                    Submitted {formatRelativeDate(interview.feedbackSubmittedAt || '')}
                  </p>
                </div>
              </div>
            ) : isBeforeStart ? (
              /* Before Interview Start */
              <div className="py-12 text-center space-y-3">
                <Clock className="mx-auto size-10 text-ink-muted" aria-hidden="true" />
                <h3 className="text-base font-semibold text-ink">Interview hasn't started yet</h3>
                <p className="text-sm text-ink-muted max-w-md mx-auto">
                  You can give feedback once the interview has started at{' '}
                  <span className="font-semibold text-ink">
                    {startTime.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                  </span>
                  .
                </p>
              </div>
            ) : (
              /* Feedback Form */
              <form onSubmit={handleSubmit} className="space-y-6">
                {/* 1. Recommendation (4 large radio cards) */}
                <div>
                  <label className="block text-xs font-semibold uppercase tracking-wider text-ink-muted mb-3">
                    Recommendation
                  </label>
                  <div className="grid gap-3 sm:grid-cols-2" role="radiogroup" aria-label="Recommendation">
                    {RECOMMENDATIONS.map((r) => {
                      const isSelected = recommendation === r.value;
                      return (
                        <label
                          key={r.value}
                          className={cn(
                            'lift relative flex cursor-pointer flex-col rounded-xl border p-4 transition-all',
                            isSelected
                              ? r.tone === 'success'
                                ? 'border-success bg-success/10 ring-2 ring-success/20 text-success'
                                : r.tone === 'warn'
                                ? 'border-warn bg-warn/10 ring-2 ring-warn/20 text-warn'
                                : 'border-danger bg-danger/10 ring-2 ring-danger/20 text-danger'
                              : 'border-line bg-surface hover:bg-sunken/40 text-ink'
                          )}
                        >
                          <input
                            type="radio"
                            name="recommendation"
                            value={r.value}
                            checked={isSelected}
                            onChange={() => setRecommendation(r.value)}
                            className="sr-only"
                          />
                          <span className="text-sm font-semibold">{r.label}</span>
                          <span
                            className={cn(
                              'text-xs mt-0.5',
                              isSelected ? 'opacity-90' : 'text-ink-muted'
                            )}
                          >
                            {r.sub}
                          </span>
                        </label>
                      );
                    })}
                  </div>
                </div>

                {/* 2. Overall Rating (five 48x44 pill radios 1-5) */}
                <div>
                  <div className="flex items-center justify-between mb-3">
                    <label className="text-xs font-semibold uppercase tracking-wider text-ink-muted">
                      Overall rating
                    </label>
                    <span className="text-xs text-ink-muted">1 poor · 3 meets · 5 exceptional</span>
                  </div>
                  <div className="flex gap-2" role="radiogroup" aria-label="Overall rating">
                    {[1, 2, 3, 4, 5].map((val) => {
                      const isSelected = rating === val;
                      return (
                        <label
                          key={val}
                          className={cn(
                            'flex h-11 w-12 cursor-pointer items-center justify-center rounded-lg border font-mono text-sm font-bold transition-all',
                            isSelected
                              ? 'border-accent bg-accent text-accent-ink shadow-sm'
                              : 'border-line bg-surface text-ink hover:bg-sunken'
                          )}
                        >
                          <input
                            type="radio"
                            name="rating"
                            value={val}
                            checked={isSelected}
                            onChange={() => setRating(val)}
                            className="sr-only"
                          />
                          {val}
                        </label>
                      );
                    })}
                  </div>
                </div>

                {/* 3. Written Assessment Textarea */}
                <div>
                  <div className="flex items-center justify-between mb-2">
                    <label htmlFor="assessment-input" className="text-xs font-semibold uppercase tracking-wider text-ink-muted">
                      Assessment
                    </label>
                    <span className="font-mono text-xs text-ink-muted">
                      {assessment.length} / 5,000
                    </span>
                  </div>
                  <textarea
                    id="assessment-input"
                    value={assessment}
                    onChange={(e) => setAssessment(e.target.value)}
                    placeholder="What you saw, with examples. At least 20 characters."
                    rows={7}
                    maxLength={5000}
                    className="w-full min-h-[180px] rounded-xl border border-line bg-surface p-3.5 text-sm text-ink focus:border-accent-text focus:outline-none focus:ring-2 focus:ring-accent-text/25 leading-relaxed"
                  />
                  <p className="text-[11px] text-ink-muted mt-1.5">
                    What you saw, with examples. At least 20 characters.
                  </p>
                </div>

                {/* Footer and Submit */}
                <div className="flex flex-wrap items-center justify-between gap-4 pt-4 border-t border-line">
                  <p className="text-xs text-ink-muted">
                    Feedback can't be changed after you submit it.
                  </p>
                  <Button
                    type="submit"
                    variant="primary"
                    disabled={!isFormValid || submitting}
                    className="h-11 px-6 text-sm"
                  >
                    Submit feedback
                  </Button>
                </div>
              </form>
            )}
          </Panel>
        </div>
      </div>

      {/* Confirmation Dialog */}
      {showConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 animate-fade-in backdrop-blur-sm">
          <div className="w-full max-w-md rounded-2xl border border-line bg-surface p-6 shadow-xl space-y-4">
            <h3 className="text-lg font-bold text-ink">Submit interview feedback?</h3>
            <p className="text-sm text-ink-muted leading-relaxed">
              Submit feedback for <span className="font-semibold text-ink">{interview.candidateName}</span>?
              This cannot be edited or retracted once submitted.
            </p>
            <div className="flex justify-end gap-3 pt-3 border-t border-line">
              <Button
                variant="secondary"
                onClick={() => setShowConfirm(false)}
                disabled={submitting}
              >
                Cancel
              </Button>
              <Button
                variant="primary"
                onClick={handleConfirmSubmit}
                disabled={submitting}
              >
                {submitting ? <Loader2 className="size-4 animate-spin" /> : <CheckCircle2 className="size-4" />}
                {submitting ? 'Submitting…' : 'Confirm and submit'}
              </Button>
            </div>
          </div>
        </div>
      )}
    </MainLayout>
  );
}
