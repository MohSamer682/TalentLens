# Interview order, closing the hire, and Roles and permissions management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Three features, one PR each: (F9) the candidate page lists interviews with the next action on top and finished interviews below in date order; (F10) after the joining date is set, joining details stay editable and the recruiter closes the hire as Joined, Not joining or Blocked; (F11) the admin permissions screen is redesigned and renamed "Roles and permissions management".

**Architecture:** F9 is UI only: a pure ordering module (`lib/interviewAgenda.ts`) decides order and the next action, and the 1,100-line `HiringPipeline.tsx` loses its interview card and joining block to focused components. F10 adds three terminal pipeline stages and a `JoiningOutcome` value object that owns the transition rules; the service locks the candidate row while closing, and analytics stop hard-coding stage lists. F11 keeps the permission model (role_permissions, admin always all) but adds permission groups to the catalog, a single atomic save with a stale-write check, and a master-detail UI driven by a pure draft reducer.

**Tech Stack:** Java 21, Spring Boot 3, Spring Data JPA, Flyway, PostgreSQL 16, Testcontainers, JUnit 5 + Mockito; React 18, TypeScript 5.6, Vite 6, Tailwind 3, Vitest 2, lucide-react.

**Spec:** Muhammed's request in the project thread (2026-10-18) and the approved design canvas https://claude.ai/artifact/5nxSdhpKYpnRBHFjELf8rp (artboards: Main, Compare, Phone, Pipeline). Design language: `ui/DESIGN.md`.

## Global Constraints

- Base branch: current `main` (7838867, F5 to F8 and interview places/offers merged, Flyway V11). Stack: `feat/interview-agenda` (F9, PR to main) → `feat/joining-outcome` (F10) → `feat/roles-permissions-page` (F11). Each later PR targets the previous branch.
- Keep main's review checks: pipeline edits only by the session owner, candidate must belong to the session, the candidate row lock (`CandidateRepository.findForUpdateById`), feedback only by the assigned interviewer.
- Flyway is at V11; F10 adds `V12__joining_outcome.sql`, additive only (no DROP, no row deletes).
- Feature cycle: plan → plan approval → implementation → testing → UAT review → final smoke test → PR. Do not open a PR before Muhammed's UAT sign-off.
- Validation before every PR: `mvn clean verify`, `npx vitest run`, `npm run build` all pass, plus a real-stack smoke (Postgres + MinIO in Docker) that reads the changed rows back from the database.
- UI: tokens from `ui/src/index.css` only, never raw hex; every screen works in dark and light; no horizontal page scroll at 360 px; 44 px touch targets; motion 160/240 ms with `cubic-bezier(0.22, 1, 0.36, 1)` and none under `prefers-reduced-motion`.
- Saffron (`mark`) means shortlisted and nothing else. The redesigned roles page stops using it for "Unsaved" and the Save button: changed rows use `warn-soft`, the unsaved bar is inverted ink.
- Sentence case copy, no all-caps eyebrows. Exact page title: "Roles and permissions management". Nav label: "Permissions". Admin tabs: "Roles and permissions", "Users".
- Commits: Conventional Commits, authored as Mohamed Samer <mohamedsamer682@gmail.com>. No Claude credit or session link in code, commits or PR bodies. PR rules: `talentlens-pr-rules` skill.
- The server enforces every rule; the UI mirrors rules only to decide what to show.

## Review Focus

1. Two recruiters close the same hire at once (or one closes while another edits joining details): exactly one outcome wins, the other gets "The hire is already closed", never two stage events. Pinned in Task 4 (`closeHire_locksTheCandidateRow`, `saveJoining_refusesAClosedHire`).
2. Two admins edit roles at once: the second save must not silently overwrite the first; it gets a conflict and the page reloads the current grants. Pinned in Task 6 (`updateAll_rejectsAStaleBase`) and Task 7 (`save conflict keeps the user informed`).
3. A save that changes three roles fails on the second: nothing is saved (today each role is its own request, so a failure leaves a half-applied change). Pinned in Task 6 (`updateAll_isAllOrNothing`).
4. Interviews rescheduled or cancelled on the page must re-order immediately without a reload, and two interviews at the same minute must keep a stable order. Pinned in Task 1 (`ties keep a stable order`, `rescheduling moves an interview`).
5. Analytics after F10: a Joined, Not joining or Blocked candidate still counts as contacted, interviewed, offered and approved in the funnel. Pinned in Task 3 (`funnelCountsClosedHiresAsApproved`).

---

## Part F9: Interviews, next action first

Order rule (from the request and the Pipeline artboard): every interview still to happen comes first, earliest first, and the earliest one is the **Next up** card. Held interviews follow in the order they happened. Cancelled ones go last. When nothing is scheduled, the top card is the next step instead: schedule the first planned kind not yet booked, or "Move to offer" once every planned kind is held.

### Task 1: Ordering and next action, as pure functions

**Files:**
- Create: `ui/src/lib/interviewAgenda.ts`
- Test: `ui/src/lib/interviewAgenda.test.ts`

**Interfaces:**
- Consumes: `CandidateInterview`, `InterviewKind`, `ReviewStatus` from `@/types`; `KIND_ORDER`, `settledStage` from `@/lib/pipeline`.
- Produces:
  - `orderInterviews(interviews: CandidateInterview[]): CandidateInterview[]` (new array, input untouched)
  - `type NextAction = { type: 'INTERVIEW'; interview: CandidateInterview; overdue: boolean } | { type: 'SCHEDULE'; kind: InterviewKind } | { type: 'MOVE_TO_OFFER' } | { type: 'NONE' }`
  - `nextAction(stage: ReviewStatus, plan: InterviewKind[], interviews: CandidateInterview[], now: Date): NextAction`

- [ ] **Step 1: Write the failing tests**

```ts
const iv = (id: string, status: InterviewStatus, start: string, kind: InterviewKind = 'HR') =>
  ({ id, status, scheduledStart: start, kind } as CandidateInterview);

describe('orderInterviews', () => {
  it('puts scheduled first, earliest first, then held by date, then cancelled', () => {
    const list = [iv('held1', 'COMPLETED', '2026-10-12T10:00:00Z'), iv('cx', 'CANCELLED', '2026-10-01T10:00:00Z'),
      iv('later', 'SCHEDULED', '2026-10-30T10:00:00Z'), iv('held2', 'COMPLETED', '2026-10-14T10:00:00Z'),
      iv('next', 'SCHEDULED', '2026-10-22T11:00:00Z')];
    expect(orderInterviews(list).map((i) => i.id)).toEqual(['next', 'later', 'held1', 'held2', 'cx']);
  });
  it('ties keep a stable order', () => {
    const a = iv('a', 'SCHEDULED', '2026-10-22T11:00:00Z'), b = iv('b', 'SCHEDULED', '2026-10-22T11:00:00Z');
    expect(orderInterviews([b, a]).map((i) => i.id)).toEqual(['a', 'b']);
  });
  it('rescheduling moves an interview', () => {
    const list = [iv('x', 'SCHEDULED', '2026-10-22T11:00:00Z'), iv('y', 'SCHEDULED', '2026-10-25T11:00:00Z')];
    const moved = list.map((i) => (i.id === 'x' ? { ...i, scheduledStart: '2026-10-28T09:00:00Z' } : i));
    expect(orderInterviews(moved).map((i) => i.id)).toEqual(['y', 'x']);
  });
  it('does not mutate its input', () => { const l = [iv('h', 'COMPLETED', '2026-10-01T00:00:00Z'), iv('s', 'SCHEDULED', '2026-10-02T00:00:00Z')]; orderInterviews(l); expect(l[0].id).toBe('h'); });
});

describe('nextAction', () => {
  const now = new Date('2026-10-18T09:00:00Z');
  it('is the earliest scheduled interview, overdue when its start has passed', () => {
    expect(nextAction('INTERVIEW_SCHEDULED', ['HR'], [iv('s', 'SCHEDULED', '2026-10-17T10:00:00Z')], now))
      .toMatchObject({ type: 'INTERVIEW', overdue: true });
  });
  it('asks to schedule the first planned kind not booked yet, in KIND_ORDER', () => {
    expect(nextAction('INTERVIEW_SCHEDULED', ['MANAGEMENT', 'HR', 'TECHNICAL'], [iv('h', 'COMPLETED', '2026-10-12T10:00:00Z', 'HR')], now))
      .toEqual({ type: 'SCHEDULE', kind: 'TECHNICAL' });
  });
  it('a cancelled interview does not count as booked', () => {
    expect(nextAction('CONTACTED', ['HR'], [iv('c', 'CANCELLED', '2026-10-12T10:00:00Z', 'HR')], now)).toEqual({ type: 'SCHEDULE', kind: 'HR' });
  });
  it('is Move to offer once every planned kind is held', () => {
    expect(nextAction('INTERVIEWED', ['HR'], [iv('h', 'COMPLETED', '2026-10-12T10:00:00Z', 'HR')], now)).toEqual({ type: 'MOVE_TO_OFFER' });
  });
  it('is NONE before Contacted and from Offer on', () => {
    expect(nextAction('SHORTLISTED', [], [], now)).toEqual({ type: 'NONE' });
    expect(nextAction('OFFER', ['HR'], [iv('h', 'COMPLETED', '2026-10-12T10:00:00Z', 'HR')], now)).toEqual({ type: 'NONE' });
  });
});
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd ui && npx vitest run src/lib/interviewAgenda.test.ts`
Expected: FAIL, "Failed to resolve import ./interviewAgenda".

- [ ] **Step 3: Implement `orderInterviews` and `nextAction` in `ui/src/lib/interviewAgenda.ts`**

`orderInterviews`: copy the array, sort with a comparator over the tuple `(STATUS_RANK[status], Date.parse(scheduledStart), id)` where `const STATUS_RANK: Record<InterviewStatus, number> = { SCHEDULED: 0, COMPLETED: 1, CANCELLED: 2 }`. The `Record` makes a future status a compile error instead of a silent misorder. O(n log n), n is a handful.
`nextAction`: only for stages `CONTACTED`, `INTERVIEW_SCHEDULED`, `INTERVIEWED`; first SCHEDULED from `orderInterviews` wins; else the first `KIND_ORDER` kind in `plan` with no non-cancelled interview; else `MOVE_TO_OFFER` when `settledStage(stage, plan, interviews) === 'INTERVIEWED'` or stage is already `INTERVIEWED`; else `NONE`. A plan of `[]` with nothing booked returns `NONE` (the existing plan picker is the prompt).

- [ ] **Step 4: Run to verify they pass**

Run: `cd ui && npx vitest run src/lib/interviewAgenda.test.ts`
Expected: PASS, 9 tests.

- [ ] **Step 5: Commit**

```bash
git add ui/src/lib/interviewAgenda.ts ui/src/lib/interviewAgenda.test.ts
git commit -m "feat(ui): order interviews with the next action first"
```

### Task 2: Interview cards on the candidate page

**Files:**
- Create: `ui/src/components/InterviewCard.tsx` (one interview, moved out of `HiringPipeline.tsx:725-848`)
- Create: `ui/src/components/NextActionCard.tsx` (the SCHEDULE and MOVE_TO_OFFER cards)
- Create: `ui/src/lib/useFlipList.ts`
- Modify: `ui/src/components/HiringPipeline.tsx` (Interviews list section, `openScheduleForm`, `handleMoveToOffer` wiring)
- Modify: `ui/DESIGN.md` (one line under "Stages, places and approvals")

**Interfaces:**
- Consumes: `orderInterviews`, `nextAction`, `NextAction` (Task 1).
- Produces:
  - `InterviewCard({ interview, emphasis, canManage, onReschedule, onCancel }: { interview: CandidateInterview; emphasis: 'next' | 'default'; canManage: boolean; onReschedule(i: CandidateInterview): void; onCancel(id: string): void })`
  - `NextActionCard({ action, canManage, busy, onSchedule, onMoveToOffer }: { action: Extract<NextAction, { type: 'SCHEDULE' | 'MOVE_TO_OFFER' }>; canManage: boolean; busy: boolean; onSchedule(kind: InterviewKind): void; onMoveToOffer(): void })`
  - `useFlipList<T extends HTMLElement>(order: string[]): (id: string) => (el: T | null) => void`

- [ ] **Step 1: Move the card markup into `InterviewCard` unchanged, then add emphasis**

`emphasis='next'` adds a "Next up" pill (ink fill, `accent-ink` text) before the kind pill, a `border-ink` 1.5px border and the `.lift` shadow; overdue SCHEDULED shows "Waiting for feedback from {interviewerName}" in place of "Waiting for the interview". Copy is fixed by the Pipeline artboard.

- [ ] **Step 2: Render the list from `orderInterviews(interviews)`; the first SCHEDULED gets `emphasis='next'`; when `nextAction` is SCHEDULE or MOVE_TO_OFFER render `NextActionCard` above the list**

SCHEDULE copy: "Schedule the {KIND_LABEL lower-cased} interview" with button "Schedule" that calls the existing `openScheduleForm` preselecting that kind (extend `openScheduleForm(kind?: InterviewKind)`). MOVE_TO_OFFER copy: "All {n} planned interviews are held" / button "Move to offer" calling `handleMoveToOffer`. Remove the old standalone Move to offer button so there is one primary action.

- [ ] **Step 3: Implement `useFlipList` and attach it to each card's root**

Record each element's `getBoundingClientRect().top` before commit (in a `useLayoutEffect` keyed on `order.join()`), then `el.animate([{ transform: 'translateY(Δpx)' }, { transform: 'none' }], { duration: 240, easing: 'cubic-bezier(0.22, 1, 0.36, 1)' })` for each moved element. Return early when `window.matchMedia('(prefers-reduced-motion: reduce)').matches` or `Element.prototype.animate` is missing (jsdom).

- [ ] **Step 4: Verify**

Run: `cd ui && npx vitest run && npm run build`
Expected: all tests pass, build succeeds with no type errors.
Then in the running app (dark and light, 360 px and 1280 px): schedule HR and technical, submit HR feedback as the HR interviewer, cancel and reschedule the technical one. Expected: the earliest scheduled card is on top with "Next up"; the held HR card slides below; cancelled sits last; after the last planned interview is held the top card reads "All 2 planned interviews are held" with "Move to offer".

- [ ] **Step 5: Commit**

```bash
git add ui/src/components/InterviewCard.tsx ui/src/components/NextActionCard.tsx ui/src/lib/useFlipList.ts ui/src/components/HiringPipeline.tsx ui/DESIGN.md
git commit -m "feat(ui): next interview on top, held interviews below in date order"
```

Then F9's cycle: UAT with Muhammed, smoke test, PR `feat/interview-agenda` → `main`.

---

## Part F10: Joining details, then close the hire

Outcomes (Pipeline artboard): **Joined** (they started; only from Joining set, on or after the joining date), **Not joining** (accepted, then withdrew; from Accepted or Joining set; note required), **Blocked** (the company stopped the hire, e.g. a failed check, visa or budget; from Accepted or Joining set; note required). Closed hires are terminal: joining details lock and no pipeline action applies. Reopening a closed hire is out of scope.

### Task 3: Domain, migration and analytics

**Files:**
- Modify: `common/src/main/java/com/resumescreener/common/session/ReviewStatus.java`
- Create: `common/src/main/java/com/resumescreener/common/session/JoiningOutcome.java`
- Modify: `common/src/main/java/com/resumescreener/common/session/CandidateOffer.java`
- Create: `common/src/main/resources/db/migration/V12__joining_outcome.sql`
- Modify: `api-service/src/main/java/com/resumescreener/api/analytics/AnalyticsRepository.java:161-164, 449-450`
- Test: `common/src/test/java/com/resumescreener/common/session/JoiningOutcomeTest.java`, `api-service/src/test/java/com/resumescreener/api/analytics/AnalyticsRepositoryTest.java`

**Interfaces:**
- Produces:
  - `ReviewStatus` gains `JOINED, NOT_JOINING, JOINING_BLOCKED` (≤ 20 chars, the column is `VARCHAR(20)`), a constructor rank (NONE 0 … ACCEPTED 8, DECLINED 8, JOINING_SET 9, the three new ones 10), `boolean isClosed()` (DECLINED and the three new ones), `static Set<ReviewStatus> atLeast(ReviewStatus s)` (every status with rank ≥ s's rank, excluding DECLINED when s's rank > 8).
  - `enum JoiningOutcome { JOINED, NOT_JOINING, BLOCKED; ReviewStatus stage(); boolean requiresNote(); boolean allowedFrom(ReviewStatus current); }`
  - `CandidateOffer`: `JoiningOutcome joiningOutcome`, `Instant joiningClosedAt`, `User joiningClosedBy` (lazy, `joining_closed_by`), `String joiningOutcomeNote`.

- [ ] **Step 1: Write the failing tests**

```java
@Test void joinedOnlyFromJoiningSet() {
    assertTrue(JoiningOutcome.JOINED.allowedFrom(ReviewStatus.JOINING_SET));
    assertFalse(JoiningOutcome.JOINED.allowedFrom(ReviewStatus.ACCEPTED));
}
@Test void withdrawingOrBlockingWorksFromAcceptedOrJoiningSet() {
    for (JoiningOutcome o : List.of(JoiningOutcome.NOT_JOINING, JoiningOutcome.BLOCKED)) {
        assertTrue(o.allowedFrom(ReviewStatus.ACCEPTED));
        assertTrue(o.allowedFrom(ReviewStatus.JOINING_SET));
        assertFalse(o.allowedFrom(ReviewStatus.OFFER_SENT));
        assertTrue(o.requiresNote());
    }
    assertFalse(JoiningOutcome.JOINED.requiresNote());
}
@Test void nothingLeavesAClosedStage() {
    for (ReviewStatus closed : List.of(ReviewStatus.JOINED, ReviewStatus.NOT_JOINING, ReviewStatus.JOINING_BLOCKED, ReviewStatus.DECLINED))
        for (JoiningOutcome o : JoiningOutcome.values()) assertFalse(o.allowedFrom(closed));
}
@Test void stagesMapOneToOne() {
    assertEquals(ReviewStatus.JOINING_BLOCKED, JoiningOutcome.BLOCKED.stage());
}
@Test void atLeastApprovedIncludesClosedHires() {
    assertTrue(ReviewStatus.atLeast(ReviewStatus.OFFER_APPROVED).containsAll(
        List.of(ReviewStatus.ACCEPTED, ReviewStatus.DECLINED, ReviewStatus.JOINED, ReviewStatus.NOT_JOINING, ReviewStatus.JOINING_BLOCKED)));
    assertFalse(ReviewStatus.atLeast(ReviewStatus.OFFER_APPROVED).contains(ReviewStatus.OFFER));
}
```

In `AnalyticsRepositoryTest`, add `funnelCountsClosedHiresAsApproved`: seed one candidate at `JOINED` and one at `JOINING_BLOCKED`, assert the funnel's contacted, interviewed, offer and approved steps each count 2.

- [ ] **Step 2: Run to verify they fail**

Run: `mvn -pl common,api-service -am test -Dtest='JoiningOutcomeTest,AnalyticsRepositoryTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, "cannot find symbol JoiningOutcome".

- [ ] **Step 3: Implement the enums, the entity fields and the migration**

`V12__joining_outcome.sql`:
```sql
-- Additive only. How a hire ended after the offer was accepted.
ALTER TABLE candidate_offers ADD COLUMN IF NOT EXISTS joining_outcome VARCHAR(16)
    CHECK (joining_outcome IN ('JOINED', 'NOT_JOINING', 'BLOCKED'));
ALTER TABLE candidate_offers ADD COLUMN IF NOT EXISTS joining_closed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE candidate_offers ADD COLUMN IF NOT EXISTS joining_closed_by VARCHAR(36) REFERENCES users(id);
ALTER TABLE candidate_offers ADD COLUMN IF NOT EXISTS joining_outcome_note VARCHAR(1000);
```
`JoiningOutcome.allowedFrom`: JOINED ⇔ `current == JOINING_SET`; the others ⇔ `current == ACCEPTED || current == JOINING_SET`. Keeping the rule on the enum (not in the service) is the single place transitions are decided.

- [ ] **Step 4: Replace the hard-coded `IN (...)` lists with `c.review_status = ANY(:reachedContacted)` etc.**

Bind each as a `String[]` of `ReviewStatus.atLeast(X)` names on the existing `MapSqlParameterSource` (`p.addValue("reachedContacted", names(ReviewStatus.atLeast(ReviewStatus.CONTACTED)))`), so a future stage cannot be forgotten and no value is concatenated into SQL.

- [ ] **Step 5: Run to verify they pass**

Run: `mvn -pl common,api-service -am test -Dtest='JoiningOutcomeTest,AnalyticsRepositoryTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add common/src api-service/src/main/java/com/resumescreener/api/analytics api-service/src/test/java/com/resumescreener/api/analytics
git commit -m "feat(pipeline): joined, not joining and blocked stages with V12"
```

### Task 4: Close the hire, server side

**Files:**
- Create: `api-service/src/main/java/com/resumescreener/api/candidate/dto/CloseHireRequest.java`
- Modify: `api-service/src/main/java/com/resumescreener/api/pipeline/PipelineService.java` (new `closeHire`, `saveJoining` guard, `determineNextStep`)
- Modify: `api-service/src/main/java/com/resumescreener/api/pipeline/PipelineController.java`
- Modify: `api-service/src/main/java/com/resumescreener/api/candidate/dto/CandidateOfferDto.java`
- Modify: `common/src/main/java/com/resumescreener/common/user/Permission.java` (PIPELINE_MANAGE description: "Contact candidates, schedule interviews, prepare offers and close hires")
- Test: `api-service/src/test/java/com/resumescreener/api/pipeline/PipelineServiceTest.java`, `api-service/src/test/java/com/resumescreener/api/auth/PermissionEnforcementTest.java`, create `api-service/src/test/java/com/resumescreener/api/pipeline/CloseHireDatabaseTest.java`

**Interfaces:**
- Consumes: `JoiningOutcome`, the new `ReviewStatus` values, `CandidateOffer` fields (Task 3).
- Produces:
  - `record CloseHireRequest(@NotNull JoiningOutcome outcome, @Size(max = 1000) String note)`
  - `PipelineService.closeHire(String sessionId, String candidateId, CloseHireRequest req, String email): CandidateOfferDto`
  - `POST /api/sessions/{sessionId}/candidates/{candidateId}/joining/outcome`, `@PreAuthorize("hasAuthority('PIPELINE_MANAGE')")`, message "Hire closed"
  - `CandidateOfferDto` gains `joiningOutcome`, `joiningClosedAt`, `joiningClosedByName`, `joiningOutcomeNote`.

- [ ] **Step 1: Write the failing tests (PipelineServiceTest, mocks as in the file)**

```java
@Test void closeHire_joinedMovesToJoinedAndStampsTheOffer() {
    // candidate at JOINING_SET, offer joiningDate = 2026-11-02, clock fixed at 2026-11-02T08:00Z
    CandidateOfferDto dto = service.closeHire("s1", "c1", new CloseHireRequest(JoiningOutcome.JOINED, null), recruiter.getEmail());
    assertEquals(ReviewStatus.JOINED, candidate.getReviewStatus());
    assertEquals(JoiningOutcome.JOINED, dto.joiningOutcome());
    verify(events).save(argThat(e -> e.getStage().equals("JOINED")));
}
@Test void closeHire_joinedBeforeTheJoiningDateIsRefused() {
    // clock fixed at 2026-11-01T08:00Z
    ApiException ex = assertThrows(ApiException.class, () -> service.closeHire("s1", "c1", new CloseHireRequest(JoiningOutcome.JOINED, null), recruiter.getEmail()));
    assertEquals("Not joined yet", ex.getMessage());
}
@Test void closeHire_notJoiningNeedsANote() {
    ApiException ex = assertThrows(ApiException.class, () -> service.closeHire("s1", "c1", new CloseHireRequest(JoiningOutcome.NOT_JOINING, "  "), recruiter.getEmail()));
    assertEquals("Add a note", ex.getMessage());
}
@Test void closeHire_blockedFromAcceptedWorksWithoutAJoiningDate() { /* stage ACCEPTED, note "Visa refused" → JOINING_BLOCKED, note stored trimmed */ }
@Test void closeHire_refusesAClosedHire() { /* stage JOINED → ApiException "The hire is already closed" */ }
@Test void closeHire_locksTheCandidateRow() { /* verify(candidates).findForUpdateById("c1") */ }
@Test void saveJoining_refusesAClosedHire() { /* stage JOINED → ApiException "The hire is already closed" */ }
```

In `PermissionEnforcementTest`, add: an HR_INTERVIEWER posting to `/joining/outcome` gets 403. `CloseHireDatabaseTest` (Testcontainers, `@DataJpaTest` like `RoleServiceDatabaseTest`): after `closeHire(BLOCKED, "Visa refused")`, `jdbc.queryForMap("select joining_outcome, joining_outcome_note, joining_closed_by from candidate_offers where candidate_id = ?")` returns `BLOCKED`, `Visa refused` and the recruiter's id, and `candidates.review_status` is `JOINING_BLOCKED`.

- [ ] **Step 2: Run to verify they fail**

Run: `mvn -pl api-service -am test -Dtest='PipelineServiceTest,PermissionEnforcementTest,CloseHireDatabaseTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, "cannot find symbol CloseHireRequest".

- [ ] **Step 3: Implement**

Clock: follow `AnalyticsService` (`private Clock clock = Clock.systemUTC();` plus a package-private `setClock`), so the constructor and existing tests stay as they are. `closeHire` order: `sessionService.getSession` (access) → `findForUpdateById` (already on main) followed by main's candidate-belongs-to-session check → if `isClosed()` throw validation "The hire is already closed" → if `!outcome.allowedFrom(stage)` throw validation "Wrong stage" → note rule → JOINED date rule (`LocalDate.now(clock).isBefore(offer.getJoiningDate())` → "Not joined yet") → stamp offer → `moveTo(candidate, outcome.stage(), outcome.stage().name(), actor, summary)`. Summaries: "Joined, recorded by {name}", "Not joining: {note}, recorded by {name}", "Blocked: {note}, recorded by {name}". `saveJoining` also uses `findForUpdateById` (already on main) followed by main's candidate-belongs-to-session check and refuses a closed stage with the same message. `determineNextStep`: JOINED → "Hired", NOT_JOINING → "Closed: not joining", JOINING_BLOCKED → "Closed: blocked"; JOINING_SET → "Joining {date}, then close the hire".

- [ ] **Step 4: Run to verify they pass**

Run: same command as Step 2.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add common/src api-service/src
git commit -m "feat(pipeline): close the hire as joined, not joining or blocked"
```

### Task 5: Joining panel in the UI

**Files:**
- Modify: `ui/src/types/index.ts` (`ReviewStatus`, `CandidateOffer`, new `JoiningOutcome = 'JOINED' | 'NOT_JOINING' | 'BLOCKED'`)
- Modify: `ui/src/lib/pipeline.ts` (`STAGE_LABEL`, `STAGE_ORDER`, `stageTone`, `buildTrack` last step, new `closeHireProblem`)
- Modify: `ui/src/api/client.ts` (`pipelineApi.closeHire`)
- Create: `ui/src/components/JoiningPanel.tsx` (replaces `HiringPipeline.tsx:1033-1070`)
- Modify: `ui/src/components/HiringPipeline.tsx` (render `JoiningPanel`; `eventLabel` gains JOINED, NOT_JOINING, JOINING_BLOCKED)
- Test: `ui/src/lib/pipeline.test.ts`

**Interfaces:**
- Consumes: the endpoint and DTO fields from Task 4.
- Produces:
  - `closeHireProblem(outcome: JoiningOutcome, note: string, joiningDate: string | null, stage: ReviewStatus, today: string): string | null` (`today` as `YYYY-MM-DD`)
  - `pipelineApi.closeHire(sessionId: string, candidateId: string, outcome: JoiningOutcome, note?: string) => api.post<CandidateOffer>(.../joining/outcome)`
  - `JoiningPanel({ sessionId, candidateId, candidateName, stage, offer, canManage, onChanged }: { …; onChanged(offer: CandidateOffer, stage: ReviewStatus): void })`

- [ ] **Step 1: Write the failing tests**

```ts
describe('closeHireProblem', () => {
  it('needs a note to close as not joining or blocked', () => {
    expect(closeHireProblem('NOT_JOINING', ' ', '2026-11-02', 'JOINING_SET', '2026-10-18')).toBe('Add a note');
    expect(closeHireProblem('BLOCKED', 'Visa refused', null, 'ACCEPTED', '2026-10-18')).toBeNull();
  });
  it('allows Joined only from the joining date', () => {
    expect(closeHireProblem('JOINED', '', '2026-11-02', 'JOINING_SET', '2026-11-01')).toBe('Not joined yet');
    expect(closeHireProblem('JOINED', '', '2026-11-02', 'JOINING_SET', '2026-11-02')).toBeNull();
    expect(closeHireProblem('JOINED', '', null, 'ACCEPTED', '2026-11-02')).toBe('Set the joining date first');
  });
});
it('names the closing step by its outcome', () => {
  expect(buildTrack('JOINING_BLOCKED', [], []).at(-1)).toEqual({ key: 'CLOSED', label: 'Blocked', state: 'done' });
});
it('closed stages read as closed', () => {
  expect(STAGE_LABEL.NOT_JOINING).toBe('Not joining');
  expect(stageTone('JOINED')).toBe('success');
  expect(stageTone('NOT_JOINING')).toBe('warn');
  expect(stageTone('JOINING_BLOCKED')).toBe('danger');
});
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd ui && npx vitest run src/lib/pipeline.test.ts`
Expected: FAIL, "closeHireProblem is not a function".

- [ ] **Step 3: Implement the lib changes, then `JoiningPanel`**

Panel states, copy fixed by the Pipeline artboard: read view of joining details with an "Edit details" button (form reuses the existing date and notes fields; Cancel restores the saved values); below it, while `ACCEPTED` or `JOINING_SET` and `canManage`, a "Close the hire" form with a `fieldset`/`legend` "How did it end?" and three radio options (Joined, Not joining, Blocked with their one-line explanations); a note `Textarea` labelled "Note (required for Not joining and Blocked)", `maxLength={1000}`; submit "Close the hire", disabled while `closeHireProblem` returns a problem, which shows under the button. Joined is disabled with the hint "Available from {date}" before the joining date. After closing: a summary card ("Joined on {date}" / "Not joining" / "Blocked"), the `StageBadge`, the quoted note, "Closed by {name}, {relative date}", and no edit controls. The `STAGE_ORDER` value for the three closed stages is 10.

- [ ] **Step 4: Verify**

Run: `cd ui && npx vitest run && npm run build`
Expected: PASS and a clean build (the `Record<ReviewStatus, …>` maps fail to compile until every map has the three new stages, which is the point).

- [ ] **Step 5: Commit**

```bash
git add ui/src
git commit -m "feat(ui): edit joining details and close the hire"
```

Then F10's cycle: real-stack smoke (accept an offer, set a joining date, edit it, close as Blocked; `psql` shows the offer columns and the stage; the Pipeline board shows "Closed: blocked"; Analytics still counts the candidate as approved), UAT, PR `feat/joining-outcome` → `feat/interview-agenda`.

---

## Part F11: Roles and permissions management

Design: canvas artboards Main (edit a role), Compare (all roles side by side), Phone (390 px, light). Role list grouped as Administration (Admin), Recruiting (HR chief, Recruiter), Interviewers (Technical interviewer, HR interviewer), Top management (Department director, CEO, Chairman). Permissions grouped as Screening, Hiring pipeline, Insight, Administration (admin only, locked).

### Task 6: Catalog groups and one atomic save

**Files:**
- Create: `common/src/main/java/com/resumescreener/common/user/PermissionGroup.java`
- Modify: `common/src/main/java/com/resumescreener/common/user/Permission.java`
- Modify: `api-service/src/main/java/com/resumescreener/api/admin/dto/RolesResponse.java`
- Create: `api-service/src/main/java/com/resumescreener/api/admin/dto/UpdateRolesRequest.java`
- Modify: `api-service/src/main/java/com/resumescreener/api/admin/RoleService.java`, `RoleController.java`
- Modify: `api-service/src/main/java/com/resumescreener/api/common/ApiErrorCode.java`, `ApiException.java`
- Test: `api-service/src/test/java/com/resumescreener/api/admin/RoleServiceDatabaseTest.java`, `RoleControllerTest.java`

**Interfaces:**
- Produces:
  - `enum PermissionGroup { SCREENING, PIPELINE, INSIGHT, ADMINISTRATION }`; `Permission(String description, PermissionGroup group)` with `adminOnly()` now `group == ADMINISTRATION` (one source of truth instead of a second boolean).
  - `RolesResponse.PermissionDto(String name, String description, boolean adminOnly, PermissionGroup group)`
  - `record UpdateRolesRequest(@NotEmpty @Size(max = 16) List<@Valid RoleChange> changes) { record RoleChange(@NotNull Role role, @NotNull Set<Permission> before, @NotNull Set<Permission> after) {} }`
  - `RoleService.updateAll(List<RoleChange> changes): RolesResponse` (`@Transactional`)
  - `PUT /api/admin/roles`, `@PreAuthorize("hasAuthority('ROLE_MANAGE')")`, message "Permissions saved"; the per-role `PUT /{role}/permissions` stays for compatibility.
  - `ApiErrorCode.CONFLICT`, `ApiException.conflict(String message, String details)` → HTTP 409.

- [ ] **Step 1: Write the failing tests**

```java
@Test void updateAll_isAllOrNothing() {
    // RECRUITER valid change, then ADMIN (not editable) → whole call throws, RECRUITER grants unchanged
    assertThrows(ApiException.class, () -> roleService.updateAll(List.of(
        new RoleChange(Role.RECRUITER, recruiterNow, plus(recruiterNow, Permission.ANALYTICS_VIEW)),
        new RoleChange(Role.ADMIN, Set.of(), Set.of()))));
    assertThat(grantsOf(Role.RECRUITER)).isEqualTo(recruiterNow);
}
@Test void updateAll_rejectsAStaleBase() {
    ApiException ex = assertThrows(ApiException.class, () -> roleService.updateAll(List.of(
        new RoleChange(Role.RECRUITER, Set.of(Permission.SESSION_RUN), Set.of()))));   // base differs from V7/V10 seed
    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
}
@Test void updateAll_savesEveryRoleAndAuditsEach() { /* two roles changed → both persisted; auditService.record called twice */ }
@Test void updateAll_refusesDuplicateRoles() { /* same role twice → validation error */ }
@Test void catalogCarriesGroups() { assertThat(roleService.catalog().permissions()).anyMatch(p -> p.name().equals("OFFER_APPROVE") && p.group() == PermissionGroup.PIPELINE); }
```

`RoleControllerTest`: a RECRUITER calling `PUT /api/admin/roles` gets 403; an empty `changes` list gets 400.

- [ ] **Step 2: Run to verify they fail**

Run: `mvn -pl api-service -am test -Dtest='RoleServiceDatabaseTest,RoleControllerTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation failure, "cannot find symbol UpdateRolesRequest".

- [ ] **Step 3: Implement**

`updateAll`: reject duplicate roles; for each change compare `current(role)` with `Permission.effective(role, before)`; any mismatch throws `conflict("Someone else changed these roles", "<ROLE> changed since you loaded it")` before any write; then call the existing `update(role, after)` per change (its admin and admin-only checks and audit diff stay as they are). One transaction, so a throw rolls everything back. Return `catalog()`.

- [ ] **Step 4: Run to verify they pass**

Run: same as Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add common/src api-service/src
git commit -m "feat(admin): permission groups and one atomic roles save"
```

### Task 7: Draft state and landing page, as pure functions

**Files:**
- Create: `ui/src/lib/roleDraft.ts`
- Modify: `ui/src/lib/permissions.ts` (`landingPage`, `homePath` built on it, `ROLE_GROUPS`, `PERMISSION_GROUP_LABEL`)
- Modify: `ui/src/types/index.ts` (`PermissionGroup`, `PermissionInfo.group`, `RoleChange`)
- Modify: `ui/src/api/client.ts` (`adminApi.updateRoles(changes: RoleChange[]) => api.put<RolesCatalog>('/admin/roles', { changes })`)
- Test: `ui/src/lib/roleDraft.test.ts`, `ui/src/lib/permissions.test.ts`

**Interfaces:**
- Produces:
  - `type RoleDraft = { base: Record<Role, Permission[]>; draft: Record<Role, Permission[]> }`
  - `type RoleDraftAction = { type: 'load'; catalog: RolesCatalog } | { type: 'toggle'; role: Role; permission: Permission } | { type: 'discard' }`
  - `roleDraftReducer(state: RoleDraft, action: RoleDraftAction): RoleDraft` (toggle is a no-op for a non-editable role or an admin-only permission)
  - `pendingChanges(catalog: RolesCatalog, state: RoleDraft): RoleChange[]` (`RoleChange = { role; before; after }`, editable roles with a diff only)
  - `affectedPeople(catalog: RolesCatalog, changes: RoleChange[]): number`
  - `landingPage(permissions: Permission[]): { path: string; label: string } | null` and `homePath(user)` = `landingPage(user?.permissions ?? [])?.path ?? '/login'`
  - `ROLE_GROUPS: { label: string; roles: Role[] }[]` (order and members as in Part F11's header)

Fixtures: `catalog` built from the V7 to V11 seed grants with `userCount` RECRUITER 6 and CEO 1; `empty = { base: {}, draft: {} }`; `loaded` is `empty` after a `load`; `recruiterNow = ['SESSION_RUN', 'CANDIDATE_REVIEW', 'PIPELINE_MANAGE']`.

- [ ] **Step 1: Write the failing tests**

```ts
it('toggling twice leaves no pending change', () => {
  let s = roleDraftReducer(empty, { type: 'load', catalog });
  s = roleDraftReducer(s, { type: 'toggle', role: 'RECRUITER', permission: 'ANALYTICS_VIEW' });
  expect(pendingChanges(catalog, s)).toEqual([{ role: 'RECRUITER', before: recruiterNow, after: [...recruiterNow, 'ANALYTICS_VIEW'] }]);
  s = roleDraftReducer(s, { type: 'toggle', role: 'RECRUITER', permission: 'ANALYTICS_VIEW' });
  expect(pendingChanges(catalog, s)).toEqual([]);
});
it('never toggles admin or an admin-only permission', () => {
  const s = roleDraftReducer(loaded, { type: 'toggle', role: 'ADMIN', permission: 'SESSION_RUN' });
  expect(roleDraftReducer(s, { type: 'toggle', role: 'RECRUITER', permission: 'ROLE_MANAGE' }).draft).toEqual(loaded.draft);
});
it('counts people across changed roles', () => { expect(affectedPeople(catalog, changesForRecruiterAndCeo)).toBe(6 + 1); });
it('discard restores the base', () => { expect(roleDraftReducer(dirty, { type: 'discard' }).draft).toEqual(dirty.base); });
it('a role with nothing granted has no landing page', () => { expect(landingPage([])).toBeNull(); });
it('lands interviewers on My interviews', () => { expect(landingPage(['INTERVIEW_FEEDBACK'])).toEqual({ path: '/interviews', label: 'My interviews' }); });
it('lands admins on Roles and permissions', () => { expect(landingPage(['USER_MANAGE', 'ROLE_MANAGE'])?.path).toBe('/admin/permissions'); });
```

Keep every existing `homePath` test in `permissions.test.ts` green; change only the admin one from `/admin/users` to `/admin/permissions`.

- [ ] **Step 2: Run to verify they fail**

Run: `cd ui && npx vitest run src/lib/roleDraft.test.ts src/lib/permissions.test.ts`
Expected: FAIL, "Failed to resolve import ./roleDraft".

- [ ] **Step 3: Implement**

Draft arrays keep catalog order so `before`/`after` compare by membership, not position (`diffGrants` already does). The reducer returns the same object on a no-op so React skips the render.

- [ ] **Step 4: Run to verify they pass**

Run: same as Step 2. Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add ui/src/lib ui/src/types/index.ts ui/src/api/client.ts
git commit -m "feat(ui): role draft reducer and landing page per permission set"
```

### Task 8: The Roles and permissions management page

**Files:**
- Create: `ui/src/components/ui/Switch.tsx`
- Create: `ui/src/components/roles/RoleList.tsx`, `RolePermissionsPanel.tsx`, `RoleCompareTable.tsx`, `UnsavedChangesBar.tsx`
- Create: `ui/src/lib/useUnsavedGuard.ts` (the leave-without-saving effect moved from `AdminRoles.tsx:54-72`)
- Create: `ui/src/pages/RolesPermissions.tsx`
- Delete: `ui/src/pages/AdminRoles.tsx`
- Modify: `ui/src/App.tsx` (route `/admin/permissions` under `PermissionRoute permission="ROLE_MANAGE"`; `/admin/roles` → `<Navigate to="/admin/permissions" replace />`)
- Modify: `ui/src/components/AdminTabs.tsx` (tabs in order: "Roles and permissions" `/admin/permissions`, "Users" `/admin/users`; active underline `ink`, not `mark`)
- Modify: `ui/src/components/Navbar.tsx:48` (label "Permissions", `to: '/admin/permissions'`, `match: ['/admin']`)
- Modify: `ui/DESIGN.md` (new "Roles and permissions" section: switches, warn-soft changed rows, inverted unsaved bar)
- Test: `ui/src/components/ui/Switch.test.tsx`

**Interfaces:**
- Consumes: Task 7's reducer, selectors, `landingPage`, `ROLE_GROUPS`, `adminApi.updateRoles`; Task 6's `group` on each permission; existing `Segmented`, `Panel`, `Alert`, `SkeletonRows`, `Button`, `MainLayout`, `PageHeader`.
- Produces:
  - `Switch({ checked, onChange, disabled, labelledBy, changed }: { checked: boolean; onChange(next: boolean): void; disabled?: boolean; labelledBy: string; changed?: boolean })`, a `<button type="button" role="switch" aria-checked>`, 44×26 inside a 44 px hit area.
  - URL state on the page: `?view=edit|compare&role=<Role>` via `useSearchParams`, defaults `edit` and the first editable role (`HR_CHIEF`).

- [ ] **Step 1: Write the failing Switch test**

```tsx
it('is a labelled switch that reports the next value', async () => {
  const onChange = vi.fn();
  render(<><span id="l">Analytics</span><Switch checked={false} onChange={onChange} labelledBy="l" /></>);
  const sw = screen.getByRole('switch', { name: 'Analytics' });
  expect(sw).toHaveAttribute('aria-checked', 'false');
  fireEvent.click(sw);
  expect(onChange).toHaveBeenCalledWith(true);
});
it('does nothing when disabled', () => { /* disabled → aria-disabled="true", click does not call onChange */ });
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd ui && npx vitest run src/components/ui/Switch.test.tsx`
Expected: FAIL, "Failed to resolve import ./Switch".

- [ ] **Step 3: Build the components to the Main, Compare and Phone artboards**

- `RoleList`: `ROLE_GROUPS` sections with `h2` group labels; each role a `button` with `aria-current` when selected, label, "{n} people · {k} on" (Admin: "every permission" and a lock icon), "Unsaved" pill (`warn-soft`/`warn`) when it has a pending change. Below `sm` it renders a labelled `<select>` ("Role") instead, options suffixed "· unsaved".
- `RolePermissionsPanel`: summary plate (name, one-line blurb from a `ROLE_BLURB` map in `lib/permissions.ts`, People, Permissions "k / 7", Lands on from `landingPage`); warning alert when `landingPage` is null ("With nothing switched on, people with this role can sign in but have no page to open. Give them at least one permission."); for Admin a muted note "Admins always hold every permission, so no change can lock the last admin out."; one plate per `PermissionGroup` with rows (label, server description, "Will be granted"/"Will be removed" when changed, `warn-soft` row wash, `Switch`); the Administration group shows "Admin only" and locked switches.
- `RoleCompareTable`: the existing matrix turned around: roles are rows (row header links to `?view=edit&role=…`), the seven editable permissions are columns under group header cells, the last column "Admin only" reads "Always" for Admin and "—" for others; dots have `role="img"` and an `aria-label` "{permission}: granted|not granted"; horizontal scroll inside the panel only.
- `UnsavedChangesBar`: sticky `region` "Unsaved changes", inverted ink, title "{r} role(s) changed, {n} people affected on their next action", one line per role "+ Analytics, − Open any session", buttons "Discard" and "Save changes".
- `RolesPermissions`: `useReducer(roleDraftReducer)`, load via `adminApi.roles()`, `Segmented` view switch "Edit a role" / "Compare roles", save via one `adminApi.updateRoles(pendingChanges(...))`; on 409 show `Alert` "Someone else changed these roles. The page now shows their changes; make yours again." and reload; on success show "Permissions saved. The change is in the audit log." and reload; `useUnsavedGuard(dirty)`. Title "Roles and permissions management", meta "Pick a role, switch what it can do, then save. People see the change on their next action, without signing out."

- [ ] **Step 4: Verify**

Run: `cd ui && npx vitest run && npm run build`
Expected: PASS, clean build.
In the running app as an admin, dark and light, at 360, 768 and 1280 px: `/admin/roles` redirects to `/admin/permissions`; the navbar shows "Permissions"; toggle Analytics for Recruiter, the row washes and the bar says "1 role changed, {n} people affected"; Save, then `psql -c "select permission from role_permissions where role='RECRUITER'"` lists `ANALYTICS_VIEW`; a recruiter's next request carries it without signing out; open the page in two tabs, save in one, save a different change in the other: the second gets the conflict message and nothing is overwritten; Compare shows the same grants; keyboard: Tab reaches every switch, Space toggles, focus ring visible; no horizontal page scroll at 360 px.

- [ ] **Step 5: Commit**

```bash
git add -A ui/src ui/DESIGN.md
git commit -m "feat(ui): roles and permissions management redesign"
```

Then F11's cycle: UAT, smoke, PR `feat/roles-permissions-page` → `feat/joining-outcome`.

---

## Out of scope (named, not built)

- Reopening a closed hire, and hire counts in analytics (the funnel stays at "approved").
- Creating or renaming roles; roles stay the fixed enum.
- Reordering interviews by drag; order is always computed.
- Changes to My interviews, which already splits waiting, upcoming and done.
