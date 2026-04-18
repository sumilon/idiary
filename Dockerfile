# ---- Stage 1: Build ----
FROM gradle:8.6-jdk17 AS build
WORKDIR /app

# Cache dependencies layer
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
RUN gradle dependencies --no-daemon --quiet || true

# Build the fat JAR using Ktor's built-in fatJar task
COPY src src
RUN gradle buildFatJar --no-daemon --quiet

# Verify the JAR was created and show its name (helps debug if path changes)
RUN echo "=== Built JARs ===" && find /app/build/libs -name "*.jar" && echo "==="

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the fat JAR — rename to app.jar for a stable runtime name
COPY --from=build /app/build/libs/diary-app.jar app.jar

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-jar", "app.jar"]
