# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

In-house HR recruiters screening a batch of applicants for a single job opening. They upload one job description and many resumes, then work from the ranked result. Admins manage user accounts and roles (`ADMIN`, `RECRUITER`).

## Product Purpose

Resume Screener turns a pile of PDF resumes into a ranked shortlist for one opening. An LLM scores each candidate against the job description, and the recruiter takes the top of the ranking forward. Success means the shortlist can be trusted without reading every resume, and the result leaves the app as a shareable Excel report.

## Positioning

It runs on the company's own infrastructure with any OpenAI-compatible LLM, local models included, so candidate data never has to go to a third-party SaaS. Each screening session has its own scoring weights, so the ranking reflects what this particular role values.

## Operating Context

- Work happens in screening sessions: create session → set weights → upload JD + resumes → batch scoring runs → review ranked candidates → export.
- Scoring is asynchronous (session status `PENDING` → `RUNNING` → `DONE` / `FAILED`; candidate status `PENDING` / `SCORED` / `FAILED`). Recruiters watch progress and may cancel.
- The Excel comparison report is a core deliverable that gets shared outside the app.

## Capabilities and Constraints

- Five weighted scoring dimensions per session: technical skills, experience, education, soft skills, culture fit (defaults 30/25/15/20/10).
- Ranking is used to auto-shortlist: recruiters mostly trust the order and move the top N forward. Per-candidate score breakdowns exist for drilling in, but the ranking is the primary output.
- Self-hosted and private: resumes are personal data stored in the company's own MinIO/Postgres (Docker Compose deployment).
- LLM provider is configurable (base URL + model); the product must not assume a specific vendor.
- Inputs are PDFs; parsing or scoring can fail per candidate without failing the session.

## Evidence on Hand

- Sample inputs: `JD.pdf`, `Resume.pdf`, `TEST_JD.md`, `TEST_RESUME.md`.
- Screen references: `docs/superpowers/Screeens/` (Login, Register, Dashboard, New Session Wizard).
- No real customers, testimonials, accuracy benchmarks, or pricing exist; do not fabricate them.

## Product Principles

1. The ranking is the product. It must be clear, stable and easy to act on, because recruiters shortlist from it directly.
2. Show progress for async work. A batch run should never look stuck or end silently.
3. Partial failure is normal. One bad PDF is flagged; it does not sink the session.
4. Candidate data stays private and only goes where the user chose to send it.
5. What leaves the app (the Excel report) matters as much as what is shown in it.
