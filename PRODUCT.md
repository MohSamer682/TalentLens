# Product

<!-- impeccable:product-schema 1 -->

## Platform

Web Application (React 18 + Vite + Tailwind CSS SPA, responsive for desktop and tablet evaluation).

## Users

Collaborative hiring teams operating under dynamic Role-Based Access Control (RBAC):
- **Admins:** System administration, LLM engine configuration, user management, and role-to-permission mapping.
- **HR Chiefs:** High-level recruiting oversight, cross-recruiter session audits, analytics, and offer approvals.
- **Recruiters:** End-to-end recruitment lifecycle owners (session creation, resume intake, shortlist curation, pipeline movement, offer creation).
- **Technical & HR Interviewers:** Assigned candidate interviewers reviewing AI evaluations and submitting structured scorecards and recommendations.
- **Department Directors & Executives (Chairman, CEO/CEC):** Departmental pipeline oversight and high-value offer approval chains.

## Product Purpose

AI-assisted candidate screening, evaluation, and recruitment pipeline orchestration. Teams configure role-weighted criteria, process batches of candidate resumes with LLM analysis, review ranked standings, conduct structured interview panels, execute multi-tier offer approvals, and track confirmed onboarding.

## Positioning

Ranked, explainable screening and full-lifecycle hiring intelligence: every candidate evaluation decomposes into weighted criteria that hiring teams can understand, audit, and defend, backed by a unified pipeline from initial intake to joining.

## Operating Context

- **Screening Sessions:** Intake batches (job description + candidate resumes) transition asynchronously through uploading, text extraction, LLM scoring, and ranked states with real-time progress indicators.
- **Hiring Pipeline:** Candidates advance through structured stages (`Sourced`, `Screening`, `Interviewing`, `Interviewed`, `Offer`, `Joining`) with historical stage tracking and audit events.
- **Interview Logistics:** Multi-mode interview coordination supporting onsite rooms, online video links, and phone screens.
- **Approval Workflow:** Offers undergo multi-stakeholder approval prior to official candidate delivery and joining confirmation.

## Capabilities and Constraints

- React 18 + TypeScript + Vite + Tailwind CSS SPA with instant light/dark theme toggle.
- Spring Boot 3.3 REST API with Spring Security and JWT authentication.
- Dynamic permission enforcement (`SESSION_RUN`, `SESSION_VIEW_ALL`, `CANDIDATE_REVIEW`, `PIPELINE_MANAGE`, `OFFER_APPROVE`, `INTERVIEW_FEEDBACK`, `ANALYTICS_VIEW`, `USER_MANAGE`, `ROLE_MANAGE`, `AUDIT_VIEW`).
- Async worker with rate limiting, retries, and failure isolation for any OpenAI-compatible LLM endpoint.
- S3-compatible document storage (MinIO) with short-lived presigned URLs.
- Self-hosted fonts with zero external CDN dependencies.

## Product Principles

- **Every score is explainable:** Evaluators see criterion scores, justifications, strengths, and gaps.
- **Fast, decisive scanning:** Standings tables and radar charts provide immediate clarity.
- **Explicit state and permission gating:** Roles see only what they are authorized to manage, while stage progression and model health are continuously transparent.
- **Comprehensive hiring lifecycle:** Continuity from resume intake to confirmed joining.

## Accessibility & Inclusion

WCAG AA contrast standards, visible keyboard navigation focus, reduced-motion responsiveness, and semantic HTML structure.
