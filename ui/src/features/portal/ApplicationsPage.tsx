import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { CalendarClock, ChevronRight } from 'lucide-react';
import { apiErrorMessage, portalApi } from '@/api/client';
import { useAuthStore } from '@/features/auth/authStore';
import type { ApplicationSummary } from '@/types';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Alert, SkeletonRows } from '@/components/ui/Feedback';
import { PortalLayout } from './PortalLayout';
import { STAGE_LABEL, STAGE_TONE, formatStart } from './portal';

export default function ApplicationsPage() {
  const name = useAuthStore((s) => s.user?.fullName?.split(' ')[0]);
  const [items, setItems] = useState<ApplicationSummary[] | null>(null);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    portalApi
      .applications(page)
      .then((res) => {
        if (!active) return;
        setItems((prev) => (page === 0 ? res.data.content : [...(prev ?? []), ...res.data.content]));
        setHasMore(res.data.number + 1 < res.data.totalPages);
      })
      .catch((err) => active && setError(apiErrorMessage(err, 'Could not load your applications')))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [page]);

  return (
    <PortalLayout>
      <h1 className="text-[32px] font-bold leading-tight">{name ? `Hi ${name}` : 'Your applications'}</h1>
      <p className="mt-2 text-ink-muted">Your applications, interviews and where each one stands.</p>

      <div className="mt-8">
        {error && <Alert>{error}</Alert>}
        {!error && !items && <SkeletonRows rows={2} columns={[60, 20]} />}
        {items?.length === 0 && (
          <p className="plate p-6 text-sm text-ink-muted">No applications yet. When a recruiter invites you, the job will appear here.</p>
        )}
        {items && items.length > 0 && (
          <ul className="space-y-3">
            {items.map((item) => (
              <li key={item.id}>
                <Link
                  to={`/portal/applications/${item.id}`}
                  className="plate flex items-center gap-4 p-4 transition-colors hover:border-line-strong focus-visible:border-line-strong"
                >
                  <div className="min-w-0 flex-1 space-y-1.5">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="min-w-0 break-words text-[17px] font-semibold">{item.jobTitle}</span>
                      {item.positionCode && <span className="font-mono text-[12px] text-ink-muted">{item.positionCode}</span>}
                    </div>
                    <Badge tone={STAGE_TONE[item.stage]}>{STAGE_LABEL[item.stage]}</Badge>
                    {item.nextInterviewAt && (
                      <p className="flex items-start gap-1.5 text-[13px] text-ink-muted">
                        <CalendarClock className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
                        Next interview {formatStart(item.nextInterviewAt)}
                      </p>
                    )}
                  </div>
                  <ChevronRight className="size-5 shrink-0 text-ink-muted" aria-hidden="true" />
                </Link>
              </li>
            ))}
          </ul>
        )}
        {hasMore && (
          <div className="mt-4 flex justify-center">
            <Button variant="secondary" onClick={() => setPage((p) => p + 1)} disabled={loading}>
              {loading ? 'Loading…' : 'Show more'}
            </Button>
          </div>
        )}
      </div>
    </PortalLayout>
  );
}
