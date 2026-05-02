# Performance Tests (k6)

Prereqs:

- k6 installed (`choco install k6` on Windows)
- App running on `http://localhost:8080`
- Simulator running on `http://localhost:8081`

Single instance:

```bash
k6 run perf/k6-single.js
```

Multi instance (start 3 app instances on different ports and point load to a load balancer or one instance):

```bash
k6 run perf/k6-multi.js
```

Override defaults:

```bash
VUS=200 DURATION=120s API_BASE_URL=http://localhost:8080 k6 run perf/k6-single.js
```
