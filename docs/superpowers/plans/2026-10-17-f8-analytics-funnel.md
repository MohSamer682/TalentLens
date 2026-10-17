# F8 Analytics for the Whole Funnel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the analytics page from a screening report into a hiring report: where candidates drop out, where they wait, whether the AI score agrees with interviewers, and how this period compares with the last.

**Architecture:** Same endpoint and same cohort rule as F3 (candidates in sessions created in the period). `AnalyticsRepository` gains four queries over F7's `candidate_stage_events` and `interviews`; `AnalyticsService` runs the KPI query twice (this period and the one before) and returns both. The page is reorganised around seven blocks, each answering one question; charts stay hand-built bars like today, no chart library.

**Tech Stack:** Java 21, Spring Boot 3.3, `NamedParameterJdbcTemplate`, PostgreSQL 16 (`percentile_cont`, `FILTER`), Testcontainers; React 18, TypeScript, Tailwind 3, Vitest.

**Spec:** [2026-10-17-talentlens-brief.md](2026-10-17-talentlens-brief.md), section F8. Mockup: TalentLens designs artifact, "Analytics".

## Global Constraints

- **Design spec is binding:** before coding any screen, analyse its artboard in the TalentLens designs (canvas or PDF) against [2026-10-17-talentlens-design-spec.md](2026-10-17-talentlens-design-spec.md), and finish with that spec's section 5 acceptance checklist in the PR. A screen that does not match its design is not done.
- Branch `feat/analytics-funnel` from `feat/hiring-pipeline`. One PR. `V11__analytics_pipeline_indexes.sql` only if `EXPLAIN` on a 50k-candidate seed shows a sequential scan on `interviews` or `candidate_stage_events` (indexes from V10 may be enough).
- Cohort rule everywhere: candidates whose session was created in `[from, to)`. The page says so under the title: "Candidates from sessions created in this period".
- Previous period: same length, ending where this one starts.
- Funnel steps and their rule (stage counted as "reached" by `review_status` ordinal, interviews by feedback):
  Uploaded (all candidates), Scored (`status = 'SCORED'`), Shortlisted (`review_status <> 'NONE'`), Contacted (≥ `CONTACTED`), Interviewed (≥ `INTERVIEWED`), Offer (≥ `OFFER`), Approved (`OFFER_APPROVED`).
- Stage durations from the first event of each stage per candidate: Shortlisted → Contacted, Contacted → Interview scheduled, Interview end (`scheduled_at + duration`) → Feedback submitted, Interviewed → Offer, Offer → Approved. Median and 90th percentile, in hours; only pairs where both ends exist.
- Hire recommendation = `HIRE` or `STRONG_HIRE`.
- Any ratio with a zero denominator is `null` in JSON and "–" in the UI with the tooltip "No data in this period".
- Delta colour: `success` when the change is good for that metric, `danger` when bad, `ink-muted` when under 1% or no prior data; always with an arrow and a sign, never colour alone. Lower is better for durations; shortlist rate is always neutral (a lower rate is not worse).
- Blue (`accent-bar`) for every bar; saffron only on the Shortlisted count, as today.

## Review Focus

1. **A period with candidates but no interviews or offers yet**: later funnel steps show 0 with "–" conversion, time-in-stage rows show "Not enough data", nothing reads 0%. Test: Task 1 `emptyLaterStagesAreNullNotZero`, Task 2 `rendersDashesForNullRates`.
2. **A candidate whose interview was cancelled and rescheduled** (two `INTERVIEW_SCHEDULED` events): durations use the first event, the candidate counts once. Test: Task 1 `repeatedStageEventsCountOnce`.
3. **Prior period with zero activity**: deltas show "New" instead of "+∞%". Test: Task 2 `deltaWithoutPriorShowsNew`.
4. **An interviewer later deleted or demoted**: their past interviews still count, listed under their name at the time (inner join on users still holds because users are not deleted; a demoted interviewer still appears). Test: Task 1 `demotedInterviewerStillListed`.
5. **Year view at 360 px**: 53 weekly columns must not overflow; the chart scrolls inside its panel with the latest week visible. Test: Task 2 screenshot assertion `scrollWidth <= innerWidth` on the page.

---

### Task 1: Funnel, timing, calibration and interviewer queries

**Files:**
- Modify: `api-service/src/main/java/com/resumescreener/api/analytics/dto/AnalyticsOverview.java`, `.../analytics/AnalyticsRepository.java`, `.../analytics/AnalyticsService.java`
- Test: `api-service/src/test/java/com/resumescreener/api/analytics/AnalyticsRepositoryTest.java` (extend), `.../analytics/AnalyticsServiceTest.java` (create if absent)

**Interfaces:**
- Produces (added to `AnalyticsOverview`, existing fields kept so F3's UI keeps compiling until Task 2):
  - `record Kpis(long resumesScored, Double shortlistRate, long interviewsCompleted, long offersApproved, Double medianHoursShortlistToOffer)`; overview fields `Kpis kpis, Kpis previousKpis`.
  - `record FunnelStep(String key, String label, long count, Double conversionFromPrevious)`; field `List<FunnelStep> funnel` with keys `uploaded, scored, shortlisted, contacted, interviewed, offer, approved`.
  - `record StageTime(String key, String label, Double medianHours, Double p90Hours, long samples)`; field `List<StageTime> stageTimes` with keys `shortlisted_contacted, contacted_scheduled, interview_feedback, interviewed_offer, offer_approved`.
  - `record Calibration(String band, long scored, long shortlisted, long interviewed, long hireRecommended)`; field `List<Calibration> calibration` (same five bands as `scoreBands`).
  - `record InterviewerRow(String userId, String fullName, InterviewKind kind, long completed, Double medianHoursToFeedback, Double meanRating, Map<Recommendation, Long> recommendations)`; field `List<InterviewerRow> interviewers`, sorted by completed descending.
  - `Week` adds `long interviewsCompleted, long offersApproved`; `RecruiterRow` adds `long contacted, long offers`.
  - `AnalyticsRepository.kpis(Instant from, Instant to): Kpis` (public, called twice by the service).

- [ ] **Step 1: Write the failing tests** (Testcontainers, seeding through plain SQL like the existing test):

```java
@Test void funnelCountsFurthestStageReached()        // 10 uploaded, 8 scored, 4 shortlisted, 3 contacted, 2 interviewed, 1 offer, 1 approved
                                                     // -> counts as listed; conversion contacted = 0.75, approved = 1.0
@Test void emptyLaterStagesAreNullNotZero()          // shortlisted 2, nobody contacted -> contacted.count 0, interviewed.conversion null; stageTimes samples 0 and median null
@Test void repeatedStageEventsCountOnce()            // two INTERVIEW_SCHEDULED events 2 days apart -> contacted_scheduled uses the first
@Test void stageTimesMedianAndP90()                  // shortlisted->contacted gaps 1,2,3,4,10 h -> median 3.0, p90 7.6 (percentile_cont)
@Test void calibrationByBand()                       // 92-score candidate shortlisted + HIRE -> band 90–100 has 1/1/1/1
@Test void demotedInterviewerStillListed()           // interviewer role changed to RECRUITER after feedback -> row present, completed 1
@Test void previousPeriodKpisUseTheSameLength()      // AnalyticsServiceTest with fixed Clock: kpis(from-90d, from) called for previousKpis
```

- [ ] **Step 2: Run** `mvn -pl api-service -am test -Dtest='Analytics*Test' -Dsurefire.failIfNoSpecifiedTests=false` — expect FAIL.
- [ ] **Step 3: Implement.** Each block is one SQL statement under the existing `IN_PERIOD` filter. Stage times: a CTE `firsts AS (SELECT candidate_id, stage, min(changed_at) AS at FROM candidate_stage_events GROUP BY 1, 2)` joined to itself per pair; interview to feedback uses `interviews` directly. `percentile_cont(0.5)` and `percentile_cont(0.9)` over hours.
- [ ] **Step 4: Run** the tests, then `mvn -q verify` — expect PASS.
- [ ] **Step 5: Commit** `feat(analytics): hiring funnel, stage timing, calibration and interviewers`

---

### Task 2: Analytics page redesign

**Files:**
- Modify: `ui/src/pages/Analytics.tsx` (split blocks into `ui/src/components/analytics/{KpiStrip,FunnelChart,StageTimes,Throughput,Calibration,InterviewerTable,RecruiterTable}.tsx`; the page keeps loading, error and empty states as today), `ui/src/lib/analytics.ts`, `ui/src/lib/analytics.test.ts`, `ui/src/types/index.ts`

**Interfaces:**
- Consumes: Task 1 DTO.
- Produces: `delta(current: number | null, previous: number | null, lowerIsBetter = false): { text: string; tone: 'good' | 'bad' | 'flat'; direction: 'up' | 'down' | 'none' }` — `text` is `"+12%"`, `"−4%"`, `"New"` or `"–"`; `slowestStep(times: StageTime[]): string | null` (largest median with at least 3 samples).

- [ ] **Step 1: Write the failing tests** (`analytics.test.ts`):

```ts
expect(delta(112, 100)).toEqual({ text: '+12%', tone: 'good', direction: 'up' });
expect(delta(30, 40, true)).toEqual({ text: '−25%', tone: 'good', direction: 'down' });
expect(delta(5, 0)).toEqual({ text: 'New', tone: 'flat', direction: 'none' });      // deltaWithoutPriorShowsNew
expect(delta(null, 3)).toEqual({ text: '–', tone: 'flat', direction: 'none' });
expect(delta(100.4, 100)).toMatchObject({ tone: 'flat' });
expect(slowestStep([t('a', 10, 2), t('b', 50, 5), t('c', 90, 1)])).toBe('b');     // c has too few samples
```

And a render test `rendersDashesForNullRates`: a funnel step with `conversionFromPrevious: null` renders "–" with `title="No data in this period"`.

- [ ] **Step 2: Run** `cd ui && npx vitest run src/lib/analytics.test.ts` — expect FAIL.
- [ ] **Step 3: Implement the layout** (top to bottom; one question per block, the question is the panel title):
  1. `KpiStrip`: five tiles, value in `.display`, delta chip with arrow, "vs prior N days" caption, prior value in `title`.
  2. Row: `FunnelChart` "Where do candidates drop out?" (seven horizontal bars, width by count, conversion % and drop-off count on each, the step after the shortlist with the lowest conversion labelled "Biggest drop" with its lost count) beside `StageTimes` "Where do they wait?" (one row per step: median as a bar, p90 as a thin tick, sample count, slowest step labelled).
  3. Row: `Throughput` "How much moved each week?" (existing bars plus two series as small dots above each week, legend) beside `Calibration` "Does the AI score agree with people?" (per band: shortlist rate bar and hire-recommendation rate bar, with counts).
  4. `InterviewerTable` "How are interviews going?" with a Technical / HR segmented filter: interviews, median hours to feedback, mean rating, recommendation mix as a stacked 4-part bar with a text legend.
  5. `RecruiterTable`: existing sortable table plus Contacted and Offers columns.
  - Remove the separate session-status panel; its counts move to the header meta line ("42 sessions · 38 ranked · 3 scoring · 1 failed").
  - Tables become cards under `sm`; the weekly chart scrolls inside its panel and starts scrolled to the right.
- [ ] **Step 4: Run** `cd ui && npx vitest run && npm run build` — expect PASS.
- [ ] **Step 5: Smoke and screenshots.** Seed with `maintenance/` data for 90 days (sessions, shortlists, interviews, offers); screenshots `docs/screenshots/f8/analytics-{dark,light}-{360,768,1280}.png` and the empty state; the script asserts no horizontal page scroll.
- [ ] **Step 6: Commit** `feat(ui): analytics for the whole hiring funnel`
