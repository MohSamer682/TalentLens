# Design

<!-- Durable visual decisions for the Resume Screener UI. PRODUCT.md owns product truth; this file owns the look. -->

## World: the league table

Recruiters shortlist straight from the ranking, so the interface borrows from the one ranked table everyone reads by heart: a football league table. The world lends four things only: type, palette, density, and one signature move. Navigation, controls and page structure stay standard web components.

Scene: an in-house recruiter at an office desk in daylight, between meetings, working through one opening at a time. Light theme.

## Signature move: the shortlist line

On the ranked results, the recruiter sets **Shortlist line · top N**. Rows inside it get a filled position cell, and a labelled double rule is drawn under rank N, like a qualification line. The candidate page repeats it ("Inside the shortlist (top 5)") and the rank tile fills to match. N is a per-browser reading aid stored in `localStorage` (`shortlist:<sessionId>`); it never changes the ranking or the export.

## Color (Restrained, dark shell, mint accent)

Tokens live as CSS variables in `ui/src/index.css` and are mapped in `ui/tailwind.config.ts`. Never use raw Tailwind palette colors (`red-500`, `slate-900`) or hex in components.

| Token | Value | Job |
|---|---|---|
| `pitch` | #0B1412 | Header band: near-black with a green undertone. Text on it: white, secondary `pitch-ink` #9DB8AE |
| `ground` | #F5F7F6 | Page background, row hover |
| `surface` | #FFFFFF | Panels, tables, inputs |
| `sunken` | #EDF1EF | Table heads, neutral chips, segmented-control track |
| `line` / `line-strong` | #DFE5E2 / #C2CBC7 | Dividers, progress tracks / input borders |
| `ink` / `ink-muted` | #111916 / #5E6B66 | Text (17.9:1) / secondary text (5.6:1) |
| `accent` | #22C38A | Mint emerald fill: primary buttons, shortlist position chips, active counts. Always with `accent-ink` #04241B text (7.3:1), never white |
| `accent-strong` | #1BAE7A | Hover fill |
| `accent-text` | #03694C | Green text, links, focus rings, input focus borders, the shortlist rule (6.7:1 on white) |
| `accent-bar` | #159A6B | Data bars (3.6:1 on white) |
| `accent-soft` | #E3F8EE | Shortlist band, success backgrounds |
| `info` | #1D5FA8 | Scoring in progress |
| `danger` | #B42318 | Failed, destructive, scores under 50 |
| `warn` | #8A4B00 | Non-blocking notices (skipped files) |

Each state color has a `-soft` background pair. Every text pair above passes WCAG AA.

## Type

One family, self-hosted: **Archivo Variable** (`@fontsource-variable/archivo`, width axis). No request leaves the company network for a font.

- Body 15px, tables 14px, captions 13px. Page titles 24–28px bold. No size below 11px.
- **Condensed voice** (`.condensed`, `font-stretch: 72%`, uppercase, semibold): table column heads, position numbers, nav links, status chips, step labels. This is the league-table voice; never use it for sentences.
- All numbers in tables use tabular figures (`.num`, and `table` sets it globally).

## Shape and depth

Radius 8px on controls, 12px on panels, full pills for status badges, tags, nav links and the segmented filter. Panels carry a 1px `line` border plus the soft `shadow-panel`; nothing lifts on hover. Primary buttons have `shadow-button`; overlays use `shadow-menu`. No gradients on surfaces, no glass or blur.

## Components (ui/src/components/ui)

- `Button` – variants `primary | secondary | ghost | danger | dangerSolid`, sizes `sm | md | icon`. `buttonVariants` styles a `<Link>` as a button.
- `StatusBadge` – session status in the condensed voice: Not started, Scoring, Ranked, Failed. `Tag` for skills.
- `Panel`, `PanelHeader` – the only container.
- `Input`, `Textarea`, `Field` – labelled fields with hint and error slots (`aria-invalid`, `<id>-error`).
- `Alert` (danger, info, success), `Loader` (the ranking-bars mark), `SkeletonRows`, `ConfirmButton` (two-step destructive action that replaces `window.confirm`).
- `Segmented` – pill filter whose highlight slides to the chosen option.
- `Pagination`.

## Motion

Keyframes are in `tailwind.config.ts`; helpers (`useCountUp`, `stagger`) in `ui/src/lib/motion.ts`. Ease-out `cubic-bezier(0.16, 1, 0.3, 1)` everywhere; no bounce.

- **Focal moment, the results reveal:** ranking rows fade up in rank order (35ms apart, capped at 420ms), score bars grow from the left, then the shortlist line draws across. Moving the line redraws it.
- **Loading:** lists and tables show shimmering skeleton rows shaped like the content; whole pages without a content shape show the `Loader`, four ranking bars stretching in turn.
- **Live work:** progress bars carry moving stripes and ease to each new value; progress and summary numbers count up; the Scoring dot pings.
- **Feedback:** buttons press to 97% scale; the filter pill slides (300ms); colors transition in 150–160ms; pages fade in over 200ms; wizard steps fade up.
- **Reduced motion** (`prefers-reduced-motion`): fade-ups become plain fades; bar growth, shimmer, stripes and ping stop; the loader slows; numbers show their final value. All state changes stay visible.

## Layout

Top bar plus a content column of max 1280px (`MainLayout`, `width="narrow"` = 768px for the setup wizard). Lists are dense rows, not cards. On phones the ranking hides the five dimension columns and keeps position, candidate and score.
