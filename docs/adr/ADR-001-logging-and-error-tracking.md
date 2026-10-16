# ADR-001: Centralised logging, error tracking and audit trail

**Status:** Proposed
**Date:** 2026-10-16
**Deciders:** Muhammed (owner); El Kabeer (architecture) and Sultan El Sokkary (review) on the hive floor

> **Assumption to confirm:** the request said "using @ponytail". Nothing named *ponytail* exists in the repo, the hive, or the installed skills, so this ADR reads it as **Papertrail** (SolarWinds' hosted log service). If you meant a different tool, only the *shipping* layer (section 3 of the Decision) changes; everything inside the app stays the same.

## Context

The Resume Screener is three Spring Boot 3.3 / Java 21 services in one Maven build (`common`, `api-service`, `batch-worker`), a React/Vite UI, Postgres and MinIO, all run by `docker-compose.yml`.

What logging looks like today (checked in the working tree):

- **11 log statements in the whole backend.** 3 in `GlobalExceptionHandler`, 7 in `batch-worker/.../web/JobService.java`, 1 in `CandidateProcessingService`. Controllers, auth, uploads, MinIO and the LLM client log nothing.
- **Stack traces are thrown away in the batch worker.** `JobService` logs `e.getMessage()` only (lines 75, 84, 95), so a failed candidate or a failed finalisation leaves one line with no cause.
- **No way to follow one request across services.** `BatchTriggerService` calls `POST /internal/jobs/start` on the worker with no request id, and `JobService.processCandidates` runs `@Async`, so worker lines cannot be tied back to the user action that started them.
- **PII in logs.** `CandidateProcessingService:31` logs the candidate's email on every candidate.
- **No audit trail ("inspections").** Nothing records who logged in, who viewed or downloaded a resume, who exported results, or what an admin changed in `AdminController`.
- **No log configuration.** No `logback-spring.xml`; both services print Spring's default console text, which Docker keeps only until the container is recreated.
- **UI errors are invisible to the backend.** `ui/src/api/client.ts` turns failures into `ApiRequestError` for the screen, but nothing is reported anywhere.
- Actuator is already on the api-service classpath; resilience4j (retry + rate limiter) wraps the LLM call in `LlmClient`.

Forces: a small team (one human, an agent floor), Docker Compose rather than Kubernetes, low volume (sessions of tens to hundreds of resumes), resumes are personal data, and the LLM is the most failure-prone dependency.

## Decision

Adopt one logging standard in-app, and ship the output to a hosted log service.

### 1. In the app: structured logs with context on every line
- **Format.** Add `logstash-logback-encoder` and a shared `logback-spring.xml`: JSON to stdout under the `docker`/`prod` profile, readable coloured text under `local`.
- **Correlation.** A servlet filter in `api-service` reads or creates `X-Request-Id`, puts it in MDC with `userId`, and returns it in the response header and in `ApiError`. `BatchTriggerService` forwards it to the worker; the worker's `InternalAuthFilter` (or a sibling filter) restores it, and a `TaskDecorator` on the `@Async` executor copies MDC into the job thread. `JobService` adds `sessionId`, and `CandidateProcessingService` adds `candidateId`, per iteration.
- **Levels, by rule.** `ERROR` = needs a human (session failed, unexpected 500, LLM permanently failing); `WARN` = handled but worth seeing (retry, defaults used, 4xx from the LLM, rejected upload); `INFO` = business milestones (session created, batch started/finished with counts and duration, report generated); `DEBUG` = payload-level detail, off by default.
- **Always log the exception object**, never just `getMessage()`, so the stack trace survives.
- **No PII or secrets.** Log ids, never emails, names, resume text, JWTs, the LLM key or the shared secret. A Logback masking rule catches emails and `Bearer …` tokens as a safety net.

### 2. Audit trail ("inspections") as data, not as log lines
- A new `audit_event` table in `common` (`id, occurred_at, actor_user_id, action, target_type, target_id, request_id, ip, outcome, details jsonb`) written through one `AuditService.record(...)`.
- Recorded actions: login success/failure, register, session create/delete, JD and resume upload, batch trigger, candidate view, resume download, results export, every admin user change.
- Each audit write also emits an `INFO` log with `"audit": true`, so the same events are searchable in the log service.
- An admin-only `GET /api/admin/audit` (paged, filter by user/action/date) so the admin dashboard screen can show it.

### 3. Shipping and error tracking: Papertrail via Docker's syslog driver
- In `docker-compose.yml`, set `logging: driver: syslog` with `syslog-address: tcp+tls://logsN.papertrailapp.com:PORT` (from `.env`, never committed) and `tag: "{{.Name}}"` on `api`, `batch-worker` and `ui`. No Java dependency is needed for shipping, and Postgres/MinIO can be added the same way.
- Error tracking is done with Papertrail **saved searches + alerts**: `level:ERROR`, `"session failed"`, LLM `HTTP 429`/`5xx` bursts, and `login failure` spikes, each alerting by email (or Slack later).
- Actuator `health`, `info`, `metrics` and `loggers` exposed on the api-service (and added to the worker) so log levels can be raised at runtime without a redeploy.
- The UI posts unexpected errors (the `ApiRequestError` path with code `INTERNAL_ERROR`/`NETWORK_ERROR`, plus a React error boundary) to a small `POST /api/client-errors` endpoint, which logs them with the request id. This keeps one place to look.

## Options considered

### Option A: Papertrail via Docker syslog driver (chosen)
| Dimension | Assessment |
|-----------|------------|
| Complexity | Low: compose config plus in-app JSON logging |
| Cost | Free tier for small volume; paid plans by GB/month |
| Scalability | Fine for this volume; search is plain-text-plus-fields, not analytics |
| Team familiarity | Minimal setup; web search and alerts work out of the box |

**Pros:** no new Java dependency for shipping; works for every container, including Postgres and MinIO; live tail is handy while debugging a batch run.
**Cons:** resumes' metadata leaves your machine (so the no-PII rule is mandatory); weak at grouping exceptions compared with a real error tracker; retention is short on the free tier.

### Option B: Logback appender inside each service (e.g. a syslog or HTTP appender to the same provider)
| Dimension | Assessment |
|-----------|------------|
| Complexity | Medium: config in each service, plus buffering on network failure |
| Cost | Same provider cost |
| Scalability | Same |
| Team familiarity | Familiar Java config |

**Pros:** works outside Docker (e.g. running from IntelliJ).
**Cons:** a log-service outage can block or drop app logs; every service needs the config; the UI and infrastructure containers are not covered.

### Option C: Self-hosted Grafana Loki + Promtail (or OpenSearch) in compose
| Dimension | Assessment |
|-----------|------------|
| Complexity | High: three more containers, storage, dashboards to build |
| Cost | Free software, paid in RAM, disk and upkeep |
| Scalability | Strong |
| Team familiarity | New stack to learn |

**Pros:** data never leaves your machine; long retention; dashboards.
**Cons:** the most work to run, and it is one more thing that can break on a laptop setup.

### Option D: Add Sentry for exceptions (complement, not replacement)
**Pros:** best-in-class exception grouping, release tracking, and React error capture.
**Cons:** a second tool and SDK in three places. Deferred: revisit if Papertrail alerts prove too noisy to manage errors.

## Trade-off analysis

The real gap is not the destination, it is that the app emits almost nothing useful and can't connect a user action to the worker job it starts. Sections 1 and 2 fix that and are worth doing whichever destination is chosen. For the destination, Papertrail through Docker's driver gives searchable, alertable, cross-service logs for the least effort, at the cost of sending data off-machine and weaker exception grouping. Loki keeps data local but costs more to run than the app itself at this size. Keeping the audit trail in Postgres rather than only in logs means it survives log retention limits and can be shown in the admin UI.

## Consequences

- **Easier:** answering "why did this session fail?" by searching one request id; seeing a broken LLM key or rate limit within minutes from an alert; showing admins who viewed or exported what.
- **Harder:** every new feature must follow the level and no-PII rules (add both to the review checklist Sultan El Sokkary uses); one more secret (`PAPERTRAIL_HOST`/`PORT`) in `.env`.
- **Revisit:** retention needs if this is used with real candidates (data-protection rules may cap how long audit data is kept); Sentry if exception noise grows; Loki if data must stay on-prem.

## Action items

1. [ ] Add `logstash-logback-encoder` and a shared `logback-spring.xml` (JSON for `docker`, text for `local`), with the email/token masking rule.
2. [ ] Add the request-id filter in `api-service`, forward `X-Request-Id` in `BatchTriggerService`, restore it in the worker, and add an MDC-copying `TaskDecorator` for `@Async`.
3. [ ] Fix existing statements: log exception objects in `JobService`, replace the email in `CandidateProcessingService:31` with `candidateId`, add `sessionId`/`candidateId` MDC.
4. [ ] Add INFO/WARN/ERROR statements at the milestones above, including retries and rate-limit waits in `LlmClient`.
5. [ ] Add the `audit_event` entity, `AuditService`, calls at each listed action, and `GET /api/admin/audit`.
6. [ ] Add `POST /api/client-errors` and wire `client.ts` plus a React error boundary to it.
7. [ ] Expose Actuator `health, info, metrics, loggers` on both services (behind admin auth).
8. [ ] Configure the Docker syslog driver to Papertrail in `docker-compose.yml` using `.env` values; add the placeholders to `.env.example`.
9. [ ] Create the Papertrail saved searches and alerts listed in section 3.
10. [ ] Tests: MDC propagation across the async boundary, audit rows written for each action, and a test that no email appears in log output.
