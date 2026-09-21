# Stage 1: Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy Maven wrapper and build file
COPY .mvn .mvn
COPY mvnw pom.xml ./

# Make wrapper script executable
RUN chmod +x mvnw

# Copy source code and build the production artifact using BuildKit cache mount
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw clean package -DskipTests -B -T 1C

# Stage 2: Minimal runtime stage
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Create non-root group and user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy compiled jar from build stage
COPY --from=builder /workspace/target/*.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser

# Configurable JVM options with production defaults
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

