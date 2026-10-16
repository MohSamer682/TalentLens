import { forwardRef } from 'react';
import { cn } from '@/lib/utils';

const field =
  'w-full rounded-md border border-line-strong bg-surface px-3 text-[15px] text-ink transition-colors placeholder:text-ink-muted hover:border-ink-muted focus:border-accent-text focus:outline-none focus:ring-4 focus:ring-accent/25 disabled:cursor-not-allowed disabled:bg-sunken aria-[invalid=true]:border-danger';

export const Input = forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ className, ...props }, ref) => <input ref={ref} className={cn(field, 'h-10', className)} {...props} />
);

Input.displayName = 'Input';

export const Textarea = forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className, ...props }, ref) => (
    <textarea ref={ref} className={cn(field, 'min-h-[96px] resize-y py-2', className)} {...props} />
  )
);

Textarea.displayName = 'Textarea';

export function Field({
  id,
  label,
  hint,
  error,
  children,
}: {
  id: string;
  label: React.ReactNode;
  hint?: React.ReactNode;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-semibold">
        {label}
      </label>
      {children}
      {error ? (
        <p id={`${id}-error`} className="text-[13px] text-danger">
          {error}
        </p>
      ) : hint ? (
        <p className="text-[13px] text-ink-muted">{hint}</p>
      ) : null}
    </div>
  );
}
