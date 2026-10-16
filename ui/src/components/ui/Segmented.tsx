import { useLayoutEffect, useRef, useState } from 'react';
import { cn } from '@/lib/utils';

/** Pill toggle group whose highlight slides to the chosen option, so the change of filter reads as one motion. */
export function Segmented<T extends string>({
  label,
  options,
  value,
  onChange,
}: {
  label: string;
  options: { value: T; label: string; count?: number }[];
  value: T;
  onChange: (value: T) => void;
}) {
  const refs = useRef<Record<string, HTMLButtonElement | null>>({});
  const [pill, setPill] = useState<{ left: number; width: number } | null>(null);

  useLayoutEffect(() => {
    const measure = () => {
      const el = refs.current[value];
      if (el) setPill({ left: el.offsetLeft, width: el.offsetWidth });
    };
    measure();
    window.addEventListener('resize', measure);
    return () => window.removeEventListener('resize', measure);
  }, [value, options]);

  return (
    <div role="group" aria-label={label} className="relative flex shrink-0 rounded-full bg-sunken p-1">
      {pill && (
        <span
          aria-hidden="true"
          className="absolute inset-y-1 rounded-full bg-surface shadow-panel transition-[left,width] duration-300 ease-out"
          style={{ left: pill.left, width: pill.width }}
        />
      )}
      {options.map((o) => (
        <button
          key={o.value}
          ref={(el) => (refs.current[o.value] = el)}
          type="button"
          aria-pressed={value === o.value}
          onClick={() => onChange(o.value)}
          className={cn(
            'condensed relative flex h-8 items-center gap-1.5 whitespace-nowrap rounded-full px-3 text-[15px] font-semibold uppercase tracking-wide transition-colors',
            value === o.value ? 'text-ink' : 'text-ink-muted hover:text-ink'
          )}
        >
          {o.label}
          {o.count !== undefined && (
            <span
              className={cn(
                'num rounded-full px-1.5 text-[12px] leading-5 transition-colors',
                value === o.value ? 'bg-accent text-accent-ink' : 'bg-line text-ink-muted'
              )}
            >
              {o.count}
            </span>
          )}
        </button>
      ))}
    </div>
  );
}
