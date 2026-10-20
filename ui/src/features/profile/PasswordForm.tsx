import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { apiErrorMessage, profileApi } from '@/api/client';
import { type PasswordForm as Values, passwordSchema } from '@/features/profile/profile';
import { Field, Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Alert } from '@/components/ui/Feedback';

export function PasswordForm({ onChanged, submitLabel = 'Change password' }: { onChanged?: () => void; submitLabel?: string }) {
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(passwordSchema) });

  const submit = async (values: Values) => {
    setError(null);
    setDone(false);
    try {
      await profileApi.changePassword(values.currentPassword, values.newPassword);
      reset();
      setDone(true);
      onChanged?.();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not change your password'));
    }
  };

  const invalid = (name: keyof Values) => ({ 'aria-invalid': !!errors[name], 'aria-describedby': errors[name] ? `${name}-error` : undefined });

  return (
    <form onSubmit={handleSubmit(submit)} className="space-y-4" noValidate>
      {error && <Alert>{error}</Alert>}
      <Field id="currentPassword" label="Current password" error={errors.currentPassword?.message}>
        <Input id="currentPassword" type="password" autoComplete="current-password" {...invalid('currentPassword')} {...register('currentPassword')} />
      </Field>
      <Field id="newPassword" label="New password" hint="At least 10 characters." error={errors.newPassword?.message}>
        <Input id="newPassword" type="password" autoComplete="new-password" {...invalid('newPassword')} {...register('newPassword')} />
      </Field>
      <Field id="confirm" label="Repeat new password" error={errors.confirm?.message}>
        <Input id="confirm" type="password" autoComplete="new-password" {...invalid('confirm')} {...register('confirm')} />
      </Field>
      <div className="flex items-center justify-end gap-3">
        {done && <span role="status" className="text-sm text-success">Password changed</span>}
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Saving…' : submitLabel}
        </Button>
      </div>
    </form>
  );
}
