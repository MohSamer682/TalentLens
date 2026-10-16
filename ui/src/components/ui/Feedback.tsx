import { useEffect, useRef, useState } from 'react';
import { AlertCircle, CheckCircle2, Info } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from './Button';

const TONES = {
  danger: { box: 'border-danger/25 bg-danger-soft text-danger', Icon: AlertCircle },
  info: { box: 'border-info/25 bg-info-soft text-info', Icon: Info },
  success: { box: 'border-accent-text/25 bg-accent-soft text-accent-text', Icon: CheckCircle2 },
} as const;

export function Alert({
  tone = 'danger',
  title,
  children,
  action,
  className,
}: {
  tone?: keyof typeof TONES;
  title?: React.ReactNode;
  children?: React.ReactNode;
  action?: React.ReactNode;
  className?: string;
}) {
  const { box, Icon } = TONES[tone];
  return (
    <div
      role={tone === 'danger' ? 'alert' : 'status'}
      className={cn('flex animate-fade-up flex-wrap items-start gap-3 rounded-lg border px-4 py-3 text-sm', box, className)}
    >
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      <div className="min-w-0 flex-1 space-y-0.5">
        {title && <p className="font-semibold">{title}</p>}
        {children && <div className="break-words text-ink">{children}</div>}
      </div>
      {action}
    </div>
  );
}

/**
 * The product's loading mark: four ranking bars that stretch in turn, like a table settling.
 * Used where there is no content shape to skeleton (whole pages, the wizard).
 */
export function Loader({ label, className }: { label: string; className?: string }) {
  return (
    <div role="status" className={cn('flex animate-fade-in flex-col items-center justify-center gap-3 py-20', className)}>
      <div className="flex w-12 flex-col gap-1.5" aria-hidden="true">
        {[0, 1, 2, 3].map((i) => (
          <span
            key={i}
            className={cn('h-1.5 origin-left animate-rank rounded-full', i === 0 ? 'bg-accent' : 'bg-line-strong')}
            style={{ animationDelay: `${i * 120}ms`, width: `${100 - i * 18}%` }}
          />
        ))}
      </div>
      <p className="text-sm text-ink-muted">{label}</p>
    </div>
  );
}

/** Placeholder rows shaped like the list or table that is loading. */
export function SkeletonRows({ rows = 6, columns = [40, 14, 10, 12] }: { rows?: number; columns?: number[] }) {
  return (
    <div role="status" aria-label="Loading" className="divide-y divide-line">
      {Array.from({ length: rows }, (_, r) => (
        <div key={r} className="flex items-center gap-4 px-4 py-4 sm:px-5" style={{ opacity: 1 - r * 0.12 }}>
          <span className="skeleton size-8 shrink-0" />
          {columns.map((w, c) => (
            <span key={c} className={cn('skeleton h-3', c > 0 && 'hidden sm:block')} style={{ width: `${w}%` }} />
          ))}
        </div>
      ))}
    </div>
  );
}

/**
 * Two-step destructive action in place of window.confirm: the first click arms it,
 * the second confirms. Disarms itself after a few seconds or on Escape.
 */
export function ConfirmButton({
  label,
  confirmLabel,
  onConfirm,
  disabled,
  icon,
  compact,
}: {
  label: string;
  confirmLabel: string;
  onConfirm: () => void;
  disabled?: boolean;
  icon?: React.ReactNode;
  compact?: boolean;
}) {
  const [armed, setArmed] = useState(false);
  const timer = useRef<number>();

  useEffect(() => {
    if (!armed) return;
    timer.current = window.setTimeout(() => setArmed(false), 4000);
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setArmed(false);
    document.addEventListener('keydown', onKey);
    return () => {
      window.clearTimeout(timer.current);
      document.removeEventListener('keydown', onKey);
    };
  }, [armed]);

  if (armed) {
    return (
      <Button
        variant="dangerSolid"
        size={compact ? 'sm' : 'md'}
        disabled={disabled}
        className="animate-fade-in"
        onClick={() => {
          setArmed(false);
          onConfirm();
        }}
        autoFocus
      >
        {confirmLabel}
      </Button>
    );
  }

  return (
    <Button
      variant="danger"
      size={compact ? 'icon' : 'md'}
      disabled={disabled}
      onClick={() => setArmed(true)}
      aria-label={compact ? label : undefined}
      title={compact ? label : undefined}
    >
      {icon}
      {!compact && label}
    </Button>
  );
}
