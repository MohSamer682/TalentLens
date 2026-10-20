# Architecture

## Shape

Three deployables and one shared library, all using one Postgres database and one MinIO bucket.

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
| `common` | Feature packages: `user`, `session` (session, weights, candidates, scores, jobs), `audit`, `llm`, `portal`, `reschedule` | Shared by both services. Owns the schema through Flyway (`db/migration`, V1 to V18). Each package holds its entities, repositories, enums and contract DTOs. |
| `api-service` | controller → service → repository | One package per feature: `auth`, `profile`, `session`, `jobs`, `upload`, `batch`, `candidate`, `pipeline`, `portal`, `reschedule`, `analytics`, `admin`, `audit`, `llm`. Uniform `ApiResponse` envelope, typed error codes, request-id tracing, audit events. |
| `batch-worker` | controller → `JobService` → `CandidateProcessingService` → `LlmClient` / `PdfTextExtractor` / `ExcelReportGenerator` | `@Async` pool (2 to 4 threads, queue 25). Resilience4j retry and rate limit on the LLM call. |
| `ui` | `features/<name>` (pages, their components and helpers) → `api/client` → zustand store in `features/auth` | Vite, Tailwind, typed API client. Features: `auth`, `sessions`, `candidates`, `pipeline`, `portal`, `profile`, `analytics`, `admin`. Shared code in `components/ui`, `layouts`, `lib`, `types`. |

## Modules and rules

A feature is a package that owns its controller, service, DTOs and, in `common`, its entities and repositories. `FeatureBoundaryTest` in each module enforces three rules:

1. Feature packages must not form a dependency cycle (`common.*`, `api.*`, `batch.*`).
2. `common` has no layer-first packages (`entity`, `repository`, `enums`, `dto`).
3. In `api-service`, features never import from `config`. `config` wires features together and may import them.

When a rule failed, the fix was to move the code to its real owner, for example `AuditService` to `api.audit` and `RequestIdFilter` to `api.common`.

### Adding a feature

1. Put entities, repositories, enums and DTOs that the worker also needs in `common/<feature>`. Skip this if only the API uses the data.
2. Add `api-service/.../api/<feature>` with `<Feature>Controller`, `<Feature>Service` and `dto/`. If the worker acts on it, add `batch-worker/.../batch/<feature>`.
3. Depend on another feature through its service, never its repository. If two features need each other, put the shared type in the lower one.
4. Add the next Flyway migration in `common/src/main/resources/db/migration`.
5. Run `mvn test`. A failing `FeatureBoundaryTest` names the cycle.

## Sign-in and access

- Staff sign in through `POST /api/admins/auth/login`, candidates through `POST /api/auth/login`. The JWT carries an audience (`staff` or `candidate`), and each sign-in accepts only its own kind of account.
- `LoginThrottle` is shared by both: after five failures for an account within 15 minutes, further attempts get 429 until the window passes.
- The first admin comes from `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD` on start-up, only while no active admin exists, and must choose a new password at first sign-in.
- Candidate accounts never hold a permission and are refused on every staff route. Portal access-link tokens are stored as hashes and work once.

### Permissions

Access is decided by permissions, not role names. `common.user.Permission` lists ten: `SESSION_RUN`, `SESSION_VIEW_ALL`, `CANDIDATE_REVIEW`, `PIPELINE_MANAGE`, `OFFER_APPROVE`, `INTERVIEW_FEEDBACK`, `ANALYTICS_VIEW`, and the admin-only `USER_MANAGE`, `ROLE_MANAGE`, `AUDIT_VIEW`.

- The `roles` table (V18) holds each role's code, display name, description and kind. Codes are fixed because the offer chain and interviewer picker depend on them; names and descriptions are editable.
- `role_permissions` maps roles to permissions. Admins edit it per role, and each change goes to the audit log, which the role's history page reads.
- `UserDetailsServiceImpl` grants `ROLE_<role>` plus the role's permissions on every request, so an edit applies on the next request without a new token.
- `Permission.effective` gives `ADMIN` every permission, gives `CANDIDATE` none, and strips admin-only ones from everyone else, whatever the table holds.
- Endpoints use `@PreAuthorize("hasAuthority('...')")`. `/api/admin/**` also requires `hasRole('ADMIN')` in `SecurityConfig`.
- Viewing a session needs ownership or `SESSION_VIEW_ALL`. Changing it, or moving its candidates through the pipeline, needs ownership or the admin role.
- The UI reads `user.permissions` only to hide what the server would refuse, and refreshes it on load and after any 403.

## Schema decisions kept on purpose

| Item | Why it stays |
|---|---|
| `varchar` ids, not `uuid` | Ids are strings end to end (JPA, DTOs, URLs). Switching touches every entity and request path for a small saving; revisit if a table passes about 10M rows. |
| `double` scores | `CHECK (0..100)` bounds them (V12), and ranking uses the stored composite, so float drift is cosmetic. |
| No `@Version` on sessions | Status moves go through atomic `UPDATE … WHERE` (`markRunning`). A version would make the worker's many `save()` calls throw on contention. |
| Pipeline board is not paged | One query per table and no N+1, but a large company would need paging on `/api/pipeline`. |

Closed value sets (permissions, statuses) are checked in the schema, so a new enum value needs a migration. Roles are rows in `roles` since V18.

## Known limits

| Area | Limit | Way forward |
|---|---|---|
| Scoring jobs | Job state lives in an in-memory `@Async` pool. Cancel is honoured per candidate, start is an atomic update, and sessions left `RUNNING` fail when the worker starts, but only one worker instance can run. | Use `batch_jobs` as a claim table (`SELECT … FOR UPDATE SKIP LOCKED`) before reaching for a broker. |
| Shared entities | Both services map the same entities from `common`. | Give the worker its own read model if it grows. |
| Containers | The API and worker run as user 1000; Postgres, MinIO and Adminer ports are published for local use. | A `dev` Compose profile for those ports. |
| Auth | No refresh tokens, logout or self-service password reset. | Future `auth` work. |
| Notifications | No email or webhook when scoring finishes or a candidate asks to reschedule. | A `notification` feature once job state is durable. |
| Role names | Renamed roles show their new name on the roles pages; other screens still use the built-in labels. | Read names from `/api/admin/roles` everywhere. |

## Deploying migrations

V12 and V13 rename a table and drop columns, so an older `api-service` or `batch-worker` can't run against the migrated database, and a rollback means restoring it. Deploy the migration and all three services together. V12 stops on purpose when existing data would break a new rule, so run these first and fix anything they return:

```sql
SELECT lower(email), count(*) FROM users GROUP BY 1 HAVING count(*) > 1;
SELECT candidate_id, count(*) FROM candidate_offers GROUP BY 1 HAVING count(*) > 1;
SELECT id FROM candidate_scores WHERE composite_score NOT BETWEEN 0 AND 100;
```

V13 copies every resume text into `candidate_texts` in one transaction and locks `candidates` and `users` while it runs, so run it in a quiet window on a large database.

Tokens issued before the separate staff sign-in carry no audience, so everyone is signed out once when that release (V18) is deployed.
