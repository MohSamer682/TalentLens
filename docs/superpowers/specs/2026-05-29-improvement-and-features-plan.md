# Resume Screener — Improvement & Features Plan
**Date:** 2026-05-29  
**Status:** Active  
**Scope:** Codebase analysis, bug backlog, and new feature screens with implementation prompts

---

## 1. Codebase Analysis

### 1.1 System Overview

| Layer | Technology | Port | Status |
|-------|-----------|------|--------|
| Frontend | React 18 + TypeScript + Vite + Tailwind + Zustand | 3000 | Scaffolded, bugs present |
| API Service | Spring Boot 3.x + Spring Security JWT + JPA | 8080 | Running, gaps remain |
| Batch Worker | Spring Boot + @Async (not Spring Batch 5) | 8081 | Deviates from original spec |
| Database | PostgreSQL 16 | 5432 | Operational |
| Object Storage | MinIO | 9000 | Operational |

### 1.2 Frontend Architecture

**Pages (7 implemented):**
- `Login.tsx` — JWT auth form
- `Register.tsx` — New recruiter registration
- `Dashboard.tsx` — Session list with search/status filter + delete
- `NewSession.tsx` — 3-step wizard: Session Info → Upload Files → Configure Weights
- `SessionDetail.tsx` — Live progress polling (3s interval), ranked results, Excel download
- `CandidateDetail.tsx` — Radar chart (Recharts) + LLM justification/strengths/gaps
- `AdminUsers.tsx` — ADMIN-only user management table

**Shared Components:**
- `Navbar.tsx`, `ProtectedRoute.tsx`, `AdminRoute.tsx` (inside ProtectedRoute)
- `SessionCard.tsx`, `WeightSliders.tsx`, `RadarScoreChart.tsx`
- UI primitives: `Button`, `Input`, `Card`, `Badge`

**State:** Zustand `authStore` — JWT token + user object, persisted to localStorage  
**API:** Axios client in `ui/src/api/client.ts` with JWT interceptor  
**Types:** Centralized in `ui/src/types/index.ts`

### 1.3 Backend Architecture

**api-service** handles: auth, session CRUD, candidate management, file upload to MinIO, batch trigger  
**batch-worker** handles: async PDF text extraction (PDFBox), LLM scoring (OpenAI-compatible endpoint via Resilience4j), rank finalization  
**common** module: shared JPA entities + repositories  
**LLM scoring formula:** `(technical_skills * tw + experience * ew + education * edu_w + soft_skills * sw + culture_fit * cw) / 100`

---

## 2. Improvement Plan (Bug Backlog)

> **Audit date: 2026-05-29** — All files read in full. Status reflects actual code.

### ✅ Already Fixed (confirmed in current code)

| ID | What | Where |
|----|------|--------|
| BUG-01 | SessionCard progress hardcoded at 45% | Polls `sessionsApi.progress()` every 3s; renders `{percent}%` and `style={{ width: \`${percent}%\` }}` dynamically |
| BUG-02 | Progress ring hidden on mount | Uses `progress?.percent ?? 0` — ring renders immediately at 0% |
| BUG-05 | Delete error silently swallowed | `setDeleteError(msg)` in SessionCard; `setActionError(...)` in SessionDetail — both rendered in UI |
| BUG-06 | No cancel endpoint | `POST /sessions/{id}/cancel` exists in SessionController → SessionService.cancelSession(); frontend calls `sessionsApi.cancel()` in both SessionCard and SessionDetail |

---

### 🔴 Open Bugs — With Exact Fixes

---

#### BUG-03: Docker env vars not hot-reloaded
**File:** `.env` + running docker containers  
**Problem:** Updating `LLM_BASE_URL` or `LLM_API_KEY` in `.env` has no effect on running containers — they were started with the old values baked in. All LLM calls hit the wrong endpoint → 401 → every candidate FAILED.

**Fix (operational):**
```bash
docker compose down && docker compose up -d
```
Add to `README` or a `scripts/restart.sh`. Also add a startup log in batch-worker:

**Fix (code) — `batch-worker/.../LlmStartupValidator.java` (new file):**
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class LlmStartupValidator implements ApplicationRunner {
    @Value("${llm.base-url}") private String baseUrl;
    @Value("${llm.api-key}") private String apiKey;

    @Override
    public void run(ApplicationArguments args) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("⚠️  LLM_API_KEY is not set — all scoring will fail with 401");
            return;
        }
        log.info("LLM configured: base-url={}, model={}", baseUrl,
                 System.getProperty("llm.model", "(from env)"));
    }
}
```

---

#### BUG-04: Resilience4j ignores all 4xx — no fast-fail log on 401
**File:** `batch-worker/src/main/resources/application.yml`  
**Problem:** The config has `ignore-exceptions: HttpClientErrorException` — this is intentional (don't retry 401/422), but when `LLM_API_KEY` is wrong every candidate silently fails with no distinguishable log. The `retry-exceptions` list is correct as-is.

**Fix** — add specific logging in `OpenAiClient` when a 401 is received:
```java
// In OpenAiClient, inside the catch block or response handler:
catch (HttpClientErrorException.Unauthorized e) {
    log.error("LLM returned 401 Unauthorized — check LLM_API_KEY and LLM_BASE_URL. " +
              "Current base-url: {}", baseUrl);
    throw e; // re-throw so CandidateProcessingService marks candidate FAILED
}
```

---

#### BUG-07: Dashboard never re-polls RUNNING sessions
**File:** `ui/src/pages/Dashboard.tsx`  
**Problem:** `useEffect(() => { loadSessions(); }, [])` fires once on mount. A session card showing RUNNING never refreshes until the user navigates away and back.

**Fix** — add a second `useEffect` that polls only when needed:
```tsx
// After the existing useEffect({ loadSessions(); }, []):
useEffect(() => {
  const hasRunning = sessions.some(s => s.status === 'RUNNING');
  if (!hasRunning) return;
  const intervalId = setInterval(loadSessions, 5000);
  return () => clearInterval(intervalId);
}, [sessions]);
```
This starts a 5s poll only while there are RUNNING sessions and stops itself automatically when all finish.

---

#### BUG-08: BatchController `startBatch` has no pre-flight validation
**File:** `api-service/.../batch/BatchController.java` — `startBatch()` method  
**Problem:** Triggers the batch job with no checks. If the session has no JD file or zero candidates, the batch worker will fail with a confusing internal error instead of a clear HTTP response.

**Fix:**
```java
@PostMapping("/{id}/start")
public ResponseEntity<Map<String, String>> startBatch(
        @PathVariable String id, Authentication auth) {

    ScreeningSession session = sessionService.getSession(id, auth.getName());

    if (session.getStatus() == SessionStatus.RUNNING) {
        return ResponseEntity.status(409)
            .body(Map.of("message", "Session is already running"));
    }
    if (session.getJdFileKey() == null) {
        return ResponseEntity.unprocessableEntity()
            .body(Map.of("message", "Upload a job description before starting"));
    }
    long candidateCount = candidateRepository.countBySessionId(id);
    if (candidateCount == 0) {
        return ResponseEntity.unprocessableEntity()
            .body(Map.of("message", "Upload at least one resume before starting"));
    }

    batchTriggerService.triggerBatch(id);
    return ResponseEntity.ok().build();
}
```
Also update the return type in the existing `ResponseEntity<Void>` signature to `ResponseEntity<?>` or `ResponseEntity<Map<String, String>>`.

---

#### BUG-09: `SessionController` missing `@Valid` — no constraints fire
**File:** `api-service/.../session/SessionController.java` lines 24, 42  
**Problem:** `@RequestBody CreateSessionRequest request` has no `@Valid`. Bean Validation never runs — `@NotBlank` on title, `@Min`/`@Max` on weight fields, and any future `@SumEquals100` are all silently ignored.

**Fix — Step 1:** Add `@Valid` to both endpoints:
```java
@PostMapping
public ResponseEntity<SessionResponse> createSession(
        @Valid @RequestBody CreateSessionRequest request, Authentication auth) { ... }

@PatchMapping("/{id}")
public ResponseEntity<SessionResponse> updateSession(
        @PathVariable String id,
        @Valid @RequestBody CreateSessionRequest request, Authentication auth) { ... }
```

**Fix — Step 2:** Add `@SumEquals100` constraint to `WeightsDto` (new annotation + validator):

`common/.../validation/SumEquals100.java`:
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SumEquals100Validator.class)
public @interface SumEquals100 {
    String message() default "Scoring weights must sum to 100";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

`common/.../validation/SumEquals100Validator.java`:
```java
public class SumEquals100Validator
        implements ConstraintValidator<SumEquals100, WeightsDto> {

    @Override
    public boolean isValid(WeightsDto dto, ConstraintValidatorContext ctx) {
        if (dto == null) return true; // null handled by @NotNull if needed
        double sum = Stream.of(
                dto.getTechnicalSkills(), dto.getExperience(),
                dto.getEducation(), dto.getSoftSkills(), dto.getCultureFit())
            .filter(Objects::nonNull)
            .mapToDouble(Double::doubleValue)
            .sum();
        return Math.abs(sum - 100.0) < 0.01;
    }
}
```

Apply to `WeightsDto`:
```java
@Data
@SumEquals100  // ← add this
public class WeightsDto {
    @Min(0) @Max(100) private Double technicalSkills = 30.0;
    @Min(0) @Max(100) private Double experience      = 25.0;
    @Min(0) @Max(100) private Double education       = 15.0;
    @Min(0) @Max(100) private Double softSkills      = 20.0;
    @Min(0) @Max(100) private Double cultureFit      = 10.0;
}
```

`GlobalExceptionHandler` will automatically translate `MethodArgumentNotValidException` into a 400 with field errors if it already handles it — verify that it does.

### Priority 2 — Correctness / UX

#### BUG-07: Dashboard never re-polls running sessions
**File:** `ui/src/pages/Dashboard.tsx`  
**Problem:** Sessions loaded once on mount. A RUNNING session card stays stale until page reload.  
**Fix:** Add a `useEffect` interval (5s) that re-fetches sessions when any session has status `RUNNING`. Clear interval when no sessions are RUNNING.

#### BUG-08: BatchController missing pre-flight validation
**File:** `api-service` `BatchController`  
**Problem:** Batch can be triggered for a session with no JD or zero candidates — batch worker will fail with a confusing error.  
**Fix:** Before triggering batch: verify session has a JD file and ≥1 candidate. Return HTTP 422 with a human-readable error if not.

#### BUG-09: @SumEquals100 validator not confirmed on WeightsDto
**File:** `api-service` `WeightsDto`  
**Problem:** The 5 weight sliders must sum to 100. Custom `@SumEquals100` constraint may not be applied.  
**Fix:** Confirm `@SumEquals100` exists on `WeightsDto` and is invoked via `@Valid` in the controller. Add integration test for weights ≠ 100.

### Priority 3 — Architecture Alignment

#### ARCH-01: batch-worker uses @Async instead of Spring Batch 5
**Current:** `JobService` uses `@Async` + manual threading  
**Planned:** Spring Batch 5 with `ExtractTextStep → ScoreWithLLMStep → RankAndFinalizeStep`  
**Recommendation:** Keep @Async for now (it works). Document the deviation. Migrate to Spring Batch 5 only if retry/restart/skip semantics become needed.

#### ARCH-02: 4-sheet Excel export not confirmed implemented
**Planned:** Ranking Summary, Skills Matrix, Justifications, Session Config sheets via Apache POI XSSF  
**Action:** Verify Excel output has all 4 sheets with conditional formatting. Add test that generates Excel and asserts sheet names + row counts.

---

## 3. Features Plan — New Screens

The following new screens extend the system beyond its current scope. Each section includes a screen description and a complete implementation prompt.

---

### SCREEN-01: User Profile & Settings

**Route:** `/profile`  
**Access:** All authenticated users  
**Purpose:** Let users view and update their account info, change password, and set notification preferences.

**UI Elements:**
- Avatar/initials circle with name and role badge
- Editable fields: Full Name, Email (read-only), Password change (old → new → confirm)
- Toggle: Email notifications for session completion
- Toggle: Auto-refresh dashboard for running sessions
- Save button with inline validation

**Prompt:**
```
Build a User Profile & Settings screen for the Resume Screener app.

Route: /profile (protected, all authenticated users)
Stack: React 18 + TypeScript + Tailwind CSS + shadcn/ui + react-hook-form + zod + Zustand authStore

Layout: Two-column card layout on desktop, single column on mobile.
Left card — Profile Info:
  - Avatar circle showing user initials (derived from fullName) in indigo-600 bg
  - Display: fullName, email (read-only), role badge (RECRUITER/ADMIN in different colors)
  - Editable field: fullName with inline save (PATCH /api/users/me { fullName })
  - Show success toast on save via react-hot-toast

Right card — Security:
  - Change Password form: currentPassword, newPassword, confirmPassword
  - zod schema: newPassword min 8 chars, confirmPassword must match newPassword
  - Submit → POST /api/users/me/password { currentPassword, newPassword }
  - Show success toast; clear fields on success
  - Show backend error message inline on failure (e.g., "Current password incorrect")

Bottom card — Preferences (full width):
  - Toggle: "Email notifications when session completes" (stored in localStorage key 'prefs.emailNotify')
  - Toggle: "Auto-refresh dashboard for running sessions" (stored in localStorage key 'prefs.autoRefresh')
  - Toggles save immediately on change, no submit button needed

API endpoints needed (backend):
  - PATCH /api/users/me — update fullName
  - POST /api/users/me/password — change password (verify currentPassword against bcrypt, update hash)
  Both endpoints use SecurityContextHolder to resolve the authenticated user.

Styling: Match existing Tailwind design system. Use Card, Input, Button primitives from ui/src/components/ui/. 
Add link to /profile in the Navbar dropdown (currently shows only logout).
```

---

### SCREEN-02: Session Comparison

**Route:** `/sessions/compare?a={id}&b={id}`  
**Access:** Authenticated users (RECRUITER sees own sessions only)  
**Purpose:** Side-by-side comparison of two screening sessions — weights, top candidates, score distributions.

**UI Elements:**
- Session selector dropdowns (A and B) pre-filled from query params
- Side-by-side weight radar charts for both sessions
- Merged ranked candidate table: columns = Rank A, Rank B, Name, Score A, Score B, Delta
- Score distribution histogram for each session
- "Compare top 5" button that highlights candidates who appear in both top-5 lists

**Prompt:**
```
Build a Session Comparison screen for the Resume Screener app.

Route: /sessions/compare?a={sessionId}&b={sessionId} (protected)
Stack: React 18 + TypeScript + Tailwind + Recharts + shadcn/ui

Page structure (vertical):
1. Header row: "Compare Sessions" title + two Select dropdowns (shadcn Select) 
   pre-populated from query params ?a= and ?b=. Changing a dropdown updates the URL query param.
   Fetch all sessions from GET /api/sessions for the dropdown options.

2. Session Info Cards side by side:
   - Left: Session A name, created date, status badge, candidate count
   - Right: Session B name, created date, status badge, candidate count

3. Scoring Weights comparison (side by side RadarChart from Recharts):
   - Both sessions' weights on one radar (two filled polygon overlays, different colors + legend)
   - Dimensions: Technical Skills, Experience, Education, Soft Skills, Culture Fit

4. Candidate Overlap Table (full width):
   - Fetch GET /api/sessions/{id}/candidates for both sessions
   - Match candidates by email or fullName across sessions
   - Columns: Candidate Name | Rank in A | Score in A | Rank in B | Score in B | Rank Delta (↑↓ arrows)
   - Sort by Score A descending by default
   - Highlight row in green if candidate is in top 5 of both sessions

5. Score Distribution (side by side bar charts):
   - Histogram of composite scores bucketed into ranges: 0-20, 21-40, 41-60, 61-80, 81-100
   - One bar chart per session using Recharts BarChart

Loading state: skeleton placeholders for each section.
Error state: if either session ID is invalid, show inline error and prompt user to reselect.

Add "Compare" button on the Dashboard SessionCard (visible only when status = DONE) that 
navigates to /sessions/compare?a={currentSessionId} with B empty (user selects second session).
```

---

### SCREEN-03: Analytics & Reports Dashboard

**Route:** `/analytics`  
**Access:** ADMIN only  
**Purpose:** Aggregate hiring metrics across all sessions — usage trends, LLM cost tracking, top skills in demand, session success rates.

**UI Elements:**
- KPI cards: Total Sessions, Total Candidates Screened, Avg Score, Sessions This Month
- Line chart: Sessions created per week (last 12 weeks)
- Bar chart: Top 10 most common candidate skills extracted by LLM across all sessions
- Pie chart: Session status distribution (DONE / FAILED / RUNNING / PENDING)
- Table: Sessions with highest failure rate + failure reason
- Export to CSV button

**Prompt:**
```
Build an Analytics & Reports screen for the Resume Screener admin area.

Route: /analytics (AdminRoute-protected — redirect non-admins to /dashboard)
Stack: React 18 + TypeScript + Tailwind + Recharts + shadcn/ui

Layout: CSS Grid, responsive. Sections:

1. KPI Row (4 cards):
   - Total Sessions (all time)
   - Total Candidates Screened (sum of candidate counts across all DONE sessions)
   - Average Composite Score (mean of all candidate_scores.composite_score)
   - Sessions This Month
   Fetch from: GET /api/admin/analytics/summary → { totalSessions, totalCandidates, avgScore, thisMonth }

2. Sessions Over Time (full width LineChart):
   - X axis: last 12 weeks (week labels)
   - Y axis: session count created that week
   - Fetch from: GET /api/admin/analytics/sessions-by-week → [{ week: "2026-W01", count: 5 }, ...]
   - Use Recharts LineChart with dot markers and a tooltip

3. Top Skills in Demand (BarChart, half width):
   - Aggregate skills_extracted JSONB arrays across all candidate_scores
   - Top 10 skills by frequency
   - Fetch from: GET /api/admin/analytics/top-skills → [{ skill: "Java", count: 143 }, ...]
   - Recharts BarChart horizontal layout, sorted descending

4. Session Status Distribution (PieChart, half width):
   - Fetch from: GET /api/admin/analytics/status-distribution → [{ status, count }]
   - Recharts PieChart with legend and percentage labels

5. Failed Sessions Table (full width):
   - Columns: Session Name | Created By | Date | Candidate Count | Failure Reason
   - Fetch from: GET /api/admin/analytics/failed-sessions
   - Paginated (10 per page)

6. Export Button:
   - "Export CSV" button → GET /api/admin/analytics/export-csv → triggers file download
   - Show loading spinner on button during download

Backend endpoints to create in AdminController:
  GET /api/admin/analytics/summary
  GET /api/admin/analytics/sessions-by-week
  GET /api/admin/analytics/top-skills (aggregate skills_extracted JSONB with jsonb_array_elements)
  GET /api/admin/analytics/status-distribution
  GET /api/admin/analytics/failed-sessions
  GET /api/admin/analytics/export-csv (returns text/csv)

All endpoints require ADMIN role. Use native queries for JSONB aggregation where needed.
```

---

### SCREEN-04: Candidate Pool (Cross-Session Search)

**Route:** `/candidates`  
**Access:** RECRUITER sees own sessions' candidates; ADMIN sees all  
**Purpose:** Search and browse all candidates across all sessions without navigating into individual sessions.

**UI Elements:**
- Search bar (name / email / skill keyword)
- Filter chips: by session, by score range, by top-N rank
- Sortable table: Name, Email, Session, Score, Rank, Top Skills, Actions
- Click row → navigate to `/sessions/{sessionId}/candidate/{candidateId}`
- Bulk export selected candidates to Excel

**Prompt:**
```
Build a Candidate Pool screen — a global cross-session candidate browser.

Route: /candidates (ProtectedRoute)
Stack: React 18 + TypeScript + Tailwind + shadcn/ui + react-table (TanStack Table v8)

Page structure:

1. Header: "Candidate Pool" title + count badge showing total candidates visible

2. Search & Filter bar (sticky):
   - Text search input: filters by candidate name, email, or any skill in skills_extracted
   - Session filter: shadcn Select (multi-select) — pick one or more sessions to scope results
   - Score range: two number inputs (Min Score / Max Score, 0–100)
   - Rank filter: "Top N" Select — options: All, Top 3, Top 5, Top 10
   - Clear Filters button

3. Candidates Table (TanStack Table):
   Columns:
     - Checkbox (for bulk selection)
     - Name (link → /sessions/{sessionId}/candidate/{candidateId})
     - Email
     - Session Name (link → /sessions/{sessionId})
     - Composite Score (colored: green ≥70, yellow 40-69, red <40)
     - Rank (#1, #2, etc. — position within their session)
     - Top 3 Skills (chips from skills_extracted array)
     - Actions: eye icon (view detail)
   
   Sorting: click column header to sort. Default: Score DESC.
   Pagination: 20 rows per page with page controls.
   
   Fetch from: GET /api/candidates?search=&sessionIds=&minScore=&maxScore=&topN=&page=&size=
   Backend: Spring Data JPA repository with Specification or JPQL dynamic query.

4. Bulk Export:
   - When ≥1 row selected, show floating action bar at bottom: "X candidates selected — Export Excel"
   - POST /api/candidates/export { candidateIds: [...] } → Excel file download
   - Excel has columns: Name, Email, Session, Score, Rank, Skills, Justification

Backend endpoint to create:
  GET /api/candidates — paginated, filterable (search, sessionIds, minScore, maxScore, topN)
    - RECRUITER: automatically scoped to sessions created by authenticated user
    - ADMIN: returns all candidates
  POST /api/candidates/export — accepts list of candidate IDs, returns Excel

Use the existing CandidateDetail page for the detail view — no new page needed.
```

---

### SCREEN-05: Notifications & Activity Feed

**Route:** `/notifications` (also: bell icon in Navbar with unread badge)  
**Access:** All authenticated users  
**Purpose:** In-app notification center for session completion, failure alerts, and admin announcements.

**UI Elements:**
- Bell icon in Navbar with red badge showing unread count
- Notification dropdown (top 5 recent, "View all" link)
- Full notifications page: chronological list with read/unread state, type icons, timestamps
- Mark all as read button
- Filter: All / Unread / Session Events / System

**Prompt:**
```
Build a Notifications & Activity Feed system for the Resume Screener app.

Components to build:
  1. NotificationBell — Navbar icon with unread badge
  2. NotificationDropdown — popover with top 5 notifications
  3. NotificationsPage — full list at /notifications

Stack: React 18 + TypeScript + Tailwind + shadcn/ui Popover + date-fns

--- Backend first ---
Create a `notifications` table:
  id UUID PK, user_id UUID FK users, type VARCHAR (SESSION_DONE/SESSION_FAILED/SESSION_CANCELLED/ADMIN_MESSAGE),
  title VARCHAR(255), body TEXT, session_id UUID nullable FK sessions, is_read BOOLEAN DEFAULT false,
  created_at TIMESTAMPTZ DEFAULT now()

Create NotificationEntity JPA entity + NotificationRepository.
Create NotificationService with:
  - createSessionNotification(sessionId, userId, type) — called by batch-worker on completion/failure
  - markRead(notificationId, userId)
  - markAllRead(userId)
  - getUnreadCount(userId) → long
  - getNotifications(userId, page, size, unreadOnly) → Page<NotificationDto>

Create NotificationController:
  GET  /api/notifications?page=&size=&unreadOnly=  → Page<NotificationDto>
  GET  /api/notifications/unread-count             → { count: N }
  POST /api/notifications/{id}/read               → 204
  POST /api/notifications/read-all               → 204

--- Frontend ---
NotificationBell (add to Navbar):
  - Poll GET /api/notifications/unread-count every 30s
  - Show red badge with count if count > 0
  - On click: open NotificationDropdown

NotificationDropdown (shadcn Popover):
  - Fetch top 5 notifications (GET /api/notifications?size=5)
  - Each item: type icon (✓ green for DONE, ✗ red for FAILED, ⚠ yellow for CANCELLED)
    + title + relative time (date-fns formatDistanceToNow)
  - Unread items have indigo-50 background
  - Click item: mark as read + navigate to session if session_id present
  - Footer: "Mark all read" button + "View all notifications →" link

NotificationsPage (/notifications):
  - Full paginated list (20 per page)
  - Filter tabs: All | Unread | Session Events | System
  - "Mark all as read" button in header
  - Each row: icon, title, body preview, session link, timestamp, read indicator dot
  - Infinite scroll or pagination controls

Wire notification creation: after batch job completes or fails in batch-worker, 
call a REST endpoint POST /api/internal/notifications (internal, not user-facing) 
or use a shared service if both services share the DB.
```

---

### SCREEN-06: Session Templates

**Route:** `/templates` + modal from New Session wizard  
**Access:** All authenticated users  
**Purpose:** Save frequently used session configurations (scoring weights + default settings) as reusable templates. Apply a template in step 3 of the New Session wizard.

**UI Elements:**
- Templates list page: cards showing template name, weights breakdown, usage count
- Create template: form with name, description, 5 weight sliders (sum to 100)
- "Apply Template" button in NewSession step 3 (Configure Weights) — opens a template picker modal
- Edit / Delete template actions

**Prompt:**
```
Build a Session Templates system for the Resume Screener app.

Purpose: Users save named weight configurations (5 dimensions summing to 100) and apply them 
when creating new sessions, avoiding manual re-entry of common scoring setups.

--- Backend ---
Create `session_templates` table:
  id UUID PK, user_id UUID FK users, name VARCHAR(100) NOT NULL, description TEXT,
  w_technical INT, w_experience INT, w_education INT, w_soft_skills INT, w_culture_fit INT,
  (constraint: sum of 5 weights = 100),
  usage_count INT DEFAULT 0, created_at TIMESTAMPTZ DEFAULT now()

Create SessionTemplateEntity, SessionTemplateRepository, SessionTemplateService, SessionTemplateController:
  GET    /api/templates              → list user's templates
  POST   /api/templates              → create { name, description, weights }
  PUT    /api/templates/{id}         → update
  DELETE /api/templates/{id}         → delete (own templates only)

When a template is applied to a new session (session creation step 3), increment usage_count.
PATCH /api/templates/{id}/use → increment usage_count + 1, return 204.

--- Frontend ---
TemplatesPage (/templates):
  - Grid of TemplateCard components (name, description, mini weight bars, usage count)
  - "New Template" button → opens CreateTemplateModal
  - Each card: Edit button (opens EditTemplateModal), Delete button (confirm dialog)

CreateTemplateModal / EditTemplateModal:
  - Fields: name (required), description (optional)
  - WeightSliders component (reuse existing) with live sum-to-100 validation
  - Submit → POST or PUT /api/templates/{id}

Integration into NewSession wizard Step 3 (Configure Weights):
  - Add "Load Template" button next to the WeightSliders
  - On click: open TemplatePicker modal listing user's templates
  - Selecting a template: fills in all 5 weight sliders with template values
  - After session is successfully created: call PATCH /api/templates/{templateId}/use

Add "Templates" link in Navbar (between Dashboard and user menu).
Reuse existing WeightSliders component — do not rewrite it.
```

---

### SCREEN-07: Admin — Audit Log

**Route:** `/admin/audit-log`  
**Access:** ADMIN only  
**Purpose:** Immutable log of all significant system actions: user logins, session creation/deletion, batch triggers, admin actions, role changes.

**UI Elements:**
- Filterable table: actor email, action type, target entity, timestamp, IP address
- Date range picker filter
- Action type filter (LOGIN / LOGOUT / SESSION_CREATE / BATCH_TRIGGER / USER_ROLE_CHANGE / etc.)
- CSV export
- Row expand: full detail JSON payload

**Prompt:**
```
Build an Audit Log screen for the admin area of the Resume Screener app.

Route: /admin/audit-log (AdminRoute-protected)
Stack: React 18 + TypeScript + Tailwind + shadcn/ui + TanStack Table v8

--- Backend ---
Create `audit_logs` table:
  id UUID PK, actor_id UUID nullable FK users, actor_email VARCHAR(255),
  action VARCHAR(100) NOT NULL, entity_type VARCHAR(50), entity_id VARCHAR(255),
  detail JSONB, ip_address VARCHAR(45), created_at TIMESTAMPTZ DEFAULT now()

Create AuditLogEntity, AuditLogRepository, AuditLogService.
Create AuditLogService.log(actorEmail, action, entityType, entityId, detail, request):
  - Extract IP from HttpServletRequest (handle X-Forwarded-For header)
  - Persist asynchronously (@Async) — never block the main request

Instrument these actions by calling auditLogService.log() at each:
  - POST /auth/login (success) → action: USER_LOGIN
  - POST /auth/register → action: USER_REGISTER  
  - POST /sessions → action: SESSION_CREATE
  - DELETE /sessions/{id} → action: SESSION_DELETE
  - POST /sessions/{id}/cancel → action: SESSION_CANCEL
  - POST /sessions/{id}/batch → action: BATCH_TRIGGER
  - PUT /admin/users/{id}/role → action: USER_ROLE_CHANGE
  - DELETE /admin/users/{id} → action: USER_DELETE (if implemented)

Create AuditLogController (ADMIN only):
  GET /api/admin/audit-log?page=&size=&actorEmail=&action=&from=&to= → Page<AuditLogDto>
  GET /api/admin/audit-log/export-csv → CSV file download

--- Frontend ---
AuditLogPage:
1. Filter bar:
   - Text input: actor email (partial match)
   - Multi-select: action type filter (all action types as options)
   - Date range: from date + to date pickers (shadcn Calendar/Popover)
   - "Apply Filters" + "Clear" buttons

2. Audit Log Table (TanStack Table):
   Columns: Timestamp | Actor | Action (colored badge) | Entity Type | Entity ID | IP Address | Details
   - Timestamp: formatted as "May 29, 2026 9:14 PM"
   - Action badge colors: LOGIN=blue, SESSION_DELETE/USER_DELETE=red, BATCH_TRIGGER=purple, ROLE_CHANGE=orange, others=gray
   - "Details" column: "View →" button that expands a row detail panel showing raw JSONB as formatted JSON
   - Pagination: 25 rows per page

3. Export CSV button → GET /api/admin/audit-log/export-csv with current filters applied

Add "Audit Log" link in the Admin navigation (next to "User Management").
```

---

### SCREEN-08: Admin — System Health Dashboard

**Route:** `/admin/system`  
**Access:** ADMIN only  
**Purpose:** Real-time view of system component health: API service, batch worker, PostgreSQL, MinIO, and LLM API connectivity.

**UI Elements:**
- Status cards: each component shows UP/DOWN/DEGRADED with response time
- LLM usage meter: tokens used this month vs. estimated budget
- Batch queue: currently RUNNING sessions + duration
- Recent errors log (last 10 errors from batch worker)
- Manual "Restart Batch Worker" trigger button

**Prompt:**
```
Build a System Health Dashboard for the admin area.

Route: /admin/system (AdminRoute-protected)
Stack: React 18 + TypeScript + Tailwind + shadcn/ui + Recharts

Auto-refresh: poll all health endpoints every 15 seconds. Show "Last updated: X seconds ago" counter.

--- Backend ---
Extend Spring Boot Actuator in api-service (already included via spring-boot-starter-actuator):
  - Expose /actuator/health with component detail (DB + MinIO custom health indicator)
  - Create MinioHealthIndicator implements HealthIndicator — ping MinIO listBuckets()
  - Create LlmHealthIndicator — send a minimal test request to LLM endpoint, report UP/DOWN + latency ms

Create SystemController (ADMIN only):
  GET /api/admin/system/health → aggregate health: { api: UP, db: UP, minio: UP, llm: UP|DOWN, llmLatencyMs: N }
  GET /api/admin/system/batch-queue → list of RUNNING sessions with { sessionId, name, startedAt, candidateCount, processed }
  GET /api/admin/system/llm-usage → { tokensThisMonth: N, estimatedCostUsd: N, callsThisMonth: N }
    (requires storing token counts in a llm_usage_log table per scoring call)
  GET /api/admin/system/recent-errors → last 10 batch errors from candidate_scores where status=FAILED
  POST /api/admin/system/batch-worker/restart → calls batch-worker health reset endpoint or restarts async executor

--- Frontend ---
SystemHealthPage layout:

1. Component Status Row (5 cards):
   Each card: component icon + name + status badge (UP=green/DOWN=red/DEGRADED=yellow) + response time ms
   Components: API Service | PostgreSQL | MinIO | Batch Worker | LLM API
   Fetch from GET /api/admin/system/health

2. Batch Queue (full width table):
   Columns: Session Name | Started | Duration (running time) | Progress (processed/total)
   Fetch from GET /api/admin/system/batch-queue
   If empty: "No sessions currently processing" empty state

3. LLM Usage This Month (half width):
   - Large number: tokens used
   - Progress bar: used vs. monthly budget (hardcoded budget from config, e.g., 1M tokens)
   - Estimated cost in USD
   - Total API calls this month
   Fetch from GET /api/admin/system/llm-usage

4. Recent Errors (half width):
   - List of last 10 failed candidate scorings
   - Each: candidate name, session name, error message, timestamp
   Fetch from GET /api/admin/system/recent-errors

5. Actions:
   - "Restart Batch Executor" button → POST /api/admin/system/batch-worker/restart
   - Confirm dialog before action
   - Show success/error toast

Add "System Health" link in admin nav alongside "User Management" and "Audit Log".
```

---

## 4. Implementation Priority

> ✅ = already fixed in code, no action needed

| Priority | Item | Effort | Impact | Status |
|----------|------|--------|--------|--------|
| ✅ | BUG-01: SessionCard 45% hardcoded | — | — | Fixed |
| ✅ | BUG-02: Progress ring hidden on mount | — | — | Fixed |
| ✅ | BUG-05: Delete error swallowed | — | — | Fixed |
| ✅ | BUG-06: No cancel endpoint | — | — | Fixed |
| P0 | BUG-09: `@Valid` missing on SessionController | XS | Critical — no validation fires at all |
| P0 | BUG-09: `@SumEquals100` on WeightsDto | XS | Critical — weights can be nonsense |
| P0 | BUG-08: BatchController pre-flight checks | S | High — clear error UX before batch |
| P0 | BUG-03: Docker env restart + startup log | S | High — silent 401 kills all candidates |
| P1 | BUG-04: Log 401 clearly in OpenAiClient | XS | High — debugging speed |
| P1 | BUG-07: Dashboard poll RUNNING sessions | XS | Medium — stale progress cards |
| P2 | SCREEN-01: User Profile & Settings | M | Medium |
| P2 | SCREEN-06: Session Templates | M | High |
| P3 | SCREEN-04: Candidate Pool | L | High |
| P3 | SCREEN-02: Session Comparison | M | Medium |
| P4 | SCREEN-05: Notifications | L | Medium |
| P5 | SCREEN-03: Analytics Dashboard | L | High (Admin) |
| P5 | SCREEN-07: Audit Log | L | High (Admin) |
| P5 | SCREEN-08: System Health | M | Medium (Admin) |

---

## 5. Summary

**Current system:** Fully scaffolded full-stack app with working auth, 7 pages, and an async LLM batch pipeline. Core user journey (create session → upload → trigger batch → view results) is functional when Docker env is correct.

**Key bugs to fix first:** Progress bar hardcoding, Docker env restart, LLM retry config, and missing cancel endpoint block smooth operation.

**New screens expand the system into:** a complete recruiter productivity platform with templates for workflow efficiency, cross-session candidate search, notifications for async feedback, and an admin control center with analytics, audit, and system health monitoring.
