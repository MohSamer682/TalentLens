import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiRequestError, apiErrorMessage, portalApi } from '@/api/client';
import { useAuthStore } from '@/features/auth/authStore';
import { AuthLayout } from '@/layouts/AuthLayout';
import { isCandidate } from '@/lib/permissions';
import type { InvitePreview } from '@/types';
import { Button } from '@/components/ui/Button';
import { Alert, Loader } from '@/components/ui/Feedback';
import { Field, Input } from '@/components/ui/Input';

const GONE = 'This link is no longer valid. Ask your recruiter for a new one.';

export default function InvitePage() {
  const { token = '' } = useParams();
  const navigate = useNavigate();
  const login = useAuthStore((s) => s.login);
  const me = useAuthStore((s) => s.user);
  const [preview, setPreview] = useState<InvitePreview | null>(null);
  const [gone, setGone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [touched, setTouched] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    portalApi
      .preview(token)
      .then((res) => active && setPreview(res.data))
      .catch((err) => {
        if (!active) return;
        if (err instanceof ApiRequestError && err.status === 410) setGone(true);
        else setError(apiErrorMessage(err, 'Could not open this link'));
      });
    return () => {
      active = false;
    };
  }, [token]);

  const tooShort = password.length < 10;
  const mismatch = confirm !== password;

  const accept = async () => {
    setTouched(true);
    if (tooShort || mismatch) return;
    setBusy(true);
    setError(null);
    try {
      const res = await portalApi.accept(token, password);
      login(res.data.token, res.data.user);
      navigate('/portal', { replace: true });
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 410) setGone(true);
      else setError(apiErrorMessage(err, 'Could not set your password'));
    } finally {
      setBusy(false);
    }
  };

  const claim = async () => {
    setBusy(true);
    setError(null);
    try {
      await portalApi.claim(token);
      navigate('/portal', { replace: true });
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 410) setGone(true);
      else setError(apiErrorMessage(err, 'Could not add this application'));
    } finally {
      setBusy(false);
    }
  };

  if (gone) {
    return (
      <AuthLayout title="Link no longer valid" subtitle="Access links work once and expire after a week." footer={<Link to="/login" className="font-semibold text-accent-text hover:underline">Go to sign in</Link>}>
        <p className="text-sm">{GONE}</p>
      </AuthLayout>
    );
  }

  if (!preview) {
    return error ? (
      <AuthLayout title="Something went wrong" subtitle="We couldn't open this link." footer={null}>
        <Alert>{error}</Alert>
      </AuthLayout>
    ) : (
      <Loader label="Opening your link…" />
    );
  }

  const firstName = preview.candidateName.split(' ')[0];
  const subtitle = `Set a password to see your application for ${preview.jobTitle}`;

  if (preview.accountExists) {
    const signedInHere = isCandidate(me);
    return (
      <AuthLayout title={`Welcome back, ${firstName}`} subtitle={`Add ${preview.jobTitle} to your applications.`} footer={null}>
        {error && <Alert className="mb-4">{error}</Alert>}
        {signedInHere ? (
          <Button className="w-full" onClick={claim} disabled={busy}>
            Add this application to my account
          </Button>
        ) : (
          <div className="space-y-4 text-sm">
            <p>You already have an account. Sign in, then open this link again.</p>
            <Link
              to="/login"
              state={{ from: { pathname: `/invite/${token}` } }}
              className="inline-flex h-10 w-full items-center justify-center rounded-md bg-accent px-4 font-semibold text-accent-ink"
            >
              Sign in
            </Link>
          </div>
        )}
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title={`Welcome, ${firstName}`} subtitle={subtitle} footer={<>Already have an account? <Link to="/login" className="font-semibold text-accent-text hover:underline">Sign in</Link></>}>
      {error && <Alert className="mb-4">{error}</Alert>}
      <form
        className="space-y-4"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          accept();
        }}
      >
        <Field id="newPassword" label="New password" hint="At least 10 characters." error={touched && tooShort ? 'Use at least 10 characters' : undefined}>
          <Input id="newPassword" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <Field id="confirm" label="Repeat new password" error={touched && !tooShort && mismatch ? "The passwords don't match" : undefined}>
          <Input id="confirm" type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
        </Field>
        <Button type="submit" className="w-full" disabled={busy}>
          {busy ? 'Saving…' : 'Set password and continue'}
        </Button>
      </form>
    </AuthLayout>
  );
}
