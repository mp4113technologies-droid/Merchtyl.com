FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY backend/pom.xml ./backend/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -pl backend -am -DskipTests dependency:go-offline
COPY backend/src ./backend/src
RUN --mount=type=cache,target=/root/.m2 mvn -pl backend -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl \
    && addgroup -S merchtyl \
    && adduser -S merchtyl -G merchtyl
WORKDIR /app
COPY --from=build /workspace/backend/target/*.jar /app/app.jar
USER merchtyl
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${PORT:-8080}/actuator/health" || exit 1
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:--XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError} -jar /app/app.jar"]
