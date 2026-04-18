# ---- Stage 1: Build ----
FROM gradle:8.6-jdk17 AS build
WORKDIR /app

# Cache Gradle dependencies first (layer caching)
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
RUN gradle dependencies --no-daemon --quiet || true

# Build the fat JAR
COPY src src
RUN gradle shadowJar --no-daemon --quiet

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /app/build/libs/diary-app.jar app.jar

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-jar", "app.jar"]
