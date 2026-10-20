import type { BadgeTone } from '@/components/ui/Badge';
import type { CandidateInterview, InterviewKind, ReviewStatus } from '@/types';

/** One source for every screen that names a candidate's stage, so lists and detail pages cannot disagree. */
export const STAGE_LABEL: Record<ReviewStatus, string> = {
  NONE: 'Screened',
  SHORTLISTED: 'Shortlisted',
  CONTACTED: 'Contacted',
  INTERVIEW_SCHEDULED: 'Interviewing',
  INTERVIEWED: 'Interviewed',
  OFFER: 'Offer',
  OFFER_APPROVED: 'Offer approved',
  OFFER_SENT: 'Offer sent',
  ACCEPTED: 'Accepted',
  DECLINED: 'Declined',
  JOINING_SET: 'Joining',
};

export const STAGE_ORDER: Record<ReviewStatus, number> = {
  NONE: 0,
  SHORTLISTED: 1,
  CONTACTED: 2,
  INTERVIEW_SCHEDULED: 3,
  INTERVIEWED: 4,
  OFFER: 5,
  OFFER_APPROVED: 6,
  OFFER_SENT: 7,
  ACCEPTED: 8,
  DECLINED: 8,
  JOINING_SET: 9,
};

export function stageTone(stage: ReviewStatus): BadgeTone {
  switch (stage) {
    case 'SHORTLISTED':
      return 'mark';
    case 'CONTACTED':
    case 'INTERVIEW_SCHEDULED':
    case 'INTERVIEWED':
      return 'info';
    case 'OFFER':
      return 'warn';
    case 'OFFER_APPROVED':
    case 'OFFER_SENT':
    case 'ACCEPTED':
    case 'JOINING_SET':
      return 'success';
    case 'DECLINED':
      return 'danger';
    default:
      return 'neutral';
  }
}

/** Shortlisted or any later stage: the candidate made the cut. */
export function madeTheCut(stage: ReviewStatus): boolean {
  return stage !== 'NONE';
}

export const KIND_LABEL: Record<InterviewKind, string> = {
  HR: 'HR',
  TECHNICAL: 'Technical',
  MANAGEMENT: 'Management',
};

export const KIND_ORDER: InterviewKind[] = ['HR', 'TECHNICAL', 'MANAGEMENT'];

/**
 * Mirrors the server: once no interview is waiting and every planned interview has been held, the
 * candidate is Interviewed. Without a plan, one held interview is enough.
 */
export function settledStage(stage: ReviewStatus, plan: InterviewKind[], interviews: CandidateInterview[]): ReviewStatus {
  if (stage !== 'INTERVIEW_SCHEDULED') return stage;
  if (interviews.some((i) => i.status === 'SCHEDULED')) return stage;
  const held = new Set(interviews.filter((i) => i.status === 'COMPLETED').map((i) => i.kind));
  const covered = plan.length === 0 ? held.size > 0 : plan.every((k) => held.has(k));
  return covered ? 'INTERVIEWED' : stage;
}

export type TrackState = 'done' | 'current' | 'todo';
export interface TrackStep {
  key: string;
  label: string;
  state: TrackState;
}

function byOrder(order: number, stageOrder: number): TrackState {
  return stageOrder > order ? 'done' : stageOrder === order ? 'current' : 'todo';
}

/**
 * The status line for one candidate. Interview steps follow what the recruiter planned: only the chosen
 * kinds appear. Without a plan (candidates from before plans existed) a single Interviews step stands in.
 */
export function buildTrack(stage: ReviewStatus, plan: InterviewKind[], interviews: CandidateInterview[]): TrackStep[] {
  const at = STAGE_ORDER[stage] ?? 0;
  const steps: TrackStep[] = [
    { key: 'SHORTLISTED', label: STAGE_LABEL.SHORTLISTED, state: byOrder(1, at) },
    { key: 'CONTACTED', label: STAGE_LABEL.CONTACTED, state: byOrder(2, at) },
  ];

  const kinds = KIND_ORDER.filter((k) => plan.includes(k));
  if (kinds.length === 0) {
    const state: TrackState = at >= STAGE_ORDER.INTERVIEWED ? 'done' : at >= STAGE_ORDER.INTERVIEW_SCHEDULED ? 'current' : 'todo';
    steps.push({ key: 'INTERVIEWS', label: 'Interviews', state });
  } else {
    let currentTaken = false;
    for (const kind of kinds) {
      const held = interviews.some((i) => i.kind === kind && i.status === 'COMPLETED');
      let state: TrackState = 'todo';
      if (held || at >= STAGE_ORDER.INTERVIEWED) {
        state = 'done';
      } else if (at >= STAGE_ORDER.CONTACTED && !currentTaken) {
        state = 'current';
        currentTaken = true;
      }
      steps.push({ key: `INTERVIEW_${kind}`, label: `${KIND_LABEL[kind]} interview`, state });
    }
  }

  steps.push(
    { key: 'OFFER', label: STAGE_LABEL.OFFER, state: byOrder(5, at) },
    { key: 'OFFER_APPROVED', label: 'Approved', state: byOrder(6, at) },
    { key: 'OFFER_SENT', label: 'Sent', state: byOrder(7, at) },
    { key: 'ANSWER', label: stage === 'DECLINED' ? STAGE_LABEL.DECLINED : STAGE_LABEL.ACCEPTED, state: byOrder(8, at) },
    { key: 'JOINING_SET', label: STAGE_LABEL.JOINING_SET, state: byOrder(9, at) }
  );
  return steps;
}
