# Payment Lab

Minimal Java 21 + Spring Boot 3 Payment API for backend interview practice.

## Requirements

- Java 21
- Maven
- Docker

## Run

```bash
docker compose up -d postgres
mvn -pl services/payment-service spring-boot:run
```

If Docker Compose is not installed, you can start PostgreSQL with Docker directly:

```bash
docker run --name payment-lab-postgres \
  -e POSTGRES_DB=payment_lab \
  -e POSTGRES_USER=payment_lab \
  -e POSTGRES_PASSWORD=payment_lab \
  -p 5433:5432 \
  -d postgres:16-alpine
```

The application connects to PostgreSQL with these defaults:

- `DB_URL=jdbc:postgresql://localhost:5433/payment_lab`
- `DB_USERNAME=payment_lab`
- `DB_PASSWORD=payment_lab`

## Observability

The local observability stack is organized under `observability/`:

- Grafana: `http://127.0.0.1:3000`
- Loki: `http://127.0.0.1:3100`
- Alloy: `http://127.0.0.1:12345`

Start with Docker Compose when available:

```bash
GRAFANA_ADMIN_PASSWORD=<strong-password> docker compose up -d loki grafana alloy
```

Alloy collects Docker logs for the `payment-lab-*` containers and Spring service logs from `logs/`.
The Spring services write logs with Logback to:

- `logs/payment-service/application.log`
- `logs/payment-service/error.log`
- `logs/payment-processor-service/application.log`
- `logs/payment-processor-service/error.log`

Set `LOG_DIR` when running a service from a different working directory.

## Test

```bash
mvn clean test
```

## Endpoints

- `GET /api/health`
- `POST /api/payments`
