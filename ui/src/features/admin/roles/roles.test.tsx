import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { PermissionGroups } from './PermissionGroups';
import { MoveMembersSheet } from './MoveMembersSheet';
import { adminApi } from '@/api/client';
import type { Permission, RolesCatalog } from '@/types';

vi.mock('@/api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/client')>();
  return { ...actual, adminApi: { listUsers: vi.fn(), moveMembers: vi.fn() } };
});

const api = vi.mocked(adminApi);

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

const ADMIN_ONLY: Permission[] = ['USER_MANAGE', 'ROLE_MANAGE', 'AUDIT_VIEW'];
const catalog: RolesCatalog['permissions'] = (
  ['SESSION_RUN', 'SESSION_VIEW_ALL', 'CANDIDATE_REVIEW', 'PIPELINE_MANAGE', 'OFFER_APPROVE', 'INTERVIEW_FEEDBACK', 'ANALYTICS_VIEW', ...ADMIN_ONLY] as Permission[]
).map((name) => ({ name, description: `${name} description`, adminOnly: ADMIN_ONLY.includes(name) }));

describe('PermissionGroups', () => {
  it('PermissionGroups locks administration permissions', () => {
    render(<PermissionGroups catalog={catalog} saved={['SESSION_RUN']} value={['SESSION_RUN']} editable onToggle={vi.fn()} />);
    expect(screen.getByText("Permissions for administrators can't be given to other roles.")).toBeTruthy();
    const manageUsers = screen.getByRole('checkbox', { name: /Manage users/ }) as HTMLInputElement;
    expect(manageUsers.disabled).toBe(true);
    expect(manageUsers.checked).toBe(false);
    const run = screen.getByRole('checkbox', { name: /Run sessions/ }) as HTMLInputElement;
    expect(run.disabled).toBe(false);
    expect(run.checked).toBe(true);
    expect(['Screening', 'Hiring', 'Insight', 'Administration'].every((g) => screen.getByText(g))).toBe(true);
  });
});

describe('MoveMembersSheet', () => {
  it('MoveMembersSheet disables yourself', async () => {
    api.listUsers.mockResolvedValue({
      data: {
        content: [
          { id: 'me', email: 'me@t.io', fullName: 'Muhammed Samer', role: 'ADMIN', status: 'ACTIVE', createdAt: '' },
          { id: 'u2', email: 'sara@t.io', fullName: 'Sara Adel', role: 'RECRUITER', status: 'ACTIVE', createdAt: '' },
          { id: 'u3', email: 'nour@t.io', fullName: 'Nour Hassan', role: 'CANDIDATE', status: 'ACTIVE', createdAt: '' },
        ],
        totalElements: 3,
        totalPages: 1,
        number: 0,
        size: 20,
      },
    } as never);
    render(<MoveMembersSheet role={{ role: 'TECHNICAL_INTERVIEWER', name: 'Technical interviewer', kind: 'STAFF' }} meId="me" onMoved={vi.fn()} onCancel={vi.fn()} />);

    const mine = (await screen.findByRole('checkbox', { name: /Muhammed Samer/ })) as HTMLInputElement;
    expect(mine.disabled).toBe(true);
    expect(screen.getByText("That's you")).toBeTruthy();
    expect((screen.getByRole('checkbox', { name: /Sara Adel/ }) as HTMLInputElement).disabled).toBe(false);
    expect(screen.queryByText('Nour Hassan')).toBeNull();
  });
});
