import { useEffect, useState } from 'react';
import { adminApi, apiErrorMessage } from '@/api/client';
import type { Page, Role, RoleHistoryItem } from '@/types';
import { Button } from '@/components/ui/Button';
import { Alert } from '@/components/ui/Feedback';
import { formatRelativeDate } from '@/lib/utils';

const ACTION_LABEL: Record<string, string> = {
  ROLE_UPDATE: 'Renamed or described',
  ROLE_PERMISSIONS_CHANGE: 'Permissions changed',
  ROLE_MEMBERS_MOVED: 'People moved',
};

export function RoleHistory({ role, revision }: { role: Role; revision: number }) {
  const [items, setItems] = useState<RoleHistoryItem[]>([]);
  const [last, setLast] = useState<Page<RoleHistoryItem> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setError(null);
    adminApi
      .roleHistory(role)
      .then((res) => {
        if (!active) return;
        setItems(res.data.content);
        setLast(res.data);
      })
      .catch((err) => active && setError(apiErrorMessage(err, 'Could not load the history')));
    return () => {
      active = false;
    };
  }, [role, revision]);

  const more = async () => {
    if (!last) return;
    try {
      const res = await adminApi.roleHistory(role, last.number + 1);
      setItems((all) => [...all, ...res.data.content]);
      setLast(res.data);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not load more'));
    }
  };

  if (error) return <Alert>{error}</Alert>;
  if (last && items.length === 0) return <p className="text-sm text-ink-muted">No changes yet.</p>;
  return (
    <div className="space-y-3">
      <ol className="space-y-3">
        {items.map((item) => (
          <li key={item.id} className="border-l-2 border-line pl-3">
            <p className="text-sm font-semibold">{ACTION_LABEL[item.action] ?? item.action}</p>
            {item.details && <p className="break-words text-[13px]">{item.details}</p>}
            <p className="text-[12px] text-ink-muted">
              {item.actorEmail ?? 'System'} · {formatRelativeDate(item.occurredAt)}
            </p>
          </li>
        ))}
      </ol>
      {last && last.number + 1 < last.totalPages && (
        <Button variant="secondary" size="sm" onClick={more}>
          Show older changes
        </Button>
      )}
    </div>
  );
}
