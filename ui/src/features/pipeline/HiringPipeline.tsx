// Everything about one candidate after the shortlist: interview plan, interviews, offer and joining.
import { useEffect, useState } from 'react';
import { Plus, UserCheck } from 'lucide-react';
import { apiErrorMessage, pipelineApi } from '@/api/client';
import { useAuthStore } from '@/features/auth/authStore';
import { hasPermission } from '@/lib/permissions';
import type {
  RescheduleRequest,
  CandidateInterview,
  CandidateOffer,
  CandidateStageEvent,
  InterviewKind,
  InterviewPlaceType,
  ReviewStatus,
  Role,
} from '@/types';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Panel } from '@/components/ui/Card';
import { Alert, Loader } from '@/components/ui/Feedback';
import { Input, Textarea } from '@/components/ui/Input';
import { Segmented } from '@/components/ui/Segmented';
import { PlaceFields, placeError } from '@/features/pipeline/InterviewPlace';
import { StageBadge } from '@/features/pipeline/StageBadge';
import { KIND_LABEL, KIND_ORDER, STAGE_ORDER, buildTrack, settledStage } from '@/features/pipeline/pipeline';
import { InterviewList } from './hiring/InterviewList';
import { OfferFollowUp } from './hiring/OfferFollowUp';
import { OfferSection } from './hiring/OfferSection';
import { StageTrack } from './hiring/StageTrack';
import { Timeline } from './hiring/Timeline';
import { toLocalInput } from './hiring/format';
import { ROLE_LABEL, cn } from '@/lib/utils';



const selectClass =
  'w-full h-10 rounded-md border border-line bg-surface dark:bg-sunken px-3 text-sm text-ink hover:border-line-strong focus:border-accent-text focus:outline-none focus:ring-2 focus:ring-accent-text/25';



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
  const [requests, setRequests] = useState<RescheduleRequest[]>([]);
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
      const [tlRes, intRes, offRes, planRes, reqRes] = await Promise.all([
        pipelineApi.getTimeline(sessionId, candidateId),
        pipelineApi.getInterviews(sessionId, candidateId),
        pipelineApi.getOffer(sessionId, candidateId).catch(() => ({ data: null })),
        pipelineApi.getInterviewPlan(sessionId, candidateId).catch(() => ({ data: [] as InterviewKind[] })),
        pipelineApi.rescheduleRequests(sessionId, candidateId).catch(() => ({ data: [] as RescheduleRequest[] })),
      ]);
      setRequests(reqRes.data);
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

      <StageTrack track={track} />

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

      <InterviewList
        interviews={interviews}
        canManage={canManage}
        onReschedule={(item) => {
          setReschedulingInterview(item);
          setRescheduleStart(toLocalInput(item.scheduledStart));
          setRescheduleDuration(item.durationMinutes);
          setReschedulePlaceType(item.placeType ?? 'HQ');
          setRescheduleLocation(item.location || '');
        }}
        onCancel={handleCancelInterview}
        requests={requests}
        busy={busy}
        onAcceptRequest={(request, start) =>
          run(async () => {
            // A stale or answered request still reloads, so HR sees what changed
            try {
              await pipelineApi.acceptReschedule(sessionId, candidateId, request.id, start, request.version);
            } finally {
              await reloadData();
            }
          }, 'Could not move the interview')
        }
        onDeclineRequest={(request, note) =>
          run(async () => {
            // A stale or answered request still reloads, so HR sees what changed
            try {
              await pipelineApi.declineReschedule(sessionId, candidateId, request.id, note, request.version);
            } finally {
              await reloadData();
            }
          }, 'Could not decline the request')
        }
      />

      {(stageIdx >= STAGE_ORDER.OFFER || offer) && (
        <OfferSection
          offer={offer}
          currentStage={currentStage}
          candidateName={candidateName}
          canManage={canManage}
          directorOpen={directorOpen}
          rejected={rejected}
          myStepOpen={myStepOpen}
          requireDirector={requireDirector}
          onRequireDirectorChange={setRequireDirector}
          uploadingOffer={uploadingOffer}
          onUpload={handleFileUpload}
          onDownload={handleDownloadOffer}
          confirmApprove={confirmApprove}
          onConfirmApproveChange={setConfirmApprove}
          approvingOffer={approvingOffer}
          onApprove={handleApproveOffer}
          rejecting={rejecting}
          onRejectingChange={setRejecting}
          rejectNote={rejectNote}
          onRejectNoteChange={setRejectNote}
          submittingReject={submittingReject}
          onReject={handleRejectOffer}
        />
      )}

      {stageIdx >= STAGE_ORDER.OFFER_APPROVED && offer && (
        <OfferFollowUp
          offer={offer}
          currentStage={currentStage}
          candidateName={candidateName}
          canManage={canManage}
          busy={busy}
          joiningDate={joiningDate}
          joiningNotes={joiningNotes}
          onJoiningDateChange={setJoiningDate}
          onJoiningNotesChange={setJoiningNotes}
          onOfferSent={handleOfferSent}
          onAnswer={handleAnswer}
          onSaveJoining={handleSaveJoining}
        />
      )}

      <Timeline timeline={timeline} />
    </Panel>
  );
}
