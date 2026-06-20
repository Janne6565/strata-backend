# syntax=docker/dockerfile:1
# Multi-stage build for the Strata backend (Spring Boot 4.1 / Java 25).

# ---- build ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
# Warm the dependency cache first (changes rarely).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
# Then the sources.
COPY src ./src
# Tests + formatting run in CI; the image build just produces the artifact.
RUN ./mvnw -B -q -DskipTests -Dspotless.check.skip=true clean package \
    && cp target/*.jar app.jar

# ---- runtime ----
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /app/app.jar app.jar
# Run as a non-root numeric UID (matches the Deployment securityContext
# runAsUser: 1000). No passwd entry is needed for the JVM. The temurin base
# already ships GID 1000, so we don't create a user/group here.
USER 1000
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
