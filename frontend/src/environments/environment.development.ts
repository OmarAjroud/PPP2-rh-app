// ng serve: the API runs locally (docker compose); CORS already allows http://localhost:4200.
export const environment = {
  apiUrl: 'http://localhost:8080',
  /** Show the demo accounts on the login page (only when the backend seeds them: SEED_TEST_ACCOUNTS=true). */
  comptesDemo: true,
};
