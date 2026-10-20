import { describe, expect, it } from 'vitest';
import type { CandidateInterview } from '@/types';
import { buildTrack, madeTheCut, settledStage, stageTone } from './pipeline';

const held = (kind: CandidateInterview['kind']) => ({ kind, status: 'COMPLETED' }) as CandidateInterview;

describe('buildTrack', () => {
  it('shows only the interviews the recruiter planned', () => {
    const labels = buildTrack('CONTACTED', ['HR', 'MANAGEMENT'], []).map((s) => s.label);
    expect(labels).toContain('HR interview');
    expect(labels).toContain('Management interview');
    expect(labels).not.toContain('Technical interview');
  });

  it('marks the first unfinished planned interview as current', () => {
    const track = buildTrack('INTERVIEW_SCHEDULED', ['HR', 'TECHNICAL', 'MANAGEMENT'], [held('HR')]);
    const states = Object.fromEntries(track.map((s) => [s.key, s.state]));
    expect(states.INTERVIEW_HR).toBe('done');
    expect(states.INTERVIEW_TECHNICAL).toBe('current');
    expect(states.INTERVIEW_MANAGEMENT).toBe('todo');
  });

  it('falls back to one Interviews step for candidates planned before plans existed', () => {
    const track = buildTrack('INTERVIEW_SCHEDULED', [], []);
    expect(track.filter((s) => s.key === 'INTERVIEWS')).toHaveLength(1);
  });

  it('carries on past the offer: sent, answer, joining', () => {
    const track = buildTrack('ACCEPTED', ['HR'], [held('HR')]);
    const states = Object.fromEntries(track.map((s) => [s.key, s.state]));
    expect(states.OFFER_SENT).toBe('done');
    expect(states.ANSWER).toBe('current');
    expect(states.JOINING_SET).toBe('todo');
  });

  it('names a declined answer as Declined', () => {
    expect(buildTrack('DECLINED', [], []).find((s) => s.key === 'ANSWER')?.label).toBe('Declined');
  });
});

describe('settledStage', () => {
  const waiting = { kind: 'TECHNICAL', status: 'SCHEDULED' } as CandidateInterview;

  it('stays interviewing while a planned kind is still to be held', () =>
    expect(settledStage('INTERVIEW_SCHEDULED', ['HR', 'TECHNICAL'], [held('HR')])).toBe('INTERVIEW_SCHEDULED'));
  it('moves on once every planned kind is held', () =>
    expect(settledStage('INTERVIEW_SCHEDULED', ['HR'], [held('HR')])).toBe('INTERVIEWED'));
  it('waits while an interview is still scheduled', () =>
    expect(settledStage('INTERVIEW_SCHEDULED', ['HR'], [held('HR'), waiting])).toBe('INTERVIEW_SCHEDULED'));
  it('needs one held interview when there is no plan', () => {
    expect(settledStage('INTERVIEW_SCHEDULED', [], [])).toBe('INTERVIEW_SCHEDULED');
    expect(settledStage('INTERVIEW_SCHEDULED', [], [held('HR')])).toBe('INTERVIEWED');
  });
  it('leaves every other stage alone', () => expect(settledStage('OFFER', ['HR'], [held('HR')])).toBe('OFFER'));
});

describe('stage helpers', () => {
  it('counts every stage after screening as made the cut', () => {
    expect(madeTheCut('NONE')).toBe(false);
    expect(madeTheCut('SHORTLISTED')).toBe(true);
    expect(madeTheCut('JOINING_SET')).toBe(true);
  });

  it('keeps saffron for shortlisted only', () => {
    expect(stageTone('SHORTLISTED')).toBe('mark');
    expect(stageTone('CONTACTED')).not.toBe('mark');
  });
});
