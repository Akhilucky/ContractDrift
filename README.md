# Contract-Drift Sentinel

> **Real-time inference-based contract testing & drift detection for microservices**

![Status](https://img.shields.io/badge/status-alpha-yellow)

---

## What is Contract-Drift Sentinel?

Contract-Drift Sentinel is a real-time, inference-based contract testing and drift detection platform for microservices. It **observes live HTTP traffic** between services, **infers the implicit schema contract** from actual request/response shapes, and **flags any deviation** as a potential breaking change — without requiring pre-written tests or maintained fixtures.

The system acts as a **promotion gate**: it decides, based on observed drift severity and configurable policy, whether a new service version is safe to promote to a given environment.

---

## Architecture

```
┌─────────────────────┐       ┌──────────────────────────────────────┐
│   Service Pair      │       │         Contract-Drift Sentinel       │
│                     │       │                                      │
│  ┌──────┐   ┌──────┐│       │  ┌──────────┐    ┌───────────────┐  │
│  │Producer├───►Consumer││  HTTP │  │ Inference │    │  Contract     │  │
│  └──────┘   └──────┘│  ──┼──►│  Engine    │───►│  Repository   │  │
│         │            │       │  │ (Sketch)   │    │  (Postgres)   │  │
│         │            │       │  └──────────┘    └──────┬────────┘  │
│         │            │       │         │                │           │
│         │            │       │         ▼                ▼           │
│         │            │       │  ┌──────────┐    ┌───────────────┐  │
│         │            │       │  │ Drift    │    │  Violation     │  │
│         │            │       │  │ Detector │───►│  Store         │  │
│         │            │       │  └──────────┘    └──────┬────────┘  │
│         │            │       │         │                │           │
│         └────────────┘       │         ▼                ▼           │
│                              │  ┌──────────┐    ┌───────────────┐  │
│                              │  │Promotion │    │   Metrics     │  │
│                              │  │  Gate    │    │  (Prometheus) │  │
│                              │  └──────────┘    └───────────────┘  │
│                              └──────────────────────────────────────┘
```

---

## Quick Start

```bash
# Start all services
docker compose up -d

# Run the demo
./scripts/demo.sh
```

The demo will deploy dummy microservices, send 1000 requests, infer a contract, simulate a breaking change, and show violations and gate decisions.

---

## Component Overview

| Service              | Port   | Description                                    |
|----------------------|--------|------------------------------------------------|
| `sidecar-agent`      | 8090   | HTTP interceptor, PII scrubber, Kafka producer |
| `ingestion-service`  | 8081   | Kafka consumer, dedup, normalizer, gRPC client |
| `schema-inference`   | 50051  | Python gRPC server: genson-based schema inf.   |
| `drift-detector`     | 8082   | Semantic diff engine, breaking change class.   |
| `contract-registry`  | 8083   | Contract CRUD, violation store, Redis cache    |
| `promotion-gate`     | 8084   | Promotion eligibility check, override, audit   |
| `correlation-engine` | 8085   | GitHub Releases adapter, deployment correl.    |
| `dashboard`          | 5173   | React + Vite SPA: contracts, violations, gate  |
| `prometheus`         | 9090   | Metrics and alerting                           |
| `grafana`            | 3000   | Dashboards and visualization                   |
| `postgres`           | 5432   | Primary data store (PostgreSQL 16)             |
| `redis`              | 6379   | Contract cache, inference result cache         |
| `clickhouse`         | 8123   | OLAP store for raw traffic replay              |
| `kafka`              | 9092   | Message bus for traffic events                 |

---

## API Reference

| Method | Path                               | Description                        |
|--------|------------------------------------|------------------------------------|
| POST   | `/api/v1/contracts/import`         | Import a contract                  |
| GET    | `/api/v1/contracts`                | List all contracts                 |
| GET    | `/api/v1/contracts/{id}`           | Get a specific contract            |
| GET    | `/api/v1/violations`               | List all violations                |
| GET    | `/api/v1/violations?severity=BREAKING` | Filter violations by severity |
| GET    | `/api/v1/gate/promote?service=X&version=Y&env=Z` | Check promotion eligibility |
| POST   | `/api/v1/gate/override`            | Admin override a gate decision     |

---

## Technology Stack

| Component            | Technology                    |
|----------------------|-------------------------------|
| Sidecar Agent        | Java 21 + Spring Boot 3.x     |
| Ingestion Service    | Java 21 + Spring Boot 3.x     |
| Schema Inference     | Python 3.11 + gRPC + genson   |
| Contract Registry    | Java 21 + Spring Boot 3.x     |
| Drift Detector       | Java 21 + structured concur.  |
| Promotion Gate       | Java 21 + Spring Boot 3.x     |
| Correlation Engine   | Java 21 + Spring Boot 3.x     |
| Dashboard            | React + Vite + Tailwind CSS   |
| Database             | PostgreSQL 16 + JSONB         |
| Cache                | Redis 7                       |
| OLAP / Replay        | ClickHouse                    |
| Message Bus          | Apache Kafka + Avro           |
| Observability        | Prometheus + Grafana          |
| Metrics          | Prometheus            |
| Dashboards       | Grafana 10.4          |
| Containerization | Docker / Compose      |
| Proxy / Capture  | Envoy / eBPF (future) |

---

## Build Plan (8 Weeks)

| Week | Milestone                                      |
|------|------------------------------------------------|
| 1    | Scaffold services, Docker Compose, CI/CD       |
| 2    | Inference Engine – sketch extraction           |
| 3    | Contract Repository – CRUD, Postgres schema    |
| 4    | Drift Detector – comparison algorithms         |
| 5    | Violation Store – querying, severity levels    |
| 6    | Promotion Gate – policy engine, overrides      |
| 7    | Grafana dashboards, Prometheus metrics         |
| 8    | Load testing, benchmarks, documentation        |

---

## Contributing

Contributions are welcome! Please open an issue or submit a PR.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes (`git commit -m 'Add my feature'`)
4. Push to the branch (`git push origin feature/my-feature`)
5. Open a Pull Request

---

## License

Apache 2.0 — see [LICENSE](LICENSE).

---

## Research

This project draws on techniques from runtime schema inference, differential dataflow, and continuous contract testing. If you find this work useful, please cite the conceptual approach described at [https://arxiv.org/abs/2401.00000](https://arxiv.org/abs/2401.00000) (placeholder).
