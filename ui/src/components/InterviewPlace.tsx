import { Building2, MapPin, Video } from 'lucide-react';
import type { InterviewPlaceType } from '@/types';
import { Input, Textarea } from '@/components/ui/Input';
import { Segmented } from '@/components/ui/Segmented';
import { cn } from '@/lib/utils';

const WEB_LINK = /^https?:\/\/\S+$/i;

/** Client-side mirror of the server's rule; the server stays the authority. */
export function placeError(placeType: InterviewPlaceType, location: string): string | null {
  const text = location.trim();
  if (placeType === 'ONLINE' && !WEB_LINK.test(text)) return 'Paste the meeting link, starting with http:// or https://';
  if (placeType === 'OTHER' && !text) return 'Write where the interview takes place';
  return null;
}

/** Where an interview happens. An online link is a real link; it opens in a new tab. */
export function InterviewPlaceText({
  placeType,
  location,
  className,
}: {
  placeType: InterviewPlaceType | null | undefined;
  location: string | null | undefined;
  className?: string;
}) {
  // Interviews saved before places existed hold a link as plain "other" text; still make it clickable
  const type = placeType === 'OTHER' && location && WEB_LINK.test(location.trim()) ? 'ONLINE' : placeType ?? (location ? 'OTHER' : 'HQ');
  const Icon = type === 'ONLINE' ? Video : type === 'HQ' ? Building2 : MapPin;
  return (
    <span className={cn('inline-flex min-w-0 items-center gap-1.5', className)}>
      <Icon className="size-3.5 shrink-0" aria-hidden="true" />
      {type === 'HQ' && <span>HQ</span>}
      {type === 'ONLINE' && location && WEB_LINK.test(location.trim()) && (
        <a
          href={location.trim()}
          target="_blank"
          rel="noopener noreferrer"
          className="min-w-0 truncate text-accent-text underline-offset-2 hover:underline"
        >
          Join online<span className="sr-only"> (opens in a new tab)</span>
        </a>
      )}
      {type === 'ONLINE' && !(location && WEB_LINK.test(location.trim())) && <span>Online</span>}
      {type === 'OTHER' && <span className="min-w-0 break-words">{location || 'Other place'}</span>}
    </span>
  );
}

/** Place picker used when scheduling or rescheduling: HQ, Online (paste a link) or Other (write it). */
export function PlaceFields({
  idPrefix,
  placeType,
  location,
  onChange,
}: {
  idPrefix: string;
  placeType: InterviewPlaceType;
  location: string;
  onChange: (placeType: InterviewPlaceType, location: string) => void;
}) {
  return (
    <div className="space-y-2">
      <span id={`${idPrefix}-place-label`} className="block text-sm font-semibold">
        Place
      </span>
      <Segmented
        label="Place"
        value={placeType}
        onChange={(next) => onChange(next, next === placeType ? location : '')}
        options={[
          { value: 'HQ', label: 'HQ' },
          { value: 'ONLINE', label: 'Online' },
          { value: 'OTHER', label: 'Other' },
        ]}
      />
      {placeType === 'HQ' && <p className="text-[13px] text-ink-muted">At HQ. Nothing else to add.</p>}
      {placeType === 'ONLINE' && (
        <div className="space-y-1.5">
          <label htmlFor={`${idPrefix}-link`} className="block text-[13px] font-semibold text-ink-muted">
            Meeting link
          </label>
          <Input
            id={`${idPrefix}-link`}
            type="url"
            inputMode="url"
            required
            maxLength={500}
            value={location}
            onChange={(e) => onChange('ONLINE', e.target.value)}
            placeholder="https://meet.example.com/…"
          />
        </div>
      )}
      {placeType === 'OTHER' && (
        <div className="space-y-1.5">
          <label htmlFor={`${idPrefix}-other`} className="block text-[13px] font-semibold text-ink-muted">
            Where
          </label>
          <Textarea
            id={`${idPrefix}-other`}
            required
            rows={2}
            maxLength={500}
            value={location}
            onChange={(e) => onChange('OTHER', e.target.value)}
            placeholder="Write the place or any instructions"
            className="min-h-[64px]"
          />
        </div>
      )}
    </div>
  );
}
