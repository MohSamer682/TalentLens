import { useCallback, useEffect, useState } from 'react';
import { Lock } from 'lucide-react';
import { adminApi, apiErrorMessage } from '@/api/client';
import type { Permission, Role, RolesCatalog } from '@/types';
import { AdminTabs } from '@/features/admin/AdminTabs';
import { Button } from '@/components/ui/Button';
import { Panel } from '@/components/ui/Card';
import { Alert, SkeletonRows } from '@/components/ui/Feedback';
import { MainLayout, PageHeader } from '@/layouts/MainLayout';
import { PERMISSION_LABEL, diffGrants } from '@/lib/permissions';
import { ROLE_LABEL, cn } from '@/lib/utils';

type Grants = Record<Role, Permission[]>;

const grantsOf = (catalog: RolesCatalog) =>
  Object.fromEntries(catalog.roles.map((r) => [r.role, r.permissions])) as Grants;

export default function AdminRoles() {
  const [catalog, setCatalog] = useState<RolesCatalog | null>(null);
  const [draft, setDraft] = useState<Grants | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      const { data } = await adminApi.roles();
      setCatalog(data);
      setDraft(grantsOf(data));
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not load roles'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  // Editable roles first: on a phone they are the columns in view, the locked admin column scrolls in last.
  const columns = catalog ? [...catalog.roles].sort((a, b) => Number(b.editable) - Number(a.editable)) : [];

  const changes = catalog && draft
    ? catalog.roles
        .filter((r) => r.editable)
        .map((r) => ({ role: r.role, ...diffGrants(r.permissions, draft[r.role]) }))
        .filter((c) => c.added.length + c.removed.length > 0)
    : [];
  const dirty = changes.length > 0;

  // BrowserRouter has no navigation blocker, so in-app links are caught here and tab closes by beforeunload.
  useEffect(() => {
    if (!dirty) return;
    const onUnload = (e: BeforeUnloadEvent) => e.preventDefault();
    const onClick = (e: MouseEvent) => {
      const link = (e.target as Element).closest('a[href]');
      if (link && !window.confirm('Leave without saving your permission changes?')) {
        e.preventDefault();
        e.stopPropagation();
      }
    };
    window.addEventListener('beforeunload', onUnload);
    document.addEventListener('click', onClick, true);
    return () => {
      window.removeEventListener('beforeunload', onUnload);
      document.removeEventListener('click', onClick, true);
    };
  }, [dirty]);

  const toggle = (role: Role, permission: Permission) => {
    setSaved(false);
    setDraft((d) =>
      d && {
        ...d,
        [role]: d[role].includes(permission) ? d[role].filter((p) => p !== permission) : [...d[role], permission],
      }
    );
  };

  const save = async () => {
    if (!draft) return;
    setSaving(true);
    setError(null);
    try {
      // One request per changed role; each is its own transaction and audit entry.
      for (const c of changes) await adminApi.updatePermissions(c.role, draft[c.role]);
      await load();
      setSaved(true);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save the permissions'));
      await load();
    } finally {
      setSaving(false);
    }
  };

  return (
    <MainLayout>
      <PageHeader
        title="Roles and permissions"
        meta={<span>Choose what each role can do. People see a change on their next action, without signing out.</span>}
      />
      <AdminTabs />

      {saved && !dirty && (
        <Alert tone="success" className="mb-4">
          Permissions saved.
        </Alert>
      )}
      {error && (
        <Alert
          className="mb-4"
          action={!catalog && <Button variant="secondary" size="sm" onClick={() => { setLoading(true); load(); }}>Try again</Button>}
        >
          {error}
        </Alert>
      )}

      {loading ? (
        <Panel>
          <SkeletonRows rows={7} columns={[30, 10, 10, 10]} />
        </Panel>
      ) : (
        catalog &&
        draft && (
          <Panel className="overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full min-w-[400px] text-sm sm:min-w-[520px]">
                <thead className="border-b border-line bg-sunken">
                  <tr>
                    <th scope="col" className="sticky left-0 z-10 bg-sunken px-4 py-3 text-left align-top text-[13px] font-medium text-ink-muted sm:px-5">
                      Permission
                    </th>
                    {columns.map((r) => {
                      const changed = changes.some((c) => c.role === r.role);
                      return (
                        <th key={r.role} scope="col" className="w-20 px-1 py-3 text-center align-top sm:w-36 sm:px-3">
                          <span className="block font-semibold">{ROLE_LABEL[r.role]}</span>
                          <span className="num block text-[12px] font-normal text-ink-muted">
                            {r.userCount} {r.userCount === 1 ? 'person' : 'people'}
                          </span>
                          {changed && (
                            <span className="mt-1 inline-block rounded-full bg-mark-soft px-2 text-[12px] font-semibold leading-5 text-mark-text">
                              Unsaved
                            </span>
                          )}
                        </th>
                      );
                    })}
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {catalog.permissions.map((p) => (
                    <tr key={p.name} className="group transition-colors hover:bg-ground">
                      <th scope="row" className="sticky left-0 z-10 w-40 bg-surface px-4 py-3 text-left font-normal transition-colors group-hover:bg-ground sm:w-auto sm:px-5">
                        <span className="block font-semibold">{PERMISSION_LABEL[p.name]}</span>
                        <span className="block text-[12px] text-ink-muted sm:text-[13px]">{p.description}</span>
                      </th>
                      {columns.map((r) => {
                        const label = `${ROLE_LABEL[r.role]}: ${PERMISSION_LABEL[p.name]}`;
                        if (!r.editable) {
                          return (
                            // relative: keeps the sr-only label inside the scroll area instead of widening the page
                            <td key={r.role} className="relative px-3 py-3 text-center">
                              <span className="inline-flex items-center gap-1 text-ink-muted" title="Admins always have every permission">
                                <Lock className="size-3.5" aria-hidden="true" />
                                <span className="sr-only">{label}, always granted</span>
                              </span>
                            </td>
                          );
                        }
                        if (p.adminOnly) {
                          return (
                            <td key={r.role} className="px-3 py-3 text-center text-[13px] text-ink-muted">
                              Admin only
                            </td>
                          );
                        }
                        const checked = draft[r.role].includes(p.name);
                        const changed = checked !== r.permissions.includes(p.name);
                        return (
                          <td key={r.role} className="px-3 py-3 text-center">
                            <label className="inline-flex size-11 cursor-pointer items-center justify-center rounded-md hover:bg-sunken">
                              <input
                                type="checkbox"
                                checked={checked}
                                disabled={saving}
                                onChange={() => toggle(r.role, p.name)}
                                aria-label={label}
                                className={cn('size-[18px] accent-[rgb(var(--ink))]', changed && 'outline outline-2 outline-offset-2 outline-mark')}
                              />
                            </label>
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="border-t border-line px-4 py-3 text-[13px] text-ink-muted sm:px-5">
              <Lock className="mr-1 inline size-3.5 align-[-2px]" aria-hidden="true" />
              Admins always have every permission. Managing users, roles and the audit log stays with admins.
            </p>

            {dirty && (
              <div
                role="region"
                aria-label="Unsaved changes"
                className="sticky bottom-0 z-20 flex animate-fade-up flex-wrap items-center gap-x-4 gap-y-2 border-t border-line bg-ink px-4 py-3 text-ground sm:px-5"
              >
                <ul className="min-w-0 flex-1 space-y-0.5 text-sm">
                  {changes.map((c) => (
                    <li key={c.role}>
                      <span className="font-semibold">{ROLE_LABEL[c.role]}:</span>{' '}
                      {[...c.added.map((p) => `+${PERMISSION_LABEL[p]}`), ...c.removed.map((p) => `−${PERMISSION_LABEL[p]}`)].join(', ')}
                    </li>
                  ))}
                </ul>
                <div className="flex gap-2">
                  <Button variant="ghost" size="sm" disabled={saving} onClick={() => setDraft(grantsOf(catalog))} className="h-9 border border-ground/40 text-ground hover:bg-ground/10 hover:text-ground">
                    Discard
                  </Button>
                  <Button size="sm" disabled={saving} onClick={save} className="h-9 bg-mark text-mark-ink shadow-none hover:bg-mark/90">
                    {saving ? 'Saving…' : 'Save changes'}
                  </Button>
                </div>
              </div>
            )}
          </Panel>
        )
      )}
    </MainLayout>
  );
}
