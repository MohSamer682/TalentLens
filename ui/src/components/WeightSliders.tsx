import type { Weights } from '@/types';
import { DEFAULT_WEIGHTS, DIMENSIONS, cn } from '@/lib/utils';

// Segment shades for the composition bar: one hue, five steps, so the bar reads as one total.
const SEGMENT = ['bg-accent-text', 'bg-accent-bar', 'bg-accent', 'bg-accent/60', 'bg-accent/35'];

export function WeightSliders({ weights, onChange }: { weights: Weights; onChange: (w: Weights) => void }) {
  const total = DIMENSIONS.reduce((sum, d) => sum + weights[d.key], 0);
  const valid = total === 100;
  const set = (key: keyof Weights, value: number) =>
    onChange({ ...weights, [key]: Math.max(0, Math.min(100, Math.round(value) || 0)) });

  return (
    <div className="space-y-5">
      <div className="space-y-2">
        <div className="flex h-3 overflow-hidden rounded-full bg-line" aria-hidden="true">
          {DIMENSIONS.map((d, i) => (
            <span
              key={d.key}
              className={cn('h-full border-r border-surface last:border-r-0 transition-[width]', SEGMENT[i])}
              style={{ width: `${(weights[d.key] / Math.max(100, total)) * 100}%` }}
            />
          ))}
        </div>
        <div className="flex flex-wrap items-center justify-between gap-2 text-sm" aria-live="polite">
          <p className={cn('num font-semibold', valid ? 'text-accent-text' : 'text-danger')}>
            Total {total} / 100
            {!valid && <span className="font-normal"> · {total > 100 ? `remove ${total - 100}` : `add ${100 - total}`} to launch</span>}
          </p>
          <button
            type="button"
            className="text-sm font-semibold text-ink-muted underline underline-offset-2 hover:text-ink"
            onClick={() => onChange(DEFAULT_WEIGHTS)}
          >
            Reset to defaults
          </button>
        </div>
      </div>

      <ul className="divide-y divide-line plate overflow-hidden">
        {DIMENSIONS.map((d, i) => (
          <li key={d.key} className="grid grid-cols-[1fr_auto] items-center gap-x-4 gap-y-2 px-4 py-3 sm:grid-cols-[160px_1fr_auto]">
            <label htmlFor={`w-${d.key}`} className="flex items-center gap-2 text-sm font-semibold">
              <span aria-hidden="true" className={cn('size-2.5 rounded-sm', SEGMENT[i])} />
              {d.label}
            </label>
            <input
              type="range"
              min={0}
              max={100}
              value={weights[d.key]}
              onChange={(e) => set(d.key, Number(e.target.value))}
              aria-label={`${d.label} weight`}
              className="col-span-2 row-start-2 w-full sm:col-span-1 sm:row-start-auto"
            />
            <div className="flex items-center gap-1">
              <input
                id={`w-${d.key}`}
                type="number"
                inputMode="numeric"
                min={0}
                max={100}
                value={weights[d.key]}
                onChange={(e) => set(d.key, Number(e.target.value))}
                className="num h-9 w-16 rounded-md border border-line-strong px-2 text-right text-sm font-semibold focus:border-accent-text focus:outline-none focus:ring-4 focus:ring-accent/25"
              />
              <span className="text-sm text-ink-muted">%</span>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
