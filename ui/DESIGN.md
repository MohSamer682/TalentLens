# Resume Screener design language: Results Night

Screening is a count, so the app reads like a results board. Dark is the base; `[data-theme='light']` is a mist-green twin. The theme follows `prefers-color-scheme`, is toggled in the navbar and stored in `localStorage('theme')`. `index.html` sets it before paint.

## Colour

Tokens are RGB triplets in `src/index.css`, exposed through `tailwind.config.ts`. Token names are unchanged from the previous system.

| Token | Dark | Job |
|---|---|---|
| `ground` / `surface` / `sunken` | #0C1110 / #121918 / #090D0C | Page, panels, table heads |
| `line` / `line-strong` | #24302E / #3E4E4B | Hairline seams. Panels divide by lines, not shadows |
| `ink` / `ink-muted` | #E8EEEB / #8B9B96 | Text (muted is 6.4:1 on surface) |
| `accent` | #E8EEEB | Monochrome ink fill for primary buttons. Text on it is `accent-ink` |
| `accent-text` / `accent-bar` | #7CB7FF | Links, focus rings, data bars. The only blue |
| `mark` | #FFB81C | The call: **only** means "shortlisted". Dark text (`mark-ink`) on it |
| `info` | #2DD4BF | Scoring in progress |
| `success` | #4AC980 | Ranked, strengths |
| `warn` / `danger` | #F08C40 / #FF6B63 | Caution, failure |

Light twin: ground #EEF2F0, surface white, ink #0E1614, accent fill ink, links #1E5FC8, mark unchanged, mark-text #825500. The shell bar stays dark in both themes.

Rule: if something is saffron, it made the cut. Never use `mark` for decoration, warnings or hover.

## Type

- **Schibsted Grotesk** (`font-sans`, `font-display`): everything people read, titles with -0.025em tracking.
- **Geist Mono** (`font-mono`, `.display`): ranks and scores. Score weight encodes the value (380 + score x 4.4), so strong candidates look heavier at a glance.
- Sentence case labels. No all-caps or tracked eyebrows.

## Signature: the call

`.marker` is a saffron chip on every shortlisted rank (standings and candidate page). Shortlisted rows get a faint `mark-soft` wash. A saffron rule with the label "The call: top N shortlisted" is drawn under the last shortlisted row.

## Shape and motion

Radii 4 / 6 / 8px. Panels have a hairline border and almost no shadow. Rows enter with a short leftward slide (360ms, exponential ease-out) staggered per row. Score bars and the call line grow once. Live progress stripes remain. All loops drop under `prefers-reduced-motion`.

## Not yet done

Rank-change markers (a row that moves holds a marker until seen) are not built.
