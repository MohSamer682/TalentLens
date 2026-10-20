import type { BadgeTone } from '@/components/ui/Badge';
import type { PortalStage, StepState } from '@/types';

export const STAGE_LABEL: Record<PortalStage, string> = {
  APPLIED: 'Applied',
  SHORTLISTED: 'Shortlisted',
  INTERVIEWING: 'Interviews',
  DECISION_PENDING: 'Decision in progress',
  OFFER: 'Offer',
  ACCEPTED: 'Offer accepted',
  JOINING: 'Joining',
  CLOSED: 'Closed',
};

export const STAGE_TONE: Record<PortalStage, BadgeTone> = {
  APPLIED: 'neutral',
  SHORTLISTED: 'info',
  INTERVIEWING: 'info',
  DECISION_PENDING: 'warn',
  OFFER: 'mark',
  ACCEPTED: 'success',
  JOINING: 'success',
  CLOSED: 'neutral',
};

export const STEP_LABEL: Record<StepState, string> = {
  PLANNED: 'Not scheduled yet',
  SCHEDULED: 'Scheduled',
  DONE: 'Done',
  CANCELLED: 'Cancelled',
};

const WEB_LINK = /^https?:\/\/\S+$/i;

/** Only a plain http(s) address becomes a clickable meeting link. */
export function meetingLink(location: string | null | undefined): string | null {
  const text = location?.trim() ?? '';
  return WEB_LINK.test(text) ? text : null;
}

export function browserZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

/** "Africa/Cairo" reads as "Cairo time"; zones without a city show their offset or name. */
export function zoneLabel(zone: string): string {
  // Etc/GMT-3 is UTC+3 (the sign is inverted), so let Intl name the offset
  if (zone.startsWith('Etc/')) return parts(new Date(), zone, { timeZoneName: 'shortOffset' })('timeZoneName');
  if (!zone.includes('/')) return zone;
  return `${zone.slice(zone.lastIndexOf('/') + 1).replace(/_/g, ' ')} time`;
}

function parts(date: Date, zone: string, options: Intl.DateTimeFormatOptions) {
  const found = new Intl.DateTimeFormat('en-GB', { timeZone: zone, ...options }).formatToParts(date);
  return (type: string) => found.find((p) => p.type === type)?.value ?? '';
}

const day = (date: Date, zone: string) => {
  const pick = parts(date, zone, { weekday: 'short', day: 'numeric', month: 'short' });
  return `${pick('weekday')} ${pick('day')} ${pick('month')}`;
};

const time = (date: Date, zone: string) => {
  const pick = parts(date, zone, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
  return `${pick('hour')}:${pick('minute')}`;
};

/** "Wed 28 Oct, 10:00 to 11:00 (Cairo time)", in the candidate's own zone. */
export function formatSlot(start: string, minutes: number, zone = browserZone()): string {
  const from = new Date(start);
  const to = new Date(from.getTime() + minutes * 60_000);
  return `${day(from, zone)}, ${time(from, zone)} to ${time(to, zone)} (${zoneLabel(zone)})`;
}

/** "Wed 28 Oct, 10:00 (Cairo time)" */
export function formatStart(start: string, zone = browserZone()): string {
  const from = new Date(start);
  return `${day(from, zone)}, ${time(from, zone)} (${zoneLabel(zone)})`;
}
