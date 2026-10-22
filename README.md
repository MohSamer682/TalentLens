# TalentLens

TalentLens ranks resumes against a job and carries the shortlist through interviews, offer approval and joining. Recruiters weight the criteria, an LLM scores each resume, and every score comes with its reasons.

Shortlisted candidates get a portal to follow their application and ask to reschedule an interview.

**Stack:** Java 21, Spring Boot 3.3, React 18, TypeScript, Tailwind CSS, PostgreSQL 16, MinIO, Docker Compose. Scoring works with any OpenAI-compatible endpoint.

## Screenshots

| Ranked standings | Candidate detail |
|---|---|
| ![Session standings](<docs/design/screens/Recruiter/Session_Detail_Standings.png>) | ![Candidate detail](<docs/design/screens/Recruiter/Candidate_Detail_View.png>) |
| **Hiring pipeline** | **Recruiting analytics** |
| ![Hiring pipeline](<docs/design/screens/Admin/Hiring_Pipeline.png>) | ![Recruiting analytics](<docs/design/screens/Admin/Recruiting_Analytics.png>) |

More screens, by role, in [docs/design/screens](docs/design/screens).

## Features

- **Screening.** One session per job. Weights for skills, experience, education, soft skills and culture fit add up to 100. Upload PDFs or ZIPs, up to 1,000 resumes. Duplicates are skipped and unreadable files are listed with the reason.
- **Scoring.** Five criterion scores, a composite, strengths, gaps and a written justification per candidate. Runs in a separate worker with live progress and cancel. Exports to Excel.
- **Shortlist.** Shortlist one, a selection or the top N. Saved on the server and shared by the team. Team notes stay hidden from interviewers.
- **Pipeline.** Shortlisted, Contacted, Interviewing, Interviewed, Offer, Offer approved, Offer sent, Accepted or Declined, Joining. Every move is logged with who and when.
- **Interviews.** HR, technical and management rounds at HQ, online or elsewhere. Interviewers see only their own and submit a recommendation, a 1 to 5 rating and notes.
- **Offers.** Optional department director approval, then the HR chief. Either can reject with a note.
- **Candidate portal.** One-time access link (7 days) or a temporary password. Candidates see job details, rounds, times and places, never scores or notes. They can ask to reschedule with up to three times that suit them.
- **Accounts.** Staff sign in at `/admins`, candidates at `/login`. Five failed attempts in 15 minutes lock the account for the window. Admins manage users, roles and permissions.
- **Analytics.** KPIs, funnel, time per stage, throughput, score calibration and team tables for 30 days, 90 days or 12 months.

## Run it

You need Docker with Compose and an API key for an OpenAI-compatible provider.

```bash
git clone https://github.com/MohSamer682/TalentLens.git && cd TalentLens
cp .env.example .env        # set LLM_API_KEY, secrets and the first admin
docker compose up -d --build
```

Open http://localhost:3000/admins and sign in as the first admin.

| Service | Address |
|---|---|
| Web app | http://localhost:3000 |
| API | http://localhost:8080, docs at `/swagger-ui.html` |
| MinIO console | http://localhost:9001 |
| Adminer | http://localhost:8085 |

### Configuration

| Variable | Purpose |
|---|---|
| `LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL` | Provider key, endpoint and model. `.env.example` lists endpoints per provider. |
| `LLM_RATE_LIMIT_RPM` | Requests per minute your plan allows. Default 60. |
| `JWT_SECRET` | Base64 signing key. Required. |
| `BATCH_WORKER_SHARED_SECRET` | Shared secret between the API and the worker. |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | First admin, created only while no active admin exists. Password of 12+ characters, changed at first sign-in. Clear both afterwards. |
| `POSTGRES_*`, `MINIO_*` | Database and storage credentials. |

## Roles

Admins can rename roles and change permissions. Defaults:

| Role | Does | Permissions |
|---|---|---|
| Admin | Users, roles, audit log | All |
| HR chief | Oversight, final offer approval, read-only sessions | `SESSION_VIEW_ALL`, `CANDIDATE_REVIEW`, `OFFER_APPROVE`, `ANALYTICS_VIEW` |
| Recruiter | Runs their own sessions | `SESSION_RUN`, `CANDIDATE_REVIEW`, `PIPELINE_MANAGE` |
| HR / technical interviewer | Interviews | `INTERVIEW_FEEDBACK` |
| Department director | Management interviews, first offer approval when asked | `INTERVIEW_FEEDBACK`, `OFFER_APPROVE` |
| CEO, Chairman | Management interviews | `INTERVIEW_FEEDBACK` |
| Candidate | Portal only | None |

Only the session owner or an admin moves candidates through the pipeline.

## API

All endpoints except sign-in, sign-up and access links need `Authorization: Bearer <token>`. Every response, errors included, is `{ "success", "message", "data" }`. Full reference at `/swagger-ui.html`.

| Area | Endpoints |
|---|---|
| Sign-in | `POST /api/admins/auth/login`, `POST /api/auth/login`, `POST /api/auth/register`, `GET /api/auth/me` |
| Profile | `GET, PUT /api/me/profile`, `PUT /api/me/password` |
| Sessions | `/api/sessions` and `/{id}`: CRUD, uploads, start, cancel, progress, candidates, export |
| Review | `.../candidates/{candidateId}/review`, `/candidates/review`, `/shortlist/top`, `/notes` |
| Pipeline | `GET /api/pipeline`; per candidate: `contact`, `interview-plan`, `interviews`, `timeline`, `move-to-offer`, `offer/*`, `joining` |
| Interviews | `GET /api/my-interviews`, `GET /api/interviews/{id}`, `POST /api/interviews/{id}/feedback`, `GET /api/interviewers` |
| Portal | `.../candidates/{candidateId}/portal`, `/api/portal/invites/*`, `GET /api/portal/applications[/{id}]` |
| Reschedule | `POST /api/portal/interviews/{id}/reschedule-requests`, `.../withdraw`, `.../accept`, `.../decline` |
| Analytics | `GET /api/analytics/overview?days=30\|90\|365` |
| Admin | `/api/admin/users`, `/api/admin/roles`, `GET /api/admin/audit` |

## Development

```bash
docker compose up -d postgres minio
mvn -pl api-service spring-boot:run       # terminal 1
mvn -pl batch-worker spring-boot:run      # terminal 2
cd ui && npm install && npm run dev       # terminal 3
```

`maintenance/fake_llm_server.py` stands in for an LLM provider, so you can score without a key.

```bash
mvn clean verify                 # backend tests and module boundary checks
cd ui && npx vitest run          # UI tests
cd ui && npm run lint && npm run build
```

| Module | Holds |
|---|---|
| `common/` | Shared entities, repositories, DTOs and Flyway migrations |
| `api-service/` | REST API |
| `batch-worker/` | PDF extraction, LLM scoring, Excel export |
| `ui/` | React app, by feature under `src/features` |
| `maintenance/` | Fake LLM server and contract tests |

Design notes in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), decisions in [docs/adr](docs/adr), visual language in [ui/DESIGN.md](ui/DESIGN.md).

## Security

- Secrets come from the environment; services refuse to start without them.
- Permissions are checked on every endpoint; admin routes also need the admin role.
- Candidate accounts can't reach staff routes. Access-link tokens are stored hashed.
- Resumes and offers sit in a private bucket behind short-lived presigned links.
- Sign-ins, role changes, downloads, exports and scoring runs go to the audit log.

## License

[MIT](LICENSE)
