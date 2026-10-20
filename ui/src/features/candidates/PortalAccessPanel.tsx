import { useEffect, useState } from 'react';
import { Copy, KeyRound, Link2 } from 'lucide-react';
import { apiErrorMessage, portalApi } from '@/api/client';
import type { IssuedInvite, PortalAccess, ReviewStatus } from '@/types';
import { Button } from '@/components/ui/Button';
import { Panel } from '@/components/ui/Card';
import { Alert, ConfirmButton } from '@/components/ui/Feedback';
import { Field, Input } from '@/components/ui/Input';
import { formatDate } from '@/lib/utils';

export function PortalAccessPanel({
  sessionId,
  candidateId,
  stage,
  email,
  canManage,
}: {
  sessionId: string;
  candidateId: string;
  stage: ReviewStatus;
  email: string | null | undefined;
  canManage: boolean;
}) {
  const eligible = stage !== 'NONE' && !!email?.trim();
  const [access, setAccess] = useState<PortalAccess | null>(null);
  const [issued, setIssued] = useState<IssuedInvite | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);
  const [creating, setCreating] = useState(false);
  const [tempPassword, setTempPassword] = useState('');

  useEffect(() => {
    if (!eligible) return;
    let active = true;
    portalApi
      .access(sessionId, candidateId)
      .then((res) => active && setAccess(res.data))
      .catch((err) => active && setError(apiErrorMessage(err, 'Could not load portal access')));
    return () => {
      active = false;
    };
  }, [sessionId, candidateId, eligible]);

  const run = async (work: () => Promise<void>, fallback: string) => {
    setBusy(true);
    setError(null);
    try {
      await work();
    } catch (err) {
      setError(apiErrorMessage(err, fallback));
    } finally {
      setBusy(false);
    }
  };

  const sendLink = () =>
    run(async () => {
      const res = await portalApi.issueInvite(sessionId, candidateId);
      setIssued(res.data);
      setCopied(false);
      setAccess((a) => ({ ...a, invite: { createdAt: new Date().toISOString(), expiresAt: res.data.expiresAt } }));
    }, 'Could not create the link');

  const revoke = () =>
    run(async () => {
      await portalApi.revokeInvite(sessionId, candidateId);
      setIssued(null);
      setAccess((a) => ({ ...a, invite: undefined }));
    }, 'Could not revoke the link');

  const createAccount = () =>
    run(async () => {
      const res = await portalApi.createAccount(sessionId, candidateId, tempPassword);
      setAccess(res.data);
      setIssued(null);
      setCreating(false);
      setTempPassword('');
    }, 'Could not create the account');

  const copy = async () => {
    if (!issued) return;
    try {
      await navigator.clipboard.writeText(issued.url);
      setCopied(true);
    } catch {
      setError('Copy did not work here. Select the link and copy it yourself.');
    }
  };

  let body: React.ReactNode;
  if (stage === 'NONE') {
    body = <p className="text-sm text-ink-muted">You can give portal access after you shortlist this candidate.</p>;
  } else if (!email?.trim()) {
    body = <p className="text-sm text-ink-muted">This candidate has no email on file, so they can't get portal access yet.</p>;
  } else if (!access) {
    body = error ? null : <p className="text-sm text-ink-muted">Loading…</p>;
  } else if (access.account) {
    body = (
      <div className="space-y-1 text-sm">
        <p className="font-semibold">They can sign in as {access.account.email}.</p>
        {access.account.mustChangePassword && <p className="text-ink-muted">They still need to replace the temporary password.</p>}
        {!access.account.active && <p className="text-ink-muted">The account is hidden, so they can't sign in.</p>}
      </div>
    );
  } else {
    body = (
      <div className="space-y-4">
        <p className="text-sm text-ink-muted">
          {access.invite ? `An access link is open until ${formatDate(access.invite.expiresAt)}.` : 'No portal access yet.'}
        </p>

        {issued && (
          <div className="space-y-2 rounded-md border border-line bg-sunken p-3">
            <Field
              id="portal-link"
              label="Access link"
              hint="Valid for 7 days. Anyone with the link can set the password, so send it only to the candidate."
            >
              <div className="space-y-2">
                <Input id="portal-link" readOnly value={issued.url} onFocus={(e) => e.currentTarget.select()} className="font-mono text-[13px]" />
                <Button variant="secondary" size="sm" onClick={copy}>
                  <Copy aria-hidden="true" />
                  {copied ? 'Copied' : 'Copy link'}
                </Button>
              </div>
            </Field>
          </div>
        )}

        {canManage && (
          <div className="flex flex-wrap gap-2">
            <Button onClick={sendLink} disabled={busy}>
              <Link2 aria-hidden="true" />
              {access.invite ? 'Send a new link' : 'Send access link'}
            </Button>
            {access.invite && <ConfirmButton label="Revoke link" confirmLabel="Revoke it" onConfirm={revoke} disabled={busy} />}
            {!creating && (
              <Button variant="ghost" onClick={() => setCreating(true)} disabled={busy} className="h-auto min-h-10 whitespace-normal py-2 text-left">
                <KeyRound aria-hidden="true" />
                Create account with a temporary password
              </Button>
            )}
          </div>
        )}

        {canManage && creating && (
          <form
            className="space-y-3 border-t border-line pt-4"
            onSubmit={(e) => {
              e.preventDefault();
              createAccount();
            }}
          >
            <Field id="portal-temp" label="Temporary password" hint="At least 10 characters. They choose their own the first time they sign in.">
              <Input id="portal-temp" type="text" autoComplete="off" value={tempPassword} onChange={(e) => setTempPassword(e.target.value)} />
            </Field>
            <div className="flex justify-end gap-2">
              <Button variant="ghost" onClick={() => setCreating(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={busy || tempPassword.length < 10}>
                Create account
              </Button>
            </div>
          </form>
        )}
      </div>
    );
  }

  return (
    <Panel className="animate-fade-up p-4">
      <h2 className="border-b border-line pb-3 text-[15px] font-semibold text-ink">Candidate portal</h2>
      {error && <Alert className="mt-3">{error}</Alert>}
      <div className="mt-3">{body}</div>
    </Panel>
  );
}
