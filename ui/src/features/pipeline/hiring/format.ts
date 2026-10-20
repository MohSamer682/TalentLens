import type { InterviewRecommendation, ReviewStatus } from '@/types';
import { STAGE_LABEL } from '@/features/pipeline/pipeline';

export const RECOMMENDATION_LABELS: Record<InterviewRecommendation, { label: string; tone: string }> = {
  STRONG_HIRE: { label: 'Strong hire', tone: 'bg-success/15 text-success' },
  HIRE: { label: 'Hire', tone: 'bg-success/15 text-success' },
  NO_HIRE: { label: 'No hire', tone: 'bg-warn/15 text-warn' },
  STRONG_NO_HIRE: { label: 'Strong no hire', tone: 'bg-danger/15 text-danger' },
};

export function formatStars(rating: number | null): string {
  if (!rating) return '';
  const full = Math.min(5, Math.max(1, rating));
  return '★'.repeat(full) + '☆'.repeat(5 - full) + ` ${full}/5`;
}

export function formatTimeRange(startIso: string, durationMinutes: number): string {
  try {
    const start = new Date(startIso);
    const end = new Date(start.getTime() + durationMinutes * 60 * 1000);
    const dateStr = start.toLocaleDateString('en-GB', {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
    });
    const startTime = start.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    const endTime = end.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    return `${dateStr}, ${startTime} – ${endTime}`;
  } catch {
    return startIso;
  }
}

/** datetime-local wants the viewer's local clock, not UTC. */
export function toLocalInput(iso: string): string {
  const d = new Date(iso);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

export function eventLabel(stage: string): string {
  if (stage in STAGE_LABEL) return STAGE_LABEL[stage as ReviewStatus];
  const words = stage.toLowerCase().replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}
