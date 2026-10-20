import { Calendar, Pencil, Trash2 } from 'lucide-react';
import type { CandidateInterview, Role } from '@/types';
import { Button } from '@/components/ui/Button';
import { InterviewPlaceText } from '@/features/pipeline/InterviewPlace';
import { KIND_LABEL } from '@/features/pipeline/pipeline';
import { ROLE_LABEL, cn, formatRelativeDate } from '@/lib/utils';
import { RECOMMENDATION_LABELS, formatStars, formatTimeRange } from './format';

interface InterviewListProps {
  interviews: CandidateInterview[];
  canManage: boolean;
  onReschedule: (interview: CandidateInterview) => void;
  onCancel: (interviewId: string) => void;
}

export function InterviewList({ interviews, canManage, onReschedule, onCancel }: InterviewListProps) {
  return (
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
                        onClick={() => onReschedule(item)}
                      >
                        <Pencil className="size-3.5" aria-hidden="true" />
                        Reschedule
                      </Button>
                      <Button variant="secondary" size="sm" className="text-danger hover:border-danger" onClick={() => onCancel(item.id)}>
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
  );
}
