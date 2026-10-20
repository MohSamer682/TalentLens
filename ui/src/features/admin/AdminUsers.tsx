import { useCallback, useEffect, useState } from 'react';
import { adminApi, apiErrorMessage } from '@/api/client';
import type { User } from '@/types';
import { useAuthStore } from '@/features/auth/authStore';
import { Panel } from '@/components/ui/Card';
import { Alert, SkeletonRows } from '@/components/ui/Feedback';
import { Pagination } from '@/components/ui/Pagination';
import { Button } from '@/components/ui/Button';
import { MainLayout, PageHeader } from '@/layouts/MainLayout';
import { AdminTabs } from '@/features/admin/AdminTabs';
import { ROLE_LABEL, formatDate, formatRelativeDate, getInitials } from '@/lib/utils';
import { stagger } from '@/lib/motion';

const PAGE_SIZE = 20;

export default function AdminUsers() {
  const me = useAuthStore((s) => s.user);
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [savingId, setSavingId] = useState<string | null>(null);

  const loadUsers = useCallback(async () => {
    try {
      const res = await adminApi.listUsers(page, PAGE_SIZE);
      setUsers(res.data.content || []);
      setTotal(res.data.totalElements ?? 0);
      setTotalPages(res.data.totalPages ?? 0);
      setError(null);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not load users'));
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    loadUsers();
  }, [loadUsers]);

  const handleRoleChange = async (userId: string, role: User['role']) => {
    setError(null);
    setSavingId(userId);
    try {
      const res = await adminApi.updateRole(userId, role);
      setUsers((current) => current.map((u) => (u.id === userId ? res.data : u)));
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not change the role'));
    } finally {
      setSavingId(null);
    }
  };

  const head = 'px-3 py-2 text-left text-[13px] font-medium text-ink-muted';

  return (
    <MainLayout>
      <PageHeader
        title="Users"
        meta={!loading && <span className="num">{total} account{total === 1 ? '' : 's'}. What each role can do is set under Roles and permissions.</span>}
      />
      <AdminTabs />

      {error && (
        <Alert
          className="mb-4"
          action={!users.length && <Button variant="secondary" size="sm" onClick={() => { setLoading(true); loadUsers(); }}>Try again</Button>}
        >
          {error}
        </Alert>
      )}

      {loading ? (
        <Panel>
          <SkeletonRows rows={4} columns={[22, 26, 10, 12]} />
        </Panel>
      ) : users.length > 0 ? (
        <Panel>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[560px] text-sm">
              <thead className="border-b border-line bg-sunken">
                <tr>
                  <th scope="col" className={`${head} pl-4 sm:pl-5`}>Name</th>
                  <th scope="col" className={head}>Email</th>
                  <th scope="col" className={head}>Joined</th>
                  <th scope="col" className={`${head} pr-4 sm:pr-5`}>Role</th>
                </tr>
              </thead>
              <tbody>
                {users.map((user) => {
                  const isMe = user.id === me?.id;
                  return (
                    <tr key={user.id} style={stagger(users.indexOf(user))} className="animate-fade-up border-b border-line transition-colors last:border-b-0 hover:bg-ground">
                      <td className="py-2.5 pl-4 pr-3 sm:pl-5">
                        <div className="flex items-center gap-3">
                          <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded bg-sunken text-[13px] font-bold text-ink-muted">
                            {getInitials(user.fullName)}
                          </span>
                          <span className="font-semibold">
                            {user.fullName}
                            {isMe && <span className="ml-1.5 font-normal text-ink-muted">(you)</span>}
                          </span>
                        </div>
                      </td>
                      <td className="px-3 py-2.5 text-ink-muted">{user.email}</td>
                      <td className="px-3 py-2.5 text-ink-muted" title={formatDate(user.createdAt)}>
                        {formatRelativeDate(user.createdAt)}
                      </td>
                      <td className="py-2.5 pl-3 pr-4 sm:pr-5">
                        <select
                          value={user.role}
                          disabled={savingId === user.id || isMe}
                          title={isMe ? 'You cannot change your own role, so at least one admin always remains' : undefined}
                          aria-label={`Role for ${user.fullName}`}
                          onChange={(e) => handleRoleChange(user.id, e.target.value as User['role'])}
                          className="h-8 rounded-md border border-line-strong bg-surface px-2 text-sm font-semibold focus:border-accent-text focus:outline-none focus:ring-4 focus:ring-accent/25 disabled:bg-sunken disabled:text-ink-muted"
                        >
                          {(Object.keys(ROLE_LABEL) as User['role'][]).map((r) => (
                            <option key={r} value={r}>
                              {ROLE_LABEL[r]}
                            </option>
                          ))}
                        </select>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="flex flex-col items-center justify-between gap-3 border-t border-line px-4 py-3 text-sm text-ink-muted sm:flex-row sm:px-5">
            <span className="num">
              {page * PAGE_SIZE + 1}–{page * PAGE_SIZE + users.length} of {total}
            </span>
            <Pagination page={page} totalPages={totalPages} onPage={setPage} />
          </div>
        </Panel>
      ) : (
        !error && <Panel className="px-6 py-12 text-center text-sm text-ink-muted">No user accounts yet.</Panel>
      )}
    </MainLayout>
  );
}
