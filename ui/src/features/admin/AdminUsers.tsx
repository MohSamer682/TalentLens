import { useCallback, useEffect, useState } from 'react';
import { Plus } from 'lucide-react';
import { adminApi, apiErrorMessage } from '@/api/client';
import type { AdminUser, Role, UserFormValues, UserRow, UserStatus } from '@/types';
import { useAuthStore } from '@/features/auth/authStore';
import { Panel } from '@/components/ui/Card';
import { Alert, SkeletonRows } from '@/components/ui/Feedback';
import { Pagination } from '@/components/ui/Pagination';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Input } from '@/components/ui/Input';
import { Segmented } from '@/components/ui/Segmented';
import { MainLayout, PageHeader } from '@/layouts/MainLayout';
import { AdminTabs } from '@/features/admin/AdminTabs';
import { AdminUserForm } from '@/features/admin/AdminUserForm';
import { ROLE_LABEL, cn, getInitials } from '@/lib/utils';

const PAGE_SIZE = 20;
type StatusFilter = UserStatus | 'ALL';

export default function AdminUsers() {
  const me = useAuthStore((s) => s.user);
  const [rows, setRows] = useState<UserRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<StatusFilter>('ACTIVE');
  const [role, setRole] = useState<Role | ''>('');
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  // null: panel closed, 'new': creating, otherwise the account being edited
  const [editing, setEditing] = useState<AdminUser | 'new' | null>(null);
  const [formKey, setFormKey] = useState(0);

  const load = useCallback(async () => {
    try {
      const res = await adminApi.listUsers({
        page,
        size: PAGE_SIZE,
        q: query.trim() || undefined,
        status: status === 'ALL' ? undefined : status,
        role: role || undefined,
      });
      setRows(res.data.content || []);
      setTotal(res.data.totalElements ?? 0);
      setTotalPages(res.data.totalPages ?? 0);
      setError(null);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not load users'));
    } finally {
      setLoading(false);
    }
  }, [page, query, status, role]);

  useEffect(() => {
    const t = window.setTimeout(load, query ? 250 : 0);
    return () => window.clearTimeout(t);
  }, [load, query]);

  const open = async (id: string) => {
    setNotice(null);
    try {
      const res = await adminApi.getUser(id);
      setEditing(res.data);
      setFormKey((k) => k + 1);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not open this account'));
    }
  };

  // Every action reports through the same banner, then refreshes the list so it shows the saved state
  const act = async (work: () => Promise<string | void>, failure: string) => {
    setError(null);
    try {
      const done = await work();
      if (done) setNotice(done);
      await load();
    } catch (err) {
      setError(apiErrorMessage(err, failure));
    }
  };

  const save = (values: UserFormValues) =>
    act(async () => {
      if (editing === 'new') {
        const res = await adminApi.createUser(values);
        setEditing(res.data);
        setFormKey((k) => k + 1);
        return `${res.data.fullName} can now sign in with the temporary password.`;
      }
      if (editing) {
        const res = await adminApi.updateUser(editing.id, { ...values, version: editing.version });
        setEditing(res.data);
        setFormKey((k) => k + 1);
        return 'Saved.';
      }
    }, 'Could not save this account');

  const edited = editing && editing !== 'new' ? editing : null;
  const head = 'px-3 py-2 text-left text-[13px] font-medium text-ink-muted';

  return (
    <MainLayout>
      <PageHeader
        title="Users"
        meta={!loading && <span className="num">{total} account{total === 1 ? '' : 's'}. What each role can do is set under Roles and permissions.</span>}
        actions={
          <Button onClick={() => { setEditing('new'); setFormKey((k) => k + 1); setNotice(null); }}>
            <Plus aria-hidden="true" />
            New user
          </Button>
        }
      />
      <AdminTabs />

      {error && <Alert className="mb-4">{error}</Alert>}
      {notice && (
        <Alert tone="success" className="mb-4" action={<Button variant="ghost" size="sm" onClick={() => setNotice(null)}>Dismiss</Button>}>
          {notice}
        </Alert>
      )}

      <div className="flex flex-wrap items-start gap-6">
        <section className="min-w-0 flex-[999_1_620px] space-y-4">
          <div className="flex flex-wrap items-center gap-3">
            <label htmlFor="user-search" className="sr-only">Search users</label>
            <Input
              id="user-search"
              type="search"
              placeholder="Search name or email"
              value={query}
              onChange={(e) => { setQuery(e.target.value); setPage(0); }}
              className="w-full sm:w-64"
            />
            <Segmented<StatusFilter>
              label="Status"
              value={status}
              onChange={(v) => { setStatus(v); setPage(0); }}
              options={[{ value: 'ACTIVE', label: 'Active' }, { value: 'HIDDEN', label: 'Hidden' }, { value: 'ALL', label: 'All' }]}
            />
            <label htmlFor="user-role" className="sr-only">Role</label>
            <select
              id="user-role"
              value={role}
              onChange={(e) => { setRole(e.target.value as Role | ''); setPage(0); }}
              className="h-10 rounded-md border border-line bg-surface px-3 text-sm text-ink focus:border-accent-text focus:outline-none focus:ring-2 focus:ring-accent-text/25 dark:bg-sunken"
            >
              <option value="">All roles</option>
              {(Object.keys(ROLE_LABEL) as Role[]).map((r) => (
                <option key={r} value={r}>{ROLE_LABEL[r]}</option>
              ))}
            </select>
          </div>

          {loading ? (
            <Panel>
              <SkeletonRows rows={4} columns={[24, 12, 10, 18]} />
            </Panel>
          ) : rows.length > 0 ? (
            <Panel>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[560px] text-sm">
                  <thead className="border-b border-line bg-sunken">
                    <tr>
                      <th scope="col" className={`${head} pl-4 sm:pl-5`}>Name</th>
                      <th scope="col" className={head}>Role</th>
                      <th scope="col" className={head}>Status</th>
                      <th scope="col" className={head}>Job title</th>
                      <th scope="col" className={`${head} pr-4 sm:pr-5`}><span className="sr-only">Actions</span></th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((u) => (
                      <tr key={u.id} className={cn('border-b border-line last:border-b-0', edited?.id === u.id && 'bg-sunken')}>
                        <td className="py-2.5 pl-4 pr-3 sm:pl-5">
                          <div className="flex items-center gap-3">
                            <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded bg-sunken text-[13px] font-bold text-ink-muted">
                              {getInitials(u.fullName)}
                            </span>
                            <span className="min-w-0">
                              <span className="block font-semibold">
                                {u.fullName}
                                {u.id === me?.id && <span className="ml-1.5 font-normal text-ink-muted">(you)</span>}
                              </span>
                              <span className="block truncate text-[13px] text-ink-muted">{u.email}</span>
                            </span>
                          </div>
                        </td>
                        <td className="px-3 py-2.5">{ROLE_LABEL[u.role] ?? u.role}</td>
                        <td className="px-3 py-2.5">
                          <Badge tone={u.status === 'ACTIVE' ? 'success' : 'neutral'}>{u.status === 'ACTIVE' ? 'Active' : 'Hidden'}</Badge>
                        </td>
                        <td className="px-3 py-2.5 text-ink-muted">{u.headline}</td>
                        <td className="py-2.5 pl-3 pr-4 text-right sm:pr-5">
                          <Button variant="secondary" size="sm" onClick={() => open(u.id)} aria-label={`Edit ${u.fullName}`}>
                            Edit
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="flex flex-col items-center justify-between gap-3 border-t border-line px-4 py-3 text-sm text-ink-muted sm:flex-row sm:px-5">
                <span className="num">
                  {page * PAGE_SIZE + 1}–{page * PAGE_SIZE + rows.length} of {total}
                </span>
                <Pagination page={page} totalPages={totalPages} onPage={setPage} />
              </div>
            </Panel>
          ) : (
            !error && <Panel className="px-6 py-12 text-center text-sm text-ink-muted">No accounts match these filters.</Panel>
          )}
        </section>

        {editing && (
          <aside className="min-w-0 flex-[1_1_380px]">
            <AdminUserForm
              key={formKey}
              user={edited}
              isMe={edited?.id === me?.id}
              onSave={save}
              onClose={() => setEditing(null)}
              onStatus={(next) =>
                act(async () => {
                  const res = await adminApi.setStatus(edited!.id, next);
                  setEditing(res.data);
                  setFormKey((k) => k + 1);
                  return next === 'HIDDEN' ? `${res.data.fullName} is hidden and signed out.` : `${res.data.fullName} can sign in again.`;
                }, 'Could not change this account')
              }
              onResetPassword={(pw) =>
                act(async () => {
                  await adminApi.resetPassword(edited!.id, pw);
                  return `Temporary password set. ${edited!.fullName} must change it at next sign-in.`;
                }, 'Could not reset the password')
              }
              onDelete={() =>
                act(async () => {
                  await adminApi.deleteUser(edited!.id);
                  setEditing(null);
                  return `${edited!.fullName}'s account was deleted.`;
                }, 'Could not delete this account')
              }
            />
          </aside>
        )}
      </div>
    </MainLayout>
  );
}
