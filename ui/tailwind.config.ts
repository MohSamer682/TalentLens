import type { Config } from 'tailwindcss';

export default {
  darkMode: 'class',
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#4647d3',
          dim: '#3939c7',
          container: '#9396ff',
          'container-low': '#eef1f3',
          'container-lowest': '#ffffff',
          'container-high': '#dfe3e6',
        },
        secondary: {
          DEFAULT: '#8126cf',
          dim: '#740ec2',
          container: '#e5c6ff',
        },
        tertiary: {
          DEFAULT: '#963776',
          dim: '#882a69',
          container: '#ff8ed2',
        },
        surface: {
          DEFAULT: '#f5f7f9',
          'container-low': '#eef1f3',
          'container-lowest': '#ffffff',
          'container-high': '#dfe3e6',
          'container-highest': '#d9dde0',
        },
        'on-surface': '#2c2f31',
        'on-surface-variant': '#595c5e',
        error: {
          DEFAULT: '#b41340',
          dim: '#a70138',
          container: '#f74b6d',
        },
        outline: '#747779',
        'outline-variant': '#abadaf',
      },
      borderRadius: {
        DEFAULT: '1rem',
        lg: '2rem',
        xl: '3rem',
        full: '9999px',
      },
      fontFamily: {
        headline: ['Inter', 'sans-serif'],
        body: ['Inter', 'sans-serif'],
        label: ['Inter', 'sans-serif'],
      },
      boxShadow: {
        'primary/20': '0 4px 14px rgba(70, 71, 211, 0.2)',
        'primary/30': '0 4px 14px rgba(70, 71, 211, 0.3)',
        'primary/40': '0 4px 14px rgba(70, 71, 211, 0.4)',
      },
    },
  },
  plugins: [],
} satisfies Config;