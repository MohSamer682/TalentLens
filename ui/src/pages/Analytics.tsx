import { useCallback, useEffect, useState } from 'react';
import { ArrowDown, ArrowUp } from 'lucide-react';
import { analyticsApi, apiErrorMessage } from '@/api/client';
import type { AnalyticsOverview, ScreeningSession } from '@/types';
import { Button } from '@/components/ui/Button';
import { Alert, SkeletonRows } from '@/components/ui/Feedback';
import { Panel, PanelHeader } from '@/components/ui/Card';
import { Segmented } from '@/components/ui/Segmented';
import { MainLayout, PageHeader } from '@/layouts/MainLayout';
import { formatCount, formatHours, formatRate, funnelSteps } from '@/lib/analytics';
import { cn, formatDate } from '@/lib/utils';

type Days = '30' | '90' | '365';
type Recruiter = AnalyticsOverview['recruiters'][number];
type SortKey = 'fullName' | 'sessions' | 'resumesScored' | 'shortlisted' | 'medianHoursToRanked';

const PERIODS: { value: Days; label: string }[] = [
  { value: '30', label: '30 days' },
  { value: '90', label: '90 days' },
  { value: '365', label: '12 months' },
];

const STATUSES: { status: ScreeningSession['status']; label: string; bar: string }[] = [
  { status: 'DONE', label: 'Ranked', bar: 'bg-success' },
  { status: 'RUNNING', label: 'Scoring', bar: 'bg-info' },
  { status: 'FAILED', label: 'Failed', bar: 'bg-danger' },
  { status: 'PENDING', label: 'Not started', bar: 'bg-ink-muted' },
];

const COLUMNS: { key: SortKey; label: string; numeric: boolean }[] = [
  { key: 'fullName', label: 'Recruiter', numeric: false },
  { key: 'sessions', label: 'Sessions', numeric: true },
  { key: 'resumesScored', label: 'Scored', numeric: true },
  { key: 'shortlisted', label: 'Shortlisted', numeric: true },
  { key: 'medianHoursToRanked', label: 'Median to ranked', numeric: true },
];

const pct = (part: number, whole: number) => (whole === 0 ? 0 : (part / whole) * 100);
const shortDate = (d: string) => new Date(d).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });

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

  return (
    <MainLayout>
      <PageHeader
        title="Recruiting analytics"
        meta={data && !error && <span>All recruiters · {formatDate(data.from)} to {formatDate(data.to)}</span>}
        actions={<Segmented label="Period" value={days} onChange={setDays} options={PERIODS} />}
      />

      {loading && !data ? (
        <Panel>
          <SkeletonRows rows={5} columns={[30, 12, 12, 12]} />
        </Panel>
      ) : error ? (
        <Alert
          title="Analytics could not be loaded"
          action={<Button variant="secondary" size="sm" onClick={load}>Try again</Button>}
        >
          {error}
        </Alert>
      ) : data && data.totals.sessions === 0 && data.totals.resumesUploaded === 0 ? (
        <Panel className="px-6 py-14 text-center">
          <h2 className="text-lg font-semibold">Nothing to report for this period yet</h2>
          <p className="mx-auto mt-1 max-w-md text-sm text-ink-muted">
            Analytics fill in as sessions are scored. Try a longer period, or check back once recruiters have run a session.
          </p>
        </Panel>
      ) : (
        data && <Report data={data} busy={loading} />
      )}
    </MainLayout>
  );
}

function Report({ data, busy }: { data: AnalyticsOverview; busy: boolean }) {
  const t = data.totals;
  return (
    <div className={cn('space-y-6 transition-opacity', busy && 'opacity-60')} aria-busy={busy}>
      <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-lg border border-line bg-line shadow-panel lg:grid-cols-5">
        <Kpi label="Sessions" value={formatCount(t.sessions)}>
          {formatCount(t.sessionsByStatus.DONE)} ranked · {formatCount(t.sessionsByStatus.RUNNING)} scoring ·{' '}
          {formatCount(t.sessionsByStatus.FAILED)} failed
        </Kpi>
        <Kpi label="Resumes scored" value={formatCount(t.resumesScored)}>
          of {formatCount(t.resumesUploaded)} uploaded
        </Kpi>
        <Kpi label="Shortlisted" value={formatCount(t.shortlisted)} valueClass="text-mark-text">
          candidates marked for interview
        </Kpi>
        <Kpi label="Shortlist rate" value={formatRate(t.shortlistRate)}>
          of scored resumes
        </Kpi>
        <Kpi label="Median time to ranked" value={formatHours(t.medianHoursToRanked)} className="col-span-2 lg:col-span-1">
          from session created to ranked
        </Kpi>
      </dl>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <Throughput weeks={data.weeks} />
        <Funnel totals={t} />
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        <ScoreBands bands={data.scoreBands} />
        <SessionStatuses byStatus={t.sessionsByStatus} total={t.sessions} />
      </div>

      <Recruiters rows={data.recruiters} />
    </div>
  );
}

function Kpi({
  label,
  value,
  valueClass,
  className,
  children,
}: {
  label: string;
  value: string;
  valueClass?: string;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <div className={cn('bg-surface px-4 py-4 sm:px-5', className)}>
      <dt className="text-[13px] font-medium text-ink-muted">{label}</dt>
      <dd className={cn('num mt-1 text-[28px] font-bold leading-none', valueClass)}>{value}</dd>
      <dd className="num mt-2 text-[13px] text-ink-muted">{children}</dd>
    </div>
  );
}

function Throughput({ weeks }: { weeks: AnalyticsOverview['weeks'] }) {
  const max = Math.max(1, ...weeks.map((w) => w.resumesScored));
  const scored = weeks.reduce((s, w) => s + w.resumesScored, 0);
  const shortlisted = weeks.reduce((s, w) => s + w.shortlisted, 0);
  return (
    <Panel>
      <PanelHeader title="Weekly throughput">
        <div className="flex gap-4 text-[13px] text-ink-muted" aria-hidden="true">
          <span className="flex items-center gap-1.5"><span className="size-2.5 rounded-sm bg-accent-bar" />Scored</span>
          <span className="flex items-center gap-1.5"><span className="size-2.5 rounded-sm bg-mark" />Shortlisted</span>
        </div>
      </PanelHeader>
      <div className="px-4 pb-4 pt-5 sm:px-5">
        <div
          role="img"
          aria-label={`${formatCount(scored)} resumes scored and ${formatCount(shortlisted)} shortlisted over ${weeks.length} weeks. The table below lists each week.`}
          className="flex h-44 items-end gap-[3px] border-b border-line-strong"
        >
          {weeks.map((w) => (
            <div key={w.weekStart} className="group relative flex h-full min-w-0 flex-1 items-end" title={`Week of ${shortDate(w.weekStart)}: ${w.resumesScored} scored, ${w.shortlisted} shortlisted`}>
              <div className="relative w-full rounded-t-sm bg-accent-bar transition-opacity group-hover:opacity-80" style={{ height: `${pct(w.resumesScored, max)}%` }}>
                <div className="absolute inset-x-0 bottom-0 rounded-t-sm bg-mark" style={{ height: `${pct(w.shortlisted, w.resumesScored)}%` }} />
              </div>
            </div>
          ))}
        </div>
        <div className="num mt-2 flex justify-between text-[12px] text-ink-muted" aria-hidden="true">
          <span>{weeks.length > 0 && shortDate(weeks[0].weekStart)}</span>
          <span>{weeks.length > 1 && shortDate(weeks[weeks.length - 1].weekStart)}</span>
        </div>
        <div className="sr-only">
        <table>
          <caption>Resumes scored and shortlisted per week</caption>
          <thead>
            <tr><th scope="col">Week of</th><th scope="col">Sessions created</th><th scope="col">Scored</th><th scope="col">Shortlisted</th></tr>
          </thead>
          <tbody>
            {weeks.map((w) => (
              <tr key={w.weekStart}>
                <th scope="row">{formatDate(w.weekStart)}</th>
                <td>{w.sessionsCreated}</td>
                <td>{w.resumesScored}</td>
                <td>{w.shortlisted}</td>
              </tr>
            ))}
          </tbody>
        </table>
        </div>
      </div>
    </Panel>
  );
}

function Funnel({ totals }: { totals: AnalyticsOverview['totals'] }) {
  const steps = funnelSteps(totals);
  const top = Math.max(1, steps[0].value);
  return (
    <Panel>
      <PanelHeader title="Resume funnel" />
      <ol className="space-y-4 px-4 py-5 sm:px-5">
        {steps.map((s, i) => (
          <li key={s.label}>
            <div className="flex items-baseline justify-between gap-3 text-sm">
              <span className="font-semibold">{s.label}</span>
              <span className="num">
                {formatCount(s.value)}
                {s.ofPrevious !== null && <span className="ml-2 text-ink-muted">{formatRate(s.ofPrevious)}</span>}
              </span>
            </div>
            <div className="mt-1.5 h-2.5 rounded-full bg-sunken">
              <div
                className={cn('h-full rounded-full', i === steps.length - 1 ? 'bg-mark' : 'bg-accent-bar')}
                style={{ width: `${pct(s.value, top)}%` }}
              />
            </div>
          </li>
        ))}
      </ol>
      {totals.resumesUnreadable > 0 && (
        <p className="num border-t border-line px-4 py-3 text-[13px] text-ink-muted sm:px-5">
          {formatCount(totals.resumesUnreadable)} could not be read
        </p>
      )}
    </Panel>
  );
}

function HBar({ label, value, total, bar }: { label: string; value: number; total: number; bar: string }) {
  return (
    <li className="grid grid-cols-[88px_minmax(0,1fr)_72px] items-center gap-3 text-sm">
      <span className="num text-ink-muted">{label}</span>
      <span className="h-2.5 rounded-full bg-sunken">
        <span className={cn('block h-full rounded-full', bar)} style={{ width: `${pct(value, total)}%` }} />
      </span>
      <span className="num text-right">{formatCount(value)}</span>
    </li>
  );
}

function ScoreBands({ bands }: { bands: AnalyticsOverview['scoreBands'] }) {
  const max = Math.max(1, ...bands.map((b) => b.count));
  return (
    <Panel>
      <PanelHeader title="Overall score bands" />
      <ul className="space-y-3 px-4 py-5 sm:px-5">
        {[...bands].reverse().map((b) => (
          <HBar key={b.label} label={b.label} value={b.count} total={max} bar="bg-accent-bar" />
        ))}
      </ul>
    </Panel>
  );
}

function SessionStatuses({ byStatus, total }: { byStatus: AnalyticsOverview['totals']['sessionsByStatus']; total: number }) {
  return (
    <Panel>
      <PanelHeader title="Sessions by status" />
      <ul className="space-y-3 px-4 py-5 sm:px-5">
        {STATUSES.map((s) => (
          <HBar key={s.status} label={s.label} value={byStatus[s.status] ?? 0} total={Math.max(1, total)} bar={s.bar} />
        ))}
      </ul>
    </Panel>
  );
}

function Recruiters({ rows }: { rows: Recruiter[] }) {
  const [sort, setSort] = useState<{ key: SortKey; desc: boolean }>({ key: 'resumesScored', desc: true });
  const sorted = [...rows].sort((a, b) => {
    const x = a[sort.key];
    const y = b[sort.key];
    // Recruiters with no ranked session sort last either way.
    if (x === null) return 1;
    if (y === null) return -1;
    const c = typeof x === 'string' ? x.localeCompare(y as string) : x - (y as number);
    return sort.desc ? -c : c;
  });
  const choose = (key: SortKey) =>
    setSort((s) => (s.key === key ? { key, desc: !s.desc } : { key, desc: key !== 'fullName' }));

  return (
    <Panel>
      <PanelHeader title="By recruiter" />
      {rows.length === 0 ? (
        <p className="px-5 py-8 text-center text-sm text-ink-muted">No recruiter created a session in this period.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[560px] text-sm">
            <thead className="bg-sunken text-[13px] text-ink-muted">
              <tr>
                {COLUMNS.map((c) => (
                  <th
                    key={c.key}
                    scope="col"
                    aria-sort={sort.key === c.key ? (sort.desc ? 'descending' : 'ascending') : undefined}
                    className={cn('px-4 py-1 font-medium sm:px-5', c.numeric ? 'text-right' : 'text-left')}
                  >
                    <button
                      type="button"
                      onClick={() => choose(c.key)}
                      className={cn('inline-flex h-8 items-center gap-1 rounded hover:text-ink', sort.key === c.key && 'text-ink')}
                    >
                      {c.label}
                      {sort.key === c.key &&
                        (sort.desc ? <ArrowDown className="size-3.5" aria-hidden="true" /> : <ArrowUp className="size-3.5" aria-hidden="true" />)}
                    </button>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {sorted.map((r) => (
                <tr key={r.userId}>
                  <th scope="row" className="px-4 py-3 text-left font-semibold sm:px-5">{r.fullName}</th>
                  <td className="num px-4 py-3 text-right sm:px-5">{formatCount(r.sessions)}</td>
                  <td className="num px-4 py-3 text-right sm:px-5">{formatCount(r.resumesScored)}</td>
                  <td className="num px-4 py-3 text-right sm:px-5">{formatCount(r.shortlisted)}</td>
                  <td className="num px-4 py-3 text-right sm:px-5">{formatHours(r.medianHoursToRanked)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Panel>
  );
}
