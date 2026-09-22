# Payment Lab

Minimal Java 21 + Spring Boot 3 Payment API for backend interview practice.

## Requirements

- Java 21
- Maven
- Docker

## Run

Start all three microservices, PostgreSQL, RabbitMQ, Kafka, Redis, and observability
with Docker Compose v2 (Java and Maven are supplied by the Dockerfile builds):

```bash
docker compose up -d --build --wait
```

If the Compose plugin is unavailable, install Docker Compose v2 first. Services
already started manually must be stopped and their conflicting containers removed
before switching to Compose; keep their data volumes. Compose cannot adopt the
existing named containers. This command does not migrate host-run service data or
the host's `.local-auth` credentials.

| Service | Local address |
| --- | --- |
| Payment API | http://localhost:8080 |
| Processor | http://localhost:8083 |
| Authorization | http://localhost:8085 |
| RabbitMQ management | http://localhost:15672 |
| Grafana | http://localhost:3000 |
| Kafka (host clients) | localhost:9092 |

Override API host ports with `PAYMENT_PORT`, `PROCESSOR_PORT`, and
`AUTHORIZATION_PORT`. Inside the Docker networks, services use DNS names and ports
8080, 8082, and 8084; Kafka clients use `kafka:29092`. PostgreSQL is exposed on 5433
and Redis on 6379. PostgreSQL uses the local lab credentials `payment_lab` /
`payment_lab`; RabbitMQ uses `guest` / `guest`, and Grafana uses `admin` / `admin`.

Bootstrap jobs generate persistent RSA keys and random authentication credentials,
and create `payment_processor_db` if absent. Services wait for those jobs and their
required dependencies to become ready using [Compose startup conditions](https://docs.docker.com/compose/how-tos/startup-order/). Only payment-service mounts the private key;
all three services mount the public key read-only. For the Compose-created admin
account, retrieve its password locally with:

```bash
docker compose exec payment-service cat /keys/private/admin-password
```

Use that password with the login endpoint described below. The bootstrap preserves
keys and passwords across restarts; an existing database admin password is never
reset. Compose uses its own key volumes, separate from `.local-auth`, so host-issued
tokens do not authenticate against this environment.

Named volumes persist databases, broker data, Redis AOF, JWT keys, application logs,
and observability data. Prometheus scrapes service DNS addresses and Alloy reads the
shared application log volumes. `docker compose down` retains these volumes;
`docker compose down -v` deletes the environment's data and credentials.

### Run services directly on the host

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

Grafana is provisioned by the project with a Loki data source:

- Provisioning file: `observability/grafana/provisioning/datasources/loki.yml`
- Dashboard provider: `observability/grafana/provisioning/dashboards/payment-lab.yml`
- Dashboard: `Payment Lab / Payment Lab Logs`
- Data source name: `Loki`
- Data source URL from Grafana: `http://payment-lab-loki:3100`
- Browser URL for Grafana: `http://127.0.0.1:3000`
- Login: `admin` and the `GRAFANA_ADMIN_PASSWORD` value used when starting Grafana

In Grafana, open **Explore**, select the `Loki` data source, and query the labels that Alloy sends to Loki. Useful starting queries:

```logql
{service="payment-service"}
{service="payment-processor-service"}
{container="payment-lab-grafana"}
{container="payment-lab-loki"}
```

If the Loki data source is not visible, add it manually in Grafana with:

- Type: `Loki`
- URL: `http://payment-lab-loki:3100`
- Access: `Server`

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

## Kafka payment completion events

After successful authorization, the processor saves a Kafka `PaymentProcessed`
event alongside the existing RabbitMQ result event. This means processing has
succeeded; the payment-service status update still happens through RabbitMQ.
Failed authorizations continue to produce only the existing RabbitMQ failure event.

Both outbox entries are saved in the same database transaction. They have separate
event IDs and publication status, and share the payment ID, requested event ID,
and trace fields. The Kafka entry uses the internal outbox type
`PaymentProcessedKafka`; the wire header `eventType` is `PaymentProcessed`.
No database migration is required.

Start the local Kafka broker for applications running on the host:

```bash
docker compose up -d kafka
```

If Compose is unavailable, the Apache image provides a single-node default:

```bash
docker run -d --name payment-lab-kafka -p 127.0.0.1:9092:9092 apache/kafka:4.0.0
```

Create the topic before processing payments:

```bash
docker exec payment-lab-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --create --if-not-exists \
  --topic payment.processed --partitions 1 --replication-factor 1
```

Processor configuration:

| Variable | Default |
| --- | --- |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `KAFKA_PAYMENT_PROCESSED_TOPIC` | `payment.processed` |
| `KAFKA_PUBLISHER_ENABLED` | `true` |
| `KAFKA_PUBLISH_TIMEOUT` | `15s` |

Kafka publishing runs every 30 seconds, configurable with
`outbox.kafka-publisher.fixed-delay` in milliseconds. It uses a separate scheduler
so Kafka connection or acknowledgement waits do not block RabbitMQ publishing.
Disabling the Kafka publisher pauses delivery; Kafka outbox entries still accumulate
and are delivered when it is enabled again. Existing events are not backfilled.

The Kafka key is `paymentId`. The JSON value contains `eventId`, `traceId`,
`requestedEventId`, `paymentId`, and `traceParent`; the W3C `traceparent` header
is also forwarded when available. Publication is marked complete only after Kafka
acknowledges the send. Failed or timed-out entries remain pending for the next run.
Delivery is at least once: consumers should deduplicate by `eventId`, including
when a send succeeds but the database status update fails.

Observe events:

```bash
docker exec payment-lab-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic payment.processed --from-beginning \
  --property print.key=true --property print.headers=true
```

The local broker advertises `localhost:9092` for host-based services. If services
run inside containers, configure Kafka advertised listeners and bootstrap servers
with addresses reachable from those containers.

## JWT authentication

Run `./scripts/setup-local-auth.sh` once, then `source .local-auth/auth.env` in each
terminal before starting the services. The script creates an RSA signing key and
random local credentials under the ignored `.local-auth/` directory, and preserves
existing credentials on subsequent runs. Give only `JWT_PUBLIC_KEY` to the
payment authorization service; the processor also needs `AUTH_PROCESSOR_PASSWORD`.
Only payment-service needs `JWT_PRIVATE_KEY` and `AUTH_ADMIN_PASSWORD`.
Without explicit key locations, services find `.local-auth` in the working directory
or its parents, so launching from the repository root or a service module works.
For launches outside the repository, set absolute `file:` locations using the
generated environment file. Explicit locations always take precedence.

On payment-service startup, Flyway creates `api_users` and the configured bootstrap
password creates `admin` with role `ADMIN` if absent. Passwords are BCrypt hashes;
restarting never resets an existing admin password. No default password is built in.

Obtain the admin token from `POST http://localhost:8080/api/auth/login` with JSON
`{"username":"admin","password":"<AUTH_ADMIN_PASSWORD from .local-auth/auth.env>"}`.
Login and service-token requests authenticate using their JSON credentials and ignore
any inherited Bearer header. Send `Content-Type: application/json` with
both `username` and `password`. Missing fields or malformed JSON return 400;
incorrect credentials return 401 with `detail: "Invalid credentials"`.
The response contains `accessToken`, `tokenType` and `expiresIn` (3600 seconds).
Send `Authorization: Bearer <accessToken>` on payment requests, keeping the existing
`Idempotency-Key` header and request body.

An ADMIN can create another ADMIN or COMUM user with
`POST /api/auth/users`, bearer authentication and JSON:

```json
{"username":"comum","password":"<choose a password of at least 12 characters>","role":"COMUM"}
```

Both roles can create and read payments. Only ADMIN can create users. Missing,
invalid or expired credentials return 401; insufficient roles return 403.
Health and Prometheus endpoints remain available without authentication.

PaymentRequested carries the original signed JWT through the existing transactional
outbox and RabbitMQ. The processor validates and forwards it to
`POST /api/authorizations`, whose Spring Security principal retains the original
username and roles. Without a user context (including legacy queued messages),
the processor obtains a short-lived SERVICE token using
`POST /api/auth/service-token` with its configured client ID
`payment-processor-service` and `AUTH_PROCESSOR_PASSWORD` as `clientSecret`.
`AUTH_TOKEN_URL` defaults to `http://localhost:8080/api/auth/service-token`.
Service tokens last 300 seconds and are cached with a 30-second refresh margin.
SERVICE can call payment authorization but cannot create payments or users.

JWT verification uses RS256 signatures, expiry, issuer `payment-service` and
audience `payment-lab`, following [Spring Security resource-server support](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
Bearer credentials in queued messages/outbox rows are sensitive: restrict broker
and database access and use TLS outside the local lab. User tokens are never
silently replaced by service tokens when invalid or expired; messages delayed past
the one-hour lifetime follow the existing retry/DLQ path. The JWT is excluded from
message `toString()` and is not added to completion events or application logs.
Test-only RSA keys in `src/test/resources` must never be configured for runtime use.

Automated tests disable RabbitMQ outbox publishers with
`outbox.publisher.enabled=false` (and the processor tests disable Kafka publishing).
This keeps test-signed JWTs out of the running lab's queues. Runtime RabbitMQ
publishing remains enabled by default. Tokens signed with test keys are intentionally
rejected by runtime services; never configure a runtime service to trust test keys.
