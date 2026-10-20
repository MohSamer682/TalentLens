import { afterEach, describe, it, expect, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { AdminUserForm } from './AdminUserForm';
import type { AdminUser } from '@/types';

afterEach(cleanup);

const karim: AdminUser = {
  id: 'u2', email: 'karim@t.io', fullName: 'Karim Adel', role: 'RECRUITER', status: 'ACTIVE', mustChangePassword: false,
  headline: 'Senior Recruiter', department: 'People', version: 3, createdAt: '2026-05-03T10:00:00Z',
};

const noop = vi.fn(async () => undefined);
const props = { onSave: noop, onClose: vi.fn(), onStatus: noop, onResetPassword: noop, onDelete: noop };

describe('AdminUserForm', () => {
  it('offers hide, reset and delete for someone else', () => {
    render(<AdminUserForm user={karim} isMe={false} {...props} />);
    expect(screen.getByRole('button', { name: 'Hide' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Reset password' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Delete' })).toBeTruthy();
  });

  it('hides account actions on your own account and locks your role', () => {
    render(<AdminUserForm user={karim} isMe {...props} />);
    expect(screen.queryByRole('button', { name: 'Hide' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Delete' })).toBeNull();
    expect((screen.getByLabelText('Role') as HTMLSelectElement).disabled).toBe(true);
  });

  it('asks for a temporary password only when creating', () => {
    render(<AdminUserForm user={null} isMe={false} {...props} />);
    expect(screen.getByLabelText('Temporary password')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Delete' })).toBeNull();
  });

  it('never offers the candidate role to staff', () => {
    render(<AdminUserForm user={null} isMe={false} {...props} />);
    const options = Array.from((screen.getByLabelText('Role') as HTMLSelectElement).options).map((o) => o.value);
    expect(options).not.toContain('CANDIDATE');
  });

  it('locks the role of a candidate account', () => {
    render(<AdminUserForm user={{ ...karim, role: 'CANDIDATE' }} isMe={false} {...props} />);
    const select = screen.getByLabelText('Role') as HTMLSelectElement;
    expect(select.disabled).toBe(true);
    expect(select.value).toBe('CANDIDATE');
  });

  it('offers Unhide for a hidden account', () => {
    render(<AdminUserForm user={{ ...karim, status: 'HIDDEN' }} isMe={false} {...props} />);
    expect(screen.getByRole('button', { name: 'Unhide' })).toBeTruthy();
  });
});
