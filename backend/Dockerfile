# syntax=docker/dockerfile:1

# ---- Build stage: Gradle 8.11.1 + Java 21 ----
FROM gradle:8.11.1-jdk21 AS build
WORKDIR /app

COPY build.gradle settings.gradle ./
COPY src src

RUN gradle bootJar --no-daemon -x test \
    && cp $(ls build/libs/*.jar | grep -v plain) app.jar

# ---- Runtime stage: Java 21 JRE only ----
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app

COPY --from=build /app/app.jar app.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
