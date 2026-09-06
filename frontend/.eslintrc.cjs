/* eslint-env node */
module.exports = {
  root: true,
  env: { browser: true, es2022: true },
  extends: [
    'eslint:recommended',
    'plugin:@typescript-eslint/recommended',
  ],
  parser: '@typescript-eslint/parser',
  parserOptions: { ecmaVersion: 'latest', sourceType: 'module' },
  plugins: ['react-hooks', '@typescript-eslint'],
  rules: {
    'react-hooks/rules-of-hooks': 'error',
    'react-hooks/exhaustive-deps': 'warn',
    '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
  },
  ignorePatterns: ['dist', 'node_modules', '*.cjs', 'playwright-report', 'test-results'],
  overrides: [
    {
      // Build and test configuration run in Node, not the browser, so they
      // legitimately use `process` and `__dirname`.
      files: ['vite.config.ts', 'playwright.config.ts', 'e2e/**/*.ts'],
      env: { node: true, browser: false },
    },
  ],
}
