// Production build. Set apiUrl to the deployed API (Render, Oracle VM…) before building;
// that origin must also be listed in the backend's CORS_ALLOWED_ORIGINS.
export const environment = {
  apiUrl: 'http://localhost:8080',
  /** Show the demo accounts on the login page (only when the backend seeds them: SEED_TEST_ACCOUNTS=true). */
  comptesDemo: true,
};
