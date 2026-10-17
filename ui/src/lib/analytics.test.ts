import { describe, expect, it } from 'vitest';
import { formatHours, formatRate, funnelSteps } from './analytics';
import type { AnalyticsTotals } from '@/types';

const totals = (uploaded: number, scored: number, shortlisted: number): AnalyticsTotals => ({
  sessions: 1,
  sessionsByStatus: { PENDING: 0, RUNNING: 0, DONE: 1, FAILED: 0 },
  resumesUploaded: uploaded,
  resumesScored: scored,
  resumesUnreadable: uploaded - scored,
  shortlisted,
  shortlistRate: null,
  medianHoursToRanked: null,
});

describe('formatRate', () => {
  it('shows one decimal', () => expect(formatRate(0.4)).toBe('40.0%'));
  it('shows a dash with nothing scored', () => expect(formatRate(null)).toBe('–'));
});

describe('formatHours', () => {
  it('keeps short times in hours', () => expect(formatHours(4)).toBe('4 h'));
  it('keeps one decimal under 10 hours', () => expect(formatHours(3.4)).toBe('3.4 h'));
  it('switches to days at 36 hours', () => expect(formatHours(36)).toBe('1.5 d'));
  it('shows minutes under an hour', () => expect(formatHours(0.25)).toBe('15 min'));
  it('floors very fast runs at a minute', () => expect(formatHours(0.0014)).toBe('< 1 min'));
  it('shows a dash when nothing is ranked', () => expect(formatHours(null)).toBe('–'));
});

describe('funnelSteps', () => {
  it('relates each step to the one before', () =>
    expect(funnelSteps(totals(10, 8, 2)).map((s) => s.ofPrevious)).toEqual([null, 0.8, 0.25]));
  it('has no shares when nothing was uploaded', () =>
    expect(funnelSteps(totals(0, 0, 0)).map((s) => s.ofPrevious)).toEqual([null, null, null]));
});
