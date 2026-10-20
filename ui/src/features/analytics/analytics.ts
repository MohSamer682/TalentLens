import type { AnalyticsTotals } from '@/types';

export const formatCount = (n: number) => n.toLocaleString('en-GB');

export function formatRate(rate: number | null): string {
  return rate === null ? '–' : `${(rate * 100).toFixed(1)}%`;
}

/** Minutes under an hour, hours up to a day and a half, then days. */
export function formatHours(h: number | null): string {
  if (h === null) return '–';
  if (h < 1) return h < 1 / 60 ? '< 1 min' : `${Math.round(h * 60)} min`;
  if (h >= 36) return `${(h / 24).toFixed(1)} d`;
  return `${h < 10 ? h.toFixed(1).replace(/\.0$/, '') : Math.round(h)} h`;
}

export function funnelSteps(t: AnalyticsTotals) {
  const share = (part: number, whole: number) => (whole === 0 ? null : part / whole);
  return [
    { label: 'Uploaded', value: t.resumesUploaded, ofPrevious: null },
    { label: 'Scored', value: t.resumesScored, ofPrevious: share(t.resumesScored, t.resumesUploaded) },
    { label: 'Shortlisted', value: t.shortlisted, ofPrevious: share(t.shortlisted, t.resumesScored) },
  ];
}
