import { ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from './Button';
import { cn } from '@/lib/utils';

export function Pagination({
  page,
  totalPages,
  onPage,
}: {
  page: number;
  totalPages: number;
  onPage: (page: number) => void;
}) {
  if (totalPages <= 1) return null;

  const shown = Array.from({ length: totalPages }, (_, i) => i).filter(
    (i) => i === 0 || i === totalPages - 1 || Math.abs(i - page) <= 1
  );

  return (
    <nav aria-label="Pagination" className="flex items-center gap-1">
      <Button variant="ghost" size="icon" disabled={page === 0} onClick={() => onPage(page - 1)} aria-label="Previous page">
        <ChevronLeft />
      </Button>
      {shown.map((i, idx) => (
        <span key={i} className="flex items-center gap-1">
          {idx > 0 && shown[idx - 1] !== i - 1 && <span className="px-1 text-ink-muted">…</span>}
          <button
            type="button"
            onClick={() => onPage(i)}
            aria-current={page === i ? 'page' : undefined}
            className={cn(
              'num h-9 min-w-9 rounded-md px-2 text-sm font-semibold transition-colors',
              page === i ? 'bg-ink text-white' : 'text-ink-muted hover:bg-sunken hover:text-ink'
            )}
          >
            {i + 1}
          </button>
        </span>
      ))}
      <Button
        variant="ghost"
        size="icon"
        disabled={page >= totalPages - 1}
        onClick={() => onPage(page + 1)}
        aria-label="Next page"
      >
        <ChevronRight />
      </Button>
    </nav>
  );
}
