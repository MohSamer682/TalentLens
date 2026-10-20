# TalentLens

TalentLens screens resumes against a job description, ranks the candidates, and then carries the shortlist through interviews, offer approval and joining. Recruiters set how much each criterion matters, an LLM scores every resume against those weights, and the team works from one ranked board where each score comes with its reasons.

Shortlisted candidates get their own portal, where they follow their application, see their interviews and ask to move one if the time doesn't work.

**Stack:** Java 21, Spring Boot 3.3, React 18 with TypeScript and Tailwind CSS, PostgreSQL 16, MinIO, Docker Compose. Scoring works with any OpenAI-compatible endpoint (OpenAI, Gemini, DeepSeek, OpenRouter, Qwen, Kimi, Grok).

## Screenshots

More screens, grouped by role, are in [docs/design/screens](docs/design/screens).

| Ranked standings | Candidate detail |
|---|---|
| ![Session standings](<docs/design/screens/Recruiter/Session_Detail_Standings.png>) | ![Candidate detail](<docs/design/screens/Recruiter/Candidate_Detail_View.png>) |
| **Hiring pipeline** | **Recruiting analytics** |
| ![Hiring pipeline](<docs/design/screens/Admin/Hiring_Pipeline.png>) | ![Recruiting analytics](<docs/design/screens/Admin/Recruiting_Analytics.png>) |
| **New session** | **My interviews** |
| ![New session](<docs/design/screens/Recruiter/New_Session_Wizard.png>) | ![My interviews](<docs/design/screens/Technical_Interviewer/My_Interviews.png>) |
| **Roles and permissions** | **Sign in** |
| ![Roles and permissions](<docs/design/screens/Admin/Admin_Roles_and_Permissions.png>) | ![Sign in](<docs/design/screens/00_Login_Screen.png>) |

## What it does

**Screening**
- A session holds one job: title, position code, description, required skills and a PDF job description.
- Weights for technical skills, experience, education, soft skills and culture fit must add up to 100.
- Upload PDFs or ZIP archives, up to 1,000 resumes per session. Duplicates are skipped, and a resume that can't be read is listed with its reason instead of failing the run.
- Each candidate gets five criterion scores, a composite, strengths, gaps, extracted skills and a written justification.
- Scoring runs in a separate worker with live progress and cancel. By default only new resumes are scored; "Re-score all" is there for when the weights change.
- The ranked list exports to Excel.

**Shortlist and review**
- Shortlist one candidate, a selection, or the top N. The shortlist is saved on the server and shared by everyone on the session.
- Team notes on each candidate, hidden from interviewers.

**Hiring pipeline**
- Stages: Shortlisted, Contacted, Interviewing, Interviewed, Offer, Offer approved, Offer sent, Accepted or Declined, Joining.
- The recruiter plans which rounds a candidate needs (HR, technical, management) and books each one at HQ, online with a meeting link, or somewhere else.
- Interviewers see only their own interviews and submit a recommendation, a 1 to 5 rating and a written assessment.
- Offers go to the department director when the recruiter asks for that step, then to the HR chief. Either can reject with a note. After approval the offer is marked sent, the candidate's answer is recorded, and the joining date follows.
- Every step lands on the candidate's timeline with who did it and when.

**Candidate portal**
- HR sends a shortlisted candidate a one-time access link (valid for 7 days) or creates the account with a temporary password.
- Candidates see each application's job details, interview rounds, times in their own time zone, and the place or meeting link. Scores, feedback and internal notes stay hidden.
- A candidate can ask to reschedule an upcoming interview with a reason and up to three times that suit them. HR accepts one of those times, picks another, or declines with a note.

**Accounts and roles**
- Staff sign in at `/admins`, candidates at `/login`. Each page accepts only its own kind of account, and both lock out after five failed attempts in 15 minutes.
- Public sign-up at `/register` creates candidate accounts. Staff accounts are created by an admin.
- Everyone has a profile page and can change their password. Temporary passwords must be replaced at the next sign-in.
- Admins create, edit, hide and delete users, rename roles, set each role's permissions, move people between roles and read each role's change history.

**Analytics**
- KPIs compared with the previous period, the funnel from upload to approval with the biggest drop called out, median and p90 time per stage, weekly throughput, score calibration by band, and interviewer and recruiter tables. Views for 30 days, 90 days and 12 months.

## How it fits together

```text
 Browser ── React UI (nginx :3000)
              │  REST + JWT
              ▼
         api-service :8080 ── HTTP + shared secret ──▶ batch-worker :8081
              │                                            │
              └──────────── PostgreSQL 16 + MinIO ◀────────┘
```

| Module | What lives there |
|---|---|
| `common/` | JPA entities, repositories and DTOs shared by both services, plus the Flyway migrations (V1 to V18). |
| `api-service/` | The REST API: auth, sessions, uploads, candidates, pipeline, portal, reschedule requests, profiles, analytics and admin. |
| `batch-worker/` | PDF text extraction, LLM scoring with retries and rate limiting, and the Excel report. |
| `ui/` | The React app, grouped by feature under `src/features`. Light and dark themes. |
| `maintenance/` | A fake LLM server for local runs, and contract tests for the API wrapper and LLM client. |

Design notes are in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), decisions in [docs/adr](docs/adr), and the visual language in [ui/DESIGN.md](ui/DESIGN.md).

## Running it

You need Docker with Compose and an API key for an OpenAI-compatible provider.

```bash
git clone https://github.com/MohSamer682/TalentLens.git && cd TalentLens
cp .env.example .env        # set LLM_API_KEY, secrets and the first admin
docker compose up -d --build
```

Then open http://localhost:3000/admins and sign in as the first admin.

| Service | Address |
|---|---|
| Web app | http://localhost:3000 |
| API | http://localhost:8080 (OpenAPI at `/swagger-ui.html`) |
| MinIO console | http://localhost:9001 |
| Adminer | http://localhost:8085 |

### Configuration

| Variable | Purpose |
|---|---|
| `LLM_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL` | Provider key, endpoint and model. `.env.example` lists the endpoint for each provider. |
| `LLM_RATE_LIMIT_RPM` | Requests per minute your plan allows (default 60). |
| `JWT_SECRET` | Base64 key for signing tokens. The app won't start without it. |
| `BATCH_WORKER_SHARED_SECRET` | Secret the API and the worker use to trust each other. |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | The first admin, created on start-up while no active admin exists. The password needs at least 12 characters and must be changed at first sign-in. Clear both once you're in. |
| `POSTGRES_*`, `MINIO_*` | Database and object storage credentials. |

## Roles

Admins can rename roles and change their permissions on the roles page. A fresh install starts with these:

| Role | Used for | Default permissions |
|---|---|---|
| Admin | Users, roles and the audit log | All of them, always |
| HR chief | Oversight across recruiters and the final offer approval; views sessions without running them | `SESSION_VIEW_ALL`, `CANDIDATE_REVIEW`, `OFFER_APPROVE`, `ANALYTICS_VIEW` |
| Recruiter | Owns the sessions they start | `SESSION_RUN`, `CANDIDATE_REVIEW`, `PIPELINE_MANAGE` |
| HR interviewer | HR interviews | `INTERVIEW_FEEDBACK` |
| Technical interviewer | Technical interviews | `INTERVIEW_FEEDBACK` |
| Department director | Management interviews and the first offer approval when asked for | `INTERVIEW_FEEDBACK`, `OFFER_APPROVE` |
| CEO, Chairman | Management interviews | `INTERVIEW_FEEDBACK` |
| Candidate | The candidate portal | None |

Only the recruiter who owns a session, or an admin, can move its candidates through the pipeline. Others who can open the session see the pipeline read-only.

## API

Every endpoint except sign-in, sign-up and the access-link pages needs `Authorization: Bearer <token>`. Every response, errors included, uses the same envelope: `{ "success": ..., "message": ..., "data": ... }`. The full reference is served at `/swagger-ui.html`.

| Area | Endpoints |
|---|---|
| Sign-in | `POST /api/admins/auth/login` (staff), `POST /api/auth/login` and `/register` (candidates), `GET /api/auth/me` |
| Profile | `GET, PUT /api/me/profile`, `PUT /api/me/password` |
| Sessions | `/api/sessions` and `/{id}`: create, list, update, delete, upload JD and resumes, start, cancel, progress, candidates, export |
| Review | `/api/sessions/{id}/candidates/{candidateId}/review`, `/candidates/review` (bulk), `/shortlist/top`, `/notes` |
| Pipeline | `GET /api/pipeline`, and under `/api/sessions/{id}/candidates/{candidateId}`: `contact`, `interview-plan`, `interviews`, `timeline`, `move-to-offer`, `offer` (`approve`, `reject`, `sent`, `answer`, `download`), `joining` |
| Interviews | `GET /api/my-interviews`, `GET /api/interviews/{id}`, `POST /api/interviews/{id}/feedback`, `GET /api/interviewers` |
| Portal | `/api/sessions/{id}/candidates/{candidateId}/portal` (invites and accounts), `/api/portal/invites` (`preview`, `accept`, `claim`), `GET /api/portal/applications` and `/{id}` |
| Reschedule | `POST /api/portal/interviews/{id}/reschedule-requests`, `POST /api/portal/reschedule-requests/{id}/withdraw`, and `reschedule-requests` (`accept`, `decline`) under the candidate |
| Analytics | `GET /api/analytics/overview?days=30\|90\|365` |
| Admin | `/api/admin/users`, `/api/admin/roles` (with `members`, `permissions`, `history`), `GET /api/admin/audit` |

## Development

Run Postgres and MinIO in Docker and the rest on your machine:

```bash
docker compose up -d postgres minio
mvn -pl api-service spring-boot:run       # terminal 1
mvn -pl batch-worker spring-boot:run      # terminal 2
cd ui && npm install && npm run dev       # terminal 3
```

`maintenance/fake_llm_server.py` answers like an LLM provider, so you can score resumes without a real key.

### Tests

```bash
mvn clean verify                 # backend tests, including module boundary checks
cd ui && npx vitest run          # UI tests
cd ui && npm run lint && npm run build
```

CI runs the backend build and the UI build on every pull request.

## Security

- No secrets in the repository. JWT, database, storage and worker secrets come from the environment, and the services refuse to start without them.
- Permissions are checked on every endpoint, and admin routes also require the admin role.
- Candidate accounts are fenced off every staff page and endpoint. Access-link tokens are stored only as hashes.
- Resumes and offer letters sit in a private bucket and are served through short-lived presigned links.
- Sign-ins, role changes, downloads, exports and scoring runs are written to an audit log.

## License

[MIT](LICENSE)
