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
# Non-root user (matches the Deployment securityContext runAsUser: 1000).
RUN groupadd --system --gid 1000 strata \
    && useradd --system --uid 1000 --gid 1000 strata
COPY --from=build /app/app.jar app.jar
USER 1000
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
