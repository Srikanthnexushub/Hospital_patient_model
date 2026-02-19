# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependency layer (copy pom.xml first)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build application (skip tests — tests run in CI separately)
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

# Install curl for HEALTHCHECK
RUN apk add --no-cache curl

# Copy built JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose backend port
EXPOSE 8080

# Health check: calls readiness probe every 10 seconds
HEALTHCHECK --interval=10s --timeout=5s --retries=3 --start-period=60s \
    CMD curl -f http://localhost:8080/actuator/health/readiness || exit 1

# Graceful shutdown via SIGTERM (Spring Boot listens for it)
STOPSIGNAL SIGTERM

# Run the application
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
