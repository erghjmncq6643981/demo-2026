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
          50: '#EEF4FF',
          100: '#E0EBFF',
          200: '#C7DAFE',
          300: '#A4C3FE',
          400: '#7BA3FC',
          500: '#4F46E5', /* 核心皇家蓝/靛蓝 */
          600: '#4338CA',
          700: '#3730A3',
          800: '#312E81',
          900: '#1E1B4B',
        },
      },
      boxShadow: {
        'dashboard': '0 30px 70px -15px rgba(22, 34, 51, 0.08), 0 0 0 1px rgba(0, 0, 0, 0.04)',
        'card': '0 10px 25px -3px rgba(0, 0, 0, 0.03), 0 4px 6px -2px rgba(0, 0, 0, 0.02)',
        'card-hover': '0 20px 35px -5px rgba(0, 0, 0, 0.06), 0 8px 12px -3px rgba(0, 0, 0, 0.03)',
        'pill': '0 4px 14px rgba(79, 70, 229, 0.28)',
        'popover': '0 20px 40px -8px rgba(0, 0, 0, 0.12), 0 4px 12px -2px rgba(0, 0, 0, 0.05), 0 0 0 1px rgba(0, 0, 0, 0.05)',
        '2xs': '0 1px 2px 0 rgba(0, 0, 0, 0.05)',
        'xs': '0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px -1px rgba(0, 0, 0, 0.1)',
      },
      borderRadius: {
        '2xl': '16px',
        '3xl': '24px',
        '4xl': '32px',
      },
    },
  },
  plugins: [],
};
