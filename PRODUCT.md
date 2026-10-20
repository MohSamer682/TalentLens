# Product

<!-- impeccable:product-schema 1 -->

## Platform

Web app: a React 18, Vite and Tailwind CSS single-page app, built for desktop and tablet, usable on phones.

## Users

Hiring teams working under admin-managed roles, plus the candidates they shortlist:
- **Admins:** users, roles and permissions, the audit log.
- **HR chiefs:** oversight across recruiters, analytics, and the final offer approval. They open sessions read-only.
- **Recruiters:** own their screening sessions from job description to joining: upload, shortlist, contact, interviews and offers.
- **Technical and HR interviewers:** see their own interviews, read the candidate's scores, and submit feedback.
- **Department directors, CEO and Chairman:** sit on management interviews. Directors also approve offers that need them.
- **Candidates:** follow their application in the portal, see interview times and places, and ask to reschedule.

## Product purpose

Resume screening and hiring in one place. Teams set weighted criteria for a job, score a batch of resumes with an LLM, review the ranked standings, run planned interview rounds with structured feedback, take offers through approval, and record the candidate's answer and joining date.

## Positioning

Ranked, explainable screening that carries on into the rest of hiring. Every score breaks down into weighted criteria a team can understand, question and defend, and the same record follows the candidate from upload to joining.

## Operating context

- **Screening sessions:** a job (title, position code, description, required skills) plus resumes, moving through upload, text extraction, scoring and ranked, with live progress.
- **Hiring pipeline:** Shortlisted, Contacted, Interviewing, Interviewed, Offer, Offer approved, Offer sent, Accepted or Declined, Joining. Each move is recorded on the candidate's timeline.
- **Interviews:** planned rounds (HR, technical, management) held at HQ, online, or elsewhere. Candidates can ask to move one.
- **Approvals:** offers go to the department director when asked for, then the HR chief, before they are sent.

## Capabilities and constraints

- React 18, TypeScript, Vite and Tailwind CSS, with a light and dark theme toggle.
- Spring Boot 3.3 REST API with Spring Security and JWT. Staff and candidates sign in on separate pages.
- Ten permissions mapped to roles by admins: `SESSION_RUN`, `SESSION_VIEW_ALL`, `CANDIDATE_REVIEW`, `PIPELINE_MANAGE`, `OFFER_APPROVE`, `INTERVIEW_FEEDBACK`, `ANALYTICS_VIEW`, `USER_MANAGE`, `ROLE_MANAGE`, `AUDIT_VIEW`.
- A scoring worker with rate limiting, retries and per-candidate failure isolation, for any OpenAI-compatible endpoint.
- S3-compatible storage (MinIO) with short-lived presigned links.
- Self-hosted fonts, no external CDN.

## Product principles

- **Every score is explainable:** criterion scores, justification, strengths and gaps sit next to the number.
- **Fast scanning:** the standings read like a results board.
- **State and permissions are visible:** people see what they may act on, where each candidate stands, and whether scoring is healthy.
- **One record from upload to joining:** nothing gets copied between tools.

## Accessibility

WCAG AA contrast, visible keyboard focus, reduced motion respected, and semantic HTML.
