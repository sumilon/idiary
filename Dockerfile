# ---- Stage 1: Build ----
FROM gradle:8.6-jdk17 AS build
WORKDIR /app

# Cache dependencies layer
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
RUN gradle dependencies --no-daemon --quiet || true

# Build the fat JAR with merged service files (required for gRPC/Firebase)
COPY src src
RUN gradle shadowJar --no-daemon --quiet

# Show built JAR name for debugging
RUN echo "=== Built JARs ===" && find /app/build/libs -name "*.jar" && echo "==="

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /app/build/libs/diary-app.jar app.jar

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-jar", "app.jar"]
