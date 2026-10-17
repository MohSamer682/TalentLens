import type { Config } from 'tailwindcss';

// Tokens live as CSS variables in src/index.css; see ui/DESIGN.md.
const token = (name: string) => `rgb(var(--${name}) / <alpha-value>)`;
const out = 'cubic-bezier(0.22, 1, 0.36, 1)';

export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        pitch: { DEFAULT: token('pitch'), raised: token('pitch-raised'), ink: token('pitch-ink') },
        ground: token('ground'),
        surface: { DEFAULT: token('surface'), raised: token('surface-raised'), sunken: token('sunken') },
        sunken: token('sunken'),
        line: { DEFAULT: token('line'), strong: token('line-strong') },
        ink: { DEFAULT: token('ink'), muted: token('ink-muted') },
        accent: {
          DEFAULT: token('accent'), // pen blue fill: primary buttons
          strong: token('accent-strong'), // hover fill
          ink: token('accent-ink'), // text on accent fills
          text: token('accent-text'), // links, focus, active borders
          bar: token('accent-bar'), // data bars (3.5:1 on white)
          soft: token('accent-soft'),
        },
        mark: { DEFAULT: token('mark'), soft: token('mark-soft'), ink: token('mark-ink'), text: token('mark-text') },
        success: { DEFAULT: token('success'), soft: token('success-soft') },
        info: { DEFAULT: token('info'), soft: token('info-soft') },
        warn: { DEFAULT: token('warn'), soft: token('warn-soft') },
        danger: { DEFAULT: token('danger'), soft: token('danger-soft') },
      },
      fontFamily: {
        sans: ['"Schibsted Grotesk Variable"', '"Schibsted Grotesk"', 'system-ui', 'sans-serif'],
        display: ['"Schibsted Grotesk Variable"', '"Schibsted Grotesk"', 'system-ui', 'sans-serif'],
        mono: ['"Geist Mono Variable"', '"Geist Mono"', 'ui-monospace', 'monospace'],
      },
      borderRadius: {
        DEFAULT: '6px',
        md: '10px',
        lg: '14px',
        xl: '20px',
      },
      boxShadow: {
        plate: 'inset 0 1px 0 rgb(255 255 255 / 0.04), 0 1px 2px rgb(var(--shadow) / 0.06), 0 2px 8px -2px rgb(var(--shadow) / 0.08)',
        lift: 'inset 0 1px 0 rgb(255 255 255 / 0.06), 0 2px 4px rgb(var(--shadow) / 0.08), 0 12px 32px -8px rgb(var(--shadow) / 0.28)',
        panel: 'inset 0 1px 0 rgb(255 255 255 / 0.04), 0 1px 2px rgb(var(--shadow) / 0.06), 0 2px 8px -2px rgb(var(--shadow) / 0.08)',
        menu: '0 12px 28px -8px rgb(var(--shadow) / 0.45), 0 0 0 1px rgb(var(--line))',
        button: 'inset 0 -1px 0 rgb(var(--shadow) / 0.2)',
      },
      transitionDuration: {
        DEFAULT: '160ms',
      },
      transitionTimingFunction: {
        out,
        lens: out,
      },
      keyframes: {
        'fade-up': { from: { opacity: '0', transform: 'translateX(-8px)' }, to: { opacity: '1', transform: 'none' } },
        'fade-in': { from: { opacity: '0' }, to: { opacity: '1' } },
        'grow-x': { from: { transform: 'scaleX(0)' }, to: { transform: 'scaleX(1)' } },
        shimmer: { from: { backgroundPosition: '200% 0' }, to: { backgroundPosition: '-200% 0' } },
        rank: { '0%, 100%': { transform: 'scaleX(0.35)' }, '50%': { transform: 'scaleX(1)' } },
        stripes: { from: { backgroundPosition: '0 0' }, to: { backgroundPosition: '24px 0' } },
        ping: { '75%, 100%': { transform: 'scale(2.2)', opacity: '0' } },
      },
      animation: {
        'fade-up': `fade-up 240ms ${out} both`,
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
