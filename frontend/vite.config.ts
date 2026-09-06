/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'

/**
 * One config for dev server, build and tests.
 *
 * A separate vitest.config.ts would make TypeScript resolve two different
 * copies of Vite's plugin types and fail to reconcile them; keeping the `test`
 * block here means one Vite, one plugin instance, one set of aliases.
 *
 * The `/api` proxy is the important part of the dev server. Without it the
 * browser would treat localhost:5173 and localhost:8080 as different origins,
 * which means CORS preflights and a session cookie the browser is reluctant to
 * send. Proxying makes development same-origin, so cookies, CSRF and sessions
 * behave exactly as they do in production behind a single hostname.
 *
 * The backend's CORS configuration still exists and is still needed: it is what
 * a split-origin production deployment relies on.
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
    rollupOptions: {
      output: {
        // Recharts and React change far less often than application code.
        // Splitting them out means a normal deploy only invalidates the small
        // app chunk in users' caches, not 600 kB of unchanged library.
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          charts: ['recharts'],
          query: ['@tanstack/react-query'],
        },
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // Playwright specs live in e2e/ and are run by `npm run e2e`.
    exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
  },
})
