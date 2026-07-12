# Screening Bug Fixes + Delete Session Cleanup — Implementation Plan

**Status:** Task 1 DONE. Tasks 2–8 pending.
**Modules affected:** `common`, `api-service`, `batch-worker`, `.env`
**Frontend:** Zero changes needed — delete UI already wired in SessionCard + Dashboard.

---

## Phase 0: Documentation Discovery (COMPLETE)

All APIs confirmed by reading actual source files.

### Allowed APIs (verified from source)
| API | Confirmed location |
|-----|--------------------|
| `CandidateRepository` derived queries | `CandidateRepository.java` — Spring Data derives DELETE |
| `MinioClient.getObject(GetObjectArgs)` | `MinioUploadService.java:111` — existing pattern |
| `MinioClient.listObjects(ListObjectsArgs)` | MinIO SDK 8.5.11 — standard |
| `MinioClient.removeObject(RemoveObjectArgs)` | MinIO SDK 8.5.11 — standard |
| `ObjectMapper.readTree / readValue` | Jackson already on classpath, used line 84 OpenAiClient |
| `String.indexOf / lastIndexOf` | JDK |

### Anti-patterns to avoid
- Do NOT use `DownloadObjectArgs` (temp-file leak) — use `GetObjectArgs` stream instead
- Do NOT use `replaceAll("(?s)\\}.*$", "}")` — truncates at first `}`, breaks nested JSON
- Do NOT call `weights.getTechnicalSkills()` without null check
- Do NOT call `allSkills.addAll(s.getSkills())` without null check
- Do NOT create a second `BatchJob` in `BatchTriggerService` — batch-worker creates the real one

---

## Task 1 — LlmScoreDto @JsonProperty Fix ✅ DONE

`common/src/main/java/com/resumescreener/common/dto/LlmScoreDto.java` — all 9 fields have `@JsonProperty` snake_case annotations. No further work needed.

---

## Task 2 — BatchTriggerService: Remove Duplicate BatchJob + Add RUNNING Guard

**File:** `api-service/src/main/java/com/resumescreener/api/batch/BatchTriggerService.java`

**Root causes (lines confirmed by file read):**
- Lines 47–54: Creates a `BatchJob` — `JobService` in batch-worker creates a second one, doubling DB records.
- No RUNNING guard — calling `/start` twice launches two async threads producing corrupt duplicate scores.

### Changes

**Remove** `private final BatchJobRepository batchJobRepository;` field (line 21) and its import.

**Add** RUNNING guard at the top of `triggerBatch()` after session fetch:
```java
if (session.getStatus() == SessionStatus.RUNNING) {
    throw new RuntimeException("Session is already running");
}
```

**Delete** the entire `BatchJob` block (lines 47–54):
```java
// DELETE THIS BLOCK:
BatchJob job = BatchJob.builder()
        .session(session)
        .total((int) candidateCount)
        .processed(0)
        .failed(0)
        .startedAt(java.time.Instant.now())
        .build();
batchJobRepository.save(job);
```

### Final method shape
```java
@Transactional
public void triggerBatch(String sessionId) {
    ScreeningSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found"));

    if (session.getStatus() == SessionStatus.RUNNING) {
        throw new RuntimeException("Session is already running");
    }
    if (session.getJdFileKey() == null) {
        throw new RuntimeException("JD file not uploaded");
    }
    long candidateCount = candidateRepository.countBySessionId(sessionId);
    if (candidateCount == 0) {
        throw new RuntimeException("No candidates uploaded");
    }

    session.setStatus(SessionStatus.RUNNING);
    sessionRepository.save(session);

    try {
        Map<String, String> body = new HashMap<>();
        body.put("sessionId", sessionId);
        restClient.post()
                .uri(batchWorkerUrl + "/internal/jobs/start")
                .header("X-Internal-Secret", sharedSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    } catch (Exception e) {
        session.setStatus(SessionStatus.FAILED);
        sessionRepository.save(session);
        throw new RuntimeException("Failed to trigger batch: " + e.getMessage());
    }
}
```

**Verification:** `grep "batchJobRepository" api-service/src` → zero results in BatchTriggerService.

---

## Task 3 — uploadResumes: Stale Candidates + Missing Candidate Name

**Files:**
- `common/src/main/java/com/resumescreener/common/repository/CandidateRepository.java`
- `api-service/src/main/java/com/resumescreener/api/upload/MinioUploadService.java`

**Root causes (confirmed):**
- `CandidateRepository` has no `deleteBySessionId` — old candidates pile up on re-upload.
- `Candidate.builder()` in `uploadResumes` (line 83) never sets `.name()` — null names in Excel and UI.

### 3a — CandidateRepository: add deleteBySessionId
```java
void deleteBySessionId(String sessionId);
```
Spring Data auto-derives `DELETE FROM candidate WHERE session_id = ?`.

### 3b — MinioUploadService.uploadResumes: purge stale + set name

At start of method body (after `checkOwnership`, before the loop):
```java
candidateRepository.deleteBySessionId(sessionId);
```

Inside the loop, replace the `Candidate candidate = Candidate.builder()...` block:
```java
String originalName = file.getOriginalFilename();
String candidateName = (originalName != null)
        ? originalName.replaceAll("(?i)\\.pdf$", "").replace("_", " ").replace("-", " ")
        : "Unknown";

Candidate candidate = Candidate.builder()
        .session(session)
        .resumeFileKey(key)
        .name(candidateName)
        .status(CandidateStatus.PENDING)
        .build();
```

**Verification:** `grep "deleteBySessionId" common/src` and `api-service/src` both return results. `candidate.getName()` non-null in Excel output.

---

## Task 4 — MinioStorageService: Fix Temp File Leak

**File:** `batch-worker/src/main/java/com/resumescreener/batch/storage/MinioStorageService.java`

**Root cause (confirmed lines 24–31):** `DownloadObjectArgs` writes to temp file; if `readAllBytes` throws, `tempFile.delete()` never runs.

### Replacement for `downloadBytes`:
```java
public byte[] downloadBytes(String key) {
    try (InputStream stream = minioClient.getObject(
            io.minio.GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build())) {
        return stream.readAllBytes();
    } catch (Exception e) {
        throw new RuntimeException("Failed to download: " + e.getMessage());
    }
}
```

**Imports to add:** `io.minio.GetObjectArgs`, `java.io.InputStream`
**Imports to remove:** `io.minio.DownloadObjectArgs`, `java.io.File`, `java.nio.file.Files`

**Verification:** `grep "DownloadObjectArgs\|createTempFile" batch-worker/src` → zero results.

---

## Task 5 — OpenAiClient: Fix Fragile JSON Extraction

**File:** `batch-worker/src/main/java/com/resumescreener/batch/llm/OpenAiClient.java`

**Root cause (confirmed line 83):** `replaceAll("(?s)\\}.*$", "}")` truncates at first `}` — breaks the `skills` array which contains nested `]` before the outer `}`.

### Replacement for `toScoreDto()` in `LlmScoreDtoWrapper`:
```java
public LlmScoreDto toScoreDto() {
    if (choices == null || choices.isEmpty())
        throw new RuntimeException("No choices in LLM response");
    String content = choices.get(0).message.content;
    try {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            throw new RuntimeException("No JSON object found in LLM response");
        }
        String json = content.substring(start, end + 1);
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, LlmScoreDto.class);
    } catch (Exception e) {
        throw new RuntimeException("Failed to parse LLM JSON: " + e.getMessage());
    }
}
```

**Key fix:** `lastIndexOf('}')` finds the outermost closing brace — handles any nested structure.

**Verification:** `grep 'replaceAll.*\\\\}' batch-worker/src` → zero results.

---

## Task 6 — Feature: Session Delete with MinIO Cleanup + RUNNING Guard

**Files:**
- `api-service/src/main/java/com/resumescreener/api/upload/MinioUploadService.java`
- `api-service/src/main/java/com/resumescreener/api/session/SessionService.java`

**Root causes (confirmed):**
- `SessionService.deleteSession` (lines 95–98): Only calls `sessionRepository.delete(session)`. JD PDFs, resume PDFs, and Excel reports in MinIO are never cleaned up.
- No RUNNING guard — can delete a session mid-processing, leaving batch-worker writing to a ghost session.

### 6a — MinioUploadService: add deleteSessionFiles()

MinIO SDK 8.5.11 patterns: `listObjects(ListObjectsArgs)` returns `Iterable<Result<Item>>`. Each result `.get().objectName()`. Remove via `removeObject(RemoveObjectArgs)`.

Add to `MinioUploadService`:
```java
public void deleteSessionFiles(String sessionId) {
    String[] prefixes = {
        "jd/" + sessionId + "/",
        "resumes/" + sessionId + "/",
        "reports/" + sessionId + "/"
    };
    for (String prefix : prefixes) {
        Iterable<io.minio.Result<io.minio.messages.Item>> results =
            minioClient.listObjects(
                io.minio.ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .recursive(true)
                    .build());
        for (io.minio.Result<io.minio.messages.Item> result : results) {
            try {
                String objectName = result.get().objectName();
                minioClient.removeObject(
                    io.minio.RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build());
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(MinioUploadService.class)
                    .warn("Failed to delete MinIO object: {}", e.getMessage());
            }
        }
    }
}
```

**Imports to add:** `io.minio.ListObjectsArgs`, `io.minio.RemoveObjectArgs`, `io.minio.messages.Item`

### 6b — SessionService: inject MinioUploadService + RUNNING guard + cleanup

Add field (Lombok `@RequiredArgsConstructor` will inject it):
```java
private final MinioUploadService minioUploadService;
```

Add import for `MinioUploadService`.

Replace `deleteSession` method (lines 94–98):
```java
@Transactional
public void deleteSession(String id, String userEmail) {
    ScreeningSession session = getSession(id, userEmail);
    if (session.getStatus() == SessionStatus.RUNNING) {
        throw new RuntimeException("Cannot delete a session that is currently running");
    }
    minioUploadService.deleteSessionFiles(id);
    sessionRepository.delete(session);
}
```

**Note:** MinIO errors are logged but do not block DB deletion (MinIO cleanup failure should not leave orphan DB rows).

**Verification:**
- `grep "deleteSessionFiles" api-service/src` → found in both files.
- `grep "RUNNING" api-service/src/main/java/com/resumescreener/api/session/SessionService.java` → guard present.
- Frontend: `sessionsApi.delete(id)` calls `DELETE /api/sessions/{id}` — no frontend changes needed.

---

## Task 7 — .env: Fix LLM Provider Mismatch

**File:** `.env` (project root)

**Root cause (confirmed):** `LLM_API_KEY=AIzaSy...` is a Google/Gemini key. `LLM_BASE_URL=https://api.openai.com/v1` and `LLM_MODEL=gpt-4o` — every LLM call returns HTTP 401.

**Change two lines:**
```
LLM_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai
LLM_MODEL=gemini-2.0-flash
```

> Gemini supports OpenAI-compatible `/chat/completions` at this endpoint. `OpenAiClient` sends the exact same request format — no Java code changes needed.

**Verification:** After `docker compose down && docker compose up -d`, first LLM call returns 200 instead of 401.

---

## Task 8 — ExcelReportGenerator: Fix NPE Bugs + Apply Green Style

**File:** `batch-worker/src/main/java/com/resumescreener/batch/excel/ExcelReportGenerator.java`

**Root causes (confirmed by file read):**
1. Line 57: `allSkills.addAll(s.getSkills())` — `getSkills()` returns null when LLM scoring failed → NPE crashes Excel generation entirely.
2. Lines 106–122: `createConfigSheet` calls `weights.getTechnicalSkills()` etc. directly — if `weights` is null (no weights saved for session), NPE.
3. Lines 31–32: `green` style created but never applied — rank-1 row highlight is dead code.
4. Line 56: `HashSet` → non-deterministic column order in Skills Matrix.

### 8a — Null-safe skills + sorted columns (line 56–57)
```java
// Change:
Set<String> allSkills = new HashSet<>();
scores.forEach(s -> allSkills.addAll(s.getSkills()));

// To:
Set<String> allSkills = new TreeSet<>();
scores.forEach(s -> { if (s.getSkills() != null) allSkills.addAll(s.getSkills()); });
```

### 8b — Apply green style to rank-1 row (inside the scores loop in createRankingSheet)
After populating all cells for a row, add:
```java
if (score.getRank() == 1) {
    for (int i = 0; i <= 8; i++) {
        if (dataRow.getCell(i) != null) dataRow.getCell(i).setCellStyle(green);
    }
}
```

### 8c — Null guard in createConfigSheet
Replace entire method body:
```java
private void createConfigSheet(XSSFWorkbook workbook, ScoringWeights weights) {
    XSSFSheet sheet = workbook.createSheet("Session Config");
    XSSFRow header = sheet.createRow(0);
    header.createCell(0).setCellValue("Weight");
    header.createCell(1).setCellValue("Value");

    if (weights == null) {
        XSSFRow r = sheet.createRow(1);
        r.createCell(0).setCellValue("No custom weights — defaults used");
        return;
    }

    int rowNum = 1;
    for (String[] entry : new String[][]{
        {"Technical Skills", String.valueOf(weights.getTechnicalSkills())},
        {"Experience",       String.valueOf(weights.getExperience())},
        {"Education",        String.valueOf(weights.getEducation())},
        {"Soft Skills",      String.valueOf(weights.getSoftSkills())},
        {"Culture Fit",      String.valueOf(weights.getCultureFit())}
    }) {
        XSSFRow r = sheet.createRow(rowNum++);
        r.createCell(0).setCellValue(entry[0]);
        r.createCell(1).setCellValue(entry[1]);
    }
}
```

**Verification:** `grep "allSkills.addAll" batch-worker/src` → always guarded by null check. `createConfigSheet(wb, null)` does not throw.

---

## Execution Order (recommended)

| # | Task | File(s) | Reason |
|---|------|---------|--------|
| 1 | ✅ DONE | LlmScoreDto | — |
| 2 | Task 7 | `.env` | Unblocks all LLM calls immediately, no code compile needed |
| 3 | Task 2 | `BatchTriggerService.java` | Stops duplicate BatchJob pollution |
| 4 | Task 3 | `CandidateRepository.java`, `MinioUploadService.java` | Fixes stale candidates + null names |
| 5 | Task 4 | `MinioStorageService.java` (batch-worker) | Fixes temp file leak |
| 6 | Task 5 | `OpenAiClient.java` | Fixes JSON extraction |
| 7 | Task 8 | `ExcelReportGenerator.java` | Fixes NPE in report generation |
| 8 | Task 6 | `MinioUploadService.java`, `SessionService.java` | Delete feature (depends on MinioUploadService from task 4) |

---

## Final Verification Checklist

- [ ] `grep "batchJobRepository" api-service/src` → zero results in BatchTriggerService
- [ ] `grep "DownloadObjectArgs\|createTempFile" batch-worker/src` → zero results
- [ ] `grep 'replaceAll.*\\\\}' batch-worker/src` → zero results
- [ ] `grep "deleteSessionFiles" api-service/src` → found in MinioUploadService AND SessionService
- [ ] `grep "RUNNING" api-service/src/main/java/com/resumescreener/api/session/SessionService.java` → guard present in deleteSession
- [ ] `.env` `LLM_BASE_URL` contains `generativelanguage.googleapis.com`
- [ ] `grep "getSkills.*null\|null.*getSkills" batch-worker/src` → null guard present
- [ ] Docker restart → first screening reaches DONE with non-null scores in DB
- [ ] Delete a DONE session → MinIO `jd/{id}/`, `resumes/{id}/`, `reports/{id}/` all gone
- [ ] Attempt delete on RUNNING session → error returned, session intact
