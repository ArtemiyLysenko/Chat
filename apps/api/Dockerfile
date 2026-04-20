FROM gradle:9.4.1-jdk25 AS build

WORKDIR /workspace

COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
COPY build-logic build-logic
COPY apps/api/build.gradle.kts apps/api/build.gradle.kts
COPY apps/api/src apps/api/src
COPY modules modules
COPY tools tools

RUN gradle --no-daemon :apps:api:bootJar

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /workspace/apps/api/build/libs/*.jar /app/app.jar

RUN mkdir -p /app/storage

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
