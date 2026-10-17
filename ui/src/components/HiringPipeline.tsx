import { useEffect, useState } from 'react';
import {
  Calendar,
  CheckCircle2,
  Download,
  FileText,
  MapPin,
  Pencil,
  Plus,
  Trash2,
  Upload,
  UserCheck,
} from 'lucide-react';
import { apiErrorMessage, pipelineApi } from '@/api/client';
import { useAuthStore } from '@/store/authStore';
import { hasPermission } from '@/lib/permissions';
import type {
  CandidateInterview,
  CandidateOffer,
  CandidateStageEvent,
  InterviewKind,
  InterviewRecommendation,
  ReviewStatus,
  Role,
} from '@/types';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Panel } from '@/components/ui/Card';
import { Alert, Loader } from '@/components/ui/Feedback';
import { Segmented } from '@/components/ui/Segmented';
import { cn, formatRelativeDate } from '@/lib/utils';

const STAGES: { key: ReviewStatus; label: string }[] = [
  { key: 'NONE', label: 'Screened' },
  { key: 'SHORTLISTED', label: 'Shortlisted' },
  { key: 'CONTACTED', label: 'Contacted' },
  { key: 'INTERVIEW_SCHEDULED', label: 'Interviewing' },
  { key: 'INTERVIEWED', label: 'Interviewed' },
  { key: 'OFFER', label: 'Offer' },
  { key: 'OFFER_APPROVED', label: 'Offer approved' },
];

const STAGE_ORDER: Record<ReviewStatus, number> = {
  NONE: 0,
  SHORTLISTED: 1,
  CONTACTED: 2,
  INTERVIEW_SCHEDULED: 3,
  INTERVIEWED: 4,
  OFFER: 5,
  OFFER_APPROVED: 6,
};

const RECOMMENDATION_LABELS: Record<InterviewRecommendation, { label: string; tone: string }> = {
  STRONG_HIRE: { label: 'Strong hire', tone: 'bg-success/15 text-success' },
  HIRE: { label: 'Hire', tone: 'bg-success/15 text-success' },
  NO_HIRE: { label: 'No hire', tone: 'bg-warn/15 text-warn' },
  STRONG_NO_HIRE: { label: 'Strong no hire', tone: 'bg-danger/15 text-danger' },
};

// events that record an action rather than a stage change
const EVENT_LABEL: Record<string, string> = {
  FEEDBACK_SUBMITTED: 'Feedback',
  INTERVIEW_RESCHEDULED: 'Rescheduled',
  INTERVIEW_CANCELLED: 'Cancelled',
  NONE: 'Screened',
};

function formatStars(rating: number | null): string {
  if (!rating) return '';
  const full = Math.min(5, Math.max(1, rating));
  return '★'.repeat(full) + '☆'.repeat(5 - full) + ` ${full}/5`;
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

interface HiringPipelineProps {
  sessionId: string;
  candidateId: string;
  candidateName: string;
  currentStage: ReviewStatus;
  onStageChange?: (newStage: ReviewStatus) => void;
}

export function HiringPipeline({
  sessionId,
  candidateId,
  candidateName,
  currentStage,
  onStageChange,
}: HiringPipelineProps) {
  const me = useAuthStore((s) => s.user);
  const canManage = hasPermission(me, 'PIPELINE_MANAGE');
  const canApprove = hasPermission(me, 'OFFER_APPROVE');

  const [interviews, setInterviews] = useState<CandidateInterview[]>([]);
  const [timeline, setTimeline] = useState<CandidateStageEvent[]>([]);
  const [offer, setOffer] = useState<CandidateOffer | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Scheduling form state
  const [showScheduleForm, setShowScheduleForm] = useState(false);
  const [scheduleKind, setScheduleKind] = useState<InterviewKind>('TECHNICAL');
  const [interviewers, setInterviewers] = useState<Array<{ id: string; name: string; email: string; role: Role }>>([]);
  const [selectedInterviewerId, setSelectedInterviewerId] = useState('');
  const [scheduledStart, setScheduledStart] = useState('');
  const [durationMinutes, setDurationMinutes] = useState(60);
  const [location, setLocation] = useState('');
  const [noteToInterviewer, setNoteToInterviewer] = useState('');
  const [submittingSchedule, setSubmittingSchedule] = useState(false);

  // Rescheduling modal/state
  const [reschedulingInterview, setReschedulingInterview] = useState<CandidateInterview | null>(null);
  const [rescheduleStart, setRescheduleStart] = useState('');
  const [rescheduleDuration, setRescheduleDuration] = useState(60);
  const [rescheduleLocation, setRescheduleLocation] = useState('');
  const [submittingReschedule, setSubmittingReschedule] = useState(false);

  // Offer upload & approve
  const [uploadingOffer, setUploadingOffer] = useState(false);
  const [approvingOffer, setApprovingOffer] = useState(false);
  const [confirmApprove, setConfirmApprove] = useState(false);

  const reloadData = async () => {
    try {
      const [tlRes, intRes, offRes] = await Promise.all([
        pipelineApi.getTimeline(sessionId, candidateId),
        pipelineApi.getInterviews(sessionId, candidateId),
        pipelineApi.getOffer(sessionId, candidateId).catch(() => ({ data: null })),
      ]);
      setTimeline(tlRes.data);
      setInterviews(intRes.data);
      setOffer(offRes.data);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not load pipeline details'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    reloadData();
  }, [sessionId, candidateId]);

  // Load interviewers when schedule kind changes
  useEffect(() => {
    if (!showScheduleForm) return;
    pipelineApi
      .getInterviewers(scheduleKind)
      .then((res) => {
        setInterviewers(res.data);
        if (res.data.length > 0 && (!selectedInterviewerId || !res.data.some((i) => i.id === selectedInterviewerId))) {
          setSelectedInterviewerId(res.data[0].id);
        } else if (res.data.length === 0) {
          setSelectedInterviewerId('');
        }
      })
      .catch(() => {});
  }, [showScheduleForm, scheduleKind]);

  const handleMarkContacted = async () => {
    setError(null);
    try {
      await pipelineApi.markContacted(sessionId, candidateId);
      onStageChange?.('CONTACTED');
      reloadData();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not mark as contacted'));
    }
  };

  const handleScheduleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedInterviewerId || !scheduledStart) return;
    setError(null);
    setSubmittingSchedule(true);
    try {
      await pipelineApi.scheduleInterview(sessionId, candidateId, {
        kind: scheduleKind,
        interviewerId: selectedInterviewerId,
        scheduledStart: new Date(scheduledStart).toISOString(),
        durationMinutes,
        location: location.trim() || undefined,
        noteToInterviewer: noteToInterviewer.trim() || undefined,
      });
      setShowScheduleForm(false);
      setScheduledStart('');
      setLocation('');
      setNoteToInterviewer('');
      if (STAGE_ORDER[currentStage] < STAGE_ORDER.INTERVIEW_SCHEDULED) {
        onStageChange?.('INTERVIEW_SCHEDULED');
      }
      reloadData();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not schedule interview'));
    } finally {
      setSubmittingSchedule(false);
    }
  };

  const handleRescheduleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reschedulingInterview || !rescheduleStart) return;
    setError(null);
    setSubmittingReschedule(true);
    try {
      await pipelineApi.rescheduleInterview(sessionId, candidateId, reschedulingInterview.id, {
        scheduledStart: new Date(rescheduleStart).toISOString(),
        durationMinutes: rescheduleDuration,
        location: rescheduleLocation.trim() || undefined,
      });
      setReschedulingInterview(null);
      reloadData();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not reschedule interview'));
    } finally {
      setSubmittingReschedule(false);
    }
  };

  const handleCancelInterview = async (interviewId: string) => {
    if (!window.confirm('Cancel this interview?')) return;
    setError(null);
    try {
      await pipelineApi.cancelInterview(sessionId, candidateId, interviewId);
      reloadData();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not cancel interview'));
    }
  };

  const handleMoveToOffer = async () => {
    setError(null);
    try {
      await pipelineApi.moveToOffer(sessionId, candidateId);
      onStageChange?.('OFFER');
      reloadData();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not move to offer stage'));
    }
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.type !== 'application/pdf') {
      setError('Please upload a PDF offer letter');
      return;
    }
    setError(null);
    setUploadingOffer(true);
    try {
      const res = await pipelineApi.uploadOffer(sessionId, candidateId, file);
      setOffer(res.data);
      reloadData();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not upload offer'));
    } finally {
      setUploadingOffer(false);
      e.target.value = '';
    }
  };

  const handleDownloadOffer = async () => {
    try {
      const res = await pipelineApi.downloadOffer(sessionId, candidateId);
      window.open(res.data.url, '_blank', 'noopener');
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not download offer'));
    }
  };

  const handleApproveOffer = async () => {
    setError(null);
    setApprovingOffer(true);
    try {
      const res = await pipelineApi.approveOffer(sessionId, candidateId);
      setOffer(res.data);
      setConfirmApprove(false);
      onStageChange?.('OFFER_APPROVED');
      reloadData();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not approve offer'));
    } finally {
      setApprovingOffer(false);
    }
  };

  const stageIdx = STAGE_ORDER[currentStage] ?? 0;
  const completedCount = interviews.filter((i) => i.status === 'COMPLETED').length;
  const totalInterviews = interviews.filter((i) => i.status !== 'CANCELLED').length;

  const stageProgressLabel =
    currentStage === 'INTERVIEW_SCHEDULED' || currentStage === 'INTERVIEWED'
      ? `Interviewing · ${completedCount} of ${totalInterviews} done`
      : STAGES.find((s) => s.key === currentStage)?.label ?? currentStage;

  if (loading) {
    return (
      <Panel className="p-8 flex items-center justify-center">
        <Loader label="Loading pipeline details…" />
      </Panel>
    );
  }

  return (
    <Panel className="animate-fade-up p-5 space-y-6">
      {/* Header with stage and progress */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line pb-4">
        <div>
          <h2 className="text-[15px] font-semibold text-ink">Hiring pipeline</h2>
        </div>
        <Badge
          tone={
            currentStage === 'OFFER_APPROVED'
              ? 'success'
              : currentStage === 'OFFER'
              ? 'warn'
              : currentStage === 'SHORTLISTED'
              ? 'mark'
              : 'neutral'
          }
        >
          {stageProgressLabel}
        </Badge>
      </div>

      {error && <Alert>{error}</Alert>}

      {/* Stage Track - 7 pills */}
      <nav aria-label="Pipeline stages" className="overflow-x-auto pb-1">
        <ol className="flex items-center gap-2 min-w-max">
          {STAGES.map((s, idx) => {
            const isReached = idx < stageIdx;
            const isCurrent = idx === stageIdx;
            const isFuture = idx > stageIdx;
            const isShortlisted = s.key === 'SHORTLISTED';

            return (
              <li key={s.key} className="flex items-center">
                <span
                  className={cn(
                    'inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium transition-colors',
                    isReached && 'bg-sunken text-ink font-semibold',
                    isCurrent && 'bg-sunken border border-accent-text text-ink font-semibold ring-1 ring-accent-text/20',
                    isFuture && 'border border-dashed border-line-strong text-ink-muted'
                  )}
                  {...(isCurrent ? { 'aria-current': 'step' } : {})}
                >
                  <span
                    aria-hidden="true"
                    className={cn(
                      'size-1.5 rounded-full shrink-0',
                      isShortlisted
                        ? 'bg-mark'
                        : isCurrent
                        ? 'bg-accent-bar'
                        : isReached
                        ? 'bg-ink-muted'
                        : 'bg-transparent'
                    )}
                  />
                  {s.label}
                </span>
                {idx < STAGES.length - 1 && (
                  <span aria-hidden="true" className="mx-1 text-ink-muted/50 text-xs">
                    ›
                  </span>
                )}
              </li>
            );
          })}
        </ol>
      </nav>

      {/* Next Action Primary Button / Controls */}
      {canManage && (
        <div className="flex flex-wrap items-center gap-3">
          {currentStage === 'SHORTLISTED' && (
            <Button variant="primary" onClick={handleMarkContacted}>
              <UserCheck className="size-4" aria-hidden="true" />
              Mark as contacted
            </Button>
          )}

          {stageIdx >= STAGE_ORDER.CONTACTED && stageIdx < STAGE_ORDER.OFFER && !showScheduleForm && (
            <Button
              variant={currentStage === 'CONTACTED' || currentStage === 'INTERVIEW_SCHEDULED' ? 'primary' : 'secondary'}
              onClick={() => setShowScheduleForm(true)}
            >
              <Plus className="size-4" aria-hidden="true" />
              Schedule interview
            </Button>
          )}

          {stageIdx >= STAGE_ORDER.CONTACTED && stageIdx < STAGE_ORDER.OFFER && completedCount > 0 && (
            <Button variant="primary" onClick={handleMoveToOffer}>
              Move to offer
            </Button>
          )}
        </div>
      )}

      {/* Inline Schedule Form */}
      {showScheduleForm && (
        <form
          onSubmit={handleScheduleSubmit}
          className="rounded-xl border border-line bg-sunken p-4 space-y-4 animate-fade-in"
        >
          <div className="flex items-center justify-between border-b border-line pb-2">
            <h3 className="text-sm font-semibold text-ink">Schedule new interview</h3>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => setShowScheduleForm(false)}
            >
              Cancel
            </Button>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs font-medium text-ink-muted">Interview kind</label>
              <Segmented
                label="Interview kind"
                value={scheduleKind}
                onChange={(val) => {
                  setScheduleKind(val as InterviewKind);
                  setSelectedInterviewerId('');
                }}
                options={[
                  { value: 'TECHNICAL', label: 'Technical' },
                  { value: 'HR', label: 'HR' },
                ]}
              />
            </div>

            <div>
              <label htmlFor="interviewer-select" className="mb-1 block text-xs font-medium text-ink-muted">
                Interviewer
              </label>
              <select
                id="interviewer-select"
                value={selectedInterviewerId}
                onChange={(e) => setSelectedInterviewerId(e.target.value)}
                required
                className="w-full h-10 rounded-md border border-line bg-surface px-3 text-sm text-ink focus:border-accent-text focus:outline-none focus:ring-2 focus:ring-accent-text/25"
              >
                <option value="" disabled>
                  Select an interviewer
                </option>
                {interviewers.map((i) => (
                  <option key={i.id} value={i.id}>
                    {i.name} ({i.email})
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label htmlFor="interview-date" className="mb-1 block text-xs font-medium text-ink-muted">
                Date and start time
              </label>
              <input
                id="interview-date"
                type="datetime-local"
                value={scheduledStart}
                onChange={(e) => setScheduledStart(e.target.value)}
                required
                className="w-full h-10 rounded-md border border-line bg-surface px-3 text-sm text-ink focus:border-accent-text focus:outline-none focus:ring-2 focus:ring-accent-text/25"
              />
            </div>

            <div>
              <label htmlFor="interview-duration" className="mb-1 block text-xs font-medium text-ink-muted">
                Duration (minutes)
              </label>
              <select
                id="interview-duration"
                value={durationMinutes}
                onChange={(e) => setDurationMinutes(Number(e.target.value))}
                className="w-full h-10 rounded-md border border-line bg-surface px-3 text-sm text-ink focus:border-accent-text focus:outline-none focus:ring-2 focus:ring-accent-text/25"
              >
                <option value={15}>15 minutes</option>
                <option value={30}>30 minutes</option>
                <option value={45}>45 minutes</option>
                <option value={60}>60 minutes</option>
                <option value={90}>90 minutes</option>
                <option value={120}>120 minutes</option>
              </select>
            </div>
          </div>

          <div>
            <label htmlFor="interview-location" className="mb-1 block text-xs font-medium text-ink-muted">
              Location or meeting link (optional)
            </label>
            <input
              id="interview-location"
              type="text"
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              placeholder="e.g. Google Meet URL or Room 402"
              maxLength={500}
              className="w-full h-10 rounded-md border border-line bg-surface px-3 text-sm text-ink focus:border-accent-text focus:outline-none focus:ring-2 focus:ring-accent-text/25"
            />
          </div>

          <div>
            <label htmlFor="interview-note" className="mb-1 block text-xs font-medium text-ink-muted">
              Note to the interviewer (optional)
            </label>
            <textarea
              id="interview-note"
              value={noteToInterviewer}
              onChange={(e) => setNoteToInterviewer(e.target.value)}
              placeholder="Context or specific areas to focus on…"
              rows={2}
              maxLength={2000}
              className="w-full rounded-md border border-line bg-surface p-2.5 text-sm text-ink focus:border-accent-text focus:outline-none focus:ring-2 focus:ring-accent-text/25"
            />
          </div>

          <div className="flex justify-end gap-2 pt-2 border-t border-line">
            <Button
              type="button"
              variant="secondary"
              onClick={() => setShowScheduleForm(false)}
              disabled={submittingSchedule}
            >
              Cancel
            </Button>
            <Button type="submit" variant="primary" disabled={submittingSchedule || !selectedInterviewerId || !scheduledStart}>
              {submittingSchedule ? 'Scheduling…' : 'Schedule interview'}
            </Button>
          </div>
        </form>
      )}

      {/* Reschedule Form Modal/Inline */}
      {reschedulingInterview && (
        <form
          onSubmit={handleRescheduleSubmit}
          className="rounded-xl border border-line bg-sunken p-4 space-y-4 animate-fade-in"
        >
          <div className="flex items-center justify-between border-b border-line pb-2">
            <h3 className="text-sm font-semibold text-ink">
              Reschedule interview with {reschedulingInterview.interviewerName}
            </h3>
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => setReschedulingInterview(null)}
            >
              Cancel
            </Button>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label htmlFor="reschedule-date" className="mb-1 block text-xs font-medium text-ink-muted">
                New date and start time
              </label>
              <input
                id="reschedule-date"
                type="datetime-local"
                value={rescheduleStart}
                onChange={(e) => setRescheduleStart(e.target.value)}
                required
                className="w-full h-10 rounded-md border border-line bg-surface px-3 text-sm text-ink focus:border-accent-text focus:outline-none focus:ring-2 focus:ring-accent-text/25"
              />
            </div>

            <div>
              <label htmlFor="reschedule-duration" className="mb-1 block text-xs font-medium text-ink-muted">
                Duration (minutes)
              </label>
              <select
                id="reschedule-duration"
                value={rescheduleDuration}
                onChange={(e) => setRescheduleDuration(Number(e.target.value))}
                className="w-full h-10 rounded-md border border-line bg-surface px-3 text-sm text-ink focus:border-accent-text focus:outline-none focus:ring-2 focus:ring-accent-text/25"
              >
                <option value={15}>15 minutes</option>
                <option value={30}>30 minutes</option>
                <option value={45}>45 minutes</option>
                <option value={60}>60 minutes</option>
                <option value={90}>90 minutes</option>
                <option value={120}>120 minutes</option>
              </select>
            </div>
          </div>

          <div>
            <label htmlFor="reschedule-location" className="mb-1 block text-xs font-medium text-ink-muted">
              Location or meeting link (optional)
            </label>
            <input
              id="reschedule-location"
              type="text"
              value={rescheduleLocation}
              onChange={(e) => setRescheduleLocation(e.target.value)}
              maxLength={500}
              className="w-full h-10 rounded-md border border-line bg-surface px-3 text-sm text-ink focus:border-accent-text focus:outline-none focus:ring-2 focus:ring-accent-text/25"
            />
          </div>

          <div className="flex justify-end gap-2 pt-2 border-t border-line">
            <Button
              type="button"
              variant="secondary"
              onClick={() => setReschedulingInterview(null)}
              disabled={submittingReschedule}
            >
              Cancel
            </Button>
            <Button type="submit" variant="primary" disabled={submittingReschedule || !rescheduleStart}>
              {submittingReschedule ? 'Rescheduling…' : 'Reschedule'}
            </Button>
          </div>
        </form>
      )}

      {/* Interviews List */}
      <div className="space-y-3">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-muted">Interviews</h3>
        {interviews.length === 0 ? (
          <p className="text-sm text-ink-muted italic">No interviews scheduled yet.</p>
        ) : (
          <div className="space-y-3">
            {interviews.map((item) => {
              const isDone = item.status === 'COMPLETED';
              const isCancelled = item.status === 'CANCELLED';
              const isUpcoming = item.status === 'SCHEDULED';
              const isUrl = item.location && /^https?:\/\//i.test(item.location);

              return (
                <div
                  key={item.id}
                  className="lift rounded-xl p-4 bg-surface-raised border border-line-strong space-y-2.5 transition-all"
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span className="rounded-full bg-sunken px-2.5 py-0.5 text-xs font-semibold text-ink-muted">
                        {item.kind}
                      </span>
                      <span className="font-semibold text-ink text-sm">{item.interviewerName}</span>
                    </div>

                    <div className="flex items-center gap-2">
                      {isDone && item.recommendation && (
                        <span
                          className={cn(
                            'rounded-full px-2.5 py-0.5 text-xs font-semibold',
                            RECOMMENDATION_LABELS[item.recommendation]?.tone
                          )}
                        >
                          {RECOMMENDATION_LABELS[item.recommendation]?.label}
                        </span>
                      )}
                      {isDone && item.rating && (
                        <span
                          className="font-mono text-xs font-semibold text-ink"
                          aria-label={`${item.rating} out of 5 stars`}
                        >
                          {formatStars(item.rating)}
                        </span>
                      )}
                      {isCancelled && (
                        <span className="rounded-full bg-danger/15 px-2.5 py-0.5 text-xs font-semibold text-danger">
                          Cancelled
                        </span>
                      )}
                      {isUpcoming && (
                        <span className="rounded-full bg-info/15 px-2.5 py-0.5 text-xs font-semibold text-info">
                          Scheduled
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-ink-muted">
                    <span className="inline-flex items-center gap-1">
                      <Calendar className="size-3.5" aria-hidden="true" />
                      {formatTimeRange(item.scheduledStart, item.durationMinutes)}
                    </span>
                    {item.location && (
                      <span className="inline-flex items-center gap-1">
                        <MapPin className="size-3.5" aria-hidden="true" />
                        {isUrl ? (
                          <a
                            href={item.location}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-accent-text hover:underline"
                          >
                            Meeting link
                          </a>
                        ) : (
                          <span>{item.location}</span>
                        )}
                      </span>
                    )}
                  </div>

                  {isDone && (
                    <div className="mt-2 rounded-lg bg-surface p-3 text-xs border border-line">
                      <p className="font-medium text-ink-muted mb-1">
                        Submitted {formatRelativeDate(item.feedbackSubmittedAt || item.createdAt)}:
                      </p>
                      <p className="whitespace-pre-wrap text-ink text-sm">
                        {item.feedbackAssessment || 'No assessment written.'}
                      </p>
                    </div>
                  )}

                  {isUpcoming && (
                    <div className="flex items-center justify-between pt-1">
                      <span className="text-xs text-ink-muted">Waiting for the interview</span>
                      {canManage && (
                        <div className="flex items-center gap-2">
                          <Button
                            variant="secondary"
                            size="sm"
                            onClick={() => {
                              setReschedulingInterview(item);
                              setRescheduleStart(item.scheduledStart.slice(0, 16));
                              setRescheduleDuration(item.durationMinutes);
                              setRescheduleLocation(item.location || '');
                            }}
                          >
                            <Pencil className="size-3.5" aria-hidden="true" />
                            Reschedule
                          </Button>
                          <Button
                            variant="secondary"
                            size="sm"
                            className="text-danger hover:border-danger"
                            onClick={() => handleCancelInterview(item.id)}
                          >
                            <Trash2 className="size-3.5" aria-hidden="true" />
                            Cancel
                          </Button>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Offer Stage Card */}
      {(stageIdx >= STAGE_ORDER.OFFER || offer) && (
        <div className="space-y-3">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-muted">Job Offer</h3>

          {offer ? (
            <div className="lift rounded-xl p-4 bg-surface-raised border border-line-strong space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-3">
                  <div className="flex size-10 items-center justify-center rounded-lg bg-surface border border-line">
                    <FileText className="size-5 text-accent-text" aria-hidden="true" />
                  </div>
                  <div>
                    <h4 className="text-sm font-semibold text-ink">{offer.fileName}</h4>
                    <p className="text-xs text-ink-muted">
                      Uploaded by {offer.uploadedByName} · {formatRelativeDate(offer.uploadedAt)}
                    </p>
                  </div>
                </div>

                {offer.isApproved ? (
                  <Badge tone="success">
                    <CheckCircle2 className="size-3.5" aria-hidden="true" />
                    Offer approved
                  </Badge>
                ) : (
                  <Badge tone="warn">Waiting for approval</Badge>
                )}
              </div>

              {offer.isApproved && offer.approvedByName && (
                <p className="text-xs text-success">
                  Approved by {offer.approvedByName} · {formatRelativeDate(offer.approvedAt || '')}
                </p>
              )}

              <div className="flex flex-wrap items-center gap-2 pt-1 border-t border-line">
                <Button variant="secondary" size="sm" onClick={handleDownloadOffer}>
                  <Download className="size-3.5" aria-hidden="true" />
                  Download
                </Button>

                {!offer.isApproved && canManage && (
                  <label className="cursor-pointer">
                    <input
                      type="file"
                      accept="application/pdf"
                      onChange={handleFileUpload}
                      className="sr-only"
                      disabled={uploadingOffer}
                    />
                    <span className="inline-flex h-8 items-center gap-1.5 rounded-md border border-line-strong px-3 text-xs font-semibold text-ink hover:bg-surface-raised transition-colors">
                      <Upload className="size-3.5" aria-hidden="true" />
                      {uploadingOffer ? 'Replacing…' : 'Replace PDF'}
                    </span>
                  </label>
                )}

                {!offer.isApproved && canApprove && me?.id !== offer.uploadedById && (
                  <>
                    {confirmApprove ? (
                      <div className="flex items-center gap-2">
                        <span className="text-xs text-ink">
                          Approve the offer for {candidateName}? This can't be undone.
                        </span>
                        <Button
                          variant="primary"
                          size="sm"
                          onClick={handleApproveOffer}
                          disabled={approvingOffer}
                        >
                          {approvingOffer ? 'Approving…' : 'Confirm approve'}
                        </Button>
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => setConfirmApprove(false)}
                        >
                          Cancel
                        </Button>
                      </div>
                    ) : (
                      <Button
                        variant="primary"
                        size="sm"
                        onClick={() => setConfirmApprove(true)}
                        disabled={approvingOffer}
                      >
                        <CheckCircle2 className="size-3.5" aria-hidden="true" />
                        Approve offer
                      </Button>
                    )}
                  </>
                )}
              </div>
            </div>
          ) : (
            canManage && (
              <div className="rounded-xl border border-dashed border-line-strong p-6 text-center space-y-2">
                <FileText className="mx-auto size-8 text-ink-muted" aria-hidden="true" />
                <h4 className="text-sm font-semibold text-ink">No offer letter uploaded yet</h4>
                <p className="text-xs text-ink-muted max-w-sm mx-auto">
                  Upload a PDF offer letter (up to 10 MB) to share with the team and send for approval.
                </p>
                <div className="pt-2">
                  <label className="cursor-pointer">
                    <input
                      type="file"
                      accept="application/pdf"
                      onChange={handleFileUpload}
                      className="sr-only"
                      disabled={uploadingOffer}
                    />
                    <span className="inline-flex h-9 items-center gap-2 rounded-md bg-accent px-4 text-xs font-semibold text-accent-ink hover:opacity-90 transition-opacity">
                      <Upload className="size-4" aria-hidden="true" />
                      {uploadingOffer ? 'Uploading…' : 'Upload offer PDF'}
                    </span>
                  </label>
                </div>
              </div>
            )
          )}
        </div>
      )}

      {/* Stage Timeline */}
      <div className="space-y-3 pt-2 border-t border-line">
        <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-muted">Timeline</h3>
        {timeline.length === 0 ? (
          <p className="text-sm text-ink-muted italic">No stage events recorded yet.</p>
        ) : (
          <ol className="relative ml-2 space-y-4 border-l-2 border-line pl-4">
            {timeline.map((evt) => {
              const isShortlisted = evt.stage === 'SHORTLISTED';
              const stageLabel =
                STAGES.find((s) => s.key === evt.stage)?.label ?? EVENT_LABEL[evt.stage] ?? evt.stage;

              return (
                <li key={evt.id} className="relative">
                  <span
                    aria-hidden="true"
                    className={cn(
                      'absolute -left-[21px] top-1 size-2 rounded-full',
                      isShortlisted ? 'bg-mark' : 'bg-ink-muted'
                    )}
                  />
                  <div className="text-xs">
                    <span className={cn('font-semibold', isShortlisted ? 'text-mark-text' : 'text-ink')}>
                      {stageLabel}
                    </span>
                    <span className="text-ink-muted">
                      {' '}
                      · {evt.summary || 'Stage updated'}
                      {evt.actorName && !evt.summary?.includes(evt.actorName) ? `, by ${evt.actorName}` : ''} ·{' '}
                      {formatRelativeDate(evt.createdAt)}
                    </span>
                  </div>
                </li>
              );
            })}
          </ol>
        )}
      </div>
    </Panel>
  );
}
