# STAGE 1: Build the React bundle
FROM node:22-alpine AS build
WORKDIR /app

# Install dependencies first so this layer is cached until package files change
COPY app/package.json app/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY app/ ./

# Same-origin API: nginx proxies /api to the backend, so the bundle only needs
# a relative base URL. Overridable for non-proxied deployments.
ARG REACT_APP_API_URL=/api
ENV REACT_APP_API_URL=$REACT_APP_API_URL
ENV GENERATE_SOURCEMAP=false
# The strict Content-Security-Policy (script-src 'self') forbids the inline runtime chunk CRA emits by default.
ENV INLINE_RUNTIME_CHUNK=false
RUN npm run build

# STAGE 2: Serve the static bundle and proxy the API
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /app/build /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -q --spider http://127.0.0.1/healthz || exit 1
