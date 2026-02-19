# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM node:20-alpine AS build
WORKDIR /app

# Cache dependency layer
COPY package.json package-lock.json* ./
RUN npm ci --silent

# Build Vite production bundle
COPY . .
RUN npm run build

# ── Stage 2: Serve with Nginx ─────────────────────────────────────────────────
FROM nginx:1.25-alpine AS runtime

# Copy built assets to Nginx default serve directory
COPY --from=build /app/dist /usr/share/nginx/html

# Nginx config for SPA: redirect all 404s to index.html (React Router handles routing)
RUN echo 'server { \
    listen 3000; \
    root /usr/share/nginx/html; \
    index index.html; \
    location / { \
        try_files $uri $uri/ /index.html; \
    } \
}' > /etc/nginx/conf.d/default.conf

EXPOSE 3000

HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:3000/ || exit 1
