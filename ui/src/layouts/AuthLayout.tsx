import { ReactNode } from 'react';
import { ShieldCheck } from 'lucide-react';
import { BrandMark } from '@/components/BrandMark';

export function AuthLayout({ title, subtitle, children, footer }: { title: string; subtitle: string; children: ReactNode; footer: ReactNode }) {
  return (
    <div className="min-h-screen bg-ground">
      <header className="border-b border-line glass text-ink">
        <div className="mx-auto flex h-14 max-w-[1280px] items-center gap-2 px-4 sm:px-6">
          <BrandMark size={36} />
          <span className="font-display text-[17px] font-bold tracking-tight">TalentLens</span>
        </div>
      </header>
      <main className="mx-auto w-full max-w-[420px] animate-fade-up px-4 py-10 sm:py-16">
        <h1 className="text-[32px] font-bold leading-tight">{title}</h1>
        <p className="mt-1 text-sm text-ink-muted">{subtitle}</p>
        <div className="mt-6 plate p-5 sm:p-6">{children}</div>
        <div className="mt-5 text-center text-sm text-ink-muted">{footer}</div>
        <p className="mt-10 flex items-start gap-2 text-[13px] text-ink-muted">
          <ShieldCheck className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          Self-hosted. Resumes and scores stay on your company's servers and go only to the language model your team configured.
        </p>
      </main>
    </div>
  );
}
