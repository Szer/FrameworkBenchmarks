FROM gradle:7.1.1-jdk16 AS build
WORKDIR /app
COPY ktor-vertx/build.gradle.kts build.gradle.kts
COPY ktor-vertx/gradle.properties gradle.properties
COPY ktor-vertx/settings.gradle.kts settings.gradle.kts
COPY ktor-vertx/src src
RUN gradle --no-daemon shadowJar

FROM openjdk:16-jdk-slim as jvm

COPY --from=build /app/app.jar app.jar

EXPOSE 9090

CMD ["java", "-server", "-Xms4g", "-XX:+UseStringDeduplication", "-XX:+UseNUMA", "-XX:+UseParallelGC", "-jar", "app.jar"]
