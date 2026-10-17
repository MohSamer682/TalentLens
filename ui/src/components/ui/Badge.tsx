import { cn } from '@/lib/utils';
import type { ScreeningSession } from '@/types';

type Status = ScreeningSession['status'];

const STATUS: Record<Status, { label: string; className: string; dot: string; live?: boolean }> = {
  PENDING: { label: 'Not started', className: 'bg-sunken text-ink-muted', dot: 'bg-ink-muted' },
  RUNNING: { label: 'Scoring', className: 'bg-info-soft text-info', dot: 'bg-info', live: true },
  DONE: { label: 'Ranked', className: 'bg-success-soft text-success', dot: 'bg-success' },
  FAILED: { label: 'Failed', className: 'bg-danger-soft text-danger', dot: 'bg-danger' },
};

export function StatusBadge({ status, className }: { status: Status; className?: string }) {
  const s = STATUS[status];
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[13px] font-semibold',
        s.className,
        className
      )}
    >
      <span aria-hidden="true" className="relative flex size-1.5">
        {s.live && <span className={cn('absolute inset-0 animate-ping rounded-full', s.dot)} />}
        <span className={cn('relative size-1.5 rounded-full', s.dot)} />
      </span>
      {s.label}
    </span>
  );
}

export function Tag({ className, ...props }: React.HTMLAttributes<HTMLSpanElement>) {
  return (
    <span
      className={cn('inline-flex items-center rounded-full border border-line bg-ground px-2.5 py-0.5 text-[12px] font-semibold text-ink', className)}
      {...props}
    />
  );
}

export type BadgeTone = 'neutral' | 'mark' | 'success' | 'warn' | 'danger' | 'info';

const TONES: Record<BadgeTone, string> = {
  neutral: 'bg-sunken text-ink-muted',
  mark: 'bg-mark-soft text-mark-text',
  success: 'bg-success-soft text-success',
  warn: 'bg-warn-soft text-warn',
  danger: 'bg-danger-soft text-danger',
  info: 'bg-info-soft text-info',
};

export interface BadgeProps extends React.HTMLAttributes<HTMLSpanElement> {
  tone?: BadgeTone;
}

export function Badge({ tone = 'neutral', className, children, ...props }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[12px] font-semibold',
        TONES[tone] || TONES.neutral,
        className
      )}
      {...props}
    >
      {children}
    </span>
  );
}

