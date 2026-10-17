# TalentLens design spec

The binding visual spec for F5 to F8. Source of truth: the **TalentLens designs** canvas (https://claude.ai/artifact/PSLJQvTKcKs4zHHnbSprTH) and its PDF export. Every screen below must be analysed against its artboard before coding and match it when done. Where this file and the artboard disagree, this file wins and the artboard gets fixed.

Artboards: Brand and Lens materials · Analytics · Candidate page · Pipeline · My interviews (light) · Interview feedback · All sessions, HR chief (light).

## How to use this spec

1. **Before coding a screen**: open its artboard (or PDF page), read its section here, and list in the task's notes every region, component and state it shows.
2. **While coding**: use tokens and shared components only (no raw hex in TSX). Copy text exactly as written here; sample names and numbers in the mockups are not copy.
3. **Done means**: screenshots at 360, 768 and 1280 px, dark and light, placed next to the artboard; every item in the screen's checklist ticked; the global checklist at the end ticked. A screen that does not match is not done, even if its tests pass.
4. Anything the design does not show (an error, a long name, an empty list) follows the "States" rules here, never improvisation.

## 1. Foundations

### Type

| Role | Font | Size / weight | Notes |
|---|---|---|---|
| Page title (h1) | Schibsted Grotesk | 30 / 700, tracking −0.025em | one per page |
| Section title (panel h2) | Schibsted Grotesk | 15 / 600 | phrased as the question the panel answers on Analytics |
| Body | Schibsted Grotesk | 15 / 400, line-height 1.5 | 14 in tables and lists |
| Caption, meta | Schibsted Grotesk | 12.5 / 400–500, `ink-muted` | |
| Numbers (scores, KPIs, counts, ranks) | Geist Mono, tabular | KPI 30 / 600, score 36–44 / 700, table 14 | always mono |
| Chips | Schibsted Grotesk | 12 / 600 | |

Six sizes only: 12.5, 14, 15, 17 (wordmark), 30, plus mono display sizes.

### Colour tokens

Dark is unchanged from Results Night plus `surface-raised`. Light is reworked.

| Token | Dark | Light | Use |
|---|---|---|---|
| `ground` | #0C1110 | #F5F7F6 | page |
| `surface` | #121918 | #FFFFFF | plates |
| `surface-raised` | #18211F | #FFFFFF | lifts, active segment |
| `sunken` | #090D0C | #F0F3F2 | table heads, inputs (dark), segmented track |
| `line` / `line-strong` | #24302E / #3E4E4B | #DDE4E1 / #B4C2BC | hairlines / input and button borders |
| `ink` / `ink-muted` | #E8EEEB / #8B9B96 | #0E1614 / #4F5F59 | text |
| `accent` (button fill) | #E8EEEB | #0E1614 | primary buttons |
| `accent-text` | #7CB7FF | #1E5FC8 | links, focus ring |
| `accent-bar` | #7CB7FF | #3478DC | every data bar |
| `mark` | #FFB81C | #FFB81C | shortlisted (and the icon's tall bar), nothing else |
| `success` | #4AC980 | #13653C on #DFF3E7 | ranked, hire, approved, good delta |
| `info` | #2DD4BF | #08675E on #DAF2EF | scoring, interviewing |
| `warn` | #F08C40 | #964600 on #FBEEDC | slowest step, biggest drop, feedback due, offer stage |
| `danger` | #FF6B63 | #A81E1A on #FDE9E7 | failed, no hire, bad delta, cancel |

Rules: saffron means "reached the shortlist or beyond" and is used only on the rank marker, the Shortlisted stage dot, shortlisted counts and the Shortlisted chip. Blue is never a chip or a button. Status is never colour alone: every chip has a word, and every delta has an arrow and a sign.

### Shape, materials, motion

- Radii: 6 (small chips, rank marker), 10 (buttons, inputs, inner cards), 14 (plates), 20 (large tiles), pill (chips, segmented controls, stage track).
- **Plate**: panels and tables. Dark: `surface`, 1px `line`, top highlight `inset 0 1px 0 rgb(255 255 255 / .04)`, shadow `0 2px 8px -2px` black 40%. Light: white, 1px `line`, `0 1px 2px` 5% + `0 4px 12px -4px` 8%.
- **Lift**: things you act on as a unit (offer cards, interview cards, selection bar, active segment). `surface-raised`, `line-strong` border, deeper shadow; hover rises 1px (only with hover and without reduced motion).
- **Glass**: the navbar and sticky bars only. Dark: pitch at 78–82% with 16px blur. Light: white at 80% with blur and a `line` bottom border, ink text. Solid fallback without `backdrop-filter` or with reduced transparency.
- Motion: `cubic-bezier(0.22, 1, 0.36, 1)`; 160 ms state, 240 ms enter, 320 ms panels; bars grow once on first load. Reduced motion keeps fades, drops travel and growth.

### Shared components

| Component | Spec |
|---|---|
| Navbar | 56px, glass. Brand mark 28px + "TalentLens" 17/700. Links 15/600, active link `ink` (white in dark) with a 3px saffron underline. Right side: user name · role, 12.5 muted. Links by permission: Sessions, Pipeline, My interviews, Analytics, Admin. |
| Primary button | 40px (44 on the feedback submit), radius 10, `accent` fill, `accent-ink` text 14/600. One per view region. |
| Secondary button | 40px, radius 10, transparent, 1px `line-strong`. Destructive variant: `danger` text and a dark-red border. |
| Segmented control | pill track `sunken`, 4px padding; options 13px; active option `surface-raised` + small shadow, 600 weight. Optional mono count after the label. `role="radiogroup"`. |
| Chip | pill, 2px 9px, 12/600, soft tint background + strong text from the table above. |
| Table | plate; head row `sunken`, 12.5/500 muted; rows 14px with 12–13px vertical padding and a `line` top border; numbers right aligned in mono; first and last cell 20px inset. Under 640px tables become cards (Analytics, Pipeline) or scroll in their panel. |
| Inputs | 40px, radius 10, 1px `line`, `sunken` fill (dark) / white (light), label above 13/500; focus 2px `accent-text` ring. |

## 2. Screens

### 2.1 Brand (F5)

- Icon: exact SVG in the F5 plan; tile #0E1614 in both themes. Sizes 16 (favicon, simplified: ring, handle and saffron bar only, border #3E4E4B), 28 (navbar), 36 (auth), 64+ (app tile).
- The wordmark is always "TalentLens", never split or recoloured.
- Checklist: favicon readable on light and dark browser tabs; navbar link has `aria-label="TalentLens, home"`; icon `aria-hidden`.

### 2.2 All sessions, HR chief (F5, light artboard)

- Header: h1 "All sessions"; subtitle "Every recruiter's sessions. You can open them; recruiters run them."; search field on the right (260px min, icon left, placeholder "Search by role or recruiter").
- Status segmented control: All, Ranked, Scoring, Failed, each with its count.
- Table columns: Session (link, 600), Recruiter, Status chip, Resumes, Shortlisted (saffron count chip, "–" when none), Created.
- No Mine/All switch and no New session button for this user.
- Checklist: rows open the session read-only; at 360px rows become cards (title, recruiter, status, counts); empty state "No recruiter has a session yet".

### 2.3 Candidate page: pipeline and notes (F6, F7)

Layout: back link; header row (saffron rank marker when shortlisted or beyond, name h1, email · "Shortlisted by X on date", score 44 mono "/ 100", Open resume secondary); then two columns: main (`flex: 999 1 560px`) and aside (`flex: 1 1 340px`), stacking under ~920px with the aside after the main column.

Main column, top to bottom:
1. **Hiring pipeline** plate. Header right: current stage chip with progress ("Interviewing · 1 of 2 done").
   - Stage track: 7 pills in order. Reached = `sunken`-tinted pill with a dot (Shortlisted's dot saffron, others muted); current = tinted pill with a 1px coloured border and `aria-current="step"`; future = dashed `line-strong` border, muted text. Wraps on narrow screens.
   - Interviews: one lift card each. Line 1: kind chip (neutral), interviewer name (600), date and time range with time zone. Done: recommendation chip (Strong hire/Hire green, No hire orange, Strong no hire red), rating "★★★★☆ 4/5" with `aria-label`, submitted time, assessment text. Upcoming: location (a link when it is a URL) and "Waiting for the interview", with Reschedule and Cancel (destructive) buttons.
   - Next action: one primary button chosen by stage (copy in the F7 plan). When scheduling, the inline form shows in a `sunken` panel: Kind segmented, Interviewer select (filtered by kind), Date and time, Duration, Location, Note to the interviewer, then Cancel + "Schedule interview".
   - Offer (at Offer stage): lift card with a document icon, file name, "uploaded by X, when", Download, Replace (manager) or Approve offer (approver), or "Waiting for approval".
   - Timeline: vertical list with a 2px `line` rule, newest first, "**Stage** · what, by whom · date"; the Shortlisted entry in saffron text.
2. **Strengths and gaps** plate: two lists side by side, stacking under 560px.

Aside:
1. **Notes** plate: title with mono count; textarea (label "New note", placeholder "Add a note for the team…"), hint "Ctrl + Enter to add", "Add note" disabled until text; list newest first: author 600, "Role · relative time" caption, Delete as a small text button only on deletable notes; body `pre-wrap`.
2. **Score breakdown** plate: dimension name, 8px bar, mono value.

Checklist: buttons hidden (not disabled) without permission; interviewer never sees this page; long names wrap without breaking the header; a candidate with no pipeline activity shows the track with only Screened reached and no empty panels.

### 2.4 Pipeline (F7)

- Header: h1 "Pipeline"; subtitle "Every candidate past the shortlist, across all sessions. Longest waiting first." (for recruiters: "…across your sessions…").
- Approver panel (only with `OFFER_APPROVE` and at least one waiting offer): plate with a stronger border, title "N offers waiting for your approval", offer lift cards in a responsive grid (min 420px): icon, "Name · Role", file and uploader caption, Download + Approve offer.
- Filters: stage segmented with counts (scrolls horizontally inside its track on phones) and an "Awaiting approval only" checkbox.
- Table: Candidate (link), Role, Score, Stage chip, In stage (days, orange from 5 days), Next (muted text of the next step or the next interview).
- Checklist: empty state "Nobody is past the shortlist yet. Shortlist candidates in a session to start the pipeline."; approve shows a confirm "Approve the offer for Name? This can't be undone." and then removes the card.

### 2.5 My interviews (F7, light artboard)

- Narrower container (1080px). Navbar shows only "My interviews" plus the user's name · role.
- Groups in order, each a plate with a count chip: "Waiting for your feedback" (rows tinted warm, action "Give feedback →" in warn text), "Upcoming" ("Prepare →" in link colour), "Done" (recommendation chip with rating).
- Row: 64px date tile (weekday caption + mono day), name 16/600, "Role · time range · location" caption, kind chip, action. The whole row is one link with a visible focus ring.
- Checklist: groups with no rows are hidden; if all are empty show "No interviews assigned to you yet. They'll appear here when a recruiter schedules one."

### 2.6 Interview feedback (F7)

- Header: back link "← My interviews"; h1 candidate name; caption "Role · Kind interview · date, time range · location"; status chip "Feedback due" (warn), "Scheduled" (neutral) or "Submitted" (success).
- Left (aside, 340px basis): "From the recruiter" plate (note to the interviewer, recruiter name in the header); "AI screen" plate (score, rank, Strengths in success text, "Gaps to probe" in warn text, Open resume).
- Right: "Your feedback" form plate, header caption "Visible to recruiters and the HR chief".
  - Recommendation: four large radio cards in a grid (min 150px): Strong hire "Raise the bar", Hire "Meets the bar", No hire "Below the bar", Strong no hire "Clear concerns". Selected = tinted fill, 1px border and ring in its colour. Real radio inputs, keyboard operable.
  - Overall rating: five 48×44 pill radios 1–5, selected filled with `accent`; caption "1 poor · 3 meets · 5 exceptional".
  - Assessment textarea, 180px min, caption "What you saw, with examples. At least 20 characters." and a mono counter "n / 5,000".
  - Footer: "Feedback can't be changed after you submit it." and primary "Submit feedback" (disabled until valid), then the confirm dialog from the F7 plan.
- Before start time the form is replaced by "You can give feedback once the interview has started at <time>". After submit the form becomes the read-only feedback.

### 2.7 Analytics (F8)

Order and titles are fixed:
1. Header: h1 "Recruiting analytics"; caption "Candidates from sessions created <from> to <to> · N sessions · N ranked · N scoring · N failed"; period segmented 30 days / 90 days / 12 months.
2. KPI strip: one plate, five cells divided by hairlines (2 per row on phones): label, mono value 30/600, delta chip (arrow + signed %, tone per the F8 plan), caption ("vs N in prior 90 days" etc.).
3. Two plates side by side (stack under ~1100px): "Where do candidates drop out?" (seven rows: label 96px, bar, mono count, mono conversion; the Shortlisted count in saffron text; "Biggest drop · N lost" warn chip on the lowest-conversion step after the shortlist) and "Where do they wait?" (five rows: label, pill track with median bar and a p90 tick, "median · p90" mono; slowest step bar in warn and a warn callout sentence under the rows).
4. Two plates: "How much moved each week?" (scored bars in `accent-bar`, interview dots in `info`, offer diamonds in `success`, legend in the header, axis dates, the current partial week at 55% opacity; scrolls inside the panel, starting at the latest week) and "Does the AI score agree with people?" (per band two thin bars, shortlist rate in `accent-bar` and hire-recommendation rate in `success`, caption "x% · y% of n", "–" with tooltip when no data, explanatory caption under it).
5. "How are interviews going?" plate with All / Technical / HR segmented in the header; table with a 4-part recommendation bar and its counts, slow feedback (over 48 h) in warn text; footer legend.
6. "Recruiters" table with the added Contacted, Offers and Shortlist to offer columns.

Checklist: no chart library; every bar has its number next to it; the empty state copy from F3 stays; loading shows skeleton plates in the same grid.

## 3. States every screen must handle

| State | Rule |
|---|---|
| Loading | Skeleton blocks in the final layout's shapes; no spinners on whole pages |
| Empty | One sentence on why it's empty plus what to do next; no illustration |
| Error | `Alert` with the server message and "Try again"; form input is never cleared on error |
| Saving | The acting button shows a spinner and is disabled; everything else stays usable |
| Long text | Names and titles wrap (`break-words`), never overflow; tables keep `nowrap` cells but scroll in their panel |
| No permission | The control is not rendered; the page never shows a button that will 403 |
| Narrow (360px) | No horizontal page scroll; columns stack in the order main then aside; tables scroll in their panel or become cards as listed |

## 4. Accessibility

- Text 4.5:1 and UI 3:1 in both themes (F5 Task 4 test enforces the light pairs).
- Every control is a real `button`, `a`, `input` or `select`; radio groups use `role="radiogroup"` or native radios; icon-only buttons have `aria-label`.
- Touch targets 44px tall on phones (40px buttons get 2px extra padding under `sm`).
- Visible focus on every interactive element, including table rows that are links.
- Charts have their numbers as text; colours are never the only signal.

## 5. Design acceptance checklist (every PR from F5 to F8)

- [ ] Each changed screen was compared with its artboard; differences are fixed or listed in the PR with a reason.
- [ ] Screenshots at 360, 768 and 1280 px, dark and light, are in `docs/screenshots/<feature>/` and linked in the PR.
- [ ] No raw hex in `ui/src` outside `index.css` and the icon (`grep -rn "#[0-9A-Fa-f]\{6\}" ui/src --include=*.tsx` is empty apart from `BrandMark`).
- [ ] Saffron appears only where section 1 allows.
- [ ] All states in section 3 were exercised on the real stack.
- [ ] The copy matches this spec word for word.
