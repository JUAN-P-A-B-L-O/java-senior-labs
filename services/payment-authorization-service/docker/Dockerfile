# Build from the repository root:
# docker build -f services/payment-authorization-service/docker/Dockerfile -t payment-authorization-service services/payment-authorization-service
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml ./
COPY src/main ./src/main
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN mkdir -p /app/logs && chown 10001:10001 /app/logs
COPY --from=build /build/target/payment-authorization-service-*.jar /app/payment-authorization-service.jar

ENV LOG_DIR=/app/logs
# Supply JWT_PUBLIC_KEY and mount its file at runtime.
USER 10001:10001
EXPOSE 8084

ENTRYPOINT ["java", "-jar", "/app/payment-authorization-service.jar"]
