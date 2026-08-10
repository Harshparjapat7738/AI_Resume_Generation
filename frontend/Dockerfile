# syntax=docker/dockerfile:1.7
# ---------------------------------------------------------------------------
# CareerForge AI frontend — build with Vite, serve the static bundle with nginx.
# Build context is ./frontend.
# VITE_* values are compile-time and therefore PUBLIC. Never pass a secret here.
# ---------------------------------------------------------------------------

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci || npm install
COPY . .
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
HEALTHCHECK --interval=20s --timeout=3s --retries=3 CMD wget -qO- http://localhost:8080/healthz || exit 1
