# Payment Lab

Minimal Java 21 + Spring Boot 3 Payment API for backend interview practice.

## Requirements

- Java 21
- Maven
- Docker

## Run

```bash
docker compose up -d postgres
mvn spring-boot:run
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

## Test

```bash
mvn clean test
```

## Endpoints

- `GET /api/health`
- `POST /api/payments`
