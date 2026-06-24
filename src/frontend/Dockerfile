# Multi-stage build cho Next.js: cài deps + build, rồi chạy bản production.

# --- Stage 1: build ---
FROM node:22-alpine AS build
WORKDIR /app

# Cache deps: chỉ cài lại khi package*.json đổi.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# API URL được nhúng lúc build (biến NEXT_PUBLIC_*). Mặc định trỏ localhost:8080;
# đổi qua build-arg khi cần.
ARG NEXT_PUBLIC_API_URL=http://localhost:8080
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
RUN npm run build

# --- Stage 2: runtime ---
FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production

COPY --from=build /app/package.json /app/package-lock.json ./
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/.next ./.next
COPY --from=build /app/public ./public

EXPOSE 3000
CMD ["npm", "run", "start"]
