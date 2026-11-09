# syntax=docker/dockerfile:1
# Front-end image in two stages: the Angular build happens in Docker (no Node needed on the machine),
# then a non-root nginx serves it and forwards /api to the back-end (nginx.conf).

# ---------- 1. Build ----------
FROM node:22-alpine AS build
WORKDIR /build
ENV NG_CLI_ANALYTICS=false

# Dependencies first, in their own layer: reinstalled only when the lock file changes
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
# Tests run in CI (npm test); here we only build the application
RUN npx ng build --configuration production

# ---------- 2. Serve ----------
FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx-securite.conf /etc/nginx/snippets/securite.conf
COPY --from=build /build/dist/rh-frontend/browser/ /usr/share/nginx/html/
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
