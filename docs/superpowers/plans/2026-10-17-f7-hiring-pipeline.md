# F7 Hiring Pipeline and Interviewer Roles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Carry a shortlisted candidate through contact, technical and HR interviews with written feedback, an uploaded offer and its approval, with interviewers seeing only the candidates assigned to them.

**Architecture:** The existing `candidates.review_status` column becomes the pipeline stage (enum extended, so "shortlisted" means "reached the shortlist or beyond"). Three tables: `interviews` (schedule and feedback on one row), `offers` (one per candidate) and `candidate_stage_events` (history for the timeline and for F8). One `PipelineService` owns every stage change; the three interview-driven stages are recomputed from the interviews, never set by hand. Interviewers reach candidates only through `/api/interviews/**`, which checks assignment, so no session endpoint is opened to them.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring Security method security, JPA, Flyway, MinIO, JUnit 5 + Mockito, Testcontainers; React 18, TypeScript, Tailwind 3, Vitest.

**Spec:** [2026-10-17-talentlens-brief.md](2026-10-17-talentlens-brief.md), section F7. Mockups: TalentLens designs artifact, "Candidate page", "My interviews", "Interview feedback", "Pipeline".

## Global Constraints

- Branch `feat/hiring-pipeline` from `feat/candidate-notes`. One PR. Migration `V9__hiring_pipeline.sql`.
- `enum ReviewStatus` in this order: `NONE, SHORTLISTED, CONTACTED, INTERVIEW_SCHEDULED, INTERVIEWED, OFFER, OFFER_APPROVED`. UI labels: Screened, Shortlisted, Contacted, Interviewing, Interviewed, Offer, Offer approved.
- `enum Role` adds `TECHNICAL_INTERVIEWER` ("Technical interviewer") and `HR_INTERVIEWER` ("HR interviewer").
- `enum Permission` adds (non admin-only):
  - `PIPELINE_MANAGE` "Contact candidates, schedule interviews and prepare offers" — seeded for ADMIN, HR_CHIEF, RECRUITER
  - `OFFER_APPROVE` "Approve job offers" — ADMIN, HR_CHIEF
  - `INTERVIEW_FEEDBACK` "Give feedback on interviews assigned to them" — ADMIN, TECHNICAL_INTERVIEWER, HR_INTERVIEWER
- `enum InterviewKind { TECHNICAL, HR }`; a `TECHNICAL` interview needs a `TECHNICAL_INTERVIEWER`, an `HR` interview an `HR_INTERVIEWER`. Error: "Choose a technical interviewer for a technical interview" (and the HR twin).
- `enum Recommendation { STRONG_HIRE, HIRE, NO_HIRE, STRONG_NO_HIRE }`, labels "Strong hire", "Hire", "No hire", "Strong no hire".
- Limits: duration 15 to 240 minutes (default 60); location up to 500 characters; note to interviewer up to 2,000; rating 1 to 5; assessment 20 to 5,000 characters; offer PDF only, up to 10 MB (`application/pdf` and `%PDF-` magic bytes).
- Changes to the pipeline (contact, schedule, reschedule, cancel, move to offer, upload offer) need `PIPELINE_MANAGE` and write access to the session (`SessionService.getOwnedSession`). Reading the pipeline needs read access (`getSession`).
- Approval needs `OFFER_APPROVE` and read access to the session, and the approver is not the uploader. Error: "Someone other than the uploader must approve this offer".
- Audit actions: `CANDIDATE_CONTACTED`, `INTERVIEW_SCHEDULE`, `INTERVIEW_RESCHEDULE`, `INTERVIEW_CANCEL`, `INTERVIEW_FEEDBACK_SUBMIT`, `CANDIDATE_TO_OFFER`, `OFFER_UPLOAD`, `OFFER_APPROVE`.
- Every stage change inserts one `candidate_stage_events` row in the same transaction. Unshortlisting removes nothing from history; it adds a `NONE` event.
- Any lazy association read outside a transaction uses an `@EntityGraph` finder (open-in-view is off).

## Review Focus

1. **Shortlist actions on candidates already further along** ("Shortlist top 10" over a candidate in `OFFER`, unshortlisting an `INTERVIEWED` candidate): the later stage stays; bulk shortlist skips them, unshortlist refuses with "Candidates past the shortlist can't be unshortlisted". Test: Task 1 `shortlistNeverMovesACandidateBackwards`, `unshortlistRefusedPastShortlist`.
2. **Two interviewers submitting feedback at the same moment**: the candidate ends `INTERVIEWED`, not stuck at `INTERVIEW_SCHEDULED`. Test: Task 3 `concurrentLastFeedbackEndsInterviewed` (Testcontainers, two threads, row lock).
3. **An interviewer opening another interviewer's interview, a session URL, or the resume of a candidate not assigned to them**: 403 and no data. Test: Task 3 `interviewerSeesOnlyOwnInterviews`, Task 1 `interviewerCannotOpenSessions`.
4. **An interviewer whose role is changed after scheduling**: they can no longer submit (no `INTERVIEW_FEEDBACK`), and the recruiter can reassign the interview by rescheduling with a new interviewer. Test: Task 3 `feedbackNeedsPermissionAtSubmitTime`, `rescheduleCanChangeInterviewer`.
5. **Uploading a non-PDF renamed `.pdf`, or an 11 MB PDF**: 400 "Upload the offer as a PDF up to 10 MB", the previous offer stays. Test: Task 4 `rejectsFakeAndOversizedPdfs`.

---

### Task 1: Stages, roles, permissions and schema

**Files:**
- Create: `common/src/main/resources/db/migration/V9__hiring_pipeline.sql`, `common/src/main/java/com/resumescreener/common/pipeline/{InterviewKind,Recommendation,Interview,InterviewRepository,Offer,OfferRepository,StageEvent,StageEventRepository}.java`
- Modify: `common/.../session/ReviewStatus.java`, `common/.../user/Role.java`, `common/.../user/Permission.java`, `common/.../session/CandidateRepository.java`, `api-service/.../candidate/CandidateService.java:50-103`, `api-service/.../session/SessionController.java:106`, `api-service/.../batch/BatchController.java:77-92`, `api-service/.../analytics/AnalyticsRepository.java:57,76,119`
- Test: `api-service/src/test/java/com/resumescreener/api/candidate/CandidateReviewTest.java` (extend), `api-service/src/test/java/com/resumescreener/api/pipeline/PipelineSchemaDatabaseTest.java`, `api-service/src/test/java/com/resumescreener/api/auth/PermissionEnforcementTest.java` (extend)

**Interfaces:**
- Produces:
  - `ReviewStatus.SHORTLISTED_OR_LATER: Set<ReviewStatus>` (every value except `NONE`) and `boolean isAtLeast(ReviewStatus other)` (ordinal compare).
  - `CandidateRepository.countBySessionIdAndReviewStatusIn(String sessionId, Collection<ReviewStatus> statuses): long`; `@Lock(PESSIMISTIC_WRITE) Optional<Candidate> findForUpdateById(String id)`.
  - SQL (V9): `interviews(id PK, candidate_id FK candidates ON DELETE CASCADE, kind VARCHAR(20), interviewer_id FK users, scheduled_at TIMESTAMPTZ, duration_minutes INT, location VARCHAR(500), note_to_interviewer TEXT, scheduled_by FK users, created_at TIMESTAMPTZ, recommendation VARCHAR(20), rating SMALLINT CHECK (rating BETWEEN 1 AND 5), assessment TEXT, feedback_submitted_at TIMESTAMPTZ)`, indexes `(interviewer_id, scheduled_at)`, `(candidate_id)`; `offers(candidate_id PK FK candidates ON DELETE CASCADE, file_key, original_file_name, uploaded_by FK users, uploaded_at, approved_by FK users, approved_at)`; `candidate_stage_events(id BIGSERIAL PK, candidate_id FK candidates ON DELETE CASCADE, stage VARCHAR(30), changed_by FK users ON DELETE SET NULL, changed_at TIMESTAMPTZ)`, indexes `(candidate_id, changed_at)`, `(stage, changed_at)`; widen `candidates.review_status` to `VARCHAR(30)`; seed the permission rows from Global Constraints; backfill one `SHORTLISTED` event per currently shortlisted candidate from `reviewed_at, reviewed_by`.
  - Entities map those columns one to one; `Interview.hasFeedback()` is `feedbackSubmittedAt != null`.

- [ ] **Step 1: Write the failing tests.**

```java
// CandidateReviewTest
@Test void shortlistNeverMovesACandidateBackwards()   // c1 OFFER, c2 NONE; shortlistTop(2) -> c1 still OFFER, c2 SHORTLISTED, changed == 1
@Test void unshortlistRefusedPastShortlist()           // c1 INTERVIEWED, review NONE -> ApiException "Candidates past the shortlist can't be unshortlisted"
@Test void shortlistWritesAStageEvent()                // StageEventRepository.save called with stage SHORTLISTED, changedBy u1
@Test void shortlistedTotalCountsEveryLaterStage()     // ReviewResult.shortlistedTotal from countBySessionIdAndReviewStatusIn(SHORTLISTED_OR_LATER)
// PermissionEnforcementTest
@Test void interviewerCannotOpenSessions()             // TECHNICAL_INTERVIEWER: GET /api/sessions/{id} -> 403, GET /api/sessions/{id}/candidates/{cid} -> 403
@Test void pipelinePermissionsSeeded()                 // RECRUITER has PIPELINE_MANAGE, not OFFER_APPROVE; HR_INTERVIEWER has only INTERVIEW_FEEDBACK
// PipelineSchemaDatabaseTest (Testcontainers): V1..V9 apply on an empty DB and on a DB holding one shortlisted candidate,
// which ends with exactly one SHORTLISTED stage event; rating 6 violates the check constraint.
```

- [ ] **Step 2: Run** `mvn -pl api-service -am test -Dtest='CandidateReviewTest,PermissionEnforcementTest,PipelineSchemaDatabaseTest' -Dsurefire.failIfNoSpecifiedTests=false` — expect FAIL.
- [ ] **Step 3: Implement.** `CandidateService.apply` skips candidates whose stage `isAtLeast(CONTACTED)` when shortlisting and refuses unshortlisting them; it writes a stage event per change. Session `shortlistedCount`, the `review=SHORTLISTED` filter in `BatchController` and the three analytics `FILTER` clauses all switch to "stage in `SHORTLISTED_OR_LATER`" (`review_status <> 'NONE'` in SQL).
- [ ] **Step 4: Run** the same command, then `mvn -q verify` — expect PASS (existing F2 and F3 tests stay green).
- [ ] **Step 5: Commit** `feat(pipeline): stages, interviewer roles and pipeline schema`

---

### Task 2: Pipeline service: contact, offer stage, timeline and board

**Files:**
- Create: `api-service/src/main/java/com/resumescreener/api/pipeline/{PipelineService,PipelineController}.java`, `.../pipeline/dto/{PipelineView,StageEventDto,PipelineRow}.java`
- Test: `api-service/src/test/java/com/resumescreener/api/pipeline/PipelineServiceTest.java`

**Interfaces:**
- Consumes: Task 1 entities and repositories.
- Produces:
  - `PipelineService.markContacted(String sessionId, String candidateId, String email): PipelineView` — only from `SHORTLISTED`.
  - `PipelineService.moveToOffer(String sessionId, String candidateId, String email): PipelineView` — only from `INTERVIEWED`.
  - `PipelineService.recomputeInterviewStage(Candidate locked, String actorId): void` — package-visible, used by Tasks 3 and 4: no interviews → `CONTACTED`; any interview without feedback → `INTERVIEW_SCHEDULED`; all with feedback → `INTERVIEWED`. Writes an event only when the stage changes; never touches a candidate at `OFFER` or later.
  - `PipelineService.view(String sessionId, String candidateId, String email): PipelineView`
  - `PipelineService.board(ReviewStatus stage /*nullable*/, boolean awaitingApproval, Pageable p, String email): Page<PipelineRow>` — all sessions with `SESSION_VIEW_ALL`, otherwise the caller's own; needs `PIPELINE_MANAGE` or `OFFER_APPROVE`.
  - `record PipelineView(ReviewStatus stage, List<StageEventDto> events, List<InterviewDto> interviews, OfferDto offer /*nullable*/, boolean canManage, boolean canApprove)`. Create the `InterviewDto` and `OfferDto` records in this task, with the fields listed in Tasks 3 and 4, so the view compiles; Tasks 3 and 4 fill them.
  - `record StageEventDto(ReviewStatus stage, String changedByName, Instant changedAt)`
  - `record PipelineRow(String candidateId, String sessionId, String sessionTitle, String name, Double compositeScore, ReviewStatus stage, Instant stageSince, Instant nextInterviewAt, boolean offerUploaded)`
  - Endpoints: `GET /api/sessions/{id}/candidates/{cid}/pipeline`, `POST .../pipeline/contacted`, `POST .../pipeline/offer-stage`, `GET /api/pipeline?stage=&awaitingApproval=&page=&size=`.
  - Wrong-stage error: 409-style validation "This candidate is at <label>, so this step isn't available".

- [ ] **Step 1: Write the failing tests.**

```java
@Test void contactsOnlyFromShortlisted()               // NONE and CONTACTED -> validation error; SHORTLISTED -> CONTACTED + event + audit CANDIDATE_CONTACTED
@Test void viewerWithoutOwnershipCannotContact()       // getOwnedSession throws -> AccessDeniedException, nothing saved
@Test void recomputeFollowsTheInterviews()             // [] -> CONTACTED; [pending] -> INTERVIEW_SCHEDULED; [done, done] -> INTERVIEWED; OFFER untouched
@Test void moveToOfferNeedsInterviewed()
@Test void boardIsScopedToOwnSessionsWithoutViewAll()  // repository called with owner id filter
@Test void boardAwaitingApprovalListsUploadedUnapprovedOffers()
```

- [ ] **Step 2: Run** `mvn -pl api-service -am test -Dtest=PipelineServiceTest -Dsurefire.failIfNoSpecifiedTests=false` — expect FAIL.
- [ ] **Step 3: Implement** with each mutating method `@Transactional`, loading the candidate through `findForUpdateById`. Board query is one JPQL with the optional stage and `awaitingApproval` (offer present, `approvedAt` null) filters, sorted by `stageSince` ascending so the longest-waiting candidate is first.
- [ ] **Step 4: Run** the test, then `mvn -q verify` — expect PASS.
- [ ] **Step 5: Commit** `feat(pipeline): contact, offer stage, timeline and pipeline board`

---

### Task 3: Interviews and feedback

**Files:**
- Create: `api-service/src/main/java/com/resumescreener/api/pipeline/{InterviewService,InterviewController}.java`, `.../pipeline/dto/{ScheduleInterviewRequest,FeedbackRequest,InterviewDto,MyInterviewDto,InterviewDetailDto}.java`
- Test: `api-service/src/test/java/com/resumescreener/api/pipeline/InterviewServiceTest.java`, `.../pipeline/InterviewConcurrencyDatabaseTest.java`

**Interfaces:**
- Consumes: `PipelineService.recomputeInterviewStage`, `MinioUploadService` presign (15 minutes, as for resumes).
- Produces:
  - `record ScheduleInterviewRequest(@NotNull InterviewKind kind, @NotBlank String interviewerId, @NotNull @Future Instant scheduledAt, @Min(15) @Max(240) Integer durationMinutes, @Size(max = 500) String location, @Size(max = 2000) String noteToInterviewer)`
  - `record FeedbackRequest(@NotNull Recommendation recommendation, @NotNull @Min(1) @Max(5) Integer rating, @NotBlank @Size(min = 20, max = 5000) String assessment)`
  - `record InterviewDto(String id, InterviewKind kind, String interviewerId, String interviewerName, Instant scheduledAt, int durationMinutes, String location, String noteToInterviewer, Recommendation recommendation, Integer rating, String assessment, Instant feedbackSubmittedAt)`; feedback fields are null for viewers who are neither session readers nor the interviewer.
  - `record MyInterviewDto(String id, InterviewKind kind, Instant scheduledAt, int durationMinutes, String location, String candidateName, String sessionTitle, boolean feedbackSubmitted)`
  - `record InterviewDetailDto(InterviewDto interview, String candidateName, String sessionTitle, String jobDescription, CandidateScoreDetailDto score)`
  - `InterviewService.schedule / reschedule(String interviewId, ScheduleInterviewRequest, String email) / cancel(String interviewId, String email)` — `PIPELINE_MANAGE`, owner; allowed at `CONTACTED`, `INTERVIEW_SCHEDULED`, `INTERVIEWED`; reschedule and cancel only while no feedback.
  - `InterviewService.mine(String email): List<MyInterviewDto>` — upcoming (by time ascending), then awaiting feedback, then submitted (descending).
  - `InterviewService.detail(String interviewId, String email): InterviewDetailDto`, `resumeUrl(String interviewId, String email): String` — the caller must be the assigned interviewer, or have read access to the session.
  - `InterviewService.submitFeedback(String interviewId, FeedbackRequest, String email): InterviewDto` — assigned interviewer with `INTERVIEW_FEEDBACK`, after `scheduledAt`, once.
  - Endpoints: `POST /api/sessions/{id}/candidates/{cid}/interviews`, `PUT /api/interviews/{iid}`, `DELETE /api/interviews/{iid}`, `GET /api/interviews/mine`, `GET /api/interviews/{iid}`, `GET /api/interviews/{iid}/resume`, `POST /api/interviews/{iid}/feedback`.
  - Interviewer pick list: `GET /api/interviewers?kind=TECHNICAL` → `[{id, fullName}]`, needs `PIPELINE_MANAGE`.

- [ ] **Step 1: Write the failing tests.**

```java
@Test void schedulesAndMovesContactedToInterviewing()
@Test void refusesWrongInterviewerKind()                // HR_INTERVIEWER on TECHNICAL -> exact error copy
@Test void refusesSchedulingBeforeContactOrAtOffer()
@Test void rescheduleCanChangeInterviewer()
@Test void cannotRescheduleOrCancelAfterFeedback()
@Test void cancellingTheOnlyInterviewReturnsToContacted()
@Test void interviewerSeesOnlyOwnInterviews()           // mine() filters by caller; detail/resume of someone else's -> AccessDeniedException
@Test void feedbackOnlyAfterStartAndOnlyOnce()          // fixed Clock: before start -> "You can give feedback once the interview has started"; second submit -> "Feedback was already submitted"
@Test void feedbackNeedsPermissionAtSubmitTime()        // assigned but role changed (no INTERVIEW_FEEDBACK) -> AccessDeniedException
@Test void lastFeedbackMovesToInterviewed()
// InterviewConcurrencyDatabaseTest: two interviews, two threads submit at once -> candidate INTERVIEWED, exactly one INTERVIEWED event
```

- [ ] **Step 2: Run** `mvn -pl api-service -am test -Dtest='Interview*Test' -Dsurefire.failIfNoSpecifiedTests=false` — expect FAIL.
- [ ] **Step 3: Implement.** Every mutation locks the candidate with `findForUpdateById` before reading its interviews, then calls `recomputeInterviewStage`. `InterviewService` takes a `Clock` (setter for tests, like `AnalyticsService`).
- [ ] **Step 4: Run** the tests, then `mvn -q verify` — expect PASS.
- [ ] **Step 5: Commit** `feat(pipeline): interviews, interviewer access and feedback`

---

### Task 4: Offer upload, download and approval

**Files:**
- Create: `api-service/src/main/java/com/resumescreener/api/pipeline/{OfferService,OfferController}.java`, `.../pipeline/dto/OfferDto.java`
- Modify: `api-service/src/main/java/com/resumescreener/api/upload/MinioUploadService.java` (add `putObject(String key, byte[] bytes, String contentType)` and `deleteObject(String key)` if not present; reuse the existing presign)
- Test: `api-service/src/test/java/com/resumescreener/api/pipeline/OfferServiceTest.java`

**Interfaces:**
- Produces:
  - `record OfferDto(String originalFileName, String uploadedByName, Instant uploadedAt, String approvedByName, Instant approvedAt, boolean canApprove)`
  - `OfferService.upload(String sessionId, String candidateId, MultipartFile file, String email): OfferDto` — `PIPELINE_MANAGE`, owner, stage `OFFER`, not yet approved. Key `offers/{candidateId}/{uuid}.pdf`; the old object is deleted after the new row is committed.
  - `OfferService.downloadUrl(String sessionId, String candidateId, String email): String` — read access.
  - `OfferService.approve(String sessionId, String candidateId, String email): PipelineView` — `OFFER_APPROVE`, read access, not the uploader; stage → `OFFER_APPROVED`.
  - Endpoints: `PUT /api/sessions/{id}/candidates/{cid}/offer` (multipart `file`), `GET .../offer/download`, `POST .../offer/approve`.

- [ ] **Step 1: Write the failing tests.**

```java
@Test void uploadsOnlyAtOfferStage()
@Test void rejectsFakeAndOversizedPdfs()                // "hello" bytes named offer.pdf; 10 MB + 1 byte -> "Upload the offer as a PDF up to 10 MB"; existing offer row unchanged
@Test void replacingDeletesTheOldObject()
@Test void approvedOfferCannotBeReplaced()
@Test void uploaderCannotApprove()                      // exact copy "Someone other than the uploader must approve this offer"
@Test void approvalNeedsPermissionAndMovesStage()      // RECRUITER -> AccessDeniedException; HR_CHIEF -> OFFER_APPROVED + event + audit OFFER_APPROVE
```

- [ ] **Step 2: Run** `mvn -pl api-service -am test -Dtest=OfferServiceTest -Dsurefire.failIfNoSpecifiedTests=false` — expect FAIL.
- [ ] **Step 3: Implement** as specified; magic-byte check reads the first 5 bytes.
- [ ] **Step 4: Run** the test, then `mvn -q verify` — expect PASS.
- [ ] **Step 5: Commit** `feat(pipeline): offer upload and approval`

---

### Task 5: Candidate page pipeline panel

**Files:**
- Create: `ui/src/components/pipeline/{StageTrack,InterviewList,ScheduleInterviewForm,OfferCard}.tsx`, `ui/src/lib/pipeline.ts`, `ui/src/lib/pipeline.test.ts`
- Modify: `ui/src/types/index.ts` (`ReviewStatus` union, `Role`, `Permission`, pipeline DTOs), `ui/src/api/client.ts` (`pipelineApi`, `interviewsApi`, `offersApi`), `ui/src/lib/utils.ts` (`ROLE_LABEL` gains the two interviewer roles), `ui/src/lib/permissions.ts` (`PERMISSION_LABEL` gains "Manage pipeline", "Approve offers", "Interview feedback"), `ui/src/pages/CandidateDetail.tsx` (panel above notes), `ui/src/components/StandingsTable.tsx:75` and `ui/src/pages/CandidateDetail.tsx:116` (`inZone` = stage is not `NONE`), `ui/src/pages/SessionDetail.tsx` (a stage chip on rows past the shortlist)

**Interfaces:**
- Produces: `STAGES: { value: ReviewStatus; label: string }[]` in order; `isShortlisted(s: ReviewStatus): boolean`; `nextAction(view: PipelineView): 'contact' | 'schedule' | 'awaitFeedback' | 'moveToOffer' | 'uploadOffer' | 'approve' | 'done'`.

- [ ] **Step 1: Write the failing tests** (`pipeline.test.ts`):

```ts
expect(isShortlisted('NONE')).toBe(false);
expect(isShortlisted('OFFER')).toBe(true);
expect(nextAction(view({ stage: 'SHORTLISTED' }))).toBe('contact');
expect(nextAction(view({ stage: 'INTERVIEW_SCHEDULED', interviews: [pending] }))).toBe('awaitFeedback');
expect(nextAction(view({ stage: 'OFFER', offer: null }))).toBe('uploadOffer');
expect(nextAction(view({ stage: 'OFFER', offer: uploaded, canApprove: true }))).toBe('approve');
expect(nextAction(view({ stage: 'OFFER_APPROVED' }))).toBe('done');
```

- [ ] **Step 2: Run** `cd ui && npx vitest run src/lib/pipeline.test.ts` — expect FAIL.
- [ ] **Step 3: Implement.** `StageTrack`: seven steps as a horizontal pill track on `lg`, a vertical list under `sm`; reached steps solid, the current one ringed, saffron only on the Shortlisted step marker. Below it, one primary button chosen by `nextAction` (copy: "Mark as contacted", "Schedule interview", "Move to offer", "Upload offer", "Approve offer"), the timeline (who and when per event), the interviews (kind chip, interviewer, local date and time with time zone, location as a link when it is a URL, feedback block with recommendation chip and rating), and `OfferCard` (file name, uploaded by and when, Download, Replace, Approve or "Waiting for approval"). Buttons hide when `canManage`/`canApprove` is false. The schedule form is an inline panel: kind segmented control, interviewer select filtered by kind, `datetime-local`, duration select (15, 30, 45, 60, 90, 120, 180, 240), location, note.
- [ ] **Step 4: Run** `cd ui && npx vitest run && npm run build` — expect PASS.
- [ ] **Step 5: Commit** `feat(ui): pipeline panel on the candidate page`

---

### Task 6: Interviewer screens and Pipeline page

**Files:**
- Create: `ui/src/pages/MyInterviews.tsx`, `ui/src/pages/InterviewFeedback.tsx`, `ui/src/pages/Pipeline.tsx`, `ui/src/components/pipeline/FeedbackForm.tsx`, `ui/src/components/pipeline/FeedbackForm.test.tsx`
- Modify: `ui/src/App.tsx` (routes `/interviews` and `/interviews/:id` behind `INTERVIEW_FEEDBACK`; `/pipeline` behind `PIPELINE_MANAGE` or `OFFER_APPROVE`; `/` and `*` send users without `SESSION_RUN` and `SESSION_VIEW_ALL` but with `INTERVIEW_FEEDBACK` to `/interviews`), `ui/src/components/ProtectedRoute.tsx` (`PermissionRoute` accepts `permission: Permission | Permission[]`, any of), `ui/src/components/Navbar.tsx` (links: Sessions if `SESSION_RUN` or `SESSION_VIEW_ALL`; Pipeline; My interviews; Analytics; Admin)

**Interfaces:**
- Consumes: Task 3 and Task 2 endpoints.

- [ ] **Step 1: Write the failing tests** (`FeedbackForm.test.tsx`):

```tsx
it('requires a recommendation, a rating and 20 characters before Submit is enabled')
it('asks to confirm with "Submit feedback? You can't change it afterwards."')
it('shows the read-only feedback after a successful submit')
it('keeps everything typed when the submit fails')
```

- [ ] **Step 2: Run** `cd ui && npx vitest run src/components/pipeline/FeedbackForm.test.tsx` — expect FAIL.
- [ ] **Step 3: Implement.**
  - `MyInterviews`: three groups, "Upcoming", "Waiting for your feedback", "Done"; each row candidate name, role title, kind chip, date and time, location; empty state "No interviews assigned to you yet. They'll appear here when a recruiter schedules one."
  - `InterviewFeedback`: left column schedule and candidate summary (score with the six dimensions, strengths, gaps, "Open resume"); right column `FeedbackForm`: four recommendation options as a radio group of large pills, rating 1 to 5 as a radio group, assessment textarea with counter. Before start time the form shows "You can give feedback once the interview has started at <time>".
  - `Pipeline`: segmented filter (All, Shortlisted, Contacted, Interviewing, Interviewed, Offer, Approved) plus an "Awaiting approval" toggle; a table on `md` and cards under it: candidate, role, score, stage chip, "in stage for N days", next interview. Rows link to the candidate page.
- [ ] **Step 4: Run** `cd ui && npx vitest run && npm run build` — expect PASS.
- [ ] **Step 5: Real-stack smoke.** With Postgres, MinIO, both jars and the stub LLM: recruiter shortlists, contacts, schedules a technical and an HR interview; each interviewer signs in, sees only their candidate, gets 403 on `/api/sessions/{id}`, submits feedback; candidate reaches Interviewed; recruiter moves to offer and uploads a PDF; recruiter's own approval is refused; HR chief approves. Screenshots `docs/screenshots/f7/` of Candidate (each stage), My interviews, Interview feedback, Pipeline in dark and light at 360 and 1280.
- [ ] **Step 6: Commit** `feat(ui): my interviews, interview feedback and pipeline pages`
