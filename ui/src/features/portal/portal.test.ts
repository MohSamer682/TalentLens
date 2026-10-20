import { describe, expect, it } from 'vitest';
import { formatSlot, formatStart, zoneLabel } from './portal';

describe('portal time formatting', () => {
  it('shows the day, start and end in the given zone with its name', () => {
    expect(formatSlot('2026-10-28T08:00:00Z', 60, 'Africa/Cairo')).toBe('Wed 28 Oct, 11:00 to 12:00 (Cairo time)');
  });

  it('shows a start time alone', () => {
    expect(formatStart('2026-10-28T08:00:00Z', 'Africa/Cairo')).toBe('Wed 28 Oct, 11:00 (Cairo time)');
  });

  it('names a zone by its city', () => {
    expect(zoneLabel('America/New_York')).toBe('New York time');
    expect(zoneLabel('UTC')).toBe('UTC');
  });
});
