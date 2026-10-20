import { useAuthStore } from '@/features/auth/authStore';
import type { Permission, ScreeningSession, User } from '@/types';

/** Short names for the matrix and the unsaved-changes summary; descriptions come from the server. */
export const PERMISSION_LABEL: Record<Permission, string> = {
  SESSION_RUN: 'Run sessions',
  SESSION_VIEW_ALL: 'Open any session',
  CANDIDATE_REVIEW: 'Shortlist candidates',
  PIPELINE_MANAGE: 'Manage hiring pipeline',
  OFFER_APPROVE: 'Approve job offers',
  INTERVIEW_FEEDBACK: 'Give interview feedback',
  ANALYTICS_VIEW: 'Analytics',
  USER_MANAGE: 'Manage users',
  ROLE_MANAGE: 'Manage roles',
  AUDIT_VIEW: 'Audit log',
};

/** The server enforces every rule; this only decides what to show. */
export function can(user: User | null | undefined, permission: Permission): boolean {
  return !!user?.permissions?.includes(permission);
}

export const hasPermission = can;

export function useCan(permission: Permission): boolean {
  return useAuthStore((s) => can(s.user, permission));
}

/** Changing a session needs SESSION_RUN and ownership; admins may change anyone's. */
export function canChangeSession(user: User | null | undefined, session: Pick<ScreeningSession, 'ownerId'>): boolean {
  return can(user, 'SESSION_RUN') && (user?.role === 'ADMIN' || session.ownerId === user?.id);
}

export function canReviewSession(user: User | null | undefined, session: Pick<ScreeningSession, 'ownerId'>): boolean {
  return can(user, 'CANDIDATE_REVIEW') && (user?.role === 'ADMIN' || session.ownerId === user?.id);
}

export function diffGrants(before: Permission[], after: Permission[]) {
  return {
    added: after.filter((p) => !before.includes(p)),
    removed: before.filter((p) => !after.includes(p)),
  };
}

/** Users who can see everyone's sessions but cannot create them land on all sessions. */
export function defaultScope(user: User | null | undefined): 'MINE' | 'EVERYONE' {
  return can(user, 'SESSION_VIEW_ALL') && !can(user, 'SESSION_RUN') ? 'EVERYONE' : 'MINE';
}

/** Sessions are for people who run or oversee screening; interviewers never see them. */
export function canSeeSessions(user: User | null | undefined): boolean {
  return can(user, 'SESSION_RUN') || can(user, 'SESSION_VIEW_ALL');
}

/** Where a user lands: sessions for recruiters, My interviews for people who only interview. */
export function homePath(user: User | null | undefined): string {
  if (canSeeSessions(user)) return '/dashboard';
  if (can(user, 'INTERVIEW_FEEDBACK')) return '/interviews';
  if (can(user, 'PIPELINE_MANAGE') || can(user, 'OFFER_APPROVE')) return '/pipeline';
  if (can(user, 'ANALYTICS_VIEW')) return '/analytics';
  if (can(user, 'USER_MANAGE') || can(user, 'ROLE_MANAGE')) return '/admin/users';
  return '/login';
}

/** Only users with both rights get the Mine / All recruiters switch. */
export function canSwitchScope(user: User | null | undefined): boolean {
  return can(user, 'SESSION_VIEW_ALL') && can(user, 'SESSION_RUN');
}
