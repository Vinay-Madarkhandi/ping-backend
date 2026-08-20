# ---- BUILD STAGE ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar

# ---- RUNTIME STAGE ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as a dedicated non-root user: a container escape or RCE in the app then doesn't hand the
# attacker root inside the container.
RUN addgroup -S ping && adduser -S ping -G ping
COPY --from=build --chown=ping:ping /app/build/libs/*.jar app.jar
USER ping

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java -Dserver.port=${PORT:-8080} -jar app.jar"]
