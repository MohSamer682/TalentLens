import { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { sessionsApi } from '@/api/client';
import type { ScreeningSession, ProgressResponse, CandidateResultDto } from '@/types';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { getScoreColor, formatDate } from '@/lib/utils';
import { MainLayout } from '@/layouts/MainLayout';
import { ArrowLeft, Download, RotateCcw, Check, Loader2, BarChart3, Edit2, Trash2, AlertCircle, X } from 'lucide-react';

export default function SessionDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [session, setSession] = useState<ScreeningSession | null>(null);
  const [progress, setProgress] = useState<ProgressResponse | null>(null);
  const [candidates, setCandidates] = useState<CandidateResultDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    if (id) loadData();
  }, [id]);

  useEffect(() => {
    if (session?.status === 'RUNNING') {
      loadProgress();
      const interval = setInterval(loadProgress, 3000);
      return () => clearInterval(interval);
    }
  }, [session?.status]);

  const loadData = async () => {
    if (!id) return;
    try {
      const [sessionRes, candidatesRes] = await Promise.all([
        sessionsApi.get(id),
        sessionsApi.candidates(id),
      ]);
      setSession(sessionRes.data);
      setCandidates(candidatesRes.data.content || []);
    } catch (err) {
      console.error('Failed to load session:', err);
    } finally {
      setLoading(false);
    }
  };

  const loadProgress = async () => {
    if (!id) return;
    try {
      const res = await sessionsApi.progress(id);
      setProgress(res.data);
      if (res.data.status === 'DONE' || res.data.status === 'FAILED') {
        loadData();
      }
    } catch (err) {
      console.error('Failed to load progress:', err);
    }
  };

  const handleExport = async () => {
    if (!id) return;
    try {
      const res = await sessionsApi.export(id);
      window.open(res.data.url, '_blank');
    } catch (err) {
      console.error('Failed to export:', err);
    }
  };

  const handleRetry = async () => {
    if (!id) return;
    try {
      await sessionsApi.start(id);
      loadData();
    } catch (err) {
      console.error('Failed to retry:', err);
    }
  };

  const handleDelete = async () => {
    if (!id) return;
    setActionError(null);
    if (confirm('Are you sure you want to delete this session?')) {
      try {
        await sessionsApi.delete(id);
        navigate('/dashboard');
      } catch (err: any) {
        setActionError(err?.response?.data?.message || 'Failed to delete session');
      }
    }
  };

  const handleCancel = async () => {
    if (!id) return;
    setActionError(null);
    if (confirm('Cancel this screening? It will be marked as FAILED.')) {
      try {
        await sessionsApi.cancel(id);
        loadData(); // refresh session status
      } catch (err: any) {
        setActionError(err?.response?.data?.message || 'Failed to cancel');
      }
    }
  };

  const formatScore = (value: number | undefined) => {
    if (typeof value !== 'number' || !Number.isFinite(value)) return '0';
    return value.toFixed(1).replace(/\.0$/, '');
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-surface flex items-center justify-center">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    );
  }

  if (!session) {
    return (
      <div className="min-h-screen bg-surface flex items-center justify-center">
        <p>Session not found</p>
      </div>
    );
  }

  const statusVariant = {
    PENDING: 'default' as const,
    RUNNING: 'running' as const,
    DONE: 'success' as const,
    FAILED: 'error' as const,
  };

  return (
    <MainLayout maxWidth="max-w-6xl">
      <Link
        to="/dashboard"
        className="inline-flex items-center text-on-surface-variant hover:text-primary mb-8"
      >
        <ArrowLeft className="w-4 h-4 mr-2" />
        Back to Dashboard
      </Link>

      {actionError && (
        <div className="flex items-start gap-3 text-red-600 text-sm font-bold bg-red-50 border border-red-200 p-4 rounded-2xl mb-6">
          <AlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5" />
          <span>{actionError}</span>
        </div>
      )}

      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-4xl font-bold tracking-tight text-on-surface">
            {session.title}
          </h1>
          <div className="flex items-center gap-4 mt-2">
            <Badge variant={statusVariant[session.status]}>
              {session.status}
            </Badge>
            <span className="text-on-surface-variant">
              {formatDate(session.createdAt)}
            </span>
          </div>
        </div>
        <div className="flex gap-3">
          {session.status === 'RUNNING' && (
            <Button variant="outline" className="text-orange-500 border-orange-200 hover:bg-orange-50" onClick={handleCancel}>
              <X className="w-4 h-4" />
              Cancel Screening
            </Button>
          )}
          <Button variant="ghost" className="text-red-500 hover:bg-red-50 hover:text-red-700" onClick={handleDelete}>
            <Trash2 className="w-4 h-4" />
            Delete
          </Button>
          {(session.status === 'PENDING' || session.status === 'FAILED') && (
            <Link to={`/sessions/new/${session.id}?step=1`}>
              <Button variant="outline">
                <Edit2 className="w-4 h-4" />
                Edit Session
              </Button>
            </Link>
          )}
          {session.status === 'DONE' && (
            <Button onClick={handleExport}>
              <Download className="w-4 h-4" />
              Download Excel
            </Button>
          )}
        </div>
      </div>

      {session.status === 'RUNNING' && (
        <div className="bg-white rounded-3xl p-8 mb-8 shadow-sm">
          <div className="flex items-center justify-center mb-8">
            <div className="relative w-40 h-40">
              <svg className="w-full h-full" viewBox="0 0 100 100">
                <circle
                  cx="50"
                  cy="50"
                  r="45"
                  fill="none"
                  stroke="#e5e9eb"
                  strokeWidth="8"
                />
                <circle
                  cx="50"
                  cy="50"
                  r="45"
                  fill="none"
                  stroke="#4647d3"
                  strokeWidth="8"
                  strokeDasharray={`${(progress?.percent ?? 0) * 2.83} 283`}
                  strokeLinecap="round"
                  transform="rotate(-90 50 50)"
                />
              </svg>
              <div className="absolute inset-0 flex items-center justify-center">
                <span className="text-3xl font-bold text-primary">
                  {progress?.percent ?? 0}%
                </span>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-8 text-center">
            <div>
              <p className="text-2xl font-bold">
                {progress?.processed ?? 0} / {progress?.total ?? 0}
              </p>
              <p className="text-sm text-on-surface-variant">Processed</p>
            </div>
            <div>
              <p className="text-2xl font-bold text-red-600">{progress?.failed ?? 0}</p>
              <p className="text-sm text-on-surface-variant">Failed</p>
            </div>
            <div>
              <p className="text-2xl font-bold">~4 min</p>
              <p className="text-sm text-on-surface-variant">Est. time</p>
            </div>
          </div>

          <div className="flex items-center justify-center gap-4 mt-8">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-full bg-green-500 flex items-center justify-center">
                <Check className="w-4 h-4 text-white" />
              </div>
              <span className="text-sm font-medium">Extract Text</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-full bg-primary flex items-center justify-center">
                <Loader2 className="w-4 h-4 text-white animate-spin" />
              </div>
              <span className="text-sm font-medium">Score with LLM</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-full bg-surface-container flex items-center justify-center">
                <BarChart3 className="w-4 h-4 text-on-surface-variant" />
              </div>
              <span className="text-sm font-medium">Rank & Finalize</span>
            </div>
          </div>
        </div>
      )}

      {session.status === 'FAILED' && (
        <div className="bg-red-50 border border-red-100 rounded-3xl p-8 mb-8">
          <div className="flex items-start gap-4">
            <div className="w-10 h-10 rounded-full bg-red-100 flex items-center justify-center flex-shrink-0">
              <AlertCircle className="w-5 h-5 text-red-600" />
            </div>
            <div>
              <h3 className="text-lg font-bold text-red-900">Screening Failed</h3>
              <p className="text-red-700 mt-1 italic">
                {session.lastErrorMessage || 'An unexpected error occurred during processing.'}
              </p>
            </div>
          </div>
          <div className="flex gap-3 mt-6">
            <Button variant="destructive" onClick={handleRetry}>
              <RotateCcw className="w-4 h-4" />
              Retry
            </Button>
            <Link to={`/sessions/new/${session.id}?step=1`}>
              <Button variant="outline">
                <Edit2 className="w-4 h-4" />
                Edit & Re-upload
              </Button>
            </Link>
          </div>
        </div>
      )}

      {session.status === 'DONE' && (
        <div className="bg-green-50 border border-green-100 rounded-3xl p-6 mb-8">
          <p className="text-green-700 font-semibold">
            Screening complete — {candidates.length} candidates ranked
          </p>
        </div>
      )}

      {session.status === 'DONE' && candidates.length > 0 && (
        <div className="bg-white rounded-3xl shadow-sm overflow-hidden">
          <table className="w-full">
            <thead className="bg-surface-container-low text-on-surface-variant">
              <tr>
                <th className="px-6 py-4 text-left text-xs font-semibold uppercase">
                  Rank
                </th>
                <th className="px-6 py-4 text-left text-xs font-semibold uppercase">
                  Name
                </th>
                <th className="px-6 py-4 text-left text-xs font-semibold uppercase">
                  Email
                </th>
                <th className="px-6 py-4 text-right text-xs font-semibold uppercase">
                  Score
                </th>
                <th className="px-6 py-4 text-right text-xs font-semibold uppercase">
                  Technical
                </th>
                <th className="px-6 py-4 text-right text-xs font-semibold uppercase">
                  Experience
                </th>
                <th className="px-6 py-4 text-right text-xs font-semibold uppercase">
                  Education
                </th>
                <th className="px-6 py-4 text-right text-xs font-semibold uppercase">
                  Soft Skills
                </th>
                <th className="px-6 py-4 text-right text-xs font-semibold uppercase">
                  Culture Fit
                </th>
                <th className="px-6 py-4 text-right text-xs font-semibold uppercase">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-surface-container/50">
              {candidates.map((c) => (
                <tr key={c.id} className="hover:bg-surface-container-low/30">
                  <td className="px-6 py-4 font-bold">{c.rank}</td>
                  <td className="px-6 py-4 font-medium">{c.name}</td>
                  <td className="px-6 py-4 text-on-surface-variant">{c.email}</td>
                  <td className="px-6 py-4 text-right">
                    <span
                      className={`px-3 py-1 rounded-full text-xs font-semibold ${getScoreColor(
                        c.compositeScore
                      )}`}
                    >
                      {formatScore(c.compositeScore)}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-right">{formatScore(c.technicalSkills)}</td>
                  <td className="px-6 py-4 text-right">{formatScore(c.experience)}</td>
                  <td className="px-6 py-4 text-right">{formatScore(c.education)}</td>
                  <td className="px-6 py-4 text-right">{formatScore(c.softSkills)}</td>
                  <td className="px-6 py-4 text-right">{formatScore(c.cultureFit)}</td>
                  <td className="px-6 py-4 text-right">
                    <Link to={`/sessions/${id}/candidate/${c.id}`}>
                      <Button variant="ghost" size="sm">
                        View
                      </Button>
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </MainLayout>
  );
}
