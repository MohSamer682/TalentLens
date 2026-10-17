import { useCallback, useEffect, useState } from 'react';
import { ChevronDown, X } from 'lucide-react';
import { apiErrorMessage, sessionsApi } from '@/api/client';
import type { ResumeFile } from '@/types';
import { Alert } from '@/components/ui/Feedback';

const SHOWN = 50;

/**
 * A session's uploaded resumes, collapsed by default. With `status="FAILED"` it is the
 * "could not be read" panel under the standings; without it, the list in the upload step.
 */
export function ResumeList({
  sessionId,
  status,
  title,
  canRemove,
  open,
  refreshKey,
  onRemoved,
}: {
  sessionId: string;
  status?: ResumeFile['status'];
  title: (count: number) => string;
  canRemove: boolean;
  open?: boolean;
  refreshKey?: unknown;
  onRemoved?: () => void;
}) {
  const [resumes, setResumes] = useState<ResumeFile[]>([]);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [removing, setRemoving] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const { data } = await sessionsApi.resumes(sessionId, status, 0, SHOWN);
      setResumes(data.content);
      setTotal(data.totalElements);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not load the resumes'));
    }
  }, [sessionId, status]);

  useEffect(() => {
    load();
  }, [load, refreshKey]);

  const remove = async (resume: ResumeFile) => {
    setError(null);
    setRemoving(resume.id);
    try {
      await sessionsApi.removeResume(sessionId, resume.id);
      await load();
      onRemoved?.();
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not remove the resume'));
    } finally {
      setRemoving(null);
    }
  };

  if (total === 0 && !error) return null;

  return (
    <details open={open} className="group plate">
      <summary className="flex cursor-pointer list-none items-center gap-2 px-4 py-3 text-sm font-semibold [&::-webkit-details-marker]:hidden">
        <ChevronDown className="size-4 text-ink-muted transition-transform group-open:rotate-180" aria-hidden="true" />
        <span className={status === 'FAILED' ? 'text-danger' : undefined}>{title(total)}</span>
      </summary>
      {error && <Alert className="mx-4 mb-3">{error}</Alert>}
      <ul className="max-h-72 divide-y divide-line overflow-y-auto border-t border-line">
        {resumes.map((r) => (
          <li key={r.id} className="flex items-center gap-3 px-4 py-2 text-sm">
            <div className="min-w-0 flex-1">
              <p className="truncate">{r.originalFileName ?? r.name}</p>
              {r.failureReason && <p className="truncate text-[13px] text-ink-muted" title={r.failureReason}>{r.failureReason}</p>}
            </div>
            {canRemove && (
              <button
                type="button"
                onClick={() => remove(r)}
                disabled={removing === r.id}
                aria-label={`Remove ${r.originalFileName ?? r.name}`}
                className="flex size-8 shrink-0 items-center justify-center rounded text-ink-muted hover:bg-sunken hover:text-ink disabled:opacity-40"
              >
                <X className="size-4" />
              </button>
            )}
          </li>
        ))}
      </ul>
      {total > resumes.length && (
        <p className="num border-t border-line px-4 py-2 text-[13px] text-ink-muted">
          Showing the newest {resumes.length} of {total}
        </p>
      )}
    </details>
  );
}
