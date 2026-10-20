import { Check, X } from 'lucide-react';
import { KIND_LABEL } from '@/features/pipeline/pipeline';
import type { StageStep } from '@/types';
import { cn } from '@/lib/utils';
import { STEP_LABEL } from './portal';

/** The planned interview rounds in order. The first scheduled round is the current one. */
export function StageSteps({ steps }: { steps: StageStep[] }) {
  if (steps.length === 0) {
    return <p className="text-sm text-ink-muted">No interviews planned yet. Your recruiter will add them here.</p>;
  }
  const current = steps.findIndex((s) => s.state === 'SCHEDULED');
  return (
    <ol className="space-y-3">
      {steps.map((step, i) => (
        <li key={step.kind} aria-current={i === current ? 'step' : undefined} className="flex items-start gap-3">
          <span
            aria-hidden="true"
            className={cn(
              'mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full border text-[12px]',
              step.state === 'DONE' && 'border-transparent bg-success-soft text-success',
              step.state === 'SCHEDULED' && 'border-accent bg-accent text-accent-ink',
              step.state === 'PLANNED' && 'border-dashed border-line-strong text-ink-muted',
              step.state === 'CANCELLED' && 'border-transparent bg-sunken text-ink-muted'
            )}
          >
            {step.state === 'DONE' && <Check className="size-3.5" />}
            {step.state === 'CANCELLED' && <X className="size-3.5" />}
            {(step.state === 'SCHEDULED' || step.state === 'PLANNED') && i + 1}
          </span>
          <div className="min-w-0">
            <p className={cn('text-sm font-semibold', step.state === 'CANCELLED' && 'text-ink-muted line-through')}>
              {KIND_LABEL[step.kind]} interview
            </p>
            <p className="text-[13px] text-ink-muted">{STEP_LABEL[step.state]}</p>
          </div>
        </li>
      ))}
    </ol>
  );
}
