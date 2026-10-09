# ==============================================================================
# AgentHub Backend Production Dockerfile
# Multi-stage build with security hardening, non-root user, and Actuator health check
# ==============================================================================

# Stage 1: Build & Package
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Copy Maven POM and dependency cache
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and build jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal Distroless/Alpine Runtime
FROM eclipse-temurin:17-jre-alpine AS runner

# Install curl/wget for health check and git for JGit/runtime tooling
RUN apk add --no-cache bash curl wget git tzdata \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone

# Security hardening: Create non-root dedicated service user
RUN addgroup -g 10001 -S agenthub && adduser -u 10001 -S agenthub -G agenthub

WORKDIR /app

# Create necessary directories and assign ownership
RUN mkdir -p /app/data /var/lib/agenthub/workspaces \
    && chown -R agenthub:agenthub /app /var/lib/agenthub

# Copy jar from builder
COPY --from=builder --chown=agenthub:agenthub /build/target/agenthub-backend-1.0.0-SNAPSHOT.jar /app/app.jar

USER agenthub

ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    AGENTHUB_WORKSPACE_BASE_DIR=/var/lib/agenthub/workspaces \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=20s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q "UP" || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
