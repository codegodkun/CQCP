FROM gradle:8.10.2-jdk21 AS build
WORKDIR /workspace/apps/api-server

COPY apps/api-server .
COPY packages/review-assets /workspace/packages/review-assets
RUN gradle bootJar --no-daemon
RUN jar tf build/libs/*.jar | grep -Fx 'BOOT-INF/classes/cqcp/review-assets/rule-sets/ruleset-v20260715.1.json'
RUN jar tf build/libs/*.jar | grep -Fx 'BOOT-INF/classes/cqcp/review-assets/review-point-definitions/review-points-v20260715.1.json'

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/apps/api-server/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
