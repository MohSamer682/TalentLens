import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { apiErrorMessage, pipelineApi } from '@/api/client';
import type { InterviewRecommendation, MyInterviewItem, MyInterviewsResponse } from '@/types';
import { Badge } from '@/components/ui/Badge';
import { Panel } from '@/components/ui/Card';
import { Alert, Loader } from '@/components/ui/Feedback';
import { InterviewPlaceText } from '@/features/pipeline/InterviewPlace';
import { KIND_LABEL } from '@/features/pipeline/pipeline';
import { MainLayout } from '@/layouts/MainLayout';
import { cn } from '@/lib/utils';

const RECOMMENDATION_LABELS: Record<InterviewRecommendation, { label: string; tone: string }> = {
  STRONG_HIRE: { label: 'Strong hire', tone: 'bg-success/15 text-success' },
  HIRE: { label: 'Hire', tone: 'bg-success/15 text-success' },
  NO_HIRE: { label: 'No hire', tone: 'bg-warn/15 text-warn' },
  STRONG_NO_HIRE: { label: 'Strong no hire', tone: 'bg-danger/15 text-danger' },
};

function formatStars(rating: number | null): string {
  if (!rating) return '';
  const full = Math.min(5, Math.max(1, rating));
  return '★'.repeat(full) + '☆'.repeat(5 - full) + ` ${full}/5`;
}

function parseDateTile(iso: string) {
  try {
    const d = new Date(iso);
    const weekday = d.toLocaleDateString('en-GB', { weekday: 'short' });
    const day = d.toLocaleDateString('en-GB', { day: 'numeric' });
    return { weekday, day };
  } catch {
    return { weekday: '–', day: '–' };
  }
}

function formatTimeRange(iso: string, durationMinutes: number) {
  try {
    const start = new Date(iso);
    const end = new Date(start.getTime() + durationMinutes * 60 * 1000);
    const startTime = start.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    const endTime = end.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    return `${startTime} – ${endTime}`;
  } catch {
    return iso;
  }
}

export default function MyInterviews() {
  const [data, setData] = useState<MyInterviewsResponse>({ waiting: [], upcoming: [], done: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    pipelineApi
      .myInterviews()
      .then((res) => {
        if (active) setData(res.data);
      })
      .catch((err) => {
        if (active) setError(apiErrorMessage(err, 'Could not load your interviews'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const totalInterviews = data.waiting.length + data.upcoming.length + data.done.length;

  return (
    <MainLayout>
      <div className="mx-auto max-w-[1080px] space-y-8">
        <div>
          <h1 className="text-[30px] font-bold text-ink">My interviews</h1>
          <p className="mt-1 text-sm text-ink-muted">
            Interviews assigned to you. Prepare, review candidate AI screens, and submit feedback.
          </p>
        </div>

        {error && <Alert>{error}</Alert>}

        {loading ? (
          <Loader label="Loading your interviews…" />
        ) : totalInterviews === 0 ? (
          <Panel className="px-6 py-14 text-center">
            <h2 className="text-base font-semibold text-ink">No interviews assigned to you yet.</h2>
            <p className="mx-auto mt-1 max-w-md text-sm text-ink-muted">
              They'll appear here when a recruiter schedules one.
            </p>
          </Panel>
        ) : (
          <div className="space-y-6">
            {/* 1. Waiting for your feedback */}
            {data.waiting.length > 0 && (
              <Panel className="overflow-hidden p-0 animate-fade-up">
                <div className="flex items-center justify-between border-b border-line px-5 py-3.5 bg-surface">
                  <h2 className="text-[15px] font-semibold text-ink flex items-center gap-2">
                    Waiting for your feedback
                    <span className="font-mono text-xs font-semibold px-2 py-0.5 rounded-full bg-warn/15 text-warn">
                      {data.waiting.length}
                    </span>
                  </h2>
                </div>
                <div className="divide-y divide-line">
                  {data.waiting.map((item) => (
                    <InterviewRow key={item.id} item={item} group="waiting" />
                  ))}
                </div>
              </Panel>
            )}

            {/* 2. Upcoming */}
            {data.upcoming.length > 0 && (
              <Panel className="overflow-hidden p-0 animate-fade-up">
                <div className="flex items-center justify-between border-b border-line px-5 py-3.5 bg-surface">
                  <h2 className="text-[15px] font-semibold text-ink flex items-center gap-2">
                    Upcoming
                    <span className="font-mono text-xs font-semibold px-2 py-0.5 rounded-full bg-sunken text-ink">
                      {data.upcoming.length}
                    </span>
                  </h2>
                </div>
                <div className="divide-y divide-line">
                  {data.upcoming.map((item) => (
                    <InterviewRow key={item.id} item={item} group="upcoming" />
                  ))}
                </div>
              </Panel>
            )}

            {/* 3. Done */}
            {data.done.length > 0 && (
              <Panel className="overflow-hidden p-0 animate-fade-up">
                <div className="flex items-center justify-between border-b border-line px-5 py-3.5 bg-surface">
                  <h2 className="text-[15px] font-semibold text-ink flex items-center gap-2">
                    Done
                    <span className="font-mono text-xs font-semibold px-2 py-0.5 rounded-full bg-sunken text-ink">
                      {data.done.length}
                    </span>
                  </h2>
                </div>
                <div className="divide-y divide-line">
                  {data.done.map((item) => (
                    <InterviewRow key={item.id} item={item} group="done" />
                  ))}
                </div>
              </Panel>
            )}
          </div>
        )}
      </div>
    </MainLayout>
  );
}

function InterviewRow({
  item,
  group,
}: {
  item: MyInterviewItem;
  group: 'waiting' | 'upcoming' | 'done';
}) {
  const { weekday, day } = parseDateTile(item.scheduledStart);
  const timeRange = formatTimeRange(item.scheduledStart, item.durationMinutes);

  return (
    <div
      className={cn(
        'group relative flex flex-wrap sm:flex-nowrap items-center gap-4 px-5 py-4 transition-all focus-within:ring-2 focus-within:ring-accent-text focus-within:ring-inset',
        group === 'waiting'
          ? 'bg-warn/[0.04] hover:bg-warn/[0.08]'
          : 'bg-surface hover:bg-sunken/50'
      )}
    >
      {/* 64px Date Tile */}
      <div className="size-16 shrink-0 rounded-xl bg-sunken border border-line flex flex-col items-center justify-center text-center">
        <span className="text-[12.5px] font-medium text-ink-muted leading-tight">
          {weekday}
        </span>
        <span className="font-mono text-xl font-bold text-ink leading-tight">{day}</span>
      </div>

      {/* Candidate, Role, Time, Location */}
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2.5">
          <h3 className="text-base font-semibold text-ink group-hover:text-accent-text transition-colors truncate">
            <Link
              to={`/interviews/${item.id}/feedback`}
              className="rounded-sm after:absolute after:inset-0 focus:outline-none"
            >
              {item.candidateName}
            </Link>
          </h3>
          <Badge tone="neutral">{KIND_LABEL[item.kind] ?? item.kind}</Badge>
        </div>
        <p className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[12.5px] text-ink-muted">
          <span>
            {item.role} · {timeRange}
          </span>
          {/* Above the stretched row link, so a meeting link can be clicked */}
          <InterviewPlaceText placeType={item.placeType} location={item.location} className="relative z-10" />
        </p>
      </div>

      {/* Action / Outcome */}
      <div className="shrink-0 flex items-center gap-3">
        {group === 'waiting' && (
          <span className="text-sm font-semibold text-warn group-hover:translate-x-0.5 transition-transform">
            Give feedback →
          </span>
        )}

        {group === 'upcoming' && (
          <span className="text-sm font-semibold text-accent-text group-hover:translate-x-0.5 transition-transform">
            Prepare →
          </span>
        )}

        {group === 'done' && (
          <div className="flex items-center gap-2">
            {item.recommendation && (
              <span
                className={cn(
                  'rounded-full px-2.5 py-0.5 text-xs font-semibold',
                  RECOMMENDATION_LABELS[item.recommendation]?.tone
                )}
              >
                {RECOMMENDATION_LABELS[item.recommendation]?.label}
              </span>
            )}
            {item.rating && (
              <span
                className="font-mono text-xs font-semibold text-ink"
                aria-label={`${item.rating} out of 5 stars`}
              >
                {formatStars(item.rating)}
              </span>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
