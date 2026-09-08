# Software DNA backend — production image.
#
# NOTE: this file has not been built or run in the environment it was written
# in, which has no Docker installed. It follows a standard, widely-used
# multi-stage pattern for a Maven/Spring Boot service, and the jar it packages
# has been built and run directly on this machine — but the containerisation
# itself is unverified until Railway (or you, locally) builds it once. If the
# build fails, the Maven layer is the one to check first.
#
# Two stages: the first has the JDK and Maven wrapper needed to compile, the
# second has only a JRE and the finished jar. That keeps the final image to
# roughly the JRE's size plus the jar, rather than carrying a full JDK and the
# Maven dependency cache into production.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Dependencies are resolved in their own layer, before any source is copied,
# so an ordinary source change does not re-download the world on every build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
# Tests already ran in CI/locally; skipped here so the image build is the
# packaging step, not a second full test run.
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Runs as a non-root user. A repository is untrusted input that gets cloned
# and parsed inside this container; there is no reason that process should
# hold root.
RUN groupadd --system softwaredna && useradd --system --gid softwaredna softwaredna
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /tmp/software-dna-workspaces && chown -R softwaredna:softwaredna /tmp/software-dna-workspaces /app
USER softwaredna

ENV WORKSPACE_ROOT=/tmp/software-dna-workspaces
EXPOSE 8080

# Railway sets $PORT; SERVER_PORT is read by application.yml and defaults to
# 8080 for every other environment.
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]
