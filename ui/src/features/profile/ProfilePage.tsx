import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { apiErrorMessage, profileApi } from '@/api/client';
import type { AdminUser } from '@/types';
import { useAuthStore } from '@/features/auth/authStore';
import { type ProfileForm, profileSchema } from '@/features/profile/profile';
import { PasswordForm } from '@/features/profile/PasswordForm';
import { Field, Input, Textarea } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { Panel } from '@/components/ui/Card';
import { Alert, Loader } from '@/components/ui/Feedback';
import { MainLayout, PageHeader } from '@/layouts/MainLayout';
import { ROLE_LABEL, formatDate, getInitials } from '@/lib/utils';

export default function ProfilePage() {
  const setUser = useAuthStore((s) => s.setUser);
  const signedIn = useAuthStore((s) => s.user);
  const [profile, setProfile] = useState<AdminUser | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<ProfileForm>({ resolver: zodResolver(profileSchema) });

  const fill = (p: AdminUser) => {
    setProfile(p);
    reset({ fullName: p.fullName, phone: p.phone ?? '', headline: p.headline ?? '', department: p.department ?? '', location: p.location ?? '', bio: p.bio ?? '' });
  };

  useEffect(() => {
    profileApi.get().then(({ data }) => fill(data)).catch((err) => setError(apiErrorMessage(err, 'Could not load your profile')));
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const save = async (values: ProfileForm) => {
    if (!profile) return;
    setError(null);
    setSaved(false);
    try {
      const { data } = await profileApi.update({ ...values, version: profile.version });
      fill(data);
      if (signedIn) setUser({ ...signedIn, fullName: data.fullName });
      setSaved(true);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not save your profile'));
    }
  };

  const invalid = (name: keyof ProfileForm) => ({ 'aria-invalid': !!errors[name], 'aria-describedby': errors[name] ? `${name}-error` : undefined });

  return (
    <MainLayout>
      <PageHeader title="My profile" />
      {error && <Alert className="mb-4">{error}</Alert>}
      {!profile ? (
        !error && <Loader label="Loading your profile" />
      ) : (
        <div className="flex flex-wrap items-start gap-6">
          <Panel className="min-w-0 flex-[999_1_560px] p-5 sm:p-7">
            <div className="mb-6 flex items-center gap-4">
              <span aria-hidden="true" className="flex size-14 items-center justify-center rounded-full bg-sunken text-lg font-bold text-ink-muted">
                {getInitials(profile.fullName)}
              </span>
              <div>
                <p className="text-lg font-semibold">{profile.fullName}</p>
                <p className="text-sm text-ink-muted">
                  {profile.email} · {ROLE_LABEL[profile.role] ?? profile.role} · joined {formatDate(profile.createdAt)}
                </p>
              </div>
            </div>
            <form onSubmit={handleSubmit(save)} className="space-y-4" noValidate>
              <Field id="fullName" label="Full name" error={errors.fullName?.message}>
                <Input id="fullName" autoComplete="name" {...invalid('fullName')} {...register('fullName')} />
              </Field>
              <div className="grid gap-4 sm:grid-cols-2">
                <Field id="headline" label="Job title" error={errors.headline?.message}>
                  <Input id="headline" autoComplete="organization-title" {...invalid('headline')} {...register('headline')} />
                </Field>
                <Field id="department" label="Department" error={errors.department?.message}>
                  <Input id="department" {...invalid('department')} {...register('department')} />
                </Field>
                <Field id="phone" label="Phone" error={errors.phone?.message}>
                  <Input id="phone" type="tel" autoComplete="tel" {...invalid('phone')} {...register('phone')} />
                </Field>
                <Field id="location" label="Location" error={errors.location?.message}>
                  <Input id="location" {...invalid('location')} {...register('location')} />
                </Field>
              </div>
              <Field id="bio" label="About you" hint="Candidates see your name and job title when you interview them." error={errors.bio?.message}>
                <Textarea id="bio" rows={4} {...invalid('bio')} {...register('bio')} />
              </Field>
              <div className="flex items-center justify-end gap-3">
                {saved && !isDirty && <span role="status" className="text-sm text-success">Saved</span>}
                <Button type="submit" disabled={isSubmitting || !isDirty}>
                  {isSubmitting ? 'Saving…' : 'Save profile'}
                </Button>
              </div>
            </form>
          </Panel>
          <Panel className="min-w-0 flex-[1_1_320px] p-5 sm:p-7">
            <h2 className="mb-4 text-lg font-semibold">Change password</h2>
            <PasswordForm />
          </Panel>
        </div>
      )}
    </MainLayout>
  );
}
