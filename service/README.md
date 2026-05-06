# Payment Processing Service

The system consists of:

- producer(s) – Accepts requests and enqueues payments
- worker(s) – Processes payments asynchronously
- payment-simulator – Simulates external payment provider behavior
- PostgreSQL – Primary database
- PgBouncer (scaled mode) – Connection pooling for DB
- Nginx (scaled mode) – Load balancing for producers


## Quick start via Docker (recommended)

### Single instance

```bash
docker compose -f docker-compose.yml up
```

This maps services on the following host ports: 

- Producer (API): http://localhost:8080
- Simulator: http://localhost:8081

To stop:

```bash
docker compose -f docker-compose.yml down
```

### Scaled setup

```bash
docker compose -f docker-compose.scale.yml up --scale producer=N --scale worker=M
```
Example startup with 2 producers and 5 workers:

```bash
docker compose -f docker-compose.scale.yml up --scale producer=2 --scale worker=5
```
Note: recommended setup is a single producer with single/multiple workers.

```bash
docker compose -f docker-compose.scale.yml up --scale worker=10
```

This maps services on the following host ports: 

- Load-balanced producer (API): http://localhost:80
- Simulator: http://localhost:8081

To stop:

```bash
docker compose -f docker-compose.scale.yml down
```

## Quick start (single instance application in local development without full Docker stack):

1) Start PostgreSQL:

```bash
docker compose up -d postgres
```
Runs on localhost:5432

2) Start the payment simulator

```bash
mvn -q spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=simulator --server.port=8081"
```
Runs on localhost:8081

3) Build and run main application

```bash
mvn clean package
java -jar target/paymentapp-0.1.0-SNAPSHOT.jar --spring.profiles.active=local --server.port=8080
```
Runs on localhost:8080

## Notes:

- Flyway runs on application startup and applies migrations from `db/migration/`.
- The simulator runs inside this app under the `simulator` profile.
- Additional docs: See `specs/001-process-payments/quickstart.md`.

## Summary table regarding ports and services in Docker setup:

### Single-instance (`docker-compose.yml`)

| Service            | Port (Host → Container) | Description                |
|-------------------|------------------------|----------------------------|
| paymentapp        | 8080 → 8080            | Main API                   |
| payment-simulator | 8081 → 8081            | External payment simulator |
| postgres          | 5432 → 5432            | Database                   |


### Scaled setup (`docker-compose.scale.yml`)

| Service            | Port (Host → Container) | Description                         |
|-------------------|------------------------|-------------------------------------|
| nginx             | 80 → 80                | Load balancer for producers         |
| payment-simulator | 8081 → 8081            | External payment simulator          |
| postgres          | 5432 → 5432            | Database                            |
| pgbouncer         | 6432 → 6432            | DB connection pool                  |
| producer(s)       | internal 8080          | API instances (behind nginx)        |
| worker(s)         | none                   | Background processors (no HTTP API) |

**Notes:**
- Producers are accessed via **http://localhost** (port 80) through nginx
- Workers run with `SERVER_PORT=0` (no HTTP exposure)
- Database connections go through **PgBouncer (port 6432)** in scaled mode

## Configuration Notes

### Default database:
- DB: paymentapp
- user: postgres
- password: postgres

### Simulator

Configurable via enviornment variables: 

| Variable               | Description            | Default |
| ---------------------- | ---------------------- | ------- |
| SIMULATOR_MIN_DELAY_MS | Minimum response delay | 10 ms   |
| SIMULATOR_MAX_DELAY_MS | Maximum response delay | 2000 ms |
| SIMULATOR_FAILURE_RATE | Failure probability    | 0.05    |

