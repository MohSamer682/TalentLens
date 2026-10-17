import { useEffect, useState } from 'react';
import { apiErrorMessage, candidatesApi } from '@/api/client';
import type { CandidateNote } from '@/types';
import { Button } from '@/components/ui/Button';
import { Panel } from '@/components/ui/Card';
import { Alert } from '@/components/ui/Feedback';
import { ROLE_LABEL, formatRelativeDate } from '@/lib/utils';

export function CandidateNotes({
  sessionId,
  candidateId,
}: {
  sessionId: string;
  candidateId: string;
}) {
  const [notes, setNotes] = useState<CandidateNote[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [content, setContent] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    candidatesApi
      .notes(sessionId, candidateId)
      .then((res) => {
        if (active) setNotes(res.data);
      })
      .catch((err) => {
        if (active) setError(apiErrorMessage(err, 'Could not load notes'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [sessionId, candidateId]);

  const handleAddNote = async () => {
    const trimmed = content.trim();
    if (!trimmed || submitting) return;
    setError(null);
    setSubmitting(true);
    try {
      const res = await candidatesApi.addNote(sessionId, candidateId, trimmed);
      setNotes((prev) => [res.data, ...prev]);
      setContent('');
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not add note'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault();
      handleAddNote();
    }
  };

  const handleDelete = async (noteId: string) => {
    if (deletingId) return;
    setError(null);
    setDeletingId(noteId);
    try {
      await candidatesApi.deleteNote(sessionId, candidateId, noteId);
      setNotes((prev) => prev.filter((n) => n.id !== noteId));
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not delete note'));
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <Panel className="animate-fade-up p-4">
      <div className="flex items-center justify-between gap-2 border-b border-line pb-3">
        <h2 className="text-[15px] font-semibold text-ink">
          Notes <span className="font-mono text-sm text-ink-muted">{notes.length}</span>
        </h2>
      </div>

      {error && <Alert className="mt-3">{error}</Alert>}

      <div className="mt-3 space-y-2">
        <label htmlFor="candidate-new-note" className="sr-only">
          New note
        </label>
        <textarea
          id="candidate-new-note"
          value={content}
          onChange={(e) => setContent(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Add a note for the team…"
          rows={3}
          maxLength={4000}
          className="w-full rounded-md border border-line bg-surface dark:bg-sunken p-2.5 text-sm text-ink placeholder:text-ink-muted focus:border-accent-text focus:outline-none focus:ring-2 focus:ring-accent-text/25"
        />
        <div className="flex items-center justify-between text-xs text-ink-muted">
          <span>Ctrl + Enter to add</span>
          <Button
            size="sm"
            onClick={handleAddNote}
            disabled={!content.trim() || submitting}
            className="h-8 px-3"
          >
            {submitting ? 'Adding…' : 'Add note'}
          </Button>
        </div>
      </div>

      <div className="mt-5 divide-y divide-line">
        {loading ? (
          <p className="py-4 text-center text-sm text-ink-muted">Loading notes…</p>
        ) : notes.length === 0 ? (
          <p className="py-4 text-center text-sm text-ink-muted">No notes yet for this candidate.</p>
        ) : (
          notes.map((n) => (
            <div key={n.id} className="py-3 first:pt-2">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <span className="text-sm font-semibold text-ink">{n.authorName}</span>
                  <span className="ml-2 text-[12.5px] text-ink-muted">
                    {(n.authorRole && ROLE_LABEL[n.authorRole as keyof typeof ROLE_LABEL]) || n.authorRole || 'Team'} · {formatRelativeDate(n.createdAt)}
                  </span>
                </div>
                {n.deletable && (
                  <button
                    type="button"
                    onClick={() => handleDelete(n.id)}
                    disabled={deletingId === n.id}
                    className="text-xs text-ink-muted hover:text-danger disabled:opacity-50"
                  >
                    {deletingId === n.id ? 'Deleting…' : 'Delete'}
                  </button>
                )}
              </div>
              <p className="mt-1 whitespace-pre-wrap break-words text-sm text-ink">{n.content}</p>
            </div>
          ))
        )}
      </div>
    </Panel>
  );
}
