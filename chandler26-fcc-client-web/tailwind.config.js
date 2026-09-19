/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#EEF2FF',
          100: '#E0E7FF',
          200: '#C7D2FE',
          300: '#A5B4FC',
          400: '#818CF8',
          500: '#4F46E5',
          600: '#4338CA',
          700: '#3730A3',
          800: '#312E81',
          900: '#1E1B4B',
        },
      },
      borderRadius: {
        '3xl': '24px',
        '4xl': '32px',
      },
      boxShadow: {
        'dashboard': '0 24px 60px -12px rgba(15, 23, 42, 0.12), 0 0 0 1px rgba(255, 255, 255, 0.7) inset',
        'card': '0 4px 20px -2px rgba(15, 23, 42, 0.05)',
        'pill': '0 4px 14px 0 rgba(79, 70, 229, 0.35)',
      },
    },
  },
  plugins: [],
};
