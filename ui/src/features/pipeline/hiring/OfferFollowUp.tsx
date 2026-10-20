import type { CandidateOffer, ReviewStatus } from '@/types';
import { Button } from '@/components/ui/Button';
import { Input, Textarea } from '@/components/ui/Input';
import { formatDate, formatRelativeDate } from '@/lib/utils';

interface OfferFollowUpProps {
  offer: CandidateOffer;
  currentStage: ReviewStatus;
  candidateName: string;
  canManage: boolean;
  busy: boolean;
  joiningDate: string;
  joiningNotes: string;
  onJoiningDateChange: (value: string) => void;
  onJoiningNotesChange: (value: string) => void;
  onOfferSent: () => void;
  onAnswer: (accepted: boolean) => void;
  onSaveJoining: (e: React.FormEvent) => void;
}

/** After the approvals: sending the offer, the candidate's answer and joining. */
export function OfferFollowUp({
  offer, currentStage, candidateName, canManage, busy, joiningDate, joiningNotes,
  onJoiningDateChange, onJoiningNotesChange, onOfferSent, onAnswer, onSaveJoining,
}: OfferFollowUpProps) {
  return (
        <div className="space-y-3">
    <h3 className="text-[15px] font-semibold text-ink">Offer and joining</h3>

    <div className="lift space-y-4 rounded-xl border border-line-strong bg-surface-raised p-4">
      {currentStage === 'OFFER_APPROVED' && (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm text-ink">The offer is approved. Send it to {candidateName}, then record the answer.</p>
          {canManage && (
            <Button variant="primary" onClick={onOfferSent} disabled={busy}>
              Mark offer as sent
            </Button>
          )}
        </div>
      )}

      {currentStage === 'OFFER_SENT' && (
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm text-ink">
            Offer sent {offer.sentAt ? formatRelativeDate(offer.sentAt).toLowerCase() : ''}. What did {candidateName} answer?
          </p>
          {canManage && (
            <div className="flex gap-2">
              <Button variant="primary" onClick={() => onAnswer(true)} disabled={busy}>
                Accepted
              </Button>
              <Button variant="secondary" onClick={() => onAnswer(false)} disabled={busy}>
                Declined
              </Button>
            </div>
          )}
        </div>
      )}

      {currentStage === 'DECLINED' && (
        <p className="text-sm text-ink">
          {candidateName} declined the offer{offer.respondedAt ? ` ${formatRelativeDate(offer.respondedAt).toLowerCase()}` : ''}.
        </p>
      )}

      {(currentStage === 'ACCEPTED' || currentStage === 'JOINING_SET') &&
        (canManage ? (
          <form onSubmit={onSaveJoining} className="space-y-3">
            <p className="text-sm text-ink">
              {candidateName} accepted{offer.respondedAt ? ` ${formatRelativeDate(offer.respondedAt).toLowerCase()}` : ''}. Set the joining date and any details.
            </p>
            <div className="space-y-1.5">
              <label htmlFor="joining-date" className="block text-sm font-semibold">
                Joining date
              </label>
              <Input id="joining-date" type="date" value={joiningDate} onChange={(e) => onJoiningDateChange(e.target.value)} required className="sm:max-w-[220px]" />
            </div>
            <div className="space-y-1.5">
              <label htmlFor="joining-notes" className="block text-sm font-semibold">
                Notes or details <span className="font-normal text-ink-muted">(optional)</span>
              </label>
              <Textarea
                id="joining-notes"
                rows={3}
                maxLength={2000}
                value={joiningNotes}
                onChange={(e) => onJoiningNotesChange(e.target.value)}
                placeholder="Where they sit, equipment, who greets them…"
              />
            </div>
            <Button type="submit" variant="primary" disabled={busy || !joiningDate}>
              {busy ? 'Saving…' : currentStage === 'JOINING_SET' ? 'Update joining details' : 'Save joining details'}
            </Button>
          </form>
        ) : (
          currentStage === 'JOINING_SET' && (
            <p className="text-sm text-ink">
              Joining {offer.joiningDate ? formatDate(offer.joiningDate) : ''}.
              {offer.joiningNotes ? ` ${offer.joiningNotes}` : ''}
            </p>
          )
        ))}
    </div>
  </div>
  );
}
