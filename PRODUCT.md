# Product

<!-- impeccable:product-schema 1 -->

## Platform

React 18, Vite and Tailwind CSS single-page app. Built for desktop and tablet, usable on phones.

## Users

- **Admins:** users, roles, permissions, audit log.
- **HR chiefs:** oversight, analytics, final offer approval. Sessions are read-only for them.
- **Recruiters:** run their sessions from job description to joining.
- **Technical and HR interviewers:** see their own interviews and submit feedback.
- **Department directors, CEO, Chairman:** management interviews. Directors also approve offers when asked.
- **Candidates:** follow their application, see interview times and places, ask to reschedule.

## Purpose

Screening and hiring in one place: weight the criteria, score a batch of resumes with an LLM, review the ranking, run interviews, approve offers and record joining.

## Positioning

Explainable ranking that carries through the rest of hiring. Every score breaks down into weighted criteria a team can question and defend. One record follows the candidate from upload to joining.

## Operating context

- **Sessions:** a job (title, position code, description, skills) plus resumes. Upload, extract, score, rank, with live progress.
- **Pipeline:** Shortlisted, Contacted, Interviewing, Interviewed, Offer, Offer approved, Offer sent, Accepted or Declined, Joining. Every move goes on the timeline.
- **Interviews:** HR, technical and management rounds at HQ, online or elsewhere. Candidates can ask to move one.
- **Approvals:** department director when asked, then HR chief, before an offer is sent.

## Capabilities and constraints

- Light and dark themes.
- Spring Boot 3.3 REST API, Spring Security and JWT. Separate sign-in pages for staff and candidates.
- Ten permissions mapped to roles by admins: `SESSION_RUN`, `SESSION_VIEW_ALL`, `CANDIDATE_REVIEW`, `PIPELINE_MANAGE`, `OFFER_APPROVE`, `INTERVIEW_FEEDBACK`, `ANALYTICS_VIEW`, `USER_MANAGE`, `ROLE_MANAGE`, `AUDIT_VIEW`.
- Scoring worker with rate limiting, retries and per-candidate failure isolation. Any OpenAI-compatible endpoint.
- MinIO storage behind short-lived presigned links.
- Self-hosted fonts, no CDN.

## Principles

- **Explainable:** scores, justification, strengths and gaps sit together.
- **Scannable:** standings read like a results board.
- **Honest state:** people see what they can act on, where each candidate stands and whether scoring is healthy.
- **One record:** nothing is copied between tools.

## Accessibility

WCAG AA contrast, visible focus, reduced motion respected, semantic HTML.
