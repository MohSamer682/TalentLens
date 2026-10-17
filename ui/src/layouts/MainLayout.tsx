import { ReactNode } from 'react';
import { Navbar } from '@/components/Navbar';
import { cn } from '@/lib/utils';

export function MainLayout({ children, width = 'wide' }: { children: ReactNode; width?: 'wide' | 'narrow' }) {
  return (
    <div className="min-h-screen bg-ground">
      <Navbar />
      <main className={cn('mx-auto animate-fade-in px-4 pb-16 pt-6 sm:px-6 sm:pt-8', width === 'wide' ? 'max-w-[1280px]' : 'max-w-3xl')}>
        {children}
      </main>
    </div>
  );
}

export function PageHeader({
  title,
  meta,
  actions,
}: {
  title: ReactNode;
  meta?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0 space-y-1.5">
        <h1 className="break-words text-[26px] font-bold leading-tight sm:text-[32px]">{title}</h1>
        {meta && <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-ink-muted">{meta}</div>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}
