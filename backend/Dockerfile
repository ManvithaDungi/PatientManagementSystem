# STAGE 1: build the JAR
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Resolve dependencies first so this layer is cached until pom.xml changes
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# STAGE 2: runtime (pg_dump is required by BackupService)
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache postgresql16-client \
    && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /app/uploads /app/backups /app/logs && chown -R app:app /app
USER app

ENTRYPOINT ["java", "-jar", "app.jar"]
