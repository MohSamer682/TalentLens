import { describe, expect, it } from 'vitest';
import {
  can,
  canChangeSession,
  canReviewSession,
  canSeeSessions,
  canSwitchScope,
  defaultScope,
  diffGrants,
  forcedPath,
  homePath,
} from './permissions';
import type { Permission, User } from '@/types';

const ALL: Permission[] = [
  'SESSION_RUN',
  'SESSION_VIEW_ALL',
  'CANDIDATE_REVIEW',
  'ANALYTICS_VIEW',
  'USER_MANAGE',
  'ROLE_MANAGE',
  'AUDIT_VIEW',
];

const user = (role: User['role'], permissions: Permission[], id = 'u1'): User => ({
  id,
  email: 'u@example.com',
  fullName: 'U',
  role,
  createdAt: '',
  permissions,
});

describe('can', () => {
  it('is false without a user', () => expect(can(null, 'SESSION_RUN')).toBe(false));
  it('is false for a stored user from before permissions existed', () =>
    expect(can({ ...user('RECRUITER', []), permissions: undefined }, 'SESSION_RUN')).toBe(false));
  it('follows the granted list', () => {
    const hr = user('HR_CHIEF', ['ANALYTICS_VIEW']);
    expect(can(hr, 'ANALYTICS_VIEW')).toBe(true);
    expect(can(hr, 'ROLE_MANAGE')).toBe(false);
  });
});

describe('homePath', () => {
  it('sends recruiters to sessions', () =>
    expect(homePath(user('RECRUITER', ['SESSION_RUN', 'PIPELINE_MANAGE']))).toBe('/dashboard'));
  it('sends interviewers to My interviews and keeps them out of sessions', () => {
    const interviewer = user('TECHNICAL_INTERVIEWER', ['INTERVIEW_FEEDBACK']);
    expect(homePath(interviewer)).toBe('/interviews');
    expect(canSeeSessions(interviewer)).toBe(false);
  });
  it('treats the department director, CEO and chairman the same way', () => {
    expect(homePath(user('DEPARTMENT_DIRECTOR', ['INTERVIEW_FEEDBACK', 'OFFER_APPROVE']))).toBe('/interviews');
    expect(homePath(user('CEO', ['INTERVIEW_FEEDBACK']))).toBe('/interviews');
    expect(homePath(user('CHAIRMAN', ['INTERVIEW_FEEDBACK']))).toBe('/interviews');
  });
  it('lets an HR chief see sessions', () =>
    expect(canSeeSessions(user('HR_CHIEF', ['SESSION_VIEW_ALL']))).toBe(true));
  it('signs out a user with no rights', () => expect(homePath(user('RECRUITER', []))).toBe('/login'));
});

describe('canChangeSession', () => {
  it('lets the owner with SESSION_RUN change it', () =>
    expect(canChangeSession(user('RECRUITER', ['SESSION_RUN']), { ownerId: 'u1' })).toBe(true));
  it('refuses an HR chief who can only view it', () =>
    expect(canChangeSession(user('HR_CHIEF', ['SESSION_RUN', 'SESSION_VIEW_ALL']), { ownerId: 'other' })).toBe(false));
  it('lets an admin change anyone’s', () =>
    expect(canChangeSession(user('ADMIN', ['SESSION_RUN']), { ownerId: 'other' })).toBe(true));
  it('refuses the owner without SESSION_RUN', () =>
    expect(canChangeSession(user('RECRUITER', ['CANDIDATE_REVIEW']), { ownerId: 'u1' })).toBe(false));
  it('reviewing needs CANDIDATE_REVIEW', () =>
    expect(canReviewSession(user('RECRUITER', ['SESSION_RUN']), { ownerId: 'u1' })).toBe(false));
});

describe('diffGrants', () => {
  it('lists added and removed, ignoring order', () =>
    expect(diffGrants(['SESSION_RUN', 'ANALYTICS_VIEW'], ['CANDIDATE_REVIEW', 'SESSION_RUN'])).toEqual({
      added: ['CANDIDATE_REVIEW'],
      removed: ['ANALYTICS_VIEW'],
    }));
  it('is empty when nothing changed', () =>
    expect(diffGrants(['SESSION_RUN', 'ANALYTICS_VIEW'], ['ANALYTICS_VIEW', 'SESSION_RUN'])).toEqual({ added: [], removed: [] }));
});

describe('defaultScope & canSwitchScope', () => {
  it('defaultScopeIsEveryoneForViewers', () => {
    expect(defaultScope(user('HR_CHIEF', ['SESSION_VIEW_ALL', 'ANALYTICS_VIEW']))).toBe('EVERYONE');
    expect(canSwitchScope(user('HR_CHIEF', ['SESSION_VIEW_ALL', 'ANALYTICS_VIEW']))).toBe(false);
    expect(defaultScope(user('ADMIN', ALL))).toBe('MINE');
    expect(canSwitchScope(user('ADMIN', ALL))).toBe(true);
    expect(defaultScope(user('RECRUITER', ['SESSION_RUN', 'CANDIDATE_REVIEW']))).toBe('MINE');
  });
});

describe('forcedPath', () => {
  it('sends a user with a temporary password to choose a new one', () => {
    expect(forcedPath({ ...user('RECRUITER', ['SESSION_RUN']), mustChangePassword: true }, '/dashboard')).toBe('/profile/password');
  });
  it('lets them stay on the password page', () => {
    expect(forcedPath({ ...user('RECRUITER', ['SESSION_RUN']), mustChangePassword: true }, '/profile/password')).toBeNull();
  });
  it('leaves everyone else alone', () => {
    expect(forcedPath(user('RECRUITER', ['SESSION_RUN']), '/dashboard')).toBeNull();
  });
});
