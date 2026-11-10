import { defineConfig } from 'vitest/config';

// Loaded by the Angular unit-test builder (angular.json: "runnerConfig": true looks for vitest-base.config.*).
// Screens with PrimeNG date pickers or lazy pages take 1-3 s alone, but can pass Vitest's 5 s default
// when every spec file runs in parallel on a busy machine: generous limits avoid random failures.
export default defineConfig({
  test: {
    testTimeout: 15_000,
    hookTimeout: 20_000,
  },
});
