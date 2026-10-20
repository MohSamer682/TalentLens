import { useState } from 'react';
import { X } from 'lucide-react';
import { cn } from '@/lib/utils';

type Props = {
  id: string;
  label: string;
  value: string[];
  onChange: (tags: string[]) => void;
  max?: number;
  error?: string;
};

export function TagInput({ id, label, value, onChange, max = 30, error }: Props) {
  const [draft, setDraft] = useState('');
  const [duplicate, setDuplicate] = useState(false);
  const message = duplicate ? 'That skill is already on the list.' : error;

  const add = () => {
    const tag = draft.trim();
    if (!tag || value.length >= max) return;
    if (value.some((t) => t.toLowerCase() === tag.toLowerCase())) {
      setDuplicate(true);
      return;
    }
    onChange([...value, tag]);
    setDraft('');
  };

  const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
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
        <ul className="contents">
          {value.map((tag) => (
            <li key={tag} className="inline-flex items-center gap-1 rounded-full border border-line-strong bg-raised py-0.5 pl-3 pr-1 text-sm">
              {tag}
              <button
                type="button"
                aria-label={`Remove ${tag}`}
                onClick={() => onChange(value.filter((t) => t !== tag))}
                className="grid h-7 w-7 place-items-center rounded-full text-ink-muted hover:bg-sunken hover:text-ink"
              >
                <X className="h-3.5 w-3.5" aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
        <input
          id={id}
          value={draft}
          onChange={(e) => {
            setDraft(e.target.value);
            setDuplicate(false);
          }}
          onKeyDown={onKeyDown}
          onBlur={add}
          disabled={value.length >= max}
          aria-invalid={!!message}
          aria-describedby={`${id}-help`}
          placeholder={value.length ? undefined : 'Type a skill, then press Enter'}
          className="min-h-9 min-w-[140px] flex-1 bg-transparent text-[15px] text-ink placeholder:text-ink-muted focus:outline-none"
        />
      </div>
      <div id={`${id}-help`} className="flex justify-between gap-3 text-[13px]">
        <span className={message ? 'text-danger' : 'text-ink-muted'}>{message ?? 'Enter or comma adds a skill'}</span>
        <span className="text-ink-muted">
          {value.length} of {max}
        </span>
      </div>
    </div>
  );
}
