# One image: the Angular dashboard is built and served by the Spring Boot app,
# so the dashboard and API share an origin (simple cookies, no CORS).

# 1) Dashboard
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npx ng build --configuration production

# 2) Backend, with the dashboard copied in as static files
FROM maven:3.9-eclipse-temurin-21 AS app
WORKDIR /app
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=web /web/dist/frontend/browser ./src/main/resources/static
# Tests run in CI (.github/workflows/ci.yml); skipped here to keep deploys fast.
RUN mvn -q -B package -DskipTests

# 3) Runtime: small JRE image, non-root user
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=app /app/target/relaybot.jar app.jar
USER app
# Render's free instance has 512 MB. SerialGC and a capped heap keep the JVM inside it;
# TieredStopAtLevel=1 shortens startup after a cold start.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
