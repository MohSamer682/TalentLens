# Architecture

## Shape

Three deployables plus one shared library, all talking through one Postgres database and one MinIO bucket.

```
 Browser ── React UI (nginx :3000)
              │  REST + JWT
              ▼
        api-service :8080 ──HTTP + X-Internal-Secret──▶ batch-worker :8081
              │                                              │
              └──────────── Postgres  +  MinIO ◀─────────────┘
                     (common: JPA entities, repositories, DTOs)
```

| Piece | Layering | Notes |
|---|---|---|
| `common` | entity, repository, enums, dto | Shared by both services. Owns the schema. |
| `api-service` | controller → service → repository | Packages by feature (`auth`, `session`, `upload`, `batch`, `admin`). Uniform `ApiResponse` envelope, typed error codes, request-id tracing, audit events. |
| `batch-worker` | controller → `JobService` → `CandidateProcessingService` → `LlmClient` / `PdfTextExtractor` / `ExcelReportGenerator` | `@Async` pool (2–4 threads, queue 25). Resilience4j retry and rate limit on the LLM call. |
| `ui` | pages → api client → zustand store | Vite, Tailwind, typed API client. |

## What is solid

- Clean module split and package-by-feature in the API.
- One response and error contract end to end, with tests (`ApiResponseContractTest`, `GlobalExceptionHandlerTest`, JS contract tests).
- LLM provider is config-only (any OpenAI-compatible base URL) and the parser validates ranges.
- Structured JSON logging with request and session ids.
- Worker-to-API auth by shared secret; resumes stay in object storage and are served by presigned URL.

## Gaps

| # | Gap | Why it matters | Fix |
|---|---|---|---|
| 1 | Cross-session read in candidate detail endpoint | Authorization hole (IDOR) | Check candidate belongs to `sessionId` |
| 2 | Secret defaults in committed `application.yml` | Forgeable admin tokens if env is missing | Remove defaults; fail on startup |
| 3 | `/actuator/**` public, `loggers` exposed, worker port published | Unauthenticated ops surface | Expose `health,info` only; drop port mapping |
| 4 | Job state lives in an in-memory `@Async` thread | Cancel is ignored, worker restart strands `RUNNING` sessions, double start duplicates scores | Worker re-checks status per candidate; atomic `PENDING→RUNNING` update; recover stale `RUNNING` on startup |
| 5 | No durable job queue | Cannot scale the worker past one instance or survive restarts | Use `batch_job` as a claim table (`SELECT … FOR UPDATE SKIP LOCKED`) before reaching for a broker |
| 6 | `ddl-auto: update` from two services, no migrations | Racy deploys, unknown prod schema | Flyway in `common`, `ddl-auto: validate` |
| 7 | Entities returned from controllers (`AuditEvent`) and shared via `common` | API shape is coupled to the table shape | DTO per endpoint; keep entities internal |
| 8 | Presigned URLs use the internal MinIO host | Downloads fail in Docker | Separate `minio.public-endpoint` |
| 9 | Weights not required to sum to 100 | Composite score can exceed 100 | Validate on `CreateSessionRequest` |
| 10 | No tests for auth, access rules, `JobService`, uploads | Security regressions go unseen | Add cross-user tests first |
| 11 | No CI, no `.github/workflows` | Nothing gates a merge | Maven verify + `npm run build` on PR |
| 12 | Containers run as root; DB, MinIO, Adminer ports published | Unsafe on a shared host | `USER`, `dev` compose profile |
| 13 | No login rate limit | Password guessing | Per-email/IP limiter |

## Readiness

| Area | Status | Reason |
|---|---|---|
| Local demo | Ready | Compose brings everything up. |
| Internal pilot (few trusted users) | Ready after gaps 1–4, 8, 9 | These are correctness or access bugs, each small. |
| Production | Not ready | Also needs 5, 6, 10–13 and a secrets plan. |

Adding features:

| Feature | Ready? | Needs first |
|---|---|---|
| New scoring criteria | Yes | A column in `ScoringWeights`/`CandidateScore`, prompt and parser line, UI slider; blocked by gap 6 for safe schema change |
| Another LLM provider | Yes | Config only if OpenAI-compatible; otherwise a second `LlmClient` behind a small interface |
| New file types (DOCX) | Yes | Extend `PdfTextExtractor` and the upload check (use magic bytes, not `Content-Type`) |
| Bigger batches or parallel sessions | No | Gaps 4, 5 |
| Multi-tenant / teams | No | Ownership is per user today; needs an org model and gap 1 fixed everywhere |
| Email or webhook on completion | Partly | Add after gap 4 so "done" is trustworthy |
| Candidate re-screen with new JD | Partly | Upload replaces and deletes data today; needs versioned runs |

## Suggested order

1. Gaps 1, 2, 3 (security, under a day).
2. Gaps 4, 8, 9 (correctness).
3. Gaps 10, 11 (tests and CI) so the rest lands safely.
4. Gap 6 (migrations), then 5 and 7 as scale or API growth demands.
