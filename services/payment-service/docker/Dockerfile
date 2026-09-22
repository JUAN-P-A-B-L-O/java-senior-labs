# Build from the repository root:
# docker build -f services/payment-service/docker/Dockerfile -t payment-service services/payment-service
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
COPY src/main ./src/main
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN mkdir -p /app/logs && chown 10001:10001 /app/logs
COPY --from=build /build/target/payment-lab-*.jar /app/payment-service.jar

ENV LOG_DIR=/app/logs
# Supply JWT_PUBLIC_KEY/JWT_PRIVATE_KEY and mount their files at runtime.
# Configure DB_URL, Redis, and Spring RabbitMQ settings for the container network.
USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/payment-service.jar"]
