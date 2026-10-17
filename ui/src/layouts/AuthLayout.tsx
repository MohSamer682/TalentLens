import { ReactNode } from 'react';
import { ListOrdered, ShieldCheck } from 'lucide-react';

export function AuthLayout({ title, subtitle, children, footer }: { title: string; subtitle: string; children: ReactNode; footer: ReactNode }) {
  return (
    <div className="min-h-screen bg-ground">
      <header className="bg-pitch text-white">
        <div className="mx-auto flex h-14 max-w-[1280px] items-center gap-2 px-4 sm:px-6">
          <span className="flex size-7 -rotate-3 items-center justify-center rounded-[5px] bg-mark text-mark-ink" aria-hidden="true">
            <ListOrdered className="size-4" />
          </span>
          <span className="font-display text-[17px] font-bold tracking-tight">Resume Screener</span>
        </div>
      </header>
      <main className="mx-auto w-full max-w-[420px] animate-fade-up px-4 py-10 sm:py-16">
        <h1 className="text-[32px] font-bold leading-tight">{title}</h1>
        <p className="mt-1 text-sm text-ink-muted">{subtitle}</p>
        <div className="mt-6 rounded-lg border border-line bg-surface p-5 shadow-panel sm:p-6">{children}</div>
        <div className="mt-5 text-center text-sm text-ink-muted">{footer}</div>
        <p className="mt-10 flex items-start gap-2 text-[13px] text-ink-muted">
          <ShieldCheck className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          Self-hosted. Resumes and scores stay on your company's servers and go only to the language model your team configured.
        </p>
      </main>
    </div>
  );
}
