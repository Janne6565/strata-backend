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
# pg_dump for the scheduled backup job (BackupService). Installed from the PGDG repo because
# stock Ubuntu ships postgresql-client 16, and pg_dump refuses servers newer than itself —
# keep this major version >= the Postgres StatefulSet image (currently postgres:17).
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && install -d /usr/share/postgresql-common/pgdg \
    && curl -fsSo /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc https://www.postgresql.org/media/keys/ACCC4CF8.asc \
    && echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] http://apt.postgresql.org/pub/repos/apt noble-pgdg main" > /etc/apt/sources.list.d/pgdg.list \
    && apt-get update && apt-get install -y --no-install-recommends postgresql-client-17 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/app.jar app.jar
# Run as a non-root numeric UID (matches the Deployment securityContext
# runAsUser: 1000). No passwd entry is needed for the JVM. The temurin base
# already ships GID 1000, so we don't create a user/group here.
USER 1000
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
