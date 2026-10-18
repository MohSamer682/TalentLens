# TalentLens design language

Screening is a count, so the app reads like a results board. Dark is the base; `[data-theme='light']` is a mist-green twin. The theme follows `prefers-color-scheme`, is toggled in the navbar and stored in `localStorage('theme')`. `index.html` sets it before paint.

## Colour

Tokens are RGB triplets in `src/index.css`, exposed through `tailwind.config.ts`. Token names are unchanged from the previous system.

| Token | Dark | Job |
|---|---|---|
| `ground` / `surface` / `surface-raised` / `sunken` | #0C1110 / #121918 / #18211F / #090D0C | Page, panels, lifts/active segment, table heads |
| `line` / `line-strong` | #24302E / #3E4E4B | Hairline seams. Panels divide by lines, not shadows |
| `ink` / `ink-muted` | #E8EEEB / #8B9B96 | Text (muted is 6.4:1 on surface) |
| `accent` | #E8EEEB | Monochrome ink fill for primary buttons. Text on it is `accent-ink` |
| `accent-text` / `accent-bar` | #7CB7FF | Links, focus rings, data bars. The only blue |
| `mark` | #FFB81C | The call: **only** means "shortlisted". Dark text (`mark-ink`) on it |
| `info` | #2DD4BF | Scoring in progress |
| `success` | #4AC980 | Ranked, strengths |
| `warn` / `danger` | #F08C40 / #FF6B63 | Caution, failure |

Light twin: ground #F5F7F6, surface white, surface-raised white, sunken #F0F3F2, ink #0E1614, ink-muted #4F5F59, accent fill ink, links #1E5FC8, accent-bar #3478DC, mark unchanged, mark-text #825500. Light theme uses its own light glass navbar with a bottom hairline.

Rule: if something is saffron, it made the cut. Never use `mark` for decoration, warnings or hover.

## Type

- **Schibsted Grotesk** (`font-sans`, `font-display`): everything people read, titles with -0.025em tracking.
- **Geist Mono** (`font-mono`, `.display`): ranks and scores. Score weight encodes the value (380 + score x 4.4), so strong candidates look heavier at a glance.
- Sentence case labels. No all-caps or tracked eyebrows.

## Signature: the call

`.marker` is a saffron chip on every shortlisted rank (standings and candidate page). Shortlisted rows get a faint `mark-soft` wash. The shortlist is saved per candidate on the server, so it need not be contiguous and there is no cut-off rule. Selecting rows raises an inverted (`ink` on `ground`) bar at the bottom of the standings whose one saffron button is "Shortlist"; Escape clears the selection.

## Lens materials

- Radii scale: `DEFAULT 6px`, `md 10px`, `lg 14px`, `xl 20px`, `full` for pills.
- Three tiers:
  - **Plate** (`.plate`): panels and tables. Hairline border, lit top edge, soft layered shadow.
  - **Lift** (`.lift`): interactive elements you act on as a unit (offer cards, interview cards, selection bar, active segment). `surface-raised` background, deeper shadow, rises 1px on hover.
  - **Glass** (`.glass`): navbar and sticky bars only. Pitch/white with 16px blur; solid fallback where blur is off or with reduced transparency.
- Motion: `cubic-bezier(0.22, 1, 0.36, 1)`; 160 ms for state, 240 ms for enter, 320 ms for panels. Interactive cards rise 1px on hover. Everything drops travel under `prefers-reduced-motion`.

## Stages, places and approvals

- One stage vocabulary (`src/lib/pipeline.ts`): Shortlisted, Contacted, Interviewing, Interviewed, Offer, Offer approved, Offer sent, Accepted or Declined, Joining. `StageBadge` renders it everywhere (standings, candidate page, pipeline), so screens cannot disagree. Saffron stays for the cut: the position cell and row wash cover every stage from Shortlisted on, while the badge says where the candidate is.
- The candidate status line shows only the interviews the recruiter planned (HR, technical, management), then the offer steps.
- Interview place is HQ, Online or Other. Online shows as a real link that opens in a new tab. In list rows the row link is stretched from the title and the place sits above it (`relative z-10`), so the two links never nest.
- Offer approvals read as a short ordered list: department director (only when the recruiter asked for it), then HR chief. Rejecting opens an inline optional note, not a modal.

## Not yet done

Rank-change markers (a row that moves holds a marker until seen) are not built.
