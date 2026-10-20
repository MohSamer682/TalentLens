# ADR-001: Centralised logging, error tracking and audit trail

**Status:** Accepted
**Date:** 2026-10-16
**Deciders:** Muhammed (owner)

The guiding rule was to write the least code that solves the problem and lean on what Spring Boot and Docker already provide.

## Context

The Resume Screener is three Spring Boot 3.3 / Java 21 services in one Maven build (`common`, `api-service`, `batch-worker`), a React/Vite UI, Postgres and MinIO, all run by `docker-compose.yml`.

What logging looked like before this change:

- **11 log statements in the whole backend.** Controllers, auth, uploads, MinIO and the LLM client logged nothing.
- **Stack traces were thrown away in the batch worker.** `JobService` logged `e.getMessage()` only, so failed candidates left one line with no cause.
- **No way to follow one request across services.** `BatchTriggerService` called the worker with no request id, and `@Async` jobs lost MDC context.
- **PII in logs.** `CandidateProcessingService` logged candidate emails.
- **No audit trail.** Nothing recorded who logged in, downloaded a resume, or changed a role.
- **No log configuration.** Default console text, lost on container recreate.
- **UI errors invisible to the backend.**

## Decision

### 1. Structured logs with context on every line
- Added `logstash-logback-encoder` and a shared `logback-spring.xml`: JSON to stdout under the `docker`/`prod` profile, readable coloured text otherwise.
- `RequestIdFilter` reads or creates `X-Request-Id`, puts it in MDC, returns it in the response. `JwtAuthFilter` adds `userId` to MDC after auth.
- `BatchTriggerService` forwards `X-Request-Id` to the worker. `InternalAuthFilter` restores it. `MdcTaskDecorator` on the `@Async` executor copies MDC into the job thread. `JobService` adds `sessionId`, `CandidateProcessingService` adds `candidateId`.
- Levels by rule: `ERROR` = needs a human; `WARN` = handled but worth seeing (retry, 4xx from LLM); `INFO` = business milestones.
- Always log the exception object, never just `getMessage()`.
- No PII: log ids, never emails, names, resume text, or tokens.

### 2. Audit trail as data
- `audit_event` table in `common` (`id, occurred_at, actor_user_id, action, target_type, target_id, request_id, ip, outcome, details`).
- `AuditService.record(...)` writes the row and emits an INFO log.
- Recorded: login success/failure, register, batch trigger, resume download, export, role change.
- Admin-only `GET /api/admin/audit` (paged, filter by user/action).

### 3. Client error reporting
- `POST /api/client-errors` (unauthenticated) logs UI errors server-side.
- `client.ts` reports `ApiRequestError` via `navigator.sendBeacon`.

### 4. Actuator
- `health`, `info`, `metrics`, `loggers` exposed on both services so log levels can be raised at runtime.

### 5. Log shipping (deferred)
- JSON to stdout is ready for any Docker log driver or sidecar. The choice of destination (Papertrail, Loki, CloudWatch, etc.) is left to deployment time — configure `logging.driver` in `docker-compose.yml` when ready.

## Consequences

- **Easier:** answering "why did this session fail?" by searching one request id; seeing LLM errors immediately; showing admins who viewed or exported what.
- **Harder:** every new feature must follow the level and no-PII rules.
- **Revisit:** log shipping destination; Sentry if exception noise grows; data-retention policies for the audit table.
