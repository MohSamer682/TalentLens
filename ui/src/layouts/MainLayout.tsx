import { ReactNode } from 'react';
import { Navbar } from '@/components/Navbar';

interface MainLayoutProps {
  children: ReactNode;
  maxWidth?: string;
}

export function MainLayout({ children, maxWidth = 'max-w-7xl' }: MainLayoutProps) {
  return (
    <div className="min-h-screen bg-surface">
      <Navbar />
      <main className={`pt-24 pb-12 px-8 mx-auto ${maxWidth}`}>
        {children}
      </main>
    </div>
  );
}
