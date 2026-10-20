import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { X } from 'lucide-react';
import type { AdminUser, Role, UserFormValues, UserStatus } from '@/types';
import { profileSchema } from '@/features/profile/profile';
import { Field, Input, Textarea } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Panel } from '@/components/ui/Card';
import { ROLE_LABEL, formatDate } from '@/lib/utils';

const userSchema = profileSchema.extend({
  email: z.string().trim().email('Enter a valid email address'),
  role: z.enum(Object.keys(ROLE_LABEL) as [Role, ...Role[]]),
  temporaryPassword: z.string().optional(),
});

type Props = {
  user: AdminUser | null;
  isMe: boolean;
  onSave: (values: UserFormValues) => Promise<void>;
  onClose: () => void;
  onStatus: (status: UserStatus) => Promise<void>;
  onResetPassword: (temporaryPassword: string) => Promise<void>;
  onDelete: () => Promise<void>;
};

/** Create (user is null) or edit one account. The server repeats every rule shown here. */
export function AdminUserForm({ user, isMe, onSave, onClose, onStatus, onResetPassword, onDelete }: Props) {
  const creating = !user;
  const [saving, setSaving] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [tempPassword, setTempPassword] = useState('');
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [typedEmail, setTypedEmail] = useState('');

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<z.infer<typeof userSchema>>({
    resolver: zodResolver(userSchema),
    defaultValues: user
      ? { ...user, phone: user.phone ?? '', headline: user.headline ?? '', department: user.department ?? '', location: user.location ?? '', bio: user.bio ?? '' }
      : { role: 'RECRUITER' },
  });

  const submit = async (values: z.infer<typeof userSchema>) => {
    if (creating && (values.temporaryPassword ?? '').length < 10) {
      setError('temporaryPassword', { message: 'Use at least 10 characters' });
      return;
    }
    setSaving(true);
    try {
      await onSave(values);
    } finally {
      setSaving(false);
    }
  };

  const field = (name: keyof z.infer<typeof userSchema>) => ({
    'aria-invalid': !!errors[name],
    'aria-describedby': errors[name] ? `u-${name}-error` : undefined,
  });

  return (
    <Panel role="region" aria-labelledby="user-form-title" className="space-y-5 p-5">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h2 id="user-form-title" className="text-lg font-semibold">
            {creating ? 'New user' : `Edit ${user.fullName}`}
          </h2>
          {user && (
            <p className="text-[13px] text-ink-muted">
              {ROLE_LABEL[user.role]} since {formatDate(user.createdAt)}
            </p>
          )}
        </div>
        <Button variant="ghost" size="icon" aria-label="Close panel" onClick={onClose}>
          <X aria-hidden="true" />
        </Button>
      </div>

      <form onSubmit={handleSubmit(submit)} className="space-y-4" noValidate>
        <Field id="u-fullName" label="Full name" error={errors.fullName?.message}>
          <Input id="u-fullName" autoComplete="off" {...field('fullName')} {...register('fullName')} />
        </Field>
        <Field id="u-email" label="Email" error={errors.email?.message}>
          <Input id="u-email" type="email" autoComplete="off" {...field('email')} {...register('email')} />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="u-role" label="Role" hint={isMe ? "You can't change your own role." : undefined}>
            <select
              id="u-role"
              disabled={isMe}
              className="h-10 w-full rounded-md border border-line bg-surface px-3 text-[15px] text-ink focus:border-accent-text focus:outline-none focus:ring-2 focus:ring-accent-text/25 disabled:bg-sunken disabled:text-ink-muted dark:bg-sunken"
              {...register('role')}
            >
              {(Object.keys(ROLE_LABEL) as Role[]).map((r) => (
                <option key={r} value={r}>
                  {ROLE_LABEL[r]}
                </option>
              ))}
            </select>
          </Field>
          <Field id="u-department" label="Department" error={errors.department?.message}>
            <Input id="u-department" {...field('department')} {...register('department')} />
          </Field>
        </div>
        <Field id="u-headline" label="Job title" error={errors.headline?.message}>
          <Input id="u-headline" {...field('headline')} {...register('headline')} />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="u-phone" label="Phone" error={errors.phone?.message}>
            <Input id="u-phone" type="tel" {...field('phone')} {...register('phone')} />
          </Field>
          <Field id="u-location" label="Location" error={errors.location?.message}>
            <Input id="u-location" {...field('location')} {...register('location')} />
          </Field>
        </div>
        <Field id="u-bio" label="Bio" error={errors.bio?.message}>
          <Textarea id="u-bio" rows={3} {...field('bio')} {...register('bio')} />
        </Field>
        {creating && (
          <Field
            id="u-temporaryPassword"
            label="Temporary password"
            hint="They choose their own password the first time they sign in."
            error={errors.temporaryPassword?.message}
          >
            <Input id="u-temporaryPassword" type="text" autoComplete="off" className="font-mono" {...field('temporaryPassword')} {...register('temporaryPassword')} />
          </Field>
        )}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={saving}>
            {saving ? 'Saving…' : creating ? 'Create user' : 'Save'}
          </Button>
        </div>
      </form>

      {user && (
        <div className="space-y-4 border-t border-line pt-4">
          <Action title="Temporary password" text="Sets a password they must replace when they next sign in.">
            {resetting ? (
              <form
                className="flex gap-2"
                onSubmit={async (e) => {
                  e.preventDefault();
                  if (tempPassword.length < 10) return;
                  await onResetPassword(tempPassword);
                  setResetting(false);
                  setTempPassword('');
                }}
              >
                <label htmlFor="u-reset" className="sr-only">
                  New temporary password
                </label>
                <Input id="u-reset" value={tempPassword} onChange={(e) => setTempPassword(e.target.value)} placeholder="10+ characters" className="w-40 font-mono" />
                <Button type="submit" variant="secondary" disabled={tempPassword.length < 10}>
                  Set
                </Button>
              </form>
            ) : (
              <Button variant="secondary" onClick={() => setResetting(true)} disabled={isMe}>
                Reset password
              </Button>
            )}
          </Action>
          {!isMe && (
            <>
              <Action
                title={user.status === 'HIDDEN' ? 'Hidden account' : 'Hide account'}
                text={user.status === 'HIDDEN' ? "They can't sign in. Unhide to restore access." : "They can't sign in or be picked. Their history stays."}
              >
                <Button variant="secondary" onClick={() => onStatus(user.status === 'HIDDEN' ? 'ACTIVE' : 'HIDDEN')}>
                  {user.status === 'HIDDEN' ? 'Unhide' : 'Hide'}
                </Button>
              </Action>
              <Action title="Delete account" text="Only for accounts with no sessions, interviews or notes.">
                {!confirmingDelete && (
                  <Button variant="danger" onClick={() => setConfirmingDelete(true)}>
                    Delete
                  </Button>
                )}
              </Action>
              {confirmingDelete && (
                <div className="space-y-2 rounded-md bg-danger-soft p-3">
                  <label htmlFor="u-confirm-delete" className="block text-[13px]">
                    Type <strong>{user.email}</strong> to delete this account for good.
                  </label>
                  <div className="flex gap-2">
                    <Input id="u-confirm-delete" value={typedEmail} onChange={(e) => setTypedEmail(e.target.value)} autoComplete="off" />
                    <Button variant="dangerSolid" disabled={typedEmail.trim().toLowerCase() !== user.email.toLowerCase()} onClick={onDelete}>
                      Delete for good
                    </Button>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </Panel>
  );
}

function Action({ title, text, children }: { title: string; text: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <div>
        <p className="text-sm font-semibold">{title}</p>
        <p className="text-[13px] text-ink-muted">{text}</p>
      </div>
      {children}
    </div>
  );
}
