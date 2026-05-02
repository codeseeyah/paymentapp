# Payment Processing Service

Quick start (local):

1) Start PostgreSQL:

```bash
docker compose up -d postgres
```

2) Start the simulator (profile `simulator`) on port 8081:

```bash
mvn -q spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=simulator --server.port=8081"
```

3) Build and run:

```bash
mvn clean package
java -jar target/paymentapp-0.1.0-SNAPSHOT.jar --spring.profiles.active=local --server.port=8080
```

Notes:

- Flyway runs on application startup and applies migrations from `db/migration/`.
- The simulator runs inside this app under the `simulator` profile.
- See `specs/001-process-payments/quickstart.md` for full instructions.

Horizontal scaling test (producers + workers via Docker Compose):

```bash
docker compose -f docker-compose.scale.yml up --build --abort-on-container-exit k6
```

This starts:

- 2 producers (API only, worker disabled)
- 2 workers (worker enabled)
- 1 simulator
- k6 load generator (hits nginx on port 8080)
