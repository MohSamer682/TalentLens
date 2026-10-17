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
      className={cn('inline-flex items-center rounded-md border border-line bg-ground px-2 py-0.5 text-[13px] text-ink', className)}
      {...props}
    />
  );
}
