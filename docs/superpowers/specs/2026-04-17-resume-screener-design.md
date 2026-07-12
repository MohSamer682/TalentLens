# Resume Screener & Candidate Ranking System — Design Spec
**Date:** 2026-04-17  
**Author:** Tech Lead  
**Status:** Approved

---

## 1. Overview

A microservices-based platform that allows HR teams to upload a job description and a batch of resumes. The system parses PDFs, calls an LLM API to score and rank candidates, extracts skills, and generates a structured Excel comparison report. Each screening session has its own configurable scoring weights. Users authenticate via JWT; roles control access.

---

## 2. Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 18 + Vite + Tailwind CSS + shadcn/ui |
| API Service | Spring Boot 3.x, Spring Security (JWT), Spring Data JPA |
| Batch Worker | Spring Boot 3.x, Spring Batch, Apache PDFBox, Apache POI |
| LLM | OpenAI API (gpt-4o), Resilience4j (retry + rate limiter) |
| ORM | Spring Data JPA + Hibernate (all DB access via repositories — no raw SQL) |
| Database | PostgreSQL 16 |
| File Storage | MinIO (S3-compatible) |
| Containerization | Docker + Docker Compose |
| Token Limiting | tiktoken-style estimation, 3500-token max per chunk |

---

## 3. System Architecture

```
┌─────────────────┐     REST/HTTP      ┌──────────────────────┐
│   React UI      │ ──────────────────▶│  Spring Boot API     │
│   (port 3000)   │                    │  (port 8080)         │
└─────────────────┘                    └──────────┬───────────┘
                                                  │ trigger job
                                                  ▼
                                       ┌──────────────────────┐
                                       │  Spring Batch Worker │
                                       │  (port 8081)         │
                                       └──────┬───────┬───────┘
                                              │       │
                                    PDFBox +  │       │ OpenAI API
                                    MinIO     ▼       ▼
                                       ┌──────────────────────┐
                                       │   PostgreSQL 16      │
                                       └──────────────────────┘
                                       ┌──────────────────────┐
                                       │   MinIO              │
                                       └──────────────────────┘
```

**Docker Compose services:** `ui`, `api`, `batch-worker`, `postgres`, `minio`

**Inter-service communication:** React → API via REST + JWT. API → Batch Worker via internal HTTP call (shared secret header). Batch Worker reads/writes PostgreSQL via JPA. Batch Worker reads/writes MinIO via S3 client.

---

## 4. Data Model (JPA Entities)

### `users`
```
id            UUID PK
email         VARCHAR UNIQUE NOT NULL
password_hash VARCHAR NOT NULL
role          ENUM(ADMIN, RECRUITER)
created_at    TIMESTAMP
```

### `screening_sessions`
```
id            UUID PK
user_id       UUID FK → users
title         VARCHAR NOT NULL
status        ENUM(PENDING, RUNNING, DONE, FAILED)
jd_file_key   VARCHAR (MinIO object key)
created_at    TIMESTAMP
completed_at  TIMESTAMP
```

### `scoring_weights`
```
id                    UUID PK
session_id            UUID FK → screening_sessions (1:1)
technical_skills_w    FLOAT   (0–100)
experience_w          FLOAT
education_w           FLOAT
soft_skills_w         FLOAT
culture_fit_w         FLOAT
-- sum of all weights must equal 100
```

### `candidates`
```
id               UUID PK
session_id       UUID FK → screening_sessions
name             VARCHAR (LLM-extracted)
email            VARCHAR (LLM-extracted)
resume_file_key  VARCHAR (MinIO)
raw_text         TEXT
status           ENUM(PENDING, SCORED, FAILED)
```

### `candidate_scores`
```
id                UUID PK
candidate_id      UUID FK → candidates (1:1)
technical_skills  FLOAT
experience        FLOAT
education         FLOAT
soft_skills       FLOAT
culture_fit       FLOAT
composite_score   FLOAT  (computed weighted sum)
rank              INT
skills_extracted  JSONB  (e.g. ["Java","Spring Boot","REST APIs"])
justification     TEXT   (LLM reasoning paragraph)
strengths         TEXT
gaps              TEXT
```

### `batch_jobs`
```
id                  UUID PK
session_id          UUID FK → screening_sessions
spring_batch_job_id VARCHAR
total               INT
processed           INT
failed              INT
started_at          TIMESTAMP
finished_at         TIMESTAMP
```

**ORM note:** All entities mapped via `@Entity` + Spring Data JPA repositories. Zero native SQL queries. Use `@Transactional` on service methods. JSONB handled via `@Type(JsonType.class)` (hypersistence-utils).

---

## 5. API Design

### Auth
```
POST /api/auth/register     body: { email, password, role }
POST /api/auth/login        body: { email, password } → { token, user }
```

### Sessions
```
POST   /api/sessions              body: { title, weights: {...} }
GET    /api/sessions              → list (paginated)
GET    /api/sessions/{id}         → session + weights + status
DELETE /api/sessions/{id}
```

### File Upload
```
POST /api/sessions/{id}/jd        multipart: jd PDF
POST /api/sessions/{id}/resumes   multipart: resume PDFs (multiple)
```

### Batch Control
```
POST /api/sessions/{id}/start     → triggers batch job
GET  /api/sessions/{id}/progress  → { total, processed, failed, status }
```

### Results
```
GET /api/sessions/{id}/candidates           → ranked list + scores
GET /api/sessions/{id}/candidates/{cid}     → full candidate detail
GET /api/sessions/{id}/export               → Excel file download (MinIO presigned URL)
```

### Admin
```
GET   /api/admin/users
PATCH /api/admin/users/{id}/role   body: { role }
```

**Security:** JWT Bearer token on all routes except `/api/auth/**`. `RECRUITER` accesses own sessions only. `ADMIN` accesses all sessions + user management.

---

## 6. Batch Processing Design

### Spring Batch Job: `ResumeScreeningJob`

**Step 1 — `ExtractTextStep`**
- `ItemReader`: JPA-based, reads `candidates` with `status = PENDING` for the session
- `ItemProcessor`: Downloads PDF from MinIO → Apache PDFBox text extraction → clean text (strip headers/footers, normalize whitespace)
- `ItemWriter`: Saves `raw_text` + sets `status = SCORED` (partial) to DB
- Chunk size: 10

**Step 2 — `ScoreWithLLMStep`**
- `ItemReader`: Reads candidates with `raw_text` not null
- `ItemProcessor`: 
  - Estimate token count of resume text (chars / 4 approximation)
  - If text > 3500 tokens → split into chunks, score each chunk, average results
  - Build structured prompt (see Section 7)
  - Call OpenAI API via Resilience4j-wrapped client (retry 3x on 429/5xx, exponential backoff)
  - Parse JSON response → `CandidateScoreDto`
- `ItemWriter`: Saves `candidate_scores` record
- Chunk size: 5 (conservative — OpenAI rate limit headroom)

**Step 3 — `RankAndFinalizeStep`** (Tasklet)
- Load all `candidate_scores` for session
- Compute `composite_score = (technical_skills * tw + experience * ew + education * edu_w + soft_skills * sw + culture_fit * cw) / 100`
- Sort DESC, assign `rank` 1..N
- Generate Excel report via Apache POI → upload to MinIO
- Set `screening_session.status = DONE`

**Rate limiting:** Resilience4j `RateLimiter` — 60 RPM max (OpenAI default tier). Configurable via `application.yml`.

---

## 7. LLM Prompt Strategy

```
System: You are an expert HR analyst. Evaluate the resume against the job description.
Return ONLY valid JSON matching the schema below. No prose outside JSON.

Schema:
{
  "technical_skills": <0-100>,
  "experience": <0-100>,
  "education": <0-100>,
  "soft_skills": <0-100>,
  "culture_fit": <0-100>,
  "skills": ["skill1", "skill2", ...],
  "justification": "<2-3 sentence overall assessment>",
  "strengths": "<key strengths>",
  "gaps": "<key gaps>"
}

User:
JOB DESCRIPTION:
{jd_text_truncated_to_1000_tokens}

RESUME:
{resume_chunk}

Evaluate the candidate and return the JSON.
```

**Token budget:** JD summary ≤ 1000 tokens. Resume chunk ≤ 3500 tokens. Prompt overhead ~200 tokens. Total ≤ 4700 tokens → well within gpt-4o 8k context.

---

## 8. Excel Export Structure

Generated via **Apache POI XSSF** (`.xlsx`).

**Sheet 1 — Ranking Summary**
Columns: Rank | Name | Email | Composite Score | Technical | Experience | Education | Soft Skills | Culture Fit
- Rows sorted by rank ascending
- Conditional formatting: top 20% green, bottom 20% red

**Sheet 2 — Skills Matrix**
Rows = candidates, columns = all unique extracted skills across session.
Cell = ✓ if candidate has skill, blank otherwise.

**Sheet 3 — Justifications**
Columns: Rank | Name | Justification | Strengths | Gaps
Full LLM reasoning per candidate.

**Sheet 4 — Session Config**
Session title, JD filename, weights used, date run, total candidates, processing duration.

---

## 9. Frontend Screens — Detailed UI Spec

### 9.1 Login Screen `/login`

**Purpose:** Authenticate existing users.

**Layout:** Centered card on dark gradient background. Logo + app name at top.

**Inputs:**
- `Email` — text input, type=email, placeholder "you@company.com", full width
- `Password` — text input, type=password, placeholder "••••••••", full width
- `Login` — primary button, full width, solid blue

**States:**
- Loading: button shows spinner + "Signing in..."
- Error: red banner above form "Invalid email or password"
- Success: redirect to `/dashboard`

**Links:**
- "Don't have an account? Register" → `/register`

**Validation:** Both fields required. Email format check. Inline error below each field.

---

### 9.2 Register Screen `/register`

**Purpose:** Create a new Recruiter account.

**Layout:** Same centered card as login.

**Inputs:**
- `Full Name` — text input, placeholder "Jane Smith"
- `Email` — text input, type=email
- `Password` — text input, type=password, min 8 chars
- `Confirm Password` — text input, type=password
- `Register` — primary button, full width

**States:**
- Password mismatch: inline red error "Passwords do not match"
- Email taken: banner error "Email already registered"
- Success: redirect to `/dashboard`

**Notes:** All new registrations default to `RECRUITER` role. Admin promotion done via admin panel.

---

### 9.3 Dashboard `/dashboard`

**Purpose:** Overview of all screening sessions for the logged-in user.

**Layout:** Top navbar (logo, user avatar + name, logout). Main content = page header + sessions grid.

**Header area:**
- Page title "Screening Sessions"
- `+ New Session` button (top right, primary blue)
- Search bar — filter sessions by title
- Status filter tabs: All | Pending | Running | Done | Failed

**Session card (grid, 3 cols on desktop, 1 col mobile):**
Each card shows:
- Session title (bold)
- Status badge: gray=PENDING, blue=RUNNING, green=DONE, red=FAILED
- Created date
- Candidate count (e.g. "24 resumes")
- Progress bar (visible only when status=RUNNING, shows processed/total)
- Actions: `View Results` (if DONE) | `View Progress` (if RUNNING) | `Continue Setup` (if PENDING)

**Empty state:** Centered illustration + "No sessions yet. Create your first screening." + New Session button.

---

### 9.4 New Session Screen `/sessions/new`

**Purpose:** Create a session, upload JD + resumes, configure weights, launch batch.

**Layout:** Stepper UI — 3 steps shown at top: `1. Session Info` → `2. Upload Files` → `3. Configure & Launch`

#### Step 1 — Session Info
- `Session Title` — text input, placeholder "Backend Engineer Q2 2026", required
- `Description` (optional) — textarea, 3 rows
- `Next →` button

#### Step 2 — Upload Files
- **JD Upload zone:**
  - Large dashed dropzone, icon + "Drop Job Description PDF here or click to browse"
  - Accepts: `.pdf` only, max 5MB
  - Uploaded state: shows filename + file size + green checkmark + remove (×) button
  
- **Resumes Upload zone:**
  - Large dashed dropzone, icon + "Drop Resume PDFs here or click to browse (multiple)"
  - Accepts: `.pdf` only, max 10MB per file, max 200 files
  - Uploaded state: scrollable list of filenames with individual remove buttons
  - Counter badge: "47 resumes uploaded"
  
- `← Back` + `Next →` buttons

#### Step 3 — Configure Weights & Launch
- Section title: "Scoring Weights" + subtitle "Weights must sum to 100"
- Five weight sliders:
  - `Technical Skills` — range slider 0–100, default 30, shows numeric value next to label
  - `Experience` — range slider 0–100, default 25
  - `Education` — range slider 0–100, default 15
  - `Soft Skills` — range slider 0–100, default 20
  - `Culture Fit` — range slider 0–100, default 10
- Live sum indicator: "Total: 100 / 100" (green if =100, red if ≠100)
- `Launch Screening` — primary button, disabled until sum=100 and files uploaded
- `← Back` button

**Post-launch:** Redirect to `/sessions/{id}` (progress view).

---

### 9.5 Session Detail / Progress Screen `/sessions/:id`

**Purpose:** Live progress tracking during batch + results view after completion.

**Layout:** Breadcrumb (Dashboard > Session Title). Two states:

#### State A — Job Running (status=RUNNING or PENDING)
- Large circular progress indicator (% complete, animated)
- Stats row: `Processed: 34 / 100` | `Failed: 2` | `Estimated time: ~4 min`
- Step indicators: Extract Text ✓ | Score with LLM ⟳ | Rank & Finalize ○
- Auto-polls `/progress` every 3 seconds
- Cancel Job button (danger, outline)

#### State B — Job Done (status=DONE)
- Green success banner "Screening complete — 98 candidates ranked"
- `Download Excel Report` button (prominent, top right)
- Results table:

| Rank | Name | Email | Score | Technical | Experience | Education | Soft Skills | Culture Fit | Actions |
|---|---|---|---|---|---|---|---|---|---|

  - Sortable columns (click header)
  - Score shown as colored badge (green ≥80, yellow 60–79, red <60)
  - `View` button per row → `/sessions/:id/candidate/:cid`
  - Pagination: 20 per page

#### State C — Job Failed
- Red error banner with failure message
- Retry button

---

### 9.6 Candidate Detail Screen `/sessions/:id/candidate/:cid`

**Purpose:** Deep dive into a single candidate's scoring.

**Layout:** Back button to session. Two-column layout (desktop).

**Left column — Candidate Info:**
- Name (heading), Email
- Extracted Skills — pill/tag list (e.g. "Java" "Spring Boot" "PostgreSQL")
- Resume file link (view/download from MinIO)

**Right column — Score Breakdown:**
- Radar/spider chart showing 5 dimensions (recharts)
- Score table:

| Dimension | Score | Weight | Weighted Score |
|---|---|---|---|
| Technical Skills | 87 | 30% | 26.1 |
| Experience | 74 | 25% | 18.5 |
| ... | | | |
| **Composite Score** | | | **82.4** |

- **Justification card:** LLM paragraph in a quoted block
- **Strengths:** bulleted list
- **Gaps:** bulleted list

---

### 9.7 Admin Panel `/admin/users`

**Purpose:** Manage user accounts and roles. Visible only to `ADMIN` role.

**Layout:** Table of all users.

**Table columns:** Name | Email | Role | Created At | Actions

**Actions per row:**
- Role dropdown: `RECRUITER` / `ADMIN` — inline update
- Deactivate button (soft delete / disable)

**Access control:** Route guard — non-admins redirected to `/dashboard`.

---

## 10. Non-Functional Requirements

| Concern | Decision |
|---|---|
| Scalability | Batch worker horizontally scalable (multiple instances, Spring Batch partitioning) |
| Token safety | Resume chunks capped at 3500 tokens; rate limiter 60 RPM global |
| Security | JWT HS256, bcrypt passwords, MinIO presigned URLs (15 min expiry) |
| Error handling | Failed candidates marked `FAILED`, don't block batch; reported in progress |
| Config | All secrets via environment variables (Docker Compose `.env`) |
| CORS | API allows `http://localhost:3000` in dev; configurable for prod |

---

## 11. Project Structure

```
resume-screener/
├── docker-compose.yml
├── .env.example
├── ui/                          # React + Vite
│   ├── src/
│   │   ├── pages/               # Login, Register, Dashboard, NewSession, SessionDetail, CandidateDetail, Admin
│   │   ├── components/          # Shared UI components
│   │   ├── api/                 # Axios API client
│   │   └── store/               # Auth state (Zustand or Context)
├── api-service/                 # Spring Boot API
│   ├── src/main/java/
│   │   ├── auth/                # JWT, SecurityConfig
│   │   ├── session/             # Session + weights CRUD
│   │   ├── upload/              # MinIO upload service
│   │   ├── candidate/           # Candidate repository
│   │   └── batch/               # Job trigger endpoint
├── batch-worker/                # Spring Batch service
│   ├── src/main/java/
│   │   ├── job/                 # ResumeScreeningJob config
│   │   ├── steps/               # ExtractTextStep, ScoreWithLLMStep, RankStep
│   │   ├── pdf/                 # PDFBox extraction + cleaning
│   │   ├── llm/                 # OpenAI client + prompt builder
│   │   ├── excel/               # Apache POI report generator
│   │   └── storage/             # MinIO client
└── docs/
    └── superpowers/specs/
        └── 2026-04-17-resume-screener-design.md
```
