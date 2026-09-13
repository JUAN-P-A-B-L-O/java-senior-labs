# Build from the repository root:
# docker build -f services/payment-processor-service/docker/Dockerfile -t payment-processor-service services/payment-processor-service
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
COPY src/main ./src/main
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN mkdir -p /app/logs && chown 10001:10001 /app/logs
COPY --from=build /build/target/payment-processor-service-*.jar /app/payment-processor-service.jar

ENV LOG_DIR=/app/logs
# Supply JWT_PUBLIC_KEY and mount its file at runtime.
# Configure DB_URL, Spring RabbitMQ, KAFKA_BOOTSTRAP_SERVERS, and AUTHORIZATION_URL for the container network.
USER 10001:10001
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/app/payment-processor-service.jar"]
