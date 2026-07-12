# Resume Screener — Improvement Plan
_Audit date: 2026-05-12_

---

## ✅ Fixed Since Last Audit (Apr 25)

| Issue | Status |
|-------|--------|
| SecurityConfig.java empty | ✅ Full JWT filter chain implemented |
| `ScreeningSession.createdAt` null constraint | ✅ Builder explicitly sets `Instant.now()` |
| `@EnableBatchProcessing` startup conflict | ✅ Annotation removed |
| `AdminController` leaks `passwordHash` | ✅ Uses `UserDto` record |
| No `CascadeType.ALL` on session relationships | ✅ All three relations have it + `orphanRemoval` |
| `RadarScoreChart` import missing file | ✅ File exists and implemented |

---

## 🔴 Bugs — Will Cause Runtime Failures

### B1 — `Candidate.rawText` truncated to VARCHAR(255)
- **File:** `common/src/main/java/com/resumescreener/common/entity/Candidate.java`
- **Problem:** No `@Column(columnDefinition = "TEXT")` — Hibernate defaults to `varchar(255)`, silently truncating extracted PDF text.
- **Effect:** LLM receives truncated resume → wrong or empty scores for all candidates.
- **Fix:**
```java
@Column(columnDefinition = "TEXT")
private String rawText;
```

---

### B2 — LazyInitializationException on GET `/sessions/{id}` for RECRUITER role
- **File:** `api-service/.../session/SessionService.java` — RECRUITER branch uses `findByIdAndUserId()` (no EntityGraph), then `SessionController.mapToResponse()` calls `session.getWeights()` outside a transaction.
- **Effect:** Crashes with `LazyInitializationException` for any recruiter fetching session detail.
- **Fix:** Add `@Transactional(readOnly = true)` to `SessionController`, or use `findWithWeightsById` for all roles:
```java
// SessionService.getSession — remove the RECRUITER branch split, always use:
return sessionRepository.findWithWeightsById(id)
    .filter(s -> user.getRole().name().equals("ADMIN") || s.getUser().getId().equals(user.getId()))
    .orElseThrow(() -> new AccessDeniedException("Not authorized"));
```

---

### B3 — `JobService.processCandidates` has no `@Transactional`
- **File:** `batch-worker/.../web/JobService.java`
- **Problem:** `@Async` runs in a new thread. Each `candidateRepository.save()` is its own transaction. If the JVM crashes mid-loop, session stays `RUNNING` forever with no recovery path.
- **Fix:** Wrap per-candidate processing in a `@Transactional` helper method. Set session to `FAILED` in an outer catch block:
```java
@Transactional
private void processCandidate(Candidate candidate, ...) { ... }
```

---

## 🟠 Medium — Wrong Behavior / Data Issues

### M1 — `CandidateDetail.tsx` uses hardcoded scoring weights
- **File:** `ui/src/pages/CandidateDetail.tsx:52–58`
- **Problem:** The `dimensions` array hardcodes `weight: 30/25/15/20/10`. Ignores actual session weights set by the recruiter.
- **Effect:** Weighted total shown in the score table is wrong if user customized weights.
- **Fix:** Include weights in `CandidateScoreDetailDto` response, or fetch the session alongside candidate detail.

---

### M2 — `BatchController.getCandidates` ignores pagination params
- **File:** `api-service/.../batch/BatchController.java`
- **Problem:** Accepts `page`/`size` from frontend but fetches all candidates via `findByCandidateSessionIdOrderByRankAsc`. FAILED candidates are also excluded (only SCORED ones returned via score repository).
- **Fix:** Add real `Pageable` to the repository query. Include failed candidates with null score fields.

---

### M3 — `reportFileKey` never sent to frontend
- **File:** `api-service/.../session/dto/SessionResponse.java`
- **Problem:** `types/index.ts` declares `reportFileKey?: string` on `ScreeningSession` but `SessionResponse.java` doesn't include it. Frontend can't know if a report exists before calling the export endpoint.
- **Fix:** Add `reportFileKey` to `SessionResponse`:
```java
private String reportFileKey;
// in mapToResponse:
session.getReportFileKey()
```

---

### M4 — `AdminController PATCH /role` allows admin self-demotion
- **File:** `api-service/.../admin/AdminController.java`
- **Problem:** An admin can patch their own role to `RECRUITER`, locking themselves (and potentially all admins) out of the system.
- **Fix:**
```java
@PatchMapping("/users/{id}/role")
public ResponseEntity<Void> updateRole(@PathVariable String id,
        @RequestBody RoleRequest request, Authentication auth) {
    User caller = userRepository.findByEmail(auth.getName()).orElseThrow();
    if (caller.getId().equals(id)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot change your own role");
    }
    ...
}
```

---

## 🟡 Low — Performance / Code Quality

### L1 — N+1 saves in `rankCandidates`
- **File:** `JobService.java` — `rankCandidates` method
- Saves each `CandidateScore` individually in a loop.
- **Fix:** `candidateScoreRepository.saveAll(scores);`

---

### L2 — Double polling on initial RUNNING session load
- **File:** `ui/src/pages/SessionDetail.tsx`
- `loadData()` calls `loadProgress()` directly when status is RUNNING, AND the `useEffect` interval also fires immediately — two simultaneous calls on mount.
- **Fix:** Remove the direct `loadProgress()` call inside `loadData`. Let the interval handle all polling.

---

### L3 — `@EnableScheduling` unused in `BatchWorkerApplication`
- **File:** `batch-worker/.../BatchWorkerApplication.java`
- No `@Scheduled` methods exist anywhere. The annotation adds a background thread pool with no purpose.
- **Fix:** Remove `@EnableScheduling`.

---

### L4 — `CandidateResultDto` type mismatch (frontend vs backend)
- **File:** `ui/src/types/index.ts`
- Frontend declares `technical, experience, education, softSkills, cultureFit` on `CandidateResultDto` but backend only returns `id, rank, name, email, compositeScore`.
- These fields are always `undefined` at runtime — dead code.
- **Fix:** Either remove them from the type, or add the score breakdown to the backend DTO.

---

## Priority Fix Order

| Priority | Issue | Impact |
|----------|-------|--------|
| 1 | B1 — rawText truncation | Breaks all LLM scoring |
| 2 | B2 — LazyInitException | Crashes session detail for recruiters |
| 3 | B3 — no @Transactional | Data stuck in RUNNING state on failure |
| 4 | M1 — hardcoded weights | Wrong score table in candidate detail |
| 5 | M3 — reportFileKey missing | Frontend can't gate export button |
| 6 | M4 — self-demotion | Security — admin lockout |
| 7 | L1–L4 | Cleanup |
