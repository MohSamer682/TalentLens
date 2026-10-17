# Resume Screener

AI-assisted resume screening for recruiters. Create a screening session, upload a job description and a batch of resumes, and an LLM scores every candidate against the role. You get a ranked list with per-criterion breakdowns, short justifications, and an Excel report.

Built with Java 21 / Spring Boot 3, React, PostgreSQL and MinIO. It works with any OpenAI-compatible LLM provider (OpenAI, Gemini, DeepSeek, OpenRouter, Qwen, Kimi, Grok, ...) by changing a few environment variables.

## How it works

```
 Browser ── React UI (nginx :3000)
              │  REST + JWT
              ▼
        api-service :8080 ──HTTP + shared secret──▶ batch-worker :8081
              │                                          │
              └────────── PostgreSQL + MinIO ◀───────────┘
```

1. A recruiter creates a **session** with five scoring weights that total 100: technical skills, experience, education, soft skills, culture fit.
2. They upload the **job description** and the **resume PDFs**. Files go to MinIO.
3. Starting the session tells the **batch worker** to process candidates one by one: extract the PDF text, ask the LLM for five 0-100 scores plus a justification, strengths and gaps, then compute the weighted composite score.
4. When all candidates are done, the worker ranks them and writes an **Excel report**. The UI shows progress while this runs.

The worker retries rate limits (HTTP 429) and malformed model output, waits behind a configurable requests-per-minute limit, and marks a single candidate as failed instead of failing the whole session.

## Modules

| Module | Stack | Purpose |
|---|---|---|
| `common/` | Java 21, JPA, Flyway | Entities, repositories, DTOs and the database migrations. Shared by both services. |
| `api-service/` | Spring Boot 3.3, Spring Security, JWT | REST API: auth, sessions, uploads, results, admin, audit log. |
| `batch-worker/` | Spring Boot 3.3, Resilience4j, PDFBox, Apache POI | PDF parsing, LLM scoring, ranking, Excel report. |
| `ui/` | React, TypeScript, Vite, Tailwind | Recruiter web app. |

Design decisions and known gaps: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/adr](docs/adr). UI mockups: [docs/design/screens](docs/design/screens).

## Quick start (Docker)

You need Docker with Compose and an API key for an OpenAI-compatible LLM provider.

```bash
cp .env.example .env     # then edit .env
docker compose up -d --build
```

Set at least these in `.env`:

| Variable | What to put |
|---|---|
| `LLM_API_KEY` | Your provider key |
| `LLM_BASE_URL` / `LLM_MODEL` | Provider endpoint and model (examples in `.env.example`) |
| `LLM_RATE_LIMIT_RPM` | Requests per minute your plan allows (free tiers are often 10-20) |
| `JWT_SECRET` | A long random base64 string |
| `BATCH_WORKER_SHARED_SECRET` | A long random string; the API and worker must share it |
| `POSTGRES_PASSWORD`, `MINIO_ROOT_PASSWORD` | Change from the example values |

Then open:

| Service | URL |
|---|---|
| Web app | http://localhost:3000 |
| API | http://localhost:8080 |
| MinIO console | http://localhost:9001 |
| Adminer (DB browser) | http://localhost:8085 |

The **first account you register becomes the admin**; later accounts are recruiters. Admins manage roles from the Admin screen. To try the flow, use the fictional files in [`samples/`](samples) (`JD.pdf`, `Resume.pdf`).

## Local development

Run only the infrastructure in Docker and the apps on your machine:

```bash
docker compose up -d postgres minio
# load .env into your shell (Windows: set the same variables), then:

mvn clean verify                              # build and test all Java modules
mvn -pl api-service spring-boot:run           # terminal 1
mvn -pl batch-worker spring-boot:run          # terminal 2
cd ui && npm install && npm run dev           # terminal 3
```

`application.yml` defaults already point the database, MinIO and worker URL at `localhost`. Secrets (`JWT_SECRET`, `SPRING_DATASOURCE_PASSWORD`, `MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY`, `BATCH_WORKER_SHARED_SECRET`, `LLM_*`) have no defaults and must be set.

Tests: `mvn verify` for the backend, `cd ui && npm test` for the frontend. CI builds both on every pull request.

## API overview

All endpoints except register and login need `Authorization: Bearer <token>`. Responses use one envelope with typed error codes.

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | Create an account, get a JWT |
| POST, GET, PATCH, DELETE | `/api/sessions`, `/api/sessions/{id}` | Manage sessions and weights; the detail response includes the LLM status |
| POST | `/api/sessions/{id}/jd`, `/api/sessions/{id}/resumes` | Upload the job description and resumes (PDF) |
| POST | `/api/sessions/{id}/start`, `/api/sessions/{id}/cancel` | Start or cancel scoring |
| GET | `/api/sessions/{id}/progress` | Live progress |
| GET | `/api/sessions/{id}/candidates`, `.../candidates/{candidateId}` | Ranked results and detail |
| GET | `/api/sessions/{id}/export` | Excel report |
| GET, PATCH | `/api/admin/users`, `/api/admin/users/{id}/role` | User management (admin only) |
| GET | `/api/admin/audit` | Audit log (admin only) |

The worker's `/internal/**` endpoints require the shared secret and are not for clients.

## Switching LLM provider

Only the base URL, model and key change:

| Provider | `LLM_BASE_URL` | Example `LLM_MODEL` |
|---|---|---|
| OpenAI | `https://api.openai.com/v1` | `gpt-4o` |
| OpenRouter | `https://openrouter.ai/api/v1` | any model id from their catalog |
| Gemini (compatible API) | `https://generativelanguage.googleapis.com/v1beta/openai` | `gemini-3.5-flash` |
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` |

The model must be able to return JSON. The worker asks for `json_object` output and also extracts JSON from free-form replies, but a very small model can still produce unusable scores. The session page shows whether the configured model is reachable.

## Security notes

- No secrets are committed. `.env` is git-ignored and the apps refuse to start without their secrets.
- Passwords are hashed, admin routes require the admin role, resumes are served through presigned URLs, and sensitive actions are written to an audit log.
- Postgres, MinIO and Adminer ports are published for local development. Do not expose them on a public host: remove their `ports:` entries or firewall them.
- `docker-compose.yml` uses `bitnamilegacy/minio` because the official MinIO images are no longer pullable from Docker Hub. It gets no security updates, so use a maintained S3-compatible store in production.
- Resumes contain personal data. Before real use, decide how long to keep files and scores and check your obligations (for example GDPR).

## Known limitations

- Scoring jobs run in memory inside one worker instance. A worker restart marks running sessions as failed so they can be started again. A durable job queue is the next step for scaling out.
- Only PDF resumes are supported.
- LLM scores support a human reviewer; they are not a hiring decision. Models can be inconsistent and can reflect bias, so always read the justification.

## Contributing

Issues and pull requests are welcome. Open an issue first for larger changes, keep pull requests focused, and make sure `mvn verify` and `npm run build` pass. Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/).

## License

[MIT](LICENSE)
