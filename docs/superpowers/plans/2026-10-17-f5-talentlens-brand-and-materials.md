# F5 TalentLens Name, Icon, Lens Materials and HR Chief View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the app as TalentLens with its own icon, move every screen to softer "Lens" materials, give the light theme a proper design of its own, and open the HR chief straight onto all sessions as a viewer.

**Architecture:** Almost all of it is tokens. Radii, shadows and easing live in `tailwind.config.ts`; changing their values restyles every component that already uses `rounded-*` and `shadow-*`. Three material utilities in `index.css` (`.plate`, `.lift`, `.glass`) replace the ad hoc panel classes in shared components. The icon is one SVG used as favicon and as a `<BrandMark>` component.

**Tech Stack:** React 18, TypeScript, Tailwind 3, Vitest, Playwright (screenshots only).

**Spec:** [2026-10-17-talentlens-brief.md](2026-10-17-talentlens-brief.md), section F5. Mockups: TalentLens designs artifact, "Materials" and "Icon" sections.

## Global Constraints

- **Design spec is binding:** before coding any screen, analyse its artboard in the TalentLens designs (canvas or PDF) against [2026-10-17-talentlens-design-spec.md](2026-10-17-talentlens-design-spec.md), and finish with that spec's section 5 acceptance checklist in the PR. A screen that does not match its design is not done.
- Branch `feat/talentlens-materials` from `feat/roles-and-permissions`. One PR. The only backend change is migration `V8__hr_chief_view_only.sql` (Task 5).
- The PR body carries no Claude Code session link or anything tied to the session (see the brief).
- Product name in user-visible text: `TalentLens` (one word, capital T and L). Never "Talent Lens" or "Talentlens".
- Do not rename Maven modules, Java packages, `resume-screener-ui` package name, Docker services or database names.
- Dark colour tokens are unchanged except one new token `--surface-raised` (dark `24 33 31` #18211F, light `255 255 255`). Light tokens change as listed in Task 4.
- Radius scale: `DEFAULT 6px`, `md 10px`, `lg 14px`, `xl 20px`, `full` for pills.
- Easing: `cubic-bezier(0.22, 1, 0.36, 1)`; durations 160 ms (state, the Tailwind default), 240 ms (enter), 320 ms (panels).
- Saffron (`mark`) still means only "shortlisted or further". The icon's saffron bar is the one sanctioned brand use.
- No new npm dependencies.

## Review Focus

1. **Browsers without `backdrop-filter`, and `prefers-reduced-transparency`**: the navbar must be a solid pitch bar, never see-through text over content. Test: Task 2 screenshot with the `@supports` block disabled, and the CSS rule itself.
2. **Light theme shadows on white panels**: panels must still separate from the mist ground at 360 px. Test: Task 3 light screenshots of Dashboard and Session.
3. **Pills with long labels at 360 px** (segmented control "Shortlisted 128", status chips): no overflow or horizontal page scroll. Test: Task 3 360 px screenshots, `document.documentElement.scrollWidth <= 360` assertion in the screenshot script.
4. **Reduced motion**: hover lift and enter animations drop travel. Test: Task 2 CSS rule under `prefers-reduced-motion`.
5. **HR chief with no sessions of their own** opens the app: lands on All sessions, never on an empty "No sessions yet" page or a "New session" button that 403s. Test: Task 5 `defaultScopeIsEveryoneForViewers`, `hrChiefCannotCreateSessions`.
6. **Light theme contrast** on chips, muted text and table heads: 4.5:1 or better. Test: Task 4 `lightTokensMeetContrast`.
7. **Old name left behind** in titles, aria-labels or docs. Test: Task 1 `grep -ri "resume screener" ui/src ui/index.html README.md PRODUCT.md` returns nothing.

---

### Task 1: TalentLens name and icon

**Files:**
- Create: `ui/public/favicon.svg` (replace if present), `ui/src/components/BrandMark.tsx`
- Modify: `ui/index.html` (title, theme-color stays), `ui/src/components/Navbar.tsx:39-44`, `ui/src/layouts/AuthLayout.tsx:12`, `README.md`, `PRODUCT.md`, `api-service/src/main/java/com/resumescreener/api/config/OpenApiConfig.java` (title "TalentLens API")
- Test: `ui/src/components/BrandMark.test.tsx`

**Interfaces:**
- Produces: `BrandMark({ size?: number; className?: string }): JSX.Element` rendering the icon inline with `aria-hidden="true"`; the link around it carries the accessible name "TalentLens, home".

- [ ] **Step 1: Write the failing test**

```tsx
it('renders the lens mark with the saffron bar and no accessible name of its own', () => {
  const { container } = render(<BrandMark size={28} />);
  const svg = container.querySelector('svg')!;
  expect(svg.getAttribute('aria-hidden')).toBe('true');
  expect(svg.getAttribute('width')).toBe('28');
  expect(container.querySelectorAll('rect[data-bar]').length).toBe(3);
});
```

- [ ] **Step 2: Run** `cd ui && npx vitest run src/components/BrandMark.test.tsx` — expect FAIL (module not found).

- [ ] **Step 3: Write the icon.** `ui/public/favicon.svg` is exactly this; `BrandMark` renders the same shapes, with `data-bar` on the three bars and fills from tokens (`rgb(var(--ink))` ring, `rgb(var(--ink-muted))` short bars, `rgb(var(--mark))` tall bar) on a `#0E1614` tile so it reads on the dark shell in both themes:

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">
  <rect width="64" height="64" rx="16" fill="#0E1614"/>
  <circle cx="28" cy="28" r="15" fill="none" stroke="#E8EEEB" stroke-width="5"/>
  <path d="M39.5 39.5 50 50" stroke="#E8EEEB" stroke-width="6.5" stroke-linecap="round"/>
  <rect x="19.5" y="29" width="4.5" height="6" rx="1.5" fill="#8B9B96"/>
  <rect x="25.75" y="25" width="4.5" height="10" rx="1.5" fill="#8B9B96"/>
  <rect x="32" y="19.5" width="4.5" height="15.5" rx="1.5" fill="#FFB81C"/>
</svg>
```

- [ ] **Step 4: Replace the name.** Navbar: `BrandMark size={28}` replaces the `ListOrdered` tile; wordmark text `TalentLens`; link `aria-label="TalentLens, home"`. AuthLayout: `BrandMark size={36}` plus `TalentLens`. `index.html` `<title>TalentLens</title>`. README and PRODUCT.md: product name only, keep the repo URL.

- [ ] **Step 5: Verify**
  Run: `cd ui && npx vitest run && npm run build && grep -ri "resume screener" src index.html ../README.md ../PRODUCT.md`
  Expected: tests PASS, build succeeds, grep prints nothing.

- [ ] **Step 6: Commit** `feat(ui): TalentLens name and lens icon`

---

### Task 2: Lens material tokens

**Files:**
- Modify: `ui/tailwind.config.ts:36-66` (borderRadius, boxShadow, transitionTimingFunction, animation durations, new `surface-raised` colour), `ui/src/index.css` (token, three material utilities, reduced-motion and reduced-transparency rules), `ui/DESIGN.md` (rewrite "Shape and motion" as "Lens materials"; title becomes "TalentLens design language")

**Interfaces:**
- Produces: utilities `.plate`, `.lift`, `.glass`; Tailwind `shadow-plate`, `shadow-lift`, `shadow-menu` (kept), `ease-lens`, `rounded-xl`, colour `surface-raised`. Task 3 uses these names.

- [ ] **Step 1: Set the tokens.**
  - `borderRadius`: `DEFAULT '6px'`, `md '10px'`, `lg '14px'`, `xl '20px'`.
  - `boxShadow.plate`: `'inset 0 1px 0 rgb(255 255 255 / 0.04), 0 1px 2px rgb(var(--shadow) / 0.06), 0 2px 8px -2px rgb(var(--shadow) / 0.08)'`
  - `boxShadow.lift`: `'inset 0 1px 0 rgb(255 255 255 / 0.06), 0 2px 4px rgb(var(--shadow) / 0.08), 0 12px 32px -8px rgb(var(--shadow) / 0.28)'`
  - `boxShadow.panel` becomes an alias of `plate` so untouched call sites follow.
  - `transitionTimingFunction.lens` and the existing `out` both `cubic-bezier(0.22, 1, 0.36, 1)`; `fade-up` animation 240 ms.
- [ ] **Step 2: Add the utilities** in `@layer components`:
  - `.plate` = `rounded-lg border border-line bg-surface shadow-plate`
  - `.lift` = `rounded-lg border border-line bg-surface-raised shadow-lift transition-[transform,box-shadow] duration-200 ease-lens`, with `:hover` `translateY(-1px)` only inside `@media (hover: hover)`
  - `.glass` = `background: rgb(var(--pitch) / 0.78); backdrop-filter: blur(16px) saturate(140%)`, wrapped in `@supports (backdrop-filter: blur(1px))`; outside it, and under `@media (prefers-reduced-transparency: reduce)`, `background: rgb(var(--pitch))`.
  - Under `prefers-reduced-motion: reduce`: `.lift:hover { transform: none }`.
- [ ] **Step 3: Verify** `cd ui && npm run build` succeeds and `npx vitest run` passes (no behaviour change).
- [ ] **Step 4: Commit** `feat(ui): lens material tokens`

---

### Task 3: Apply materials to shared components and screens

**Files:**
- Modify: `ui/src/components/ui/Card.tsx` (`Panel` uses `.plate`), `ui/src/components/ui/Button.tsx` (radius `rounded-md`, `ease-lens`, pressed `scale-[0.98]` only without reduced motion), `ui/src/components/ui/Input.tsx` (`rounded-md`, sunken fill, `focus:border-accent-text`), `ui/src/components/ui/Segmented.tsx` (pill track `rounded-full bg-sunken p-1`; active option `rounded-full bg-surface-raised shadow-plate`), `ui/src/components/ui/Badge.tsx` (`rounded-full`), `ui/src/components/Navbar.tsx` (`glass` instead of `bg-pitch`), `ui/src/components/ShortlistBar.tsx` (`lift rounded-xl`), `ui/src/components/SessionCard.tsx` (`.lift` when it is a link), `ui/src/components/ui/Feedback.tsx` (Alert `rounded-lg`); the five call sites from `grep -rn "rounded-lg border border-line" ui/src` switch to `Panel` or `.plate`.
- Create: `maintenance/screenshots.mjs` if it does not exist on the branch (Playwright, `executablePath` from `PLAYWRIGHT_BROWSERS_PATH`), writes `docs/screenshots/f5/<page>-<theme>-<width>.png`.

**Interfaces:**
- Consumes: Task 2 utilities.

- [ ] **Step 1: Apply the classes listed above.** Layout, spacing and copy do not change; only material classes.
- [ ] **Step 2: Run** `cd ui && npx vitest run && npm run build` — expect PASS.
- [ ] **Step 3: Screenshot pass.** Start the stack (`docker compose up`, stub LLM `maintenance/fake_llm_server.py`), run `node maintenance/screenshots.mjs` for Login, Dashboard, Session, Candidate, Analytics, Admin roles at 360 and 1280, dark and light. The script asserts `document.documentElement.scrollWidth <= innerWidth` on each page and exits non-zero otherwise. Then run once with `--no-backdrop` (injects `.glass{backdrop-filter:none!important;background:rgb(var(--pitch))!important}`) for the navbar.
  Expected: exit 0; navbar text readable over scrolled content in every shot.
- [ ] **Step 4: Commit** `feat(ui): lens materials across shared components`

---

### Task 4: Light theme, reworked

**Files:**
- Modify: `ui/src/index.css` (`[data-theme='light']` block), `ui/src/components/Navbar.tsx` (light glass in light theme), `ui/src/index.css` `.glass` (light variant), `ui/index.html` (`theme-color` follows the theme), `ui/DESIGN.md` (light section)
- Create: `ui/src/lib/contrast.ts`, `ui/src/lib/contrast.test.ts`

**Interfaces:**
- Produces: `contrastRatio(fg: string, bg: string): number` (WCAG relative luminance, hex in).

- [ ] **Step 1: Write the failing test** `lightTokensMeetContrast`: parse the light block of `index.css` (read the file in the test with `fs`), and assert `contrastRatio(ink, ground) >= 4.5`, `contrastRatio(inkMuted, surface) >= 4.5`, `contrastRatio(inkMuted, sunken) >= 4.5`, `contrastRatio(accentText, surface) >= 4.5`, `contrastRatio(markText, markSoft) >= 4.5`, and for each status tint pair (`success`/`success-soft`, `info`/`info-soft`, `warn`/`warn-soft`, `danger`/`danger-soft`) `>= 4.5`.
- [ ] **Step 2: Run** `cd ui && npx vitest run src/lib/contrast.test.ts` — expect FAIL.
- [ ] **Step 3: Set the light tokens** (RGB triplets in the file, hex here): `ground #F5F7F6`, `surface #FFFFFF`, `surface-raised #FFFFFF`, `sunken #F0F3F2`, `line #DDE4E1`, `line-strong #B4C2BC`, `ink #0E1614`, `ink-muted #4F5F59`, `accent-text #1E5FC8`, `accent-bar #3478DC`, `shadow #0E1614`; status pairs keep their hues with text darkened until the test passes. Light shadows: `plate` = `0 1px 2px rgb(var(--shadow) / 0.05), 0 4px 12px -4px rgb(var(--shadow) / 0.08)`, `lift` = `0 2px 4px rgb(var(--shadow) / 0.06), 0 16px 32px -12px rgb(var(--shadow) / 0.18)` (no inset highlight in light). Light `.glass`: `rgb(255 255 255 / 0.8)` with the same blur, a `line` bottom hairline, ink text; nav links `ink-muted`, active `ink` with the saffron underline. Drop the "shell stays dark" rule from DESIGN.md; the brand tile stays dark.
- [ ] **Step 4: Run** `cd ui && npx vitest run && npm run build` — expect PASS. Re-run the Task 3 screenshot script for the light shots.
- [ ] **Step 5: Commit** `feat(ui): reworked light theme with light glass shell`

---

### Task 5: HR chief opens on all sessions, view only

**Files:**
- Create: `common/src/main/resources/db/migration/V8__hr_chief_view_only.sql` (`DELETE FROM role_permissions WHERE role = 'HR_CHIEF' AND permission = 'SESSION_RUN';`)
- Modify: `ui/src/lib/permissions.ts`, `ui/src/lib/permissions.test.ts`, `ui/src/pages/Dashboard.tsx:32-80`, `ui/src/components/SessionRow.tsx` and `SessionCard.tsx` (show `ownerName`, which the API already fills when `everyone=true`), `api-service/src/test/java/com/resumescreener/api/auth/PermissionEnforcementTest.java`

**Interfaces:**
- Produces: `defaultScope(user: User | null): 'MINE' | 'EVERYONE'` — `EVERYONE` when the user has `SESSION_VIEW_ALL` and not `SESSION_RUN`; `canSwitchScope(user)` — has both.

- [ ] **Step 1: Write the failing tests.**

```ts
it('defaultScopeIsEveryoneForViewers', () => {
  expect(defaultScope(user('HR_CHIEF', ['SESSION_VIEW_ALL', 'ANALYTICS_VIEW']))).toBe('EVERYONE');
  expect(canSwitchScope(user('HR_CHIEF', ['SESSION_VIEW_ALL', 'ANALYTICS_VIEW']))).toBe(false);
  expect(defaultScope(user('ADMIN', ALL))).toBe('MINE');
  expect(canSwitchScope(user('ADMIN', ALL))).toBe(true);
  expect(defaultScope(user('RECRUITER', ['SESSION_RUN', 'CANDIDATE_REVIEW']))).toBe('MINE');
});
```

```java
@Test void hrChiefCannotCreateSessions()   // after V8 seed: HR_CHIEF POST /api/sessions -> 403; GET /api/sessions?everyone=true -> 200
```

- [ ] **Step 2: Run** `cd ui && npx vitest run src/lib/permissions.test.ts` and `mvn -pl api-service -am test -Dtest=PermissionEnforcementTest` — expect FAIL.
- [ ] **Step 3: Implement.** Dashboard initialises `everyone` from `defaultScope(me)`; when `!canSwitchScope(me)` it hides the Mine / All recruiters control, titles the page "All sessions", subtitle "Every recruiter's sessions. You can open them; recruiters run them." and shows the owner on each row. "New session" already hides without `SESSION_RUN`; check the empty state also drops its "Start a session" button. Mockup: TalentLens designs, "Dashboard, HR chief, light".
- [ ] **Step 4: Run** both test commands, then `mvn -q verify` and `cd ui && npm run build` — expect PASS.
- [ ] **Step 5: Commit** `feat(auth): HR chief opens on all sessions and no longer runs them`
