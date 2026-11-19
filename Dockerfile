# ==========================================
# Multi-Stage Production Dockerfile (Java 21)
# Minimal attack surface, Non-Root User
# ==========================================

# Stage 1: Build JAR
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create unprivileged user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Create directories for persistent storage and logs with proper permissions
RUN mkdir -p /app/data/storage /app/data/db /app/data/drive-sync /app/logs && \
    chown -R appuser:appgroup /app

# Copy executable jar from builder stage
COPY --from=builder --chown=appuser:appgroup /build/target/whatsapp-document-chatbot-1.0.0.jar app.jar

USER appuser

EXPOSE 8790

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8790/api/health || exit 1

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
