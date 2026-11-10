# syntax=docker/dockerfile:1.7
# klean-server: the Klean sync server (backend/) with the Klean web app (web/) served at /.
#
#   docker build -t klean-server:dev .
#   docker run -d -p 8080:8080 -v klean-data:/data klean-server:dev
#   (or: docker compose -f deploy/docker-compose.yml up -d, see docs/deploy.md)
#
# Reproducible: base images are pinned by digest, dependencies by lockfiles (pnpm-lock.yaml, Gradle's version
# catalog + wrapper checksum), jars are built without timestamps. With SOURCE_DATE_EPOCH set and
# `--output type=image,rewrite-timestamp=true` (what CI does), the same commit gives the same image digest;
# scripts/image-repro.sh checks it.
#
# Multi-arch without emulation: the two build stages run on the build machine's platform ($BUILDPLATFORM)
# because their output (JS/CSS, JVM bytecode) is platform-independent; only the runtime stage is per-target,
# and it has no RUN steps.
#
# Bump a base image: crane digest docker.io/library/<image>:<tag>

ARG NODE_IMAGE=node:22-bookworm-slim@sha256:5ec23b7c622b00547753bc6ffd78085dd868a68aeb41c8e0a1418f033e13d96d
ARG JDK_IMAGE=eclipse-temurin:21-jdk@sha256:ebfbb18d11ff163ee83b21258acf12248af48d22a0fdfdc68fa1a1516c05976a
ARG JRE_IMAGE=eclipse-temurin:21-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a

# --- 1. Web app: web/ → web/dist ------------------------------------------------------------------------------
FROM --platform=$BUILDPLATFORM ${NODE_IMAGE} AS web
WORKDIR /src
RUN corepack enable
# The web build reads the shared design tokens, the Cantarell fonts and the launcher icon from the Android tree.
COPY core/design/tokens/ core/design/tokens/
COPY core/design/src/main/res/font/ core/design/src/main/res/font/
COPY app/src/main/res/drawable/ic_launcher_background.xml app/src/main/res/drawable/ic_launcher_foreground.xml app/src/main/res/drawable/
COPY web/package.json web/pnpm-lock.yaml web/
RUN cd web && corepack install && pnpm install --frozen-lockfile
COPY web/ web/
RUN cd web && pnpm build

# --- 2. Server: backend/ (+ shared/) → an installDist tree with the web app inside its jar ----------------------
FROM --platform=$BUILDPLATFORM ${JDK_IMAGE} AS server
WORKDIR /src
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.welcome=never"
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
COPY build-logic/ build-logic/
RUN ./gradlew --version
COPY shared/ shared/
COPY backend/ backend/
COPY --from=web /src/web/dist/ web/dist/
# serverOnly: include just :shared and :backend (no Android SDK needed). prebuilt: package web/dist from stage 1.
RUN ./gradlew :backend:installDist -Pklean.serverOnly=true -Pklean.web=prebuilt --no-configuration-cache --console=plain \
 && mkdir -p /out/data

# --- 3. Runtime ---------------------------------------------------------------------------------------------------
FROM ${JRE_IMAGE}
ARG REVISION=dev
LABEL org.opencontainers.image.title="klean-server" \
      org.opencontainers.image.description="Klean sync server with the Klean web app at /" \
      org.opencontainers.image.source="https://github.com/kquote03/klean" \
      org.opencontainers.image.revision="${REVISION}"
ENV KLEAN_DATA_DIR=/data \
    KLEAN_PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
COPY --from=server /src/backend/build/install/backend/ /opt/klean/
# Runs as an unprivileged user; /data is owned by it so a fresh named volume is writable.
COPY --from=server --chown=10001:10001 /out/data/ /data/
USER 10001:10001
VOLUME ["/data"]
EXPOSE 8080
ENTRYPOINT ["/opt/klean/bin/backend"]
