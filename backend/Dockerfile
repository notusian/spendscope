# ---------------------------------------------------------------------------
# Build stage: compile and package the application.
#
# Dependencies are resolved in their own layer so that changing application
# source does not re-download the whole Maven repository on every build.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
# Tests need a PostgreSQL container and run in CI, not in the image build.
RUN mvn -B clean package -DskipTests

# ---------------------------------------------------------------------------
# Runtime stage: JRE only, no Maven, no source, no build cache.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# curl is used by the container healthcheck below.
RUN apk add --no-cache curl

# Run as an unprivileged user. A container process running as root that gets
# compromised has a much shorter path to compromising the host.
RUN addgroup --system spendscope && adduser --system --ingroup spendscope spendscope

WORKDIR /app
COPY --from=build --chown=spendscope:spendscope /build/target/*.jar app.jar

USER spendscope
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# exec form via sh so JAVA_OPTS is expanded, while still making java PID 1 so
# it receives SIGTERM directly and shuts down gracefully.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
