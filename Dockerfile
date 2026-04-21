# ---- Stage 1: Build ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Install Gradle
RUN wget -q https://services.gradle.org/distributions/gradle-8.12.1-bin.zip -O /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && rm /tmp/gradle.zip
ENV PATH="/opt/gradle-8.12.1/bin:$PATH"

# Copy build files first — this layer is cached until build.gradle.kts changes
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle

# Build the fat JAR with merged service files (required for gRPC/Firebase)
COPY src src
RUN gradle shadowJar --no-daemon --quiet

# Show built JAR name for debugging
RUN echo "=== Built JARs ===" && find /app/build/libs -name "*.jar" && echo "==="

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /app/build/libs/diary-app.jar app.jar

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=70.0", \
  "-XX:InitialRAMPercentage=20.0", \
  "-XX:+UseSerialGC", \
  "-XX:MaxMetaspaceSize=96m", \
  "-Djava.awt.headless=true", \
  "-jar", "app.jar"]
