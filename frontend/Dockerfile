# syntax=docker/dockerfile:1
#
# 프론트엔드(React + Vite) 이미지. 멀티스테이지: 빌드 결과(정적 파일)만 nginx 에 담는다.
#
#   docker build -t flatmap-frontend \
#     --build-arg VITE_KAKAO_API_KEY=<카카오 JS 키> ./frontend
#
# 빌드 인자(VITE_*)는 브라우저 번들에 그대로 들어가는 "공개 값"이다. 카카오 JS 키는 브라우저에 노출되는 키이며
# 카카오 콘솔의 허용 도메인으로 제한해서 쓴다. 서버 비밀값(DB 비밀번호, JWT 비밀, 관리 키, 각종 API 키)은
# 절대 빌드 인자로 넣지 않는다. 빌드 컨텍스트에서 .env 는 frontend/.dockerignore 가 막는다.

# ---- 1단계: 빌드 ----
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
ARG VITE_KAKAO_API_KEY
# 비워 두면 같은 출처(/api)를 쓴다 — 운영에서는 nginx 가 /api 를 백엔드로 넘긴다.
ARG VITE_API_BASE_URL=
ARG VITE_BACKEND_URL=
ENV VITE_KAKAO_API_KEY=$VITE_KAKAO_API_KEY \
    VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_BACKEND_URL=$VITE_BACKEND_URL
RUN npm run build

# ---- 2단계: 실행 (비루트 nginx, 8080) ----
FROM nginxinc/nginx-unprivileged:1.25-alpine

# 템플릿의 ${BACKEND_URL}, ${NGINX_RESOLVER} 만 시작할 때 환경변수로 치환된다(nginx 변수($host 등)는 그대로).
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/build /usr/share/nginx/html

ENV BACKEND_URL=http://backend:8080 \
    NGINX_RESOLVER=127.0.0.11
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/ >/dev/null || exit 1
