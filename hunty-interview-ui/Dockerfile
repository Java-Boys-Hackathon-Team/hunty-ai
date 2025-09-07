# Multi-stage Dockerfile for Vite + React + TypeScript (production build)
# Builder stage
FROM node:20-alpine AS builder

# Ensure reproducible installs and smaller image
ENV NODE_ENV=production
WORKDIR /app

# Install dependencies (use npm ci for clean, reproducible install)
# Copy only lockfile and package manifest first for better layer caching
COPY package.json package-lock.json ./
RUN npm ci

# Copy the rest of the app and build
COPY . .
# Build for production; Vite sets import.meta.env.PROD=true
RUN npm run build

# Runtime stage: use a minimal web server to serve static assets
FROM nginx:1.27-alpine AS runner

# Copy nginx config optimized for SPA
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy built static files
COPY --from=builder /app/dist /usr/share/nginx/html

# Expose port
EXPOSE 80

# nginx runs as non-root user internally in Alpine image configuration; no CMD override needed