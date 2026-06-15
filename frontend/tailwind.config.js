/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{vue,js,ts,jsx,tsx}'
  ],
  theme: {
    extend: {
      colors: {
        accent: {
          cyan:    '#5cdcff',
          blue:    '#3da9ff',
          orange:  '#ff7e00',
          amber:   '#ffb347',
          green:   '#3bff9f',
          red:     '#ff5d5d',
          violet:  '#a78bfa'
        },
        bg: {
          deep:     '#050b18',
          primary:  '#0a1929',
          secondary:'#0d2540',
          tertiary: 'rgba(13, 37, 64, 0.78)'
        }
      },
      fontFamily: {
        sans: ['Bahnschrift', 'Segoe UI', 'Microsoft YaHei', 'sans-serif']
      },
      backdropBlur: {
        glass: '18px'
      },
      boxShadow: {
        glass: '0 14px 40px rgba(0, 0, 0, 0.55)',
        'glow-cyan':   '0 0 24px rgba(92, 220, 255, 0.45)',
        'glow-orange': '0 0 22px rgba(255, 126, 0, 0.45)',
        'glow-red':    '0 0 22px rgba(255, 93, 93, 0.45)'
      },
      animation: {
        'pulse-slow':   'pulse 3s ease-in-out infinite',
        'flow':         'flow 2.5s linear infinite',
        'flow-orange':  'flow 3s linear infinite',
        'spin-slow':    'spin 8s linear infinite',
        'spin-reverse': 'spin-reverse 12s linear infinite',
        'blink':        'blink 1.5s ease-in-out infinite'
      },
      keyframes: {
        flow: {
          to: { strokeDashoffset: -120 }
        },
        'spin-reverse': {
          to: { transform: 'rotate(-360deg)' }
        },
        blink: {
          '0%, 100%': { opacity: 1 },
          '50%': { opacity: 0.5 }
        }
      }
    }
  },
  plugins: []
}