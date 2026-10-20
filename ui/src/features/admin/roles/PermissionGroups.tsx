import { Lock } from 'lucide-react';
import type { Permission, RolesCatalog } from '@/types';
import { PERMISSION_LABEL } from '@/lib/permissions';
import { cn } from '@/lib/utils';

const GROUPS: { title: string; permissions: Permission[] }[] = [
  { title: 'Screening', permissions: ['SESSION_RUN', 'SESSION_VIEW_ALL', 'CANDIDATE_REVIEW'] },
  { title: 'Hiring', permissions: ['PIPELINE_MANAGE', 'INTERVIEW_FEEDBACK', 'OFFER_APPROVE'] },
  { title: 'Insight', permissions: ['ANALYTICS_VIEW'] },
  { title: 'Administration', permissions: ['USER_MANAGE', 'ROLE_MANAGE', 'AUDIT_VIEW'] },
];

interface PermissionGroupsProps {
  catalog: RolesCatalog['permissions'];
  /** What the role holds now on the server */
  saved: Permission[];
  value: Permission[];
  /** False for Admin, which always holds everything */
  editable: boolean;
  disabled?: boolean;
  onToggle: (permission: Permission) => void;
}

export function PermissionGroups({ catalog, saved, value, editable, disabled, onToggle }: PermissionGroupsProps) {
  const describe = (p: Permission) => catalog.find((c) => c.name === p);
  return (
    <div className="space-y-5">
      {GROUPS.map((group) => {
        const locked = group.permissions.every((p) => describe(p)?.adminOnly);
        return (
          <fieldset key={group.title} className="space-y-1">
            <legend className="mb-1 flex items-center gap-1.5 text-[13px] font-semibold uppercase tracking-wide text-ink-muted">
              {group.title}
              {locked && <Lock className="size-3.5" aria-hidden="true" />}
            </legend>
            {locked && editable && (
              <p className="pb-1 text-[13px] text-ink-muted">Permissions for administrators can't be given to other roles.</p>
            )}
            <ul className="divide-y divide-line rounded-lg border border-line">
              {group.permissions.map((p) => {
                const info = describe(p);
                const fixed = !editable || !!info?.adminOnly;
                const checked = editable ? value.includes(p) : true;
                const changed = editable && value.includes(p) !== saved.includes(p);
                return (
                  <li key={p}>
                    <label className={cn('flex items-start gap-3 px-3 py-2.5', !fixed && 'cursor-pointer hover:bg-sunken')}>
                      <input
                        type="checkbox"
                        className={cn('mt-0.5 size-[18px] shrink-0 accent-[rgb(var(--ink))]', changed && 'outline outline-2 outline-offset-2 outline-mark')}
                        checked={fixed && editable ? false : checked}
                        disabled={fixed || disabled}
                        onChange={() => onToggle(p)}
                      />
                      <span className="min-w-0">
                        <span className="block text-sm font-semibold">{PERMISSION_LABEL[p]}</span>
                        {info && <span className="block text-[13px] text-ink-muted">{info.description}</span>}
                      </span>
                      {changed && <span className="ml-auto shrink-0 rounded-full bg-mark-soft px-2 text-[12px] font-semibold leading-5 text-mark-text">Unsaved</span>}
                    </label>
                  </li>
                );
              })}
            </ul>
          </fieldset>
        );
      })}
    </div>
  );
}
