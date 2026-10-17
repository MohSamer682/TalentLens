# TalentLens: Enterprise Resume Screening & Hiring Platform

**TalentLens** is a comprehensive recruitment intelligence platform that helps hiring teams evaluate large applicant pools quickly, consistently and transparently. Beyond AI-driven resume screening, TalentLens orchestrates the complete hiring journey: from intake and weighted criteria scoring, to shortlist curation, interview planning, multi-stage approval chains, offer management, and joining tracking.

It is built for modern talent acquisition teams where recruiters, interviewers, hiring managers, and executives collaborate under role-based permissions with a unified candidate pipeline.

**Stack:** Java 21 · Spring Boot 3.3 · React 18 + TypeScript + Vite + Tailwind CSS · PostgreSQL 16 · MinIO (S3-compatible storage) · Docker Compose. The scoring engine is provider-neutral and compatible with any OpenAI-compatible LLM endpoint (OpenAI, Gemini, DeepSeek, OpenRouter, Qwen, Kimi, Grok, etc.).

---

## 🚀 Key Capabilities

| Capability | What it gives the recruiting team |
|---|---|
| **Role-Specific Weighted Scoring** | Each screening session defines weighted criteria: technical skills, experience, education, soft skills, and culture fit (totalling 100), ensuring rankings reflect specific role requirements. |
| **Explainable AI Evaluations** | Every candidate receives five criterion scores, a composite score, strengths, gaps, extracted skills, and an explainable justification. |
| **High-Volume Batch Processing** | Resumes are processed asynchronously by a dedicated worker with live progress, cancellation, and per-candidate failure isolation. |
| **End-to-End Hiring Pipeline** | Visual Kanban board tracking candidates through every hiring milestone: `Sourced` ➔ `Screening` ➔ `Interviewing` ➔ `Interviewed` ➔ `Offer` ➔ `Joining`. |
| **Interview Places & Modes** | Multi-mode interview logistics: Onsite (office location, room code), Online (video conference link, passcode), and Phone screen. |
| **Interviewer Scorecards & Feedback** | Dedicated feedback portal for technical and HR interviewers with structured recommendations (`Strong Hire`, `Hire`, `No Hire`, `Strong No Hire`), criteria scoring, and qualitative notes. |
| **Offer Approval Chain & Lifecycle** | Complete job offer lifecycle: drafting offers, multi-level authorization workflows (`OFFER_APPROVE`), signed document uploads, sending offers, and candidate acceptance/decline tracking. |
| **Joining & Onboarding Tracking** | Candidate onboarding management recording accepted start dates, documentation status, and joining confirmations. |
| **Candidate Review & Saved Shortlists** | Quick review status toggles (`Shortlisted`, `Rejected`, `Maybe`), bulk candidate reviews, and instant top-N auto-shortlisting. |
| **Candidate Notes & Activity Timeline** | Chronological audit trail of all candidate interactions, stage transitions, and private team notes. |
| **Dynamic Roles & Granular Permissions** | Admin-managed role permission matrix (`/admin/roles`) and user administration (`/admin/users`) with 8 distinct organizational roles. |
| **Recruiting Analytics Dashboard** | Historical metrics, pipeline throughput, conversion funnels, score distributions, and recruiter velocity across 30, 90, or 365 days. |
| **Reporting & Exports** | Ranked Excel export of every screening session with complete score breakdowns for hiring managers. |
| **Resilient AI Integration** | Rate-limit aware backoff, automatic retries for provider throttling (HTTP 429) and malformed output, request timeouts, and provider status probes. |
| **Secure Document Handling** | Resumes, job descriptions, and offer letters live in object storage served through short-lived presigned URLs. |

---

## 🏛️ Architecture & System Flow

```text
 Browser ── React 18 UI (Vite + nginx :3000)
              │  REST API + JWT
              ▼
         api-service :8080 ──HTTP + shared secret──▶ batch-worker :8081
              │                                          │
              └────────── PostgreSQL 16 + MinIO ◀────────┘
```

1. **Intake & Setup:** A recruiter creates a session, customizes scoring weights, and uploads the Job Description (PDF) and candidate resumes (individual PDFs or ZIP archives up to 1000 files).
2. **Batch Processing:** The batch worker extracts PDF text, prompts the configured LLM for scores and justifications, enforces rate limits, computes composite scores, and generates an Excel report.
3. **Review & Shortlisting:** Recruiters review ranked candidates on the standings table with interactive radar charts and shortlist the top applicants.
4. **Pipeline & Interviews:** Candidates move to the hiring pipeline. Recruiters schedule onsite, online, or phone interviews with assigned interviewers.
5. **Feedback & Decision:** Interviewers submit evaluations and recommendations directly via the *My Interviews* portal.
6. **Offer & Joining:** HR drafts the offer, runs it through the required approval chain, marks it sent, records candidate acceptance, and confirms joining details.

---

## 📦 Project Modules

| Module | Stack | Purpose |
|---|---|---|
| `common/` | Java 21, Spring Data JPA, Flyway | Shared domain entities, repositories, DTOs, and database schema migrations. |
| `api-service/` | Spring Boot 3.3, Spring Security, JWT | Core REST API: authentication, sessions, candidate reviews, pipeline, interviews, offers, analytics, and admin. |
| `batch-worker/` | Spring Boot 3.3, Resilience4j, PDFBox, Apache POI | Asynchronous resume text extraction, LLM scoring orchestration, rate limiting, and Excel generation. |
| `ui/` | React 18, TypeScript, Vite, Tailwind CSS | Single-page application with dark/light theme support and role-tailored views. |

- **UI Screens & Documentation:** [docs/design/screens](docs/design/screens) *(organized by role)*
- **Architecture Decisions:** [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/adr](docs/adr)

---

## ⚡ Quick Start (Docker)

### Prerequisites
- Docker and Docker Compose
- API Key from any OpenAI-compatible LLM provider (OpenAI, OpenRouter, Google Gemini, DeepSeek, etc.)

```bash
cp .env.example .env     # configure credentials and LLM settings
docker compose up -d --build
```

### Essential `.env` Variables

| Variable | Description |
|---|---|
| `LLM_API_KEY` | Provider API key |
| `LLM_BASE_URL` | Provider endpoint (e.g. `https://openrouter.ai/api/v1` or `https://generativelanguage.googleapis.com/v1beta/openai`) |
| `LLM_MODEL` | Target model identifier |
| `LLM_RATE_LIMIT_RPM` | Maximum requests per minute supported by your plan (default: 60) |
| `JWT_SECRET` | 256-bit base64-encoded secret key for JWT signing |
| `BATCH_WORKER_SHARED_SECRET` | Shared secret for inter-service communication between API and Worker |
| `POSTGRES_PASSWORD`, `MINIO_ROOT_PASSWORD` | Infrastructure passwords |

### Running Services

| Service | Endpoint | Description |
|---|---|---|
| **Web UI** | `http://localhost:3000` | Recruiter and management portal |
| **REST API** | `http://localhost:8080` | Backend API & Swagger/Actuator endpoints |
| **MinIO Console** | `http://localhost:9001` | S3 Object storage browser |
| **Adminer** | `http://localhost:8085` | PostgreSQL database administration |

> **First Login:** The initial account registered via `/register` automatically receives the `ADMIN` role. Subsequent accounts register as `RECRUITER` by default and can be assigned roles by the administrator. Default test credentials: password `12345678`.

---

## 👥 Role-Based Access Control (RBAC)

TalentLens features an admin-managed dynamic permission matrix (`/admin/roles`) covering 8 specialized roles:

| Role | Primary Purpose | Key Permissions |
|---|---|---|
| **Admin** | Full system control & configuration | `USER_MANAGE`, `ROLE_MANAGE`, `AUDIT_VIEW`, `SESSION_RUN`, `SESSION_VIEW_ALL`, `PIPELINE_MANAGE`, `OFFER_APPROVE`, `ANALYTICS_VIEW` |
| **HR Chief** | Departmental oversight & analytics | `SESSION_RUN`, `SESSION_VIEW_ALL`, `CANDIDATE_REVIEW`, `PIPELINE_MANAGE`, `OFFER_APPROVE`, `ANALYTICS_VIEW` |
| **Recruiter** | Screening session owner & candidate manager | `SESSION_RUN`, `CANDIDATE_REVIEW`, `PIPELINE_MANAGE` |
| **HR Interviewer** | Behavioral interview evaluation | `INTERVIEW_FEEDBACK` |
| **Technical Interviewer** | Code and technical interview evaluation | `INTERVIEW_FEEDBACK` |
| **Department Director** | Department candidate review & offer approval | `PIPELINE_MANAGE`, `OFFER_APPROVE`, `INTERVIEW_FEEDBACK` |
| **Chairman** | Executive review & offer authorization | `OFFER_APPROVE`, `ANALYTICS_VIEW`, `INTERVIEW_FEEDBACK` |
| **CEC / CEO** | Executive review & offer authorization | `OFFER_APPROVE`, `ANALYTICS_VIEW`, `INTERVIEW_FEEDBACK` |

---

## 📡 API Overview

All secured endpoints require `Authorization: Bearer <token>`. Responses conform to a uniform envelope: `{ "success": true, "message": "...", "data": ... }`.

### Authentication & Self
- `POST /api/auth/register` — Create a new account
- `POST /api/auth/login` — Sign in and receive JWT token
- `GET /api/auth/me` — Current user profile, role, and granted permissions

### Screening Sessions & Resumes
- `POST, GET /api/sessions` — Create and list screening sessions (`?everyone=true` for oversight roles)
- `GET, PATCH, DELETE /api/sessions/{id}` — Session detail, weight updates, and deletion
- `POST /api/sessions/{id}/jd` — Upload job description PDF
- `POST /api/sessions/{id}/resumes` — Upload candidate resume PDFs or ZIP archives
- `GET /api/sessions/{id}/resumes` — List session resumes with processing status and failure reasons
- `POST /api/sessions/{id}/start?scope=UNSCORED|ALL` — Start LLM scoring batch
- `POST /api/sessions/{id}/cancel` — Cancel ongoing scoring batch
- `GET /api/sessions/{id}/progress` — Live scoring progress and candidate status
- `GET /api/sessions/{id}/candidates` — Ranked candidates with composite scores and criteria breakdown
- `GET /api/sessions/{id}/export` — Download ranked Excel report

### Candidate Review & Shortlisting
- `PATCH /api/sessions/{id}/candidates/{candidateId}/review` — Update review status (`SHORTLISTED`, `REJECTED`, `MAYBE`, `NONE`)
- `POST /api/sessions/{id}/candidates/review` — Bulk candidate review update
- `POST /api/sessions/{id}/shortlist/top` — Automatically shortlist top N candidates
- `GET, POST, DELETE /api/sessions/{id}/candidates/{candidateId}/notes` — Candidate notes management

### Hiring Pipeline & Interview Places
- `GET /api/pipeline` — Load candidates on the multi-stage pipeline board
- `POST /api/sessions/{id}/candidates/{candidateId}/contact` — Transition candidate to *Contacted* stage
- `GET, PUT /api/sessions/{id}/candidates/{candidateId}/interview-plan` — Set interview stages (e.g. HR, Technical, Culture)
- `POST /api/sessions/{id}/candidates/{candidateId}/interviews` — Schedule interview (Onsite place, Online video link, or Phone)
- `PATCH, DELETE /api/sessions/{id}/candidates/{candidateId}/interviews/{interviewId}` — Reschedule or cancel interview
- `GET /api/interviewers` — List available interviewers by evaluation domain

### Interviewer Feedback Portal
- `GET /api/my-interviews` — List interviews assigned to the current interviewer
- `GET /api/interviews/{interviewId}` — Detailed interview view with candidate profile and scores
- `POST /api/interviews/{interviewId}/feedback` — Submit recommendation (`STRONG_HIRE`, `HIRE`, `NO_HIRE`), score, and notes

### Offer Approval Chain & Joining
- `POST /api/sessions/{id}/candidates/{candidateId}/move-to-offer` — Advance candidate to *Offer* stage
- `POST /api/sessions/{id}/candidates/{candidateId}/offer` — Upload offer letter and set approval requirements
- `GET, GET /download /api/sessions/{id}/candidates/{candidateId}/offer` — View offer details and presigned download URL
- `POST /api/sessions/{id}/candidates/{candidateId}/offer/approve` — Approve offer in approval chain
- `POST /api/sessions/{id}/candidates/{candidateId}/offer/reject` — Reject offer with reasons
- `POST /api/sessions/{id}/candidates/{candidateId}/offer/sent` — Mark approved offer as sent to candidate
- `POST /api/sessions/{id}/candidates/{candidateId}/offer/answer` — Record candidate answer (`ACCEPTED` or `DECLINED`)
- `PUT /api/sessions/{id}/candidates/{candidateId}/joining` — Save onboarding and joining start date details

### Analytics & Administration
- `GET /api/analytics/overview?days=30|90|365` — Recruiting funnel throughput and score bands
- `GET, PATCH /api/admin/users`, `/api/admin/users/{id}/role` — Manage users and assign roles
- `GET, PUT /api/admin/roles`, `/api/admin/roles/{role}/permissions` — View and edit role permission matrix
- `GET /api/admin/audit` — Security and administrative audit trail

---

## 🛠️ Local Development

```bash
# 1. Start Postgres and MinIO containers
docker compose up -d postgres minio

# 2. Build and verify backend modules
mvn clean verify

# 3. Launch Spring Boot API Service (Terminal 1)
mvn -pl api-service spring-boot:run

# 4. Launch Spring Boot Batch Worker (Terminal 2)
mvn -pl batch-worker spring-boot:run

# 5. Launch React UI Development Server (Terminal 3)
cd ui
npm install
npm run dev
```

---

## 🔒 Security & Compliance

- **Zero Committed Secrets:** All credentials, tokens, and storage keys are managed strictly through environment variables.
- **Access Control:** Every API endpoint verifies fine-grained permissions via Spring Security `@PreAuthorize`.
- **Short-Lived Documents:** Sensitive resumes and offer documents are protected behind private S3 buckets and accessed solely via time-limited presigned URLs.
- **Audit Logging:** Administrative actions, role modifications, and scoring runs are recorded to the database with user attribution and timestamps.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
