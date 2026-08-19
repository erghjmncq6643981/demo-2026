import { defineConfig } from 'vitest/config'
import { resolve } from 'node:path'
import { normalizePath } from 'vite'

export default defineConfig({
  publicDir: false,
  resolve: {
    alias: [
      { find: /^\/src\//, replacement: `${normalizePath(resolve(process.cwd(), 'public/src'))}/` },
    ],
  },
  test: {
    environment: 'jsdom',
    include: ['tests/unit/**/*.test.js'],
    coverage: {
      reporter: ['text', 'json-summary'],
    },
  },
})
