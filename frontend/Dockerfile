# Build Stage
# Use slim Node.js 22 Image for Build Stage
FROM node:22-alpine AS build
WORKDIR /app

# enables pnpm package manager via Corepack (integrated in Node.js, no separate installation needed)
RUN corepack enable

# copy all the needed dependencies
COPY package.json .
COPY pnpm-lock.yaml .
COPY pnpm-workspace.yaml .

# install dependencies
RUN pnpm install --frozen-lockfile

# copy source and build
COPY . .

# define a build argument
ARG NEXT_PUBLIC_API_URL

# variable must be available at build time, so it needs to be set as environment variable
ENV NEXT_PUBLIC_API_URL=${NEXT_PUBLIC_API_URL}

# build
RUN pnpm build

# Runtime Stage
# Use slim Node.js 22 Image for Runtime Stage
FROM node:22-alpine AS final
WORKDIR /app

# Run as an unprivileged user
# -S creates a system user/group (no password, no aging), -G assigns the group
RUN addgroup -S app && adduser -S app -G app

# copy only the minimal standalone output, not the full node_modules
COPY --from=build --chown=app:app /app/.next/standalone ./
COPY --from=build --chown=app:app /app/.next/static ./.next/static
COPY --from=build --chown=app:app /app/public ./public

# switch to unprivileged user
USER app

# expose on port 3000
EXPOSE 3000

# standalone mode creates a server.js, that can be started with node server.js
ENTRYPOINT ["node", "server.js"]