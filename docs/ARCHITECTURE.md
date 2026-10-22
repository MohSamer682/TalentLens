# Architecture

## Shape

Three services and a shared library on one Postgres database and one MinIO bucket.

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
| `common` | Feature packages: `user`, `session`, `audit`, `llm`, `portal`, `reschedule` | Shared by both services. Owns the schema in Flyway (`db/migration`). Each package holds its entities, repositories, enums and DTOs. |
| `api-service` | controller → service → repository | One package per feature: `auth`, `profile`, `session`, `jobs`, `upload`, `batch`, `candidate`, `pipeline`, `portal`, `reschedule`, `analytics`, `admin`, `audit`, `llm`. `ApiResponse` envelope, typed error codes, request ids, audit events. |
| `batch-worker` | controller → `JobService` → `CandidateProcessingService` → `LlmClient` / `PdfTextExtractor` / `ExcelReportGenerator` | `@Async` pool (2 to 4 threads, queue 25). Resilience4j retry and rate limit on LLM calls. |
| `ui` | `features/<name>` → `api/client` → zustand store in `features/auth` | Features: `auth`, `sessions`, `candidates`, `pipeline`, `portal`, `profile`, `analytics`, `admin`. Shared code in `components/ui`, `layouts`, `lib`, `types`. |

## Module rules

A feature package owns its controller, service, DTOs and, in `common`, its entities and repositories. `FeatureBoundaryTest` enforces:

1. No dependency cycles between feature packages.
2. No layer-first packages in `common` (`entity`, `repository`, `enums`, `dto`).
3. In `api-service`, features never import `config`. `config` wires features and may import them.

When a rule fails, move the code to its real owner.

### Adding a feature

1. Shared entities, repositories, enums and DTOs go in `common/<feature>`. API-only data stays in the API.
2. Add `api/<feature>` with `<Feature>Controller`, `<Feature>Service` and `dto/`. Add `batch/<feature>` if the worker needs it.
3. Call other features through their service, never their repository.
4. Add the next Flyway migration in `common/src/main/resources/db/migration`.
5. Run `mvn test`. `FeatureBoundaryTest` names any cycle.

## Sign-in and access

- Staff: `POST /api/admins/auth/login`. Candidates: `POST /api/auth/login`. The JWT audience (`staff` or `candidate`) must match the door.
- `LoginThrottle` covers both: five failures in 15 minutes returns 429 until the window passes.
- The first admin comes from `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD`, only while no active admin exists, and must change the password at first sign-in.
- Candidates hold no permissions and are refused on staff routes. Access-link tokens are hashed and single use.

### Permissions

Access follows permissions, not role names. `common.user.Permission` lists ten, three of them admin-only (`USER_MANAGE`, `ROLE_MANAGE`, `AUDIT_VIEW`).

- `roles` (V18) holds code, name, description and kind. Codes are fixed because the offer chain and interviewer picker use them. Names and descriptions are editable.
- `role_permissions` maps roles to permissions. Every change is audited and shows on the role's history page.
- `UserDetailsServiceImpl` loads permissions per request, so edits apply without a new token.
- `Permission.effective` gives `ADMIN` everything, `CANDIDATE` nothing, and strips admin-only permissions from everyone else.
- Endpoints use `@PreAuthorize("hasAuthority('...')")`. `/api/admin/**` also needs `hasRole('ADMIN')`.
- Viewing a session needs ownership or `SESSION_VIEW_ALL`. Changing it, or moving its candidates, needs ownership or admin.
- The UI reads `user.permissions` only to hide what the server would refuse, and refreshes it on load and after any 403.

## Schema choices kept on purpose

| Item | Why |
|---|---|
| `varchar` ids, not `uuid` | Ids are strings end to end. Switching touches every entity and path for little gain. Revisit past ~10M rows. |
| `double` scores | `CHECK (0..100)` bounds them (V12) and ranking uses the stored composite. |
| No `@Version` on sessions | Status moves are atomic `UPDATE … WHERE`. A version would make the worker's saves fail under contention. |
| Unpaged pipeline board | One query per table, no N+1. A large company would need paging on `/api/pipeline`. |

Closed value sets (permissions, statuses) are checked in the schema, so a new enum value needs a migration.

## Known limits

| Area | Limit | Next step |
|---|---|---|
| Scoring jobs | Job state lives in an in-memory `@Async` pool, so only one worker can run. Stuck `RUNNING` sessions fail on worker start. | Claim jobs from `batch_jobs` with `FOR UPDATE SKIP LOCKED`. |
| Shared entities | Both services map the same entities. | Give the worker its own read model if it grows. |
| Containers | Postgres, MinIO and Adminer ports are published for local use. | A `dev` Compose profile. |
| Auth | No refresh tokens, logout or password reset. | Future auth work. |
| Notifications | No email or webhook on scoring done or reschedule requests. | A `notification` feature. |
| Role names | Renamed roles show only on the roles pages. | Read names from `/api/admin/roles` everywhere. |

## Deploying migrations

V12 and V13 rename a table and drop columns. Older services can't run on the migrated database, and rollback means a restore. Deploy the migrations and all three services together.

V12 stops if existing data breaks a new rule. Run these first and fix what they return:

```sql
SELECT lower(email), count(*) FROM users GROUP BY 1 HAVING count(*) > 1;
SELECT candidate_id, count(*) FROM candidate_offers GROUP BY 1 HAVING count(*) > 1;
SELECT id FROM candidate_scores WHERE composite_score NOT BETWEEN 0 AND 100;
```

V13 copies resume text into `candidate_texts` in one transaction and locks `candidates` and `users`. Run it in a quiet window on a large database.

V18 adds the token audience, so everyone signs in again once after that release.
