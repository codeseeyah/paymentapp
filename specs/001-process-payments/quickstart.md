# Quickstart: Payment Processing Service (developer)

Prerequisites

- Java 17 (LTS)
- Maven (`mvn`) or the included `mvnw`
- Docker & Docker Compose (for local Postgres and the simulator)

Run PostgreSQL locally (example `docker-compose.yml` included in `service/`):

```bash
docker compose up -d postgres
```

Run the simulator (profile `simulator`):

```bash
mvn -q spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=simulator --server.port=8081"
```

Run DB migrations (Flyway runs on application startup; or run manually):

```bash
# Build application
./mvnw clean package -DskipTests

# Run (app starts API + worker)
java -jar service/target/paymentapp.jar --spring.profiles.active=local --server.port=8080
```

Local env vars (example):

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/paymentapp
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=postgres
export PAYMENT_SIMULATOR_URL=http://localhost:8081/simulate
```

Running multiple instances locally (for multi-instance tests):

```bash
# Terminal 1
java -jar service/target/paymentapp.jar --spring.profiles.active=local --server.port=8080

# Terminal 2
java -jar service/target/paymentapp.jar --spring.profiles.active=local --server.port=8082
```

Run tests:

```bash
# Unit + integration (Testcontainers will run Postgres container)
./mvnw verify

# Performance tests (see performance-tests.md for scenarios)
# Example: k6 script invocation
```

Notes

- Flyway migrations are kept in `service/db/migration/`.
- The simulator for the external payment service is available under `service/simulator/` and is used by performance tests.
