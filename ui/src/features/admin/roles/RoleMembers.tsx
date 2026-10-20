import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { adminApi, apiErrorMessage } from '@/api/client';
import type { Page, RoleInfo, UserRow } from '@/types';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Alert } from '@/components/ui/Feedback';
import { Pagination } from '@/components/ui/Pagination';
import { Badge } from '@/components/ui/Badge';
import { MoveMembersSheet } from './MoveMembersSheet';

export function RoleMembers({ role, meId, onMoved }: { role: RoleInfo; meId: string | undefined; onMoved: () => void }) {
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<UserRow> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [moving, setMoving] = useState(false);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    let active = true;
    const timer = setTimeout(() => {
      adminApi
        .roleMembers(role.role, { q: q || undefined, page })
        .then((res) => active && setData(res.data))
        .catch((err) => active && setError(apiErrorMessage(err, 'Could not load the people in this role')));
    }, 200);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [role.role, q, page, revision]);

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <label htmlFor="member-search" className="sr-only">
          Search people in this role
        </label>
        <Input
          id="member-search"
          type="search"
          placeholder="Search by name or email"
          className="max-w-xs"
          value={q}
          onChange={(e) => {
            setQ(e.target.value);
            setPage(0);
          }}
        />
        {!moving && (
          <Button size="sm" variant="secondary" className="ml-auto" onClick={() => setMoving(true)}>
            Move people here
          </Button>
        )}
      </div>
      {moving && (
        <MoveMembersSheet
          role={role}
          meId={meId}
          onCancel={() => setMoving(false)}
          onMoved={() => {
            setMoving(false);
            setRevision((r) => r + 1);
            onMoved();
          }}
        />
      )}
      {error && <Alert>{error}</Alert>}
      {data && (
        <>
          {data.content.length === 0 ? (
            <p className="text-sm text-ink-muted">{q ? 'Nobody in this role matches.' : 'Nobody holds this role yet.'}</p>
          ) : (
            <ul className="divide-y divide-line rounded-lg border border-line">
              {data.content.map((u) => (
                <li key={u.id} className="flex items-center gap-3 px-3 py-2.5">
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-semibold">{u.fullName}</span>
                    <span className="block truncate text-[13px] text-ink-muted">{u.headline ? `${u.headline} · ${u.email}` : u.email}</span>
                  </span>
                  {u.status === 'HIDDEN' && <Badge>Hidden</Badge>}
                  {role.kind === 'STAFF' && (
                    <Link to={`/admin/users?q=${encodeURIComponent(u.email)}`} className="shrink-0 text-[13px] text-accent-text hover:underline">
                      Edit<span className="sr-only"> {u.fullName}</span>
                    </Link>
                  )}
                </li>
              ))}
            </ul>
          )}
          <Pagination page={data.number} totalPages={data.totalPages} onPage={setPage} />
        </>
      )}
    </div>
  );
}
