# Resume Screener

AI-assisted resume screening platform. Upload a job description and a batch of resumes; an LLM scores each candidate, and recruiters review ranked results with per-criterion breakdowns.

## Architecture

| Module | Stack | Purpose |
|---|---|---|
| `common/` | Java 21, Maven | Shared DTOs / contracts |
| `api-service/` | Spring Boot 3.3, JPA, JWT, MinIO | REST API: auth, sessions, uploads, admin |
| `batch-worker/` | Spring Boot 3.3, Resilience4j | Resume parsing + LLM scoring |
| `ui/` | React, TypeScript, Vite, Tailwind | Recruiter web app |
| `docker-compose.yml` | Postgres 16, MinIO | Local infrastructure |

## Repository layout

```
common/          shared Java library
api-service/     REST API
batch-worker/    scoring worker
ui/              frontend
docs/            product, design, ADRs, specs, plans (docs/design/screens = UI mockups)
maintenance/     ops logs, contract tests, fake LLM server
samples/         sample JD / resume / LLM payload fixtures
```

## Branches

- `main`: integration branch.
- `backend`: Java modules (`common`, `api-service`, `batch-worker`) and infra.
- `frontend`: `ui/` work.

## Run locally

```bash
cp .env.example .env            # fill in secrets
docker compose up -d            # Postgres + MinIO
mvn clean verify                # build and test backend
mvn -pl api-service spring-boot:run
cd ui && npm install && npm run dev
```

## Docs

[Architecture and readiness](docs/ARCHITECTURE.md) · [ADRs](docs/adr) · [UI mockups](docs/design/screens)
