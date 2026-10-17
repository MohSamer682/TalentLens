# TalentLens: feature brief for F5 to F8

Requested by Muhammed on 2026-10-17. This is the spec the four plans below argue from. It follows F1 to F4 (bulk upload, shortlist, analytics, roles and permissions) and builds on `feat/roles-and-permissions` (Flyway at V7).

| # | Feature | Plan | Asked for |
|---|---|---|---|
| F5 | TalentLens name, icon, "Lens" materials, a better light mode, HR chief sees all sessions | [f5](2026-10-17-f5-talentlens-brand-and-materials.md) | items 3 and 4, follow-ups 1 and 2 |
| F6 | Notes on each candidate | [f6](2026-10-17-f6-candidate-notes.md) | item 2 |
| F7 | Hiring pipeline: contact, interviews, offer, approval; interviewer roles | [f7](2026-10-17-f7-hiring-pipeline.md) | items 5 and 6 |
| F8 | Analytics dashboard for the whole funnel | [f8](2026-10-17-f8-analytics-funnel.md) | item 1 |

Order: F5 first so every new screen is built on the new materials; F8 last because it reports on the stages F7 creates. One branch and one PR per feature, each stacked on the previous one. The cycle stays: plan approval, implementation, tests, UAT, smoke test, PR.

**No session links, ever (Muhammed, 2026-10-17):** no PR, PR comment, commit, code file or doc we create may contain a Claude Code session link (`claude.ai/code/session_…`) or anything else tied to the session. PR bodies end with "Built by Mohamed Samer with Claude Code" and nothing after it.

Mockups for every new and changed screen: [TalentLens designs](https://claude.ai/artifact/PSLJQvTKcKs4zHHnbSprTH) (brand and materials, analytics, candidate page, pipeline, my interviews, interview feedback, all sessions for the HR chief). The written spec every screen must be built to and checked against: [2026-10-17-talentlens-design-spec.md](2026-10-17-talentlens-design-spec.md).

## F5. TalentLens name, icon and materials

- Every place the product names itself says **TalentLens**: browser title, navbar, auth screens, README, PRODUCT.md, OpenAPI title. Maven artifact ids, Java packages, the database and Docker service names stay as they are (renaming them breaks deployments for no user-visible gain).
- Icon: a lens ring with a handle, framing three ranked bars, the tallest in saffron (the candidate who made the cut). App tile `#0E1614`, radius 16 of 64. Used as favicon, navbar mark and auth-screen mark. Exact SVG is in the F5 plan.
- Design direction, "Lens materials": the Results Night colours and fonts stay; shape, depth and motion get softer and more physical.
  - Shapes: radius scale 6 / 10 / 14 / 20 px plus full pills for chips, status and segmented controls (was 4 / 6 / 8).
  - Materials, three tiers: **plate** (panels: surface, hairline, 1px top highlight in dark, soft 2-layer shadow in light), **lift** (things you can pick up: hoverable cards, menus, the selection bar: stronger shadow, `surface-raised` fill), **glass** (navbar and sticky bars only: 78% pitch with 16px blur, solid fallback when `backdrop-filter` is unsupported or `prefers-reduced-transparency`).
  - Motion: one ease `cubic-bezier(0.22, 1, 0.36, 1)`; 160 ms for state, 240 ms for enter, 320 ms for panels. Interactive cards rise 1px on hover. Everything still drops travel under `prefers-reduced-motion`.
  - Light mode, reworked: the light theme gets its own light glass navbar (white at 80% with blur, ink text, saffron underline) instead of the dark bar; a quieter ground `#F5F7F6`; hairlines `#DDE4E1`; two-layer soft shadows so white panels lift off the ground; chips as light tints with dark text (all 4.5:1 or better); muted text `#4F5F59`; table heads `#F0F3F2`. The brand tile stays dark in both themes.
  - Rules kept: saffron only means "made the cut"; blue only for links, focus and data; monochrome ink primary buttons; WCAG AA in both themes.

### HR chief sees all sessions (follow-up 1)

- The HR chief oversees sessions and does not run them: `HR_CHIEF` loses `SESSION_RUN` by default (an admin can grant it back on the Roles page). Sessions an HR chief already created stay viewable; only their owner-side actions go.
- Anyone who can see all sessions but cannot run them lands on **All sessions**: the dashboard opens on every recruiter's sessions, titled "All sessions", with each session's owner shown, no "Mine / All recruiters" switch and no "New session" button.
- Users who can do both (admins, or an HR chief granted `SESSION_RUN`) keep the switch, starting on "Mine".

## F6. Notes on each candidate

- A "Notes" panel on the candidate page: newest first, author name, role and relative time; a textarea (1 to 4,000 characters) with "Add note"; Ctrl/Cmd+Enter submits.
- Who: anyone who can open the session reads and adds notes. Authors delete their own notes; admins delete any. No editing (delete and re-add).
- Interviewers do not see notes; they have their own feedback form (F7).
- Each add and delete is audited (`CANDIDATE_NOTE_ADD`, `CANDIDATE_NOTE_DELETE`).

## F7. Hiring pipeline and interviewer roles

Stages a candidate moves through, in order. The existing `review_status` column carries them, so "shortlisted" keeps meaning "reached the shortlist or beyond":

| Stage | Label | How a candidate gets there |
|---|---|---|
| `NONE` | Screened | Scored, no decision |
| `SHORTLISTED` | Shortlisted | Recruiter shortlists (F2) |
| `CONTACTED` | Contacted | Recruiter: "Mark as contacted" |
| `INTERVIEW_SCHEDULED` | Interviewing | Automatic when the first interview is scheduled |
| `INTERVIEWED` | Interviewed | Automatic when every scheduled interview has submitted feedback |
| `OFFER` | Offer | Recruiter: "Move to offer", then uploads the offer PDF |
| `OFFER_APPROVED` | Offer approved | An approver approves the uploaded offer |

- New roles: `TECHNICAL_INTERVIEWER` (label "Technical interviewer") and `HR_INTERVIEWER` ("HR interviewer"). Admin assigns them on the Users page like any role.
- Interviews have a kind, `TECHNICAL` or `HR`, and a technical interview can only go to a technical interviewer, an HR interview only to an HR interviewer.
- Scheduling (session owner or admin, needs `PIPELINE_MANAGE`): kind, interviewer, date and time, duration (15 to 240 minutes, default 60), location or meeting link (optional, up to 500 characters), note to the interviewer (optional, up to 2,000). Allowed from `CONTACTED` onward and before `OFFER`. An interview can be rescheduled or cancelled until its feedback is submitted.
- Interviewers see only candidates with an interview assigned to them, on a "My interviews" page: upcoming first, then awaiting feedback, then done. Opening one shows the schedule, the candidate's score, strengths, gaps and resume, and the feedback form. They see nothing else in the app: no sessions, no other candidates, no notes.
- Feedback: recommendation (`STRONG_HIRE`, `HIRE`, `NO_HIRE`, `STRONG_NO_HIRE`), overall rating 1 to 5, written assessment (20 to 5,000 characters). Submitted once; it cannot be changed after submitting. Only the assigned interviewer submits it, and only once the interview's start time has passed.
- Offer: once at `OFFER`, the recruiter uploads one PDF (up to 10 MB). Uploading again replaces it until it is approved. Recruiters, HR chiefs and admins who can open the session see and download it.
- Approval needs `OFFER_APPROVE` (default: HR chief and admin). Whoever uploaded the offer cannot approve it. Approval moves the candidate to `OFFER_APPROVED` and is final.
- Every stage change is recorded with who and when (`candidate_stage_events`) and shown as a timeline on the candidate page. HR chiefs and recruiters follow candidates on a **Pipeline** page: every candidate past the shortlist, grouped by stage, filterable to "Offers awaiting approval".
- New permissions: `PIPELINE_MANAGE` "Contact candidates, schedule interviews and prepare offers" (default: admin, recruiter), `OFFER_APPROVE` "Approve job offers" (default: admin, HR chief), `INTERVIEW_FEEDBACK` "Give feedback on interviews assigned to them" (default: both interviewer roles). Interviewer roles get nothing else by default.

## F8. Analytics for the whole funnel

Written as a senior data analyst would want to read it: one question per block, every number with its denominator and its change against the previous period.

1. **Headline KPIs** (5): Resumes scored, Shortlist rate, Interviews completed, Offers approved, Median days shortlist to offer. Each shows the change against the previous period of the same length ("+12% vs prior 90 days"), green when the change is good for that metric and red when it is bad (lower is better for durations), always with an arrow and a sign, and the raw prior value in the tooltip.
2. **Hiring funnel**: Uploaded, Scored, Shortlisted, Contacted, Interviewed, Offer, Approved. For each step: count, conversion from the previous step, and drop-off. Counts are cohort-based: candidates whose session was created in the period, by the furthest stage reached.
3. **Time in stage**: median and 90th percentile days for Shortlisted to Contacted, Contacted to first interview, Interview to feedback, Interviewed to Offer, Offer to Approved. The slowest step is called out.
4. **Weekly throughput**: existing chart, adds interviews completed and offers approved as series.
5. **Score calibration**: for each AI score band, the share of candidates shortlisted and the share interviewed with a hire recommendation. This is the check that the AI ranking agrees with people.
6. **Interviewers**: per interviewer, interviews done, median hours from interview to feedback, mean rating and recommendation mix; split by technical and HR.
7. **Recruiters**: existing table adds Contacted, Offers and Shortlist to offer conversion.

Empty and partial data: any rate with a zero denominator shows "–" with "No data in this period" as its tooltip, never 0%.

## Out of scope (named so they are not built by accident)

Email or calendar invitations, a "rejected" stage or moving a candidate backwards past the shortlist, editing notes or submitted feedback, offer letter generation or salary fields, CSV export of analytics, a recruiter filter on analytics.
