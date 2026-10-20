import { useRef, useState } from 'react';
import { X } from 'lucide-react';
import { cn } from '@/lib/utils';

type Props = {
  id: string;
  label: string;
  value: string[];
  onChange: (tags: string[]) => void;
  max?: number;
  maxLength?: number;
  error?: string;
};

export function TagInput({ id, label, value, onChange, max = 30, maxLength = 80, error }: Props) {
  const [draft, setDraft] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const message = problem ?? error;
  const full = value.length >= max;

  const add = () => {
    const tag = draft.trim();
    if (!tag || full) return;
    if (tag.length > maxLength) {
      setProblem(`Keep each skill under ${maxLength} characters.`);
      return;
    }
    if (value.some((t) => t.toLowerCase() === tag.toLowerCase())) {
      setProblem('That skill is already on the list.');
      return;
    }
    onChange([...value, tag]);
    setDraft('');
  };

  const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.nativeEvent.isComposing) return;
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      add();
    } else if (e.key === 'Backspace' && !draft && value.length) {
      onChange(value.slice(0, -1));
    }
  };

  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-semibold">
        {label}
      </label>
      <div
        className={cn(
          'flex flex-wrap items-center gap-2 rounded-md border bg-surface p-2 transition-colors focus-within:border-accent-text focus-within:ring-2 focus-within:ring-accent-text/25 dark:bg-sunken',
          message ? 'border-danger' : 'border-line hover:border-line-strong'
        )}
      >
        <ul role="list" className="contents">
          {value.map((tag) => (
            <li key={tag} className="inline-flex items-center gap-1 rounded-full border border-line-strong bg-raised py-0.5 pl-3 pr-1 text-sm">
              {tag}
              <button
                type="button"
                aria-label={`Remove ${tag}`}
                onClick={() => {
                  onChange(value.filter((t) => t !== tag));
                  inputRef.current?.focus();
                }}
                className="grid h-7 w-7 place-items-center rounded-full text-ink-muted hover:bg-sunken hover:text-ink"
              >
                <X className="h-3.5 w-3.5" aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
        <input
          ref={inputRef}
          id={id}
          value={draft}
          onChange={(e) => {
            // At the cap the input stays focusable so Backspace can still remove a skill
            if (!full) setDraft(e.target.value);
            setProblem(null);
          }}
          onKeyDown={onKeyDown}
          onBlur={add}
          aria-disabled={full || undefined}
          aria-invalid={!!message}
          aria-describedby={`${id}-help`}
          placeholder={value.length ? undefined : 'Type a skill, then press Enter'}
          className="min-h-9 min-w-[140px] flex-1 bg-transparent text-[15px] text-ink placeholder:text-ink-muted focus:outline-none"
        />
      </div>
      <div id={`${id}-help`} className="flex justify-between gap-3 text-[13px]">
        <span role="status" className={message ? 'text-danger' : 'text-ink-muted'}>
          {message ?? (full ? `That's the maximum of ${max} skills` : 'Enter or comma adds a skill')}
        </span>
        <span className="text-ink-muted">
          {value.length} of {max}
        </span>
      </div>
    </div>
  );
}
