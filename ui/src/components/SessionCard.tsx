import { Link } from 'react-router-dom';
import { useState, useEffect } from 'react';
import { sessionsApi } from '@/api/client';
import type { ScreeningSession } from '@/types';
import { Badge } from '@/components/ui/Badge';
import { FileText, Edit2, Eye, AlertCircle, Trash2, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';

interface SessionCardProps {
  session: ScreeningSession;
  onDelete?: () => void;
}

export function SessionCard({ session, onDelete }: SessionCardProps) {
  const [percent, setPercent] = useState(0);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  useEffect(() => {
    if (session.status !== 'RUNNING') return;
    let active = true;
    const poll = async () => {
      try {
        const res = await sessionsApi.progress(session.id);
        if (active) setPercent(res.data.percent);
      } catch {
        // silent
      }
    };
    poll();
    const id = setInterval(poll, 3000);
    return () => { active = false; clearInterval(id); };
  }, [session.id, session.status]);

  const statusVariant = {
    PENDING: 'default' as const,
    RUNNING: 'running' as const,
    DONE: 'success' as const,
    FAILED: 'error' as const,
  };

  const handleDelete = async () => {
    setDeleteError(null);
    if (!confirm('Are you sure you want to delete this session?')) return;
    try {
      await sessionsApi.delete(session.id);
      onDelete?.();
    } catch (err: any) {
      const msg = err?.response?.data?.message || 'Failed to delete session';
      setDeleteError(msg);
    }
  };

  const handleCancel = async () => {
    setDeleteError(null);
    if (!confirm('Cancel this screening session?')) return;
    try {
      await sessionsApi.cancel(session.id);
      onDelete?.(); // trigger reload to show failed status
    } catch (err: any) {
      setDeleteError(err?.response?.data?.message || 'Failed to cancel');
    }
  };

  return (
    <div className="bg-surface-container-lowest border border-surface-container rounded-3xl p-8 shadow-sm hover:shadow-xl hover:border-primary/20 transition-all group flex flex-col justify-between min-h-[320px]">
      <div className="space-y-6">
        <div className="flex justify-between items-start gap-4">
          <h3 className="text-2xl font-bold tracking-tight text-on-surface leading-tight">
            {session.title}
          </h3>
          <Badge variant={statusVariant[session.status]}>{session.status}</Badge>
        </div>

        <div className="flex items-center gap-2 text-on-surface-variant">
          <FileText className="text-base" />
          <span className="text-sm font-semibold">
            {session.candidateCount} resumes
          </span>
        </div>

        {session.status === 'RUNNING' && (
          <div className="space-y-3 pt-4">
            <div className="flex justify-between text-[11px] font-black text-on-surface-variant uppercase tracking-wider">
              <span>Progress</span>
              <span>{percent}%</span>
            </div>
            <div className="h-2 w-full bg-surface-container-high rounded-full overflow-hidden">
              <div
                className="h-full bg-primary rounded-full shadow-primary/20 transition-all duration-500"
                style={{ width: `${percent}%` }}
              />
            </div>
          </div>
        )}

        {session.status === 'FAILED' && (
          <div className="flex items-start gap-3 text-red-600 text-xs font-bold bg-red-50 border border-red-200 p-4 rounded-2xl mt-2">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            <span>{session.lastErrorMessage || 'Screening failed. Check logs or retry.'}</span>
          </div>
        )}

        {deleteError && (
          <div className="flex items-start gap-3 text-red-600 text-xs font-bold bg-red-50 border border-red-200 p-4 rounded-2xl mt-2">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            <span>{deleteError}</span>
          </div>
        )}
      </div>

      <div className="flex items-center gap-3 mt-10">
        {session.status === 'DONE' ? (
          <Link to={`/sessions/${session.id}`} className="flex-1">
            <Button variant="outline" className="w-full justify-center">
              <Eye className="w-4 h-4" />
              View Results
            </Button>
          </Link>
        ) : session.status === 'RUNNING' ? (
          <Link to={`/sessions/${session.id}`} className="flex-1">
            <Button variant="outline" className="w-full justify-center">
              <Eye className="w-4 h-4" />
              View Progress
            </Button>
          </Link>
        ) : (
          <Link to={`/sessions/new/${session.id}`} className="flex-1">
            <Button variant="outline" className="w-full justify-center">
              <Eye className="w-4 h-4" />
              Continue Setup
            </Button>
          </Link>
        )}
        <Link to={`/sessions/new/${session.id}?step=1`}>
          <Button variant="ghost" size="icon">
            <Edit2 className="w-4 h-4" />
          </Button>
        </Link>
        
        {session.status === 'RUNNING' && (
          <Button
            variant="ghost"
            size="icon"
            className="text-orange-500 hover:text-orange-700 hover:bg-orange-50"
            title="Cancel screening"
            onClick={handleCancel}
          >
            <X className="w-4 h-4" />
          </Button>
        )}

        <Button
          variant="ghost"
          size="icon"
          className="text-red-500 hover:text-red-700 hover:bg-red-50"
          onClick={handleDelete}
        >
          <Trash2 className="w-4 h-4" />
        </Button>
      </div>
    </div>
  );
}