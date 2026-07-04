# syntax=docker/dockerfile:1

# ---- Build stage: Vite 정적 빌드 ----
FROM node:22-alpine AS build
WORKDIR /app

# 의존성 레이어 캐싱
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- Runtime stage: nginx 로 SPA 서빙 + /api 프록시 ----
FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
