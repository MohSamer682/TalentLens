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
| `common` | feature packages: `user`, `session` (aggregate: session, weights, candidates, scores, jobs), `audit`, `llm` | Shared by both services. Owns the schema. Each package holds its entities, repositories, enums and contract DTOs. |
| `api-service` | controller → service → repository | Packages by feature (`auth`, `session`, `upload`, `batch`, `admin`, `audit`, `llm`). Uniform `ApiResponse` envelope, typed error codes, request-id tracing, audit events. |
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
| 1 | ~~Cross-session read in candidate detail endpoint~~ | Authorization hole (IDOR) | **Fixed**: `BatchController.getCandidateDetail` now checks the candidate belongs to `sessionId`; covered by `BatchControllerTest` |
| 2 | ~~Secret defaults in committed `application.yml`~~ | Forgeable admin tokens if env is missing | **Fixed on `Updates-And-Gaps-Fixes`**: no defaults for JWT, DB password, MinIO keys or the shared secret; the app fails to start without them (`.env` supplies them in Compose) |
| 3 | ~~`/actuator/**` public, `loggers` exposed, worker port published~~ | Unauthenticated ops surface | **Fixed on `Updates-And-Gaps-Fixes`**: actuator exposes `health,info` only, and only those are public; worker port no longer published |
| 4 | Job state lives in an in-memory `@Async` thread | Cancel is ignored, worker restart strands `RUNNING` sessions, double start duplicates scores | **Partly fixed**: worker re-checks status per candidate (cancel is honoured), start is an atomic `UPDATE`, stranded `RUNNING` sessions fail on worker startup. Still in-memory, single instance |
| 5 | No durable job queue | Cannot scale the worker past one instance or survive restarts | Use `batch_job` as a claim table (`SELECT … FOR UPDATE SKIP LOCKED`) before reaching for a broker |
| 6 | `ddl-auto: update` from two services, no migrations | Racy deploys, unknown prod schema | Flyway in `common`, `ddl-auto: validate` |
| 7 | Entities returned from controllers (`AuditEvent`) and shared via `common` | API shape is coupled to the table shape | DTO per endpoint; keep entities internal |
| 8 | ~~Presigned URLs use the internal MinIO host~~ | Downloads fail in Docker | **Fixed on `Updates-And-Gaps-Fixes`**: `minio.public-endpoint` (`MINIO_PUBLIC_ENDPOINT`) signs download links for the browser-reachable host |
| 9 | ~~Weights not required to sum to 100~~ | Composite score can exceed 100 | **Fixed on `Updates-And-Gaps-Fixes`**: create and update reject weights that do not total 100 |
| 10 | No tests for auth, access rules, `JobService`, uploads | Security regressions go unseen | Add cross-user tests first |
| 11 | ~~No CI, no `.github/workflows`~~ | Nothing gates a merge | **Fixed on `Updates-And-Gaps-Fixes`**: `.github/workflows/ci.yml` runs `mvn verify` and the UI build |
| 12 | Containers run as root (**api and worker now use `USER 1000`**); DB, MinIO, Adminer ports published | Unsafe on a shared host | `USER`, `dev` compose profile |
| 13 | No login rate limit | Password guessing | Per-email/IP limiter |

## Database findings

Read from the entities in `common/` (no migrations exist on `main`, so entities are the only schema record). Not yet changed.

| # | Finding | Impact | Fix |
|---|---|---|---|
| D1 | **Indexes added** for session_id (candidates, batch_jobs) and user_id (sessions); audit index still missing. No indexes on foreign keys: `candidates.session_id`, `candidate_scores.candidate_id`, `screening_sessions.user_id`, `batch_jobs.session_id`; none on `audit_event(actor_user_id, occurred_at)` | Postgres does not index FKs. Every list, rank and delete scans the table | Flyway migration with the five indexes |
| D2 | Ids are `String` UUIDs, stored as `varchar(255)` | Twice the key size, no type check | `UUID` field type, `uuid` column |
| D3 | Scores and weights are `Double` with no range or sum check | Floating error in ranking; composite can exceed 100 (gap 9) | `numeric(5,2)`, `CHECK (0..100)`, weights sum to 100 |
| D4 | `Candidate.rawText` (full resume text) loads with every `Candidate` fetch, including list queries | Large rows read to render a table | Move to a `candidate_text` table or fetch by projection |
| D5 | `ScreeningSession` cascades `ALL` + `orphanRemoval` to candidates and jobs | Deleting a session loads every candidate and its text into memory | `ON DELETE CASCADE` in the schema, delete by query |
| D6 | No `@Version` on `ScreeningSession`; API and worker both write its status | Lost updates (e.g. a cancel overwritten by the worker finishing) | Optimistic locking, or an atomic `UPDATE … WHERE status = ?` |
| D7 | **Lookups and the duplicate check are now case-insensitive**; column is still case-sensitive. `users.email` is unique but case-sensitive | `A@x.com` and `a@x.com` register as two accounts | Store lower-cased, or a unique index on `lower(email)` |
| D8 | `batch_jobs.spring_batch_job_id` but Spring Batch is not used; counters are nullable `Integer` | Dead column, null checks everywhere | Drop the column, `NOT NULL DEFAULT 0` counters |
| D9 | `audit_event` is singular, other tables plural; `actor_user_id` has no FK; `details` is free text | Inconsistent, unqueryable | Rename in migration, `jsonb` details |
| D10 | Two services run `ddl-auto: update` against one database (gap 6) | Racy schema changes | Flyway in `common`, `ddl-auto: validate` |

Branch `feature/architecture-refinement` (10 unmerged commits, in `.worktrees/`) already holds Flyway `V1`/`V2` migrations with query indexes, a `ScoreCalculator`, and runbooks. It diverged from `main` at `d4cda4f`, before the later `main` fixes. Review it before writing D1 and D10 again.

## Structural findings

| # | Finding | Fix |
|---|---|---|
| S1 | `api.batch` mixes three features: starting a job, candidate results (list, detail, resume link) and export. `BatchController` calls repositories directly | Split a `candidate` feature with its own service; controllers call services only |
| S2 | `common` is a shared database library: both services map the same entities | Acceptable now; if the worker grows, give it its own read model so one table change does not break both |
| S3 | Worker packages are partly layer-named (`web`, `config`) | Rename `web` to `job` when the next worker feature lands |
| S4 | No refresh tokens, logout, token revocation or password reset | `auth` feature work |
| S5 | No email or webhook notification on completion, no per-user limits or quotas | New `notification` feature after gap 4 |

## Modules and rules

A feature is a package that owns its controller, service, DTOs and, in `common`, its entities and repositories. Rules, enforced by `FeatureBoundaryTest` in each module:

1. Feature packages must not form a dependency cycle (`common.*`, `api.*`, `batch.*`).
2. `common` has no layer-first packages (`entity`, `repository`, `enums`, `dto`).
3. In `api-service`, features never import from `config`. `config` wires features together and may import them.

Where a rule failed when written, the fix was to move code to its real owner: `AuditService` to `api.audit`, `RequestIdFilter` to `api.common`, `SessionActionResponse` to `session.dto`, `LlmStatusClient` to `api.llm`.

### Adding a feature

1. Create `common/<feature>` for entities, repositories, enums and DTOs shared with the worker. Skip it if only one service needs the data.
2. Create `api-service/.../api/<feature>` with `<Feature>Controller`, `<Feature>Service` and `dto/`. If the worker acts on it, add `batch-worker/.../batch/<feature>`.
3. Depend on another feature through its service, never its repository. If two features need each other, put the shared type in the lower one.
4. Run `mvn test`. A failing `FeatureBoundaryTest` names the cycle.
5. Add a schema migration once gap 6 is closed.

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
