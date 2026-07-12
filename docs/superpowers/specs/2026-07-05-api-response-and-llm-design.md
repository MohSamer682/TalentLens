# API Response and LLM Reliability Design

**Date:** 2026-07-05
**Status:** Approved for planning checkpoint
**Scope:** Backend API response contract, frontend API client adaptation, and provider-neutral LLM scoring reliability.

## Goal

Make the backend API responses predictable for the frontend and make the LLM scoring path correct, configurable, and safe to run with the API key/model/base URL supplied at runtime.

## Current Problems

The API response bodies are inconsistent. Some endpoints return raw strings, some return raw numbers, some return empty bodies, and some return maps or DTOs. The frontend has to know a different response shape for each operation, which makes error handling and data rendering brittle.

The LLM service is named `OpenAiClient` even though the app is intended to support OpenAI-compatible providers such as Gemini, DeepSeek, Qwen, Kimi, or OpenAI. It assumes `/chat/completions`, parses the model response loosely, and does not strongly validate that the returned scores are real, complete, and in range before saving them.

Secrets must not be committed. API key, model, base URL, and rate limit remain environment-driven.

## Response Contract

All API endpoints return one of two JSON shapes.

Success:

```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": {}
}
```

Failure:

```json
{
  "success": false,
  "message": "Operation failed",
  "error": {
    "code": "ERROR_CODE",
    "details": "Human-readable diagnostic details"
  }
}
```

`data` is always typed. It is never a loose map unless the value is genuinely dynamic. Void operations still return a useful data object, such as `{ "sessionId": "session-123", "status": "RUNNING" }`.

## Endpoint Data Shapes

Authentication endpoints keep their current paths but return wrapped auth data:

- `POST /api/auth/register`
- `POST /api/auth/login`

Session endpoints keep their current paths and return wrapped `SessionResponse` data:

- `POST /api/sessions`
- `GET /api/sessions`
- `GET /api/sessions/{id}`
- `PATCH /api/sessions/{id}`
- `DELETE /api/sessions/{id}`

Upload endpoints return explicit operation DTOs:

- `POST /api/sessions/{id}/jd`
  - data: `{ "sessionId": "session-123", "jdFileKey": "jd/session-123/file.pdf", "fileName": "backend-engineer-jd.pdf", "sizeBytes": 12345 }`
- `POST /api/sessions/{id}/resumes`
  - data: `{ "sessionId": "session-123", "uploadedCount": 3, "candidateIds": ["candidate-1", "candidate-2", "candidate-3"] }`

Batch endpoints return explicit state DTOs:

- `POST /api/sessions/{id}/start`
  - data: `{ "sessionId": "session-123", "status": "RUNNING" }`
- `POST /api/sessions/{id}/cancel`
  - data: `{ "sessionId": "session-123", "status": "FAILED", "message": "Cancelled by user" }`
- `GET /api/sessions/{id}/progress`
  - data: existing progress fields
- `GET /api/sessions/{id}/candidates`
  - data: page of candidate result DTOs, including composite score and five AI dimension scores
- `GET /api/sessions/{id}/candidates/{candidateId}`
  - data: candidate score detail DTO
- `GET /api/sessions/{id}/export`
  - data: `{ "sessionId": "session-123", "url": "http://localhost:9000/resumes/reports/session-123/report.xlsx", "expiresInMinutes": 15 }`

Admin endpoints return wrapped page/update responses:

- `GET /api/admin/users`
- `PATCH /api/admin/users/{id}/role`

## Error Contract

The backend should classify common failures with stable codes:

- `VALIDATION_ERROR` for file type, file size, invalid request body, or invalid weights
- `AUTHENTICATION_FAILED` for bad credentials
- `ACCESS_DENIED` for unauthorized session access
- `NOT_FOUND` for missing sessions, candidates, or scores
- `MINIO_UNAVAILABLE` for MinIO connectivity and name resolution failures
- `BATCH_WORKER_UNAVAILABLE` for internal batch trigger failures
- `LLM_CONFIGURATION_ERROR` for missing API key/base URL/model
- `LLM_PROVIDER_ERROR` for provider HTTP errors
- `LLM_RESPONSE_ERROR` for missing choices/content, malformed JSON, missing scores, or scores outside `0-100`
- `INTERNAL_ERROR` for unexpected failures

Errors should preserve a useful `message` for the frontend and safe diagnostic details for development. API keys and Authorization headers must never be included in error details or logs.

## LLM Client Design

Rename `OpenAiClient` to `LlmClient`. The class remains in the batch worker and keeps an OpenAI-compatible chat completions implementation first because the current provider list is OpenAI-compatible.

Configuration remains:

```yaml
llm:
  api-key: ${LLM_API_KEY:}
  model: ${LLM_MODEL:gpt-4o}
  base-url: ${LLM_BASE_URL:https://api.openai.com/v1}
  rate-limit-rpm: ${LLM_RATE_LIMIT_RPM:60}
```

Before every scoring call, the client validates that:

- `llm.api-key` is present
- `llm.model` is present
- `llm.base-url` is present and has no trailing `/chat/completions` duplication issue

The request body uses the configured model, the existing HR scoring prompt, and low temperature. If the provider supports OpenAI-compatible JSON mode later, it can be added behind config, but the first implementation should keep broad provider compatibility.

The response parser validates:

- response body exists
- `choices` is non-empty
- first choice has `message.content`
- content contains a JSON object
- JSON maps to `LlmScoreDto`
- all five scores are non-null integers between `0` and `100`
- `skills` is non-null, defaulting to an empty list only if the provider omitted it
- `justification`, `strengths`, and `gaps` are non-blank, otherwise replaced with safe fallback text

Invalid provider output fails the candidate with `LLM_RESPONSE_ERROR`; it must not save static fallback scores.

## Processing Behavior

Each candidate is scored by the LLM using the extracted JD text and extracted resume text. The worker persists only validated LLM scores. Composite score is still computed from the five validated dimensions and session weights.

If a candidate fails because of a provider or parsing error, that candidate is marked `FAILED`, the batch job failed count increments, and the session records the latest safe error message. Other candidates continue processing.

If all candidates fail, the session ends as `FAILED`. If at least one candidate is scored, the session can complete as `DONE` while exposing failed count in progress.

## Frontend Adaptation

The Axios client should unwrap `ApiResponse<T>` consistently. Frontend endpoint methods should return the typed `data` payload rather than exposing wrapper details everywhere.

The UI should display backend `message` fields for successful upload/start/cancel operations when needed and show backend error `message` plus `error.details` for development-friendly failures.

The frontend must not know about raw MinIO errors except through the standardized API error response.

## Testing Strategy

Backend tests:

- API wrapper DTO serializes success and error shapes.
- Global exception handler maps validation, access, MinIO, batch worker, and LLM exceptions to expected status codes and error codes.
- Upload controller returns typed `UploadJdResponse` and `UploadResumesResponse`.
- Batch controller returns typed start/cancel/export responses.
- LLM parser accepts valid OpenAI-compatible responses.
- LLM parser rejects missing choices, missing content, malformed JSON, missing score fields, and out-of-range scores.
- Candidate processing never saves fallback static scores when the LLM response is invalid.

Frontend tests or contract checks:

- API client unwraps `ApiResponse<T>`.
- Candidate results still include five AI dimension scores.
- Upload/start/export call sites read the typed data payload.

Verification commands:

```powershell
node maintenance\tests\candidate-results-contract.test.mjs
npm run build
& 'C:\Users\Muham\.m2\wrapper\dists\apache-maven-3.9.14-bin\1cb7fhup6b5n3bed6kckbrnspv\apache-maven-3.9.14\bin\mvn.cmd' test
```

Full runtime verification should use Docker Compose when Docker Desktop exposes its engine pipe:

```powershell
docker compose up -d --build
docker compose ps
```

## Non-Goals

This work does not change the database schema except where DTOs expose already stored fields. It does not commit real API keys. It does not add non-OpenAI-compatible provider protocols unless the supplied provider requires a different protocol and the user explicitly approves that expansion.

## Open Runtime Input

The user will provide:

- LLM API key
- LLM model
- LLM base URL, if different from the current `.env`

Those values will be placed in local environment configuration only and must not be added to committed documentation or examples.
