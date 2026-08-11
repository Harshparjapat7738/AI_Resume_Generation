import { defineConfig, configDefaults } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'node:path';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    port: 5173,
    // All API calls go through the gateway. Nothing in the browser ever talks to a
    // service, MongoDB, MinIO or Groq directly.
    proxy: {
      '/api': {
        target: process.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    // tests/e2e is Playwright's testDir (`npm run e2e`), not Vitest's — without this,
    // Vitest's default glob picks up those specs too and fails calling Playwright's
    // test() outside the Playwright runner.
    exclude: [...configDefaults.exclude, 'tests/e2e/**'],
    // No unit tests exist under src/ yet — without this, `vitest run` exits 1 on an
    // empty suite and fails CI. Drop this once real *.test.ts files are added.
    passWithNoTests: true,
  },
});
