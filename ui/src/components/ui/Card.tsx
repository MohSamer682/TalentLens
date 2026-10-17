import { forwardRef } from 'react';
import { cn } from '@/lib/utils';

/** A bordered panel with a soft shadow. Panels group content; they never lift on hover. */
export const Panel = forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div ref={ref} className={cn('plate', className)} {...props} />
  )
);

Panel.displayName = 'Panel';

export function PanelHeader({
  title,
  children,
  className,
}: {
  title: React.ReactNode;
  children?: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn('flex flex-wrap items-center justify-between gap-3 border-b border-line px-4 py-3 sm:px-5', className)}>
      <h2 className="text-[15px] font-semibold">{title}</h2>
      {children}
    </div>
  );
}
