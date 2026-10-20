import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { ApiRequestError, adminApi, apiErrorMessage } from '@/api/client';
import type { Permission, Role, RoleInfo, RolesCatalog } from '@/types';
import { useAuthStore } from '@/features/auth/authStore';
import { AdminTabs } from '@/features/admin/AdminTabs';
import { Button } from '@/components/ui/Button';
import { Panel, PanelHeader } from '@/components/ui/Card';
import { Alert, Loader } from '@/components/ui/Feedback';
import { Field, Input, Textarea } from '@/components/ui/Input';
import { MainLayout, PageHeader } from '@/layouts/MainLayout';
import { PERMISSION_LABEL, can, diffGrants } from '@/lib/permissions';
import { PermissionGroups } from './PermissionGroups';
import { RoleHistory } from './RoleHistory';
import { RoleMembers } from './RoleMembers';

function NameForm({ role, onSaved, onCancel }: { role: RoleInfo; onSaved: (role: RoleInfo) => void; onCancel: () => void }) {
  const [name, setName] = useState(role.name);
  const [description, setDescription] = useState(role.description ?? '');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const save = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return setError('Give the role a name');
    setSaving(true);
    setError(null);
    try {
      const res = await adminApi.renameRole(role.role, { name: name.trim(), description: description.trim(), version: role.version });
      onSaved(res.data);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save the role'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={save} noValidate className="space-y-4 p-4 sm:px-5">
      <Field id="role-name" label="Name">
        <Input id="role-name" value={name} maxLength={80} onChange={(e) => setName(e.target.value)} />
      </Field>
      <Field id="role-description" label="Description" hint="One line on what people in this role do.">
        <Textarea id="role-description" value={description} maxLength={300} className="min-h-[72px]" onChange={(e) => setDescription(e.target.value)} />
      </Field>
      {error && <Alert>{error}</Alert>}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" size="sm" onClick={onCancel} disabled={saving}>
          Cancel
        </Button>
        <Button type="submit" size="sm" disabled={saving}>
          {saving ? 'Saving…' : 'Save'}
        </Button>
      </div>
    </form>
  );
}

export default function RoleDetail() {
  const { role: code = '' } = useParams();
  const me = useAuthStore((s) => s.user);
  const [role, setRole] = useState<RoleInfo | null>(null);
  const [catalog, setCatalog] = useState<RolesCatalog['permissions'] | null>(null);
  const [draft, setDraft] = useState<Permission[]>([]);
  const [editingName, setEditingName] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [revision, setRevision] = useState(0);

  const load = useCallback(async () => {
    try {
      const [detail, all] = await Promise.all([adminApi.role(code as Role), adminApi.roles()]);
      setRole(detail.data);
      setDraft(detail.data.permissions);
      setCatalog(all.data.permissions);
    } catch (err) {
      setError(err instanceof ApiRequestError && err.status === 400 ? "This role doesn't exist." : apiErrorMessage(err, 'Could not load this role'));
    }
  }, [code]);

  useEffect(() => {
    load();
  }, [load]);

  const changes = role ? diffGrants(role.permissions, draft) : { added: [], removed: [] };
  const dirty = changes.added.length + changes.removed.length > 0;

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

  /** Keeps unsaved permission toggles unless the permissions themselves were just saved. */
  const changed = (updated: RoleInfo, message: string, permissionsSaved = false) => {
    setRole(updated);
    if (permissionsSaved) setDraft(updated.permissions);
    setNotice(message);
    setRevision((r) => r + 1);
  };

  const savePermissions = async () => {
    if (!role) return;
    setSaving(true);
    setError(null);
    try {
      const res = await adminApi.updatePermissions(role.role, draft, role.version);
      changed(res.data, 'Permissions saved. People see the change on their next action.', true);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save the permissions'));
    } finally {
      setSaving(false);
    }
  };

  const toggle = (p: Permission) => {
    setNotice(null);
    setDraft((d) => (d.includes(p) ? d.filter((x) => x !== p) : [...d, p]));
  };

  return (
    <MainLayout>
      <Link to="/admin/roles" className="mb-3 inline-flex items-center gap-1.5 text-sm text-ink-muted hover:text-ink">
        <ArrowLeft className="size-4" aria-hidden="true" />
        All roles
      </Link>
      <PageHeader
        title={role?.name ?? 'Role'}
        meta={role?.description ? <span>{role.description}</span> : undefined}
        actions={
          role &&
          !editingName && (
            <Button variant="secondary" size="sm" onClick={() => setEditingName(true)}>
              Rename or describe
            </Button>
          )
        }
      />
      <AdminTabs />
      {notice && (
        <Alert tone="success" className="mb-4">
          {notice}
        </Alert>
      )}
      {error && (
        <Alert className="mb-4" action={role && <Button variant="secondary" size="sm" onClick={() => { setError(null); load(); }}>Reload</Button>}>
          {error}
        </Alert>
      )}
      {!role && !error && <Loader label="Loading the role…" />}
      {role && catalog && (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
          <div className="min-w-0 space-y-6">
            {editingName && (
              <Panel>
                <PanelHeader title="Name and description" />
                <NameForm
                  role={role}
                  onCancel={() => setEditingName(false)}
                  onSaved={(updated) => {
                    setEditingName(false);
                    changed(updated, 'Role saved.');
                  }}
                />
              </Panel>
            )}
            <Panel>
              <PanelHeader title="What it can do" />
              <div className="p-4 sm:px-5">
                {role.kind === 'CANDIDATE' ? (
                  <p className="text-sm text-ink-muted">Candidates can't hold permissions. They only see their own applications in the portal.</p>
                ) : (
                  <>
                    {!role.editable && <p className="mb-4 text-sm text-ink-muted">Admins always have every permission.</p>}
                    <PermissionGroups catalog={catalog} saved={role.permissions} value={draft} editable={role.editable} disabled={saving} onToggle={toggle} />
                  </>
                )}
              </div>
              {dirty && (
                <div
                  role="region"
                  aria-label="Unsaved changes"
                  className="sticky bottom-0 z-20 flex flex-wrap items-center gap-x-4 gap-y-2 rounded-b-xl border-t border-line bg-ink px-4 py-3 text-ground sm:px-5"
                >
                  <p className="min-w-0 flex-1 text-sm">
                    {[...changes.added.map((p) => `+${PERMISSION_LABEL[p]}`), ...changes.removed.map((p) => `−${PERMISSION_LABEL[p]}`)].join(', ')}
                  </p>
                  <div className="flex gap-2">
                    <Button variant="ghost" size="sm" disabled={saving} onClick={() => setDraft(role.permissions)} className="h-9 border border-ground/40 text-ground hover:bg-ground/10 hover:text-ground">
                      Discard
                    </Button>
                    <Button size="sm" disabled={saving} onClick={savePermissions} className="h-9 bg-mark text-mark-ink shadow-none hover:bg-mark/90">
                      {saving ? 'Saving…' : 'Save changes'}
                    </Button>
                  </div>
                </div>
              )}
            </Panel>
            {can(me, 'USER_MANAGE') && (
              <Panel>
                <PanelHeader title={`Who has it (${role.memberCount} active)`} />
                <div className="p-4 sm:px-5">
                  <RoleMembers role={role} meId={me?.id} onMoved={() => adminApi.role(role.role).then((res) => changed(res.data, 'People moved.'))} />
                </div>
              </Panel>
            )}
          </div>
          <aside className="space-y-6">
            <Panel>
              <PanelHeader title="What the workflow uses it for" />
              <ul className="list-disc space-y-1 py-4 pl-9 pr-4 text-sm">
                {role.uses.map((use) => (
                  <li key={use}>{use}</li>
                ))}
              </ul>
            </Panel>
            {can(me, 'AUDIT_VIEW') && (
              <Panel>
                <PanelHeader title="Changes" />
                <div className="p-4 sm:px-5">
                  <RoleHistory role={role.role} revision={revision} />
                </div>
              </Panel>
            )}
          </aside>
        </div>
      )}
    </MainLayout>
  );
}
