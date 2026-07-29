# Build stage
FROM node:22-alpine AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

# NEXT_PUBLIC_* values are inlined at build time, so the deployed API and analytics
# origins have to be supplied here rather than at runtime.
ARG NEXT_PUBLIC_API_URL=http://localhost:8080
ARG NEXT_PUBLIC_ANALYTICS_URL=http://localhost:8001
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL \
    NEXT_PUBLIC_ANALYTICS_URL=$NEXT_PUBLIC_ANALYTICS_URL

RUN npm run build

# Runtime stage
FROM node:22-alpine

WORKDIR /app

RUN addgroup -g 1001 -S nodejs && \
    adduser -S nextjs -u 1001

COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=builder --chown=nextjs:nodejs /app/public ./public

USER nextjs

EXPOSE 3000

# Defaults only. The standalone server reads PORT and HOSTNAME at runtime, so a
# platform-assigned port takes effect without a rebuild.
ENV NODE_ENV=production \
    PORT=3000 \
    HOSTNAME=0.0.0.0

CMD ["node", "server.js"]
