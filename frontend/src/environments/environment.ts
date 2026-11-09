// Production build (Docker image): the API is reached on the same origin, nginx forwards /api and
// /actuator to the backend (frontend/nginx.conf). No CORS needed, and the SSE stream goes through as is.
export const environment = {
  apiUrl: '',
  /** Never print demo accounts and their password in a production build (the dev server does, see environment.development.ts). */
  comptesDemo: false,
};
