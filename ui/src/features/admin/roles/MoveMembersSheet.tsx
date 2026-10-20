import { useEffect, useState } from 'react';
import { adminApi, apiErrorMessage } from '@/api/client';
import type { Role, RoleInfo, UserRow } from '@/types';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Alert } from '@/components/ui/Feedback';
import { ROLE_LABEL } from '@/lib/utils';

interface MoveMembersSheetProps {
  role: Pick<RoleInfo, 'role' | 'name' | 'kind'>;
  meId: string | undefined;
  onMoved: () => void;
  onCancel: () => void;
}

const MAX = 50;

/** Picks people from other roles of the same kind and moves them into this one, all at once. */
export function MoveMembersSheet({ role, meId, onMoved, onCancel }: MoveMembersSheetProps) {
  const [q, setQ] = useState('');
  const [people, setPeople] = useState<UserRow[]>([]);
  const [picked, setPicked] = useState<Map<string, UserRow>>(new Map());
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;
    const timer = setTimeout(() => {
      adminApi
        .listUsers({ q: q || undefined, size: 20, status: 'ACTIVE' })
        .then((res) => active && setPeople(res.data.content.filter((u) => u.role !== role.role && (u.role === 'CANDIDATE') === (role.kind === 'CANDIDATE'))))
        .catch((err) => active && setError(apiErrorMessage(err, 'Could not search people')));
    }, 200);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [q, role.role, role.kind]);

  const toggle = (user: UserRow) =>
    setPicked((current) => {
      const next = new Map(current);
      if (next.has(user.id)) next.delete(user.id);
      else if (next.size < MAX) next.set(user.id, user);
      return next;
    });

  const move = async () => {
    setSaving(true);
    setError(null);
    try {
      await adminApi.moveMembers(role.role as Role, [...picked.keys()]);
      onMoved();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not move these people'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-3 rounded-lg border border-line bg-surface p-4" role="group" aria-label={`Move people to ${role.name}`}>
      <label htmlFor="move-search" className="block text-sm font-semibold">
        Find people to move to {role.name}
      </label>
      <Input id="move-search" type="search" value={q} placeholder="Name or email" onChange={(e) => setQ(e.target.value)} />
      <ul className="max-h-72 divide-y divide-line overflow-y-auto rounded-md border border-line">
        {people.length === 0 && <li className="px-3 py-3 text-sm text-ink-muted">Nobody else matches.</li>}
        {people.map((u) => {
          const self = u.id === meId;
          return (
            <li key={u.id}>
              <label className={self ? 'flex items-center gap-3 px-3 py-2.5 opacity-60' : 'flex cursor-pointer items-center gap-3 px-3 py-2.5 hover:bg-sunken'}>
                <input
                  type="checkbox"
                  className="size-[18px] shrink-0 accent-[rgb(var(--ink))]"
                  checked={picked.has(u.id)}
                  disabled={self || saving}
                  onChange={() => toggle(u)}
                  aria-describedby={self ? `self-${u.id}` : undefined}
                />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-semibold">{u.fullName}</span>
                  <span className="block truncate text-[13px] text-ink-muted">
                    {u.email} · {ROLE_LABEL[u.role]}
                  </span>
                </span>
                {self && (
                  <span id={`self-${u.id}`} className="shrink-0 text-[12px] text-ink-muted">
                    That's you
                  </span>
                )}
              </label>
            </li>
          );
        })}
      </ul>
      {error && <Alert>{error}</Alert>}
      <div className="flex flex-wrap items-center justify-end gap-2">
        <span className="mr-auto text-[13px] text-ink-muted">{picked.size} selected</span>
        <Button variant="secondary" size="sm" onClick={onCancel} disabled={saving}>
          Cancel
        </Button>
        <Button size="sm" onClick={move} disabled={saving || picked.size === 0}>
          {saving ? 'Moving…' : picked.size ? `Move ${picked.size} to ${role.name}` : `Move to ${role.name}`}
        </Button>
      </div>
    </div>
  );
}
