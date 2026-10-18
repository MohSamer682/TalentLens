import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ArrowDown, ArrowUp } from 'lucide-react';
import { analyticsApi, apiErrorMessage } from '@/api/client';
import type { AnalyticsOverview, FunnelStep, InterviewerStat, StageWait } from '@/types';
import { Button } from '@/components/ui/Button';
import { Alert, SkeletonRows } from '@/components/ui/Feedback';
import { Panel, PanelHeader } from '@/components/ui/Card';
import { Segmented } from '@/components/ui/Segmented';
import { MainLayout, PageHeader } from '@/layouts/MainLayout';
import { formatCount, formatHours } from '@/lib/analytics';
import { cn, formatDate } from '@/lib/utils';

type Days = '30' | '90' | '365';
type InterviewerFilter = 'ALL' | 'TECHNICAL' | 'HR';

const PERIODS: { value: Days; label: string }[] = [
  { value: '30', label: '30 days' },
  { value: '90', label: '90 days' },
  { value: '365', label: '12 months' },
];

const shortDate = (d: string) =>
  new Date(d).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });

function formatRate(r: number | null | undefined): string {
  if (r == null) return '–';
  return `${Math.round(r * 100)}%`;
}

function formatDays(d: number | null | undefined): string {
  if (d == null) return '–';
  return `${d.toFixed(1)}d`;
}

export default function Analytics() {
  const [days, setDays] = useState<Days>('90');
  const [data, setData] = useState<AnalyticsOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await analyticsApi.overview(Number(days) as 30 | 90 | 365);
      setData(res.data);
    } catch (err) {
      setError(apiErrorMessage(err, 'Could not load analytics'));
    } finally {
      setLoading(false);
    }
  }, [days]);

  useEffect(() => {
    load();
  }, [load]);

  const headerCaption = useMemo(() => {
    if (!data) return '';
    const t = data.totals;
    const ranked = t.sessionsByStatus?.DONE || 0;
    const scoring = t.sessionsByStatus?.RUNNING || 0;
    const failed = t.sessionsByStatus?.FAILED || 0;
    return `Candidates from sessions created ${formatDate(data.from)} to ${formatDate(data.to)} · ${t.sessions} sessions · ${ranked} ranked · ${scoring} scoring · ${failed} failed`;
  }, [data]);

  return (
    <MainLayout>
      <PageHeader
        title="Recruiting analytics"
        meta={data && !error && <span>{headerCaption}</span>}
        actions={<Segmented label="Period" value={days} onChange={setDays} options={PERIODS} />}
      />

      {loading && !data ? (
        <div className="space-y-6">
          <Panel>
            <SkeletonRows rows={3} columns={[20, 20, 20, 20, 20]} />
          </Panel>
          <div className="grid gap-6 lg:grid-cols-2 [&>*]:min-w-0">
            <Panel>
              <SkeletonRows rows={5} columns={[40, 60]} />
            </Panel>
            <Panel>
              <SkeletonRows rows={5} columns={[40, 60]} />
            </Panel>
          </div>
        </div>
      ) : error ? (
        <Alert
          title="Analytics could not be loaded"
          action={
            <Button variant="secondary" size="sm" onClick={load}>
              Try again
            </Button>
          }
        >
          {error}
        </Alert>
      ) : data && data.totals.sessions === 0 && data.totals.resumesUploaded === 0 ? (
        <Panel className="px-6 py-14 text-center">
          <h2 className="text-lg font-semibold text-ink">Nothing to report for this period yet</h2>
          <p className="mx-auto mt-1 max-w-md text-sm text-ink-muted">
            Analytics fill in as sessions are scored. Try a longer period, or check back once recruiters have run a session.
          </p>
        </Panel>
      ) : (
        data && <FullFunnelReport data={data} days={days} busy={loading} />
      )}
    </MainLayout>
  );
}

function FullFunnelReport({
  data,
  days,
  busy,
}: {
  data: AnalyticsOverview;
  days: Days;
  busy: boolean;
}) {
  const t = data.totals;
  const pt = data.priorTotals;
  const daysLabel = days === '365' ? '12 months' : `${days} days`;

  return (
    <div className={cn('space-y-6 transition-opacity', busy && 'opacity-60')} aria-busy={busy}>
      {/* 2. KPI Strip (5 cells) */}
      <dl className="grid grid-cols-2 gap-px overflow-hidden plate bg-line lg:grid-cols-5">
        <KpiCell
          label="Resumes scored"
          value={formatCount(t.resumesScored)}
          priorDelta={
            pt ? (
              <DeltaChip
                current={t.resumesScored}
                prior={pt.resumesScored}
                formatValue={(v) => formatCount(v)}
              />
            ) : null
          }
          caption={pt ? `vs ${formatCount(pt.resumesScored)} in prior ${daysLabel}` : 'in this period'}
        />

        <KpiCell
          label="Shortlist rate"
          value={formatRate(t.shortlistRate)}
          priorDelta={
            pt ? (
              <DeltaChip
                current={t.shortlistRate != null ? Math.round(t.shortlistRate * 100) : null}
                prior={pt.shortlistRate != null ? Math.round(pt.shortlistRate * 100) : null}
                formatValue={(v) => `${v}%`}
              />
            ) : null
          }
          caption="of scored resumes"
        />

        <KpiCell
          label="Interviews completed"
          value={formatCount(t.interviewsCompleted ?? 0)}
          priorDelta={
            pt ? (
              <DeltaChip
                current={t.interviewsCompleted ?? 0}
                prior={pt.interviewsCompleted}
                formatValue={(v) => formatCount(v)}
              />
            ) : null
          }
          caption={pt ? `vs ${formatCount(pt.interviewsCompleted)} in prior ${daysLabel}` : 'in this period'}
        />

        <KpiCell
          label="Offers approved"
          value={formatCount(t.offersApproved ?? 0)}
          priorDelta={
            pt ? (
              <DeltaChip
                current={t.offersApproved ?? 0}
                prior={pt.offersApproved}
                formatValue={(v) => formatCount(v)}
              />
            ) : null
          }
          caption={pt ? `vs ${formatCount(pt.offersApproved)} in prior ${daysLabel}` : 'in this period'}
        />

        <KpiCell
          label="Median days shortlist to offer"
          value={formatDays(t.medianDaysShortlistToOffer)}
          className="col-span-2 lg:col-span-1"
          priorDelta={
            pt && t.medianDaysShortlistToOffer != null && pt.medianDaysShortlistToOffer != null ? (
              <DeltaChip
                current={t.medianDaysShortlistToOffer}
                prior={pt.medianDaysShortlistToOffer}
                lowerIsBetter
                formatValue={(v) => `${v.toFixed(1)}d`}
              />
            ) : null
          }
          caption="from shortlist to approved offer"
        />
      </dl>

      {/* 3. Two plates: Where do candidates drop out? and Where do they wait? */}
      <div className="grid gap-6 lg:grid-cols-2 [&>*]:min-w-0">
        <DropOutFunnel funnel={data.funnel ?? []} totalUploaded={t.resumesUploaded} />
        <TimeInStage stageWaits={data.stageWaits ?? []} />
      </div>

      {/* 4. Two plates: How much moved each week? and Does the AI score agree with people? */}
      <div className="grid gap-6 lg:grid-cols-2 [&>*]:min-w-0">
        <WeeklyThroughput weeks={data.weeks} />
        <ScoreCalibration bands={data.scoreBands} />
      </div>

      {/* 5. How are interviews going? */}
      <InterviewerAnalytics interviewers={data.interviewers ?? []} />

      {/* 6. Recruiters table */}
      <RecruitersTable rows={data.recruiters} />
    </div>
  );
}

function KpiCell({
  label,
  value,
  priorDelta,
  caption,
  className,
}: {
  label: string;
  value: string;
  priorDelta?: React.ReactNode;
  caption: string;
  className?: string;
}) {
  return (
    <div className={cn('bg-surface px-4 py-4 sm:px-5 flex flex-col justify-between', className)}>
      <dt className="text-[12.5px] font-medium text-ink-muted">{label}</dt>
      <dd className="mt-2 flex items-baseline justify-between gap-2">
        <span className="font-mono text-[30px] font-semibold text-ink leading-none">{value}</span>
        {priorDelta}
      </dd>
      <dd className="mt-2 text-xs text-ink-muted truncate">{caption}</dd>
    </div>
  );
}

function DeltaChip({
  current,
  prior,
  lowerIsBetter = false,
  formatValue = (v) => `${v}`,
}: {
  current: number | null;
  prior: number | null;
  lowerIsBetter?: boolean;
  formatValue?: (v: number) => string;
}) {
  if (current == null || prior == null || prior === 0) {
    return null;
  }
  const diff = current - prior;
  const pct = Math.round((diff / prior) * 100);
  if (pct === 0) {
    return (
      <span className="inline-flex items-center text-xs text-ink-muted" title={`Prior: ${formatValue(prior)}`}>
        0%
      </span>
    );
  }
  const isPositive = pct > 0;
  const isGood = lowerIsBetter ? !isPositive : isPositive;
  const Arrow = isPositive ? ArrowUp : ArrowDown;
  const sign = isPositive ? '+' : '';

  return (
    <span
      className={cn(
        'inline-flex items-center gap-0.5 rounded-full px-2 py-0.5 text-xs font-semibold',
        isGood ? 'bg-success/15 text-success' : 'bg-danger/15 text-danger'
      )}
      title={`Prior: ${formatValue(prior)}`}
    >
      <Arrow className="size-3" aria-hidden="true" />
      {sign}{pct}%
    </span>
  );
}

function DropOutFunnel({
  funnel,
  totalUploaded,
}: {
  funnel: FunnelStep[];
  totalUploaded: number;
}) {
  const max = Math.max(1, totalUploaded, ...funnel.map((s) => s.count));

  // Determine biggest drop after the shortlist by lowest conversion rate
  const postShortlist = funnel.filter(
    (s) =>
      ['CONTACTED', 'INTERVIEWED', 'OFFER', 'APPROVED'].includes(s.stage) &&
      s.conversionRate != null
  );
  let biggestDropStage: string | null = null;
  let minConversion = 1.0;
  for (const s of postShortlist) {
    if (s.conversionRate != null && s.conversionRate < minConversion) {
      minConversion = s.conversionRate;
      biggestDropStage = s.stage;
    }
  }
  const biggestDropIndex = funnel.findIndex((s) => s.stage === biggestDropStage);
  const biggestDropLost = biggestDropIndex > 0 ? funnel[biggestDropIndex - 1].count - funnel[biggestDropIndex].count : 0;

  return (
    <Panel className="p-5 space-y-4">
      <PanelHeader title="Where do candidates drop out?" />
      <div className="space-y-3 pt-2">
        {funnel.map((step) => {
          const isShortlisted = step.stage === 'SHORTLISTED';
          const isBiggestDrop = step.stage === biggestDropStage && minConversion < 1.0;
          const barWidth = Math.max(2, Math.round((step.count / max) * 100));

          return (
            <div key={step.stage} className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
              <span className="w-24 shrink-0 text-xs font-medium text-ink-muted truncate">
                {step.label}
              </span>

              <div className="flex-1 min-w-16 h-5 rounded bg-sunken overflow-hidden relative">
                <div
                  className={cn(
                    'h-full rounded transition-all duration-500',
                    isShortlisted ? 'bg-mark' : 'bg-accent-bar'
                  )}
                  style={{ width: `${barWidth}%` }}
                />
              </div>

              <div className="w-24 shrink-0 text-right flex items-center justify-end gap-2">
                <span
                  className={cn(
                    'font-mono text-xs font-semibold',
                    isShortlisted ? 'text-mark-text' : 'text-ink'
                  )}
                >
                  {formatCount(step.count)}
                </span>
                <span className="font-mono text-xs text-ink-muted w-10 text-right">
                  {step.conversionRate != null ? formatRate(step.conversionRate) : '–'}
                </span>
              </div>

              {isBiggestDrop && (
                <span className="shrink-0 rounded-full bg-warn/15 px-2 py-0.5 text-[11px] font-semibold text-warn">
                  Biggest drop · {formatCount(biggestDropLost)} lost
                </span>
              )}
            </div>
          );
        })}
      </div>
    </Panel>
  );
}

function TimeInStage({ stageWaits }: { stageWaits: StageWait[] }) {
  const max = Math.max(1, ...stageWaits.map((s) => s.p90Days || s.medianDays || 0));

  let slowestWait: StageWait | null = null;
  let maxWaitDays = 0;
  for (const s of stageWaits) {
    if ((s.medianDays || 0) > maxWaitDays) {
      maxWaitDays = s.medianDays!;
      slowestWait = s;
    }
  }

  return (
    <Panel className="p-5 space-y-4">
      <PanelHeader title="Where do they wait?" />
      <div className="space-y-4 pt-2">
        {stageWaits.map((w) => {
          const isSlowest = slowestWait && w.stage === slowestWait.stage;
          const med = w.medianDays || 0;
          const p90 = w.p90Days || 0;
          const medWidth = Math.max(0, Math.round((med / max) * 100));
          const p90Pos = Math.max(0, Math.round((p90 / max) * 100));

          return (
            <div key={w.stage} className="space-y-1">
              <div className="flex items-center justify-between text-xs">
                <span className="font-medium text-ink truncate">{w.label}</span>
                <span className="font-mono text-ink-muted">
                  {w.medianDays != null ? `${w.medianDays.toFixed(1)}d median` : '–'} ·{' '}
                  {w.p90Days != null ? `${w.p90Days.toFixed(1)}d p90` : '–'}
                </span>
              </div>

              <div className="relative h-3 w-full rounded-full bg-sunken overflow-hidden">
                <div
                  className={cn(
                    'h-full rounded-full transition-all duration-500',
                    isSlowest ? 'bg-warn' : 'bg-accent-bar'
                  )}
                  style={{ width: `${medWidth}%` }}
                />
                {p90 > 0 && (
                  <span
                    className="absolute top-0 bottom-0 w-1 bg-ink-muted rounded-full"
                    style={{ left: `${p90Pos}%` }}
                    title={`p90: ${p90.toFixed(1)} days`}
                  />
                )}
              </div>
            </div>
          );
        })}

        {slowestWait && slowestWait.medianDays != null && (
          <p className="pt-2 text-xs font-medium text-warn border-t border-line">
            Candidates spend the most time waiting in {slowestWait.label} (median{' '}
            {slowestWait.medianDays.toFixed(1)}d).
          </p>
        )}
      </div>
    </Panel>
  );
}

function WeeklyThroughput({ weeks }: { weeks: AnalyticsOverview['weeks'] }) {
  const maxScored = Math.max(1, ...weeks.map((w) => w.resumesScored));
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollLeft = scrollRef.current.scrollWidth;
    }
  }, [weeks]);

  return (
    <Panel className="p-5 space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line pb-3">
        <h2 className="text-[15px] font-semibold text-ink">How much moved each week?</h2>
        <div className="flex items-center gap-3 text-xs text-ink-muted">
          <span className="flex items-center gap-1.5">
            <span className="size-2.5 rounded-sm bg-accent-bar" />
            Scored
          </span>
          <span className="flex items-center gap-1.5">
            <span className="size-2 rounded-full bg-info" />
            Interviews
          </span>
          <span className="flex items-center gap-1.5">
            <span className="size-2 rotate-45 bg-success" />
            Offers
          </span>
        </div>
      </div>

      <div ref={scrollRef} className="overflow-x-auto pb-2">
        <div className="flex h-44 items-end gap-2 min-w-[360px] border-b border-line-strong pt-4">
          {weeks.map((w, idx) => {
            const isLast = idx === weeks.length - 1;
            const barHeight = Math.max(4, Math.round((w.resumesScored / maxScored) * 100));

            return (
              <div
                key={w.weekStart}
                className={cn(
                  'group relative flex h-full min-w-0 flex-1 flex-col items-center justify-end',
                  isLast && 'opacity-55'
                )}
                title={`Week of ${shortDate(w.weekStart)}: ${w.resumesScored} scored, ${w.interviewsCompleted ?? 0} interviews, ${w.offersApproved ?? 0} offers`}
              >
                {/* Dots / Diamonds above bar */}
                <div className="mb-1 flex flex-col items-center gap-1">
                  {(w.offersApproved ?? 0) > 0 && (
                    <span className="size-2 rotate-45 bg-success" />
                  )}
                  {(w.interviewsCompleted ?? 0) > 0 && (
                    <span className="size-2 rounded-full bg-info" />
                  )}
                </div>

                {/* Scored Bar */}
                <div
                  className="w-full rounded-t-sm bg-accent-bar transition-opacity group-hover:opacity-80"
                  style={{ height: `${barHeight}%` }}
                />
              </div>
            );
          })}
        </div>

        <div className="flex justify-between text-[11px] font-mono text-ink-muted pt-1">
          <span>{weeks.length > 0 && shortDate(weeks[0].weekStart)}</span>
          <span>{weeks.length > 1 && shortDate(weeks[weeks.length - 1].weekStart)}</span>
        </div>
      </div>
    </Panel>
  );
}

function ScoreCalibration({ bands }: { bands: AnalyticsOverview['scoreBands'] }) {
  return (
    <Panel className="p-5 space-y-4">
      <PanelHeader title="Does the AI score agree with people?" />
      <div className="space-y-3 pt-2">
        {bands.map((b) => (
          <div key={b.label} className="space-y-1">
            <div className="flex flex-wrap items-center justify-between gap-x-2 text-xs">
              <span className="font-medium text-ink w-16">{b.label}</span>
              <span className="font-mono text-ink-muted min-w-0 break-words">
                {b.count > 0 ? (
                  `${formatRate(b.shortlistRate)} shortlist · ${formatRate(b.hireRate)} hire (${b.count})`
                ) : (
                  <span title="No data in this period">–</span>
                )}
              </span>
            </div>

            <div className="h-1.5 w-full rounded-full bg-sunken overflow-hidden">
              <div
                className="h-full rounded-full bg-accent-bar transition-all"
                style={{ width: `${Math.round((b.shortlistRate || 0) * 100)}%` }}
                title="Shortlist rate"
              />
            </div>
            <div className="h-1.5 w-full rounded-full bg-sunken overflow-hidden">
              <div
                className="h-full rounded-full bg-success transition-all"
                style={{ width: `${Math.round((b.hireRate || 0) * 100)}%` }}
                title="Hire recommendation rate"
              />
            </div>
          </div>
        ))}

        <p className="pt-2 text-[11px] text-ink-muted border-t border-line leading-relaxed">
          Higher AI scores correlate with higher candidate shortlist rates and interviewer hire recommendations.
        </p>
      </div>
    </Panel>
  );
}

function InterviewerAnalytics({ interviewers }: { interviewers: InterviewerStat[] }) {
  const [filter, setFilter] = useState<InterviewerFilter>('ALL');

  const filtered = useMemo(() => {
    return interviewers.filter((i) => {
      if (filter === 'TECHNICAL') return i.role === 'TECHNICAL_INTERVIEWER';
      if (filter === 'HR') return i.role === 'HR_INTERVIEWER';
      return true;
    });
  }, [interviewers, filter]);

  return (
    <Panel className="overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4">
        <h2 className="text-[15px] font-semibold text-ink">How are interviews going?</h2>
        <Segmented
          label="Interviewer kind filter"
          value={filter}
          onChange={(val) => setFilter(val as InterviewerFilter)}
          options={[
            { value: 'ALL', label: 'All' },
            { value: 'TECHNICAL', label: 'Technical' },
            { value: 'HR', label: 'HR' },
          ]}
        />
      </div>

      {filtered.length === 0 ? (
        <div className="p-8 text-center text-sm text-ink-muted">
          No interview data recorded for this selection.
        </div>
      ) : (
        <>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-line bg-sunken text-[12.5px] font-medium text-ink-muted">
                <tr>
                  <th scope="col" className="px-5 py-3 text-left">
                    Interviewer
                  </th>
                  <th scope="col" className="px-3 py-3 text-right">
                    Done
                  </th>
                  <th scope="col" className="px-3 py-3 text-right">
                    Time to feedback
                  </th>
                  <th scope="col" className="px-3 py-3 text-right">
                    Rating
                  </th>
                  <th scope="col" className="px-5 py-3 text-left">
                    Recommendation mix
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {filtered.map((inv) => {
                  const isSlow = (inv.medianHoursToFeedback || 0) > 48;
                  const total =
                    inv.strongHireCount + inv.hireCount + inv.noHireCount + inv.strongNoHireCount;

                  const shPct = total > 0 ? (inv.strongHireCount / total) * 100 : 0;
                  const hPct = total > 0 ? (inv.hireCount / total) * 100 : 0;
                  const nhPct = total > 0 ? (inv.noHireCount / total) * 100 : 0;
                  const snhPct = total > 0 ? (inv.strongNoHireCount / total) * 100 : 0;

                  return (
                    <tr key={inv.userId} className="hover:bg-sunken/40">
                      <td className="px-5 py-3">
                        <span className="font-semibold text-ink block">{inv.fullName}</span>
                        <span className="text-[11px] text-ink-muted">
                          {inv.role === 'TECHNICAL_INTERVIEWER' ? 'Technical' : 'HR'}
                        </span>
                      </td>
                      <td className="px-3 py-3 text-right font-mono font-medium text-ink">
                        {inv.interviewsDone}
                      </td>
                      <td className="px-3 py-3 text-right font-mono">
                        <span className={cn(isSlow ? 'text-warn font-semibold' : 'text-ink-muted')}>
                          {inv.medianHoursToFeedback != null
                            ? `${inv.medianHoursToFeedback.toFixed(1)}h`
                            : '–'}
                        </span>
                      </td>
                      <td className="px-3 py-3 text-right font-mono text-ink">
                        {inv.meanRating != null ? inv.meanRating.toFixed(1) : '–'}
                      </td>
                      <td className="px-5 py-3">
                        <div className="flex h-3 w-40 rounded-full overflow-hidden bg-sunken">
                          {shPct > 0 && (
                            <div className="h-full bg-success" style={{ width: `${shPct}%` }} title="Strong hire" />
                          )}
                          {hPct > 0 && (
                            <div className="h-full bg-success/60" style={{ width: `${hPct}%` }} title="Hire" />
                          )}
                          {nhPct > 0 && (
                            <div className="h-full bg-warn" style={{ width: `${nhPct}%` }} title="No hire" />
                          )}
                          {snhPct > 0 && (
                            <div className="h-full bg-danger" style={{ width: `${snhPct}%` }} title="Strong no hire" />
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          <div className="flex items-center gap-4 px-5 py-3 bg-sunken/30 border-t border-line text-xs text-ink-muted">
            <span className="flex items-center gap-1.5">
              <span className="size-2 rounded-full bg-success" /> Strong hire
            </span>
            <span className="flex items-center gap-1.5">
              <span className="size-2 rounded-full bg-success/60" /> Hire
            </span>
            <span className="flex items-center gap-1.5">
              <span className="size-2 rounded-full bg-warn" /> No hire
            </span>
            <span className="flex items-center gap-1.5">
              <span className="size-2 rounded-full bg-danger" /> Strong no hire
            </span>
          </div>
        </>
      )}
    </Panel>
  );
}

function RecruitersTable({ rows }: { rows: AnalyticsOverview['recruiters'] }) {
  return (
    <Panel className="overflow-hidden">
      <PanelHeader title="Recruiters" />
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="border-b border-line bg-sunken text-[12.5px] font-medium text-ink-muted">
            <tr>
              <th scope="col" className="px-5 py-3 text-left">
                Recruiter
              </th>
              <th scope="col" className="px-3 py-3 text-right">
                Sessions
              </th>
              <th scope="col" className="px-3 py-3 text-right">
                Scored
              </th>
              <th scope="col" className="px-3 py-3 text-right">
                Shortlisted
              </th>
              <th scope="col" className="px-3 py-3 text-right">
                Contacted
              </th>
              <th scope="col" className="px-3 py-3 text-right">
                Offers
              </th>
              <th scope="col" className="px-3 py-3 text-right">
                Shortlist to offer
              </th>
              <th scope="col" className="px-5 py-3 text-right">
                Median to ranked
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line">
            {rows.map((r) => (
              <tr key={r.userId} className="hover:bg-sunken/40">
                <td className="px-5 py-3 font-semibold text-ink">{r.fullName}</td>
                <td className="px-3 py-3 text-right font-mono text-ink">{r.sessions}</td>
                <td className="px-3 py-3 text-right font-mono text-ink">{r.resumesScored}</td>
                <td className="px-3 py-3 text-right font-mono font-medium text-mark-text">
                  {r.shortlisted}
                </td>
                <td className="px-3 py-3 text-right font-mono text-ink">{r.contacted ?? 0}</td>
                <td className="px-3 py-3 text-right font-mono text-ink">{r.offers ?? 0}</td>
                <td className="px-3 py-3 text-right font-mono text-ink">
                  {r.shortlistToOfferRate != null ? formatRate(r.shortlistToOfferRate) : '–'}
                </td>
                <td className="px-5 py-3 text-right font-mono text-ink-muted">
                  {formatHours(r.medianHoursToRanked)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Panel>
  );
}
