# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users
Recruiters screening batches of resumes on desktop, plus admins who manage users and the LLM setup, HR chiefs who oversee every recruiter's sessions, approve offers and read recruiting analytics without managing users, and technical and HR interviewers who see only the candidates assigned to them. Admins decide what each role may do. Mixed audience; hiring managers may drop in to review shortlists. (Inferred from README and user answer "Mixed, incl. admins".)

## Product Purpose
AI-assisted resume screening. Upload a job description and a batch of resumes; an LLM scores each candidate against weighted criteria; recruiters review a ranked standings table, a shortlist cut-off, and per-criterion breakdowns for each candidate.

## Positioning
Ranked, explainable screening: every score decomposes into weighted criteria a recruiter can adjust and defend.

## Operating Context
Sessions (one job description plus a batch of resumes) move through uploading, scoring and ranked states. Batches score asynchronously in a worker with rate-limited LLM calls, so progress and model health are visible states.

## Capabilities and Constraints
React + TypeScript + Vite + Tailwind SPA, JWT auth, admin user management, session creation with weight sliders, standings table with shortlist size, candidate detail with radar chart. Self-hosted fonts (no external requests). Existing semantic tokens live in `ui/src/index.css`.

## Brand Commitments
None binding. Colour, type and styling are open for replacement. Light and dark themes must both ship.

## Evidence on Hand
Sample fixtures in `samples/`. No customer logos, testimonials or benchmarks exist; none may be invented.

## Product Principles
- Every score is explainable.
- Scanning a ranked list must be fast.
- State (scoring, ranked, failed, model health) is always legible.

## Accessibility & Inclusion
WCAG AA contrast, visible keyboard focus, reduced-motion alternatives. (Assumed standard; not user-stated.)
