# ADR-001: Centralised logging, error tracking and audit trail

**Status:** Accepted
**Date:** 2026-10-16
**Deciders:** Muhammed (owner)

## Context

Three Spring Boot services (`common`, `api-service`, `batch-worker`), a React UI, Postgres and MinIO, run by `docker-compose.yml`. Logging before this change:

- 11 log statements in the whole backend. Controllers, auth, uploads, MinIO and the LLM client logged nothing.
- `JobService` logged `e.getMessage()` only, so failed candidates had no cause.
- No request id across services, and `@Async` jobs lost MDC.
- `CandidateProcessingService` logged candidate emails.
- No record of who signed in, downloaded a resume or changed a role.
- Default console text, lost when a container was recreated.
- UI errors never reached the backend.

## Decision

Write the least code that solves it, on top of what Spring Boot and Docker already give us.

### 1. Structured logs with context
- `logstash-logback-encoder` and a shared `logback-spring.xml`: JSON to stdout under `docker`/`prod`, coloured text otherwise.
- `RequestIdFilter` reads or creates `X-Request-Id`, puts it in MDC and returns it. `JwtAuthFilter` adds `userId`.
- `BatchTriggerService` forwards `X-Request-Id` to the worker and `InternalAuthFilter` restores it. `MdcTaskDecorator` copies MDC into `@Async` threads. `JobService` adds `sessionId`, `CandidateProcessingService` adds `candidateId`.
- Levels: `ERROR` needs a human, `WARN` is handled but worth seeing, `INFO` marks business milestones.
- Log the exception object, never just the message.
- No PII: ids only, never emails, names, resume text or tokens.

### 2. Audit trail as data
- `audit_event` table: `id, occurred_at, actor_user_id, action, target_type, target_id, request_id, ip, outcome, details`.
- `AuditService.record(...)` writes the row and an INFO log.
- Recorded: sign-in success and failure, register, batch trigger, resume download, export, role change.
- Admin-only `GET /api/admin/audit`, paged, filtered by user or action.

### 3. Client errors
- `POST /api/client-errors` (no auth) logs UI errors on the server.
- `client.ts` reports `ApiRequestError` with `navigator.sendBeacon`.

### 4. Actuator
- `health`, `info`, `metrics` and `loggers` on both services, so log levels change at runtime.

### 5. Log shipping (deferred)
- JSON on stdout works with any Docker log driver. Pick the destination at deploy time through `logging.driver` in `docker-compose.yml`.

## Consequences

- **Easier:** trace a failed session by one request id, see LLM errors at once, show admins who viewed or exported what.
- **Harder:** every new feature must follow the level and no-PII rules.
- **Revisit:** log destination, Sentry if exception noise grows, retention for the audit table.
