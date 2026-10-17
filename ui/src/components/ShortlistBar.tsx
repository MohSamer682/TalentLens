import { Button } from '@/components/ui/Button';

/** Appears at the bottom of the standings while rows are selected. Escape clears the selection. */
export function ShortlistBar({
  count,
  busy,
  onShortlist,
  onRemove,
  onClear,
}: {
  count: number;
  busy: boolean;
  onShortlist: () => void;
  onRemove: () => void;
  onClear: () => void;
}) {
  if (count === 0) return null;
  return (
    <div
      role="region"
      aria-label="Selection"
      className="sticky bottom-0 z-20 flex animate-fade-up flex-wrap items-center gap-x-3 gap-y-2 lift rounded-xl bg-ink px-4 py-3 text-ground sm:px-5"
    >
      <p className="flex-1 text-sm font-bold" aria-live="polite">
        <span className="num">{count}</span> selected
      </p>
      <Button size="sm" className="h-9 bg-mark text-mark-ink shadow-none hover:bg-mark/90" onClick={onShortlist} disabled={busy}>
        Shortlist
      </Button>
      <Button size="sm" variant="ghost" className="h-9 border border-ground/40 text-ground hover:bg-ground/10 hover:text-ground" onClick={onRemove} disabled={busy}>
        Remove from shortlist
      </Button>
      <Button size="sm" variant="ghost" className="h-9 text-ground underline hover:bg-ground/10 hover:text-ground" onClick={onClear}>
        Clear
      </Button>
    </div>
  );
}
