import type { Config } from 'tailwindcss';

// Tokens live as CSS variables in src/index.css; see DESIGN.md at the repo root.
const token = (name: string) => `rgb(var(--${name}) / <alpha-value>)`;
const out = 'cubic-bezier(0.16, 1, 0.3, 1)';

export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        pitch: { DEFAULT: token('pitch'), raised: token('pitch-raised'), ink: token('pitch-ink') },
        ground: token('ground'),
        surface: { DEFAULT: token('surface'), sunken: token('sunken') },
        line: { DEFAULT: token('line'), strong: token('line-strong') },
        ink: { DEFAULT: token('ink'), muted: token('ink-muted') },
        accent: {
          DEFAULT: token('accent'), // mint fill: buttons, shortlist zone
          strong: token('accent-strong'), // hover fill
          ink: token('accent-ink'), // text on accent fills
          text: token('accent-text'), // green text, focus, borders on light
          bar: token('accent-bar'), // data bars (3.5:1 on white)
          soft: token('accent-soft'),
        },
        info: { DEFAULT: token('info'), soft: token('info-soft') },
        warn: { DEFAULT: token('warn'), soft: token('warn-soft') },
        danger: { DEFAULT: token('danger'), soft: token('danger-soft') },
      },
      fontFamily: {
        sans: ['"Archivo Variable"', 'Archivo', 'system-ui', 'sans-serif'],
      },
      borderRadius: {
        DEFAULT: '6px',
        md: '8px',
        lg: '12px',
      },
      boxShadow: {
        panel: '0 1px 2px rgb(17 25 22 / 0.05), 0 2px 8px -2px rgb(17 25 22 / 0.06)',
        menu: '0 8px 24px -6px rgb(17 25 22 / 0.2), 0 2px 6px rgb(17 25 22 / 0.08)',
        button: '0 1px 2px rgb(17 25 22 / 0.12), inset 0 1px 0 rgb(255 255 255 / 0.25)',
      },
      transitionDuration: {
        DEFAULT: '160ms',
      },
      transitionTimingFunction: {
        out,
      },
      keyframes: {
        'fade-up': { from: { opacity: '0', transform: 'translateY(6px)' }, to: { opacity: '1', transform: 'none' } },
        'fade-in': { from: { opacity: '0' }, to: { opacity: '1' } },
        'grow-x': { from: { transform: 'scaleX(0)' }, to: { transform: 'scaleX(1)' } },
        shimmer: { from: { backgroundPosition: '200% 0' }, to: { backgroundPosition: '-200% 0' } },
        rank: { '0%, 100%': { transform: 'scaleX(0.35)' }, '50%': { transform: 'scaleX(1)' } },
        stripes: { from: { backgroundPosition: '0 0' }, to: { backgroundPosition: '24px 0' } },
        ping: { '75%, 100%': { transform: 'scale(2.2)', opacity: '0' } },
      },
      animation: {
        'fade-up': `fade-up 420ms ${out} both`,
        'fade-in': 'fade-in 200ms ease-out both',
        'grow-x': `grow-x 700ms ${out} both`,
        shimmer: 'shimmer 1.6s linear infinite',
        rank: `rank 1.1s ${out} infinite`,
        stripes: 'stripes 0.8s linear infinite',
        ping: 'ping 1.4s cubic-bezier(0, 0, 0.2, 1) infinite',
      },
    },
  },
  plugins: [],
} satisfies Config;
