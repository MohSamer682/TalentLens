// Everything about one candidate after the shortlist: interview plan, interviews, offer and joining.
import { useEffect, useState } from 'react';
import {
  Calendar,
  CheckCircle2,
  Download,
  FileText,
  Pencil,
  Plus,
  Trash2,
  Upload,
  UserCheck,
  XCircle,
} from 'lucide-react';
import { apiErrorMessage, pipelineApi } from '@/api/client';
import { useAuthStore } from '@/store/authStore';
import { hasPermission } from '@/lib/permissions';
import type {
  CandidateInterview,
  CandidateOffer,
  CandidateStageEvent,
  InterviewKind,
  InterviewPlaceType,
  InterviewRecommendation,
  ReviewStatus,
  Role,
} from '@/types';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Panel } from '@/components/ui/Card';
import { Alert, Loader } from '@/components/ui/Feedback';
import { Input, Textarea } from '@/components/ui/Input';
import { Segmented } from '@/components/ui/Segmented';
import { InterviewPlaceText, PlaceFields, placeError } from '@/components/InterviewPlace';
import { StageBadge } from '@/components/StageBadge';
import { KIND_LABEL, KIND_ORDER, STAGE_LABEL, STAGE_ORDER, buildTrack, settledStage } from '@/lib/pipeline';
import { ROLE_LABEL, cn, formatDate, formatRelativeDate } from '@/lib/utils';

const RECOMMENDATION_LABELS: Record<InterviewRecommendation, { label: string; tone: string }> = {
  STRONG_HIRE: { label: 'Strong hire', tone: 'bg-success/15 text-success' },
  HIRE: { label: 'Hire', tone: 'bg-success/15 text-success' },
  NO_HIRE: { label: 'No hire', tone: 'bg-warn/15 text-warn' },
  STRONG_NO_HIRE: { label: 'Strong no hire', tone: 'bg-danger/15 text-danger' },
};

const selectClass =
  'w-full h-10 rounded-md border border-line bg-surface dark:bg-sunken px-3 text-sm text-ink hover:border-line-strong focus:border-accent-text focus:outline-none focus:ring-2 focus:ring-accent-text/25';

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

/** datetime-local wants the viewer's local clock, not UTC. */
function toLocalInput(iso: string): string {
  const d = new Date(iso);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

function eventLabel(stage: string): string {
  if (stage in STAGE_LABEL) return STAGE_LABEL[stage as ReviewStatus];
  const words = stage.toLowerCase().replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

type Interviewer = { id: string; name: string; email: string; role: Role };

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
  const [plan, setPlan] = useState<InterviewKind[]>([]);
  const [planDraft, setPlanDraft] = useState<InterviewKind[]>([]);
  const [savingPlan, setSavingPlan] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Scheduling form state
  const [showScheduleForm, setShowScheduleForm] = useState(false);
  const [scheduleKind, setScheduleKind] = useState<InterviewKind>('HR');
  const [interviewers, setInterviewers] = useState<Interviewer[]>([]);
  const [selectedInterviewerId, setSelectedInterviewerId] = useState('');
  const [scheduledStart, setScheduledStart] = useState('');
  const [durationMinutes, setDurationMinutes] = useState(60);
  const [placeType, setPlaceType] = useState<InterviewPlaceType>('HQ');
  const [location, setLocation] = useState('');
  const [noteToInterviewer, setNoteToInterviewer] = useState('');
  const [submittingSchedule, setSubmittingSchedule] = useState(false);

  // Rescheduling state
  const [reschedulingInterview, setReschedulingInterview] = useState<CandidateInterview | null>(null);
  const [rescheduleStart, setRescheduleStart] = useState('');
  const [rescheduleDuration, setRescheduleDuration] = useState(60);
  const [reschedulePlaceType, setReschedulePlaceType] = useState<InterviewPlaceType>('HQ');
  const [rescheduleLocation, setRescheduleLocation] = useState('');
  const [submittingReschedule, setSubmittingReschedule] = useState(false);

  // Offer
  const [requireDirector, setRequireDirector] = useState(false);
  const [uploadingOffer, setUploadingOffer] = useState(false);
  const [approvingOffer, setApprovingOffer] = useState(false);
  const [confirmApprove, setConfirmApprove] = useState(false);
  const [rejecting, setRejecting] = useState(false);
  const [rejectNote, setRejectNote] = useState('');
  const [submittingReject, setSubmittingReject] = useState(false);

  // After the offer
  const [busy, setBusy] = useState(false);
  const [joiningDate, setJoiningDate] = useState('');
  const [joiningNotes, setJoiningNotes] = useState('');

  const reloadData = async () => {
    try {
      const [tlRes, intRes, offRes, planRes] = await Promise.all([
        pipelineApi.getTimeline(sessionId, candidateId),
        pipelineApi.getInterviews(sessionId, candidateId),
        pipelineApi.getOffer(sessionId, candidateId).catch(() => ({ data: null })),
        pipelineApi.getInterviewPlan(sessionId, candidateId).catch(() => ({ data: [] as InterviewKind[] })),
      ]);
      setTimeline(tlRes.data);
      setInterviews(intRes.data);
      setOffer(offRes.data);
      setPlan(planRes.data);
      setPlanDraft(planRes.data);
      setJoiningDate(offRes.data?.joiningDate ?? '');
      setJoiningNotes(offRes.data?.joiningNotes ?? '');
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not load pipeline details'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    reloadData();
  }, [sessionId, candidateId]);

  // Interview kinds the recruiter may schedule: the plan, or all three for a candidate planned before plans existed
  const schedulableKinds = KIND_ORDER.filter((k) => plan.length === 0 || plan.includes(k));

  // Load interviewers when the form opens or the kind changes
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

  const run = async (action: () => Promise<void>, fallback: string) => {
    setError(null);
    setBusy(true);
    try {
      await action();
    } catch (err) {
      setError(apiErrorMessage(err, fallback));
    } finally {
      setBusy(false);
    }
  };

  const handleMarkContacted = () =>
    run(async () => {
      await pipelineApi.markContacted(sessionId, candidateId);
      onStageChange?.('CONTACTED');
      await reloadData();
    }, 'Could not mark as contacted');

  const planDirty = planDraft.length !== plan.length || planDraft.some((k) => !plan.includes(k));
  const togglePlanKind = (kind: InterviewKind) =>
    setPlanDraft((d) => (d.includes(kind) ? d.filter((k) => k !== kind) : [...d, kind]));

  const handleSavePlan = async () => {
    setError(null);
    setSavingPlan(true);
    try {
      const res = await pipelineApi.setInterviewPlan(sessionId, candidateId, planDraft);
      setPlan(res.data);
      setPlanDraft(res.data);
      // The server moves a candidate on when every planned interview is already held
      const next = settledStage(currentStage, res.data, interviews);
      if (next !== currentStage) onStageChange?.(next);
      await reloadData();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save the interview plan'));
    } finally {
      setSavingPlan(false);
    }
  };

  const openScheduleForm = () => {
    const first = schedulableKinds.find((k) => !interviews.some((i) => i.kind === k && i.status !== 'CANCELLED'));
    setScheduleKind(first ?? schedulableKinds[0] ?? 'HR');
    setSelectedInterviewerId('');
    setShowScheduleForm(true);
  };

  const scheduleProblem = placeError(placeType, location);

  const handleScheduleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedInterviewerId || !scheduledStart || scheduleProblem) return;
    setError(null);
    setSubmittingSchedule(true);
    try {
      await pipelineApi.scheduleInterview(sessionId, candidateId, {
        kind: scheduleKind,
        interviewerId: selectedInterviewerId,
        scheduledStart: new Date(scheduledStart).toISOString(),
        durationMinutes,
        placeType,
        location: placeType === 'HQ' ? undefined : location.trim(),
        noteToInterviewer: noteToInterviewer.trim() || undefined,
      });
      setShowScheduleForm(false);
      setScheduledStart('');
      setPlaceType('HQ');
      setLocation('');
      setNoteToInterviewer('');
      if (STAGE_ORDER[currentStage] < STAGE_ORDER.INTERVIEW_SCHEDULED || currentStage === 'INTERVIEWED') {
        onStageChange?.('INTERVIEW_SCHEDULED');
      }
      await reloadData();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not schedule interview'));
    } finally {
      setSubmittingSchedule(false);
    }
  };

  const rescheduleProblem = placeError(reschedulePlaceType, rescheduleLocation);

  const handleRescheduleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reschedulingInterview || !rescheduleStart || rescheduleProblem) return;
    setError(null);
    setSubmittingReschedule(true);
    try {
      await pipelineApi.rescheduleInterview(sessionId, candidateId, reschedulingInterview.id, {
        scheduledStart: new Date(rescheduleStart).toISOString(),
        durationMinutes: rescheduleDuration,
        placeType: reschedulePlaceType,
        location: reschedulePlaceType === 'HQ' ? undefined : rescheduleLocation.trim(),
      });
      setReschedulingInterview(null);
      await reloadData();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not reschedule interview'));
    } finally {
      setSubmittingReschedule(false);
    }
  };

  const handleCancelInterview = async (interviewId: string) => {
    if (!window.confirm('Cancel this interview?')) return;
    await run(async () => {
      await pipelineApi.cancelInterview(sessionId, candidateId, interviewId);
      const remaining = interviews.map((i) => (i.id === interviewId ? { ...i, status: 'CANCELLED' as const } : i));
      const next = settledStage(currentStage, plan, remaining);
      if (next !== currentStage) onStageChange?.(next);
      await reloadData();
    }, 'Could not cancel interview');
  };

  const handleMoveToOffer = () =>
    run(async () => {
      await pipelineApi.moveToOffer(sessionId, candidateId);
      onStageChange?.('OFFER');
      await reloadData();
    }, 'Could not move to offer stage');

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
      const res = await pipelineApi.uploadOffer(sessionId, candidateId, file, requireDirector);
      setOffer(res.data);
      await reloadData();
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
      if (res.data.isApproved) onStageChange?.('OFFER_APPROVED');
      await reloadData();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not approve offer'));
    } finally {
      setApprovingOffer(false);
    }
  };

  const handleRejectOffer = async () => {
    setError(null);
    setSubmittingReject(true);
    try {
      const res = await pipelineApi.rejectOffer(sessionId, candidateId, rejectNote.trim() || undefined);
      setOffer(res.data);
      setRejecting(false);
      setRejectNote('');
      await reloadData();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not reject the offer'));
    } finally {
      setSubmittingReject(false);
    }
  };

  const handleOfferSent = () =>
    run(async () => {
      const res = await pipelineApi.markOfferSent(sessionId, candidateId);
      setOffer(res.data);
      onStageChange?.('OFFER_SENT');
      await reloadData();
    }, 'Could not mark the offer as sent');

  const handleAnswer = (accepted: boolean) =>
    run(async () => {
      const res = await pipelineApi.answerOffer(sessionId, candidateId, accepted);
      setOffer(res.data);
      onStageChange?.(accepted ? 'ACCEPTED' : 'DECLINED');
      await reloadData();
    }, 'Could not record the candidate’s answer');

  const handleSaveJoining = (e: React.FormEvent) => {
    e.preventDefault();
    if (!joiningDate) return;
    return run(async () => {
      const res = await pipelineApi.saveJoining(sessionId, candidateId, joiningDate, joiningNotes.trim() || undefined);
      setOffer(res.data);
      onStageChange?.('JOINING_SET');
      await reloadData();
    }, 'Could not save the joining details');
  };

  const stageIdx = STAGE_ORDER[currentStage] ?? 0;
  const completedCount = interviews.filter((i) => i.status === 'COMPLETED').length;
  const totalInterviews = interviews.filter((i) => i.status !== 'CANCELLED').length;
  const track = buildTrack(currentStage, plan, interviews);

  const interviewing = stageIdx >= STAGE_ORDER.CONTACTED && stageIdx < STAGE_ORDER.OFFER;
  const planHint = interviewing && plan.length === 0 && interviews.length === 0;
  const stageProgressLabel =
    currentStage === 'INTERVIEW_SCHEDULED' || currentStage === 'INTERVIEWED'
      ? `Interviewing · ${completedCount} of ${totalInterviews || plan.length} done`
      : null;

  // The offer step that is open, and whether this viewer holds it
  const directorOpen = !!offer && offer.requiresDirectorApproval && !offer.directorApprovedAt;
  const rejected = !!offer?.rejectedAt;
  const myStepOpen =
    canApprove &&
    !!offer &&
    currentStage === 'OFFER' &&
    !offer.isApproved &&
    !rejected &&
    me?.id !== offer.uploadedById &&
    (me?.role === 'ADMIN' || (directorOpen ? me?.role === 'DEPARTMENT_DIRECTOR' : me?.role === 'HR_CHIEF'));

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
        <h2 className="text-[15px] font-semibold text-ink">Hiring pipeline</h2>
        {stageProgressLabel ? <Badge tone="info">{stageProgressLabel}</Badge> : <StageBadge stage={currentStage} />}
      </div>

      {error && <Alert>{error}</Alert>}

      {/* Status line: follows the interviews the recruiter planned */}
      <nav aria-label="Pipeline stages" className="overflow-x-auto pb-1">
        <ol className="flex min-w-max items-center gap-2">
          {track.map((s, idx) => (
            <li key={s.key} className="flex items-center">
              <span
                className={cn(
                  'inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium transition-colors',
                  s.state === 'done' && 'bg-sunken font-semibold text-ink',
                  s.state === 'current' && 'border border-accent-text bg-sunken font-semibold text-ink ring-1 ring-accent-text/20',
                  s.state === 'todo' && 'border border-dashed border-line-strong text-ink-muted'
                )}
                {...(s.state === 'current' ? { 'aria-current': 'step' } : {})}
              >
                <span
                  aria-hidden="true"
                  className={cn(
                    'size-1.5 shrink-0 rounded-full',
                    s.key === 'SHORTLISTED' ? 'bg-mark' : s.state === 'current' ? 'bg-accent-bar' : s.state === 'done' ? 'bg-ink-muted' : 'bg-transparent'
                  )}
                />
                {s.label}
              </span>
              {idx < track.length - 1 && (
                <span aria-hidden="true" className="mx-1 text-xs text-ink-muted/50">
                  ›
                </span>
              )}
            </li>
          ))}
        </ol>
      </nav>

      {/* Interview plan */}
      {interviewing && (
        <div className="space-y-3">
          <h3 className="text-[15px] font-semibold text-ink">Interviews for this candidate</h3>
          {canManage ? (
            <>
              <div role="group" aria-label="Interviews this candidate will take" className="flex flex-wrap gap-2">
                {KIND_ORDER.map((kind) => {
                  const checked = planDraft.includes(kind);
                  const locked = interviews.some((i) => i.kind === kind && i.status !== 'CANCELLED');
                  return (
                    <label
                      key={kind}
                      className={cn(
                        'inline-flex h-10 cursor-pointer items-center gap-2 rounded-md border px-3 text-sm font-semibold transition-colors',
                        checked ? 'border-ink bg-ink text-ground' : 'border-line-strong text-ink hover:bg-sunken',
                        locked && 'cursor-not-allowed opacity-70'
                      )}
                    >
                      <input
                        type="checkbox"
                        className="size-4 accent-[rgb(var(--accent-text))]"
                        checked={checked}
                        disabled={locked}
                        onChange={() => togglePlanKind(kind)}
                      />
                      {KIND_LABEL[kind]}
                    </label>
                  );
                })}
                <Button onClick={handleSavePlan} disabled={!planDirty || planDraft.length === 0 || savingPlan} variant="secondary">
                  {savingPlan ? 'Saving…' : 'Save plan'}
                </Button>
              </div>
              <p className="text-[13px] text-ink-muted">
                {planHint
                  ? 'Choose the interviews this candidate will take. The status line above shows only those.'
                  : 'An interview that is already scheduled stays in the plan until it is cancelled.'}
              </p>
            </>
          ) : (
            <p className="text-sm text-ink-muted">
              {plan.length > 0 ? plan.map((k) => KIND_LABEL[k]).join(', ') : 'Not chosen yet.'}
            </p>
          )}
        </div>
      )}

      {/* Next Action Primary Button / Controls */}
      {canManage && (
        <div className="flex flex-wrap items-center gap-3">
          {currentStage === 'SHORTLISTED' && (
            <Button variant="primary" onClick={handleMarkContacted} disabled={busy}>
              <UserCheck className="size-4" aria-hidden="true" />
              Mark as contacted
            </Button>
          )}

          {interviewing && !showScheduleForm && (
            <Button
              variant={currentStage === 'CONTACTED' || currentStage === 'INTERVIEW_SCHEDULED' ? 'primary' : 'secondary'}
              onClick={openScheduleForm}
              disabled={planHint || planDirty}
              title={planHint ? 'Choose the interviews first' : planDirty ? 'Save the plan first' : undefined}
            >
              <Plus className="size-4" aria-hidden="true" />
              Schedule interview
            </Button>
          )}

          {currentStage === 'INTERVIEWED' && (
            <Button variant="primary" onClick={handleMoveToOffer} disabled={busy}>
              Move to offer
            </Button>
          )}
        </div>
      )}

      {/* Inline Schedule Form */}
      {showScheduleForm && (
        <form onSubmit={handleScheduleSubmit} className="space-y-4 rounded-xl border border-line bg-sunken p-4 animate-fade-in">
          <div className="flex items-center justify-between border-b border-line pb-2">
            <h3 className="text-sm font-semibold text-ink">Schedule new interview</h3>
            <Button type="button" variant="secondary" size="sm" onClick={() => setShowScheduleForm(false)}>
              Cancel
            </Button>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <span className="block text-sm font-semibold">Interview</span>
              <Segmented
                label="Interview kind"
                value={scheduleKind}
                onChange={(val) => {
                  setScheduleKind(val as InterviewKind);
                  setSelectedInterviewerId('');
                }}
                options={schedulableKinds.map((k) => ({ value: k, label: KIND_LABEL[k] }))}
              />
            </div>

            <div className="space-y-1.5">
              <label htmlFor="interviewer-select" className="block text-sm font-semibold">
                {scheduleKind === 'MANAGEMENT' ? 'Top management interviewer' : 'Interviewer'}
              </label>
              <select
                id="interviewer-select"
                value={selectedInterviewerId}
                onChange={(e) => setSelectedInterviewerId(e.target.value)}
                required
                className={selectClass}
              >
                <option value="" disabled>
                  {scheduleKind === 'MANAGEMENT' ? 'Choose a person' : 'Select an interviewer'}
                </option>
                {interviewers.map((i) => (
                  <option key={i.id} value={i.id}>
                    {scheduleKind === 'MANAGEMENT' ? `${i.name} · ${ROLE_LABEL[i.role] ?? i.role}` : `${i.name} (${i.email})`}
                  </option>
                ))}
              </select>
              {interviewers.length === 0 && (
                <p className="text-[13px] text-warn">
                  No one holds this role yet. An admin can assign it on the Users page.
                </p>
              )}
            </div>

            <div className="space-y-1.5">
              <label htmlFor="interview-date" className="block text-sm font-semibold">
                Date and start time
              </label>
              <Input id="interview-date" type="datetime-local" value={scheduledStart} onChange={(e) => setScheduledStart(e.target.value)} required />
            </div>

            <div className="space-y-1.5">
              <label htmlFor="interview-duration" className="block text-sm font-semibold">
                Duration
              </label>
              <select id="interview-duration" value={durationMinutes} onChange={(e) => setDurationMinutes(Number(e.target.value))} className={selectClass}>
                {[15, 30, 45, 60, 90, 120].map((m) => (
                  <option key={m} value={m}>
                    {m} minutes
                  </option>
                ))}
              </select>
            </div>
          </div>

          <PlaceFields
            idPrefix="schedule"
            placeType={placeType}
            location={location}
            onChange={(t, l) => {
              setPlaceType(t);
              setLocation(l);
            }}
          />

          <div className="space-y-1.5">
            <label htmlFor="interview-note" className="block text-sm font-semibold">
              Note to the interviewer <span className="font-normal text-ink-muted">(optional)</span>
            </label>
            <Textarea
              id="interview-note"
              value={noteToInterviewer}
              onChange={(e) => setNoteToInterviewer(e.target.value)}
              placeholder="Context or specific areas to focus on…"
              rows={2}
              maxLength={2000}
              className="min-h-[64px]"
            />
          </div>

          <div className="flex justify-end gap-2 border-t border-line pt-3">
            <Button type="button" variant="secondary" onClick={() => setShowScheduleForm(false)} disabled={submittingSchedule}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" disabled={submittingSchedule || !selectedInterviewerId || !scheduledStart || !!scheduleProblem}>
              {submittingSchedule ? 'Scheduling…' : 'Schedule interview'}
            </Button>
          </div>
        </form>
      )}

      {/* Reschedule */}
      {reschedulingInterview && (
        <form onSubmit={handleRescheduleSubmit} className="space-y-4 rounded-xl border border-line bg-sunken p-4 animate-fade-in">
          <div className="flex items-center justify-between border-b border-line pb-2">
            <h3 className="text-sm font-semibold text-ink">Reschedule interview with {reschedulingInterview.interviewerName}</h3>
            <Button type="button" variant="secondary" size="sm" onClick={() => setReschedulingInterview(null)}>
              Cancel
            </Button>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <label htmlFor="reschedule-date" className="block text-sm font-semibold">
                New date and start time
              </label>
              <Input id="reschedule-date" type="datetime-local" value={rescheduleStart} onChange={(e) => setRescheduleStart(e.target.value)} required />
            </div>
            <div className="space-y-1.5">
              <label htmlFor="reschedule-duration" className="block text-sm font-semibold">
                Duration
              </label>
              <select id="reschedule-duration" value={rescheduleDuration} onChange={(e) => setRescheduleDuration(Number(e.target.value))} className={selectClass}>
                {[15, 30, 45, 60, 90, 120].map((m) => (
                  <option key={m} value={m}>
                    {m} minutes
                  </option>
                ))}
              </select>
            </div>
          </div>

          <PlaceFields
            idPrefix="reschedule"
            placeType={reschedulePlaceType}
            location={rescheduleLocation}
            onChange={(t, l) => {
              setReschedulePlaceType(t);
              setRescheduleLocation(l);
            }}
          />

          <div className="flex justify-end gap-2 border-t border-line pt-3">
            <Button type="button" variant="secondary" onClick={() => setReschedulingInterview(null)} disabled={submittingReschedule}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" disabled={submittingReschedule || !rescheduleStart || !!rescheduleProblem}>
              {submittingReschedule ? 'Rescheduling…' : 'Reschedule'}
            </Button>
          </div>
        </form>
      )}

      {/* Interviews List */}
      <div className="space-y-3">
        <h3 className="text-[15px] font-semibold text-ink">Interviews</h3>
        {interviews.length === 0 ? (
          <p className="text-sm italic text-ink-muted">No interviews scheduled yet.</p>
        ) : (
          <div className="space-y-3">
            {interviews.map((item) => {
              const isDone = item.status === 'COMPLETED';
              const isCancelled = item.status === 'CANCELLED';
              const isUpcoming = item.status === 'SCHEDULED';

              return (
                <div key={item.id} className="lift space-y-2.5 rounded-xl border border-line-strong bg-surface-raised p-4 transition-all">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span className="rounded-full bg-sunken px-2.5 py-0.5 text-xs font-semibold text-ink-muted">
                        {KIND_LABEL[item.kind] ?? item.kind}
                      </span>
                      <span className="text-sm font-semibold text-ink">{item.interviewerName}</span>
                      {item.interviewerRole && item.kind === 'MANAGEMENT' && (
                        <span className="text-xs text-ink-muted">{ROLE_LABEL[item.interviewerRole as Role] ?? item.interviewerRole}</span>
                      )}
                    </div>

                    <div className="flex items-center gap-2">
                      {isDone && item.recommendation && (
                        <span className={cn('rounded-full px-2.5 py-0.5 text-xs font-semibold', RECOMMENDATION_LABELS[item.recommendation]?.tone)}>
                          {RECOMMENDATION_LABELS[item.recommendation]?.label}
                        </span>
                      )}
                      {isDone && item.rating && (
                        <span className="font-mono text-xs font-semibold text-ink" aria-label={`${item.rating} out of 5 stars`}>
                          {formatStars(item.rating)}
                        </span>
                      )}
                      {isCancelled && <span className="rounded-full bg-danger/15 px-2.5 py-0.5 text-xs font-semibold text-danger">Cancelled</span>}
                      {isUpcoming && <span className="rounded-full bg-info/15 px-2.5 py-0.5 text-xs font-semibold text-info">Scheduled</span>}
                    </div>
                  </div>

                  <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-ink-muted">
                    <span className="inline-flex items-center gap-1">
                      <Calendar className="size-3.5" aria-hidden="true" />
                      {formatTimeRange(item.scheduledStart, item.durationMinutes)}
                    </span>
                    <InterviewPlaceText placeType={item.placeType} location={item.location} />
                  </div>

                  {isDone && (
                    <div className="mt-2 rounded-lg border border-line bg-surface p-3 text-xs">
                      <p className="mb-1 font-medium text-ink-muted">Submitted {formatRelativeDate(item.feedbackSubmittedAt || item.createdAt)}:</p>
                      <p className="whitespace-pre-wrap text-sm text-ink">{item.feedbackAssessment || 'No assessment written.'}</p>
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
                              setRescheduleStart(toLocalInput(item.scheduledStart));
                              setRescheduleDuration(item.durationMinutes);
                              setReschedulePlaceType(item.placeType ?? 'HQ');
                              setRescheduleLocation(item.location || '');
                            }}
                          >
                            <Pencil className="size-3.5" aria-hidden="true" />
                            Reschedule
                          </Button>
                          <Button variant="secondary" size="sm" className="text-danger hover:border-danger" onClick={() => handleCancelInterview(item.id)}>
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

      {/* Offer */}
      {(stageIdx >= STAGE_ORDER.OFFER || offer) && (
        <div className="space-y-3">
          <h3 className="text-[15px] font-semibold text-ink">Job offer</h3>

          {offer ? (
            <div className="lift space-y-3 rounded-xl border border-line-strong bg-surface-raised p-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-3">
                  <div className="flex size-10 items-center justify-center rounded-lg border border-line bg-surface">
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
                ) : rejected ? (
                  <Badge tone="danger">Rejected</Badge>
                ) : (
                  <Badge tone="warn">{directorOpen ? 'Waiting for the department director' : 'Waiting for the HR chief'}</Badge>
                )}
              </div>

              {/* Who has to sign, in order */}
              <ol className="space-y-1.5 text-sm" aria-label="Approvals">
                {offer.requiresDirectorApproval && (
                  <li className="flex items-center gap-2">
                    <span
                      aria-hidden="true"
                      className={cn('size-2 rounded-full', offer.directorApprovedAt ? 'bg-success' : 'border border-line-strong')}
                    />
                    <span className="text-ink">Department director</span>
                    <span className="text-ink-muted">
                      {offer.directorApprovedAt
                        ? `${offer.directorApprovedByName} · ${formatRelativeDate(offer.directorApprovedAt)}`
                        : 'waiting'}
                    </span>
                  </li>
                )}
                <li className="flex items-center gap-2">
                  <span aria-hidden="true" className={cn('size-2 rounded-full', offer.isApproved ? 'bg-success' : 'border border-line-strong')} />
                  <span className="text-ink">HR chief</span>
                  <span className="text-ink-muted">
                    {offer.isApproved
                      ? `${offer.approvedByName} · ${formatRelativeDate(offer.approvedAt || '')}`
                      : directorOpen
                      ? 'after the director'
                      : 'waiting'}
                  </span>
                </li>
              </ol>

              {rejected && (
                <div className="rounded-lg border border-danger/40 bg-danger-soft p-3 text-sm" role="status">
                  <p className="font-semibold text-danger">
                    Rejected by {offer.rejectedByName} · {formatRelativeDate(offer.rejectedAt || '')}
                  </p>
                  <p className="mt-1 whitespace-pre-wrap text-ink">{offer.rejectionNote || 'No reason given.'}</p>
                  {canManage && <p className="mt-1 text-ink-muted">Upload a revised offer to start the approvals again.</p>}
                </div>
              )}

              <div className="flex flex-wrap items-center gap-2 border-t border-line pt-3">
                <Button variant="secondary" size="sm" onClick={handleDownloadOffer}>
                  <Download className="size-3.5" aria-hidden="true" />
                  Download
                </Button>

                {!offer.isApproved && canManage && currentStage === 'OFFER' && (
                  <>
                    <label className="inline-flex items-center gap-2 text-xs text-ink">
                      <input
                        type="checkbox"
                        className="size-4 accent-[rgb(var(--accent-text))]"
                        checked={requireDirector}
                        onChange={(e) => setRequireDirector(e.target.checked)}
                      />
                      Department director approves first
                    </label>
                    <label className="cursor-pointer">
                      <input type="file" accept="application/pdf" onChange={handleFileUpload} className="sr-only" disabled={uploadingOffer} />
                      <span className="inline-flex h-8 items-center gap-1.5 rounded-md border border-line-strong px-3 text-[13px] font-semibold text-ink transition-colors hover:bg-sunken">
                        <Upload className="size-3.5" aria-hidden="true" />
                        {uploadingOffer ? 'Uploading…' : rejected ? 'Upload revised PDF' : 'Replace PDF'}
                      </span>
                    </label>
                  </>
                )}

                {myStepOpen && (
                  <>
                    {confirmApprove ? (
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-xs text-ink">Approve the offer for {candidateName}? This can't be undone.</span>
                        <Button variant="primary" size="sm" onClick={handleApproveOffer} disabled={approvingOffer}>
                          {approvingOffer ? 'Approving…' : 'Confirm approve'}
                        </Button>
                        <Button variant="secondary" size="sm" onClick={() => setConfirmApprove(false)}>
                          Cancel
                        </Button>
                      </div>
                    ) : (
                      <>
                        <Button variant="primary" size="sm" onClick={() => setConfirmApprove(true)} disabled={approvingOffer}>
                          <CheckCircle2 className="size-3.5" aria-hidden="true" />
                          {directorOpen ? 'Approve as director' : 'Approve offer'}
                        </Button>
                        <Button variant="danger" size="sm" onClick={() => setRejecting((r) => !r)} aria-expanded={rejecting}>
                          <XCircle className="size-3.5" aria-hidden="true" />
                          Reject
                        </Button>
                      </>
                    )}
                  </>
                )}
              </div>

              {rejecting && myStepOpen && (
                <div className="space-y-2 border-t border-line pt-3">
                  <label htmlFor="reject-note" className="block text-sm font-semibold text-ink">
                    Why are you rejecting it? <span className="font-normal text-ink-muted">(optional)</span>
                  </label>
                  <Textarea
                    id="reject-note"
                    rows={2}
                    maxLength={1000}
                    value={rejectNote}
                    onChange={(e) => setRejectNote(e.target.value)}
                    placeholder="Tell the recruiter what to change. You can leave this empty."
                    className="min-h-[64px]"
                  />
                  <div className="flex gap-2">
                    <Button variant="dangerSolid" size="sm" onClick={handleRejectOffer} disabled={submittingReject}>
                      {submittingReject ? 'Rejecting…' : 'Reject offer'}
                    </Button>
                    <Button variant="secondary" size="sm" onClick={() => setRejecting(false)} disabled={submittingReject}>
                      Keep reviewing
                    </Button>
                  </div>
                </div>
              )}
            </div>
          ) : (
            canManage &&
            currentStage === 'OFFER' && (
              <div className="space-y-3 rounded-xl border border-dashed border-line-strong p-6 text-center">
                <FileText className="mx-auto size-8 text-ink-muted" aria-hidden="true" />
                <h4 className="text-sm font-semibold text-ink">No offer letter uploaded yet</h4>
                <p className="mx-auto max-w-sm text-xs text-ink-muted">
                  Upload a PDF offer letter (up to 10 MB). The HR chief approves it. Tick the box if the department director must sign first.
                </p>
                <label className="mx-auto inline-flex items-center gap-2 text-sm text-ink">
                  <input
                    type="checkbox"
                    className="size-4 accent-[rgb(var(--accent-text))]"
                    checked={requireDirector}
                    onChange={(e) => setRequireDirector(e.target.checked)}
                  />
                  Department director approves first
                </label>
                <div>
                  <label className="cursor-pointer">
                    <input type="file" accept="application/pdf" onChange={handleFileUpload} className="sr-only" disabled={uploadingOffer} />
                    <span className="inline-flex h-9 items-center gap-2 rounded-md bg-accent px-4 text-xs font-semibold text-accent-ink transition-opacity hover:opacity-90">
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

      {/* After the approvals: sending the offer and joining */}
      {stageIdx >= STAGE_ORDER.OFFER_APPROVED && offer && (
        <div className="space-y-3">
          <h3 className="text-[15px] font-semibold text-ink">Offer and joining</h3>

          <div className="lift space-y-4 rounded-xl border border-line-strong bg-surface-raised p-4">
            {currentStage === 'OFFER_APPROVED' && (
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-sm text-ink">The offer is approved. Send it to {candidateName}, then record the answer.</p>
                {canManage && (
                  <Button variant="primary" onClick={handleOfferSent} disabled={busy}>
                    Mark offer as sent
                  </Button>
                )}
              </div>
            )}

            {currentStage === 'OFFER_SENT' && (
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-sm text-ink">
                  Offer sent {offer.sentAt ? formatRelativeDate(offer.sentAt).toLowerCase() : ''}. What did {candidateName} answer?
                </p>
                {canManage && (
                  <div className="flex gap-2">
                    <Button variant="primary" onClick={() => handleAnswer(true)} disabled={busy}>
                      Accepted
                    </Button>
                    <Button variant="secondary" onClick={() => handleAnswer(false)} disabled={busy}>
                      Declined
                    </Button>
                  </div>
                )}
              </div>
            )}

            {currentStage === 'DECLINED' && (
              <p className="text-sm text-ink">
                {candidateName} declined the offer{offer.respondedAt ? ` ${formatRelativeDate(offer.respondedAt).toLowerCase()}` : ''}.
              </p>
            )}

            {(currentStage === 'ACCEPTED' || currentStage === 'JOINING_SET') &&
              (canManage ? (
                <form onSubmit={handleSaveJoining} className="space-y-3">
                  <p className="text-sm text-ink">
                    {candidateName} accepted{offer.respondedAt ? ` ${formatRelativeDate(offer.respondedAt).toLowerCase()}` : ''}. Set the joining date and any details.
                  </p>
                  <div className="space-y-1.5">
                    <label htmlFor="joining-date" className="block text-sm font-semibold">
                      Joining date
                    </label>
                    <Input id="joining-date" type="date" value={joiningDate} onChange={(e) => setJoiningDate(e.target.value)} required className="sm:max-w-[220px]" />
                  </div>
                  <div className="space-y-1.5">
                    <label htmlFor="joining-notes" className="block text-sm font-semibold">
                      Notes or details <span className="font-normal text-ink-muted">(optional)</span>
                    </label>
                    <Textarea
                      id="joining-notes"
                      rows={3}
                      maxLength={2000}
                      value={joiningNotes}
                      onChange={(e) => setJoiningNotes(e.target.value)}
                      placeholder="Where they sit, equipment, who greets them…"
                    />
                  </div>
                  <Button type="submit" variant="primary" disabled={busy || !joiningDate}>
                    {busy ? 'Saving…' : currentStage === 'JOINING_SET' ? 'Update joining details' : 'Save joining details'}
                  </Button>
                </form>
              ) : (
                currentStage === 'JOINING_SET' && (
                  <p className="text-sm text-ink">
                    Joining {offer.joiningDate ? formatDate(offer.joiningDate) : ''}.
                    {offer.joiningNotes ? ` ${offer.joiningNotes}` : ''}
                  </p>
                )
              ))}
          </div>
        </div>
      )}

      {/* Stage Timeline */}
      <div className="space-y-3 border-t border-line pt-4">
        <h3 className="text-[15px] font-semibold text-ink">Timeline</h3>
        {timeline.length === 0 ? (
          <p className="text-sm italic text-ink-muted">No stage events recorded yet.</p>
        ) : (
          <ol className="relative ml-2 space-y-4 border-l-2 border-line pl-4">
            {timeline.map((evt) => {
              const isShortlisted = evt.stage === 'SHORTLISTED';
              return (
                <li key={evt.id} className="relative">
                  <span aria-hidden="true" className={cn('absolute -left-[21px] top-1 size-2 rounded-full', isShortlisted ? 'bg-mark' : 'bg-ink-muted')} />
                  <div className="text-xs">
                    <span className={cn('font-semibold', isShortlisted ? 'text-mark-text' : 'text-ink')}>{eventLabel(evt.stage)}</span>
                    <span className="text-ink-muted">
                      {' '}
                      · {evt.summary || 'Stage updated'}
                      {evt.actorName ? `, by ${evt.actorName}` : ''} · {formatRelativeDate(evt.createdAt)}
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
