# ==============================================================================
# FlagShip Platform — Multi-Stage Production Dockerfile
# Base: Eclipse Temurin OpenJDK 21 (Alpine)
# ==============================================================================

# Stage 1: Build multi-module reactor with Maven 3.9 & Java 21
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy reactor descriptor and all module trees
COPY pom.xml .
COPY flagship-core/ flagship-core/
COPY flagship-spring-boot-starter/ flagship-spring-boot-starter/
COPY flagship-server/ flagship-server/
COPY flagship-demo-app/ flagship-demo-app/

# Package production executable JAR (skipping unit tests during Docker image build)
RUN mvn clean package -DskipTests

# Stage 2: Hardened Alpine JRE runtime container
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Run as non-privileged service user
RUN addgroup -S flagship && adduser -S flagship -G flagship

# Copy built server artifact from builder
COPY --from=builder /build/flagship-server/target/flagship-server-1.0.0.jar /app/app.jar

USER flagship

EXPOSE 8080

# Dynamically bind to Render PORT environment variable with fallback to 8080
ENTRYPOINT ["sh", "-c", "exec java -Djava.security.egd=file:/dev/./urandom -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
