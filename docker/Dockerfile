# syntax=docker/dockerfile:1.7

############################################
# Stage 1 — Build with a lightweight JDK
############################################
ARG JAVA_VERSION=21
FROM eclipse-temurin:${JAVA_VERSION}-jdk-alpine AS build
WORKDIR /workspace

# Optional: useful for Gradle plugins that invoke git, etc.
RUN apk add --no-cache git

# Speed up builds with a persistent Gradle cache (BuildKit required)
ENV GRADLE_USER_HOME=/home/gradle/.gradle

# Now copy sources and build for real
COPY . .

# Build
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon clean shadowJar -x test

# create version less jar
RUN JAR="$(ls build/libs/*all.jar | head -n1)" && cp "$JAR" /workspace/app.jar

############################################
# Stage 2 — Minimal runtime (distroless)
############################################
# Distroless is tiny, secure, and multi-arch (amd64/arm64).
FROM gcr.io/distroless/java21-debian12 AS runtime
WORKDIR /app

# Non-root by default in distroless (uid 65532), keep it that way.
COPY --from=build /workspace/app.jar /app/

# Tune JVM for containers; adapt memory settings to your app.
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75"

# Basic build-time metadata as defined at http://label-schema.org
LABEL org.label-schema.build-date=$BUILD_DATE \
    org.label-schema.docker.dockerfile="docker/Dockerfile" \
    org.label-schema.license="GPL3" \
    org.label-schema.name="bco-gateway-homeassistant" \
    org.label-schema.vendor="openbase.org" \
    org.label-schema.version=$VERSION \
    org.label-schema.description="A gateway that enables seamless integration between BaseCubeOne and Assistant." \
    org.label-schema.url="https://www.basecubeone.org" \
    org.label-schema.vcs-ref=$VCS_REF \
    org.label-schema.vcs-type="Git" \
    org.label-schema.vcs-url="https://github.com/openbase/bco.gateway.homeassistant.git" \
    io.hass.version=$VERSION \
    io.hass.type="addon" \
    io.hass.arch="aarch64|amd64"
    maintainer="Divine Threepwood <divine@openbase.org>"

# If your app serves HTTP on 8080, expose it (optional, docs only)
# EXPOSE 8080

# Healthcheck example (uncomment and adjust path/port if needed)
# HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
#   CMD ["/usr/bin/curl","-f","http://localhost:8080/actuator/health"] || exit 1

# ENTRYPOINT ["/app/bco-device-hass/bin/bco-device-hass"]
ENTRYPOINT ["/usr/bin/java","-jar","/app/app.jar"]
