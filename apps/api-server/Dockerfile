FROM gradle:8.10.2-jdk21 AS build
WORKDIR /workspace

COPY . .
RUN gradle build --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

