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
