import { CheckCircle2, Download, FileText, Upload, XCircle } from 'lucide-react';
import type { CandidateOffer, ReviewStatus } from '@/types';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Textarea } from '@/components/ui/Input';
import { cn, formatRelativeDate } from '@/lib/utils';

interface OfferSectionProps {
  offer: CandidateOffer | null;
  currentStage: ReviewStatus;
  candidateName: string;
  canManage: boolean;
  /** The approval step that is open, and whether this viewer holds it */
  directorOpen: boolean;
  rejected: boolean;
  myStepOpen: boolean;
  requireDirector: boolean;
  onRequireDirectorChange: (value: boolean) => void;
  uploadingOffer: boolean;
  onUpload: (e: React.ChangeEvent<HTMLInputElement>) => void;
  onDownload: () => void;
  confirmApprove: boolean;
  onConfirmApproveChange: (value: boolean) => void;
  approvingOffer: boolean;
  onApprove: () => void;
  rejecting: boolean;
  onRejectingChange: (value: boolean | ((r: boolean) => boolean)) => void;
  rejectNote: string;
  onRejectNoteChange: (value: string) => void;
  submittingReject: boolean;
  onReject: () => void;
}

export function OfferSection({
  offer, currentStage, candidateName, canManage, directorOpen, rejected, myStepOpen,
  requireDirector, onRequireDirectorChange, uploadingOffer, onUpload, onDownload,
  confirmApprove, onConfirmApproveChange, approvingOffer, onApprove,
  rejecting, onRejectingChange, rejectNote, onRejectNoteChange, submittingReject, onReject,
}: OfferSectionProps) {
  return (
        <div className="space-y-3">
    <h3 className="text-[15px] font-semibold text-ink">Job offer</h3>

    {offer ? (
      <div className="lift space-y-3 rounded-xl border border-line-strong bg-surface-raised p-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex items-center gap-3">
            <div className="flex size-10 items-center justify-center rounded-lg border border-line bg-surface">
              <FileText className="size-5 text-accent-text" aria-hidden="true" />
            </div>
            <div>
              <h4 className="text-sm font-semibold text-ink">{offer.fileName}</h4>
              <p className="text-xs text-ink-muted">
                Uploaded by {offer.uploadedByName} · {formatRelativeDate(offer.uploadedAt)}
              </p>
            </div>
          </div>

          {offer.isApproved ? (
            <Badge tone="success">
              <CheckCircle2 className="size-3.5" aria-hidden="true" />
              Offer approved
            </Badge>
          ) : rejected ? (
            <Badge tone="danger">Rejected</Badge>
          ) : (
            <Badge tone="warn">{directorOpen ? 'Waiting for the department director' : 'Waiting for the HR chief'}</Badge>
          )}
        </div>

        {/* Who has to sign, in order */}
        <ol className="space-y-1.5 text-sm" aria-label="Approvals">
          {offer.requiresDirectorApproval && (
            <li className="flex items-center gap-2">
              <span
                aria-hidden="true"
                className={cn('size-2 rounded-full', offer.directorApprovedAt ? 'bg-success' : 'border border-line-strong')}
              />
              <span className="text-ink">Department director</span>
              <span className="text-ink-muted">
                {offer.directorApprovedAt
                  ? `${offer.directorApprovedByName} · ${formatRelativeDate(offer.directorApprovedAt)}`
                  : 'waiting'}
              </span>
            </li>
          )}
          <li className="flex items-center gap-2">
            <span aria-hidden="true" className={cn('size-2 rounded-full', offer.isApproved ? 'bg-success' : 'border border-line-strong')} />
            <span className="text-ink">HR chief</span>
            <span className="text-ink-muted">
              {offer.isApproved
                ? `${offer.approvedByName} · ${formatRelativeDate(offer.approvedAt || '')}`
                : directorOpen
                ? 'after the director'
                : 'waiting'}
            </span>
          </li>
        </ol>

        {rejected && (
          <div className="rounded-lg border border-danger/40 bg-danger-soft p-3 text-sm" role="status">
            <p className="font-semibold text-danger">
              Rejected by {offer.rejectedByName} · {formatRelativeDate(offer.rejectedAt || '')}
            </p>
            <p className="mt-1 whitespace-pre-wrap text-ink">{offer.rejectionNote || 'No reason given.'}</p>
            {canManage && <p className="mt-1 text-ink-muted">Upload a revised offer to start the approvals again.</p>}
          </div>
        )}

        <div className="flex flex-wrap items-center gap-2 border-t border-line pt-3">
          <Button variant="secondary" size="sm" onClick={onDownload}>
            <Download className="size-3.5" aria-hidden="true" />
            Download
          </Button>

          {!offer.isApproved && canManage && currentStage === 'OFFER' && (
            <>
              <label className="inline-flex items-center gap-2 text-xs text-ink">
                <input
                  type="checkbox"
                  className="size-4 accent-[rgb(var(--accent-text))]"
                  checked={requireDirector}
                  onChange={(e) => onRequireDirectorChange(e.target.checked)}
                />
                Department director approves first
              </label>
              <label className="cursor-pointer">
                <input type="file" accept="application/pdf" onChange={onUpload} className="sr-only" disabled={uploadingOffer} />
                <span className="inline-flex h-8 items-center gap-1.5 rounded-md border border-line-strong px-3 text-[13px] font-semibold text-ink transition-colors hover:bg-sunken">
                  <Upload className="size-3.5" aria-hidden="true" />
                  {uploadingOffer ? 'Uploading…' : rejected ? 'Upload revised PDF' : 'Replace PDF'}
                </span>
              </label>
            </>
          )}

          {myStepOpen && (
            <>
              {confirmApprove ? (
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-xs text-ink">Approve the offer for {candidateName}? This can't be undone.</span>
                  <Button variant="primary" size="sm" onClick={onApprove} disabled={approvingOffer}>
                    {approvingOffer ? 'Approving…' : 'Confirm approve'}
                  </Button>
                  <Button variant="secondary" size="sm" onClick={() => onConfirmApproveChange(false)}>
                    Cancel
                  </Button>
                </div>
              ) : (
                <>
                  <Button variant="primary" size="sm" onClick={() => onConfirmApproveChange(true)} disabled={approvingOffer}>
                    <CheckCircle2 className="size-3.5" aria-hidden="true" />
                    {directorOpen ? 'Approve as director' : 'Approve offer'}
                  </Button>
                  <Button variant="danger" size="sm" onClick={() => onRejectingChange((r) => !r)} aria-expanded={rejecting}>
                    <XCircle className="size-3.5" aria-hidden="true" />
                    Reject
                  </Button>
                </>
              )}
            </>
          )}
        </div>

        {rejecting && myStepOpen && (
          <div className="space-y-2 border-t border-line pt-3">
            <label htmlFor="reject-note" className="block text-sm font-semibold text-ink">
              Why are you rejecting it? <span className="font-normal text-ink-muted">(optional)</span>
            </label>
            <Textarea
              id="reject-note"
              rows={2}
              maxLength={1000}
              value={rejectNote}
              onChange={(e) => onRejectNoteChange(e.target.value)}
              placeholder="Tell the recruiter what to change. You can leave this empty."
              className="min-h-[64px]"
            />
            <div className="flex gap-2">
              <Button variant="dangerSolid" size="sm" onClick={onReject} disabled={submittingReject}>
                {submittingReject ? 'Rejecting…' : 'Reject offer'}
              </Button>
              <Button variant="secondary" size="sm" onClick={() => onRejectingChange(false)} disabled={submittingReject}>
                Keep reviewing
              </Button>
            </div>
          </div>
        )}
      </div>
    ) : (
      canManage &&
      currentStage === 'OFFER' && (
        <div className="space-y-3 rounded-xl border border-dashed border-line-strong p-6 text-center">
          <FileText className="mx-auto size-8 text-ink-muted" aria-hidden="true" />
          <h4 className="text-sm font-semibold text-ink">No offer letter uploaded yet</h4>
          <p className="mx-auto max-w-sm text-xs text-ink-muted">
            Upload a PDF offer letter (up to 10 MB). The HR chief approves it. Tick the box if the department director must sign first.
          </p>
          <label className="mx-auto inline-flex items-center gap-2 text-sm text-ink">
            <input
              type="checkbox"
              className="size-4 accent-[rgb(var(--accent-text))]"
              checked={requireDirector}
              onChange={(e) => onRequireDirectorChange(e.target.checked)}
            />
            Department director approves first
          </label>
          <div>
            <label className="cursor-pointer">
              <input type="file" accept="application/pdf" onChange={onUpload} className="sr-only" disabled={uploadingOffer} />
              <span className="inline-flex h-9 items-center gap-2 rounded-md bg-accent px-4 text-xs font-semibold text-accent-ink transition-opacity hover:opacity-90">
                <Upload className="size-4" aria-hidden="true" />
                {uploadingOffer ? 'Uploading…' : 'Upload offer PDF'}
              </span>
            </label>
          </div>
        </div>
      )
    )}
  </div>
  );
}
