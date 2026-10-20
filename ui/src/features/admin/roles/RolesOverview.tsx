import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight, Lock, Users } from 'lucide-react';
import { adminApi, apiErrorMessage } from '@/api/client';
import type { RoleInfo } from '@/types';
import { AdminTabs } from '@/features/admin/AdminTabs';
import { Panel } from '@/components/ui/Card';
import { Alert, SkeletonRows } from '@/components/ui/Feedback';
import { MainLayout, PageHeader } from '@/layouts/MainLayout';

function RoleRow({ role }: { role: RoleInfo }) {
  return (
    <li>
      <Link to={`/admin/roles/${role.role}`} className="group flex items-center gap-4 px-4 py-3.5 transition-colors hover:bg-ground sm:px-5">
        <div className="min-w-0 flex-1">
          <p className="flex items-center gap-1.5 font-semibold">
            {role.name}
            {!role.editable && <Lock className="size-3.5 text-ink-muted" aria-label="Permissions are fixed" />}
          </p>
          {role.description && <p className="text-[13px] text-ink-muted">{role.description}</p>}
        </div>
        <span className="num hidden shrink-0 text-[13px] text-ink-muted sm:block">
          {role.kind === 'CANDIDATE' ? 'No permissions' : `${role.permissions.length} ${role.permissions.length === 1 ? 'permission' : 'permissions'}`}
        </span>
        <span className="num inline-flex w-16 shrink-0 items-center justify-end gap-1 text-[13px] text-ink-muted">
          <Users className="size-3.5" aria-hidden="true" />
          {role.memberCount}
          <span className="sr-only">{role.memberCount === 1 ? 'person' : 'people'}</span>
        </span>
        <ChevronRight className="size-4 shrink-0 text-ink-muted group-hover:text-ink" aria-hidden="true" />
      </Link>
    </li>
  );
}

export default function RolesOverview() {
  const [roles, setRoles] = useState<RoleInfo[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    adminApi
      .roles()
      .then((res) => setRoles(res.data.roles))
      .catch((err) => setError(apiErrorMessage(err, 'Could not load roles')));
  }, []);

  const staff = roles?.filter((r) => r.kind === 'STAFF') ?? [];
  const candidates = roles?.filter((r) => r.kind === 'CANDIDATE') ?? [];

  return (
    <MainLayout>
      <PageHeader title="Roles and permissions" meta={<span>What each role is for, who holds it, and what it can do.</span>} />
      <AdminTabs />
      {error && <Alert className="mb-4">{error}</Alert>}
      {!roles && !error && (
        <Panel>
          <SkeletonRows rows={8} columns={[40, 15, 10]} />
        </Panel>
      )}
      {roles && (
        <div className="space-y-6">
          <Panel className="overflow-hidden">
            <h2 className="border-b border-line px-4 py-3 text-[13px] font-semibold uppercase tracking-wide text-ink-muted sm:px-5">Staff</h2>
            <ul className="divide-y divide-line">
              {staff.map((r) => (
                <RoleRow key={r.role} role={r} />
              ))}
            </ul>
          </Panel>
          <Panel className="overflow-hidden">
            <h2 className="border-b border-line px-4 py-3 text-[13px] font-semibold uppercase tracking-wide text-ink-muted sm:px-5">Portal</h2>
            <ul className="divide-y divide-line">
              {candidates.map((r) => (
                <RoleRow key={r.role} role={r} />
              ))}
            </ul>
          </Panel>
        </div>
      )}
    </MainLayout>
  );
}
