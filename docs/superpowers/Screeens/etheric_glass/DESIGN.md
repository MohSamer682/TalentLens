# Design System Specification: The Ethereal Interface

## 1. Overview & Creative North Star
**Creative North Star: "The Digital Curator"**

This design system is built to move away from the rigid, boxy constraints of traditional SaaS and Web3 interfaces. Instead of a "dashboard," we are building a "gallery." The goal is an editorial experience that feels breathable, prestigious, and humanized. 

We break the "template" look through **Intentional Asymmetry** and **Tonal Depth**. By utilizing massive corner radii (`rounded-xl` to `rounded-3xl`) and generous whitespace, we signal to the user that space is a luxury. We avoid structural clutter by favoring overlapping "glass" surfaces and organic mesh gradients over traditional lines.

---

## 2. Colors & Surface Philosophy

The palette is rooted in a "High-Value Light Mode" that uses subtle temperature shifts to guide the eye.

### The "No-Line" Rule
**Explicit Instruction:** You are prohibited from using 1px solid borders (`#000` or high-contrast grays) for sectioning. Boundaries must be defined solely through:
1.  **Background Shifts:** Placing a `surface-container-low` section against a `surface` background.
2.  **Tonal Transitions:** Using the `surface-variant` to imply a change in context.
3.  **Soft Shadows:** Letting a container "float" via ambient occlusion rather than a stroke.

### Surface Hierarchy & Nesting
Treat the UI as a physical stack of semi-transparent sheets. 
- **Base Layer:** `surface` (#f5f7f9) - The foundation.
- **Sectioning:** `surface-container-low` (#eef1f3) - For large secondary areas.
- **Interactive Cards:** `surface-container-lowest` (#ffffff) - Pure white, reserved for the highest level of focus.
- **Nested Content:** Use `surface-container` or `surface-container-high` for inner modules to create "inset" depth.

### The "Glass & Gradient" Rule
To achieve a "Signature" feel, floating elements (modals, dropdowns, navigation) must utilize **Glassmorphism**:
- **Background:** `on-primary` at 60% opacity.
- **Effect:** `backdrop-blur` (20px - 40px).
- **Edge:** A "Ghost Border" using `outline-variant` at 15% opacity.

### Signature Textures
Main CTAs and Hero sections must not be flat. Use a linear gradient from `primary` (#4647d3) to `secondary` (#8126cf) at a 135-degree angle to provide "visual soul."

---

## 3. Typography: Editorial Tech

We use **Inter** as our typographic anchor. To achieve a high-end feel, we prioritize "Air."

*   **Display Scale:** Use `display-lg` (3.5rem) with `-0.02em` letter spacing for a compact, authoritative look in hero sections.
*   **Reading Rhythm:** All `body-lg` text must have a line-height of `1.6` or higher. This prevents the "dense block" look common in low-end SaaS.
*   **The Label Rule:** `label-sm` and `label-md` should be used sparingly for metadata, often in all-caps with `0.05em` letter spacing to act as a decorative "stamp" of information.

| Level | Size | Weight | Tracking | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **Display-LG** | 3.5rem | 600 | -0.02em | Hero headlines |
| **Headline-MD**| 1.75rem | 500 | -0.01em | Section headers |
| **Title-SM**   | 1rem | 600 | 0 | Card titles |
| **Body-MD**    | 0.875rem| 400 | 0 | General content |
| **Label-SM**   | 0.6875rem| 500 | 0.05em | Metadata / Overlines |

---

## 4. Elevation & Depth

### The Layering Principle
Depth is achieved by "stacking" tonal tiers. For example:
- **Level 0:** `surface` (The floor).
- **Level 1:** `surface-container-low` (The background for a list).
- **Level 2:** `surface-container-lowest` (The individual cards in that list).

### Ambient Shadows
When a card needs to "pop" (e.g., on hover), use the **Shadow-2xl** profile:
- **Blur:** 40px - 60px.
- **Opacity:** 4% - 8%.
- **Color:** Use a tinted version of `on-surface` (dark blue-grey) rather than pure black to keep the shadow feeling "airy" and natural.

### The "Ghost Border" Fallback
If accessibility requirements demand a border, use the `outline-variant` token at **20% opacity**. This creates a "silver" hint that defines the edge without creating a visual cage.

---

## 5. Components

### Buttons: The Action Catalyst
- **Primary:** Gradient (`primary` to `secondary`), `rounded-full` or `rounded-3xl`. No border.
- **Secondary:** `surface-container-lowest` with a "Ghost Border."
- **Interaction:** On hover, a subtle `shadow-lg` (low opacity) and a 2% scale increase.

### Cards: The Content Vessel
- **Rule:** Absolute prohibition of divider lines.
- **Style:** `surface-container-lowest`, `rounded-3xl`, subtle ambient shadow. Use vertical whitespace (from the 32px or 48px scale) to separate card sections.

### Input Fields: Minimalist Precision
- **Default:** `surface-container-low`, `rounded-2xl`, no border.
- **Focus:** Transition to `surface-container-lowest` with a 1px "Ghost Border" and a soft glow using `primary_container`.

### Additional Component: The "Glass Float" Navigation
- A floating bottom or top bar using `on-secondary` (60% opacity) with a heavy `backdrop-blur`. This ensures the organic background mesh gradients are visible through the UI, maintaining a sense of place.

---

## 6. Do's and Don'ts

### Do
*   **DO** use asymmetric layouts. Align text to the left but allow images to "break the grid" and bleed off-center.
*   **DO** use organic "mesh" gradients in the background (faint blurs of `primary_container` and `secondary_container`) to add warmth to the light mode.
*   **DO** use "Thin Stroke" (1.5px or 1px) icons to match the tech-forward aesthetic.

### Don't
*   **DON'T** use pure black (#000000) for text. Use `on-surface` (#2c2f31) for a softer, more premium contrast.
*   **DON'T** use sharp corners. If it's a container, it must have a minimum radius of `rounded-md` (1.5rem).
*   **DON'T** use "Standard" dropdowns. All overlays must be glassmorphic with a blur.