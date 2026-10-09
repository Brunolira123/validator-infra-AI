# syntax=docker/dockerfile:1

# ---- Stage 1: build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
COPY src ./src

# Cache do ~/.m2 entre builds (BuildKit): só baixa dependências quando o pom muda.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q package -DskipTests \
    && cp target/validator-infra-*.jar app.jar

# ---- Stage 2: runtime ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S app \
    && adduser -S -G app app \
    && mkdir -p /app/storage/fotos \
    && chown -R app:app /app

COPY --from=build --chown=app:app /build/app.jar app.jar

USER app

ENV FOTOS_PATH=/app/storage/fotos \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
