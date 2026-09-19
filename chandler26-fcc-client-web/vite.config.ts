import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import path from 'path';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 8888,
    proxy: {
      '/api/admin': {
        target: 'http://127.0.0.1:8089',
        changeOrigin: true,
      },
      '/api/telephony': {
        target: 'http://127.0.0.1:8085',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://127.0.0.1:8085',
        ws: true,
        changeOrigin: true,
      },
    },
  },
});
