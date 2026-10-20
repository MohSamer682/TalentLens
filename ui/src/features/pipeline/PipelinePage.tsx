import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, Download, FileText, Loader2, XCircle } from 'lucide-react';
import { apiErrorMessage, pipelineApi } from '@/api/client';
import { useAuthStore } from '@/features/auth/authStore';
import { hasPermission } from '@/lib/permissions';
import type { PipelineCandidate, ReviewStatus } from '@/types';
import { Button } from '@/components/ui/Button';
import { Panel } from '@/components/ui/Card';
import { Alert, Loader } from '@/components/ui/Feedback';
import { Textarea } from '@/components/ui/Input';
import { Segmented } from '@/components/ui/Segmented';
import { StageBadge } from '@/features/pipeline/StageBadge';
import { MainLayout, PageHeader } from '@/layouts/MainLayout';
import { STAGE_LABEL } from '@/features/pipeline/pipeline';
import { cn, formatScore } from '@/lib/utils';

const FILTER_STAGES: ReviewStatus[] = [
  'SHORTLISTED',
  'CONTACTED',
  'INTERVIEW_SCHEDULED',
  'INTERVIEWED',
  'OFFER',
  'OFFER_APPROVED',
  'OFFER_SENT',
  'ACCEPTED',
  'DECLINED',
  'JOINING_SET',
];
const PIPELINE_STAGES: { key: string; label: string }[] = [
  { key: 'ALL', label: 'All' },
  ...FILTER_STAGES.map((s) => ({ key: s, label: STAGE_LABEL[s] })),
];

export default function Pipeline() {
  const me = useAuthStore((s) => s.user);
  const canApprove = hasPermission(me, 'OFFER_APPROVE');
  const isRecruiter = me?.role === 'RECRUITER';

  const [candidates, setCandidates] = useState<PipelineCandidate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [selectedStage, setSelectedStage] = useState('ALL');
  const [awaitingApprovalOnly, setAwaitingApprovalOnly] = useState(false);

  // Approver action states
  const [approvingId, setApprovingId] = useState<string | null>(null);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const [rejectingId, setRejectingId] = useState<string | null>(null);
  const [rejectNote, setRejectNote] = useState('');
  const [submittingReject, setSubmittingReject] = useState(false);

  const loadPipeline = async () => {
    setError(null);
    try {
      const res = await pipelineApi.getPipeline(awaitingApprovalOnly ? true : undefined);
      setCandidates(res.data);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not load pipeline'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPipeline();
  }, [awaitingApprovalOnly]);

  const handleApprove = async (c: PipelineCandidate) => {
    if (!window.confirm(`Approve the offer for ${c.name}? This can't be undone.`)) {
      return;
    }
    setApprovingId(c.id);
    try {
      await pipelineApi.approveOffer(c.sessionId, c.id);
      await loadPipeline();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not approve offer'));
    } finally {
      setApprovingId(null);
    }
  };

  const handleReject = async (c: PipelineCandidate) => {
    setSubmittingReject(true);
    try {
      await pipelineApi.rejectOffer(c.sessionId, c.id, rejectNote.trim() || undefined);
      setRejectingId(null);
      setRejectNote('');
      await loadPipeline();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not reject the offer'));
    } finally {
      setSubmittingReject(false);
    }
  };

  const handleDownloadOffer = async (c: PipelineCandidate) => {
    setDownloadingId(c.id);
    try {
      const res = await pipelineApi.downloadOffer(c.sessionId, c.id);
      window.open(res.data.url, '_blank', 'noopener');
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not download offer'));
    } finally {
      setDownloadingId(null);
    }
  };

  // Stage counts for segmented control
  const stageCounts = useMemo(() => {
    const counts: Record<string, number> = { ALL: candidates.length };
    for (const c of candidates) {
      counts[c.stage] = (counts[c.stage] || 0) + 1;
    }
    return counts;
  }, [candidates]);

  // Filtered candidate list
  const filteredCandidates = useMemo(() => {
    return candidates.filter((c) => {
      if (selectedStage !== 'ALL' && c.stage !== selectedStage) return false;
      return true;
    });
  }, [candidates, selectedStage]);

  // Offers waiting on this approver: the department director signs first, then the HR chief
  const waitingOffers = useMemo(() => {
    if (!canApprove) return [];
    return candidates.filter((c) => {
      if (c.stage !== 'OFFER' || !c.offerFileName || c.offerUploadedById === me?.id) return false;
      if (me?.role === 'ADMIN') return c.offerStep === 'DIRECTOR' || c.offerStep === 'HR_CHIEF';
      if (me?.role === 'DEPARTMENT_DIRECTOR') return c.offerStep === 'DIRECTOR';
      return c.offerStep === 'HR_CHIEF';
    });
  }, [candidates, canApprove, me?.id, me?.role]);

  const stageOptions = PIPELINE_STAGES.map((s) => ({
    value: s.key,
    label: s.label,
    count: stageCounts[s.key] || 0,
  }));

  const subtitle = isRecruiter
    ? 'Every candidate past the shortlist, across your sessions. Longest waiting first.'
    : 'Every candidate past the shortlist, across all sessions. Longest waiting first.';

  return (
    <MainLayout>
      <PageHeader title="Pipeline" meta={subtitle} />

      {error && <Alert className="mb-6">{error}</Alert>}

      {/* Approver Panel (only with OFFER_APPROVE and at least one waiting offer) */}
      {canApprove && waitingOffers.length > 0 && (
        <Panel className="mb-6 border-line-strong p-5 bg-surface-raised animate-fade-up">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-base font-semibold text-ink">
              {waitingOffers.length} {waitingOffers.length === 1 ? 'offer' : 'offers'} waiting for your approval
            </h2>
          </div>

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {waitingOffers.map((c) => (
              <div
                key={c.id}
                className="lift rounded-xl p-4 bg-surface border border-line-strong flex flex-col justify-between space-y-3"
              >
                <div className="flex items-start gap-3">
                  <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-sunken border border-line">
                    <FileText className="size-5 text-accent-text" aria-hidden="true" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <Link
                      to={`/sessions/${c.sessionId}/candidates/${c.id}`}
                      className="font-semibold text-sm text-ink hover:underline truncate block"
                    >
                      {c.name}
                    </Link>
                    <p className="text-xs text-ink-muted truncate">{c.sessionTitle}</p>
                    {c.offerFileName && (
                      <p className="text-xs font-mono text-ink-muted truncate mt-0.5">
                        {c.offerFileName}
                        {c.offerUploaderName && ` · Uploaded by ${c.offerUploaderName}`}
                      </p>
                    )}
                    <p className="text-[11px] text-ink-muted mt-1">Waiting in offer stage: {c.inStageDays}d</p>
                  </div>
                </div>

                <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-line">
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => handleDownloadOffer(c)}
                    disabled={downloadingId === c.id}
                  >
                    {downloadingId === c.id ? <Loader2 className="size-3 animate-spin" /> : <Download className="size-3" />}
                    Download
                  </Button>
                  <Button
                    variant="primary"
                    size="sm"
                    onClick={() => handleApprove(c)}
                    disabled={approvingId === c.id}
                  >
                    {approvingId === c.id ? (
                      <Loader2 className="size-3 animate-spin" />
                    ) : (
                      <CheckCircle2 className="size-3" />
                    )}
                    {c.offerStep === 'DIRECTOR' ? 'Approve as director' : 'Approve offer'}
                  </Button>
                  <Button
                    variant="danger"
                    size="sm"
                    onClick={() => {
                      setRejectingId(rejectingId === c.id ? null : c.id);
                      setRejectNote('');
                    }}
                    aria-expanded={rejectingId === c.id}
                  >
                    <XCircle className="size-3" aria-hidden="true" />
                    Reject
                  </Button>
                </div>

                {rejectingId === c.id && (
                  <div className="space-y-2 border-t border-line pt-3">
                    <label htmlFor={`reject-${c.id}`} className="block text-[13px] font-semibold text-ink">
                      Why are you rejecting it? <span className="font-normal text-ink-muted">(optional)</span>
                    </label>
                    <Textarea
                      id={`reject-${c.id}`}
                      rows={2}
                      maxLength={1000}
                      value={rejectNote}
                      onChange={(e) => setRejectNote(e.target.value)}
                      placeholder="Tell the recruiter what to change. You can leave this empty."
                      className="min-h-[64px]"
                    />
                    <div className="flex gap-2">
                      <Button variant="dangerSolid" size="sm" onClick={() => handleReject(c)} disabled={submittingReject}>
                        {submittingReject ? 'Rejecting…' : 'Reject offer'}
                      </Button>
                      <Button variant="secondary" size="sm" onClick={() => setRejectingId(null)} disabled={submittingReject}>
                        Keep reviewing
                      </Button>
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
        </Panel>
      )}

      {/* Filters: Stage Segmented and Awaiting approval checkbox */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div className="overflow-x-auto pb-1 max-w-full">
          <Segmented
            label="Filter by stage"
            value={selectedStage}
            onChange={setSelectedStage}
            options={stageOptions}
          />
        </div>

        <label className="flex items-center gap-2 text-sm text-ink cursor-pointer select-none">
          <input
            type="checkbox"
            checked={awaitingApprovalOnly}
            onChange={(e) => setAwaitingApprovalOnly(e.target.checked)}
            className="size-4 rounded border-line text-accent focus:ring-accent-text"
          />
          <span>Awaiting approval only</span>
        </label>
      </div>

      {/* Main Table / Cards */}
      {loading ? (
        <Loader label="Loading pipeline…" />
      ) : candidates.length === 0 ? (
        <Panel className="px-6 py-14 text-center">
          <h2 className="text-base font-semibold text-ink">Nobody is past the shortlist yet.</h2>
          <p className="mx-auto mt-1 max-w-md text-sm text-ink-muted">
            Shortlist candidates in a session to start the pipeline.
          </p>
        </Panel>
      ) : filteredCandidates.length === 0 ? (
        <Panel className="px-6 py-10 text-center">
          <p className="text-sm text-ink-muted">No candidates found in this stage.</p>
        </Panel>
      ) : (
        <Panel className="overflow-hidden">
          {/* Desktop Table View */}
          <div className="hidden sm:block overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-line bg-sunken text-[12.5px] font-medium text-ink-muted">
                <tr>
                  <th scope="col" className="px-5 py-3 text-left">
                    Candidate
                  </th>
                  <th scope="col" className="px-3 py-3 text-left">
                    Role
                  </th>
                  <th scope="col" className="px-3 py-3 text-right">
                    Score
                  </th>
                  <th scope="col" className="px-3 py-3 text-left">
                    Stage
                  </th>
                  <th scope="col" className="px-3 py-3 text-right">
                    In stage
                  </th>
                  <th scope="col" className="px-5 py-3 text-left">
                    Next
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {filteredCandidates.map((c) => (
                  <tr key={c.id} className="hover:bg-sunken/40 transition-colors">
                    <td className="px-5 py-3 font-semibold text-ink">
                      <Link
                        to={`/sessions/${c.sessionId}/candidates/${c.id}`}
                        className="hover:text-accent-text hover:underline block truncate max-w-xs"
                      >
                        {c.name}
                      </Link>
                    </td>
                    <td className="px-3 py-3 text-ink-muted truncate max-w-xs">{c.sessionTitle}</td>
                    <td className="px-3 py-3 text-right font-mono font-medium text-ink">
                      {c.score != null ? formatScore(c.score) : '–'}
                    </td>
                    <td className="px-3 py-3">
                      <StageBadge stage={c.stage} />
                    </td>
                    <td className="px-3 py-3 text-right font-mono">
                      <span className={cn(c.inStageDays >= 5 ? 'text-warn font-semibold' : 'text-ink-muted')}>
                        {c.inStageDays}d
                      </span>
                    </td>
                    <td className="px-5 py-3 text-xs text-ink-muted max-w-xs truncate">{c.nextStep}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Mobile Cards View (< 640px) */}
          <div className="sm:hidden divide-y divide-line">
            {filteredCandidates.map((c) => (
              <div key={c.id} className="p-4 space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <Link
                    to={`/sessions/${c.sessionId}/candidates/${c.id}`}
                    className="font-semibold text-ink hover:underline text-sm truncate"
                  >
                    {c.name}
                  </Link>
                  <span className="font-mono text-xs font-semibold text-ink">
                    {c.score != null ? formatScore(c.score) : '–'}
                  </span>
                </div>
                <p className="text-xs text-ink-muted truncate">{c.sessionTitle}</p>
                <div className="flex items-center justify-between pt-1">
                  <StageBadge stage={c.stage} />
                  <span className={cn('text-xs font-mono', c.inStageDays >= 5 ? 'text-warn font-semibold' : 'text-ink-muted')}>
                    {c.inStageDays} days in stage
                  </span>
                </div>
                <p className="text-xs text-ink-muted pt-1 border-t border-line">
                  <span className="font-medium text-ink">Next:</span> {c.nextStep}
                </p>
              </div>
            ))}
          </div>
        </Panel>
      )}
    </MainLayout>
  );
}

