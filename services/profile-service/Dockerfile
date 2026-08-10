# syntax=docker/dockerfile:1.7
# ---------------------------------------------------------------------------
# profile-service
# Build context is the REPOSITORY ROOT so the Maven reactor can resolve the
# parent POM and platform-common:
#     docker build -f services/profile-service/Dockerfile -t careerforge/profile-service .
# No secrets are baked in; all configuration arrives as environment variables.
# ---------------------------------------------------------------------------

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy POMs first so dependency resolution is cached independently of source changes.
COPY pom.xml ./
COPY services/ services/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl services/profile-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S careerforge && adduser -S careerforge -G careerforge
WORKDIR /app
COPY --from=build /build/services/profile-service/target/*.jar app.jar
RUN chown -R careerforge:careerforge /app
USER careerforge

EXPOSE 8082
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=20s --timeout=3s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8082/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
